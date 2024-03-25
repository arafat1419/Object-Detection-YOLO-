package com.example.objectdetection.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.objectdetection.R
import java.util.LinkedList
import kotlin.math.max

class OverlayView2(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: List<BoundingBox> = LinkedList<BoundingBox>()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()

    private var scaleFactor: Float = 1f

    private var bounds = Rect()
    private var imageWidth = 0
    private var imageHeight = 0

    init {
        initPaints()
    }

    fun clear() {
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f

        boxPaint.color = ContextCompat.getColor(context!!, R.color.purple_500)
        boxPaint.strokeWidth = 8F
        boxPaint.style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        for (boundingBox in results) {
            val x1 = (boundingBox.x1 * imageWidth + left)
            val y1 = (boundingBox.y1 * imageHeight + top)
            val x2 = (boundingBox.x2 * imageWidth + left)
            val y2 = (boundingBox.y2 * imageHeight + top)
            canvas.drawRect(x1, y1, x2, y2, boxPaint)

            // Create text to display alongside detected objects
            val drawableText =
                ObjectDetection.getLabelFromIndex(boundingBox.labelIndex) + " " +
                        String.format("%.2f", boundingBox.cnf)

            // Draw rect behind display text
            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
            val textWidth = bounds.width()
            val textHeight = bounds.height()
            canvas.drawRect(
                x1,
                y1,
                x1 + textWidth + Companion.BOUNDING_RECT_TEXT_PADDING,
                y1 + textHeight + Companion.BOUNDING_RECT_TEXT_PADDING,
                textBackgroundPaint
            )

            // Draw text for detected object
            canvas.drawText(drawableText, x1, y1 + bounds.height(), textPaint)
        }
    }

    fun setResults(
        detectionResults: List<BoundingBox>,
        imageHeight: Int,
        imageWidth: Int,
    ) {
        results = detectionResults

        // PreviewView is in FILL_START mode. So we need to scale up the bounding box to match with
        // the size that the captured images will be displayed.
        scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}