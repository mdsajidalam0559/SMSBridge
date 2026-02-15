package com.smsgateway

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SmsFcmService : FirebaseMessagingService() {

    companion object {
        const val TAG = "SmsFcmService"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        Log.d(TAG, "FCM message received: $data")

        val action = data["action"] ?: return
        if (action != "send_sms") return

        val messageId = data["message_id"] ?: return
        val recipient = data["recipient"] ?: return
        val message = data["message"] ?: return

        sendSms(this, messageId, recipient, message)
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token: $token")
        // Optionally re-register with the server
    }

    private fun sendSms(context: Context, messageId: String, recipient: String, message: String) {
        Log.d(TAG, "Sending SMS to $recipient: $message")

        // Check permission at runtime
        if (context.checkSelfPermission(android.Manifest.permission.SEND_SMS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SEND_SMS permission not granted!")
            SmsStatusReceiver.reportStatus(context, messageId, "FAILED",
                "SEND_SMS permission not granted. Open the app and grant permission.")
            return
        }

        val sentIntent = Intent("com.smsgateway.SMS_SENT").apply {
            putExtra("message_id", messageId)
            setPackage(context.packageName)
        }
        val deliveredIntent = Intent("com.smsgateway.SMS_DELIVERED").apply {
            putExtra("message_id", messageId)
            setPackage(context.packageName)
        }

        val sentPI = PendingIntent.getBroadcast(
            context, messageId.hashCode(),
            sentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val deliveredPI = PendingIntent.getBroadcast(
            context, messageId.hashCode() + 1,
            deliveredIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!SmsLimitManager.canSend(context)) {
            Log.e(TAG, "Daily SMS limit reached. Cannot send to $recipient")
            SmsStatusReceiver.reportStatus(context, messageId, "FAILED", "Daily SMS limit reached")
            return
        }

        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(recipient, null, message, sentPI, deliveredPI)
            Log.d(TAG, "SMS queued for $recipient")
            
            // Increment the counter
            SmsLimitManager.increment(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS", e)
            SmsStatusReceiver.reportStatus(context, messageId, "FAILED", e.message)
        }
    }
}
