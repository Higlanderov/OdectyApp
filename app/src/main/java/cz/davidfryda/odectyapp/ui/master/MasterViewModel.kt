package cz.davidfryda.odectyapp.ui.master

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
        db.collection("users").addSnapshotListener { usersSnapshot, _ ->
            usersSnapshot?.let {
                allUsers = it.documents.mapNotNull { doc -> doc.toObject(UserData::class.java) }.associateBy { it.uid }
                applyFilter()
            }
        }
        db.collectionGroup("meters").addSnapshotListener { metersSnapshot, _ ->
            metersSnapshot?.let {
                allMeters = it.documents.mapNotNull { doc -> doc.toObject(Meter::class.java)?.copy(id = doc.id) }.associateBy { it.id }
                applyFilter()
            }
        }
        db.collection("readings").addSnapshotListener { readingsSnapshot, _ ->
            readingsSnapshot?.let {
                allReadings = it.documents.mapNotNull { doc -> doc.toObject(Reading::class.java)?.copy(id = doc.id) }
                applyFilter()
            }
        }
    }

    fun applyFilter(searchText: String? = null, status: ReadingStatusFilter? = null, month: Int? = null, year: Int? = null) {
        searchText?.let { currentSearchText = it }
        status?.let { currentStatusFilter = it }
        month?.let { currentMonthFilter = it }
        year?.let { currentYearFilter = it }

        var filteredUsers = allUsers.values.toList()
        if (currentSearchText.isNotBlank()) {
            filteredUsers = filteredUsers.filter { user ->
                user.name.contains(currentSearchText, true) ||
                        user.surname.contains(currentSearchText, true) ||
                        user.address.contains(currentSearchText, true)
            }
        }

        val calendar = Calendar.getInstance()
        val usersWithStatus = filteredUsers.map { user ->
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
        _filteredUsersWithStatus.value = uiFinalList

        exportableData = uiFinalList.map { userWithStatus ->
            if (userWithStatus.hasReadingForCurrentMonth) {
                val reading = allReadings.find { reading ->
                    calendar.time = reading.timestamp ?: Date(0)
                    reading.userId == userWithStatus.user.uid &&
                            calendar.get(Calendar.MONTH) == currentMonthFilter &&
                            calendar.get(Calendar.YEAR) == currentYearFilter
                }
                val meter = allMeters[reading?.meterId]
                if (reading != null && meter != null) {
                    FullReadingData(userWithStatus.user, meter, reading)
                } else {
                    FullReadingData(userWithStatus.user, null, null)
                }
            } else {
                FullReadingData(userWithStatus.user, null, null)
            }
        }
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
    }

    fun resetFilter() {
        applyFilter("", ReadingStatusFilter.ALL, Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.YEAR))
    }

    fun generateCsvContent(): String {
        val header = "Datum Odečtu,Jméno,Příjmení,Adresa,Typ Měřáku,Hodnota Odečtu\n"
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val rows = exportableData.joinToString(separator = "\n") { data ->
            val date = data.reading?.timestamp?.let { dateFormat.format(it) } ?: ""
            val value = data.reading?.let { formatValueWithUnit(it, data.meter!!) } ?: ""
            val meterInfo = data.meter?.let { "${it.name} (${it.type})" } ?: ""
            "\"$date\",\"${data.user.name}\",\"${data.user.surname}\",\"${data.user.address.replace("\"", "\"\"")}\",\"$meterInfo\",\"$value\""
        }
        return header + rows
    }

    fun generateXlsxContent(): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Odečty")
        val headerRow = sheet.createRow(0)
        val headers = listOf("Datum Odečtu", "Jméno", "Příjmení", "Adresa", "Typ Měřáku", "Hodnota Odečtu")
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
            dataRow.createCell(4).setCellValue(data.meter?.let { "${it.name} (${it.type})" } ?: "")
            dataRow.createCell(5).setCellValue(if (data.reading != null && data.meter != null) formatValueWithUnit(data.reading, data.meter) else "")
        }
        val outputStream = ByteArrayOutputStream()
        workbook.write(outputStream)
        workbook.close()
        return outputStream.toByteArray()
    }

    private fun formatValueWithUnit(reading: Reading, meter: Meter): String {
        val unit = when (meter.type) {
            "Elektřina" -> "kWh"
            "Plyn" -> "m³"
            "Voda" -> "m³"
            else -> ""
        }
        return "${reading.finalValue} $unit"
    }
}