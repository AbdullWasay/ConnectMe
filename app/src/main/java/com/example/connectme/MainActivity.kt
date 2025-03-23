package com.example.connectme
import android.content.Intent
import android.os.Bundle
import android.Manifest
import android.app.Activity
import android.content.ContentUris
 import android.util.Log

import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private var selectedImageUri: android.net.Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        setContentView(R.layout.activity_main) // Screen 1 layout

        Handler(Looper.getMainLooper()).postDelayed({
            showScreen2()
        }, 3000)
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
                        Toast.makeText(this, "Signup successful", Toast.LENGTH_SHORT).show()
                        showScreen2()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Signup failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun showScreen4() {
        setContentView(R.layout.screen4) // Screen 4 layout

        val messagesButton = findViewById<ImageView>(R.id.MessagesButtonm)
        messagesButton.setOnClickListener {
            showScreen5()
        }

        val addStory = findViewById<ImageButton>(R.id.AddStory)
        addStory.setOnClickListener {
            showScreen16()
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

    private fun showScreen5() {
        setContentView(R.layout.screen5) // Screen 5 layout

        //Back button
        val backButton = findViewById<ImageView>(R.id.BackButtonm)
        backButton.setOnClickListener {
            showScreen4()
        }

        //Open Chat
        val chat = findViewById<LinearLayout>(R.id.chat2)
        chat.setOnClickListener {
            showScreen6()
        }
    }

    private fun showScreen6() {
        setContentView(R.layout.screen6) // Set layout for Screen 6

        val backButton = findViewById<ImageView>(R.id.BackButton)
        backButton.setOnClickListener {
            showScreen5()
        }

        val callButton = findViewById<ImageView>(R.id.callbutton)
        callButton.setOnClickListener {
            showScreen8()
        }

        val videocallButton = findViewById<ImageView>(R.id.videocallbutton)
        videocallButton.setOnClickListener {
            showScreen9()
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
                            showScreen7()
                            return true
                        }
                    }
                }
                return false
            }
        })
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
                            showScreen6()
                            return true
                        }
                    }
                }
                return false
            }
        })
    }

    private fun showScreen8() {
        setContentView(R.layout.screen8) // Screen 4 layout

        val EndCall = findViewById<ImageView>(R.id.btnEndCall)
        EndCall.setOnClickListener {
            showScreen6()
        }
    }

    private fun showScreen9() {
        setContentView(R.layout.screen9) // Screen 4 layout

        val EndCall = findViewById<ImageView>(R.id.btnEndCall)
        EndCall.setOnClickListener {
            showScreen6()
        }
    }

    private fun showScreen10() {
        setContentView(R.layout.screen10) // Screen 4 layout

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
    }

    private fun showScreen11() {
        setContentView(R.layout.screen11) // Screen 4 layout

        val backButton = findViewById<ImageView>(R.id.BackButton)
        backButton.setOnClickListener {
            showScreen10()
        }

        val followingPage = findViewById<LinearLayout>(R.id.FollowingTab)
        followingPage.setOnClickListener {
            showScreen12()
        }
    }

    private fun showScreen12() {
        setContentView(R.layout.screen12) // Screen 4 layout

        val backButton = findViewById<ImageView>(R.id.BackButton)
        backButton.setOnClickListener {
            showScreen10()
        }

        val followersPage = findViewById<LinearLayout>(R.id.FollowersTab)
        followersPage.setOnClickListener {
            showScreen11()
        }
    }

    private fun showScreen13() {
        setContentView(R.layout.screen13) // Screen 4 layout

        val doneEditing = findViewById<TextView>(R.id.DoneEditing)
        doneEditing.setOnClickListener {
            showScreen10()
        }
    }

    private fun showScreen14() {
        setContentView(R.layout.screen14) // Screen 4 layout

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

    private fun showScreen15() {
        setContentView(R.layout.screen15)

        val closePost = findViewById<ImageView>(R.id.btnClose)
        closePost.setOnClickListener {
            showScreen4()
        }

        val nextPost = findViewById<TextView>(R.id.btnNext)
        nextPost.setOnClickListener {
            showScreen17()
        }

        // Check and request permissions
        if (checkAndRequestPermissions()) {
            // Load gallery images
            loadGalleryImages()
        }

        // Set click listener for gallery icon
        val galleryIcon = findViewById<ImageView>(R.id.galleryIcon)
        galleryIcon.setOnClickListener {
            // Open gallery intent
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
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
    }

    private fun showScreen17() {
        setContentView(R.layout.screen17)

        val ClosePost = findViewById<ImageView>(R.id.btnClose)
        ClosePost.setOnClickListener {
            showScreen4()
        }

        val SharePost = findViewById<TextView>(R.id.btnShare)
        SharePost.setOnClickListener {
            showScreen4()
        }
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

                            // Set click listener for the thumbnail (defining it outside the apply block to avoid nested context issues)
                            setOnClickListener(null) // Clear any previous listeners
                        }

                        // Load image using Glide
                        Glide.with(this)
                            .load(imageUri)
                            .centerCrop()
                            .into(imageView)

                        // Set click listener after loading the image
                        imageView.setOnClickListener { view ->
                            try {
                                // Find the selectedImage view
                                val selectedImageView = findViewById<ImageView>(R.id.selectedImage)
                                if (selectedImageView == null) {
                                    Log.e("MainActivity", "selectedImage view not found")
                                    return@setOnClickListener
                                }

                                // Get URI from tag
                                val clickedUri = android.net.Uri.parse(view.tag as String)

                                // Update selected image
                                selectedImageUri = clickedUri

                                // Use try-catch specifically for the Glide call
                                try {
                                    Glide.with(this@MainActivity)
                                        .load(clickedUri)
                                        .centerCrop()
                                        .into(selectedImageView)
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Error loading image with Glide: ${e.message}")
                                    Toast.makeText(this@MainActivity, "Failed to load selected image", Toast.LENGTH_SHORT).show()
                                }

                                // Reset alpha for all thumbnails
                                for (iv in imageViewList) {
                                    iv.alpha = 1.0f
                                }

                                // Highlight the selected thumbnail
                                view.alpha = 0.6f

                            } catch (e: Exception) {
                                Log.e("MainActivity", "Error in click handler: ${e.message}")
                                e.printStackTrace()
                            }
                        }

                        imageViewList.add(imageView)
                        gridLayout.addView(imageView)

                        // If it's the first image, select it by default
                        if (count == 0) {
                            // Get the selected image view
                            val selectedImageView = findViewById<ImageView>(R.id.selectedImage)
                            if (selectedImageView != null) {
                                selectedImageUri = imageUri

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
                PICK_IMAGE_REQUEST -> {
                    // Handle gallery image selection
                    data?.data?.let { uri ->
                        selectedImageUri = uri
                        val selectedImageView = findViewById<ImageView>(R.id.selectedImage)
                        Glide.with(this)
                            .load(uri)
                            .centerCrop()
                            .into(selectedImageView)

                        // Reload gallery with the new image selected
                        loadGalleryImages()
                    }
                }
                CAMERA_REQUEST -> {
                    // Handle camera image
                    val bitmap = data?.extras?.get("data") as? Bitmap
                    bitmap?.let {
                        val selectedImageView = findViewById<ImageView>(R.id.selectedImage)
                        selectedImageView.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }
}