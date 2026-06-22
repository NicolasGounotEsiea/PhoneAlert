package com.phonealert.app

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Orchestre l'alarme : règle le volume du canal ALARME, joue la sirène générée
 * (voir [SirenPlayer]) et fait vibrer.
 */
object AlarmController {

    // Volume d'alarme = maximum du canal ALARME. Ré-appliqué en boucle pendant
    // le verrouillage pour qu'on ne puisse pas le baisser avec les boutons.
    private const val VOLUME_RATIO = 1.0

    private var vibrator: Vibrator? = null

    /** Alarme complète : sirène + vibration + volume réglé. */
    fun start(context: Context, type: SirenPlayer.Type) {
        setAlarmVolume(context)
        SirenPlayer.start(type)
        startVibration(context)
    }

    /** Aperçu : uniquement le son (pas de vibration). */
    fun startPreview(context: Context, type: SirenPlayer.Type) {
        setAlarmVolume(context)
        SirenPlayer.start(type)
    }

    fun stop() {
        SirenPlayer.stop()
        vibrator?.cancel()
        vibrator = null
    }

    /** Ré-applique le volume max (appelé en boucle pendant le verrouillage). */
    fun keepVolume(context: Context) = setAlarmVolume(context)

    private fun setAlarmVolume(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val level = (max * VOLUME_RATIO).toInt().coerceIn(1, max)
        am.setStreamVolume(AudioManager.STREAM_ALARM, level, 0)
    }

    private fun startVibration(context: Context) {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = vib
        // Motif répété indéfiniment : pause 0, vibre 800 ms, pause 250 ms...
        val pattern = longArrayOf(0, 800, 250)
        vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }
}
