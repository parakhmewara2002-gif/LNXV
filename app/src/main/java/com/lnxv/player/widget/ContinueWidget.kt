package com.lnxv.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.lnxv.player.LNXVApplication
import com.lnxv.player.MainActivity
import com.lnxv.player.R
import kotlinx.coroutines.launch

/** Home-screen "Continue watching" widget: last unfinished non-vault video; tap resumes it. Vault titles are never shown. */
class ContinueWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext as LNXVApplication
        app.appScope.launch {
            try {
                val title = runCatching { app.database.videoDao().getLastUnfinished()?.title }.getOrNull()
                val views = RemoteViews(context.packageName, R.layout.widget_continue)
                views.setTextViewText(R.id.widget_title, title ?: context.getString(R.string.widget_nothing))
                val i = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_CONTINUE)
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally { pending.finish() }
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            val ids = m.getAppWidgetIds(ComponentName(context, ContinueWidget::class.java))
            if (ids.isNotEmpty()) context.sendBroadcast(
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE, null, context, ContinueWidget::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }
}
