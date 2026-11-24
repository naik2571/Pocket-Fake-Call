package com.fakecall.escape

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fakecall_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_SERVICE_ENABLED = "service_enabled"
        const val KEY_CALLER_NAME = "caller_name"
        const val KEY_CALLER_NUMBER = "caller_number"
        const val KEY_SCRIPT_INDEX = "script_index"
        const val KEY_PROXIMITY_TRIGGER = "proximity_trigger"
        const val KEY_VOLUME_TRIGGER = "volume_trigger"
        const val KEY_VIBRATION = "vibration_enabled"
    }

    // Master Switch: DORMANT BY DEFAULT (false)
    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var callerName: String
        get() = prefs.getString(KEY_CALLER_NAME, "Office Manager") ?: "Office Manager"
        set(value) = prefs.edit().putString(KEY_CALLER_NAME, value).apply()

    var callerNumber: String
        get() = prefs.getString(KEY_CALLER_NUMBER, "Mobile • +1 (555) 019-2834") ?: "Mobile • +1 (555) 019-2834"
        set(value) = prefs.edit().putString(KEY_CALLER_NUMBER, value).apply()

    var scriptIndex: Int
        get() = prefs.getInt(KEY_SCRIPT_INDEX, 0)
        set(value) = prefs.edit().putInt(KEY_SCRIPT_INDEX, value).apply()

    var isProximityTrigger: Boolean
        get() = prefs.getBoolean(KEY_PROXIMITY_TRIGGER, true)
        set(value) = prefs.edit().putBoolean(KEY_PROXIMITY_TRIGGER, value).apply()

    var isVolumeTrigger: Boolean
        get() = prefs.getBoolean(KEY_VOLUME_TRIGGER, true)
        set(value) = prefs.edit().putBoolean(KEY_VOLUME_TRIGGER, value).apply()

    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION, value).apply()
}
