package com.phonealert.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ListenerRegistration
import com.phonealert.app.databinding.ActivityMainBinding
import com.phonealert.app.databinding.ItemMemberBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var previewing = false
    private var membersReg: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Fcm.register(this)
        ensurePermissions()

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
        binding.groupButton.setOnClickListener { startActivity(Intent(this, GroupActivity::class.java)) }

        binding.triggerButton.setOnClickListener {
            stopPreview()
            LockService.start(this)
        }
    }

    override fun onResume() {
        super.onResume()
        val inGroup = Prefs.isInGroup(this)
        binding.groupStatus.text = getString(
            if (inGroup) R.string.main_group_in else R.string.main_group_none
        )
        binding.groupButton.text = getString(
            if (inGroup) R.string.group_button_manage else R.string.group_button_join
        )
        binding.membersLabel.visibility = if (inGroup) View.VISIBLE else View.GONE

        membersReg?.remove()
        if (inGroup) {
            WatchService.start(this)
            membersReg = Groups.listenMembers(this) { members ->
                runOnUiThread { buildMemberRows(members) }
            }
        } else {
            binding.membersContainer.removeAllViews()
        }
    }

    override fun onPause() {
        super.onPause()
        membersReg?.remove()
        membersReg = null
        stopPreview()
    }

    private fun buildMemberRows(members: Map<String, String>) {
        val me = Prefs.getMyUid(this)
        binding.membersContainer.removeAllViews()
        val others = members.filterKeys { it != me }
        if (others.isEmpty()) {
            val tv = android.widget.TextView(this)
            tv.text = getString(R.string.group_alone)
            binding.membersContainer.addView(tv)
            return
        }
        for ((uid, name) in others) {
            val row = ItemMemberBinding.inflate(layoutInflater, binding.membersContainer, false)
            row.memberName.text = name
            row.lockBtn.setOnClickListener {
                RemoteControl.lock(this, uid) { ok, err ->
                    runOnUiThread { toast(if (ok) getString(R.string.sent_lock, name) else getString(R.string.remote_error, err ?: "")) }
                }
            }
            row.unlockBtn.setOnClickListener {
                RemoteControl.unlock(this, uid) { ok, err ->
                    runOnUiThread { toast(if (ok) getString(R.string.sent_unlock, name) else getString(R.string.remote_error, err ?: "")) }
                }
            }
            binding.membersContainer.addView(row.root)
        }
    }

    private fun ensurePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm != null && !nm.canUseFullScreenIntent()) {
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

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
}
