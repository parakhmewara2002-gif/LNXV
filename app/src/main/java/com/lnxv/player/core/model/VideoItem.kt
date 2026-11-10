package com.lnxv.player.core.model

import android.net.Uri

data class VideoItem(
    val id: Long,
    val title: String,
    val uri: Uri,
    val path: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val dateAddedSec: Long,
    val folderPath: String,
    val folderName: String,
    val lastPositionMs: Long = 0L,
    val lastPlayedTimestamp: Long = 0L,
    val isFavorite: Boolean = false,
    val isVault: Boolean = false
) {
    val durationFormatted: String
        get() = formatDuration(durationMs)

    val sizeFormatted: String
        get() = formatFileSize(sizeBytes)

    val resolutionLabel: String
        get() = when {
            width >= 3840 || height >= 2160 -> "4K"
            width >= 2560 || height >= 1440 -> "2K"
            width >= 1920 || height >= 1080 -> "FHD"
            width >= 1280 || height >= 720 -> "HD"
            else -> "SD"
        }

    companion object {
        fun formatDuration(durationMs: Long): String {
            val totalSeconds = durationMs / 1000
            val seconds = totalSeconds % 60
            val minutes = (totalSeconds / 60) % 60
            val hours = totalSeconds / 3600
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return String.format("%.1f %s", value, units[digitGroups.coerceIn(0, units.size - 1)])
        }
    }
}

data class FolderItem(
    val path: String,
    val name: String,
    val videoCount: Int,
    val totalSizeBytes: Long
) {
    val sizeFormatted: String
        get() = VideoItem.formatFileSize(totalSizeBytes)
}
