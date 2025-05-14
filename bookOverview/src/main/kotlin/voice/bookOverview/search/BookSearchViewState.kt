package voice.bookOverview.search

import androidx.compose.runtime.Immutable
import voice.bookOverview.overview.BookOverviewItemViewState
import voice.bookOverview.overview.BookOverviewLayoutMode
import voice.search.error.SearchError
import voice.search.repository.DiscoveryResult

@Immutable
sealed interface BookSearchViewState {
  val query: String

  data class SearchResults(
    val books: List<BookOverviewItemViewState>,
    val layoutMode: BookOverviewLayoutMode,
    override val query: String,
    val discoveryResults: DiscoverySearchState = DiscoverySearchState.Loading,
  ) : BookSearchViewState

  data class EmptySearch(
    val suggestedAuthors: List<String>,
    val recentQueries: List<String>,
    override val query: String,
  ) : BookSearchViewState
}

@Immutable
sealed interface DiscoverySearchState {
  object Loading : DiscoverySearchState
  data class Success(val results: List<DiscoveryResult>) : DiscoverySearchState
  data class Error(val error: SearchError) : DiscoverySearchState
}
