package com.sKapit.smartassistant

data class Task(
    val id: Int,
    val title: String,
    val description: String? = null,
    val time: Long, // Changed to Long for travel_time calculations
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isCompleted: Boolean = false
)