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

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvToken: TextView
    private lateinit var etServerUrl: EditText
    private lateinit var btnRegister: Button

    companion object {
        const val PREFS_NAME = "sms_gateway_prefs"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_DEVICE_ID = "device_id"
        const val SMS_PERMISSION_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvToken = findViewById(R.id.tvToken)
        etServerUrl = findViewById(R.id.etServerUrl)
        btnRegister = findViewById(R.id.btnRegister)

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
                prefs.edit().putString(KEY_SERVER_URL, serverUrl).apply()
                registerDevice(serverUrl, token)
            }
        }

        // Check if already registered
        val apiKey = prefs.getString(KEY_API_KEY, null)
        if (apiKey != null) {
            tvStatus.text = "✅ Registered (API Key: ${apiKey.take(15)}...)"
        }
    }

    private fun registerDevice(serverUrl: String, fcmToken: String) {
        tvStatus.text = "Registering..."

        val json = JSONObject().apply {
            put("name", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("fcm_token", fcmToken)
        }

        val client = OkHttpClient()
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$serverUrl/devices/register")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { tvStatus.text = "❌ Failed: ${e.message}" }
            }

            override fun onResponse(call: Call, response: Response) {
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
