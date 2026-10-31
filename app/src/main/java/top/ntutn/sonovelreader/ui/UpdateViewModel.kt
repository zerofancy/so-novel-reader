package top.ntutn.sonovelreader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.ntutn.sonovelreader.R
import top.ntutn.sonovelreader.data.update.AvailableUpdate
import top.ntutn.sonovelreader.data.update.InstalledAppInfo
import top.ntutn.sonovelreader.data.update.InvalidUpdateResponse
import top.ntutn.sonovelreader.data.update.UpdateServiceError
import java.io.IOException

internal data class UpdateUiState(
    val checking: Boolean = false,
    val message: Int = R.string.update_idle,
    val available: AvailableUpdate? = null,
    val showDetails: Boolean = false,
    val retryBlocked: Boolean = false,
)

class UpdateViewModel(
    val installed: InstalledAppInfo,
    private val check: suspend (String) -> AvailableUpdate,
) : ViewModel() {
    private var visible = false
    private val mutableState = MutableStateFlow(UpdateUiState(message = when {
        installed.debug -> R.string.update_debug
        installed.arch == null -> R.string.update_unsupported_arch
        else -> R.string.update_idle
    }))
    internal val state = mutableState.asStateFlow()
    val supported get() = !installed.debug && installed.arch != null

    fun setVisible(value: Boolean) {
        visible = value
        if (!value) dismissDetails()
    }

    fun showDetails() = mutableState.update { it.copy(showDetails = it.available != null) }
    fun dismissDetails() = mutableState.update { it.copy(showDetails = false) }

    fun checkUpdates() {
        if (!supported || state.value.checking || state.value.retryBlocked) return
        mutableState.value = UpdateUiState(checking = true, message = R.string.update_checking)
        viewModelScope.launch {
            try {
                val latest = check(requireNotNull(installed.arch))
                mutableState.value = when {
                    latest.isNewerThan(installed.versionCode) -> UpdateUiState(
                        message = R.string.update_available, available = latest, showDetails = visible,
                    )
                    latest.versionCode == installed.versionCode -> UpdateUiState(message = R.string.update_current)
                    else -> UpdateUiState(message = R.string.update_ahead)
                }
            } catch (e: Exception) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                currentCoroutineContext().ensureActive()
                val message = when (e) {
                    is UpdateServiceError -> when {
                        e.status == 404 && e.code == "SOFTWARE_NOT_FOUND" -> R.string.update_not_configured
                        e.status == 404 && e.code == "NO_MATCHING_PACKAGE" -> R.string.update_no_package
                        e.status == 409 && e.code == "AMBIGUOUS_LATEST" -> R.string.update_conflict
                        e.status == 429 || e.status >= 500 -> R.string.update_service_unavailable
                        e.status in 300..399 -> R.string.update_invalid_response
                        else -> R.string.update_failed
                    }
                    is InvalidUpdateResponse -> R.string.update_invalid_response
                    is IOException, is HttpRequestTimeoutException, is TimeoutCancellationException -> R.string.update_network_error
                    else -> R.string.update_failed
                }
                val retryDelay = (e as? UpdateServiceError)?.retryAfterMillis?.takeIf { it > 0 }
                mutableState.value = UpdateUiState(message = message, retryBlocked = retryDelay != null)
                if (retryDelay != null) {
                    delay(retryDelay)
                    mutableState.update { it.copy(retryBlocked = false) }
                }
            }
        }
    }
}
