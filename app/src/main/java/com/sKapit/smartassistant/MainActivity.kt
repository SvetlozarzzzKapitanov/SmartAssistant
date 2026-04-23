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
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TaskAdapter
    private val tasks = mutableListOf<Task>()

    private val ADD_TASK_REQUEST = 1

    private val addTaskLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data

                val title = data?.getStringExtra("title") ?: return@registerForActivityResult
                val time = data.getStringExtra("time") ?: ""
                val location = data.getStringExtra("location") ?: ""

                tasks.add(Task(title, time, location))
                adapter.notifyDataSetChanged()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerTasks)
        val btnAdd = findViewById<Button>(R.id.btnAddTask)

        adapter = TaskAdapter(tasks)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // test danni
        tasks.add(Task("Лекция", "10:00", "ТУ София"))
        tasks.add(Task("Среща", "18:00", "Ресторант"))

        adapter.notifyDataSetChanged()

        btnAdd.setOnClickListener {
            val intent = Intent(this, AddTaskActivity::class.java)
            addTaskLauncher.launch(intent)
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == ADD_TASK_REQUEST && resultCode == RESULT_OK) {
            val title = data?.getStringExtra("title") ?: ""
            val time = data?.getStringExtra("time") ?: ""
            val location = data?.getStringExtra("location") ?: ""

            tasks.add(Task(title, time, location))
            adapter.notifyDataSetChanged()
        }
    }
}