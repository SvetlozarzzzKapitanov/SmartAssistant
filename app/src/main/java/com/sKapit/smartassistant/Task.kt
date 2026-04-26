package com.sKapit.smartassistant

data class Task(
    val id: Int,
    val title: String,
    val description: String? = null,
    val time: Long, // Changed to Long for travel_time calculations
    val locationName: String,
    val latitude: Double,    // Взето от Places API
    val longitude: Double,   // Взето от Places API
    var leaveTime: Long? = null // чрез Distance Matrix
)