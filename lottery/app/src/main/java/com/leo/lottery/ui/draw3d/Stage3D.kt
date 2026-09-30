package com.leo.lottery.ui.draw3d

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import android.opengl.Matrix
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.TransformManager
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.filamat.MaterialBuilder
import com.leo.lottery.core.SeedHash
import com.leo.lottery.core.SplitMix64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 开奖演播室 3D 舞台（it-002 / US-2R）：Filament 渲染的玻璃球摇奖机。
 *
 * 场景单位：玻璃球心 (0, DOME_CY, 0)、半径 DOME_R；出球管在球顶；底座圆柱在其下。
 * 编排式搅动（确定性 Lissajous 轨迹 + 气流包络），不做刚体碰撞——直播观感靠密度与乱度，
 * 不靠物理精度（ADR-006）。UNLIT 材质 + 球面 UV 号码盘贴图（号码恒面向镜头，微摆）。
 */
object Stage3D {

    // ---- 舞台几何（世界单位）----
    const val DOME_R = 0.86f
    const val DOME_CY = 0.62f
    const val BALL_R = 0.158f
    const val TUBE_R = 0.15f
    const val TUBE_H = 0.30f

    // ---- 时间轴（ms）----
    const val T_OPEN = 900L
    const val T_WARM = 1000L
    const val T_PRE = 500L
    const val T_DROP = 520L
    const val T_FLIGHT = 640L
    const val T_LAND = 560L
    const val T_SWAP = 1100L
    const val T_RESULT = 1500L
    const val T_BALL = T_PRE + T_DROP + T_FLIGHT + T_LAND

    class Beat(val swapAt: Long, val endAt: Long)

    fun beat(n1: Int, n2: Int): Beat {
        val swapAt = T_OPEN + T_WARM + n1 * T_BALL
        return Beat(swapAt, swapAt + T_SWAP + n2 * T_BALL + T_RESULT)
    }

    // ---- 引擎与材质（进程级；号码贴图按 [颜色+号码] 缓存）----
    @Volatile private var engineInternal: Engine? = null
    @Volatile private var matBallInternal: Material? = null
    @Volatile private var matSolidInternal: Material? = null
    private val texCache = HashMap<String, Texture>()
    private var sphereMesh: Pair<VertexBuffer, IndexBuffer>? = null
    private var cylinderMesh: Pair<VertexBuffer, IndexBuffer>? = null

    internal fun ensureEngine(): Engine {
        engineInternal?.let { return it }
        synchronized(this) {
            engineInternal?.let { return it }
            android.util.Log.w("Stage3D", "ensureEngine: cold init on tid=${Thread.currentThread().id}")
            com.google.android.filament.Filament.init()
            MaterialBuilder.init()
            val e = Engine.create()
            matBallInternal = buildMaterial(e) {
                name("ball")
                platform(MaterialBuilder.Platform.MOBILE)
                .shading(MaterialBuilder.Shading.UNLIT)
                .blending(MaterialBuilder.BlendingMode.OPAQUE)
                .materialDomain(MaterialBuilder.MaterialDomain.SURFACE)
                .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "baseColor")
                .samplerParameter(
                    MaterialBuilder.SamplerType.SAMPLER_2D, MaterialBuilder.SamplerFormat.FLOAT,
                    MaterialBuilder.ParameterPrecision.DEFAULT, "baseColorMap",
                )
            }
            matSolidInternal = buildMaterial(e) {
                name("solid")
                platform(MaterialBuilder.Platform.MOBILE)
                .shading(MaterialBuilder.Shading.UNLIT)
                .blending(MaterialBuilder.BlendingMode.OPAQUE)
                .materialDomain(MaterialBuilder.MaterialDomain.SURFACE)
                .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "baseColor")
                .samplerParameter(
                    MaterialBuilder.SamplerType.SAMPLER_2D, MaterialBuilder.SamplerFormat.FLOAT,
                    MaterialBuilder.ParameterPrecision.DEFAULT, "baseColorMap",
                )
            }
            engineInternal = e
            return e
        }
    }

    private fun buildMaterial(e: Engine, block: MaterialBuilder.() -> MaterialBuilder): Material {
        var lastErr: Throwable? = null
        repeat(2) { attempt ->
            try {
                val pkg = block(MaterialBuilder()).build(e)
                val buf = pkg.buffer
                android.util.Log.w("Stage3D", "material pkg attempt=$attempt valid=${pkg.isValid} bytes=${buf.remaining()}")
                if (pkg.isValid && buf.remaining() > 0) {
                    return Material.Builder().payload(buf, buf.remaining()).build(e)
                }
            } catch (t: Throwable) {
                lastErr = t
                android.util.Log.e("Stage3D", "material build attempt=$attempt failed", t)
            }
        }
        throw IllegalStateException("Stage3D material init failed", lastErr)
    }

    internal fun matBall(): Material = matBallInternal!!
    internal fun matSolid(): Material = matSolidInternal!!

    internal fun sampler() = TextureSampler(
        TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR,
        TextureSampler.WrapMode.CLAMP_TO_EDGE,
    )

    /** UV 球（lon ∈ [-π,π] 使 u=0.5 正对镜头、接缝在后）。 */
    internal fun sphere(e: Engine, segU: Int = 28, segV: Int = 20): Pair<VertexBuffer, IndexBuffer> {
        sphereMesh?.let { return it }
        val n = (segU + 1) * (segV + 1)
        val floats = FloatArray(n * 5)
        var p = 0
        for (iv in 0..segV) {
            val v = iv.toFloat() / segV
            val lat = (PI / 2.0 - v * PI).toFloat()
            val cy = sin(lat)
            val r = cos(lat)
            for (iu in 0..segU) {
                val u = iu.toFloat() / segU
                val lon = (u - 0.5f) * 2f * PI.toFloat()
                floats[p++] = r * sin(lon)
                floats[p++] = cy
                floats[p++] = r * cos(lon)
                floats[p++] = u
                floats[p++] = 1f - v
            }
        }
        val idx = ShortArray(segU * segV * 6)
        var q = 0
        for (iv in 0 until segV) {
            for (iu in 0 until segU) {
                val a = iv * (segU + 1) + iu
                val b = a + 1
                val c = a + segU + 1
                val d = c + 1
                idx[q++] = a.toShort(); idx[q++] = c.toShort(); idx[q++] = b.toShort()
                idx[q++] = b.toShort(); idx[q++] = c.toShort(); idx[q++] = d.toShort()
            }
        }
        val vb = VertexBuffer.Builder()
            .bufferCount(1).vertexCount(n)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 20)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 12, 20)
            .build(e)
        vb.setBufferAt(e, 0, floatBuffer(floats))
        val ib = IndexBuffer.Builder()
            .indexCount(idx.size).bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(e)
        ib.setBuffer(e, shortBuffer(idx))
        return (vb to ib).also { sphereMesh = it }
    }

    /** 圆柱侧面。 */
    internal fun cylinder(e: Engine, topR: Float, botR: Float, h: Float, seg: Int = 36): Pair<VertexBuffer, IndexBuffer> {
        cylinderMesh?.let { return it }
        val n = (seg + 1) * 2
        val floats = FloatArray(n * 5)
        var p = 0
        for (i in 0..seg) {
            val u = i.toFloat() / seg
            val ang = u * 2f * PI.toFloat()
            val x = sin(ang); val z = cos(ang)
            floats[p++] = topR * x; floats[p++] = h / 2f; floats[p++] = topR * z; floats[p++] = u; floats[p++] = 0f
            floats[p++] = botR * x; floats[p++] = -h / 2f; floats[p++] = botR * z; floats[p++] = u; floats[p++] = 1f
        }
        val idx = ShortArray(seg * 6)
        var q = 0
        for (i in 0 until seg) {
            val a = i * 2; val b = a + 1; val c = a + 2; val d = a + 3
            idx[q++] = a.toShort(); idx[q++] = b.toShort(); idx[q++] = c.toShort()
            idx[q++] = c.toShort(); idx[q++] = b.toShort(); idx[q++] = d.toShort()
        }
        val vb = VertexBuffer.Builder()
            .bufferCount(1).vertexCount(n)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 20)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 12, 20)
            .build(e)
        vb.setBufferAt(e, 0, floatBuffer(floats))
        val ib = IndexBuffer.Builder()
            .indexCount(idx.size).bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(e)
        ib.setBuffer(e, shortBuffer(idx))
        return (vb to ib).also { cylinderMesh = it }
    }

    /** 号码球贴图：白底号码盘 + 纵向明暗 + 弱光泽（镜面高光由 2D 层补）。 */
    internal fun ballTexture(e: Engine, number: Int, base: Color): Texture {
        val key = "$number|${base.value}"
        synchronized(texCache) { texCache[key]?.let { return it } }
        val size = 160
        val bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bm)
        val wall = Paint(Paint.ANTI_ALIAS_FLAG)
        val rows = 24
        for (r in 0 until rows) {
            val lat = ((r + 0.5f) / rows - 0.5f) * PI.toFloat()
            val lum = 0.88f + 0.20f * cos(lat)
            wall.color = AColor.argb(
                255,
                (base.red * 255 * lum).toInt().coerceIn(0, 255),
                (base.green * 255 * lum).toInt().coerceIn(0, 255),
                (base.blue * 255 * lum).toInt().coerceIn(0, 255),
            )
            cv.drawRect(0f, r * size / rows.toFloat(), size.toFloat(), (r + 1) * size / rows.toFloat(), wall)
        }
        val gloss = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, size * 0.72f, size * 0.72f,
                intArrayOf(AColor.argb(135, 255, 255, 255), AColor.argb(0, 255, 255, 255)),
                floatArrayOf(0f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        cv.drawRect(0f, 0f, size.toFloat(), size.toFloat(), gloss)
        val pc = PointF(size * 0.5f, size * 0.46f)
        val pr = size * 0.33f
        cv.drawCircle(pc.x, pc.y, pr, Paint().apply { color = AColor.rgb(252, 250, 244) })
        cv.drawCircle(pc.x, pc.y, pr, Paint().apply {
            color = AColor.argb(55, 0, 0, 0); style = Paint.Style.STROKE; strokeWidth = 2.5f
        })
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AColor.rgb(0x22, 0x18, 0x15)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (number >= 10) size * 0.285f else size * 0.35f
        }
        val fm = txt.fontMetrics
        cv.drawText(number.toString(), pc.x, pc.y - (fm.ascent + fm.descent) / 2f, txt)

        val tex = Texture.Builder()
            .width(size).height(size).levels(1)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.RGBA8)
            .build(e)
        tex.setImage(e, 0, Texture.PixelBufferDescriptor(toRgba(bm), Texture.Format.RGBA, Texture.Type.UBYTE))
        synchronized(texCache) { texCache[key] = tex }
        return tex
    }

    internal fun pedestalTexture(e: Engine): Texture {
        synchronized(texCache) { texCache["pedestal"]?.let { return it } }
        val w = 64; val h = 128
        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bm)
        val p = Paint()
        p.shader = android.graphics.LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(AColor.rgb(0x1E, 0x2A, 0x46), AColor.rgb(0x0D, 0x14, 0x26)),
            floatArrayOf(0f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = AColor.rgb(0xC9, 0xA2, 0x4A)
        cv.drawRect(0f, h * 0.10f, w.toFloat(), h * 0.145f, p)
        p.color = AColor.argb(70, 0xC9, 0xA2, 0x4A)
        cv.drawRect(0f, h * 0.16f, w.toFloat(), h * 0.20f, p)
        val tex = Texture.Builder().width(w).height(h).levels(1)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.RGBA8)
            .build(e)
        tex.setImage(e, 0, Texture.PixelBufferDescriptor(toRgba(bm), Texture.Format.RGBA, Texture.Type.UBYTE))
        synchronized(texCache) { texCache["pedestal"] = tex }
        return tex
    }

    private fun toRgba(bm: Bitmap): ByteBuffer {
        val w = bm.width; val h = bm.height
        val px = IntArray(w * h)
        bm.getPixels(px, 0, w, 0, 0, w, h)
        val bb = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        for (c in px) {
            bb.put(((c ushr 16) and 0xFF).toByte())
            bb.put(((c ushr 8) and 0xFF).toByte())
            bb.put((c and 0xFF).toByte())
            bb.put(((c ushr 24) and 0xFF).toByte())
        }
        bb.rewind()
        return bb
    }

    private fun floatBuffer(a: FloatArray): ByteBuffer =
        ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).run {
            asFloatBuffer().put(a); rewind(); this
        }

    private fun shortBuffer(a: ShortArray): ByteBuffer =
        ByteBuffer.allocateDirect(a.size * 2).order(ByteOrder.nativeOrder()).run {
            asShortBuffer().put(a); rewind(); this
        }
}

/** 舞台运行状态快照（Compose 侧粗粒度消费，值不变不触发重组）。 */
data class StageUi(
    val t: Long = 0L,
    val machineBlue: Boolean = false,
    val drawnCount: Int = 0,
    val flashNumber: Int? = null,
    val ended: Boolean = false,
)

private class MachineBalls(
    val entities: IntArray,
    val phase: FloatArray,
    val rest: FloatArray,
)

/**
 * 导演：单一 Choreographer 时钟驱动「搅动 → 逐球吸顶冲出 → 换机 → 终幕」。
 * 跳过 = 时钟快进到终幕前 40ms。
 */
class StageDirector(
    private val drawZone1: List<Int>,
    private val drawZone2: List<Int>,
    pool1: Int,
    pool2: Int,
    red: Color,
    blue: Color,
    seedTag: String,
) {
    private val e = Stage3D.ensureEngine()
    val renderer: Renderer = e.createRenderer()
    val scene: Scene = e.createScene()
    val view: View = e.createView()
    val camera: Camera = e.createCamera(EntityManager.get().create())
    private val tm: TransformManager = e.transformManager

    val n1 = drawZone1.size
    val beat = Stage3D.beat(n1, drawZone2.size)
    val ui = androidx.compose.runtime.mutableStateOf(StageUi())

    private var swapChain: SwapChain? = null
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)

    private var redM: MachineBalls? = null
    private var blueM: MachineBalls? = null
    private var redShown = true
    private var blueShown = false
    private val mats = ArrayList<MaterialInstance>()
    private val own = ArrayList<Int>()

    private val consumedRed = HashSet<Int>()
    private val consumedBlue = HashSet<Int>()

    private val choreographer = Choreographer.getInstance()
    private var frameCb: Choreographer.FrameCallback? = null
    @Volatile private var skipped = false
    private var startNs = -1L
    private var lastUi = StageUi()
    private val tmp = FloatArray(16)
    private val tmpD = DoubleArray(16)

    init {
        view.scene = scene
        view.camera = camera
        view.blendMode = View.BlendMode.TRANSLUCENT
        view.antiAliasing = View.AntiAliasing.FXAA
        camera.setProjection(36.0, 1.0, 0.05, 50.0, Camera.Fov.VERTICAL)

        uiHelper.setOpaque(false)
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { e.destroySwapChain(it) }
                swapChain = e.createSwapChain(surface)
            }

            override fun onDetachedFromSurface() {
                swapChain?.let { e.destroySwapChain(it) }
                swapChain = null
            }

            override fun onResized(width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height)
                camera.setProjection(36.0, width.toDouble() / height.coerceAtLeast(1), 0.05, 50.0, Camera.Fov.VERTICAL)
            }
        }

        buildScene(seedTag, pool1, pool2, red, blue)
    }

    fun attach(sv: SurfaceView) {
        uiHelper.attachTo(sv)
    }

    private fun buildScene(seedTag: String, pool1: Int, pool2: Int, red: Color, blue: Color) {
        val rng1 = SplitMix64(SeedHash.seedLong("stage|$seedTag|r".toByteArray()))
        val rng2 = SplitMix64(SeedHash.seedLong("stage|$seedTag|b".toByteArray()))
        redM = buildBalls(pool1, red, rng1)
        blueM = buildBalls(pool2, blue, rng2)
        blueM?.let { hideMachine(it) }

        val (pvb, pib) = Stage3D.cylinder(e, 0.50f, 0.58f, 0.62f)
        val pedMat = Stage3D.matSolid().createInstance().also { mats.add(it) }
        pedMat.setParameter("baseColorMap", Stage3D.pedestalTexture(e), Stage3D.sampler())
        pedMat.setParameter("baseColor", 1f, 1f, 1f, 1f)
        addStatic(pvb, pib, pedMat, 0f, 0.31f, 0f, 0.62f)

    }

    private fun buildBalls(count: Int, color: Color, rng: SplitMix64): MachineBalls {
        val entities = IntArray(count)
        val phase = FloatArray(count * 4)
        val rest = FloatArray(count * 3)
        val (vb, ib) = Stage3D.sphere(e)
        for (i in 0 until count) {
            phase[i * 4] = rng.nextInt(6283) / 1000f
            phase[i * 4 + 1] = rng.nextInt(6283) / 1000f
            phase[i * 4 + 2] = rng.nextInt(6283) / 1000f
            phase[i * 4 + 3] = 0.7f + rng.nextInt(60) / 100f
            val a = i * 2.399963f
            val rr = Stage3D.DOME_R * 0.62f * sqrt((i + 0.5f) / count)
            rest[i * 3] = rr * cos(a)
            rest[i * 3 + 1] = -Stage3D.DOME_R + Stage3D.BALL_R + if (i % 2 == 0) 0.02f else Stage3D.BALL_R * 0.9f
            rest[i * 3 + 2] = rr * sin(a)
            val mi = Stage3D.matBall().createInstance().also { mats.add(it) }
            mi.setParameter("baseColorMap", Stage3D.ballTexture(e, i + 1, color), Stage3D.sampler())
            mi.setParameter("baseColor", 1f, 1f, 1f, 1f)
            entities[i] = addStatic(
                vb, ib, mi,
                rest[i * 3], Stage3D.DOME_CY + rest[i * 3 + 1], rest[i * 3 + 2],
                Stage3D.BALL_R,
            )
        }
        return MachineBalls(entities, phase, rest)
    }

    private fun addStatic(
        vb: VertexBuffer, ib: IndexBuffer, mi: MaterialInstance,
        x: Float, y: Float, z: Float, radius: Float,
    ): Int {
        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(Box(-radius * 1.3f, -radius * 1.3f, -radius * 1.3f, radius * 1.3f, radius * 1.3f, radius * 1.3f))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib, 0, ib.indexCount)
            .material(0, mi)
            .culling(false)
            .castShadows(false)
            .receiveShadows(false)
            .build(e, entity)
        tm.create(entity)
        Matrix.setIdentityM(tmp, 0)
        Matrix.translateM(tmp, 0, x, y, z)
        tmpD.forEachIndexed { i, _ -> tmpD[i] = tmp[i].toDouble() }
        tm.setTransform(entity, tmpD)
        scene.addEntity(entity)
        own.add(entity)
        return entity
    }

    private fun showMachine(m: MachineBalls) {
        m.entities.forEach { scene.addEntity(it) }
    }

    private fun hideMachine(m: MachineBalls) {
        m.entities.forEach { scene.removeEntity(it) }
    }

    fun start() {
        if (frameCb != null) return
        frameCb = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (startNs < 0 || skipped) startNs = frameTimeNanos - (if (skipped) (beat.endAt - 60) * 1_000_000 else 0L)
                val t = ((frameTimeNanos - startNs) / 1_000_000).coerceIn(0, beat.endAt)
                update(t)
                publish(t)
                swapChain?.let { sc ->
                    if (renderer.beginFrame(sc, frameTimeNanos)) {
                        renderer.render(view)
                        renderer.endFrame()
                    }
                }
                if (t < beat.endAt) choreographer.postFrameCallback(this) else frameStop()
            }
        }
        choreographer.postFrameCallback(frameCb!!)
    }

    private fun frameStop() {
        frameCb?.let { choreographer.removeFrameCallback(it) }
        frameCb = null
    }

    fun skip() {
        skipped = true
    }

    fun dropStartOf(k: Int): Long {
        val base = if (k < n1) Stage3D.T_OPEN + Stage3D.T_WARM else beat.swapAt + Stage3D.T_SWAP
        return base + (if (k < n1) k else k - n1) * Stage3D.T_BALL + Stage3D.T_PRE
    }

    private fun publish(t: Long) {
        val blueOn = t >= beat.swapAt
        var drawn = 0
        var flash: Int? = null
        for (i in 0 until n1 + drawZone2.size) {
            val land = dropStartOf(i) + Stage3D.T_DROP
            if (t >= land + 140L) drawn++
            if (t >= land && t < land + Stage3D.T_LAND) {
                flash = if (i < n1) drawZone1[i] else drawZone2[i - n1]
            }
        }
        val next = StageUi(t, blueOn, drawn, flash, t >= beat.endAt - 1)
        if (next != lastUi) {
            ui.value = next
            lastUi = next
        }
    }

    private fun update(t: Long) {
        val blueOn = t >= beat.swapAt
        if (blueOn && !blueShown) {
            redM?.let { hideMachine(it) }; blueM?.let { showMachine(it) }
            redShown = false; blueShown = true
        } else if (!blueOn && !redShown) {
            blueM?.let { hideMachine(it) }; redM?.let { showMachine(it) }
            redShown = true; blueShown = false
        }
        val m = (if (blueOn) blueM else redM) ?: return
        val consumed = if (blueOn) consumedBlue else consumedRed

        val jets = when {
            t < Stage3D.T_OPEN -> 0f
            t < Stage3D.T_OPEN + Stage3D.T_WARM -> (t - Stage3D.T_OPEN).toFloat() / Stage3D.T_WARM
            blueOn && t < beat.swapAt + Stage3D.T_SWAP -> 0.45f
            else -> 1f
        }
        val tt = t / 1000f
        val dropK = dropInProgress(t)
        val dropNum = dropK?.let { if (it < n1) drawZone1[it] else drawZone2[it - n1] }

        m.entities.forEachIndexed { i, entity ->
            val num = i + 1
            var x: Float; var y: Float; var z: Float
            if (num == dropNum && dropK != null) {
                val ds = dropStartOf(dropK)
                val u = ((t - ds).toFloat() / Stage3D.T_DROP).coerceIn(0f, 1f)
                val f = flyPos(tt, m, i, jets)
                val suck = (u / 0.55f).coerceIn(0f, 1f)
                val topY = Stage3D.DOME_R - Stage3D.BALL_R * 1.4f
                val sx = f[0] * (1f - suck * suck)
                val sz = f[2] * (1f - suck * suck)
                val sy = f[1] + (topY - f[1]) * suck * suck
                if (u <= 0.55f) {
                    x = sx; y = sy; z = sz
                } else {
                    val w = ((u - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    x = sx * (1f - w); z = sz * (1f - w)
                    y = topY + w * w * (Stage3D.DOME_R + Stage3D.TUBE_H + 0.55f)
                }
                if (u >= 1f) consumed.add(num)
            } else if (num in consumed) {
                x = 0f; y = -6f; z = 0f
            } else {
                val f = flyPos(tt, m, i, jets)
                val j = jets
                x = m.rest[i * 3] + (f[0] * Stage3D.DOME_R * 0.78f - m.rest[i * 3]) * j
                y = m.rest[i * 3 + 1] + (f[1] * Stage3D.DOME_R * 0.72f - m.rest[i * 3 + 1]) * j
                z = m.rest[i * 3 + 2] + (f[2] * Stage3D.DOME_R * 0.78f - m.rest[i * 3 + 2]) * j
            }
            val wob = sin(tt * 1.7f + m.phase[i * 4]) * 0.16f
            Matrix.setIdentityM(tmp, 0)
            Matrix.translateM(tmp, 0, x, Stage3D.DOME_CY + y, z)
            Matrix.rotateM(tmp, 0, Math.toDegrees(wob.toDouble()).toFloat(), 0f, 1f, 0f)
            tmpD.forEachIndexed { i2, _ -> tmpD[i2] = tmp[i2].toDouble() }
            tm.setTransform(entity, tmpD)
        }

        var punch = 0f
        dropK?.let {
            val ds = dropStartOf(it)
            val u = ((t - ds).toFloat() / Stage3D.T_DROP).coerceIn(0f, 1f)
            if (u > 0.55f) punch = sin(((u - 0.55f) / 0.45f * PI).toFloat()) * 0.12f
        }
        val openDolly = (t.toFloat() / Stage3D.T_OPEN).coerceIn(0f, 1f)
        val dist = 3.02f - 0.22f * openDolly - punch
        val sway = sin(tt * 0.42f) * 0.10f
        camera.lookAt(
            (sway * dist).toDouble(), (Stage3D.DOME_CY + 0.30f).toDouble(), dist.toDouble(),
            0.0, (Stage3D.DOME_CY - 0.02f).toDouble(), 0.0,
            0.0, 1.0, 0.0,
        )
    }

    private fun dropInProgress(t: Long): Int? {
        for (i in 0 until n1 + drawZone2.size) {
            val ds = dropStartOf(i)
            if (t >= ds && t < ds + Stage3D.T_DROP) return i
        }
        return null
    }

    private fun flyPos(tt: Float, m: MachineBalls, i: Int, jets: Float): FloatArray {
        if (jets <= 0.02f) return REST_BOTTOM
        val p0 = m.phase[i * 4]; val p1 = m.phase[i * 4 + 1]
        val p2 = m.phase[i * 4 + 2]; val spd = m.phase[i * 4 + 3]
        val a = tt * 1.9f * spd + p0
        val b = tt * 1.3f * spd + p1
        val c = tt * 2.4f * spd + p2
        val breath = 0.55f + 0.42f * (0.5f + 0.5f * sin(tt * 1.1f * spd + p0 * 2f))
        var x = sin(a) * cos(b)
        var y = sin(b) * 0.82f + 0.06f * sin(c * 2f)
        var z = cos(a) * sin(b)
        val len = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-4f)
        val rr = breath / len
        x *= rr; y *= rr; z *= rr
        return floatArrayOf(x, y, z)
    }

    fun dispose() {
        frameStop()
        uiHelper.detach()
        own.forEach { ent ->
            scene.removeEntity(ent)
            e.destroyEntity(ent)
        }
        mats.forEach { e.destroyMaterialInstance(it) }
        e.destroyView(view)
        e.destroyScene(scene)
        e.destroyRenderer(renderer)
        // camera 组件随实体释放（Engine 无独立 destroyCamera）
        e.destroyEntity(camera.entity)
    }

    private companion object {
        val REST_BOTTOM = floatArrayOf(0f, -1f, 0f)
    }
}

/** Compose 包装：透明 SurfaceView（默认 z 序，位于窗口之下；窗口留孔透出）。 */
@Composable
fun DrawStage3D(
    director: StageDirector,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val sv = remember {
        SurfaceView(ctx).apply {
            holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        }
    }
    AndroidView(
        factory = {
            director.attach(sv)
            sv
        },
        modifier = modifier,
    )
    DisposableEffect(director) {
        onDispose { director.dispose() }
    }
}
