package com.phonealert.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ListenerRegistration
import com.phonealert.app.databinding.ActivityGroupBinding

class GroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupBinding
    private var membersReg: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Prefs.getMyName(this)?.let { binding.nameInput.setText(it) }

        binding.createButton.setOnClickListener { doCreate() }
        binding.joinButton.setOnClickListener { doJoin() }
        binding.leaveButton.setOnClickListener {
            Groups.leave(this) {
                refreshStatus()
                message(getString(R.string.group_left))
            }
        }
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        membersReg?.remove()
        membersReg = Groups.listenMembers(this) { members ->
            runOnUiThread {
                binding.membersText.text =
                    if (members.isEmpty()) "—" else members.values.joinToString("\n") { "• $it" }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        membersReg?.remove()
        membersReg = null
    }

    private fun refreshStatus() {
        val inGroup = Prefs.isInGroup(this)
        binding.statusText.text = getString(
            if (inGroup) R.string.group_status_in else R.string.group_status_none
        )
        binding.leaveButton.visibility = if (inGroup) View.VISIBLE else View.GONE
        if (!inGroup) binding.membersText.text = "—"
    }

    private fun name(): String = binding.nameInput.text.toString().trim()

    private fun doCreate() {
        val n = name()
        if (n.isEmpty()) { message(getString(R.string.group_need_name)); return }
        setBusy(true)
        Groups.create(
            this, n,
            onCode = { code ->
                runOnUiThread {
                    binding.codeText.text = code
                    message(getString(R.string.group_created))
                    refreshStatus()
                    setBusy(false)
                }
            },
            onError = { e -> runOnUiThread { message(getString(R.string.pair_error, e)); setBusy(false) } }
        )
    }

    private fun doJoin() {
        val n = name()
        if (n.isEmpty()) { message(getString(R.string.group_need_name)); return }
        val code = binding.codeInput.text.toString().trim().uppercase()
        if (code.isEmpty()) { message(getString(R.string.group_need_code)); return }
        setBusy(true)
        Groups.join(
            this, code, n,
            onJoined = {
                runOnUiThread {
                    message(getString(R.string.group_joined))
                    refreshStatus()
                    setBusy(false)
                }
            },
            onError = { e -> runOnUiThread { message(getString(R.string.pair_error, e)); setBusy(false) } }
        )
    }

    private fun setBusy(busy: Boolean) {
        binding.createButton.isEnabled = !busy
        binding.joinButton.isEnabled = !busy
    }

    private fun message(text: String) { binding.messageText.text = text }
}
