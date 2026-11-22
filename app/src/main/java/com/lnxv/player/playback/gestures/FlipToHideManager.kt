package com.lnxv.player.playback.gestures

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Detects when the device is turned face-down (panic/privacy hide gesture).
 * When triggered, instantly calls [onPanicHide].
 */
@Composable
fun FlipToHideEffect(enabled: Boolean = true, onPanicHide: () -> Unit) {
    if (!enabled) return
    val context = LocalContext.current
    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gravitySensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        var triggered = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || triggered) return
                // Z axis points out from the screen.
                // When screen is facing downwards onto a table or held face down, Z < -7.5 m/s^2.
                val z = event.values.getOrNull(2) ?: 0f
                if (z < -7.8f) {
                    triggered = true
                    onPanicHide()
                } else if (z > -2.0f) {
                    triggered = false
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (sm != null && gravitySensor != null) {
            sm.registerListener(listener, gravitySensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sm?.unregisterListener(listener)
        }
    }
}
