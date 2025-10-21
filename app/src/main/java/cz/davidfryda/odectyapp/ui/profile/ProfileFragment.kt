package cz.davidfryda.odectyapp.ui.profile

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.UserData
import cz.davidfryda.odectyapp.databinding.FragmentProfileBinding
import cz.davidfryda.odectyapp.ui.user.SaveResult

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadUserData()

        viewModel.userData.observe(viewLifecycleOwner) { user: UserData ->
            binding.nameEditText.setText(user.name)
            binding.surnameEditText.setText(user.surname)
            binding.addressEditText.setText(user.address)
        }

        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            binding.progressBar.isVisible = result is SaveResult.Loading
            binding.saveButton.isEnabled = result !is SaveResult.Loading

            when(result) {
                is SaveResult.Success -> {
                    showSuccessDialog("Změny úspěšně uloženy.")
                }
                is SaveResult.Error -> {
                    Toast.makeText(context, "Chyba: ${result.message}", Toast.LENGTH_LONG).show()
                }
                is SaveResult.Loading -> { /* ProgressBar se točí */ }
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

    private fun showSuccessDialog(message: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_success, null)
        val messageTextView = dialogView.findViewById<android.widget.TextView>(R.id.successMessageTextView)
        messageTextView.text = message

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.show()

        Handler(Looper.getMainLooper()).postDelayed({
            dialog.dismiss()
        }, 1500)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}