package com.formtaiyar.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.formtaiyar.app.databinding.ActivityCropBinding
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
    private var processedFile: File? = null
    private var addWatermark = true
    private var customWidth = 400
    private var customHeight = 400

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCropBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uriString = intent.getStringExtra(EXTRA_IMAGE_URI) ?: run { finish(); return }
        val templateId = intent.getStringExtra(EXTRA_TEMPLATE_ID) ?: "pan"
        template = Templates.all.find { it.id == templateId } ?: Templates.PAN_CARD

        supportActionBar?.hide()
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = template.nameHindi

        setupUI(Uri.parse(uriString))
    }

    private fun setupUI(imageUri: Uri) {
        binding.tvTemplateName.text = template.nameHindi
        binding.tvTemplateDimension.text = template.dimensionLabel

        val cropView = binding.cropImageView
        if (template.id == "custom") {
            cropView.setAspectRatio(0, 0)
            binding.customSizePanel.visibility = View.VISIBLE
            binding.etCustomWidth.setText(customWidth.toString())
            binding.etCustomHeight.setText(customHeight.toString())
        } else {
            cropView.setAspectRatio(template.widthPx, template.heightPx)
            binding.customSizePanel.visibility = View.GONE
        }

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

        binding.seekbarQuality.max = 100
        binding.seekbarQuality.progress = 85
        updateQualityLabel(85)

        binding.seekbarQuality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val actual = progress.coerceAtLeast(10)
                if (progress < 10) seekBar?.progress = 10
                updateQualityLabel(actual)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.switchWatermark.isChecked = true
        addWatermark = true
        binding.switchWatermark.setOnCheckedChangeListener { _, checked -> addWatermark = checked }

        binding.btnCrop.setOnClickListener { performCrop() }
        binding.btnSave.setOnClickListener { saveImage() }
        binding.btnShare.setOnClickListener { shareImage() }
        binding.btnCropAgain.setOnClickListener { showCropPanel() }

        showCropPanel()
    }

    private fun performCrop() {
        val quality = binding.seekbarQuality.progress.coerceAtLeast(10)

        if (template.id == "custom") {
            customWidth = binding.etCustomWidth.text.toString().toIntOrNull()?.coerceIn(50, 4000) ?: 400
            customHeight = binding.etCustomHeight.text.toString().toIntOrNull()?.coerceIn(50, 4000) ?: 400
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnCrop.isEnabled = false

        lifecycleScope.launch {
            val cropped = withContext(Dispatchers.Default) {
                binding.cropImageView.getCroppedBitmap()
            }

            if (cropped == null) {
                binding.progressBar.visibility = View.GONE
                binding.btnCrop.isEnabled = true
                Toast.makeText(this@CropActivity, "Photo crop nahi ho saka. Dobara try karein.", Toast.LENGTH_SHORT).show()
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

            binding.progressBar.visibility = View.GONE
            binding.btnCrop.isEnabled = true

            if (processed != null) {
                processedFile = processed
                val sizeKB = ImageProcessor.getFileSizeKB(processed)
                showPreviewPanel(processed, sizeKB, quality)
            } else {
                Toast.makeText(this@CropActivity, "Processing fail ho gayi. Dobara try karein.", Toast.LENGTH_SHORT).show()
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

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        binding.ivPreview.setImageBitmap(bitmap)
        updateSizeInfo(sizeKB, quality, bitmap?.width ?: 0, bitmap?.height ?: 0)
    }

    private fun updateSizeInfo(sizeKB: Long, quality: Int, w: Int, h: Int) {
        val ok = template.maxSizeKB <= 0 || sizeKB <= template.maxSizeKB
        binding.tvSizeInfo.text = if (ok) "Size: ${sizeKB}KB OK | Quality: $quality%" else "Size: ${sizeKB}KB (limit: ${template.maxSizeKB}KB) | Quality: $quality%"
        binding.tvDimensionInfo.text = "$w x $h px"
    }

    private fun updateQualityLabel(quality: Int) {
        val label = when {
            quality >= 85 -> "High ($quality%)"
            quality >= 60 -> "Medium ($quality%)"
            else -> "Low ($quality%)"
        }
        binding.tvQualityLabel.text = "Quality: $label"
    }

    private fun saveImage() {
        val file = processedFile ?: return
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    val destDir = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_PICTURES
                    )
                    val ftDir = File(destDir, "FormTaiyar").apply { mkdirs() }
                    val dest = File(ftDir, "FormTaiyar_${template.id}_${System.currentTimeMillis()}.jpg")
                    file.copyTo(dest, overwrite = true)
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, dest.name)
                        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(android.provider.MediaStore.Images.Media.DATA, dest.absolutePath)
                    }
                    contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    true
                } catch (e: Exception) { false }
            }
            if (saved) Toast.makeText(this@CropActivity, "Photo Gallery mein save ho gayi!", Toast.LENGTH_LONG).show()
            else Toast.makeText(this@CropActivity, "Save mein dikkat aayi. Dobara try karein.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImage() {
        val file = processedFile ?: return
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, ImageProcessor.WATERMARK_TEXT)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Photo Share Karein"))
    }
}
