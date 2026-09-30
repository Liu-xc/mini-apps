package com.leo.lottery.ui.draw

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.leo.lottery.core.physics.DrawScene
import com.leo.lottery.ui.theme.LotteryPalette
import kotlin.math.*

/** A single coordinate system for chamber, capture outlet, pipe, rail and both rendering paths. */
class StageGeometry(val width: Float, val height: Float) {
    val r=min(width/3.8f,height/4.15f)
    val cx=width*.46f
    val cy=height*.36f
    fun point(x: Float,y: Float,z: Float=0f): Offset {
        val perspective=8f/(8f-z)
        return Offset(cx+x*r*perspective,cy+y*r*perspective)
    }
}

internal class BallPainter(val measurer: TextMeasurer) {
    private val layouts=HashMap<Int,TextLayoutResult>()
    fun layout(num: Int)=layouts.getOrPut(num) {
        measurer.measure(num.toString(),TextStyle(fontSize=13.sp,fontWeight=FontWeight.Bold,color=Color(0xFF252720)))
    }
}
internal class BallBrushes(base: Color, hi: Color) {
    val sphere=Brush.radialGradient(listOf(hi,base,Color(base.red*.45f,base.green*.45f,base.blue*.45f)),Offset(-14f,-18f),70f)
    val plate=Brush.radialGradient(listOf(Color(0xFFFDFCF8),Color(0xFFE8E6DC)),Offset(-5f,-7f),50f)
    companion object { const val R=40f }
}
internal fun DrawScope.drawGlossyBall(x: Float,y: Float,r: Float,num: Int,painter: BallPainter,brushes: BallBrushes,angle: Float=0f) {
    withTransform({ translate(x,y); scale(r/40f,r/40f,pivot=Offset.Zero) }) {
        drawCircle(brushes.sphere,40f,Offset.Zero)
        rotate(angle,pivot=Offset.Zero) {
            drawCircle(brushes.plate,26f,Offset.Zero)
            val res=painter.layout(num)
            drawText(res,topLeft=Offset(-res.size.width/2f,-res.size.height/2f))
        }
        drawCircle(Color.White.copy(alpha=.32f),4f,Offset(-17f,-20f))
    }
}

@Composable
fun MechanicalStage(scene: DrawScene, clock: State<Long>, foreground: Boolean, mainColor: Color, specialColor: Color,
    canvasBalls: Boolean=false, modifier: Modifier=Modifier) {
    val measurer=rememberTextMeasurer()
    val painter=remember(measurer) { BallPainter(measurer) }
    val red=remember(mainColor) { BallBrushes(mainColor,mainColor.copy(red=(mainColor.red+.15f).coerceAtMost(1f))) }
    val blue=remember(specialColor) { BallBrushes(specialColor,specialColor.copy(blue=(specialColor.blue+.15f).coerceAtMost(1f))) }
    Canvas(modifier) {
        clock.value // Drawing invalidation only. Physics is advanced by the single playback clock.
        val g=StageGeometry(size.width,size.height)
        val r=g.r; val c=g.point(0f,0f)
        if(!foreground) {
            val shadow=g.point(0f,1.57f)
            drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha=.40f),Color.Transparent),shadow,r*1.3f),
                shadow-Offset(r*1.3f,r*.22f),Size(r*2.6f,r*.44f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF27373A),Color(0xFF182226)),c-Offset(r*.3f,r*.3f),r*1.3f),r,c)
            val top=g.point(-.58f,.90f); val bottom=g.point(.65f,1.48f)
            val base=Path().apply { moveTo(top.x,top.y); lineTo(g.point(.58f,.90f).x,top.y); lineTo(bottom.x,bottom.y); lineTo(g.point(-.65f,1.48f).x,bottom.y); close() }
            drawPath(base,Brush.horizontalGradient(listOf(Color(0xFF455356),Color(0xFF87918E),Color(0xFF414E50)),top.x,bottom.x))
            drawLine(Color(0xFFBEC7C0),g.point(-.58f,1.02f),g.point(.58f,1.02f),r*.023f)
            repeat(7) { i -> drawLine(Color(0xFF263436),g.point(-.27f+i*.09f,1.18f),g.point(-.27f+i*.09f,1.30f),r*.023f) }
            // Transparent pipe wall. Centerline is exactly DrawScene.tube().
            val pipe=Path()
            val v=DrawScene.VisualBall(0,false)
            for(i in 0..100) { scene.tube(i/100f,v); val p=g.point(v.x,v.y); if(i==0)pipe.moveTo(p.x,p.y) else pipe.lineTo(p.x,p.y) }
            drawPath(pipe,Color(0xFFB8CDC9).copy(alpha=.26f),style=Stroke(r*.27f,cap=StrokeCap.Round,join=StrokeJoin.Round))
            drawPath(pipe,Color(0xFF131F22),style=Stroke(r*.215f,cap=StrokeCap.Round,join=StrokeJoin.Round))
            // Rail sits on two legs; the shallow slope defines rolling gravity direction.
            val left=g.point(-1.51f,scene.railY(-1.51f)+scene.radius)
            val right=g.point(1.63f,scene.railY(1.63f)+scene.radius)
            for(x in listOf(-1.12f,1.1f)) drawLine(Color(0xFF687977),g.point(x,2.02f),g.point(x,2.21f),r*.04f)
            drawLine(Color(0xFF98AAA6),left,right,r*.055f,StrokeCap.Round)
            drawLine(Color(0xFF263B3B),left+Offset(0f,r*.08f),right+Offset(0f,r*.08f),r*.07f,StrokeCap.Round)
            scene.numbers.indices.forEach { k ->
                val p=g.point(scene.slotX(k)-scene.radius*1.1f,scene.railY(scene.slotX(k)))
                drawLine(Color(0xFF718580),p+Offset(0f,r*.01f),p+Offset(0f,r*.16f),r*.024f)
            }
        } else {
            if(canvasBalls) {
                scene.visuals.filter { it.visible }.sortedBy { it.z }.forEach { b ->
                    val p=g.point(b.x,b.y,b.z)
                    drawGlossyBall(p.x,p.y,r*scene.radius*8f/(8f-b.z),b.number,painter,if(b.special)blue else red,b.rz)
                }
            }
            // Stationary glass reflections, no breathing machine or pulsing glow.
            drawCircle(Color(0xFFB4D0CE).copy(alpha=.32f),r,c,style=Stroke(r*.01f))
            drawArc(Color.White.copy(alpha=.18f),195f,65f,false,c-Offset(r*.91f,r*.91f),Size(r*1.82f,r*1.82f),style=Stroke(r*.035f,cap=StrokeCap.Round))
            drawArc(Color.White.copy(alpha=.07f),-10f,48f,false,c-Offset(r*.94f,r*.94f),Size(r*1.88f,r*1.88f),style=Stroke(r*.017f,cap=StrokeCap.Round))
            // Connection flange and pipe highlight remain above the moving sphere.
            val outlet=g.point(0f,-1.02f)
            drawLine(Color(0xFF809793),outlet-Offset(r*.17f,0f),outlet+Offset(r*.17f,0f),r*.035f)
            drawLine(Color.White.copy(alpha=.13f),g.point(1.23f,-.85f),g.point(1.23f,1.39f),r*.012f)
            val railL=g.point(-1.51f,scene.railY(-1.51f)+scene.radius+.015f)
            val railR=g.point(1.63f,scene.railY(1.63f)+scene.radius+.015f)
            drawLine(Color.White.copy(alpha=.20f),railL,railR,r*.012f)
        }
    }
}
