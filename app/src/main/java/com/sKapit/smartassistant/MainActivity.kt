// MainActivity.kt
package com.sKapit.smartassistant

import android.net.Uri
import android.view.View
import android.widget.CalendarView
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
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
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder

class MainActivity : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var adapter: TaskAdapter
    private val tasks = mutableListOf<Task>()

    private val firebaseManager = FirebaseManager()
    private val networkManager = NetworkManager()
    private lateinit var notificationHelper: NotificationManagerHelper
    private lateinit var calendarAdapter: TaskAdapter // Адаптер за задачите в календара
    private val calendarTasks = mutableListOf<Task>() // Списък само за филтрираните задачи
    private var selectedCalendarDateMillis: Long = System.currentTimeMillis() // Пазим избраната дата

    private val addTaskLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val taskId = data?.getIntExtra("id", -1) ?: -1
            val task = tasks.find { it.id == taskId } ?: Task(id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt())

            task.apply {
                title = data?.getStringExtra("title") ?: ""
                time = data?.getLongExtra("time", 0L) ?: 0L
                locationName = data?.getStringExtra("location") ?: ""
                latitude = data?.getDoubleExtra("lat", 0.0) ?: 0.0
                longitude = data?.getDoubleExtra("lng", 0.0) ?: 0.0
                travelMode = data?.getStringExtra("travelMode") ?: "driving"
                leaveTime = null
            }

            if (!tasks.contains(task)) tasks.add(task)

            adapter.notifyDataSetChanged()
            firebaseManager.saveTask(task)
            calculateLeaveTimeForTask(task)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
            onTaskEditClick = { clickedTask ->
                val intent = Intent(this, AddTaskActivity::class.java).apply {
                    putExtra("isEdit", true)
                    putExtra("id", clickedTask.id)
                    putExtra("title", clickedTask.title)
                    putExtra("time", clickedTask.time)
                    putExtra("location", clickedTask.locationName)
                    putExtra("lat", clickedTask.latitude)
                    putExtra("lng", clickedTask.longitude)
                    putExtra("travelMode", clickedTask.travelMode)
                }
                addTaskLauncher.launch(intent)
            },
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
            onTaskEditClick = { /* Logic shared with main adapter */ },
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
            val cal = java.util.Calendar.getInstance()
            cal.set(year, month, dayOfMonth, 0, 0, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            selectedCalendarDateMillis = cal.timeInMillis

            filterTasksForCalendar()
        }
        // ---------------------------------------------

        if (firebaseManager.isUserLoggedIn()) {
            loadData()
        }

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
        firebaseManager.loadTasks(
            onSuccess = { loadedTasks ->
                tasks.clear()
                tasks.addAll(loadedTasks)
                // Trigger travel time calculation for tasks without it
                tasks.forEach { if (it.leaveTime == null || it.leaveTime == 0L) calculateLeaveTimeForTask(it) }
                adapter.notifyDataSetChanged()
                filterTasksForCalendar()
            },
            onFailure = { 
                Log.e("MainActivity", "Failed to load tasks", it)
                android.widget.Toast.makeText(this, "Грешка при зареждане на данните", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun calculateLeaveTimeForTask(task: Task) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { location ->
            if (location != null) {
                networkManager.fetchTravelTime(
                    task = task,
                    userLat = location.latitude,
                    userLng = location.longitude,
                    onSuccess = { updatedTask ->
                        runOnUiThread {
                            adapter.notifyDataSetChanged()
                            firebaseManager.saveTask(updatedTask)
                            notificationHelper.scheduleNotification(updatedTask)
                        }
                    },
                    onError = { 
                        Log.e("MainActivity", "Network error: $it")
                        // Silent fail for travel time unless critical, but log it
                    }
                )
            } else {
                android.widget.Toast.makeText(this, "Не може да се определи местоположението", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSwipeToDelete(recyclerView: RecyclerView) {
        val swipeCallback = SwipeToDeleteCallback(this) { position ->
            val taskToDelete = tasks[position]
            MaterialAlertDialogBuilder(this)
                .setTitle("Изтриване на задача")
                .setMessage("Сигурни ли сте, че искате да изтриете '${taskToDelete.title}'?")
                .setCancelable(false)
                .setPositiveButton("Изтрий") { _, _ ->
                    firebaseManager.deleteTask(
                        taskId = taskToDelete.id,
                        onSuccess = {
                            notificationHelper.cancelNotification(taskToDelete.id)
                            tasks.removeAt(position)
                            adapter.notifyItemRemoved(position)
                        },
                        onFailure = { adapter.notifyItemChanged(position) }
                    )
                }
                .setNegativeButton("Отказ") { dialog, _ ->
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
        val targetCal = java.util.Calendar.getInstance()
        targetCal.timeInMillis = selectedCalendarDateMillis
        val targetYear = targetCal.get(java.util.Calendar.YEAR)
        val targetDayOfYear = targetCal.get(java.util.Calendar.DAY_OF_YEAR)

        calendarTasks.clear()

        for (task in tasks) {
            val taskCal = java.util.Calendar.getInstance()
            taskCal.timeInMillis = task.time

            if (taskCal.get(java.util.Calendar.YEAR) == targetYear &&
                taskCal.get(java.util.Calendar.DAY_OF_YEAR) == targetDayOfYear) {
                calendarTasks.add(task)
            }
        }

        calendarAdapter.notifyDataSetChanged()
    }
}