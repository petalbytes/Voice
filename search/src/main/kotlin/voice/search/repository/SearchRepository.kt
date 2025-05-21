package voice.search.repository

import android.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import voice.search.api.SearchApi
import voice.search.api.DiscoverySearchResponse
import voice.search.error.SearchError
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for searching books and fetching details
 */
@Singleton
class SearchRepository @Inject constructor(
  private val api: SearchApi
) {
  /**
   * Search for books by query
   */
  suspend fun search(query: String): Flow<Result<List<DiscoveryResult>>> = flow {
    try {
      val response = api.search(query)
      if (response.isSuccessful) {
        val results = response.body()?.map { it.toDiscoveryResult() } ?: emptyList()
        emit(Result.success(results))
      } else {
        emit(Result.failure(createApiError(response.code(), response.message())))
      }
    } catch (e: IOException) {
      emit(Result.failure(SearchError.NetworkError("Network error occurred", e)))
    } catch (e: Exception) {
      emit(Result.failure(SearchError.UnknownError("An unexpected error occurred", e)))
    }
  }

  /**
   * Get book details by ID
   */
  suspend fun getBookDetails(bookId: String): Flow<Result<BookDetails>> = flow {
    try {
      val response = api.getDetails(bookId)
      if (response.isSuccessful) {
        val responseBody = response.body()

        // Log the entire response to check for borrow_link
        android.util.Log.d("SearchRepository", "Book details response: $responseBody")
        if (responseBody != null) {
          // Log available sources to specifically check for borrow_link
          responseBody.availableSources.forEachIndexed { index, source ->
            android.util.Log.d("SearchRepository", "Source $index - borrow_link: ${source.borrowLink}")
          }
        }

        val details = responseBody?.let { BookDetails.fromResponse(it) }
        if (details != null) {
          emit(Result.success(details))
        } else {
          emit(Result.failure(SearchError.ParseError("Empty response body")))
        }
      } else {
        emit(Result.failure(createApiError(response.code(), response.errorBody()?.string())))
      }
    } catch (e: IOException) {
      emit(Result.failure(SearchError.NetworkError("Network error occurred", e)))
    } catch (e: Exception) {
      emit(Result.failure(SearchError.UnknownError("An unexpected error occurred", e)))
    }
  }

  private fun createApiError(code: Int, message: String?): SearchError.ApiError {
    return SearchError.ApiError(
      code = code,
      message = message ?: "Unknown API error"
    )
  }

  private fun DiscoverySearchResponse.toDiscoveryResult(): DiscoveryResult {
    return DiscoveryResult(
      id = DiscoveryResult.generateId(mediaDetails),
      title = title,
      authors = authors,
      coverImageUrl = imageUrl,
      categories = category,
      language = language,
      keywords = keywords,
      mediaDetailsUrl = mediaDetails
    )
  }
}

/**
 * Domain model for discovery results
 */
data class DiscoveryResult(
  val id: String,
  val title: String,
  val authors: List<String>,
  val coverImageUrl: String,
  val categories: List<String> = emptyList(),
  val language: String = "",
  val keywords: List<String> = emptyList(),
  val mediaDetailsUrl: String
) {
  companion object {
    fun generateId(mediaDetailsUrl: String): String {
      val idParam = mediaDetailsUrl.substringAfter("id=", mediaDetailsUrl)
      return Base64.encodeToString(idParam.toByteArray(), Base64.URL_SAFE)
        .replace("=", "")
    }
  }
}

/**
 * Domain models for book details
 */
data class BookDetails(
  val description: String,
  val writtenBy: String,
  val availableSources: List<AudioSource>
) {
  companion object {
    fun fromResponse(response: voice.search.api.BookDetailsResponse): BookDetails {
      return BookDetails(
        description = response.description,
        writtenBy = response.writtenBy,
        availableSources = response.availableSources.map { AudioSource.fromResponse(it) }
      )
    }
  }
}

data class AudioSource(
  val readBy: String,
  val fileSize: String,
  val m3u: String?,
  val borrowLink: String?
) {
  companion object {
    fun fromResponse(response: voice.search.api.AudioSourceResponse): AudioSource {
      return AudioSource(
        readBy = response.readBy,
        fileSize = response.fileSize,
        m3u = response.m3u,
        borrowLink = response.borrowLink
      )
    }
  }
}
