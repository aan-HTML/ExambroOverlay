package com.exambro.overlay

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var headerView: View? = null
    private var footerView: View? = null

    // FIX BUG 2: mediaPlayer sebagai instance variable agar tidak di-GC
    // saat suara sedang dimainkan.
    private var mediaPlayer: MediaPlayer? = null

    // Handler untuk update jam di header tiap detik
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 1000)
        }
    }

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
        clockHandler.post(clockRunnable)
    }

    // Tampilkan Header + Footer

    private fun showOverlay() {
        showHeader()
        showFooter()
    }

    private fun showHeader() {
        val inflater = LayoutInflater.from(this)
        headerView = inflater.inflate(R.layout.overlay_header, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
        }

        // FIX BUG 3: Pakai androidx.appcompat.widget.PopupMenu (bukan android.widget.PopupMenu)
        // agar tidak crash di OEM ROM yang memerlukan Activity window token.
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

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        windowManager.addView(footerView, params)

        val navBarHeight = getNavigationBarHeight()
        val footerRoot = footerView!!.findViewById<LinearLayout>(R.id.footerRoot)
        footerRoot.setPadding(0, 0, 0, navBarHeight)

        val btnBack    = footerView!!.findViewById<Button>(R.id.btnBack)
        val btnExit    = footerView!!.findViewById<Button>(R.id.btnExit)
        val btnRefresh = footerView!!.findViewById<Button>(R.id.btnRefresh)
        val btnForward = footerView!!.findViewById<Button>(R.id.btnForward)

        btnBack.setOnClickListener {
            Toast.makeText(this, "Gunakan tombol BACK hardware", Toast.LENGTH_SHORT).show()
        }

        btnExit.setOnClickListener {
            stopOverlayWithSound()
        }

        btnRefresh.setOnClickListener {
            Toast.makeText(this, "Gunakan gesture Chrome untuk refresh", Toast.LENGTH_SHORT).show()
        }

        btnForward.setOnClickListener {
            Toast.makeText(this, "Gunakan gesture Chrome untuk forward", Toast.LENGTH_SHORT).show()
        }
    }

    // Update Jam di Header

    private fun updateClock() {
        val tvClock = headerView?.findViewById<TextView>(R.id.tvClock) ?: return
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        tvClock.text = timeFormat.format(Date())
    }

    // Tinggi Navigation Bar HP

    private fun getNavigationBarHeight(): Int {
        val resourceId = resources.getIdentifier(
            "navigation_bar_height", "dimen", "android"
        )
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    // Stop Overlay + Mainkan Bunyi

    private fun stopOverlayWithSound() {
        clockHandler.removeCallbacks(clockRunnable)

        // FIX BUG 2: Simpan ke instance variable `mediaPlayer` bukan local variable.
        // Local variable bisa di-GC sebelum suara selesai → suara terpotong / tidak bunyi.
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.exit_sound)
            mediaPlayer?.setOnCompletionListener { mp ->
                mp.release()
                mediaPlayer = null
                stopSelf()
            }
            // Jika MediaPlayer.create() gagal (return null), langsung stopSelf()
            if (mediaPlayer != null) {
                mediaPlayer!!.start()
            } else {
                stopSelf()
            }
        } catch (e: Exception) {
            mediaPlayer = null
            stopSelf()
        }
    }

    // Hapus View saat Service Mati

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
        // FIX BUG 2: Release mediaPlayer jika masih ada (misal service di-kill paksa)
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
            mediaPlayer = null
        }
        headerView?.let { windowManager.removeView(it); headerView = null }
        footerView?.let { windowManager.removeView(it); footerView = null }
    }

    // Notification (untuk Foreground Service)

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
            .setContentText("Overlay ujian sedang berjalan")
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