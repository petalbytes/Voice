package voice.bookOverview.streaming

import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import voice.bookOverview.bottomSheet.BottomSheetItem
import voice.bookOverview.bottomSheet.BottomSheetItemViewModel
import voice.bookOverview.di.BookOverviewScope
import voice.common.BookId
import voice.common.navigation.Navigator
import voice.data.repo.BookRepository
import javax.inject.Inject

@BookOverviewScope
@ContributesMultibinding(
  scope = BookOverviewScope::class,
  boundType = BottomSheetItemViewModel::class,
)
class StreamingOptionsViewModel
@Inject
constructor(
  private val repo: BookRepository,
  private val navigator: Navigator
) : BottomSheetItemViewModel {

  private val scope = MainScope()

  override suspend fun items(bookId: BookId): List<BottomSheetItem> {
    // For now, no books in overview are streamed
    return emptyList()
  }

  override suspend fun onItemClick(
    bookId: BookId,
    item: BottomSheetItem,
  ) {
    // Only process streaming-specific actions
    when (item) {
      BottomSheetItem.DownloadForOffline -> downloadForOffline(bookId)
      BottomSheetItem.ManageSubscription -> openSubscriptionManagement()
      else -> { /* Ignore other actions */ }
    }
  }
  
  private fun downloadForOffline(bookId: BookId) {
    // TODO: Implement download functionality once streaming is implemented
    scope.launch {
      // This would typically:
      // 1. Start a download service or worker
      // 2. Show a notification or progress
    }
  }
  
  private fun openSubscriptionManagement() {
    // TODO: Navigate to subscription management screen once implemented
    // navigator.goTo(Destination.ManageSubscription)
  }
} 