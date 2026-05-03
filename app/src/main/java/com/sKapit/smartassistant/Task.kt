package com.sKapit.smartassistant

data class Task(
    var id: Int = 0,
    var title: String = "",
    var description: String? = null,
    var time: Long = 0L,
    var locationName: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var leaveTime: Long? = null,
    var travelMode: String = "driving",
    var distanceText: String = "---",
    var isExpanded: Boolean = false
)