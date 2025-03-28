package com.example.connectme
import android.content.Intent
import android.os.Bundle
import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.util.Log
// Add these imports at the top of your file
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

import android.content.pm.PackageManager
import android.database.Cursor
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.ImageButton

import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.bumptech.glide.Glide
import android.view.GestureDetector
import android.view.ViewGroup
import android.widget.ProgressBar

// Add these imports at the top of your file if not already present
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import java.util.Timer
import java.util.TimerTask




class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private var selectedImageUri: android.net.Uri? = null
    private lateinit var sharedPreferences: SharedPreferences

    // Add these properties to your MainActivity class
    private var rtcEngine: RtcEngine? = null
    private var isJoined = false
    private var isMuted = false
    private var isSpeakerOn = false
    private var callTimer: Timer? = null
    private var callDurationInSeconds = 0

    // Add your Agora App ID - you should get this from your Agora Console
    private val appId = "7ec9993cf23a4d65b8450b9c4e6a6229" // Replace with your actual Agora App ID
    private val channelName = "call_channel" // This can be dynamic based on the chat


    companion object {
        private const val PREF_NAME = "ConnectMePrefs"
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_USERNAME = "username"
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHONE = "phone"
        private const val KEY_BIO = "bio"  // Add this for bio
        private const val PROFILE_IMAGE_REQUEST = 3  // Add this for profile image selection
        private const val CHAT_PARTNER_USERNAME = "chat_partner_username"
        private const val CHAT_PARTNER_NAME = "chat_partner_name"
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        setContentView(R.layout.activity_main) // Screen 1 layout

        // Check for expired stories
        cleanupExpiredStories()
        showScreen2()
//        if (isLoggedIn()) {
//            // Skip to home screen
//            Handler(Looper.getMainLooper()).postDelayed({
//                showScreen4()
//            }, 1000)
//        } else {
//            // Show login screen after splash
//            Handler(Looper.getMainLooper()).postDelayed({
//                showScreen2()
//            }, 3000)
//        }
    }

    private fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    private fun saveUserCredentials(username: String, name: String, email: String, phone: String, bio: String = "") {
        val editor = sharedPreferences.edit()
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.putString(KEY_USERNAME, username)
        editor.putString(KEY_NAME, name)
        editor.putString(KEY_EMAIL, email)
        editor.putString(KEY_PHONE, phone)
        editor.putString(KEY_BIO, bio)
        editor.apply()
    }

    // Clear user credentials on logout
    private fun clearUserCredentials() {
        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply()
    }
    // Update the startChatWithUser function to open the chat screen
    private fun startChatWithUser(username: String) {
        // Get user info to display in chat
        getUserInfo(username) { name, profilePicUrl ->
            // Create or get chat ID
            val currentUsername = sharedPreferences.getString(KEY_USERNAME, "") ?: ""
            createOrGetChatId(currentUsername, username) { chatId ->
                // Store chat partner info temporarily
                val editor = sharedPreferences.edit()
                editor.putString(CHAT_PARTNER_USERNAME, username)
                editor.putString(CHAT_PARTNER_NAME, name)
                editor.apply()

                // Open chat screen
                showScreen6(chatId, username, name, profilePicUrl)
            }
        }
    }

    // Function to create or get a chat ID between two users
    private fun createOrGetChatId(user1: String, user2: String, callback: (String) -> Unit) {
        // Sort usernames to ensure consistent chat ID regardless of who initiates
        val sortedUsers = listOf(user1, user2).sorted()
        val chatId = "${sortedUsers[0]}_${sortedUsers[1]}"

        // Check if chat exists in Firebase
        val database = FirebaseDatabase.getInstance()
        val chatsRef = database.getReference("Chats")

        chatsRef.child(chatId).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                // Create new chat entry
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val chatData = mapOf(
                    "participants" to sortedUsers,
                    "created_at" to timestamp,
                    "last_message" to "",
                    "last_message_time" to timestamp
                )

                chatsRef.child(chatId).setValue(chatData)
                    .addOnSuccessListener {
                        callback(chatId)
                    }
                    .addOnFailureListener { e ->
                        Log.e("CreateChat", "Error creating chat: ${e.message}")
                        callback(chatId) // Still proceed with the chat ID
                    }
            } else {
                // Chat already exists
                callback(chatId)
            }
        }.addOnFailureListener { e ->
            Log.e("CreateChat", "Error checking chat: ${e.message}")
            callback(chatId) // Still proceed with the chat ID
        }
    }


    private fun showScreen2() {
        setContentView(R.layout.screen2)

        val registerText = findViewById<TextView>(R.id.secondText)
        registerText.setOnClickListener {
            showScreen3()
        }

        val loginButton = findViewById<Button>(R.id.loginButton)
        loginButton.setOnClickListener {
            val usernameInput = findViewById<EditText>(R.id.username).text.toString()
            val passwordInput = findViewById<EditText>(R.id.passwordtext).text.toString()

            if (usernameInput.isEmpty() || passwordInput.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            } else {
                loginUser(usernameInput, passwordInput)
            }
        }
    }

    private fun loginUser(username: String, password: String) {
        val database = FirebaseDatabase.getInstance()
        val usersRef = database.getReference("Users")

        usersRef.child(username).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val dbPassword = snapshot.child("password").value.toString()
                if (dbPassword == password) {
                    // Get user data from database
                    val name = snapshot.child("name").value.toString()
                    val email = snapshot.child("email").value.toString()
                    val phone = snapshot.child("phone").value.toString()

                    // Save credentials to SharedPreferences
                    saveUserCredentials(username, name, email, phone)

                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                    showScreen4() // Navigate to home screen
                } else {
                    Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Username not found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Login failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showScreen3() {
        setContentView(R.layout.screen3) // Screen 3 layout

        val loginText = findViewById<TextView>(R.id.secondText)
        loginText.setOnClickListener {
            showScreen2()
        }

        val registerButton = findViewById<Button>(R.id.registerButton)
        registerButton.setOnClickListener {
            val name = findViewById<EditText>(R.id.nameInput).text.toString()
            val username = findViewById<EditText>(R.id.usernameInput).text.toString()
            val phonenumber = findViewById<EditText>(R.id.phoneInput).text.toString()
            val email = findViewById<EditText>(R.id.emailInput).text.toString()
            val password = findViewById<EditText>(R.id.passwordInput).text.toString()

            signUpUser(name, username, phonenumber, email, password)
        }
    }

    private fun signUpUser(name: String, username: String, phone: String, email: String, password: String) {
        if (email.isEmpty() || password.isEmpty() || name.isEmpty() || username.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val database = FirebaseDatabase.getInstance()
        val usersRef = database.getReference("Users")

        // Check if username already exists
        usersRef.child(username).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                Toast.makeText(this, "Username already exists", Toast.LENGTH_SHORT).show()
            } else {
                // Save user data
                val userMap = mapOf(
                    "name" to name,
                    "username" to username,
                    "phone" to phone,
                    "email" to email,
                    "password" to password // In production, hash the password!
                )
                usersRef.child(username).setValue(userMap)
                    .addOnSuccessListener {
                        // Save credentials to SharedPreferences
                        saveUserCredentials(username, name, email, phone)

                        Toast.makeText(this, "Signup successful", Toast.LENGTH_SHORT).show()
                        showScreen4() // Take them directly to home screen after registration
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Signup failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun logout() {
        clearUserCredentials()
        showScreen2() // Go back to login screen
    }
    private fun showScreen4() {
        setContentView(R.layout.screen4) // Screen 4 layout

        val messagesButton = findViewById<ImageView>(R.id.MessagesButtonm)
        messagesButton.setOnClickListener {
            showScreen5()
        }

        val addStory = findViewById<ImageButton>(R.id.AddStory)
        addStory.setOnClickListener {
            val username = sharedPreferences.getString(KEY_USERNAME, null)
            if (username != null) {
                val intent = Intent(this, Screen16Activity::class.java)
                intent.putExtra("USERNAME", username)
                startActivity(intent)
            } else {
                Toast.makeText(this, "You need to be logged in to add a story", Toast.LENGTH_SHORT).show()
            }
        }

        val gotoHome = findViewById<ImageButton>(R.id.HomePage)
        gotoHome.setOnClickListener {
            showScreen4()
        }
        val gotoSearch = findViewById<ImageButton>(R.id.SearchPage)
        gotoSearch.setOnClickListener {
            showScreen14()
        }
        val gotoPost = findViewById<ImageButton>(R.id.PostPage)
        gotoPost.setOnClickListener {
            showScreen15()
        }
        val gotoProfile = findViewById<ImageButton>(R.id.ProfilePage)
        gotoProfile.setOnClickListener {
            showScreen10()
        }
        val gotoContacts = findViewById<ImageButton>(R.id.ContactsPage)
        gotoContacts.setOnClickListener {
            showScreen18()
        }

        // Get current username
        val currentUsername = sharedPreferences.getString(KEY_USERNAME, "") ?: ""
        if (currentUsername.isEmpty()) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Load stories from followed users
        loadFollowingStories(currentUsername)

        // Load posts from followed users
        loadFollowingPosts(currentUsername)
    }

    private fun cleanupExpiredStories() {
        val database = FirebaseDatabase.getInstance()
        val storiesRef = database.getReference("stories")

        storiesRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (!snapshot.exists()) {
                    return
                }

                val currentTime = System.currentTimeMillis()
                val oneDayInMillis = 24 * 60 * 60 * 1000

                for (userSnapshot in snapshot.children) {
                    val username = userSnapshot.key ?: continue
                    val storyTimestamp = userSnapshot.child("timestamp").value as? Long ?: 0

                    // Check if story is older than 24 hours
                    val storyAge = currentTime - storyTimestamp
                    if (storyAge > oneDayInMillis) {
                        // Delete expired story
                        deleteExpiredStory(username)
                    }
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e("CleanupStories", "Error checking stories: ${error.message}")
            }
        })
    }

    // Function to delete expired stories
    private fun deleteExpiredStory(username: String) {
        val database = FirebaseDatabase.getInstance()
        val storyRef = database.getReference("stories").child(username)

        storyRef.removeValue()
            .addOnSuccessListener {
                Log.d("DeleteStory", "Expired story deleted for user: $username")
            }
            .addOnFailureListener { e ->
                Log.e("DeleteStory", "Error deleting expired story: ${e.message}")
            }
    }

    // Function to load stories from followed users
    private fun loadFollowingStories(currentUsername: String) {
        // Get reference to the stories container
        val storiesContainer = findViewById<HorizontalScrollView>(R.id.storiesSection)?.getChildAt(0) as? LinearLayout
        if (storiesContainer == null) {
            Log.e("Stories", "Stories container not found")
            return
        }

        // Clear existing stories except the first one (which is the user's own story)
        if (storiesContainer.childCount > 1) {
            storiesContainer.removeViews(1, storiesContainer.childCount - 1)
        }

        // Get reference to Firebase database
        val database = FirebaseDatabase.getInstance()
        val followingRef = database.getReference("Following").child(currentUsername)

        // Get list of users that the current user follows
        followingRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                    // Not following anyone, no stories to show
                    return
                }

                // Get list of followed usernames
                val followedUsers = ArrayList<String>()
                for (userSnapshot in snapshot.children) {
                    val username = userSnapshot.key ?: continue
                    followedUsers.add(username)
                }

                // Now fetch stories from these users
                fetchStoriesFromUsers(followedUsers, storiesContainer)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e("LoadFollowingStories", "Error loading following: ${error.message}")
            }
        })
    }

    // Function to fetch stories from followed users
    private fun fetchStoriesFromUsers(usernames: List<String>, container: LinearLayout) {
        // Get reference to Firebase database
        val database = FirebaseDatabase.getInstance()
        val storiesRef = database.getReference("stories")

        // For each username, check if they have a story
        for (username in usernames) {
            storiesRef.child(username).addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (!snapshot.exists()) {
                        // No story for this user
                        return
                    }

                    // Get story data
                    val storyImage = snapshot.child("image").value as? String
                    val storyTimestamp = snapshot.child("timestamp").value as? Long ?: 0

                    // Check if story is less than 24 hours old
                    val currentTime = System.currentTimeMillis()
                    val storyAge = currentTime - storyTimestamp
                    val oneDayInMillis = 24 * 60 * 60 * 1000

                    if (storyAge > oneDayInMillis) {
                        // Story is older than 24 hours, don't show it
                        return
                    }

                    // Get user info for the story author
                    getUserInfo(username) { name, profilePicUrl ->
                        // Create story view
                        val storyView = createStoryView(username, name, profilePicUrl, storyImage)
                        container.addView(storyView)
                    }
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Log.e("FetchStories", "Error fetching story for $username: ${error.message}")
                }
            })
        }
    }

    // Function to create a story view
    private fun createStoryView(username: String, name: String, profilePicUrl: String, storyImage: String?): View {
        // Create story image view
        val storyImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(80.dpToPx(), 80.dpToPx()).apply {
                marginEnd = 10.dpToPx()
            }
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.story_circle)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
        }

        // Load profile image if available
        if (profilePicUrl.isNotEmpty()) {
            Glide.with(this)
                .load(profilePicUrl)
                .centerCrop()
                .into(storyImageView)
        } else {
            // Use a default profile image
            storyImageView.setImageResource(R.drawable.profilepic1)
        }

        // Set click listener to view story
        storyImageView.setOnClickListener {
            viewStory(username, name, storyImage)
        }

        return storyImageView
    }

    // Function to view a story
    private fun viewStory(username: String, name: String, storyImage: String?) {
        if (storyImage.isNullOrEmpty()) {
            Toast.makeText(this, "Story not available", Toast.LENGTH_SHORT).show()
            return
        }

        // Create a dialog to show the story
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.screen20storyview)

        // Get views from the dialog
        val storyImageView = dialog.findViewById<ImageView>(R.id.storyImage)
        val usernameTextView = dialog.findViewById<TextView>(R.id.storyUsername)
        val progressBar = dialog.findViewById<ProgressBar>(R.id.storyProgress)

        // Set username
        usernameTextView.text = name

        // Load story image
        try {
            val imageBytes = android.util.Base64.decode(storyImage, android.util.Base64.DEFAULT)
            val decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            storyImageView.setImageBitmap(decodedImage)
        } catch (e: Exception) {
            Log.e("ViewStory", "Error decoding image: ${e.message}")
            storyImageView.setImageResource(R.drawable.profilepic1) // Default image
        }

        // Show the dialog
        dialog.show()

        // Animate progress bar
        val progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", 0, 100)
        progressAnimator.duration = 5000 // 5 seconds
        progressAnimator.start()

        // Close the dialog after 5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }, 5000) // 5 seconds
    }



    // Function to load posts from users that the current user follows
    private fun loadFollowingPosts(currentUsername: String) {
        // Get reference to the posts container
        val postsContainer = findViewById<LinearLayout>(R.id.postsContainer)

        // Clear existing posts
        postsContainer.removeAllViews()

        // Show loading indicator
        val loadingText = TextView(this).apply {
            text = "Loading posts..."
            textSize = 16f
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            gravity = Gravity.CENTER
        }
        postsContainer.addView(loadingText)

        // Get reference to Firebase database
        val database = FirebaseDatabase.getInstance()
        val followingRef = database.getReference("Following").child(currentUsername)

        // Get list of users that the current user follows
        followingRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                    // Not following anyone
                    postsContainer.removeAllViews()
                    val noFollowingText = TextView(this@MainActivity).apply {
                        text = "You're not following anyone yet. Follow some users to see their posts here!"
                        textSize = 16f
                        setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                        gravity = Gravity.CENTER
                        setTextColor(android.graphics.Color.GRAY)
                    }
                    postsContainer.addView(noFollowingText)
                    return
                }

                // Get list of followed usernames
                val followedUsers = ArrayList<String>()
                for (userSnapshot in snapshot.children) {
                    val username = userSnapshot.key ?: continue
                    followedUsers.add(username)
                }

                // Add current user to see their own posts too
                if (!followedUsers.contains(currentUsername)) {
                    followedUsers.add(currentUsername)
                }

                // Now fetch posts from these users
                fetchPostsFromUsers(followedUsers, postsContainer)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e("LoadFollowingPosts", "Error loading following: ${error.message}")

                // Show error message
                postsContainer.removeAllViews()
                val errorText = TextView(this@MainActivity).apply {
                    text = "Error loading posts: ${error.message}"
                    textSize = 16f
                    setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                    setTextColor(android.graphics.Color.RED)
                }
                postsContainer.addView(errorText)
            }
        })
    }

    // Function to fetch posts from a list of users
    private fun fetchPostsFromUsers(usernames: List<String>, container: LinearLayout) {
        // Clear container except for loading indicator
        if (container.childCount > 1) {
            container.removeViews(1, container.childCount - 1)
        }

        // Get reference to Firebase database
        val database = FirebaseDatabase.getInstance()
        val postsRef = database.getReference("Posts")

        // List to store all posts
        val allPosts = ArrayList<Map<String, Any>>()

        // Counter to track how many queries have completed
        var completedQueries = 0

        // For each username, fetch their posts
        for (username in usernames) {
            postsRef.orderByChild("username").equalTo(username)
                .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        // Process posts from this user
                        for (postSnapshot in snapshot.children) {
                            val postId = postSnapshot.key ?: continue
                            val post = postSnapshot.value as? Map<*, *> ?: continue

                            // Convert to mutable map and add postId
                            val postMap = post.toMutableMap()
                            postMap["postId"] = postId

                            // Add to our list of all posts
                            @Suppress("UNCHECKED_CAST")
                            allPosts.add(postMap as Map<String, Any>)
                        }

                        // Increment completed queries counter
                        completedQueries++

                        // If all queries are complete, display the posts
                        if (completedQueries >= usernames.size) {
                            displayPosts(allPosts, container)
                        }
                    }

                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        Log.e("FetchPosts", "Error fetching posts for $username: ${error.message}")

                        // Increment completed queries counter even on error
                        completedQueries++

                        // If all queries are complete, display the posts we have
                        if (completedQueries >= usernames.size) {
                            displayPosts(allPosts, container)
                        }
                    }
                })
        }
    }

    // Function to display posts in the UI
    private fun displayPosts(posts: List<Map<String, Any>>, container: LinearLayout) {
        // Remove loading indicator
        container.removeAllViews()

        // If no posts, show message
        if (posts.isEmpty()) {
            val noPostsText = TextView(this).apply {
                text = "No posts to show. Follow users or create your own posts!"
                textSize = 16f
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.GRAY)
            }
            container.addView(noPostsText)
            return
        }

        // Sort posts by timestamp (most recent first)
        val sortedPosts = posts.sortedByDescending { it["timestamp"] as? String }

        // Display each post
        for (post in sortedPosts) {
            val username = post["username"] as? String ?: continue
            val caption = post["caption"] as? String ?: ""
            val timestamp = post["timestamp"] as? String ?: ""
            val postId = post["postId"] as? String ?: ""
            val imagesList = post["images"] as? ArrayList<*> ?: continue

            // Get user info for the post author
            getUserInfo(username) { name, profilePicUrl ->
                // Create post view
                val postView = createPostView(postId, username, name, profilePicUrl, imagesList, caption, timestamp)
                container.addView(postView)
            }
        }
    }

    // Function to create a post view
    private fun createPostView(postId: String, username: String, name: String, profilePicUrl: String,
                               imagesList: ArrayList<*>, caption: String, timestamp: String): View {
        // Create the post layout
        val postLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Add divider at the top
        val topDivider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2.dpToPx()
            )
            setBackgroundColor(android.graphics.Color.parseColor("#CCCCCC"))
        }
        postLayout.addView(topDivider)

        // Create user info section
        val userInfoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
        }

        // Profile image
        val profileImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(40.dpToPx(), 40.dpToPx())
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.story_circle)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            setImageResource(R.drawable.profilepic1) // Default image
        }

        // Load profile image if available
        if (profilePicUrl.isNotEmpty()) {
            Glide.with(this)
                .load(profilePicUrl)
                .centerCrop()
                .into(profileImageView)
        }

        // Username text
        val usernameTextView = TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(android.graphics.Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            setPadding(10.dpToPx(), 0, 0, 0)
        }

        // More options button
        val moreButton = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx())
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            setImageResource(R.drawable.three_dots_icon)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = null
        }

        // Add views to user info layout
        userInfoLayout.addView(profileImageView)
        userInfoLayout.addView(usernameTextView)
        userInfoLayout.addView(moreButton)

        // Add user info layout to post layout
        postLayout.addView(userInfoLayout)

        // Create post image view
        if (imagesList.isNotEmpty()) {
            val base64Image = imagesList[0] as? String ?: ""
            if (base64Image.isNotEmpty()) {
                try {
                    // Convert base64 to bitmap
                    val imageBytes = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT)
                    val decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                    // Create image view
                    val postImageView = ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            400.dpToPx()
                        ).apply {
                            topMargin = 8.dpToPx()
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageBitmap(decodedImage)
                    }

                    // Add double-tap gesture for liking
                    postImageView.setOnTouchListener(object : View.OnTouchListener {
                        private val gestureDetector = GestureDetector(this@MainActivity, object : GestureDetector.SimpleOnGestureListener() {
                            override fun onDoubleTap(e: MotionEvent): Boolean {
                                // Like the post on double tap
                                likePost(postId, username)
                                return true
                            }
                        })

                        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                            return event?.let { gestureDetector.onTouchEvent(it) } ?: false
                        }
                    })

                    postLayout.addView(postImageView)
                } catch (e: Exception) {
                    Log.e("CreatePostView", "Error decoding image: ${e.message}")
                }
            }
        }

        // Create action buttons layout
        val actionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                40.dpToPx()
            )
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
        }

        // Like button
        val likeButton = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx())
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            setImageResource(R.drawable.like_icon)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = null
        }

        // Set click listener for like button
        likeButton.setOnClickListener {
            likePost(postId, username)
        }

        // Comment button
        val commentButton = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx())
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            setImageResource(R.drawable.comment_icon)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = null
        }

        // Set click listener for comment button
        commentButton.setOnClickListener {
            showCommentDialog(postId, username)
        }

        // Share button
        val shareButton = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx())
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            setImageResource(R.drawable.send)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = null
        }

        // Spacer
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                0,
                1.0f
            )
        }

        // Bookmark button
        val bookmarkButton = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx())
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            setImageResource(R.drawable.bookmark_icon)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = null
        }

        // Add buttons to actions layout
        actionsLayout.addView(likeButton)
        actionsLayout.addView(commentButton)
        actionsLayout.addView(shareButton)
        actionsLayout.addView(spacer)
        actionsLayout.addView(bookmarkButton)

        // Add actions layout to post layout
        postLayout.addView(actionsLayout)

        // Add divider
        val middleDivider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2.dpToPx()
            )
            setBackgroundColor(android.graphics.Color.parseColor("#CCCCCC"))

        }
        postLayout.addView(middleDivider)

        // Add caption
        if (caption.isNotEmpty()) {
            val captionTextView = TextView(this).apply {
                text = caption
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
            }
            postLayout.addView(captionTextView)
        }

        // Add bottom divider
        val bottomDivider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2.dpToPx()
            )
            setBackgroundColor(android.graphics.Color.parseColor("#CCCCCC"))

        }
        postLayout.addView(bottomDivider)

        return postLayout
    }

    private fun showScreen5() {
        setContentView(R.layout.screen5)

        // Back button
        val backButton = findViewById<ImageView>(R.id.BackButtonm)
        backButton.setOnClickListener {
            showScreen4()
        }

        // Requests tab
        val requestsTab = findViewById<LinearLayout>(R.id.requests_container)
        requestsTab.setOnClickListener {
            showScreen19()
        }

        // Get current username
        val currentUsername = sharedPreferences.getString(KEY_USERNAME, "") ?: ""
        if (currentUsername.isEmpty()) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Get chat container
        val chatContainer = findViewById<LinearLayout>(R.id.chatContainer)

        // Load chats
        loadChats(currentUsername, chatContainer)

        // Set up search functionality
        val searchEditText = findViewById<EditText>(R.id.searchEditText)
        val searchButton = findViewById<ImageView>(R.id.searchButton)

        searchButton?.setOnClickListener {
            val query = searchEditText?.text.toString().trim()
            if (query.isNotEmpty()) {
                // Filter chats
                filterChats(query, currentUsername, chatContainer)
            } else {
                // Reload all chats
                loadChats(currentUsername, chatContainer)
            }
        }
    }

    // Function to load chats from Firebase
    private fun loadChats(username: String, container: LinearLayout) {
        // Clear existing views
        container.removeAllViews()

        // Show loading indicator
        val loadingText = TextView(this).apply {
            text = "Loading chats..."
            textSize = 16f
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
        }
        container.addView(loadingText)

        // Get reference to Firebase database
        val database = FirebaseDatabase.getInstance()
        val chatsRef = database.getReference("Chats")

        // Query for chats where this user is a participant
        chatsRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                // Clear the container
                container.removeAllViews()

                if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                    // No chats found
                    val noChatsText = TextView(this@MainActivity).apply {
                        text = "No chats yet. Start a conversation with someone!"
                        textSize = 16f
                        setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                        setTextColor(android.graphics.Color.GRAY)
                    }
                    container.addView(noChatsText)
                    return
                }

                // Process each chat
                val chatsList = ArrayList<Map<String, Any>>()

                for (chatSnapshot in snapshot.children) {
                    val chatId = chatSnapshot.key ?: continue
                    val chatData = chatSnapshot.value as? Map<*, *> ?: continue

                    // Get participants
                    val participants = chatData["participants"] as? List<*> ?: continue
                    if (!participants.contains(username)) continue

                    // Find the other participant (not the current user)
                    val otherUser = participants.find { it != username } as? String ?: continue

                    // Get last message and time
                    val lastMessage = chatData["last_message"] as? String ?: ""
                    val lastMessageTime = chatData["last_message_time"] as? String ?: ""

                    // Create a map with chat info
                    val chatInfo = mapOf(
                        "chatId" to chatId,
                        "otherUser" to otherUser,
                        "lastMessage" to lastMessage,
                        "lastMessageTime" to lastMessageTime
                    )

                    chatsList.add(chatInfo)
                }

                // Sort chats by last message time (most recent first)
                chatsList.sortByDescending { it["lastMessageTime"] as String }

                // Display each chat
                for (chatInfo in chatsList) {
                    val otherUsername = chatInfo["otherUser"] as String
                    val chatId = chatInfo["chatId"] as String
                    val lastMessage = chatInfo["lastMessage"] as String

                    // Get user info for the other participant
                    getUserInfo(otherUsername) { name, profilePicUrl ->
                        // Create chat item view
                        val chatView = createChatItemView(chatId, otherUsername, name, profilePicUrl, lastMessage)
                        container.addView(chatView)
                    }
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e("Chats", "Error loading chats: ${error.message}")

                // Show error message
                container.removeAllViews()
                val errorText = TextView(this@MainActivity).apply {
                    text = "Error loading chats: ${error.message}"
                    textSize = 16f
                    setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                    setTextColor(android.graphics.Color.RED)
                }
                container.addView(errorText)
            }
        })
    }

    // Update the createChatItemView function to include the last message:

    // Function to create a chat item view
    private fun createChatItemView(chatId: String, username: String, name: String, profilePicUrl: String, lastMessage: String = ""): View {
        // Create a horizontal layout for the chat item
        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Create profile image
        val profileImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx())
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.story_circle)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
        }

        // Load profile image
        if (profilePicUrl.isNotEmpty()) {
            Glide.with(this)
                .load(profilePicUrl)
                .centerCrop()
                .into(profileImageView)
        } else {
            // Use a default profile image
            profileImageView.setImageResource(R.drawable.profilepic1)
        }

        // Create a vertical layout for name and last message
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            setPadding(12.dpToPx(), 0, 0, 0)
        }

        // Create text view for the name
        val nameTextView = TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(android.graphics.Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        // Create text view for the last message
        val lastMessageTextView = TextView(this).apply {
            text = if (lastMessage.isNotEmpty()) lastMessage else "No messages yet"
            textSize = 14f
            setTextColor(android.graphics.Color.GRAY)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        // Add text views to the container
        textContainer.addView(nameTextView)
        textContainer.addView(lastMessageTextView)

        // Create camera icon
        val cameraIcon = ImageView(this).apply {
            setImageResource(R.drawable.camera_icon)
            layoutParams = LinearLayout.LayoutParams(
                32.dpToPx(),
                32.dpToPx()
            )
        }

        // Add views to the main layout
        itemLayout.addView(profileImageView)
        itemLayout.addView(textContainer)
        itemLayout.addView(cameraIcon)

        // Set click listener to open chat
        itemLayout.setOnClickListener {
            // Store chat partner info temporarily
            val editor = sharedPreferences.edit()
            editor.putString(CHAT_PARTNER_USERNAME, username)
            editor.putString(CHAT_PARTNER_NAME, name)
            editor.apply()

            // Open chat screen
            showScreen6(chatId, username, name, profilePicUrl)
        }

        return itemLayout
    }

    // Function to filter chats by search query
    private fun filterChats(query: String, username: String, container: LinearLayout) {
        // This is a simple client-side filtering
        // For a real app, you might want to do this filtering on the server

        // Get all chat items
        val chatItems = ArrayList<View>()
        for (i in 0 until container.childCount) {
            chatItems.add(container.getChildAt(i))
        }

        // Clear container
        container.removeAllViews()

        // Filter and add matching items
        var matchFound = false
        for (item in chatItems) {
            if (item is LinearLayout) {
                // Find the name TextView (second child)
                val nameTextView = item.getChildAt(1) as? TextView
                val name = nameTextView?.text?.toString() ?: ""

                if (name.contains(query, ignoreCase = true)) {
                    container.addView(item)
                    matchFound = true
                }
            }
        }

        // Show no results message if needed
        if (!matchFound) {
            val noResultsText = TextView(this).apply {
                text = "No matching chats found"
                textSize = 16f
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                setTextColor(android.graphics.Color.GRAY)
            }
            container.addView(noResultsText)
        }
    }

    private fun showScreen19() {
        setContentView(R.layout.screen19request)

        // Get references to UI elements
        val backButton = findViewById<ImageView>(R.id.BackButtonm)
        val dmsTab = findViewById<LinearLayout>(R.id.dms_container)
        val requestsTab = findViewById<LinearLayout>(R.id.requests_container)
        val searchEditText = findViewById<EditText>(R.id.searchEditText)
        val searchButton = findViewById<ImageView>(R.id.searchButton)
        val requestsContainer = findViewById<LinearLayout>(R.id.requestsContainer)

        // Set up back button
        backButton.setOnClickListener {
            showScreen4()
        }

        // Set up tab navigation
        dmsTab.setOnClickListener {
            showScreen5() // Navigate to DMs screen
        }

        // Load follow requests
        loadFollowRequests(requestsContainer)

        // Set up search functionality if needed
        searchButton.setOnClickListener {
            val query = searchEditText.text.toString().trim()
            if (query.isNotEmpty()) {
                // Filter requests by query
                filterRequests(query, requestsContainer)
            } else {
                // Reload all requests
                loadFollowRequests(requestsContainer)
            }
        }
    }


    // Function to load follow requests from Firebase
    private fun loadFollowRequests(container: LinearLayout) {
        // Clear existing views
        container.removeAllViews()

        // Show loading indicator
        val loadingText = TextView(this).apply {
            text = "Loading requests..."
            textSize = 16f
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
        }
        container.addView(loadingText)

        // Get current user's username
        val currentUsername = sharedPreferences.getString(KEY_USERNAME, "")
        if (currentUsername.isNullOrEmpty()) {
            loadingText.text = "You need to be logged in to view requests"
            return
        }

        // Get reference to Firebase database
        val database = FirebaseDatabase.getInstance()
        val requestsRef = database.getReference("FollowRequests").child(currentUsername)

        // Query for follow requests
        requestsRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                // Clear the container
                container.removeAllViews()

                if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                    // No requests found
                    val noRequestsText = TextView(this@MainActivity).apply {
                        text = "No follow requests"
                        textSize = 16f
                        setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                        setTextColor(android.graphics.Color.GRAY)
                    }
                    container.addView(noRequestsText)
                    return
                }

                // Process each request
                for (requestSnapshot in snapshot.children) {
                    val fromUsername = requestSnapshot.key ?: continue
                    val timestamp = requestSnapshot.child("timestamp").value as? String ?: ""

                    // Get user info for the requester
                    getUserInfo(fromUsername) { name, profilePicUrl ->
                        // Create request item view
                        val requestView = createRequestItemView(fromUsername, name, profilePicUrl, container)
                        container.addView(requestView)
                    }
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e("FollowRequests", "Error loading requests: ${error.message}")

                // Show error message
                container.removeAllViews()
                val errorText = TextView(this@MainActivity).apply {
                    text = "Error loading requests: ${error.message}"
                    textSize = 16f
                    setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                    setTextColor(android.graphics.Color.RED)
                }
                container.addView(errorText)
            }
        })
    }


    // Function to create a request item view
    private fun createRequestItemView(username: String, name: String, profilePicUrl: String, container: LinearLayout): View {
        // Create a horizontal layout for the request item
        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Create profile image
        val profileImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx())
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.story_circle)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
        }

        // Load profile image
        if (profilePicUrl.isNotEmpty()) {
            Glide.with(this)
                .load(profilePicUrl)
                .centerCrop()
                .into(profileImageView)
        } else {
            // Use a default profile image
            profileImageView.setImageResource(R.drawable.profilepic1)
        }

        // Create text view for the username
        val usernameTextView = TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(android.graphics.Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            setPadding(12.dpToPx(), 0, 0, 0)
        }

        // Create buttons container
        val buttonsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Create accept button
        val acceptButton = Button(this).apply {
            text = "Accept"
            textSize = 12f
            setPadding(12.dpToPx(), 4.dpToPx(), 12.dpToPx(), 4.dpToPx())
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.btn_default)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 8.dpToPx()
            }
        }

        // Create reject button
        val rejectButton = Button(this).apply {
            text = "Reject"
            textSize = 12f
            setPadding(12.dpToPx(), 4.dpToPx(), 12.dpToPx(), 4.dpToPx())
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.btn_default)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Set click listeners for buttons
        acceptButton.setOnClickListener {
            acceptFollowRequest(username, container)
        }

        rejectButton.setOnClickListener {
            rejectFollowRequest(username, container)
        }

        // Add buttons to container
        buttonsContainer.addView(acceptButton)
        buttonsContainer.addView(rejectButton)

        // Add views to the main layout
        itemLayout.addView(profileImageView)
        itemLayout.addView(usernameTextView)
        itemLayout.addView(buttonsContainer)

        return itemLayout
    }


    // Function to accept a follow request
    private fun acceptFollowRequest(fromUsername: String, container: LinearLayout) {
        val currentUsername = sharedPreferences.getString(KEY_USERNAME, "") ?: ""
        if (currentUsername.isEmpty()) return

        val database = FirebaseDatabase.getInstance()
        val requestsRef = database.getReference("FollowRequests")
        val followersRef = database.getReference("Followers")
        val followingRef = database.getReference("Following")

        // Show loading toast
        Toast.makeText(this, "Accepting request...", Toast.LENGTH_SHORT).show()

        // Add to followers list (fromUsername is now following currentUsername)
        followersRef.child(currentUsername).child(fromUsername).setValue(true)
            .addOnSuccessListener {
                // Add to following list (currentUsername is now followed by fromUsername)
                followingRef.child(fromUsername).child(currentUsername).setValue(true)
                    .addOnSuccessListener {
                        // Remove the request
                        requestsRef.child(currentUsername).child(fromUsername).removeValue()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Follow request accepted", Toast.LENGTH_SHORT).show()
                                // Reload the requests
                                loadFollowRequests(container)
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error removing request: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error updating following: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error updating followers: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Function to reject a follow request
    private fun rejectFollowRequest(fromUsername: String, container: LinearLayout) {
        val currentUsername = sharedPreferences.getString(KEY_USERNAME, "") ?: ""
        if (currentUsername.isEmpty()) return

        val database = FirebaseDatabase.getInstance()
        val requestsRef = database.getReference("FollowRequests")

        // Show loading toast
        Toast.makeText(this, "Rejecting request...", Toast.LENGTH_SHORT).show()

        // Remove the request
        requestsRef.child(currentUsername).child(fromUsername).removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "Follow request rejected", Toast.LENGTH_SHORT).show()
                // Reload the requests
                loadFollowRequests(container)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error rejecting request: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Function to filter requests by search query
    private fun filterRequests(query: String, container: LinearLayout) {
        // This is a simple client-side filtering
        // For a real app, you might want to do this filtering on the server

        // Get all request items
        val requestItems = ArrayList<View>()
        for (i in 0 until container.childCount) {
            requestItems.add(container.getChildAt(i))
        }

        // Clear container
        container.removeAllViews()

        // Filter and add matching items
        var matchFound = false
        for (item in requestItems) {
            if (item is LinearLayout) {
                // Find the username TextView (second child)
                val usernameTextView = item.getChildAt(1) as? TextView
                val username = usernameTextView?.text?.toString() ?: ""

                if (username.contains(query, ignoreCase = true)) {
                    container.addView(item)
                    matchFound = true
                }
            }
        }

        // Show no results message if needed
        if (!matchFound) {
            val noResultsText = TextView(this).apply {
                text = "No matching requests found"
                textSize = 16f
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                setTextColor(android.graphics.Color.GRAY)
            }
            container.addView(noResultsText)
        }
    }
    // Function to get user info from Firebase
    private fun getUserInfo(username: String, callback: (name: String, profilePicUrl: String) -> Unit) {
        val database = FirebaseDatabase.getInstance()
        val usersRef = database.getReference("Users").child(username)

        usersRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (snapshot.exists()) {
                    val name = snapshot.child("name").value as? String ?: username
                    val profilePicUrl = snapshot.child("profilePicUrl").value as? String ?: ""
                    callback(name, profilePicUrl)
                } else {
                    callback(username, "")
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e("UserInfo", "Error getting user info: ${error.message}")
                callback(username, "")
            }
        })
    }


    private fun showScreen6(chatId: String, username: String, name: String, profilePicUrl: String) {
        setContentView(R.layout.screen6)

        // Set user name in the header
        val userNameTextView = findViewById<TextView>(R.id.userName)
        userNameTextView.text = name

        // Set profile image
        val profileImageView = findViewById<ImageView>(R.id.profileImage)
        if (profilePicUrl.isNotEmpty()) {
            Glide.with(this)
                .load(profilePicUrl)
                .centerCrop()
                .into(profileImageView)
        } else {
            // Use a default profile image
            profileImageView.setImageResource(R.drawable.profilepic3)
        }

        // Set up back button
        val backButton = findViewById<ImageView>(R.id.BackButton)
        backButton.setOnClickListener {
            showScreen5()
        }

        // Set up call buttons
        val callButton = findViewById<ImageView>(R.id.callbutton)
        callButton.setOnClickListener {
            showScreen8()
        }

        val videocallButton = findViewById<ImageView>(R.id.videocallbutton)
        videocallButton.setOnClickListener {
            showScreen9()
        }

        // Set up view profile button
        val viewProfileButton = findViewById<Button>(R.id.viewProfileButton)
        viewProfileButton.setOnClickListener {
            viewUserProfile(username)
        }

        // Get message container
        val messageContainer = findViewById<LinearLayout>(R.id.messageContainer)

        // Load messages
        loadMessages(chatId, messageContainer)

        // Set up send message functionality
        val messageInput = findViewById<EditText>(R.id.messageInput)
        val sendButton = findViewById<ImageView>(R.id.sendButton)

        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(chatId, username, messageText, messageContainer)
                messageInput.text.clear()
            }
        }

        // Set up swipe gesture for screen 7
        val rootView = findViewById<ScrollView>(R.id.messagearea)
        rootView.setOnTouchListener(object : View.OnTouchListener {
            private var startY: Float = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.y
                    }
                    MotionEvent.ACTION_UP -> {
                        val endY = event.y
                        val deltaY = startY - endY

                        if (deltaY > 100) {
                            showScreen7()
                            return true
                        }
                    }
                }
                return false
            }
        })
    }

    // Function to load messages from Firebase
    private fun loadMessages(chatId: String, container: LinearLayout) {
        // Clear existing views
        container.removeAllViews()

        // Get current username
        val currentUsername = sharedPreferences.getString(KEY_USERNAME, "") ?: ""

        // Get reference to Firebase database
        val database = FirebaseDatabase.getInstance()
        val messagesRef = database.getReference("Messages").child(chatId)

        // Query for messages
        messagesRef.orderByChild("timestamp")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    // Clear the container
                    container.removeAllViews()

                    if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                        // No messages found
                        val noMessagesText = TextView(this@MainActivity).apply {
                            text = "No messages yet. Start the conversation!"
                            textSize = 16f
                            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                            setTextColor(android.graphics.Color.GRAY)
                            gravity = android.view.Gravity.CENTER
                        }
                        container.addView(noMessagesText)
                        return
                    }

                    // Process each message
                    val messagesList = ArrayList<Map<String, Any>>()

                    for (messageSnapshot in snapshot.children) {
                        val messageData = messageSnapshot.value as? Map<*, *> ?: continue

                        // Get message details
                        val sender = messageData["sender"] as? String ?: ""
                        val text = messageData["text"] as? String ?: ""
                        val timestamp = messageData["timestamp"] as? String ?: ""
                        val time = formatMessageTime(timestamp)

                        // Create a map with message info
                        val messageInfo = mapOf(
                            "sender" to sender,
                            "text" to text,
                            "timestamp" to timestamp,
                            "time" to time
                        )

                        messagesList.add(messageInfo)
                    }

                    // Sort messages by timestamp
                    messagesList.sortBy { it["timestamp"] as String }

                    // Display each message
                    for (messageInfo in messagesList) {
                        val sender = messageInfo["sender"] as String
                        val text = messageInfo["text"] as String
                        val time = messageInfo["time"] as String

                        // Create message view based on sender
                        val messageView = if (sender == currentUsername) {
                            createOutgoingMessageView(text, time)
                        } else {
                            createIncomingMessageView(text, time)
                        }

                        container.addView(messageView)
                    }

                    // Scroll to bottom
                    val scrollView = findViewById<ScrollView>(R.id.messagearea)
                    scrollView.post {
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                    }

                    // Update last seen message in Chats
                    val lastMessage = messagesList.lastOrNull()
                    if (lastMessage != null) {
                        updateLastMessage(chatId, lastMessage["text"] as String, lastMessage["timestamp"] as String)
                    }
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Log.e("Messages", "Error loading messages: ${error.message}")

                    // Show error message
                    container.removeAllViews()
                    val errorText = TextView(this@MainActivity).apply {
                        text = "Error loading messages: ${error.message}"
                        textSize = 16f
                        setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                        setTextColor(android.graphics.Color.RED)
                    }
                    container.addView(errorText)
                }
            })
    }

    // Function to send a message
    private fun sendMessage(chatId: String, recipientUsername: String, messageText: String, container: LinearLayout) {
        // Get current username
        val currentUsername = sharedPreferences.getString(KEY_USERNAME, "") ?: ""
        if (currentUsername.isEmpty()) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Get current timestamp
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // Create message data
        val messageData = mapOf(
            "sender" to currentUsername,
            "recipient" to recipientUsername,
            "text" to messageText,
            "timestamp" to timestamp,
            "read" to false
        )

        // Get reference to Firebase database
        val database = FirebaseDatabase.getInstance()
        val messagesRef = database.getReference("Messages").child(chatId)

        // Generate a unique message ID
        val messageId = messagesRef.push().key ?: UUID.randomUUID().toString()

        // Save message to Firebase
        messagesRef.child(messageId).setValue(messageData)
            .addOnSuccessListener {
                // Message sent successfully
                // The ValueEventListener will update the UI

                // Update last message in Chats
                updateLastMessage(chatId, messageText, timestamp)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Function to update last message in Chats
    private fun updateLastMessage(chatId: String, lastMessage: String, timestamp: String) {
        val database = FirebaseDatabase.getInstance()
        val chatsRef = database.getReference("Chats")

        val updates = mapOf(
            "last_message" to lastMessage,
            "last_message_time" to timestamp
        )

        chatsRef.child(chatId).updateChildren(updates)
            .addOnFailureListener { e ->
                Log.e("UpdateChat", "Error updating last message: ${e.message}")
            }
    }

    // Function to create an outgoing message view (sent by current user)
    private fun createOutgoingMessageView(message: String, time: String): View {
        // Create a container for the message
        val messageLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8.dpToPx(), 0, 8.dpToPx())
            }
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }

        // Create message bubble
        val messageBubble = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.viewprofile_button)
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
        }

        // Create message text
        val messageText = TextView(this).apply {
            text = message  // Fixed issue by renaming the parameter
            textSize = 14f
            setTextColor(android.graphics.Color.BLACK)
        }

        // Create time text
        val timeText = TextView(this).apply {
            text = time
            textSize = 12f
            setTextColor(android.graphics.Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END
                topMargin = 1.dpToPx()
            }
        }

        // Add message text to bubble
        messageBubble.addView(messageText)

        // Add views to layout
        messageLayout.addView(messageBubble)
        messageLayout.addView(timeText)

        return messageLayout
    }

    // Function to create an incoming message view (received from other user)
    private fun createIncomingMessageView(message: String, time: String): View {
        // Create a horizontal layout to hold the profile image and message bubble
        val messageLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8.dpToPx(), 0, 8.dpToPx())
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
        }

        // Create profile image
        val profileImage = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(40.dpToPx(), 40.dpToPx()).apply {
                marginEnd = 8.dpToPx()
            }
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.story_circle)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            setImageResource(R.drawable.profilepic3)
        }

        // Create a vertical layout for message bubble and time
        val messageContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Create message bubble
        val messageBubble = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.viewprofile_button)
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
        }

        // Create message text
        val messageText = TextView(this).apply {
            text = message  // Fixed issue by renaming the parameter
            textSize = 14f
            setTextColor(android.graphics.Color.BLACK)
        }

        // Create time text
        val timeText = TextView(this).apply {
            text = time
            textSize = 12f
            setTextColor(android.graphics.Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START
                topMargin = 2.dpToPx()
            }
        }

        // Add message text to bubble
        messageBubble.addView(messageText)

        // Add message bubble and time text to container
        messageContainer.addView(messageBubble)
        messageContainer.addView(timeText)

        // Add profile image and message container to the main layout
        messageLayout.addView(profileImage)
        messageLayout.addView(messageContainer)

        return messageLayout
    }

    // Function to format message timestamp to a readable time
    private fun formatMessageTime(timestamp: String): String {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = dateFormat.parse(timestamp) ?: return ""

            val now = Date()
            val diff = now.time - date.time

            // If message is from today, show only time
            return if (diff < 24 * 60 * 60 * 1000) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            } else {
                // Otherwise show date and time
                SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(date)
            }
        } catch (e: Exception) {
            Log.e("FormatTime", "Error formatting time: ${e.message}")
            return ""
        }
    }
    private fun showScreen7() {
        setContentView(R.layout.screen7)

        val backButton = findViewById<ImageView>(R.id.BackButton)
        backButton.setOnClickListener {
            showScreen5()
        }

        val rootView = findViewById<ScrollView>(R.id.messagearea)

        rootView.setOnTouchListener(object : View.OnTouchListener {
            private var startY: Float = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.y
                    }
                    MotionEvent.ACTION_UP -> {
                        val endY = event.y
                        val deltaY = startY - endY

                        if (deltaY > 100) {
                            //showScreen6()
                            return true
                        }
                    }
                }
                return false
            }
        })
    }

    private fun showScreen8() {
        setContentView(R.layout.screen8)

        // Get the username from SharedPreferences or from the chat partner
        val chatPartnerUsername = sharedPreferences.getString(CHAT_PARTNER_USERNAME, "")
        val chatPartnerName = sharedPreferences.getString(CHAT_PARTNER_NAME, "")

        // Set the user name in the call screen
        val userNameTextView = findViewById<TextView>(R.id.tvUserName)
        userNameTextView.text = chatPartnerName

        // Initialize call duration timer
        val callDurationTextView = findViewById<TextView>(R.id.tvCallDuration)
        callDurationTextView.text = "00:00"

        // Set up button click listeners
        val endCallButton = findViewById<ImageView>(R.id.btnEndCall)
        endCallButton.setOnClickListener {
            endCall()
        }

        val muteButton = findViewById<ImageView>(R.id.btnMuteToggle)
        muteButton.setOnClickListener {
            toggleMute()
        }

        val speakerButton = findViewById<ImageView>(R.id.btnSpeakerToggle)
        speakerButton.setOnClickListener {
            toggleSpeaker()
        }

        // Initialize Agora SDK and join channel
        initializeAndJoinChannel()

        // Start call duration timer
        startCallDurationTimer(callDurationTextView)
    }

    private fun initializeAndJoinChannel() {
        try {
            // Initialize RtcEngine
            val config = RtcEngineConfig()
            config.mContext = applicationContext
            config.mAppId = appId
            config.mEventHandler = object : IRtcEngineEventHandler() {
                override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
                    runOnUiThread {
                        isJoined = true
                        Toast.makeText(applicationContext, "Joined Channel Successfully", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onUserJoined(uid: Int, elapsed: Int) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Remote User Joined", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onUserOffline(uid: Int, reason: Int) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Remote User Left", Toast.LENGTH_SHORT).show()
                        // Optionally end the call if the remote user leaves
                        // endCall()
                    }
                }

                override fun onError(err: Int) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Error: $err", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            rtcEngine = RtcEngine.create(config)

            // Enable audio
            rtcEngine?.enableAudio()

            // Set audio profile
            rtcEngine?.setAudioProfile(
                Constants.AUDIO_PROFILE_DEFAULT,
                Constants.AUDIO_SCENARIO_CHATROOM
            )

            // Join the channel
            val option = ChannelMediaOptions()
            option.autoSubscribeAudio = true
            option.publishMicrophoneTrack = true

            // Generate a temporary token (in production, you should get this from your server)
            // For testing, you can set Security to "Testing Mode" in Agora Console
            val token: String? = null // Use null for testing mode or provide a valid token

            // Join the channel
            rtcEngine?.joinChannel(token, channelName, 0, option)

        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Error initializing Agora SDK: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun toggleMute() {
        if (rtcEngine != null) {
            isMuted = !isMuted
            rtcEngine?.muteLocalAudioStream(isMuted)

            // Update UI to reflect mute state
            val muteButton = findViewById<ImageView>(R.id.btnMuteToggle)
            if (isMuted) {
                // Change button appearance for muted state
                muteButton.alpha = 0.5f
                Toast.makeText(this, "Microphone muted", Toast.LENGTH_SHORT).show()
            } else {
                // Change button appearance for unmuted state
                muteButton.alpha = 1.0f
                Toast.makeText(this, "Microphone unmuted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleSpeaker() {
        if (rtcEngine != null) {
            isSpeakerOn = !isSpeakerOn
            rtcEngine?.setEnableSpeakerphone(isSpeakerOn)

            // Update UI to reflect speaker state
            val speakerButton = findViewById<ImageView>(R.id.btnSpeakerToggle)
            if (isSpeakerOn) {
                // Change button appearance for speaker on state
                speakerButton.alpha = 1.0f
                Toast.makeText(this, "Speaker on", Toast.LENGTH_SHORT).show()
            } else {
                // Change button appearance for speaker off state
                speakerButton.alpha = 0.5f
                Toast.makeText(this, "Speaker off", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCallDurationTimer(timerTextView: TextView) {
        callDurationInSeconds = 0
        callTimer = Timer()
        callTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                callDurationInSeconds++
                val minutes = callDurationInSeconds / 60
                val seconds = callDurationInSeconds % 60
                val timeString = String.format("%02d:%02d", minutes, seconds)

                runOnUiThread {
                    timerTextView.text = timeString
                }
            }
        }, 0, 1000) // Update every second
    }

    private fun endCall() {
        // Stop the timer
        callTimer?.cancel()
        callTimer = null

        // Leave the channel
        rtcEngine?.leaveChannel()

        // Reset variables
        isJoined = false
        isMuted = false
        isSpeakerOn = false

        // Return to chat screen
        val chatPartnerUsername = sharedPreferences.getString(CHAT_PARTNER_USERNAME, "")
        val chatPartnerName = sharedPreferences.getString(CHAT_PARTNER_NAME, "")
        val chatId = "${chatPartnerUsername}_${sharedPreferences.getString(KEY_USERNAME, "")}"

        // Get profile pic URL (you might need to adjust this based on your implementation)
        getUserInfo(chatPartnerUsername ?: "") { name, profilePicUrl ->
            showScreen6(chatId, chatPartnerUsername ?: "", chatPartnerName ?: "", profilePicUrl)
        }
    }

    // Make sure to destroy the RtcEngine when the activity is destroyed
    override fun onDestroy() {
        super.onDestroy()

        // Stop timer if it's running
        callTimer?.cancel()

        // Destroy the RtcEngine instance
        rtcEngine?.leaveChannel()
        RtcEngine.destroy()
        rtcEngine = null
    }

    private fun showScreen9() {
        setContentView(R.layout.screen9) // Screen 4 layout

        val EndCall = findViewById<ImageView>(R.id.btnEndCall)
        EndCall.setOnClickListener {
            // showScreen6()
        }
    }

    // Add this function to MainActivity class to fetch and display user posts
    private fun loadUserPosts(username: String) {
        try {
            val postsGridLayout = findViewById<GridLayout>(R.id.postsGrid)
            postsGridLayout.removeAllViews() // Clear existing views

            // Reference to Firebase posts
            val database = FirebaseDatabase.getInstance()
            val postsRef = database.getReference("Posts")

            // Query to find posts by this username
            postsRef.orderByChild("username").equalTo(username)
                .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        if (snapshot.exists()) {
                            val posts = ArrayList<Map<String, Any>>()

                            // Collect all posts
                            for (postSnapshot in snapshot.children) {
                                val post = postSnapshot.value as? Map<String, Any>
                                post?.let {
                                    // Add post ID to the map
                                    val postWithId = post.toMutableMap()
                                    postWithId["postId"] = postSnapshot.key.toString()
                                    posts.add(postWithId)
                                }
                            }

                            // Sort posts by timestamp (most recent first)
                            posts.sortByDescending { it["timestamp"] as? String }

                            // Update post count in UI
                            findViewById<TextView>(R.id.postcount).text = posts.size.toString()

                            // Display posts in grid
                            displayPostsInGrid(posts, postsGridLayout)
                        } else {
                            // No posts found
                            findViewById<TextView>(R.id.postcount).text = "0"
                            Toast.makeText(this@MainActivity, "No posts found", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        Toast.makeText(this@MainActivity, "Error loading posts: ${error.message}",
                            Toast.LENGTH_SHORT).show()
                    }
                })
        } catch (e: Exception) {
            Log.e("LoadUserPosts", "Error: ${e.message}")
            Toast.makeText(this, "Error loading posts: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Helper function to display posts in grid layout
    private fun displayPostsInGrid(posts: List<Map<String, Any>>, gridLayout: GridLayout) {
        try {
            // Set up grid layout parameters
            val columnCount = 3
            gridLayout.columnCount = columnCount

            // Calculate cell size based on screen width
            val cellSize = resources.displayMetrics.widthPixels / columnCount

            // Add each post to the grid
            for (i in posts.indices) {
                val post = posts[i]

                // Get the first image from the post (if it has any)
                val imagesList = post["images"] as? ArrayList<*>
                if (imagesList.isNullOrEmpty()) continue

                val base64Image = imagesList[0] as? String ?: continue

                // Create image view for the post
                val imageView = ImageView(this).apply {
                    val params = GridLayout.LayoutParams()
                    params.width = cellSize
                    params.height = cellSize
                    layoutParams = params

                    scaleType = ImageView.ScaleType.CENTER_CROP
                    // Optional: add padding or border
                    setPadding(2.dpToPx(), 2.dpToPx(), 2.dpToPx(), 2.dpToPx())
                }

                // Get the post ID (for handling clicks)
                val postId = post["postId"] as? String
                imageView.tag = postId

                // Handle click on post image
                imageView.setOnClickListener {
                    // TODO: Navigate to post detail screen
                    Toast.makeText(this, "Post clicked: $postId", Toast.LENGTH_SHORT).show()
                }

                // Convert base64 to bitmap and display
                try {
                    val imageBytes = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT)
                    val decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    imageView.setImageBitmap(decodedImage)
                } catch (e: Exception) {
                    Log.e("DisplayPosts", "Error decoding image: ${e.message}")
                    // Set a placeholder image if decoding fails
                    imageView.setImageResource(R.drawable.profile_pictures_border)
                }

                // Add the image to grid
                gridLayout.addView(imageView)
            }

            // If no posts were added to the grid, show a message
            if (gridLayout.childCount == 0) {
                findViewById<TextView>(R.id.postcount).text = "0"
            }
        } catch (e: Exception) {
            Log.e("DisplayPosts", "Error: ${e.message}")
            Toast.makeText(this, "Error displaying posts: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Now update the showScreen10 method to load user posts
    private fun showScreen10() {
        setContentView(R.layout.screen10) // Screen 10 layout

        // Your existing code for this function...
        val followersPage = findViewById<LinearLayout>(R.id.FollowersPage)
        followersPage.setOnClickListener {
            showScreen11()
        }

        val followingPage = findViewById<LinearLayout>(R.id.FollowingPage)
        followingPage.setOnClickListener {
            showScreen12()
        }

        val editProfile = findViewById<ImageButton>(R.id.EditProfile)
        editProfile.setOnClickListener {
            showScreen13()
        }

        val gotoHome = findViewById<ImageButton>(R.id.HomePage)
        gotoHome.setOnClickListener {
            showScreen4()
        }
        val gotoSearch = findViewById<ImageButton>(R.id.SearchPage)
        gotoSearch.setOnClickListener {
            showScreen14()
        }
        val gotoPost = findViewById<ImageButton>(R.id.PostPage)
        gotoPost.setOnClickListener {
            showScreen15()
        }
        val gotoProfile = findViewById<ImageButton>(R.id.ProfilePage)
        gotoProfile.setOnClickListener {
            showScreen10()
        }
        val gotoContacts = findViewById<ImageButton>(R.id.ContactsPage)
        gotoContacts.setOnClickListener {
            showScreen18()
        }

        val username = sharedPreferences.getString(KEY_USERNAME, "")
        val name = sharedPreferences.getString(KEY_NAME, "")
        val email = sharedPreferences.getString(KEY_EMAIL, "")
        val phone = sharedPreferences.getString(KEY_PHONE, "")

        // Update UI with user information
        findViewById<TextView>(R.id.username).text = name

        // Add this line to load the user's posts
        if (!username.isNullOrEmpty()) {
            loadUserPosts(username)
        } else {
            Toast.makeText(this, "Cannot load posts: Username not found", Toast.LENGTH_SHORT).show()
        }
    }

    // Update the showScreen11 method to load and display followers
    private fun showScreen11() {
        setContentView(R.layout.screen11)

        // Set up back button
        val backButton = findViewById<ImageView>(R.id.BackButton)
        backButton.setOnClickListener {
            showScreen10()
        }

        // Set up tab navigation
        val followingTab = findViewById<LinearLayout>(R.id.FollowingTab)
        followingTab.setOnClickListener {
            showScreen12()
        }

        // Get references to UI elements
        val followersCountText = findViewById<TextView>(R.id.tabFollowers)
        val followingCountText = findViewById<TextView>(R.id.tabFollowing)
        val followersContainer = findViewById<LinearLayout>(R.id.followersContainer)

        // Get current username
        val currentUsername = sharedPreferences.getString(KEY_USERNAME, "")
        if (currentUsername.isNullOrEmpty()) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Load followers
        loadFollowers(currentUsername, followersContainer, followersCountText)

        // Load following count (this was missing)
        loadFollowingCount(currentUsername, followingCountText)
    }

    // Function to load only the following count
    private fun loadFollowingCount(username: String, countTextView: TextView) {
        val database = FirebaseDatabase.getInstance()
        val followingRef = database.getReference("Following").child(username)

        followingRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                countTextView.text = "${snapshot.childrenCount} Following"
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                countTextView.text = "0 Following"
            }
        })
    }

    // Update the showScreen12 method to load and display following
    private fun showScreen12() {
        setContentView(R.layout.screen12)

        // Set up back button
        val backButton = findViewById<ImageView>(R.id.BackButton)
        backButton.setOnClickListener {
            showScreen10()
        }

        // Set up tab navigation
        val followersTab = findViewById<LinearLayout>(R.id.FollowersTab)
        followersTab.setOnClickListener {
            showScreen11()
        }

        // Get references to UI elements
        val followingCountText = findViewById<TextView>(R.id.tab_requests)
        val followersCountText = findViewById<TextView>(R.id.tab_dms) // Missing followers count
        val followingContainer = findViewById<LinearLayout>(R.id.followingContainer)

        // Get current username
        val currentUsername = sharedPreferences.getString(KEY_USERNAME, "")
        if (currentUsername.isNullOrEmpty()) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Load following
        loadFollowing(currentUsername, followingContainer, followingCountText)

        // Load followers count (this was missing)
        loadFollowersCount(currentUsername, followersCountText)
    }

    // Function to load only the followers count
    private fun loadFollowersCount(username: String, countTextView: TextView) {
        val database = FirebaseDatabase.getInstance()
        val followersRef = database.getReference("Followers").child(username)

        followersRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                countTextView.text = "${snapshot.childrenCount} Followers"
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                countTextView.text = "0 Followers"
            }
        })
    }

    // Function to load followers from Firebase
    private fun loadFollowers(username: String, container: LinearLayout, countTextView: TextView) {
        // Clear existing views
        container.removeAllViews()

        // Show loading indicator
        val loadingText = TextView(this).apply {
            text = "Loading followers..."
            textSize = 16f
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
        }
        container.addView(loadingText)

        // Get reference to Firebase database
        val database = FirebaseDatabase.getInstance()
        val followersRef = database.getReference("Followers").child(username)

        // Query for followers
        followersRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                // Clear the container
                container.removeAllViews()

                if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                    // No followers found
                    val noFollowersText = TextView(this@MainActivity).apply {
                        text = "No followers yet"
                        textSize = 16f
                        setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                        setTextColor(android.graphics.Color.GRAY)
                    }
                    container.addView(noFollowersText)
                    countTextView.text = "0 Followers"
                    return
                }

                // Update followers count
                countTextView.text = "${snapshot.childrenCount} Followers"

                // Process each follower
                for (followerSnapshot in snapshot.children) {
                    val followerUsername = followerSnapshot.key ?: continue

                    // Get user info for the follower
                    getUserInfo(followerUsername) { name, profilePicUrl ->
                        // Create follower item view
                        val followerView = createFollowerItemView(followerUsername, name, profilePicUrl)
                        container.addView(followerView)
                    }
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e("Followers", "Error loading followers: ${error.message}")

                // Show error message
                container.removeAllViews()
                val errorText = TextView(this@MainActivity).apply {
                    text = "Error loading followers: ${error.message}"
                    textSize = 16f
                    setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                    setTextColor(android.graphics.Color.RED)
                }
                container.addView(errorText)
            }
        })
    }

    // Function to load following from Firebase
    private fun loadFollowing(username: String, container: LinearLayout, countTextView: TextView) {
        // Clear existing views
        container.removeAllViews()

        // Show loading indicator
        val loadingText = TextView(this).apply {
            text = "Loading following..."
            textSize = 16f
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
        }
        container.addView(loadingText)

        // Get reference to Firebase database
        val database = FirebaseDatabase.getInstance()
        val followingRef = database.getReference("Following").child(username)

        // Query for following
        followingRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                // Clear the container
                container.removeAllViews()

                if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                    // Not following anyone
                    val noFollowingText = TextView(this@MainActivity).apply {
                        text = "Not following anyone yet"
                        textSize = 16f
                        setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                        setTextColor(android.graphics.Color.GRAY)
                    }
                    container.addView(noFollowingText)
                    countTextView.text = "0 Following"
                    return
                }

                // Update following count
                countTextView.text = "${snapshot.childrenCount} Following"

                // Process each following
                for (followingSnapshot in snapshot.children) {
                    val followingUsername = followingSnapshot.key ?: continue

                    // Get user info for the following
                    getUserInfo(followingUsername) { name, profilePicUrl ->
                        // Create following item view
                        val followingView = createFollowingItemView(followingUsername, name, profilePicUrl)
                        container.addView(followingView)
                    }
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e("Following", "Error loading following: ${error.message}")

                // Show error message
                container.removeAllViews()
                val errorText = TextView(this@MainActivity).apply {
                    text = "Error loading following: ${error.message}"
                    textSize = 16f
                    setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                    setTextColor(android.graphics.Color.RED)
                }
                container.addView(errorText)
            }
        })
    }

    // Function to create a follower item view
    private fun createFollowerItemView(username: String, name: String, profilePicUrl: String): View {
        // Create a horizontal layout for the follower item
        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Create profile image
        val profileImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx())
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.story_circle)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
        }

        // Load profile image
        if (profilePicUrl.isNotEmpty()) {
            Glide.with(this)
                .load(profilePicUrl)
                .centerCrop()
                .into(profileImageView)
        } else {
            // Use a default profile image
            profileImageView.setImageResource(R.drawable.profilepic1)
        }

        // Create text view for the username
        val nameTextView = TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(android.graphics.Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            setPadding(12.dpToPx(), 0, 0, 0)
        }

        // Create message button
        val messageButton = ImageView(this).apply {
            setImageResource(R.drawable.message_icon)
            layoutParams = LinearLayout.LayoutParams(
                32.dpToPx(),
                32.dpToPx()
            )
        }

        // Set click listener for the message button
        messageButton.setOnClickListener {
            // Start a chat with this user
            startChatWithUser(username)
        }

        // Add views to the main layout
        itemLayout.addView(profileImageView)
        itemLayout.addView(nameTextView)
        itemLayout.addView(messageButton)

        // Set click listener on the layout to view the user's profile
        itemLayout.setOnClickListener {
            viewUserProfile(username)
        }

        return itemLayout
    }

    // Function to create a following item view
    private fun createFollowingItemView(username: String, name: String, profilePicUrl: String): View {
        // Create a horizontal layout for the following item
        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Create profile image
        val profileImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx())
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.story_circle)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
        }

        // Load profile image
        if (profilePicUrl.isNotEmpty()) {
            Glide.with(this)
                .load(profilePicUrl)
                .centerCrop()
                .into(profileImageView)
        } else {
            // Use a default profile image
            profileImageView.setImageResource(R.drawable.profilepic1)
        }

        // Create text view for the username
        val nameTextView = TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(android.graphics.Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            setPadding(12.dpToPx(), 0, 0, 0)
        }

        // Create message button
        val messageButton = ImageView(this).apply {
            setImageResource(R.drawable.message_icon)
            layoutParams = LinearLayout.LayoutParams(
                32.dpToPx(),
                32.dpToPx()
            )
        }

        // Set click listener for the message button
        messageButton.setOnClickListener {
            // Start a chat with this user
            startChatWithUser(username)
        }

        // Add views to the main layout
        itemLayout.addView(profileImageView)
        itemLayout.addView(nameTextView)
        itemLayout.addView(messageButton)

        // Set click listener on the layout to view the user's profile
        itemLayout.setOnClickListener {
            viewUserProfile(username)
        }

        return itemLayout
    }

    // Function to filter followers by search query
    private fun filterFollowers(query: String, container: LinearLayout) {
        // This is a simple client-side filtering
        // For a real app, you might want to do this filtering on the server

        // Get all follower items
        val followerItems = ArrayList<View>()
        for (i in 0 until container.childCount) {
            followerItems.add(container.getChildAt(i))
        }

        // Clear container
        container.removeAllViews()

        // Filter and add matching items
        var matchFound = false
        for (item in followerItems) {
            if (item is LinearLayout) {
                // Find the name TextView (second child)
                val nameTextView = item.getChildAt(1) as? TextView
                val name = nameTextView?.text?.toString() ?: ""

                if (name.contains(query, ignoreCase = true)) {
                    container.addView(item)
                    matchFound = true
                }
            }
        }

        // Show no results message if needed
        if (!matchFound) {
            val noResultsText = TextView(this).apply {
                text = "No matching followers found"
                textSize = 16f
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                setTextColor(android.graphics.Color.GRAY)
            }
            container.addView(noResultsText)
        }
    }

    // Function to filter following by search query
    private fun filterFollowing(query: String, container: LinearLayout) {
        // This is a simple client-side filtering
        // For a real app, you might want to do this filtering on the server

        // Get all following items
        val followingItems = ArrayList<View>()
        for (i in 0 until container.childCount) {
            followingItems.add(container.getChildAt(i))
        }

        // Clear container
        container.removeAllViews()

        // Filter and add matching items
        var matchFound = false
        for (item in followingItems) {
            if (item is LinearLayout) {
                // Find the name TextView (second child)
                val nameTextView = item.getChildAt(1) as? TextView
                val name = nameTextView?.text?.toString() ?: ""

                if (name.contains(query, ignoreCase = true)) {
                    container.addView(item)
                    matchFound = true
                }
            }
        }

        // Show no results message if needed
        if (!matchFound) {
            val noResultsText = TextView(this).apply {
                text = "No matching users found"
                textSize = 16f
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                setTextColor(android.graphics.Color.GRAY)
            }
            container.addView(noResultsText)
        }
    }

    // Function to start a chat with a user

    private fun showScreen13() {
        setContentView(R.layout.screen13)

        // Get user data from SharedPreferences
        val username = sharedPreferences.getString(KEY_USERNAME, "")
        val name = sharedPreferences.getString(KEY_NAME, "")
        val email = sharedPreferences.getString(KEY_EMAIL, "")
        val phone = sharedPreferences.getString(KEY_PHONE, "")
        val bio = sharedPreferences.getString(KEY_BIO, "Just an average 14 year old")  // Default bio if not set

        // Log the current user data for debugging
        Log.d("EditProfile", "Current user data: username=$username, name=$name, email=$email, phone=$phone, bio=$bio")

        // Find all the EditText and TextView fields
        val nameEditText = findViewById<EditText>(R.id.nameEditText)
        val usernameEditText = findViewById<EditText>(R.id.usernameEditText)
        val phoneEditText = findViewById<EditText>(R.id.phoneEditText)
        val bioTextView = findViewById<TextView>(R.id.textView)
        val profileNameTextView = findViewById<TextView>(R.id.profileNameTextView)

        // Set the current values
        nameEditText.setText(name)
        usernameEditText.setText(username)
        phoneEditText.setText(phone)
        bioTextView.text = bio
        profileNameTextView.text = name

        // Handle the Done button click
        val doneEditing = findViewById<TextView>(R.id.DoneEditing)
        doneEditing.setOnClickListener {
            // Get the updated values
            val updatedName = nameEditText.text.toString()
            val updatedUsername = usernameEditText.text.toString()
            val updatedPhone = phoneEditText.text.toString()
            val updatedBio = bioTextView.text.toString()

            Log.d("EditProfile", "Updated values: name=$updatedName, username=$updatedUsername, phone=$updatedPhone, bio=$updatedBio")

            // Validate input
            if (updatedName.isEmpty() || updatedUsername.isEmpty() || updatedPhone.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show a loading indicator
            Toast.makeText(this, "Updating profile...", Toast.LENGTH_SHORT).show()

            // For simplicity, let's just update the SharedPreferences directly and go back to screen 10
            // This will help us determine if the issue is with Firebase or something else
            saveUserCredentials(username ?: "", updatedName, email ?: "", updatedPhone, updatedBio)
            Toast.makeText(this, "Profile updated in SharedPreferences", Toast.LENGTH_SHORT).show()

            // Now let's try to update Firebase
            try {
                val database = FirebaseDatabase.getInstance()
                val usersRef = database.getReference("Users")

                Log.d("EditProfile", "Updating Firebase at path: Users/$username")

                // Create a map of fields to update
                val updates = HashMap<String, Any>()
                updates["name"] = updatedName
                updates["phone"] = updatedPhone
                updates["bio"] = updatedBio

                // Update in Firebase with more detailed callbacks
                usersRef.child(username ?: "").updateChildren(updates)
                    .addOnSuccessListener {
                        Log.d("EditProfile", "Firebase update successful")
                        Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                        showScreen10()
                    }
                    .addOnFailureListener { e ->
                        Log.e("EditProfile", "Firebase update failed: ${e.message}", e)
                        Toast.makeText(this, "Firebase update failed: ${e.message}", Toast.LENGTH_LONG).show()
                        // Still go to screen 10 since we updated SharedPreferences
                        showScreen10()
                    }
                    .addOnCompleteListener {
                        Log.d("EditProfile", "Firebase update operation completed")
                    }
            } catch (e: Exception) {
                Log.e("EditProfile", "Exception during Firebase update: ${e.message}", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                // Still go to screen 10 since we updated SharedPreferences
                showScreen10()
            }
        }

        // Handle profile image change
        val cameraIcon = findViewById<ImageView>(R.id.cameraIcon)
        cameraIcon.setOnClickListener {
            // Open image picker
            if (checkAndRequestPermissions()) {
                val intent = Intent(Intent.ACTION_PICK)
                intent.type = "image/*"
                startActivityForResult(intent, PROFILE_IMAGE_REQUEST)
            }
        }
    }
    private fun updateUserProfile(username: String, name: String, phone: String, email: String, bio: String) {
        val database = FirebaseDatabase.getInstance()
        val usersRef = database.getReference("Users")

        // Create a map of fields to update
        val updates = HashMap<String, Any>()
        updates["name"] = name
        updates["phone"] = phone
        updates["bio"] = bio

        // Update in Firebase
        usersRef.child(username).updateChildren(updates).addOnSuccessListener {
            // Update in SharedPreferences
            saveUserCredentials(username, name, email, phone, bio)
            Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()

            // Navigate back to profile screen (screen 10)
            showScreen10()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun checkUsernameAvailability(newUsername: String, oldUsername: String, name: String, phone: String, email: String, bio: String) {
        val database = FirebaseDatabase.getInstance()
        val usersRef = database.getReference("Users")

        usersRef.child(newUsername).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                Toast.makeText(this, "Username already taken", Toast.LENGTH_SHORT).show()
            } else {
                // Username is available, update user data with new username
                // First, copy the data to the new username node
                usersRef.child(oldUsername).get().addOnSuccessListener { oldUserData ->
                    if (oldUserData.exists()) {
                        // Create a map of the user data to copy
                        val userData = HashMap<String, Any>()
                        for (child in oldUserData.children) {
                            userData[child.key!!] = child.value!!
                        }

                        // Update with new values
                        userData["name"] = name
                        userData["username"] = newUsername
                        userData["phone"] = phone
                        userData["bio"] = bio

                        // Save to new username node
                        usersRef.child(newUsername).setValue(userData).addOnSuccessListener {
                            // Delete the old username node
                            usersRef.child(oldUsername).removeValue().addOnSuccessListener {
                                // Update SharedPreferences
                                saveUserCredentials(newUsername, name, email, phone, bio)
                                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()

// Navigate back to profile screen (screen 10)
                                showScreen10()   }
                        }.addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error checking username: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showScreen14() {
        setContentView(R.layout.screen14)

        // Get references to UI elements
        val searchEditText = findViewById<EditText>(R.id.searchEditText)
        val searchButton = findViewById<ImageView>(R.id.searchButton)
        val recentSearchesContainer = findViewById<LinearLayout>(R.id.recentSearchesContainer)
        val recentSearchesText = findViewById<TextView>(R.id.recentText)
        val searchResultsContainer = findViewById<LinearLayout>(R.id.searchResultsContainer)
        val resultsScrollContainer = findViewById<ScrollView>(R.id.resultsScrollContainer)
        val recentScrollContainer = findViewById<ScrollView>(R.id.scrollContainer)

        // Set up navigation buttons
        val gotoHome = findViewById<ImageButton>(R.id.HomePage)
        gotoHome.setOnClickListener {
            showScreen4()
        }
        val gotoSearch = findViewById<ImageButton>(R.id.SearchPage)
        gotoSearch.setOnClickListener {
            showScreen14()
        }
        val gotoPost = findViewById<ImageButton>(R.id.PostPage)
        gotoPost.setOnClickListener {
            showScreen15()
        }
        val gotoProfile = findViewById<ImageButton>(R.id.ProfilePage)
        gotoProfile.setOnClickListener {
            showScreen10()
        }
        val gotoContacts = findViewById<ImageButton>(R.id.ContactsPage)
        gotoContacts.setOnClickListener {
            showScreen18()
        }

        // Load recent searches from SharedPreferences
        loadRecentSearches(recentSearchesContainer)

        // Set up search functionality
        searchButton.setOnClickListener {
            val searchQuery = searchEditText.text.toString().trim()
            if (searchQuery.isNotEmpty()) {
                // Show loading indicator
                Toast.makeText(this, "Searching for users...", Toast.LENGTH_SHORT).show()

                // Add to recent searches
                addToRecentSearches(searchQuery)

                // Perform search
                searchUsers(
                    searchQuery,
                    searchResultsContainer,
                    recentSearchesContainer,
                    recentSearchesText,
                    resultsScrollContainer,
                    recentScrollContainer
                )
            } else {
                Toast.makeText(this, "Please enter a username to search", Toast.LENGTH_SHORT).show()
            }
        }

        // Also trigger search when user presses enter/done on keyboard
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                searchButton.performClick()
                return@setOnEditorActionListener true
            }
            false
        }
    }

    // Function to search for users in Firebase
    private fun searchUsers(
        query: String,
        resultsContainer: LinearLayout,
        recentSearchesContainer: LinearLayout,
        recentSearchesText: TextView,
        resultsScrollContainer: ScrollView,
        recentScrollContainer: ScrollView
    ) {
        // Clear previous results
        resultsContainer.removeAllViews()

        // Show results container, hide recent searches
        resultsScrollContainer.visibility = View.VISIBLE
        recentScrollContainer.visibility = View.GONE
        recentSearchesText.text = "Search Results"

        // Get reference to Firebase database
        val database = FirebaseDatabase.getInstance()
        val usersRef = database.getReference("Users")

        Log.d("SearchUsers", "Starting search for query: $query")

        // Perform the search
        usersRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                Log.d("SearchUsers", "Data snapshot received, children count: ${snapshot.childrenCount}")

                var resultsFound = false

                // Debug: Print all users in the database
                for (userSnapshot in snapshot.children) {
                    val username = userSnapshot.key
                    val userData = userSnapshot.value as? Map<*, *>
                    val name = userData?.get("name")?.toString() ?: "Unknown"

                    Log.d("SearchUsers", "User in database: username=$username, name=$name")
                }

                // Now search for matching users
                for (userSnapshot in snapshot.children) {
                    val username = userSnapshot.key ?: continue
                    val userData = userSnapshot.value as? Map<*, *> ?: continue
                    val name = userData["name"]?.toString() ?: "Unknown"

                    Log.d("SearchUsers", "Checking user: username=$username, name=$name")

                    // Check if username or name contains the search query (case-insensitive)
                    if (username.contains(query, ignoreCase = true) ||
                        name.contains(query, ignoreCase = true)) {

                        Log.d("SearchUsers", "Match found: username=$username, name=$name")

                        // Create a user item view
                        val userItemView = createUserItemView(username, name)
                        resultsContainer.addView(userItemView)
                        resultsFound = true
                    }
                }

                if (!resultsFound) {
                    // No results found
                    Log.d("SearchUsers", "No results found for query: $query")

                    val noResultsText = TextView(this@MainActivity).apply {
                        text = "No users found matching '$query'"
                        textSize = 16f
                        setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                        setTextColor(android.graphics.Color.GRAY)
                    }
                    resultsContainer.addView(noResultsText)
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e("SearchUsers", "Search cancelled with error: ${error.message}")

                Toast.makeText(this@MainActivity, "Search failed: ${error.message}",
                    Toast.LENGTH_SHORT).show()

                // Show recent searches again if search fails
                resultsScrollContainer.visibility = View.GONE
                recentScrollContainer.visibility = View.VISIBLE
                recentSearchesText.text = "Recent Searches"
            }
        })
    }

    // Function to create a user item view for search results
    // Function to create a user item view for search results with a Follow button
    private fun createUserItemView(username: String, name: String): View {
        // Create a horizontal LinearLayout
        val userItemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Create profile image (placeholder)
        val profileImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(40.dpToPx(), 40.dpToPx())
            setImageResource(R.drawable.profile_pictures_border) // Use a placeholder image
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        // Create text container for name and username
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            setPadding(16.dpToPx(), 0, 0, 0)
        }

        // Create name TextView
        val nameTextView = TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(android.graphics.Color.BLACK)
        }

        // Create username TextView
        val usernameTextView = TextView(this).apply {
            text = "@$username"
            textSize = 14f
            setTextColor(android.graphics.Color.GRAY)
        }

        // Add TextViews to the text container
        textContainer.addView(nameTextView)
        textContainer.addView(usernameTextView)

        // Create Follow button
        val followButton = Button(this).apply {
            text = "Follow"
            textSize = 12f
            setPadding(12.dpToPx(), 4.dpToPx(), 12.dpToPx(), 4.dpToPx())
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.btn_default)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Get current user's username
        val currentUsername = sharedPreferences.getString(KEY_USERNAME, "") ?: ""

        // Don't show follow button for the current user
        if (username == currentUsername) {
            followButton.visibility = View.GONE
        } else {
            // Check if already following this user
            checkFollowStatus(currentUsername, username, followButton)
        }

        // Set click listener for the Follow button
        followButton.setOnClickListener {
            if (followButton.text == "Follow") {
                // Send follow request
                sendFollowRequest(currentUsername, username, followButton)
            } else if (followButton.text == "Unfollow") {
                // Unfollow the user
                unfollowUser(currentUsername, username, followButton)
            } else if (followButton.text == "Requested") {
                // Cancel follow request
                cancelFollowRequest(currentUsername, username, followButton)
            }
        }

        // Add views to the main layout
        userItemLayout.addView(profileImageView)
        userItemLayout.addView(textContainer)
        userItemLayout.addView(followButton)

        // Set click listener on the layout (excluding the button) to view the user's profile
        val clickListener = View.OnClickListener {
            viewUserProfile(username)
        }

        profileImageView.setOnClickListener(clickListener)
        textContainer.setOnClickListener(clickListener)

        return userItemLayout
    }
    private fun checkFollowStatus(currentUsername: String, targetUsername: String, followButton: Button) {
        if (currentUsername.isEmpty()) {
            followButton.visibility = View.GONE
            return
        }

        val database = FirebaseDatabase.getInstance()

        // First check if there's a pending follow request
        val requestsRef = database.getReference("FollowRequests")
        requestsRef.child(targetUsername).child(currentUsername).addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (snapshot.exists()) {
                    // There's a pending request
                    followButton.text = "Requested"
                    return
                }

                // If no pending request, check if already following
                val followersRef = database.getReference("Followers")
                followersRef.child(targetUsername).child(currentUsername).addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        if (snapshot.exists()) {
                            // Already following
                            followButton.text = "Unfollow"
                        } else {
                            // Not following
                            followButton.text = "Follow"
                        }
                    }

                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        Log.e("FollowStatus", "Error checking follow status: ${error.message}")
                        followButton.text = "Follow"
                    }
                })
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e("FollowStatus", "Error checking request status: ${error.message}")
                followButton.text = "Follow"
            }
        })
    }

    // Function to send a follow request
    private fun sendFollowRequest(fromUsername: String, toUsername: String, followButton: Button) {
        if (fromUsername.isEmpty()) {
            Toast.makeText(this, "You need to be logged in to follow users", Toast.LENGTH_SHORT).show()
            return
        }

        val database = FirebaseDatabase.getInstance()
        val requestsRef = database.getReference("FollowRequests")

        // Get current timestamp
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // Create request data
        val requestData = mapOf(
            "timestamp" to timestamp,
            "status" to "pending"
        )

        // Add the request to Firebase
        requestsRef.child(toUsername).child(fromUsername).setValue(requestData)
            .addOnSuccessListener {
                // Update button state
                followButton.text = "Requested"
                Toast.makeText(this, "Follow request sent", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send request: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Function to cancel a follow request
    private fun cancelFollowRequest(fromUsername: String, toUsername: String, followButton: Button) {
        if (fromUsername.isEmpty()) return

        val database = FirebaseDatabase.getInstance()
        val requestsRef = database.getReference("FollowRequests")

        // Remove the request from Firebase
        requestsRef.child(toUsername).child(fromUsername).removeValue()
            .addOnSuccessListener {
                // Update button state
                followButton.text = "Follow"
                Toast.makeText(this, "Follow request cancelled", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to cancel request: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Function to unfollow a user
    private fun unfollowUser(fromUsername: String, toUsername: String, followButton: Button) {
        if (fromUsername.isEmpty()) return

        val database = FirebaseDatabase.getInstance()
        val followersRef = database.getReference("Followers")
        val followingRef = database.getReference("Following")

        // Remove from followers list
        followersRef.child(toUsername).child(fromUsername).removeValue()

        // Remove from following list
        followingRef.child(fromUsername).child(toUsername).removeValue()
            .addOnSuccessListener {
                // Update button state
                followButton.text = "Follow"
                Toast.makeText(this, "Unfollowed $toUsername", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to unfollow: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    // Function to view another user's profile
    private fun viewUserProfile(username: String) {
        // For now, just show a toast
        Toast.makeText(this, "Viewing profile of $username", Toast.LENGTH_SHORT).show()

        // TODO: Implement a screen to view another user's profile
        // This would be similar to showScreen10 but for a different user
    }
    // Function to manage recent searches
    private fun addToRecentSearches(query: String) {
        // Get existing recent searches
        val recentSearches = getRecentSearches().toMutableList()

        // Remove the query if it already exists (to avoid duplicates)
        recentSearches.remove(query)

        // Add the new query at the beginning
        recentSearches.add(0, query)

        // Keep only the most recent 5 searches
        val trimmedList = recentSearches.take(5)

        // Save the updated list
        val editor = sharedPreferences.edit()
        editor.putString("recent_searches", trimmedList.joinToString(","))
        editor.apply()
    }

    // Function to get recent searches from SharedPreferences
    private fun getRecentSearches(): List<String> {
        val recentSearchesString = sharedPreferences.getString("recent_searches", "")
        return if (recentSearchesString.isNullOrEmpty()) {
            emptyList()
        } else {
            recentSearchesString.split(",")
        }
    }

    // Function to load and display recent searches
    private fun loadRecentSearches(container: LinearLayout) {
        // Clear existing views
        container.removeAllViews()

        // Get recent searches
        val recentSearches = getRecentSearches()

        // If there are no recent searches, show a message
        if (recentSearches.isEmpty()) {
            val noSearchesText = TextView(this).apply {
                text = "No recent searches"
                textSize = 16f
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                setTextColor(android.graphics.Color.GRAY)
            }
            container.addView(noSearchesText)
            return
        }

        // Add each recent search to the container
        for (search in recentSearches) {
            // Create a horizontal layout for each item
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            // Create text view for the search query
            val searchTextView = TextView(this).apply {
                text = search
                textSize = 16f
                setTextColor(android.graphics.Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
                )
            }

            // Create delete button
            val deleteButton = ImageView(this).apply {
                setImageResource(R.drawable.cross_icon)
                layoutParams = LinearLayout.LayoutParams(
                    25.dpToPx(),
                    25.dpToPx()
                )
                setPadding(0, 0, 10.dpToPx(), 0)
            }

            // Set click listener for the delete button
            deleteButton.setOnClickListener {
                removeRecentSearch(search)
                loadRecentSearches(container) // Reload the list
            }

            // Set click listener for the search text to perform the search again
            searchTextView.setOnClickListener {
                // Get the search EditText and set its text
                val searchEditText = findViewById<EditText>(R.id.searchEditText)
                searchEditText.setText(search)

                // Trigger the search
                findViewById<ImageView>(R.id.searchButton).performClick()
            }

            // Add views to the layout
            itemLayout.addView(searchTextView)
            itemLayout.addView(deleteButton)

            // Add the item to the container
            container.addView(itemLayout)
        }
    }

    // Function to remove a recent search
    private fun removeRecentSearch(query: String) {
        // Get existing recent searches
        val recentSearches = getRecentSearches().toMutableList()

        // Remove the query
        recentSearches.remove(query)

        // Save the updated list
        val editor = sharedPreferences.edit()
        editor.putString("recent_searches", recentSearches.joinToString(","))
        editor.apply()

        Toast.makeText(this, "Removed from recent searches", Toast.LENGTH_SHORT).show()
    }

    // If you want to support multiple images (since your UI shows horizontally scrollable images)
    private var selectedImageUris: ArrayList<android.net.Uri> = ArrayList()

    // Modify showScreen15 function to reset image selections when entering the screen
    private var multiSelectMode = false
    private val selectedPositions = HashSet<Int>()

    // Modify your showScreen15() function to change the galleryIcon click behavior
    private fun showScreen15() {
        setContentView(R.layout.screen15)

        // Reset selected images when entering this screen
        selectedImageUris.clear()
        selectedImageUri = null
        multiSelectMode = false
        selectedPositions.clear()

        val closePost = findViewById<ImageView>(R.id.btnClose)
        closePost.setOnClickListener {
            showScreen4()
        }

        val nextPost = findViewById<TextView>(R.id.btnNext)
        nextPost.setOnClickListener {
            // Only proceed if at least one image is selected
            if (selectedImageUris.isNotEmpty()) {
                showScreen17()
            } else {
                Toast.makeText(this, "Please select at least one image", Toast.LENGTH_SHORT).show()
            }
        }

        // Check and request permissions
        if (checkAndRequestPermissions()) {
            // Load gallery images
            loadGalleryImages()
        }

        // Set click listener for gallery icon - now toggles multi-select mode
        val galleryIcon = findViewById<ImageView>(R.id.galleryIcon)
        galleryIcon.setOnClickListener {
            // Toggle multi-select mode
            multiSelectMode = !multiSelectMode

            // Update UI to indicate multi-select mode
            if (multiSelectMode) {
                Toast.makeText(this, "Multi-select mode enabled. Tap images to select.", Toast.LENGTH_SHORT).show()
                galleryIcon.alpha = 0.6f  // Visual indicator that multi-select is active
            } else {
                Toast.makeText(this, "Multi-select mode disabled", Toast.LENGTH_SHORT).show()
                galleryIcon.alpha = 1.0f
            }

            // Refresh the gallery to update the UI
            loadGalleryImages()
        }

        // Set click listener for camera icon
        val cameraIcon = findViewById<ImageView>(R.id.cameraIcon)
        cameraIcon.setOnClickListener {
            // Open camera intent
            if (checkCameraPermission()) {
                openCamera()
            }
        }
    }
    private fun showScreen16() {
        setContentView(R.layout.screen16)

        val ClosePost = findViewById<ImageView>(R.id.btnClose)
        ClosePost.setOnClickListener {
            showScreen4()
        }

        val NextPost = findViewById<TextView>(R.id.btnNext)
        NextPost.setOnClickListener {
            showScreen17()
        }
        val username = sharedPreferences.getString(KEY_USERNAME, null) ?: return

        val intent = Intent(this, Screen16Activity::class.java)
        intent.putExtra("USERNAME", username)
        startActivity(intent)
    }


    // Add this function to convert images to Base64
    private fun uriToBase64(uri: android.net.Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    // Modify the showScreen17 function to add Firebase post functionality
    private fun showScreen17() {
        setContentView(R.layout.screen17)

        val closePost = findViewById<ImageView>(R.id.btnClose)
        closePost.setOnClickListener {
            showScreen4()
        }

        // Show the selected images in the horizontal scroll view
        val scrollView = findViewById<HorizontalScrollView>(R.id.postSection)
        val linearLayout = scrollView.getChildAt(0) as LinearLayout
        linearLayout.removeAllViews()

        // Display selected images
        if (selectedImageUris.isNotEmpty()) {
            for (uri in selectedImageUris) {
                val imageView = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        250.dpToPx(),
                        320.dpToPx()
                    ).apply {
                        leftMargin = if (linearLayout.childCount == 0) 30.dpToPx() else 20.dpToPx()
                    }
                    clipToOutline = true
                    background = ContextCompat.getDrawable(this@MainActivity, R.drawable.viewprofile_button)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }

                Glide.with(this)
                    .load(uri)
                    .centerCrop()
                    .into(imageView)

                linearLayout.addView(imageView)
            }
        } else if (selectedImageUri != null) {
            // Fallback to single image
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    250.dpToPx(),
                    320.dpToPx()
                ).apply {
                    leftMargin = 30.dpToPx()
                }
                clipToOutline = true
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.viewprofile_button)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            Glide.with(this)
                .load(selectedImageUri)
                .centerCrop()
                .into(imageView)

            linearLayout.addView(imageView)
        }

        // Implement share button functionality
        val sharePost = findViewById<Button>(R.id.btnShare)
        sharePost.setOnClickListener {
            // Get caption text
            val captionView = findViewById<TextView>(R.id.description)
            val caption = captionView.text.toString()

            // Check if it's the default text and replace with empty string if it is
            val finalCaption = if (caption == "Add a caption...") "" else caption

            // Show loading message
            Toast.makeText(this, "Uploading post...", Toast.LENGTH_SHORT).show()

            // Upload the post to Firebase
            uploadPostToFirebase(finalCaption)
        }
    }

    private fun uploadPostToFirebase(caption: String) {
        try {
            // Get current logged in username
            val username = sharedPreferences.getString(KEY_USERNAME, "") ?: ""
            if (username.isEmpty()) {
                Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
                return
            }

            // Create database reference
            val database = FirebaseDatabase.getInstance()
            val postsRef = database.getReference("Posts")

            // Generate a unique ID for this post
            val postId = UUID.randomUUID().toString()

            // Get current timestamp
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            // Prepare images list
            val imagesList = ArrayList<String>()

            // Convert images to base64
            if (selectedImageUris.isNotEmpty()) {
                // Convert all selected images to base64
                for (uri in selectedImageUris) {
                    try {
                        val base64Image = uriToBase64(uri)
                        imagesList.add(base64Image)
                    } catch (e: Exception) {
                        Log.e("UploadPost", "Error converting image: ${e.message}")
                    }
                }
            } else if (selectedImageUri != null) {
                // Convert single image to base64
                try {
                    val base64Image = uriToBase64(selectedImageUri!!)
                    imagesList.add(base64Image)
                } catch (e: Exception) {
                    Log.e("UploadPost", "Error converting single image: ${e.message}")
                }
            }

            if (imagesList.isEmpty()) {
                Toast.makeText(this, "No images to upload", Toast.LENGTH_SHORT).show()
                return
            }

            // Create post data structure
            val postMap = mapOf(
                "username" to username,
                "caption" to caption,
                "timestamp" to timestamp,
                "images" to imagesList,
                "likes" to 0,
                "comments" to ArrayList<String>()
            )

            // Save to Firebase
            postsRef.child(postId).setValue(postMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Post shared successfully!", Toast.LENGTH_SHORT).show()
                    showScreen4()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error sharing post: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e("UploadPost", "Error: ${e.message}")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to like a post
    private fun likePost(postId: String, postOwner: String) {
        val currentUsername = sharedPreferences.getString(KEY_USERNAME, "") ?: ""
        if (currentUsername.isEmpty()) {
            Toast.makeText(this, "You need to be logged in to like posts", Toast.LENGTH_SHORT).show()
            return
        }

        val database = FirebaseDatabase.getInstance()
        val likesRef = database.getReference("PostLikes").child(postId).child(currentUsername)

        // Check if user already liked this post
        likesRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (snapshot.exists()) {
                    // User already liked this post, so unlike it
                    likesRef.removeValue()
                        .addOnSuccessListener {
                            Toast.makeText(this@MainActivity, "Post unliked", Toast.LENGTH_SHORT).show()
                            updatePostLikeCount(postId, false)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this@MainActivity, "Failed to unlike post: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    // User hasn't liked this post yet, so like it
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    likesRef.setValue(timestamp)
                        .addOnSuccessListener {
                            Toast.makeText(this@MainActivity, "Post liked", Toast.LENGTH_SHORT).show()
                            updatePostLikeCount(postId, true)

                            // Show heart animation (optional)
                            showHeartAnimation()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this@MainActivity, "Failed to like post: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Toast.makeText(this@MainActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Function to update post like count
    private fun updatePostLikeCount(postId: String, increment: Boolean) {
        val database = FirebaseDatabase.getInstance()
        val postRef = database.getReference("Posts").child(postId)

        // Use a transaction to safely update the like count
        postRef.runTransaction(object : com.google.firebase.database.Transaction.Handler {
            override fun doTransaction(mutableData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                val post = mutableData.getValue(object : com.google.firebase.database.GenericTypeIndicator<HashMap<String, Any>>() {})
                if (post == null) {
                    return com.google.firebase.database.Transaction.abort()
                }

                // Get current like count
                var likeCount = 0
                if (post.containsKey("likes")) {
                    val currentLikes = post["likes"]
                    if (currentLikes is Long) {
                        likeCount = currentLikes.toInt()
                    } else if (currentLikes is Int) {
                        likeCount = currentLikes
                    }
                }

                // Update like count
                if (increment) {
                    likeCount++
                } else if (likeCount > 0) {
                    likeCount--
                }

                // Update the post data
                post["likes"] = likeCount
                mutableData.value = post

                return com.google.firebase.database.Transaction.success(mutableData)
            }

            override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, dataSnapshot: com.google.firebase.database.DataSnapshot?) {
                if (error != null) {
                    Log.e("UpdateLikeCount", "Error updating like count: ${error.message}")
                }
            }
        })
    }

    // Function to show heart animation when liking a post
    private fun showHeartAnimation() {
        try {
            // This is a simple implementation - in a real app, you might want to show an animation
            // at the exact position where the user double-tapped
            val rootView = window.decorView.findViewById<ViewGroup>(android.R.id.content)

            // Create a heart ImageView
            val heartView = ImageView(this).apply {
                setImageResource(R.drawable.like_icon) // Use your heart icon
                layoutParams = RelativeLayout.LayoutParams(100.dpToPx(), 100.dpToPx()).apply {
                    addRule(RelativeLayout.CENTER_IN_PARENT)
                }
                alpha = 0f
            }

            // Add to root view
            rootView.addView(heartView)

            // Animate the heart
            heartView.animate()
                .alpha(1f)
                .scaleX(1.5f)
                .scaleY(1.5f)
                .setDuration(300)
                .withEndAction {
                    heartView.animate()
                        .alpha(0f)
                        .scaleX(0.5f)
                        .scaleY(0.5f)
                        .setDuration(300)
                        .withEndAction {
                            rootView.removeView(heartView)
                        }
                        .start()
                }
                .start()
        } catch (e: Exception) {
            Log.e("HeartAnimation", "Error showing heart animation: ${e.message}")
        }
    }

    // Function to show comment dialog
    private fun showCommentDialog(postId: String, postOwner: String) {
        val currentUsername = sharedPreferences.getString(KEY_USERNAME, "") ?: ""
        if (currentUsername.isEmpty()) {
            Toast.makeText(this, "You need to be logged in to comment", Toast.LENGTH_SHORT).show()
            return
        }

        // Create dialog
        val dialog = android.app.AlertDialog.Builder(this)
        dialog.setTitle("Add Comment")

        // Create layout for dialog
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dpToPx(), 10.dpToPx(), 20.dpToPx(), 10.dpToPx())
        }

        // Create comment input
        val commentInput = EditText(this).apply {
            hint = "Write your comment..."
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        layout.addView(commentInput)
        dialog.setView(layout)

        // Add buttons
        dialog.setPositiveButton("Post") { _, _ ->
            val commentText = commentInput.text.toString().trim()
            if (commentText.isNotEmpty()) {
                addCommentToPost(postId, postOwner, commentText)
            } else {
                Toast.makeText(this, "Comment cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.setNegativeButton("Cancel") { dialogInterface, _ ->
            dialogInterface.dismiss()
        }

        // Show dialog
        dialog.show()
    }

    // Function to add comment to post
    private fun addCommentToPost(postId: String, postOwner: String, commentText: String) {
        val currentUsername = sharedPreferences.getString(KEY_USERNAME, "") ?: ""
        val currentName = sharedPreferences.getString(KEY_NAME, "") ?: ""

        if (currentUsername.isEmpty()) return

        val database = FirebaseDatabase.getInstance()
        val commentsRef = database.getReference("PostComments").child(postId)

        // Generate a unique comment ID
        val commentId = commentsRef.push().key ?: UUID.randomUUID().toString()

        // Get current timestamp
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // Create comment data
        val commentData = mapOf(
            "commentId" to commentId,
            "username" to currentUsername,
            "name" to currentName,
            "text" to commentText,
            "timestamp" to timestamp
        )

        // Save comment to Firebase
        commentsRef.child(commentId).setValue(commentData)
            .addOnSuccessListener {
                Toast.makeText(this, "Comment added", Toast.LENGTH_SHORT).show()

                // Update comment count on the post
                updatePostCommentCount(postId)

                // Optionally notify the post owner about the new comment
                if (currentUsername != postOwner) {
                    addCommentNotification(postId, postOwner, commentText)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to add comment: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Function to update post comment count
    private fun updatePostCommentCount(postId: String) {
        val database = FirebaseDatabase.getInstance()
        val commentsRef = database.getReference("PostComments").child(postId)

        commentsRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val commentCount = snapshot.childrenCount.toInt()

                // Update the post with the new comment count
                val postRef = database.getReference("Posts").child(postId)
                postRef.child("commentCount").setValue(commentCount)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e("UpdateCommentCount", "Error updating comment count: ${error.message}")
            }
        })
    }

    // Function to add notification for a new comment
    private fun addCommentNotification(postId: String, postOwner: String, commentText: String) {
        val currentUsername = sharedPreferences.getString(KEY_USERNAME, "") ?: ""
        val currentName = sharedPreferences.getString(KEY_NAME, "") ?: ""

        if (currentUsername.isEmpty() || postOwner.isEmpty()) return

        val database = FirebaseDatabase.getInstance()
        val notificationsRef = database.getReference("Notifications").child(postOwner)

        // Generate a unique notification ID
        val notificationId = notificationsRef.push().key ?: UUID.randomUUID().toString()

        // Get current timestamp
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // Create notification data
        val notificationData = mapOf(
            "notificationId" to notificationId,
            "type" to "comment",
            "postId" to postId,
            "fromUsername" to currentUsername,
            "fromName" to currentName,
            "text" to commentText,
            "timestamp" to timestamp,
            "read" to false
        )

        // Save notification to Firebase
        notificationsRef.child(notificationId).setValue(notificationData)
            .addOnFailureListener { e ->
                Log.e("CommentNotification", "Error adding notification: ${e.message}")
            }
    }

    // Function to view post comments
    private fun viewPostComments(postId: String) {
        // This would open a new screen or dialog showing all comments for the post
        Toast.makeText(this, "Viewing comments for post $postId", Toast.LENGTH_SHORT).show()

        // TODO: Implement a screen to view all comments for a post
    }
    private fun showScreen18() {
        setContentView(R.layout.screen18)

        val backButton = findViewById<ImageView>(R.id.BackButton)
        backButton.setOnClickListener {
            showScreen4()
        }

        val gotoHome = findViewById<ImageButton>(R.id.HomePage)
        gotoHome.setOnClickListener {
            showScreen4()
        }
        val gotoSearch = findViewById<ImageButton>(R.id.SearchPage)
        gotoSearch.setOnClickListener {
            showScreen14()
        }
        val gotoPost = findViewById<ImageButton>(R.id.PostPage)
        gotoPost.setOnClickListener {
            showScreen15()
        }
        val gotoProfile = findViewById<ImageButton>(R.id.ProfilePage)
        gotoProfile.setOnClickListener {
            showScreen10()
        }
        val gotoContacts = findViewById<ImageButton>(R.id.ContactsPage)
        gotoContacts.setOnClickListener {
            showScreen18()
        }
    }

    private val PERMISSION_REQUEST_CODE = 200
    private val PICK_IMAGE_REQUEST = 1
    private val CAMERA_REQUEST = 2

    private fun checkAndRequestPermissions(): Boolean {
        val permissions = ArrayList<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // Below Android 13
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
            return false
        }

        return true
    }

    private fun checkCameraPermission(): Boolean {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST_CODE)
            return false
        }
        return true
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(cameraIntent, CAMERA_REQUEST)
    }

    private fun loadGalleryImages() {
        try {
            // Get the GridLayout
            val gridLayout = findViewById<GridLayout>(R.id.galleryGrid)
            if (gridLayout == null) {
                Log.e("MainActivity", "GridLayout not found")
                return
            }

            gridLayout.removeAllViews() // Clear any existing views

            // Get gallery images
            val cursor = getImageCursor() ?: return

            if (cursor.moveToFirst()) {
                val columnIndexId = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

                // Calculate number of columns based on screen width
                val columnCount = 3
                gridLayout.columnCount = columnCount

                // Load up to 12 images
                var count = 0
                val imageViewList = ArrayList<ImageView>()

                do {
                    try {
                        val imageId = cursor.getLong(columnIndexId)
                        val imageUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId)

                        // Create an ImageView programmatically
                        val imageView = ImageView(this).apply {
                            val params = GridLayout.LayoutParams()
                            params.width = resources.displayMetrics.widthPixels / columnCount - 10.dpToPx()
                            params.height = resources.displayMetrics.widthPixels / columnCount - 10.dpToPx()
                            params.setMargins(3.dpToPx(), 3.dpToPx(), 3.dpToPx(), 3.dpToPx())
                            layoutParams = params

                            scaleType = ImageView.ScaleType.CENTER_CROP
                            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.profile_pictures_border)

                            // Set tag with the URI for later retrieval
                            tag = imageUri.toString()

                            // Set position tag for tracking selection
                            setTag(R.id.tag_position, count)

                            // If this image is in our selected list, show it as selected
                            alpha = if (selectedImageUris.contains(imageUri) || selectedPositions.contains(count)) 0.6f else 1.0f
                        }

                        // Load image using Glide
                        Glide.with(this)
                            .load(imageUri)
                            .centerCrop()
                            .into(imageView)

                        // Set click listener after loading the image
                        imageView.setOnClickListener { view ->
                            try {
                                // Get URI from tag
                                val clickedUri = android.net.Uri.parse(view.tag as String)
                                val position = view.getTag(R.id.tag_position) as Int

                                // Find the selectedImage view for preview
                                val selectedImageView = findViewById<ImageView>(R.id.selectedImage)

                                if (multiSelectMode) {
                                    // In multi-select mode, toggle selection
                                    if (selectedImageUris.contains(clickedUri)) {
                                        // Deselect
                                        selectedImageUris.remove(clickedUri)
                                        selectedPositions.remove(position)
                                        view.alpha = 1.0f
                                    } else {
                                        // Select
                                        selectedImageUris.add(clickedUri)
                                        selectedPositions.add(position)
                                        view.alpha = 0.6f
                                    }

                                    // Update the count
                                    Toast.makeText(this@MainActivity, "${selectedImageUris.size} images selected", Toast.LENGTH_SHORT).show()

                                    // Update preview with the last selected image if any
                                    if (selectedImageUris.isNotEmpty()) {
                                        selectedImageUri = selectedImageUris.last()
                                        Glide.with(this@MainActivity)
                                            .load(selectedImageUri)
                                            .centerCrop()
                                            .into(selectedImageView)
                                    } else {
                                        // Clear preview if no images selected
                                        selectedImageView.setImageDrawable(null)
                                        selectedImageUri = null
                                    }
                                } else {
                                    // In single-select mode, just select this one image
                                    selectedImageUri = clickedUri

                                    // Clear existing selections
                                    selectedImageUris.clear()
                                    selectedPositions.clear()

                                    // Add this as the only selection
                                    selectedImageUris.add(clickedUri)
                                    selectedPositions.add(position)

                                    // Update the preview
                                    Glide.with(this@MainActivity)
                                        .load(clickedUri)
                                        .centerCrop()
                                        .into(selectedImageView)

                                    // Reset alpha for all thumbnails
                                    for (iv in imageViewList) {
                                        iv.alpha = 1.0f
                                    }

                                    // Highlight only the selected thumbnail
                                    view.alpha = 0.6f
                                }

                            } catch (e: Exception) {
                                Log.e("MainActivity", "Error in click handler: ${e.message}")
                                e.printStackTrace()
                            }
                        }

                        imageViewList.add(imageView)
                        gridLayout.addView(imageView)

                        // If it's the first image and we have no selections, select it by default
                        if (count == 0 && selectedImageUris.isEmpty() && !multiSelectMode) {
                            // Get the selected image view
                            val selectedImageView = findViewById<ImageView>(R.id.selectedImage)
                            if (selectedImageView != null) {
                                selectedImageUri = imageUri
                                selectedImageUris.add(imageUri)
                                selectedPositions.add(count)

                                // Load the image into the selected image view
                                Glide.with(this)
                                    .load(imageUri)
                                    .centerCrop()
                                    .into(selectedImageView)

                                imageView.alpha = 0.6f
                            }
                        }

                        count++
                        if (count >= 12) break

                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error processing gallery image: ${e.message}")
                        e.printStackTrace()
                        // Continue to the next image instead of breaking the entire loop
                    }
                } while (cursor.moveToNext())

                // Close the cursor after using it
                cursor.close()
            } else {
                cursor.close()
                Log.e("MainActivity", "No images found in cursor")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading gallery: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Error loading gallery: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    // Add this method to help with debugging
    private fun logImageInfo(imageView: ImageView, uri: android.net.Uri) {
        try {
            Log.d("ImageDebug", "ImageView dimensions: ${imageView.width}x${imageView.height}")
            Log.d("ImageDebug", "URI: $uri")

            // Try to get some info about the image
            val projection = arrayOf(MediaStore.Images.Media.SIZE, MediaStore.Images.Media.DISPLAY_NAME)
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                    val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)

                    if (sizeIndex >= 0 && nameIndex >= 0) {
                        val size = cursor.getLong(sizeIndex)
                        val name = cursor.getString(nameIndex)
                        Log.d("ImageDebug", "Image name: $name, size: ${size/1024}KB")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ImageDebug", "Error logging image info: ${e.message}")
        }
    }

// Add these imports at the top of your file:
// import android.util.Log

    private fun getImageCursor(): Cursor? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )

        // Sort by most recent first
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        return try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Error accessing gallery: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    // Extension function to convert dp to pixels
    private fun Int.dpToPx(): Int {
        val density = resources.displayMetrics.density
        return (this * density).toInt()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Permissions granted, load gallery images
                loadGalleryImages()
            } else {
                Toast.makeText(this, "Permissions required to access images", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                PROFILE_IMAGE_REQUEST -> {
                    // Handle profile image selection
                    data?.data?.let { uri ->
                        val profileImageView = findViewById<ImageView>(R.id.profileImage)

                        // Load the selected image
                        Glide.with(this)
                            .load(uri)
                            .centerCrop()
                            .into(profileImageView)

                        // Reset the alpha to make the image fully visible
                        profileImageView.alpha = 1.0f

                        // Here you would typically upload the image to Firebase Storage
                        // and update the user's profile with the image URL
                        // For simplicity, we're just showing it locally for now
                        Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()
                    }
                }
                PICK_IMAGE_REQUEST -> {
                    // Check if multiple images were selected
                    if (data?.clipData != null) {
                        val clipData = data.clipData
                        val count = clipData?.itemCount ?: 0

                        // Clear previous selections if you want to replace them
                        // Or comment this line if you want to add to existing selections
                        selectedImageUris.clear()

                        // Add all selected images to our list
                        for (i in 0 until count) {
                            val imageUri = clipData?.getItemAt(i)?.uri
                            if (imageUri != null && !selectedImageUris.contains(imageUri)) {
                                selectedImageUris.add(imageUri)
                            }
                        }

                        // Set the first image as the preview if available
                        if (selectedImageUris.isNotEmpty()) {
                            selectedImageUri = selectedImageUris[0]
                            val selectedImageView = findViewById<ImageView>(R.id.selectedImage)
                            Glide.with(this)
                                .load(selectedImageUri)
                                .centerCrop()
                                .into(selectedImageView)
                        }

                        // Show a counter or indicator of how many images are selected
                        Toast.makeText(this, "${selectedImageUris.size} images selected", Toast.LENGTH_SHORT).show()

                    } else {
                        // Handle single image selection (your existing code)
                        data?.data?.let { uri ->
                            selectedImageUri = uri
                            // Add to our list if we're supporting multiple images
                            if (!selectedImageUris.contains(uri)) {
                                selectedImageUris.add(uri)
                            }

                            val selectedImageView = findViewById<ImageView>(R.id.selectedImage)
                            Glide.with(this)
                                .load(uri)
                                .centerCrop()
                                .into(selectedImageView)
                        }
                    }

                    // Reload gallery with the new image(s) selected
                    loadGalleryImages()
                }
                // Rest of your code...
            }
        }
    }




}

