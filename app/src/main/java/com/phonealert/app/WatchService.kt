package com.phonealert.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Service de surveillance (actif quand l'appareil est appairé) :
 * écoute pairs/{pairId} et réagit aux ordres qui visent CET appareil.
 *  - LOCK   -> démarre [LockService] (verrouillage + alarme)
 *  - UNLOCK -> arrête [LockService]
 * Idempotent grâce à l'état Prefs.isLocked.
 */
class WatchService : Service() {

    private var reg: ListenerRegistration? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        ensureSignedInThenListen()
        return START_STICKY
    }

    private fun ensureSignedInThenListen() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            startListening()
        } else {
            auth.signInAnonymously().addOnSuccessListener { startListening() }
        }
    }

    private fun startListening() {
        val groupId = Prefs.getGroupId(this) ?: run { Log.w("PhoneAlert", "Watch: pas de groupe"); return }
        val me = Prefs.getMyUid(this) ?: run { Log.w("PhoneAlert", "Watch: pas de myUid"); return }
        reg?.remove()
        Log.d("PhoneAlert", "Watch: écoute groupe=$groupId me=$me")
        Fcm.saveToken(me)
        reg = FirebaseFirestore.getInstance()
            .collection("groups").document(groupId)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w("PhoneAlert", "Watch snapshot err: ${err.message}"); return@addSnapshotListener }
                val controls = snap?.get("controls") as? Map<*, *> ?: return@addSnapshotListener
                val mine = controls[me] as? Map<*, *> ?: return@addSnapshotListener
                val action = mine["action"]
                Log.d("PhoneAlert", "Watch controls[$me] action=$action locked=${Prefs.isLocked(this)}")
                when (action) {
                    "LOCK" -> if (!Prefs.isLocked(this)) LockService.start(this)
                    "UNLOCK" -> if (Prefs.isLocked(this)) LockService.stop(this)
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        reg?.remove()
        reg = null
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.watch_channel),
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.watch_title))
            .setContentText(getString(R.string.watch_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "phonealert_watch"
        private const val NOTIF_ID = 2

        fun start(context: Context) {
            val i = Intent(context, WatchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
