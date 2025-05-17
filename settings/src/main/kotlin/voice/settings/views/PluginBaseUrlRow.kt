package voice.settings.views

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import voice.strings.R as StringsR

@Composable
fun PluginBaseUrlRow(pluginBaseUrl: String, onClick: () -> Unit) {
  ListItem(
    modifier = Modifier.clickable(onClick = onClick),
    leadingContent = {
      Icon(
        imageVector = Icons.Outlined.Api,
        contentDescription = stringResource(StringsR.string.pref_plugin_base_url)
      )
    },
    headlineContent = {
      Text(stringResource(StringsR.string.pref_plugin_base_url))
    },
    supportingContent = {
      Text(pluginBaseUrl)
    }
  )
} 