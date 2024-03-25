package com.example.objectdetection.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.objectdetection.R
import com.example.objectdetection.data.DetectionData
import com.example.objectdetection.data.DetectionDetail
import com.example.objectdetection.data.ResearchData
import com.example.objectdetection.data.toCsvString
import com.example.objectdetection.databinding.ActivityCameraBinding
import com.example.objectdetection.utils.BoundingBox
import com.example.objectdetection.utils.CommonUtils
import com.example.objectdetection.utils.ObjectDetectorHelper
import com.example.objectdetection.utils.OverlayProcessListener
import com.google.gson.Gson
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity(), ObjectDetectorHelper.DetectorListener {
    private lateinit var binding: ActivityCameraBinding

    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var bitmapBuffer: Bitmap
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var isProcessing = false
    private val listOfResearchData = mutableListOf<ResearchData>()

    private var counter = 0
    private var isStart = false

    private var detectionData = DetectionData()
    private val listOfDetectionDetail = mutableListOf<DetectionDetail>()

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.camera_container)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        objectDetectorHelper = ObjectDetectorHelper(
            context = this,
            objectDetectorListener = this)

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        binding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        binding.btnStop.setOnClickListener {
            if (isStart) {
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                    detectionData.videoDuration = CommonUtils.getCurrentDateTimeInMillis() - CommonUtils.convertToMillis(detectionData.dateTime.toString())
//                }

                detectionData.data = listOfDetectionDetail
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    detectionData.video_duration = (CommonUtils.getCurrentDateTimeInMillis() - CommonUtils.convertToMillis(detectionData.date_time.toString())) / 1000
                }

                Log.d("LHK", detectionData.toString())
                Log.d("LHK", Gson().toJson(detectionData).toString())

                detectionData = DetectionData()

                val csvData = listOfResearchData.toCsvString()
                CommonUtils.writeCsvToFile(this, csvData, "research_data.csv")
            } else {
                if (detectionData.date_time == null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        detectionData.date_time = CommonUtils.getCurrentDateTime()
                        detectionData.video_resolution =
                            "${binding.overlay.width}x${binding.overlay.height}"
                    }
                }
            }

            binding.btnStop.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, if (isStart) android.R.color.holo_green_light else android.R.color.holo_red_light))
            isStart = !isStart
        }


//        updateControlsUi()
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider =
            cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector - makes assumption that we're only using the back camera
        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        val screenSize = Size(640, 640)
        val resolutionSelector = ResolutionSelector.Builder().setResolutionStrategy(
            ResolutionStrategy(screenSize,
            ResolutionStrategy.FALLBACK_RULE_NONE)
        ).build()
        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview =
            Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_DEFAULT)
//                .setResolutionSelector(resolutionSelector)
//                .setTargetResolution(screenSize)
                .setTargetRotation(binding.viewFinder.display.rotation)
                .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder()
//                .setResolutionSelector(resolutionSelector)
                .setTargetAspectRatio(AspectRatio.RATIO_DEFAULT)
//                .setTargetResolution(screenSize)
                .setTargetRotation(binding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->

                        if (!::bitmapBuffer.isInitialized) {
                            // The image rotation and RGB image buffer are initialized only once
                            // the analyzer has started running
                            bitmapBuffer = Bitmap.createBitmap(
                                image.width,
                                image.height,
                                Bitmap.Config.ARGB_8888
                            )
                        }

                        Log.d("CEK", "---")
                        Log.d("CEK", isProcessing.toString())
                        Log.d("CEK", isStart.toString())

                        if (!isProcessing) {
                            val researchData = ResearchData(
                                0,
                                "",
                                "",
                                "",
                                "",
                                "",
                                "",
                                "",
                                "",
                                listOf()
                            )
                            researchData.id = counter
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                                detectionData.dateTime = CommonUtils.getCurrentDateTime()
                                researchData.takeImageTime = CommonUtils.getCurrentDateTime()
                            }
                            detectObjects(image, researchData)
                        }
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun updateControlsUi() {
        // Needs to be cleared instead of reinitialized because the GPU
        // delegate needs to be initialized on the thread using it when applicable
        objectDetectorHelper.clearObjectDetector()
        binding.overlay.clear()
    }

    private fun detectObjects(
        image: ImageProxy,
        researchData: ResearchData,
    ) {
        // Copy out RGB bits to the shared bitmap buffer
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }
        val imageRotation = image.imageInfo.rotationDegrees
        Log.d(TAG, "imageRotation = $imageRotation")
        // Pass Bitmap and rotation to the object detector helper for processing and detection

        if (isStart) {
            objectDetectorHelper.detect(bitmapBuffer, imageRotation, researchData, detectionData.date_time.toString())
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = binding.viewFinder.display.rotation
    }

    override fun onResults(
        results: List<BoundingBox>?,
        rotatedBitmap: Bitmap,
        rotation: Int,
        researchData: ResearchData,
        detectionDetail: DetectionDetail
    ) {
        Log.d(TAG, results.toString())
        runOnUiThread {
            isProcessing = false

            if (!results.isNullOrEmpty()) {
                listOfDetectionDetail.add(detectionDetail)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                researchData.overlayStart = CommonUtils.getCurrentDateTime()
            }
            Log.d("LHC", "START")
            Log.d("LHC", results.toString())
            // Pass necessary information to OverlayView for drawing on the canvas
            binding.overlay.setResults(
                results ?: listOf(),
                rotatedBitmap.width,
                rotatedBitmap.height,
                rotation,
                researchData,
            )

            binding.overlay.setProcessListener(object : OverlayProcessListener {
                override fun onProcessComplete(data: ResearchData?) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        researchData.finishTime = CommonUtils.getCurrentDateTime()
                        researchData.label = data?.label ?: listOf()
                        Log.d("LHZ", "RESULT")
                        Log.d("LHZ", binding.overlay.researchData.toString())
                    }

                    listOfResearchData.add(researchData)
                }
            })



            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                researchData.counter = counter.toString()
            }

            counter++

            if (counter <= 100) {

            }

            if (results != null) {
                val newBitmap = draw(results, rotatedBitmap, 1F, true)
                binding.ivBitmap.setImageBitmap(newBitmap)
                Log.d("LHC", "FINISH 1")
            }
            // Force a redraw
            binding.overlay.invalidate()
        }
    }

    private fun draw(
        results: List<BoundingBox>,
        frame: Bitmap,
        boxWidth: Float = 0F,
        useLabel: Boolean,
    ): Bitmap? {
        val resultBitmap = frame.copy(Bitmap.Config.ARGB_8888, true)

        if (boxWidth > 0F) {
            val canvas = Canvas(resultBitmap)
            val paint = Paint()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = boxWidth

            for (box in results) {
                val x1 = (box.x1 * frame.width).toInt()
                val y1 = (box.y1 * frame.height).toInt()
                val x2 = (box.x2 * frame.width).toInt()
                val y2 = (box.y2 * frame.height).toInt()
                paint.color = ObjectDetectorHelper.getRandomColor(box.labelIndex)
                canvas.drawRect(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), paint)

                if (useLabel) {
                    val textSize = 24F.coerceAtMost(8F.coerceAtLeast((x2 - x1) / 10F))

                    val label = ObjectDetectorHelper.getLabelNameFromIndex(box.labelIndex)
                    val labelPaint = Paint()
                    labelPaint.color = paint.color
                    labelPaint.textSize = textSize
                    canvas.drawText(label, x1.toFloat(), y1.toFloat() + textSize, labelPaint)
                }
            }
        }

        Log.d("LHT", "Label Added")

        return resultBitmap
    }

    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        const val TAG = "CameraActivity"
    }
}