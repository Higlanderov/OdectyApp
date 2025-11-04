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
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.databinding.FragmentAddLocationBinding

class AddLocationFragment : Fragment() {

    private var _binding: FragmentAddLocationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddLocationViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupObservers()
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
            saveLocation()
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
            binding.saveButton.isEnabled = !isLoading
            binding.cancelButton.isEnabled = !isLoading

            // Disable input fields during loading
            binding.locationNameEditText.isEnabled = !isLoading
            binding.locationAddressEditText.isEnabled = !isLoading
            binding.locationNoteEditText.isEnabled = !isLoading
            binding.setAsDefaultSwitch.isEnabled = !isLoading
        }

        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is AddLocationViewModel.SaveResult.Success -> {
                    Toast.makeText(
                        context,
                        R.string.location_added_successfully,
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.resetSaveResult()
                    findNavController().navigateUp()
                }
                is AddLocationViewModel.SaveResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    viewModel.resetSaveResult()
                }
                else -> {}
            }
        }
    }

    private fun saveLocation() {
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
        viewModel.saveLocation(name, address, note, setAsDefault)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}