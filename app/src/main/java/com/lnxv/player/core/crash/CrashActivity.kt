package com.lnxv.player.core.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Plain-Views crash screen (no Compose / Koin / DB) running in the ":crash" process, so it still opens
 * when the main app crashes during startup.
 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra(CrashReporter.EXTRA_TRACE)
            ?: CrashReporter.readSaved(application) ?: "No crash details were saved."

        val pad = (16 * resources.displayMetrics.density).toInt()
        val title = TextView(this).apply {
            text = "LNXV crashed"
            textSize = 20f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        }
        val body = TextView(this).apply {
            text = trace
            textSize = 11f; setTextColor(Color.parseColor("#D0D7E2")); typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(body) }
        val copy = Button(this).apply {
            text = "Copy"
            setOnClickListener {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("LNXV crash", trace))
                Toast.makeText(this@CrashActivity, "Copied", Toast.LENGTH_SHORT).show()
            }
        }
        val share = Button(this).apply {
            text = "Share"
            setOnClickListener {
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, trace), "Share crash report"))
            }
        }
        val close = Button(this).apply { text = "Close"; setOnClickListener { finishAndRemoveTask() } }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(copy, lp); addView(share, lp); addView(close, lp)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1020"))
            setPadding(pad, pad * 2, pad, pad)
            addView(title)
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(buttons)
        })
    }
}
