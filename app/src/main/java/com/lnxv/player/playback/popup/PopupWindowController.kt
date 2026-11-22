package com.lnxv.player.playback.popup

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.lnxv.player.MainActivity
import com.lnxv.player.R
import com.lnxv.player.core.model.PopupGeometry
import com.lnxv.player.playback.service.BackgroundPlaybackConnector

/**
 * A small draggable floating window that shows the SAME player the full screen uses. Needs the user to allow
 * "Display over other apps" (SYSTEM_ALERT_WINDOW, declared in the sideload manifest only). One window at a time.
 */
object PopupWindowController {
    private var root: FrameLayout? = null
    private var playerView: PlayerView? = null
    private var player: Player? = null
    private var listener: Player.Listener? = null
    private var connector: BackgroundPlaybackConnector? = null

    /** Called after the popup closes so the full-screen PlayerView can take the video surface back. */
    var onClosed: (() -> Unit)? = null

    val isShowing: Boolean get() = root != null

    fun canShow(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun requestPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show(context: Context, p: Player): Boolean {
        val ctx = context.applicationContext
        if (!canShow(ctx) || root != null) return false
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = ctx.resources.displayMetrics
        val size = p.videoSize
        val (w, h) = PopupGeometry.size(size.width, size.height, (dm.widthPixels * 0.6f).toInt(), (dm.heightPixels * 0.4f).toInt())

        val pv = PlayerView(ctx).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setBackgroundColor(Color.BLACK)
            this.player = p
        }
        val box = FrameLayout(ctx).apply { addView(pv, FrameLayout.LayoutParams(-1, -1)) }

        fun chip(text: String, desc: String, gravity: Int, action: () -> Unit) = TextView(ctx).apply {
            this.text = text
            contentDescription = desc
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(0x99000000.toInt())
            setPadding(24, 8, 24, 8)
            setOnClickListener { action() }
            box.addView(this, FrameLayout.LayoutParams(-2, -2, gravity))
        }
        chip("✕", ctx.getString(R.string.popup_close), Gravity.TOP or Gravity.END) { hide() }
        chip("⤢", ctx.getString(R.string.popup_restore), Gravity.TOP or Gravity.START) {
            ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            hide()
        }

        val lp = WindowManager.LayoutParams(
            w, h,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels - w - 32
            y = dm.heightPixels / 6
        }

        // Drag by touching the video; a short tap toggles play/pause.
        val slop = ViewConfiguration.get(ctx).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        pv.setOnTouchListener { _: View, e: MotionEvent ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt(); val dy = (e.rawY - downY).toInt()
                    if (!moved && (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop)) moved = true
                    if (moved) {
                        val (nx, ny) = PopupGeometry.clamp(startX + dx, startY + dy, lp.width, lp.height, dm.widthPixels, dm.heightPixels)
                        lp.x = nx; lp.y = ny
                        runCatching { wm.updateViewLayout(box, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) { if (p.isPlaying) p.pause() else p.play() }; true }
                else -> false
            }
        }

        try {
            wm.addView(box, lp)
        } catch (e: Exception) {
            return false
        }
        root = box; playerView = pv; player = p
        listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) hide() }
        }.also { p.addListener(it) }
        // Keep the process alive and show the media notification while the app is in the background.
        connector = BackgroundPlaybackConnector(ctx).also { it.connect() }
        return true
    }

    fun hide() {
        val box = root ?: return
        val ctx = box.context
        runCatching { (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(box) }
        playerView?.player = null
        listener?.let { player?.removeListener(it) }
        connector?.disconnect()
        root = null; playerView = null; player = null; listener = null; connector = null
        onClosed?.invoke()
    }
}
