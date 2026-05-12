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
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var headerView: View? = null
    private var footerView: View? = null
    private val clockHandler = Handler(Looper.getMainLooper())

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

        Handler(Looper.getMainLooper()).postDelayed({
            showHeader()
            showFooter()
        }, 300)
    }

    private fun makeParams(gravity: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
        }
    }

    private fun showHeader() {
        try {
            headerView = LayoutInflater.from(this).inflate(R.layout.overlay_header, null)
            windowManager.addView(headerView, makeParams(Gravity.TOP))

            // Update jam setiap detik
            val tvClock = headerView!!.findViewById<TextView>(R.id.tvClock)
            val clockRunnable = object : Runnable {
                override fun run() {
                    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                    tvClock.text = fmt.format(Date())
                    clockHandler.postDelayed(this, 30000) // update tiap 30 detik
                }
            }
            clockHandler.post(clockRunnable)

            // Tombol titik tiga
            headerView!!.findViewById<ImageButton>(R.id.btnMenu).setOnClickListener { view ->
                val popup = PopupMenu(this, view)
                popup.menu.add(0, 1, 0, "Matikan Overlay")
                popup.setOnMenuItemClickListener {
                    if (it.itemId == 1) { stopOverlayWithSound(); true } else false
                }
                popup.show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showFooter() {
        try {
            footerView = LayoutInflater.from(this).inflate(R.layout.overlay_footer, null)
            windowManager.addView(footerView, makeParams(Gravity.BOTTOM))

            footerView!!.findViewById<TextView>(R.id.btnBack).setOnClickListener {
                Toast.makeText(this, "Gunakan tombol back hardware", Toast.LENGTH_SHORT).show()
            }
            footerView!!.findViewById<TextView>(R.id.btnExit).setOnClickListener {
                stopOverlayWithSound()
            }
            footerView!!.findViewById<TextView>(R.id.btnRefresh).setOnClickListener {
                Toast.makeText(this, "Gunakan gesture Chrome untuk refresh", Toast.LENGTH_SHORT).show()
            }
            footerView!!.findViewById<TextView>(R.id.btnForward).setOnClickListener {
                Toast.makeText(this, "Gunakan gesture Chrome untuk forward", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopOverlayWithSound() {
        clockHandler.removeCallbacksAndMessages(null)
        try {
            val mp = MediaPlayer.create(this, R.raw.exit_sound)
            mp?.setOnCompletionListener { it.release(); stopSelf() }
            mp?.start() ?: stopSelf()
        } catch (e: Exception) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacksAndMessages(null)
        headerView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        footerView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Exambro Overlay", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, OverlayService::class.java).apply { action = "STOP" }
        val pi = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Exambro Aktif")
            .setContentText("Overlay sedang berjalan")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Matikan", pi)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") stopOverlayWithSound()
        return START_STICKY
    }
}
