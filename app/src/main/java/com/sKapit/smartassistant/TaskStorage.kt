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
            Task(1, "Лекция", time = System.currentTimeMillis(), locationName = "ТУ София"),
            Task(2, "Среща", time = System.currentTimeMillis() + 3600000, locationName = "Ресторант")
        )
        saveTasks(defaultTasks)
        return defaultTasks
    }
}