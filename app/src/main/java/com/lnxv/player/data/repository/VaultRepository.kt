package com.lnxv.player.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Base64
import com.lnxv.player.core.database.VaultThumbEntity
import com.lnxv.player.core.database.VideoDao
import com.lnxv.player.core.database.VideoEntity
import com.lnxv.player.core.datastore.UserPreferencesDataStore
import com.lnxv.player.core.model.VideoItem
import com.lnxv.player.core.security.ChunkedAead
import com.lnxv.player.core.security.LockoutPolicy
import com.lnxv.player.core.security.PinHasher
import com.lnxv.player.core.security.VaultKeyManager
import com.lnxv.player.playback.engine.VaultRoutingDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

sealed class VerifyResult {
    object Success : VerifyResult()
    data class Wrong(val attemptsUntilLock: Int) : VerifyResult()
    data class Locked(val remainingMs: Long) : VerifyResult()
}

class NotEnoughStorageException(val neededBytes: Long, val availableBytes: Long) :
    IOException("Not enough storage: need ${neededBytes - availableBytes} more bytes")

/** Result of a move: [deleteConsent] is non-null when the user must approve deleting the original. */
data class MoveResult(val deleteConsent: IntentSender?)

class VaultRepository(
    private val context: Context,
    private val videoDao: VideoDao,
    private val dataStore: UserPreferencesDataStore,
    private val keys: VaultKeyManager
) {
    private val vaultDir: File get() = VaultFiles.dir(context)

    val isUnlocked: Boolean get() = keys.isUnlocked

    val vaultVideosFlow: Flow<List<VideoItem>> = videoDao.getVaultVideos().map { entities ->
        entities.map { it.toModel() }
    }

    val legacyCountFlow: Flow<Int> = videoDao.countLegacyVault()

    // ---------------------------------------------------------------- PIN / session

    suspend fun isPinSet(): Boolean = dataStore.userPreferencesFlow.first().hasVaultCredential

    suspend fun pinLength(): Int = dataStore.userPreferencesFlow.first().vaultPinLength

    suspend fun lockoutRemainingMs(): Long =
        LockoutPolicy.remainingMs(dataStore.userPreferencesFlow.first().lockoutState, System.currentTimeMillis())

    suspend fun setPin(pin: String) = withContext(Dispatchers.Default) {
        require(PinHasher.isValidPin(pin)) { "PIN must be 4-8 digits" }
        val salt = PinHasher.newSalt()
        val iterations = PinHasher.DEFAULT_ITERATIONS
        val derived = PinHasher.hash(pin, salt, iterations)
        dataStore.saveVaultCredential(b64(salt), b64(derived), iterations, pin.length)
        keys.ensureKey()
        keys.unlock()
    }

    suspend fun verifyPin(entered: String): VerifyResult = withContext(Dispatchers.Default) {
        val prefs = dataStore.userPreferencesFlow.first()
        val now = System.currentTimeMillis()
        val remaining = LockoutPolicy.remainingMs(prefs.lockoutState, now)
        if (remaining > 0) return@withContext VerifyResult.Locked(remaining)

        val ok = when {
            prefs.vaultPinDerived.isNotEmpty() ->
                PinHasher.verify(
                    entered, Base64.decode(prefs.vaultPinSalt, Base64.NO_WRAP),
                    Base64.decode(prefs.vaultPinDerived, Base64.NO_WRAP), prefs.vaultPinIterations
                )
            prefs.vaultPinHash.isNotEmpty() -> PinHasher.verifyLegacy(entered, prefs.vaultPinHash)
            else -> false
        }
        if (ok) {
            if (prefs.vaultPinDerived.isEmpty()) setPin(entered) // migrate legacy hash to PBKDF2
            else { keys.ensureKey(); keys.unlock() }
            dataStore.saveLockout(LockoutPolicy.onSuccess())
            VerifyResult.Success
        } else {
            val next = LockoutPolicy.onFailure(prefs.lockoutState, now)
            dataStore.saveLockout(next)
            if (LockoutPolicy.isLocked(next, now)) VerifyResult.Locked(LockoutPolicy.remainingMs(next, now))
            else VerifyResult.Wrong(LockoutPolicy.FREE_ATTEMPTS - next.failedAttempts)
        }
    }

    /**
     * Change PIN: verifies the old PIN (counts toward lockout) and stores a new PBKDF2 credential.
     * The master key is wrapped by the Keystore, not derived from the PIN, so no file re-encryption is needed.
     */
    suspend fun changePin(oldPin: String, newPin: String): VerifyResult {
        if (!PinHasher.isValidPin(newPin)) return VerifyResult.Wrong(LockoutPolicy.FREE_ATTEMPTS)
        val r = verifyPin(oldPin)
        if (r is VerifyResult.Success) setPin(newPin)
        return r
    }

    /** Clears the in-memory key. Callers also cancel running transfers and drop vault UI state. */
    fun lock() = keys.lock()

    /** Forgot PIN: irreversibly deletes the key, all encrypted files, thumbnails and vault rows. */
    suspend fun wipe() = withContext(Dispatchers.IO) {
        keys.destroy()
        vaultDir.listFiles()?.forEach { if (it.name != ".nomedia") it.delete() }
        videoDao.clearThumbs()
        videoDao.deleteAllVaultRows()
        dataStore.clearVaultCredential()
    }

    // ---------------------------------------------------------------- move

    suspend fun moveToVault(videoId: Long, onProgress: (Float) -> Unit = {}): MoveResult =
        withContext(Dispatchers.IO) {
            val entity = videoDao.getVideoById(videoId) ?: throw IOException("Video not found")
            if (entity.vaultFile != null) return@withContext MoveResult(null) // already encrypted
            keys.ensureKey(); keys.unlock()
            val source = Uri.parse(entity.uriString)
            val plainSize = querySize(source).takeIf { it > 0 } ?: entity.sizeBytes
            val needed = ChunkedAead.encryptedSize(plainSize) + SAFETY_MARGIN
            val available = StatFs(context.filesDir.absolutePath).availableBytes
            if (available < needed) throw NotEnoughStorageException(needed, available)

            val name = UUID.randomUUID().toString() + VaultFiles.EXT
            val part = File(vaultDir, name + VaultFiles.PART_EXT)
            val finalFile = File(vaultDir, name)
            val key = keys.requireKey()
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                val input = context.contentResolver.openInputStream(source) ?: throw IOException("Cannot open video")
                input.use { inp ->
                    part.outputStream().buffered(CHUNK).use { fileOut ->
                        ChunkedAead.Encryptor(fileOut, key).use { enc ->
                            val buf = ByteArray(CHUNK)
                            var done = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = inp.read(buf)
                                if (n < 0) break
                                digest.update(buf, 0, n)
                                enc.write(buf, 0, n)
                                done += n
                                if (plainSize > 0) onProgress((done.toFloat() / plainSize).coerceAtMost(0.9f))
                            }
                        }
                    }
                }
                // Verify: decrypt the copy and compare the hash with the source.
                verify(part, key, digest.digest())
                onProgress(0.95f)
                if (!part.renameTo(finalFile)) throw IOException("Could not finalize vault file")
            } catch (t: Throwable) {
                withContext(NonCancellable) { part.delete(); finalFile.delete() }
                throw t
            } finally {
                key.fill(0)
            }

            val thumb = buildThumbnail(source)
            try {
                videoDao.setVaultState(videoId, true, name)
                if (thumb != null) videoDao.insertThumb(VaultThumbEntity(videoId, thumb))
            } catch (t: Throwable) {
                withContext(NonCancellable) { finalFile.delete() }
                throw t
            }
            onProgress(1f)
            MoveResult(deleteOriginal(source))
        }

    private fun verify(file: File, key: ByteArray, expectedSha256: ByteArray) {
        val digest = MessageDigest.getInstance("SHA-256")
        ChunkedAead.Reader(ChunkedAead.FileSource(file), key).use { r ->
            val buf = ByteArray(CHUNK)
            var pos = 0L
            while (pos < r.plainSize) {
                val n = r.read(pos, buf, 0, buf.size)
                if (n <= 0) throw IOException("Verification failed")
                digest.update(buf, 0, n); pos += n
            }
        }
        if (!MessageDigest.isEqual(digest.digest(), expectedSha256)) throw IOException("Verification failed: checksum mismatch")
    }

    /** Deletes the original from MediaStore. Returns an IntentSender when the user must confirm. */
    private fun deleteOriginal(uri: Uri): IntentSender? {
        if (uri.scheme != "content") return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender
            } else {
                context.contentResolver.delete(uri, null, null)
                null
            }
        } catch (e: RecoverableSecurityException) {
            e.userAction.actionIntent.intentSender
        } catch (e: SecurityException) {
            null
        }
    }

    private fun querySize(uri: Uri): Long = try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    } catch (e: Exception) { -1L }

    private fun buildThumbnail(source: Uri): ByteArray? {
        val key = keys.requireKey()
        return try {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, source)
                val frame = mmr.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
                val w = 320
                val h = (frame.height * (w.toFloat() / frame.width)).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(frame, w, h, true)
                val bytes = ByteArrayOutputStream()
                @Suppress("DEPRECATION")
                val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
                else Bitmap.CompressFormat.WEBP
                scaled.compress(fmt, 70, bytes)
                val out = ByteArrayOutputStream()
                ChunkedAead.Encryptor(out, key, 16 * 1024).use { it.write(bytes.toByteArray()) }
                out.toByteArray()
            } finally {
                mmr.release()
            }
        } catch (e: Exception) {
            null
        } finally {
            key.fill(0)
        }
    }

    /** Decrypts the small thumbnail; null when locked or missing. */
    suspend fun loadThumbnail(videoId: Long): ByteArray? = withContext(Dispatchers.IO) {
        if (!keys.isUnlocked) return@withContext null
        val enc = videoDao.getThumb(videoId)?.data ?: return@withContext null
        val key = keys.requireKey()
        try {
            ChunkedAead.Reader(ChunkedAead.BytesSource(enc), key).use { r ->
                val out = ByteArray(r.plainSize.toInt())
                var pos = 0
                while (pos < out.size) {
                    val n = r.read(pos.toLong(), out, pos, out.size - pos)
                    if (n <= 0) return@withContext null
                    pos += n
                }
                out
            }
        } catch (e: Exception) { null } finally { key.fill(0) }
    }

    // ---------------------------------------------------------------- restore

    suspend fun restoreFromVault(videoId: Long, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val entity = videoDao.getVideoById(videoId) ?: return@withContext
        val file = entity.vaultFile
        if (file == null) { // legacy plain item: the file never left MediaStore
            videoDao.setVaultState(videoId, false, null)
            return@withContext
        }
        if (!keys.isUnlocked) throw IOException("Vault is locked")
        val enc = File(vaultDir, file)
        val key = keys.requireKey()
        val displayName = restoreName(entity)
        var uri: Uri? = null
        var legacyTarget: File? = null
        try {
            ChunkedAead.Reader(ChunkedAead.FileSource(enc), key).use { reader ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Video.Media.MIME_TYPE, entity.mimeType.ifBlank { "video/*" })
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/LNXV")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                    uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw IOException("Could not create file in Movies/LNXV")
                    context.contentResolver.openOutputStream(uri!!)!!.use { out -> copyOut(reader, out, onProgress) }
                    context.contentResolver.update(uri!!, ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }, null, null)
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "LNXV")
                    dir.mkdirs()
                    val target = uniqueFile(dir, displayName)
                    legacyTarget = target
                    target.outputStream().use { out -> copyOut(reader, out, onProgress) }
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
                }
            }
        } catch (t: Throwable) {
            withContext(NonCancellable) {
                uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
                legacyTarget?.delete()
            }
            throw t
        } finally {
            key.fill(0)
        }
        // Restore succeeded: drop the encrypted copy and the vault row (rescan re-adds the video).
        videoDao.deleteThumb(videoId)
        videoDao.deleteVideo(videoId)
        enc.delete()
        onProgress(1f)
    }

    private suspend fun copyOut(reader: ChunkedAead.Reader, out: java.io.OutputStream, onProgress: (Float) -> Unit) {
        val buf = ByteArray(CHUNK)
        var pos = 0L
        while (pos < reader.plainSize) {
            currentCoroutineContext().ensureActive()
            val n = reader.read(pos, buf, 0, buf.size)
            if (n <= 0) throw IOException("Restore failed while decrypting")
            out.write(buf, 0, n); pos += n
            if (reader.plainSize > 0) onProgress((pos.toFloat() / reader.plainSize).coerceAtMost(0.99f))
        }
    }

    private fun restoreName(e: VideoEntity): String {
        val base = e.title.ifBlank { "video" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (base.substringAfterLast('.', "").length in 2..4) return base
        val ext = when (e.mimeType.lowercase()) {
            "video/x-matroska" -> "mkv"; "video/webm" -> "webm"; "video/3gpp" -> "3gp"
            "video/quicktime" -> "mov"; else -> "mp4"
        }
        return "$base.$ext"
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name); var i = 1
        val stem = name.substringBeforeLast('.'); val ext = name.substringAfterLast('.', "")
        while (f.exists()) { f = File(dir, "$stem ($i)" + if (ext.isEmpty()) "" else ".$ext"); i++ }
        return f
    }

    // ---------------------------------------------------------------- maintenance

    /** Deletes interrupted copies and encrypted files with no Room row. Safe to call at any time. */
    suspend fun cleanupOrphans() = withContext(Dispatchers.IO) {
        val referenced = videoDao.getAllVaultFileNames().toHashSet()
        vaultDir.listFiles()?.forEach { f ->
            if (f.name == ".nomedia") return@forEach
            if (f.name.endsWith(VaultFiles.PART_EXT) || f.name !in referenced) f.delete()
        }
    }

    suspend fun legacyItems(): List<VideoItem> = withContext(Dispatchers.IO) {
        videoDao.getLegacyVault().map { it.toModel() }
    }

    private fun VideoEntity.toModel(): VideoItem = VideoItem(
        id = id,
        title = title,
        uri = if (vaultFile != null) VaultRoutingDataSource.uriFor(vaultFile) else Uri.parse(uriString),
        path = if (vaultFile != null) "" else path,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        mimeType = mimeType,
        dateAddedSec = dateAddedSec,
        folderPath = folderPath,
        folderName = folderName,
        lastPositionMs = lastPositionMs,
        lastPlayedTimestamp = lastPlayedTimestamp,
        isFavorite = isFavorite,
        isVault = isVault,
        vaultFile = vaultFile
    )

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    private companion object {
        const val CHUNK = 256 * 1024
        const val SAFETY_MARGIN = 32L * 1024 * 1024
    }
}
