package com.phonealert.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Au redémarrage :
 *  - si l'appareil était verrouillé ("volé") -> relance l'alarme + l'écran rouge ;
 *  - si l'appareil est appairé -> relance la surveillance des ordres distants.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        if (!isBoot) return

        if (Prefs.isLocked(context)) {
            LockService.start(context)
        } else if (Prefs.isInGroup(context)) {
            WatchService.start(context)
        }
    }
}
