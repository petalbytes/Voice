package voice.settings.di

import com.squareup.anvil.annotations.ContributesTo
import voice.common.AppScope
import voice.documentfile.CachedDocumentFileFactory

@ContributesTo(AppScope::class)
interface BorrowLocationComponent {
  val cachedDocumentFileFactory: CachedDocumentFileFactory
}
