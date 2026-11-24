package com.fakecall.escape

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.fakecall.escape.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startEscapeService()
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PocketEscapeTriggerService.ACTION_STATUS_UPDATE) {
                val isRunning = intent.getBooleanExtra(PocketEscapeTriggerService.EXTRA_IS_RUNNING, false)
                binding.switchMaster.isChecked = isRunning
                updateStatusUi(isRunning)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)

        setupUI()
    }

    private fun setupUI() {
        // Dormant by default
        binding.switchMaster.isChecked = PocketEscapeTriggerService.isRunning && prefs.isServiceEnabled
        updateStatusUi(PocketEscapeTriggerService.isRunning && prefs.isServiceEnabled)

        binding.switchMaster.setOnCheckedChangeListener { _, isChecked ->
            saveCallerSettings()
            if (isChecked) {
                checkAndStartService()
            } else {
                stopEscapeService()
            }
        }

        // Caller Identity Fields
        binding.etCallerName.setText(prefs.callerName)
        binding.etCallerNumber.setText(prefs.callerNumber)

        when (prefs.scriptIndex) {
            0 -> binding.rbWorkEmergency.isChecked = true
            1 -> binding.rbFamilyUrgent.isChecked = true
            2 -> binding.rbDelivery.isChecked = true
        }

        binding.radioGroupScript.setOnCheckedChangeListener { _, checkedId ->
            val idx = when (checkedId) {
                R.id.rbWorkEmergency -> 0
                R.id.rbFamilyUrgent -> 1
                R.id.rbDelivery -> 2
                else -> 0
            }
            prefs.scriptIndex = idx
        }

        // Quick Countdown Buttons
        binding.btnTimer5s.setOnClickListener {
            triggerDelayedCall(5)
        }
        binding.btnTimer15s.setOnClickListener {
            triggerDelayedCall(15)
        }
        binding.btnTimer30s.setOnClickListener {
            triggerDelayedCall(30)
        }
        binding.btnTimer1m.setOnClickListener {
            triggerDelayedCall(60)
        }

        // Live Test Call Button
        binding.btnTestIncomingCall.setOnClickListener {
            saveCallerSettings()
            val callIntent = Intent(this, IncomingCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(callIntent)
        }
    }

    private fun triggerDelayedCall(seconds: Int) {
        saveCallerSettings()
        prefs.isServiceEnabled = true
        val serviceIntent = Intent(this, PocketEscapeTriggerService::class.java).apply {
            action = PocketEscapeTriggerService.ACTION_TRIGGER_DELAYED
            putExtra(PocketEscapeTriggerService.EXTRA_DELAY_SECONDS, seconds)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        binding.switchMaster.isChecked = true
        updateStatusUi(true)
    }

    private fun saveCallerSettings() {
        val name = binding.etCallerName.text?.toString()?.trim()
        if (!name.isNullOrEmpty()) {
            prefs.callerName = name
        }

        val number = binding.etCallerNumber.text?.toString()?.trim()
        if (!number.isNullOrEmpty()) {
            prefs.callerNumber = number
        }
    }

    private fun checkAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startEscapeService()
    }

    private fun startEscapeService() {
        prefs.isServiceEnabled = true
        val serviceIntent = Intent(this, PocketEscapeTriggerService::class.java).apply {
            action = PocketEscapeTriggerService.ACTION_START
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        updateStatusUi(true)
        Toast.makeText(this, "Pocket Escape Service Activated", Toast.LENGTH_SHORT).show()
    }

    private fun stopEscapeService() {
        prefs.isServiceEnabled = false
        val serviceIntent = Intent(this, PocketEscapeTriggerService::class.java).apply {
            action = PocketEscapeTriggerService.ACTION_STOP_SERVICE
        }
        startService(serviceIntent)
        updateStatusUi(false)
        Toast.makeText(this, "Pocket Escape Stopped (Dormant)", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatusUi(isActive: Boolean) {
        if (isActive) {
            binding.viewStatusDot.setBackgroundResource(R.drawable.bg_dot_active)
            binding.tvStatusText.text = "Active • Ready to Escape (Cover sensor for 3s)"
            binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_active))
        } else {
            binding.viewStatusDot.setBackgroundResource(R.drawable.bg_dot_inactive)
            binding.tvStatusText.text = "Service Inactive (Dormant)"
            binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(PocketEscapeTriggerService.ACTION_STATUS_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }

        binding.switchMaster.isChecked = PocketEscapeTriggerService.isRunning && prefs.isServiceEnabled
        updateStatusUi(PocketEscapeTriggerService.isRunning && prefs.isServiceEnabled)
    }

    override fun onPause() {
        super.onPause()
        saveCallerSettings()
        try {
            unregisterReceiver(statusReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }
}
