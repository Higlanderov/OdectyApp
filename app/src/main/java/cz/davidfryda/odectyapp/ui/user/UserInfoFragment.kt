package cz.davidfryda.odectyapp.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.databinding.FragmentUserInfoBinding
import cz.davidfryda.odectyapp.ui.profile.ProfileViewModel // Použijeme stejný ViewModel jako Profil

class UserInfoFragment : Fragment() {
    private var _binding: FragmentUserInfoBinding? = null
    private val binding get() = _binding!!

    // Použijeme ProfileViewModel pro sjednocenou logiku ukládání
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.saveButton.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()
            val surname = binding.surnameEditText.text.toString().trim()
            val address = binding.addressEditText.text.toString().trim()

            if (name.isNotEmpty() && surname.isNotEmpty() && address.isNotEmpty()) {
                // Voláme sjednocenou funkci z ProfileViewModel
                viewModel.saveOrUpdateUser(name, surname, address)
            } else {
                Toast.makeText(context, "Prosím, vyplňte všechna pole.", Toast.LENGTH_SHORT).show()
            }
        }

        // Pozorovatel reaguje na výsledek uložení
        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            binding.progressBar.isVisible = result is SaveResult.Loading
            binding.saveButton.isEnabled = result !is SaveResult.Loading

            when (result) {
                is SaveResult.Success -> {
                    // Po úspěšném uložení navigujeme na hlavní obrazovku
                    findNavController().navigate(R.id.action_userInfoFragment_to_mainFragment)
                    // Resetujeme stav, aby se při případném návratu zpět nezobrazovala znovu navigace
                    viewModel.resetSaveResult()
                }
                is SaveResult.Error -> {
                    Toast.makeText(context, "Chyba při ukládání: ${result.message}", Toast.LENGTH_LONG).show()
                    // Resetujeme stav
                    viewModel.resetSaveResult()
                }
                is SaveResult.Loading -> { /* ProgressBar se točí */ }
                is SaveResult.Idle -> {
                    binding.progressBar.isVisible = false // Ujistíme se, že je skrytý
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
