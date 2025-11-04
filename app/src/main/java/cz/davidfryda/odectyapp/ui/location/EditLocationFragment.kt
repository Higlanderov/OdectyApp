package cz.davidfryda.odectyapp.ui.location

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.databinding.FragmentEditLocationBinding

class EditLocationFragment : Fragment() {

    private var _binding: FragmentEditLocationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditLocationViewModel by viewModels()
    private val args: EditLocationFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupObservers()

        // Načti data lokace
        viewModel.loadLocation(args.locationId)
    }

    private fun setupUI() {
        // Clear error při psaní
        binding.locationNameEditText.doAfterTextChanged {
            binding.locationNameLayout.error = null
        }

        binding.locationAddressEditText.doAfterTextChanged {
            binding.locationAddressLayout.error = null
        }

        // Tlačítko Zrušit
        binding.cancelButton.setOnClickListener {
            findNavController().navigateUp()
        }

        // Tlačítko Uložit
        binding.saveButton.setOnClickListener {
            updateLocation()
        }
    }

    private fun setupObservers() {
        viewModel.location.observe(viewLifecycleOwner) { location ->
            if (location != null) {
                // Naplň formulář daty
                binding.locationNameEditText.setText(location.name)
                binding.locationAddressEditText.setText(location.address)
                binding.locationNoteEditText.setText(location.note)
                binding.setAsDefaultSwitch.isChecked = location.isDefault

                // Zobraz/skryj loading
                binding.loadingLayout.isVisible = false
                binding.formLayout.isVisible = true
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

        viewModel.meterCount.observe(viewLifecycleOwner) { count ->
            if (count > 0) {
                val text = when (count) {
                    1 -> getString(R.string.location_has_one_meter)
                    else -> getString(R.string.location_has_meters, count)
                }
                binding.meterInfoTextView.text = text
                binding.meterInfoCard.isVisible = true
            } else {
                binding.meterInfoCard.isVisible = false
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (viewModel.location.value == null) {
                // Zobraz loading state při prvním načítání
                binding.loadingLayout.isVisible = isLoading
                binding.formLayout.isVisible = !isLoading
            } else {
                // Zobraz progress bar při ukládání
                binding.progressBar.isVisible = isLoading
                binding.saveButton.isEnabled = !isLoading
                binding.cancelButton.isEnabled = !isLoading

                // Disable input fields during loading
                binding.locationNameEditText.isEnabled = !isLoading
                binding.locationAddressEditText.isEnabled = !isLoading
                binding.locationNoteEditText.isEnabled = !isLoading
                binding.setAsDefaultSwitch.isEnabled = !isLoading
            }
        }

        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is EditLocationViewModel.SaveResult.Success -> {
                    Toast.makeText(
                        context,
                        R.string.location_updated_successfully,
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.resetSaveResult()
                    findNavController().navigateUp()
                }
                is EditLocationViewModel.SaveResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    viewModel.resetSaveResult()
                }
                else -> {}
            }
        }
    }

    private fun updateLocation() {
        val name = binding.locationNameEditText.text.toString().trim()
        val address = binding.locationAddressEditText.text.toString().trim()
        val note = binding.locationNoteEditText.text.toString().trim()
        val setAsDefault = binding.setAsDefaultSwitch.isChecked

        // Validace na UI straně
        var hasError = false

        if (name.isBlank()) {
            binding.locationNameLayout.error = getString(R.string.location_name_required)
            hasError = true
        }

        if (address.isBlank()) {
            binding.locationAddressLayout.error = getString(R.string.address_required)
            hasError = true
        }

        if (hasError) {
            return
        }

        // Zavolej ViewModel
        viewModel.updateLocation(args.locationId, name, address, note, setAsDefault)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}