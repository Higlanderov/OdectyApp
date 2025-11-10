package cz.davidfryda.odectyapp.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.databinding.FragmentPasswordResetBinding
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatActivity

class PasswordResetFragment : Fragment() {

    private var _binding: FragmentPasswordResetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PasswordResetViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPasswordResetBinding.inflate(inflater, container, false)

        (activity as? AppCompatActivity)?.supportActionBar?.hide()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Nastavení navigace zpět
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        // Kliknutí na tlačítko Odeslat
        binding.sendButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            lifecycleScope.launch {
                viewModel.sendPasswordResetEmail(email)
            }
        }

        // Sledování stavu
        viewModel.resetState.observe(viewLifecycleOwner) { state ->
            binding.progressBar.isVisible = state is ResetState.Loading
            binding.sendButton.isEnabled = state !is ResetState.Loading

            when (state) {
                is ResetState.Success -> {
                    Toast.makeText(requireContext(), R.string.password_reset_success, Toast.LENGTH_LONG).show()
                    viewModel.resetStateToIdle()
                    findNavController().popBackStack() // Vrátíme se na Login
                }
                is ResetState.Error -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetStateToIdle()
                }
                else -> {
                    // Idle nebo Loading
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        (activity as? AppCompatActivity)?.supportActionBar?.show()
    }
}