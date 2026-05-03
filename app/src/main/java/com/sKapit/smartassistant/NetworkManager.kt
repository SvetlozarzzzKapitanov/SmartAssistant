package com.sKapit.smartassistant

import android.util.Log
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.Calendar

class NetworkManager(
    private val mapsApiKey: String = BuildConfig.MAPS_API_KEY,
    private val bestTimeApiKey: String = BuildConfig.BEST_TIME_API_KEY
) {

    private val client = OkHttpClient()

    // --- Google Distance Matrix API ---
    /**
     * Fetches travel time between user location and task destination.
     */
    fun fetchTravelTime(
        task: Task,
        userLat: Double,
        userLng: Double,
        onSuccess: (Task) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://maps.googleapis.com/maps/api/distancematrix/json" +
                "?origins=$userLat,$userLng" +
                "&destinations=${task.latitude},${task.longitude}" +
                "&mode=${task.travelMode}" +
                "&key=$mapsApiKey"

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    try {
                        val jsonObject = JSONObject(responseBody)
                        val rows = jsonObject.getJSONArray("rows")
                        val elements = rows.getJSONObject(0).getJSONArray("elements")

                        val status = elements.getJSONObject(0).getString("status")
                        if (status == "ZERO_RESULTS") {
                            onError("Няма намерен маршрут")
                            return
                        }

                        val duration = elements.getJSONObject(0).getJSONObject("duration")
                        val travelTimeSeconds = duration.getLong("value")
                        val distance = elements.getJSONObject(0).getJSONObject("distance")
                        val distanceText = distance.getString("text")

                        task.distanceText = distanceText
                        val bufferSeconds = 10 * 60
                        task.leaveTime =
                            task.time - (travelTimeSeconds * 1000) - (bufferSeconds * 1000)

                        onSuccess(task)

                    } catch (e: Exception) {
                        onError("Грешка при обработка на данните от Google")
                        Log.e("NetworkManager", "Parsing error: ${e.message}")
                    }
                } else {
                    onError("Празен отговор от сървъра")
                }
            }
        })
    }

    // --- BestTime API ---
    /**
     * Fetches venue busyness forecast from BestTime API.
     */
    fun fetchBestTimeData(
        venueName: String,
        venueAddress: String,
        targetTimeInMillis: Long,
        onSuccess: (List<HourlyForecast>) -> Unit,
        onError: (String) -> Unit
    ) {
        val cal = Calendar.getInstance().apply { timeInMillis = targetTimeInMillis }
        // BestTime uses 0 for Monday, so we convert from Calendar.DAY_OF_WEEK
        val targetDayInt = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

        val urlBuilder = "https://besttime.app/api/v1/forecasts".toHttpUrlOrNull()?.newBuilder()
        urlBuilder?.addQueryParameter("api_key_private", bestTimeApiKey)
        urlBuilder?.addQueryParameter("venue_name", venueName)
        urlBuilder?.addQueryParameter("venue_address", venueAddress)

        val url = urlBuilder?.build().toString()
        // BestTime forecasts endpoint requires a POST request, even if empty
        val requestBody = okhttp3.FormBody.Builder().build()

        val request = okhttp3.Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("BestTimeAPI_Network", "Мрежова грешка: ${e.message}", e)
                onError("Грешка при връзка с BestTime API")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        val weekAnalysis = jsonResponse.getJSONArray("analysis")

                        var dayForecast: JSONObject? = null
                        for (i in 0 until weekAnalysis.length()) {
                            val dayObj = weekAnalysis.getJSONObject(i)
                            val dayInfo = dayObj.getJSONObject("day_info")
                            if (dayInfo.getInt("day_int") == targetDayInt) {
                                dayForecast = dayObj
                                break
                            }
                        }

                        if (dayForecast != null) {
                            val hourAnalysis = dayForecast.getJSONArray("hour_analysis")
                            val dayRaw = dayForecast.getJSONArray("day_raw")

                            val hourlyList = mutableListOf<HourlyForecast>()
                            for (i in 0 until hourAnalysis.length()) {
                                val hourObj = hourAnalysis.getJSONObject(i)
                                val rawPercent = dayRaw.getInt(i)
                                val intensityText = hourObj.getString("intensity_txt")

                                if (intensityText != "Closed") {
                                    hourlyList.add(
                                        HourlyForecast(
                                            hour = hourObj.getInt("hour"),
                                            intensityPercent = rawPercent,
                                            intensityTxt = intensityText
                                        )
                                    )
                                }
                            }
                            onSuccess(hourlyList)
                        } else {
                            onError("Няма данни за избрания ден")
                        }
                    } catch (e: Exception) {
                        onError("Грешка при обработка на данните от BestTime")
                        Log.e("NetworkManager", "BestTime parsing error: ${e.message}")
                    }
                } else {
                    Log.e("BestTimeAPI_Error", "HTTP ${response.code}: $responseBody")
                    onError("Неуспешно извличане на данни за обекта")
                }
            }
        })
    }
}
