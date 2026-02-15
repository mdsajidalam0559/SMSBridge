package com.smsgateway

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SmsLimitManager {
    private const val PREFS_NAME = "sms_limit_prefs"
    private const val KEY_COUNT = "daily_sms_count"
    private const val KEY_LAST_RESET_DATE = "last_reset_date"
    private const val LIMIT = 90

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun checkReset(context: Context) {
        val prefs = getPrefs(context)
        val lastDate = prefs.getString(KEY_LAST_RESET_DATE, "")
        val today = getTodayDate()

        if (lastDate != today) {
            prefs.edit()
                .putString(KEY_LAST_RESET_DATE, today)
                .putInt(KEY_COUNT, 0)
                .apply()
        }
    }

    fun canSend(context: Context): Boolean {
        checkReset(context)
        val count = getPrefs(context).getInt(KEY_COUNT, 0)
        return count < LIMIT
    }

    fun increment(context: Context) {
        checkReset(context)
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_COUNT, 0)
        prefs.edit().putInt(KEY_COUNT, current + 1).apply()
    }

    fun getDetails(context: Context): String {
        checkReset(context)
        val count = getPrefs(context).getInt(KEY_COUNT, 0)
        return "$count / $LIMIT"
    }
}
