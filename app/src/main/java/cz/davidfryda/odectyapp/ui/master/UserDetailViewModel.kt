package cz.davidfryda.odectyapp.ui.master

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.data.Reading
import cz.davidfryda.odectyapp.data.UserData
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

class UserDetailViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val TAG = "UserDetailViewModel"

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _userData = MutableLiveData<UserData?>()
    val userData: LiveData<UserData?> = _userData

    private val _meterCount = MutableLiveData<Int?>(null)
    val meterCount: LiveData<Int?> = _meterCount

    private val _lastReadingDate = MutableLiveData<Date?>(null)
    val lastReadingDate: LiveData<Date?> = _lastReadingDate

    fun loadUserDetails(userId: String) {
        if (_userData.value?.uid == userId) return // Už načítáme nebo máme data pro tohoto uživatele

        viewModelScope.launch {
            _isLoading.value = true
            _userData.value = null // Reset previous data
            _meterCount.value = null
            _lastReadingDate.value = null

            try {
                // 1. Načíst UserData
                val userDoc = db.collection("users").document(userId).get().await()
                _userData.value = userDoc.toObject(UserData::class.java)

                // 2. Načíst počet měřáků
                val metersSnapshot = db.collection("users").document(userId).collection("meters").get().await()
                _meterCount.value = metersSnapshot.size()

                // 3. Načíst poslední odečet
                val lastReadingSnapshot = db.collection("readings")
                    .whereEqualTo("userId", userId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()

                if (!lastReadingSnapshot.isEmpty) {
                    val lastReading = lastReadingSnapshot.documents.first().toObject(Reading::class.java)
                    _lastReadingDate.value = lastReading?.timestamp
                } else {
                    _lastReadingDate.value = null // Explicitně nastavíme null, pokud nejsou odečty
                }

                Log.d(TAG, "User details loaded successfully for $userId")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading user details for $userId", e)
                // Ponecháme data na null v případě chyby
                _userData.value = null
                _meterCount.value = null
                _lastReadingDate.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }
}