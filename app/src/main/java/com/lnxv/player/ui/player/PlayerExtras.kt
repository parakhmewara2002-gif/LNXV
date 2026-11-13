package com.lnxv.player.ui.player

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.PlayerView
import com.lnxv.player.core.design.NeonCyan
import com.lnxv.player.core.model.VideoItem

/** Saves the current video frame to Pictures/LNXV (Android 10+). */
object ScreenshotHelper {

    fun capture(context: Context, playerView: PlayerView) {
        val surface = playerView.videoSurfaceView as? SurfaceView
        if (surface == null || surface.width <= 0 || surface.height <= 0) {
            toast(context, "Screenshot not available")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            toast(context, "Screenshot needs Android 10 or newer")
            return
        }
        val bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
        val main = Handler(Looper.getMainLooper())
        PixelCopy.request(surface, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                Thread {
                    val ok = save(context, bitmap)
                    main.post { toast(context, if (ok) "Saved to Pictures/LNXV" else "Screenshot failed") }
                }.start()
            } else {
                toast(context, "Screenshot failed")
            }
        }, main)
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun save(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "LNXV_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LNXV")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                false
            } else {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}

@Composable
fun ResumeDialog(
    positionMs: Long,
    onResume: () -> Unit,
    onStartOver: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onStartOver,
        containerColor = Color(0xFF101622),
        title = { Text("Resume playback?", color = Color.White) },
        text = {
            ImmersiveDialogEffect()
            Text(
                "Continue from ${VideoItem.formatDuration(positionMs)}?",
                color = Color(0xCCFFFFFF)
            )
        },
        confirmButton = {
            TextButton(onClick = onResume) { Text("Resume", color = NeonCyan) }
        },
        dismissButton = {
            TextButton(onClick = onStartOver) { Text("Start over", color = Color(0xCCFFFFFF)) }
        }
    )
}

@Composable
fun SleepTimerDialog(
    activeSeconds: Long?,
    onSelect: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101622),
        title = { Text("Sleep timer", color = Color.White) },
        text = {
            ImmersiveDialogEffect()
            Column {
                if (activeSeconds != null) {
                    Text(
                        "Stops in ${activeSeconds / 60}m ${activeSeconds % 60}s",
                        color = NeonCyan,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                listOf(15, 30, 45, 60, 90).forEach { minutes ->
                    Text(
                        text = "$minutes minutes",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(minutes)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp)
                    )
                }
                if (activeSeconds != null) {
                    Text(
                        text = "Turn off timer",
                        color = Color(0xFFFF6E6E),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCancelTimer()
                                onDismiss()
                            }
                            .padding(vertical = 10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = NeonCyan) }
        }
    )
}

/** Call inside an AlertDialog's content: hides the system bars in the dialog window too. */
@Composable
fun ImmersiveDialogEffect() {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

/** Grabs a tiny snapshot of the video surface; stretched back up it becomes the frosted-glass backdrop. */
object BackdropCapture {
    fun capture(playerView: PlayerView, onResult: (Bitmap?) -> Unit) {
        val surface = playerView.videoSurfaceView as? SurfaceView
        if (surface == null || surface.width <= 0 || surface.height <= 0) {
            onResult(null)
            return
        }
        val targetWidth = 48
        val targetHeight = (surface.height.toFloat() / surface.width * targetWidth).toInt().coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(surface, bitmap, { result ->
                onResult(if (result == PixelCopy.SUCCESS) bitmap else null)
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            onResult(null)
        }
    }
}
