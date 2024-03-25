package com.example.objectdetection.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "research_data")
data class ResearchData(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,
    var counter: String,
    var takeImageTime: String,
    var preProcessingTime: String,
    var processingStart: String,
    var boundingBoxStart: String,
    var boundingBoxFinish: String,
    var overlayStart: String,
    var finishTime: String,
    var label: List<String>
)

fun List<ResearchData>.toCsvString(): String {
    val csvHeader = "ID,Counter,TakeImageTime,PreProcessingTime,ProcessingStart,BoundingBoxStart,BoundingBoxFinish,OverlayStart,FinishTime,Label\n"
    val csvBody = this.joinToString("\n") { data ->
        with(data) {
            "$id,$counter,$takeImageTime,$preProcessingTime,$processingStart,$boundingBoxStart,$boundingBoxFinish,$overlayStart,$finishTime,${label.joinToString(";")}"
        }
    }
    return csvHeader + csvBody
}

@Entity(tableName = "research_data_in_ms")
data class ResearchDataInMs(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,
    var counter: String,
    var takeImageTime: Long,
    var preProcessingTime: Long,
    var processingStart: Long,
    var boundingBoxStart: Long,
    var boundingBoxFinish: Long,
    var overlayStart: Long,
    var finishTime: Long,
    var label: List<String>
)