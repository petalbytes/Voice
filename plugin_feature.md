# Audiobook Plugin System

## Overview

This document outlines the architecture for implementing a plugin system that allows the Voice application to retrieve audiobooks from multiple sources. Each plugin can provide different audio formats, narrators, or quality options for the same book.

## Plugin Architecture

The plugin system is organized within the search module:

```
search/
├── src/main/kotlin/voice/search/
    ├── plugin/
        ├── PluginManager.kt
        ├── AudiobookPlugin.kt
        ├── plugins/
            ├── LibrivoxPlugin.kt
            ├── InternetArchivePlugin.kt
            ├── CommunityPlugin.kt
```

## Plugin Interface

```kotlin
interface AudiobookPlugin {
  /**
   * Unique identifier for the plugin
   */
  val id: String
  
  /**
   * Display name of the plugin
   */
  val name: String
  
  /**
   * Brief description of the plugin's source
   */
  val description: String
  
  /**
   * Plugin icon for UI display
   */
  val icon: ImageVector
  
  /**
   * Search for audiobooks using this plugin's source
   */
  suspend fun search(query: String): Result<List<DiscoveryResult>>
  
  /**
   * Download an audiobook from this plugin's source
   */
  suspend fun downloadAudiobook(source: AudioSource): Result<AudiobookContent>
  
  /**
   * Check if this plugin can handle the given audio source
   */
  fun canHandle(source: AudioSource): Boolean = source.pluginId == id
}

/**
 * Represents downloaded audiobook content
 */
data class AudiobookContent(
  val path: String,
  val format: String,
  val chapters: List<Chapter>? = null
)
```

## Plugin Manager

```kotlin
class PluginManager @Inject constructor(
  private val plugins: Set<@JvmSuppressWildcards AudiobookPlugin>
) {
  /**
   * Get a plugin by its ID
   */
  fun getPlugin(pluginId: String): AudiobookPlugin? {
    return plugins.find { it.id == pluginId }
  }
  
  /**
   * Get all available plugins
   */
  fun getPlugins(): List<AudiobookPlugin> = plugins.toList()
  
  /**
   * Search across all plugins
   */
  suspend fun searchAll(query: String): Map<String, Result<List<DiscoveryResult>>> {
    return plugins.associate { plugin ->
      plugin.id to runCatching { plugin.search(query).getOrThrow() }
    }
  }
}
```

## Extended Audio Source Model

For use with plugins, the AudioSource model is extended with additional fields:

```kotlin
data class AudioSource(
  val pluginId: String,
  val readBy: String,
  val duration: String,
  val fileSize: String,
  val format: String,
  val quality: String,
  val m3u: String? = null,
  val chapters: List<Chapter>? = null
) {
  companion object {
    fun fromJson(json: JSONObject): AudioSource {
      val chapters = json.optJSONArray("chapters")?.let { chaptersArray ->
        List(chaptersArray.length()) { i ->
          val chapterJson = chaptersArray.getJSONObject(i)
          Chapter(
            title = chapterJson.getString("title"),
            startTime = chapterJson.getString("start_time")
          )
        }
      }
      
      return AudioSource(
        pluginId = json.getString("plugin_id"),
        readBy = json.getString("read_by"),
        duration = json.getString("duration"),
        fileSize = json.getString("file_size"),
        format = json.getString("format"),
        quality = json.getString("quality"),
        m3u = json.optString("m3u", null),
        chapters = chapters
      )
    }
  }
}

data class Chapter(
  val title: String,
  val startTime: String
)
```

## Media Import with Plugins

```kotlin
class MediaImportManager @Inject constructor(
  private val bookRepository: BookRepository,
  private val coverDownloader: CoverDownloader,
  private val httpClient: OkHttpClient,
  private val pluginManager: PluginManager
) {
  suspend fun importAudiobook(
    discoveryResult: DiscoveryResult, 
    selectedSource: AudioSource
  ): Result<BookId> {
    return try {
      // Get the plugin that can handle this source
      val plugin = pluginManager.getPlugin(selectedSource.pluginId)
        ?: return Result.failure(ImportError.PluginNotFound(selectedSource.pluginId))
      
      // Download the audiobook content using the plugin
      val downloadResult = plugin.downloadAudiobook(selectedSource)
      if (downloadResult.isFailure) {
        return Result.failure(downloadResult.exceptionOrNull() ?: ImportError.DownloadFailed)
      }
      
      // Process the downloaded content
      val localContent = downloadResult.getOrNull()
        ?: return Result.failure(ImportError.ProcessingFailed)
      
      // Download the cover image if available
      val coverFile = discoveryResult.coverImageUrl?.let { imageUrl ->
        coverDownloader.downloadCover(imageUrl)
      }
      
      // Create a new book entity from the discovery result and local content
      val bookId = bookRepository.addBook(
        title = discoveryResult.title,
        author = discoveryResult.authors.firstOrNull() ?: "Unknown",
        duration = selectedSource.duration,
        narrator = selectedSource.readBy,
        coverPath = coverFile?.path,
        audiobookPath = localContent.path,
        chapters = selectedSource.chapters?.map { chapter ->
          BookChapter(
            title = chapter.title,
            startTimeMs = parseTimeToMs(chapter.startTime)
          )
        } ?: emptyList()
      )
      
      Result.success(bookId)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
  
  private fun parseTimeToMs(timeString: String): Long {
    // Parse time format HH:MM:SS to milliseconds
    val parts = timeString.split(":").map { it.toLong() }
    return when (parts.size) {
      3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
      2 -> (parts[0] * 60 + parts[1]) * 1000
      else -> 0
    }
  }
}

sealed class ImportError : Exception() {
  object DownloadFailed : ImportError()
  object ProcessingFailed : ImportError()
  data class PluginNotFound(val pluginId: String) : ImportError()
}
```

## Source Selection UI

When a user selects a discovery result with multiple available sources, they will be presented with a source selection screen:

```kotlin
@Composable
fun SourceSelectionScreen(
  discoveryResult: DiscoveryResult,
  onSourceSelected: (AudioSource) -> Unit,
  onCancel: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    Text(
      text = discoveryResult.title,
      style = MaterialTheme.typography.headlineMedium
    )
    
    Text(
      text = discoveryResult.authors.joinToString(", "),
      style = MaterialTheme.typography.bodyLarge
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Text(
      text = "Available sources",
      style = MaterialTheme.typography.titleMedium
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    discoveryResult.availableSources?.forEach { source ->
      SourceCard(
        source = source,
        onClick = { onSourceSelected(source) }
      )
      
      Spacer(modifier = Modifier.height(8.dp))
    }
    
    Spacer(modifier = Modifier.weight(1f))
    
    TextButton(
      onClick = onCancel,
      modifier = Modifier.align(Alignment.End)
    ) {
      Text("Cancel")
    }
  }
}

@Composable
fun SourceCard(
  source: AudioSource,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
  ) {
    Column(
      modifier = Modifier.padding(16.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Plugin icon or avatar
        Badge {
          Text(source.pluginId.first().uppercase())
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
          text = source.pluginId,
          style = MaterialTheme.typography.titleMedium
        )
      }
      
      Spacer(modifier = Modifier.height(8.dp))
      
      Text("Read by: ${source.readBy}")
      Text("Format: ${source.format.uppercase()} (${source.quality})")
      Text("Duration: ${source.duration}")
      Text("Size: ${source.fileSize}")
    }
  }
}
```

## Example Response Format

Plugins provide audiobook details with more specific technical information:

```json
{
    "description": "The 7 Habits of Highly Effective People...",
    "written_by": "Stephen R. Covey",
    "available_sources": [
        {
            "plugin_id": "librivox",
            "read_by": "Stephen R. Covey",
            "duration": "13 hours 4 minutes",
            "file_size": "568.38 MBs",
            "format": "mp3",
            "quality": "64kbps",
            "m3u": "#EXTM3U\n#EXTINF:-1,Chapter 1\nhttp://..."
        },
        {
            "plugin_id": "audiobooks",
            "read_by": "John Doe",
            "duration": "12 hours 45 minutes",
            "file_size": "720.5 MBs",
            "format": "m4b",
            "quality": "128kbps",
            "chapters": [
                {"title": "Introduction", "start_time": "00:00:00"},
                {"title": "Chapter 1", "start_time": "00:10:35"}
            ]
        },
        {
            "plugin_id": "community",
            "read_by": "Jane Smith",
            "duration": "13 hours 10 minutes",
            "file_size": "490.2 MBs",
            "format": "mp3",
            "quality": "96kbps",
            "m3u": "#EXTM3U\n#EXTINF:-1,Intro\nhttp://..."
        }
    ]
}
```

## Plugin Registration

Plugins are registered using Dagger's multibinding:

```kotlin
@Module
@ContributesTo(AppScope::class)
object PluginsModule {
  @Provides
  @IntoSet
  fun provideLibrivoxPlugin(
    httpClient: OkHttpClient,
    jsonParser: JsonParser
  ): AudiobookPlugin {
    return LibrivoxPlugin(httpClient, jsonParser)
  }
  
  @Provides
  @IntoSet
  fun provideInternetArchivePlugin(
    httpClient: OkHttpClient,
    jsonParser: JsonParser
  ): AudiobookPlugin {
    return InternetArchivePlugin(httpClient, jsonParser)
  }
  
  // Additional plugin bindings
}
```

## Future Plugin Enhancements

1. **User-Installed Plugins**
   - Support for third-party plugins installed by users
   - Plugin marketplace or directory

2. **Plugin Settings**
   - Per-plugin configuration options
   - Account integration for premium sources

3. **Content Filtering**
   - Age/content restrictions
   - Language preferences

4. **Advanced Formats**
   - Support for DRM-protected content
   - High-definition audio formats
   - Streaming-only sources
``` 