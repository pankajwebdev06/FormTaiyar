package com.formtaiyar.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object ImageProcessor {

    const val WATERMARK_TEXT = "Made with FormTaiyar - Bina internet ke photo sahi karein"

    /**
     * Load bitmap from URI, correcting orientation via EXIF
     */
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val exifStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(exifStream)
            exifStream.close()

            val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotation != 0f) {
                val matrix = Matrix()
                matrix.postRotate(rotation)
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resize bitmap to exact target dimensions
     */
    fun resizeBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    /**
     * Compress bitmap to target size in KB using binary search
     */
    fun compressToTargetSize(
        bitmap: Bitmap,
        maxSizeKB: Int,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ): ByteArray {
        val maxBytes = maxSizeKB * 1024L

        var quality = 90
        var low = 10
        var high = 100
        var result: ByteArray

        // Binary search for best quality within size limit
        do {
            val baos = ByteArrayOutputStream()
            bitmap.compress(format, quality, baos)
            result = baos.toByteArray()

            if (result.size <= maxBytes) {
                low = quality + 1
            } else {
                high = quality - 1
            }
            quality = (low + high) / 2
        } while (low <= high && quality in 10..100)

        // Ensure we are under the limit
        if (result.size > maxBytes) {
            val baos = ByteArrayOutputStream()
            bitmap.compress(format, 10, baos)
            result = baos.toByteArray()
        }

        return result
    }

    /**
     * Compress to a specific quality percentage
     */
    fun compressAtQuality(bitmap: Bitmap, quality: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return baos.toByteArray()
    }

    /**
     * Add watermark text at bottom of bitmap
     */
    fun addWatermark(source: Bitmap): Bitmap {
        val watermarkedBitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(watermarkedBitmap)

        val textSize = (source.height * 0.03f).coerceAtLeast(12f).coerceAtMost(30f)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 0, 0, 0)
            style = Paint.Style.FILL
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        val padding = textSize * 0.5f
        val bgHeight = textSize + padding * 2
        val bgRect = Rect(0, (source.height - bgHeight).toInt(), source.width, source.height)
        canvas.drawRect(bgRect, bgPaint)

        canvas.drawText(
            WATERMARK_TEXT,
            source.width / 2f,
            source.height - padding,
            textPaint
        )

        return watermarkedBitmap
    }

    /**
     * Process image: resize, compress, and optionally add watermark
     * Returns a temporary file with the processed image
     */
    fun processImage(
        context: Context,
        sourceBitmap: Bitmap,
        template: PhotoTemplate,
        qualityPercent: Int,
        addWatermarkFlag: Boolean = false,
        customWidth: Int = 0,
        customHeight: Int = 0
    ): File? {
        return try {
            val targetW = if (template.id == "custom" && customWidth > 0) customWidth else template.widthPx
            val targetH = if (template.id == "custom" && customHeight > 0) customHeight else template.heightPx

            val resized = if (targetW > 0 && targetH > 0) {
                resizeBitmap(sourceBitmap, targetW, targetH)
            } else {
                sourceBitmap
            }

            val finalBitmap = if (addWatermarkFlag) addWatermark(resized) else resized

            val imageBytes = if (template.maxSizeKB < 500 && qualityPercent >= 90) {
                compressToTargetSize(finalBitmap, template.maxSizeKB)
            } else {
                compressAtQuality(finalBitmap, qualityPercent)
            }

            val cacheDir = context.cacheDir
            val outputFile = File(cacheDir, "formtaiyar_${System.currentTimeMillis()}.jpg")
            val fos = FileOutputStream(outputFile)
            fos.write(imageBytes)
            fos.close()

            outputFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get file size in KB
     */
    fun getFileSizeKB(file: File): Long {
        return file.length() / 1024
    }

    /**
     * Get approximate size of byte array in KB
     */
    fun getSizeKB(data: ByteArray): Int {
        return (data.size / 1024)
    }
}
