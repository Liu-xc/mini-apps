package com.leo.eats.platform

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
import com.leo.eats.EatsApp
import com.leo.eats.MainActivity
import com.leo.eats.R
import com.leo.eats.domain.model.statsOfAll
import com.leo.eats.domain.usecase.MemoryCandidateSelector
import java.util.concurrent.TimeUnit

/**
 * 「好久没去」本地提醒（it-007 阶段B，ADR-013）：WorkManager 每日一次，
 * 无服务端；候选与选取规则在 [MemoryCandidateSelector]（JVM 可测）。
 * 演示模式与开关关闭时任务空转；通知点击经 EXTRA 深链进 W5 详情。
 */
object ReminderScheduler {
    const val CHANNEL_ID = "memory_reminder"
    private const val WORK_NAME = "memory_reminder_daily"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "好久没去提醒", NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "老店太久没去时轻推一条" }
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

    /** 开关状态与任务对齐（App 启动时也会调一次，兜底重启/升级场景） */
    fun sync(context: Context, enabled: Boolean) {
        if (enabled) schedule(context) else cancel(context)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}

class MemoryReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? EatsApp ?: return Result.success()
        val container = app.container
        if (container.isDemo) return Result.success()

        val prefs = container.recapPrefs.snapshot()
        if (!prefs.enabled || !ReminderScheduler.canNotify(applicationContext)) return Result.success()

        val now = System.currentTimeMillis()
        val candidates = MemoryCandidateSelector.candidates(
            container.repository.data.value.statsOfAll(),
            prefs.days,
            now,
        )
        val pick = MemoryCandidateSelector.pick(candidates, prefs.lastNotifiedPlaceId)
            ?: return Result.success()

        val s = pick
        val days = ((now - (s.lastVisitAt ?: now)) / MemoryCandidateSelector.DAY_MS).coerceAtLeast(0)
        ReminderScheduler.ensureChannel(applicationContext)

        val open = Intent(applicationContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_PLACE_ID, s.place.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentPi = PendingIntent.getActivity(
            applicationContext,
            s.place.id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle("好久没去「${s.place.name}」了")
            .setContentText("距上次已经 $days 天，想它吗？")
            .setAutoCancel(true)
            .setContentIntent(contentPi)

        s.place.photos.firstOrNull()?.let { container.imageStore.file(it) }
            ?.takeIf { it.exists() }
            ?.let { file ->
                runCatching {
                    NotificationCompat.BigPictureStyle(builder)
                        .bigPicture(android.graphics.BitmapFactory.decodeFile(file.path))
                }
            }

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(s.place.id.hashCode(), builder.build())
        container.recapPrefs.setLastNotified(s.place.id)
        return Result.success()
    }
}
