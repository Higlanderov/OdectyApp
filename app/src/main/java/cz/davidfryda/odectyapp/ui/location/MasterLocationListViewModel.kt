package cz.davidfryda.odectyapp.ui.location

import android.app.Application // ✨ OPRAVA: Potřebujeme Application context
import android.util.Log
import androidx.lifecycle.AndroidViewModel // ✨ OPRAVA: Změna z ViewModel na AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.data.Location
import cz.davidfryda.odectyapp.data.Reading
import cz.davidfryda.odectyapp.database.AppDatabase
import cz.davidfryda.odectyapp.database.DeletionTypes
import cz.davidfryda.odectyapp.database.PendingDeletion
import cz.davidfryda.odectyapp.workers.DeleteWorker
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.net.URLDecoder

// ✨ OPRAVA: Dědíme z AndroidViewModel pro přístup ke kontextu pro WorkManager a Databázi
class MasterLocationListViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Firebase.firestore
    private val tag = "MasterLocationListVM"

    // ✨ OPRAVA: Získání DAO a WorkManageru
    private val pendingDeletionDao = AppDatabase.getDatabase(application).pendingDeletionDao()
    private val workManager = WorkManager.getInstance(application)

    private val _locations = MutableLiveData<List<Location>>()
    val locations: LiveData<List<Location>> = _locations

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _deleteResult = MutableLiveData<LocationListViewModel.DeleteResult>()
    val deleteResult: LiveData<LocationListViewModel.DeleteResult> = _deleteResult

    private val _setDefaultResult = MutableLiveData<LocationListViewModel.SetDefaultResult>()
    val setDefaultResult: LiveData<LocationListViewModel.SetDefaultResult> = _setDefaultResult

    private var targetUserId: String? = null

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

    // ✨ OPRAVA: Toto je nyní nová, zjednodušená funkce deleteLocation
    fun deleteLocation(locationId: String) {
        if (targetUserId == null) {
            _deleteResult.value = LocationListViewModel.DeleteResult.Error("User ID není nastaveno")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true // Stále zobrazíme načítání, i když je to lokální
            try {
                // Krok 1: Vytvoř úkol ke smazání
                val deletionTask = PendingDeletion(
                    entityType = DeletionTypes.LOCATION,
                    userId = targetUserId!!,
                    entityId = locationId
                )

                // Krok 2: Ulož úkol do lokální databáze (Room)
                pendingDeletionDao.insert(deletionTask)
                Log.d(tag, "Lokace $locationId přidána do offline fronty mazání.")

                // Krok 3: Vytvoř omezení pro Workera (jen když je online)
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                // Krok 4: Zařaď Workera
                val deleteRequest = OneTimeWorkRequestBuilder<DeleteWorker>()
                    .setConstraints(constraints)
                    .build()

                workManager.enqueueUniqueWork(
                    DeleteWorker.TAG, // Unikátní jméno, aby se nespustil vícekrát
                    ExistingWorkPolicy.KEEP, // Pokud už běží, nech ho doběhnout
                    deleteRequest
                )

                // Krok 5: Okamžitě informuj UI o "úspěchu" (Optimistic Update)
                // Fragment po této zprávě obnoví seznam
                _deleteResult.value = LocationListViewModel.DeleteResult.Success
                _isLoading.value = false

            } catch (e: Exception) {
                Log.e(tag, "Chyba při zařazování do offline fronty", e)
                _deleteResult.value = LocationListViewModel.DeleteResult.Error(e.message ?: "Chyba při offline mazání")
                _isLoading.value = false
            }
        }
    }

    // Funkce setAsDefault zůstává online, protože je to rychlá operace
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

    fun resetDeleteResult() { _deleteResult.value = LocationListViewModel.DeleteResult.Idle }
    fun resetSetDefaultResult() { _setDefaultResult.value = LocationListViewModel.SetDefaultResult.Idle }
}