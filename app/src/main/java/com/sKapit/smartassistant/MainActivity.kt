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

class MainActivity : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var storage: TaskStorage
    private lateinit var adapter: TaskAdapter
    private val tasks = mutableListOf<Task>()

    // Launcher за добавяне на задача
    private val addTaskLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val title = data?.getStringExtra("title") ?: ""
                val time = data?.getLongExtra("time", System.currentTimeMillis()) ?: System.currentTimeMillis()
                val location = data?.getStringExtra("location") ?: ""
                val lat = data?.getDoubleExtra("lat", 0.0) ?: 0.0
                val lng = data?.getDoubleExtra("lng", 0.0) ?: 0.0

                val newTask = Task(
                    id = tasks.size + 1,
                    title = title,
                    time = time,
                    locationName = location,
                    latitude = lat,
                    longitude = lng
                )

                tasks.add(newTask)
                adapter.notifyItemInserted(tasks.size - 1)
                storage.saveTasks(tasks)
                calculateLeaveTimeForTask(newTask)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Инициализация на съхранението
        storage = TaskStorage(this)
        tasks.addAll(storage.loadTasks())

        // 2. Настройка на UI
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerTasks)
        adapter = TaskAdapter(tasks)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 3. Инициализация на GPS клиента
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 4. Искане на разрешения
        requestLocationPermissions()

        // 5. Настройка на бутона за добавяне
        findViewById<Button>(R.id.btnAddTask).setOnClickListener {
            val intent = Intent(this, AddTaskActivity::class.java)
            addTaskLauncher.launch(intent)
        }

        for (task in tasks) {
            calculateLeaveTimeForTask(task)
        }
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            1
        )
    }

    // 1. Функция за взимане на текущия GPS и стартиране на изчислението
    private fun calculateLeaveTimeForTask(task: Task) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Няма достъп до GPS!", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    fetchTravelTimeFromGoogle(task, location.latitude, location.longitude)
                } else {
                    Toast.makeText(this, "Не може да бъде открита локация", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // 2. Функция, която пита Distance Matrix API
    private fun fetchTravelTimeFromGoogle(task: Task, userLat: Double, userLng: Double) {
        val client = OkHttpClient()
        val apiKey = BuildConfig.MAPS_API_KEY
        val url = "https://maps.googleapis.com/maps/api/distancematrix/json" +
                "?origins=$userLat,$userLng" +
                "&destinations=${task.latitude},${task.longitude}" +
                "&mode=driving" +
                "&key=$apiKey"

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this@MainActivity, "Грешка в мрежата", Toast.LENGTH_SHORT).show() }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                // 1. Принтираме отговор от Google в Logcat
                Log.d("API_TEST", "Отговор от Google: $responseBody")

                if (responseBody != null) {
                    try {
                        val jsonObject = JSONObject(responseBody)
                        val rows = jsonObject.getJSONArray("rows")
                        val elements = rows.getJSONObject(0).getJSONArray("elements")

                        // Проверяваме дали Google изобщо е намерил път
                        val status = elements.getJSONObject(0).getString("status")
                        if (status == "ZERO_RESULTS") {
                            runOnUiThread { Toast.makeText(this@MainActivity, "Няма намерен маршрут!", Toast.LENGTH_LONG).show() }
                            return
                        }

                        val duration = elements.getJSONObject(0).getJSONObject("duration")
                        val travelTimeSeconds = duration.getLong("value")
                        val travelTimeText = duration.getString("text")

                        val bufferSeconds = 10 * 60
                        val leaveTimeMillis = task.time - (travelTimeSeconds * 1000) - (bufferSeconds * 1000)

                        task.leaveTime = leaveTimeMillis

                        runOnUiThread {
                            adapter.notifyDataSetChanged()
                            storage.saveTasks(tasks)
                            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                            Toast.makeText(this@MainActivity, "Тръгни в ${sdf.format(Date(leaveTimeMillis))}", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Log.e("API_TEST", "Грешка при парсване: ${e.message}")
                        runOnUiThread { Toast.makeText(this@MainActivity, "JSON Грешка: ${e.message}", Toast.LENGTH_LONG).show() }
                    }
                }
            }
        })
    }
}