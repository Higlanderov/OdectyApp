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
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.Meter
import cz.davidfryda.odectyapp.databinding.FragmentLocationDetailBinding
import cz.davidfryda.odectyapp.ui.meter.MeterAdapter

class LocationDetailFragment : Fragment() {

    private var _binding: FragmentLocationDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LocationDetailViewModel by viewModels()
    private val args: LocationDetailFragmentArgs by navArgs()

    // Adapter pro měřáky
    private lateinit var meterAdapter: MeterAdapter

    // ✨ NOVÉ: Target userId
    private var targetUserId: String? = null

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

        // ✨ Určí, čí lokaci načíst
        targetUserId = args.userId ?: Firebase.auth.currentUser?.uid

        if (targetUserId == null) {
            Toast.makeText(context, "Chyba: Uživatel není přihlášen", Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
            return
        }

        setupUI()
        setupObservers()

        // Načti data s userId
        viewModel.loadLocation(targetUserId!!, args.locationId)
        viewModel.loadMeters(targetUserId!!, args.locationId)
    }

    private fun setupUI() {
        // Inicializace adapteru pro měřáky
        meterAdapter = MeterAdapter(
            onMeterClick = { meter ->
                // ✅ Navigace na stávající MeterDetailFragment
                val action = LocationDetailFragmentDirections
                    .actionLocationDetailFragmentToMeterDetailFragment(
                        meterId = meter.id,
                        userId = targetUserId
                    )
                findNavController().navigate(action)
            },
            onEditClick = { meter ->
                val action = LocationDetailFragmentDirections
                    .actionLocationDetailFragmentToEditMeterFragment(
                        meterId = meter.id,
                        userId = targetUserId
                    )
                findNavController().navigate(action)
            },
            onDeleteClick = { meter ->
                showDeleteMeterDialog(meter)
            }
        )

        // Nastavení RecyclerView
        binding.metersRecyclerView.apply {
            adapter = meterAdapter
            layoutManager = LinearLayoutManager(context)
        }

        // Tlačítko Upravit lokaci
        binding.editButton.setOnClickListener {
            val action = LocationDetailFragmentDirections
                .actionLocationDetailFragmentToEditLocationFragment(
                    locationId = args.locationId,
                    userId = targetUserId
                )
            findNavController().navigate(action)
        }

        // Tlačítko Smazat lokaci
        binding.deleteButton.setOnClickListener {
            showDeleteLocationDialog()
        }

        // FAB - Přidat měřák
        binding.fabAddMeter.setOnClickListener {
            val action = LocationDetailFragmentDirections
                .actionLocationDetailFragmentToAddMeterFragment(
                    preselectedLocationId = args.locationId,
                    userId = targetUserId
                )
            findNavController().navigate(action)
        }
    }

    private fun setupObservers() {
        viewModel.location.observe(viewLifecycleOwner) { location ->
            if (location != null) {
                // Zobraz data lokace
                binding.locationNameTextView.text = location.name
                binding.locationAddressTextView.text = location.address

                // Poznámka (zobraz jen pokud není prázdná)
                if (location.note.isNotBlank()) {
                    binding.locationNoteTextView.text = location.note
                    binding.noteLayout.isVisible = true
                } else {
                    binding.noteLayout.isVisible = false
                }

                // Výchozí chip
                binding.defaultChip.isVisible = location.isDefault

                // Zobraz content, skryj loading
                binding.loadingLayout.isVisible = false
                binding.contentLayout.isVisible = true
            } else {
                // Chyba načítání
                Toast.makeText(
                    context,
                    R.string.error_loading_location,
                    Toast.LENGTH_LONG
                ).show()
                findNavController().navigateUp()
            }
        }

        viewModel.meters.observe(viewLifecycleOwner) { meters ->
            // Aktualizuj počet měřáků
            binding.meterCountTextView.text = meters.size.toString()

            // Empty state
            val isEmpty = meters.isEmpty()
            binding.emptyMetersLayout.isVisible = isEmpty
            binding.metersRecyclerView.isVisible = !isEmpty

            // Předej data do adapteru
            meterAdapter.submitList(meters)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Zobraz loading jen při prvním načítání
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