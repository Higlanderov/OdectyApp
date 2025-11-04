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
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.Location
import cz.davidfryda.odectyapp.databinding.FragmentAddMeterBinding

class AddMeterFragment : Fragment() {

    private var _binding: FragmentAddMeterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddMeterViewModel by viewModels()
    private val args: AddMeterFragmentArgs by navArgs()

    private val locationsList = mutableListOf<Location>()
    private val locationsMap = mutableMapOf<String, String>() // name -> id
    private var selectedLocationId: String? = null

    // Typy měřáků
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
        _binding = FragmentAddMeterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("AddMeterFragment", "🚀 onViewCreated STARTED")
        Log.d("AddMeterFragment", "preselectedLocationId = ${args.preselectedLocationId}")

        setupMeterTypeSpinner()
        setupUI()
        setupObservers()

        // Načti lokace
        Log.d("AddMeterFragment", "📥 Volám viewModel.loadLocations()")
        viewModel.loadLocations()
    }

    private fun setupMeterTypeSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            meterTypes
        )
        binding.meterTypeSpinner.setAdapter(adapter)

        // Nastav výchozí hodnotu
        binding.meterTypeSpinner.setText(meterTypes[0], false)

        // Listener pro otevření dropdownu
        binding.meterTypeSpinner.setOnClickListener {
            binding.meterTypeSpinner.showDropDown()
        }
    }

    private fun setupUI() {
        // Clear error při psaní
        binding.meterNameEditText.doAfterTextChanged {
            binding.meterNameLayout.error = null
        }

        // Tlačítko Zrušit
        binding.cancelButton.setOnClickListener {
            findNavController().navigateUp()
        }

        // Tlačítko Uložit
        binding.saveButton.setOnClickListener {
            saveMeter()
        }
    }

    private fun setupObservers() {
        viewModel.locations.observe(viewLifecycleOwner) { locations ->
            // ✨ KRITICKÝ DEBUG LOG
            Log.d("AddMeterFragment", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d("AddMeterFragment", "Observer TRIGGERED")
            Log.d("AddMeterFragment", "locations = $locations")
            Log.d("AddMeterFragment", "locations?.size = ${locations?.size}")
            Log.d("AddMeterFragment", "isLoading = ${viewModel.isLoading.value}")
            Log.d("AddMeterFragment", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            // Pokud je null, data se ještě nenačetla
            if (locations == null) {
                Log.d("AddMeterFragment", "❌ Locations je NULL - přeskakuji")
                return@observe
            }

            locationsList.clear()
            locationsMap.clear()

            if (locations.isEmpty()) {
                Log.d("AddMeterFragment", "❌ Locations je PRÁZDNÝ seznam")
                // Žádné lokace - zobraz chybu
                Toast.makeText(
                    context,
                    R.string.no_locations_create_first,
                    Toast.LENGTH_LONG
                ).show()
                findNavController().navigateUp()
                return@observe
            }

            // Naplň data
            val names = mutableListOf<String>()
            for (location in locations) {
                locationsList.add(location)
                locationsMap[location.name] = location.id
                names.add(location.name)
                Log.d("AddMeterFragment", "  ➕ Lokace: ${location.name} (${location.id})")
            }

            Log.d("AddMeterFragment", "✅ Načteno ${locations.size} lokací")
            Log.d("AddMeterFragment", "✅ Názvy: $names")

            // Nastav adapter pro spinner
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                names
            )
            binding.locationSpinner.setAdapter(adapter)

            // Přidej listener pro otevření dropdownu při kliknutí
            binding.locationSpinner.setOnClickListener {
                binding.locationSpinner.showDropDown()
            }

            // Listener pro výběr položky
            binding.locationSpinner.setOnItemClickListener { _, _, position, _ ->
                val selectedLocation = locationsList[position]
                selectedLocationId = selectedLocation.id
                binding.locationSpinnerLayout.error = null
                Log.d("AddMeterFragment", "Vybrána lokace: ${selectedLocation.name} (${selectedLocation.id})")
            }

            // Předvybrat lokaci
            preselectLocation(locations)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
            binding.saveButton.isEnabled = !isLoading
            binding.cancelButton.isEnabled = !isLoading

            // Disable input fields during loading
            binding.locationSpinner.isEnabled = !isLoading
            binding.meterNameEditText.isEnabled = !isLoading
            binding.meterTypeSpinner.isEnabled = !isLoading
        }

        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is AddMeterViewModel.SaveResult.Success -> {
                    Toast.makeText(
                        context,
                        R.string.meter_added_successfully,
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.resetSaveResult()
                    findNavController().navigateUp()
                }
                is AddMeterViewModel.SaveResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    viewModel.resetSaveResult()
                }
                else -> {}
            }
        }
    }

    private fun preselectLocation(locations: List<Location>) {
        // 1. Pokud přicházíme z LocationDetailFragment, předvyber tu lokaci
        val preselectedId = args.preselectedLocationId
        if (!preselectedId.isNullOrBlank()) {
            val location = locations.find { it.id == preselectedId }
            if (location != null) {
                binding.locationSpinner.setText(location.name, false)
                selectedLocationId = location.id
                Log.d("AddMeterFragment", "Předvybrána lokace z argumentu: ${location.name}")
                return
            }
        }

        // 2. Jinak předvyber výchozí lokaci
        val defaultLocation = locations.find { it.isDefault }
        if (defaultLocation != null) {
            binding.locationSpinner.setText(defaultLocation.name, false)
            selectedLocationId = defaultLocation.id
            Log.d("AddMeterFragment", "Předvybrána výchozí lokace: ${defaultLocation.name}")
            return
        }

        // 3. Jinak předvyber první lokaci
        if (locations.isNotEmpty()) {
            binding.locationSpinner.setText(locations[0].name, false)
            selectedLocationId = locations[0].id
            Log.d("AddMeterFragment", "Předvybrána první lokace: ${locations[0].name}")
        }
    }

    private fun saveMeter() {
        val locationName = binding.locationSpinner.text.toString().trim()
        val locationId = locationsMap[locationName] ?: selectedLocationId

        val name = binding.meterNameEditText.text.toString().trim()
        val type = binding.meterTypeSpinner.text.toString().trim()

        // Validace na UI straně
        var hasError = false

        if (locationId.isNullOrBlank()) {
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

        // Smart cast check
        if (locationId == null) {
            Toast.makeText(context, R.string.location_required, Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("AddMeterFragment", "Ukládám měřák: location=$locationId, name=$name, type=$type")

        // Zavolej ViewModel
        viewModel.saveMeter(locationId, name, type)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}