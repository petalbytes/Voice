package voice.search.di

import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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
    
    return Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(okHttpClient)
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
    return createRetrofit(okHttpClient, baseUrl).create(SearchApi::class.java)
  }
} 