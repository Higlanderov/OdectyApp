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

        // ✨ OPRAVA: Předáme oba argumenty z navigace (včetně args.userId).
        // Váš původní kód zde posílal pouze args.locationId.
        viewModel.loadLocation(args.userId, args.locationId)
    }

    private fun setupUI() {
        // Kliknutí na tlačítko Uložit
        binding.saveButton.setOnClickListener {
            updateLocation()
        }

        // ✨ OPRAVA: Přidáno volání pro tlačítko "Zrušit", které máte v XML
        binding.cancelButton.setOnClickListener {
            findNavController().navigateUp()
        }

        // Automatické mazání chyb při psaní
        binding.locationNameEditText.doAfterTextChanged {
            binding.locationNameLayout.error = null
        }
        binding.locationAddressEditText.doAfterTextChanged {
            binding.locationAddressLayout.error = null
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Skryjeme formulář a ukážeme ProgressBar při načítání
            binding.progressBar.isVisible = isLoading
            binding.formLayout.isVisible = !isLoading // Používáme ID z XML
            binding.saveButton.isEnabled = !isLoading
            binding.cancelButton.isEnabled = !isLoading
        }

        viewModel.location.observe(viewLifecycleOwner) { location ->
            if (location != null) {
                // Vyplníme pole daty z ViewModelu
                binding.locationNameEditText.setText(location.name)
                binding.locationAddressEditText.setText(location.address)
                binding.locationNoteEditText.setText(location.note)
                binding.setAsDefaultSwitch.isChecked = location.isDefault
            } else {
                // Pokud se lokace nenačte (a už neprobíhá načítání), zobrazíme chybu
                if (viewModel.isLoading.value == false) {
                    Toast.makeText(context, "Lokace nenalezena.", Toast.LENGTH_LONG).show()
                    binding.saveButton.isEnabled = false
                }
            }
        }

        viewModel.meterCount.observe(viewLifecycleOwner) { count ->
            // ✨ OPRAVA: Posuvník se již neskrývá.
            // Zobrazíme ho VŽDY.
            binding.setAsDefaultSwitch.isVisible = true //

            if (count > 0) {
                // Místo má měřáky, tak alespoň ukážeme informační kartu
                binding.meterInfoCard.isVisible = true //
                val meterText = if (count == 1) "1 měřák" else "$count měřáky"
                binding.meterInfoTextView.text = "Toto místo má $meterText." //
            } else {
                // Místo nemá měřáky, info kartu skryjeme
                binding.meterInfoCard.isVisible = false
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

        // ✨ OPRAVA: Zavoláme novou funkci ViewModelu (již nepotřebuje ID).
        // Váš původní kód zde posílal args.locationId.
        viewModel.updateLocation(name, address, note, setAsDefault)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}