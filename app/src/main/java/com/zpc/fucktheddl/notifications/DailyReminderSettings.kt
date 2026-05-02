package com.zpc.fucktheddl.notifications

import android.content.Context

data class DailyReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = DefaultHour,
    val minute: Int = DefaultMinute,
) {
    val timeLabel: String
        get() = "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    companion object {
        const val DefaultHour = 8
        const val DefaultMinute = 30

        fun fromTimeLabel(enabled: Boolean, timeLabel: String): DailyReminderSettings {
            val parts = timeLabel.split(":")
            return DailyReminderSettings(
                enabled = enabled,
                hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: DefaultHour,
                minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: DefaultMinute,
            )
        }
    }
}

class DailyReminderSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("daily_reminder", Context.MODE_PRIVATE)

    fun load(): DailyReminderSettings {
        return DailyReminderSettings(
            enabled = preferences.getBoolean("enabled", false),
            hour = preferences.getInt("hour", DailyReminderSettings.DefaultHour).coerceIn(0, 23),
            minute = preferences.getInt("minute", DailyReminderSettings.DefaultMinute).coerceIn(0, 59),
        )
    }

    fun save(settings: DailyReminderSettings) {
        preferences.edit()
            .putBoolean("enabled", settings.enabled)
            .putInt("hour", settings.hour.coerceIn(0, 23))
            .putInt("minute", settings.minute.coerceIn(0, 59))
            .apply()
    }
}
