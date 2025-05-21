package voice.bookOverview.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import voice.localborrow.BorrowService
import voice.localborrow.DownloadProgress
import voice.search.error.SearchError
import voice.search.repository.DiscoveryResult
import voice.search.repository.SearchRepository

class BookDetailsViewModel @AssistedInject constructor(
  private val repository: SearchRepository,
  private val borrowService: BorrowService,
  @Assisted private val discoveryResult: DiscoveryResult,
  @Assisted private val mediaDetailsId: String
) : ViewModel() {

  private val _state = MutableStateFlow<BookDetailsViewState>(BookDetailsViewState.Loading)
  val state: StateFlow<BookDetailsViewState> = _state.asStateFlow()

  init {
    loadBookDetails()
  }

  fun loadBookDetails() {
    _state.value = BookDetailsViewState.Loading
    viewModelScope.launch {
      repository.getBookDetails(mediaDetailsId)
        .catch { e ->
          val error = when (e) {
            is SearchError -> e
            else -> SearchError.UnknownError("Unexpected error", e)
          }
          _state.value = BookDetailsViewState.Error(error)
        }
        .collectLatest { result ->
          result.fold(
            onSuccess = { bookDetails ->
              _state.value = BookDetailsViewState.Success(
                discoveryResult = discoveryResult,
                details = bookDetails
              )
            },
            onFailure = { error ->
              _state.value = BookDetailsViewState.Error(
                error as? SearchError ?: SearchError.UnknownError("Unknown error", error)
              )
            }
          )
        }
    }
  }

  fun borrowBook(borrowLink: String) {
    viewModelScope.launch {
      val currentState = _state.value
      if (currentState !is BookDetailsViewState.Success) return@launch

      // Immediately set to Starting state to grey out the button
      _state.value = currentState.copy(borrowProgress = BorrowProgress.Starting)

      borrowService.downloadAndExtractRar(borrowLink, discoveryResult.title)
        .collect { progress ->
          val borrowProgress = when (progress) {
            is DownloadProgress.Downloading -> BorrowProgress.Downloading(progress.progress)
            is DownloadProgress.Extracting -> BorrowProgress.Extracting(progress.progress)
            is DownloadProgress.Completed -> BorrowProgress.Completed
            is DownloadProgress.Error -> BorrowProgress.Error(progress.exception.message ?: "Unknown error")
          }
          _state.value = currentState.copy(borrowProgress = borrowProgress)
        }
    }
  }

  fun cancelBorrowBook() {
    borrowService.cancelDownload()
    
    // Reset borrow progress state
    val currentState = _state.value
    if (currentState is BookDetailsViewState.Success) {
      _state.value = currentState.copy(borrowProgress = null)
    }
  }

  @AssistedFactory
  interface Factory {
    fun create(
      discoveryResult: DiscoveryResult,
      mediaDetailsId: String
    ): BookDetailsViewModel
  }
} 