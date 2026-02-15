package com.smsgateway

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class HeartbeatWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    companion object {
        const val TAG = "HeartbeatWorker"
    }

    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val serverUrl = prefs.getString(MainActivity.KEY_SERVER_URL, null)
        val apiKey = prefs.getString(MainActivity.KEY_API_KEY, null)

        if (serverUrl == null || apiKey == null) {
            Log.e(TAG, "Missing credentials, skipping heartbeat")
            return Result.failure()
        }

        if (!SmsLimitManager.canSend(applicationContext)) {
            Log.w(TAG, "Daily SMS limit reached, skipping heartbeat")
            return Result.success()
        }

        Log.d(TAG, "Sending heartbeat to $serverUrl")

        val client = OkHttpClient()
        val request = Request.Builder()
            .url("$serverUrl/devices/heartbeat")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .addHeader("x-api-key", apiKey)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Log.d(TAG, "Heartbeat successful")
                Result.success()
            } else {
                Log.e(TAG, "Heartbeat failed: ${response.code}")
                Result.retry()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Heartbeat error", e)
            Result.retry()
        }
    }
}
