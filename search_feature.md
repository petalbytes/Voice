# Online Search Feature Implementation Guide

## Overview

This document outlines the architecture for implementing a dual-source search feature that:
1. Shows local results immediately (existing functionality)
2. Shows online discovery results asynchronously with loading indicators
3. Supports both grid and list layouts for both result types
4. Maintains proper separation between bookOverview and search modules

## Search Results Architecture

The search functionality implements a dual-source approach where results are displayed in the following order:

1. **Local Results (Immediate)**
   - Results from the user's local library displayed first
   - These maintain all existing functionality and metadata
   - Local results are immediately available without network latency
   - Local results appear instantly while discovery search is in progress

2. **Discovery Results (Asynchronous)**
   - Displayed below local results with clear visual separation
   - Each discovery result represents an audiobook (ebook support will be added later)
   - Discovery search runs asynchronously in parallel with local search
   - A loading indicator is shown while fetching discovery results
   - The UI updates automatically when discovery results arrive
   - If discovery search fails, local results remain visible with an error message for discovery section

This approach ensures:
- Users have instant access to their local content
- The UI remains responsive during search
- Clear visual separation between local and discovery results
- Proper error handling when discovery search fails

## Module Distribution

### Search Module
- Core search functionality that returns results from both sources
- Network communication layer for discovery search
- Domain models for search results
- Error handling for discovery search

### BookOverview Module
- UI implementation for both local and discovery results
- ViewState management for both result types
- GridView and ListView implementations for both result types
- Loading indicators for discovery results
- Error display for discovery search failures

## Implementation Structure

### Search Module Changes

```
search/
├── src/main/kotlin/voice/search/
    ├── BookSearch.kt (modified to support both sources)
    ├── api/
    │   ├── DiscoverySearchApi.kt
    │   └── DiscoverySearchResponse.kt
    ├── domain/
    │   ├── SearchResult.kt
    │   └── DiscoveryResult.kt
    ├── repository/
    │   ├── SearchRepository.kt
    │   └── DiscoveryRepository.kt
    └── error/
        └── SearchError.kt
```

### BookOverview Module Changes

```
bookOverview/
├── src/main/kotlin/voice/bookOverview/
    ├── search/
    │   ├── BookSearchScreen.kt (modified to show both sources)
    │   ├── BookSearchViewModel.kt
    │   └── BookSearchViewState.kt (expanded to include discovery results)
    └── details/
        ├── BookDetailsScreen.kt
        ├── BookDetailsViewModel.kt
        └── BookDetailsViewState.kt
```

## Data Flow

1. User enters search query in BookSearchScreen
2. BookSearchViewModel calls BookSearch.search(query)
3. BookSearch initiates two parallel operations:
   - Local search via BookContentDao (existing functionality)
   - Discovery search via DiscoveryRepository (new functionality)
4. Local results are returned immediately and displayed
5. Discovery results are fetched asynchronously
6. BookSearchViewState is updated as results arrive
7. BookSearchScreen displays both result sets with appropriate loading indicators

## API Integration

The discovery search will connect to an API endpoint with the following format:

```
GET http://localhost:8080/search?q={search_term}
```

Example response:

```json
[
  {
    "title": "The 7 Habits of Highly Effective People",
    "authors": ["Stephen R. Covey"],
    "category": ["Self-help"],
    "language": "English",
    "keywords": ["7 Habits", "highly effective people"],
    "image_url": "http://ecx.images-amazon.com/images/I/519NZyFurHL._BO2,204,203,200_PIsitb-sticker-arrow-click,TopRight,35,-76_AA300_SH20_OU01_.jpg",
    "media_details": "http://localhost:8080/details?id=the-7-habits-of-highly-effective-people-stephen-r-covey"
  }
]
```

Example details response:

```json
{
    "description": "The 7 Habits of Highly Effective People...",
    "written_by": "Stephen R. Covey",
    "available_sources": [
        {
            "read_by": "Stephen R. Covey",
            "description": "13 hours 4 minutes, complete unabridged version",
            "file_size": "568.38 MBs",
            "m3u": "#EXTM3U\n#EXTINF:-1,Chapter 1\nhttp://..."
        },
        {
            "read_by": "John Doe",
            "description": "12 hours 45 minutes, professionally narrated",
            "file_size": "720.5 MBs",
            "m3u": "#EXTM3U\n#EXTINF:-1,Introduction\nhttp://..."
        },
        {
            "read_by": "Jane Smith",
            "description": "13 hours 10 minutes, community reading",
            "file_size": "490.2 MBs",
            "m3u": "#EXTM3U\n#EXTINF:-1,Intro\nhttp://..."
        }
    ]
}
```

## Implementation Details

### Modified BookSearch Class

The BookSearch class will be updated to handle both local and discovery searches:

```kotlin
class BookSearch
@Inject constructor(
  private val dao: BookContentDao,
  private val repo: BookRepository,
  private val discoveryRepository: DiscoveryRepository,
) {
  // Returns local results immediately, discovery results come through Flow
  suspend fun search(query: String): Pair<List<Book>, Flow<Result<List<DiscoveryResult>>>> {
    val localResults = searchLocal(query)
    val discoveryResultsFlow = discoveryRepository.search(query)
    return Pair(localResults, discoveryResultsFlow)
  }

  // Existing local search functionality
  private suspend fun searchLocal(query: String): List<Book> {
    return dao.search(
      buildString {
        append("\"")
        append('*')
        append(query.trim().replace("\"", "\"\""))
        append('*')
        append("\"")
      },
    ).mapNotNull { repo.get(it) }
  }
}
```

### Discovery Repository Implementation

```kotlin
class DiscoveryRepository @Inject constructor(
  private val api: DiscoverySearchApi,
  private val errorHandler: ErrorHandler
) {
  suspend fun search(query: String): Flow<Result<List<DiscoveryResult>>> = flow {
    try {
      val response = api.search(query)
      if (response.isSuccessful) {
        val results = response.body()?.map { DiscoveryResult.fromSearchResponse(it) }
          ?: emptyList()
        emit(Result.success(results))
      } else {
        val error = SearchError.ApiError(
          code = response.code(),
          message = response.errorBody()?.string() ?: "Unknown API error"
        )
        errorHandler.handleError(error)
        emit(Result.failure(error))
      }
    } catch (e: IOException) {
      val error = SearchError.NetworkError("Network error occurred", e)
      errorHandler.handleError(error)
      emit(Result.failure(error))
    } catch (e: Exception) {
      val error = SearchError.UnknownError("An unexpected error occurred", e)
      errorHandler.handleError(error)
      emit(Result.failure(error))
    }
  }
}
```

### BookSearchViewState

The view state will be expanded to handle both result types:

```kotlin
sealed class BookSearchViewState {
  data class EmptySearch(
    val recentQueries: List<String>,
    val suggestedAuthors: List<String>,
  ) : BookSearchViewState()

  data class SearchResults(
    val query: String,
    val layoutMode: BookOverviewLayoutMode,
    val localBooks: List<Book>,
    val discoveryResults: DiscoverySearchState,
  ) : BookSearchViewState()
}

sealed class DiscoverySearchState {
  object Loading : DiscoverySearchState()
  data class Success(val results: List<DiscoveryResult>) : DiscoverySearchState()
  data class Error(val error: SearchError) : DiscoverySearchState()
}
```

### Domain Models

```kotlin
data class DiscoveryResult(
  val id: String,  // Base64 encoded hash of the media_details id parameter
  // Basic info (always available from search)
  val title: String,
  val authors: List<String>,
  val coverImageUrl: String,
  val categories: List<String>,
  val language: String,
  val keywords: List<String>,
  val mediaDetailsUrl: String,
  
  // Detailed info (null until details are fetched)
  val description: String? = null,
  val writtenBy: String? = null,
  val availableSources: List<AudioSource>? = null
) {
  companion object {
    fun generateId(mediaDetailsUrl: String): String {
      val idParam = mediaDetailsUrl.substringAfter("id=")
      return Base64.getUrlEncoder().encodeToString(idParam.toByteArray())
        .replace("=", "")
    }

    fun fromSearchResponse(json: JSONObject): DiscoveryResult {
      val mediaDetailsUrl = json.getString("media_details")
      return DiscoveryResult(
        id = generateId(mediaDetailsUrl),
        title = json.getString("title"),
        authors = json.getJSONArray("authors").let { array ->
          List(array.length()) { i -> array.getString(i) }
        },
        coverImageUrl = json.getString("image_url"),
        categories = json.getJSONArray("category").let { array ->
          List(array.length()) { i -> array.getString(i) }
        },
        language = json.getString("language"),
        keywords = json.getJSONArray("keywords").let { array ->
          List(array.length()) { i -> array.getString(i) }
        },
        mediaDetailsUrl = mediaDetailsUrl
      )
    }

    fun withDetails(result: DiscoveryResult, detailsJson: JSONObject): DiscoveryResult {
      val sources = detailsJson.optJSONArray("available_sources")?.let { sourcesArray ->
        List(sourcesArray.length()) { i -> 
          AudioSource.fromJson(sourcesArray.getJSONObject(i))
        }
      }
      
      return result.copy(
        description = detailsJson.optString("description"),
        writtenBy = detailsJson.optString("written_by"),
        availableSources = sources
      )
    }
  }
}

data class AudioSource(
  val readBy: String,
  val description: String,
  val fileSize: String,
  val m3u: String? = null
) {
  companion object {
    fun fromJson(json: JSONObject): AudioSource {
      return AudioSource(
        readBy = json.getString("read_by"),
        description = json.getString("description"),
        fileSize = json.getString("file_size"),
        m3u = json.optString("m3u", null)
      )
    }
  }
}
```

## Media Import Manager

```kotlin
class MediaImportManager @Inject constructor(
  private val bookRepository: BookRepository,
  private val coverDownloader: CoverDownloader,
  private val httpClient: OkHttpClient
) {
  suspend fun importAudiobook(
    discoveryResult: DiscoveryResult, 
    selectedSource: AudioSource
  ): Result<BookId> {
    return try {
      // Download the cover image if available
      val coverFile = discoveryResult.coverImageUrl?.let { imageUrl ->
        coverDownloader.downloadCover(imageUrl)
      }
      
      // Download audio content from the m3u playlist
      val audioContent = selectedSource.m3u?.let { m3uContent ->
        downloadAudioFromM3U(m3uContent)
      } ?: return Result.failure(ImportError.MissingAudioSource)
      
      // Create a new book entity from the discovery result and downloaded content
      val bookId = bookRepository.addBook(
        title = discoveryResult.title,
        author = discoveryResult.authors.firstOrNull() ?: "Unknown",
        narrator = selectedSource.readBy,
        coverPath = coverFile?.path,
        audiobookPath = audioContent.path
      )
      
      Result.success(bookId)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
  
  private suspend fun downloadAudioFromM3U(m3uContent: String): AudioContent {
    // Implementation to download audio files from m3u playlist
    // and return local file information
    return AudioContent("/path/to/downloaded/audio.mp3")
  }
}

data class AudioContent(val path: String)

sealed class ImportError : Exception() {
  object MissingAudioSource : ImportError()
}
```

## Error Handling and Notifications

### Error Types

```kotlin
sealed class SearchError {
  data class NetworkError(val message: String, val cause: Throwable? = null) : SearchError()
  data class ApiError(val code: Int, val message: String) : SearchError()
  data class ParseError(val message: String, val cause: Throwable? = null) : SearchError()
  data class UnknownError(val message: String, val cause: Throwable? = null) : SearchError()
}
```

### Error Handler

```kotlin
class ErrorHandler @Inject constructor(
  private val logger: Logger,
  private val analytics: Analytics
) {
  fun handleError(error: SearchError) {
    when (error) {
      is SearchError.NetworkError -> {
        logger.e("Network error during search", error.cause)
        analytics.logEvent("search_network_error", mapOf(
          "message" to error.message
        ))
      }
      is SearchError.ApiError -> {
        logger.e("API error during search: ${error.code}", null)
        analytics.logEvent("search_api_error", mapOf(
          "code" to error.code.toString(),
          "message" to error.message
        ))
      }
      is SearchError.ParseError -> {
        logger.e("Parse error during search", error.cause)
        analytics.logEvent("search_parse_error", mapOf(
          "message" to error.message
        ))
      }
      is SearchError.UnknownError -> {
        logger.e("Unknown error during search", error.cause)
        analytics.logEvent("search_unknown_error", mapOf(
          "message" to error.message
        ))
      }
    }
  }
}
```

### Error Display in UI

```kotlin
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
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Button(onClick = onRetry) {
      Text("Retry")
    }
  }
}
```

## BookSearchScreen UI Updates

The BookSearchScreen will be updated to display both local and discovery results:

```kotlin
@Composable
internal fun BookSearchContent(
  viewState: BookSearchViewState,
  contentPadding: PaddingValues,
  onQueryChange: (String) -> Unit,
  onBookClick: (BookId) -> Unit,
  onDiscoveryResultClick: (DiscoveryResult) -> Unit,
  onRetryDiscoverySearch: () -> Unit
) {
  when (viewState) {
    is BookSearchViewState.EmptySearch -> {
      // Existing empty search UI with suggestions and history
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
        // Local results section
        Text(
          text = stringResource(id = StringsR.string.search_local_results),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        if (viewState.localBooks.isEmpty()) {
          Text(
            text = stringResource(id = StringsR.string.search_no_local_results),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
          )
        } else {
          when (viewState.layoutMode) {
            BookOverviewLayoutMode.List -> {
              LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier
                  .padding(horizontal = 8.dp)
                  .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = {
                  items(viewState.localBooks) { book ->
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
                modifier = Modifier.weight(1f, fill = false),
                content = {
                  items(viewState.localBooks) { book ->
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
        
        // Discovery results section
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        
        Text(
          text = stringResource(id = StringsR.string.search_discovery_results),
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
                text = stringResource(id = StringsR.string.search_no_discovery_results),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
              )
            } else {
              when (viewState.layoutMode) {
                BookOverviewLayoutMode.List -> {
                  LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier
                      .padding(horizontal = 8.dp)
                      .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = {
                      items(discoveryState.results) { result ->
                        DiscoveryListRow(
                          result = result,
                          onClick = { onDiscoveryResultClick(result) }
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
                    modifier = Modifier.weight(1f, fill = false),
                    content = {
                      items(discoveryState.results) { result ->
                        DiscoveryGridItem(
                          result = result,
                          onClick = { onDiscoveryResultClick(result) }
                        )
                      }
                    },
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
      }
    }
  }
}
```

## Benefits of this Architecture

1. **Clean Separation of Concerns**
   - Search module handles data retrieval
   - BookOverview module handles presentation
   - Each result type has its own UI components

2. **Progressive Loading**
   - Local results appear immediately
   - Discovery results load asynchronously
   - UI always responds quickly

3. **Consistent UI**
   - Both result types support grid and list views
   - Layout consistency across result types

4. **Error Resilience**
   - Local results still shown even if discovery fails
   - Specific error handling for discovery results

5. **Modularity**
   - Clean separation between modules
   - Each module has clearly defined responsibilities

## Future Enhancements

1. **Ebook Support**
   - Add support for ebook formats
   - Update models and UI to handle multiple format options

2. **Cached Search Results**
   - Store recent search results for offline viewing
   - Reduce API calls for repeated searches

3. **Personalized Recommendations**
   - Use search history to suggest content
   - Show trending or popular items

4. **Advanced Filtering**
   - Filter by language, publication date, categories
   - Sort by relevance, popularity, date

5. **Streaming Integration**
   - Allow immediate streaming of content without full download
   - Preview capability for media items
