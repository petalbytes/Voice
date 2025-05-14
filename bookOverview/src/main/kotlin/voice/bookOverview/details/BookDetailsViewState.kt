package voice.bookOverview.details

import voice.search.error.SearchError
import voice.search.repository.BookDetails
import voice.search.repository.DiscoveryResult

sealed class BookDetailsViewState {
  object Loading : BookDetailsViewState()
  
  data class Success(
    val discoveryResult: DiscoveryResult,
    val details: BookDetails
  ) : BookDetailsViewState()
  
  data class Error(val error: SearchError) : BookDetailsViewState()
} 