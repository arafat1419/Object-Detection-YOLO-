package com.example.objectdetection.ui

import android.annotation.SuppressLint
import android.content.ContentValues
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.lifecycleScope
import com.example.objectdetection.R
import com.example.objectdetection.databinding.ActivityTestBinding
import com.example.objectdetection.ui.screen.PreviewAnalyzer
import com.example.objectdetection.utils.ObjectDetection
import kotlinx.coroutines.launch
import java.lang.Exception
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class TestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestBinding

    private lateinit var cameraExecutor: ExecutorService
//    private var imageCapture: ImageCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        initCamera()
    }

    @SuppressLint("MissingPermission")
    private fun initCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }

            val selector = QualitySelector
                .from(
                    Quality.UHD,
                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                )

            val recorder = Recorder.Builder()
                .setQualitySelector(selector)
                .build()

            val videoCapture = VideoCapture.withOutput(recorder)

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "CameraX-VideoCapture")
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            }

            val mediaStoreOutputOptions = MediaStoreOutputOptions
                .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build()

            var recording: Recording? = null

            val recordingListener = Consumer<VideoRecordEvent> { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        val msg = "Capture Started"
                        Log.d("LHT", msg)
                        // update app internal recording state
                    }

                    is VideoRecordEvent.Finalize -> {
                        val msg = if (!event.hasError()) {
                            // update app internal state
                            "Video capture succeeded: ${event.outputResults.outputUri}"

                        } else {
                            // update app state when the capture failed.
                            recording?.close()
                            recording = null

                            "Video capture ends with error: ${event.error}"
                        }
                        Log.d("LHT", msg)
                    }
                }
            }

//            imageCapture = ImageCapture.Builder()
//                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
//                .build()

            val imageAnalyzer = ImageAnalysis.Builder().build().also { imageAnalysis ->
                imageAnalysis.setAnalyzer(cameraExecutor, PreviewAnalyzer {
                    lifecycleScope.launch {
                        if (binding.cameraPreview.bitmap != null) {
                            val objectDetection = ObjectDetection(this@TestActivity)
                            objectDetection.setup()
                            objectDetection.drawImage(
                                binding.cameraPreview.bitmap!!,
                                15F,
                                true
                            ) { boundingBoxes, bitmap ->
                                if (boundingBoxes.isNotEmpty()) {
                                    binding.imagePreview.setImageBitmap(bitmap)

                                    val filteredBoundingBoxes = boundingBoxes.distinctBy { it.labelIndex }

                                    Log.d("LHB", filteredBoundingBoxes.toString())
                                }
                            }
                        }
                    }
                })
            }

            binding.btnCapture.setOnClickListener {
                recording = videoCapture.output
                    .prepareRecording(this, mediaStoreOutputOptions)
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(this), recordingListener)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    videoCapture,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {

            }

        }, ContextCompat.getMainExecutor(this))
    }
}