package com.lnxv.player.core.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.security.MessageDigest
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
    val notes: String,
    val checksumUrl: String? = null
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
            var sumUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val n = a.getString("name")
                if (n.endsWith(".apk") && url == null) url = a.getString("browser_download_url")
                if (n.endsWith(".apk.sha256")) sumUrl = a.getString("browser_download_url")
            }
            val apkUrl = url ?: throw IOException("No APK in latest release")
            if (!UpdateVerifier.isTrustedUrl(apkUrl)) throw IOException("Untrusted update URL")
            UpdateInfo(
                versionName = tag.removePrefix("v"),
                build = build,
                apkUrl = apkUrl,
                notes = json.optString("body", ""),
                checksumUrl = sumUrl?.takeIf { UpdateVerifier.isTrustedUrl(it) }
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Opens [start], following at most 5 redirects, each of which must stay on a trusted HTTPS host. */
    private fun openTrusted(start: String, readTimeout: Int): HttpURLConnection {
        var url = start
        repeat(6) {
            if (!UpdateVerifier.isTrustedUrl(url)) throw IOException("Blocked untrusted URL")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = readTimeout
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: throw IOException("Bad redirect")
                conn.disconnect()
                url = URL(URL(url), loc).toString()
            } else {
                return conn
            }
        }
        throw IOException("Too many redirects")
    }

    fun isOnUnmeteredNetwork(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    suspend fun download(
        ctx: Context,
        info: UpdateInfo,
        wifiOnly: Boolean,
        verify: Boolean,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (wifiOnly && !isOnUnmeteredNetwork(ctx)) throw IOException("Wi-Fi only: connect to an unmetered network")
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "LNXV-${info.versionName}.apk")

        // Fetch the expected checksum first so a missing/invalid one aborts before the big download.
        val expected: String? = if (verify) {
            val sumUrl = info.checksumUrl ?: throw IOException("Release has no checksum file; refusing to install")
            val c = openTrusted(sumUrl, 15000)
            try {
                if (c.responseCode != 200) throw IOException("Checksum download failed (${c.responseCode})")
                UpdateVerifier.parseChecksum(c.inputStream.bufferedReader().use { it.readText().take(4096) })
                    ?: throw IOException("Invalid checksum file")
            } finally { c.disconnect() }
        } else null

        val conn = openTrusted(info.apkUrl, 30000)
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
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            conn.disconnect()
        }

        if (verify) {
            if (!UpdateVerifier.fileMatches(out, expected!!)) {
                out.delete()
                throw IOException("Checksum mismatch: update deleted")
            }
            if (!signedBySameCertificate(ctx, out)) {
                out.delete()
                throw IOException("Signing certificate does not match the installed app: update deleted")
            }
        }
        out
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(pi: android.content.pm.PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.signingInfo?.apkContentsSigners
        } else {
            pi.signatures
        } ?: return emptySet()
        return sigs.map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    @Suppress("DEPRECATION")
    fun signedBySameCertificate(ctx: Context, apk: File): Boolean {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES
        val candidate = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        if (candidate.packageName != ctx.packageName) return false
        val installed = pm.getPackageInfo(ctx.packageName, flags)
        return UpdateVerifier.sameSigners(signerDigests(installed), signerDigests(candidate))
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
