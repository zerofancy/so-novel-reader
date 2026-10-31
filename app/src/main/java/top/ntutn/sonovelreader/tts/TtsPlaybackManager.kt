package top.ntutn.sonovelreader.tts

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.ntutn.sonovelreader.data.ReaderLocator
import top.ntutn.sonovelreader.data.ReaderSettings

class TtsPlaybackManager(private val context: Context) {
    private val mutableState = MutableStateFlow(TtsPlaybackState())
    val state = mutableState.asStateFlow()
    var startPending: Boolean = false
        private set

    fun play(bookId: String, locator: ReaderLocator, settings: ReaderSettings) {
        val intent = commandIntent(TtsPlaybackService.ACTION_PLAY).apply {
            putExtra(TtsPlaybackService.EXTRA_BOOK_ID, bookId)
            putExtra(TtsPlaybackService.EXTRA_CHAPTER_INDEX, locator.chapterIndex)
            putExtra(TtsPlaybackService.EXTRA_CHAPTER_FRACTION, locator.chapterFraction)
            putSettings(settings)
        }
        requestStart { ContextCompat.startForegroundService(context, intent) }
    }

    fun pause() = context.startService(commandIntent(TtsPlaybackService.ACTION_PAUSE))

    fun resume() = requestStart {
        ContextCompat.startForegroundService(context, commandIntent(TtsPlaybackService.ACTION_RESUME))
    }

    fun stop() = context.startService(commandIntent(TtsPlaybackService.ACTION_STOP))

    fun updateSettings(settings: ReaderSettings) {
        context.startService(commandIntent(TtsPlaybackService.ACTION_UPDATE_SETTINGS).apply { putSettings(settings) })
    }

    internal fun publish(state: TtsPlaybackState) {
        startPending = false
        mutableState.value = state
    }

    private inline fun requestStart(start: () -> Unit) {
        startPending = true
        try {
            start()
        } catch (error: Exception) {
            startPending = false
            throw error
        }
    }

    private fun commandIntent(action: String) = Intent(context, TtsPlaybackService::class.java).setAction(action)

    private fun Intent.putSettings(settings: ReaderSettings) {
        putExtra(TtsPlaybackService.EXTRA_RATE, settings.ttsRate)
        putExtra(TtsPlaybackService.EXTRA_PITCH, settings.ttsPitch)
        putExtra(TtsPlaybackService.EXTRA_VOICE, settings.ttsVoiceName)
    }
}
