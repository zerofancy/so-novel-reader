package top.ntutn.sonovelreader.data

import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.ntutn.sonovelreader.data.fonts.FontException
import top.ntutn.sonovelreader.data.fonts.FontRepository
import top.ntutn.sonovelreader.data.fonts.ResolvedReaderFont

/** One atomic settings/font snapshot for preview, layout measurement and actual drawing. */
data class ReaderAppearance(
    val settings: ReaderSettings,
    val font: ResolvedReaderFont,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReaderFontManager(
    val repository: FontRepository,
    private val settings: SettingsRepository,
    scope: CoroutineScope,
) {
    private val mutations = Mutex()
    private val retry = MutableStateFlow(0)
    private val mutableAppearance = MutableStateFlow<ReaderAppearance?>(null)
    val appearance = mutableAppearance.asStateFlow()
    private val mutableMessages = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val messages = mutableMessages.receiveAsFlow()
    private val initialized = scope.async {
        try { repository.refresh() } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { mutableMessages.send("字体列表读取失败，可在字体页重试") }
    }

    init {
        scope.launch {
            initialized.await()
            retry.flatMapLatest {
                settings.settings.catch {
                    mutableMessages.send("阅读设置暂时无法读取，使用默认设置；可在字体页重试")
                    emit(ReaderSettings())
                }
            }.collectLatest { value ->
                try {
                    val font = repository.resolve(value.fontId)
                    mutableAppearance.value = ReaderAppearance(value, font)
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) {
                    val font = ResolvedReaderFont("system", "system", FontFamily.Default)
                    mutableAppearance.value = ReaderAppearance(value.copy(fontId = "system"), font)
                    val transient = (error as? FontException)?.isPermanentSelectionFailure != true
                    mutableMessages.send(if (transient) "字体暂时无法读取，使用系统默认；可在字体页重试"
                        else "所选字体不可用，已使用系统默认")
                    if (!transient) {
                        try { mutations.withLock {
                            if (settings.settings.first().fontId == value.fontId) settings.setFontId("system")
                        } } catch (cancelled: CancellationException) { throw cancelled
                        } catch (_: Exception) { mutableMessages.send("字体设置保存失败，请重试") }
                    }
                }
            }
        }
    }

    suspend fun select(id: String) = mutations.withLock {
        initialized.await()
        if (mutableAppearance.value?.font?.id == id && settings.settings.first().fontId == id) return@withLock
        repository.resolve(id)
        currentCoroutineContext().ensureActive()
        settings.setFontId(id)
    }

    suspend fun refresh() {
        initialized.await()
        repository.refresh()
        retry.value++
    }

    suspend fun delete(id: String): Boolean = mutations.withLock {
        initialized.await()
        if (settings.settings.first().fontId == id) {
            repository.resolve("system")
            settings.setFontId("system")
        }
        repository.deleteImported(id).cleanupPending
    }
}
