package cz.davidfryda.odectyapp.ui.master

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.data.FullReadingData
import cz.davidfryda.odectyapp.data.UserData
import cz.davidfryda.odectyapp.data.Meter
import cz.davidfryda.odectyapp.data.Reading
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ReadingStatusFilter { DONE, PENDING, ALL }

enum class BlockedFilter { ALL, ACTIVE, BLOCKED }

sealed class DownloadProgress {
    object Idle : DownloadProgress()
    data class Downloading(val current: Int, val total: Int) : DownloadProgress()
    object Complete : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}

class MasterViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val tag = "MasterViewModel"

    private var allUsers = mapOf<String, UserData>()
    private var allMeters = mapOf<String, Meter>()
    private var allReadings = listOf<Reading>()

    private val _filteredUsersWithStatus = MutableLiveData<List<UserWithStatus>>()
    val filteredUsersWithStatus: LiveData<List<UserWithStatus>> = _filteredUsersWithStatus

    private val _isFilterActive = MutableLiveData(false)
    val isFilterActive: LiveData<Boolean> = _isFilterActive

    private val _downloadProgress = MutableLiveData<DownloadProgress>(DownloadProgress.Idle)
    val downloadProgress: LiveData<DownloadProgress> = _downloadProgress

    internal var exportableData = listOf<FullReadingData>()
    private var currentSearchText = ""
    private var currentStatusFilter = ReadingStatusFilter.ALL
    private var currentMonthFilter = Calendar.getInstance().get(Calendar.MONTH)
    private var currentYearFilter = Calendar.getInstance().get(Calendar.YEAR)
    private var currentBlockedFilter = BlockedFilter.ALL

    init {
        Log.d(tag, "Initializing MasterViewModel")
        db.collection("users").addSnapshotListener { usersSnapshot, error ->
            if (error != null) {
                Log.e(tag, "Error fetching users", error)
                return@addSnapshotListener
            }
            usersSnapshot?.let { snapshot ->  // ✨ ZMĚNA: it → snapshot
                allUsers = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(UserData::class.java)?.copy(uid = doc.id)
                }.associateBy { it.uid }
                Log.d(tag, "Fetched ${allUsers.size} users")
                applyFilter()
            }
        }
        db.collectionGroup("meters").addSnapshotListener { metersSnapshot, error ->
            if (error != null) {
                Log.e(tag, "Error fetching meters", error)
                return@addSnapshotListener
            }
            metersSnapshot?.let { snapshot ->  // ✨ ZMĚNA: it → snapshot
                allMeters = snapshot.documents.mapNotNull { doc ->
                    val meter = doc.toObject(Meter::class.java)?.copy(id = doc.id)
                    meter
                }.associateBy { it.id }
                Log.d(tag, "Fetched ${allMeters.size} meters")
                applyFilter()
            }
        }
        db.collection("readings").addSnapshotListener { readingsSnapshot, error ->
            if (error != null) {
                Log.e(tag, "Error fetching readings", error)
                return@addSnapshotListener
            }
            readingsSnapshot?.let { snapshot ->  // ✨ ZMĚNA: it → snapshot
                allReadings = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Reading::class.java)?.copy(id = doc.id)
                }
                Log.d(tag, "Fetched ${allReadings.size} readings")
                applyFilter()
            }
        }
    }

    fun applyFilter(
        searchText: String? = null,
        status: ReadingStatusFilter? = null,
        month: Int? = null,
        year: Int? = null,
        blockedFilter: BlockedFilter? = null
    ) {
        searchText?.let { currentSearchText = it }
        status?.let { currentStatusFilter = it }
        month?.let { currentMonthFilter = it }
        year?.let { currentYearFilter = it }
        blockedFilter?.let { currentBlockedFilter = it }

        Log.d(tag, "Applying filter: Search='$currentSearchText', Status=$currentStatusFilter, Month=$currentMonthFilter, Year=$currentYearFilter, Blocked=$currentBlockedFilter")

        val filteredUserIds = if (currentSearchText.isNotBlank()) {
            allUsers.values.filter { user ->
                user.name.contains(currentSearchText, true) ||
                        user.surname.contains(currentSearchText, true) ||
                        user.address.contains(currentSearchText, true)
            }.map { it.uid }.toSet()
        } else {
            allUsers.keys
        }
        Log.d(tag, "Filtered users by text count: ${filteredUserIds.size}")

        val filteredByBlocked = when (currentBlockedFilter) {
            BlockedFilter.ALL -> filteredUserIds
            BlockedFilter.ACTIVE -> filteredUserIds.filter { uid ->
                allUsers[uid]?.isDisabled == false
            }.toSet()
            BlockedFilter.BLOCKED -> filteredUserIds.filter { uid ->
                allUsers[uid]?.isDisabled == true
            }.toSet()
        }
        Log.d(tag, "Filtered users by blocked status count: ${filteredByBlocked.size}")

        val calendar = Calendar.getInstance()
        val usersWithStatus = allUsers.values
            .filter { it.uid in filteredByBlocked }
            .map { user ->
                val hasReading = allReadings.any { reading ->
                    calendar.time = reading.timestamp ?: Date(0)
                    reading.userId == user.uid &&
                            calendar.get(Calendar.MONTH) == currentMonthFilter &&
                            calendar.get(Calendar.YEAR) == currentYearFilter
                }
                UserWithStatus(user, hasReading)
            }

        val uiFinalList = when (currentStatusFilter) {
            ReadingStatusFilter.DONE -> usersWithStatus.filter { it.hasReadingForCurrentMonth }
            ReadingStatusFilter.PENDING -> usersWithStatus.filter { !it.hasReadingForCurrentMonth }
            ReadingStatusFilter.ALL -> usersWithStatus
        }
        _filteredUsersWithStatus.value = uiFinalList.sortedBy { it.user.surname }
        Log.d(tag, "UI list size after status filter: ${uiFinalList.size}")

        val newExportableData = mutableListOf<FullReadingData>()
        val finalFilteredUserIdsForExport = uiFinalList.map { it.user.uid }.toSet()

        allReadings.forEach { reading ->
            calendar.time = reading.timestamp ?: Date(0)
            if (reading.userId in finalFilteredUserIdsForExport &&
                calendar.get(Calendar.MONTH) == currentMonthFilter &&
                calendar.get(Calendar.YEAR) == currentYearFilter) {

                val user = allUsers[reading.userId]
                val meter = allMeters[reading.meterId]

                if (user != null && meter != null) {
                    newExportableData.add(FullReadingData(user, meter, reading))
                } else {
                    Log.w(tag, "Skipping reading ${reading.id} for export: User or Meter not found")
                }
            }
        }

        exportableData = newExportableData.sortedWith(compareBy({ it.user.surname }, { it.reading?.timestamp }))
        Log.d(tag, "Exportable data size: ${exportableData.size}")

        updateFilterActiveStatus()
    }

    private fun updateFilterActiveStatus() {
        val defaultMonth = Calendar.getInstance().get(Calendar.MONTH)
        val defaultYear = Calendar.getInstance().get(Calendar.YEAR)
        val isActive = currentSearchText.isNotBlank() ||
                currentStatusFilter != ReadingStatusFilter.ALL ||
                currentMonthFilter != defaultMonth ||
                currentYearFilter != defaultYear ||
                currentBlockedFilter != BlockedFilter.ALL
        _isFilterActive.value = isActive
        Log.d(tag, "Filter active status updated: $isActive")
    }

    fun resetFilter() {
        Log.d(tag, "Resetting filter")
        currentSearchText = ""
        currentStatusFilter = ReadingStatusFilter.ALL
        currentMonthFilter = Calendar.getInstance().get(Calendar.MONTH)
        currentYearFilter = Calendar.getInstance().get(Calendar.YEAR)
        currentBlockedFilter = BlockedFilter.ALL
        applyFilter()
    }

    fun generateCsvContent(): String {
        Log.d(tag, "Generating CSV content for ${exportableData.size} records")
        val header = "Datum Odečtu,Jméno,Příjmení,Adresa,Měřák ID,Název Měřáku,Typ Měřáku,Popis Správce,Hodnota Odečtu,Jednotka\n"
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val rows = exportableData.joinToString(separator = "\n") { data ->
            val date = data.reading?.timestamp?.let { dateFormat.format(it) } ?: ""
            val value = data.reading?.finalValue?.toString() ?: ""
            val unit = if (data.meter != null) getUnitForMeter(data.meter) else ""
            val meterId = data.meter?.id ?: ""
            val meterName = data.meter?.name ?: ""
            val meterType = data.meter?.type ?: ""
            val masterDesc = data.meter?.masterDescription ?: ""
            val address = data.user.address.replace("\"", "\"\"")

            "\"$date\",\"${data.user.name}\",\"${data.user.surname}\",\"$address\",\"$meterId\",\"$meterName\",\"$meterType\",\"$masterDesc\",\"$value\",\"$unit\""
        }
        return header + rows
    }

    fun generateXlsxContent(): ByteArray {
        Log.d(tag, "Generating XLSX content for ${exportableData.size} records")
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Odečty")
        val headerRow = sheet.createRow(0)

        val headers = listOf("Datum Odečtu", "Jméno", "Příjmení", "Adresa", "Měřák ID", "Název Měřáku", "Typ Měřáku", "Popis Správce", "Hodnota Odečtu", "Jednotka")
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).setCellValue(header)
        }
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        exportableData.forEachIndexed { rowIndex, data ->
            val dataRow = sheet.createRow(rowIndex + 1)
            dataRow.createCell(0).setCellValue(data.reading?.timestamp?.let { dateFormat.format(it) } ?: "")
            dataRow.createCell(1).setCellValue(data.user.name)
            dataRow.createCell(2).setCellValue(data.user.surname)
            dataRow.createCell(3).setCellValue(data.user.address)
            dataRow.createCell(4).setCellValue(data.meter?.id ?: "")
            dataRow.createCell(5).setCellValue(data.meter?.name ?: "")
            dataRow.createCell(6).setCellValue(data.meter?.type ?: "")
            dataRow.createCell(7).setCellValue(data.meter?.masterDescription ?: "")

            if (data.reading?.finalValue != null) {
                dataRow.createCell(8).setCellValue(data.reading.finalValue)
            } else {
                dataRow.createCell(8).setCellValue("")
            }
            dataRow.createCell(9).setCellValue(if (data.meter != null) getUnitForMeter(data.meter) else "")
        }

        val outputStream = ByteArrayOutputStream()
        workbook.write(outputStream)
        workbook.close()
        return outputStream.toByteArray()
    }

    private fun getUnitForMeter(meter: Meter): String {
        return when (meter.type) {
            "Elektřina" -> "kWh"
            "Plyn" -> "m³"
            "Voda" -> "m³"
            else -> ""
        }
    }

    // ✨ ZMĚNA 1: Odebrán nepoužitý parametr context
    suspend fun generateImagesZip(): ByteArray = withContext(Dispatchers.IO) {
        Log.d(tag, "=== Starting generateImagesZip ===")
        Log.d(tag, "Total exportableData records: ${exportableData.size}")

        val byteArrayOutputStream = ByteArrayOutputStream()
        val zipOutputStream = ZipOutputStream(byteArrayOutputStream)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

        try {
            val recordsWithImages = exportableData.filter { !it.reading?.photoUrl.isNullOrEmpty() }
            val totalImages = recordsWithImages.size

            Log.d(tag, "Records with images: $totalImages")

            withContext(Dispatchers.Main) {
                _downloadProgress.value = DownloadProgress.Downloading(0, totalImages)
            }

            var processedCount = 0
            var skippedCount = 0

            for ((index, data) in exportableData.withIndex()) {
                val reading = data.reading
                val photoUrl = reading?.photoUrl

                Log.d(tag, "Processing record ${index + 1}/${exportableData.size}")

                if (photoUrl.isNullOrEmpty()) {
                    Log.d(tag, "  -> SKIPPED: No photo URL")
                    skippedCount++
                    continue
                }

                try {
                    Log.d(tag, "  -> Downloading ${processedCount + 1}/$totalImages")

                    val photoRef = storage.getReferenceFromUrl(photoUrl)
                    val imageBytes = photoRef.getBytes(Long.MAX_VALUE).await()
                    Log.d(tag, "  -> Downloaded ${imageBytes.size} bytes")

                    val address = data.user.address.replace("[^a-zA-Z0-9]".toRegex(), "_")
                    val timestamp = reading.timestamp?.let { dateFormat.format(it) } ?: "bez_data"
                    val fileName = "${address}_${timestamp}.jpg"

                    val zipEntry = ZipEntry(fileName)
                    zipOutputStream.putNextEntry(zipEntry)
                    zipOutputStream.write(imageBytes)
                    zipOutputStream.closeEntry()

                    processedCount++
                    Log.d(tag, "  -> SUCCESS ($processedCount/$totalImages)")

                    withContext(Dispatchers.Main) {
                        _downloadProgress.value = DownloadProgress.Downloading(processedCount, totalImages)
                    }

                } catch (e: Exception) {
                    Log.e(tag, "  -> FAILED to download image", e)
                    skippedCount++
                }
            }

            Log.d(tag, "=== ZIP generation complete ===")
            Log.d(tag, "Processed: $processedCount")
            Log.d(tag, "Skipped: $skippedCount")
            Log.d(tag, "ZIP size: ${byteArrayOutputStream.size()} bytes")

            withContext(Dispatchers.Main) {
                _downloadProgress.value = DownloadProgress.Complete
            }

        } catch (e: Exception) {
            Log.e(tag, "Fatal error in generateImagesZip", e)

            withContext(Dispatchers.Main) {
                _downloadProgress.value = DownloadProgress.Error(e.message ?: "Neznámá chyba")
            }

            throw e
        } finally {
            zipOutputStream.close()
        }

        byteArrayOutputStream.toByteArray()
    }

    fun resetDownloadProgress() {
        _downloadProgress.value = DownloadProgress.Idle
        Log.d(tag, "Download progress reset to Idle")
    }
}