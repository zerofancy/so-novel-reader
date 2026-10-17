package top.ntutn.sonovelreader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.ntutn.sonovelreader.AppContainer
import top.ntutn.sonovelreader.data.AiSettings
import top.ntutn.sonovelreader.data.ReaderSettings
import top.ntutn.sonovelreader.data.ReaderTheme
import top.ntutn.sonovelreader.data.ReadingMode
import top.ntutn.sonovelreader.tts.TtsPlaybackStatus
import top.ntutn.sonovelreader.tts.TtsVoiceCatalogState

// =====================================
// 其它 ViewModel（Settings / Reader）
// =====================================

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val aiSettings = container.settingsRepository.aiSettings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AiSettings(),
    )
    val settings = container.settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ReaderSettings(),
    )
    private val mutableTtsVoices = MutableStateFlow(TtsVoiceCatalogState())
    val ttsVoices = mutableTtsVoices.asStateFlow()

    private val mutableSaepAvailable = MutableStateFlow<Boolean?>(null)
    /** SAEP 是否可用；null 表示检测中。 */
    val saepAvailable = mutableSaepAvailable.asStateFlow()

    init {
        viewModelScope.launch { mutableSaepAvailable.value = container.saepPolicyManager.isSaepEnabled() }
    }

    fun setMode(value: ReadingMode) = viewModelScope.launch { container.settingsRepository.setReadingMode(value) }
    fun setFontSize(value: Int) = viewModelScope.launch { container.settingsRepository.setFontSize(value) }
    fun setLineHeight(value: Float) = viewModelScope.launch { container.settingsRepository.setLineHeight(value) }
    fun setFirstLineIndent(value: Boolean) = viewModelScope.launch { container.settingsRepository.setFirstLineIndent(value) }
    fun setParagraphSpacing(value: Int) = viewModelScope.launch { container.settingsRepository.setParagraphSpacing(value) }
    fun setTheme(value: ReaderTheme) = viewModelScope.launch { container.settingsRepository.setTheme(value) }
    fun setKeepScreenOn(value: Boolean) = viewModelScope.launch { container.settingsRepository.setKeepScreenOn(value) }
    fun setTtsRate(value: Float) = updateTtsSettings(settings.value.copy(ttsRate = value.coerceIn(0.5f, 2f))) {
        container.settingsRepository.setTtsRate(value)
    }
    fun setTtsPitch(value: Float) = updateTtsSettings(settings.value.copy(ttsPitch = value.coerceIn(0.5f, 2f))) {
        container.settingsRepository.setTtsPitch(value)
    }
    fun setTtsVoiceName(value: String?) = updateTtsSettings(settings.value.copy(ttsVoiceName = value)) {
        container.settingsRepository.setTtsVoiceName(value)
    }

    fun loadTtsVoices() {
        if (mutableTtsVoices.value.loading || mutableTtsVoices.value.voices.isNotEmpty()) return
        viewModelScope.launch {
            mutableTtsVoices.value = TtsVoiceCatalogState(loading = true)
            mutableTtsVoices.value = container.ttsVoiceCatalog.load()
        }
    }

    private fun updateTtsSettings(value: ReaderSettings, update: suspend () -> Unit) = viewModelScope.launch {
        update()
        if (container.ttsPlaybackManager.state.value.status in setOf(TtsPlaybackStatus.PLAYING, TtsPlaybackStatus.PAUSED)) {
            container.ttsPlaybackManager.updateSettings(value)
        }
    }
}
