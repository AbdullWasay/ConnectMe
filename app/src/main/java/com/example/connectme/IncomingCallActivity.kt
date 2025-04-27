// IncomingCallActivity.kt
package com.example.connectme

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.database.FirebaseDatabase

class IncomingCallActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var callId: String = ""
    private var callerUsername: String = ""
    private var isVideoCall: Boolean = false

    companion object {
        private const val CALL_STATUS_ENDED = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        // Get call details from intent
        callId = intent.getStringExtra("CALL_ID") ?: ""
        callerUsername = intent.getStringExtra("CALLER_USERNAME") ?: ""
        val callerName = intent.getStringExtra("CALLER_NAME") ?: callerUsername
        isVideoCall = intent.getBooleanExtra("IS_VIDEO_CALL", false)

        if (callId.isEmpty() || callerUsername.isEmpty()) {
            finish()
            return
        }

        // Set up UI
        findViewById<TextView>(R.id.callerNameText).text = callerName
        findViewById<TextView>(R.id.callTypeText).text = if (isVideoCall) "Video Call" else "Voice Call"

        // Load caller profile image
        loadCallerProfileImage(callerUsername)

        // Set up accept button
        findViewById<Button>(R.id.acceptCallButton).setOnClickListener {
            acceptCall()
        }

        // Set up reject button
        findViewById<Button>(R.id.rejectCallButton).setOnClickListener {
            rejectCall()
        }

        // Play ringtone
        playRingtone()

        // Auto-reject call after 30 seconds if not answered
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                rejectCall()
            }
        }, 30000) // 30 seconds
    }

    private fun loadCallerProfileImage(username: String) {
        val database = FirebaseDatabase.getInstance()
        val userRef = database.getReference("Users").child(username)

        userRef.child("profilePicUrl").get().addOnSuccessListener { snapshot ->
            val profilePicUrl = snapshot.getValue(String::class.java)
            if (!profilePicUrl.isNullOrEmpty()) {
                val imageView = findViewById<ImageView>(R.id.callerProfileImage)

                try {
                    // If it's a base64 image
                    val imageBytes = android.util.Base64.decode(profilePicUrl, android.util.Base64.DEFAULT)
                    val decodedImage = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    imageView.setImageBitmap(decodedImage)
                } catch (e: Exception) {
                    // If it's a URL
                    Glide.with(this)
                        .load(profilePicUrl)
                        .centerCrop()
                        .into(imageView)
                }
            }
        }
    }

    private fun playRingtone() {
        try {
            mediaPlayer = MediaPlayer.create(this, Settings.System.DEFAULT_RINGTONE_URI)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        } catch (e: Exception) {
            // Fallback to default ringtone
            try {
                mediaPlayer?.isLooping = true
                mediaPlayer?.start()
            } catch (e: Exception) {
                // Ignore if we can't play a ringtone
            }
        }
    }

    private fun stopRingtone() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun acceptCall() {
        stopRingtone()

        // Get chat ID from the call data
        val database = FirebaseDatabase.getInstance()
        val callRef = database.getReference("Calls").child(callId)

        callRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val chatId = snapshot.child("chatId").getValue(String::class.java) ?: ""

                // Launch the Agora call activity
                val intent = Intent(this, AgoraCallActivity::class.java)
                intent.putExtra("IS_VIDEO_CALL", isVideoCall)
                intent.putExtra("CHAT_ID", chatId)
                intent.putExtra("REMOTE_USERNAME", callerUsername)
                intent.putExtra("CALL_ID", callId)
                intent.putExtra("IS_INCOMING", true)
                startActivity(intent)

                finish()
            } else {
                // Call no longer exists
                finish()
            }
        }.addOnFailureListener {
            finish()
        }
    }

    private fun rejectCall() {
        stopRingtone()

        // Update call status to ended
        val database = FirebaseDatabase.getInstance()
        val callRef = database.getReference("Calls").child(callId)

        callRef.child("status").setValue(CALL_STATUS_ENDED)
            .addOnCompleteListener {
                finish()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtone()
    }
}