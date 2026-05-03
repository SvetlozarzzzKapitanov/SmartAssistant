package com.sKapit.smartassistant

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class TaskAdapter(
    private val tasks: List<Task>,
    private val onTaskEditClick: (Task) -> Unit,
    private val onNavigateClick: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {
    private var lastPosition = -1

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

        holder.title.text = task.title
        holder.location.text = task.locationName

        val modeIconRes = when (task.travelMode) {
            "walking" -> R.drawable.ic_walk
            "transit" -> R.drawable.ic_public_transport
            else -> R.drawable.ic_car
        }
        holder.iconTransportMode.setImageResource(modeIconRes)
        holder.distance.text = task.distanceText

        holder.arrivalTime.text = formatHumanReadableDate(task.time)

        // Status and Colors
        if (task.leaveTime != null && task.leaveTime!! > 0) {
            val statusData = calculateStatus(task.leaveTime!!)
            holder.status.text = statusData.first
            holder.status.setTextColor(statusData.second)

            val timeOnlySdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            holder.fullLeaveTime.text = "Тръгване: ${timeOnlySdf.format(Date(task.leaveTime!!))}"
            holder.fullArrivalTime.text = "Пристигане: ${timeOnlySdf.format(Date(task.time))}"
        } else {
            holder.status.text = "Изчисляване на маршрут..."
            holder.status.setTextColor(Color.parseColor("#9E9E9E"))
            holder.fullLeaveTime.text = "Тръгване: --"
            holder.fullArrivalTime.text = "Пристигане: --"
        }

        // Expand / Collapse logic
        val isExpanded = task.isExpanded
        holder.layoutExpanded.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.iconExpand.rotation = if (isExpanded) 180f else 0f

        holder.itemView.setOnClickListener {
            val willExpand = !task.isExpanded
            task.isExpanded = willExpand

            holder.iconExpand.animate().rotation(if (willExpand) 180f else 0f).setDuration(200).start()

            notifyItemChanged(position)
        }

        // Long click to edit
        holder.itemView.setOnLongClickListener {
            onTaskEditClick(task)
            true
        }

        // Navigation
        holder.btnNavigate.setOnClickListener {
            onNavigateClick(task)
        }
        
        // Cascade animation for new items
        if (position > lastPosition) {
            holder.itemView.alpha = 0f
            holder.itemView.translationY = 100f
            holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setStartDelay((position * 50).toLong()) // Всяка следваща задача леко закъснява (каскаден ефект)
                .start()
            lastPosition = position
        }
    }

    override fun getItemCount() = tasks.size

    private fun formatHumanReadableDate(timeMillis: Long): String {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_YEAR)
        val year = cal.get(Calendar.YEAR)

        cal.timeInMillis = timeMillis
        val targetDay = cal.get(Calendar.DAY_OF_YEAR)
        val targetYear = cal.get(Calendar.YEAR)

        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)

        return when {
            year == targetYear && today == targetDay -> "$timeStr · Днес"
            year == targetYear && today + 1 == targetDay -> "$timeStr · Утре"
            else -> {
                val dateStr = SimpleDateFormat("EEE, d MMM", Locale("bg", "BG")).format(cal.time)
                "$timeStr · $dateStr"
            }
        }
    }

    private fun calculateStatus(leaveTimeMillis: Long): Pair<String, Int> {
        val now = System.currentTimeMillis()
        val diffMillis = leaveTimeMillis - now
        val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)

        return when {
            diffMinutes < 0 -> Pair("Закъсняваш", Color.parseColor("#E57373"))
            diffMinutes in 0..60 -> Pair("Тръгни след $diffMinutes мин", Color.parseColor("#4CAF50"))
            else -> {
                val leaveStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(leaveTimeMillis))
                Pair("Тръгни в $leaveStr", Color.parseColor("#757575"))
            }
        }
    }
}