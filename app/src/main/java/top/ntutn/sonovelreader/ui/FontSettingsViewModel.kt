package top.ntutn.sonovelreader.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import top.ntutn.sonovelreader.AppContainer
import top.ntutn.sonovelreader.data.fonts.FontException

internal data class FontFocusRequest(val id: String, val sequence: Long)

class FontSettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val manager = container.readerFontManager
    val catalog = container.fontRepository.catalog
    val appearance = manager.appearance
    private val mutableSelecting = MutableStateFlow<String?>(null)
    val selecting = mutableSelecting.asStateFlow()
    private val mutableImporting = MutableStateFlow(false)
    val importing = mutableImporting.asStateFlow()
    private val mutableDeleting = MutableStateFlow(false)
    val deleting = mutableDeleting.asStateFlow()
    private val mutableFocus = MutableStateFlow<FontFocusRequest?>(null)
    internal val focus = mutableFocus.asStateFlow()
    private var focusSequence = 0L
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private var selectionJob: Job? = null
    private var selectionGeneration = 0

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        try { manager.refresh() } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { messages.send("字体列表读取失败，请重试") }
    }

    fun select(id: String) {
        if (mutableDeleting.value) return
        val generation = ++selectionGeneration
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            mutableSelecting.value = id
            try {
                manager.select(id)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) { messages.send(message(error, "字体设置保存失败，请重试"))
            } finally { if (generation == selectionGeneration) mutableSelecting.value = null }
        }
    }

    fun importFont(uri: Uri?) {
        if (uri == null || mutableImporting.value) return
        viewModelScope.launch {
            mutableImporting.value = true
            try {
                val result = container.fontRepository.importFont(uri)
                mutableFocus.value = FontFocusRequest(result.entry.id, ++focusSequence)
                messages.send(when {
                    result.builtin -> "该字体已内置"
                    result.duplicate -> "该字体已导入"
                    else -> "字体已导入，点击字体即可应用"
                })
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) { messages.send(message(error, "字体导入失败，请重试"))
            } finally { mutableImporting.value = false }
        }
    }

    fun delete(id: String) {
        if (mutableDeleting.value) return
        selectionJob?.cancel()
        viewModelScope.launch {
            mutableDeleting.value = true
            try {
                val pending = manager.delete(id)
                messages.send(if (pending) "字体已移除，文件清理将在下次启动重试" else "字体已删除")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) { messages.send(message(error, "字体删除失败，请重试"))
            } finally { mutableDeleting.value = false }
        }
    }

    private fun message(error: Exception, fallback: String) = (error as? FontException)?.code?.userMessage ?: fallback
}
