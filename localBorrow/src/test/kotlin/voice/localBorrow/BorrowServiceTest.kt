package voice.localborrow

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockkStatic
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import voice.data.folders.AudiobookFolders
import voice.documentfile.CachedDocumentFile
import voice.documentfile.CachedDocumentFileFactory
import android.net.Uri
import androidx.datastore.core.DataStore
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import android.os.Environment
import voice.pref.Pref
import java.io.File

class BorrowServiceTest {

  private val mockOkHttpClient = mockk<OkHttpClient>()
  private val mockDocumentFileFactory = mockk<CachedDocumentFileFactory>()
  private val mockAudiobookFolders = mockk<AudiobookFolders>()
  private val mockRootFolders = mockk<DataStore<List<Uri>>>()
  private val mockContext = mockk<Context>()
  private val mockBorrowLocation = mockk<Pref<String>>()

  private val borrowService = BorrowService(
    okHttpClient = mockOkHttpClient,
    documentFileFactory = mockDocumentFileFactory,
//    audiobookFolders = mockAudiobookFolders,
//    rootAudioBookFolders = mockRootFolders,
    context = mockContext,
    borrowLocation = mockBorrowLocation
  )

  @Test
  fun `downloadAndExtractRar fails when no root folder configured`() = runTest {
    // Given
    coEvery { mockRootFolders.data } returns flowOf(emptyList())

    // When
    val result = borrowService.downloadAndExtractRar("http://example.com/book.rar", "Test Book")

    // Then
    result.isFailure shouldBe true
    result.exceptionOrNull()?.message shouldBe "No root audiobook folder configured"
  }

  @Test
  fun `downloadAndExtractRar fails when no borrow location set and below Android 10`() = runTest {
    // Given
    coEvery { mockBorrowLocation.flow } returns flowOf("")
    every { android.os.Build.VERSION.SDK_INT } returns 28 // Below Android 10

    // When
    val result = borrowService.downloadAndExtractRar("http://example.com/book.rar", "Test Book")

    // Then
    result.isFailure shouldBe true
    result.exceptionOrNull()?.message shouldBe "No borrow location set"
  }

  @Test
  fun `downloadAndExtractRar uses system audiobooks directory on Android 10+ when no location set`() = runTest {
    // Given
    coEvery { mockBorrowLocation.flow } returns flowOf("")
    every { android.os.Build.VERSION.SDK_INT } returns 29 // Android 10

    val mockAudiobooksDir = mockk<File>()
    every { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS) } returns mockAudiobooksDir
    every { mockAudiobooksDir.mkdirs() } returns true
    every { mockAudiobooksDir.absolutePath } returns "/storage/emulated/0/Audiobooks"

    val mockUri = mockk<Uri>()
    every { Uri.fromFile(mockAudiobooksDir) } returns mockUri

    val mockRootFolder = mockk<CachedDocumentFile>()
    every { mockDocumentFileFactory.create(any()) } returns mockRootFolder
    every { mockRootFolder.children } returns emptyList()
    every { mockRootFolder.uri } returns mockUri

    val mockDocumentFile = mockk<DocumentFile>()
    every { DocumentFile.fromTreeUri(mockContext, mockUri) } returns mockDocumentFile
    every { mockDocumentFile.createDirectory("Test Book") } returns mockk {
      every { uri } returns mockk()
    }

    mockHttpResponse(200)

    // When
    val result = borrowService.downloadAndExtractRar("http://example.com/book.rar", "Test Book")

    // Then
    result.isSuccess shouldBe true
  }

  @Test
  fun `downloadAndExtractRar uses custom location when set`() = runTest {
    // Given
    val customLocation = "/storage/emulated/0/MyAudiobooks"
    coEvery { mockBorrowLocation.flow } returns flowOf(customLocation)

    mockkStatic(Uri::class)
    val mockUri = mockk<Uri>()
    every { Uri.parse(customLocation) } returns mockUri

    val mockRootFolder = mockk<CachedDocumentFile>()
    every { mockDocumentFileFactory.create(any()) } returns mockRootFolder
    every { mockRootFolder.children } returns emptyList()
    every { mockRootFolder.uri } returns mockUri

    mockkStatic(DocumentFile::class)
    val mockDocumentFile = mockk<DocumentFile>()
    every { DocumentFile.fromTreeUri(mockContext, mockUri) } returns mockDocumentFile
    every { mockDocumentFile.createDirectory("Test Book") } returns mockk {
      every { uri } returns mockk()
    }

    mockHttpResponse(200)

    // When
    val result = borrowService.downloadAndExtractRar("http://example.com/book.rar", "Test Book")

    // Then
    result.isSuccess shouldBe true
  }

  @Test
  fun `downloadAndExtractRar fails when download fails`() = runTest {
    // Given
    coEvery { mockBorrowLocation.flow } returns flowOf("/storage/emulated/0/Audiobooks")
    val mockUri = mockk<Uri>()
    every { Uri.parse(any()) } returns mockUri

    val mockRootFolder = mockk<CachedDocumentFile>()
    every { mockDocumentFileFactory.create(any()) } returns mockRootFolder
    every { mockRootFolder.children } returns emptyList()
    every { mockRootFolder.uri } returns mockUri

    mockkStatic(DocumentFile::class)
    val mockDocumentFile = mockk<DocumentFile>()
    every { DocumentFile.fromTreeUri(mockContext, mockUri) } returns mockDocumentFile
    every { mockDocumentFile.createDirectory("Test Book") } returns mockk {
      every { uri } returns mockk()
    }

    mockHttpResponse(404)

    // When
    val result = borrowService.downloadAndExtractRar("http://example.com/book.rar", "Test Book")

    // Then
    result.isFailure shouldBe true
    result.exceptionOrNull()?.message shouldBe "Failed to download RAR: 404"
  }

  private fun mockHttpResponse(statusCode: Int) {
    val mockCall = mockk<okhttp3.Call>()
    every { mockOkHttpClient.newCall(any()) } returns mockCall

    val responseBuilder = Response.Builder()
      .code(statusCode)
      .request(Request.Builder().url("http://example.com/book.rar").build())
      .protocol(Protocol.HTTP_1_1)
      .message(if (statusCode == 200) "OK" else "Not Found")

    if (statusCode == 200) {
      responseBuilder.body(ByteArray(100).toResponseBody())
    }

    every { mockCall.execute() } returns responseBuilder.build()
  }
}
