package voice.bookOverview.di

import com.squareup.anvil.annotations.ContributesSubcomponent
import com.squareup.anvil.annotations.ContributesTo
import voice.bookOverview.bottomSheet.BottomSheetViewModel
import voice.bookOverview.deleteBook.DeleteBookViewModel
import voice.bookOverview.details.BookDetailsController
import voice.bookOverview.details.BookDetailsViewModel
import voice.bookOverview.editTitle.EditBookTitleViewModel
import voice.bookOverview.fileCover.FileCoverViewModel
import voice.bookOverview.overview.BookOverviewViewModel
import voice.bookOverview.search.BookSearchViewModel
import voice.common.AppScope
import javax.inject.Scope

@Scope
annotation class BookOverviewScope

@ContributesSubcomponent(scope = BookOverviewScope::class, parentScope = AppScope::class)
@BookOverviewScope
interface BookOverviewComponent {
  val bookOverviewViewModel: BookOverviewViewModel
  val editBookTitleViewModel: EditBookTitleViewModel
  val bottomSheetViewModel: BottomSheetViewModel
  val deleteBookViewModel: DeleteBookViewModel
  val fileCoverViewModel: FileCoverViewModel
  val bookSearchViewModelFactory: BookSearchViewModel.Factory
  val bookDetailsViewModelFactory: BookDetailsViewModel.Factory
  
  fun inject(controller: BookDetailsController)

  @ContributesSubcomponent.Factory
  interface Factory {
    fun create(): BookOverviewComponent

    @ContributesTo(AppScope::class)
    interface Provider {
      val bookOverviewComponentProviderFactory: Factory
    }
  }
}
