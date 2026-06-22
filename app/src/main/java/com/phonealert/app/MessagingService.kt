package com.phonealert.app

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Réception des ordres distants via push FCM — fonctionne même quand l'app est fermée
 * (message data haute priorité → réveille le process et déclenche le service).
 */
class MessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val action = message.data["action"]
        Log.d("PhoneAlert", "FCM reçu: action=$action")
        when (action) {
            "LOCK" -> if (!Prefs.isLocked(this)) LockService.start(this)
            "UNLOCK" -> if (Prefs.isLocked(this)) LockService.stop(this)
        }
    }

    override fun onNewToken(token: String) {
        Log.d("PhoneAlert", "FCM onNewToken")
        FirebaseAuth.getInstance().currentUser?.uid?.let { Fcm.saveToken(it) }
    }
}
