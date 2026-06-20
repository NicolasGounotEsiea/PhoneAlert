package com.phonealert.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Génère des sirènes très puissantes en PCM, à la volée (aucun fichier audio requis).
 *
 * Optimisé pour être le plus strident / audible de loin possible :
 *  - fréquences poussées dans la bande 1–3,2 kHz (sensibilité max de l'oreille
 *    ET zone de meilleur rendement du haut-parleur d'un téléphone) ;
 *  - saturation (soft-clip tanh) → timbre déchirant + RMS plus élevé = plus fort ;
 *  - balayage de fréquence (wail/yelp) → pas d'accoutumance.
 * Joué sur le canal ALARME, pleine amplitude.
 */
object SirenPlayer {

    enum class Type { POLICE, WAIL, YELP, BEEP }

    private const val SAMPLE_RATE = 44100

    // Bande de fréquences des balayages (Hz)
    private const val LOW = 1000.0
    private const val HIGH = 3200.0

    // Niveau de saturation (plus haut = plus "déchirant"/fort)
    private const val DRIVE = 4.0

    @Volatile
    private var playing = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null

    fun start(type: Type) {
        if (playing) return
        playing = true

        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, 8192)

        val at = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = at
        at.play()

        thread = Thread { synthLoop(at, type) }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        playing = false
        thread?.join(300)
        thread = null
        track?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        track = null
    }

    private fun synthLoop(at: AudioTrack, type: Type) {
        val chunk = ShortArray(1024)
        var phase = 0.0
        var t = 0.0
        val dt = 1.0 / SAMPLE_RATE
        val amp = 0.97 * Short.MAX_VALUE
        val norm = tanh(DRIVE) // pour renormaliser après saturation

        while (playing) {
            for (i in chunk.indices) {
                val freq = freqAt(type, t)
                phase += 2.0 * PI * freq * dt
                if (phase > 2.0 * PI) phase -= 2.0 * PI

                val osc = sin(phase)
                // Saturation douce -> riche en harmoniques, plus fort et plus agressif
                var sample = tanh(DRIVE * osc) / norm

                // Coupure marquée pour les bips d'urgence
                if (type == Type.BEEP) {
                    val on = (t * 1000).toLong() / 150 % 2 == 0L
                    if (!on) sample = 0.0
                }

                chunk[i] = (sample * amp).toInt().toShort()
                t += dt
            }
            if (!playing) break
            at.write(chunk, 0, chunk.size)
        }
    }

    /** Fréquence instantanée (Hz) selon le style et le temps écoulé. */
    private fun freqAt(type: Type, t: Double): Double = when (type) {
        // Deux tons aigus alternés (toutes les 0,4 s)
        Type.POLICE -> if ((t * 1000).toLong() / 400 % 2 == 0L) 1400.0 else 2100.0

        // Montée/descente continue (wail) sur ~1,6 s
        Type.WAIL -> {
            val period = 1.6
            val x = (t % period) / period
            val tri = if (x < 0.5) x * 2.0 else (1.0 - x) * 2.0
            LOW + tri * (HIGH - LOW)
        }

        // Balayage rapide montant répété ~5x/s (yelp)
        Type.YELP -> {
            val period = 0.2
            val x = (t % period) / period
            LOW + x * (HIGH - LOW)
        }

        // Bips aigus très perçants
        Type.BEEP -> 3000.0
    }
}
