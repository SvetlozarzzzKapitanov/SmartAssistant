// NotificationManagerHelper.kt
package com.sKapit.smartassistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationManagerHelper(private val context: Context) {

    fun scheduleNotification(task: Task) {
        val leaveTime = task.leaveTime ?: return
        if (leaveTime < System.currentTimeMillis()) return // Не насрочваме аларми за минало време

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("TASK_ID", task.id)
            putExtra("TASK_TITLE", task.title)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            // Използваме setExactAndAllowWhileIdle за максимална точност дори при Doze mode
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, leaveTime, pendingIntent)
        } catch (e: SecurityException) {
            Log.e("NotificationHelper", "No permission for Exact Alarm")
            // Fallback към обикновена аларма, ако липсва разрешение за точна
            alarmManager.set(AlarmManager.RTC_WAKEUP, leaveTime, pendingIntent)
        }
    }

    fun cancelNotification(taskId: Int) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }
}