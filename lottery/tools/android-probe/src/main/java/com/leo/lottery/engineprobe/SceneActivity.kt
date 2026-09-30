package com.leo.lottery.engineprobe

import android.app.Activity
import android.content.Intent
import android.os.Process
import android.util.Log
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotActivity
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot

/** One engine in its own process: engine teardown cannot terminate the native host. */
class SceneActivity : GodotActivity() {
    private var lastEvent = "模型尚未就绪"
    private var plugin: ProbePlugin? = null

    override fun getCommandLine(): MutableList<String> =
        super.getCommandLine().toMutableList().apply {
            addAll(listOf("--main-pack", "res://probe.pck"))
            if (intent.getBooleanExtra("probe_gles", false))
                addAll(listOf("--rendering-method", "gl_compatibility", "--rendering-driver", "opengl3"))
            if (intent.getBooleanExtra("probe_auto_close", false)) addAll(listOf("--", "--auto-close"))
        }

    override fun getHostPlugins(godot: Godot): Set<GodotPlugin> {
        val bridge = plugin ?: ProbePlugin(godot).also { plugin = it }
        return setOf(bridge)
    }

    override fun onGodotForceQuit(instance: Godot) {
        runOnUiThread {
            Log.i("LotteryProbe", "STAGE_EXIT pid=${Process.myPid()}")
            setResult(Activity.RESULT_OK, Intent().putExtra("event", lastEvent))
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Dedicated :stage only. Do not reuse a terminated native singleton on the next visit.
        Log.i("LotteryProbe", "STAGE_PROCESS_RELEASE pid=${Process.myPid()}")
        Process.killProcess(Process.myPid())
    }

    inner class ProbePlugin(godot: Godot) : GodotPlugin(godot) {
        override fun getPluginName() = "LotteryProbe"

        @UsedByGodot
        fun report_event(payload: String) {
            Log.i("LotteryProbe", "STAGE_EVENT pid=${Process.myPid()} $payload")
            runOnUiThread {
                lastEvent = payload
                sendBroadcast(Intent(LauncherActivity.EVENT).setPackage(packageName).putExtra("event", payload))
            }
        }
    }
}
