package com.sKapit.smartassistant

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class AddTaskActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_task)

        val inputTitle = findViewById<EditText>(R.id.inputTitle)
        val inputTime = findViewById<EditText>(R.id.inputTime)
        val inputLocation = findViewById<EditText>(R.id.inputLocation)
        val btnSave = findViewById<Button>(R.id.btnSave)

        btnSave.setOnClickListener {
            val title = inputTitle.text.toString()
            val location = inputLocation.text.toString()

            // Parse HH:mm input into a Long timestamp
            val timeStr = inputTime.text.toString()
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val date = try { sdf.parse(timeStr) } catch (e: Exception) { null }

            // If parsing fails, use current time
            val timeLong = date?.time ?: System.currentTimeMillis()

            val intent = Intent()
            intent.putExtra("title", title)
            intent.putExtra("time", timeLong)
            intent.putExtra("location", location)

            setResult(RESULT_OK, intent)
            finish()
        }
    }
}