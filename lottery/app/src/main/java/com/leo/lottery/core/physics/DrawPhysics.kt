package com.leo.lottery.core.physics

import com.leo.lottery.core.SeedHash
import com.leo.lottery.core.SplitMix64
import kotlin.math.*

/** Meter-independent, seeded 3D sphere contacts. Screen y points down. */
class DrawPhysics(count: Int, seed: String) {
    class Ball(val number: Int, var x: Float, var y: Float, var z: Float) {
        var vx = 0f; var vy = 0f; var vz = 0f
        var ax = 0f; var ay = 0f; var az = 0f
        var active = true
        var captured = false
    }
    val radius = .105f
    val balls: List<Ball>
    var elapsed = 0f; private set
    private var capture: Ball? = null
    private var captureTime = 0f
    private var sx = 0f; private var sy = 0f; private var sz = 0f
    private var svx = 0f; private var svy = 0f; private var svz = 0f

    init {
        val rng = SplitMix64(SeedHash.seedLong(seed.toByteArray()))
        val points = mutableListOf<Ball>()
        // Distinct lattice positions, then gravity settles the population before first display.
        for (iy in 3 downTo -3) for (iz in -3..3) for (ix in -3..3) {
            val x = ix * .218f; val y = iy * .218f; val z = iz * .218f
            if (x*x + y*y + z*z < .78f*.78f && points.size < count) {
                points += Ball(points.size + 1, x, y, z).also {
                    it.ax = rng.nextInt(360).toFloat(); it.ay = rng.nextInt(360).toFloat()
                }
            }
        }
        require(points.size == count) { "Population exceeds chamber capacity" }
        balls = points
        repeat(360) { step(1f / 120f, 0f) }
        elapsed = 0f
    }

    fun capture(number: Int) {
        val b = balls.firstOrNull { it.number == number && it.active } ?: return
        capture = b; b.captured = true; captureTime = 0f
        sx = b.x; sy = b.y; sz = b.z; svx = b.vx; svy = b.vy; svz = b.vz
    }

    /** Bounded substep, impulses dissipate energy. No frame delta is fed directly to this method. */
    fun step(dt: Float, air: Float) {
        require(dt > 0f && dt <= 1f / 60f)
        elapsed += dt
        val wind = air.coerceIn(0f, 1f)
        for (b in balls) {
            if (!b.active || b.captured) continue
            val jet = exp(-(b.x*b.x + b.z*b.z) * 3.8f)
            val p = elapsed * 3.7f + b.number * 1.618f
            // A central upward jet with swirl and changing pressure; forces, never prescribed positions.
            val fx = wind * (-b.z * 8f + sin(p) * 5f + b.x * 2f)
            val fy = 5.5f - wind * (jet * 21f + 1.4f * sin(p * .71f))
            val fz = wind * (b.x * 8f + cos(p * .83f) * 5f + b.z * 2f)
            val drag = exp(-(if (wind > .1f) .7f else 1.8f) * dt)
            b.vx = ((b.vx + fx * dt) * drag).coerceIn(-5f, 5f)
            b.vy = ((b.vy + fy * dt) * drag).coerceIn(-5f, 5f)
            b.vz = ((b.vz + fz * dt) * drag).coerceIn(-5f, 5f)
            b.x += b.vx * dt; b.y += b.vy * dt; b.z += b.vz * dt
            b.ax += b.vz * dt / radius * 57.29578f
            b.ay += b.vx * dt / radius * 57.29578f
            b.az -= b.vx * dt / radius * 25f
        }
        capture?.let { b ->
            captureTime += dt
            val duration = .65f
            val u = (captureTime / duration).coerceIn(0f,1f)
            // Hermite capture: start position AND velocity continuous; outlet velocity tends to zero.
            val h0 = 2*u*u*u-3*u*u+1; val h1 = u*u*u-2*u*u+u
            val h2 = -2*u*u*u+3*u*u
            val oldX=b.x; val oldY=b.y; val oldZ=b.z
            b.x = h0*sx+h1*duration*svx
            b.y = h0*sy+h1*duration*svy+h2*(-1.02f)
            b.z = h0*sz+h1*duration*svz
            // Stay inside the chamber until aligned with its open neck.
            val length=sqrt(b.x*b.x+b.y*b.y+b.z*b.z)
            val neck = b.y < -.78f && abs(b.x)<.14f && abs(b.z)<.14f
            if(!neck && length>1f-radius) { val scale=(1f-radius)/length; b.x*=scale; b.y*=scale; b.z*=scale }
            b.vx=(b.x-oldX)/dt; b.vy=(b.y-oldY)/dt; b.vz=(b.z-oldZ)/dt
            if (u >= 1f) { b.active = false; capture = null }
        }
        repeat(4) {
            for (i in balls.indices) {
                val a = balls[i]
                if (!a.active) continue
                for (j in i+1 until balls.size) {
                    val b = balls[j]
                    if (!b.active) continue
                    var dx = b.x-a.x; var dy = b.y-a.y; var dz = b.z-a.z
                    val d2 = dx*dx+dy*dy+dz*dz
                    val diameter = radius*2f
                    if (d2 >= diameter*diameter) continue
                    val d = sqrt(d2).coerceAtLeast(.00001f)
                    if (d2 < 1e-10f) { dx = 1f; dy = 0f; dz = 0f }
                    else { dx /= d; dy /= d; dz /= d }
                    val ma = if(a.captured) 0f else 1f
                    val mb = if(b.captured) 0f else 1f
                    val mass = (ma+mb).coerceAtLeast(1f)
                    val correction = (diameter-d+.00001f)/mass
                    a.x -= dx*correction*ma; a.y -= dy*correction*ma; a.z -= dz*correction*ma
                    b.x += dx*correction*mb; b.y += dy*correction*mb; b.z += dz*correction*mb
                    val approach = (b.vx-a.vx)*dx+(b.vy-a.vy)*dy+(b.vz-a.vz)*dz
                    if (approach < 0f) {
                        val impulse = -(1f + .58f)*approach/mass
                        a.vx -= impulse*dx*ma; a.vy -= impulse*dy*ma; a.vz -= impulse*dz*ma
                        b.vx += impulse*dx*mb; b.vy += impulse*dy*mb; b.vz += impulse*dz*mb
                        // Surface friction dissipates tangential slip.
                        val tx = (b.vx-a.vx)-dx*((b.vx-a.vx)*dx+(b.vy-a.vy)*dy+(b.vz-a.vz)*dz)
                        a.vx += tx*.025f*ma; b.vx -= tx*.025f*mb
                    }
                }
                if(!a.captured) wall(a)
            }
        }

    }

    private fun wall(b: Ball) {
        val length = sqrt(b.x*b.x+b.y*b.y+b.z*b.z)
        val limit = 1f-radius
        if (length <= limit) return
        val nx=b.x/length; val ny=b.y/length; val nz=b.z/length
        b.x=nx*limit; b.y=ny*limit; b.z=nz*limit
        val outward=b.vx*nx+b.vy*ny+b.vz*nz
        if (outward > 0f) {
            b.vx=(b.vx-1.55f*outward*nx)*.97f
            b.vy=(b.vy-1.55f*outward*ny)*.97f
            b.vz=(b.vz-1.55f*outward*nz)*.97f
        }
    }
}

/** One timing source shared by physical scene, number readout and verdict. */
object DrawTiming {
    const val OPEN = 650L
    const val WARM = 1100L
    const val CAPTURE = 650L
    const val TRAVEL = 1250L
    const val HOLD = 450L
    const val BALL = CAPTURE + TRAVEL + HOLD
    const val SWAP = 900L
    const val RESULT = 600L
    fun start(k: Int, main: Int): Long = if (k < main) OPEN+WARM+k*BALL
        else swap(main)+SWAP+(k-main)*BALL
    fun swap(main: Int) = OPEN+WARM+main*BALL
    fun end(main: Int, special: Int) = swap(main)+SWAP+special*BALL+RESULT
}
