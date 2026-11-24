package com.fakecall.escape

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fakecall.escape.databinding.ActivityIncomingCallBinding
import java.util.Locale

class IncomingCallActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val TAG = "IncomingCallActivity"
    }

    private lateinit var binding: ActivityIncomingCallBinding
    private lateinit var prefs: PreferencesManager

    private var ringtone: Ringtone? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val timerHandler = Handler(Looper.getMainLooper())
    private var callDurationSeconds = 0
    private var isCallConnected = false

    private val timerRunnable = object : Runnable {
        override fun run() {
            callDurationSeconds++
            val minutes = callDurationSeconds / 60
            val seconds = callDurationSeconds % 60
            binding.tvCallTimer.text = String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
            timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreenFlags()

        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)
        tts = TextToSpeech(this, this)

        setupCallerInfo()
        setupListeners()
        startIncomingRinger()
    }

    private fun configureScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupCallerInfo() {
        binding.tvCallerName.text = prefs.callerName
        binding.tvCallerSubtitle.text = prefs.callerNumber

        val initials = prefs.callerName
            .split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase(Locale.ROOT)
            .ifEmpty { "CALL" }

        binding.tvAvatarInitials.text = initials
    }

    private fun setupListeners() {
        // Decline incoming call
        binding.btnDecline.setOnClickListener {
            stopIncomingRinger()
            finish()
        }

        // Answer incoming call
        binding.btnAnswer.setOnClickListener {
            onCallAnswered()
        }

        // End connected call
        binding.btnEndCall.setOnClickListener {
            onCallEnded()
        }

        // Quick Controls
        binding.btnMute.setOnClickListener {
            Toast.makeText(this, "Microphone muted", Toast.LENGTH_SHORT).show()
        }
        binding.btnKeypad.setOnClickListener {
            Toast.makeText(this, "Keypad opening", Toast.LENGTH_SHORT).show()
        }
        binding.btnSpeaker.setOnClickListener {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val isSpeaker = !audioManager.isSpeakerphoneOn
            audioManager.isSpeakerphoneOn = isSpeaker
            Toast.makeText(this, if (isSpeaker) "Speaker ON" else "Speaker OFF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startIncomingRinger() {
        try {
            val ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, ringtoneUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                play()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error playing system ringtone: ${e.message}")
        }

        if (prefs.isVibrationEnabled) {
            startVibration()
        }
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 1000, 1000, 1000, 1000)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    v?.vibrate(pattern, 0)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error starting vibration: ${e.message}")
        }
    }

    private fun stopVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.cancel()
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.cancel()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling vibration: ${e.message}")
        }
    }

    private fun stopIncomingRinger() {
        try {
            ringtone?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping ringtone: ${e.message}")
        }
        stopVibration()
    }

    private fun onCallAnswered() {
        isCallConnected = true
        stopIncomingRinger()

        // Transition UI to connected state
        binding.layoutIncomingActions.visibility = View.GONE
        binding.layoutInCallOptions.visibility = View.VISIBLE
        binding.layoutConnectedActions.visibility = View.VISIBLE

        binding.tvCallerSubtitle.visibility = View.GONE
        binding.tvCallTimer.visibility = View.VISIBLE
        binding.tvCallTimer.text = "00:00"

        // Route call audio strictly to ear-piece receiver (private conversational audio)
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = false

        // Start call timer
        timerHandler.postDelayed(timerRunnable, 1000)

        // Play realistic conversational voice audio
        playVoiceAudioScript()
    }

    private fun playVoiceAudioScript() {
        if (!isTtsReady || tts == null) return

        val scriptText = when (prefs.scriptIndex) {
            0 -> "Hey! Are you still at the office? We have an emergency issue on the deployment server right now, please join the meeting immediately!"
            1 -> "Hey, where are you right now? We urgently need you to come back home, please wrap up and head over as soon as you step out!"
            2 -> "Hello, I am downstairs at the main entrance with your parcel package delivery, please come down to collect it."
            else -> "Hey, I need you on the phone right now for an urgent update, please excuse yourself and step outside."
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)
        }

        tts?.speak(scriptText, TextToSpeech.QUEUE_FLUSH, null, "fake_call_voice")
        Log.i(TAG, "Speaking realistic conversational escape voice through earpiece")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
            if (isCallConnected) {
                playVoiceAudioScript()
            }
        }
    }

    private fun onCallEnded() {
        timerHandler.removeCallbacks(timerRunnable)
        tts?.stop()
        stopIncomingRinger()
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        stopIncomingRinger()
        tts?.stop()
        tts?.shutdown()
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
    }
}
