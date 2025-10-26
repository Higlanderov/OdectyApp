package cz.davidfryda.odectyapp.ui.master

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.data.UserData
import cz.davidfryda.odectyapp.ui.user.SaveResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

class UserDetailViewModel : ViewModel() {

    private val db = Firebase.firestore
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

    fun loadUserDetails(userId: String) {
        _isLoading.value = true
        if (_blockResult.value != SaveResult.Loading) {
            _blockResult.value = SaveResult.Idle
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

                // 2. Načtení počtu měřáků (PŘIZPŮSOBTE!)
                val metersSnapshot = db.collection("readings") // Vaše kolekce
                    .whereEqualTo("userId", userId)
                    .get().await()
                count = metersSnapshot.documents.distinctBy { it.getString("meterId") }.size // Vaše pole
                Log.d(tag,"Meter count calculated: $count")

                // 3. Načtení posledního odečtu (PŘIZPŮSOBTE!)
                val lastReadingSnapshot = db.collection("readings") // Vaše kolekce
                    .whereEqualTo("userId", userId)
                    .orderBy("timestamp", Query.Direction.DESCENDING) // Vaše pole
                    .limit(1)
                    .get().await()
                lastDate = lastReadingSnapshot.documents.firstOrNull()?.getTimestamp("timestamp")?.toDate() // Vaše pole
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

    // --- ✨ ZAČÁTEK ÚPRAVY: Okamžitá aktualizace LiveData ---
    fun toggleBlockUser(userId: String, currentDisabledState: Boolean) {
        _isLoading.value = true
        _blockResult.value = SaveResult.Loading

        val newDisabledState = !currentDisabledState
        Log.d(tag, "Attempting to set isDisabled to $newDisabledState for user $userId")

        db.collection("users").document(userId)
            .update("isDisabled", newDisabledState)
            .addOnSuccessListener {
                Log.d(tag, "Firestore update successful. isDisabled set to $newDisabledState")

                // Ihned aktualizujeme LiveData s novým stavem, abychom nezáviseli na latenci čtení
                val currentUserData = _userData.value
                if (currentUserData != null) {
                    _userData.value = currentUserData.copy(isDisabled = newDisabledState)
                    Log.d(tag, "Immediately updated LiveData _userData.value.isDisabled to $newDisabledState")
                } else {
                    // Pokud nemáme data, načteme je znovu, abychom získali nový stav
                    Log.w(tag, "currentUserData was null, reloading all details.")
                    loadUserDetails(userId) // Spadne do finally a nastaví isLoading na false
                }

                _blockResult.value = SaveResult.Success
                _isLoading.value = false // Ukončíme ProgressBar zde, pokud nevoláme loadUserDetails
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Chyba při aktualizaci stavu isDisabled pro $userId", e)
                _blockResult.value = SaveResult.Error(e.message ?: "Neznámá chyba")
                _isLoading.value = false // Ukončíme ProgressBar
            }
    }
    // --- ✨ KONEC ÚPRAVY ---

    fun doneHandlingBlockResult() {
        if (_blockResult.value != SaveResult.Idle) {
            _blockResult.value = SaveResult.Idle
        }
    }
}