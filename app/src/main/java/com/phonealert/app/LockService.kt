package com.phonealert.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * Cœur du verrouillage. Service de premier plan :
 *  - détient l'alarme (survit quand l'écran passe en arrière-plan / process protégé) ;
 *  - persiste l'état "verrouillé" (repris au démarrage par [BootReceiver]) ;
 *  - relance l'écran rouge [LockActivity] (intent direct + full-screen intent).
 */
class LockService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val volumeKeeper = object : Runnable {
        override fun run() {
            AlarmController.keepVolume(this@LockService)
            handler.postDelayed(this, 700)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Contrat startForegroundService : on doit appeler startForeground rapidement.
        startForeground(NOTIF_ID, buildNotification())

        if (intent?.action == ACTION_STOP) {
            deactivate()
            return START_NOT_STICKY
        }

        // Démarrage / redémarrage du service => (ré)active le verrouillage
        Prefs.setLocked(this, true)
        AlarmController.start(this, Prefs.getSiren(this))
        handler.removeCallbacks(volumeKeeper)
        handler.post(volumeKeeper)
        launchLockScreen()
        return START_STICKY
    }

    private fun deactivate() {
        handler.removeCallbacks(volumeKeeper)
        AlarmController.stop()
        Prefs.setLocked(this, false)
        LockActivity.finishIfRunning()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun launchLockScreen() {
        startActivity(
            Intent(this, LockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun buildNotification(): Notification {
        createChannel()
        val fullScreen = PendingIntent.getActivity(
            this,
            0,
            Intent(this, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(fullScreen, true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.notif_channel),
                        NotificationManager.IMPORTANCE_HIGH
                    )
                )
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "phonealert_lock"
        private const val NOTIF_ID = 1
        const val ACTION_START = "com.phonealert.app.LOCK"
        const val ACTION_STOP = "com.phonealert.app.UNLOCK"

        fun start(context: Context) = send(context, ACTION_START)
        fun stop(context: Context) = send(context, ACTION_STOP)

        private fun send(context: Context, action: String) {
            val i = Intent(context, LockService::class.java).setAction(action)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
