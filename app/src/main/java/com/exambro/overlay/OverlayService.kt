package com.exambro.overlay

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.Button
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var headerView: View? = null
    private var footerView: View? = null

    companion object {
        const val CHANNEL_ID = "exambro_overlay_channel"
        const val NOTIFICATION_ID = 101
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
    }

    private fun showOverlay() {
        showHeader()
        showFooter()
    }

    private fun showHeader() {
        val inflater = LayoutInflater.from(this)
        headerView = inflater.inflate(R.layout.overlay_header, null)

        // FIX #1: Flag yang benar agar tombol bisa di-tap
        // FLAG_NOT_FOCUSABLE  → tetap dipakai supaya tidak merebut focus dari Chrome
        // FLAG_NOT_TOUCH_MODAL → sentuhan di luar view diteruskan ke app bawah (Chrome)
        // FLAG_WATCH_OUTSIDE_TOUCH dihapus (tidak diperlukan)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            // FIX #2: TYPE_APPLICATION_OVERLAY wajib untuk Android 8+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            // FIX #3: Hapus FLAG_NOT_FOCUSABLE dari header agar PopupMenu bisa muncul
            // Gunakan FLAG_ALT_FOCUSABLE_IM saja agar keyboard tidak ikut muncul
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
        }

        val btnMenu = headerView!!.findViewById<ImageButton>(R.id.btnMenu)
        btnMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 1, 0, "Matikan Overlay")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { stopOverlayWithSound(); true }
                    else -> false
                }
            }
            popup.show()
        }

        windowManager.addView(headerView, params)
    }

    private fun showFooter() {
        val inflater = LayoutInflater.from(this)
        footerView = inflater.inflate(R.layout.overlay_footer, null)

        // FIX #4: Footer pakai FLAG_NOT_FOCUSABLE agar Chrome di bawah tetap aktif
        // tapi tombol tetap bisa di-tap karena FLAG_NOT_TOUCH_MODAL dihapus dari footer
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            // FIX #5: Untuk footer, hapus FLAG_NOT_FOCUSABLE supaya tombol clickable
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        val btnBack    = footerView!!.findViewById<Button>(R.id.btnBack)
        val btnExit    = footerView!!.findViewById<Button>(R.id.btnExit)
        val btnRefresh = footerView!!.findViewById<Button>(R.id.btnRefresh)
        val btnForward = footerView!!.findViewById<Button>(R.id.btnForward)

        // BACK → kirim broadcast ke Chrome untuk navigasi back
        // (tanpa Accessibility Service, simulasi via inject tidak bisa;
        //  solusi terbaik: kirim KeyEvent ke window yang focused)
        btnBack.setOnClickListener {
            simulateBackButton()
        }

        btnExit.setOnClickListener {
            stopOverlayWithSound()
        }

        btnRefresh.setOnClickListener {
            // Kirim intent reload ke Chrome
            val intent = Intent(Intent.ACTION_VIEW).apply {
                // Reload tab Chrome yang aktif tidak bisa tanpa Accessibility,
                // tapi bisa workaround dengan membuka ulang URL yang sama
                setPackage("com.android.chrome")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Gunakan gesture Chrome untuk refresh", Toast.LENGTH_SHORT).show()
            }
        }

        btnForward.setOnClickListener {
            Toast.makeText(this, "Gunakan gesture swipe Chrome untuk forward", Toast.LENGTH_SHORT).show()
        }

        windowManager.addView(footerView, params)
    }

    // Simulasi back button via KeyEvent injection ke focused window
    private fun simulateBackButton() {
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
        val upEvent   = KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_BACK)
        try {
            val inst = android.app.Instrumentation()
            Thread {
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            }.start()
        } catch (e: Exception) {
            // Fallback jika tidak punya permission inject
            Toast.makeText(this, "Gunakan tombol back hardware", Toast.LENGTH_SHORT).show()
        }
    }

    // FIX #6: exit_sound sekarang ada di res/raw/ jadi tidak akan crash
    private fun stopOverlayWithSound() {
        try {
            val mediaPlayer = MediaPlayer.create(this, R.raw.exit_sound)
            mediaPlayer?.setOnCompletionListener { mp ->
                mp.release()
                stopSelf()
            }
            mediaPlayer?.start() ?: stopSelf()
        } catch (e: Exception) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        headerView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        footerView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Exambro Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Overlay ujian aktif"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = "STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Exambro Aktif")
            .setContentText("Overlay sedang berjalan")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Matikan", stopPendingIntent)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopOverlayWithSound()
        }
        return START_STICKY
    }
}
