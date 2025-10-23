package cz.davidfryda.odectyapp.ui.profile

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView // Import pro TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog // Import pro AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder // Import pro Material dialog
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.UserData
import cz.davidfryda.odectyapp.databinding.FragmentProfileBinding
import cz.davidfryda.odectyapp.ui.user.SaveResult

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()

    private var successDialog: AlertDialog? = null // Reference pro success dialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadUserData() // Načteme data při vytvoření view

        viewModel.userData.observe(viewLifecycleOwner) { user: UserData? ->
            // Kontrola na null pro případ chyby načítání
            user?.let {
                binding.nameEditText.setText(it.name)
                binding.surnameEditText.setText(it.surname)
                binding.addressEditText.setText(it.address)
            }
        }

        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            binding.progressBar.isVisible = result is SaveResult.Loading
            binding.saveButton.isEnabled = result !is SaveResult.Loading

            when(result) {
                is SaveResult.Success -> {
                    binding.progressBar.isVisible = false
                    showSuccessDialog("Změny úspěšně uloženy.")
                    viewModel.resetSaveResult() // Resetujeme stav
                }
                is SaveResult.Error -> {
                    binding.progressBar.isVisible = false
                    Toast.makeText(context, "Chyba při ukládání: ${result.message}", Toast.LENGTH_LONG).show()
                    viewModel.resetSaveResult() // Resetujeme stav
                }
                is SaveResult.Loading -> { /* ProgressBar je viditelný */ }
                is SaveResult.Idle -> {
                    binding.progressBar.isVisible = false
                    successDialog?.dismiss() // Zavřeme dialog, pokud je otevřený
                }
            }
        }

        binding.saveButton.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()
            val surname = binding.surnameEditText.text.toString().trim()
            val address = binding.addressEditText.text.toString().trim()

            if (name.isNotEmpty() && surname.isNotEmpty() && address.isNotEmpty()) {
                viewModel.saveOrUpdateUser(name, surname, address)
            } else {
                Toast.makeText(context, "Prosím, vyplňte všechna pole.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Zobrazí dialog o úspěchu
    private fun showSuccessDialog(message: String) {
        successDialog?.dismiss() // Zavřeme předchozí
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_success, null)
        val messageTextView = dialogView.findViewById<TextView>(R.id.successMessageTextView)
        messageTextView.text = message
        successDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()
        successDialog?.show()
        Handler(Looper.getMainLooper()).postDelayed({
            if (successDialog?.isShowing == true) {
                successDialog?.dismiss()
                successDialog = null
            }
        }, 1500)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        successDialog?.dismiss() // Zavřeme dialog při zničení view
        successDialog = null
        _binding = null
    }
}