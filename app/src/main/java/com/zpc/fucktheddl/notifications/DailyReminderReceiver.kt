package com.zpc.fucktheddl.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.Room
import com.zpc.fucktheddl.commitments.room.CommitmentDatabase
import com.zpc.fucktheddl.commitments.room.LocalOwnerUserId
import com.zpc.fucktheddl.commitments.room.MIGRATION_1_2
import com.zpc.fucktheddl.commitments.room.RoomCommitmentRepository

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                val appContext = context.applicationContext
                val settings = DailyReminderSettingsStore(appContext).load()
                if (!settings.enabled) return@Thread
                val database = Room.databaseBuilder(
                    appContext,
                    CommitmentDatabase::class.java,
                    "fucktheddl_commitments.db",
                ).addMigrations(MIGRATION_1_2).build()
                try {
                    val commitments = RoomCommitmentRepository(database).listCommitments(LocalOwnerUserId)
                    val briefing = DailyBriefingBuilder().build(commitments)
                    DailyReminderNotifier(appContext).show(briefing)
                    DailyReminderScheduler(appContext).apply(settings)
                } finally {
                    database.close()
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
