package com.lnxv.player.core.crash

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import kotlin.system.exitProcess

/**
 * Last-resort crash capture for devices without a PC/adb. Installed as early as possible (attachBaseContext);
 * on an uncaught exception it writes the trace to filesDir/last_crash.txt and opens [CrashActivity] in a
 * separate process (":crash") so the error can be read, copied or shared even if the main UI crashes at launch.
 */
object CrashReporter {
    const val EXTRA_TRACE = "trace"
    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_EXTRA_CHARS = 60_000
    @Volatile private var handling = false

    fun install(app: Application) {
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (handling) { // a crash while reporting: give up quietly
                Process.killProcess(Process.myPid()); exitProcess(10)
            }
            handling = true
            try {
                val trace = buildReport(thread, error)
                runCatching { File(app.filesDir, FILE_NAME).writeText(trace) }
                val intent = Intent(app, CrashActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(EXTRA_TRACE, trace.take(MAX_EXTRA_CHARS))
                app.startActivity(intent)
                Thread.sleep(400) // let the system accept the start request before the process dies
            } catch (_: Throwable) {
                // never throw from the crash handler
            } finally {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    /** Previous crash report saved on disk, if any. */
    fun readSaved(app: Application): String? =
        runCatching { File(app.filesDir, FILE_NAME).takeIf { it.exists() }?.readText() }.getOrNull()

    /** True inside the ":crash" process, where the normal app startup (Koin, DB, player) must be skipped. */
    fun isCrashProcess(app: Application): Boolean {
        val name = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName()
        else runCatching { File("/proc/self/cmdline").readText().trim('\u0000') }.getOrNull()
        return name?.endsWith(":crash") == true
    }

    private fun buildReport(thread: Thread, error: Throwable): String = buildString {
        appendLine("LNXV crash report")
        appendLine("Time: ${java.util.Date()}")
        appendLine("Thread: ${thread.name}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) | ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine()
        appendLine("Exception chain:")
        var t: Throwable? = error
        var depth = 0
        while (t != null && depth < 8) {
            appendLine("  ${t.javaClass.name}: ${t.message}")
            t = t.cause; depth++
        }
        appendLine()
        appendLine(Log.getStackTraceString(error).take(30_000))
    }
}
