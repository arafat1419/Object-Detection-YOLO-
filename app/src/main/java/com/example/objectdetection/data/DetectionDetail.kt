package com.example.objectdetection.data

data class DetectionData(
    var video_resolution: String? = null,
    var title: String? = null,
    var city: String? = null,
    var date_time: String? = null,
    var video_duration: Long? = null,
    var distance: Long? = null,
    var data: List<DetectionDetail>? = null,
)

data class DetectionDetail(
    var duration: Long? = null,
    var date_time: String? = null,
    var lat: Long? = null,
    var long: Long? = null,
    var result: List<DetectionResult>? = null,
)

data class DetectionResult(
    var bounding_box: BoundingBoxData? = null,
    var label_name: String? = null,
    var label_code: String? = null,
    var confidence: Float? = null,
)

data class BoundingBoxData(
    var top_left: CoordinateData? = null,
    var bottom_right: CoordinateData? = null,
)

data class CoordinateData(
    var x: Float? = null,
    var y: Float? = null,
)