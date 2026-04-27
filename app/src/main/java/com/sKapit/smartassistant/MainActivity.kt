package com.sKapit.smartassistant

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.gms.location.Priority
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import androidx.core.text.HtmlCompat
import android.widget.TextView
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.graphics.drawable.GradientDrawable
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt

class MainActivity : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var adapter: TaskAdapter
    private val tasks = mutableListOf<Task>()

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var userId: String = ""
    private var editingTaskId: Int? = null

    // Task addition launcher
    private val addTaskLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val taskId = data?.getIntExtra("id", -1) ?: -1

                // Edit existing or add new task
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

                if (!tasks.contains(task)) {
                    tasks.add(task)
                }

                adapter.notifyDataSetChanged()
                saveTaskToCloud(task)
                calculateLeaveTimeForTask(task)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        auth = Firebase.auth
        db = Firebase.firestore
        userId = auth.currentUser?.uid ?: ""

        // Initialize adapter with click listener
        adapter = TaskAdapter(tasks) { clickedTask ->
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
            editingTaskId = clickedTask.id
            addTaskLauncher.launch(intent)
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerTasks)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        setupSwipeToDelete(recyclerView)

        findViewById<Button>(R.id.btnAddTask).setOnClickListener {
            val intent = Intent(this, AddTaskActivity::class.java)
            addTaskLauncher.launch(intent)
        }

        if (userId.isNotEmpty()) {
            loadTasksFromCloud()
        }

        requestLocationPermissions()
    }

    private fun requestLocationPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
    }

    // Get current location and start leave time calculation
    private fun calculateLeaveTimeForTask(task: Task) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    fetchTravelTimeFromGoogle(task, location.latitude, location.longitude)
                }
            }
    }

    // Call Google Distance Matrix API
    private fun fetchTravelTimeFromGoogle(task: Task, userLat: Double, userLng: Double) {
        val client = OkHttpClient()
        val mode = task.travelMode
        val apiKey = BuildConfig.MAPS_API_KEY
        val url = "https://maps.googleapis.com/maps/api/distancematrix/json" +
                "?origins=$userLat,$userLng" +
                "&destinations=${task.latitude},${task.longitude}" +
                "&mode=$mode" +
                "&key=$apiKey"

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                if (responseBody != null) {
                    try {
                        val jsonObject = JSONObject(responseBody)
                        val rows = jsonObject.getJSONArray("rows")
                        val elements = rows.getJSONObject(0).getJSONArray("elements")

                        val status = elements.getJSONObject(0).getString("status")
                        if (status == "ZERO_RESULTS") return

                        val duration = elements.getJSONObject(0).getJSONObject("duration")
                        val travelTimeSeconds = duration.getLong("value")
                        val distance = elements.getJSONObject(0).getJSONObject("distance")
                        val distanceText = distance.getString("text")

                        task.distanceText = distanceText

                        val bufferSeconds = 10 * 60
                        val leaveTimeMillis = task.time - (travelTimeSeconds * 1000) - (bufferSeconds * 1000)

                        task.leaveTime = leaveTimeMillis

                        runOnUiThread {
                            adapter.notifyDataSetChanged()
                            saveTaskToCloud(task)
                            scheduleNotification(task)
                        }
                    } catch (e: Exception) {
                        Log.e("API_TEST", "Parsing error: ${e.message}")
                    }
                }
            }
        })
    }
    private fun scheduleNotification(task: Task) {
        val leaveTime = task.leaveTime ?: return

        val intent = Intent(this, NotificationReceiver::class.java).apply {
            putExtra("TASK_TITLE", task.title)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            task.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                leaveTime,
                pendingIntent
            )
        } catch (e: SecurityException) {
            Log.e("SmartAssistant_Debug", "No permission for Exact Alarm")
        }
    }
    private fun loadTasksFromCloud() {
        db.collection("users").document(userId).collection("tasks")
            .get()
            .addOnSuccessListener { result ->
                tasks.clear()
                for (document in result) {
                    val task = document.toObject(Task::class.java)
                    tasks.add(task)

                    // Calculate leave time if missing
                    if (task.leaveTime == null || task.leaveTime == 0L) {
                        calculateLeaveTimeForTask(task)
                    }
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun saveTaskToCloud(task: Task) {
        db.collection("users").document(userId).collection("tasks").document(task.id.toString())
            .set(task)
    }
    private fun cancelNotification(taskId: Int) {
        val intent = Intent(this, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            taskId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }
    private fun setupSwipeToDelete(recyclerView: RecyclerView) {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val density = itemView.context.resources.displayMetrics.density

                val marginH = (8 * density).toInt()
                val marginV = (6 * density).toInt()
                val cornerRadius = 12 * density
                val samOffsetDistance = (-20 * density).toInt()

                val background = GradientDrawable()
                background.setColor("#FF4081".toColorInt())
                background.cornerRadius = cornerRadius
                val top = itemView.top + marginV
                val bottom = itemView.bottom - marginV

                val swipeRatio = Math.abs(dX) / itemView.width
                val bgAlpha = Math.min(140, (swipeRatio * 2 * 255).toInt())
                background.alpha = bgAlpha

                val icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_sam_super)

                if (icon != null) {
                    val cardHeight = bottom - top
                    val iconSize = (cardHeight * 1.2).toInt()
                    val iconTop = top + (cardHeight - iconSize) / 2
                    val iconBottom = iconTop + iconSize

                    if (dX > 0) { // Swipe Right
                        val leftBounds = itemView.left + marginH
                        val rightBounds = itemView.left + dX.toInt()
                        if (rightBounds > leftBounds) {
                            background.setBounds(leftBounds, top, rightBounds, bottom)
                            background.draw(c)
                        }

                        val iconRight = itemView.left + dX.toInt() - samOffsetDistance
                        val iconLeft = iconRight - iconSize
                        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        icon.draw(c)

                    } else if (dX < 0) { // Swipe Left
                        val rightBounds = itemView.right - marginH
                        val leftBounds = itemView.right + dX.toInt()
                        if (leftBounds < rightBounds) {
                            background.setBounds(leftBounds, top, rightBounds, bottom)
                            background.draw(c)
                        }

                        val iconLeft = itemView.right + dX.toInt() + samOffsetDistance
                        val iconRight = iconLeft + iconSize
                        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)

                        c.save()
                        val pivotX = iconLeft + (iconSize / 2f)
                        val pivotY = iconTop + (iconSize / 2f)
                        c.scale(-1f, 1f, pivotX, pivotY)
                        icon.draw(c)
                        c.restore()
                    }
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val taskToDelete = tasks[position]

                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Delete task")
                    .setMessage("Are you sure you want to delete '${taskToDelete.title}'?")
                    .setCancelable(false)
                    .setPositiveButton("Delete") { _, _ ->
                        db.collection("users").document(userId).collection("tasks").document(taskToDelete.id.toString())
                            .delete()
                            .addOnSuccessListener {
                                cancelNotification(taskToDelete.id)
                                tasks.removeAt(position)
                                adapter.notifyItemRemoved(position)
                            }
                            .addOnFailureListener {
                                adapter.notifyItemChanged(position)
                            }
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        adapter.notifyItemChanged(position)
                        dialog.dismiss()
                    }
                    .show()
            }
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
}