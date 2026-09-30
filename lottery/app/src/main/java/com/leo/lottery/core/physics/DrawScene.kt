package com.leo.lottery.core.physics

import kotlin.math.*

/** A shared mutable frame buffer; simulation advances once, renderers only consume it. */
class DrawScene(val main: List<Int>, val special: List<Int>, pool1: Int, pool2: Int, tag: String) {
    val first = DrawPhysics(pool1,"$tag|main")
    val second = DrawPhysics(pool2,"$tag|special")
    val numbers = main+special
    val endAt = DrawTiming.end(main.size,special.size)
    class VisualBall(val number: Int, val special: Boolean) {
        var x=0f; var y=0f; var z=0f
        var rx=0f; var ry=0f; var rz=0f
        var visible=false
    }
    val visuals = first.balls.map { VisualBall(it.number,false) } + second.balls.map { VisualBall(it.number,true) }
    var time=0L; private set
    var blue=false; private set
    private var steps=0L
    private val captured=BooleanArray(numbers.size)
    private val travelSpin = FloatArray(numbers.size)
    private val travelTiltX = FloatArray(numbers.size)
    private val travelTiltY = FloatArray(numbers.size)
    val radius = first.radius
    var drawn=0; private set
    var latest: Int?=null; private set
    val ended get()=time>=endAt

    fun advance(target: Long) {
        time=target.coerceIn(time,endAt)
        blue=time>=DrawTiming.swap(main.size)
        val desired=time*120/1000
        while (steps < desired) {
            val ms=steps*1000/120
            val useBlue=ms>=DrawTiming.swap(main.size)
            val world=if(useBlue) second else first
            numbers.indices.forEach { k ->
                if (!captured[k] && ms>=DrawTiming.start(k,main.size)) {
                    captured[k]=true
                    world.capture(numbers[k])
                    val b=world.balls[numbers[k]-1]
                    travelSpin[k]=b.az
                    travelTiltX[k]=b.ax
                    travelTiltY[k]=b.ay
                }
            }
            val warmStart=if(useBlue) DrawTiming.swap(main.size) else DrawTiming.OPEN
            val air=((ms-warmStart).toFloat()/DrawTiming.WARM).coerceIn(0f,1f)
            world.step(1f/120f, if(ms>=endAt-DrawTiming.RESULT) 0f else air)
            steps++
        }
        sync()
    }

    /** Skip does not run seconds of simulation in one UI frame. */
    fun finish() {
        time=endAt; blue=true
        numbers.indices.forEach { k ->
            val world=if(k<main.size) first else second
            world.balls[numbers[k]-1].active=false
        }
        steps=time*120/1000
        sync()
    }

    private fun sync() {
        drawn=0; latest=null
        val current=if(blue) second else first
        visuals.forEach { v ->
            v.visible=v.special==blue
            if (v.special==blue) {
                val b=current.balls[v.number-1]
                v.x=b.x; v.y=b.y; v.z=b.z; v.rx=b.ax; v.ry=b.ay; v.rz=b.az
                v.visible=b.active
            }
        }
        numbers.indices.forEach { k ->
            val departure=DrawTiming.start(k,main.size)+DrawTiming.CAPTURE
            if(time<departure) return@forEach
            val v=visuals[(if(k<main.size) 0 else first.balls.size)+numbers[k]-1]
            val age=time-departure
            v.visible=true; v.z=0f
            val tilt=(1f-age/240f).coerceIn(0f,1f)
            v.rx=travelTiltX[k]*tilt; v.ry=travelTiltY[k]*tilt
            val railStart=1.50f
            val stop=slotX(k)
            val tubeTime=750f
            when {
                age<tubeTime -> {
                    val u=(age/tubeTime).coerceIn(0f,1f)
                    tube(u*u*(3f-2f*u),v)
                    v.rz=travelSpin[k]+u*720f
                }
                age<DrawTiming.TRAVEL -> {
                    val u=(age-tubeTime)/(DrawTiming.TRAVEL-tubeTime)
                    v.x=railStart+(stop-railStart)*u*u
                    v.y=railY(v.x)
                    v.rz=travelSpin[k]+720f+(v.x-railStart)/radius*57.29578f
                }
                else -> {
                    val since=(age-DrawTiming.TRAVEL)/1000f
                    val bounce=if(since<.38f) .065f*sin(since*PI.toFloat()/.38f)*exp(-since*8f) else 0f
                    v.x=stop+bounce; v.y=railY(v.x)
                    v.rz=travelSpin[k]+720f+(v.x-railStart)/radius*57.29578f
                    drawn++
                    if(age<DrawTiming.TRAVEL+DrawTiming.HOLD) latest=numbers[k]
                }
            }
        }
    }

    fun slotX(k: Int): Float = -1.35f+k*.39f+if(k>=main.size) .13f else 0f
    fun railY(x: Float)=1.87f-.035f*x
    /** Arc length parameterized pipe: smooth corners, no airborne shortcuts. */
    fun tube(u: Float, v: VisualBall) {
        val q=u.coerceIn(0f,1f)
        when {
            q<.28f -> {
                val a=q/.28f*PI.toFloat()*.5f
                v.x=1.35f*sin(a); v.y=-1.02f-.22f*sin(a*2f)
            }
            q<.84f -> { val a=(q-.28f)/.56f; v.x=1.35f; v.y=-1.02f+a*2.50f }
            else -> {
                val a=(q-.84f)/.16f
                v.x=1.35f+.15f*sin(a*PI.toFloat()*.5f)
                v.y=1.48f+(railY(1.5f)-1.48f)*a
            }
        }
    }
}
