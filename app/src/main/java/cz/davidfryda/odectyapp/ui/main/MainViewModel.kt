package cz.davidfryda.odectyapp.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.ui.user.SaveResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val currentUser = Firebase.auth.currentUser

    private val _meters = MutableLiveData<List<Meter>>()
    val meters: LiveData<List<Meter>> = _meters

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // NOVÉ: LiveData pro výsledek uložení
    private val _saveResult = MutableLiveData<SaveResult>()
    val saveResult: LiveData<SaveResult> = _saveResult

    init {
        fetchMeters()
    }

    // Vaše funkční metoda pro načítání měřáků zůstává beze změny
    private fun fetchMeters() {
        _isLoading.value = true
        if (currentUser == null) return

        db.collection("users").document(currentUser.uid).collection("meters")
            .addSnapshotListener { snapshots, error ->
                _isLoading.value = false
                if (error != null || snapshots == null) return@addSnapshotListener
                _meters.value = snapshots.map { it.toObject(Meter::class.java).copy(id = it.id) }
            }
    }

    // UPRAVENO: Funkce nyní hlásí výsledek přes LiveData
    fun addMeter(name: String, type: String) {
        viewModelScope.launch {
            _saveResult.value = SaveResult.Loading
            if (currentUser == null) {
                _saveResult.value = SaveResult.Error("Uživatel není přihlášen.")
                return@launch
            }
            val newMeter = hashMapOf("name" to name, "type" to type)
            try {
                db.collection("users").document(currentUser.uid).collection("meters").add(newMeter).await()
                _saveResult.value = SaveResult.Success
            } catch (e: Exception) {
                _saveResult.value = SaveResult.Error(e.message ?: "Chyba při ukládání měřáku.")
            }
        }
    }
}