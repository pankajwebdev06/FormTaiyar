package com.formtaiyar.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.formtaiyar.app.databinding.ActivityCropBinding
import com.isseiaoki.simplecropview.CropImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CropActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_TEMPLATE_ID = "extra_template_id"
    }

    private lateinit var binding: ActivityCropBinding
    private var template: PhotoTemplate = Templates.PAN_CARD
    private var croppedBitmap: Bitmap? = null
    private var processedFile: File? = null
    private var addWatermark = true

    private var customWidth = 400
    private var customHeight = 400

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCropBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uriString = intent.getStringExtra(EXTRA_IMAGE_URI) ?: run {
            finish()
            return
        }
        val templateId = intent.getStringExtra(EXTRA_TEMPLATE_ID) ?: "pan"

        template = Templates.all.find { it.id == templateId } ?: Templates.PAN_CARD

        supportActionBar?.title = template.nameHindi
        setupUI(Uri.parse(uriString))
    }

    private fun setupUI(imageUri: Uri) {
        // Setup crop view
        val cropView = binding.cropImageView

        // Set aspect ratio based on template
        if (template.id == "custom") {
            cropView.setCropMode(CropImageView.CropMode.FREE)
        } else {
            cropView.setCropMode(CropImageView.CropMode.CUSTOM)
            cropView.setCustomRatio(template.widthPx, template.heightPx)
        }

        cropView.setHandleColor(getColor(R.color.primary_blue))
        cropView.setGuideColor(getColor(R.color.primary_blue))
        cropView.setOverlayColor(getColor(R.color.crop_overlay))
        cropView.setBackgroundColor(getColor(R.color.surface))
        cropView.setHandleSize(14)
        cropView.setTouchPadding(4)

        // Load image
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                ImageProcessor.loadBitmapFromUri(this@CropActivity, imageUri)
            }
            if (bitmap != null) {
                cropView.setImageBitmap(bitmap)
            } else {
                Toast.makeText(this@CropActivity, "Image load nahi ho saka", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        // Template info
        binding.tvTemplateName.text = template.nameHindi
        binding.tvTemplateDimension.text = template.dimensionLabel

        // Custom size panel
        if (template.id == "custom") {
            binding.customSizePanel.visibility = View.VISIBLE
            binding.etCustomWidth.setText(customWidth.toString())
            binding.etCustomHeight.setText(customHeight.toString())
        } else {
            binding.customSizePanel.visibility = View.GONE
        }

        // Quality slider
        binding.seekbarQuality.max = 100
        binding.seekbarQuality.progress = 85
        updateQualityLabel(85)

        binding.seekbarQuality.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val minProgress = 10
                val actualProgress = progress.coerceAtLeast(minProgress)
                if (progress < minProgress) seekBar?.progress = minProgress
                updateQualityLabel(actualProgress)
                // Live preview update if cropped bitmap exists
                croppedBitmap?.let { updatePreviewSize(it, actualProgress) }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        // Watermark toggle
        binding.switchWatermark.isChecked = true
        addWatermark = true
        binding.switchWatermark.setOnCheckedChangeListener { _, isChecked ->
            addWatermark = isChecked
        }

        // Crop button
        binding.btnCrop.setOnClickListener {
            performCrop()
        }

        // Save/Share buttons
        binding.btnSave.setOnClickListener { saveImage() }
        binding.btnShare.setOnClickListener { shareImage() }

        // Crop again
        binding.btnCropAgain.setOnClickListener {
            showCropPanel()
        }

        // Initially show only crop panel
        showCropPanel()
    }

    private fun performCrop() {
        val quality = binding.seekbarQuality.progress.coerceAtLeast(10)

        if (template.id == "custom") {
            val wStr = binding.etCustomWidth.text.toString()
            val hStr = binding.etCustomHeight.text.toString()
            customWidth = wStr.toIntOrNull()?.coerceIn(50, 4000) ?: 400
            customHeight = hStr.toIntOrNull()?.coerceIn(50, 4000) ?: 400
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnCrop.isEnabled = false

        lifecycleScope.launch {
            val cropped = withContext(Dispatchers.IO) {
                binding.cropImageView.croppedBitmap
            }

            if (cropped == null) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCrop.isEnabled = true
                    Toast.makeText(this@CropActivity, "Crop karna zaroori hai!", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val processed = withContext(Dispatchers.IO) {
                ImageProcessor.processImage(
                    context = this@CropActivity,
                    sourceBitmap = cropped,
                    template = template,
                    qualityPercent = quality,
                    addWatermarkFlag = addWatermark,
                    customWidth = customWidth,
                    customHeight = customHeight
                )
            }

            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                binding.btnCrop.isEnabled = true

                if (processed != null) {
                    croppedBitmap = cropped
                    processedFile = processed
                    val sizeKB = ImageProcessor.getFileSizeKB(processed)
                    showPreviewPanel(processed, sizeKB, quality)
                } else {
                    Toast.makeText(this@CropActivity, "Processing failed. Dobara try karein.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showCropPanel() {
        binding.cropPanel.visibility = View.VISIBLE
        binding.previewPanel.visibility = View.GONE
    }

    private fun showPreviewPanel(file: File, sizeKB: Long, quality: Int) {
        binding.cropPanel.visibility = View.GONE
        binding.previewPanel.visibility = View.VISIBLE

        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        binding.ivPreview.setImageBitmap(bitmap)

        updateSizeInfo(sizeKB, quality)
    }

    private fun updateSizeInfo(sizeKB: Long, quality: Int) {
        val sizeStatus = if (template.maxSizeKB > 0 && sizeKB > template.maxSizeKB) {
            "⚠ ${sizeKB}KB (limit: ${template.maxSizeKB}KB)"
        } else {
            "✓ ${sizeKB}KB"
        }
        binding.tvSizeInfo.text = "Size: $sizeStatus | Quality: $quality%"

        val dims = processedFile?.let {
            val bmp = android.graphics.BitmapFactory.decodeFile(it.absolutePath)
            "${bmp?.width ?: 0} × ${bmp?.height ?: 0} px"
        } ?: ""
        binding.tvDimensionInfo.text = dims
    }

    private fun updateQualityLabel(quality: Int) {
        val label = when {
            quality >= 85 -> "High ($quality%)"
            quality >= 60 -> "Medium ($quality%)"
            else -> "Low ($quality%)"
        }
        binding.tvQualityLabel.text = "Quality: $label"
    }

    private fun updatePreviewSize(bitmap: Bitmap, quality: Int) {
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                ImageProcessor.processImage(
                    context = this@CropActivity,
                    sourceBitmap = bitmap,
                    template = template,
                    qualityPercent = quality,
                    addWatermarkFlag = addWatermark,
                    customWidth = customWidth,
                    customHeight = customHeight
                )
            }
            file?.let {
                processedFile = it
                val sizeKB = ImageProcessor.getFileSizeKB(it)
                updateSizeInfo(sizeKB, quality)
            }
        }
    }

    private fun saveImage() {
        val file = processedFile ?: return

        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    val destDir = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                    } else {
                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                    }
                    val formTaiyarDir = File(destDir, "FormTaiyar")
                    formTaiyarDir.mkdirs()
                    val destFile = File(formTaiyarDir, "FormTaiyar_${template.id}_${System.currentTimeMillis()}.jpg")
                    file.copyTo(destFile, overwrite = true)

                    // Add to media store so it appears in gallery
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, destFile.name)
                        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(android.provider.MediaStore.Images.Media.DATA, destFile.absolutePath)
                    }
                    contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

                    true
                } catch (e: Exception) {
                    false
                }
            }

            if (saved) {
                Toast.makeText(this@CropActivity, "Photo Gallery mein save ho gayi!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@CropActivity, "Save karne mein dikkat aayi. Dobara try karein.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareImage() {
        val file = processedFile ?: return
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, ImageProcessor.WATERMARK_TEXT)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Photo Share Karein"))
    }
}
