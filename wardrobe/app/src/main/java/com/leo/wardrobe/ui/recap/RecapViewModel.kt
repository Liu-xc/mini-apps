package com.leo.wardrobe.ui.recap

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leo.wardrobe.WardrobeApp
import com.leo.wardrobe.data.prefs.RecapReminderPrefs
import com.leo.wardrobe.domain.usecase.WardrobeRecapRange
import com.leo.wardrobe.domain.usecase.wardrobeRecap
import com.leo.wardrobe.platform.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * 衣橱回顾域 ViewModel（it-021 自全局 AppViewModel 拆出）：
 * 回忆提醒设置、年度/累计长图生成与导出。角色与数据状态仍以全局 AppViewModel 为 SSOT 出口
 * （屏幕同时持有两个 VM）；本 VM 只持有回顾域自己的容器依赖。
 */
class RecapViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as WardrobeApp).container

    /** 「好久没穿」提醒设置（DataStore，跨启动保留） */
    val recapPrefs: StateFlow<RecapReminderPrefs> =
        container.recapPrefs.flow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecapReminderPrefs())

    val isDemo: Boolean get() = container.isDemo

    fun setReminder(enabled: Boolean, days: Int) {
        viewModelScope.launch {
            container.recapPrefs.set(enabled, days)
            if (!container.isDemo) {
                ReminderScheduler.sync(getApplication(), enabled)
            }
        }
    }

    /** 应用启动时对齐提醒任务与开关（兜底重启/升级；演示模式恒取消）；失败仅记日志 */
    fun syncReminderSchedule() {
        viewModelScope.launch {
            runCatching {
                val prefs = container.recapPrefs.snapshot()
                ReminderScheduler.sync(
                    getApplication(),
                    prefs.enabled && !container.isDemo,
                )
            }.onFailure { android.util.Log.e("Wardrobe", "syncReminderSchedule failed", it) }
        }
    }

    /** 生成年终衣橱长图（按 personId），写 export 目录返回文件；无角色/空打卡数据返回 null */
    fun generateRecap(personId: String?, range: WardrobeRecapRange, now: Long, onReady: (File?) -> Unit) {
        viewModelScope.launch {
            if (personId == null) {
                onReady(null); return@launch
            }
            runCatching {
                val stats = container.repository.data.value.wardrobeRecap(personId, range, now)
                if (!stats.hasWearData) {
                    onReady(null); return@launch
                }
                val label = when (val r = range) {
                    is WardrobeRecapRange.Year -> "${r.year}"
                    WardrobeRecapRange.All -> "衣橱总账"
                }
                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                    .format(java.util.Date(now))
                val renderer = WardrobeRecapLongImage(
                    stats, label, dateStr, photoFileOf = { name -> imageFileOf(name) },
                )
                onReady(renderer.renderTo(container.imageEditStore.exportDir()))
            }.onFailure {
                android.util.Log.e("Wardrobe", "generateRecap failed", it)
                onReady(null)
            }
        }
    }

    fun imageFileOf(name: String): File? = container.imageStore.file(name)?.takeIf { it.exists() }

    /** 存相册（Pictures/Wardrobe，复用导出门面） */
    fun saveRecapImage(file: File): Boolean = container.share.saveToGallery(file)

    /** 分享（复用导出门面） */
    fun shareRecapImage(file: File) = container.share.shareImage(file)
}
