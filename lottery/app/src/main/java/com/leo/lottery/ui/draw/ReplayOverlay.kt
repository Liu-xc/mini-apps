package com.leo.lottery.ui.draw

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.leo.lottery.LotteryViewModel
import com.leo.lottery.core.*
import com.leo.lottery.core.physics.*
import com.leo.lottery.platform.Haptics
import com.leo.lottery.ui.common.Ball
import com.leo.lottery.ui.draw3d.DrawStage3D
import com.leo.lottery.ui.theme.*
import kotlinx.coroutines.isActive

private data class ReplayUi(val count: Int=0,val blue: Boolean=false,val latest: Int?=null,val ended: Boolean=false,val phase: Int=0)

@Composable
fun ReplayOverlay(vm: LotteryViewModel,state: LotteryViewModel.UiState) {
    val result=vm.resultOf(state.draw.game,state.draw.issue)
    if(result==null) { LaunchedEffect(Unit) { vm.finishReplay() }; return }
    val context=LocalContext.current
    DisposableEffect(context) {
        val activity=context as? ComponentActivity
        activity?.enableEdgeToEdge(
            statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        onDispose { activity?.enableEdgeToEdge() }
    }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val colors=LocalLotteryColors.current
    val simplified=remember { context.getSharedPreferences("experience",0).getBoolean("simple_draw",false) }
    val resumeTime=rememberSaveable { mutableLongStateOf(0L) }
    val model=remember(result) { DrawScene(result.zone1,result.zone2,result.game.poolZone1,result.game.poolZone2,"${result.game}|${result.issue}").apply {
        if(simplified || resumeTime.longValue >= endAt) finish() else advance(resumeTime.longValue)
    } }
    val clock=remember { mutableLongStateOf(model.time) }
    var ui by remember { mutableStateOf(ReplayUi(ended=model.ended,count=model.drawn,blue=model.blue,latest=model.latest)) }
    var sceneReady by remember { mutableStateOf(simplified) }
    var skip by remember { mutableStateOf(false) }
    val tickets=vm.ticketsFor(result.game,result.issue)
    val verdicts=remember(result,tickets) { tickets.map { it to Verify.verify(it,result) } }
    val won=verdicts.any { it.second.won }
    LaunchedEffect(model) {
        var previous=0L
        var elapsedNs=model.time*1_000_000
        var lastCount=0
        var announcedResult=false
        val intervals=ArrayList<Double>()
        var simNs=0L; var frames=0
        while(isActive && !model.ended) {
            withFrameNanos { now ->
                if(!sceneReady && !skip) { previous=0L; return@withFrameNanos }
                if(!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { previous=0L; return@withFrameNanos }
                if(previous!=0L) {
                    val raw=now-previous
                    intervals+=raw/1e6
                    elapsedNs+=raw
                }
                previous=now
                val start=System.nanoTime()
                if(skip) model.finish() else model.advance(elapsedNs/1_000_000)
                simNs+=System.nanoTime()-start; frames++
                clock.longValue=model.time
                resumeTime.longValue=model.time
                val phase=when {
                    model.time<DrawTiming.OPEN -> 0
                    model.time<DrawTiming.OPEN+DrawTiming.WARM -> 1
                    model.blue && model.time<DrawTiming.swap(result.zone1.size)+DrawTiming.SWAP -> 3
                    else -> 2
                }
                val next=ReplayUi(model.drawn,model.blue,model.latest,model.ended,phase)
                if(next!=ui) ui=next
                if(model.drawn>lastCount && !skip) Haptics.tick(context)
                lastCount=model.drawn
                if(model.ended && won && !announcedResult) { Haptics.confirm(context); announcedResult=true }
            }
        }
        if(intervals.isNotEmpty()) {
            intervals.sort()
            Log.i("LotteryPlayback","frames=$frames p50Ms=${intervals[intervals.size/2]} p95Ms=${intervals[(intervals.size*.95).toInt().coerceAtMost(intervals.lastIndex)]} over32Ms=${intervals.count{it>32}} simMeanMs=${simNs/1e6/frames}")
        }
    }
    val ink=LotteryPalette.StageInk
    val faint=LotteryPalette.StageFaint
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LotteryPalette.TheaterHi,LotteryPalette.Theater))).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start=20.dp,end=8.dp,top=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("开奖时刻",color=ink,fontSize=23.sp,fontWeight=FontWeight.Medium)
                Text("${result.game.label} / 第 ${result.issue} 期",color=faint,style=MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick={ if(ui.ended)vm.finishReplay() else skip=true }) { Text(if(ui.ended)"关闭" else "跳过",color=ink) }
        }
        Text("演示数据 · 非官方",Modifier.padding(start=20.dp,top=8.dp),color=faint,style=MaterialTheme.typography.labelSmall)
        if(!simplified) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                MechanicalStage(model,clock,false,colors.zone1(result.game),colors.zone2(result.game),modifier=Modifier.fillMaxSize())
                DrawStage3D(model,colors.zone1(result.game),colors.zone2(result.game),Modifier.fillMaxSize()) { sceneReady=true }
                MechanicalStage(model,clock,true,colors.zone1(result.game),colors.zone2(result.game),canvasBalls=!sceneReady,modifier=Modifier.fillMaxSize())
                Text(if(ui.blue)result.game.zone2Label+"摇奖机" else result.game.zone1Label+"摇奖机",
                    Modifier.align(Alignment.BottomStart).padding(start=20.dp,bottom=8.dp),color=faint,style=MaterialTheme.typography.labelSmall)
            }
        } else Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth().height(228.dp).padding(horizontal=20.dp).padding(bottom=16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            if(!ui.ended) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(when(ui.phase) { 0->"静候开场"; 1->"气流启动"; 3->"切换${result.game.zone2Label}机"; else->if(ui.latest!=null)"号码已落定" else "翻滚 · 等待下一颗球" },color=ink,style=MaterialTheme.typography.titleMedium)
                        Text("已出 ${ui.count} / ${model.numbers.size} 球",color=faint,style=MaterialTheme.typography.bodySmall)
                    }
                    Text(ui.latest?.let { "%02d".format(it) } ?: "—",color=ink,fontSize=42.sp,fontWeight=FontWeight.Medium)
                }
                Spacer(Modifier.height(14.dp))
            }
            NumberBoard(result,ui.count)
            if(ui.ended) {
                Spacer(Modifier.height(18.dp))
                VerdictPanel(result,verdicts,vm::finishReplay)
            } else {
                Spacer(Modifier.height(12.dp))
                Text("球沿透明导管滚入轨道后揭晓",color=faint,style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NumberBoard(result: DrawResult,count: Int) {
    val c=LocalLotteryColors.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val all=result.zone1+result.zone2
        val size=minOf(40.dp,(maxWidth-((all.size-1)*6+12).dp)/all.size)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            all.forEachIndexed { i,n ->
                if(i>0)Spacer(Modifier.width(if(i==result.zone1.size)18.dp else 6.dp))
                if(i<count) Ball(n,if(i<result.zone1.size)c.zone1(result.game) else c.zone2(result.game),
                    if(i<result.zone1.size)c.zone1Hi(result.game) else c.zone2Hi(result.game),size=size)
                else Box(Modifier.size(size).border(1.dp,LotteryPalette.StageFaint.copy(alpha=.28f),RoundedCornerShape(50)),contentAlignment=Alignment.Center) {
                    Text("·",color=LotteryPalette.StageFaint,fontSize=18.sp)
                }
            }
        }
    }
}

@Composable
private fun VerdictPanel(result: DrawResult,verdicts: List<Pair<Ticket,TicketVerdict>>,onClose: ()->Unit) {
    val ink=LotteryPalette.StageInk; val faint=LotteryPalette.StageFaint
    Column(Modifier.fillMaxWidth().heightIn(max=144.dp).background(Color.White.copy(alpha=.055f),RoundedCornerShape(20.dp)).padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("开奖结束",color=ink,style=MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        if(verdicts.isEmpty()) Text("本期没有收藏的票，留住下一次期待。",color=faint,style=MaterialTheme.typography.bodyMedium)
        verdicts.forEach { (ticket,v) ->
            Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${ticket.game.label} · 第${ticket.take}批",Modifier.weight(1f),color=faint,style=MaterialTheme.typography.bodyMedium)
                Text(if(v.won)"喜中 ${v.best?.label}" else "这次擦肩而过",color=if(v.won)Color(0xFFE7C17E) else ink,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium)
            }
        }
        TextButton(onClick=onClose,modifier=Modifier.align(Alignment.End)) { Text("收好这一刻",color=ink) }
    }
}
