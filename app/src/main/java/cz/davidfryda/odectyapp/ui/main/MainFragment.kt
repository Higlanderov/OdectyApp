package cz.davidfryda.odectyapp.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.databinding.FragmentMainBinding
import cz.davidfryda.odectyapp.ui.user.SaveResult

class MainFragment : Fragment() {
    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by viewModels()
    private lateinit var meterAdapter: MeterAdapter

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(context, "Oprávnění pro notifikace uděleno.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Oprávnění pro notifikace zamítnuto.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        askNotificationPermission()
        setupRecyclerView()

        binding.fabAddMeter.setOnClickListener {
            showAddMeterDialog()
        }

        viewModel.meters.observe(viewLifecycleOwner) { meters ->
            meterAdapter.submitList(meters)
            binding.emptyView.isVisible = meters.isEmpty()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
        }

        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            when(result) {
                is SaveResult.Success -> {
                    showSuccessDialog("Měřák úspěšně přidán.")
                }
                is SaveResult.Error -> {
                    Toast.makeText(context, "Chyba: ${result.message}", Toast.LENGTH_LONG).show()
                }
                is SaveResult.Loading -> { /* Zde bychom mohli deaktivovat tlačítko v dialogu */ }
            }
        }
    }

    private fun setupRecyclerView() {
        meterAdapter = MeterAdapter()
        binding.metersRecyclerView.adapter = meterAdapter
    }

    // UPRAVENO: Metoda nyní používá vlastní layout a tlačítka
    private fun showAddMeterDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_meter, null)
        val editText = dialogView.findViewById<EditText>(R.id.meterNameEditText)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.meterTypeRadioGroup)
        val saveButton = dialogView.findViewById<Button>(R.id.dialogSaveButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.dialogCancelButton)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Přidat nový měřák")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        saveButton.setOnClickListener {
            val name = editText.text.toString().trim()
            val selectedTypeId = radioGroup.checkedRadioButtonId

            if (name.isNotEmpty() && selectedTypeId != -1) {
                val type = when (selectedTypeId) {
                    R.id.radioElectricity -> "Elektřina"
                    R.id.radioGas -> "Plyn"
                    R.id.radioWater -> "Voda"
                    else -> "Obecný"
                }
                viewModel.addMeter(name, type)
                dialog.dismiss()
            } else {
                Toast.makeText(context, "Vyplňte prosím název a typ měřáku.", Toast.LENGTH_SHORT).show()
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showSuccessDialog(message: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_success, null)
        val messageTextView = dialogView.findViewById<android.widget.TextView>(R.id.successMessageTextView)
        messageTextView.text = message

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.show()

        Handler(Looper.getMainLooper()).postDelayed({
            dialog.dismiss()
        }, 1500)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}