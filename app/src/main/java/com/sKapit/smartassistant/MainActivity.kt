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

        // 4. Искане на разрешение за локация при старт
        requestLocationPermissions()

        findViewById<Button>(R.id.btnAddTask).setOnClickListener {
            val intent = Intent(this, AddTaskActivity::class.java)
            addTaskLauncher.launch(intent)
        }
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            1
        )
    }
}