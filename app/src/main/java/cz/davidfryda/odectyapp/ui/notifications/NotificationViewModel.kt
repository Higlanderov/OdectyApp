package cz.davidfryda.odectyapp.ui.notifications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.data.NotificationItem
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class NotificationViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val currentUser = Firebase.auth.currentUser

    private val _notifications = MutableLiveData<List<NotificationItem>>()
    val notifications: LiveData<List<NotificationItem>> = _notifications

    fun loadNotifications() {
        if (currentUser == null) return
        db.collection("notifications").document(currentUser.uid).collection("items")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50) // Zobrazíme max 50 posledních
            .addSnapshotListener { snapshots, _ ->
                _notifications.value = snapshots?.map {
                    it.toObject(NotificationItem::class.java).copy(id = it.id)
                } ?: emptyList()
            }
    }

    fun markAllAsRead() {
        if (currentUser == null) return
        viewModelScope.launch {
            try {
                val unreadSnapshot = db.collection("notifications").document(currentUser.uid).collection("items")
                    .whereEqualTo("read", false)
                    .get()
                    .await()

                val batch = db.batch()
                unreadSnapshot.documents.forEach { doc ->
                    batch.update(doc.reference, "read", true)
                }
                batch.commit().await()
            } catch (e: Exception) {
                // Chybu zalogujeme, ale nemusíme nutně informovat uživatele
            }
        }
    }
}
