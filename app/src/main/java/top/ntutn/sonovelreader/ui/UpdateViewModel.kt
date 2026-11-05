package top.ntutn.sonovelreader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.ntutn.sonovelreader.R
import top.ntutn.sonovelreader.data.update.InstalledAppInfo
import top.ntutn.updater.*
import kotlin.time.Clock

internal data class UpdateUiState(
    val checking: Boolean = false,
    val message: Int = R.string.update_idle,
    val available: UpdateRelease? = null,
    val showDetails: Boolean = false,
    val retryBlocked: Boolean = false,
)

class UpdateViewModel(
    val installed: InstalledAppInfo,
    private val check: suspend (String) -> UpdateCheckResult,
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
                when (val result = check(requireNotNull(installed.arch))) {
                    is UpdateCheckResult.Available -> mutableState.value = UpdateUiState(
                        message = R.string.update_available, available = result.release, showDetails = visible,
                    )
                    is UpdateCheckResult.UpToDate -> mutableState.value = UpdateUiState(message =
                        if (result.latest.code == installed.versionCode) R.string.update_current else R.string.update_ahead)
                    UpdateCheckResult.NoMatchingPackage -> mutableState.value = UpdateUiState(message = R.string.update_no_package)
                    is UpdateCheckResult.Failed -> showError(result.error)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                currentCoroutineContext().ensureActive()
                mutableState.value = UpdateUiState(message = R.string.update_failed)
            }
        }
    }

    private suspend fun showError(error: UpdateError) {
        val message = when {
            error.httpStatus == 404 && error.serviceCode == "SOFTWARE_NOT_FOUND" -> R.string.update_not_configured
            error.kind == UpdateErrorKind.AmbiguousLatest -> R.string.update_conflict
            error.httpStatus == 429 || (error.httpStatus ?: 0) >= 500 -> R.string.update_service_unavailable
            error.kind in setOf(UpdateErrorKind.InvalidResponse, UpdateErrorKind.UnsafeDownloadUrl) ||
                error.httpStatus in 300..399 -> R.string.update_invalid_response
            error.kind in setOf(UpdateErrorKind.Network, UpdateErrorKind.Timeout) -> R.string.update_network_error
            else -> R.string.update_failed
        }
        val retryDelay = error.retryAt?.let { (it - Clock.System.now()).inWholeMilliseconds }?.takeIf { it > 0 }
        mutableState.value = UpdateUiState(message = message, retryBlocked = retryDelay != null)
        if (retryDelay != null) {
            delay(retryDelay)
            mutableState.update { it.copy(retryBlocked = false) }
        }
    }
}
