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

class MasterUserListFragment : Fragment() {
    private var _binding: FragmentMasterUserListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MasterViewModel by viewModels()
    private lateinit var userAdapter: UserListAdapter

    private val createFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val format = result.data?.getStringExtra("export_format")
                try {
                    requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                        if (format == "csv") {
                            val content = viewModel.generateCsvContent()
                            outputStream.write(content.toByteArray())
                        } else {
                            val content = viewModel.generateXlsxContent()
                            outputStream.write(content)
                        }
                    }
                    Toast.makeText(context, "Export úspěšně uložen.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Chyba při ukládání souboru: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
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
            val months = resources.getStringArray(R.array.months_array)
            val selectedMonthIndex = months.indexOf(binding.monthSpinner.text.toString())
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            viewModel.applyFilter(searchText, status, selectedMonthIndex, currentYear)
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
        val xlsxButton = dialogView.findViewById<Button>(R.id.exportXlsxButton)
        val csvButton = dialogView.findViewById<Button>(R.id.exportCsvButton)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.export_format_title))
            .setView(dialogView)
            .create()

        xlsxButton.setOnClickListener {
            createFile("xlsx")
            dialog.dismiss()
        }

        csvButton.setOnClickListener {
            createFile("csv")
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun createFile(format: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val currentDate = sdf.format(Date())
            if (format == "csv") {
                type = "text/csv"
                putExtra(Intent.EXTRA_TITLE, "odecty_export_$currentDate.csv")
            } else {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_TITLE, "odecty_export_$currentDate.xlsx")
            }
            putExtra("export_format", format)
        }
        createFileLauncher.launch(intent)
    }

    private fun setupRecyclerView() {
        userAdapter = UserListAdapter()
        binding.usersRecyclerView.adapter = userAdapter
    }

    private fun setupFilterSpinners() {
        val months = resources.getStringArray(R.array.months_array)
        val monthAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, months)
        val monthSpinner = (binding.monthSpinnerLayout.editText as? AutoCompleteTextView)
        monthSpinner?.setAdapter(monthAdapter)
        val currentMonthName = months[Calendar.getInstance().get(Calendar.MONTH)]
        monthSpinner?.setText(currentMonthName, false)
        monthSpinner?.setOnClickListener { monthSpinner.showDropDown() }

        val statuses = listOf("Vše", "Provedeno", "Čekající")
        val statusAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, statuses)
        val statusSpinner = (binding.statusSpinnerLayout.editText as? AutoCompleteTextView)
        statusSpinner?.setAdapter(statusAdapter)
        statusSpinner?.setText(statuses[0], false)
        statusSpinner?.setOnClickListener { statusSpinner.showDropDown() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
