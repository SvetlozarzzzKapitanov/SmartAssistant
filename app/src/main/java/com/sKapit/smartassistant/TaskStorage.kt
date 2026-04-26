package com.sKapit.smartassistant

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TaskStorage(context: Context) {
    private val prefs = context.getSharedPreferences("tasks_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveTasks(tasks: List<Task>) {
        val json = gson.toJson(tasks)
        prefs.edit().putString("tasks", json).apply()
    }

    fun loadTasks(): MutableList<Task> {
        val json = prefs.getString("tasks", null)
        if (json != null) {
            val type = object : TypeToken<MutableList<Task>>() {}.type
            return gson.fromJson(json, type)
        }
        return createDefaultTasks()
    }

    private fun createDefaultTasks(): MutableList<Task> {
        val defaultTasks = mutableListOf(
            Task(
                id = 1,
                title = "Лекция",
                time = System.currentTimeMillis(),
                locationName = "ТУ София",
                latitude = 42.6536,
                longitude = 23.3551
            ),
            Task(
                id = 2,
                title = "Среща",
                time = System.currentTimeMillis() + 3600000,
                locationName = "Ресторант",
                latitude = 42.6977,
                longitude = 23.3219
            )
        )
        saveTasks(defaultTasks)
        return defaultTasks
    }
}