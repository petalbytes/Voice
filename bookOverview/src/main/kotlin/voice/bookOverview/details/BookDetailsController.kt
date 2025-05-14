package voice.bookOverview.details

import android.os.Bundle
import androidx.compose.runtime.Composable
import com.squareup.anvil.annotations.ContributesTo
import kotlinx.serialization.json.Json
import voice.bookOverview.di.BookOverviewComponent
import voice.common.AppScope
import voice.common.compose.ComposeController
import voice.common.navigation.Navigator
import voice.common.rootComponentAs
import voice.search.repository.AudioSource
import voice.search.repository.DiscoveryResult
import javax.inject.Inject

private const val NI_TITLE = "ni#title"
private const val NI_AUTHORS = "ni#authors"
private const val NI_COVER_URL = "ni#coverUrl"
private const val NI_CATEGORIES = "ni#categories"
private const val NI_LANGUAGE = "ni#language"
private const val NI_MEDIA_DETAILS_URL = "ni#mediaDetailsUrl"
private const val NI_ID = "ni#id"
private const val NI_MEDIA_DETAILS_ID = "ni#mediaDetailsId"

class BookDetailsController(args: Bundle) : ComposeController(args) {

  constructor(discoveryResult: DiscoveryResult, mediaDetailsId: String) : this(
    Bundle().apply {
      // Store individual fields instead of using Parcelable
      putString(NI_ID, discoveryResult.id)
      putString(NI_TITLE, discoveryResult.title)
      putStringArrayList(NI_AUTHORS, ArrayList(discoveryResult.authors))
      putString(NI_COVER_URL, discoveryResult.coverImageUrl)
      putStringArrayList(NI_CATEGORIES, ArrayList(discoveryResult.categories))
      putString(NI_LANGUAGE, discoveryResult.language)
      putString(NI_MEDIA_DETAILS_URL, discoveryResult.mediaDetailsUrl)
      putString(NI_MEDIA_DETAILS_ID, mediaDetailsId)
    }
  )
  
  private val discoveryResult: DiscoveryResult by lazy {
    DiscoveryResult(
      id = args.getString(NI_ID) ?: "",
      title = args.getString(NI_TITLE) ?: "",
      authors = args.getStringArrayList(NI_AUTHORS)?.toList() ?: emptyList(),
      coverImageUrl = args.getString(NI_COVER_URL) ?: "",
      categories = args.getStringArrayList(NI_CATEGORIES)?.toList() ?: emptyList(),
      language = args.getString(NI_LANGUAGE) ?: "",
      mediaDetailsUrl = args.getString(NI_MEDIA_DETAILS_URL) ?: ""
    )
  }
  
  private val mediaDetailsId: String by lazy {
    args.getString(NI_MEDIA_DETAILS_ID) ?: ""
  }

  // Initialize ViewModel when controller is created
  private val viewModel by lazy {
    rootComponentAs<BookOverviewComponent.Factory.Provider>()
      .bookOverviewComponentProviderFactory
      .create()
      .bookDetailsViewModelFactory
      .create(
        discoveryResult = discoveryResult,
        mediaDetailsId = mediaDetailsId
      )
  }

  @Inject
  lateinit var navigator: Navigator

  init {
    rootComponentAs<BookOverviewComponent.Factory.Provider>()
      .bookOverviewComponentProviderFactory
      .create()
      .inject(this)
  }

  @Composable
  override fun Content() {
    BookDetailsScreen(
      viewModel = viewModel,
      onBackClick = { navigator.goBack() },
      onImportBook = { result, source -> importBook(result, source) }
    )
  }

  private fun importBook(result: DiscoveryResult, source: AudioSource) {
    // TODO: Implement book import functionality
    // This would typically download the audio content and add it to the local library
    // For now, just pop back to the previous screen
    navigator.goBack()
  }
} 