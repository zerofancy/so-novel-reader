package top.ntutn.sonovelreader.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerDataStore by preferencesDataStore(name = "reader_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val mode = stringPreferencesKey("reading_mode")
        val fontSize = intPreferencesKey("font_size_sp")
        val lineHeight = floatPreferencesKey("line_height")
        val firstLineIndent = booleanPreferencesKey("first_line_indent")
        val paragraphSpacing = intPreferencesKey("paragraph_spacing_dp")
        val theme = stringPreferencesKey("reader_theme")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val ttsRate = floatPreferencesKey("tts_rate")
        val ttsPitch = floatPreferencesKey("tts_pitch")
        val ttsVoiceName = stringPreferencesKey("tts_voice_name")
    }

    val settings: Flow<ReaderSettings> = context.readerDataStore.data.map(::decode)

    suspend fun setReadingMode(value: ReadingMode) = update(Keys.mode, value.name)
    suspend fun setFontSize(value: Int) = update(Keys.fontSize, value.coerceIn(14, 32))
    suspend fun setLineHeight(value: Float) = update(Keys.lineHeight, value.coerceIn(1.2f, 2.2f))
    suspend fun setFirstLineIndent(value: Boolean) = update(Keys.firstLineIndent, value)
    suspend fun setParagraphSpacing(value: Int) = update(Keys.paragraphSpacing, value.coerceIn(4, 48))
    suspend fun setTheme(value: ReaderTheme) = update(Keys.theme, value.name)
    suspend fun setKeepScreenOn(value: Boolean) = update(Keys.keepScreenOn, value)
    suspend fun setTtsRate(value: Float) = update(Keys.ttsRate, value.coerceIn(0.5f, 2f))
    suspend fun setTtsPitch(value: Float) = update(Keys.ttsPitch, value.coerceIn(0.5f, 2f))
    suspend fun setTtsVoiceName(value: String?) {
        context.readerDataStore.edit { preferences ->
            if (value == null) preferences.remove(Keys.ttsVoiceName) else preferences[Keys.ttsVoiceName] = value
        }
    }

    private suspend fun <T> update(key: Preferences.Key<T>, value: T) {
        context.readerDataStore.edit { it[key] = value }
    }

    private fun decode(preferences: Preferences): ReaderSettings = ReaderSettings(
        readingMode = preferences[Keys.mode].toEnumOrDefault(ReadingMode.SCROLL),
        fontSizeSp = (preferences[Keys.fontSize] ?: 20).coerceIn(14, 32),
        lineHeight = (preferences[Keys.lineHeight] ?: 1.5f).coerceIn(1.2f, 2.2f),
        firstLineIndent = preferences[Keys.firstLineIndent] ?: true,
        paragraphSpacingDp = (preferences[Keys.paragraphSpacing] ?: 24).coerceIn(4, 48),
        theme = preferences[Keys.theme].toEnumOrDefault(ReaderTheme.SYSTEM),
        keepScreenOn = preferences[Keys.keepScreenOn] ?: false,
        ttsRate = (preferences[Keys.ttsRate] ?: 1f).coerceIn(0.5f, 2f),
        ttsPitch = (preferences[Keys.ttsPitch] ?: 1f).coerceIn(0.5f, 2f),
        ttsVoiceName = preferences[Keys.ttsVoiceName],
    )
}

private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: default
