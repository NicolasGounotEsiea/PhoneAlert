package com.phonealert.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Au redémarrage du téléphone : si l'appareil était en état "volé/verrouillé",
 * on relance immédiatement l'alarme + l'écran rouge.
 * => éteindre/redémarrer ne libère pas le téléphone.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if ((action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
                action == "android.intent.action.QUICKBOOT_POWERON") &&
            Prefs.isLocked(context)
        ) {
            LockService.start(context)
        }
    }
}
