package cz.davidfryda.odectyapp.ui.notifications

import android.util.Log // <-- Přidej import
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

    private val TAG = "NotificationViewModel" // Tag pro logování


    fun loadNotifications() {
        if (currentUser == null) return
        Log.d(TAG, "loadNotifications: Načítám notifikace pro ${currentUser.uid}") // LOG
        db.collection("notifications").document(currentUser.uid).collection("items")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50) // Zobrazíme max 50 posledních
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w(TAG, "loadNotifications: Chyba při načítání:", error) // LOG CHYBY
                    return@addSnapshotListener
                }
                _notifications.value = snapshots?.map {
                    it.toObject(NotificationItem::class.java).copy(id = it.id)
                } ?: emptyList()
                Log.d(TAG, "loadNotifications: Listener obdržel ${snapshots?.size() ?: 0} notifikací.") // LOG
            }
    }

    fun markAllAsRead() {
        if (currentUser == null) return
        Log.d(TAG, "markAllAsRead: Spuštěno pro ${currentUser.uid}") // LOG
        viewModelScope.launch {
            try {
                val unreadSnapshot = db.collection("notifications").document(currentUser.uid).collection("items")
                    .whereEqualTo("read", false)
                    .get()
                    .await()

                if (unreadSnapshot.isEmpty) {
                    Log.d(TAG, "markAllAsRead: Žádné nepřečtené notifikace k označení.") // LOG
                    return@launch
                }

                Log.d(TAG, "markAllAsRead: Nalezeno ${unreadSnapshot.size()} nepřečtených notifikací k označení.") // LOG

                val batch = db.batch()
                unreadSnapshot.documents.forEach { doc ->
                    batch.update(doc.reference, "read", true)
                }
                batch.commit().await()
                Log.d(TAG, "markAllAsRead: ${unreadSnapshot.size()} notifikací úspěšně označeno jako přečtené.") // LOG ÚSPĚCHU
            } catch (e: Exception) {
                // LOG CHYBY PŘI ZÁPISU
                Log.e(TAG, "markAllAsRead: Chyba při označování notifikací jako přečtených:", e)
            }
        }
    }
}