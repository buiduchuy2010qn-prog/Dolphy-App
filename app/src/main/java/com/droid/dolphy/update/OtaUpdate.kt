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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
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

data class OtaRelease(
    val tag: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
)

sealed class OtaState {
    data object Idle : OtaState()
    data object Checking : OtaState()
    data class UpToDate(val current: String) : OtaState()
    data class Available(val current: String, val release: OtaRelease) : OtaState()
    data class Downloading(val release: OtaRelease, val progress: Float) : OtaState()
    data class ReadyToInstall(val release: OtaRelease, val file: File) : OtaState()
    data class Error(val message: String) : OtaState()
}

object OtaController {
    private const val OWNER_REPO = "buiduchuy2010qn-prog/Dolphy-App"
    private const val API = "https://api.github.com/repos/$OWNER_REPO/releases/latest"
    private const val PREFS = "DolphyPrefs"
    private const val LAST_CHECK = "ota_last_check_ms"
    private const val AUTO_INTERVAL_MS = 12L * 60 * 60 * 1000
    private const val UA = "Dolphy-VN-OTA"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<OtaState>(OtaState.Idle)
    val state: StateFlow<OtaState> = _state

    fun autoCheck(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < AUTO_INTERVAL_MS) return
        check(context, manual = false)
    }

    fun check(context: Context, manual: Boolean) {
        if (_state.value is OtaState.Checking || _state.value is OtaState.Downloading) return
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
            _state.value = OtaState.Downloading(release, 0f)
            runCatching {
                val dir = File(context.cacheDir, "ota").apply { mkdirs() }
                val dest = File(dir, release.apkName.ifBlank { "Dolphy-VN-update.apk" })
                downloadFile(release.apkUrl, dest) { read, total ->
                    val p = if (total > 0) (read.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                    _state.value = OtaState.Downloading(release, p)
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

    fun dismiss() {
        if (_state.value !is OtaState.Downloading) {
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
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url")
                apkName = name
                apkSize = asset.optLong("size")
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
    val state by OtaController.state.collectAsState()

    LaunchedEffect(Unit) {
        OtaController.autoCheck(context.applicationContext)
    }

    when (val s = state) {
        is OtaState.Available -> OtaDialog(
            title = if (isVi) "Có bản cập nhật mới" else "Update available",
            body = buildString {
                append(if (isVi) "Hiện tại: " else "Current: ")
                append(s.current)
                append('\n')
                append(if (isVi) "Bản mới: " else "Latest: ")
                append(s.release.title.ifBlank { s.release.tag })
                if (s.release.notes.isNotBlank()) {
                    append("\n\n")
                    append(s.release.notes.take(900))
                }
            },
            confirm = if (isVi) "Tải và cài" else "Download & install",
            dismiss = if (isVi) "Để sau" else "Later",
            onConfirm = { OtaController.download(context, s.release) },
            onDismiss = { OtaController.dismiss() },
        )
        is OtaState.Downloading -> OtaDialog(
            title = if (isVi) "Đang tải bản cập nhật" else "Downloading update",
            body = s.release.title,
            confirm = null,
            dismiss = null,
            progress = s.progress,
            onConfirm = {},
            onDismiss = {},
        )
        is OtaState.ReadyToInstall -> OtaDialog(
            title = if (isVi) "Sẵn sàng cài đặt" else "Ready to install",
            body = if (isVi)
                "File APK đã tải xong. Nếu hệ thống hỏi, hãy cho phép cài từ nguồn này."
            else
                "APK downloaded. Allow installs from this source if Android asks.",
            confirm = if (isVi) "Cài đặt" else "Install",
            dismiss = if (isVi) "Đóng" else "Close",
            onConfirm = { OtaController.installApk(context, s.file) },
            onDismiss = { OtaController.dismiss() },
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
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(body)
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("${(progress * 100).toInt()}%")
                }
            }
        },
        confirmButton = {
            if (confirm != null) {
                TextButton(onClick = onConfirm) { Text(confirm) }
            }
        },
        dismissButton = {
            if (dismiss != null) {
                TextButton(onClick = onDismiss) { Text(dismiss) }
            }
        }
    )
}
