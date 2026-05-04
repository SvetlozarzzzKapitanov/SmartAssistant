// FirebaseManager.kt
package com.sKapit.smartassistant

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FirebaseManager {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val userId: String get() = auth.currentUser?.uid ?: ""

    fun isUserLoggedIn() = userId.isNotEmpty()

    fun loadTasks(onSuccess: (List<Task>) -> Unit, onFailure: (Exception) -> Unit) {
        val currentUserId = userId
        if (currentUserId.isEmpty()) return
        db.collection("users").document(currentUserId).collection("tasks")
            .get()
            .addOnSuccessListener { result ->
                val loadedTasks = result.documents.mapNotNull { it.toObject(Task::class.java) }
                onSuccess(loadedTasks)
            }
            .addOnFailureListener(onFailure)
    }

    fun saveTask(task: Task, onSuccess: (() -> Unit)? = null, onFailure: ((Exception) -> Unit)? = null) {
        val currentUserId = userId
        if (currentUserId.isEmpty()) {
            onFailure?.invoke(Exception("User not logged in"))
            return
        }
        db.collection("users").document(currentUserId).collection("tasks").document(task.id.toString())
            .set(task)
            .addOnSuccessListener { onSuccess?.invoke() }
            .addOnFailureListener { onFailure?.invoke(it) }
    }

    fun deleteTask(taskId: Int, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val currentUserId = userId
        if (currentUserId.isEmpty()) {
            onFailure(Exception("User not logged in"))
            return
        }
        db.collection("users").document(currentUserId).collection("tasks").document(taskId.toString())
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }
}