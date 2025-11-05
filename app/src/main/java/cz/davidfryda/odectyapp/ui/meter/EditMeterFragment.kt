package cz.davidfryda.odectyapp.ui.meter

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.Location
import cz.davidfryda.odectyapp.databinding.FragmentEditMeterBinding

class EditMeterFragment : Fragment() {

    private var _binding: FragmentEditMeterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditMeterViewModel by viewModels()
    private val args: EditMeterFragmentArgs by navArgs()

    private val locationsList = mutableListOf<Location>()
    private val locationsMap = mutableMapOf<String, String>() // name -> id
    private var selectedLocationId: String? = null
    private var targetUserId: String? = null

    private val meterTypes = listOf(
        "Elektřina",
        "Plyn",
        "Voda studená",
        "Voda teplá",
        "Teplo"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditMeterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        targetUserId = args.userId ?: Firebase.auth.currentUser?.uid

        if (targetUserId == null) {
            Toast.makeText(context, "Chyba: Nelze identifikovat uživatele", Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
            return
        }

        setupMeterTypeSpinner()
        setupUI()
        setupObservers()

        viewModel.loadMeter(targetUserId!!, args.meterId)
        viewModel.loadLocations(targetUserId!!)
    }

    private fun setupMeterTypeSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            meterTypes
        )
        binding.meterTypeSpinner.setAdapter(adapter)

        binding.meterTypeSpinner.setOnClickListener {
            binding.meterTypeSpinner.showDropDown()
        }
    }

    private fun setupUI() {
        binding.meterNameEditText.doAfterTextChanged {
            binding.meterNameLayout.error = null
        }

        binding.cancelButton.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.saveButton.setOnClickListener {
            saveMeter()
        }
    }

    private fun setupObservers() {
        viewModel.meter.observe(viewLifecycleOwner) { meter ->
            if (meter != null) {
                binding.meterNameEditText.setText(meter.name)
                binding.meterTypeSpinner.setText(meter.type, false)
                selectedLocationId = meter.locationId
                updateLocationSpinnerSelection(meter.locationId)

                binding.loadingLayout.isVisible = false
                binding.contentLayout.isVisible = true
            } else {
                Toast.makeText(
                    context,
                    R.string.error_loading_meter,
                    Toast.LENGTH_LONG
                ).show()
                findNavController().navigateUp()
            }
        }

        viewModel.locations.observe(viewLifecycleOwner) { locations ->
            if (locations == null) {
                return@observe
            }

            locationsList.clear()
            locationsMap.clear()

            if (locations.isEmpty()) {
                Toast.makeText(
                    context,
                    R.string.no_locations_create_first,
                    Toast.LENGTH_LONG
                ).show()
                findNavController().navigateUp()
                return@observe
            }

            val names = mutableListOf<String>()
            for (location in locations) {
                locationsList.add(location)
                locationsMap[location.name] = location.id
                names.add(location.name)
            }

            Log.d("EditMeterFragment", "Načteno ${locations.size} lokací: $names")

            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                names
            )
            binding.locationSpinner.setAdapter(adapter)

            binding.locationSpinner.setOnClickListener {
                binding.locationSpinner.showDropDown()
            }

            binding.locationSpinner.setOnItemClickListener { _, _, position, _ ->
                val selectedLocation = locationsList[position]
                selectedLocationId = selectedLocation.id
                binding.locationSpinnerLayout.error = null
                Log.d("EditMeterFragment", "Vybrána lokace: ${selectedLocation.name}")
            }

            val currentMeter = viewModel.meter.value
            if (currentMeter != null) {
                updateLocationSpinnerSelection(currentMeter.locationId)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
            binding.saveButton.isEnabled = !isLoading
            binding.cancelButton.isEnabled = !isLoading

            if (binding.contentLayout.isVisible) {
                binding.locationSpinner.isEnabled = !isLoading
                binding.meterNameEditText.isEnabled = !isLoading
                binding.meterTypeSpinner.isEnabled = !isLoading
            }
        }

        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is EditMeterViewModel.SaveResult.Success -> {
                    Toast.makeText(
                        context,
                        R.string.meter_updated_successfully,
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.resetSaveResult()
                    findNavController().navigateUp()
                }
                is EditMeterViewModel.SaveResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    viewModel.resetSaveResult()
                }
                else -> {}
            }
        }
    }

    private fun updateLocationSpinnerSelection(locationId: String) {
        val currentLocation = locationsList.find { it.id == locationId }
        if (currentLocation != null) {
            binding.locationSpinner.setText(currentLocation.name, false)
        }
    }

    private fun saveMeter() {
        val locationName = binding.locationSpinner.text.toString().trim()
        val newLocationId = locationsMap[locationName] ?: selectedLocationId

        val name = binding.meterNameEditText.text.toString().trim()
        val type = binding.meterTypeSpinner.text.toString().trim()

        var hasError = false

        if (newLocationId.isNullOrBlank()) {
            binding.locationSpinnerLayout.error = getString(R.string.location_required)
            hasError = true
        } else {
            binding.locationSpinnerLayout.error = null
        }

        if (name.isBlank()) {
            binding.meterNameLayout.error = getString(R.string.meter_name_required)
            hasError = true
        }

        if (type.isBlank()) {
            binding.meterTypeLayout.error = getString(R.string.meter_type_required)
            hasError = true
        }

        if (hasError) {
            return
        }

        if (newLocationId == null) {
            Toast.makeText(context, R.string.location_required, Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("EditMeterFragment", "Aktualizuji měřák: meterId=${args.meterId}, newLocationId=$newLocationId, name=$name, type=$type")

        viewModel.updateMeter(
            userId = targetUserId!!,
            meterId = args.meterId,
            newLocationId = newLocationId,
            name = name,
            type = type
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}