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

// =====================================
// AI 操作设置 ViewModel https://o.doubao.com/developer
// =====================================

sealed interface SaepSyncState {
    data object Idle : SaepSyncState
    data class Synced(val version: Int) : SaepSyncState
    data object Failed : SaepSyncState
}

class AiOperationSettingsViewModel(private val container: AppContainer) : ViewModel() {
    val aiSettings = container.settingsRepository.aiSettings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AiSettings(),
    )

    private val mutableSaepAvailable = MutableStateFlow<Boolean?>(null)
    val saepAvailable = mutableSaepAvailable.asStateFlow()

    private val mutableSyncState = MutableStateFlow<SaepSyncState>(SaepSyncState.Idle)
    val syncState = mutableSyncState.asStateFlow()

    init {
        viewModelScope.launch { mutableSaepAvailable.value = container.saepPolicyManager.isSaepEnabled() }
    }

    fun setAllowAiOperation(value: Boolean) = applySetting { container.settingsRepository.setAllowAiOperation(value) }
    fun setAllowAiDeleteContent(value: Boolean) = applySetting { container.settingsRepository.setAllowAiDeleteContent(value) }

    private fun applySetting(update: suspend () -> Unit) = viewModelScope.launch {
        mutableSyncState.value = SaepSyncState.Idle
        update()
        val settings = container.settingsRepository.currentAiSettings()
        val version = container.settingsRepository.nextPolicyVersion()
        val pushed = container.saepPolicyManager.pushPolicy(settings.copy(policyVersion = version))
        mutableSyncState.value = if (pushed) SaepSyncState.Synced(version) else SaepSyncState.Failed
    }
}
