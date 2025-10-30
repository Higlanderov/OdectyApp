package cz.davidfryda.odectyapp.ui.master

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
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
import com.google.firebase.auth.FirebaseAuth
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject

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
    // OPRAVENÁ FUNKCE: Smazání uživatele včetně všech dat
    fun deleteUser(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _deleteUserResult.value = SaveResult.Loading
            Log.d(tag, "=== START deleteUser for userId: $userId ===")

            try {
                // --- KROK 1: Smazání odečtů a fotek ---
                Log.d(tag, "KROK 1A: Začínám načítat readings pro userId: $userId")
                val readingsSnapshot = try {
                    db.collection("readings")
                        .whereEqualTo("userId", userId)
                        .get()
                        .await()
                } catch (e: Exception) {
                    Log.e(tag, "KROK 1A: CHYBA při načítání readings!", e)
                    throw e
                }
                Log.d(tag, "KROK 1A: SUCCESS - Nalezeno ${readingsSnapshot.size()} odečtů ke smazání")

                Log.d(tag, "KROK 1B: Začínám mazat readings a fotky")
                withContext(Dispatchers.IO) {
                    for ((index, readingDoc) in readingsSnapshot.documents.withIndex()) {
                        Log.d(tag, "KROK 1B: Zpracovávám reading ${index + 1}/${readingsSnapshot.size()}: ${readingDoc.id}")

                        val reading = try {
                            readingDoc.toObject(Reading::class.java)
                        } catch (e: Exception) {
                            Log.w(tag, "KROK 1B: Chyba při převodu reading dokumentu ${readingDoc.id}", e)
                            null
                        }

                        // PŘIDÁNO: Detailní logování pro fotky
                        Log.d(tag, "KROK 1B: Reading ${readingDoc.id} - photoUrl: '${reading?.photoUrl}'")
                        Log.d(tag, "KROK 1B: photoUrl.isNotEmpty: ${reading?.photoUrl?.isNotEmpty()}")

                        // Smazat fotku ze Storage
                        if (reading?.photoUrl?.isNotEmpty() == true) {
                            try {
                                Log.d(tag, "KROK 1B: Pokouším se smazat fotku: ${reading.photoUrl}")
                                val photoRef = storage.getReferenceFromUrl(reading.photoUrl)
                                Log.d(tag, "KROK 1B: Storage reference získána: ${photoRef.path}")
                                photoRef.delete().await()
                                Log.d(tag, "KROK 1B: ✓ Smazána fotka: ${reading.photoUrl}")
                            } catch (e: Exception) {
                                Log.w(tag, "KROK 1B: ✗ Nepodařilo se smazat fotku: ${reading.photoUrl}", e)
                                Log.w(tag, "KROK 1B: Error type: ${e.javaClass.simpleName}")
                                Log.w(tag, "KROK 1B: Error message: ${e.message}")
                            }
                        } else {
                            Log.d(tag, "KROK 1B: Reading ${readingDoc.id} nemá fotku nebo je prázdná")
                        }

                        // Smazat dokument odečtu
                        try {
                            readingDoc.reference.delete().await()
                            Log.d(tag, "KROK 1B: ✓ Smazán reading dokument: ${readingDoc.id}")
                        } catch (e: Exception) {
                            Log.e(tag, "KROK 1B: CHYBA při mazání reading dokumentu: ${readingDoc.id}", e)
                            throw e
                        }
                    }
                }
                Log.d(tag, "KROK 1B: SUCCESS - Všechny odečty a fotky smazány")

                // --- KROK 2: Smazání měřáků ---
                Log.d(tag, "KROK 2A: Začínám načítat meters pro userId: $userId")
                val metersSnapshot = try {
                    db.collection("users")
                        .document(userId)
                        .collection("meters")
                        .get()
                        .await()
                } catch (e: Exception) {
                    Log.e(tag, "KROK 2A: CHYBA při načítání meters!", e)
                    throw e
                }
                Log.d(tag, "KROK 2A: SUCCESS - Nalezeno ${metersSnapshot.size()} měřáků")

                if (!metersSnapshot.isEmpty) {
                    Log.d(tag, "KROK 2B: Začínám mazat ${metersSnapshot.size()} měřáků")
                    val batch = db.batch()
                    for (meterDoc in metersSnapshot.documents) {
                        batch.delete(meterDoc.reference)
                        Log.d(tag, "KROK 2B: Přidán do batch: ${meterDoc.id}")
                    }
                    try {
                        batch.commit().await()
                        Log.d(tag, "KROK 2B: SUCCESS - Batch commit dokončen, smazáno ${metersSnapshot.size()} měřáků")
                    } catch (e: Exception) {
                        Log.e(tag, "KROK 2B: CHYBA při commit batch pro meters!", e)
                        throw e
                    }
                } else {
                    Log.d(tag, "KROK 2: Žádné měřáky k smazání")
                }

                // --- KROK 3: Smazat notifikace (POUZE pokud mazaný uživatel je master) ---
                Log.d(tag, "KROK 3: Kontroluji, zda mazaný uživatel má notifikace...")

                // Nejdřív zjistíme, jestli je mazaný uživatel master
                val userToDeleteDoc = try {
                    db.collection("users").document(userId).get().await()
                } catch (_: Exception) {
                    Log.w(tag, "KROK 3: Dokument mazaného uživatele už neexistuje (možná byl smazán dříve)")
                    null
                }

                val isMazanyUserMaster = userToDeleteDoc?.data?.get("role") == "master"

                if (isMazanyUserMaster) {
                    Log.d(tag, "KROK 3A: Mazaný uživatel je master, mažu jeho notifikace...")
                    val notificationsSnapshot = try {
                        db.collection("notifications")
                            .document(userId)
                            .collection("items")
                            .get()
                            .await()
                    } catch (e: Exception) {
                        Log.e(tag, "KROK 3A: CHYBA při načítání notifications!", e)
                        throw e
                    }
                    Log.d(tag, "KROK 3A: SUCCESS - Nalezeno ${notificationsSnapshot.size()} notifikací")

                    if (!notificationsSnapshot.isEmpty) {
                        Log.d(tag, "KROK 3B: Začínám mazat ${notificationsSnapshot.size()} notifikací")
                        val notifBatch = db.batch()
                        for (notifDoc in notificationsSnapshot.documents) {
                            notifBatch.delete(notifDoc.reference)
                        }
                        try {
                            notifBatch.commit().await()
                            Log.d(tag, "KROK 3B: SUCCESS - Smazáno ${notificationsSnapshot.size()} notifikací")
                        } catch (e: Exception) {
                            Log.e(tag, "KROK 3B: CHYBA při commit batch pro notifications!", e)
                            throw e
                        }
                    } else {
                        Log.d(tag, "KROK 3: Žádné notifikace k smazání")
                    }

                    // Smazat kořenový dokument notifikací
                    Log.d(tag, "KROK 3C: Mažu kořenový dokument notifications")
                    try {
                        db.collection("notifications").document(userId).delete().await()
                        Log.d(tag, "KROK 3C: SUCCESS - Kořenový dokument notifications smazán")
                    } catch (e: Exception) {
                        Log.w(tag, "KROK 3C: Kořenový dokument notifikací neexistoval nebo selhalo smazání", e)
                    }
                } else {
                    Log.d(tag, "KROK 3: Mazaný uživatel není master, přeskakuji mazání notifikací")
                }

                // --- KROK 4: Smazat dokument uživatele z Firestore ---
                Log.d(tag, "KROK 4: Začínám mazat user dokument: $userId")
                try {
                    db.collection("users").document(userId).delete().await()
                    Log.d(tag, "KROK 4: SUCCESS - User dokument smazán z Firestore")
                } catch (e: Exception) {
                    Log.e(tag, "KROK 4: CHYBA při mazání user dokumentu!", e)
                    throw e
                }

                // --- KROK 5: Smazat účet z Firebase Authentication ---
                Log.d(tag, "KROK 5: Začínám mazat účet z Firebase Authentication")

// Získat aktuálního uživatele
                val currentUser = FirebaseAuth.getInstance().currentUser
                Log.d(tag, "KROK 5: currentUser == null? ${currentUser == null}")

                if (currentUser == null) {
                    Log.e(tag, "KROK 5: CRITICAL - Aktuální uživatel není přihlášen!")
                    throw Exception("Musíte být přihlášeni pro smazání uživatele")
                }

                Log.d(tag, "KROK 5: Aktuální uživatel UID: ${currentUser.uid}")
                Log.d(tag, "KROK 5: Získávám ID token...")

                val tokenResult = try {
                    currentUser.getIdToken(true).await()
                } catch (e: Exception) {
                    Log.e(tag, "KROK 5: CHYBA při získávání ID tokenu!", e)
                    throw e
                }

                val idToken = tokenResult.token
                Log.d(tag, "KROK 5: ID token získán (délka: ${idToken?.length})")

// Připravit HTTP request
                withContext(Dispatchers.IO) {
                    try {
                        // Získat Project ID z Firebase App
                        val projectId = "odecty-a5b6a"
                        val url = "https://us-central1-$projectId.cloudfunctions.net/deleteUser"
                        Log.d(tag, "KROK 5: Volám URL: $url")

                        val client = OkHttpClient()
                        val json = JSONObject().apply {
                            put("data", JSONObject().apply {
                                put("userId", userId)
                            })
                        }

                        val requestBody = json.toString().toRequestBody("application/json".toMediaType())

                        val request = Request.Builder()
                            .url(url)
                            .post(requestBody)
                            .addHeader("Authorization", "Bearer $idToken")
                            .addHeader("Content-Type", "application/json")
                            .build()

                        Log.d(tag, "KROK 5: Odesílám request...")
                        val response = client.newCall(request).execute()

                        val responseBody = response.body?.string()
                        Log.d(tag, "KROK 5: Response code: ${response.code}")
                        Log.d(tag, "KROK 5: Response body: $responseBody")

                        if (!response.isSuccessful) {
                            throw Exception("HTTP ${response.code}: $responseBody")
                        }

                        Log.d(tag, "KROK 5: SUCCESS - Uživatel smazán z Authentication")

                    } catch (e: Exception) {
                        Log.e(tag, "KROK 5: CHYBA při HTTP volání!", e)
                        throw e
                    }
                }

                // Vše proběhlo úspěšně
                _deleteUserResult.value = SaveResult.Success
                _isLoading.value = false
                Log.d(tag, "=== SUCCESS: Uživatel $userId úspěšně smazán ze všech míst ===")

            } catch (e: Exception) {
                // Zachytí všechny chyby
                Log.e(tag, "=== ERROR: Chyba během procesu deleteUser pro $userId ===", e)
                Log.e(tag, "ERROR Message: ${e.message}")
                Log.e(tag, "ERROR Type: ${e.javaClass.simpleName}")
                _deleteUserResult.value = SaveResult.Error(e.message ?: "Neznámá chyba při mazání uživatele")
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
