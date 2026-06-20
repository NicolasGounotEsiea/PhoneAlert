package com.phonealert.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.phonealert.app.databinding.ActivityLockBinding

/**
 * Écran "TÉLÉPHONE VOLÉ" plein écran (couche visuelle).
 * L'alarme et l'état sont gérés par [LockService].
 *
 * EXIGENCE FORTE : aucun déverrouillage local. Seul l'autre téléphone appairé
 * peut désactiver, via [remoteDeactivate] (→ Phase 3, Firestore/FCM).
 */
class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var messages: Array<String>
    private var index = 0

    private val cycleRunnable = object : Runnable {
        override fun run() {
            binding.stolenText.text = messages[index % messages.size]
            index++
            handler.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        messages = resources.getStringArray(R.array.stolen_messages)
        binding.staticLangs.text = messages.joinToString("   •   ")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* retour bloqué */ }
        })

        try { startLockTask() } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        index = 0
        handler.post(cycleRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(cycleRunnable)
    }

    override fun onStop() {
        super.onStop()
        // Tant que c'est verrouillé et qu'on ne se ferme pas volontairement,
        // on revient au premier plan (résiste au bouton Accueil).
        if (Prefs.isLocked(this) && !isFinishing) {
            startActivity(
                Intent(this, LockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(cycleRunnable)
        if (instance === this) instance = null
    }

    companion object {
        @Volatile
        private var instance: LockActivity? = null

        /** Ferme l'écran rouge (appelé par le service lors de la désactivation). */
        fun finishIfRunning() {
            instance?.let { act ->
                act.runOnUiThread {
                    try { act.stopLockTask() } catch (_: Exception) {}
                    act.finish()
                }
            }
        }

        /**
         * Seul point de déverrouillage : déclenché par l'autre téléphone appairé.
         * Sera relié au listener Firestore/FCM en Phase 3.
         */
        fun remoteDeactivate(context: Context) {
            LockService.stop(context.applicationContext)
        }
    }
}
