package cz.davidfryda.odectyapp.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels()

    // NOVÉ: Připravíme spouštěč pro Google Sign-In
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)!!
            // Získáme klíčový ID token a pošleme ho do ViewModelu
            viewModel.signInWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Toast.makeText(context, "Přihlášení přes Google selhalo.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.registerPromptText.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                viewModel.login(email, password)
            } else {
                Toast.makeText(context, "Prosím, vyplňte e-mail a heslo.", Toast.LENGTH_SHORT).show()
            }
        }

        // NOVÉ: Listener pro Google tlačítko
        binding.googleSignInButton.setOnClickListener {
            launchGoogleSignIn()
        }

        // UPRAVENO: Observer nyní řeší i nové uživatele
        viewModel.authResult.observe(viewLifecycleOwner) { result ->
            binding.progressBar.isVisible = result is AuthResult.Loading

            when (result) {
                is AuthResult.Success -> {
                    Toast.makeText(context, "Přihlášení úspěšné!", Toast.LENGTH_SHORT).show()

                    if (result.isNewUser) {
                        // Nový uživatel (z Google nebo z registrace) musí vyplnit údaje
                        findNavController().navigate(R.id.action_loginFragment_to_userInfoFragment)
                    } else if (result.isMaster) {
                        // Stávající uživatel a je to správce
                        findNavController().navigate(R.id.action_loginFragment_to_masterUserListFragment)
                    } else {
                        // Stávající běžný uživatel
                        findNavController().navigate(R.id.action_loginFragment_to_mainFragment)
                    }
                }
                is AuthResult.Error -> {
                    Toast.makeText(context, "Chyba: ${result.message}", Toast.LENGTH_LONG).show()
                }
                is AuthResult.Loading -> { /* ProgressBar se točí */ }
            }
        }
    }

    // NOVÁ METODA pro spuštění přihlašovacího dialogu
    private fun launchGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
        // Před spuštěním je dobré se odhlásit, aby si uživatel mohl vždy vybrat účet
        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}