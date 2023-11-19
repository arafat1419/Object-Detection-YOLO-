package com.example.objectdetection.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.objectdetection.R
import com.example.objectdetection.ui.components.MainToolbar
import com.example.objectdetection.ui.navigation.Navigation
import com.example.objectdetection.ui.theme.ColorPrimary
import com.example.objectdetection.utils.CommonUtils
import com.example.objectdetection.utils.CommonUtils.getCameraProvider
import com.example.objectdetection.utils.CommonUtils.getMirror
import com.example.objectdetection.utils.CommonUtils.toBitmap
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    navigateTo: (String) -> Unit,
    sharedViewModel: SharedViewModel,
) {
    val context = LocalContext.current

    var imageBitmap: Bitmap? by remember {
        mutableStateOf(null)
    }

    var isBackCamera: Boolean by remember {
        mutableStateOf(true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val preview = Preview.Builder().build()
    val previewView = remember { PreviewView(context) }
    val imageCapture: ImageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(
            if (isBackCamera) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
        )
        .build()

    val galleryLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
            imageBitmap = it.toBitmap(context)
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(context, "Permission Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }

    val cameraExecutor = Executors.newSingleThreadExecutor()

    LaunchedEffect(if (isBackCamera) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT) {
        val permissionCheckResult =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            )
        if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {

        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture
        )

        preview.setSurfaceProvider(previewView.surfaceProvider)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MainToolbar(
            isClose = true, title = "Preview"
        ) {
            navigateTo(Navigation.BACK_SCREEN)
        }

        if (imageBitmap == null) {
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
                        .padding(start = 16.dp)
                        .clickable {
                            val permissionCheckResult =
                                ContextCompat.checkSelfPermission(
                                    context,
                                    CommonUtils.getGalleryPermission()
                                )
                            if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                                galleryLauncher.launch("image/*")
                            } else {
                                permissionLauncher.launch(CommonUtils.getGalleryPermission())
                            }
                        },
                    painter = painterResource(id = R.drawable.ic_insert_photo_24),
                    contentDescription = null
                )
                Image(
                    modifier = Modifier
                        .clickable {
                            imageCapture.takePicture(
                                cameraExecutor,
                                object : OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        super.onCaptureSuccess(image)

                                        imageBitmap =
                                            if (isBackCamera) image.toBitmap() else image.toBitmap()
                                                .getMirror()
                                    }
                                })
                        },
                    painter = painterResource(id = R.drawable.ic_camera_48),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(color = ColorPrimary)
                )
                Image(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .clickable {
                            isBackCamera = !isBackCamera
                        },
                    painter = painterResource(id = R.drawable.ic_flip_camera_android_24),
                    contentDescription = null
                )
            }
        } else {
            Image(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .weight(1F)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.LightGray),
                bitmap = imageBitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth
            )

            Row(
                modifier = Modifier
                    .padding(16.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier
                        .weight(1F),
                    onClick = {
                        imageBitmap = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        contentColor = ColorPrimary,
                        containerColor = Color.Transparent
                    ),
                    border = BorderStroke(1.dp, ColorPrimary)
                ) {
                    Text(text = "Re-take")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    modifier = Modifier.weight(1F),
                    onClick = {
                        sharedViewModel.setBitmap(imageBitmap!!)
                        navigateTo(Navigation.RESULT_SCREEN)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text(text = "Process")
                }
            }
        }
    }
}