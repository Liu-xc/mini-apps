package com.leo.lottery.engineprobe

import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Standalone compatibility tool; never an entry in the production lottery app. */
class LauncherActivity : Activity() {
    private lateinit var status: TextView
    private var launches = 0
    private var returns = 0
    private var launching = false
    private val handler = Handler(Looper.getMainLooper())
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val event = intent.getStringExtra("event").orEmpty()
            status.text = "主进程 ${Process.myPid()} · 进入 $launches · 返回 $returns\n$event"
            android.util.Log.i("LotteryProbe", "HOST_EVENT $event")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launches = savedInstanceState?.getInt("launches") ?: 0
        returns = savedInstanceState?.getInt("returns") ?: 0
        val spacing = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(spacing, spacing * 3, spacing, spacing)
            setBackgroundColor(Color.rgb(247, 246, 242))
        }
        layout.addView(TextView(this).apply {
            text = "Android 引擎兼容验证"
            textSize = 24f
            setTextColor(Color.rgb(35, 38, 36))
        })
        layout.addView(TextView(this).apply {
            text = "测试模型仅验证 GLB / PBR / Godot / Jolt。\n此工具不是新版摇奖机或皮肤的交付。"
            textSize = 16f
            setPadding(0, spacing, 0, spacing)
        })
        status = TextView(this).apply { textSize = 15f; text = "主进程 ${Process.myPid()} · 尚未进入" }
        layout.addView(status)
        layout.addView(Button(this).apply {
            text = "进入模型验证场景"
            setOnClickListener { enterScene() }
        })
        setContentView(layout)
        val filter = IntentFilter(EVENT)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
        if (savedInstanceState == null && intent.getBooleanExtra("probe_launch", false)) enterScene()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("probe_launch", false)) enterScene()
    }

    private fun enterScene() {
        if (launching) return
        launching = true
        launchAfterRelease(0)
    }

    private fun launchAfterRelease(attempt: Int) {
        if (isFinishing || isDestroyed) return
        val busy = getSystemService(ActivityManager::class.java).runningAppProcesses
            ?.any { it.processName == "$packageName:stage" } == true
        if (busy) {
            if (attempt >= 100) {
                launching = false
                status.text = "上一场景进程未释放；本次未启动"
                return
            }
            handler.postDelayed({ launchAfterRelease(attempt + 1) }, 50)
            return
        }
        launches++
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(this, SceneActivity::class.java)
            .putExtra("probe_gles", intent.getBooleanExtra("probe_gles", false))
            .putExtra("probe_auto_close", intent.getBooleanExtra("probe_auto_close", false)), 1)
    }

    @Deprecated("Probe uses a minimal native host")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            launching = false
            returns++
            status.text = "主进程 ${Process.myPid()} · 进入 $launches · 返回 $returns\n${data?.getStringExtra("event") ?: "场景结束（结果码 $resultCode）"}"
            android.util.Log.i("LotteryProbe", "HOST_RETURN launches=$launches returns=$returns pid=${Process.myPid()} code=$resultCode")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("launches", launches)
        outState.putInt("returns", returns)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    companion object { const val EVENT = "com.leo.lottery.engineprobe.EVENT" }
}
