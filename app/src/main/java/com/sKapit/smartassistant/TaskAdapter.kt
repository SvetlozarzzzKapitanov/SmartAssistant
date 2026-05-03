package com.sKapit.smartassistant

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class TaskAdapter(
    private var tasks: List<Task>,
    private val onTaskEditClick: (Task) -> Unit,
    private val onNavigateClick: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private var lastPosition = -1
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.taskTitle)
        val arrivalTime: TextView = itemView.findViewById(R.id.taskArrivalTime)
        val location: TextView = itemView.findViewById(R.id.taskLocation)
        val distance: TextView = itemView.findViewById(R.id.taskDistance)
        val status: TextView = itemView.findViewById(R.id.taskStatus)
        val iconExpand: ImageView = itemView.findViewById(R.id.iconExpand)
        val iconTransportMode: ImageView = itemView.findViewById(R.id.iconTransportMode)
        val layoutExpanded: LinearLayout = itemView.findViewById(R.id.layoutExpanded)
        val fullLeaveTime: TextView = itemView.findViewById(R.id.txtFullLeaveTime)
        val fullArrivalTime: TextView = itemView.findViewById(R.id.txtFullArrivalTime)
        val btnNavigate: MaterialButton = itemView.findViewById(R.id.btnNavigate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        val context = holder.itemView.context

        with(holder) {
            title.text = task.title
            location.text = task.locationName
            distance.text = task.distanceText
            arrivalTime.text = task.getFormattedArrivalTime()

            // Icon logic
            val modeIconRes = when (task.travelMode) {
                TravelMode.WALKING.value -> R.drawable.ic_walk
                TravelMode.TRANSIT.value -> R.drawable.ic_public_transport
                else -> R.drawable.ic_car
            }
            iconTransportMode.setImageResource(modeIconRes)

            // Status and Colors using Task model logic
            val (statusText, statusColor) = task.getStatusData()
            status.text = statusText
            status.setTextColor(statusColor)

            // Time details
            task.leaveTime?.takeIf { it > 0 }?.let { leaveTime ->
                fullLeaveTime.text = context.getString(R.string.label_leave_time, timeFormatter.format(Date(leaveTime)))
                fullArrivalTime.text = context.getString(R.string.label_arrival_time, timeFormatter.format(Date(task.time)))
            } ?: run {
                val placeholder = context.getString(R.string.placeholder_time)
                fullLeaveTime.text = context.getString(R.string.label_leave_time, placeholder)
                fullArrivalTime.text = context.getString(R.string.label_arrival_time, placeholder)
            }

            // Expand / Collapse logic
            layoutExpanded.isVisible = task.isExpanded
            iconExpand.rotation = if (task.isExpanded) 180f else 0f

            itemView.setOnClickListener {
                task.isExpanded = !task.isExpanded
                notifyItemChanged(position)
            }

            itemView.setOnLongClickListener {
                onTaskEditClick(task)
                true
            }

            btnNavigate.setOnClickListener {
                onNavigateClick(task)
            }

            // Cascade animation
            if (position > lastPosition) {
                animateEntry(itemView, position)
                lastPosition = position
            }
        }
    }

    private fun animateEntry(view: View, position: Int) {
        view.alpha = 0f
        view.translationY = 100f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay((position * 50).toLong())
            .start()
    }

    override fun getItemCount() = tasks.size
}
