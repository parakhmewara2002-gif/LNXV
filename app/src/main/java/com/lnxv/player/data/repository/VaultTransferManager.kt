package com.lnxv.player.data.repository

import android.content.IntentSender
import com.lnxv.player.core.model.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VaultJobState(
    val title: String = "",
    val restoring: Boolean = false,
    val progress: Float = 0f,
    val queued: Int = 0
) {
    val isRunning: Boolean get() = title.isNotEmpty()
}

/**
 * Runs vault copy/restore jobs one at a time in an app-level scope (SupervisorJob), so they survive
 * screen changes but are cancelled on lock, cancel or wipe. Never leaves orphan files (see repository).
 */
class VaultTransferManager(
    private val scope: CoroutineScope,
    private val repo: VaultRepository
) {
    private sealed class Op {
        data class Move(val video: VideoItem) : Op()
        data class Restore(val video: VideoItem) : Op()
    }

    private val queue = Channel<Op>(Channel.UNLIMITED)
    private var current: Job? = null
    private var pending = 0

    private val _state = MutableStateFlow(VaultJobState())
    val state: StateFlow<VaultJobState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _deleteConsents = MutableSharedFlow<IntentSender>(extraBufferCapacity = 16)
    val deleteConsents: SharedFlow<IntentSender> = _deleteConsents.asSharedFlow()

    init {
        scope.launch {
            for (op in queue) {
                val job = scope.launch { run(op) }
                current = job
                job.join()
                current = null
                pending = (pending - 1).coerceAtLeast(0)
                _state.value = VaultJobState(queued = 0)
            }
        }
    }

    fun postMessage(text: String) { _messages.tryEmit(text) }

    fun enqueueMove(video: VideoItem) = submit(Op.Move(video))
    fun enqueueRestore(video: VideoItem) = submit(Op.Restore(video))

    private fun submit(op: Op) {
        pending++
        queue.trySend(op)
    }

    /** Cancels the running job and drops everything queued. */
    fun cancelAll() {
        while (queue.tryReceive().getOrNull() != null) pending--
        current?.cancel()
    }

    private suspend fun run(op: Op) {
        val video = when (op) { is Op.Move -> op.video; is Op.Restore -> op.video }
        val restoring = op is Op.Restore
        _state.value = VaultJobState(video.title, restoring, 0f, (pending - 1).coerceAtLeast(0))
        try {
            if (op is Op.Move) {
                val result = repo.moveToVault(video.id) { p -> _state.value = _state.value.copy(progress = p) }
                result.deleteConsent?.let { _deleteConsents.tryEmit(it) }
                _messages.tryEmit("Encrypted \"${video.title}\"")
            } else {
                repo.restoreFromVault(video.id) { p -> _state.value = _state.value.copy(progress = p) }
                _messages.tryEmit("Restored \"${video.title}\" to Movies/LNXV")
            }
        } catch (e: CancellationException) {
            _messages.tryEmit("Cancelled")
        } catch (e: NotEnoughStorageException) {
            val missing = e.neededBytes - e.availableBytes
            _messages.tryEmit(
                "Not enough storage: free up ${VideoItem.formatFileSize(missing)} " +
                    "(${VideoItem.formatFileSize(e.neededBytes)} needed, ${VideoItem.formatFileSize(e.availableBytes)} free)"
            )
        } catch (e: Exception) {
            _messages.tryEmit("Vault error: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
