package voice.bookOverview.details

import voice.search.error.SearchError
import voice.search.repository.BookDetails
import voice.search.repository.DiscoveryResult

sealed class BookDetailsViewState {
  object Loading : BookDetailsViewState()
  
  data class Success(
    val discoveryResult: DiscoveryResult,
    val details: BookDetails,
    val borrowProgress: BorrowProgress? = null
  ) : BookDetailsViewState()
  
  data class Error(val error: SearchError) : BookDetailsViewState()
}

sealed class BorrowProgress {
  object Starting : BorrowProgress()
  data class Downloading(val progress: Int) : BorrowProgress()
  data class Extracting(val progress: Int) : BorrowProgress()
  object Completed : BorrowProgress()
  data class Error(val message: String) : BorrowProgress()
} 