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
import java.util.Calendar

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
                locationName = data.getStringExtra("location") ?: ""
                latitude = data.getDoubleExtra("lat", 0.0)
                longitude = data.getDoubleExtra("lng", 0.0)
                travelMode = data.getStringExtra("travelMode") ?: TravelMode.DRIVING.value
                leaveTime = null

                val incomingStartName = data.getStringExtra("startLocation")?.trim()
                val incomingStartLat = data.getDoubleExtra("startLat", 0.0)
                val incomingStartLng = data.getDoubleExtra("startLng", 0.0)

                val hasManualStart =
                    !incomingStartName.isNullOrBlank() &&
                            incomingStartLat != 0.0 &&
                            incomingStartLng != 0.0

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

        // Initialize stylized header
        val txtAppTitle = findViewById<TextView>(R.id.txtAppTitle)
        
        val accentColor = ContextCompat.getColor(this, R.color.colorAccent)
        val primaryTextColor = ContextCompat.getColor(this, R.color.textColorPrimary)

        val fullTitle = "Smart Action Manager"
        val spannable = SpannableString(fullTitle)

        // Highlight first letters (S.A.M)
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
            onNavigateClick = { clickedTask ->
                startGoogleMapsNavigation(clickedTask)
            }
        )

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerTasks)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Setup separate list for calendar view filtering
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

        // Apply "breathing" animation to the FAB
        val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
            btnAddTask,
            PropertyValuesHolder.ofFloat("scaleX", 0.93f),
            PropertyValuesHolder.ofFloat("scaleY", 0.93f)
        )
        scaleDown.duration = 1000
        scaleDown.repeatCount = ObjectAnimator.INFINITE
        scaleDown.repeatMode = ObjectAnimator.REVERSE
        scaleDown.start()

        btnAddTask.setOnClickListener {
            addTaskLauncher.launch(Intent(this, AddTaskActivity::class.java))
        }

        val layoutCalendarTab = findViewById<View>(R.id.layoutCalendarTab)
        val bottomNavigation = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)

        // Handle navigation between Tasks and Calendar tabs
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_tasks -> {
                    recyclerView.visibility = View.VISIBLE
                    layoutCalendarTab.visibility = View.GONE
                    btnAddTask.show()
                    true
                }
                R.id.nav_calendar -> {
                    recyclerView.visibility = View.GONE
                    layoutCalendarTab.visibility = View.VISIBLE
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
        // ---------------------------------------------

        requestLocationPermissions()
    }

    private fun startGoogleMapsNavigation(task: Task) {
        val mapMode = when (task.travelMode) {
            "walking" -> "w"
            "transit" -> "r"
            "bicycling" -> "b"
            else -> "d"
        }

        val gmmIntentUri = Uri.parse("google.navigation:q=${task.latitude},${task.longitude}&mode=$mapMode")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")

        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${task.latitude},${task.longitude}&travelmode=${task.travelMode}")
            startActivity(Intent(Intent.ACTION_VIEW, browserUri))
        }
    }

    private fun loadData() {
        loadingProgress.visibility = View.VISIBLE
        firebaseManager.loadTasks(
            onSuccess = { loadedTasks ->
                runOnUiThread {
                    loadingProgress.visibility = View.GONE
                    tasks.clear()
                    tasks.addAll(loadedTasks)
                    
                    // Ensure the recycler view is visible
                    findViewById<RecyclerView>(R.id.recyclerTasks).visibility = View.VISIBLE
                    findViewById<View>(R.id.layoutCalendarTab).visibility = View.GONE

                    val uniqueDays = tasks.map { task ->
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = task.time
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        cal.timeInMillis
                    }.distinct()

                    uniqueDays.forEach { dayMillis ->
                        recalculateRoutesForDay(dayMillis)
                    }
                    adapter.notifyDataSetChanged()
                    filterTasksForCalendar()
                }
            },
            onFailure = { 
                runOnUiThread {
                    loadingProgress.visibility = View.GONE
                    Log.e("MainActivity", "Failed to load tasks", it)
                    Toast.makeText(this, getString(R.string.error_loading_data), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun setupSwipeToDelete(recyclerView: RecyclerView) {
        val swipeCallback = SwipeToDeleteCallback(this) { position ->
            val taskToDelete = tasks[position]
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_delete_task_title))
                .setMessage(getString(R.string.dialog_delete_task_message, taskToDelete.title))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                    firebaseManager.deleteTask(
                        taskId = taskToDelete.id,
                        onSuccess = {
                            notificationHelper.cancelNotification(taskToDelete.id)

                            val deletedTaskTime = taskToDelete.time

                            tasks.removeAt(position)
                            adapter.notifyItemRemoved(position)

                            recalculateRoutesForDay(deletedTaskTime)
                            filterTasksForCalendar()
                        },
                        onFailure = { 
                            adapter.notifyItemChanged(position)
                            Toast.makeText(this, getString(R.string.error_loading_data), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                .setNegativeButton(getString(R.string.btn_cancel)) { dialog, _ ->
                    adapter.notifyItemChanged(position)
                    dialog.dismiss()
                }
                .show()
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)
    }

    private fun requestLocationPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
    }
    private fun filterTasksForCalendar() {
        val targetCal = Calendar.getInstance()
        targetCal.timeInMillis = selectedCalendarDateMillis
        val targetYear = targetCal.get(Calendar.YEAR)
        val targetDayOfYear = targetCal.get(Calendar.DAY_OF_YEAR)

        calendarTasks.clear()

        for (task in tasks) {
            val taskCal = Calendar.getInstance()
            taskCal.timeInMillis = task.time

            if (taskCal.get(Calendar.YEAR) == targetYear &&
                taskCal.get(Calendar.DAY_OF_YEAR) == targetDayOfYear) {
                calendarTasks.add(task)
            }
        }

        calendarAdapter.notifyDataSetChanged()
    }

    private fun recalculateRoutesForDay(dayMillis: Long) {
        val dayTasks = tasks
            .filter { isSameDay(it.time, dayMillis) }
            .sortedBy { it.time }

        if (dayTasks.isEmpty()) return

        fun calculateNext(index: Int, originLat: Double, originLng: Double) {
            if (index >= dayTasks.size) {
                adapter.notifyDataSetChanged()
                filterTasksForCalendar()
                return
            }

            val task = dayTasks[index]

            val hasManualStart = task.startLatitude != null && task.startLongitude != null

            val actualOriginLat = if (hasManualStart) task.startLatitude!! else originLat
            val actualOriginLng = if (hasManualStart) task.startLongitude!! else originLng

            when {
                hasManualStart -> {
                    task.routeSourceType = "manual"
                    task.resolvedStartLocationName = task.startLocationName ?: getString(R.string.manual_start_point)
                }

                index == 0 -> {
                    task.routeSourceType = "gps"
                    task.resolvedStartLocationName = getString(R.string.current_location)
                }

                else -> {
                    val previousTask = dayTasks[index - 1]
                    task.routeSourceType = "chain"
                    task.resolvedStartLocationName = previousTask.locationName
                }
            }

            networkManager.fetchTravelTime(
                task = task,
                userLat = actualOriginLat,
                userLng = actualOriginLng,
                onSuccess = { updatedTask ->
                    runOnUiThread {
                        val isChain = updatedTask.routeSourceType == "chain"
                        val previousTask = if (index > 0) dayTasks[index - 1] else null

                        if (isChain && previousTask != null) {
                            val calculatedLeaveTime = updatedTask.leaveTime ?: 0L

                            if (calculatedLeaveTime < previousTask.time) {
                                updatedTask.hasRouteConflict = true
                                updatedTask.routeWarning = getString(R.string.route_conflict_warning)
                            } else {
                                updatedTask.hasRouteConflict = false
                                updatedTask.routeWarning = null
                            }
                        } else {
                            updatedTask.hasRouteConflict = false
                            updatedTask.routeWarning = null
                        }

                        firebaseManager.saveTask(updatedTask)

                        if (!updatedTask.hasRouteConflict) {
                            notificationHelper.scheduleNotification(updatedTask)
                        } else {
                            notificationHelper.cancelNotification(updatedTask.id)
                        }

                        calculateNext(
                            index + 1,
                            updatedTask.latitude,
                            updatedTask.longitude
                        )
                    }
                },
                onError = { error ->
                    Log.e("SAM_Routing", "Грешка при маршрут за ${task.title}: $error")

                    runOnUiThread {
                        calculateNext(
                            index + 1,
                            task.latitude,
                            task.longitude
                        )
                    }
                }
            )
        }

        val firstTask = dayTasks.first()

        if (firstTask.startLatitude != null && firstTask.startLongitude != null) {
            calculateNext(0, firstTask.startLatitude!!, firstTask.startLongitude!!)
            return
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    calculateNext(0, location.latitude, location.longitude)
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_location_unavailable),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }
    private fun isSameDay(firstMillis: Long, secondMillis: Long): Boolean {
        val firstCal = Calendar.getInstance().apply {
            timeInMillis = firstMillis
        }

        val secondCal = Calendar.getInstance().apply {
            timeInMillis = secondMillis
        }

        return firstCal.get(Calendar.YEAR) == secondCal.get(Calendar.YEAR) &&
                firstCal.get(Calendar.DAY_OF_YEAR) == secondCal.get(Calendar.DAY_OF_YEAR)
    }
}