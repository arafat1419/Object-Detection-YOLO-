package com.example.objectdetection.ui.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

class NavigationActions(private val navHostController: NavHostController) {
    fun navigate(destination: String) {
        navHostController.navigate(destination) {
            when (destination) {
                Navigation.HOME_SCREEN -> {
                    popUpTo(navHostController.graph.findStartDestination().id){}
                    launchSingleTop = true
                }
                else -> {}
            }
        }
    }
}