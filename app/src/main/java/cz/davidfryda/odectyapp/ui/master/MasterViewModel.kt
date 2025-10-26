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

enum class ReadingStatusFilter { DONE, PENDING, ALL }

class MasterViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val TAG = "MasterViewModel"

    private var allUsers = mapOf<String, UserData>()
    private var allMeters = mapOf<String, Meter>()
    private var allReadings = listOf<Reading>()

    private val _filteredUsersWithStatus = MutableLiveData<List<UserWithStatus>>()
    val filteredUsersWithStatus: LiveData<List<UserWithStatus>> = _filteredUsersWithStatus

    private val _isFilterActive = MutableLiveData<Boolean>(false)
    val isFilterActive: LiveData<Boolean> = _isFilterActive

    private var exportableData = listOf<FullReadingData>()
    private var currentSearchText = ""
    private var currentStatusFilter = ReadingStatusFilter.ALL
    private var currentMonthFilter = Calendar.getInstance().get(Calendar.MONTH)
    private var currentYearFilter = Calendar.getInstance().get(Calendar.YEAR)

    init {
        Log.d(TAG, "Initializing MasterViewModel")
        db.collection("users").addSnapshotListener { usersSnapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error fetching users", error)
                return@addSnapshotListener
            }
            usersSnapshot?.let {
                allUsers = it.documents.mapNotNull { doc -> doc.toObject(UserData::class.java)?.copy(uid = doc.id) }.associateBy { it.uid }
                Log.d(TAG, "Fetched ${allUsers.size} users")
                applyFilter()
            }
        }
        db.collectionGroup("meters").addSnapshotListener { metersSnapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error fetching meters", error)
                return@addSnapshotListener
            }
            metersSnapshot?.let {
                allMeters = it.documents.mapNotNull { doc ->
                    val meter = doc.toObject(Meter::class.java)?.copy(id = doc.id)
                    meter
                }.associateBy { it.id }
                Log.d(TAG, "Fetched ${allMeters.size} meters")
                applyFilter()
            }
        }
        db.collection("readings").addSnapshotListener { readingsSnapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error fetching readings", error)
                return@addSnapshotListener
            }
            readingsSnapshot?.let {
                allReadings = it.documents.mapNotNull { doc -> doc.toObject(Reading::class.java)?.copy(id = doc.id) }
                Log.d(TAG, "Fetched ${allReadings.size} readings")
                applyFilter()
            }
        }
    }

    fun applyFilter(searchText: String? = null, status: ReadingStatusFilter? = null, month: Int? = null, year: Int? = null) {
        searchText?.let { currentSearchText = it }
        status?.let { currentStatusFilter = it }
        month?.let { currentMonthFilter = it }
        year?.let { currentYearFilter = it }

        Log.d(TAG, "Applying filter: Search='$currentSearchText', Status=$currentStatusFilter, Month=$currentMonthFilter, Year=$currentYearFilter")

        // 1. Filtrování uživatelů podle textu
        val filteredUserIds = if (currentSearchText.isNotBlank()) {
            allUsers.values.filter { user ->
                user.name.contains(currentSearchText, true) ||
                        user.surname.contains(currentSearchText, true) ||
                        user.address.contains(currentSearchText, true)
            }.map { it.uid }.toSet()
        } else {
            allUsers.keys
        }
        Log.d(TAG, "Filtered users by text count: ${filteredUserIds.size}")


        // 2. Příprava seznamu uživatelů pro UI
        val calendar = Calendar.getInstance()
        val usersWithStatus = allUsers.values
            .filter { it.uid in filteredUserIds }
            .map { user ->
                val hasReading = allReadings.any { reading ->
                    calendar.time = reading.timestamp ?: Date(0)
                    reading.userId == user.uid &&
                            calendar.get(Calendar.MONTH) == currentMonthFilter &&
                            calendar.get(Calendar.YEAR) == currentYearFilter
                }
                UserWithStatus(user, hasReading)
            }

        // 3. Aplikace filtru statusu na seznam pro UI
        val uiFinalList = when (currentStatusFilter) {
            ReadingStatusFilter.DONE -> usersWithStatus.filter { it.hasReadingForCurrentMonth }
            ReadingStatusFilter.PENDING -> usersWithStatus.filter { !it.hasReadingForCurrentMonth }
            ReadingStatusFilter.ALL -> usersWithStatus
        }
        _filteredUsersWithStatus.value = uiFinalList.sortedBy { it.user.surname }
        Log.d(TAG, "UI list size after status filter: ${uiFinalList.size}")

        // 4. Sestavení dat pro export
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
                    Log.w(TAG, "Skipping reading ${reading.id} for export: User or Meter not found (User found: ${user!=null}, Meter found: ${meter!=null}, MeterId was: ${reading.meterId})")
                }
            }
        }

        exportableData = newExportableData.sortedWith(compareBy({ it.user.surname }, { it.reading?.timestamp }))
        Log.d(TAG, "Exportable data size: ${exportableData.size}")

        updateFilterActiveStatus()
    }

    private fun updateFilterActiveStatus() {
        val defaultMonth = Calendar.getInstance().get(Calendar.MONTH)
        val defaultYear = Calendar.getInstance().get(Calendar.YEAR)
        val isActive = currentSearchText.isNotBlank() ||
                currentStatusFilter != ReadingStatusFilter.ALL ||
                currentMonthFilter != defaultMonth ||
                currentYearFilter != defaultYear
        _isFilterActive.value = isActive
        Log.d(TAG, "Filter active status updated: $isActive")
    }

    fun resetFilter() {
        Log.d(TAG, "Resetting filter")
        currentSearchText = ""
        currentStatusFilter = ReadingStatusFilter.ALL
        currentMonthFilter = Calendar.getInstance().get(Calendar.MONTH)
        currentYearFilter = Calendar.getInstance().get(Calendar.YEAR)
        applyFilter()
    }

    fun generateCsvContent(): String {
        Log.d(TAG, "Generating CSV content for ${exportableData.size} records")
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
        Log.d(TAG, "Generating XLSX content for ${exportableData.size} records")
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

        // --- ✨ ZMĚNA: Automatické přizpůsobení šířky bylo ODSTRANĚNO ---
        // headers.indices.forEach { sheet.autoSizeColumn(it) } // TOTO ZPŮSOBOVALO PÁD

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
}