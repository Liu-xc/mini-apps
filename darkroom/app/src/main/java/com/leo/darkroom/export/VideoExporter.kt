package com.leo.darkroom.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.EGLExt
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import com.leo.darkroom.card.CardSpec
import com.leo.darkroom.card.GrainNoise
import com.leo.darkroom.card.PhotoCardPainter
import com.leo.darkroom.card.PhotoLook
import com.leo.darkroom.card.ShareFormat
import com.leo.darkroom.develop.DevelopMode
import com.leo.darkroom.develop.DevelopSpec
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * 视频导出（it-001 US-6 / AC6）：确定性逐帧渲染 → EGL 窗口面 → MediaCodec(H.264) →
 * MediaMuxer(MP4)，SfxSynth 音轨整段 AAC 编码后一并入片。
 *
 * 管线：先离线合成+编码音轨（结果小，缓存在内存）→ 视频逐帧（每帧把卡片位图经
 * 纹理 blit 画到 codec 输入面，presentationTime 用 EGLExt 精确打戳）→
 * 首个视频输出格式到达时建 muxer、先补写音轨 → 视频流式入片 → stop 统一写 moov。
 */
class VideoExporter {

    data class Params(
        val photo: Bitmap,
        val spec: CardSpec,
        val mode: DevelopMode = DevelopMode.POLAROID,
        val plan: ExportPlan,
        val format: ShareFormat,
        val look: PhotoLook = PhotoLook.ORIGINAL,
        val outFile: File,
        /** null = 无声视频 */
        val withAudio: Boolean = true,
    )

    private companion object {
        const val MIME = "video/avc"
        const val AUDIO_MIME = "audio/mp4a-latm"
        const val TIMEOUT_US = 10_000L
        const val VIDEO_BITRATE = 8_000_000
        const val IFRAME_INTERVAL = 1
    }

    fun export(params: Params, onProgress: (Float) -> Unit = {}): File {
        val plan = params.plan
        val videoWidth = params.format.width
        val videoHeight = params.format.height
        // 1) 音轨离线合成 + AAC 编码
        val audio = if (params.withAudio) encodeAudio(plan) else null

        var codec: MediaCodec? = null
        var egl: EglCore? = null
        var muxer: MediaMuxer? = null
        try {
            codec = MediaCodec.createEncoderByType(MIME).apply {
                configure(
                    MediaFormat.createVideoFormat(MIME, videoWidth, videoHeight).apply {
                        setInteger(
                            MediaFormat.KEY_COLOR_FORMAT,
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                        )
                        setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
                        setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
                    },
                    null, null, MediaCodec.CONFIGURE_FLAG_ENCODE,
                )
            }
            val inputSurface = codec.createInputSurface()
            codec.start()
            egl = EglCore(inputSurface)

            val grain = GrainNoise.bitmap()
            val frameBmp = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
            val frameCanvas = Canvas(frameBmp)

            var videoTrackIndex = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()
            var framesDone = 0
            val total = plan.frameCount
            var inputDone = false

            while (true) {
                // —— 喂帧 ——
                if (!inputDone) {
                    if (framesDone < total) {
                        val timing = plan.frameAt(framesDone)
                        val visual = DevelopSpec.visualAt(params.mode, timing.developProgress)
                        PhotoCardPainter.paintShareFrame(
                            canvas = frameCanvas,
                            widthPx = videoWidth.toFloat(),
                            heightPx = videoHeight.toFloat(),
                            photo = params.photo,
                            spec = params.spec,
                            visual = visual,
                            mode = params.mode,
                            grain = grain,
                            format = params.format,
                            look = params.look,
                        )

                        egl.drawFrame(frameBmp)
                        EGLExt.eglPresentationTimeANDROID(
                            egl.display, egl.surface, timing.timeMs * 1_000_000L,
                        )
                        egl.swapBuffers()
                        framesDone++
                        onProgress(framesDone.toFloat() / total)
                    } else {
                        codec.signalEndOfInputStream()
                        inputDone = true
                    }
                }

                // —— 收输出 ——
                val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(muxer == null) { "muxer already created" }
                        val newMuxer = MediaMuxer(
                            params.outFile.absolutePath,
                            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                        )
                        muxer = newMuxer
                        videoTrackIndex = newMuxer.addTrack(codec.outputFormat)
                        if (audio != null) {
                            val audioTrack = newMuxer.addTrack(audio.format)
                            newMuxer.start()
                            muxerStarted = true
                            audio.chunks.forEach { (buf, info) ->
                                newMuxer.writeSampleData(audioTrack, buf, info)
                            }
                        } else {
                            newMuxer.start()
                            muxerStarted = true
                        }
                    }
                    outIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null && bufferInfo.size > 0 &&
                            (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        ) {
                            check(muxerStarted && muxer != null) { "muxer not started" }
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer!!.writeSampleData(videoTrackIndex, outBuf, bufferInfo)
                        }
                        val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        codec.releaseOutputBuffer(outIndex, false)
                        if (eos) break
                    }
                }
            }

            check(muxerStarted) { "no output format" }
            muxer?.stop()
            onProgress(1f)
            return params.outFile
        } catch (t: Throwable) {
            params.outFile.delete()
            throw t
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            egl?.release()
            runCatching { muxer?.release() }
        }
    }

    /** 音轨：合成 PCM → AAC 编码 → 缓存编码块与格式 */
    private fun encodeAudio(plan: ExportPlan): EncodedAudio {
        val track = SfxSynth.buildDevelopTrack(plan.totalMs, plan.leadMs + plan.developMs)
        val codec = MediaCodec.createEncoderByType(AUDIO_MIME)
        val format = MediaFormat.createAudioFormat(AUDIO_MIME, track.sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1 shl 16)
        }
        val chunks = mutableListOf<Pair<ByteBuffer, MediaCodec.BufferInfo>>()
        var audioFormat: MediaFormat? = null
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var fed = 0
            var inputDone = false
            var outputDone = false
            val pcm = track.pcm

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        inBuf.clear()
                        val capacityShorts = inBuf.capacity() / 2
                        val n = minOf(capacityShorts, pcm.size - fed)
                        if (n > 0) {
                            inBuf.asShortBuffer().put(pcm, fed, n)
                            val ptsUs = fed * 1_000_000L / track.sampleRate
                            codec.queueInputBuffer(inIndex, 0, n * 2, ptsUs, 0)
                            fed += n
                        } else {
                            codec.queueInputBuffer(
                                inIndex, 0, 0,
                                pcm.size * 1_000_000L / track.sampleRate,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                        audioFormat = codec.outputFormat

                    outIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null && info.size > 0 &&
                            (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        ) {
                            val copy = ByteBuffer.allocate(info.size)
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            copy.put(outBuf)
                            copy.flip()
                            val infoCopy = MediaCodec.BufferInfo().apply {
                                set(0, info.size, info.presentationTimeUs, info.flags)
                            }
                            chunks.add(copy to infoCopy)
                        }
                        val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        codec.releaseOutputBuffer(outIndex, false)
                        if (eos) outputDone = true
                    }
                }
            }
            checkNotNull(audioFormat) { "audio format missing" }
            return EncodedAudio(audioFormat, chunks)
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }
    }

    private data class EncodedAudio(
        val format: MediaFormat,
        val chunks: List<Pair<ByteBuffer, MediaCodec.BufferInfo>>,
    )
}

/**
 * 极简 EGL14 封装：display + ES2 包裹 codec 输入面的 window surface + 纹理 blit。
 */
private class EglCore(codecSurface: Surface) {
    val display: EGLDisplay
    val surface: EGLSurface
    private val context: EGLContext

    private var program = 0
    private var aPosLoc = 0
    private var aTexLoc = 0
    private var textureId = -1
    private var textureUploaded = false
    private var texW = 0
    private var texH = 0

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }

        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            // EGL_WINDOW_SURFACE = 0x0005（khronos egl.h；EGL14 类未暴露该常量）
            EGL14.EGL_SURFACE_TYPE, 0x0005,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(
            EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, numConfigs, 0) &&
                numConfigs[0] > 0,
        ) { "eglChooseConfig failed" }
        val config = configs[0]!!

        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }

        surface = EGL14.eglCreateWindowSurface(display, config, codecSurface, null, 0)
        check(surface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent failed" }

        initProgram()
    }

    private fun initProgram() {
        val vs = compileShader(
            GLES20.GL_VERTEX_SHADER,
            """
            attribute vec4 aPos;
            attribute vec2 aTex;
            varying vec2 vTex;
            void main() {
                gl_Position = aPos;
                vTex = aTex;
            }
            """.trimIndent(),
        )
        val fs = compileShader(
            GLES20.GL_FRAGMENT_SHADER,
            """
            precision mediump float;
            varying vec2 vTex;
            uniform sampler2D uTex;
            void main() {
                gl_FragColor = texture2D(uTex, vTex);
            }
            """.trimIndent(),
        )
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val link = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, link, 0)
        check(link[0] != 0) { "program link failed: ${GLES20.glGetProgramInfoLog(program)}" }
        aPosLoc = GLES20.glGetAttribLocation(program, "aPos")
        aTexLoc = GLES20.glGetAttribLocation(program, "aTex")

        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        textureId = texIds[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    // 顶点：全屏四边形；纹理 t=0 对应位图首行 → 顶点在屏上半部（y=+1），图不倒置
    private val quadPos = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    private val quadTex = floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)
    private val posBuf: FloatBuffer = nativeFloatBuffer(quadPos)
    private val texBuf: FloatBuffer = nativeFloatBuffer(quadTex)

    fun drawFrame(frame: Bitmap) {
        GLES20.glViewport(0, 0, frame.width, frame.height)
        GLES20.glClearColor(0.125f, 0.122f, 0.098f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        if (!textureUploaded || texW != frame.width || texH != frame.height) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, frame, 0)
            texW = frame.width
            texH = frame.height
            textureUploaded = true
        } else {
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, frame)
        }
        posBuf.position(0)
        texBuf.position(0)
        GLES20.glEnableVertexAttribArray(aPosLoc)
        GLES20.glVertexAttribPointer(aPosLoc, 2, GLES20.GL_FLOAT, false, 0, posBuf)
        GLES20.glEnableVertexAttribArray(aTexLoc)
        GLES20.glVertexAttribPointer(aTexLoc, 2, GLES20.GL_FLOAT, false, 0, texBuf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosLoc)
        GLES20.glDisableVertexAttribArray(aTexLoc)
    }

    fun swapBuffers(): Boolean = EGL14.eglSwapBuffers(display, surface)

    fun release() {
        runCatching {
            EGL14.eglMakeCurrent(
                display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
            )
            if (textureId != -1) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            if (program != 0) GLES20.glDeleteProgram(program)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val ids = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ids, 0)
        check(ids[0] != 0) { "shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}" }
        return shader
    }
}

private fun nativeFloatBuffer(values: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(values.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(values)
            position(0)
        }
