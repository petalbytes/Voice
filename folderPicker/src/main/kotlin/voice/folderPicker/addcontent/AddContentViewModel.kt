package voice.folderPicker.addcontent

import android.net.Uri
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import voice.common.navigation.Destination
import voice.common.navigation.Destination.AddContent.Mode
import voice.common.navigation.Destination.OnboardingCompletion
import voice.common.navigation.Destination.SelectFolderType
import voice.common.navigation.Navigator
import voice.common.pref.BorrowLocation
import voice.data.folders.AudiobookFolders
import voice.data.folders.FolderType
import voice.folderPicker.folderPicker.FileTypeSelection
import voice.pref.Pref
import com.kiwi.navigationcompose.typed.navigate as typedNavigate
import voice.common.navigation.Destination.SelectFolderType.Mode as SelectFolderTypeMode

class AddContentViewModel
@AssistedInject constructor(
  private val audiobookFolders: AudiobookFolders,
  private val navigator: Navigator,
  @BorrowLocation
  private val borrowLocationPref: Pref<String>,
  @Assisted
  private val mode: Mode,
) {

  internal fun add(
    uri: Uri,
    type: FileTypeSelection,
  ) {
    when (type) {
      FileTypeSelection.File -> {
        audiobookFolders.add(uri, FolderType.SingleFile)
        when (mode) {
          Mode.Default -> {
            navigator.execute { navController ->
              navController.typedNavigate(Destination.BookOverview) {
                popUpTo(0)
              }
            }
          }
          Mode.Onboarding -> {
            navigator.goTo(OnboardingCompletion)
          }
        }
      }
      FileTypeSelection.Folder -> {
        when (mode) {
          Mode.Default -> {
            navigator.goTo(
              SelectFolderType(
                uri = uri,
                mode = SelectFolderTypeMode.Default,
              ),
            )
          }
          Mode.Onboarding -> {
            // For onboarding, automatically use Top-Level Book Mode and set borrow location
            audiobookFolders.add(uri, FolderType.Root)
            borrowLocationPref.value = uri.toString()
            navigator.goTo(OnboardingCompletion)
          }
        }
      }
    }
  }

  internal fun back() {
    navigator.goBack()
  }

  @AssistedFactory
  interface Factory {
    fun create(mode: Mode): AddContentViewModel
  }
}
