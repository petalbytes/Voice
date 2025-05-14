package voice.bookOverview.views.topbar

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import voice.bookOverview.search.BookSearchContent
import voice.bookOverview.search.BookSearchViewState
import voice.bookOverview.views.BookFolderIcon
import voice.bookOverview.views.MigrateIcon
import voice.bookOverview.views.SettingsIcon
import voice.common.BookId
import voice.search.repository.DiscoveryResult
import voice.strings.R

@Composable
internal fun ColumnScope.BookOverviewSearchBar(
  horizontalPadding: Dp,
  onQueryChange: (String) -> Unit,
  onActiveChange: (Boolean) -> Unit,
  onBookMigrationClick: () -> Unit,
  onBoomMigrationHelperConfirmClick: () -> Unit,
  onBookFolderClick: () -> Unit,
  onSettingsClick: () -> Unit,
  onSearchBookClick: (BookId) -> Unit,
  onSearchButtonClick: () -> Unit,
  onDiscoveryResultClick: (DiscoveryResult) -> Unit = {},
  onRetryDiscoverySearch: () -> Unit = {},
  searchActive: Boolean,
  showMigrateIcon: Boolean,
  showMigrateHint: Boolean,
  showAddBookHint: Boolean,
  searchViewState: BookSearchViewState,
) {
  SearchBar(
    inputField = {
      Row(modifier = Modifier.fillMaxWidth()) {
        SearchBarDefaults.InputField(
          query = if (searchActive) {
            searchViewState.query
          } else {
            ""
          },
          onQueryChange = onQueryChange,
          onSearch = { onSearchButtonClick() },
          expanded = searchActive,
          onExpandedChange = onActiveChange,
          modifier = Modifier.weight(1f),
          leadingIcon = {
            if (searchActive) {
              IconButton(onClick = { onActiveChange(false) }) {
                Icon(
                  imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                  contentDescription = stringResource(id = R.string.close),
                )
              }
            } else {
              Row {
                if (showMigrateIcon) {
                  MigrateIcon(
                    onClick = onBookMigrationClick,
                    withHint = showMigrateHint,
                    onHintClick = onBoomMigrationHelperConfirmClick,
                  )
                }
                BookFolderIcon(withHint = showAddBookHint, onClick = onBookFolderClick)
                SettingsIcon(onSettingsClick)
              }
            }
          },
          trailingIcon = {
            if (!searchActive) {
              IconButton(onClick = { onActiveChange(true) }) {
                Icon(
                  imageVector = Icons.Outlined.Search,
                  contentDescription = stringResource(id = R.string.search_hint),
                )
              }
            }
          },
        )
        
        if (searchActive) {
          Spacer(modifier = Modifier.width(8.dp))
          
          IconButton(
            onClick = onSearchButtonClick,
            modifier = Modifier.padding(end = 8.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.Search,
              contentDescription = stringResource(id = R.string.search_hint),
            )
          }
        }
      }
    },
    expanded = searchActive,
    onExpandedChange = onActiveChange,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = horizontalPadding),
    content = {
      BookSearchContent(
        viewState = searchViewState,
        contentPadding = PaddingValues(),
        onQueryChange = onQueryChange,
        onBookClick = onSearchBookClick,
        onDiscoveryResultClick = onDiscoveryResultClick,
        onRetryDiscoverySearch = onRetryDiscoverySearch,
      )
    },
  )
}
