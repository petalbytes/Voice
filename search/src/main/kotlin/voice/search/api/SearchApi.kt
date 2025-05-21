package voice.search.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API interface for discovery search and book details
 */
interface SearchApi {
  @GET("search")
  suspend fun search(@Query("q") query: String): Response<List<DiscoverySearchResponse>>

  @GET("details")
  suspend fun getDetails(@Query("id") id: String): Response<BookDetailsResponse>
}

/**
 * Response model for discovery search
 */
@Serializable
data class DiscoverySearchResponse(
  val title: String,
  val authors: List<String>,
  val category: List<String> = emptyList(),
  val language: String = "",
  val keywords: List<String> = emptyList(),
  @SerialName("image_url")
  val imageUrl: String,
  @SerialName("media_details")
  val mediaDetails: String
)

/**
 * Response models for book details
 */
@Serializable
data class BookDetailsResponse(
  val description: String = "",
  @SerialName("written_by")
  val writtenBy: String = "",
  @SerialName("available_sources")
  val availableSources: List<AudioSourceResponse> = emptyList()
)

@Serializable
data class AudioSourceResponse(
  @SerialName("read_by")
  val readBy: String,
  @SerialName("file_size")
  val fileSize: String,
  val m3u: String? = null,
  @SerialName("borrow_link")
  val borrowLink: String? = null
) {
  override fun toString(): String {
    return "AudioSourceResponse(readBy=$readBy, fileSize=$fileSize, m3u=$m3u, borrowLink=$borrowLink)"
  }
}
