package com.example.netfilter

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Widget d'accueil : un bouton qui bascule le filtrage.
 * Démarrage : on ouvre l'app (l'Activity lance le service et gère l'autorisation VPN).
 * Arrêt : on commande directement le service.
 */
class FilterWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_TOGGLE = "com.example.netfilter.WIDGET_TOGGLE"

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, FilterWidgetProvider::class.java))
            for (id in ids) updateWidget(context, mgr, id)
        }

        private fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_filter)
            val on = FilterVpnService.isRunning
            views.setTextViewText(R.id.widget_status, if (on) "Filtrage ACTIF" else "Filtrage inactif")
            views.setInt(
                R.id.widget_button, "setBackgroundResource",
                if (on) R.drawable.widget_bg_on else R.drawable.widget_bg_off
            )
            val pending = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, FilterWidgetProvider::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_button, pending)
            mgr.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return
        if (FilterVpnService.isRunning) {
            context.startService(
                Intent(context, FilterVpnService::class.java).setAction(FilterVpnService.ACTION_STOP)
            )
        } else {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_AUTO_START, true)
            )
        }
    }
}
