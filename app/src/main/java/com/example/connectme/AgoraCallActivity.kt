// AgoraCallActivity.kt
package com.example.connectme

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AgoraCallActivity : AppCompatActivity() {

    private val PERMISSION_REQ_ID = 22
    private var rtcEngine: RtcEngine? = null
    private val APP_ID = "4b15915025e64bf38bf5c886c4a60fc2"  // Your Agora App ID
    private var callId = ""
    private var isVideoCall = false
    private var chatId = ""
    private var remoteUsername = ""
    private var isChannelJoined = false
    private lateinit var callStatusListener: ValueEventListener

    companion object {
        private const val CALL_STATUS_IDLE = 0
        private const val CALL_STATUS_CALLING = 1
        private const val CALL_STATUS_RINGING = 2
        private const val CALL_STATUS_CONNECTED = 3
        private const val CALL_STATUS_ENDED = 4
        private const val TAG = "AgoraCallActivity"
    }

    private val mRtcEventHandler = object : IRtcEngineEventHandler() {
        // Listen for the remote user joining the channel
        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.d(TAG, "Remote user joined: $uid")
            runOnUiThread {
                setupRemoteVideo(uid)
            }
        }

        // Listen for the remote user leaving the channel
        override fun onUserOffline(uid: Int, reason: Int) {
            Log.d(TAG, "Remote user left: $uid, reason: $reason")
            runOnUiThread {
                onRemoteUserLeft()
            }
        }

        // Listen for the local user joining the channel
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.d(TAG, "Successfully joined channel: $channel, uid: $uid")
            runOnUiThread {
                Toast.makeText(this@AgoraCallActivity, "Connected to call", Toast.LENGTH_SHORT).show()
                isChannelJoined = true
            }
        }

        // Listen for connection state changes
        override fun onConnectionStateChanged(state: Int, reason: Int) {
            Log.d(TAG, "Connection state changed: state=$state, reason=$reason")
        }

        // Listen for errors
        override fun onError(err: Int) {
            Log.e(TAG, "Agora error: $err")
            runOnUiThread {
                Toast.makeText(this@AgoraCallActivity, "Call error: $err", Toast.LENGTH_SHORT).show()
                if (err == 17) {
                    // Join channel failed
                    Toast.makeText(this@AgoraCallActivity, "Failed to join call channel", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agora_call)

        Log.d(TAG, "AgoraCallActivity created")

        // Get intent extras
        isVideoCall = intent.getBooleanExtra("IS_VIDEO_CALL", false)
        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        remoteUsername = intent.getStringExtra("REMOTE_USERNAME") ?: ""
        callId = intent.getStringExtra("CALL_ID") ?: ""
        val isIncoming = intent.getBooleanExtra("IS_INCOMING", false)

        Log.d(TAG, "Call parameters: video=$isVideoCall, chatId=$chatId, remote=$remoteUsername, callId=$callId, incoming=$isIncoming")

        if (chatId.isEmpty() || remoteUsername.isEmpty()) {
            Log.e(TAG, "Missing required call parameters")
            Toast.makeText(this, "Missing required call parameters", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Set up UI based on call type
        setupUI()

        // Check permissions before initializing Agora
        if (!checkPermissions()) {
            Log.d(TAG, "Requesting permissions")
            requestPermissions()
        } else {
            Log.d(TAG, "Permissions already granted, initializing Agora")
            initializeAgora()
        }

        // Set up end call button
        findViewById<ImageView>(R.id.btnEndCall).setOnClickListener {
            endCall()
        }

        // Set up mute button
        findViewById<ImageView>(R.id.btnMute).setOnClickListener {
            toggleMute()
        }

        // Set up switch camera button (only for video calls)
        if (isVideoCall) {
            findViewById<ImageView>(R.id.btnSwitchCamera).setOnClickListener {
                switchCamera()
            }
        }

        // Handle call based on whether it's incoming or outgoing
        if (isIncoming && callId.isNotEmpty()) {
            Log.d(TAG, "Handling incoming call")
            handleIncomingCall()
        } else {
            Log.d(TAG, "Initiating outgoing call")
            initiateCall()
        }
    }

    private fun handleIncomingCall() {
        // For incoming calls, we just need to listen for call status changes
        // The call status should already be CONNECTED from IncomingCallActivity
        listenForCallStatusChanges(callId)
    }

    private fun initiateCall() {
        Log.d(TAG, "Initiating call")
        val currentUsername = getSharedPreferences("ConnectMePrefs", Context.MODE_PRIVATE)
            .getString("username", "") ?: ""

        if (currentUsername.isEmpty() || remoteUsername.isEmpty() || chatId.isEmpty()) {
            Log.e(TAG, "Cannot initiate call: Missing information")
            Toast.makeText(this, "Cannot initiate call: Missing information", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Create a unique call ID if not provided
        if (callId.isEmpty()) {
            callId = UUID.randomUUID().toString()
            Log.d(TAG, "Generated new callId: $callId")
        }

        // Get current timestamp
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // Create call data
        val callData = mapOf(
            "callId" to callId,
            "chatId" to chatId,
            "caller" to currentUsername,
            "receiver" to remoteUsername,
            "isVideoCall" to isVideoCall,
            "status" to CALL_STATUS_CALLING,
            "timestamp" to timestamp,
            "channelName" to "channel_$callId" // Use callId for channel name
        )

        // Save call to Firebase
        val database = FirebaseDatabase.getInstance()
        val callsRef = database.getReference("Calls")

        callsRef.child(callId).setValue(callData)
            .addOnSuccessListener {
                Log.d(TAG, "Call data saved to Firebase")

                // Call data saved, now send notification to recipient
                // Note: We're skipping the notification part as requested

                // Listen for call status changes
                listenForCallStatusChanges(callId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save call data: ${e.message}")
                Toast.makeText(this, "Failed to initiate call: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun listenForCallStatusChanges(callId: String) {
        Log.d(TAG, "Setting up listener for call status changes")
        val database = FirebaseDatabase.getInstance()
        val callRef = database.getReference("Calls").child(callId)

        callStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Log.e(TAG, "Call data no longer exists")
                    Toast.makeText(this@AgoraCallActivity, "Call no longer exists", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }

                val status = snapshot.child("status").getValue(Int::class.java) ?: return
                Log.d(TAG, "Call status changed to: $status")

                when (status) {
                    CALL_STATUS_RINGING -> {
                        runOnUiThread {
                            Log.d(TAG, "Call is ringing")
                            Toast.makeText(this@AgoraCallActivity, "Ringing...", Toast.LENGTH_SHORT).show()
                        }
                    }
                    CALL_STATUS_CONNECTED -> {
                        runOnUiThread {
                            Log.d(TAG, "Call is connected")
                            if (!isChannelJoined) {
                                val channelName = snapshot.child("channelName").getValue(String::class.java)
                                    ?: "channel_$callId"
                                Log.d(TAG, "Joining channel: $channelName")
                                joinChannel(channelName)
                            }
                        }
                    }
                    CALL_STATUS_ENDED -> {
                        runOnUiThread {
                            Log.d(TAG, "Call has ended")
                            Toast.makeText(this@AgoraCallActivity, "Call ended", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error monitoring call status: ${error.message}")
                Toast.makeText(this@AgoraCallActivity,
                    "Failed to monitor call: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }

        callRef.addValueEventListener(callStatusListener)
    }

    private fun setupUI() {
        Log.d(TAG, "Setting up UI for ${if (isVideoCall) "video" else "audio"} call")
        // Show/hide video containers based on call type
        val localVideoContainer = findViewById<FrameLayout>(R.id.local_video_view_container)
        val remoteVideoContainer = findViewById<FrameLayout>(R.id.remote_video_view_container)

        if (isVideoCall) {
            localVideoContainer.visibility = View.VISIBLE
            remoteVideoContainer.visibility = View.VISIBLE
            findViewById<ImageView>(R.id.btnSwitchCamera).visibility = View.VISIBLE
        } else {
            localVideoContainer.visibility = View.GONE
            remoteVideoContainer.visibility = View.GONE
            findViewById<ImageView>(R.id.btnSwitchCamera).visibility = View.GONE
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = if (isVideoCall) {
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            )
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }

        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun requestPermissions() {
        val permissions = if (isVideoCall) {
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            )
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }

        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQ_ID)
    }

    private fun initializeAgora() {
        try {
            Log.d(TAG, "Initializing Agora SDK")
            // Create RtcEngine instance
            rtcEngine = RtcEngine.create(this, APP_ID, mRtcEventHandler)

            // Check if rtcEngine was created successfully
            if (rtcEngine == null) {
                Log.e(TAG, "Failed to create RtcEngine instance")
                Toast.makeText(this, "Failed to initialize call service", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            // Enable video if it's a video call
            if (isVideoCall) {
                Log.d(TAG, "Enabling video")
                rtcEngine?.enableVideo()
                setupLocalVideo()
            } else {
                Log.d(TAG, "Audio only call")
                rtcEngine?.enableAudio()
            }

            Log.d(TAG, "Agora SDK initialized successfully")
            Toast.makeText(this, "Call connecting...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Agora SDK initialization failed: ${e.message}")
            Toast.makeText(this, "Failed to initialize call: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupLocalVideo() {
        try {
            Log.d(TAG, "Setting up local video")
            // Create a SurfaceView for local video
            val localFrame = findViewById<FrameLayout>(R.id.local_video_view_container)
            localFrame.removeAllViews()

            val localView = SurfaceView(baseContext)
            localView.setZOrderMediaOverlay(true)
            localFrame.addView(localView)

            // Set up local video to render your camera preview
            rtcEngine?.setupLocalVideo(VideoCanvas(localView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up local video: ${e.message}")
        }
    }

    private fun setupRemoteVideo(uid: Int) {
        try {
            Log.d(TAG, "Setting up remote video for uid: $uid")
            // Create a SurfaceView for remote video
            val remoteFrame = findViewById<FrameLayout>(R.id.remote_video_view_container)
            remoteFrame.removeAllViews()

            val remoteView = SurfaceView(baseContext)
            remoteFrame.addView(remoteView)

            // Set up remote video to render the remote user's camera
            rtcEngine?.setupRemoteVideo(VideoCanvas(remoteView, VideoCanvas.RENDER_MODE_HIDDEN, uid))

            Toast.makeText(this, "Remote user joined", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up remote video: ${e.message}")
        }
    }

    private fun onRemoteUserLeft() {
        Log.d(TAG, "Remote user left the call")
        // Handle remote user leaving - typically end the call
        Toast.makeText(this, "Remote user left the call", Toast.LENGTH_SHORT).show()
        endCall()
    }

    private fun joinChannel(channelName: String) {
        try {
            Log.d(TAG, "Joining channel: $channelName")

            // Set channel options based on call type
            val options = ChannelMediaOptions()
            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER

            // Enable audio
            rtcEngine?.enableAudio()

            // For video calls, make sure video is enabled
            if (isVideoCall) {
                rtcEngine?.enableVideo()
            }

            // Join the channel
            val result = rtcEngine?.joinChannel("", channelName, 0, options)

            if (result != 0) {
                // Join channel failed
                Log.e(TAG, "Join channel failed with error code: $result")
                Toast.makeText(this, "Failed to join call channel", Toast.LENGTH_SHORT).show()
            } else {
                Log.d(TAG, "Join channel command sent successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception when joining channel: ${e.message}")
            Toast.makeText(this, "Error joining call: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun endCall() {
        Log.d(TAG, "Ending call")
        // Update call status in Firebase
        val database = FirebaseDatabase.getInstance()
        val callRef = database.getReference("Calls").child(callId)

        callRef.child("status").setValue(CALL_STATUS_ENDED)
            .addOnSuccessListener {
                Log.d(TAG, "Call status updated to ENDED")
                rtcEngine?.leaveChannel()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update call status: ${e.message}")
                Toast.makeText(this, "Failed to end call: ${e.message}", Toast.LENGTH_SHORT).show()
                rtcEngine?.leaveChannel()
                finish()
            }
    }

    private fun toggleMute() {
        val btnMute = findViewById<ImageView>(R.id.btnMute)
        val isMuted = rtcEngine?.adjustRecordingSignalVolume(0) == 0

        if (isMuted) {
            // Unmute
            Log.d(TAG, "Unmuting audio")
            rtcEngine?.adjustRecordingSignalVolume(100)
            btnMute.setImageResource(R.drawable.mute_icon)
            Toast.makeText(this, "Microphone unmuted", Toast.LENGTH_SHORT).show()
        } else {
            // Mute
            Log.d(TAG, "Muting audio")
            rtcEngine?.adjustRecordingSignalVolume(0)
            btnMute.setImageResource(R.drawable.phone_icon)
            Toast.makeText(this, "Microphone muted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchCamera() {
        Log.d(TAG, "Switching camera")
        rtcEngine?.switchCamera()
        Toast.makeText(this, "Camera switched", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQ_ID) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "All permissions granted")
                initializeAgora()
            } else {
                Log.e(TAG, "Permissions denied")
                Toast.makeText(this, "Permissions are required for the call", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AgoraCallActivity being destroyed")

        // Remove Firebase listeners
        if (::callStatusListener.isInitialized && callId.isNotEmpty()) {
            try {
                val database = FirebaseDatabase.getInstance()
                val callRef = database.getReference("Calls").child(callId)
                callRef.removeEventListener(callStatusListener)
                Log.d(TAG, "Call status listener removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing call status listener: ${e.message}")
            }
        }

        // Leave channel and destroy RtcEngine
        try {
            rtcEngine?.leaveChannel()
            Log.d(TAG, "Left Agora channel")
        } catch (e: Exception) {
            Log.e(TAG, "Error leaving channel: ${e.message}")
        }

        try {
            RtcEngine.destroy()
            rtcEngine = null
            Log.d(TAG, "RtcEngine destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying RtcEngine: ${e.message}")
        }
    }
}