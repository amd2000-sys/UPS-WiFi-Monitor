package com.example.upswifimonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class MonitorService : Service() {
    companion object {
        const val PREFS = "ups_wifi_monitor"
        const val ACTION_START = "com.example.upswifimonitor.START"
        private const val CHANNEL_ID = "ups_monitor_channel"
        private const val NOTIFICATION_ID = 41
    }

    private lateinit var connectivity: ConnectivityManager
    private val handler = Handler(Looper.getMainLooper())
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var tone: ToneGenerator? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Menyiapkan pemantauan WiFi"))
        connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        registerNetworkMonitor()
        handler.postDelayed({ evaluateWifi() }, 500)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("monitor_active", true).apply()
        evaluateWifi()
        return START_STICKY
    }

    private fun registerNetworkMonitor() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handler.post { beginSessionIfNeeded() }
            }
            override fun onLost(network: Network) {
                handler.postDelayed({ evaluateWifi() }, 700)
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                handler.post { evaluateWifi() }
            }
        }

        try {
            connectivity.registerNetworkCallback(request, callback!!)
        } catch (_: Exception) {
            // The initial status check still works; Android may restrict callbacks on some devices.
        }
    }

    private fun isWifiConnected(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val caps = connectivity.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun evaluateWifi() {
        if (isWifiConnected()) beginSessionIfNeeded() else endSessionIfNeeded()
        val message = if (isWifiConnected()) "WiFi terhubung — durasi sedang dihitung"
                      else "WiFi terputus — menunggu tersambung"
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun beginSessionIfNeeded() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getLong("session_start", 0L) == 0L) {
            prefs.edit().putLong("session_start", System.currentTimeMillis()).apply()
        }
        prefs.edit().putBoolean("monitor_active", true).apply()
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification("WiFi terhubung — durasi sedang dihitung"))
    }

    private fun endSessionIfNeeded() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val start = prefs.getLong("session_start", 0L)
        if (start > 0L) {
            val duration = (System.currentTimeMillis() - start).coerceAtLeast(0L)
            prefs.edit()
                .putLong("last_duration", duration)
                .putLong("last_ended_at", System.currentTimeMillis())
                .putLong("session_start", 0L)
                .apply()
            playAlarmFor30Seconds()
        }
    }

    private fun playAlarmFor30Seconds() {
        try {
            tone?.release()
            tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 30_000)
            handler.postDelayed({
                try { tone?.stopTone(); tone?.release() } catch (_: Exception) {}
                tone = null
            }, 30_000)
        } catch (_: Exception) {
            // Sound may be blocked by device volume, DND, or manufacturer restrictions.
        }
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder
            .setContentTitle("UPS WiFi Monitor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Pemantauan UPS WiFi",
                NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try { callback?.let { connectivity.unregisterNetworkCallback(it) } } catch (_: Exception) {}
        try { tone?.stopTone(); tone?.release() } catch (_: Exception) {}
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("monitor_active", false).apply()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
