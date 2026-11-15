package top.ntutn.sonovelreader

import android.app.Application
import androidx.room.Room
import top.ntutn.sonovelreader.data.BookmarkRepository
import top.ntutn.sonovelreader.data.BookRepository
import top.ntutn.sonovelreader.data.ProgressRepository
import top.ntutn.sonovelreader.data.SaepPolicyManager
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

class AppContainer(application: Application) : AutoCloseable {
    private val database = Room.databaseBuilder(
        application,
        LibraryDatabase::class.java,
        "library.db",
    ).build()

    private val updateHttpClient by lazy { top.ntutn.sonovelreader.data.update.createUpdateHttpClient() }
    val updateChecker by lazy { top.ntutn.sonovelreader.data.update.UpdateChecker(
        updateHttpClient, top.ntutn.updater.UpdateVersion(installedAppInfo.versionCode, installedAppInfo.versionName),
    ) }
    val installedAppInfo by lazy { top.ntutn.sonovelreader.data.update.InstalledAppInfo.read(application) }

    val bookRepository = BookRepository(application, database.libraryDao())
    val bookmarkRepository = BookmarkRepository(database.bookmarkDao())
    val progressRepository = ProgressRepository(database.libraryDao())
    val settingsRepository = SettingsRepository(application)
    private val applicationScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate,
    )
    val fontRepository = top.ntutn.sonovelreader.data.fonts.FontRepository(application)
    val readerFontManager by lazy { top.ntutn.sonovelreader.data.ReaderFontManager(
        fontRepository, settingsRepository, applicationScope,
    ) }

    /** For owners of short-lived containers (e.g. device tests); the app container is process scoped. */
    override fun close() {
        applicationScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        database.close()
    }
    val saepPolicyManager = SaepPolicyManager(application)
    val ttsPlaybackManager = TtsPlaybackManager(application)
    val ttsVoiceCatalog = SystemTtsVoiceCatalog(application)
}
