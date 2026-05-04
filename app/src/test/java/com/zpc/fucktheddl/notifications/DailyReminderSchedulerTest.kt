package com.zpc.fucktheddl.notifications

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyReminderSchedulerTest {
    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun nextTriggerUsesTodayWhenReminderTimeIsStillAhead() {
        val now = ZonedDateTime.parse("2026-05-02T08:00:00+08:00[Asia/Shanghai]")
        val trigger = DailyReminderScheduler.nextTriggerMillis(now, 8, 30, zoneId)

        assertEquals(
            "2026-05-02T00:30:00Z",
            Instant.ofEpochMilli(trigger).toString(),
        )
    }

    @Test
    fun nextTriggerMovesToTomorrowWhenReminderTimeAlreadyPassed() {
        val now = ZonedDateTime.parse("2026-05-02T09:00:00+08:00[Asia/Shanghai]")
        val trigger = DailyReminderScheduler.nextTriggerMillis(now, 8, 30, zoneId)

        assertEquals(
            "2026-05-03T00:30:00Z",
            Instant.ofEpochMilli(trigger).toString(),
        )
    }

    @Test
    fun alarmModeUsesExactWhenPermissionIsAvailable() {
        assertEquals(
            DailyReminderScheduler.AlarmMode.ExactWhileIdle,
            DailyReminderScheduler.alarmMode(canScheduleExactAlarms = true),
        )
    }

    @Test
    fun alarmModeFallsBackWhenExactPermissionIsMissing() {
        assertEquals(
            DailyReminderScheduler.AlarmMode.AllowWhileIdle,
            DailyReminderScheduler.alarmMode(canScheduleExactAlarms = false),
        )
    }
}
