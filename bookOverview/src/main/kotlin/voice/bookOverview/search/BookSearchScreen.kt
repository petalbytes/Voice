package voice.bookOverview.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.bookOverview.overview.BookOverviewLayoutMode
import voice.bookOverview.views.GridBook
import voice.bookOverview.views.ListBookRow
import voice.bookOverview.views.gridColumnCount
import voice.common.BookId
import voice.common.compose.plus
import voice.search.error.SearchError
import voice.search.repository.DiscoveryResult
import voice.strings.R as StringsR

@Composable
fun BookSearchScreen(
  viewModel: BookSearchViewModel,
  contentPadding: PaddingValues,
  onQueryChange: (String) -> Unit,
  onBookClick: (BookId) -> Unit,
  onDiscoveryResultClick: (DiscoveryResult) -> Unit
) {
  val viewState = viewModel.viewState.collectAsState().value

  BookSearchContent(
    viewState = viewState,
    contentPadding = contentPadding,
    onQueryChange = onQueryChange,
    onBookClick = onBookClick,
    onDiscoveryResultClick = onDiscoveryResultClick,
    onRetryDiscoverySearch = viewModel::retryDiscoverySearch
  )
}

@Composable
internal fun BookSearchContent(
  viewState: BookSearchViewState,
  contentPadding: PaddingValues,
  onQueryChange: (String) -> Unit,
  onBookClick: (BookId) -> Unit,
  onDiscoveryResultClick: (DiscoveryResult) -> Unit = {},
  onRetryDiscoverySearch: () -> Unit = {},
) {
  when (viewState) {
    is BookSearchViewState.EmptySearch -> {
      LazyColumn(contentPadding = contentPadding) {
        item {
          Spacer(modifier = Modifier.size(16.dp))
        }
        items(viewState.recentQueries) { query ->
          ListItem(
            modifier = Modifier.clickable { onQueryChange(query) },
            headlineContent = { Text(query) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
              Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = stringResource(id = StringsR.string.cover_search_icon_recent),
              )
            },
          )
        }
        items(viewState.suggestedAuthors) { author ->
          ListItem(
            modifier = Modifier.clickable { onQueryChange(author) },
            headlineContent = { Text(author) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
              Icon(
                imageVector = Icons.Outlined.SentimentSatisfied,
                contentDescription = stringResource(id = StringsR.string.cover_search_author),
              )
            },
          )
        }
      }
    }
    is BookSearchViewState.SearchResults -> {
      Column {
        // Discovery results section - now placed at the top
        Text(
          text = "Discovery Results",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when (val discoveryState = viewState.discoveryResults) {
          is DiscoverySearchState.Loading -> {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              contentAlignment = Alignment.Center
            ) {
              CircularProgressIndicator()
            }
          }
          is DiscoverySearchState.Success -> {
            if (discoveryState.results.isEmpty()) {
              Text(
                text = "No online results found",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
              )
            } else {
              when (viewState.layoutMode) {
                BookOverviewLayoutMode.List -> {
                  LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    items(discoveryState.results) { result ->
                      DiscoveryResultRow(
                        result = result,
                        onClick = { onDiscoveryResultClick(result) }
                      )
                    }
                  }
                }
                BookOverviewLayoutMode.Grid -> {
                  LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumnCount()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    content = {
                      items(discoveryState.results) { result ->
                        DiscoveryResultGridItem(
                          result = result,
                          onClick = { onDiscoveryResultClick(result) }
                        )
                      }
                    }
                  )
                }
              }
            }
          }
          is DiscoverySearchState.Error -> {
            DiscoveryErrorDisplay(
              error = discoveryState.error,
              onRetry = onRetryDiscoverySearch
            )
          }
        }

        // Local results section - now moved to the bottom and commented out
        // We might add this functionality back later
        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Local search results - commented out but kept for later use
        /*
        Text(
          text = "Local Results",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (viewState.books.isEmpty()) {
          Text(
            text = "No local results found",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
          )
        } else {
          when (viewState.layoutMode) {
            BookOverviewLayoutMode.List -> {
              LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = {
                  items(viewState.books) { book ->
                    ListBookRow(
                      book = book,
                      onBookClick = onBookClick,
                      onBookLongClick = onBookClick,
                    )
                  }
                },
              )
            }
            BookOverviewLayoutMode.Grid -> {
              LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumnCount()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                content = {
                  items(viewState.books) { book ->
                    GridBook(
                      book = book,
                      onBookClick = onBookClick,
                      onBookLongClick = onBookClick,
                    )
                  }
                },
              )
            }
          }
        }
        */
      }
    }
  }
}

@Composable
private fun DiscoveryResultRow(
  result: DiscoveryResult,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 4.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Cover image
      AsyncImage(
        model = result.coverImageUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .size(60.dp)
          .clip(RoundedCornerShape(4.dp)),
        fallback = painterResource(id = voice.common.R.drawable.album_art),
        error = painterResource(id = voice.common.R.drawable.album_art)
      )

      Spacer(modifier = Modifier.width(16.dp))

      // Book info
      Column(
        modifier = Modifier.weight(1f)
      ) {
        Text(
          text = result.title,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = result.authors.joinToString(", "),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        if (result.categories.isNotEmpty()) {
          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = result.categories.take(2).joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

@Composable
private fun DiscoveryResultGridItem(
  result: DiscoveryResult,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
  ) {
    Column {
      AsyncImage(
        modifier = Modifier
          .aspectRatio(1f)
          .padding(start = 8.dp, end = 8.dp, top = 8.dp)
          .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop,
        model = result.coverImageUrl,
        placeholder = painterResource(id = voice.common.R.drawable.album_art),
        error = painterResource(id = voice.common.R.drawable.album_art),
        contentDescription = null,
      )
      Text(
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
        text = result.title,
        maxLines = 3,
        style = MaterialTheme.typography.bodyMedium,
      )
      Text(
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        text = result.authors.firstOrNull() ?: "",
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

@Composable
private fun DiscoveryErrorDisplay(
  error: SearchError,
  onRetry: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = when (error) {
        is SearchError.NetworkError -> "Network error: ${error.message}"
        is SearchError.ApiError -> "Server error (${error.code})"
        is SearchError.ParseError -> "Error processing results"
        is SearchError.UnknownError -> "An unexpected error occurred"
      },
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.error
    )

    Spacer(modifier = Modifier.size(8.dp))

    Button(onClick = onRetry) {
      Text("Retry")
    }
  }
}
