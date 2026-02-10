package com.smsgateway

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class SmsStatusReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "SmsStatusReceiver"

        fun reportStatus(context: Context, messageId: String, status: String, error: String? = null) {
            val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val serverUrl = prefs.getString(MainActivity.KEY_SERVER_URL, "") ?: return

            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                val json = JSONObject().apply {
                    put("message_id", messageId)
                    put("status", status)
                    put("device_token", token)
                    if (error != null) put("error", error)
                }

                val client = OkHttpClient()
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$serverUrl/sms/status")
                    .post(body)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Failed to report status: ${e.message}")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        Log.d(TAG, "Status reported: $status for $messageId")
                        response.close()
                    }
                })
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra("message_id") ?: return

        when (intent.action) {
            "com.smsgateway.SMS_SENT" -> {
                val status = when (resultCode) {
                    Activity.RESULT_OK -> "SENT"
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "FAILED"
                    SmsManager.RESULT_ERROR_NO_SERVICE -> "FAILED"
                    SmsManager.RESULT_ERROR_NULL_PDU -> "FAILED"
                    SmsManager.RESULT_ERROR_RADIO_OFF -> "FAILED"
                    else -> "FAILED"
                }
                val error = if (status == "FAILED") "SMS send failed (code: $resultCode)" else null
                Log.d(TAG, "SMS_SENT: $messageId → $status")
                reportStatus(context, messageId, status, error)
            }

            "com.smsgateway.SMS_DELIVERED" -> {
                val status = when (resultCode) {
                    Activity.RESULT_OK -> "DELIVERED"
                    else -> "FAILED"
                }
                Log.d(TAG, "SMS_DELIVERED: $messageId → $status")
                reportStatus(context, messageId, status)
            }
        }
    }
}
