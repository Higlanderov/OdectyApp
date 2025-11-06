package cz.davidfryda.odectyapp.ui.location

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.Meter
import cz.davidfryda.odectyapp.databinding.DialogAddDescriptionBinding
import cz.davidfryda.odectyapp.databinding.FragmentLocationDetailBinding
import cz.davidfryda.odectyapp.ui.main.MeterAdapter
import cz.davidfryda.odectyapp.ui.main.MeterInteractionListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LocationDetailFragment : Fragment(), MeterInteractionListener {

    private var _binding: FragmentLocationDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LocationDetailViewModel by viewModels()
    private val args: LocationDetailFragmentArgs by navArgs()

    private lateinit var meterAdapter: MeterAdapter
    private var targetUserId: String? = null
    private var isMasterMode: Boolean = false
    private val db = Firebase.firestore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocationDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Určení target userId - buď z argumentů nebo current user
        targetUserId = args.userId ?: Firebase.auth.currentUser?.uid
        val currentUserId = Firebase.auth.currentUser?.uid

        // ✨ DŮLEŽITÉ: Určení, zda jsme v master režimu
        // Master režim = díváme se na data jiného uživatele
        isMasterMode = targetUserId != null && targetUserId != currentUserId

        if (targetUserId == null) {
            Toast.makeText(context, "Chyba: Uživatel není přihlášen", Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
            return
        }

        setupUI()
        setupObservers()

        viewModel.loadLocation(targetUserId!!, args.locationId)
        viewModel.loadMeters(targetUserId!!, args.locationId)
    }

    private fun setupUI() {
        // ✨ OPRAVA: Správné nastavení parametrů pro MeterAdapter
        meterAdapter = MeterAdapter().apply {
            listener = this@LocationDetailFragment
            currentLocationId = args.locationId

            // Klíčové nastavení pro správné zobrazení:
            if (isMasterMode) {
                // Master se dívá na cizí měřáky
                ownerId = targetUserId  // ID vlastníka měřáků
                isMasterOwnProfile = false  // NENÍ to masterův vlastní profil
            } else {
                // Běžný uživatel se dívá na své měřáky
                ownerId = null  // Null znamená běžný uživatelský režim
                isMasterOwnProfile = false
            }
        }

        binding.metersRecyclerView.apply {
            adapter = meterAdapter
            layoutManager = LinearLayoutManager(context)
        }

        // ✨ Skrýt editační tlačítka v master režimu (když se díváme na cizí lokaci)
        binding.editButton.isVisible = !isMasterMode
        binding.deleteButton.isVisible = !isMasterMode
        binding.fabAddMeter.isVisible = !isMasterMode

        if (!isMasterMode) {
            binding.editButton.setOnClickListener {
                val action = LocationDetailFragmentDirections
                    .actionLocationDetailFragmentToEditLocationFragment(
                        locationId = args.locationId,
                        userId = targetUserId
                    )
                findNavController().navigate(action)
            }

            binding.deleteButton.setOnClickListener {
                showDeleteLocationDialog()
            }

            binding.fabAddMeter.setOnClickListener {
                val action = LocationDetailFragmentDirections
                    .actionLocationDetailFragmentToAddMeterFragment(
                        preselectedLocationId = args.locationId,
                        userId = targetUserId
                    )
                findNavController().navigate(action)
            }
        }
    }

    override fun onEditMeterClicked(meter: Meter) {
        // ✨ Editace pouze pro vlastní měřáky
        if (!isMasterMode) {
            val action = LocationDetailFragmentDirections
                .actionLocationDetailFragmentToEditMeterFragment(
                    meterId = meter.id,
                    locationId = args.locationId,
                    userId = targetUserId
                )
            findNavController().navigate(action)
        } else {
            Toast.makeText(context, R.string.cannot_edit_in_master_mode, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDeleteMeterClicked(meter: Meter) {
        // ✨ Mazání pouze pro vlastní měřáky
        if (!isMasterMode) {
            showDeleteMeterDialog(meter)
        } else {
            Toast.makeText(context, R.string.cannot_delete_in_master_mode, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAddDescriptionClicked(meter: Meter, ownerUserId: String) {
        // ✨ NOVÁ IMPLEMENTACE: Zobrazit dialog pro přidání/editaci master popisu
        if (isMasterMode) {
            showAddDescriptionDialog(meter, ownerUserId)
        }
    }

    // ✨ NOVÁ METODA: Dialog pro přidání master popisu
    private fun showAddDescriptionDialog(meter: Meter, ownerUserId: String) {
        val dialogBinding = DialogAddDescriptionBinding.inflate(layoutInflater)

        // Předvyplnit existující popis, pokud existuje
        dialogBinding.editDescriptionEditText.setText(meter.masterDescription ?: "")

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.add_description_dialog_title, meter.name))
            .setView(dialogBinding.root)
            .create()

        dialogBinding.dialogSaveButton.setOnClickListener {
            val newDescription = dialogBinding.editDescriptionEditText.text.toString().trim()

            // Uložit popis do Firestore
            saveMasterDescription(meter.id, ownerUserId, newDescription) { success ->
                if (success) {
                    Toast.makeText(context, R.string.save_description_success, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    // Znovu načíst měřáky pro zobrazení aktualizovaného popisu
                    viewModel.loadMeters(ownerUserId, args.locationId)
                } else {
                    Toast.makeText(context, getString(R.string.save_description_error, "Neznámá chyba"), Toast.LENGTH_LONG).show()
                }
            }
        }

        dialogBinding.dialogCancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // ✨ NOVÁ METODA: Uložení master popisu do Firestore
    private fun saveMasterDescription(meterId: String, userId: String, description: String, callback: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Aktualizovat masterDescription v dokumentu měřáku
                db.collection("users")
                    .document(userId)
                    .collection("meters")
                    .document(meterId)
                    .update("masterDescription", description.ifEmpty { null })
                    .await()

                CoroutineScope(Dispatchers.Main).launch {
                    callback(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                CoroutineScope(Dispatchers.Main).launch {
                    callback(false)
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.location.observe(viewLifecycleOwner) { location ->
            if (location != null) {
                binding.locationNameTextView.text = location.name
                binding.locationAddressTextView.text = location.address

                if (location.note.isNotBlank()) {
                    binding.locationNoteTextView.text = location.note
                    binding.noteLayout.isVisible = true
                } else {
                    binding.noteLayout.isVisible = false
                }

                binding.defaultChip.isVisible = location.isDefault

                binding.loadingLayout.isVisible = false
                binding.contentLayout.isVisible = true
            } else {
                Toast.makeText(
                    context,
                    R.string.error_loading_location,
                    Toast.LENGTH_LONG
                ).show()
                findNavController().navigateUp()
            }
        }

        viewModel.meters.observe(viewLifecycleOwner) { meters ->
            binding.meterCountTextView.text = meters.size.toString()

            val isEmpty = meters.isEmpty()
            binding.emptyMetersLayout.isVisible = isEmpty
            binding.metersRecyclerView.isVisible = !isEmpty

            meterAdapter.submitList(meters)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (viewModel.location.value == null) {
                binding.loadingLayout.isVisible = isLoading
                binding.contentLayout.isVisible = !isLoading
            }
        }

        viewModel.deleteResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is LocationDetailViewModel.DeleteResult.Success -> {
                    Toast.makeText(
                        context,
                        R.string.location_deleted,
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.resetDeleteResult()
                    findNavController().navigateUp()
                }
                is LocationDetailViewModel.DeleteResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    viewModel.resetDeleteResult()
                }
                else -> {}
            }
        }

        viewModel.deleteMeterResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is LocationDetailViewModel.DeleteMeterResult.Success -> {
                    Toast.makeText(
                        context,
                        R.string.meter_deleted,
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.resetDeleteMeterResult()
                }
                is LocationDetailViewModel.DeleteMeterResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    viewModel.resetDeleteMeterResult()
                }
                else -> {}
            }
        }
    }

    private fun showDeleteLocationDialog() {
        val locationName = viewModel.location.value?.name ?: ""
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_location_title)
            .setMessage(getString(R.string.delete_location_message, locationName))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteLocation(targetUserId!!, args.locationId)
            }
            .show()
    }

    private fun showDeleteMeterDialog(meter: Meter) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_meter_title)
            .setMessage(getString(R.string.delete_meter_message, meter.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteMeter(targetUserId!!, meter.id)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}