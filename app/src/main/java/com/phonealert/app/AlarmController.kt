package com.phonealert.app

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Orchestre l'alarme : volume du canal ALARME poussé au max,
 * sirène générée (voir [SirenPlayer]) et vibration.
 */
object AlarmController {

    private var vibrator: Vibrator? = null

    /** Alarme complète : sirène + vibration + volume max. */
    fun start(context: Context, type: SirenPlayer.Type) {
        forceMaxAlarmVolume(context)
        SirenPlayer.start(type)
        startVibration(context)
    }

    /** Aperçu : uniquement le son (pas de vibration). */
    fun startPreview(context: Context, type: SirenPlayer.Type) {
        forceMaxAlarmVolume(context)
        SirenPlayer.start(type)
    }

    fun stop() {
        SirenPlayer.stop()
        vibrator?.cancel()
        vibrator = null
    }

    private fun forceMaxAlarmVolume(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
    }

    private fun startVibration(context: Context) {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = vib
        // Motif répété indéfiniment : pause 0, vibre 800ms, pause 250ms...
        val pattern = longArrayOf(0, 800, 250)
        vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }
}
