package com.sKapit.smartassistant

import android.content.Context
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

enum class TravelMode(val value: String) {
    DRIVING("driving"),
    WALKING("walking"),
    TRANSIT("transit");

    companion object {
        fun fromString(value: String): TravelMode {
            return values().find { it.value == value } ?: DRIVING
        }
    }
}

data class Task(
    var id: Int = 0,
    var title: String = "",
    var description: String? = null,
    var time: Long = 0L,
    var locationName: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var leaveTime: Long? = null,
    var travelMode: String = TravelMode.DRIVING.value,
    var distanceText: String = "---",
    var isExpanded: Boolean = false,
    var startLocationName: String? = null,
    var startLatitude: Double? = null,
    var startLongitude: Double? = null,
    var resolvedStartLocationName: String? = null,
    var routeSourceType: String = "gps",
    var routeWarning: String? = null,
    var hasRouteConflict: Boolean = false
) {
    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dateFormat = SimpleDateFormat("EEE, d MMM", Locale("bg", "BG"))
    }

    fun getFormattedArrivalTime(): String {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_YEAR)
        val year = cal.get(Calendar.YEAR)

        cal.timeInMillis = time
        val targetDay = cal.get(Calendar.DAY_OF_YEAR)
        val targetYear = cal.get(Calendar.YEAR)

        val timeStr = timeFormat.format(cal.time)

        return when {
            year == targetYear && today == targetDay -> "$timeStr · Днес"
            year == targetYear && today + 1 == targetDay -> "$timeStr · Утре"
            else -> {
                val dateStr = dateFormat.format(cal.time)
                "$timeStr · $dateStr"
            }
        }
    }

    fun getStatusData(context: Context): Pair<String, Int> {
        val leaveTimeMillis = leaveTime ?: return Pair(
            context.getString(R.string.status_calculating),
            ContextCompat.getColor(context, R.color.status_calculating)
        )
        val now = System.currentTimeMillis()
        val diffMillis = leaveTimeMillis - now
        val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)

        return when {
            diffMinutes < 0 -> Pair(
                context.getString(R.string.status_late),
                ContextCompat.getColor(context, R.color.status_late)
            )
            diffMinutes in 0..60 -> Pair(
                context.getString(R.string.status_leave_in, diffMinutes),
                ContextCompat.getColor(context, R.color.status_on_time)
            )
            else -> {
                val leaveStr = timeFormat.format(Date(leaveTimeMillis))
                Pair(
                    context.getString(R.string.status_leave_at, leaveStr),
                    ContextCompat.getColor(context, R.color.status_future)
                )
            }
        }
    }
}
