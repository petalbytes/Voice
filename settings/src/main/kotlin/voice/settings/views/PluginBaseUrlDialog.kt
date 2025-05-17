package voice.settings.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import voice.strings.R as StringsR

@Composable
fun PluginBaseUrlDialog(
  currentUrl: String,
  onUrlConfirm: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var url by remember { mutableStateOf(currentUrl) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(stringResource(StringsR.string.pref_plugin_base_url))
    },
    text = {
      Column {
        OutlinedTextField(
          value = url,
          onValueChange = { url = it },
          label = { Text(stringResource(StringsR.string.pref_plugin_base_url_hint)) },
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          onUrlConfirm(url)
          onDismiss()
        }
      ) {
        Text(stringResource(StringsR.string.dialog_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(StringsR.string.dialog_cancel))
      }
    }
  )
} 