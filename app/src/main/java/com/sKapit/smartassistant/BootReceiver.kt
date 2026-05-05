package com.sKapit.smartassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Телефонът е рестартиран. Преизчисляваме алармите...")
            
            val firebaseManager = FirebaseManager()
            val notificationHelper = NotificationManagerHelper(context)
            
            // Зареждаме задачите и насрочваме тези, които предстоят
            firebaseManager.loadTasks(
                onSuccess = { tasks ->
                    val now = System.currentTimeMillis()
                    tasks.forEach { task ->
                        if (task.leaveTime != null && task.leaveTime!! > now) {
                            notificationHelper.scheduleNotification(task)
                        }
                    }
                },
                onFailure = {
                    Log.e("BootReceiver", "Грешка при зареждане на задачи за възстановяване на аларми")
                }
            )
        }
    }
}