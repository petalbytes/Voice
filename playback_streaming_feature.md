# Streaming Playback Feature: Implementation Plan

## Overview

This document outlines the architecture and implementation plan for adding streaming playback capabilities to the Voice app. The design addresses several key requirements:

1. Support for playing m3u playlist files from remote sources
2. Handling URL expiration and automatic refreshing of stream links
3. Plugin architecture for multiple streaming content providers
4. Unified playback experience between local and streaming content
5. Position tracking across playback sessions
6. Clean separation between temporary streaming content and library books

## Core Architecture Components

### 1. Media Source Abstraction Layer

The core of the architecture is a flexible abstraction layer that separates "book" identity from its playback source:

```kotlin
interface MediaSource {
  val id: String
  val refreshRequired: Boolean
  suspend fun getPlaybackUrl(): String  // Returns local path or streaming URL
  suspend fun refresh(): Boolean  // Returns true if successful
}

class LocalMediaSource(val filePath: String) : MediaSource {
  override val id: String = filePath
  override val refreshRequired: Boolean = false
  override suspend fun getPlaybackUrl() = filePath
  override suspend fun refresh() = true  // No refresh needed for local sources
}

class StreamingMediaSource(
  val sourceId: String,
  val pluginId: String,
  val m3uGenerator: suspend () -> String,
  private val expirationTimeMillis: Long
) : MediaSource {
  override val id: String = "$pluginId:$sourceId"
  private var lastRefreshTime = 0L
  private var cachedUrl: String? = null
  
  override val refreshRequired: Boolean 
    get() = System.currentTimeMillis() - lastRefreshTime > expirationTimeMillis
    
  override suspend fun getPlaybackUrl(): String {
    if (refreshRequired || cachedUrl == null) {
      refresh()
    }
    return cachedUrl!!
  }
  
  override suspend fun refresh(): Boolean {
    return try {
      cachedUrl = m3uGenerator()
      lastRefreshTime = System.currentTimeMillis()
      true
    } catch (e: Exception) {
      false
    }
  }
}
```

### 2. Plugin Registry System

The plugin architecture allows for extensible streaming source providers:

```kotlin
interface StreamingPlugin {
  val id: String
  val name: String
  val icon: Drawable?
  suspend fun search(query: String): List<StreamableContent>
  suspend fun getDetails(contentId: String): BookDetails?
  suspend fun generateM3u(contentId: String): String
}

@Singleton
class PluginRegistry @Inject constructor() {
  private val plugins = mutableMapOf<String, StreamingPlugin>()
  
  fun registerPlugin(plugin: StreamingPlugin) {
    plugins[plugin.id] = plugin
  }
  
  fun getPlugin(id: String): StreamingPlugin? = plugins[id]
  
  fun allPlugins(): List<StreamingPlugin> = plugins.values.toList()
}
```

### 3. Unified Book Repository

A repository that handles both local and streaming content:

```kotlin
data class Book(
  val id: BookId,
  val title: String,
  val author: String,
  val cover: Uri?,
  val mediaSource: MediaSource,
  val temporaryEntry: Boolean = false,
  val pluginId: String? = null,
  val externalId: String? = null,
  val lastAccessed: Long = System.currentTimeMillis(),
  val progress: Float = 0f,
  val lastPosition: Long = 0
)

class BookRepository @Inject constructor(
  private val localDao: BookDao,
  private val streamingDao: StreamingBookDao,
  private val pluginRegistry: PluginRegistry,
) {
  fun getBook(id: BookId): Flow<Book?> = flow {
    // First check local books
    val localBook = localDao.getBook(id)
    if (localBook != null) {
      emit(localBook)
      return@flow
    }
    
    // Then check streaming books
    val streamingBook = streamingDao.getBook(id)
    if (streamingBook != null) {
      // Check if we need to refresh the m3u URL
      val plugin = pluginRegistry.getPlugin(streamingBook.pluginId)
      val source = StreamingMediaSource(
        sourceId = streamingBook.externalId,
        pluginId = streamingBook.pluginId,
        m3uGenerator = { plugin?.generateM3u(streamingBook.externalId) ?: "" },
        expirationTimeMillis = 24 * 60 * 60 * 1000  // 24 hours
      )
      emit(streamingBook.copy(mediaSource = source))
    } else {
      emit(null)
    }
  }
  
  suspend fun createTemporaryStreamingBook(
    pluginId: String,
    externalId: String,
    title: String,
    author: String,
    coverUrl: String?
  ): BookId {
    val book = Book(
      id = BookId(UUID.randomUUID().toString()),
      title = title,
      author = author, 
      cover = coverUrl?.toUri(),
      mediaSource = createMediaSourceForPlugin(pluginId, externalId),
      temporaryEntry = true,
      pluginId = pluginId,
      externalId = externalId
    )
    
    streamingDao.insertBook(book)
    return book.id
  }
  
  // Promote temporary book to permanent library entry
  suspend fun addToLibrary(bookId: BookId): Boolean {
    val book = streamingDao.getBookSync(bookId) ?: return false
    streamingDao.updateBook(book.copy(temporaryEntry = false))
    return true
  }
  
  private fun createMediaSourceForPlugin(pluginId: String, contentId: String): StreamingMediaSource {
    val plugin = pluginRegistry.getPlugin(pluginId)
    return StreamingMediaSource(
      sourceId = contentId,
      pluginId = pluginId,
      m3uGenerator = { plugin?.generateM3u(contentId) ?: "" },
      expirationTimeMillis = 24 * 60 * 60 * 1000  // 24 hours
    )
  }
}
```

### 4. Enhanced Player Service

A player service that handles refreshing URLs during playback:

```kotlin
class EnhancedPlayer @Inject constructor(
  private val exoPlayer: ExoPlayer,
  private val coroutineScope: CoroutineScope
) {
  private var currentMediaSource: MediaSource? = null
  private var currentBookId: BookId? = null
  private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
  val playbackState = _playbackState.asStateFlow()
  
  suspend fun play(book: Book) {
    currentMediaSource = book.mediaSource
    currentBookId = book.id
    
    val url = book.mediaSource.getPlaybackUrl()
    prepareAndPlay(url, book.lastPosition)
    
    // Monitor if source needs refresh during playback
    coroutineScope.launch {
      while (isActive) {
        delay(60 * 1000) // Check every minute
        val source = currentMediaSource ?: break
        if (source.refreshRequired) {
          val refreshed = source.refresh()
          if (refreshed) {
            // Update player without interrupting (if possible)
            updatePlaybackSource(source.getPlaybackUrl())
          }
        }
      }
    }
  }
  
  private fun prepareAndPlay(url: String, startPosition: Long) {
    // Implementation with ExoPlayer
  }
  
  private fun updatePlaybackSource(newUrl: String) {
    // Implementation to update source while maintaining playback state
  }
}
```

### 5. Automatic Cleanup for Temporary Books

```kotlin
@Singleton
class TemporaryBookCleanupService @Inject constructor(
  private val streamingDao: StreamingBookDao
) {
  // Run daily cleanup of temporary books not used in last 30 days
  @Scheduled(fixedRate = 24 * 60 * 60 * 1000)
  fun cleanupTemporaryBooks() {
    val threshold = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000)
    streamingDao.deleteTemporaryBooksOlderThan(threshold)
  }
}
```

## Database Schema Changes

### 1. Streaming Book Table

```kotlin
@Entity(tableName = "streaming_books")
data class StreamingBookEntity(
  @PrimaryKey
  val id: String,
  val title: String,
  val author: String,
  val coverUrl: String?,
  val pluginId: String,
  val externalId: String,
  val lastPosition: Long = 0,
  val duration: Long = 0,
  val progress: Float = 0f,
  val lastAccessed: Long = System.currentTimeMillis(),
  val temporaryEntry: Boolean = true
)

@Dao
interface StreamingBookDao {
  @Query("SELECT * FROM streaming_books WHERE id = :id")
  fun getBook(id: String): Flow<StreamingBookEntity?>
  
  @Query("SELECT * FROM streaming_books WHERE id = :id")
  suspend fun getBookSync(id: String): StreamingBookEntity?
  
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBook(book: StreamingBookEntity)
  
  @Update
  suspend fun updateBook(book: StreamingBookEntity)
  
  @Query("DELETE FROM streaming_books WHERE temporaryEntry = 1 AND lastAccessed < :timestamp")
  suspend fun deleteTemporaryBooksOlderThan(timestamp: Long)
}
```

### 2. Plugin Preferences Storage

```kotlin
@Entity(tableName = "plugin_preferences")
data class PluginPreferences(
  @PrimaryKey
  val pluginId: String,
  val enabled: Boolean = true,
  val authToken: String? = null,
  val refreshToken: String? = null,
  val tokenExpiry: Long = 0,
  val preferences: Map<String, String> = emptyMap()
)

@Dao
interface PluginPreferencesDao {
  @Query("SELECT * FROM plugin_preferences WHERE pluginId = :pluginId")
  suspend fun getPreferences(pluginId: String): PluginPreferences?
  
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun savePreferences(preferences: PluginPreferences)
}
```

## UI Changes

### 1. Integration with Book Details Screen

```kotlin
@Composable
fun BookDetailsScreen(
  viewModel: BookDetailsViewModel,
  onBackClick: () -> Unit,
  onImportBook: (DiscoveryResult, AudioSource) -> Unit,
  onPlayStreaming: (DiscoveryResult, AudioSource) -> Unit  // New callback
) {
  // Existing UI code...
  
  // In the AudioSourceItem:
  Row(modifier = Modifier.fillMaxWidth()) {
    // Play streaming button
    Button(onClick = { onPlayStreaming(result, source) }) {
      Icon(Icons.Default.PlayArrow, contentDescription = null)
      Spacer(modifier = Modifier.width(4.dp))
      Text("Stream")
    }
    
    Spacer(modifier = Modifier.width(8.dp))
    
    // Import button
    Button(onClick = { onImportBook(result, source) }) {
      Icon(Icons.Default.Download, contentDescription = null)
      Spacer(modifier = Modifier.width(4.dp))
      Text("Import")
    }
  }
}
```

### 2. Extensions to Playback Screen

```kotlin
@Composable
fun BookPlayView(
  viewState: BookPlayViewState,
  // Existing parameters...
  onRefreshStream: () -> Unit = {},  // New callback for refreshing expired streams
  isStreaming: Boolean = false       // Flag to show streaming-specific UI
) {
  // Existing UI code...
  
  // Add streaming indicator if applicable
  if (isStreaming) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.End
    ) {
      Icon(
        imageVector = Icons.Default.Cloud,
        contentDescription = "Streaming",
        tint = MaterialTheme.colorScheme.primary
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "Streaming",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary
      )
      
      Spacer(modifier = Modifier.width(16.dp))
      
      // Add to library button for temporary entries
      if (viewState.isTemporary) {
        Button(onClick = viewState.onAddToLibrary) {
          Text("Add to Library")
        }
      }
    }
    
    // Add refresh button for handling expired streams
    if (viewState.streamNeedsRefresh) {
      Button(
        onClick = onRefreshStream,
        modifier = Modifier.align(Alignment.CenterHorizontally)
      ) {
        Icon(Icons.Default.Refresh, contentDescription = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text("Refresh Stream")
      }
    }
  }
}
```

## Implementation Phases

### Phase 1: Core Infrastructure
- Create MediaSource abstraction
- Add StreamingBookDao and related database entities
- Modify BookRepository to handle streaming sources
- Update Player to handle URL refreshing

### Phase 2: Basic Streaming
- Implement initial M3U streaming support
- Integrate with existing playback UI
- Add simple refresh mechanism for expired URLs
- Support basic streaming from discovery results

### Phase 3: Plugin Architecture
- Create plugin interface and registry
- Implement plugin loading mechanism
- Add plugin preferences storage
- Create sample plugin implementation

### Phase 4: Enhanced UI
- Add streaming-specific UI elements
- Implement "Add to Library" functionality
- Create plugin management screen
- Add streaming source indicators

### Phase 5: Advanced Features
- Implement download capability for streaming content
- Add quality selection for streams
- Implement background refresh for playlist URLs
- Create analytics for streaming usage

## Benefits of This Architecture

1. **Unified Playback Experience**
   - Same UI and controls for both local and streaming content
   - Position tracking works consistently across all content types
   - Seamless switching between content sources

2. **Flexible Plugin System**
   - Easy to add new content providers without modifying core code
   - Each plugin manages its own authentication and content format
   - Centralized registry makes discovery seamless

3. **Robust Streaming Capability**
   - Automatic refreshing of expired URLs
   - No interruption to playback when refreshing
   - Fallback mechanisms for connection issues

4. **Clean Library Management**
   - Temporary entries don't clutter the main library
   - Simple promotion of streaming content to library
   - Automatic cleanup of unused temporary entries

5. **Future-Proof Design**
   - Architecture supports advanced features like downloading
   - Plugin system can expand to new content types beyond audiobooks
   - Designed for scalability as streaming sources increase 