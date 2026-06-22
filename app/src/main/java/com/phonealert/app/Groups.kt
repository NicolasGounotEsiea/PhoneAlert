package com.phonealert.app

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Groupe / cercle familial via Firebase (Auth anonyme + Firestore).
 *
 * Modèle Firestore :
 *  - groups/{groupId} : {
 *        members:  { uid: "Papa", uid2: "Mamie", ... }   // membres + noms
 *        controls: { uid: { action: "LOCK"/"UNLOCK", by, at } }  // ordres par cible
 *        createdAt
 *    }
 *
 * Le groupId est le code à 6 caractères (sert de secret partagé pour rejoindre).
 */
object Groups {

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    private fun signIn(onReady: (String) -> Unit, onError: (String) -> Unit) {
        val current = auth.currentUser
        if (current != null) onReady(current.uid)
        else auth.signInAnonymously()
            .addOnSuccessListener { onReady(it.user!!.uid) }
            .addOnFailureListener { onError(it.message ?: "Authentification échouée") }
    }

    /** Crée un groupe et ajoute le créateur dedans. */
    fun create(
        context: Context,
        name: String,
        onCode: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        signIn({ uid ->
            val code = randomCode()
            db.collection("groups").document(code).set(
                mapOf(
                    "members" to mapOf(uid to name),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).addOnSuccessListener {
                Prefs.saveGroup(context.applicationContext, code, uid, name)
                onCode(code)
            }.addOnFailureListener { onError(it.message ?: "Erreur de création") }
        }, onError)
    }

    /** Rejoint un groupe existant via son code. */
    fun join(
        context: Context,
        code: String,
        name: String,
        onJoined: () -> Unit,
        onError: (String) -> Unit
    ) {
        signIn({ uid ->
            val ref = db.collection("groups").document(code)
            ref.get().addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    onError("Groupe introuvable")
                    return@addOnSuccessListener
                }
                ref.update("members.$uid", name).addOnSuccessListener {
                    Prefs.saveGroup(context.applicationContext, code, uid, name)
                    onJoined()
                }.addOnFailureListener { onError(it.message ?: "Erreur en rejoignant") }
            }.addOnFailureListener { onError(it.message ?: "Erreur de lecture") }
        }, onError)
    }

    /** Quitte le groupe (se retire de la liste des membres). */
    fun leave(context: Context, onDone: () -> Unit) {
        val gid = Prefs.getGroupId(context)
        val uid = Prefs.getMyUid(context)
        if (gid != null && uid != null) {
            db.collection("groups").document(gid).update("members.$uid", FieldValue.delete())
        }
        Prefs.clearGroup(context.applicationContext)
        onDone()
    }

    /** Écoute la liste des membres {uid: nom} en temps réel. */
    fun listenMembers(context: Context, onMembers: (Map<String, String>) -> Unit): ListenerRegistration? {
        val gid = Prefs.getGroupId(context) ?: return null
        return db.collection("groups").document(gid).addSnapshotListener { snap, _ ->
            @Suppress("UNCHECKED_CAST")
            val members = (snap?.get("members") as? Map<String, String>) ?: emptyMap()
            onMembers(members)
        }
    }

    private fun randomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // sans I, O, 0, 1
        return (1..6).map { chars.random() }.joinToString("")
    }
}
