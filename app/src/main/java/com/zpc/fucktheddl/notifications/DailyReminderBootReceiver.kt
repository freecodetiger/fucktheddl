package com.zpc.fucktheddl.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DailyReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = DailyReminderSettingsStore(context.applicationContext).load()
        if (settings.enabled) {
            DailyReminderScheduler(context.applicationContext).apply(settings)
        }
    }
}
