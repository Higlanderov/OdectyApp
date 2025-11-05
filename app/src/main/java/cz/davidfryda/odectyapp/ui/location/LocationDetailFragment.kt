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
import cz.davidfryda.odectyapp.ui.main.MeterAdapter
import cz.davidfryda.odectyapp.ui.main.MeterInteractionListener

class LocationDetailFragment : Fragment(), MeterInteractionListener {

    private var _binding: FragmentLocationDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LocationDetailViewModel by viewModels()
    private val args: LocationDetailFragmentArgs by navArgs()

    private lateinit var meterAdapter: MeterAdapter
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

        targetUserId = args.userId ?: Firebase.auth.currentUser?.uid

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
        meterAdapter = MeterAdapter().apply {
            listener = this@LocationDetailFragment
            currentLocationId = args.locationId
            ownerId = if (targetUserId != Firebase.auth.currentUser?.uid) targetUserId else null
        }

        binding.metersRecyclerView.apply {
            adapter = meterAdapter
            layoutManager = LinearLayoutManager(context)
        }

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

    override fun onEditMeterClicked(meter: Meter) {
        val action = LocationDetailFragmentDirections
            .actionLocationDetailFragmentToEditMeterFragment(
                meterId = meter.id,
                locationId = args.locationId,
                userId = targetUserId
            )
        findNavController().navigate(action)
    }

    override fun onDeleteMeterClicked(meter: Meter) {
        showDeleteMeterDialog(meter)
    }

    override fun onAddDescriptionClicked(meter: Meter, ownerUserId: String) {
        Toast.makeText(context, "Přidání popisu není dostupné", Toast.LENGTH_SHORT).show()
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
