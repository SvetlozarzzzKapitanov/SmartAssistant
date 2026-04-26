package com.sKapit.smartassistant

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var storage: TaskStorage
    private lateinit var adapter: TaskAdapter
    private val tasks = mutableListOf<Task>()

    private val addTaskLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val title = data?.getStringExtra("title") ?: ""
                val time = data?.getLongExtra("time", System.currentTimeMillis()) ?: System.currentTimeMillis()
                val location = data?.getStringExtra("location") ?: ""

                val newTask = Task(
                    id = tasks.size + 1,
                    title = title,
                    time = time,
                    locationName = location
                )

                tasks.add(newTask)
                adapter.notifyItemInserted(tasks.size - 1)
                storage.saveTasks(tasks) // Save to SharedPreferences
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storage = TaskStorage(this)
        tasks.addAll(storage.loadTasks())

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerTasks)
        adapter = TaskAdapter(tasks)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btnAddTask).setOnClickListener {
            val intent = Intent(this, AddTaskActivity::class.java)
            addTaskLauncher.launch(intent)
        }
    }
}