package com.example.netfilter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Déclenché par une alarme programmée : (dés)active la catégorie visée, puis demande
 * au service de recharger ses règles (sans effet si le filtrage est arrêté).
 */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val theme = intent.getStringExtra(SchedulerManager.EXTRA_THEME) ?: return
        val enable = intent.getBooleanExtra(SchedulerManager.EXTRA_ENABLE, false)
        BlockListRepository.setThemeEnabled(context, theme, enable)
        runCatching {
            context.startService(
                Intent(context, FilterVpnService::class.java)
                    .setAction(FilterVpnService.ACTION_RELOAD)
            )
        }
    }
}
