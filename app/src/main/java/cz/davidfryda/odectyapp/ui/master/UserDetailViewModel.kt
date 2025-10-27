package cz.davidfryda.odectyapp.ui.master

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import cz.davidfryda.odectyapp.data.Reading
import cz.davidfryda.odectyapp.data.UserData
import cz.davidfryda.odectyapp.ui.user.SaveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

class UserDetailViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val tag = "UserDetailViewModel"

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _userData = MutableLiveData<UserData?>()
    val userData: LiveData<UserData?> = _userData

    private val _meterCount = MutableLiveData<Int?>()
    val meterCount: LiveData<Int?> = _meterCount

    private val _lastReadingDate = MutableLiveData<Date?>()
    val lastReadingDate: LiveData<Date?> = _lastReadingDate

    private val _blockResult = MutableLiveData<SaveResult>(SaveResult.Idle)
    val blockResult: LiveData<SaveResult> = _blockResult

    // NOVÉ: LiveData pro výsledek smazání uživatele
    private val _deleteUserResult = MutableLiveData<SaveResult>(SaveResult.Idle)
    val deleteUserResult: LiveData<SaveResult> = _deleteUserResult

    private val functions = Firebase.functions

    fun loadUserDetails(userId: String) {
        _isLoading.value = true
        if (_blockResult.value != SaveResult.Loading) {
            _blockResult.value = SaveResult.Idle
        }
        // NOVÉ: Reset delete result při načítání
        if (_deleteUserResult.value != SaveResult.Loading) {
            _deleteUserResult.value = SaveResult.Idle
        }
        Log.d(tag, "Starting loadUserDetails for userId: $userId")

        viewModelScope.launch {
            var user: UserData? = null
            var count: Int? = null
            var lastDate: Date? = null

            try {
                // 1. Načtení dat uživatele
                val userDoc = db.collection("users").document(userId).get().await()
                user = userDoc.toObject(UserData::class.java)
                Log.d(tag, "Fetched user data from Firestore. isDisabled = ${user?.isDisabled}")

                // 2. Načtení počtu měřáků
                val metersSnapshot = db.collection("readings")
                    .whereEqualTo("userId", userId)
                    .get().await()
                count = metersSnapshot.documents.distinctBy { it.getString("meterId") }.size
                Log.d(tag,"Meter count calculated: $count")

                // 3. Načtení posledního odečtu
                val lastReadingSnapshot = db.collection("readings")
                    .whereEqualTo("userId", userId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get().await()
                lastDate = lastReadingSnapshot.documents.firstOrNull()?.getTimestamp("timestamp")?.toDate()
                Log.d(tag,"Last reading date found: $lastDate")

            } catch (e: Exception) {
                Log.e(tag, "Chyba při načítání detailů uživatele $userId", e)
            } finally {
                _userData.value = user
                _meterCount.value = count
                _lastReadingDate.value = lastDate
                _isLoading.value = false
                Log.d(tag,"Finished loadUserDetails. isLoading: false. UserData posted: $user")
            }
        }
    }

    fun toggleBlockUser(userId: String, currentDisabledState: Boolean) {
        _isLoading.value = true
        _blockResult.value = SaveResult.Loading

        val newDisabledState = !currentDisabledState
        Log.d(tag, "Attempting to set isDisabled to $newDisabledState for user $userId")

        db.collection("users").document(userId)
            .update("isDisabled", newDisabledState)
            .addOnSuccessListener {
                Log.d(tag, "Firestore update successful. isDisabled set to $newDisabledState")

                val currentUserData = _userData.value
                if (currentUserData != null) {
                    _userData.value = currentUserData.copy(isDisabled = newDisabledState)
                    Log.d(tag, "Immediately updated LiveData _userData.value.isDisabled to $newDisabledState")
                } else {
                    Log.w(tag, "currentUserData was null, reloading all details.")
                    loadUserDetails(userId)
                }

                _blockResult.value = SaveResult.Success
                _isLoading.value = false
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Chyba při aktualizaci stavu isDisabled pro $userId", e)
                _blockResult.value = SaveResult.Error(e.message ?: "Neznámá chyba")
                _isLoading.value = false
            }
    }

    fun doneHandlingBlockResult() {
        if (_blockResult.value != SaveResult.Idle) {
            _blockResult.value = SaveResult.Idle
        }
    }

    // NOVÁ FUNKCE: Smazání uživatele včetně všech dat
    fun deleteUser(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _deleteUserResult.value = SaveResult.Loading
            Log.d(tag, "Starting deleteUser for userId: $userId")

            try {
                // 1. Načtení všech měřáků uživatele
                val metersSnapshot = db.collection("users")
                    .document(userId)
                    .collection("meters")
                    .get()
                    .await()

                val meterIds = metersSnapshot.documents.map { it.id }
                Log.d(tag, "Found ${meterIds.size} meters for user $userId")

                // 2. Smazání všech odečtů a fotek
                withContext(Dispatchers.IO) {
                    for (meterId in meterIds) {
                        try {
                            // Najít všechny odečty pro tento měřák
                            val readingsSnapshot = db.collection("readings")
                                .whereEqualTo("userId", userId)
                                .whereEqualTo("meterId", meterId)
                                .get()
                                .await()

                            Log.d(tag, "Found ${readingsSnapshot.size()} readings for meter $meterId")

                            // Smazat fotky ze Storage a dokumenty z Firestore
                            for (readingDoc in readingsSnapshot.documents) {
                                val reading = readingDoc.toObject(Reading::class.java)

                                // Smazat fotku ze Storage
                                if (reading?.photoUrl?.isNotEmpty() == true) {
                                    try {
                                        val photoRef = storage.getReferenceFromUrl(reading.photoUrl)
                                        photoRef.delete().await()
                                        Log.d(tag, "Deleted photo: ${reading.photoUrl}")
                                    } catch (e: Exception) {
                                        Log.w(tag, "Failed to delete photo: ${reading.photoUrl}", e)
                                    }
                                }

                                // Smazat dokument odečtu
                                readingDoc.reference.delete().await()
                            }

                            // Smazat dokument měřáku
                            db.collection("users")
                                .document(userId)
                                .collection("meters")
                                .document(meterId)
                                .delete()
                                .await()

                            Log.d(tag, "Deleted meter: $meterId")

                        } catch (e: Exception) {
                            Log.e(tag, "Error deleting meter $meterId", e)
                            throw e
                        }
                    }
                }

                // 3. Smazat notifikace uživatele
                try {
                    val notificationsSnapshot = db.collection("notifications")
                        .document(userId)
                        .collection("items")
                        .get()
                        .await()

                    for (notifDoc in notificationsSnapshot.documents) {
                        notifDoc.reference.delete().await()
                    }

                    db.collection("notifications").document(userId).delete().await()
                    Log.d(tag, "Deleted ${notificationsSnapshot.size()} notifications")
                } catch (e: Exception) {
                    Log.w(tag, "Error deleting notifications", e)
                }

                // 4. Smazat dokument uživatele z Firestore
                db.collection("users").document(userId).delete().await()
                Log.d(tag, "Deleted user document from Firestore")

                // 5. Smazat účet z Firebase Authentication
                withContext(Dispatchers.IO) {
                    try {
                        Log.d(tag, "Calling Cloud Function to delete auth account")

                        val data = hashMapOf(
                            "userId" to userId
                        )

                        val result = functions
                            .getHttpsCallable("deleteUser")
                            .call(data)
                            .await()

                        Log.d(tag, "Cloud Function result: ${result.data}")

                    } catch (e: Exception) {
                        Log.e(tag, "Cloud Function call failed", e)
                        // Pokračujeme dál i když Cloud Function selže
                        // (data už jsou smazána z Firestore)
                    }
                }

                _deleteUserResult.value = SaveResult.Success
                _isLoading.value = false
                Log.d(tag, "Successfully deleted user $userId")

            } catch (e: Exception) {
                Log.e(tag, "Error deleting user $userId", e)
                _deleteUserResult.value = SaveResult.Error(e.message ?: "Chyba při mazání uživatele")
                _isLoading.value = false
            }
        }
    }

    // NOVÁ FUNKCE: Reset výsledku smazání
    fun doneHandlingDeleteResult() {
        if (_deleteUserResult.value != SaveResult.Idle) {
            _deleteUserResult.value = SaveResult.Idle
        }
    }
}
