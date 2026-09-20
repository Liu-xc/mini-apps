package com.leo.wardrobe.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.leo.wardrobe.MainActivity
import com.leo.wardrobe.R
import com.leo.wardrobe.WardrobeApp
import java.util.concurrent.TimeUnit

/**
 * 「好久没穿」本地提醒（it-018 阶段C，ADR-019）：WorkManager 每日一次，无服务端。
 * 单品粒度——穿过 ≥2 次且超过 N 天没穿；每日至多 1 条，上次提醒过的单品先让位。
 * 演示模式与开关关闭时空转；通知点击经 EXTRA 进 W5 衣物详情。
 */
object ReminderScheduler {
    const val CHANNEL_ID = "wardrobe_reminder"
    private const val WORK_NAME = "wardrobe_reminder_daily"

    /** 候选规则（纯数据，便于单测口径对照）：穿过 ≥2 次且距最后穿着 ≥N 天 */
    const val MIN_WEAR_COUNT = 2
    const val DAY_MS = 86_400_000L

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "好久没穿提醒", NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "常穿的单品太久没上身时轻推一条" }
        nm.createNotificationChannel(channel)
    }

    fun schedule(context: Context) {
        ensureChannel(context)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MemoryReminderWorker>(1, TimeUnit.DAYS).build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun sync(context: Context, enabled: Boolean) {
        if (enabled) schedule(context) else cancel(context)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** 单品粒度候选：穿过的次数与最后穿着时间由打卡记录聚合 */
    data class ItemWearStat(val itemId: String, val personId: String, val wearCount: Int, val lastWornAt: Long)

    fun candidates(
        stats: List<ItemWearStat>,
        names: Map<String, String>,
        days: Int,
        now: Long,
    ): List<ItemWearStat> =
        stats.filter { s ->
            s.wearCount >= MIN_WEAR_COUNT &&
                s.lastWornAt > 0 &&
                now - s.lastWornAt >= days * DAY_MS &&
                names.containsKey(s.itemId)
        }
}

class MemoryReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? WardrobeApp ?: return Result.success()
        val container = app.container
        if (container.isDemo) return Result.success()

        val prefs = container.recapPrefs.snapshot()
        if (!prefs.enabled || !ReminderScheduler.canNotify(applicationContext)) return Result.success()

        val data = container.repository.data.value
        val now = System.currentTimeMillis()
        // 单品穿着聚合（it-018 口径）：同一单品的穿着次数/最后穿着 = 其所在全部穿搭打卡的合并
        val outfitById = data.outfits.associateBy { it.id }
        val stats: List<ReminderScheduler.ItemWearStat> = data.items
            .flatMap { item ->
                data.wearLogs.mapNotNull { log ->
                    val outfit = outfitById[log.outfitId] ?: return@mapNotNull null
                    if (item.id in outfit.itemIds) Triple(item.id, item.personId, log.at) else null
                }
            }
            .groupBy { (itemId, personId, _) -> itemId to personId }
            .map { (key, triples) ->
                ReminderScheduler.ItemWearStat(
                    itemId = key.first,
                    personId = key.second,
                    wearCount = triples.size,
                    lastWornAt = triples.maxOf { it.third },
                )
            }
        val names = data.items.associate { it.id to it.name }
        val personNames = data.persons.associate { it.id to it.name }
        val candidates = ReminderScheduler.candidates(stats, names, prefs.days, now)
        val pool = candidates.filterNot { it.itemId == prefs.lastNotifiedItemId }.ifEmpty { candidates }
        val pick = pool.randomOrNull() ?: return Result.success()
        return notifyPick(pick, names, personNames, now, container)
    }

    private suspend fun notifyPick(
        pick: ReminderScheduler.ItemWearStat,
        names: Map<String, String>,
        personNames: Map<String, String>,
        now: Long,
        container: com.leo.wardrobe.di.AppContainer,
    ): Result {

        val itemName = names[pick.itemId].orEmpty()
        val personName = personNames[pick.personId]
        val days = ((now - pick.lastWornAt) / ReminderScheduler.DAY_MS).coerceAtLeast(0)
        ReminderScheduler.ensureChannel(applicationContext)

        val open = Intent(applicationContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_ITEM_ID, pick.itemId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentPi = PendingIntent.getActivity(
            applicationContext,
            pick.itemId.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (personName.isNullOrBlank()) "「$itemName」好久没穿了"
        else "${personName}的「$itemName」好久没穿了"
        val builder = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText("已经 $days 天没上身，想它吗？")
            .setAutoCancel(true)
            .setContentIntent(contentPi)

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(pick.itemId.hashCode(), builder.build())
        container.recapPrefs.setLastNotified(pick.itemId)
        return Result.success()
    }
}
