package voice.search.di

import android.util.Log
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import voice.common.AppScope
import voice.common.pref.PrefKeys
import voice.pref.Pref
import voice.search.api.SearchApi
import javax.inject.Named
import javax.inject.Singleton

@ContributesTo(AppScope::class)
@Module
object SearchModule {
  
  private fun createRetrofit(okHttpClient: OkHttpClient, baseUrl: String): Retrofit {
    val contentType = "application/json".toMediaType()
    val json = Json { ignoreUnknownKeys = true }
    
    // Add logging interceptor
    val loggingInterceptor = HttpLoggingInterceptor { message ->
      Log.d("SearchAPI", message)
    }.apply {
      level = HttpLoggingInterceptor.Level.BODY
    }
    
    // Create a new client with the logging interceptor
    val clientWithLogging = okHttpClient.newBuilder()
      .addInterceptor(loggingInterceptor)
      .build()
    
    Log.d("SearchAPI", "Using base URL: $baseUrl")
    
    return Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(clientWithLogging)
      .addConverterFactory(json.asConverterFactory(contentType))
      .build()
  }
  
  @Provides
  @Singleton
  fun provideSearchApi(
    okHttpClient: OkHttpClient,
    @Named(PrefKeys.PLUGIN_BASE_URL) pluginBaseUrlPref: Pref<String>
  ): SearchApi {
    val baseUrl = pluginBaseUrlPref.value.takeIf { it.isNotBlank() } ?: "http://10.0.2.2:8080/"
    Log.d("SearchAPI", "Initializing SearchApi with base URL: $baseUrl")
    return createRetrofit(okHttpClient, baseUrl).create(SearchApi::class.java)
  }
} 