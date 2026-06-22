package com.phonealert.app

import android.content.Context

/** Préférences persistées : sirène, état verrouillé, appartenance au groupe. */
object Prefs {
    private const val FILE = "phonealert_prefs"
    private const val KEY_SIREN = "siren_type"
    private const val KEY_LOCKED = "locked"
    private const val KEY_GROUP_ID = "group_id"
    private const val KEY_MY_UID = "my_uid"
    private const val KEY_MY_NAME = "my_name"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // --- Sirène ---
    fun getSiren(context: Context): SirenPlayer.Type {
        val name = prefs(context).getString(KEY_SIREN, SirenPlayer.Type.POLICE.name)
        return runCatching { SirenPlayer.Type.valueOf(name!!) }
            .getOrDefault(SirenPlayer.Type.POLICE)
    }

    fun setSiren(context: Context, type: SirenPlayer.Type) {
        prefs(context).edit().putString(KEY_SIREN, type.name).apply()
    }

    // --- État verrouillé (survit au redémarrage) ---
    fun isLocked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCKED, false)

    fun setLocked(context: Context, locked: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCKED, locked).apply()
    }

    // --- Groupe ---
    fun saveGroup(context: Context, groupId: String, myUid: String, myName: String) {
        prefs(context).edit()
            .putString(KEY_GROUP_ID, groupId)
            .putString(KEY_MY_UID, myUid)
            .putString(KEY_MY_NAME, myName)
            .apply()
    }

    fun getGroupId(context: Context): String? = prefs(context).getString(KEY_GROUP_ID, null)
    fun getMyUid(context: Context): String? = prefs(context).getString(KEY_MY_UID, null)
    fun getMyName(context: Context): String? = prefs(context).getString(KEY_MY_NAME, null)
    fun isInGroup(context: Context): Boolean = getGroupId(context) != null

    fun clearGroup(context: Context) {
        prefs(context).edit().remove(KEY_GROUP_ID).apply() // on garde l'uid (identité stable)
    }
}
