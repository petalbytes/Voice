package voice.localborrow

import android.content.Context
import android.content.Intent
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
import androidx.core.net.toUri
import android.provider.DocumentsContract

@Singleton
class BorrowService @Inject constructor(
  private val okHttpClient: OkHttpClient,
  private val context: Context,
  private val documentFileFactory: CachedDocumentFileFactory,
  @BorrowLocation
  private val borrowLocation: Pref<String>,
) {
  // Keep track of active borrows to allow cancellation
  private var activeCall: Call? = null
  private var activeJob: Job? = null
  private var activeBookFolder: CachedDocumentFile? = null

  /**
   * Cancels any ongoing borrow or extraction and cleans up temporary files
   */
  fun cancelBorrow() {
    // Cancel the HTTP call if active
    activeCall?.cancel()
    activeCall = null

    // Cancel the job
    activeJob?.cancel()
    activeJob = null

    // Clean up the folder if it was created for this borrow
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
   * Borrows and extracts a RAR file from the borrow link
   * @param borrowLink The URL to borrow the RAR file from
   * @param bookName The name of the book (used for folder creation)
   * @return Flow of BorrowProgress that can be collected to track progress
   */
  suspend fun borrowAndExtractRar(borrowLink: String, bookName: String): Flow<BorrowProgress> = callbackFlow {
    try {
      // Cancel any existing borrow first
      cancelBorrow()

      // Get the root audiobooks directory from settings
      val location = borrowLocation.flow.first()
      if (location.isEmpty()) {
        throw BorrowException.NoLocationSet
      }

      val rootUri = location.toUri()

      // Check if we already have the permission instead of trying to take it
      val hasPermission = context.contentResolver.persistedUriPermissions.any {
        it.uri == rootUri && it.isReadPermission && it.isWritePermission
      }

      if (!hasPermission) {
        throw BorrowException.StoragePermissionRequired
      }

      // Convert tree URI to document URI for proper access
      val documentUri = if (DocumentsContract.isTreeUri(rootUri)) {
        DocumentsContract.buildDocumentUriUsingTree(
          rootUri,
          DocumentsContract.getTreeDocumentId(rootUri)
        )
      } else {
        rootUri
      }

      // Create a folder for this book
      val bookFolder = createBookFolder(documentUri, bookName)
        ?: throw BorrowException.FolderCreationFailed

      // Store reference to current book folder for potential cancellation
      activeBookFolder = bookFolder

      // Launch the borrow and extraction in a background coroutine
      activeJob = launch(Dispatchers.IO) {
        try {
          // Create a temporary file for the borrow
          val tempFile = File.createTempFile("book", "")

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
                cleanupEmptyBookFolder(bookFolder)
                throw BorrowException.BorrowFailed("Failed to borrow: ${response.code}")
              }

              val body = response.body ?: throw BorrowException.BorrowFailed("Empty response body")
              val contentLength = body.contentLength()

              // Save the content with progress tracking
              tempFile.sink().buffer().use { sink ->
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
                    send(BorrowProgress.Borrowing(progress))
                  }
                }
              }

              // Ensure the file is properly flushed and verify its size
              if (!tempFile.exists() || tempFile.length() == 0L) {
                cleanupEmptyBookFolder(bookFolder)
                throw BorrowException.BorrowFailed("Borrowed file is empty or does not exist")
              }

              if (contentLength > 0 && tempFile.length() != contentLength) {
                cleanupEmptyBookFolder(bookFolder)
                throw BorrowException.BorrowFailed("Borrowed file size (${tempFile.length()}) does not match expected size ($contentLength)")
              }

              // Clear active call now that borrow is complete
              activeCall = null

              // Check if this is a RAR file that needs extraction
              val isRarFile = response.headers["Content-Type"]?.contains("rar", ignoreCase = true) == true ||
                             borrowLink.endsWith(".rar", ignoreCase = true)

              if (isRarFile) {
                // Extract the RAR contents
                try {
                  extractRarFile(tempFile, bookFolder, this@callbackFlow)
                  send(BorrowProgress.Completed)
                } catch (e: com.github.junrar.exception.CorruptHeaderException) {
                  cleanupEmptyBookFolder(bookFolder)
                  throw BorrowException.ExtractionFailed("Invalid or corrupted RAR file", e)
                }
              } else {
                // Move the audio file to the final location
                val fileName = borrowLink.substringAfterLast('/').let { 
                  java.net.URLDecoder.decode(it, "UTF-8") 
                }
                
                // Get appropriate mime type
                val extension = fileName.substringAfterLast(".", "").lowercase()
                val mimeType = when(extension) {
                  "mp3" -> "audio/mpeg"
                  "m4a", "m4b" -> "audio/mp4"
                  "ogg" -> "audio/ogg"
                  "opus" -> "audio/opus"
                  "wav" -> "audio/wav"
                  "flac" -> "audio/flac"
                  else -> "*/*"
                }

                // Get root document file and create the audio file
                val rootDocumentFile = DocumentFile.fromTreeUri(context, bookFolder.uri)
                  ?: throw BorrowException.ExtractionFailed("Could not access book folder")
                
                val audioFile = rootDocumentFile.createFile(mimeType, fileName)
                  ?: throw BorrowException.ExtractionFailed("Could not create file $fileName")

                // Copy the temp file to the final location
                context.contentResolver.openOutputStream(audioFile.uri)?.use { outputStream ->
                  tempFile.inputStream().use { input ->
                    input.copyTo(outputStream)
                  }
                } ?: throw BorrowException.ExtractionFailed("Could not open output stream for $fileName")

                send(BorrowProgress.Completed)
              }

              // Clear active resources since we're done
              activeBookFolder = null
              activeJob = null
            }
          } finally {
            // Clean up the temporary file
            tempFile.delete()
          }
        } catch (e: Exception) {
          Logger.e(e, "Error borrowing/extracting RAR")
          cleanupEmptyBookFolder(bookFolder)
          when (e) {
            is BorrowException -> send(BorrowProgress.Error(e))
            else -> send(BorrowProgress.Error(BorrowException.Unknown(e)))
          }
        }
      }

      // Handle cleanup when the flow is cancelled or closed
      awaitClose {
        cancelBorrow()
      }

    } catch (e: Exception) {
      Logger.e(e, "Error setting up borrow")
      when (e) {
        is BorrowException -> send(BorrowProgress.Error(e))
        else -> send(BorrowProgress.Error(BorrowException.Unknown(e)))
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
    producerScope: kotlinx.coroutines.channels.ProducerScope<BorrowProgress>,
  ) {
    withContext(Dispatchers.IO) {
      // Validate RAR file before extraction
      if (!rarFile.exists()) {
        throw BorrowException.ExtractionFailed("RAR file does not exist")
      }

      if (rarFile.length() < 512) { // RAR files have a minimum header size
        throw BorrowException.ExtractionFailed("File is too small to be a valid RAR archive")
      }

      // Check RAR signature
      rarFile.inputStream().use { input ->
        val signature = ByteArray(7)
        val read = input.read(signature)
        if (read != 7 || !isRarSignature(signature)) {
          throw BorrowException.ExtractionFailed("File is not a valid RAR archive")
        }
      }

      // Extract the RAR contents
      producerScope.send(BorrowProgress.Extracting(0))

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
            producerScope.send(BorrowProgress.Extracting(extractedFiles * 100 / totalFiles))
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
    createdDirs: MutableMap<String, DocumentFile>,
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

  /**
   * Checks if the given bytes match the RAR file signature
   * RAR5 signature: 52 61 72 21 1A 07 01 00
   * RAR4 signature: 52 61 72 21 1A 07 00
   */
  private fun isRarSignature(signature: ByteArray): Boolean {
    if (signature.size < 7) return false
    return signature[0] == 0x52.toByte() && // R
           signature[1] == 0x61.toByte() && // a
           signature[2] == 0x72.toByte() && // r
           signature[3] == 0x21.toByte() && // !
           signature[4] == 0x1A.toByte() &&
           signature[5] == 0x07.toByte() &&
           (signature[6] == 0x00.toByte() || // RAR4
            signature[6] == 0x01.toByte())   // RAR5
  }

  /**
   * Checks if a book folder is empty and deletes it if so
   */
  private fun cleanupEmptyBookFolder(bookFolder: CachedDocumentFile) {
    try {
      // Check if the folder exists and is empty
      if (bookFolder.children.isEmpty()) {
        DocumentFile.fromTreeUri(context, bookFolder.uri)?.delete()
        Logger.d("Deleted empty book folder: ${bookFolder.name}")
      }
    } catch (e: Exception) {
      Logger.w(e, "Failed to cleanup empty book folder: ${bookFolder.name}")
    }
  }
}

sealed class BorrowProgress {
  data class Borrowing(val progress: Int) : BorrowProgress()
  data class Extracting(val progress: Int = 0) : BorrowProgress()
  object Completed : BorrowProgress()
  data class Error(val exception: BorrowException) : BorrowProgress()
}

sealed class BorrowException : Exception() {
  data class BorrowFailed(override val message: String) : BorrowException()
  data class ExtractionFailed(override val message: String, override val cause: Throwable? = null) : BorrowException()
  object NoAudioFiles : BorrowException() {
    override val message: String = "No audio files found in RAR archive"
  }
  object NoLocationSet : BorrowException() {
    override val message: String = "Please select a folder for borrowed audiobooks in settings"
  }
  object FolderCreationFailed : BorrowException() {
    override val message: String = "Could not create book folder"
  }
  object StoragePermissionRequired : BorrowException() {
    override val message: String = "Storage permission required. Please grant folder access in settings."
  }
  data class Unknown(override val cause: Throwable) : BorrowException() {
    override val message: String = "Unknown error: ${cause.message}"
  }
}
