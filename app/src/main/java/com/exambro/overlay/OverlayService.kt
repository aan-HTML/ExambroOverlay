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

        // FIX: Delay addView agar foreground service sempat fully started
        // Di Android 12+ addView langsung di onCreate() kadang di-skip system
        Handler(Looper.getMainLooper()).postDelayed({
            showOverlay()
        }, 300)
    }

    private fun showOverlay() {
        showHeader()
        showFooter()
    }

    private fun makeOverlayParams(gravity: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        // FLAG_NOT_TOUCH_MODAL → sentuhan di area tengah (Chrome) diteruskan ke bawah
        // Tanpa FLAG_NOT_FOCUSABLE → view bisa menerima click/touch
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
        }
    }

    private fun showHeader() {
        try {
            val inflater = LayoutInflater.from(this)
            headerView = inflater.inflate(R.layout.overlay_header, null)

            val params = makeOverlayParams(Gravity.TOP)
            windowManager.addView(headerView, params)

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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showFooter() {
        try {
            val inflater = LayoutInflater.from(this)
            footerView = inflater.inflate(R.layout.overlay_footer, null)

            val params = makeOverlayParams(Gravity.BOTTOM)
            windowManager.addView(footerView, params)

            footerView!!.findViewById<Button>(R.id.btnBack).setOnClickListener {
                Toast.makeText(this, "Gunakan tombol back hardware", Toast.LENGTH_SHORT).show()
            }
            footerView!!.findViewById<Button>(R.id.btnExit).setOnClickListener {
                stopOverlayWithSound()
            }
            footerView!!.findViewById<Button>(R.id.btnRefresh).setOnClickListener {
                Toast.makeText(this, "Gunakan gesture Chrome untuk refresh", Toast.LENGTH_SHORT).show()
            }
            footerView!!.findViewById<Button>(R.id.btnForward).setOnClickListener {
                Toast.makeText(this, "Gunakan gesture Chrome untuk forward", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
        headerView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        footerView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
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
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, OverlayService::class.java).apply { action = "STOP" }
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
        if (intent?.action == "STOP") stopOverlayWithSound()
        return START_STICKY
    }
}
