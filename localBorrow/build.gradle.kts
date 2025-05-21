plugins {
  id("voice.library")
  id("voice.compose")
  alias(libs.plugins.anvil)
}

anvil {
  generateDaggerFactories.set(true)
}

android.buildFeatures.androidResources = true

dependencies {
  implementation(projects.common)
  implementation(projects.data)
  implementation(projects.pref)
  implementation(libs.documentFile)
  implementation(projects.search)
  implementation(projects.strings)

  implementation(libs.dagger.core)
  implementation(libs.coroutines.core)
  implementation(libs.okhttp)
  implementation(libs.junrar)
  implementation(libs.datastore)
  implementation(libs.androidxCore)

  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.coroutines.test)
  testImplementation(libs.okhttp.mockwebserver)
}
