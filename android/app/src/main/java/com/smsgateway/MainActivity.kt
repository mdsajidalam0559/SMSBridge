package com.smsgateway

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import android.provider.Settings
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvToken: TextView
    private lateinit var tvLimit: TextView
    private lateinit var etServerUrl: EditText
    private lateinit var btnRegister: Button
    private lateinit var progressBar: android.widget.ProgressBar

    companion object {
        const val PREFS_NAME = "sms_gateway_prefs"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_DEVICE_ID = "device_id"
        const val SMS_PERMISSION_CODE = 101
    }

    override fun onResume() {
        super.onResume()
        if (::tvLimit.isInitialized) {
            tvLimit.text = "Daily Limit: ${SmsLimitManager.getDetails(this)}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvLimit = findViewById(R.id.tvLimit)
        tvToken = findViewById(R.id.tvToken)
        etServerUrl = findViewById(R.id.etServerUrl)
        btnRegister = findViewById(R.id.btnRegister)
        progressBar = findViewById(R.id.progressBar)

        // Load saved server URL
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        etServerUrl.setText(prefs.getString(KEY_SERVER_URL, "http://YOUR_SERVER:8000"))

        // Request SMS permission
        requestSmsPermission()

        // Get FCM token
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            tvToken.text = "FCM Token: ${token.take(20)}..."
            btnRegister.isEnabled = true

            btnRegister.setOnClickListener {
                val serverUrl = etServerUrl.text.toString().trimEnd('/')
                if (serverUrl.isEmpty()) {
                    tvStatus.text = "❌ Please enter a Server URL"
                    return@setOnClickListener
                }
                prefs.edit().putString(KEY_SERVER_URL, serverUrl).apply()
                registerDevice(serverUrl, token)
            }
        }.addOnFailureListener { e ->
            tvToken.text = "❌ FCM Error: ${e.message}"
            btnRegister.isEnabled = false
        }

        // Check if already registered
        val apiKey = prefs.getString(KEY_API_KEY, null)
        if (apiKey != null) {
            tvStatus.text = "✅ Registered (API Key: ${apiKey.take(15)}...)"
        }
    }

    private fun registerDevice(serverUrl: String, fcmToken: String) {
        tvStatus.text = "Registering..."
        progressBar.visibility = android.view.View.VISIBLE
        btnRegister.isEnabled = false

        val hardwareId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        val json = JSONObject().apply {
            put("name", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("fcm_token", fcmToken)
            put("hardware_id", hardwareId)
        }

        // Configure timeouts
        val client = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$serverUrl/devices/register")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility = android.view.View.GONE
                    btnRegister.isEnabled = true
                    tvStatus.text = "❌ Connection Failed: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    progressBar.visibility = android.view.View.GONE
                    btnRegister.isEnabled = true
                }

                if (!response.isSuccessful) {
                    runOnUiThread { tvStatus.text = "❌ Server Error: ${response.code}" }
                    return
                }

                val responseBody = response.body?.string()
                val result = JSONObject(responseBody ?: "{}")
                val apiKey = result.optString("api_key", "")
                val deviceId = result.optString("id", "")

                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(KEY_API_KEY, apiKey)
                    .putString(KEY_DEVICE_ID, deviceId)
                    .apply()

                runOnUiThread {
                    tvStatus.text = "✅ Registered!\nAPI Key: ${apiKey.take(15)}...\nDevice ID: $deviceId"
                }

                // Schedule Heartbeat
                val heartbeatRequest = PeriodicWorkRequest.Builder(HeartbeatWorker::class.java, 15, TimeUnit.MINUTES)
                    .build()
                WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                    "HeartbeatWork",
                    androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                    heartbeatRequest
                )
            }
        })
    }

    private fun requestSmsPermission() {
        val perms = mutableListOf(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), SMS_PERMISSION_CODE)
        }
    }
}
