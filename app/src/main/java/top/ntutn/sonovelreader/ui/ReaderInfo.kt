package top.ntutn.sonovelreader.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Date

internal data class ReaderDisplayInfo(
    val chapterOffset: Int = 0,
    val page: Int? = null,
    val pageCount: Int? = null,
    val percent: Int? = null,
)

internal fun readerPercent(fraction: Float, atEnd: Boolean): Int? = when {
    atEnd -> 100
    !fraction.isFinite() -> null
    else -> (fraction.coerceIn(0f, 1f) * 100).toInt()
}

internal fun batteryPercent(level: Int, scale: Int): Int? =
    if (level < 0 || scale <= 0) null else (level.toLong() * 100 / scale).coerceIn(0, 100).toInt()

internal val LocalReaderPadding = staticCompositionLocalOf {
    PaddingValues(start = 22.dp, end = 22.dp, top = 84.dp, bottom = 92.dp)
}

internal data class ReaderPageDecoration(
    val start: Dp,
    val end: Dp,
    val top: Dp,
    val bottom: Dp,
    val twoRows: Boolean,
    val titles: Map<Int, String>,
    val device: ReaderDeviceStatus,
    val palette: ReaderPalette,
)

internal val LocalReaderPageDecoration = compositionLocalOf<ReaderPageDecoration?> { null }

/** Rendered inside each pager item / curl texture, so both sides retain their own labels. */
@Composable
internal fun ReaderPageInfo(
    entry: ChapterPage,
    accessible: Boolean = true,
    decoration: ReaderPageDecoration? = LocalReaderPageDecoration.current,
) {
    if (decoration == null) return
    Box(if (accessible) Modifier.fillMaxSize() else Modifier.fillMaxSize().clearAndSetSemantics {}) {
        ReaderInfoLayer(decoration.titles[entry.chapterOffset] ?: entry.chapterTitle,
            false, entry.displayInfo(), decoration)
    }
}

/** Stable insets are shared by page measurement and rendering, including when controls are visible. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderInfoLayout(
    visible: Boolean,
    chapterTitle: String,
    scrolling: Boolean,
    info: ReaderDisplayInfo?,
    palette: ReaderPalette,
    pageAttached: Boolean = !scrolling,
    chapterTitles: Map<Int, String> = mapOf(0 to chapterTitle),
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("reader-layout")) {
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val safe = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
        val left = with(density) { safe.getLeft(density, direction).toDp() }
        val right = with(density) { safe.getRight(density, direction).toDp() }
        val start = if (direction == androidx.compose.ui.unit.LayoutDirection.Ltr) left else right
        val end = if (direction == androidx.compose.ui.unit.LayoutDirection.Ltr) right else left
        val top = with(density) { safe.getTop(density).toDp() }
        val bottom = with(density) { safe.getBottom(density).toDp() }
        // Reserve for the longest labels, independently of the changing clock/battery values.
        val twoRows = maxWidth - left - right - 44.dp < 320.dp * density.fontScale
        val lineHeight = with(density) { 20.sp.toDp() }.coerceAtLeast(24.dp)
        val topSpace = (top + lineHeight + 12.dp).coerceAtLeast(84.dp)
        val bottomSpace = (bottom + lineHeight * (if (twoRows) 2 else 1) + 12.dp).coerceAtLeast(92.dp)
        // One subscription per reader, shared by every composed/pre-rendered page.
        val device = if (visible) rememberReaderDeviceStatus() else ReaderDeviceStatus()
        val decoration = if (visible) ReaderPageDecoration(start, end, top, bottom, twoRows, chapterTitles, device, palette) else null
        CompositionLocalProvider(LocalReaderPadding provides PaddingValues(
            start = start + 22.dp, end = end + 22.dp, top = topSpace, bottom = bottomSpace,
        ), LocalReaderPageDecoration provides decoration.takeIf { pageAttached }) { content() }
        if (!pageAttached && decoration != null) {
            ReaderInfoLayer(chapterTitle, scrolling, info, decoration)
        }
    }
}

@Composable
private fun ReaderInfoLayer(chapterTitle: String, scrolling: Boolean, info: ReaderDisplayInfo?, decoration: ReaderPageDecoration) {
    val (start, end, top, bottom, twoRows, _, device, palette) = decoration
    Box(Modifier.fillMaxSize()) {
            Text(
                chapterTitle, color = palette.muted, fontSize = 12.sp, lineHeight = 20.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopStart).padding(start = start + 22.dp, end = end + 22.dp, top = top + 6.dp)
                    .testTag("reader-info-chapter")
                    .clearAndSetSemantics { contentDescription = chapterTitle },
            )
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(start = start + 22.dp, end = end + 22.dp, bottom = bottom + 6.dp)) {
                if (twoRows) {
                    ReaderProgressLabel(scrolling, info, palette)
                    ReaderDeviceLabel(device, palette, Modifier.align(Alignment.End))
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        ReaderProgressLabel(scrolling, info, palette)
                        ReaderDeviceLabel(device, palette)
                    }
                }
            }
    }
}

@Composable
private fun ReaderProgressLabel(scrolling: Boolean, info: ReaderDisplayInfo?, palette: ReaderPalette) {
    val text = if (scrolling) "本章 ${info?.percent ?: "—"}%"
        else "本章 ${info?.page ?: "—"} / ${info?.pageCount ?: "—"} 页"
    val description = if (!scrolling && info?.page != null && info.pageCount != null)
        "本章第 ${info.page} 页，共 ${info.pageCount} 页" else text
    Text(text, color = palette.muted, fontSize = 12.sp, lineHeight = 20.sp, maxLines = 1,
        modifier = Modifier.testTag("reader-info-progress").clearAndSetSemantics { contentDescription = description })
}

internal data class ReaderDeviceStatus(val time: String = "", val battery: Int? = null, val charging: Boolean = false)

@Composable
private fun rememberReaderDeviceStatus(): ReaderDeviceStatus {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var status by remember { mutableStateOf(ReaderDeviceStatus()) }
    DisposableEffect(context, lifecycle) {
        var registered = false
        fun refresh(intent: Intent? = null) {
            val battery = intent?.takeIf { it.action == Intent.ACTION_BATTERY_CHANGED }
                ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val chargeStatus = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            status = ReaderDeviceStatus(
                time = DateFormat.getTimeFormat(context).format(Date()),
                battery = batteryPercent(battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1,
                    battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1),
                charging = chargeStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                    (chargeStatus == BatteryManager.BATTERY_STATUS_FULL &&
                        battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0),
            )
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = refresh(intent)
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        fun stop() {
            if (!registered) return
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
            registered = false
        }
        fun start() {
            if (registered) return
            refresh()
            ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_LOCALE_CHANGED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            context.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24), false, observer)
            registered = true
        }
        val lifecycleObserver = LifecycleEventObserver { _, _ ->
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start() else stop()
        }
        lifecycle.addObserver(lifecycleObserver)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose { lifecycle.removeObserver(lifecycleObserver); stop() }
    }
    return status
}

@Composable
internal fun ReaderDeviceLabel(status: ReaderDeviceStatus, palette: ReaderPalette, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(status.time, fontSize = 12.sp, lineHeight = 20.sp, color = palette.muted, maxLines = 1)
        Row(Modifier.clearAndSetSemantics {
            contentDescription = "电量 ${status.battery?.let { "$it%" } ?: "未知"}" + if (status.charging) "，充电中" else ""
        }, verticalAlignment = Alignment.CenterVertically) {
            Icon(if (status.charging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                contentDescription = null, tint = palette.muted, modifier = Modifier.size(16.dp))
            Text("${status.battery ?: "—"}%", fontSize = 12.sp, lineHeight = 20.sp, color = palette.muted, maxLines = 1)
        }
    }
}
