package com.lnxv.player.data.mediastore

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** Bulk share / delete / rename on MediaStore videos (Phase 6). Vault items must never be passed in. */
object MediaActions {
    /** Keeps the original extension, strips path separators and control characters; null when nothing usable is left. */
    fun sanitizeName(input: String, originalName: String): String? {
        val cleaned = input.trim().replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "").trim().trim('.')
        if (cleaned.isEmpty()) return null
        val ext = originalName.substringAfterLast('.', "")
        return if (ext.isNotEmpty() && !cleaned.endsWith(".$ext", ignoreCase = true)) "$cleaned.$ext" else cleaned
    }

    fun shareIntent(uris: List<Uri>): Intent {
        val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        return Intent.createChooser(i.setType("video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null)
    }

    /** API 30+: system confirmation dialog for deleting; null below 30 (use [deleteDirect]). */
    fun deleteRequest(ctx: Context, uris: List<Uri>): IntentSender? =
        if (Build.VERSION.SDK_INT >= 30) MediaStore.createDeleteRequest(ctx.contentResolver, uris).intentSender else null

    /** API 30+: system confirmation to move to (or restore from) the trash; null below 30. */
    fun trashRequest(ctx: Context, uris: List<Uri>, trash: Boolean): IntentSender? =
        if (Build.VERSION.SDK_INT >= 30) MediaStore.createTrashRequest(ctx.contentResolver, uris, trash).intentSender else null

    /** Videos currently in the system trash that this app can see (API 30+); empty below 30 or on any error. */
    fun queryTrashed(ctx: Context): List<com.lnxv.player.core.model.TrashItem> {
        if (Build.VERSION.SDK_INT < 30) return emptyList()
        return runCatching {
            val args = android.os.Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            }
            val out = ArrayList<com.lnxv.player.core.model.TrashItem>()
            ctx.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_EXPIRES),
                args,
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    out += com.lnxv.player.core.model.TrashItem(c.getLong(0), c.getString(1) ?: "", c.getLong(2), c.getLong(3))
                }
            }
            com.lnxv.player.core.model.TrashPolicy.sorted(out)
        }.getOrDefault(emptyList())
    }

    fun uriForId(id: Long): Uri = android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

    fun writeRequest(ctx: Context, uris: List<Uri>): IntentSender? =
        if (Build.VERSION.SDK_INT >= 30) MediaStore.createWriteRequest(ctx.contentResolver, uris).intentSender else null

    /** API 26-29: direct delete; returns the number deleted (files we do not own throw and count as 0). */
    fun deleteDirect(ctx: Context, uris: List<Uri>): Int =
        uris.count { runCatching { ctx.contentResolver.delete(it, null, null) > 0 }.getOrDefault(false) }

    fun rename(ctx: Context, uri: Uri, newDisplayName: String): Boolean = runCatching {
        ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName) }, null, null) > 0
    }.getOrDefault(false)
}
