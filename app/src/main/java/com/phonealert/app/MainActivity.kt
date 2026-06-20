package com.phonealert.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.phonealert.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var previewing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Restaurer la sirène choisie
        when (Prefs.getSiren(this)) {
            SirenPlayer.Type.POLICE -> binding.sirenPolice.isChecked = true
            SirenPlayer.Type.WAIL -> binding.sirenWail.isChecked = true
            SirenPlayer.Type.YELP -> binding.sirenYelp.isChecked = true
            SirenPlayer.Type.BEEP -> binding.sirenBeep.isChecked = true
        }

        binding.sirenGroup.setOnCheckedChangeListener { _, _ ->
            Prefs.setSiren(this, selectedType())
            if (previewing) {
                AlarmController.stop()
                AlarmController.startPreview(this, selectedType())
            }
        }

        binding.previewButton.setOnClickListener { togglePreview() }

        binding.triggerButton.setOnClickListener {
            stopPreview()
            LockService.start(this)
        }
    }

    private fun selectedType(): SirenPlayer.Type = when (binding.sirenGroup.checkedRadioButtonId) {
        R.id.sirenWail -> SirenPlayer.Type.WAIL
        R.id.sirenYelp -> SirenPlayer.Type.YELP
        R.id.sirenBeep -> SirenPlayer.Type.BEEP
        else -> SirenPlayer.Type.POLICE
    }

    private fun togglePreview() {
        if (previewing) {
            stopPreview()
        } else {
            AlarmController.startPreview(this, selectedType())
            previewing = true
            binding.previewButton.text = getString(R.string.preview_stop)
        }
    }

    private fun stopPreview() {
        if (previewing) {
            AlarmController.stop()
            previewing = false
            binding.previewButton.text = getString(R.string.preview_play)
        }
    }

    override fun onStop() {
        super.onStop()
        stopPreview()
    }
}
