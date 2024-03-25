package com.example.objectdetection.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import com.example.objectdetection.data.BoundingBoxData
import com.example.objectdetection.data.CoordinateData
import com.example.objectdetection.data.DetectionDetail
import com.example.objectdetection.data.DetectionResult
import com.example.objectdetection.data.ResearchData
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import kotlin.random.Random

class ObjectDetectorHelper(
    private val context: Context,
    private val objectDetectorListener: DetectorListener?
) {

    // For this example this needs to be a var so it can be reset on changes. If the ObjectDetector
    // will not change, a lazy val would be preferable.
    private var interpreter: Interpreter? = null
    private var counter = 0
    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()
    private val tensorImage = TensorImage(DataType.FLOAT32)
    private val output =
        TensorBuffer.createFixedSize(intArrayOf(1, 11, NUM_ELEMENTS),
            OUTPUT_IMAGE_TYPE
        )
    private val matrix = Matrix()

    init {
        setupObjectDetector()
    }

    fun clearObjectDetector() {
        interpreter = null
    }

    // Initialize the object detector using current settings on the
    // thread that is using it. CPU and NNAPI delegates can be used with detectors
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the detector
    fun setupObjectDetector() {
        // Create the base options for the detector using specifies max results and score threshold
        try {
            val model = FileUtil.loadMappedFile(context, MODEL_PATH)
            val options = Interpreter.Options()
                .setNumThreads(4)
                .setUseNNAPI(true)

            interpreter = Interpreter(model, options)
        } catch (e: IllegalStateException) {
            objectDetectorListener?.onError(
                "Object detector failed to initialize. See error logs for details"
            )
            Log.e("Test", "TFLite failed to load model with error: " + e.message)
        }
    }

    fun detect(
        image: Bitmap,
        imageRotation: Int,
        researchData: ResearchData,
        dateTime: String,
    ) {
        Log.d("LHT", "start detect")
        Log.d("LHR", "START $counter")
        Log.d(TAG, "${image.width}|${image.height}|$imageRotation")
        val detectionDetail = DetectionDetail()


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            detectionDetail.duration =
                (CommonUtils.getCurrentDateTimeInMillis() - CommonUtils.convertToMillis(dateTime)) / 1000
            detectionDetail.date_time = CommonUtils.getCurrentDateTime()

            researchData.preProcessingTime = CommonUtils.getCurrentDateTime()
            Log.d("LHQ", "preProcessingTime")
        }

        if (interpreter == null) {
            setupObjectDetector()
        }

        // Inference time is the difference between the system time at the start and finish of the
        // process
//        var inferenceTime = SystemClock.uptimeMillis()

        // Create preprocessor for the image.
        // See https://www.tensorflow.org/lite/inference_with_metadata/
        //            lite_support#imageprocessor_architecture


        Log.d("LHT", "rotate start")

        Log.d("LHQ", "resize")
        val resizedBitmap =
            resizeBitmap(image,
                TENSOR_WIDTH,
                TENSOR_HEIGHT)

        Log.d("LHQ", "rotate")
        val rotatedBitmap = rotateBitmap(resizedBitmap, imageRotation.toFloat())

        Log.d("LHT", "rotate finish")


        Log.d("LHQ", "tensorImage.load")
        tensorImage.load(rotatedBitmap)

        Log.d("LHQ", "imageProcessor.process")
        val processedImage = imageProcessor.process(tensorImage)
        val imageBuffer = processedImage.buffer

        Log.d("LHT", "interpreter start")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            researchData.processingStart = CommonUtils.getCurrentDateTime()
        }

        Log.d("LHQ", "run")
        interpreter?.run(imageBuffer, output.buffer)

        Log.d("LHT", "interpreter finish")



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            researchData.boundingBoxStart = CommonUtils.getCurrentDateTime()
        }

        Log.d("LHR", output.floatArray.size.toString())
        var results: List<BoundingBox>? = null

        bestBox(output.floatArray) { boundingBoxes ->
            results = boundingBoxes
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            researchData.boundingBoxFinish = CommonUtils.getCurrentDateTime()
        }

        val detectionResults = mutableListOf<DetectionResult>()

        results?.forEach {
            val detectionResult = DetectionResult()
            val boundingBoxData = BoundingBoxData()

            detectionResult.label_name = getLabelNameFromIndex(it.labelIndex)
            detectionResult.label_code = getLabelCodeFromIndex(it.labelIndex)
            detectionResult.confidence = it.cnf
            boundingBoxData.top_left = CoordinateData(it.x1, it.y1)
            boundingBoxData.bottom_right = CoordinateData(it.x2, it.y2)
            detectionResult.bounding_box = boundingBoxData

            detectionResults.add(detectionResult)
        }

        detectionDetail.result = detectionResults

        objectDetectorListener?.onResults(
            results,
            rotatedBitmap,
            imageRotation,
            researchData,
            detectionDetail
        )
    }

    private fun bestBox(array: FloatArray, onFinish: (List<BoundingBox>?) -> Unit) {
        Log.d("LHT", "bestBox start")

        val boundingBoxes = mutableListOf<BoundingBox>()
        for (c in 0 until NUM_ELEMENTS) {
            var maxConfidence = Float.MIN_VALUE
            var maxConfidenceIndex = -1

            for (i in 0 until TOTAL_LABEL) {
                val confidence = array[c + NUM_ELEMENTS * (4 + i)]
                if (confidence > maxConfidence) {
                    maxConfidence = confidence
                    maxConfidenceIndex = i
                }
            }

            if (maxConfidence > CONFIDENCE_THRESHOLD) {
                val cx = array[c]
                val cy = array[c + NUM_ELEMENTS]
                val w = array[c + NUM_ELEMENTS * 2]
                val h = array[c + NUM_ELEMENTS * 3]
                val x1 = cx - (w / 2F)
                val y1 = cy - (h / 2F)
                val x2 = cx + (w / 2F)
                val y2 = cy + (h / 2F)

                if (x1 >= 0F && y1 >= 0F && x2 <= TENSOR_WIDTH_FLOAT && y2 <= TENSOR_HEIGHT_FLOAT) {
                    boundingBoxes.add(
                        BoundingBox(
                            x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                            cx = cx, cy = cy, w = w, h = h, cnf = maxConfidence,
                            labelIndex = maxConfidenceIndex,
                        )
                    )

                }
            }
        }


        if (boundingBoxes.isEmpty()) return onFinish(null)
        counter++
        Log.d("LHT", "bestBox end")

        onFinish(applyNMS(boundingBoxes))
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
        Log.d("LHT", "applyNMS start")

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
        Log.d("LHT", "applyNMS end")
        return selectedBoxes
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        matrix.reset()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val resizedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        resizedBitmap.eraseColor(Color.GREEN) // Fill with black color

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

    interface DetectorListener {
        fun onError(error: String)
        fun onResults(
            results: List<BoundingBox>?,
            rotatedBitmap: Bitmap,
            rotation: Int,
            researchData: ResearchData,
            detectionDetail: DetectionDetail
        )
    }

    companion object {
        private const val MODEL_PATH = "best_float16.tflite"
        private const val TENSOR_WIDTH = 640
        private const val TENSOR_HEIGHT = 640
        private const val TENSOR_WIDTH_FLOAT = TENSOR_WIDTH.toFloat()
        private const val TENSOR_HEIGHT_FLOAT = TENSOR_HEIGHT.toFloat()

        private const val INPUT_MEAN = 0F
        private const val INPUT_STANDARD_DEVIATION = 255F

        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32

        private const val NUM_ELEMENTS = 8400
        private const val CONFIDENCE_THRESHOLD = 0.2F
        private const val IOU_THRESHOLD = 0.7F
        private const val TOTAL_LABEL = 7
        private val mapOfLabel = listOf(
            "Wheel Mark Longitudinal Crack", "Construction Joint Longitudinal Crack", "Equal Interval Longitudinal Crack", "Construction Joint Lateral Crack", "Alligator Crack", "Pothole", "White Line Blur", "truck",
            "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench",
            "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra",
            "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove",
            "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup",
            "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
            "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
            "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
            "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier",
            "toothbrush"
        )
        private val mapOfCode = listOf(
            "D00",
            "D00",
            "D10",
            "D11",
            "D20",
            "D40",
            "D44"
        )
        fun getLabelNameFromIndex(index: Int): String = mapOfLabel[index]
        fun getLabelCodeFromIndex(index: Int): String = mapOfCode[index]
        fun getRandomColor(index: Int): Int {
            val brightness = (index % 3) * TOTAL_LABEL // Adjust the factor as needed
            val seed = index.toLong()

            val random = Random(seed)

            val red = random.nextInt(256 - brightness) + brightness
            val green = random.nextInt(256 - brightness) + brightness
            val blue = random.nextInt(256 - brightness) + brightness

            return Color.rgb(red, green, blue)
        }

        private const val TAG = "ObjectDetectorHelper"
    }
}