package cz.davidfryda.odectyapp.ui.profile

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.UserData
import cz.davidfryda.odectyapp.databinding.FragmentProfileBinding
import cz.davidfryda.odectyapp.ui.user.SaveResult

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()

    private var successDialog: AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadUserData()

        viewModel.userData.observe(viewLifecycleOwner) { user: UserData? ->
            user?.let {
                binding.nameEditText.setText(it.name)
                binding.surnameEditText.setText(it.surname)
                binding.addressEditText.setText(it.address)
                binding.phoneEditText.setText(it.phoneNumber)
            } ?: run {
                binding.nameEditText.setText("")
                binding.surnameEditText.setText("")
                binding.addressEditText.setText("")
                binding.phoneEditText.setText("")
            }
        }

        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            binding.progressBar.isVisible = result is SaveResult.Loading
            binding.saveButton.isEnabled = result !is SaveResult.Loading

            when(result) {
                is SaveResult.Success -> {
                    binding.progressBar.isVisible = false
                    showSuccessDialog()  // ✨ ZMĚNA: Bez parametru
                }
                is SaveResult.Error -> {
                    binding.progressBar.isVisible = false
                    Toast.makeText(context, "Chyba při ukládání: ${result.message}", Toast.LENGTH_LONG).show()
                    viewModel.resetSaveResult()
                }
                is SaveResult.Loading -> { /* ProgressBar je viditelný */ }
                is SaveResult.Idle -> {
                    binding.progressBar.isVisible = false
                    dismissSuccessDialog()
                }
            }
        }

        binding.saveButton.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()
            val surname = binding.surnameEditText.text.toString().trim()
            val address = binding.addressEditText.text.toString().trim()
            val phone = binding.phoneEditText.text.toString().trim()

            if (name.isNotEmpty() && surname.isNotEmpty() && address.isNotEmpty() && phone.isNotEmpty()) {
                viewModel.saveOrUpdateUser(name, surname, address, phone)
            } else {
                Toast.makeText(context, "Prosím, vyplňte všechna pole.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✨ ZMĚNA: Bez parametru
    private fun showSuccessDialog() {
        dismissSuccessDialog()

        context?.let { ctx ->
            val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_success, null)
            val messageTextView = dialogView.findViewById<TextView>(R.id.successMessageTextView)
            messageTextView.setText(R.string.changes_saved_successfully)  // ✨ Hardcoded text
            successDialog = MaterialAlertDialogBuilder(ctx)
                .setView(dialogView)
                .setCancelable(false)
                .create()

            try {
                successDialog?.show()
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({
                    dismissSuccessDialog()
                    viewModel.resetSaveResult()
                }, 1500)
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Error showing success dialog: ${e.message}")
                successDialog = null
                viewModel.resetSaveResult()
            }
        } ?: run {
            Log.e("ProfileFragment", "showSuccessDialog: Context is null!")
            viewModel.resetSaveResult()
        }
    }

    private fun dismissSuccessDialog() {
        if (successDialog?.isShowing == true && isAdded) {
            try {
                successDialog?.dismiss()
            } catch (e: Exception) {
                Log.w("ProfileFragment", "Error dismissing success dialog (might be expected during rapid navigation): ${e.message}")
            } finally {
                successDialog = null
            }
        } else if (successDialog != null) {
            successDialog = null
        }

        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        dismissSuccessDialog()
        _binding = null
    }
}