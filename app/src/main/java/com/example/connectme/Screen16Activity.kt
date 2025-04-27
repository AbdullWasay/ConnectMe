package com.example.connectme


import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Screen16Activity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var previewImage: ImageView
    private lateinit var captureButton: ImageButton
    private lateinit var galleryButton: ImageButton
    private lateinit var switchCameraButton: ImageView
    private lateinit var nextButton: TextView
    private var isFrontCamera = false
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen16)

        val username = intent.getStringExtra("USERNAME")


        previewView = findViewById(R.id.previewArea)
        previewImage = findViewById(R.id.previewImage)
        captureButton = findViewById(R.id.capture_picture_button)
        galleryButton = findViewById(R.id.galleryButton)
        switchCameraButton = findViewById(R.id.reload_icon)
        nextButton = findViewById(R.id.btnNext)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera(isFrontCamera)
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        switchCameraButton.setOnClickListener { switchCamera() }
        captureButton.setOnClickListener { captureImage() }
        galleryButton.setOnClickListener { openGallery() }
        nextButton.setOnClickListener {
            if (username != null) {
                uploadStory(username)
            } else {
                Toast.makeText(this, "Username not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera(useFrontCamera: Boolean) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(if (useFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK)
                .build()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchCamera() {
        isFrontCamera = !isFrontCamera
        startCamera(isFrontCamera)
    }

    private fun captureImage() {
        val imageCapture = imageCapture ?: return

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "captured_image")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val uri = outputFileResults.savedUri
                    runOnUiThread {
                        previewImage.setImageURI(uri)
                        previewImage.visibility = View.VISIBLE
                        previewView.visibility = View.GONE
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraX", "Photo capture failed: ${exception.message}", exception)
                }
            })
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            previewImage.setImageURI(it)
            previewImage.visibility = View.VISIBLE
            previewView.visibility = View.GONE
        }
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun uploadStory(username: String) {
        // Show loading indicator
        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("Uploading story...")
            setCancelable(false)
            show()
        }

        try {
            if (previewImage.drawable == null) {
                progressDialog.dismiss()
                Toast.makeText(this, "No image to upload", Toast.LENGTH_SHORT).show()
                return
            }

            previewImage.isDrawingCacheEnabled = true
            previewImage.buildDrawingCache()
            val bitmap = previewImage.drawingCache ?: return
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
            val base64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)

            // Get current timestamp
            val timestamp = System.currentTimeMillis()

            // Create story data with expiration time
            // Instead of using .info/ttl (which is invalid), we'll store the expiration time
            // and handle cleanup separately
            val storyData = mapOf(
                "image" to base64Image,
                "timestamp" to timestamp,
                "expiresAt" to (timestamp + 86400000) // 24 hours from now
            )

            // Get reference to Firebase database
            val database = FirebaseDatabase.getInstance()
            val storiesRef = database.getReference("stories")

            // Save story to Firebase
            storiesRef.child(username).setValue(storyData)
                .addOnSuccessListener {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Story uploaded successfully! It will disappear after 24 hours", Toast.LENGTH_SHORT).show()

                    // Return to home screen (Screen 4)
                    // Create an intent to start MainActivity and tell it to show Screen 4
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("SHOW_SCREEN", 4)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    progressDialog.dismiss()
                    Log.e("Firebase", "Failed to upload story", e)
                    Toast.makeText(this, "Failed to upload story: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            progressDialog.dismiss()
            Log.e("UploadStory", "Error: ${e.message}")
            Toast.makeText(this, "Error uploading story: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    data class Story(val image: String, val timestamp: Long)

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}
