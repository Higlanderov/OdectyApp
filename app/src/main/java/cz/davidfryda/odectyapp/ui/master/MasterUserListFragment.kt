package cz.davidfryda.odectyapp.ui.master

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.databinding.FragmentMasterUserListBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.util.Log


class MasterUserListFragment : Fragment() {
    private var _binding: FragmentMasterUserListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MasterViewModel by viewModels()
    private lateinit var userAdapter: UserListAdapter

    private var pendingExportFormat: String? = null

    private val createFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MasterUserListFragment", "=== createFileLauncher callback ===")
        Log.d("MasterUserListFragment", "resultCode: ${result.resultCode}")
        Log.d("MasterUserListFragment", "pendingExportFormat: $pendingExportFormat")

        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val format = pendingExportFormat
                Log.d("MasterUserListFragment", "Format: $format, URI: $uri")

                if (format == "images") {
                    lifecycleScope.launch {
                        try {
                            Log.d("MasterUserListFragment", "=== Starting image export ===")
                            Log.d("MasterUserListFragment", "exportableData size: ${viewModel.exportableData.size}")

                            if (viewModel.exportableData.isEmpty()) {
                                Toast.makeText(context, "Žádná data k exportu.", Toast.LENGTH_LONG).show()
                                pendingExportFormat = null
                                return@launch
                            }

                            Log.d("MasterUserListFragment", "Calling generateImagesZip...")

                            val zipContent = viewModel.generateImagesZip(requireContext())

                            Log.d("MasterUserListFragment", "ZIP generated, size: ${zipContent.size} bytes")

                            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.write(zipContent)
                            }

                            Toast.makeText(context, "Obrázky úspěšně exportovány (${zipContent.size} bytes).", Toast.LENGTH_SHORT).show()

                        } catch (e: Exception) {
                            Log.e("MasterUserListFragment", "Chyba při exportu obrázků", e)
                            Toast.makeText(context, "Chyba: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            pendingExportFormat = null
                        }
                    }
                    return@registerForActivityResult
                }

                // Pro CSV a XLSX (synchronní zpracování)
                lifecycleScope.launch {
                    try {
                        Log.d("MasterUserListFragment", "Starting $format export")

                        Toast.makeText(context, "Exportuji data...", Toast.LENGTH_SHORT).show()

                        requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                            when (format) {
                                "csv" -> {
                                    val content = viewModel.generateCsvContent()
                                    outputStream.write(content.toByteArray())
                                    Log.d("MasterUserListFragment", "CSV export completed")
                                }
                                "xlsx" -> {
                                    val content = viewModel.generateXlsxContent()
                                    outputStream.write(content)
                                    Log.d("MasterUserListFragment", "XLSX export completed")
                                }
                                else -> {
                                    Log.w("MasterUserListFragment", "Unknown export format: $format")
                                }
                            }
                        }

                        Toast.makeText(context, "Export úspěšně uložen.", Toast.LENGTH_SHORT).show()

                    } catch (e: Exception) {
                        Log.e("MasterUserListFragment", "Chyba při exportu $format", e)
                        Toast.makeText(context, "Chyba při ukládání souboru: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        pendingExportFormat = null
                    }
                }
            }
        } else {
            Log.d("MasterUserListFragment", "Export cancelled or failed")
            pendingExportFormat = null
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMasterUserListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilterSpinners()

        viewModel.filteredUsersWithStatus.observe(viewLifecycleOwner) { userList ->
            userAdapter.submitList(userList)
        }

        viewModel.isFilterActive.observe(viewLifecycleOwner) { isActive ->
            updateFilterButtonState(isActive)
        }

        viewModel.downloadProgress.observe(viewLifecycleOwner) { progress ->
            when (progress) {
                is DownloadProgress.Idle -> {
                    binding.downloadProgressCard.isVisible = false
                }
                is DownloadProgress.Downloading -> {
                    binding.downloadProgressCard.isVisible = true
                    val percentage = if (progress.total > 0) {
                        (progress.current * 100) / progress.total
                    } else 0
                    binding.downloadProgressBar.progress = percentage
                    binding.downloadProgressText.text =
                        "Stahuji obrázky... (${progress.current}/${progress.total})"
                }
                is DownloadProgress.Complete -> {
                    binding.downloadProgressCard.isVisible = false
                    viewModel.resetDownloadProgress()
                }
                is DownloadProgress.Error -> {
                    binding.downloadProgressCard.isVisible = false
                    Toast.makeText(context, "Chyba: ${progress.message}", Toast.LENGTH_LONG).show()
                    viewModel.resetDownloadProgress()
                }
            }
        }

        binding.toggleFilterButton.setOnClickListener {
            val isVisible = !binding.filterCard.isVisible
            binding.filterCard.isVisible = isVisible
            if (viewModel.isFilterActive.value == false) {
                binding.toggleFilterButton.text = if (isVisible) getString(R.string.hide_filter) else getString(R.string.show_filter)
            }
        }

        binding.fabExport.setOnClickListener {
            showExportFormatDialog()
        }

        binding.applyFilterButton.setOnClickListener {
            val searchText = binding.searchEditText.text.toString()

            val status = when (binding.statusSpinner.text.toString()) {
                "Provedeno" -> ReadingStatusFilter.DONE
                "Čekající" -> ReadingStatusFilter.PENDING
                else -> ReadingStatusFilter.ALL
            }

            // ✨ NOVÉ: Parsování filtru pro zablokované uživatele
            val blockedFilter = when (binding.blockedSpinner.text.toString()) {
                getString(R.string.blocked_users) -> BlockedFilter.BLOCKED
                getString(R.string.active_users) -> BlockedFilter.ACTIVE
                else -> BlockedFilter.ALL
            }

            val months = resources.getStringArray(R.array.months_array)
            val selectedMonthIndex = months.indexOf(binding.monthSpinner.text.toString())
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)

            // ✨ UPRAVENO: Přidán parametr blockedFilter
            viewModel.applyFilter(searchText, status, selectedMonthIndex, currentYear, blockedFilter)
            binding.filterCard.isVisible = false
        }

        binding.resetFilterButton.setOnClickListener {
            binding.searchEditText.setText("")
            setupFilterSpinners()
            viewModel.resetFilter()
            binding.filterCard.isVisible = false
        }
    }

    private fun updateFilterButtonState(isActive: Boolean) {
        if (isActive) {
            binding.toggleFilterButton.text = "Filtr je Aktivní"
            val activeColor = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
            binding.toggleFilterButton.iconTint = ColorStateList.valueOf(activeColor)
        } else {
            binding.toggleFilterButton.text = if (binding.filterCard.isVisible) getString(R.string.hide_filter) else getString(R.string.show_filter)
            val typedValue = TypedValue()
            requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
            binding.toggleFilterButton.iconTint = ContextCompat.getColorStateList(requireContext(), typedValue.resourceId)
        }
    }

    private fun showExportFormatDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_export_format, null)
        val imagesButton = dialogView.findViewById<Button>(R.id.exportImagesButton)
        val xlsxButton = dialogView.findViewById<Button>(R.id.exportXlsxButton)
        val csvButton = dialogView.findViewById<Button>(R.id.exportCsvButton)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.export_format_title))
            .setView(dialogView)
            .create()

        imagesButton.setOnClickListener {
            Log.d("MasterUserListFragment", "Images export button clicked")
            createFile("images")
            dialog.dismiss()
        }

        xlsxButton.setOnClickListener {
            Log.d("MasterUserListFragment", "XLSX export button clicked")
            createFile("xlsx")
            dialog.dismiss()
        }

        csvButton.setOnClickListener {
            Log.d("MasterUserListFragment", "CSV export button clicked")
            createFile("csv")
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun createFile(format: String) {
        Log.d("MasterUserListFragment", "createFile called with format: $format")
        pendingExportFormat = format

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val currentDate = sdf.format(Date())
            when (format) {
                "csv" -> {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_TITLE, "odecty_export_$currentDate.csv")
                }
                "xlsx" -> {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_TITLE, "odecty_export_$currentDate.xlsx")
                }
                "images" -> {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_TITLE, "odecty_obrazky_$currentDate.zip")
                }
            }
        }
        createFileLauncher.launch(intent)
    }

    private fun setupRecyclerView() {
        userAdapter = UserListAdapter()
        binding.usersRecyclerView.adapter = userAdapter
    }

    private fun setupFilterSpinners() {
        // Měsíce
        val months = resources.getStringArray(R.array.months_array)
        val monthAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, months)
        val monthSpinner = (binding.monthSpinnerLayout.editText as? AutoCompleteTextView)
        monthSpinner?.setAdapter(monthAdapter)
        val currentMonthName = months[Calendar.getInstance().get(Calendar.MONTH)]
        monthSpinner?.setText(currentMonthName, false)
        monthSpinner?.setOnClickListener { monthSpinner.showDropDown() }

        // Status odečtu
        val statuses = listOf("Vše", "Provedeno", "Čekající")
        val statusAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, statuses)
        val statusSpinner = (binding.statusSpinnerLayout.editText as? AutoCompleteTextView)
        statusSpinner?.setAdapter(statusAdapter)
        statusSpinner?.setText(statuses[0], false)
        statusSpinner?.setOnClickListener { statusSpinner.showDropDown() }

        // ✨ NOVÉ: Filtr pro zablokované uživatele
        val blockedStatuses = listOf(
            getString(R.string.all_users),
            getString(R.string.active_users),
            getString(R.string.blocked_users)
        )
        val blockedAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, blockedStatuses)
        val blockedSpinner = (binding.blockedSpinnerLayout.editText as? AutoCompleteTextView)
        blockedSpinner?.setAdapter(blockedAdapter)
        blockedSpinner?.setText(blockedStatuses[0], false) // Defaultně "Vše"
        blockedSpinner?.setOnClickListener { blockedSpinner.showDropDown() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}