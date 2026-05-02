package com.zpc.fucktheddl.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyReminderScheduler(
    context: Context,
    private val zoneId: ZoneId = ZoneId.of("Asia/Shanghai"),
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun apply(settings: DailyReminderSettings) {
        if (!settings.enabled) {
            cancel()
            return
        }
        val triggerAt = nextTriggerMillis(
            now = ZonedDateTime.now(zoneId),
            hour = settings.hour,
            minute = settings.minute,
            zoneId = zoneId,
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            createPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT),
        )
    }

    fun cancel() {
        alarmManager.cancel(findPendingIntent() ?: return)
    }

    private fun createPendingIntent(extraFlags: Int): PendingIntent {
        val intent = Intent(appContext, DailyReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            appContext,
            RequestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or extraFlags,
        )
    }

    private fun findPendingIntent(): PendingIntent? {
        val intent = Intent(appContext, DailyReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            appContext,
            RequestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
    }

    companion object {
        private const val RequestCode = 7020

        fun nextTriggerMillis(
            now: ZonedDateTime,
            hour: Int,
            minute: Int,
            zoneId: ZoneId = ZoneId.of("Asia/Shanghai"),
        ): Long {
            val targetTime = now
                .withZoneSameInstant(zoneId)
                .withHour(hour.coerceIn(0, 23))
                .withMinute(minute.coerceIn(0, 59))
                .withSecond(0)
                .withNano(0)
            val next = if (targetTime.isAfter(now.withZoneSameInstant(zoneId))) {
                targetTime
            } else {
                targetTime.plusDays(1)
            }
            return next.toInstant().toEpochMilli()
        }
    }
}
