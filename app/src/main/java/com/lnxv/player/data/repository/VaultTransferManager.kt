package com.lnxv.player.data.repository

import android.content.IntentSender
import com.lnxv.player.core.model.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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

    private data class QueuedOp(val generation: Long, val op: Op)

    private val queue = Channel<QueuedOp>(Channel.UNLIMITED)
    private val stateLock = Any()
    private var generation = 0L
    private var current: Job? = null
    private var pending = 0
    private var accepting = true

    private val _state = MutableStateFlow(VaultJobState())
    val state: StateFlow<VaultJobState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _deleteConsents = MutableSharedFlow<IntentSender>(extraBufferCapacity = 16)
    val deleteConsents: SharedFlow<IntentSender> = _deleteConsents.asSharedFlow()

    init {
        scope.launch {
            for (queuedOp in queue) {
                // Publish the current Job atomically with the generation check. This closes
                // the race where cancelAll runs after dequeue but before current is assigned.
                val job = synchronized(stateLock) {
                    if (queuedOp.generation != generation) null
                    else scope.launch { run(queuedOp.op) }.also { current = it }
                }
                if (job == null) {
                    synchronized(stateLock) { pending = (pending - 1).coerceAtLeast(0) }
                    continue
                }
                job.join()
                synchronized(stateLock) {
                    if (current === job) current = null
                    pending = (pending - 1).coerceAtLeast(0)
                    _state.value = VaultJobState(queued = pending)
                }
            }
        }
    }

    fun postMessage(text: String) { _messages.tryEmit(text) }

    fun enqueueMove(video: VideoItem) = submit(Op.Move(video))
    fun enqueueRestore(video: VideoItem) = submit(Op.Restore(video))

    private fun submit(op: Op) {
        val accepted = synchronized(stateLock) {
            if (!accepting) return@synchronized false
            pending++
            if (queue.trySend(QueuedOp(generation, op)).isSuccess) true
            else {
                pending = (pending - 1).coerceAtLeast(0)
                false
            }
        }
        if (!accepted) _messages.tryEmit("Could not queue vault operation")
    }

    /** Cancels the running job and drops queued jobs without waiting. */
    fun cancelAll() {
        cancelQueuedAndCurrent()?.cancel()
    }

    /** Waits for active vault I/O and key copies to be released before the caller wipes/locks. */
    suspend fun cancelAllAndJoin() {
        cancelQueuedAndCurrent(stopAccepting = true)?.cancelAndJoin()
        synchronized(stateLock) {
            current = null
            pending = 0
            _state.value = VaultJobState()
        }
    }

    fun resumeAccepting() {
        synchronized(stateLock) { accepting = true }
    }

    private fun cancelQueuedAndCurrent(stopAccepting: Boolean = false): Job? = synchronized(stateLock) {
        if (stopAccepting) accepting = false
        generation++
        while (queue.tryReceive().isSuccess) {
            pending = (pending - 1).coerceAtLeast(0)
        }
        current
    }

    private suspend fun run(op: Op) {
        val video = when (op) { is Op.Move -> op.video; is Op.Restore -> op.video }
        val restoring = op is Op.Restore
        val queuedCount = synchronized(stateLock) { (pending - 1).coerceAtLeast(0) }
        _state.value = VaultJobState(video.title, restoring, 0f, queuedCount)
        try {
            if (op is Op.Move) {
                val result = repo.moveToVault(video.id) { p -> _state.value = _state.value.copy(progress = p) }
                when {
                    result.deleteConsent != null -> {
                        _deleteConsents.tryEmit(result.deleteConsent)
                        _messages.tryEmit("Encrypted \"${video.title}\". Confirm the system prompt to remove the original outside Vault.")
                    }
                    result.originalDeleteFailed -> {
                        _messages.tryEmit("Encrypted copy created, but the original remains outside Vault because Android did not allow its deletion.")
                    }
                    else -> _messages.tryEmit("Encrypted \"${video.title}\"")
                }
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
