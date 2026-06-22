package com.phonealert.app

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Enregistre le token FCM de cet appareil dans users/{uid}.fcmToken,
 * pour que la Cloud Function puisse lui envoyer un push de verrouillage
 * (même quand l'app est fermée).
 */
object Fcm {

    fun register(context: Context) {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        if (uid != null) {
            saveToken(uid)
        } else {
            auth.signInAnonymously().addOnSuccessListener { saveToken(it.user!!.uid) }
        }
    }

    fun saveToken(uid: String) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            Log.d("PhoneAlert", "FCM token enregistré pour users/$uid")
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
        }
    }
}
