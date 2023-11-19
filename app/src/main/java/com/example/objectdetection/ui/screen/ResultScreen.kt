package com.example.objectdetection.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.objectdetection.ui.components.MainToolbar
import com.example.objectdetection.ui.navigation.Navigation
import com.example.objectdetection.utils.ObjectDetection

@Composable
fun ResultScreen(
    navigateTo: (String) -> Unit,
    sharedViewModel: SharedViewModel,
) {
    val context = LocalContext.current

    val boundingBoxes = sharedViewModel.boundingBoxes.collectAsState()
    val imageBitmap = sharedViewModel.bitmap.collectAsState()
    val isImageDetected = sharedViewModel.isImageDetected.collectAsState()

    LaunchedEffect(imageBitmap) {
        if (!isImageDetected.value) {
            sharedViewModel.detectImage(context, 2F)
        }
    }

    val filteredBoundingBoxes = boundingBoxes.value.distinctBy { it.labelIndex }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isImageDetected.value) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column {
                MainToolbar(
                    isClose = false, title = "Result"
                ) {
                    navigateTo(Navigation.BACK_SCREEN)
                }

                Image(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .weight(1F)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.LightGray),
                    bitmap = imageBitmap.value!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(items = filteredBoundingBoxes) {
                        Spacer(
                            modifier = Modifier
                                .padding(start = 16.dp, end = 8.dp)
                                .size(16.dp)
                                .background(
                                    color = Color(ObjectDetection.getRandomColor(it.labelIndex)),
                                    shape = RoundedCornerShape(4.dp)
                                )
                        )
                        Text(
                            text = ObjectDetection.getLabelFromIndex(it.labelIndex),
                            fontSize = 12.sp,
                            color = Color.Black
                        )
                        if (it == filteredBoundingBoxes.last()) {
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                    }
                }
            }
        }
    }
}