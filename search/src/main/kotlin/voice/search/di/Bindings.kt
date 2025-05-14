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
import voice.search.api.SearchApi
import javax.inject.Singleton

@ContributesTo(AppScope::class)
@Module
object SearchModule {
  
  private fun createRetrofit(okHttpClient: OkHttpClient): Retrofit {
    val contentType = "application/json".toMediaType()
    val json = Json { ignoreUnknownKeys = true }
    
    return Retrofit.Builder()
      .baseUrl("http://10.0.2.2:8080/") // Special IP for accessing host machine from emulator
      .client(okHttpClient)
      .addConverterFactory(json.asConverterFactory(contentType))
      .build()
  }
  
  @Provides
  @Singleton
  fun provideSearchApi(okHttpClient: OkHttpClient): SearchApi {
    return createRetrofit(okHttpClient).create(SearchApi::class.java)
  }
} 