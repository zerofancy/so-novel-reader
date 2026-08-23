package top.ntutn.sonovelreader

import android.app.Application
import androidx.room.Room
import top.ntutn.sonovelreader.data.BookRepository
import top.ntutn.sonovelreader.data.ProgressRepository
import top.ntutn.sonovelreader.data.SettingsRepository
import top.ntutn.sonovelreader.data.local.LibraryDatabase
import top.ntutn.sonovelreader.tts.SystemTtsVoiceCatalog
import top.ntutn.sonovelreader.tts.TtsPlaybackManager
import timber.log.Timber

class SoNovelReaderApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}

class AppContainer(application: Application) {
    private val database = Room.databaseBuilder(
        application,
        LibraryDatabase::class.java,
        "library.db",
    ).build()

    val bookRepository = BookRepository(application, database.libraryDao())
    val progressRepository = ProgressRepository(database.libraryDao())
    val settingsRepository = SettingsRepository(application)
    val ttsPlaybackManager = TtsPlaybackManager(application)
    val ttsVoiceCatalog = SystemTtsVoiceCatalog(application)
}
