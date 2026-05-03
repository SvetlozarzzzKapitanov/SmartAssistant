package com.sKapit.smartassistant

data class HourlyForecast(
    val hour: Int,
    val intensityPercent: Int,
    val intensityTxt: String
)