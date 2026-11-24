package com.fakecall.escape

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat

/**
 * Covert Emergency Escape Call Service.
 * Calibrated specifically for Realme 7 Pro proximity sensors:
 * - 3-Second Sustained Pocket Hold -> Triggers Escape Call
 * - Quick Double-Tap Cover (2 covers in 1.2s) -> Triggers Escape Call
 * - High-Priority Status notification with 1-tap instant trigger.
 */
class PocketEscapeTriggerService : Service(), SensorEventListener {

    companion object {
        const val TAG = "PocketEscapeService"
        const val CHANNEL_ID = "fakecall_escape_channel"
        const val NOTIFICATION_ID = 6060

        const val ACTION_START = "com.fakecall.escape.ACTION_START"
        const val ACTION_STOP_SERVICE = "com.fakecall.escape.ACTION_STOP_SERVICE"
        const val ACTION_TRIGGER_NOW = "com.fakecall.escape.ACTION_TRIGGER_NOW"
        const val ACTION_TRIGGER_DELAYED = "com.fakecall.escape.ACTION_TRIGGER_DELAYED"
        const val ACTION_STATUS_UPDATE = "com.fakecall.escape.ACTION_STATUS_UPDATE"

        const val EXTRA_DELAY_SECONDS = "extra_delay_seconds"
        const val EXTRA_IS_RUNNING = "extra_is_running"

        var isRunning = false
            private set
    }

    private lateinit var prefs: PreferencesManager
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null

    private var proximityCoverStartTime = 0L
    private var lastCoverPulseTime = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        createNotificationChannel()
        registerSensors()
        isRunning = true
        Log.i(TAG, "PocketEscapeTriggerService initialized successfully")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                Log.i(TAG, "Stop service requested via Notification Kill-Switch")
                stopEscapeService()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_NOW -> {
                scheduleFakeCall(5)
                return START_STICKY
            }
            ACTION_TRIGGER_DELAYED -> {
                val delaySeconds = intent.getIntExtra(EXTRA_DELAY_SECONDS, 10)
                scheduleFakeCall(delaySeconds)
                return START_STICKY
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        broadcastStatus()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PocketEscapeTriggerService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, PocketEscapeTriggerService::class.java).apply { action = ACTION_TRIGGER_NOW },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pocket Fake Call Escape")
            .setContentText("Discreet triggers active • Hold sensor for 3s to escape")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_notification, "⚡ TRIGGER (5s)", triggerIntent)
            .addAction(R.drawable.ic_notification, "🛑 STOP / CLOSE", stopIntent)
            .build()
    }

    private fun registerSensors() {
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.i(TAG, "Proximity sensor registered")
        }
    }

    private fun unregisterSensors() {
        try {
            sensorManager.unregisterListener(this)
            Log.i(TAG, "Sensors unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering sensors: ${e.message}")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY && prefs.isProximityTrigger) {
            val distance = event.values[0]
            val maxRange = proximitySensor?.maximumRange ?: 5f
            val isNear = distance < 3.5f && distance < maxRange
            val now = System.currentTimeMillis()

            if (isNear) {
                if (proximityCoverStartTime == 0L) {
                    proximityCoverStartTime = now

                    // Check for double pulse (2 taps within 1200ms)
                    if (now - lastCoverPulseTime in 150..1200) {
                        Log.i(TAG, "Double proximity tap detected! Scheduling discreet escape call.")
                        lastCoverPulseTime = 0L
                        vibrateDiscreetCue()
                        scheduleFakeCall(4)
                        return
                    }
                    lastCoverPulseTime = now

                    // Check for sustained hold (3 seconds)
                    mainHandler.postDelayed({
                        if (proximityCoverStartTime > 0 && (System.currentTimeMillis() - proximityCoverStartTime >= 2700)) {
                            Log.i(TAG, "Proximity hold 3s confirmed! Launching Fake Call!")
                            proximityCoverStartTime = 0L
                            vibrateDiscreetCue()
                            launchFakeCallActivity()
                        }
                    }, 2900)
                }
            } else {
                proximityCoverStartTime = 0L
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun scheduleFakeCall(delaySeconds: Int) {
        Toast.makeText(this, "Escape Call in $delaySeconds seconds", Toast.LENGTH_SHORT).show()
        mainHandler.postDelayed({
            launchFakeCallActivity()
        }, delaySeconds * 1000L)
    }

    private fun launchFakeCallActivity() {
        val callIntent = Intent(this, IncomingCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(callIntent)
    }

    private fun vibrateDiscreetCue() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(50)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error vibrating cue: ${e.message}")
        }
    }

    private fun broadcastStatus() {
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_IS_RUNNING, isRunning)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun stopEscapeService() {
        prefs.isServiceEnabled = false
        isRunning = false
        unregisterSensors()
        mainHandler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        broadcastStatus()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pocket Fake Call Escape Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains discreet background triggers for quick call escapes"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEscapeService()
        Log.i(TAG, "PocketEscapeTriggerService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
