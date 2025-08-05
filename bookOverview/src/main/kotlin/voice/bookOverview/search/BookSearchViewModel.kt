package voice.bookOverview.search

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import voice.bookOverview.overview.BookOverviewItemViewState
import voice.bookOverview.overview.BookOverviewLayoutMode
import voice.common.compose.ImmutableFile
import voice.data.Book
import voice.logging.core.Logger
import voice.search.BookSearch

class BookSearchViewModel @AssistedInject constructor(
  private val bookSearch: BookSearch,
  @Assisted initialQuery: String,
  @Assisted private val layoutMode: BookOverviewLayoutMode,
) : ViewModel() {

  private val _viewState = MutableStateFlow<BookSearchViewState>(
    BookSearchViewState.EmptySearch(
      suggestedAuthors = emptyList(),
      recentQueries = emptyList(),
      query = initialQuery,
    ),
  )
  val viewState: StateFlow<BookSearchViewState> = _viewState.asStateFlow()

  init {
    if (initialQuery.isNotBlank()) {
      executeSearch(initialQuery)
    }
  }

  // Update query without triggering search
  fun updateQuery(query: String) {
    val currentState = _viewState.value

    if (query.isBlank()) {
      _viewState.value = BookSearchViewState.EmptySearch(
        suggestedAuthors = emptyList(),
        recentQueries = emptyList(),
        query = query,
      )
    } else if (currentState is BookSearchViewState.SearchResults) {
      _viewState.value = currentState.copy(query = query)
    } else {
      _viewState.value = BookSearchViewState.EmptySearch(
        suggestedAuthors = emptyList(),
        recentQueries = emptyList(),
        query = query,
      )
    }
  }

  // Explicitly trigger search execution
  fun executeSearch(query: String) {
    if (query.isBlank()) return

    viewModelScope.launch {
      // Start with loading state for discovery results
      val searchResults = BookSearchViewState.SearchResults(
        books = emptyList(),
        layoutMode = layoutMode,
        query = query,
        discoveryResults = DiscoverySearchState.Loading,
      )
      _viewState.value = searchResults

      // Perform the search
      val (books, discoveryFlow) = bookSearch.search(query)

      // Update with local results
      _viewState.update { state ->
        if (state is BookSearchViewState.SearchResults) {
          state.copy(
            books = books.map { it.toViewState() },
          )
        } else {
          state
        }
      }

      // Collect discovery results asynchronously
      discoveryFlow.collectLatest { result ->
        result.fold(
          onSuccess = { discoveryResults ->
            _viewState.update { state ->
              if (state is BookSearchViewState.SearchResults) {
                state.copy(
                  discoveryResults = DiscoverySearchState.Success(discoveryResults),
                )
              } else {
                state
              }
            }
          },
          onFailure = { error ->
            _viewState.update { state ->
              if (state is BookSearchViewState.SearchResults) {
                state.copy(
                  discoveryResults = DiscoverySearchState.Error(
                    error as? voice.search.error.SearchError
                      ?: voice.search.error.SearchError.UnknownError(error.message ?: "Unknown error", error),
                  ),
                )
              } else {
                state
              }
            }
          },
        )
      }
    }
  }

  // For backward compatibility - now just calls executeSearch
  fun search(query: String) {
    executeSearch(query)
  }

  fun retryDiscoverySearch() {
    viewModelScope.launch {
      val currentState = _viewState.value
      if (currentState is BookSearchViewState.SearchResults) {
        // Set discovery results back to loading state
        _viewState.update { state ->
          if (state is BookSearchViewState.SearchResults) {
            state.copy(discoveryResults = DiscoverySearchState.Loading)
          } else {
            state
          }
        }

        // Retry the discovery search
        val (_, discoveryFlow) = bookSearch.search(currentState.query)

        // Process the results
        discoveryFlow.collectLatest { result ->
          result.fold(
            onSuccess = { discoveryResults ->
              _viewState.update { state ->
                if (state is BookSearchViewState.SearchResults) {
                  state.copy(
                    discoveryResults = DiscoverySearchState.Success(discoveryResults),
                  )
                } else {
                  state
                }
              }
            },
            onFailure = { error ->
              _viewState.update { state ->
                if (state is BookSearchViewState.SearchResults) {
                  state.copy(
                    discoveryResults = DiscoverySearchState.Error(
                      error as? voice.search.error.SearchError
                        ?: voice.search.error.SearchError.UnknownError(error.message ?: "Unknown error", error),
                    ),
                  )
                } else {
                  state
                }
              }
            },
          )
        }
      }
    }
  }

  private fun Book.toViewState(): BookOverviewItemViewState {
    return BookOverviewItemViewState(
      name = content.name,
      author = content.author,
      cover = content.cover?.let { ImmutableFile(it) },
      progress = calculateProgress(),
      id = id,
      remainingTime = DateUtils.formatElapsedTime((duration - position) / 1000),
    )
  }

  private fun Book.calculateProgress(): Float {
    val progress = position.toFloat() / duration.toFloat()
    if (progress < 0F) {
      Logger.w("Couldn't determine progress for book=$this")
    }
    return progress.coerceIn(0F, 1F)
  }

  @AssistedFactory
  interface Factory {
    fun create(
      initialQuery: String,
      layoutMode: BookOverviewLayoutMode,
    ): BookSearchViewModel
  }
}
