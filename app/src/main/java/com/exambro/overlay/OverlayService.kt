package com.exambro.overlay

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.Button
import android.widget.ImageButton
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

    // FIX SUARA: Instance variable wajib — local variable bisa di-GC sebelum suara selesai
    private var mediaPlayer: MediaPlayer? = null

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 1000)
        }
    }

    companion object {
        const val CHANNEL_ID   = "exambro_overlay_channel"
        const val NOTIFICATION_ID = 101
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Toast.makeText(this, "[DBG] Service onCreate", Toast.LENGTH_SHORT).show()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            Toast.makeText(this, "[DBG] startForeground OK", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "[DBG] FG GAGAL: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf(); return
        }

        showOverlay()
        clockHandler.post(clockRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
        mediaPlayer?.let {
            try { if (it.isPlaying) it.stop(); it.release() } catch (_: Exception) {}
            mediaPlayer = null
        }
        headerView?.let { try { windowManager.removeView(it) } catch (_: Exception) {}; headerView = null }
        footerView?.let { try { windowManager.removeView(it) } catch (_: Exception) {}; footerView = null }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") stopOverlayWithSound()
        return START_STICKY
    }

    // ─── Tinggi Status Bar & Nav Bar (akurat untuk SEMUA mode navigasi) ──────

    /**
     * Pakai currentWindowMetrics (API 30+) agar akurat di gesture navigation.
     * getIdentifier("navigation_bar_height") sering return 0 di gesture nav devices.
     */
    private fun getStatusBarHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics
                .windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
                .top
        } else {
            val id = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) resources.getDimensionPixelSize(id)
            else (24 * resources.displayMetrics.density).toInt()
        }
    }

    private fun getNavBarHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics
                .windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
                .bottom
        } else {
            val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (id > 0) resources.getDimensionPixelSize(id) else 0
        }
    }

    // ─── Flag WindowManager yang dipakai di header & footer ──────────────────

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    /** Flag wajib agar overlay bisa menutupi status bar & nav bar */
    private val OVERLAY_FLAGS =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or      // gambar full layar
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS          // tembus keluar batas display

    // ─── Tampilkan Overlay ────────────────────────────────────────────────────

    private fun showOverlay() {
        showHeader()
        showFooter()
    }

    private fun showHeader() {
        try {
            headerView = LayoutInflater.from(this).inflate(R.layout.overlay_header, null)

            // Isi tinggi placeholder status bar (#1a7a3c) secara dinamis
            val statusH = getStatusBarHeight()
            headerView!!.findViewById<View>(R.id.viewStatusBar).apply {
                layoutParams = layoutParams.also { it.height = statusH }
                requestLayout()
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                OVERLAY_FLAGS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                y = 0
            }

            // 3-titik: HANYA untuk matikan overlay
            headerView!!.findViewById<ImageButton>(R.id.btnMenu).setOnClickListener { view ->
                val popup = PopupMenu(this, view)
                popup.menu.add(0, 1, 0, "Matikan Overlay")
                popup.setOnMenuItemClickListener {
                    if (it.itemId == 1) { stopOverlayWithSound(); true } else false
                }
                popup.show()
            }

            windowManager.addView(headerView, params)
            Toast.makeText(this, "[DBG] Header OK", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "[DBG] Header GAGAL: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showFooter() {
        try {
            footerView = LayoutInflater.from(this).inflate(R.layout.overlay_footer, null)

            // Isi tinggi placeholder nav bar (#22a24a) secara dinamis
            val navH = getNavBarHeight()
            footerView!!.findViewById<View>(R.id.viewNavBar).apply {
                layoutParams = layoutParams.also { it.height = navH }
                requestLayout()
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                OVERLAY_FLAGS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
                y = 0
            }

            // Tombol footer: TIDAK ada aksi — hanya visual overlay saja
            footerView!!.findViewById<Button>(R.id.btnBack).isClickable    = false
            footerView!!.findViewById<Button>(R.id.btnExit).isClickable    = false
            footerView!!.findViewById<Button>(R.id.btnRefresh).isClickable = false
            footerView!!.findViewById<Button>(R.id.btnForward).isClickable = false

            windowManager.addView(footerView, params)
            Toast.makeText(this, "[DBG] Footer OK (navH=$navH)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "[DBG] Footer GAGAL: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ─── Update Jam ───────────────────────────────────────────────────────────

    private fun updateClock() {
        val tvClock = headerView?.findViewById<TextView>(R.id.tvClock) ?: return
        tvClock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    // ─── Stop Overlay + Bunyi ─────────────────────────────────────────────────

    private fun stopOverlayWithSound() {
        clockHandler.removeCallbacks(clockRunnable)

        // Hapus view dulu agar user tidak bisa klik lagi saat suara dimainkan
        headerView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} ; headerView = null }
        footerView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} ; footerView = null }

        try {
            // FIX SUARA: Simpan ke instance variable agar tidak di-GC sebelum selesai play
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
                // Buka file suara dari raw resource
                val afd = resources.openRawResourceFd(R.raw.exit_sound)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
                setOnCompletionListener { mp ->
                    mp.release()
                    mediaPlayer = null
                    stopSelf()
                }
                start()
            }
        } catch (e: Exception) {
            // Suara gagal dimainkan (misal file korup), langsung stop service
            Toast.makeText(this, "[DBG] Suara GAGAL: ${e.message}", Toast.LENGTH_LONG).show()
            mediaPlayer = null
            stopSelf()
        }
    }

    // ─── Notification (wajib Foreground Service) ─────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Exambro Overlay", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Overlay ujian aktif"; setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).apply { action = "STOP" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Exambro Aktif")
            .setContentText("Overlay ujian sedang berjalan")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Matikan", pi)
            .build()
    }
}