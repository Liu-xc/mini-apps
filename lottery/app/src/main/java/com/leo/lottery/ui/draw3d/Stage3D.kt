package com.leo.lottery.ui.draw3d

import android.graphics.*
import android.opengl.Matrix
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.*
import com.google.android.filament.Camera
import com.google.android.filament.android.UiHelper
import com.leo.lottery.core.physics.DrawScene
import com.leo.lottery.ui.draw.StageGeometry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Immutable GPU assets live for the process; scene entities and surfaces are released per replay. */
private object FilamentRuntime {
    val engine: Engine by lazy { Filament.init(); Engine.create(Engine.Backend.OPENGL) }
    val sphere by lazy { sphere(engine) }
    val cylinder by lazy { cylinder(engine) }
    private val materials=HashMap<String,Material>()
    private val textures=HashMap<String,Texture>()
    fun material(context: android.content.Context,name: String)=materials.getOrPut(name) {
        val bytes=context.assets.open(name).use { it.readBytes() }
        val buffer=ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
        Material.Builder().payload(buffer,bytes.size).build(engine)
    }
    fun texture(number: Int,color: Color)=textures.getOrPut("$number|${color.value}") { texture(engine,number,color) }
}

/** Lit UV spheres, driven by the same physical frame buffer as the mechanical drawing. */
private class PhysicalRenderer(val context: android.content.Context, val model: DrawScene, mainColor: Color, specialColor: Color, private val onReady: ()->Unit) {
    private val engine: Engine
    private val renderer: Renderer
    private val scene: Scene
    private val view: View
    private val camera: Camera
    private val cameraEntity: Int
    private val material: Material
    private val vb: VertexBuffer
    private val ib: IndexBuffer
    private val entities=IntArray(model.visuals.size)
    private val instances=IntArray(model.visuals.size)
    private val visible=BooleanArray(model.visuals.size)
    private val staticEntities=ArrayList<Int>()
    private val materials=ArrayList<MaterialInstance>()
    private val lights=ArrayList<Int>()
    private val ambient: IndirectLight
    private val matrix=FloatArray(16)
    private var swap: SwapChain?=null
    private val helper=UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val choreographer=Choreographer.getInstance()
    private var closed=false
    private var lastTime=Long.MIN_VALUE
    private var geometry: StageGeometry?=null
    private var firstFrameReady=false
    private var presented=0
    private var renderCost=0L
    private val callback=object: Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if(closed) return
            if((model.time!=lastTime || !firstFrameReady) && geometry!=null) {
                val started=System.nanoTime()
                update()
                swap?.let { sc ->
                    if(renderer.beginFrame(sc,frameTimeNanos)) {
                        renderer.render(view)
                        renderer.endFrame()
                        if(presented==0) {
                            // Fence first GPU frame before advancing content; only a single pixel is read.
                            val pixel=ByteBuffer.allocateDirect(4)
                            val descriptor=Texture.PixelBufferDescriptor(pixel,Texture.Format.RGBA,Texture.Type.UBYTE)
                            descriptor.setCallback(android.os.Handler(android.os.Looper.getMainLooper()),Runnable { if(!closed) { firstFrameReady=true; onReady(); android.util.Log.i("Lottery3D", "First GPU frame ready") } })
                            renderer.readPixels(0,0,1,1,descriptor)
                        }
                        presented++; renderCost+=System.nanoTime()-started
                    }
                }
                lastTime=model.time
            }
            choreographer.postFrameCallback(this)
        }
    }
    init {
        engine=FilamentRuntime.engine
        renderer=engine.createRenderer(); scene=engine.createScene(); view=engine.createView()
        cameraEntity=EntityManager.get().create(); camera=engine.createCamera(cameraEntity)
        view.scene=scene; view.camera=camera
        view.blendMode=View.BlendMode.TRANSLUCENT
        view.antiAliasing=View.AntiAliasing.FXAA
        view.ambientOcclusionOptions=View.AmbientOcclusionOptions().apply { enabled=false }
        renderer.clearOptions=Renderer.ClearOptions().apply { clear=true; clearColor=floatArrayOf(0f,0f,0f,0f) }
        camera.setExposure(16f,1f/125f,100f)
        material=FilamentRuntime.material(context,"lottery-ball.filamat")
        val mesh=FilamentRuntime.sphere; vb=mesh.first; ib=mesh.second
        model.visuals.forEachIndexed { i,b ->
            val tex=FilamentRuntime.texture(b.number,if(b.special) specialColor else mainColor)
            val mi=material.createInstance(); materials+=mi
            mi.setParameter("numbers",tex,TextureSampler(TextureSampler.MinFilter.LINEAR,TextureSampler.MagFilter.LINEAR,TextureSampler.WrapMode.CLAMP_TO_EDGE))
            val entity=EntityManager.get().create(); entities[i]=entity
            RenderableManager.Builder(1).boundingBox(Box(0f,0f,0f,1f,1f,1f))
                .geometry(0,RenderableManager.PrimitiveType.TRIANGLES,vb,ib)
                .material(0,mi).culling(false).castShadows(false).receiveShadows(true).build(engine,entity)
            // Native API takes a component instance, not the entity id.
            instances[i]=engine.transformManager.getInstance(entity).let { if(it!=0) it else engine.transformManager.create(entity) }
        }
        // Real metal base and transparent Fresnel shell, using the same scene coordinates.
        val glass=FilamentRuntime.material(context,"lottery-glass.filamat")
        addStatic(vb,ib,glass,0f,0f,0f,1f,1f,1f)
        val metal=FilamentRuntime.material(context,"lottery-metal.filamat")
        val cylinder=FilamentRuntime.cylinder
        addStatic(cylinder.first,cylinder.second,metal,0f,-1.19f,0f,1f,1f,1f)
        ambient=IndirectLight.Builder().irradiance(1,floatArrayOf(.8f,.85f,.9f)).intensity(18000f).build(engine)
        scene.indirectLight=ambient
        addLight(-.6f,-.8f,-1f,1f,.97f,.90f,75000f)
        addLight(.8f,.15f,-1f,.65f,.8f,1f,25000f)
        helper.setOpaque(false)
        helper.renderCallback=object: UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swap?.let(engine::destroySwapChain)
                swap=engine.createSwapChain(surface,helper.swapChainFlags); lastTime=Long.MIN_VALUE
            }
            override fun onDetachedFromSurface() {
                swap?.let(engine::destroySwapChain); swap=null
                engine.flushAndWait()
            }
            override fun onResized(width: Int,height: Int) {
                if(width<=0 || height<=0) return
                // TextureView recreates its buffer after backgrounding; use actual surface dimensions.
                val renderWidth=width
                val renderHeight=height
                view.viewport=Viewport(0,0,renderWidth,renderHeight)
                val g=StageGeometry(renderWidth.toFloat(),renderHeight.toFloat()); geometry=g
                // Off-axis perspective exactly matches Canvas projection, without any animated camera sway.
                val near=.1; val dist=8.0
                camera.setProjection(Camera.Projection.PERSPECTIVE,
                    -g.cx/g.r*near/dist,(renderWidth-g.cx)/g.r*near/dist,
                    -(renderHeight-g.cy)/g.r*near/dist,g.cy/g.r*near/dist,near,30.0)
                camera.lookAt(0.0,0.0,dist,0.0,0.0,0.0,0.0,1.0,0.0)
                lastTime=Long.MIN_VALUE
            }
        }
        android.util.Log.i("Lottery3D","LIT physical renderer ready; ${entities.size} spheres; uniqueInstances=${entities.map { engine.transformManager.getInstance(it) }.distinct().size}")
    }
    private fun addStatic(vertices: VertexBuffer, indices: IndexBuffer, mat: Material,x: Float,y: Float,z: Float,sx: Float,sy: Float,sz: Float) {
        val entity=EntityManager.get().create(); staticEntities+=entity
        val mi=mat.createInstance(); materials+=mi
        RenderableManager.Builder(1).boundingBox(Box(0f,0f,0f,1f,1f,1f))
            .geometry(0,RenderableManager.PrimitiveType.TRIANGLES,vertices,indices)
            .material(0,mi).culling(false).castShadows(false).receiveShadows(true).build(engine,entity)
        val instance=engine.transformManager.getInstance(entity).let { if(it!=0) it else engine.transformManager.create(entity) }
        Matrix.setIdentityM(matrix,0); Matrix.translateM(matrix,0,x,y,z); Matrix.scaleM(matrix,0,sx,sy,sz)
        engine.transformManager.setTransform(instance,matrix); scene.addEntity(entity)
    }
    fun attach(surface: TextureView) {
        helper.attachTo(surface); choreographer.postFrameCallback(callback)
    }
    fun resize(width: Int,height: Int) {
        if(width>0 && height>0) helper.setDesiredSize(width,height)
    }
    private fun addLight(x: Float,y: Float,z: Float,r: Float,g: Float,b: Float,power: Float) {
        val e=EntityManager.get().create(); lights+=e
        LightManager.Builder(LightManager.Type.DIRECTIONAL).direction(x,y,z).color(r,g,b).intensity(power).castShadows(false).build(engine,e)
        scene.addEntity(e)
    }
    private fun update() {
        val tm=engine.transformManager
        tm.openLocalTransformTransaction()
        model.visuals.forEachIndexed { i,b ->
            if(b.visible!=visible[i]) {
                if(b.visible) scene.addEntity(entities[i]) else scene.removeEntity(entities[i])
                visible[i]=b.visible
            }
            if(b.visible) {
                Matrix.setIdentityM(matrix,0)
                Matrix.translateM(matrix,0,b.x,-b.y,b.z)
                Matrix.rotateM(matrix,0,b.rx,1f,0f,0f)
                Matrix.rotateM(matrix,0,b.ry,0f,1f,0f)
                Matrix.rotateM(matrix,0,-b.rz,0f,0f,1f)
                Matrix.scaleM(matrix,0,model.radius,model.radius,model.radius)
                tm.setTransform(tm.getInstance(entities[i]),matrix)
            }
        }
        tm.commitLocalTransformTransaction()
    }
    fun close() {
        if(closed) return
        closed=true; choreographer.removeFrameCallback(callback)
        helper.detach(); engine.flushAndWait()
        (entities.toList()+staticEntities).forEach { engine.destroyEntity(it); EntityManager.get().destroy(it) }
        lights.forEach { engine.destroyEntity(it); EntityManager.get().destroy(it) }
        materials.forEach(engine::destroyMaterialInstance)
        engine.destroyIndirectLight(ambient); engine.destroyView(view); engine.destroyScene(scene)
        engine.destroyRenderer(renderer); engine.destroyCameraComponent(cameraEntity); EntityManager.get().destroy(cameraEntity)
        android.util.Log.i("Lottery3D","Disposed; presented=$presented cpuSubmitMeanMs=${if(presented>0) renderCost/1e6/presented else 0.0}")
    }
}

@Composable
fun DrawStage3D(model: DrawScene, mainColor: Color, specialColor: Color, modifier: Modifier=Modifier, onReady: ()->Unit={}) {
    val context=LocalContext.current
    val ready by rememberUpdatedState(onReady)
    var renderer by remember(model) { mutableStateOf<PhysicalRenderer?>(null) }
    LaunchedEffect(model) {
        withFrameNanos { }; withFrameNanos { }
        renderer=PhysicalRenderer(context,model,mainColor,specialColor) { ready() }
    }
    val active=renderer ?: return
    AndroidView(factory={ TextureView(context).apply {
        isOpaque=false
        addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            active.resize(right-left,bottom-top)
        }
        active.attach(this)
    } },modifier=modifier)
    DisposableEffect(active) { onDispose { active.close() } }
}

private fun sphere(engine: Engine): Pair<VertexBuffer,IndexBuffer> {
    val uCount=24; val vCount=16
    val verts=FloatArray((uCount+1)*(vCount+1)*9)
    var k=0
    for(v in 0..vCount) for(u in 0..uCount) {
        val lat=PI/2-v.toDouble()/vCount*PI
        val lon=(u.toDouble()/uCount-.5)*PI*2
        val x=(cos(lat)*sin(lon)).toFloat(); val y=sin(lat).toFloat(); val z=(cos(lat)*cos(lon)).toFloat()
        verts[k++]=x; verts[k++]=y; verts[k++]=z
        // Quaternion mapping the tangent frame's +z normal to the sphere normal.
        if(z < -.9999f) { verts[k++]=1f; verts[k++]=0f; verts[k++]=0f; verts[k++]=0f }
        else { val q=sqrt(y*y+x*x+(1+z)*(1+z)); verts[k++]=-y/q; verts[k++]=x/q; verts[k++]=0f; verts[k++]=(1+z)/q }
        verts[k++]=u.toFloat()/uCount; verts[k++]=1f-v.toFloat()/vCount
    }
    val indices=ShortArray(uCount*vCount*6); var p=0
    for(v in 0 until vCount) for(u in 0 until uCount) {
        val a=v*(uCount+1)+u; val b=a+1; val c=a+uCount+1; val d=c+1
        indices[p++]=a.toShort(); indices[p++]=c.toShort(); indices[p++]=b.toShort()
        indices[p++]=b.toShort(); indices[p++]=c.toShort(); indices[p++]=d.toShort()
    }
    val vb=VertexBuffer.Builder().bufferCount(1).vertexCount(verts.size/9)
        .attribute(VertexBuffer.VertexAttribute.POSITION,0,VertexBuffer.AttributeType.FLOAT3,0,36)
        .attribute(VertexBuffer.VertexAttribute.TANGENTS,0,VertexBuffer.AttributeType.FLOAT4,12,36)
        .attribute(VertexBuffer.VertexAttribute.UV0,0,VertexBuffer.AttributeType.FLOAT2,28,36).build(engine)
    val vertices=ByteBuffer.allocateDirect(verts.size*4).order(ByteOrder.nativeOrder()); vertices.asFloatBuffer().put(verts)
    vb.setBufferAt(engine,0,vertices)
    val ib=IndexBuffer.Builder().indexCount(indices.size).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
    val idx=ByteBuffer.allocateDirect(indices.size*2).order(ByteOrder.nativeOrder()); idx.asShortBuffer().put(indices); ib.setBuffer(engine,idx)
    return vb to ib
}
private fun texture(engine: Engine,number: Int,color: Color): Texture {
    val w=256; val h=128
    val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
    val c=Canvas(bitmap); c.drawColor(android.graphics.Color.rgb((color.red*255).toInt(),(color.green*255).toInt(),(color.blue*255).toInt()))
    val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    // Two small printed discs on opposite sides; the disc rotates away rather than always facing the camera.
    for(x in listOf(0f,128f,256f)) {
        paint.color=android.graphics.Color.rgb(248,247,240); c.drawCircle(x,64f,23f,paint)
        paint.color=android.graphics.Color.rgb(25,30,27); paint.textSize=24f; paint.textAlign=Paint.Align.CENTER
        paint.typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
        c.drawText("%02d".format(number),x,64f-(paint.fontMetrics.ascent+paint.fontMetrics.descent)/2,paint)
    }
    val pixels=IntArray(w*h); bitmap.getPixels(pixels,0,w,0,0,w,h); bitmap.recycle()
    val data=ByteBuffer.allocateDirect(w*h*4)
    pixels.forEach { data.put((it shr 16).toByte()); data.put((it shr 8).toByte()); data.put(it.toByte()); data.put(255.toByte()) }; data.flip()
    val tex=Texture.Builder().width(w).height(h).levels(1).sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.SRGB8_A8).build(engine)
    tex.setImage(engine,0,Texture.PixelBufferDescriptor(data,Texture.Format.RGBA,Texture.Type.UBYTE))
    return tex
}

private fun cylinder(engine: Engine): Pair<VertexBuffer,IndexBuffer> {
    val segments=32
    val data=ArrayList<Float>(); val index=ArrayList<Short>()
    fun vertex(x: Float,y: Float,z: Float,nx: Float,ny: Float,nz: Float) {
        data.addAll(listOf(x,y,z))
        val length=sqrt(nx*nx+ny*ny+(1f+nz)*(1f+nz))
        if(length<.001f) data.addAll(listOf(1f,0f,0f,0f)) else data.addAll(listOf(-ny/length,nx/length,0f,(1f+nz)/length))
        data.addAll(listOf(0f,0f))
    }
    for(i in 0..segments) {
        val a=i.toFloat()/segments*PI.toFloat()*2
        val x=sin(a); val z=cos(a)
        vertex(x*.58f,.29f,z*.58f,x,0f,z)
        vertex(x*.65f,-.29f,z*.65f,x,0f,z)
    }
    for(i in 0 until segments) {
        val a=i*2
        listOf(a,a+1,a+2,a+2,a+1,a+3).forEach { index+=it.toShort() }
    }
    for(top in listOf(true,false)) {
        val offset=data.size/9; val y=if(top).29f else -.29f; val r=if(top).58f else .65f
        vertex(0f,y,0f,0f,if(top)1f else -1f,0f)
        for(i in 0..segments) { val a=i.toFloat()/segments*PI.toFloat()*2; vertex(sin(a)*r,y,cos(a)*r,0f,if(top)1f else -1f,0f) }
        for(i in 0 until segments) { listOf(offset,offset+i+1,offset+i+2).forEach { index+=it.toShort() } }
    }
    val vb=VertexBuffer.Builder().bufferCount(1).vertexCount(data.size/9)
        .attribute(VertexBuffer.VertexAttribute.POSITION,0,VertexBuffer.AttributeType.FLOAT3,0,36)
        .attribute(VertexBuffer.VertexAttribute.TANGENTS,0,VertexBuffer.AttributeType.FLOAT4,12,36)
        .attribute(VertexBuffer.VertexAttribute.UV0,0,VertexBuffer.AttributeType.FLOAT2,28,36).build(engine)
    val vertices=ByteBuffer.allocateDirect(data.size*4).order(ByteOrder.nativeOrder()); vertices.asFloatBuffer().put(data.toFloatArray()); vb.setBufferAt(engine,0,vertices)
    val ib=IndexBuffer.Builder().indexCount(index.size).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
    val indices=ByteBuffer.allocateDirect(index.size*2).order(ByteOrder.nativeOrder()); indices.asShortBuffer().put(index.toShortArray()); ib.setBuffer(engine,indices)
    return vb to ib
}
