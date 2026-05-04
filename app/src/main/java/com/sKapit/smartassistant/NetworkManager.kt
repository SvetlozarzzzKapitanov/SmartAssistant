package com.sKapit.smartassistant

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.Calendar

class NetworkManager(
    private val context: Context,
    private val mapsApiKey: String = BuildConfig.MAPS_API_KEY,
    private val bestTimeApiKey: String = BuildConfig.BEST_TIME_API_KEY
) {

    private val client = OkHttpClient()

    private fun getString(resId: Int): String = context.getString(resId)

    // --- Google Distance Matrix API ---
    fun fetchTravelTime(
        task: Task,
        userLat: Double,
        userLng: Double,
        onSuccess: (Task) -> Unit,
        onError: (String) -> Unit
    ) {
        if (mapsApiKey.isEmpty()) {
            onError(getString(R.string.error_api_key_not_configured))
            return
        }

        val url = "https://maps.googleapis.com/maps/api/distancematrix/json" +
                "?origins=$userLat,$userLng" +
                "&destinations=${task.latitude},${task.longitude}" +
                "&mode=${task.travelMode}" +
                "&key=$mapsApiKey"

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: getString(R.string.error_network_generic))
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    try {
                        val jsonObject = JSONObject(responseBody)
                        val status = jsonObject.optString("status")
                        
                        if (status == "REQUEST_DENIED" || status == "OVER_QUERY_LIMIT") {
                            onError(jsonObject.optString("error_message", getString(R.string.error_parsing_google)))
                            return
                        }

                        val rows = jsonObject.getJSONArray("rows")
                        if (rows.length() == 0) {
                            onError(getString(R.string.error_no_route))
                            return
                        }
                        
                        val elements = rows.getJSONObject(0).getJSONArray("elements")
                        val element = elements.getJSONObject(0)
                        val elementStatus = element.getString("status")
                        
                        if (elementStatus == "ZERO_RESULTS" || elementStatus == "NOT_FOUND") {
                            onError(getString(R.string.error_no_route))
                            return
                        }

                        if (elementStatus != "OK") {
                            onError(getString(R.string.error_parsing_google))
                            return
                        }

                        val duration = element.getJSONObject("duration")
                        val travelTimeSeconds = duration.getLong("value")
                        val distance = element.getJSONObject("distance")
                        val distanceText = distance.getString("text")

                        task.distanceText = distanceText
                        val bufferSeconds = 10 * 60
                        task.leaveTime =
                            task.time - (travelTimeSeconds * 1000) - (bufferSeconds * 1000)

                        onSuccess(task)

                    } catch (e: Exception) {
                        onError(getString(R.string.error_parsing_google))
                        Log.e("NetworkManager", "Parsing error: ${e.message}")
                    }
                } else {
                    onError(getString(R.string.error_empty_response))
                }
            }
        })
    }

    // --- BestTime API ---
    fun fetchBestTimeData(
        venueName: String,
        venueAddress: String,
        targetTimeInMillis: Long,
        onSuccess: (List<HourlyForecast>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (bestTimeApiKey.isEmpty()) {
            onError(getString(R.string.error_api_key_not_configured))
            return
        }

        val cal = Calendar.getInstance().apply { timeInMillis = targetTimeInMillis }
        val targetDayInt = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

        val urlBuilder = "https://besttime.app/api/v1/forecasts".toHttpUrlOrNull()?.newBuilder()
        urlBuilder?.addQueryParameter("api_key_private", bestTimeApiKey)
        urlBuilder?.addQueryParameter("venue_name", venueName)
        urlBuilder?.addQueryParameter("venue_address", venueAddress)

        val url = urlBuilder?.build().toString()
        val requestBody = FormBody.Builder().build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("BestTimeAPI_Network", "Мрежова грешка: ${e.message}", e)
                onError(getString(R.string.error_network_generic))
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.optString("status") == "error") {
                            onError(jsonResponse.optString("message", getString(R.string.error_fetching_venue)))
                            return
                        }

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
                            onError(getString(R.string.error_no_data_for_day))
                        }
                    } catch (e: Exception) {
                        onError(getString(R.string.error_parsing_besttime))
                        Log.e("NetworkManager", "BestTime parsing error: ${e.message}")
                    }
                } else {
                    Log.e("BestTimeAPI_Error", "HTTP ${response.code}: $responseBody")
                    onError(getString(R.string.error_fetching_venue))
                }
            }
        })
    }
}
