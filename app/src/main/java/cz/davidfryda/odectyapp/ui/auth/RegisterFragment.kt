package cz.davidfryda.odectyapp.ui.auth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.databinding.FragmentRegisterBinding
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels()
    private lateinit var credentialManager: CredentialManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✨ NOVÉ: Inicializace Credential Manager
        credentialManager = CredentialManager.create(requireContext())

        binding.loginPromptText.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }

        binding.registerButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()
            val confirmPassword = binding.confirmPasswordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(context, "Prosím, vyplňte všechna pole.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(context, "Hesla se neshodují.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.register(email, password)
        }

        // ✨ UPRAVENO: Google Sign-In s Credential Manager
        binding.googleSignInButton.setOnClickListener {
            signInWithGoogle()
        }

        viewModel.authResult.observe(viewLifecycleOwner) { result ->
            binding.progressBar.isVisible = result is AuthResult.Loading

            when (result) {
                is AuthResult.Success -> {
                    Toast.makeText(context, "Přihlášení úspěšné!", Toast.LENGTH_SHORT).show()

                    if (result.isNewUser) {
                        findNavController().navigate(R.id.action_registerFragment_to_userInfoFragment)
                    } else if (result.isMaster) {
                        findNavController().navigate(R.id.action_registerFragment_to_masterUserListFragment)
                    } else {
                        findNavController().navigate(R.id.action_registerFragment_to_locationListFragment)
                    }
                }
                is AuthResult.Error -> {
                    Toast.makeText(context, "Chyba: ${result.message}", Toast.LENGTH_LONG).show()
                }
                is AuthResult.Loading -> { /* ProgressBar se točí */ }
            }
        }
    }

    // ✨ NOVÁ METODA: Google Sign-In s Credential Manager API
    private fun signInWithGoogle() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = requireContext()
                )

                val credential = result.credential

                // ✨ OPRAVENO: Správná kontrola typu
                when (credential) {
                    is GoogleIdTokenCredential -> {
                        val idToken = credential.idToken
                        Log.d("RegisterFragment", "Google ID Token received: ${idToken.take(20)}...")
                        viewModel.signInWithGoogle(idToken)
                    }
                    else -> {
                        // Pokusíme se vytvořit z dat
                        try {
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            val idToken = googleIdTokenCredential.idToken
                            Log.d("RegisterFragment", "Google ID Token created from data: ${idToken.take(20)}...")
                            viewModel.signInWithGoogle(idToken)
                        } catch (e: Exception) {
                            Log.e("RegisterFragment", "Failed to parse credential", e)
                            Toast.makeText(context, "Neplatný typ přihlašovacích údajů", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

            } catch (e: GetCredentialException) {
                Log.e("RegisterFragment", "Error getting credential", e)
                Toast.makeText(context, "Přihlášení přes Google selhalo: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("RegisterFragment", "Unexpected error", e)
                Toast.makeText(context, "Neočekávaná chyba: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}