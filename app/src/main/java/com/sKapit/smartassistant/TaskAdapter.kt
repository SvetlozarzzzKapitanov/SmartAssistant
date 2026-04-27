package com.sKapit.smartassistant

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class TaskAdapter(private val tasks: List<Task>) :
    RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.taskTitle)
        val time: TextView = itemView.findViewById(R.id.taskTime)
        val leaveTime: TextView = itemView.findViewById(R.id.taskLeaveTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.title.text = task.title

        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        holder.time.text = "Събитие: " + sdf.format(Date(task.time))

        if (task.leaveTime != null) {
            holder.leaveTime.visibility = View.VISIBLE
            holder.leaveTime.text = "Тръгни в: " + sdf.format(Date(task.leaveTime!!))
        } else {
            holder.leaveTime.visibility = View.GONE
        }
    }

    override fun getItemCount() = tasks.size
}