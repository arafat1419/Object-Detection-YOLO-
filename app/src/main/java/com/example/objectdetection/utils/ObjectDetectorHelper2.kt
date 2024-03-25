//package com.example.objectdetection.utils
//
//import android.content.Context
//import android.graphics.Bitmap
//import android.os.SystemClock
//import android.util.Log
//import org.tensorflow.lite.DataType
//import org.tensorflow.lite.Interpreter
//import org.tensorflow.lite.support.common.FileUtil
//import org.tensorflow.lite.support.image.ImageProcessor
//import org.tensorflow.lite.support.image.TensorImage
//import org.tensorflow.lite.support.image.ops.Rot90Op
//import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
//import org.tensorflow.lite.task.core.BaseOptions
//import org.tensorflow.lite.task.gms.vision.TfLiteVision
//import org.tensorflow.lite.task.gms.vision.detector.ObjectDetector
//
//class ObjectDetectorHelper2(
//    var threshold: Float = 0.5f,
//    var numThreads: Int = 4,
//    var maxResults: Int = 3,
//    var currentDelegate: Int = 0,
//    val context: Context,
//    val objectDetectorListener: DetectorListener
//) {
//
//    private val TAG = "ObjectDetectionHelper"
//
//    // For this example this needs to be a var so it can be reset on changes. If the ObjectDetector
//    // will not change, a lazy val would be preferable.
////    private var objectDetector: ObjectDetector? = null
//    private var interpreter: Interpreter? = null
//    private var gpuSupported = false
//
//    init {
//
//        TfLiteGpu.isGpuDelegateAvailable(context).onSuccessTask { gpuAvailable: Boolean ->
//            val optionsBuilder =
//                TfLiteInitializationOptions.builder()
//            if (gpuAvailable) {
//                optionsBuilder.setEnableGpuDelegateSupport(true)
//            }
//            TfLiteVision.initialize(context, optionsBuilder.build())
//        }.addOnSuccessListener {
//            objectDetectorListener.onInitialized()
//        }.addOnFailureListener{
//            objectDetectorListener.onError("TfLiteVision failed to initialize: "
//                    + it.message)
//        }
//    }
//
//    fun clearObjectDetector() {
//        interpreter = null
//    }
//
//    // Initialize the object detector using current settings on the
//    // thread that is using it. CPU and NNAPI delegates can be used with detectors
//    // that are created on the main thread and used on a background thread, but
//    // the GPU delegate needs to be used on the thread that initialized the detector
//    fun setupObjectDetector() {
//        if (!TfLiteVision.isInitialized()) {
//            Log.e(TAG, "setupObjectDetector: TfLiteVision is not initialized yet")
//            return
//        }
//
//        // Create the base options for the detector using specifies max results and score threshold
//        val optionsBuilder =
//            ObjectDetector.ObjectDetectorOptions.builder()
//                .setScoreThreshold(threshold)
//                .setMaxResults(maxResults)
//
//        // Set general detection options, including number of used threads
//        val baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads)
//
//        // Use the specified hardware for running the model. Default to CPU
//        when (currentDelegate) {
//            DELEGATE_CPU -> {
//                // Default
//            }
//            DELEGATE_GPU -> {
//                if (gpuSupported) {
//                    baseOptionsBuilder.useGpu()
//                } else {
//                    objectDetectorListener.onError("GPU is not supported on this device")
//                }
//            }
//            DELEGATE_NNAPI -> {
//                baseOptionsBuilder.useNnapi()
//            }
//        }
//
//        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())
//
//        val modelName = "best_float32.tflite"
//        try {
////            objectDetector =
////                ObjectDetector.createFromFileAndOptions(context, modelName, optionsBuilder.build())
//            val model = FileUtil.loadMappedFile(context, modelName)
//            val options = Interpreter.Options()
//                .setNumThreads(4)
//
//            interpreter = Interpreter(model, options)
//        } catch (e: Exception) {
//            objectDetectorListener.onError(
//                "Object detector failed to initialize. See error logs for details"
//            )
//            Log.e(TAG, "TFLite failed to load model with error: " + e.message)
//        }
//    }
//
//    fun detect(image: Bitmap, imageRotation: Int) {
//        Log.d("LHC", "detect")
//        if (!TfLiteVision.isInitialized()) {
//            Log.e(TAG, "detect: TfLiteVision is not initialized yet")
//            return
//        }
//
//        if (interpreter == null) {
//            setupObjectDetector()
//        }
//
//        // Inference time is the difference between the system time at the start and finish of the
//        // process
//        var inferenceTime = SystemClock.uptimeMillis()
//
//        // Create preprocessor for the image.
//        // See https://www.tensorflow.org/lite/inference_with_metadata/
//        //            lite_support#imageprocessor_architecture
//        val imageProcessor = ImageProcessor.Builder().add(Rot90Op(-imageRotation / 90)).build()
//
//        // Preprocess the image and convert it into a TensorImage for detection.
//
//        Log.d("LHT", image.width.toString())
//        Log.d("LHT", image.height.toString())
//
//        val resizedBitmap =
//            Bitmap.createScaledBitmap(image,
//                640,
//                640, false)
//
//        val tensorImage = TensorImage(DataType.FLOAT32)
//        tensorImage.load(resizedBitmap)
//        val processedImage = imageProcessor.process(tensorImage)
//        val imageBuffer = processedImage.buffer
//
//        val output =
//            TensorBuffer.createFixedSize(intArrayOf(1, 11, NUM_ELEMENTS),
//                DataType.FLOAT32
//            )
//        interpreter?.run(imageBuffer, output.buffer)
//
//        val results = bestBox(
//            output.floatArray,
//            tensorImage.height.toFloat(),
//            tensorImage.width.toFloat()
//        )
//        inferenceTime = SystemClock.uptimeMillis() - inferenceTime
//        objectDetectorListener.onResults(
//            results,
//            inferenceTime,
//            tensorImage.height,
//            tensorImage.width
//        )
//    }
//
//    private fun bestBox(array: FloatArray, width: Float, height: Float): List<BoundingBox>? {
//        Log.d("LHC", "bestBox")
//        val boundingBoxes = mutableListOf<BoundingBox>()
//        for (c in 0 until NUM_ELEMENTS) {
//
////            val indices =
////                (4..(3 + TOTAL_LABEL)).map { c + NUM_ELEMENTS * it } //  I have 3 classes so the LABEL_SIZE is 3
////            val cnfArray = indices.map { array[it] }.toFloatArray()
////            val cnf = cnfArray.maxOrNull() ?: continue
////
////            val maxConfidenceIndex = cnfArray.indexOfFirst {
////                it == cnf
////            }
//            val cnf = array[c + NUM_ELEMENTS * 4]
//
//            if (cnf > CONFIDENCE_THRESHOLD) {
//                val cx = array[c]
//                val cy = array[c + NUM_ELEMENTS]
//                val w = array[c + NUM_ELEMENTS * 2]
//                val h = array[c + NUM_ELEMENTS * 3]
//                val x1 = cx - (w / 2F)
//                val y1 = cy - (h / 2F)
//                val x2 = cx + (w / 2F)
//                val y2 = cy + (h / 2F)
//                if (x1 <= 0F || x1 >= width) continue
//                if (y1 <= 0F || y1 >= height) continue
//                if (x2 <= 0F || x2 >= width) continue
//                if (y2 <= 0F || y2 >= height) continue
//                boundingBoxes.add(
//                    BoundingBox(
//                        x1 = x1, y1 = y1, x2 = x2, y2 = y2,
//                        cx = cx, cy = cy, w = w, h = h, cnf = cnf,
//                        labelIndex = 0,
//                    )
//                )
//            }
//        }
//
//        if (boundingBoxes.isEmpty()) return null
//
//        return applyNMS(boundingBoxes)
//    }
//
//    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
//        Log.d("LHC", "calculateIoU")
//        val x1 = maxOf(box1.x1, box2.x1)
//        val y1 = maxOf(box1.y1, box2.y1)
//        val x2 = minOf(box1.x2, box2.x2)
//        val y2 = minOf(box1.y2, box2.y2)
//        val intersectionArea = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
//        val box1Area = box1.w * box1.h
//        val box2Area = box2.w * box2.h
//        return intersectionArea / (box1Area + box2Area - intersectionArea)
//    }
//
//    private fun applyNMS(boxes: List<BoundingBox>): MutableList<BoundingBox> {
//        Log.d("LHC", "applyNMS")
//        val sortedBoxes = boxes.sortedByDescending { it.w * it.h }.toMutableList()
//        val selectedBoxes = mutableListOf<BoundingBox>()
//
//        while (sortedBoxes.isNotEmpty()) {
//            val first = sortedBoxes.first()
//            selectedBoxes.add(first)
//            sortedBoxes.remove(first)
//
//            val iterator = sortedBoxes.iterator()
//            while (iterator.hasNext()) {
//                val nextBox = iterator.next()
//                val iou = calculateIoU(first, nextBox)
//                if (iou >= IOU_THRESHOLD) {
//                    iterator.remove()
//                }
//            }
//        }
//
//        return selectedBoxes
//    }
//
//    interface DetectorListener {
//        fun onInitialized()
//        fun onError(error: String)
//        fun onResults(
//            results: List<BoundingBox>?,
//            inferenceTime: Long,
//            imageHeight: Int,
//            imageWidth: Int
//        )
//    }
//
//    companion object {
//        const val DELEGATE_CPU = 0
//        const val DELEGATE_GPU = 1
//        const val DELEGATE_NNAPI = 2
//
//
//        private const val NUM_ELEMENTS = 8400
//        private const val CONFIDENCE_THRESHOLD = 0.5F
//        private const val IOU_THRESHOLD = 0.7F
//        private const val TOTAL_LABEL = 7
//    }
//}