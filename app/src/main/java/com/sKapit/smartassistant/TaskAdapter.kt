package com.sKapit.smartassistant

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
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

        val txtRouteType: TextView = itemView.findViewById(R.id.txtRouteType)
        val txtStartSummary: TextView = itemView.findViewById(R.id.txtStartSummary)
        val txtTimeRange: TextView = itemView.findViewById(R.id.txtTimeRange)
        val txtRouteWarning: TextView = itemView.findViewById(R.id.txtRouteWarning)

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
            val (statusText, statusColor) = task.getStatusData(context)

            val routeBadge = when (task.routeSourceType) {
                "manual" -> context.getString(R.string.route_badge_manual)
                "chain" -> context.getString(R.string.route_badge_chain)
                else -> context.getString(R.string.route_badge_gps)
            }

            if (task.hasRouteConflict) {
                status.text = context.getString(R.string.route_conflict) + routeBadge
                status.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_light))
            } else {
                status.text = statusText + routeBadge
                status.setTextColor(statusColor)
            }

            // Time details
            task.leaveTime?.takeIf { it > 0 }?.let { leaveTime ->
                val routeTypeText = when (task.routeSourceType) {
                    "manual" -> context.getString(R.string.route_manual)
                    "chain" -> context.getString(R.string.route_chain)
                    else -> context.getString(R.string.route_gps)
                }

                val startText = when (task.routeSourceType) {
                    "manual" -> context.getString(R.string.start_summary_manual, task.resolvedStartLocationName ?: task.startLocationName ?: context.getString(R.string.manual_start_point))
                    "chain" -> context.getString(R.string.start_summary_chain, task.resolvedStartLocationName ?: context.getString(R.string.previous_task))
                    else -> context.getString(R.string.start_summary_gps)
                }

                txtRouteType.text = routeTypeText
                txtStartSummary.text = startText
                txtTimeRange.text = "${timeFormatter.format(Date(leaveTime))} → ${timeFormatter.format(Date(task.time))}"

                if (task.hasRouteConflict) {
                    txtRouteWarning.visibility = View.VISIBLE
                    txtRouteWarning.text = task.routeWarning ?: context.getString(R.string.route_conflict)
                } else {
                    txtRouteWarning.visibility = View.GONE
                    txtRouteWarning.text = ""
                }
            } ?: run {
                txtRouteType.text = context.getString(R.string.route_calculating)
                txtStartSummary.text = context.getString(R.string.start_summary_calculating)
                txtTimeRange.text = "--:-- → --:--"
                txtRouteWarning.visibility = View.GONE
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
