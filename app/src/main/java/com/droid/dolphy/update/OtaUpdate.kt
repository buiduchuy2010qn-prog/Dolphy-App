package com.droid.dolphy.update

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class OtaRelease(
    val tag: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    val assetId: Long = 0L,
)

sealed class OtaState {
    data object Idle : OtaState()
    data object Checking : OtaState()
    data class UpToDate(val current: String) : OtaState()
    data class Available(val current: String, val release: OtaRelease) : OtaState()
    data class Downloading(
        val release: OtaRelease,
        val progress: Float,
        val readBytes: Long = 0L,
        val totalBytes: Long = 0L
    ) : OtaState()
    data class ReadyToInstall(val release: OtaRelease, val file: File) : OtaState()
    data class Error(val message: String) : OtaState()
}

object OtaController {
    private const val OWNER_REPO = "buiduchuy2010qn-prog/Dolphy-App"
    private const val API = "https://api.github.com/repos/$OWNER_REPO/releases/latest"
    private const val PREFS = "DolphyPrefs"
    private const val LAST_CHECK = "ota_last_check_ms"
    private const val AUTO_INTERVAL_MS = 60L * 1000L // Check on startup (1-minute cooldown)
    private const val UA = "Dolphy-VN-OTA"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<OtaState>(OtaState.Idle)
    val state: StateFlow<OtaState> = _state

    /**
     * Automatically deletes any leftover OTA `.apk` or `.part` files in `cacheDir/ota` once installed,
     * freeing ~61–75 MB of internal storage immediately!
     */
    fun cleanupInstalledOtaApks(context: Context, notifyToast: Boolean = true) {
        if (_state.value is OtaState.Downloading) return
        scope.launch {
            val otaDir = File(context.cacheDir, "ota")
            if (!otaDir.exists()) return@launch
            val currentVer = currentVersion(context)
            val readyState = _state.value as? OtaState.ReadyToInstall
            var freedBytes = 0L

            otaDir.listFiles()?.forEach { f ->
                // If we are still waiting to install a newer version, keep only that specific file until installed
                val isPendingNewerInstall = readyState != null &&
                    readyState.file.absolutePath == f.absolutePath &&
                    isNewer(readyState.release.tag, currentVer)

                if (!isPendingNewerInstall) {
                    val len = f.length()
                    if (runCatching { f.delete() }.getOrDefault(false)) {
                        freedBytes += len
                    }
                }
            }

            if (readyState != null && !isNewer(readyState.release.tag, currentVer)) {
                _state.value = OtaState.Idle
            }

            if (freedBytes > 1024 * 1024 && notifyToast) {
                val freedMb = freedBytes / (1024.0 * 1024.0)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        if (isVi(context)) {
                            String.format(Locale.US, "🧹 Đã tự động xóa file APK cập nhật cũ (Giải phóng %.1f MB)", freedMb)
                        } else {
                            String.format(Locale.US, "🧹 Deleted old OTA APK file (Freed %.1f MB)", freedMb)
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun autoCheck(context: Context) {
        // Always clean up any old downloaded OTA APK files first on app launch!
        cleanupInstalledOtaApks(context, notifyToast = true)

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < AUTO_INTERVAL_MS) return
        check(context, manual = false)
    }

    fun check(context: Context, manual: Boolean) {
        if (_state.value is OtaState.Checking || _state.value is OtaState.Downloading) return
        cleanupInstalledOtaApks(context, notifyToast = false)
        scope.launch {
            _state.value = OtaState.Checking
            val current = currentVersion(context)
            runCatching { fetchLatest() }
                .onSuccess { release ->
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                        putLong(LAST_CHECK, System.currentTimeMillis())
                    }
                    if (release == null || !isNewer(release.tag, current)) {
                        _state.value = OtaState.UpToDate(current)
                        if (manual) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    if (isVi(context)) "Bạn đang dùng bản mới nhất ($current)"
                                    else "You are on the latest version ($current)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            _state.value = OtaState.Idle
                        }
                    } else {
                        _state.value = OtaState.Available(current, release)
                    }
                }
                .onFailure { err ->
                    if (manual) {
                        _state.value = OtaState.Error(err.message ?: "OTA error")
                    } else {
                        _state.value = OtaState.Idle
                    }
                }
        }
    }

    fun download(context: Context, release: OtaRelease) {
        if (_state.value is OtaState.Downloading) return
        scope.launch {
            _state.value = OtaState.Downloading(release, 0f, 0L, release.apkSize)
            runCatching {
                val dir = File(context.cacheDir, "ota").apply { mkdirs() }
                // Clean any previous APK/part files before downloading
                dir.listFiles()?.forEach { runCatching { it.delete() } }
                val dest = File(dir, release.apkName.ifBlank { "Dolphy-VN-update.apk" })
                downloadFile(release.apkUrl, dest) { read, total ->
                    val effectiveTotal = if (total > 0) total else release.apkSize
                    val p = if (effectiveTotal > 0) (read.toFloat() / effectiveTotal.toFloat()).coerceIn(0f, 1f) else 0f
                    _state.value = OtaState.Downloading(release, p, read, effectiveTotal)
                }
                dest
            }.onSuccess { file ->
                _state.value = OtaState.ReadyToInstall(release, file)
                withContext(Dispatchers.Main) { installApk(context, file) }
            }.onFailure { err ->
                _state.value = OtaState.Error(err.message ?: "Download failed")
            }
        }
    }

    fun installApk(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(
                context,
                if (isVi(context)) "Hãy cho phép cài đặt từ nguồn này, rồi bấm Cài đặt lại"
                else "Allow installs from this source, then tap Install again",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun dismiss(deleteReadyFile: Boolean = false) {
        val current = _state.value
        if (current is OtaState.ReadyToInstall && deleteReadyFile) {
            runCatching { current.file.delete() }
        }
        if (current !is OtaState.Downloading) {
            _state.value = OtaState.Idle
        }
    }

    fun currentVersion(context: Context): String {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        }.getOrDefault("0")
    }

    fun isVi(context: Context): Boolean {
        val language = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("app_language", "vi") ?: "vi"
        return language == "vi"
    }

    private fun fetchLatest(): OtaRelease? {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", UA)
            instanceFollowRedirects = true
        }
        val body = try {
            if (conn.responseCode !in 200..299) error("GitHub HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val json = JSONObject(body)
        if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) return null
        val assets = json.optJSONArray("assets") ?: return null
        var apkUrl = ""
        var apkName = "Dolphy-VN-update.apk"
        var apkSize = 0L
        var assetId = 0L
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url")
                apkName = name
                apkSize = asset.optLong("size")
                assetId = asset.optLong("id")
                if (name.contains("Dolphy-VN", ignoreCase = true)) break
            }
        }
        if (apkUrl.isBlank()) return null
        return OtaRelease(
            tag = json.optString("tag_name"),
            title = json.optString("name").ifBlank { json.optString("tag_name") },
            notes = json.optString("body").replace("\r\n", "\n").trim(),
            apkUrl = apkUrl,
            apkName = apkName,
            apkSize = apkSize,
            assetId = assetId,
        )
    }

    private fun downloadFile(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", UA)
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) error("Download HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.let { if (it > 0) it else -1L }
            dest.parentFile?.mkdirs()
            val tmp = File(dest.absolutePath + ".part")
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var readTotal = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        readTotal += n
                        onProgress(readTotal, total)
                    }
                }
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } finally {
            conn.disconnect()
        }
    }

    internal fun isNewer(remoteTag: String, localVersion: String): Boolean {
        return versionKey(remoteTag) > versionKey(localVersion)
    }

    private fun versionKey(raw: String): Int {
        val nums = Regex("""\d+""").findAll(raw).mapNotNull { it.value.toIntOrNull() }.toList()
        val major = nums.getOrNull(0) ?: 0
        val minor = nums.getOrNull(1) ?: 0
        val patch = nums.getOrNull(2) ?: 0
        return major * 10_000 + minor * 100 + patch
    }
}

@Composable
fun OtaUpdateHost(isVi: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by OtaController.state.collectAsState()

    LaunchedEffect(Unit) {
        OtaController.autoCheck(context.applicationContext)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                OtaController.cleanupInstalledOtaApks(context.applicationContext, notifyToast = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (val s = state) {
        is OtaState.Available -> OtaDialog(
            title = if (isVi) "🚀 Có bản cập nhật mới (${s.release.tag})" else "🚀 Update available (${s.release.tag})",
            body = buildString {
                append(if (isVi) "Phiên bản hiện tại: " else "Current version: ")
                append(s.current)
                append('\n')
                append(if (isVi) "Phiên bản mới: " else "Latest version: ")
                append(s.release.title.ifBlank { s.release.tag })
                if (s.release.apkSize > 0) {
                    val sizeMb = s.release.apkSize / (1024.0 * 1024.0)
                    append(String.format(Locale.US, " (%.1f MB)", sizeMb))
                }
                if (s.release.notes.isNotBlank()) {
                    append("\n\n")
                    append(s.release.notes.take(900))
                }
            },
            confirm = if (isVi) "Tải & Cài đè ngay" else "Download & Install",
            dismiss = if (isVi) "Để sau" else "Later",
            onConfirm = { OtaController.download(context, s.release) },
            onDismiss = { OtaController.dismiss() },
        )
        is OtaState.Downloading -> {
            val readMb = s.readBytes / (1024.0 * 1024.0)
            val totalMb = s.totalBytes / (1024.0 * 1024.0)
            val detail = if (s.totalBytes > 0) {
                String.format(Locale.US, "%.1f MB / %.1f MB (%d%%)", readMb, totalMb, (s.progress * 100).toInt())
            } else {
                "${(s.progress * 100).toInt()}%"
            }
            OtaDialog(
                title = if (isVi) "⏳ Đang tải bản cập nhật OTA..." else "⏳ Downloading OTA update...",
                body = "${s.release.title}\n$detail",
                confirm = null,
                dismiss = null,
                progress = s.progress,
                onConfirm = {},
                onDismiss = {},
            )
        }
        is OtaState.ReadyToInstall -> OtaDialog(
            title = if (isVi) "✅ Đã tải xong — Sẵn sàng cài đè" else "✅ Ready to install",
            body = if (isVi)
                "Gói cập nhật ${s.release.tag} đã tải xong. Bấm «Cài đặt ngay» để cập nhật trực tiếp (file APK tạm sẽ tự động xóa sau khi cài xong)."
            else
                "APK ${s.release.tag} downloaded. Tap 'Install Now' to update (temp APK will be auto-deleted after install).",
            confirm = if (isVi) "Cài đặt ngay" else "Install Now",
            dismiss = if (isVi) "Hủy & Xóa APK" else "Cancel & Delete APK",
            onConfirm = { OtaController.installApk(context, s.file) },
            onDismiss = { OtaController.dismiss(deleteReadyFile = true) },
        )
        is OtaState.Error -> OtaDialog(
            title = if (isVi) "Không kiểm tra được cập nhật" else "Update check failed",
            body = s.message,
            confirm = if (isVi) "Thử lại" else "Retry",
            dismiss = if (isVi) "Đóng" else "Close",
            onConfirm = { OtaController.check(context, manual = true) },
            onDismiss = { OtaController.dismiss() },
        )
        else -> Unit
    }
}

@Composable
private fun OtaDialog(
    title: String,
    body: String,
    confirm: String?,
    dismiss: String?,
    progress: Float? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (dismiss != null) onDismiss() },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (confirm != null) {
                TextButton(onClick = onConfirm) { Text(confirm, fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = {
            if (dismiss != null) {
                TextButton(onClick = onDismiss) { Text(dismiss) }
            }
        }
    )
}
