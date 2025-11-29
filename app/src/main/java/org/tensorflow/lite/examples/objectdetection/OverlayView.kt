package org.tensorflow.lite.examples.objectdetection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetection
import org.tensorflow.lite.examples.objectdetection.tracking.LineCrossingDetector
import org.tensorflow.lite.examples.objectdetection.tracking.TrackedObject
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var results: List<ObjectDetection> = emptyList()
    private var trackedObjects: List<TrackedObject>? = null
    private var newCrossings: List<Int>? = null
    private var scaleFactor: Float = 1f

    private var totalCount = 0
    private var ripeCount = 0
    private var unripeCount = 0

    private var totalCrossings = 0
    private var ripeCrossings = 0
    private var unripeCrossings = 0

    private val countedObjectIds = mutableSetOf<Int>()

    private var lineCrossingDetector: LineCrossingDetector? = null

    private var isStaticImage = false

    private var isCountingActive = false

    private val boxPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.bounding_box_color)
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val crossedBoxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 10f
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val lineEndpointPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textBackgroundPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        textSize = 48f
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textSize = 48f
        isAntiAlias = true
    }

    private val idTextPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.FILL
        textSize = 40f
        isAntiAlias = true
    }

    private val bounds = Rect()
    private val drawableRect = RectF()

    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    fun clear() {
        results = emptyList()
        trackedObjects = null
        newCrossings = null
        totalCount = 0
        ripeCount = 0
        unripeCount = 0
        totalCrossings = 0
        ripeCrossings = 0
        unripeCrossings = 0
        imageWidth = 0
        imageHeight = 0
        isStaticImage = false
        isCountingActive = false
        lineCrossingDetector = null
        countedObjectIds.clear()
        invalidate()
    }

    fun setResults(
        detectionResults: List<ObjectDetection>,
        imageHeight: Int,
        imageWidth: Int,
        isStatic: Boolean = false,
        trackedObjs: List<TrackedObject>? = null,
        crossings: List<Int>? = null,
        crossingDetector: LineCrossingDetector? = null,
        countingActive: Boolean = false,
        crossingStats: Map<String, Int>? = null
    ) {
        results = detectionResults
        trackedObjects = trackedObjs
        newCrossings = crossings
        lineCrossingDetector = crossingDetector
        isCountingActive = countingActive

        newCrossings?.forEach { id ->
            countedObjectIds.add(id)
        }

        totalCount = detectionResults.size
        ripeCount = detectionResults.count { it.category.label.equals("ripe", ignoreCase = true) }
        unripeCount =
            detectionResults.count { it.category.label.equals("unripe", ignoreCase = true) }

        if (crossingStats != null) {
            totalCrossings = crossingStats["total"] ?: 0
            ripeCrossings = crossingStats["ripe"] ?: 0
            unripeCrossings = crossingStats["unripe"] ?: 0
        }

        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.isStaticImage = isStatic

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val pad = 16f
        var yOffset = pad

        drawCounter(canvas, "Detected: $totalCount", pad, yOffset)
        yOffset += pad + bounds.height()

        drawCounter(canvas, "Ripe: $ripeCount", pad, yOffset)
        yOffset += pad + bounds.height()

        drawCounter(canvas, "Unripe: $unripeCount", pad, yOffset)
        yOffset += pad + bounds.height()

        if (isCountingActive) {
            yOffset += pad // Extra spacing

            drawCounter(canvas, "Scanned Fruits", pad, yOffset)
            yOffset += pad + bounds.height()

            drawCounter(canvas, "Total Crossed: $totalCrossings", pad, yOffset)
            yOffset += pad + bounds.height()

            drawCounter(canvas, "Ripe: $ripeCrossings", pad, yOffset)
            yOffset += pad + bounds.height()

            drawCounter(canvas, "Unripe: $unripeCrossings", pad, yOffset)
        }

        if (imageWidth == 0 || imageHeight == 0 || results.isEmpty()) {
            if (isCountingActive && lineCrossingDetector != null) {
                drawCountingLine(canvas)
            }
            return
        }

        if (isStaticImage) {
            scaleFactor = min(width * 1f / imageWidth, height * 1f / imageHeight)
            val xOffset = (width - imageWidth * scaleFactor) / 2f
            val yOffset = (height - imageHeight * scaleFactor) / 2f

            canvas.save()
            canvas.translate(xOffset, yOffset)

        } else {
            scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
        }

        if (isCountingActive && lineCrossingDetector != null) {
            drawCountingLine(canvas)
        }

        if (isCountingActive && trackedObjects != null) {
        } else if (!isCountingActive && trackedObjects != null) {
            for (result in results) {
                val boundingBox = result.boundingBox

                val top = boundingBox.top * scaleFactor
                val bottom = boundingBox.bottom * scaleFactor
                val left = boundingBox.left * scaleFactor
                val right = boundingBox.right * scaleFactor

                drawableRect.set(left, top, right, bottom)
                canvas.drawRect(drawableRect, boxPaint)

                val drawableText =
                    result.category.label + " " +
                            String.format(Locale.getDefault(), "%.2f", result.category.confidence)

                textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
                val textWidth = bounds.width()
                val textHeight = bounds.height()
                canvas.drawRect(
                    left,
                    top,
                    left + textWidth + BOUNDING_RECT_TEXT_PADDING,
                    top + textHeight + BOUNDING_RECT_TEXT_PADDING,
                    textBackgroundPaint
                )

                canvas.drawText(drawableText, left, top + bounds.height(), textPaint)
            }
        } else {
            for (result in results) {
                val boundingBox = result.boundingBox

                val top = boundingBox.top * scaleFactor
                val bottom = boundingBox.bottom * scaleFactor
                val left = boundingBox.left * scaleFactor
                val right = boundingBox.right * scaleFactor

                drawableRect.set(left, top, right, bottom)
                canvas.drawRect(drawableRect, boxPaint)

                val drawableText =
                    result.category.label + " " +
                            String.format(Locale.getDefault(), "%.2f", result.category.confidence)

                textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
                val textWidth = bounds.width()
                val textHeight = bounds.height()
                canvas.drawRect(
                    left,
                    top,
                    left + textWidth + BOUNDING_RECT_TEXT_PADDING,
                    top + textHeight + BOUNDING_RECT_TEXT_PADDING,
                    textBackgroundPaint
                )

                canvas.drawText(drawableText, left, top + bounds.height(), textPaint)
            }
        }

        if (isStaticImage) {
            canvas.restore()
        }
    }

    private fun drawCountingLine(canvas: Canvas) {
        if (lineCrossingDetector == null) return

        val lineStart = lineCrossingDetector!!.getLineStart()
        val lineEnd = lineCrossingDetector!!.getLineEnd()

        val x1 = lineStart.first * scaleFactor
        val y1 = lineStart.second * scaleFactor
        val x2 = lineEnd.first * scaleFactor
        val y2 = lineEnd.second * scaleFactor

        canvas.drawLine(x1, y1, x2, y2, linePaint)

        canvas.drawCircle(x1, y1, 12f, lineEndpointPaint)
        canvas.drawCircle(x2, y2, 12f, lineEndpointPaint)
    }

    private fun drawCounter(canvas: Canvas, text: String, x: Float, y: Float) {
        textBackgroundPaint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawRect(
            x,
            y,
            x + bounds.width() + 16f,
            y + bounds.height() + 16f,
            textBackgroundPaint
        )
        canvas.drawText(text, x + 8f, y + bounds.height(), textPaint)
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}