package voice.localborrow

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.github.junrar.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import voice.common.pref.BorrowLocation
import voice.data.supportedAudioFormats
import voice.documentfile.CachedDocumentFile
import voice.documentfile.CachedDocumentFileFactory
import voice.logging.core.Logger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import voice.pref.Pref

@Singleton
class BorrowService @Inject constructor(
  private val okHttpClient: OkHttpClient,
  private val context: Context,
  private val documentFileFactory: CachedDocumentFileFactory,
  @BorrowLocation
  private val borrowLocation: Pref<String>
) {
  // Keep track of active downloads to allow cancellation
  private var activeCall: Call? = null
  private var activeJob: Job? = null
  private var activeBookFolder: CachedDocumentFile? = null

  /**
   * Cancels any ongoing download or extraction and cleans up temporary files
   */
  fun cancelDownload() {
    // Cancel the HTTP call if active
    activeCall?.cancel()
    activeCall = null
    
    // Cancel the job
    activeJob?.cancel()
    activeJob = null
    
    // Clean up the folder if it was created for this download
    activeBookFolder?.let { folder ->
      try {
        DocumentFile.fromTreeUri(context, folder.uri)?.delete()
      } catch (e: Exception) {
        Logger.e(e, "Error deleting book folder during cancellation")
      }
      activeBookFolder = null
    }
  }

  /**
   * Downloads and extracts a RAR file from the borrow link
   * @param borrowLink The URL to download the RAR file from
   * @param bookName The name of the book (used for folder creation)
   * @return Flow of DownloadProgress that can be collected to track progress
   */
  suspend fun downloadAndExtractRar(borrowLink: String, bookName: String): Flow<DownloadProgress> = callbackFlow {
    try {
      // Cancel any existing download first
      cancelDownload()
      
      // Get or create the root audiobooks directory
      val location = borrowLocation.flow.first()
      val rootUri = if (location.isNotEmpty()) {
        Uri.parse(location)
      } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        // Use the standard Audiobooks directory on Android 10+
        val audiobooks = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS)
        audiobooks.mkdirs()
        Uri.fromFile(audiobooks)
      } else {
        throw BorrowException.NoLocationSet
      }

      // Create a folder for this book
      val bookFolder = createBookFolder(rootUri, bookName)
        ?: throw BorrowException.FolderCreationFailed
        
      // Store reference to current book folder for potential cancellation
      activeBookFolder = bookFolder

      // Launch the download and extraction in a background coroutine
      activeJob = launch(Dispatchers.IO) {
        try {
          // Create a temporary file for the RAR
          val tempRarFile = File.createTempFile("book", ".rar")
          
          try {
            // Build the request
            val request = Request.Builder()
              .url(borrowLink)
              .build()
              
            // Create call and store reference for potential cancellation
            val call = okHttpClient.newCall(request)
            activeCall = call
              
            // Execute the network request
            call.execute().use { response ->
              if (!response.isSuccessful) {
                throw BorrowException.DownloadFailed("Failed to download RAR: ${response.code}")
              }
              
              val body = response.body ?: throw BorrowException.DownloadFailed("Empty response body")
              val contentLength = body.contentLength()
              
              // Save the RAR content with progress tracking
              tempRarFile.sink().buffer().use { sink ->
                body.byteStream().use { input ->
                  val buffer = ByteArray(8192)
                  var bytesRead: Long = 0
                  var read: Int
                  
                  while (input.read(buffer).also { read = it } != -1) {
                    sink.write(buffer, 0, read)
                    bytesRead += read
                    val progress = if (contentLength > 0) {
                      (bytesRead * 100 / contentLength).toInt()
                    } else {
                      0
                    }
                    send(DownloadProgress.Downloading(progress))
                  }
                }
              }
              
              // Clear active call now that download is complete
              activeCall = null
              
              // Extract the RAR contents
              extractRarFile(tempRarFile, bookFolder, this@callbackFlow)
              send(DownloadProgress.Completed)
              
              // Clear active resources since we're done
              activeBookFolder = null
              activeJob = null
            }
          } finally {
            // Clean up the temporary file
            tempRarFile.delete()
          }
        } catch (e: Exception) {
          Logger.e(e, "Error downloading/extracting RAR")
          when (e) {
            is BorrowException -> send(DownloadProgress.Error(e))
            else -> send(DownloadProgress.Error(BorrowException.Unknown(e)))
          }
        }
      }
      
      // Handle cleanup when the flow is cancelled or closed
      awaitClose { 
        cancelDownload()
      }
      
    } catch (e: Exception) {
      Logger.e(e, "Error setting up download")
      when (e) {
        is BorrowException -> send(DownloadProgress.Error(e))
        else -> send(DownloadProgress.Error(BorrowException.Unknown(e)))
      }
      awaitClose { }
    }
  }
   
  /**
   * Extracts a RAR file to the specified book folder
   */
  private suspend fun extractRarFile(
    rarFile: File, 
    bookFolder: CachedDocumentFile,
    producerScope: kotlinx.coroutines.channels.ProducerScope<DownloadProgress>
  ) {
    withContext(Dispatchers.IO) {
      // Extract the RAR contents
      producerScope.send(DownloadProgress.Extracting(0))
      
      Archive(rarFile).use { archive ->
        // We'll still check for at least one audio file to ensure it's a valid audiobook
        val hasAudioFiles = archive.fileHeaders.any { 
          it.fileName.substringAfterLast(".", "").lowercase() in supportedAudioFormats 
        }
        
        if (!hasAudioFiles) {
          throw BorrowException.NoAudioFiles
        }
        
        val totalFiles = archive.fileHeaders.size
        var extractedFiles = 0
        
        // Create a map to track directories we've already created
        val createdDirs = mutableMapOf<String, DocumentFile>()
        // Store the root document file
        val rootDocumentFile = DocumentFile.fromTreeUri(context, bookFolder.uri)
          ?: throw BorrowException.ExtractionFailed("Could not access book folder")
        createdDirs[""] = rootDocumentFile
        
        archive.fileHeaders.forEach { fileHeader ->
          val fileName = fileHeader.fileName
          
          try {
            // Check if this is a directory
            if (fileHeader.isDirectory) {
              // Create directory structure
              createDirectoryStructure(fileName, rootDocumentFile, createdDirs)
            } else {
              // It's a file - determine appropriate mime type
              val extension = fileName.substringAfterLast(".", "").lowercase()
              val mimeType = when(extension) {
                "mp3" -> "audio/mpeg"
                "m4a", "m4b" -> "audio/mp4"
                "ogg" -> "audio/ogg"
                "opus" -> "audio/opus"
                "wav" -> "audio/wav"
                "flac" -> "audio/flac"
                "cue" -> "application/x-cue"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "txt" -> "text/plain"
                "pdf" -> "application/pdf"
                else -> "*/*" // Generic mime type for other files
              }
              
              // Create parent directories if needed
              val lastSlashIndex = fileName.lastIndexOf('/')
              val parentPath = if (lastSlashIndex >= 0) fileName.substring(0, lastSlashIndex) else ""
              val fileNameWithoutPath = if (lastSlashIndex >= 0) fileName.substring(lastSlashIndex + 1) else fileName
              
              val parentDir = if (parentPath.isEmpty()) {
                rootDocumentFile
              } else {
                createDirectoryStructure(parentPath, rootDocumentFile, createdDirs)
              }
              
              // Create the file
              val extractedFile = parentDir.createFile(mimeType, fileNameWithoutPath)
                ?: throw BorrowException.ExtractionFailed("Could not create file $fileName")
              
              // Extract the file content
              context.contentResolver.openOutputStream(extractedFile.uri)?.use { outputStream ->
                archive.extractFile(fileHeader, outputStream)
              }
            }
            
            extractedFiles++
            producerScope.send(DownloadProgress.Extracting(extractedFiles * 100 / totalFiles))
          } catch (e: Exception) {
            Logger.e(e, "Error extracting file: $fileName")
            // Continue with next file instead of failing completely
          }
        }
      }
    }
  }
  
  /**
   * Creates a directory structure based on a path string
   * Returns the innermost directory DocumentFile
   */
  private fun createDirectoryStructure(
    path: String,
    rootDir: DocumentFile,
    createdDirs: MutableMap<String, DocumentFile>
  ): DocumentFile {
    // Handle directory path with trailing slash
    val dirPath = path.trimEnd('/')
    
    // If we've already created this directory, return it
    createdDirs[dirPath]?.let { return it }
    
    // Split path into components
    val components = dirPath.split('/')
    var currentPath = ""
    var currentDir = rootDir
    
    components.forEach { component ->
      // Skip empty components (like from a leading slash)
      if (component.isEmpty()) return@forEach
      
      val nextPath = if (currentPath.isEmpty()) component else "$currentPath/$component"
      
      // Check if we've already created this directory
      val nextDir = createdDirs[nextPath]
        ?: run {
          // Look for existing directory first
          var dir = currentDir.findFile(component)
          // Create if it doesn't exist
          if (dir == null || !dir.isDirectory) {
            dir = currentDir.createDirectory(component)
          }
          dir ?: throw BorrowException.ExtractionFailed("Could not create directory: $nextPath")
        }
      
      // Store the created directory for future use
      createdDirs[nextPath] = nextDir
      currentPath = nextPath
      currentDir = nextDir
    }
    
    return currentDir
  }

  private suspend fun createBookFolder(rootUri: Uri, bookName: String): CachedDocumentFile? {
    return withContext(Dispatchers.IO) {
      val rootDoc = documentFileFactory.create(rootUri)
      
      // First check if folder already exists
      rootDoc.children.firstOrNull { it.name == bookName } 
        ?: DocumentFile.fromTreeUri(context, rootUri)?.createDirectory(bookName)?.let { 
          documentFileFactory.create(it.uri)
        }
    }
  }
}

sealed class DownloadProgress {
  data class Downloading(val progress: Int) : DownloadProgress()
  data class Extracting(val progress: Int = 0) : DownloadProgress()
  object Completed : DownloadProgress()
  data class Error(val exception: BorrowException) : DownloadProgress()
}

sealed class BorrowException : Exception() {
  data class DownloadFailed(override val message: String) : BorrowException()
  data class ExtractionFailed(override val message: String, override val cause: Throwable? = null) : BorrowException()
  object NoAudioFiles : BorrowException() {
    override val message: String = "No audio files found in RAR archive"
  }
  object NoLocationSet : BorrowException() {
    override val message: String = "No borrow location set"
  }
  object FolderCreationFailed : BorrowException() {
    override val message: String = "Could not create book folder"
  }
  data class Unknown(override val cause: Throwable) : BorrowException() {
    override val message: String = "Unknown error: ${cause.message}"
  }
}
