package com.example.connectme

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

import java.util.concurrent.atomic.AtomicInteger

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID_MESSAGES = "channel_messages"
        private const val CHANNEL_ID_REQUESTS = "channel_requests"
        private const val CHANNEL_ID_ALERTS = "channel_alerts"

        // For generating unique notification IDs
        private val notificationId = AtomicInteger(0)

        // Function to get a unique notification ID
        fun getUniqueNotificationId(): Int {
            return notificationId.incrementAndGet()
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")

        // Save the token to your server
        sendRegistrationToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            // Handle the data payload based on notification type
            val notificationType = remoteMessage.data["type"] ?: "message"

            when (notificationType) {
                "message" -> handleNewMessageNotification(remoteMessage.data)
                "follow_request" -> handleFollowRequestNotification(remoteMessage.data)
                "screenshot" -> handleScreenshotNotification(remoteMessage.data)
                else -> handleDefaultNotification(remoteMessage.data)
            }
        }

        // Check if message contains notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            // If there's a notification payload, display it
            showNotification(it.title ?: "New Notification", it.body ?: "", null, "default")
        }
    }

    private fun sendRegistrationToServer(token: String) {
        // Get current user ID from SharedPreferences
        val sharedPreferences = getSharedPreferences("ConnectMePrefs", Context.MODE_PRIVATE)
        val username = sharedPreferences.getString("username", null)

        if (username.isNullOrEmpty()) {
            Log.w(TAG, "Cannot save FCM token: No logged in user")
            return
        }

        // Save the FCM token to Firebase for this user
        val database = com.google.firebase.database.FirebaseDatabase.getInstance()
        val tokensRef = database.getReference("FCMTokens")

        tokensRef.child(username).setValue(token)
            .addOnSuccessListener {
                Log.d(TAG, "FCM token saved to Firebase for user: $username")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save FCM token: ${e.message}")
            }
    }

    // Handle new message notification
    private fun handleNewMessageNotification(data: Map<String, String>) {
        val title = data["title"] ?: "New Message"
        val body = data["body"] ?: "You have a new message"
        val senderUsername = data["senderUsername"] ?: ""
        val chatId = data["chatId"] ?: ""
        val senderProfilePic = data["senderProfilePic"]

        // Load profile picture if available
        var profileBitmap: Bitmap? = null
        if (!senderProfilePic.isNullOrEmpty()) {
            try {
                // Use URL connection instead of Picasso
                val url = java.net.URL(senderProfilePic)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.doInput = true
                connection.connect()
                val input = connection.inputStream
                profileBitmap = BitmapFactory.decodeStream(input)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile picture: ${e.message}")
            }
        }

        // Create intent to open the chat screen
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("OPEN_CHAT", true)
            putExtra("CHAT_ID", chatId)
            putExtra("USERNAME", senderUsername)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        showNotification(title, body, profileBitmap, "message", intent)
    }

    // Handle follow request notification
    private fun handleFollowRequestNotification(data: Map<String, String>) {
        val title = data["title"] ?: "New Follow Request"
        val body = data["body"] ?: "Someone wants to follow you"
        val requesterUsername = data["requesterUsername"] ?: ""
        val requesterProfilePic = data["requesterProfilePic"]

        // Load profile picture if available
        var profileBitmap: Bitmap? = null
        if (!requesterProfilePic.isNullOrEmpty()) {
            try {
                // Use Glide instead of Picasso
                profileBitmap = com.bumptech.glide.Glide.with(applicationContext)
                    .asBitmap()
                    .load(requesterProfilePic)
                    .submit()
                    .get()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile picture: ${e.message}")
            }
        }

        // Create intent to open the follow requests screen
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("OPEN_FOLLOW_REQUESTS", true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        showNotification(title, body, profileBitmap, "follow_request", intent)
    }

    // Handle screenshot notification
    private fun handleScreenshotNotification(data: Map<String, String>) {
        val title = data["title"] ?: "Screenshot Alert"
        val body = data["body"] ?: "Someone took a screenshot of your chat"
        val username = data["username"] ?: ""
        val chatId = data["chatId"] ?: ""

        // Create intent to open the chat screen
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("OPEN_CHAT", true)
            putExtra("CHAT_ID", chatId)
            putExtra("USERNAME", username)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        showNotification(title, body, null, "screenshot", intent)
    }

    // Handle default notification
    private fun handleDefaultNotification(data: Map<String, String>) {
        val title = data["title"] ?: "New Notification"
        val body = data["body"] ?: "You have a new notification"

        showNotification(title, body, null, "default")
    }

    // Show notification with the provided details
    private fun showNotification(
        title: String,
        body: String,
        largeIcon: Bitmap?,
        type: String,
        intent: Intent = Intent(this, MainActivity::class.java)
    ) {
        // Create a pending intent for when the notification is tapped
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Select the appropriate channel ID based on notification type
        val channelId = when (type) {
            "message" -> CHANNEL_ID_MESSAGES
            "follow_request" -> CHANNEL_ID_REQUESTS
            "screenshot" -> CHANNEL_ID_ALERTS
            else -> CHANNEL_ID_MESSAGES
        }

        // Get the default notification sound
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Build the notification
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.notification) // Make sure you have this icon in your drawable resources
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Add large icon if available
        if (largeIcon != null) {
            notificationBuilder.setLargeIcon(largeIcon)
        }

        // Get notification manager
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channels for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Messages channel
            val messagesChannel = NotificationChannel(
                CHANNEL_ID_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new messages"
                enableLights(true)
                enableVibration(true)
            }

            // Follow requests channel
            val requestsChannel = NotificationChannel(
                CHANNEL_ID_REQUESTS,
                "Follow Requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for follow requests"
                enableLights(true)
                enableVibration(true)
            }

            // Alerts channel
            val alertsChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for security alerts like screenshots"
                enableLights(true)
                enableVibration(true)
            }

            // Register the channels with the system
            notificationManager.createNotificationChannels(
                listOf(messagesChannel, requestsChannel, alertsChannel)
            )
        }

        // Show the notification
        notificationManager.notify(getUniqueNotificationId(), notificationBuilder.build())
    }
}