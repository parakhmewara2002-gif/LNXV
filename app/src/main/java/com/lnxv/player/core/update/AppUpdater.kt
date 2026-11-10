package com.lnxv.player.core.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val build: Int,
    val apkUrl: String,
    val notes: String
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

object AppUpdater {
    private const val LATEST_URL =
        "https://api.github.com/repos/parakhmewara2002-gif/LNXV/releases/latest"

    @Suppress("DEPRECATION")
    fun currentVersionName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0.0"

    fun currentBuild(ctx: Context): Int =
        currentVersionName(ctx).substringAfterLast('.').toIntOrNull() ?: 0

    /** Returns UpdateInfo if a newer release exists, null if already up to date. */
    suspend fun check(ctx: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = URL(LATEST_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode != 200) {
                throw IOException("Update server error (${conn.responseCode})")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            val build = tag.substringAfterLast('.').toIntOrNull()
                ?: throw IOException("Bad release tag: $tag")
            if (build <= currentBuild(ctx)) return@withContext null
            val assets = json.getJSONArray("assets")
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    url = a.getString("browser_download_url")
                    break
                }
            }
            url ?: throw IOException("No APK in latest release")
            UpdateInfo(
                versionName = tag.removePrefix("v"),
                build = build,
                apkUrl = url,
                notes = json.optString("body", "")
            )
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(
        ctx: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "LNXV-${info.versionName}.apk")
        val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        try {
            if (conn.responseCode != 200) throw IOException("Download failed (${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 }
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total != null) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct / 100f)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        out
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(ctx: Context) {
        val i = Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            android.net.Uri.parse("package:${ctx.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
