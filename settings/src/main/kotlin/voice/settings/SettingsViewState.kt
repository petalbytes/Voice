package voice.settings

data class SettingsViewState(
  val useDarkTheme: Boolean,
  val showDarkThemePref: Boolean,
  val seekTimeInSeconds: Int,
  val autoRewindInSeconds: Int,
  val appVersion: String,
  val dialog: Dialog?,
  val useGrid: Boolean,
  val pluginBaseUrl: String,
  val borrowLocation: String,
) {

  enum class Dialog {
    AutoRewindAmount,
    SeekTime,
    PluginBaseUrl,
    BorrowLocation,
  }
}
