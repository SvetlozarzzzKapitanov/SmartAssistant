package com.sKapit.smartassistant

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.util.Calendar // ВЪРНИ ТОЗИ ИМПОРТ
import android.animation.ValueAnimator

class HourlyForecastAdapter(
    private val hourlyList: List<HourlyForecast>,
    private val selectedTimeInMillis: Long, // НОВО: Подаваме времето на задачата
    private val onHourSelected: (Int) -> Unit
) : RecyclerView.Adapter<HourlyForecastAdapter.ForecastViewHolder>() {

    class ForecastViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtHour: TextView = view.findViewById(R.id.txtHour)
        val cardBar: MaterialCardView = view.findViewById(R.id.cardBar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ForecastViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hourly_forecast, parent, false)
        return ForecastViewHolder(view)
    }

    override fun onBindViewHolder(holder: ForecastViewHolder, position: Int) {
        val forecast = hourlyList[position]

        holder.txtHour.text = String.format("%02d:00", forecast.hour)

        val maxBarHeightDp = 120
        val scale = holder.itemView.context.resources.displayMetrics.density
        val calculatedHeight = ((forecast.intensityPercent / 100f) * maxBarHeightDp * scale).toInt()

        // СТАРИЯТ КОД:
        // val layoutParams = holder.cardBar.layoutParams
        // layoutParams.height = Math.max((4 * scale).toInt(), calculatedHeight)
        // holder.cardBar.layoutParams = layoutParams

        // НОВИЯТ КОД (С АНИМАЦИЯ):
        val layoutParams = holder.cardBar.layoutParams
        val minHeight = (4 * scale).toInt()
        val targetHeight = Math.max(minHeight, calculatedHeight)

        // Анимираме височината от минимума до реалната стойност за 600 милисекунди
        val animator = ValueAnimator.ofInt(minHeight, targetHeight)
        animator.duration = 600
        animator.addUpdateListener { animation ->
            layoutParams.height = animation.animatedValue as Int
            holder.cardBar.layoutParams = layoutParams
        }
        animator.start()

        // Вземаме часа, който е избран в полето "Дата и час на пристигане"
        val targetCal = Calendar.getInstance().apply { timeInMillis = selectedTimeInMillis }
        val selectedHour = targetCal.get(Calendar.HOUR_OF_DAY)

        // Оцветяваме в червено/розово стълбчето, което отговаря на ИЗБРАНИЯ час
        if (forecast.hour == selectedHour) {
            holder.cardBar.setCardBackgroundColor(Color.parseColor("#EF7C8E")) // Розово/Червено
        } else {
            holder.cardBar.setCardBackgroundColor(Color.parseColor("#7AA2F7")) // Меко синьо
        }

        holder.itemView.setOnClickListener {
            onHourSelected(forecast.hour)
        }
    }

    override fun getItemCount() = hourlyList.size
}