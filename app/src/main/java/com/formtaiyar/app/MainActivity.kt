package com.formtaiyar.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.formtaiyar.app.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var selectedTemplate: PhotoTemplate? = null
    private var cameraImageUri: Uri? = null

    // Gallery picker
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { launchCrop(it) }
    }

    // Camera capture
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            cameraImageUri?.let { launchCrop(it) }
        }
    }

    // Permission requests
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openCamera()
        else Toast.makeText(this, "Camera permission chahiye", Toast.LENGTH_SHORT).show()
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openGallery()
        else Toast.makeText(this, "Storage permission chahiye", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCards()
    }

    private fun setupCards() {
        // PAN Card
        binding.cardPan.setOnClickListener {
            selectTemplate(Templates.PAN_CARD)
        }

        // Aadhaar / Passport
        binding.cardAadhaar.setOnClickListener {
            selectTemplate(Templates.AADHAAR_PASSPORT)
        }

        // State Exams (SSC)
        binding.cardExam.setOnClickListener {
            selectTemplate(Templates.STATE_EXAM)
        }

        // Custom Resize
        binding.cardCustom.setOnClickListener {
            selectTemplate(Templates.CUSTOM)
        }

        // Source selection buttons
        binding.btnGallery.setOnClickListener {
            checkStoragePermissionAndOpenGallery()
        }

        binding.btnCamera.setOnClickListener {
            checkCameraPermissionAndOpen()
        }

        // Initially hide the source selection panel
        binding.sourceSelectionPanel.visibility = View.GONE
    }

    private fun selectTemplate(template: PhotoTemplate) {
        selectedTemplate = template

        // Highlight selected card
        resetCardHighlights()
        when (template.id) {
            "pan" -> binding.cardPan.strokeWidth = 6
            "aadhaar_passport" -> binding.cardAadhaar.strokeWidth = 6
            "ssc" -> binding.cardExam.strokeWidth = 6
            "custom" -> binding.cardCustom.strokeWidth = 6
        }

        // Update selected info
        binding.tvSelectedTemplate.text = "${template.nameHindi}"
        binding.tvSelectedDimension.text = template.dimensionLabel

        // Show source selection
        binding.sourceSelectionPanel.visibility = View.VISIBLE
    }

    private fun resetCardHighlights() {
        binding.cardPan.strokeWidth = 2
        binding.cardAadhaar.strokeWidth = 2
        binding.cardExam.strokeWidth = 2
        binding.cardCustom.strokeWidth = 2
    }

    private fun checkStoragePermissionAndOpenGallery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                openGallery()
            }
            else -> storagePermissionLauncher.launch(permission)
        }
    }

    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun openCamera() {
        val photoFile = File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        cameraImageUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        cameraLauncher.launch(cameraImageUri)
    }

    private fun launchCrop(imageUri: Uri) {
        val template = selectedTemplate ?: return
        val intent = Intent(this, CropActivity::class.java).apply {
            putExtra(CropActivity.EXTRA_IMAGE_URI, imageUri.toString())
            putExtra(CropActivity.EXTRA_TEMPLATE_ID, template.id)
        }
        startActivity(intent)
    }
}
