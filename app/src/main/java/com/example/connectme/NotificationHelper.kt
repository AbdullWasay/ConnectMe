package com.example.connectme

import android.util.Log
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

class NotificationHelper {
    companion object {
        private const val TAG = "NotificationHelper"

        // Your FCM server key from Firebase Console
        private const val FCM_SERVER_KEY = "AAAAXXX..." // Replace with your actual FCM server key

        // FCM API endpoint
        private const val FCM_API_URL = "https://fcm.googleapis.com/fcm/send"

        /**
         * Send a notification to a specific device
         */
        fun sendNotification(token: String, data: Map<String, String>) {
            thread {
                try {
                    // Create connection
                    val url = URL(FCM_API_URL)
                    val connection = url.openConnection() as HttpsURLConnection
                    connection.requestMethod = "POST"
                    connection.doOutput = true

                    // Set headers
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Authorization", "key=$FCM_SERVER_KEY")

                    // Create JSON payload
                    val notificationJson = JSONObject()
                    val dataJson = JSONObject()

                    // Add data fields
                    for ((key, value) in data) {
                        dataJson.put(key, value)
                    }

                    // Add notification fields if title and body are present
                    if (data.containsKey("title") && data.containsKey("body")) {
                        val notificationContent = JSONObject()
                        notificationContent.put("title", data["title"])
                        notificationContent.put("body", data["body"])
                        notificationJson.put("notification", notificationContent)
                    }

                    // Add data payload
                    notificationJson.put("data", dataJson)

                    // Add token
                    notificationJson.put("to", token)

                    // Add priority
                    notificationJson.put("priority", "high")

                    // Log the payload for debugging
                    Log.d(TAG, "Sending notification payload: $notificationJson")

                    // Write payload to connection
                    val outputStream = connection.outputStream
                    outputStream.write(notificationJson.toString().toByteArray())
                    outputStream.close()

                    // Get response
                    val responseCode = connection.responseCode
                    val responseMessage = connection.responseMessage

                    if (responseCode == 200) {
                        // Read response
                        val inputStream = connection.inputStream
                        val response = inputStream.bufferedReader().use { it.readText() }
                        Log.d(TAG, "Notification sent successfully: $response")
                    } else {
                        Log.e(TAG, "Failed to send notification. Response code: $responseCode, message: $responseMessage")

                        // Try to read error stream
                        try {
                            val errorStream = connection.errorStream
                            val errorResponse = errorStream?.bufferedReader()?.use { it.readText() }
                            Log.e(TAG, "Error response: $errorResponse")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading error response: ${e.message}")
                        }
                    }

                    connection.disconnect()
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending notification: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        /**
         * Add diagnostic logging to help debug notification issues
         */
        fun logNotificationDebug(token: String, data: Map<String, String>) {
            Log.d(TAG, "======= NOTIFICATION DEBUG =======")
            Log.d(TAG, "Token: ${token.take(10)}...${token.takeLast(5)}")
            Log.d(TAG, "Data payload:")
            for ((key, value) in data) {
                Log.d(TAG, "  $key: $value")
            }
            Log.d(TAG, "======= END DEBUG =======")
        }
    }
}