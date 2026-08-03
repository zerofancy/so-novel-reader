package top.ntutn.sonovelreader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.ntutn.sonovelreader.ui.SoNovelReaderApp
import top.ntutn.sonovelreader.ui.theme.SoNovelReaderTheme

class MainActivity : ComponentActivity() {
    private val mutableSharedUris = MutableStateFlow<List<Uri>>(emptyList())
    val sharedUris = mutableSharedUris.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        acceptImportIntent(intent)
        setContent {
            SoNovelReaderTheme {
                SoNovelReaderApp(
                    container = (application as SoNovelReaderApplication).container,
                    sharedUris = sharedUris,
                    consumeSharedUris = { mutableSharedUris.value = emptyList() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptImportIntent(intent)
    }

    private fun acceptImportIntent(intent: Intent?) {
        val supportedAction = intent?.action == Intent.ACTION_VIEW ||
            intent?.action == Intent.ACTION_SEND ||
            intent?.action == Intent.ACTION_SEND_MULTIPLE
        if (!supportedAction) return

        val uris = buildList {
            intent.data?.let(::add)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let(::add)
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let(::addAll)
            } else {
                @Suppress("DEPRECATION")
                (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let(::add)
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let(::addAll)
            }
            intent.clipData?.let { clip ->
                repeat(clip.itemCount) { index -> clip.getItemAt(index).uri?.let(::add) }
            }
        }.distinct()
        if (uris.isNotEmpty()) mutableSharedUris.value = uris
    }
}
