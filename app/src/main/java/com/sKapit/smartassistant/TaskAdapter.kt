package com.sKapit.smartassistant

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class TaskAdapter(
    private val tasks: List<Task>,
    private val onTaskClick: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.taskTitle)
        val arrivalTime: TextView = itemView.findViewById(R.id.taskArrivalTime)
        val leaveTime: TextView = itemView.findViewById(R.id.taskLeaveTime)
        val location: TextView = itemView.findViewById(R.id.taskLocation)
        val mode: TextView = itemView.findViewById(R.id.taskTravelMode)
        val distance: TextView = itemView.findViewById(R.id.taskDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.title.text = task.title
        holder.location.text = "Локация: ${task.locationName}"
        holder.distance.text = task.distanceText ?: "-- км"

        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        holder.arrivalTime.text = "Пристигане: ${sdf.format(Date(task.time))}"

        // Travel mode
        holder.mode.text = when(task.travelMode) {
            "walking" -> "Пеша"
            "transit" -> "Градски транспорт"
            else -> "Кола"
        }

        // Leave time calculation
        if (task.leaveTime != null && task.leaveTime!! > 0) {
            holder.leaveTime.visibility = View.VISIBLE
            holder.leaveTime.text = "Тръгни в: ${sdf.format(Date(task.leaveTime!!))}"
            holder.leaveTime.setTextColor(Color.parseColor("#D32F2F"))
        } else {
            holder.leaveTime.text = "Изчисляване..."
            holder.leaveTime.setTextColor(Color.GRAY)
        }

        holder.itemView.setOnClickListener { onTaskClick(task) }
    }

    override fun getItemCount() = tasks.size
}