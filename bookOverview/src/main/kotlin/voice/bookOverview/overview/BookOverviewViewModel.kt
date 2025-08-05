package voice.bookOverview.overview

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import voice.app.scanner.DeviceHasStoragePermissionBug
import voice.app.scanner.MediaScanTrigger
import voice.bookOverview.BookMigrationExplanationQualifier
import voice.bookOverview.BookMigrationExplanationShown
import voice.bookOverview.di.BookOverviewScope
import voice.bookOverview.search.BookSearchViewModel
import voice.bookOverview.search.BookSearchViewState
import voice.common.BookId
import voice.common.grid.GridCount
import voice.common.grid.GridMode
import voice.common.navigation.Destination
import voice.common.navigation.Navigator
import voice.common.pref.CurrentBook
import voice.common.pref.PrefKeys
import voice.data.repo.BookContentRepo
import voice.data.repo.BookRepository
import voice.data.repo.internals.dao.LegacyBookDao
import voice.data.repo.internals.dao.RecentBookSearchDao
import voice.playback.PlayerController
import voice.playback.playstate.PlayStateManager
import voice.pref.Pref
import voice.search.BookSearch
import voice.search.repository.DiscoveryResult
import javax.inject.Inject
import javax.inject.Named

@BookOverviewScope
class BookOverviewViewModel
@Inject
constructor(
  private val repo: BookRepository,
  private val mediaScanner: MediaScanTrigger,
  private val playStateManager: PlayStateManager,
  private val playerController: PlayerController,
  @CurrentBook
  private val currentBookDataStore: DataStore<BookId?>,
  @Named(PrefKeys.GRID_MODE)
  private val gridModePref: Pref<GridMode>,
  private val gridCount: GridCount,
  @BookMigrationExplanationQualifier
  private val bookMigrationExplanationShown: BookMigrationExplanationShown,
  private val legacyBookDao: LegacyBookDao,
  private val navigator: Navigator,
  private val recentBookSearchDao: RecentBookSearchDao,
  private val search: BookSearch,
  private val contentRepo: BookContentRepo,
  private val deviceHasStoragePermissionBug: DeviceHasStoragePermissionBug,
  private val bookSearchViewModelFactory: BookSearchViewModel.Factory,
) {

  private val scope = MainScope()
  private var searchActive by mutableStateOf(false)
  private var query by mutableStateOf("")
  private var bookSearchViewModel: BookSearchViewModel? = null
  private val _searchViewState = MutableStateFlow<BookSearchViewState>(
    BookSearchViewState.EmptySearch(
      suggestedAuthors = emptyList(),
      recentQueries = emptyList(),
      query = "",
    ),
  )

  fun attach() {
    mediaScanner.scan()
  }

  @Composable
  internal fun state(): BookOverviewViewState {
    val playState = remember { playStateManager.flow }
      .collectAsState(initial = PlayStateManager.PlayState.Paused).value
    val hasStoragePermissionBug = remember { deviceHasStoragePermissionBug.hasBug }
      .collectAsState().value
    val books = remember { repo.flow() }
      .collectAsState(initial = emptyList()).value
    val currentBookId = remember { currentBookDataStore.data }
      .collectAsState(initial = null).value
    val scannerActive = remember { mediaScanner.scannerActive }
      .collectAsState(initial = false).value
    val gridMode = remember { gridModePref.flow }
      .collectAsState(initial = null).value
      ?: return BookOverviewViewState.Loading
    val bookMigrationExplanationShown = remember { bookMigrationExplanationShown.data }
      .collectAsState(initial = null).value
      ?: return BookOverviewViewState.Loading

    val hasLegacyBooks = produceState<Boolean?>(initialValue = null) {
      value = legacyBookDao.bookMetaDataCount() != 0
    }.value ?: return BookOverviewViewState.Loading

    val noBooks = !scannerActive && books.isEmpty()
    val showMigrateHint = hasLegacyBooks && !bookMigrationExplanationShown

    val layoutMode = when (gridMode) {
      GridMode.LIST -> BookOverviewLayoutMode.List
      GridMode.GRID -> BookOverviewLayoutMode.Grid
      GridMode.FOLLOW_DEVICE -> if (gridCount.useGridAsDefault()) {
        BookOverviewLayoutMode.Grid
      } else {
        BookOverviewLayoutMode.List
      }
    }

    // Get search view state
    val searchViewState = if (searchActive && bookSearchViewModel != null) {
      remember { bookSearchViewModel!!.viewState }
        .collectAsState().value
    } else {
      _searchViewState.collectAsState().value
    }

    return BookOverviewViewState(
      layoutMode = layoutMode,
      books = books
        .groupBy {
          it.category
        }
        .mapValues { (category, books) ->
          books
            .sortedWith(category.comparator)
            .map { book ->
              book.toItemViewState()
            }
        }
        .toSortedMap()
        .toImmutableMap(),
      playButtonState = if (playState == PlayStateManager.PlayState.Playing) {
        BookOverviewViewState.PlayButtonState.Playing
      } else {
        BookOverviewViewState.PlayButtonState.Paused
      }.takeIf { currentBookId != null },
      showAddBookHint = if (showMigrateHint || hasStoragePermissionBug) {
        false
      } else {
        noBooks
      },
      showMigrateIcon = hasLegacyBooks,
      showMigrateHint = showMigrateHint,
      showSearchIcon = books.isNotEmpty(),
      isLoading = scannerActive,
      searchActive = searchActive,
      searchViewState = searchViewState,
      showStoragePermissionBugCard = hasStoragePermissionBug,
    )
  }

  fun onSettingsClick() {
    navigator.goTo(Destination.Settings)
  }

  fun onBookClick(id: BookId) {
    navigator.goTo(Destination.Playback(id))
  }

  fun onBookFolderClick() {
    navigator.goTo(Destination.FolderPicker)
  }

  fun onBookMigrationClick() {
    navigator.goTo(Destination.Migration)
  }

  fun onSearchActiveChange(active: Boolean) {
    if (active && !searchActive) {
      query = ""

      // Get the current layout mode from the same logic used in state()
      val layoutMode = when {
        gridModePref.value == GridMode.LIST -> BookOverviewLayoutMode.List
        gridModePref.value == GridMode.GRID -> BookOverviewLayoutMode.Grid
        gridModePref.value == GridMode.FOLLOW_DEVICE && gridCount.useGridAsDefault() -> BookOverviewLayoutMode.Grid
        else -> BookOverviewLayoutMode.List
      }

      // Create a new BookSearchViewModel when search is activated
      bookSearchViewModel = bookSearchViewModelFactory.create(
        initialQuery = "",
        layoutMode = layoutMode,
      )

      // Listen to search view model changes
      scope.launch {
        bookSearchViewModel?.viewState?.collectLatest { state ->
          _searchViewState.value = state
        }
      }
    }
    this.searchActive = active
  }

  fun onSearchQueryChange(query: String) {
    this.query = query
    bookSearchViewModel?.let { viewModel ->
      viewModel.updateQuery(query)
    }
  }

  fun onSearchButtonClick() {
    bookSearchViewModel?.let { viewModel ->
      viewModel.executeSearch(query)
    }
  }

  fun onSearchBookClick(id: BookId) {
    if (query.isNotBlank()) {
      scope.launch {
        recentBookSearchDao.add(query.trim())
      }
    }
    searchActive = false
    navigator.goTo(Destination.Playback(id))
  }

  fun onDiscoveryResultClick(result: DiscoveryResult) {
    // Navigate to the book details screen
    navigator.goTo(
      Destination.BookDetails(
        id = result.id,
        title = result.title,
        authors = result.authors,
        coverImageUrl = result.coverImageUrl,
        categories = result.categories,
        language = result.language,
        mediaDetailsUrl = result.mediaDetailsUrl,
      ),
    )
  }

  fun onRetryDiscoverySearch() {
    bookSearchViewModel?.retryDiscoverySearch()
  }

  fun onBoomMigrationHelperConfirmClick() {
    scope.launch {
      bookMigrationExplanationShown.updateData { true }
    }
  }

  fun playPause() {
    playerController.playPause()
  }

  fun onPermissionBugCardClick() {
    if (Build.VERSION.SDK_INT >= 30) {
      navigator.goTo(
        Destination.Activity(
          Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            .setData("package:com.android.externalstorage".toUri()),
        ),
      )
    }
  }
}
