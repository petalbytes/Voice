package voice.settings.views

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import voice.common.compose.rememberScoped
import voice.common.navigation.Navigator
import voice.common.rootComponentAs
import voice.documentfile.CachedDocumentFileFactory
import voice.strings.R as StringsR
import voice.settings.di.BorrowLocationComponent

@Composable
fun BorrowLocationDialog(
  currentLocation: String,
  onLocationConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val documentFileFactory = rememberScoped { 
    rootComponentAs<BorrowLocationComponent>().cachedDocumentFileFactory 
  }
  
  // DocumentTree picker launcher
  val folderPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
    onResult = { uri ->
      if (uri != null) {
        // Persist permission for future use
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        
        // Get folder name for display
        val documentFile = DocumentFile.fromTreeUri(context, uri)
        if (documentFile != null) {
          onLocationConfirm(uri.toString())
          onDismiss() // Automatically dismiss the dialog after selection
        }
      }
    }
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(stringResource(StringsR.string.pref_borrow_location))
    },
    text = {
      Text(stringResource(StringsR.string.pref_borrow_location_explanation))
    },
    confirmButton = {
      Button(
        onClick = { folderPicker.launch(null) }
      ) {
        Text(stringResource(StringsR.string.select_folder))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(StringsR.string.dialog_cancel))
      }
    },
  )
} 