package com.example.objectdetection.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.objectdetection.R
import com.example.objectdetection.ui.navigation.Navigation
import com.example.objectdetection.ui.theme.ColorPrimary
import com.example.objectdetection.ui.theme.ObjectDetectionTheme

@Composable
fun HomeScreen(
    navigateTo: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .clickable {
                    navigateTo(Navigation.CAMERA_SCREEN)
                },

            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp
            ),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(Color.White)
        ) {
            Image(
                modifier = Modifier
                    .padding(horizontal = 50.dp, vertical = 24.dp),
                painter = painterResource(id = R.drawable.ill_machine_learning),
                contentDescription = null,
                colorFilter = ColorFilter.tint(color = ColorPrimary)
            )
            Text(
                modifier = Modifier
                    .padding(bottom = 32.dp)
                    .width(210.dp),
                text = "Detect",
                textAlign = TextAlign.Center,
                color = ColorPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(device = Devices.NEXUS_5X, showSystemUi = true)
@Composable
fun HomeScreenPrev() {
    ObjectDetectionTheme {
        HomeScreen({})
    }
}