package cz.davidfryda.odectyapp.ui.location

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import cz.davidfryda.odectyapp.data.Location
import cz.davidfryda.odectyapp.data.Reading // Import pro datovou třídu Reading
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.net.URLDecoder // ✨ OPRAVA: Přidán import pro dekódování cesty

class MasterLocationListViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val tag = "MasterLocationListVM"

    private val _locations = MutableLiveData<List<Location>>()
    val locations: LiveData<List<Location>> = _locations

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _deleteResult = MutableLiveData<LocationListViewModel.DeleteResult>()
    val deleteResult: LiveData<LocationListViewModel.DeleteResult> = _deleteResult

    private val _setDefaultResult = MutableLiveData<LocationListViewModel.SetDefaultResult>()
    val setDefaultResult: LiveData<LocationListViewModel.SetDefaultResult> = _setDefaultResult

    // ID uživatele, pro kterého pracujeme
    private var targetUserId: String? = null

    /**
     * Načte lokace pro konkrétního uživatele (režim Master).
     */
    fun loadLocationsForUser(userId: String) {
        this.targetUserId = userId
        Log.d(tag, "Načítám lokace pro uživatele: $userId")

        _isLoading.value = true

        db.collection("users").document(userId).collection("locations")
            .orderBy("isDefault", Query.Direction.DESCENDING)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                _isLoading.value = false

                if (error != null) {
                    Log.e(tag, "Error loading locations for $userId", error)
                    _locations.value = emptyList()
                    return@addSnapshotListener
                }

                val locationsList = snapshots?.documents?.mapNotNull { doc ->
                    doc.toObject(Location::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                loadMeterCounts(userId, locationsList)
            }
    }

    private fun loadMeterCounts(userId: String, locationsList: List<Location>) {
        viewModelScope.launch {
            val locationsWithCounts = locationsList.map { location ->
                try {
                    val meterCount = db.collection("users")
                        .document(userId)
                        .collection("meters")
                        .whereEqualTo("locationId", location.id)
                        .get()
                        .await()
                        .size()

                    location.copy(meterCount = meterCount)
                } catch (e: Exception) {
                    Log.e(tag, "Error loading meter count for location ${location.id}", e)
                    location
                }
            }
            _locations.value = locationsWithCounts
        }
    }

    // Funkce pro kaskádové mazání (opravená)
    fun deleteLocation(locationId: String) {
        if (targetUserId == null) {
            _deleteResult.value = LocationListViewModel.DeleteResult.Error("User ID není nastaveno")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            val userId = targetUserId!!
            Log.d(tag, "Zahájení kaskádového mazání pro lokaci $locationId u uživatele $userId")

            try {
                // Vytvoříme dávku pro mazání v databázi
                val batch = db.batch()

                // Krok 1: Najdi všechny měřáky v dané lokaci
                val metersRef = db.collection("users").document(userId).collection("meters")
                val metersSnapshot = metersRef.whereEqualTo("locationId", locationId).get().await()
                Log.d(tag, "Nalezeno ${metersSnapshot.size()} měřáků k smazání.")

                // Krok 2: Projdi všechny nalezené měřáky
                for (meterDoc in metersSnapshot.documents) {
                    val meterId = meterDoc.id
                    Log.d(tag, "Zpracovávám měřák $meterId")

                    // Krok 3: Najdi všechny odečty pro daný měřák
                    val readingsRef = db.collection("readings")
                    val readingsSnapshot = readingsRef.whereEqualTo("meterId", meterId).get().await()
                    Log.d(tag, "Nalezeno ${readingsSnapshot.size()} odečtů pro měřák $meterId")

                    // Krok 4: Smaž všechny fotky a přidej odečty do dávky
                    for (readingDoc in readingsSnapshot.documents) {
                        val reading = readingDoc.toObject(Reading::class.java)

                        // KROK 4a: SMAZÁNÍ FOTKY (první)
                        val photoUrl = reading?.photoUrl //

                        if (photoUrl != null && photoUrl.isNotBlank()) {
                            try {
                                // ✨ OPRAVA: Extrahujeme čistou cestu z URL
                                // Cesta je vše mezi "/o/" a "?alt=media"
                                var pathToDelete = photoUrl.substringAfter("/o/").substringBefore("?alt=media")
                                // Dekódujeme URL znaky (např. %2F -> /)
                                pathToDelete = URLDecoder.decode(pathToDelete, "UTF-8")

                                val photoRef = storage.reference.child(pathToDelete)
                                photoRef.delete().await()
                                Log.d(tag, "Fotka smazána ze Storage: $pathToDelete")
                            } catch (_: Exception) {
                                // Pokud URL nemá očekávaný formát, zkusíme ji použít jako přímou cestu
                                try {
                                    val photoRef = storage.reference.child(photoUrl)
                                    photoRef.delete().await()
                                    Log.d(tag, "Fotka smazána ze Storage (jako přímá cesta): $photoUrl")
                                } catch (e2: Exception) {
                                    Log.w(tag, "Fotku $photoUrl se nepodařilo smazat (možná neexistuje nebo má neznámý formát): ${e2.message}")
                                }
                            }
                        }

                        // KROK 4b: PŘIDÁNÍ ODEČTU DO DÁVKY
                        batch.delete(readingDoc.reference)
                        Log.d(tag, "Odečet ${readingDoc.id} přidán do dávky ke smazání")
                    }

                    // KROK 4c: PŘIDÁNÍ MĚŘÁKU DO DÁVKY
                    batch.delete(meterDoc.reference)
                    Log.d(tag, "Měřák $meterId přidán do dávky ke smazání")
                }

                // Krok 5: Přidej lokaci do dávky ke smazání
                val locationRef = db.collection("users")
                    .document(userId)
                    .collection("locations")
                    .document(locationId)
                batch.delete(locationRef)
                Log.d(tag, "Lokace $locationId přidána do dávky ke smazání")

                // Krok 6: Spusť celou dávku
                batch.commit().await()

                Log.d(tag, "Kaskádové smazání pro lokaci $locationId úspěšně dokončeno.")
                _deleteResult.value = LocationListViewModel.DeleteResult.Success
                _isLoading.value = false

            } catch (e: Exception) {
                Log.e(tag, "Chyba při kaskádovém mazání lokace $locationId", e)
                _deleteResult.value = LocationListViewModel.DeleteResult.Error(e.message ?: "Neznámá chyba při mazání")
                _isLoading.value = false
            }
        }
    }


    fun setAsDefault(locationId: String) {
        if (targetUserId == null) {
            _setDefaultResult.value = LocationListViewModel.SetDefaultResult.Error("User ID není nastaveno")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentDefaultSnapshot = db.collection("users")
                    .document(targetUserId!!)
                    .collection("locations")
                    .whereEqualTo("isDefault", true)
                    .get()
                    .await()

                for (doc in currentDefaultSnapshot.documents) {
                    doc.reference.update("isDefault", false).await()
                }

                db.collection("users")
                    .document(targetUserId!!)
                    .collection("locations")
                    .document(locationId)
                    .update("isDefault", true)
                    .await()

                db.collection("users")
                    .document(targetUserId!!)
                    .update("defaultLocationId", locationId)
                    .await()

                Log.d(tag, "Location $locationId set as default for user $targetUserId")
                _setDefaultResult.value = LocationListViewModel.SetDefaultResult.Success
                _isLoading.value = false

            } catch (e: Exception) {
                Log.e(tag, "Error setting default location", e)
                _setDefaultResult.value = LocationListViewModel.SetDefaultResult.Error(e.message ?: "Unknown error")
                _isLoading.value = false
            }
        }
    }

    // Resetovací funkce mohou zůstat stejné
    fun resetDeleteResult() { _deleteResult.value = LocationListViewModel.DeleteResult.Idle }
    fun resetSetDefaultResult() { _setDefaultResult.value = LocationListViewModel.SetDefaultResult.Idle }

    // Používáme stejné sealed classes jako LocationListViewModel
    // (Nebo si můžete vytvořit vlastní, ale není to nutné)
}