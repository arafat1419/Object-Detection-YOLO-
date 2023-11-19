package com.example.objectdetection.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.objectdetection.utils.BoundingBox
import com.example.objectdetection.utils.ObjectDetection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SharedViewModel : ViewModel() {
    private val _bitmap: MutableStateFlow<Bitmap?> = MutableStateFlow(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap.asStateFlow()

    private val _boundingBoxes: MutableStateFlow<List<BoundingBox>> = MutableStateFlow(emptyList())
    val boundingBoxes: StateFlow<List<BoundingBox>> = _boundingBoxes.asStateFlow()

    private val _isImageDetected: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isImageDetected: StateFlow<Boolean> = _isImageDetected.asStateFlow()

    fun setBitmap(bitmap: Bitmap) {
        _isImageDetected.value = false
        _boundingBoxes.value = emptyList()
        _bitmap.value = bitmap
    }

    fun detectImage(context: Context, boxWidth: Float) {
        if (!isImageDetected.value) {
            viewModelScope.launch {
                val objectDetection = ObjectDetection(context = context)
                objectDetection.setup()
                _bitmap.value = objectDetection.drawImage(frame = _bitmap.value!!, boxWidth, true) {
                    _boundingBoxes.value = it
                }
                objectDetection.clear()
                _isImageDetected.value = true
            }
        }
    }
}