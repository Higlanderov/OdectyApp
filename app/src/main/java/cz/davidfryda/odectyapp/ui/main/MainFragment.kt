package cz.davidfryda.odectyapp.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView // Import pro TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText // Import pro TextInputEditText
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.Meter // Import pro Meter
import cz.davidfryda.odectyapp.databinding.FragmentMainBinding
import cz.davidfryda.odectyapp.ui.user.SaveResult

// Implementujeme nové rozhraní
class MainFragment : Fragment(), MeterInteractionListener {
    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by viewModels()
    private lateinit var meterAdapter: MeterAdapter

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(context, "Oprávnění pro notifikace uděleno.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Oprávnění pro notifikace zamítnuto.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        askNotificationPermission()
        setupRecyclerView() // Nastavení RecyclerView zavolá konstruktor adaptéru

        binding.fabAddMeter.setOnClickListener {
            showAddMeterDialog()
        }

        viewModel.meters.observe(viewLifecycleOwner) { meters ->
            meterAdapter.submitList(meters)
            binding.emptyView.isVisible = meters.isEmpty()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
            // Deaktivujeme FAB, pokud se načítá
            binding.fabAddMeter.isEnabled = !isLoading
        }

        // Sledujeme výsledek přidání
        viewModel.addResult.observe(viewLifecycleOwner) { result ->
            // Zobrazíme/skryjeme progress bar jen pokud jde o Loading stav pro PŘIDÁNÍ
            if (result is SaveResult.Loading) binding.progressBar.isVisible = true
            else if (viewModel.isLoading.value != true) binding.progressBar.isVisible = false // Skryjeme, jen pokud celkově nenačítáme

            binding.fabAddMeter.isEnabled = result !is SaveResult.Loading // FAB tlačítko

            when(result) {
                is SaveResult.Success -> {
                    showSuccessDialog("Měřák úspěšně přidán.")
                }
                is SaveResult.Error -> {
                    Toast.makeText(context, "Chyba při přidávání: ${result.message}", Toast.LENGTH_LONG).show()
                }
                is SaveResult.Loading -> { /* ProgressBar se točí */ }
                is SaveResult.Idle -> { /* Idle */ }
            }
        }

        // NOVÉ: Sledujeme výsledek úpravy
        viewModel.updateResult.observe(viewLifecycleOwner) { result ->
            // Zde můžeme také ukazovat ProgressBar, pokud chceme vizuální odezvu i při úpravě
            // binding.progressBar.isVisible = result is SaveResult.Loading // Odkomentovat pro ProgressBar při úpravě

            when(result) {
                is SaveResult.Success -> {
                    showSuccessDialog("Název měřáku úspěšně upraven.")
                }
                is SaveResult.Error -> {
                    Toast.makeText(context, "Chyba při úpravě: ${result.message}", Toast.LENGTH_LONG).show()
                }
                is SaveResult.Loading -> { /* Loading */ }
                is SaveResult.Idle -> { /* Idle */ }
            }
        }

        // NOVÉ: Sledujeme výsledek smazání
        viewModel.deleteResult.observe(viewLifecycleOwner) { result ->
            // Zde můžeme také ukazovat ProgressBar
            // binding.progressBar.isVisible = result is SaveResult.Loading // Odkomentovat pro ProgressBar při mazání

            when(result) {
                is SaveResult.Success -> {
                    Toast.makeText(context, "Měřák byl úspěšně smazán.", Toast.LENGTH_SHORT).show()
                    // Můžeme přidat Success dialog, pokud chceme výraznější potvrzení
                    // showSuccessDialog("Měřák byl úspěšně smazán.")
                }
                is SaveResult.Error -> {
                    Toast.makeText(context, "Chyba při mazání: ${result.message}", Toast.LENGTH_LONG).show()
                }
                is SaveResult.Loading -> { /* Loading */ }
                is SaveResult.Idle -> { /* Idle */ }
            }
        }
    }

    private fun setupRecyclerView() {
        // Vytvoříme instanci adaptéru a nastavíme listener
        meterAdapter = MeterAdapter()
        meterAdapter.listener = this
        binding.metersRecyclerView.adapter = meterAdapter
    }

    private fun showAddMeterDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_meter, null)
        val editText = dialogView.findViewById<EditText>(R.id.meterNameEditText)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.meterTypeRadioGroup)
        val saveButton = dialogView.findViewById<Button>(R.id.dialogSaveButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.dialogCancelButton)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Přidat nový měřák")
            .setView(dialogView)
            .setCancelable(false) // Aby se nezavřel kliknutím mimo
            .create()

        saveButton.setOnClickListener {
            val name = editText.text.toString().trim()
            val selectedTypeId = radioGroup.checkedRadioButtonId

            if (name.isNotEmpty() && selectedTypeId != -1) {
                val type = when (selectedTypeId) {
                    R.id.radioElectricity -> "Elektřina"
                    R.id.radioGas -> "Plyn"
                    R.id.radioWater -> "Voda"
                    else -> "Obecný" // Mělo by být ošetřeno, ale pro jistotu
                }
                viewModel.addMeter(name, type)
                dialog.dismiss() // Zavřeme dialog po úspěšném pokusu o uložení
            } else {
                Toast.makeText(context, "Vyplňte prosím název a typ měřáku.", Toast.LENGTH_SHORT).show()
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // --- NOVÉ METODY ---

    // Metoda volaná z adaptéru pro zobrazení dialogu úpravy
    override fun onEditMeterClicked(meter: Meter) {
        showEditMeterDialog(meter)
    }

    // Metoda volaná z adaptéru pro zobrazení dialogu smazání
    override fun onDeleteMeterClicked(meter: Meter) {
        showDeleteConfirmationDialog(meter)
    }

    // NOVÉ: Implementace pro Master-detail zobrazení, zde zůstane prázdná
    override fun onAddDescriptionClicked(meter: Meter, ownerUserId: String) {
        // Tato akce je určena pro MasterUserDetailFragment a v MainFragment by neměla nastat.
    }

    // Zobrazí dialog pro úpravu názvu měřáku
    private fun showEditMeterDialog(meter: Meter) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_meter, null)
        // Používáme správné ID z nového layoutu
        val editText = dialogView.findViewById<TextInputEditText>(R.id.editMeterNameEditText)
        val saveButton = dialogView.findViewById<Button>(R.id.dialogSaveButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.dialogCancelButton)

        editText.setText(meter.name) // Předvyplníme aktuální název

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Upravit název měřáku")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        saveButton.setOnClickListener {
            val newName = editText.text.toString().trim()
            if (newName.isNotEmpty() && newName != meter.name) {
                // Zavoláme metodu ViewModelu pro update
                viewModel.updateMeterName(meter.id, newName, requireContext())
                dialog.dismiss()
            } else if (newName.isEmpty()) {
                Toast.makeText(context, "Název měřáku nemůže být prázdný.", Toast.LENGTH_SHORT).show()
            } else {
                // Název se nezměnil, jen zavřeme dialog
                dialog.dismiss()
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // Zobrazí potvrzovací dialog pro smazání
    private fun showDeleteConfirmationDialog(meter: Meter) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Smazat měřák")
            .setMessage("Opravdu chcete smazat měřák '${meter.name}' včetně všech jeho odečtů a fotografií? Tato akce je nevratná.")
            .setNegativeButton("Zrušit", null) // Zavře dialog
            .setPositiveButton("Smazat") { _, _ ->
                // Zavoláme metodu ViewModelu pro smazání
                viewModel.deleteMeter(meter.id, requireContext())
            }
            .show()
    }

    // --- KONEC NOVÝCH METOD ---

    private fun showSuccessDialog(message: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_success, null)
        val messageTextView = dialogView.findViewById<TextView>(R.id.successMessageTextView) // Použijeme TextView
        messageTextView.text = message

        val dialog = MaterialAlertDialogBuilder(requireContext()) // Použijeme MaterialAlertDialogBuilder
            .setView(dialogView)
            .setCancelable(false) // Dialog se nezavře kliknutím mimo
            .create()

        dialog.show()

        // Automatické zavření dialogu po krátké chvíli
        Handler(Looper.getMainLooper()).postDelayed({
            // Zkontrolujeme, zda dialog stále existuje a je zobrazený, než ho zavřeme
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }, 1500) // 1.5 sekundy
    }


    private fun askNotificationPermission() {
        // Můžeme přidat kontrolu pro Android 13 (TIRAMISU) a vyšší
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Můžeme zobrazit dialog vysvětlující, proč povolení potřebujeme,
                // než spustíme žádost, ale pro jednoduchost ji rovnou spustíme.
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // Pro starší verze není třeba explicitní povolení pro notifikace
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
