package com.exambro.overlay

import android.app.*
import android.content.Intent
import android.graphics.Color
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

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

    // ─── Tampilkan Header + Footer ─────────────────────────────────────────────

    private fun showOverlay() {
        showHeader()
        showFooter()
    }

    private fun showHeader() {
        val inflater = LayoutInflater.from(this)
        headerView = inflater.inflate(R.layout.overlay_header, null)

        val params = buildOverlayLayoutParams(Gravity.TOP)
        applyHeaderInsets()

        // Tombol titik tiga → matiin overlay + bunyi
        val btnMenu = headerView!!.findViewById<ImageButton>(R.id.btnMenu)
        btnMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 1, 0, "Matikan Overlay")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        stopOverlayWithSound()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        windowManager.addView(headerView, params)
        ViewCompat.requestApplyInsets(headerView!!)
    }

    private fun showFooter() {
        val inflater = LayoutInflater.from(this)
        footerView = inflater.inflate(R.layout.overlay_footer, null)

        val params = buildOverlayLayoutParams(Gravity.BOTTOM)
        applyFooterInsets()

        val btnBack    = footerView!!.findViewById<Button>(R.id.btnBack)
        val btnExit    = footerView!!.findViewById<Button>(R.id.btnExit)
        val btnRefresh = footerView!!.findViewById<Button>(R.id.btnRefresh)
        val btnForward = footerView!!.findViewById<Button>(R.id.btnForward)

        // BACK → simulasi tombol back sistem
        btnBack.setOnClickListener {
            val i = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            // Fallback: pesan toast karena tanpa Accessibility tidak bisa inject keyevent
            Toast.makeText(this, "Gunakan tombol BACK hardware", Toast.LENGTH_SHORT).show()
        }

        // EXIT → matiin overlay + bunyi
        btnExit.setOnClickListener {
            stopOverlayWithSound()
        }

        // REFRESH & FORWARD → info toast
        btnRefresh.setOnClickListener {
            Toast.makeText(this, "Gunakan gesture Chrome untuk refresh", Toast.LENGTH_SHORT).show()
        }

        btnForward.setOnClickListener {
            Toast.makeText(this, "Gunakan gesture Chrome untuk forward", Toast.LENGTH_SHORT).show()
        }

        windowManager.addView(footerView, params)
        ViewCompat.requestApplyInsets(footerView!!)
    }

    // ─── Stop Overlay + Mainkan Bunyi ─────────────────────────────────────────

    private fun stopOverlayWithSound() {
        try {
            // Ganti R.raw.exit_sound dengan nama file bunyi dari kamu
            val mediaPlayer = MediaPlayer.create(this, R.raw.exit_sound)
            mediaPlayer?.setOnCompletionListener { mp ->
                mp.release()
                stopSelf()
            }
            mediaPlayer?.start() ?: run {
                // Kalau file bunyi belum ada, langsung stop
                stopSelf()
            }
        } catch (e: Exception) {
            stopSelf()
        }
    }

    // ─── Hapus View saat Service Mati ─────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        headerView?.let { windowManager.removeView(it) }
        footerView?.let { windowManager.removeView(it) }
    }

    // ─── Notification (wajib untuk Foreground Service) ───────────────────────

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

    private fun buildOverlayLayoutParams(gravity: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
        }
    }

    private fun applyHeaderInsets() {
        val view = headerView ?: return
        val start = view.paddingStart
        val top = view.paddingTop
        val end = view.paddingEnd
        val bottom = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPaddingRelative(start, top + statusBarInsets.top, end, bottom)
            insets
        }
    }

    private fun applyFooterInsets() {
        val view = footerView ?: return
        val start = view.paddingStart
        val top = view.paddingTop
        val end = view.paddingEnd
        val bottom = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val navigationInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPaddingRelative(start, top, end, bottom + navigationInsets.bottom)
            insets
        }
    }
}
