package cz.davidfryda.odectyapp.ui.main

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log // Import pro logování
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage // Import pro Storage
import cz.davidfryda.odectyapp.data.Meter
import cz.davidfryda.odectyapp.data.Reading // Import pro Reading
import cz.davidfryda.odectyapp.ui.user.SaveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val storage = Firebase.storage // Reference na Storage
    private val currentUser = Firebase.auth.currentUser

    private val _meters = MutableLiveData<List<Meter>>()
    val meters: LiveData<List<Meter>> = _meters

    private val _isLoading = MutableLiveData<Boolean>(false) // Výchozí hodnota false
    val isLoading: LiveData<Boolean> = _isLoading

    // Přejmenováno pro jasnost (výsledek přidání)
    private val _addResult = MutableLiveData<SaveResult>()
    val addResult: LiveData<SaveResult> = _addResult

    // NOVÉ: LiveData pro výsledek úpravy
    private val _updateResult = MutableLiveData<SaveResult>()
    val updateResult: LiveData<SaveResult> = _updateResult

    // NOVÉ: LiveData pro výsledek smazání
    private val _deleteResult = MutableLiveData<SaveResult>()
    val deleteResult: LiveData<SaveResult> = _deleteResult

    private val TAG = "MainViewModel" // Tag pro logování

    init {
        fetchMeters()
    }

    private fun fetchMeters() {
        if (currentUser == null) {
            Log.w(TAG, "fetchMeters: Uživatel není přihlášen.")
            _isLoading.value = false // Ukončíme načítání
            return
        }
        _isLoading.value = true // Začínáme načítat

        // Nasloucháme na změny v reálném čase
        db.collection("users").document(currentUser.uid).collection("meters")
            .addSnapshotListener { snapshots, error ->
                _isLoading.value = false // Skončili jsme načítání (nebo přišla chyba)
                if (error != null) {
                    Log.e(TAG, "fetchMeters: Chyba při načítání měřáků", error)
                    // Zde bychom mohli nastavit nějaký chybový stav, např. _meters.value = emptyList() a zobrazit Toast
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    _meters.value = snapshots.map { doc ->
                        doc.toObject(Meter::class.java).copy(id = doc.id)
                    }.sortedBy { it.name } // Seřadíme podle jména
                    Log.d(TAG, "fetchMeters: Načteno ${_meters.value?.size ?: 0} měřáků.")
                } else {
                    _meters.value = emptyList() // Pokud je snapshot null, nastavíme prázdný seznam
                    Log.d(TAG, "fetchMeters: Snapshot je null.")
                }
            }
    }

    // Přejmenováno pro jasnost
    fun addMeter(name: String, type: String) {
        viewModelScope.launch {
            _addResult.value = SaveResult.Loading // Používáme addResult
            if (currentUser == null) {
                _addResult.value = SaveResult.Error("Uživatel není přihlášen.")
                return@launch
            }
            val newMeter = hashMapOf("name" to name, "type" to type)
            try {
                db.collection("users").document(currentUser.uid).collection("meters").add(newMeter).await()
                _addResult.value = SaveResult.Success
                Log.d(TAG, "addMeter: Měřák '$name' úspěšně přidán.")
            } catch (e: Exception) {
                _addResult.value = SaveResult.Error(e.message ?: "Chyba při ukládání měřáku.")
                Log.e(TAG, "addMeter: Chyba při přidávání měřáku '$name'", e)
            }
        }
    }

    // --- NOVÉ METODY ---

    // Metoda pro úpravu názvu měřáku
    fun updateMeterName(meterId: String, newName: String, context: Context) {
        viewModelScope.launch {
            if (!isOnline(context)) {
                _updateResult.value = SaveResult.Error("Nejste připojeni k serveru. Prosím, zkuste to později.")
                return@launch
            }
            _updateResult.value = SaveResult.Loading
            if (currentUser == null) {
                _updateResult.value = SaveResult.Error("Uživatel není přihlášen.")
                return@launch
            }
            try {
                db.collection("users").document(currentUser.uid)
                    .collection("meters").document(meterId)
                    .update("name", newName)
                    .await()
                _updateResult.value = SaveResult.Success
                Log.d(TAG, "updateMeterName: Název měřáku $meterId úspěšně změněn na '$newName'.")
            } catch (e: Exception) {
                _updateResult.value = SaveResult.Error(e.message ?: "Chyba při úpravě názvu měřáku.")
                Log.e(TAG, "updateMeterName: Chyba při úpravě měřáku $meterId", e)
            }
        }
    }

    // Metoda pro smazání měřáku a jeho dat
    fun deleteMeter(meterId: String, context: Context) {
        viewModelScope.launch {
            if (!isOnline(context)) {
                _deleteResult.value = SaveResult.Error("Nejste připojeni k serveru. Prosím, zkuste to později.")
                return@launch
            }
            _deleteResult.value = SaveResult.Loading
            if (currentUser == null) {
                _deleteResult.value = SaveResult.Error("Uživatel není přihlášen.")
                return@launch
            }

            try {
                // 1. Najít všechny odečty pro daný měřák
                val readingsSnapshot = db.collection("readings")
                    .whereEqualTo("userId", currentUser.uid)
                    .whereEqualTo("meterId", meterId)
                    .get()
                    .await()

                val photoUrlsToDelete = mutableListOf<String>()
                val readingIdsToDelete = mutableListOf<String>()

                for (doc in readingsSnapshot.documents) {
                    val reading = doc.toObject(Reading::class.java)
                    if (reading != null) {
                        readingIdsToDelete.add(doc.id)
                        if (reading.photoUrl.isNotEmpty()) {
                            photoUrlsToDelete.add(reading.photoUrl)
                        }
                    }
                }
                Log.d(TAG, "deleteMeter: Nalezeno ${readingIdsToDelete.size} odečtů a ${photoUrlsToDelete.size} fotek ke smazání pro měřák $meterId.")

                // Použijeme WriteBatch pro atomické smazání odečtů a měřáku
                val batch = db.batch()

                // 2. Přidat smazání všech nalezených odečtů do batch
                readingIdsToDelete.forEach { readingId ->
                    val readingRef = db.collection("readings").document(readingId)
                    batch.delete(readingRef)
                }

                // 3. Přidat smazání samotného měřáku do batch
                val meterRef = db.collection("users").document(currentUser.uid)
                    .collection("meters").document(meterId)
                batch.delete(meterRef)

                // 4. Provést batch zápis (smazání dokumentů)
                batch.commit().await()
                Log.d(TAG, "deleteMeter: Dokumenty měřáku a odečtů pro $meterId smazány z Firestore.")

                // 5. Smazat fotky ze Storage (mimo batch, postupně)
                // Přepneme na IO dispatcher pro síťové operace mazání fotek
                withContext(Dispatchers.IO) {
                    var deletedPhotosCount = 0
                    photoUrlsToDelete.forEach { photoUrl ->
                        try {
                            val photoRef = storage.getReferenceFromUrl(photoUrl)
                            photoRef.delete().await()
                            deletedPhotosCount++
                        } catch (e: Exception) {
                            // Logujeme chybu, ale pokračujeme v mazání ostatních fotek
                            Log.e(TAG, "deleteMeter: Chyba při mazání fotky $photoUrl ze Storage", e)
                        }
                    }
                    Log.d(TAG, "deleteMeter: Smazáno $deletedPhotosCount z ${photoUrlsToDelete.size} fotek ze Storage.")
                }

                _deleteResult.value = SaveResult.Success
                Log.d(TAG, "deleteMeter: Měřák $meterId a jeho data úspěšně smazána.")

            } catch (e: Exception) {
                _deleteResult.value = SaveResult.Error(e.message ?: "Chyba při mazání měřáku.")
                Log.e(TAG, "deleteMeter: Chyba při mazání měřáku $meterId", e)
            }
        }
    }

    // Pomocná funkce pro kontrolu připojení k internetu
    private fun isOnline(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            // Můžeme zvážit i jiné typy, např. Ethernet, pokud je to relevantní
            // activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    // --- KONEC NOVÝCH METOD ---
}