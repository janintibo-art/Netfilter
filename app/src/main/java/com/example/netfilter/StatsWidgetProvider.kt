package com.example.netfilter

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Widget d'accueil en lecture seule : affiche le nombre de requêtes bloquées aujourd'hui.
 * Un appui ouvre les statistiques détaillées. Se rafraîchit périodiquement et à chaque
 * changement d'état du filtrage.
 */
class StatsWidgetProvider : AppWidgetProvider() {

    companion object {
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, StatsWidgetProvider::class.java))
            for (id in ids) updateWidget(context, mgr, id)
        }

        private fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            // Si le service tourne, les compteurs en mémoire sont à jour ; sinon on lit le disque.
            if (!FilterVpnService.isRunning) StatsStore.load(context)

            val views = RemoteViews(context.packageName, R.layout.widget_stats)
            views.setTextViewText(R.id.widget_stats_count, StatsStore.todayBlocked().toString())

            val pending = PendingIntent.getActivity(
                context, 0, Intent(context, StatsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_stats_root, pending)
            mgr.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
    }
}
