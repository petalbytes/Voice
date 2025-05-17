plugins {
  id("voice.library")
  alias(libs.plugins.anvil)
  alias(libs.plugins.kotlin.serialization)
}

anvil {
  generateDaggerFactories.set(true)
}

dependencies {
  implementation(projects.data)
  implementation(projects.common)
  implementation(projects.strings)
  implementation(projects.pref)

  // Dagger
  implementation(libs.dagger.core)

  // Network
  implementation(libs.bundles.retrofit)
  implementation(libs.retrofit.serialization)
  implementation(libs.okhttp)

  // Coroutines
  implementation(libs.coroutines.core)
  implementation(libs.coroutines.android)

  // Testing
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.coroutines.test)
}
