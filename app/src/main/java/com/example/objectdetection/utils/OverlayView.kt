package com.example.objectdetection.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.example.objectdetection.data.ResearchData
import java.util.LinkedList
import kotlin.math.max

interface OverlayProcessListener {
    fun onProcessComplete(data: ResearchData?)
}

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: List<BoundingBox> = LinkedList<BoundingBox>()
    private val paint = Paint()
    private var scaleFactor = 1f
    private var targetSize = 0
    private var targetWidth = 0
    private var targetHeight = 0
    private var rotation = 0
    var researchData: ResearchData? = null
    private var processListener: OverlayProcessListener? = null

    init {
        initPaints()
    }

    fun clear() {
        paint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8F // can be change to variable
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        val clipWidth = if (rotation == 90 || rotation == 270) ((targetSize * scaleFactor - width) / 2) else 0F
        val clipHeight = if (rotation == 0 || rotation == 180) ((targetSize * scaleFactor - height) / 2) else 0F

        Log.d(TAG, "$width|$height-$targetSize|$scaleFactor-$clipWidth|$clipHeight")

        val labelList = emptyList<String>().toMutableList()

        for (box in results) {
            val x1 = (box.x1 * targetSize * scaleFactor - clipWidth).toInt()
            val y1 = (box.y1 * targetSize * scaleFactor - clipHeight).toInt()
            val x2 = (box.x2 * targetSize * scaleFactor - clipWidth).toInt()
            val y2 = (box.y2 * targetSize * scaleFactor - clipHeight).toInt()

            paint.color = ObjectDetection.getRandomColor(box.labelIndex)
            canvas.drawRect(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), paint)

            val textSize = 32F.coerceAtMost(8F.coerceAtLeast((x2 - x1) / 10F))

            val label = ObjectDetectorHelper.getLabelNameFromIndex(box.labelIndex)
            labelList.add(label)
            val labelPaint = Paint()
            labelPaint.color = paint.color
            labelPaint.textSize = textSize
            labelPaint.setTypeface(Typeface.DEFAULT_BOLD)
            canvas.drawText(label, x1.toFloat(), y1.toFloat() + textSize, labelPaint)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            researchData?.finishTime = CommonUtils.getCurrentDateTime()
            researchData?.label = labelList
        }

        processListener?.onProcessComplete(researchData)

        Log.d("LHX", "RESULT")
        Log.d("LHT", "overlay finish")

        Log.d("LHC", "FINISH 2")
        if (results.isNotEmpty()) {
            Log.d("LHR", "END ${results[0].labelIndex}")
        }
    }

    fun setProcessListener(listener: OverlayProcessListener) {
        processListener = listener
    }

    fun setResults(
        detectionResults: List<BoundingBox>,
        targetWidth: Int,
        targetHeight: Int,
        rotation: Int,
        researchData: ResearchData,
    ) {
        Log.d("LHT", "overlay start")

        results = detectionResults
        scaleFactor = max(width * 1f / targetWidth, height * 1f / targetHeight)

        this.targetWidth = targetWidth
        this.targetHeight = targetHeight
        this.targetSize = max(targetWidth, targetHeight)
        this.rotation = rotation
        this.researchData = researchData
    }

    companion object {
        private const val TAG = "OverlayView"
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}