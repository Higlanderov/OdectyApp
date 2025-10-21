package cz.davidfryda.odectyapp.ui.masterdetail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.ui.main.Meter

class MasterUserDetailViewModel : ViewModel() {
    private val db = Firebase.firestore

    private val _meters = MutableLiveData<List<Meter>>()
    val meters: LiveData<List<Meter>> = _meters

    fun fetchMetersForUser(userId: String) {
        db.collection("users").document(userId).collection("meters")
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                _meters.value = snapshots.map { it.toObject(Meter::class.java).copy(id = it.id) }
            }
    }
}
