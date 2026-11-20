package com.lnxv.player.playback.effects

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.lnxv.player.core.enhancer.ColorMath
import com.lnxv.player.core.enhancer.EnhancerShader
import com.lnxv.player.core.enhancer.PictureSettings

/**
 * One Media3 GL effect that applies the whole Picture pipeline (unsharp mask, colour matrix, Vivid, gamma) in a single
 * fragment-shader pass. Written for LNXV; it uses only public Media3 effect APIs. SDR only: HDR sources are gated off
 * before this effect is ever created.
 */
@UnstableApi
class PictureEffect(private val settings: PictureSettings) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        if (useHdr) throw VideoFrameProcessingException("Picture enhancer does not support HDR input")
        return PictureShaderProgram(settings)
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = settings.isNeutral
}

@UnstableApi
private class PictureShaderProgram(private val settings: PictureSettings) : BaseGlShaderProgram(false, 1) {
    private val glProgram: GlProgram
    private val colorMatrix: FloatArray
    private val colorOffset: FloatArray
    private var texelW = 0f
    private var texelH = 0f

    init {
        try {
            glProgram = GlProgram(EnhancerShader.VERTEX, EnhancerShader.fragment(settings.sharpTaps))
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
        glProgram.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        val affine = ColorMath.compose(settings)
        colorMatrix = affine.columnMajor()
        colorOffset = affine.t
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        texelW = 1f / inputWidth.coerceAtLeast(1)
        texelH = 1f / inputHeight.coerceAtLeast(1)
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.setFloatsUniform("uTexelSize", floatArrayOf(texelW, texelH))
            glProgram.setFloatUniform("uSharpAmount", settings.sharpness)
            glProgram.setFloatUniform("uSharpThreshold", settings.sharpNoiseThreshold)
            glProgram.setFloatsUniform("uColorMatrix", colorMatrix)
            glProgram.setFloatsUniform("uColorOffset", colorOffset)
            glProgram.setFloatUniform("uVivid", settings.vivid)
            glProgram.setFloatUniform("uInvGamma", 1f / settings.gamma)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }
}
