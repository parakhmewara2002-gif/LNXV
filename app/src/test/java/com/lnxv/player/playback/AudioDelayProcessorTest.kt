package com.lnxv.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.lnxv.player.playback.engine.AudioDelayProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@UnstableApi
class AudioDelayProcessorTest {
    private val format = AudioProcessor.AudioFormat(1000, 1, C.ENCODING_PCM_16BIT) // 1 kHz mono: 1 ms = 1 frame

    private fun pcm(vararg v: Int): ByteBuffer {
        val b = ByteBuffer.allocateDirect(v.size * 2).order(ByteOrder.nativeOrder())
        v.forEach { b.putShort(it.toShort()) }
        b.flip()
        return b
    }

    private fun drain(p: AudioProcessor): List<Int> {
        val out = p.output
        val l = mutableListOf<Int>()
        out.order(ByteOrder.nativeOrder())
        while (out.remaining() >= 2) l += out.short.toInt()
        return l
    }

    @Test fun insertsSilenceForPositiveDelay() {
        val p = AudioDelayProcessor()
        p.setDelayMs(3)
        p.configure(format); p.flush()
        p.queueInput(pcm(1, 2, 3))
        assertEquals(listOf(0, 0, 0, 1, 2, 3), drain(p))
        p.queueInput(pcm(4, 5))
        assertEquals(listOf(4, 5), drain(p)) // silence only once
    }

    @Test fun zeroDelayIsPassThrough() {
        val p = AudioDelayProcessor()
        p.configure(format); p.flush()
        p.queueInput(pcm(7, 8))
        assertEquals(listOf(7, 8), drain(p))
    }

    @Test fun flushReappliesDelay() {
        val p = AudioDelayProcessor()
        p.setDelayMs(2)
        p.configure(format); p.flush()
        p.queueInput(pcm(1)); drain(p)
        p.flush()
        p.queueInput(pcm(9))
        assertEquals(listOf(0, 0, 9), drain(p))
    }

    @Test fun decreasingDelayDropsSamples() {
        val p = AudioDelayProcessor()
        p.setDelayMs(4)
        p.configure(format); p.flush()
        p.queueInput(pcm(1)); drain(p)   // consumes the 4 frames of silence + 1
        p.setDelayMs(2)                  // drop 2 frames
        p.queueInput(pcm(2, 3, 4))
        assertEquals(listOf(4), drain(p))
    }

    @Test fun delayIsClamped() {
        val p = AudioDelayProcessor()
        p.setDelayMs(99_999)
        p.configure(AudioProcessor.AudioFormat(1000, 1, C.ENCODING_PCM_16BIT)); p.flush()
        p.queueInput(pcm(1))
        assertEquals(2000 + 1, drain(p).size)
    }

    @Test fun nonPcm16StaysInactive() {
        val p = AudioDelayProcessor()
        p.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT))
        assertFalse(p.isActive)
    }
}
