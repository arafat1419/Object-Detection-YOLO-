package com.example.objectdetection.ui.screen

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
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
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.example.objectdetection.R
import com.example.objectdetection.ui.components.MainToolbar
import com.example.objectdetection.ui.navigation.Navigation
import com.example.objectdetection.ui.theme.ColorPrimary
import com.example.objectdetection.utils.CommonUtils
import com.example.objectdetection.utils.CommonUtils.getCameraProvider
import com.example.objectdetection.utils.CommonUtils.getMirror
import com.example.objectdetection.utils.ObjectDetection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
@Composable
fun VideoScreen(
    navigateTo: (String) -> Unit,
    sharedViewModel: SharedViewModel,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current


    val selector = QualitySelector
        .from(
            Quality.UHD,
            FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
        )


    val recorder = Recorder.Builder()
        .setQualitySelector(selector)
        .build()

    val videoCapture = VideoCapture.withOutput(recorder)
    val preview = Preview.Builder().build()

    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
        .build()

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "CameraX-VideoCapture")
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
    }

    val mediaStoreOutputOptions = MediaStoreOutputOptions
        .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
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
    val cameraExecutor = Executors.newSingleThreadExecutor()

    val previewView = remember { PreviewView(context) }



    LaunchedEffect(selector) {
        val imageAnalyzer = ImageAnalysis.Builder().build().also { imageAnalysis ->
            imageAnalysis.setAnalyzer(cameraExecutor, PreviewAnalyzer {
                this.launch {
                    Log.d("LHTA", it.toString())
                    withContext(Dispatchers.Main) {
                        if (previewView.bitmap != null) {
                            val objectDetection = ObjectDetection(context)
                            objectDetection.drawImage(
                                previewView.bitmap!!,
                                1F,
                                true
                            ) { boundingBoxes, bitmap ->

                            }
                        }
                    }
                }
            })
        }

        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            videoCapture,
            preview,
            imageAnalyzer
        )

        recording = videoCapture.output
            .prepareRecording(context, mediaStoreOutputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context), recordingListener)

        preview.setSurfaceProvider(previewView.surfaceProvider)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MainToolbar(
            isClose = true, title = "Preview"
        ) {
            navigateTo(Navigation.BACK_SCREEN)
        }

        AndroidView(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .weight(1F)
                .clip(RoundedCornerShape(16.dp)),
            factory = { previewView }
        )

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Image(
                modifier = Modifier
                    .clickable {
                        recording?.stop()
                    },
                painter = painterResource(id = R.drawable.ic_camera_48),
                contentDescription = null,
                colorFilter = ColorFilter.tint(color = ColorPrimary)
            )
        }
    }
}

fun getResolutions(
    selector: CameraSelector,
    provider: ProcessCameraProvider
): Map<Quality, Size> {
    return selector.filter(provider.availableCameraInfos).firstOrNull()
        ?.let { camInfo ->
            QualitySelector.getSupportedQualities(camInfo)
                .associateWith { quality ->
                    QualitySelector.getResolution(camInfo, quality)!!
                }
        } ?: emptyMap()
}

class PreviewAnalyzer(private val listener: (Double) -> Unit) : ImageAnalysis.Analyzer {
    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()
        val data = ByteArray(remaining())
        get(data)
        return data
    }

    override fun analyze(image: ImageProxy) {
        val buffer = image.planes[0].buffer
        val data = buffer.toByteArray()
        val pixels = data.map { it.toInt() and 0xFF }
        val avr = pixels.average()

        listener(avr)
        image.close()
    }
}