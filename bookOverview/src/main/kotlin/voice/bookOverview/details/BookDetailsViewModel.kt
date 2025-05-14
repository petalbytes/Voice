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
import voice.search.error.SearchError
import voice.search.repository.DiscoveryResult
import voice.search.repository.SearchRepository

class BookDetailsViewModel @AssistedInject constructor(
  private val repository: SearchRepository,
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

  @AssistedFactory
  interface Factory {
    fun create(
      discoveryResult: DiscoveryResult,
      mediaDetailsId: String
    ): BookDetailsViewModel
  }
} 