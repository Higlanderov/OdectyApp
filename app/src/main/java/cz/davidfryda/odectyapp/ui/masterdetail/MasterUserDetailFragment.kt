package cz.davidfryda.odectyapp.ui.masterdetail

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.Meter
import cz.davidfryda.odectyapp.databinding.FragmentMasterUserDetailBinding
import cz.davidfryda.odectyapp.ui.main.MeterAdapter
import cz.davidfryda.odectyapp.ui.main.MeterInteractionListener
import cz.davidfryda.odectyapp.ui.master.MasterUserDetailViewModel
import cz.davidfryda.odectyapp.ui.user.SaveResult

/**
 * Fragment zobrazující seznam měřáků pro konkrétního uživatele v režimu správce.
 * Umožňuje správci přidávat/upravovat popisy k měřákům.
 */
class MasterUserDetailFragment : Fragment(), MeterInteractionListener {
    private var _binding: FragmentMasterUserDetailBinding? = null
    private val binding get() = _binding!!

    private val args: MasterUserDetailFragmentArgs by navArgs()
    private val viewModel: MasterUserDetailViewModel by viewModels()
    private lateinit var meterAdapter: MeterAdapter
    private var successDialog: AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tag = "MasterUserDetailFrag"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMasterUserDetailBinding.inflate(inflater, container, false)
        Log.d(tag, "onCreateView called for user: ${args.userId}")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(tag, "onViewCreated called")

        // ✨ Nastavit default title okamžitě
        activity?.title = getString(R.string.master_user_detail_default_title)
        Log.d(tag, "Default title set")

        setupRecyclerView()

        binding.progressBar.isVisible = true
        binding.emptyView.isVisible = false

        // ✨ DŮLEŽITÉ: Nejdřív nastavit observer, PAK zavolat fetch
        viewModel.userName.observe(viewLifecycleOwner) { name ->
            Log.d(tag, "userName observer triggered with value: '$name'")
            if (!name.isNullOrEmpty()) {
                val newTitle = getString(R.string.master_user_meters_title, name)

                // ✨ Nastavit okamžitě
                activity?.title = newTitle
                Log.d(tag, "Title bar updated immediately to: '$newTitle'")

                // ✨ Vynutit refresh toolbar
                (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.let { actionBar ->
                    actionBar.title = newTitle
                    Log.d(tag, "ActionBar title set directly to: '$newTitle'")
                }

                // ✨ A ještě jednou s malým zpožděním pro jistotu
                view.postDelayed({
                    activity?.title = newTitle
                    (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = newTitle
                    Log.d(tag, "Title bar updated (delayed) to: '$newTitle'")
                }, 100)
            } else {
                Log.w(tag, "userName is null or empty!")
            }
        }

        // ✨ Teď zavoláme fetch (observer už je připravený)
        Log.d(tag, "Calling fetchMetersForUser for userId: ${args.userId}")
        viewModel.fetchMetersForUser(args.userId)

        viewModel.meters.observe(viewLifecycleOwner) { meters ->
            Log.d(tag, "Meters LiveData updated with ${meters.size} items.")
            binding.progressBar.isVisible = false
            meterAdapter.submitList(meters)
            binding.emptyView.isVisible = meters.isEmpty()
            Log.d(tag, "RecyclerView updated. Empty view visible: ${meters.isEmpty()}")
        }

        viewModel.saveDescriptionResult.observe(viewLifecycleOwner) { result ->
            Log.d(tag, "saveDescriptionResult observed: $result")
            binding.progressBar.isVisible = result is SaveResult.Loading

            when (result) {
                is SaveResult.Success -> {
                    Log.d(tag, "SaveResult.Success received.")
                    showSuccessDialog(getString(R.string.save_description_success))
                    viewModel.resetSaveDescriptionResult()
                }
                is SaveResult.Error -> {
                    Log.e(tag, "SaveResult.Error received: ${result.message}")
                    Toast.makeText(context, getString(R.string.save_description_error, result.message), Toast.LENGTH_LONG).show()
                    viewModel.resetSaveDescriptionResult()
                }
                is SaveResult.Loading -> {
                    Log.d(tag, "SaveResult.Loading received.")
                }
                is SaveResult.Idle -> {
                    Log.d(tag, "SaveResult.Idle received.")
                    dismissSuccessDialog()
                }
            }
        }
    }

    private fun setupRecyclerView() {
        Log.d(tag, "setupRecyclerView called")
        meterAdapter = MeterAdapter()
        meterAdapter.ownerId = args.userId
        meterAdapter.listener = this
        binding.usersRecyclerView.adapter = meterAdapter
    }

    override fun onEditMeterClicked(meter: Meter) {
        Log.d(tag, "onEditMeterClicked called (no action in master mode)")
        Toast.makeText(context, "Úprava názvu je dostupná pouze pro vlastní měřáky.", Toast.LENGTH_SHORT).show()
    }

    override fun onDeleteMeterClicked(meter: Meter) {
        Log.d(tag, "onDeleteMeterClicked called (no action in master mode)")
        Toast.makeText(context, "Mazání měřáku je dostupné pouze pro vlastní měřáky.", Toast.LENGTH_SHORT).show()
    }

    override fun onAddDescriptionClicked(meter: Meter, ownerUserId: String) {
        Log.d(tag, "onAddDescriptionClicked called for meter: ${meter.id}")
        showAddDescriptionDialog(meter, ownerUserId)
    }

    private fun showAddDescriptionDialog(meter: Meter, ownerUserId: String) {
        val currentContext = context ?: run {
            Log.e(tag, "showAddDescriptionDialog: Context is null!")
            return
        }
        Log.d(tag, "Showing add description dialog for meter: ${meter.id}")

        val dialogView = LayoutInflater.from(currentContext).inflate(R.layout.dialog_add_description, null)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.editDescriptionEditText)
        val saveButton = dialogView.findViewById<Button>(R.id.dialogSaveButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.dialogCancelButton)

        editText.setText(meter.masterDescription ?: "")

        val dialog = MaterialAlertDialogBuilder(currentContext)
            .setTitle(getString(R.string.add_description_dialog_title, meter.name))
            .setView(dialogView)
            .setCancelable(false)
            .create()

        saveButton.setOnClickListener {
            val description = editText.text.toString().trim()
            Log.d(tag, "Save button clicked. Description: '$description'")
            viewModel.saveMasterDescription(ownerUserId, meter.id, description)
            dialog.dismiss()
        }

        cancelButton.setOnClickListener {
            Log.d(tag, "Cancel button clicked.")
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showSuccessDialog(message: String) {
        val currentContext = context ?: run {
            Log.e(tag, "showSuccessDialog: Context is null!")
            return
        }

        dismissSuccessDialog()
        Log.d(tag, "Showing success dialog with message: $message")

        val dialogView = LayoutInflater.from(currentContext).inflate(R.layout.dialog_success, null)
        val messageTextView = dialogView.findViewById<TextView>(R.id.successMessageTextView)
        messageTextView.text = message

        successDialog = MaterialAlertDialogBuilder(currentContext)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        try {
            successDialog?.show()
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed(::dismissSuccessDialog, 1500)
        } catch (e: Exception) {
            Log.e(tag, "Error showing success dialog: ${e.message}")
        }
    }

    private fun dismissSuccessDialog() {
        if (successDialog?.isShowing == true && isAdded) {
            try {
                Log.d(tag, "Dismissing success dialog.")
                successDialog?.dismiss()
            } catch (e: Exception) {
                Log.w(tag, "Error dismissing success dialog (might be expected during rapid navigation): ${e.message}")
            } finally {
                successDialog = null
            }
        } else if (successDialog != null) {
            Log.d(tag, "Success dialog exists but not dismissing (showing=${successDialog?.isShowing}, isAdded=$isAdded). Setting reference to null.")
            successDialog = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(tag, "onDestroyView called")
        handler.removeCallbacksAndMessages(null)
        dismissSuccessDialog()
        meterAdapter.listener = null
        _binding = null
        Log.d(tag, "onDestroyView completed.")
    }
}