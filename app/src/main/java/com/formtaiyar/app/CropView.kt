package com.formtaiyar.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Custom lightweight crop view — no external library needed.
 * Uses only Android's native Canvas/Paint/Bitmap APIs.
 */
class CropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var sourceBitmap: Bitmap? = null
    private var imageMatrix = Matrix()
    private var imageRect = RectF()

    // Crop frame in view coordinates
    private var cropRect = RectF()

    // Fixed aspect ratio (0f = free crop)
    private var aspectRatioW = 0f
    private var aspectRatioH = 0f
    private var isFreeMode = false

    // Touch handling
    private var activeEdge = Edge.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val touchSlop = 40f

    private enum class Edge { NONE, MOVE, TOP, BOTTOM, LEFT, RIGHT, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    // Paint objects
    private val overlayPaint = Paint().apply {
        color = Color.argb(160, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        isAntiAlias = true
    }
    private val handlePaint = Paint().apply {
        color = Color.parseColor("#1565C0")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val gridPaint = Paint().apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = true
    }

    private val handleRadius = 18f
    private val minCropSize = 80f

    fun setImageBitmap(bitmap: Bitmap) {
        sourceBitmap = bitmap
        post { computeImageLayout() }
        invalidate()
    }

    fun setAspectRatio(widthRatio: Int, heightRatio: Int) {
        if (widthRatio == 0 || heightRatio == 0) {
            isFreeMode = true
            aspectRatioW = 0f
            aspectRatioH = 0f
        } else {
            isFreeMode = false
            aspectRatioW = widthRatio.toFloat()
            aspectRatioH = heightRatio.toFloat()
        }
        if (imageRect.width() > 0) {
            initCropRect()
            invalidate()
        }
    }

    private fun computeImageLayout() {
        val bmp = sourceBitmap ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw == 0f || vh == 0f) return

        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val scale = min(vw / bw, vh / bh)
        val scaledW = bw * scale
        val scaledH = bh * scale
        val left = (vw - scaledW) / 2f
        val top = (vh - scaledH) / 2f

        imageMatrix.reset()
        imageMatrix.setScale(scale, scale)
        imageMatrix.postTranslate(left, top)
        imageRect.set(left, top, left + scaledW, top + scaledH)

        initCropRect()
    }

    private fun initCropRect() {
        val ir = imageRect
        val iw = ir.width()
        val ih = ir.height()
        val padding = 30f

        if (isFreeMode || aspectRatioW == 0f) {
            cropRect.set(ir.left + padding, ir.top + padding, ir.right - padding, ir.bottom - padding)
        } else {
            val ratio = aspectRatioW / aspectRatioH
            var cropW = iw - padding * 2
            var cropH = cropW / ratio
            if (cropH > ih - padding * 2) {
                cropH = ih - padding * 2
                cropW = cropH * ratio
            }
            val cx = ir.centerX()
            val cy = ir.centerY()
            cropRect.set(cx - cropW / 2f, cy - cropH / 2f, cx + cropW / 2f, cy + cropH / 2f)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeImageLayout()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = sourceBitmap ?: return

        // Draw the image
        canvas.drawBitmap(bmp, imageMatrix, null)

        val cr = cropRect

        // Draw dark overlay — 4 rects around crop area
        canvas.drawRect(0f, 0f, width.toFloat(), cr.top, overlayPaint)
        canvas.drawRect(0f, cr.top, cr.left, cr.bottom, overlayPaint)
        canvas.drawRect(cr.right, cr.top, width.toFloat(), cr.bottom, overlayPaint)
        canvas.drawRect(0f, cr.bottom, width.toFloat(), height.toFloat(), overlayPaint)

        // Draw grid (rule of thirds)
        val thirdW = cr.width() / 3f
        val thirdH = cr.height() / 3f
        canvas.drawLine(cr.left + thirdW, cr.top, cr.left + thirdW, cr.bottom, gridPaint)
        canvas.drawLine(cr.left + thirdW * 2, cr.top, cr.left + thirdW * 2, cr.bottom, gridPaint)
        canvas.drawLine(cr.left, cr.top + thirdH, cr.right, cr.top + thirdH, gridPaint)
        canvas.drawLine(cr.left, cr.top + thirdH * 2, cr.right, cr.top + thirdH * 2, gridPaint)

        // Draw crop border
        canvas.drawRect(cr, borderPaint)

        // Draw corner handles (filled circles)
        canvas.drawCircle(cr.left, cr.top, handleRadius, handlePaint)
        canvas.drawCircle(cr.right, cr.top, handleRadius, handlePaint)
        canvas.drawCircle(cr.left, cr.bottom, handleRadius, handlePaint)
        canvas.drawCircle(cr.right, cr.bottom, handleRadius, handlePaint)

        // Draw edge mid-handles (smaller)
        val midHandleR = handleRadius * 0.7f
        canvas.drawCircle(cr.centerX(), cr.top, midHandleR, handlePaint)
        canvas.drawCircle(cr.centerX(), cr.bottom, midHandleR, handlePaint)
        canvas.drawCircle(cr.left, cr.centerY(), midHandleR, handlePaint)
        canvas.drawCircle(cr.right, cr.centerY(), midHandleR, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeEdge = detectEdge(x, y)
                lastTouchX = x
                lastTouchY = y
                return activeEdge != Edge.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeEdge == Edge.NONE) return false
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                moveCrop(activeEdge, dx, dy)
                lastTouchX = x
                lastTouchY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeEdge = Edge.NONE
                return true
            }
        }
        return false
    }

    private fun detectEdge(x: Float, y: Float): Edge {
        val cr = cropRect
        val t = touchSlop

        val nearLeft = abs(x - cr.left) < t
        val nearRight = abs(x - cr.right) < t
        val nearTop = abs(y - cr.top) < t
        val nearBottom = abs(y - cr.bottom) < t

        return when {
            nearLeft && nearTop -> Edge.TOP_LEFT
            nearRight && nearTop -> Edge.TOP_RIGHT
            nearLeft && nearBottom -> Edge.BOTTOM_LEFT
            nearRight && nearBottom -> Edge.BOTTOM_RIGHT
            nearTop && x > cr.left && x < cr.right -> Edge.TOP
            nearBottom && x > cr.left && x < cr.right -> Edge.BOTTOM
            nearLeft && y > cr.top && y < cr.bottom -> Edge.LEFT
            nearRight && y > cr.top && y < cr.bottom -> Edge.RIGHT
            x > cr.left + t && x < cr.right - t && y > cr.top + t && y < cr.bottom - t -> Edge.MOVE
            else -> Edge.NONE
        }
    }

    private fun moveCrop(edge: Edge, dx: Float, dy: Float) {
        val ir = imageRect
        val cr = cropRect

        when (edge) {
            Edge.MOVE -> {
                val newLeft = (cr.left + dx).coerceIn(ir.left, ir.right - cr.width())
                val newTop = (cr.top + dy).coerceIn(ir.top, ir.bottom - cr.height())
                cropRect.offsetTo(newLeft, newTop)
            }
            Edge.TOP_LEFT -> adjustEdge(left = dx, top = dy)
            Edge.TOP_RIGHT -> adjustEdge(right = dx, top = dy)
            Edge.BOTTOM_LEFT -> adjustEdge(left = dx, bottom = dy)
            Edge.BOTTOM_RIGHT -> adjustEdge(right = dx, bottom = dy)
            Edge.TOP -> adjustEdge(top = dy)
            Edge.BOTTOM -> adjustEdge(bottom = dy)
            Edge.LEFT -> adjustEdge(left = dx)
            Edge.RIGHT -> adjustEdge(right = dx)
            Edge.NONE -> {}
        }

        // Clamp to image bounds
        cropRect.left = cropRect.left.coerceIn(ir.left, ir.right - minCropSize)
        cropRect.top = cropRect.top.coerceIn(ir.top, ir.bottom - minCropSize)
        cropRect.right = cropRect.right.coerceIn(ir.left + minCropSize, ir.right)
        cropRect.bottom = cropRect.bottom.coerceIn(ir.top + minCropSize, ir.bottom)

        // Enforce aspect ratio if set
        if (!isFreeMode && aspectRatioW > 0f && aspectRatioH > 0f) {
            enforceAspectRatio(edge)
        }
    }

    private fun adjustEdge(left: Float = 0f, top: Float = 0f, right: Float = 0f, bottom: Float = 0f) {
        val cr = cropRect
        val minSize = minCropSize
        if (left != 0f && cr.right - (cr.left + left) >= minSize) cropRect.left += left
        if (top != 0f && cr.bottom - (cr.top + top) >= minSize) cropRect.top += top
        if (right != 0f && (cr.right + right) - cr.left >= minSize) cropRect.right += right
        if (bottom != 0f && (cr.bottom + bottom) - cr.top >= minSize) cropRect.bottom += bottom
    }

    private fun enforceAspectRatio(edge: Edge) {
        val cr = cropRect
        val ratio = aspectRatioW / aspectRatioH
        when (edge) {
            Edge.LEFT, Edge.RIGHT, Edge.TOP_LEFT, Edge.TOP_RIGHT, Edge.BOTTOM_LEFT, Edge.BOTTOM_RIGHT -> {
                val newH = cr.width() / ratio
                cropRect.bottom = cr.top + newH
            }
            Edge.TOP, Edge.BOTTOM -> {
                val newW = cr.height() * ratio
                val cx = cr.centerX()
                cropRect.left = cx - newW / 2f
                cropRect.right = cx + newW / 2f
            }
            else -> {}
        }
    }

    /**
     * Returns the cropped bitmap based on the current crop rectangle.
     */
    fun getCroppedBitmap(): Bitmap? {
        val bmp = sourceBitmap ?: return null
        val ir = imageRect
        if (ir.width() == 0f) return null

        // Map cropRect from view coordinates to bitmap coordinates
        val scaleX = bmp.width / ir.width()
        val scaleY = bmp.height / ir.height()

        val bitmapLeft = ((cropRect.left - ir.left) * scaleX).toInt().coerceAtLeast(0)
        val bitmapTop = ((cropRect.top - ir.top) * scaleY).toInt().coerceAtLeast(0)
        val bitmapWidth = (cropRect.width() * scaleX).toInt().coerceAtMost(bmp.width - bitmapLeft)
        val bitmapHeight = (cropRect.height() * scaleY).toInt().coerceAtMost(bmp.height - bitmapTop)

        if (bitmapWidth <= 0 || bitmapHeight <= 0) return null

        return Bitmap.createBitmap(bmp, bitmapLeft, bitmapTop, bitmapWidth, bitmapHeight)
    }
}
