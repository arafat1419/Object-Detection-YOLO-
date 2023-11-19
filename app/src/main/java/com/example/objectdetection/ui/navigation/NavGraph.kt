package com.example.objectdetection.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.objectdetection.ui.screen.CameraScreen
import com.example.objectdetection.ui.screen.HomeScreen
import com.example.objectdetection.ui.screen.ResultScreen
import com.example.objectdetection.ui.screen.SharedViewModel

@Composable
fun NavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Navigation.HOME_SCREEN
) {
    val navActions = remember(navController) {
        NavigationActions(navController)
    }
    val sharedViewModel: SharedViewModel = viewModel()

    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Navigation.HOME_SCREEN) {
            HomeScreen(navigateTo = navActions::navigate)
        }
        composable(Navigation.CAMERA_SCREEN) {
            CameraScreen(navigateTo = navActions::navigate, sharedViewModel)
        }
        composable(Navigation.RESULT_SCREEN) {
            ResultScreen(navigateTo = navActions::navigate, sharedViewModel)
        }
        composable(Navigation.BACK_SCREEN) {
            navController.popBackStack()
        }
    }
}