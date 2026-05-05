package com.sKapit.smartassistant

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class TrafficUpdateWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val firebaseManager = FirebaseManager()
    private val networkManager = NetworkManager(context)
    private val notificationHelper = NotificationManagerHelper(context)

    override suspend fun doWork(): androidx.work.ListenableWorker.Result = withContext(Dispatchers.IO) {
        Log.d("TrafficUpdateWorker", "Стартиране на фоново обновяване на трафика...")

        if (!firebaseManager.isUserLoggedIn()) return@withContext androidx.work.ListenableWorker.Result.success()

        val tasksList = suspendCoroutine<List<Task>> { continuation ->
            firebaseManager.loadTasks(
                onSuccess = { continuation.resume(it) },
                onFailure = { continuation.resume(emptyList()) }
            )
        }

        if (tasksList.isEmpty()) return@withContext androidx.work.ListenableWorker.Result.success()

        val today = Calendar.getInstance()
        val todayTasks = tasksList.filter { task ->
            val taskCal = Calendar.getInstance().apply { timeInMillis = task.time }
            taskCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    taskCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        }.sortedBy { it.time }

        if (todayTasks.isEmpty()) return@withContext androidx.work.ListenableWorker.Result.success()

        // Опитваме се да вземем последната локация за GPS задачите
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val lastLoc = try {
            Tasks.await(fusedLocationClient.lastLocation)
        } catch (e: Exception) {
            null
        }

        for (i in todayTasks.indices) {
            val task = todayTasks[i]
            val originLat: Double
            val originLng: Double

            when {
                task.startLatitude != null && task.startLongitude != null -> {
                    originLat = task.startLatitude!!
                    originLng = task.startLongitude!!
                }
                i > 0 -> {
                    val prevTask = todayTasks[i - 1]
                    originLat = prevTask.latitude
                    originLng = prevTask.longitude
                }
                lastLoc != null -> {
                    originLat = lastLoc.latitude
                    originLng = lastLoc.longitude
                }
                else -> continue // Скипваме, ако нямаме никаква изходна точка
            }

            val updatedTask = suspendCoroutine<Task?> { continuation ->
                networkManager.fetchTravelTime(
                    task = task,
                    userLat = originLat,
                    userLng = originLng,
                    onSuccess = { continuation.resume(it) },
                    onError = { continuation.resume(null) }
                )
            }

            updatedTask?.let {
                if (i > 0) {
                    it.checkAndSetConflict(context, todayTasks[i - 1].getEndTime())
                }
                firebaseManager.saveTask(it)
                if (!it.hasRouteConflict) notificationHelper.scheduleNotification(it)
            }
        }

        androidx.work.ListenableWorker.Result.success()
    }
}