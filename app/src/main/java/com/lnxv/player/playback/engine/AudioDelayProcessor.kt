package com.lnxv.player.playback.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Delays audio relative to video by inserting silence (positive delay only: advancing audio would need
 * the video to be delayed instead). Works on 16-bit PCM; for any other encoding the processor stays inactive.
 * A change of delay while playing inserts extra silence or drops samples; after a seek/flush the full delay is re-applied.
 */
@UnstableApi
class AudioDelayProcessor : BaseAudioProcessor() {

    private var sampleRate = 0
    private var frameSize = 0
    private var appliedFrames = 0L
    private var pendingFrames = 0L

    @Synchronized
    fun setDelayMs(ms: Long) {
        val clamped = ms.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
        if (sampleRate == 0) {
            requestedMs = clamped
            return
        }
        requestedMs = clamped
        val newFrames = framesFor(clamped, sampleRate)
        pendingFrames += newFrames - appliedFrames
        appliedFrames = newFrames
    }

    @Volatile private var requestedMs = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        synchronized(this) {
            sampleRate = inputAudioFormat.sampleRate
            frameSize = inputAudioFormat.channelCount * 2
        }
        return inputAudioFormat
    }

    @Synchronized
    override fun onFlush() {
        appliedFrames = if (sampleRate > 0) framesFor(requestedMs, sampleRate) else 0L
        pendingFrames = appliedFrames
    }

    @Synchronized
    override fun onReset() {
        sampleRate = 0; frameSize = 0; appliedFrames = 0; pendingFrames = 0
    }

    @Synchronized
    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        var silenceBytes = 0
        if (pendingFrames > 0) {
            val frames = pendingFrames.coerceAtMost(MAX_SILENCE_FRAMES)
            silenceBytes = (frames * frameSize).toInt()
            pendingFrames -= frames
        } else if (pendingFrames < 0) {
            val skipBytes = minOf(-pendingFrames * frameSize, inputBuffer.remaining().toLong()).toInt()
            val skipFrames = skipBytes / frameSize
            inputBuffer.position(inputBuffer.position() + skipFrames * frameSize)
            pendingFrames += skipFrames
            if (!inputBuffer.hasRemaining()) return
        }
        val out = replaceOutputBuffer(silenceBytes + inputBuffer.remaining())
        if (silenceBytes > 0) out.put(ByteArray(silenceBytes))
        out.put(inputBuffer)
        out.flip()
    }

    companion object {
        const val MIN_DELAY_MS = 0L
        const val MAX_DELAY_MS = 2000L
        private const val MAX_SILENCE_FRAMES = 2L * 96_000L

        fun framesFor(ms: Long, sampleRate: Int): Long = ms * sampleRate / 1000L
    }
}
