package com.phonealert.app

import android.content.Context

/** Petites préférences persistées (choix de la sirène, état verrouillé). */
object Prefs {
    private const val FILE = "phonealert_prefs"
    private const val KEY_SIREN = "siren_type"
    private const val KEY_LOCKED = "locked"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getSiren(context: Context): SirenPlayer.Type {
        val name = prefs(context).getString(KEY_SIREN, SirenPlayer.Type.POLICE.name)
        return runCatching { SirenPlayer.Type.valueOf(name!!) }
            .getOrDefault(SirenPlayer.Type.POLICE)
    }

    fun setSiren(context: Context, type: SirenPlayer.Type) {
        prefs(context).edit().putString(KEY_SIREN, type.name).apply()
    }

    /** L'appareil est-il en état "volé/verrouillé" ? (survit au redémarrage) */
    fun isLocked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCKED, false)

    fun setLocked(context: Context, locked: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCKED, locked).apply()
    }
}
