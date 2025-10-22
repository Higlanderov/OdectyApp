package cz.davidfryda.odectyapp.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.databinding.FragmentMeterDetailBinding
import kotlinx.coroutines.launch
import java.io.File

class MeterDetailFragment : Fragment() {
    private var _binding: FragmentMeterDetailBinding? = null
    private val binding get() = _binding!!
    private val args: MeterDetailFragmentArgs by navArgs()
    private val viewModel: MeterDetailViewModel by viewModels()
    private var latestTmpUri: Uri? = null
    private lateinit var historyAdapter: ReadingHistoryAdapter
    private lateinit var targetUserId: String

    // Dočasná paměť pro potvrzení
    private var lastEnteredValue: Double? = null
    private var lastPhotoUri: Uri? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) takeImage()
            else Toast.makeText(requireContext(), "Bez povolení nelze použít fotoaparát.", Toast.LENGTH_LONG).show()
        }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
        if (isSuccess) {
            latestTmpUri?.let { uri ->
                showManualInputDialog(uri)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMeterDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        targetUserId = if (args.userId != null) {
            args.userId!!
        } else {
            Firebase.auth.currentUser!!.uid
        }

        viewModel.initializeForUser(targetUserId, args.meterId, requireContext())
        setupRecyclerView()

        val isLoggedInUser = (targetUserId == Firebase.auth.currentUser!!.uid)
        binding.takeReadingButton.isVisible = isLoggedInUser
        val isMasterView = !isLoggedInUser
        historyAdapter.isMasterView = isMasterView

        viewModel.loadMeterDetails(targetUserId, args.meterId)

        viewModel.readingHistory.observe(viewLifecycleOwner) { history ->
            historyAdapter.submitList(history)
        }

        binding.takeReadingButton.setOnClickListener {
            requestCameraPermission()
        }

        binding.fabShowChart.setOnClickListener {
            val action = MeterDetailFragmentDirections.actionMeterDetailFragmentToChartFragment(args.meterId, targetUserId)
            findNavController().navigate(action)
        }

        viewModel.meter.observe(viewLifecycleOwner) { meter ->
            binding.meterNameTextView.text = meter.name
            historyAdapter.meterType = meter.type
            historyAdapter.notifyItemRangeChanged(0, historyAdapter.itemCount)
        }

        viewModel.uploadResult.observe(viewLifecycleOwner) { result ->
            binding.progressBar.isVisible = result is UploadResult.Loading
            if(binding.takeReadingButton.isVisible) {
                binding.takeReadingButton.isEnabled = result !is UploadResult.Loading
            }
            if (result is UploadResult.Success) {
                Toast.makeText(requireContext(), "Odečet úspěšně zpracován!", Toast.LENGTH_SHORT).show()
            } else if (result is UploadResult.Error) {
                Toast.makeText(requireContext(), "Chyba nahrávání: ${result.message}", Toast.LENGTH_LONG).show()
            }
        }

        // NOVÉ: Pozorovatel pro výsledek validace
        viewModel.validationResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ValidationResult.WarningHigh -> showWarningDialog(result.message)
                is ValidationResult.WarningLow -> showWarningDialog(result.message)
                is ValidationResult.Error -> Toast.makeText(context, "Chyba: ${result.message}", Toast.LENGTH_LONG).show()
                else -> { /* Validní, nic neděláme, uložení už proběhlo */ }
            }
        }
    }

    private fun setupRecyclerView() {
        historyAdapter = ReadingHistoryAdapter()
        binding.historyRecyclerView.adapter = historyAdapter
        binding.historyRecyclerView.addItemDecoration(
            DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        )
    }

    private fun showManualInputDialog(photoUri: Uri) {
        lastPhotoUri = photoUri // Uložíme si URI fotky
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_manual_reading, null)
        val photoPreview = dialogView.findViewById<ImageView>(R.id.dialogPhotoPreview)
        val editText = dialogView.findViewById<EditText>(R.id.dialogValueEditText)
        val saveButton = dialogView.findViewById<Button>(R.id.dialogSaveButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.dialogCancelButton)

        photoPreview.load(photoUri)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Zadat hodnotu odečtu")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        saveButton.setOnClickListener { 
            val valueString = editText.text.toString()
            val valueDouble = valueString.toDoubleOrNull()

            if (valueDouble != null) {
                lastEnteredValue = valueDouble // Uložíme si zadanou hodnotu
                // Voláme novou funkci pro validaci
                viewModel.validateAndSaveReading(targetUserId, args.meterId, photoUri, valueDouble, requireContext())
                dialog.dismiss()
            } else {
                Toast.makeText(context, "Prosím, zadejte platnou číselnou hodnotu.", Toast.LENGTH_SHORT).show()
            }
        }
        cancelButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // NOVÁ METODA: Zobrazí varovný dialog
    private fun showWarningDialog(message: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_warning, null)
        val messageTextView = dialogView.findViewById<TextView>(R.id.warningMessageTextView)
        val confirmButton = dialogView.findViewById<Button>(R.id.dialogConfirmButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.dialogCancelButton)

        messageTextView.text = message

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Pozor, možná chyba")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        confirmButton.setOnClickListener { 
            // Uživatel potvrdil, že chce hodnotu uložit.
            if (lastPhotoUri != null && lastEnteredValue != null) {
                viewModel.forceSaveReading(targetUserId, args.meterId, lastPhotoUri!!, lastEnteredValue!!, requireContext())
            }
            dialog.dismiss()
        }
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> takeImage()
            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun takeImage() {
        lifecycleScope.launch {
            getTmpFileUri().let { uri ->
                latestTmpUri = uri
                takePictureLauncher.launch(uri)
            }
        }
    }

    private fun getTmpFileUri(): Uri {
        val tmpFile = File.createTempFile("tmp_image_file", ".png", requireContext().cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }
        return FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", tmpFile)
    } 

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}