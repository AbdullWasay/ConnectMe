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
        nextButton.setOnClickListener { uploadStory(username) }
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

    private fun uploadStory(username : String?)
    {
        if (previewImage.drawable == null) {
            Log.e("Upload", "No image to upload")
            return
        }

        previewImage.isDrawingCacheEnabled = true
        previewImage.buildDrawingCache()
        val bitmap = previewImage.drawingCache ?: return
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val base64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)


        if (username == null) {
            Log.e("Firebase", "User not authenticated")
            return
        }

        FirebaseDatabase.getInstance().reference.child("stories").child(username)
            .setValue(Story(base64Image, System.currentTimeMillis()))
            .addOnSuccessListener {
                Log.d("Firebase", "Story uploaded")
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Failed to upload story", e)
            }
    }


    data class Story(val image: String, val timestamp: Long)

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}