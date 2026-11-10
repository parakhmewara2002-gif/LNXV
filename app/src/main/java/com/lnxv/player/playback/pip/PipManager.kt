package com.lnxv.player.playback.pip

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi

object PipManager {

    fun isPipSupported(activity: Activity): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun enterPipMode(activity: Activity, videoWidth: Int = 16, videoHeight: Int = 9) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipSupported(activity)) {
            val rational = calculateAspectRatio(videoWidth, videoHeight)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(rational)
                .build()
            activity.enterPictureInPictureMode(params)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculateAspectRatio(width: Int, height: Int): Rational {
        val safeWidth = if (width <= 0) 16 else width
        val safeHeight = if (height <= 0) 9 else height

        val rawRatio = safeWidth.toFloat() / safeHeight.toFloat()
        // Android PiP supports ratios between 1:2.39 (0.418) and 2.39:1 (2.39)
        return when {
            rawRatio < 0.42f -> Rational(42, 100)
            rawRatio > 2.38f -> Rational(238, 100)
            else -> Rational(safeWidth, safeHeight)
        }
    }
}
