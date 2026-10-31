package top.ntutn.sonovelreader.data.update

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process

/** Version and ABI of this installation, rather than the first ABI supported by the device. */
data class InstalledAppInfo(val versionName: String, val versionCode: Long, val arch: String?, val debug: Boolean) {
    companion object {
        @Suppress("DEPRECATION")
        fun read(context: Context): InstalledAppInfo {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            return InstalledAppInfo(
                info.versionName.orEmpty(), info.longVersionCode,
                installedArchitecture(context.applicationInfo.nativeLibraryDir, Process.is64Bit()),
                context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            )
        }
    }
}

internal fun installedArchitecture(nativeLibraryDir: String, is64Bit: Boolean): String? {
    if (!is64Bit) return null
    return when (nativeLibraryDir.substringAfterLast('/')) {
        "arm64" -> "ARM64"
        "x86_64" -> "X86_64"
        else -> null
    }
}
