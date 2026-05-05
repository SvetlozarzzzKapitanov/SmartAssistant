// MainActivity.kt
package com.sKapit.smartassistant

import android.net.Uri
import android.view.View
import android.widget.CalendarView
import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.Toast
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var adapter: TaskAdapter
    private val tasks = mutableListOf<Task>()

    private val firebaseManager = FirebaseManager()
    private lateinit var networkManager: NetworkManager
    private lateinit var notificationHelper: NotificationManagerHelper
    private var calendarTasks = mutableListOf<Task>()
    private lateinit var calendarAdapter: TaskAdapter
    private var selectedCalendarDateMillis: Long = System.currentTimeMillis()
    private lateinit var loadingProgress: ProgressBar

    private val onTaskEdit: (Task) -> Unit = { clickedTask ->
        val intent = Intent(this, AddTaskActivity::class.java).apply {
            putExtra("isEdit", true)
            putExtra("id", clickedTask.id)
            putExtra("title", clickedTask.title)
            putExtra("time", clickedTask.time)
            putExtra("duration", clickedTask.durationMinutes)
            putExtra("location", clickedTask.locationName)
            putExtra("lat", clickedTask.latitude)
            putExtra("lng", clickedTask.longitude)
            putExtra("travelMode", clickedTask.travelMode)
            putExtra("startLocation", clickedTask.startLocationName)
            putExtra("startLat", clickedTask.startLatitude ?: 0.0)
            putExtra("startLng", clickedTask.startLongitude ?: 0.0)
        }
        addTaskLauncher.launch(intent)
    }

    private val addTaskLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val taskId = data.getIntExtra("id", -1)

            val existingTask = tasks.find { it.id == taskId }
            val oldTaskTime = existingTask?.time

            val task = existingTask ?: Task(
                id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            )

            task.apply {
                title = data.getStringExtra("title") ?: ""
                time = data.getLongExtra("time", 0L)
                durationMinutes = data.getIntExtra("duration", 30)
                locationName = data.getStringExtra("location") ?: ""
                latitude = data.getDoubleExtra("lat", 0.0)
                longitude = data.getDoubleExtra("lng", 0.0)
                travelMode = data.getStringExtra("travelMode") ?: TravelMode.DRIVING.value
                leaveTime = null

                val incomingStartName = data.getStringExtra("startLocation")?.trim()
                val incomingStartLat = data.getDoubleExtra("startLat", 0.0)
                val incomingStartLng = data.getDoubleExtra("startLng", 0.0)

                val hasManualStart = !incomingStartName.isNullOrBlank() && incomingStartLat != 0.0 && incomingStartLng != 0.0

                startLocationName = if (hasManualStart) incomingStartName else null
                startLatitude = if (hasManualStart) incomingStartLat else null
                startLongitude = if (hasManualStart) incomingStartLng else null

                resolvedStartLocationName = null
                routeSourceType = "gps"
                routeWarning = null
                hasRouteConflict = false
                distanceText = "---"
            }

            if (!tasks.contains(task)) tasks.add(task)

            firebaseManager.saveTask(task)
            adapter.notifyDataSetChanged()

            if (oldTaskTime != null && !isSameDay(oldTaskTime, task.time)) {
                recalculateRoutesForDay(oldTaskTime)
            }

            recalculateRoutesForDay(task.time)
            filterTasksForCalendar()
        }
    }

    override fun onResume() {
        super.onResume()
        if (firebaseManager.isUserLoggedIn()) {
            loadData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        networkManager = NetworkManager(this)
        loadingProgress = findViewById(R.id.loadingProgress)

        val txtAppTitle = findViewById<TextView>(R.id.txtAppTitle)
        val accentColor = ContextCompat.getColor(this, R.color.colorAccent)
        val primaryTextColor = ContextCompat.getColor(this, R.color.textColorPrimary)
        val fullTitle = "Smart Action Manager"
        val spannable = SpannableString(fullTitle)
        spannable.setSpan(ForegroundColorSpan(accentColor), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(primaryTextColor), 1, 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(accentColor), 6, 7, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(primaryTextColor), 7, 13, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(accentColor), 13, 14, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(primaryTextColor), 14, fullTitle.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        txtAppTitle.text = spannable

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationHelper = NotificationManagerHelper(this)

        adapter = TaskAdapter(
            tasks = tasks,
            onTaskEditClick = onTaskEdit,
            onNavigateClick = { startGoogleMapsNavigation(it) }
        )

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerTasks)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val recyclerCalendarTasks = findViewById<RecyclerView>(R.id.recyclerCalendarTasks)
        recyclerCalendarTasks.layoutManager = LinearLayoutManager(this)
        calendarAdapter = TaskAdapter(
            tasks = calendarTasks,
            onTaskEditClick = onTaskEdit,
            onNavigateClick = { startGoogleMapsNavigation(it) }
        )
        recyclerCalendarTasks.adapter = calendarAdapter

        setupSwipeToDelete(recyclerView)

        val btnAddTask = findViewById<FloatingActionButton>(R.id.btnAddTask)
        btnAddTask.setOnClickListener {
            addTaskLauncher.launch(Intent(this, AddTaskActivity::class.java))
        }

        val bottomNavigation = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_tasks -> {
                    recyclerView.visibility = View.VISIBLE
                    findViewById<View>(R.id.layoutCalendarTab).visibility = View.GONE
                    btnAddTask.show()
                    true
                }
                R.id.nav_calendar -> {
                    recyclerView.visibility = View.GONE
                    findViewById<View>(R.id.layoutCalendarTab).visibility = View.VISIBLE
                    btnAddTask.hide()
                    true
                }
                else -> false
            }
        }

        val calendarView = findViewById<CalendarView>(R.id.calendarView)
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            selectedCalendarDateMillis = cal.timeInMillis
            filterTasksForCalendar()
        }

        requestLocationPermissions()
        setupTrafficUpdateWorker()
    }

    private fun setupTrafficUpdateWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val trafficRequest = PeriodicWorkRequestBuilder<TrafficUpdateWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "TrafficUpdateWork",
            ExistingPeriodicWorkPolicy.KEEP,
            trafficRequest
        )
    }

    private fun startGoogleMapsNavigation(task: Task) {
        val origin = when {
            task.startLatitude != null && task.startLongitude != null ->
                "${task.startLatitude},${task.startLongitude}"
            task.routeSourceType == "chain" -> {
                val dayTasks = tasks.filter { isSameDay(it.time, task.time) }.sortedBy { it.time }
                val index = dayTasks.indexOfFirst { it.id == task.id }
                if (index > 0) "${dayTasks[index - 1].latitude},${dayTasks[index - 1].longitude}" else null
            }
            else -> null
        }

        if (origin != null) {
            val uriBuilder = Uri.parse("https://www.google.com/maps/dir/?api=1")
                .buildUpon()
                .appendQueryParameter("origin", origin)
                .appendQueryParameter("destination", "${task.latitude},${task.longitude}")
                .appendQueryParameter("travelmode", task.travelMode)
            val intent = Intent(Intent.ACTION_VIEW, uriBuilder.build()).apply { setPackage("com.google.android.apps.maps") }
            startActivity(intent)
            return
        }

        val mapMode = when (task.travelMode) {
            "walking" -> "w"
            "transit" -> "r"
            "bicycling" -> "b"
            else -> "d"
        }
        val gmmIntentUri = Uri.parse("google.navigation:q=${task.latitude},${task.longitude}&mode=$mapMode")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply { setPackage("com.google.android.apps.maps") }
        startActivity(mapIntent)
    }

    private fun loadData() {
        loadingProgress.visibility = View.VISIBLE
        firebaseManager.loadTasks(
            onSuccess = { loadedTasks ->
                runOnUiThread {
                    loadingProgress.visibility = View.GONE
                    tasks.clear()
                    tasks.addAll(loadedTasks)
                    adapter.notifyDataSetChanged()
                    
                    tasks.map { task ->
                        Calendar.getInstance().apply {
                            timeInMillis = task.time
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }.distinct().forEach { recalculateRoutesForDay(it) }
                    
                    filterTasksForCalendar()
                }
            },
            onFailure = { 
                runOnUiThread {
                    loadingProgress.visibility = View.GONE
                    Toast.makeText(this, getString(R.string.error_loading_data), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun recalculateRoutesForDay(dayMillis: Long) {
        val dayTasks = tasks.filter { isSameDay(it.time, dayMillis) }.sortedBy { it.time }
        if (dayTasks.isEmpty()) return

        fun calculateNext(index: Int, originLat: Double, originLng: Double) {
            if (index >= dayTasks.size) return

            val task = dayTasks[index]
            val hasManualStart = task.startLatitude != null && task.startLongitude != null
            val actualOriginLat = if (hasManualStart) task.startLatitude!! else originLat
            val actualOriginLng = if (hasManualStart) task.startLongitude!! else originLng

            if (actualOriginLat == 0.0 && actualOriginLng == 0.0) {
                calculateNext(index + 1, task.latitude, task.longitude)
                return
            }

            task.routeSourceType = when {
                hasManualStart -> "manual"
                index == 0 -> "gps"
                else -> "chain"
            }
            task.resolvedStartLocationName = when (task.routeSourceType) {
                "manual" -> task.startLocationName ?: getString(R.string.manual_start_point)
                "gps" -> getString(R.string.current_location)
                else -> dayTasks[index - 1].locationName
            }

            networkManager.fetchTravelTime(
                task = task,
                userLat = actualOriginLat,
                userLng = actualOriginLng,
                onSuccess = { updatedTask ->
                    runOnUiThread {
                        // Проверка за конфликт с предходната задача (ако има такава)
                        if (index > 0) {
                            updatedTask.checkAndSetConflict(this@MainActivity, dayTasks[index - 1].getEndTime())
                        } else {
                            updatedTask.hasRouteConflict = false
                            updatedTask.routeWarning = null
                        }

                        firebaseManager.saveTask(updatedTask)
                        if (!updatedTask.hasRouteConflict) notificationHelper.scheduleNotification(updatedTask)
                        else notificationHelper.cancelNotification(updatedTask.id)

                        // Обновяване на интерфейса
                        val globalIdx = tasks.indexOfFirst { it.id == updatedTask.id }
                        if (globalIdx != -1) adapter.notifyItemChanged(globalIdx)
                        
                        val calIdx = calendarTasks.indexOfFirst { it.id == updatedTask.id }
                        if (calIdx != -1) calendarAdapter.notifyItemChanged(calIdx)

                        calculateNext(index + 1, updatedTask.latitude, updatedTask.longitude)
                    }
                },
                onError = { calculateNext(index + 1, task.latitude, task.longitude) }
            )
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    val firstTask = dayTasks[0]
                    val startLat = firstTask.startLatitude ?: loc?.latitude ?: 0.0
                    val startLng = firstTask.startLongitude ?: loc?.longitude ?: 0.0
                    calculateNext(0, startLat, startLng)
                }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            loadData()
        }
    }

    private fun setupSwipeToDelete(recyclerView: RecyclerView) {
        ItemTouchHelper(SwipeToDeleteCallback(this) { position ->
            val task = tasks[position]
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_task_title)
                .setMessage(getString(R.string.dialog_delete_task_message, task.title))
                .setPositiveButton(R.string.btn_delete) { _, _ ->
                    firebaseManager.deleteTask(task.id, {
                        notificationHelper.cancelNotification(task.id)
                        tasks.removeAt(position)
                        adapter.notifyItemRemoved(position)
                        recalculateRoutesForDay(task.time)
                        filterTasksForCalendar()
                    }, { adapter.notifyItemChanged(position) })
                }
                .setNegativeButton(R.string.btn_cancel) { d, _ -> adapter.notifyItemChanged(position); d.dismiss() }
                .show()
        }).attachToRecyclerView(recyclerView)
    }

    private fun requestLocationPermissions() {
        val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) p.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, p.toTypedArray(), 1)
    }

    private fun filterTasksForCalendar() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedCalendarDateMillis }
        calendarTasks.clear()
        calendarTasks.addAll(tasks.filter { isSameDay(it.time, cal.timeInMillis) })
        calendarAdapter.notifyDataSetChanged()
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }
}