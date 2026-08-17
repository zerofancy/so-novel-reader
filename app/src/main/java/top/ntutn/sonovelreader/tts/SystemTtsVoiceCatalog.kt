package top.ntutn.sonovelreader.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class TtsVoiceOption(
    val name: String,
    val locale: Locale,
    val requiresNetwork: Boolean,
) {
    val label: String
        get() = buildString {
            append(locale.getDisplayName(Locale.getDefault()).ifBlank { locale.toLanguageTag() })
            append(" · ")
            append(name)
            if (requiresNetwork) append(" · 需联网")
        }
}

data class TtsVoiceCatalogState(
    val loading: Boolean = false,
    val voices: List<TtsVoiceOption> = emptyList(),
    val error: String? = null,
)

class SystemTtsVoiceCatalog(private val context: Context) {
    suspend fun load(): TtsVoiceCatalogState = suspendCancellableCoroutine { continuation ->
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            val tts = engine
            if (!continuation.isActive) {
                tts?.shutdown()
                return@TextToSpeech
            }
            val result = if (status == TextToSpeech.SUCCESS && tts != null) {
                val voices = runCatching {
                    tts.voices.orEmpty()
                        .map { TtsVoiceOption(it.name, it.locale, it.isNetworkConnectionRequired) }
                        .distinctBy(TtsVoiceOption::name)
                        .sortedWith(compareBy<TtsVoiceOption> { it.locale.displayLanguage }.thenBy { it.name })
                }.getOrDefault(emptyList())
                TtsVoiceCatalogState(voices = voices, error = if (voices.isEmpty()) "没有检测到可用的系统语音" else null)
            } else {
                TtsVoiceCatalogState(error = "系统 TTS 初始化失败")
            }
            continuation.resume(result)
            tts?.shutdown()
        }
        continuation.invokeOnCancellation { engine.shutdown() }
    }
}
