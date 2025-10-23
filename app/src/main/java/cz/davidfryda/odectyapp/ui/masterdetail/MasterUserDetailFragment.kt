package cz.davidfryda.odectyapp.ui.masterdetail

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log // Import pro logování
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog // Použijeme AppCompat AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder // Pro konzistentní vzhled dialogů
import com.google.android.material.textfield.TextInputEditText
import cz.davidfryda.odectyapp.R // Import R pro přístup k resources
import cz.davidfryda.odectyapp.data.Meter
import cz.davidfryda.odectyapp.databinding.FragmentMasterUserDetailBinding
import cz.davidfryda.odectyapp.ui.main.MeterAdapter
import cz.davidfryda.odectyapp.ui.main.MeterInteractionListener
import cz.davidfryda.odectyapp.ui.master.MasterUserDetailViewModel
import cz.davidfryda.odectyapp.ui.user.SaveResult // Import SaveResult

/**
 * Fragment zobrazující seznam měřáků pro konkrétního uživatele v režimu správce.
 * Umožňuje správci přidávat/upravovat popisy k měřákům.
 */
class MasterUserDetailFragment : Fragment(), MeterInteractionListener {
    private var _binding: FragmentMasterUserDetailBinding? = null
    // Tato vlastnost je platná pouze mezi onCreateView a onDestroyView.
    private val binding get() = _binding!!

    private val args: MasterUserDetailFragmentArgs by navArgs() // Navigační argumenty (userId)
    private val viewModel: MasterUserDetailViewModel by viewModels() // ViewModel pro tento fragment
    private lateinit var meterAdapter: MeterAdapter // Adaptér pro RecyclerView
    private var successDialog: AlertDialog? = null // Reference na dialog úspěchu
    private val handler = Handler(Looper.getMainLooper()) // Handler pro odložení zavření dialogu
    private val tag = "MasterUserDetailFrag" // Tag pro logování

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMasterUserDetailBinding.inflate(inflater, container, false)
        Log.d(tag, "onCreateView called for user: ${args.userId}")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(tag, "onViewCreated called")
        setupRecyclerView()

        // Zobrazíme progress bar na začátku, než se data načtou
        binding.progressBar.isVisible = true
        binding.emptyView.isVisible = false // Skryjeme empty view na začátku
        // Spustíme načítání měřáků, což spustí listener ve ViewModelu
        viewModel.fetchMetersForUser(args.userId)

        // Sledujeme seznam měřáků z ViewModelu
        viewModel.meters.observe(viewLifecycleOwner) { meters ->
            Log.d(tag, "Meters LiveData updated with ${meters.size} items.")
            binding.progressBar.isVisible = false // Skryjeme progress bar po načtení/aktualizaci
            meterAdapter.submitList(meters) // Aktualizujeme adaptér novým seznamem
            binding.emptyView.isVisible = meters.isEmpty() // Zobrazíme text pro prázdný stav, pokud je seznam prázdný
            Log.d(tag, "RecyclerView updated. Empty view visible: ${meters.isEmpty()}")
        }

        // Sledujeme výsledek uložení popisu
        viewModel.saveDescriptionResult.observe(viewLifecycleOwner) { result ->
            Log.d(tag, "saveDescriptionResult observed: $result")
            // Zobrazíme progress bar POUZE během stavu Loading
            binding.progressBar.isVisible = result is SaveResult.Loading

            // Reagujeme pouze na relevantní stavy (Success, Error)
            when (result) {
                is SaveResult.Success -> {
                    // Zobrazíme dialog úspěchu
                    Log.d(tag, "SaveResult.Success received.")
                    // Použijeme string resource
                    showSuccessDialog(getString(R.string.save_description_success))
                    // Resetujeme stav AŽ PO ZOBRAZENÍ dialogu
                    viewModel.resetSaveDescriptionResult()
                }
                is SaveResult.Error -> {
                    // Zobrazíme chybovou hlášku
                    Log.e(tag, "SaveResult.Error received: ${result.message}")
                    // Použijeme string resource pro formátování
                    Toast.makeText(context, getString(R.string.save_description_error, result.message), Toast.LENGTH_LONG).show()
                    // Resetujeme stav AŽ PO ZOBRAZENÍ Toastu
                    viewModel.resetSaveDescriptionResult()
                }
                is SaveResult.Loading -> {
                    Log.d(tag, "SaveResult.Loading received.")
                    // ProgressBar je již řízen výše
                }
                is SaveResult.Idle -> {
                    Log.d(tag, "SaveResult.Idle received.")
                    // Zajistíme, že dialog úspěchu je zavřený, když se stav resetuje na Idle
                    dismissSuccessDialog()
                }
            }
        }
    }

    /**
     * Nastaví RecyclerView s MeterAdapter.
     */
    private fun setupRecyclerView() {
        Log.d(tag, "setupRecyclerView called")
        meterAdapter = MeterAdapter()
        meterAdapter.ownerId = args.userId // Nastavíme ID vlastníka pro chování master režimu v adaptéru
        meterAdapter.listener = this // Nastavíme tento fragment jako listener pro interakce adaptéru
        binding.usersRecyclerView.adapter = meterAdapter // Nastavíme adaptér pro RecyclerView
    }

    // --- Implementace MeterInteractionListener ---

    /** V Master režimu se nepoužívá. */
    override fun onEditMeterClicked(meter: Meter) {
        Log.d(tag, "onEditMeterClicked called (no action in master mode)")
        /* V master režimu není potřeba žádná akce */
        Toast.makeText(context, "Úprava názvu je dostupná pouze pro vlastní měřáky.", Toast.LENGTH_SHORT).show()
    }

    /** V Master režimu se nepoužívá. */
    override fun onDeleteMeterClicked(meter: Meter) {
        Log.d(tag, "onDeleteMeterClicked called (no action in master mode)")
        /* V master režimu není potřeba žádná akce */
        Toast.makeText(context, "Mazání měřáku je dostupné pouze pro vlastní měřáky.", Toast.LENGTH_SHORT).show()
    }

    /** Voláno, když master klikne na možnost 'Přidat/Upravit popis' z menu měřáku. */
    override fun onAddDescriptionClicked(meter: Meter, ownerUserId: String) {
        Log.d(tag, "onAddDescriptionClicked called for meter: ${meter.id}")
        showAddDescriptionDialog(meter, ownerUserId)
    }
    // --- Konec implementace MeterInteractionListener ---

    /**
     * Zobrazí dialog pro přidání nebo úpravu popisu měřáku správcem.
     * @param meter Měřák, který se upravuje.
     * @param ownerUserId ID uživatele, kterému měřák patří.
     */
    private fun showAddDescriptionDialog(meter: Meter, ownerUserId: String) {
        // Použijeme bezpečný přístup ke kontextu
        val currentContext = context ?: run {
            Log.e(tag, "showAddDescriptionDialog: Context is null!")
            return
        }
        Log.d(tag, "Showing add description dialog for meter: ${meter.id}")

        val dialogView = LayoutInflater.from(currentContext).inflate(R.layout.dialog_add_description, null)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.editDescriptionEditText)
        val saveButton = dialogView.findViewById<Button>(R.id.dialogSaveButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.dialogCancelButton)

        // Předvyplníme EditText existujícím popisem, pokud existuje
        editText.setText(meter.masterDescription ?: "")

        val dialog = MaterialAlertDialogBuilder(currentContext)
            // Použijeme string resource pro titulek
            .setTitle(getString(R.string.add_description_dialog_title, meter.name))
            .setView(dialogView)
            .setCancelable(false) // Zabránění zavření kliknutím mimo
            .create()

        // Akce tlačítka Uložit
        saveButton.setOnClickListener {
            val description = editText.text.toString().trim() // Trim pro odstranění bílých znaků
            Log.d(tag, "Save button clicked. Description: '$description'")
            // Zavoláme ViewModel pro uložení
            viewModel.saveMasterDescription(ownerUserId, meter.id, description)
            dialog.dismiss() // Zavřeme dialog
        }

        // Akce tlačítka Zrušit
        cancelButton.setOnClickListener {
            Log.d(tag, "Cancel button clicked.")
            dialog.dismiss() // Zavřeme dialog
        }

        dialog.show()
    }

    /**
     * Zobrazí dočasný dialog o úspěchu.
     * @param message Zpráva k zobrazení.
     */
    private fun showSuccessDialog(message: String) {
        // Použijeme bezpečný přístup ke kontextu
        val currentContext = context ?: run {
            Log.e(tag, "showSuccessDialog: Context is null!")
            return
        }

        dismissSuccessDialog() // Nejdříve zavřeme předchozí dialog, pokud existuje
        Log.d(tag, "Showing success dialog with message: $message")

        val dialogView = LayoutInflater.from(currentContext).inflate(R.layout.dialog_success, null)
        val messageTextView = dialogView.findViewById<TextView>(R.id.successMessageTextView)
        messageTextView.text = message

        // Použijeme standardní AlertDialog, který uložíme do successDialog
        successDialog = MaterialAlertDialogBuilder(currentContext)
            .setView(dialogView)
            .setCancelable(false) // Zabránění zavření kliknutím mimo
            .create()

        try {
            successDialog?.show() // Zobrazíme dialog
            // Automaticky zavřeme dialog po zpoždění
            handler.removeCallbacksAndMessages(null) // Zrušíme předchozí čekání, pokud existuje
            handler.postDelayed(::dismissSuccessDialog, 1500) // Zavoláme metodu pro bezpečné zavření
        } catch (e: Exception) {
            Log.e(tag, "Error showing success dialog: ${e.message}")
        }
    }

    /**
     * Bezpečně zavře dialog úspěchu, pokud je zobrazený.
     * Volá se buď po uplynutí času, nebo při onDestroyView.
     */
    private fun dismissSuccessDialog() {
        // Kontrola, zda fragment stále existuje (je připojen k aktivitě) a dialog je zobrazený
        if (successDialog?.isShowing == true && isAdded) {
            try {
                Log.d(tag, "Dismissing success dialog.")
                successDialog?.dismiss()
            } catch (e: Exception) { // Zachycení obecnější výjimky pro robustnost
                // Může nastat chyba, pokud se view mezitím zničí nebo kontext není platný
                Log.w(tag, "Error dismissing success dialog (might be expected during rapid navigation): ${e.message}")
            } finally {
                successDialog = null // Uvolníme referenci VŽDY
            }
        } else if (successDialog != null) {
            // Pokud dialog existuje, ale není zobrazený nebo fragment není připojen, jen uvolníme referenci
            Log.d(tag, "Success dialog exists but not dismissing (showing=${successDialog?.isShowing}, isAdded=$isAdded). Setting reference to null.")
            successDialog = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(tag, "onDestroyView called")
        handler.removeCallbacksAndMessages(null) // Odstraníme čekající callbacky (např. zavření dialogu)
        dismissSuccessDialog() // Zajistíme zavření dialogu, pokud ještě běží
        meterAdapter.listener = null // Důležité: Uvolnit referenci na listener v adaptéru
        _binding = null // Uvolnit referenci na view binding
        Log.d(tag, "onDestroyView completed.")
    }
}
