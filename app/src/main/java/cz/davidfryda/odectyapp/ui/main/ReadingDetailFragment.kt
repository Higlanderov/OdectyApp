package cz.davidfryda.odectyapp.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.databinding.FragmentReadingDetailBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ReadingDetailFragment : Fragment() {
    private var _binding: FragmentReadingDetailBinding? = null
    private val binding get() = _binding!!
    private val args: ReadingDetailFragmentArgs by navArgs()
    private val viewModel: MeterDetailViewModel by viewModels()
    private val dateFormat = SimpleDateFormat("dd. MM. yyyy HH:mm", Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReadingDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loadSingleReading(args.readingId)

        viewModel.singleReading.observe(viewLifecycleOwner) { reading ->
            val unit = when (args.meterType) {
                "Elektřina" -> "kWh"
                "Plyn" -> "m³"
                "Voda" -> "m³"
                else -> ""
            }
            binding.readingValue.text = "${reading.finalValue} $unit"
            binding.readingDate.text = reading.timestamp?.let { dateFormat.format(it) } ?: "N/A"

            // --- NOVÁ VYLEPŠENÁ LOGIKA PRO NAČÍTÁNÍ OBRÁZKU ---

            // 1. Vytvoříme si instanci animace točícího se kolečka
            val circularProgressDrawable = CircularProgressDrawable(requireContext())
            circularProgressDrawable.strokeWidth = 5f  // Tloušťka čáry
            circularProgressDrawable.centerRadius = 30f // Velikost kolečka
            circularProgressDrawable.start() // Spustíme animaci

            // 2. Použijeme tuto animaci jako placeholder v knihovně Coil
            binding.readingPhoto.load(reading.photoUrl) {
                crossfade(true) // Plynulý přechod
                placeholder(circularProgressDrawable) // Zobrazí se, zatímco se fotka stahuje
                error(R.drawable.ic_error) // Zobrazí se, pokud se fotku nepodaří načíst
            }

            if (reading.editedByAdmin) {
                binding.editButton.text = "Upraveno správcem"
                binding.editButton.setIconResource(R.drawable.ic_admin)
                binding.editButton.isEnabled = args.isMasterView
            } else {
                binding.editButton.text = "Upravit hodnotu"
                binding.editButton.isEnabled = true
                binding.editButton.icon = null
            }
        }

        binding.editButton.setOnClickListener {
            viewModel.singleReading.value?.let { currentReading ->
                if (currentReading.editedByAdmin && !args.isMasterView) {
                    showLockedDialog()
                } else {
                    showEditDialog(currentReading.finalValue ?: 0.0)
                }
            }
        }

        viewModel.updateResult.observe(viewLifecycleOwner) { result ->
            when(result) {
                is UploadResult.Success -> {
                    Toast.makeText(context, "Hodnota úspěšně aktualizována.", Toast.LENGTH_SHORT).show()
                }
                is UploadResult.Error -> {
                    Toast.makeText(context, "Chyba: ${result.message}", Toast.LENGTH_LONG).show()
                }
                is UploadResult.Loading -> { /* Můžeme zobrazit progress bar */ }
            }
        }
    }

    private fun showEditDialog(currentValue: Double) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_reading, null)
        val editText = dialogView.findViewById<EditText>(R.id.dialogValueEditText)

        editText.setText(currentValue.toString())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Upravit hodnotu odečtu")
            .setView(dialogView)
            .setPositiveButton("Uložit") { _, _ ->
                val newValueString = editText.text.toString()
                val newValueDouble = newValueString.toDoubleOrNull()

                if (newValueDouble != null) {
                    viewModel.updateReadingValue(args.readingId, newValueDouble, args.isMasterView)
                } else {
                    Toast.makeText(context, "Prosím, zadejte platnou číselnou hodnotu.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    private fun showLockedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Úprava Uzamčena")
            .setMessage("Tento odečet byl již zkontrolován a upraven správcem. Další změny nejsou možné.")
            .setPositiveButton("Rozumím", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}