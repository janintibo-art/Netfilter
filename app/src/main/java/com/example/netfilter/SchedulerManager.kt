package com.example.netfilter

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Programme des alarmes quotidiennes (approximatives, sans permission spéciale ni
 * consommation notable) qui, à l'heure dite, (dés)activent une catégorie puis
 * rechargent le filtrage s'il tourne.
 */
object SchedulerManager {

    const val EXTRA_THEME = "theme"
    const val EXTRA_ENABLE = "enable"

    fun scheduleAll(context: Context) {
        for (rule in ScheduleStore.getRules(context)) scheduleRule(context, rule)
    }

    fun scheduleRule(context: Context, rule: ScheduleRule) {
        alarmManager(context).setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextTrigger(rule.hour, rule.minute),
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context, rule)
        )
    }

    fun cancelRule(context: Context, rule: ScheduleRule) {
        alarmManager(context).cancel(pendingIntent(context, rule))
    }

    private fun pendingIntent(context: Context, rule: ScheduleRule): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java)
            .putExtra(EXTRA_THEME, rule.themeId)
            .putExtra(EXTRA_ENABLE, rule.enable)
        return PendingIntent.getBroadcast(
            context, rule.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextTrigger(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now.timeInMillis) cal.add(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }

    private fun alarmManager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
}
