package com.example.upswifimonitor

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var elapsed: TextView
    private lateinit var lastResult: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE) }

    private val refresh = object : Runnable {
        override fun run() {
            updateScreen()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 36)
        }
        val title = TextView(this).apply {
            text = "UPS WiFi Monitor"
            textSize = 26f
        }
        status = TextView(this).apply { textSize = 18f; setPadding(0, 28, 0, 12) }
        elapsed = TextView(this).apply { textSize = 32f; setPadding(0, 8, 0, 24) }
        lastResult = TextView(this).apply { textSize = 16f; setPadding(0, 8, 0, 24) }

        val start = Button(this).apply {
            text = "Mulai pantau"
            setOnClickListener {
                val intent = Intent(this@MainActivity, MonitorService::class.java)
                    .setAction(MonitorService.ACTION_START)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
                updateScreen()
            }
        }
        val stop = Button(this).apply {
            text = "Hentikan pemantauan"
            setOnClickListener {
                stopService(Intent(this@MainActivity, MonitorService::class.java))
                updateScreen()
            }
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(elapsed)
        layout.addView(lastResult)
        layout.addView(start)
        layout.addView(stop)
        setContentView(layout)
        updateScreen()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun updateScreen() {
        val active = prefs.getBoolean("monitor_active", false)
        val start = prefs.getLong("session_start", 0L)
        status.text = if (active) "Status: pemantauan aktif" else "Status: tidak memantau"
        elapsed.text = if (active && start > 0L) formatDuration((System.currentTimeMillis() - start).coerceAtLeast(0L))
                       else "00:00:00"

        val last = prefs.getLong("last_duration", -1L)
        val endedAt = prefs.getLong("last_ended_at", 0L)
        lastResult.text = if (last >= 0L) {
            val date = if (endedAt > 0L) SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(endedAt)) else "-"
            "Hasil terakhir: ${formatDuration(last)}\nWiFi terputus: $date"
        } else {
            "Belum ada hasil pengujian."
        }
    }

    private fun formatDuration(ms: Long): String {
        val total = ms / 1000
        return String.format(Locale.getDefault(), "%02d:%02d:%02d",
            total / 3600, (total % 3600) / 60, total % 60)
    }
}
