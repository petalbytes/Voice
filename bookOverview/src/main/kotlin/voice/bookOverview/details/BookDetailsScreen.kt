package voice.bookOverview.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.common.compose.VoiceTheme
import voice.search.error.SearchError
import voice.search.repository.AudioSource
import voice.search.repository.DiscoveryResult

@Composable
fun BookDetailsScreen(
  viewModel: BookDetailsViewModel,
  onBackClick: () -> Unit
) {
  val viewState by viewModel.state.collectAsState()

  BookDetailsContent(
    viewState = viewState,
    onBackClick = onBackClick,
    onRetry = { viewModel.loadBookDetails() },
    onBorrowBook = { borrowLink -> viewModel.borrowBook(borrowLink) },
    onCancelBorrow = { viewModel.cancelBorrowBook() }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookDetailsContent(
  viewState: BookDetailsViewState,
  onBackClick: () -> Unit,
  onRetry: () -> Unit,
  onBorrowBook: (String) -> Unit,
  onCancelBorrow: () -> Unit
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = "Book Details") },
        navigationIcon = {
          IconButton(onClick = onBackClick) {
            Icon(
              imageVector = Icons.Default.ArrowBack,
              contentDescription = "Back"
            )
          }
        }
      )
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      when (viewState) {
        is BookDetailsViewState.Loading -> {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator()
          }
        }
        is BookDetailsViewState.Success -> {
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .padding(16.dp)
          ) {
            item {
              BookInfoHeader(
                discoveryResult = viewState.discoveryResult,
                description = viewState.details.description
              )
            }

            item {
              Spacer(modifier = Modifier.height(24.dp))
              Text(
                text = "Available Sources",
                style = MaterialTheme.typography.titleLarge
              )
              Spacer(modifier = Modifier.height(8.dp))
            }

            items(viewState.details.availableSources) { source ->
              AudioSourceItem(
                source = source,
                onBorrowClick = { source.borrowLink?.let(onBorrowBook) },
                onCancelBorrow = onCancelBorrow,
                borrowProgress = viewState.borrowProgress
              )
              Spacer(modifier = Modifier.height(8.dp))
            }
          }
        }
        is BookDetailsViewState.Error -> {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Text(
              text = "Error loading book details",
              style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = when (val error = viewState.error) {
                is SearchError.NetworkError -> "Network error: ${error.message}"
                is SearchError.ApiError -> "API error: ${error.message}"
                is SearchError.ParseError -> "Error parsing response: ${error.message}"
                is SearchError.UnknownError -> "Unknown error: ${error.message}"
              },
              style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onRetry) {
              Text("Retry")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BookInfoHeader(
  discoveryResult: DiscoveryResult,
  description: String
) {
  Column {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.Top
    ) {
      // Cover image
      AsyncImage(
        model = discoveryResult.coverImageUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .width(120.dp)
          .height(180.dp)
          .clip(RoundedCornerShape(8.dp)),
        fallback = painterResource(id = voice.common.R.drawable.album_art),
        error = painterResource(id = voice.common.R.drawable.album_art)
      )

      Spacer(modifier = Modifier.width(16.dp))

      // Book info
      Column {
        Text(
          text = discoveryResult.title,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "By ${discoveryResult.authors.joinToString(", ")}",
          style = MaterialTheme.typography.bodyLarge
        )

        if (discoveryResult.categories.isNotEmpty()) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = discoveryResult.categories.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = discoveryResult.language,
          style = MaterialTheme.typography.bodySmall
        )
      }
    }

    Spacer(modifier = Modifier.height(16.dp))
    Divider()
    Spacer(modifier = Modifier.height(16.dp))

    // Description
    Text(
      text = "Description",
      style = MaterialTheme.typography.titleLarge
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = description.ifEmpty { "No description available" },
      style = MaterialTheme.typography.bodyMedium
    )
  }
}

@Composable
private fun AudioSourceItem(
  source: AudioSource,
  onBorrowClick: () -> Unit,
  onCancelBorrow: () -> Unit,
  borrowProgress: BorrowProgress? = null
) {
  Card(
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Read by: ${source.readBy}",
            style = MaterialTheme.typography.titleMedium
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = "File size: ${source.fileSize}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (source.borrowLink != null) {
            // Show button in disabled (greyed out) state during active borrow/extraction
            val isBorrowInProgress = borrowProgress is BorrowProgress.Starting ||
                                       borrowProgress is BorrowProgress.Borrowing ||
                                       borrowProgress is BorrowProgress.Extracting

            Button(
              onClick = onBorrowClick,
              enabled = borrowProgress == null || borrowProgress is BorrowProgress.Error
            ) {
              Text("Borrow")
            }

            when (val progress = borrowProgress) {
              is BorrowProgress.Starting -> {
                // Show indeterminate progress indicator for Starting state
                CircularProgressIndicator(
                  modifier = Modifier.size(24.dp)
                )
              }
              is BorrowProgress.Borrowing -> {
                Box(
                  contentAlignment = Alignment.Center
                ) {
                  CircularProgressIndicator(
                    progress = progress.progress / 100f,
                    modifier = Modifier.size(24.dp)
                  )

                  // X button to cancel borrow
                  IconButton(
                    onClick = onCancelBorrow,
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Close,
                      contentDescription = "Cancel borrow",
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }
              is BorrowProgress.Extracting -> {
                Box(
                  contentAlignment = Alignment.Center
                ) {
                  CircularProgressIndicator(
                    progress = progress.progress / 100f,
                    modifier = Modifier.size(24.dp)
                  )

                  // X button to cancel extraction
                  IconButton(
                    onClick = onCancelBorrow,
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Close,
                      contentDescription = "Cancel extraction",
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }
              is BorrowProgress.Completed -> {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = "Borrow completed",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(24.dp)
                )
              }
              is BorrowProgress.Error -> {
                Icon(
                  imageVector = Icons.Default.Error,
                  contentDescription = "Borrow error",
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(24.dp)
                )
              }
              null -> { /* No progress indicator shown */ }
            }
          }
        }
      }
    }
  }
}
