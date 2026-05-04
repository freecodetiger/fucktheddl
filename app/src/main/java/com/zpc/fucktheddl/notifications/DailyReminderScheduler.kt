package com.zpc.fucktheddl.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyReminderScheduler(
    context: Context,
    private val zoneId: ZoneId = ZoneId.of("Asia/Shanghai"),
) {
    enum class AlarmMode {
        ExactWhileIdle,
        AllowWhileIdle,
    }

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
        schedule(triggerAt, createPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT))
    }

    fun cancel() {
        alarmManager.cancel(findPendingIntent() ?: return)
    }

    fun canScheduleExactAlarms(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    fun currentAlarmMode(): AlarmMode {
        return alarmMode(canScheduleExactAlarms())
    }

    private fun schedule(triggerAt: Long, pendingIntent: PendingIntent) {
        if (currentAlarmMode() == AlarmMode.ExactWhileIdle) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            }.onSuccess {
                return
            }
        }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent,
        )
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

        fun alarmMode(canScheduleExactAlarms: Boolean): AlarmMode {
            return if (canScheduleExactAlarms) AlarmMode.ExactWhileIdle else AlarmMode.AllowWhileIdle
        }

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
