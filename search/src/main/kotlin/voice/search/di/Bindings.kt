package voice.search.di

import android.util.Log
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
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

  /**
   * Interceptor that rewrites the scheme/host/port of every outgoing request
   * to match the *current* value in `pluginBaseUrlPref`.
   * Because the preference is read inside `intercept`, the very next
   * request after the user changes the setting is routed to the new server
   * – no restart or graph rebuild needed.
   */
  private fun dynamicBaseUrlInterceptor(
    pluginBaseUrlPref: Pref<String>
  ) = Interceptor { chain ->
    val fallback = "http://10.0.2.2:8080/".toHttpUrl()
    val current = pluginBaseUrlPref.value
      .takeIf { it.isNotBlank() }
      ?.let { runCatching { it.toHttpUrl() }.getOrNull() }
      ?: fallback

    val newUrl = chain.request().url.newBuilder()
      .scheme(current.scheme)
      .host(current.host)
      .port(current.port)
      .build()

    chain.proceed(chain.request().newBuilder().url(newUrl).build())
  }

  private fun createRetrofit(
    okHttpClient: OkHttpClient,
    pluginBaseUrlPref: Pref<String>
  ): Retrofit {
    val contentType = "application/json".toMediaType()
    val json = Json { ignoreUnknownKeys = true }

    // Logging interceptor (unchanged from your original code).
    val loggingInterceptor = HttpLoggingInterceptor { msg ->
      Log.d("SearchAPI", msg)
    }.apply { level = HttpLoggingInterceptor.Level.BODY }

    // Client with both logging and dynamic-base-URL interceptors.
    val client = okHttpClient.newBuilder()
      .addInterceptor(dynamicBaseUrlInterceptor(pluginBaseUrlPref))
      .addInterceptor(loggingInterceptor)
      .build()

    // The baseUrl here is never used – the interceptor rewrites it.
    return Retrofit.Builder()
      .baseUrl("http://0.0.0.0/")          // dummy placeholder
      .client(client)
      .addConverterFactory(json.asConverterFactory(contentType))
      .build()
  }

  @Provides
  @Singleton
  fun provideSearchApi(
    okHttpClient: OkHttpClient,
    @Named(PrefKeys.PLUGIN_BASE_URL) pluginBaseUrlPref: Pref<String>
  ): SearchApi {
    Log.d("SearchAPI", "Initializing SearchApi module")
    return createRetrofit(okHttpClient, pluginBaseUrlPref)
      .create(SearchApi::class.java)
  }
}
