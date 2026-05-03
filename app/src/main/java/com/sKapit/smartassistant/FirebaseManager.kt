// FirebaseManager.kt
package com.sKapit.smartassistant

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FirebaseManager {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val userId = auth.currentUser?.uid ?: ""

    fun isUserLoggedIn() = userId.isNotEmpty()

    fun loadTasks(onSuccess: (List<Task>) -> Unit, onFailure: (Exception) -> Unit) {
        if (!isUserLoggedIn()) return
        db.collection("users").document(userId).collection("tasks")
            .get()
            .addOnSuccessListener { result ->
                val loadedTasks = result.documents.mapNotNull { it.toObject(Task::class.java) }
                onSuccess(loadedTasks)
            }
            .addOnFailureListener(onFailure)
    }

    fun saveTask(task: Task) {
        if (!isUserLoggedIn()) return
        db.collection("users").document(userId).collection("tasks").document(task.id.toString())
            .set(task)
    }

    fun deleteTask(taskId: Int, onSuccess: () -> Unit, onFailure: () -> Unit) {
        if (!isUserLoggedIn()) return
        db.collection("users").document(userId).collection("tasks").document(taskId.toString())
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure() }
    }
}