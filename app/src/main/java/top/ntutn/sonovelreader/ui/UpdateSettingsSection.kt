package top.ntutn.sonovelreader.ui

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import top.ntutn.updater.createAndroidBrowserOpener
import top.ntutn.updater.BrowserOpenResult
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.ntutn.sonovelreader.R

@Composable
fun UpdateSettingsSection(viewModel: UpdateViewModel) {
    val context = LocalContext.current
    val browser = remember(context) { createAndroidBrowserOpener(context) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by viewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, _ ->
            viewModel.setVisible(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycle.addObserver(observer)
        viewModel.setVisible(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.setVisible(false)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.update_installed, viewModel.installed.versionName, viewModel.installed.versionCode))
        Text(state.available?.let {
            stringResource(R.string.update_available_version, it.version.name, it.version.code)
        } ?: stringResource(state.message), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = viewModel::checkUpdates,
                enabled = viewModel.supported && !state.checking && !state.retryBlocked) {
                if (state.checking) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(stringResource(if (state.checking) R.string.update_checking else R.string.update_check))
            }
            if (state.available != null) {
                TextButton(onClick = viewModel::showDetails) { Text(stringResource(R.string.update_view)) }
            }
        }
        if (state.retryBlocked) Text(stringResource(R.string.update_retry_later), style = MaterialTheme.typography.bodySmall)
    }
    val latest = state.available
    if (state.showDetails && latest != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDetails,
            title = { Text(stringResource(R.string.update_available)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.update_installed, viewModel.installed.versionName, viewModel.installed.versionCode))
                    Text(stringResource(R.string.update_new_version, latest.version.name, latest.version.code))
                    if (latest.distributionTitle.isNotBlank()) Text(latest.distributionTitle)
                    latest.packageSize?.let { Text(stringResource(R.string.update_size, Formatter.formatFileSize(context, it))) }
                    latest.description?.let { Text(it) }
                    Text(stringResource(R.string.update_external_download))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        when (browser.open(latest.downloadUrl)) {
                            BrowserOpenResult.Dispatched -> viewModel.dismissDetails()
                            is BrowserOpenResult.Failed -> Toast.makeText(context, R.string.update_no_browser, Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text(stringResource(R.string.update_download)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDetails) { Text(stringResource(R.string.update_later)) }
            },
        )
    }
}
