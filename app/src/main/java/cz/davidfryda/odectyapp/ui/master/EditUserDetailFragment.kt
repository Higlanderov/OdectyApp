package cz.davidfryda.odectyapp.ui.master

import android.os.Bundle
import android.util.Log // ✨ PŘIDÁN IMPORT
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.databinding.FragmentEditUserDetailBinding
import cz.davidfryda.odectyapp.ui.user.SaveResult

class EditUserDetailFragment : Fragment() {

    private var _binding: FragmentEditUserDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditUserDetailViewModel by viewModels()
    private val args: EditUserDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditUserDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.edit_user_details_title)

        val userId = args.userId
        viewModel.loadUserData(userId)

        observeViewModel()

        binding.saveButton.setOnClickListener {
            viewModel.saveUserData(
                userId = userId,
                name = binding.nameEditText.text.toString(),
                surname = binding.surnameEditText.text.toString(),
                address = binding.addressEditText.text.toString(),
                phoneNumber = binding.phoneEditText.text.toString(),
                note = binding.noteEditText.text.toString()
            )
        }
    }

    private fun observeViewModel() {
        viewModel.user.observe(viewLifecycleOwner) { userData ->
            if (userData != null) {
                binding.nameEditText.setText(userData.name)
                binding.surnameEditText.setText(userData.surname)
                binding.addressEditText.setText(userData.address)
                binding.phoneEditText.setText(userData.phoneNumber)
                binding.noteEditText.setText(userData.note)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.saveProgressBar.isVisible = isLoading
            binding.saveButton.isEnabled = !isLoading
        }

        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is SaveResult.Loading -> {
                    binding.saveProgressBar.isVisible = true
                    binding.saveButton.isEnabled = false
                }
                is SaveResult.Success -> {
                    binding.saveProgressBar.isVisible = false
                    binding.saveButton.isEnabled = true
                    Toast.makeText(context, getString(R.string.user_data_saved_successfully), Toast.LENGTH_SHORT).show()

                    // --- ✨ ZAČÁTEK OPRAVY ---
                    // Nastavíme "výsledek" pro předchozí fragment, aby věděl, že má obnovit data
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_data", true)
                    Log.d("EditUserDetail", "Nastaven příznak refresh_data=true")
                    // --- ✨ KONEC OPRAVY ---

                    findNavController().popBackStack()
                    viewModel.doneNavigating()
                }
                is SaveResult.Error -> {
                    binding.saveProgressBar.isVisible = false
                    binding.saveButton.isEnabled = true
                    Toast.makeText(context, "Chyba: ${result.message}", Toast.LENGTH_LONG).show()
                }
                SaveResult.Idle -> {
                    binding.saveProgressBar.isVisible = false
                    binding.saveButton.isEnabled = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}