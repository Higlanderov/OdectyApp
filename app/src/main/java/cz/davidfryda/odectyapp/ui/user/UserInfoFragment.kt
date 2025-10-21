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
import cz.davidfryda.odectyapp.ui.profile.ProfileViewModel

class UserInfoFragment : Fragment() {
    private var _binding: FragmentUserInfoBinding? = null
    private val binding get() = _binding!!

    // ZMĚNA: Použijeme nový, sjednocený ViewModel
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
                // Voláme novou, sjednocenou funkci
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
                }
                is SaveResult.Error -> {
                    Toast.makeText(context, "Chyba: ${result.message}", Toast.LENGTH_LONG).show()
                }
                is SaveResult.Loading -> { /* ProgressBar se točí */ }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
