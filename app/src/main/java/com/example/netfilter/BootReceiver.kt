package com.example.netfilter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

/**
 * Redémarre le filtrage au démarrage du téléphone, si l'option est activée ET si
 * l'autorisation VPN a déjà été accordée. BOOT_COMPLETED fait partie des cas où
 * démarrer un service au premier plan depuis l'arrière-plan est autorisé.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        if (!BlockListRepository.isStartOnBoot(context)) return
        if (VpnService.prepare(context) != null) return // autorisation VPN pas encore donnée

        context.startForegroundService(
            Intent(context, FilterVpnService::class.java).setAction(FilterVpnService.ACTION_START)
        )
    }
}
