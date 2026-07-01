package com.example.netfilter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer

/**
 * Cœur de l'application : VPN local qui filtre le DNS (IPv4 et IPv6). Voir README.
 *
 * Fonctions : liste blanche prioritaire, pause, rechargement à chaud, redémarrage du
 * tunnel (apps exclues), comptage, résolveur amont configurable, cache DNS, réponses
 * adaptées au type (A -> 0.0.0.0, AAAA -> ::).
 */
class FilterVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.example.netfilter.START"
        const val ACTION_STOP = "com.example.netfilter.STOP"
        const val ACTION_PAUSE = "com.example.netfilter.PAUSE"
        const val ACTION_RELOAD = "com.example.netfilter.RELOAD"
        const val ACTION_RESTART = "com.example.netfilter.RESTART"
        const val ACTION_STATE_CHANGED = "com.example.netfilter.STATE"
        const val EXTRA_PAUSE_MINUTES = "pause_minutes"

        private const val VPN_ADDRESS4 = "10.0.0.2"
        private const val VPN_DNS4 = "10.0.0.1"
        private const val VPN_ADDRESS6 = "fd00::2"
        private const val VPN_DNS6 = "fd00::1"
        private const val CHANNEL_ID = "netfilter_vpn"
        private const val NOTIF_ID = 1

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var pausedUntil = 0L
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var blockList: Set<String> = emptySet()
    @Volatile private var whiteList: Set<String> = emptySet()
    @Volatile private var upstreamDns: String = "8.8.8.8"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> pause(intent.getIntExtra(EXTRA_PAUSE_MINUTES, 5))
            ACTION_RELOAD -> reloadLists()
            ACTION_RESTART -> reestablish()
            else -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        StatsStore.load(this)
        reloadLists()
        vpnInterface = buildTunnel().establish() ?: return
        isRunning = true
        pausedUntil = 0L
        startForeground(NOTIF_ID, buildNotification())
        broadcastState()
        worker = Thread({ runLoop() }, "netfilter-worker").apply { start() }
    }

    /** Construit le tunnel (IPv4 + IPv6), en excluant les apps choisies. */
    private fun buildTunnel(): Builder {
        val builder = Builder()
            .setSession("NetFilter")
            .addAddress(VPN_ADDRESS4, 24)
            .addAddress(VPN_ADDRESS6, 64)
            .addDnsServer(VPN_DNS4)
            .addDnsServer(VPN_DNS6)
            .addRoute(VPN_DNS4, 32)  // on ne capte que le DNS
            .addRoute(VPN_DNS6, 128)
        for (pkg in BlockListRepository.getExcludedApps(this)) {
            try {
                builder.addDisallowedApplication(pkg)
            } catch (_: Exception) {
            }
        }
        return builder
    }

    private fun reestablish() {
        if (!isRunning) return
        worker?.interrupt()
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        val newInterface = buildTunnel().establish() ?: return
        vpnInterface = newInterface
        worker = Thread({ runLoop() }, "netfilter-worker").apply { start() }
    }

    private fun reloadLists() {
        blockList = BlockListRepository.load(this)
        whiteList = BlockListRepository.getWhitelist(this)
        upstreamDns = BlockListRepository.getUpstreamDns(this)
    }

    private fun pause(minutes: Int) {
        pausedUntil = System.currentTimeMillis() + minutes * 60_000L
        updateNotification()
        broadcastState()
        mainHandler.postDelayed({ updateNotification(); broadcastState() }, minutes * 60_000L)
    }

    private fun stopVpn() {
        isRunning = false
        pausedUntil = 0L
        StatsStore.save(this)
        DnsCache.clear()
        worker?.interrupt()
        worker = null
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        broadcastState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    // --- Boucle de lecture des paquets ---

    private fun runLoop() {
        val tunFd = vpnInterface?.fileDescriptor ?: return
        val input = FileInputStream(tunFd)
        val output = FileOutputStream(tunFd)
        val buffer = ByteArray(32767)

        while (isRunning) {
            val length = try {
                input.read(buffer)
            } catch (e: Exception) {
                break
            }
            if (length <= 0) continue
            try {
                handlePacket(ByteBuffer.wrap(buffer, 0, length), output)
            } catch (_: Exception) {
            }
        }
    }

    private fun handlePacket(packet: ByteBuffer, output: FileOutputStream) {
        val version = (packet.get(0).toInt() shr 4) and 0x0F
        val ipHeaderLen: Int
        val protocol: Int
        when (version) {
            4 -> {
                ipHeaderLen = (packet.get(0).toInt() and 0x0F) * 4
                protocol = packet.get(9).toInt() and 0xFF
            }
            6 -> {
                ipHeaderLen = 40
                protocol = packet.get(6).toInt() and 0xFF // en-tête suivant
            }
            else -> return
        }
        if (protocol != 17) return // UDP seulement (DNS sur TCP non géré)

        val udpStart = ipHeaderLen
        if (packet.limit() < udpStart + 8) return
        val destPort = word(packet, udpStart + 2)
        if (destPort != 53) return

        val dnsStart = udpStart + 8
        val domain = DnsPacket.readQueryName(packet, dnsStart) ?: return
        val qtype = DnsPacket.readQueryType(packet, dnsStart)

        if (isBlocked(domain)) {
            StatsStore.record(domain)
            val reply = DnsPacket.buildBlockedResponse(packet, version, ipHeaderLen, udpStart, dnsStart, qtype)
            if (reply != null) output.write(reply.array(), 0, reply.limit())
        } else {
            forwardDns(packet, version, ipHeaderLen, udpStart, dnsStart, domain, qtype, output)
        }
    }

    private fun isBlocked(domain: String): Boolean {
        if (System.currentTimeMillis() < pausedUntil) return false
        if (matches(domain, whiteList)) return false
        return matches(domain, blockList)
    }

    private fun matches(domain: String, set: Set<String>): Boolean {
        if (set.isEmpty()) return false
        val d = domain.lowercase()
        if (set.contains(d)) return true
        var idx = d.indexOf('.')
        while (idx != -1) {
            if (set.contains(d.substring(idx + 1))) return true
            idx = d.indexOf('.', idx + 1)
        }
        return false
    }

    private fun forwardDns(
        packet: ByteBuffer,
        ipVersion: Int,
        ipHeaderLen: Int,
        udpStart: Int,
        dnsStart: Int,
        domain: String,
        qtype: Int,
        output: FileOutputStream
    ) {
        val key = "$qtype:${domain.lowercase()}"
        val queryId = word(packet, dnsStart)

        var payload = DnsCache.get(key)
        if (payload == null) {
            val upstream = queryUpstream(packet, dnsStart) ?: return
            DnsCache.put(key, upstream, DnsPacket.responseTtlSeconds(upstream))
            payload = upstream
        }

        val out = payload.copyOf()
        // ID de transaction de la requête en cours
        out[0] = ((queryId shr 8) and 0xFF).toByte()
        out[1] = (queryId and 0xFF).toByte()
        // recopie la question de la requête (écho exact, casse comprise)
        val qLen = DnsPacket.questionByteLength(packet, dnsStart)
        if (12 + qLen <= out.size) {
            for (j in 0 until qLen) out[12 + j] = packet.get(dnsStart + 12 + j)
        }

        val reply = DnsPacket.buildForwardedResponse(packet, ipVersion, ipHeaderLen, udpStart, dnsStart, out)
        if (reply != null) output.write(reply.array(), 0, reply.limit())
    }

    /** Envoie la requête au résolveur amont et renvoie la charge DNS (ou null si échec/délai). */
    private fun queryUpstream(packet: ByteBuffer, dnsStart: Int): ByteArray? {
        return try {
            val socket = DatagramSocket()
            protect(socket) // sinon la requête repasserait par notre propre VPN
            socket.soTimeout = 5000
            val len = packet.limit() - dnsStart
            val query = ByteArray(len)
            for (i in 0 until len) query[i] = packet.get(dnsStart + i)
            socket.send(DatagramPacket(query, len, InetSocketAddress(upstreamDns, 53)))
            val buf = ByteArray(1500)
            val resp = DatagramPacket(buf, buf.size)
            socket.receive(resp)
            socket.close()
            buf.copyOf(resp.length)
        } catch (e: Exception) {
            null // pas de réponse : l'app réessaiera
        }
    }

    private fun word(b: ByteBuffer, i: Int) =
        ((b.get(i).toInt() and 0xFF) shl 8) or (b.get(i + 1).toInt() and 0xFF)

    // --- Notification (au premier plan, avec actions) ---

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Filtrage réseau", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val pausePi = PendingIntent.getService(
            this, 1,
            Intent(this, FilterVpnService::class.java)
                .setAction(ACTION_PAUSE).putExtra(EXTRA_PAUSE_MINUTES, 5),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 2,
            Intent(this, FilterVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val paused = System.currentTimeMillis() < pausedUntil
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("NetFilter")
            .setContentText(if (paused) "En pause" else "Filtrage en cours")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Pause 5 min", pausePi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Arrêter", stopPi)
            .build()
    }

    private fun updateNotification() {
        if (!isRunning) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun broadcastState() {
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
        FilterWidgetProvider.refreshAll(this)
        FilterTileService.refresh(this)
    }
}
