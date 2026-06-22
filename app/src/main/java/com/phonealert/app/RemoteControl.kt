package com.phonealert.app

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Envoi d'un ordre à un membre du groupe : écrit groups/{groupId}.controls.{targetUid}.
 * Le membre ciblé (WatchService / push FCM) réagit.
 */
object RemoteControl {

    fun lock(context: Context, targetUid: String, onResult: (Boolean, String?) -> Unit) =
        send(context, targetUid, "LOCK", onResult)

    fun unlock(context: Context, targetUid: String, onResult: (Boolean, String?) -> Unit) =
        send(context, targetUid, "UNLOCK", onResult)

    private fun send(context: Context, targetUid: String, action: String, onResult: (Boolean, String?) -> Unit) {
        val groupId = Prefs.getGroupId(context)
        val me = Prefs.getMyUid(context) ?: ""
        if (groupId == null) {
            onResult(false, "Aucun groupe")
            return
        }
        val cmd = mapOf(
            "action" to action,
            "by" to me,
            "at" to System.currentTimeMillis()
        )
        Log.d("PhoneAlert", "RemoteControl.send $action -> $targetUid (groupe $groupId)")
        FirebaseFirestore.getInstance()
            .collection("groups").document(groupId)
            .update("controls.$targetUid", cmd)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { onResult(false, it.message) }
    }
}
