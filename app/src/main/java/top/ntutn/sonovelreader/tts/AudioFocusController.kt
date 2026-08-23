package top.ntutn.sonovelreader.tts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import timber.log.Timber

/** 音频焦点申请与耳机拔出监听。 */
class AudioFocusController(
    context: Context,
    private val onFocusChange: (Int) -> Unit,
    private val onNoisy: () -> Unit,
) {
    private val context: Context = context.applicationContext
    private val audioManager = this.context.getSystemService(AudioManager::class.java)

    /** 供 TTS 引擎与 MediaPlayer 共用的音频属性。 */
    val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener(onFocusChange)
        .build()

    private var hasFocus = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onNoisy()
        }
    }

    fun register() {
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(noisyReceiver, filter)
        }
    }

    fun unregister() {
        runCatching { context.unregisterReceiver(noisyReceiver) }
        abandon()
    }

    fun request(): Boolean {
        if (hasFocus) return true
        hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasFocus) Timber.w("音频焦点申请失败")
        return hasFocus
    }

    fun abandon() {
        audioManager.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }
}
