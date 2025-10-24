package cz.davidfryda.odectyapp.ui.main

import android.os.Bundle
import android.util.Log // Import pro Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat // <--- Import pro ContextCompat
import androidx.core.view.isVisible // Import pro isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController // Import pro navigaci
import androidx.navigation.fragment.navArgs
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.Reading // Import Reading
import cz.davidfryda.odectyapp.databinding.FragmentReadingDetailBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ReadingDetailFragment : Fragment() {
    private var _binding: FragmentReadingDetailBinding? = null
    private val binding get() = _binding!!
    private val args: ReadingDetailFragmentArgs by navArgs()
    private val viewModel: MeterDetailViewModel by viewModels()
    private val dateFormat = SimpleDateFormat("dd. MM. yyyy HH:mm", Locale.getDefault())
    private val tag = "ReadingDetailFragment" // TAG pro logování

    // Přidáme proměnnou pro uložení aktuálního odečtu
    private var currentReading: Reading? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReadingDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Resetujeme stavy ViewModelu při vstupu na obrazovku, abychom nezobrazovali staré toasty/dialogy
        viewModel.resetUpdateResult()
        viewModel.resetDeleteResult()

        viewModel.loadSingleReading(args.readingId)

        viewModel.singleReading.observe(viewLifecycleOwner) { reading ->
            currentReading = reading // Uložíme si aktuální odečet

            if (reading == null) {
                // Odečet nebyl nalezen nebo se nepodařilo načíst (např. smazán mezitím)
                Log.w(tag, "singleReading observer: reading je null (ID: ${args.readingId}). Možná byl smazán.")
                Toast.makeText(context, R.string.reading_not_found, Toast.LENGTH_SHORT).show()
                findNavController().popBackStack() // Vrátíme se zpět
                return@observe
            }

            val unit = when (args.meterType) {
                "Elektřina" -> "kWh"
                "Plyn" -> "m³"
                "Voda" -> "m³"
                else -> ""
            }
            binding.readingValue.text = getString(R.string.reading_value_with_unit, reading.finalValue ?: "N/A", unit) // Ošetření null hodnoty
            binding.readingDate.text = reading.timestamp?.let { dateFormat.format(it) } ?: "N/A"

            // --- Vylepšená logika načítání obrázku ---
            val circularProgressDrawable = CircularProgressDrawable(requireContext()).apply {
                strokeWidth = 5f
                centerRadius = 30f
                start()
            }
            binding.readingPhoto.load(reading.photoUrl.ifEmpty { null }) { // Pokud je URL prázdné, načteme null
                crossfade(true)
                placeholder(circularProgressDrawable)
                error(R.drawable.ic_error)
            }

            // --- ŘÍZENÍ AKTIVITY A VZHLEDU TLAČÍTEK ---
            val isLoading = viewModel.updateResult.value is UploadResult.Loading || viewModel.deleteResult.value is UploadResult.Loading
            val isAdminEdit = reading.editedByAdmin && !args.isMasterView

            // Ovládání tlačítka Upravit
            if (isAdminEdit) {
                binding.editButton.text = getString(R.string.edited_by_admin)
                binding.editButton.setIconResource(R.drawable.ic_admin)
                binding.editButton.isEnabled = false // Běžný uživatel nemůže upravit
            } else {
                binding.editButton.text = getString(R.string.edit_value)
                // Tlačítko Upravit je aktivní, pokud není loading A odečet je synchronizovaný
                binding.editButton.isEnabled = !isLoading && reading.isSynced // Úprava offline nemá smysl
                binding.editButton.icon = null
            }

            // Ovládání tlačítka Smazat
            if (isAdminEdit) {
                // Pokud upravil admin a nejsme master, zablokujeme a vizuálně odlišíme
                binding.deleteButton.text = getString(R.string.edited_by_admin) // Můžeme použít stejný text nebo jiný
                binding.deleteButton.setIconResource(R.drawable.ic_admin) // Můžeme přidat ikonu zámku nebo admina
                binding.deleteButton.isEnabled = false // Běžný uživatel nemůže smazat
            } else {
                // Jinak nastavíme standardní text a ikonu (pokud má mít ikonu)
                binding.deleteButton.text = getString(R.string.delete_reading) // Standardní text
                binding.deleteButton.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete) // Standardní ikona (pokud používáme ikonu)
                // Tlačítko Smazat je aktivní, pokud není loading (master může vždy, uživatel jen pokud neupravil admin)
                binding.deleteButton.isEnabled = !isLoading
            }
            // --- KONEC ŘÍZENÍ AKTIVITY A VZHLEDU TLAČÍTEK ---
        }

        binding.editButton.setOnClickListener {
            // Použijeme uložený currentReading
            currentReading?.let { reading ->
                // Kontrola isSynced přidána pro jistotu, i když by se sem offline neměl dostat
                if (!reading.isSynced) {
                    Toast.makeText(context, R.string.cannot_edit_unsynced_reading, Toast.LENGTH_SHORT).show()
                } else if (reading.editedByAdmin && !args.isMasterView) {
                    showLockedDialog()
                } else {
                    showEditDialog(reading.finalValue ?: 0.0)
                }
            } ?: Log.w(tag, "Edit button clicked but currentReading is null.") // Logování, pokud je currentReading null
        }

        // Listener pro tlačítko Smazat
        binding.deleteButton.setOnClickListener {
            currentReading?.let { reading ->
                showDeleteConfirmationDialog(reading)
            } ?: Log.w(tag, "Delete button clicked but currentReading is null.")
        }

        // Observer pro výsledek mazání
        viewModel.deleteResult.observe(viewLifecycleOwner) { result ->
            val isLoading = result is UploadResult.Loading || viewModel.updateResult.value is UploadResult.Loading
            binding.progressBar.isVisible = isLoading
            // Znovu aplikujeme logiku pro isEnabled na základě aktuálního reading a nového isLoading
            currentReading?.let { reading ->
                val isAdminEdit = reading.editedByAdmin && !args.isMasterView
                binding.editButton.isEnabled = !isLoading && reading.isSynced && !isAdminEdit
                binding.deleteButton.isEnabled = !isLoading && (!isAdminEdit || args.isMasterView)
            } ?: run { // Pokud currentReading je null (např. po smazání), deaktivujeme
                binding.editButton.isEnabled = false
                binding.deleteButton.isEnabled = false
            }


            when(result) {
                is UploadResult.Success -> {
                    Log.d(tag, "deleteResult observer: Success.")
                    Toast.makeText(context, R.string.reading_deleted_successfully, Toast.LENGTH_SHORT).show()
                    viewModel.resetDeleteResult() // Resetujeme stav
                    findNavController().popBackStack() // Navigace zpět po úspěšném smazání
                }
                is UploadResult.Error -> {
                    Log.e(tag, "deleteResult observer: Error: ${result.message}")
                    Toast.makeText(context, getString(R.string.error_deleting_reading, result.message), Toast.LENGTH_LONG).show()
                    viewModel.resetDeleteResult() // Resetujeme stav
                }
                is UploadResult.Loading -> {
                    Log.d(tag, "deleteResult observer: Loading.")
                    /* Progress bar se točí */
                }
                is UploadResult.Idle -> { /* Nic neděláme */ }
            }
        }

        // Observer pro výsledek úpravy
        viewModel.updateResult.observe(viewLifecycleOwner) { result ->
            val isLoading = result is UploadResult.Loading || viewModel.deleteResult.value is UploadResult.Loading
            binding.progressBar.isVisible = isLoading
            // Znovu aplikujeme logiku pro isEnabled na základě aktuálního reading a nového isLoading
            currentReading?.let { reading ->
                val isAdminEdit = reading.editedByAdmin && !args.isMasterView
                binding.editButton.isEnabled = !isLoading && reading.isSynced && !isAdminEdit
                binding.deleteButton.isEnabled = !isLoading && (!isAdminEdit || args.isMasterView)
            } ?: run { // Pokud currentReading je null
                binding.editButton.isEnabled = false
                binding.deleteButton.isEnabled = false
            }

            when(result) {
                is UploadResult.Success -> {
                    Log.d(tag, "updateResult observer: Success.")
                    Toast.makeText(context, R.string.value_updated_successfully, Toast.LENGTH_SHORT).show()
                    viewModel.resetUpdateResult() // Resetujeme stav po zobrazení
                }
                is UploadResult.Error -> {
                    Log.e(tag, "updateResult observer: Error: ${result.message}")
                    Toast.makeText(context, getString(R.string.error_updating_value, result.message), Toast.LENGTH_LONG).show()
                    viewModel.resetUpdateResult() // Resetujeme stav po zobrazení
                }
                is UploadResult.Loading -> {
                    Log.d(tag, "updateResult observer: Loading.")
                    /* Progress bar se točí */
                }
                is UploadResult.Idle -> { /* Nic neděláme */ }
            }
        }
    }

    private fun showEditDialog(currentValue: Double) {
        val root = view as? ViewGroup
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_reading, root, false)
        val editText = dialogView.findViewById<EditText>(R.id.dialogValueEditText)

        editText.setText(currentValue.toString())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_reading_value)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val newValueString = editText.text.toString().replace(',', '.') // Nahradíme čárku tečkou pro jistotu
                val newValueDouble = newValueString.toDoubleOrNull()

                if (newValueDouble != null) {
                    viewModel.updateReadingValue(args.readingId, newValueDouble, args.isMasterView)
                } else {
                    Toast.makeText(context, R.string.please_enter_valid_numeric_value, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLockedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.editing_locked)
            .setMessage(R.string.reading_locked_message)
            .setPositiveButton(R.string.i_understand, null)
            .show()
    }

    // Dialog pro potvrzení smazání
    private fun showDeleteConfirmationDialog(reading: Reading) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_reading)
            .setMessage(R.string.delete_reading_confirmation)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                Log.d(tag, "Potvrzeno smazání odečtu: ${reading.id}")
                // Zavoláme metodu ViewModelu pro smazání
                viewModel.deleteReading(reading.id, reading.photoUrl, requireContext())
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d(tag, "onDestroyView: Binding uvolněn.") // Log pro onDestroyView
    }

}