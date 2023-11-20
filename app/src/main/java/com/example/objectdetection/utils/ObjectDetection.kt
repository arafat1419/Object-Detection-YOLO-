package com.example.objectdetection.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.lang.Integer.min
import kotlin.random.Random

class ObjectDetection(private val context: Context) {
    private var interpreter: Interpreter? = null
    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()
    private var squareSize = 0
    private var left = 0
    private var top = 0

    fun setup() {
        val model = FileUtil.loadMappedFile(context, MODEL_PATH)
        val options = Interpreter.Options()
            .setNumThreads(4)

        interpreter = Interpreter(model, options)
    }

    fun clear() {
        interpreter?.close()
        interpreter = null
    }

    fun drawImage(
        frame: Bitmap,
        boxWidth: Float = 0F,
        useLabel: Boolean,
        onResult: (List<BoundingBox>) -> Unit
    ): Bitmap {
        val newBitmap = resizeBitmap(frame, TENSOR_WIDTH, TENSOR_HEIGHT)
        val boundingBoxes = detect(newBitmap)

        if (boundingBoxes.isNullOrEmpty()) {
            return newBitmap // No bounding boxes to draw
        }

        val resultBitmap = newBitmap.copy(Bitmap.Config.ARGB_8888, true)

        if (boxWidth > 0F) {
            val canvas = Canvas(resultBitmap)
            val paint = Paint()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = boxWidth

            for (box in boundingBoxes) {
                val x1 = (box.x1 * newBitmap.width + left).toInt()
                val y1 = (box.y1 * newBitmap.height + top).toInt()
                val x2 = (box.x2 * newBitmap.width + left).toInt()
                val y2 = (box.y2 * newBitmap.height + top).toInt()
                paint.color = getRandomColor(box.labelIndex)
                canvas.drawRect(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), paint)

                if (useLabel) {
                    val textSize = 24F.coerceAtMost(8F.coerceAtLeast((x2 - x1) / 10F))

                    val label = getLabelFromIndex(box.labelIndex)
                    val labelPaint = Paint()
                    labelPaint.color = paint.color
                    labelPaint.textSize = textSize
                    canvas.drawText(label, x1.toFloat(), y1.toFloat() + textSize, labelPaint)
                }
            }
        }

        val newestBitmap = if (frame.width < frame.height) {
            if (frame.height > TENSOR_HEIGHT) resizeBitmap(
                resultBitmap,
                frame.width,
                frame.height
            ) else resultBitmap
        } else {
            resultBitmap
        }

        onResult(boundingBoxes)
        return newestBitmap
    }

    private fun detect(frame: Bitmap): List<BoundingBox>? {
        interpreter ?: return null

        if (squareSize == 0) {
            squareSize = min(frame.width, frame.height)
            left = (frame.width - squareSize) / 2
            top = (frame.height - squareSize) / 2
        }

        val croppedBitmap = Bitmap.createBitmap(frame, left, top, squareSize, squareSize)
        val resizedBitmap =
            Bitmap.createScaledBitmap(croppedBitmap, TENSOR_WIDTH, TENSOR_HEIGHT, false)

        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(resizedBitmap)
        val processedImage = imageProcessor.process(tensorImage)
        val imageBuffer = processedImage.buffer

        val output =
            TensorBuffer.createFixedSize(intArrayOf(1, 84, NUM_ELEMENTS), OUTPUT_IMAGE_TYPE)
        interpreter?.run(imageBuffer, output.buffer)

        return bestBox(output.floatArray)
    }

    private fun bestBox(array: FloatArray): List<BoundingBox>? {
        val boundingBoxes = mutableListOf<BoundingBox>()
        for (c in 0 until NUM_ELEMENTS) {

            val indices =
                (4..(3 + TOTAL_LABEL)).map { c + NUM_ELEMENTS * it } //  I have 3 classes so the LABEL_SIZE is 3
            val cnfArray = indices.map { array[it] }.toFloatArray()
            val cnf = cnfArray.maxOrNull() ?: continue

            val maxConfidenceIndex = cnfArray.indexOfFirst {
                it == cnf
            }

            if (cnf > CONFIDENCE_THRESHOLD) {
                val cx = array[c]
                val cy = array[c + NUM_ELEMENTS]
                val w = array[c + NUM_ELEMENTS * 2]
                val h = array[c + NUM_ELEMENTS * 3]
                val x1 = cx - (w / 2F)
                val y1 = cy - (h / 2F)
                val x2 = cx + (w / 2F)
                val y2 = cy + (h / 2F)
                if (x1 <= 0F || x1 >= TENSOR_WIDTH_FLOAT) continue
                if (y1 <= 0F || y1 >= TENSOR_HEIGHT_FLOAT) continue
                if (x2 <= 0F || x2 >= TENSOR_WIDTH_FLOAT) continue
                if (y2 <= 0F || y2 >= TENSOR_HEIGHT_FLOAT) continue
                boundingBoxes.add(
                    BoundingBox(
                        x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                        cx = cx, cy = cy, w = w, h = h, cnf = cnf,
                        labelIndex = maxConfidenceIndex,
                    )
                )
            }
        }

        if (boundingBoxes.isEmpty()) return null

        return applyNMS(boundingBoxes)
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)
        val intersectionArea = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        val box1Area = box1.w * box1.h
        val box2Area = box2.w * box2.h
        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    private fun applyNMS(boxes: List<BoundingBox>): MutableList<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.w * it.h }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()

        while (sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.first()
            selectedBoxes.add(first)
            sortedBoxes.remove(first)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val nextBox = iterator.next()
                val iou = calculateIoU(first, nextBox)
                if (iou >= IOU_THRESHOLD) {
                    iterator.remove()
                }
            }
        }

        return selectedBoxes
    }

    private fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val resizedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        resizedBitmap.eraseColor(Color.TRANSPARENT) // Fill with black color

        val canvas = Canvas(resizedBitmap)

        val widthRatio = targetWidth.toFloat() / bitmap.width
        val heightRatio = targetHeight.toFloat() / bitmap.height

        val scaleFactor = if (bitmap.width > bitmap.height) widthRatio else heightRatio

        val scaledWidth = bitmap.width * scaleFactor
        val scaledHeight = bitmap.height * scaleFactor

        val translateX = (targetWidth - scaledWidth) / 2
        val translateY = (targetHeight - scaledHeight) / 2

        val matrix = Matrix().apply {
            postScale(scaleFactor, scaleFactor)
            postTranslate(translateX, translateY)
        }

        canvas.drawBitmap(bitmap, matrix, null)

        return resizedBitmap
    }

    companion object {
        private const val MODEL_PATH = "model.tflite"
        private const val TENSOR_WIDTH = 640
        private const val TENSOR_HEIGHT = 640
        private const val TENSOR_WIDTH_FLOAT = TENSOR_WIDTH.toFloat()
        private const val TENSOR_HEIGHT_FLOAT = TENSOR_HEIGHT.toFloat()

        const val INPUT_MEAN = 0F
        const val INPUT_STANDARD_DEVIATION = 255F

        val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32

        private const val NUM_ELEMENTS = 8400
        private const val CONFIDENCE_THRESHOLD = 0.5F
        private const val IOU_THRESHOLD = 0.7F
        private const val TOTAL_LABEL = 80

        private val mapOfLabel = mapOf(
            0 to "helmet",
            1 to "vest",
        )

        fun getRandomColor(index: Int): Int {
            val brightness = (index % 3) * TOTAL_LABEL // Adjust the factor as needed
            val seed = index.toLong()

            val random = Random(seed)

            val red = random.nextInt(256 - brightness) + brightness
            val green = random.nextInt(256 - brightness) + brightness
            val blue = random.nextInt(256 - brightness) + brightness

            return Color.rgb(red, green, blue)
        }

        fun getLabelFromIndex(index: Int): String = mapOfLabel[index].toString()
    }
}

data class BoundingBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val cnf: Float,
    val labelIndex: Int
)