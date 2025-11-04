package cz.davidfryda.odectyapp.ui.location

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.MainActivity
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.Location
import cz.davidfryda.odectyapp.databinding.FragmentLocationListBinding

class LocationListFragment : Fragment() {

    private var _binding: FragmentLocationListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LocationListViewModel by viewModels()
    private lateinit var adapter: LocationAdapter

    // ✨ NOVÉ: Přijmi userId z argumentů (pro mastera)
    private val args: LocationListFragmentArgs by navArgs()
    private var targetUserId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocationListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✨ Určí, čí lokace načíst (master nebo vlastní)
        targetUserId = args.userId ?: Firebase.auth.currentUser?.uid

        if (targetUserId == null) {
            Toast.makeText(context, "Chyba: Uživatel není přihlášen", Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
            return
        }

        setupToolbar()
        setupRecyclerView()
        setupObservers()

        binding.fabAddLocation.setOnClickListener {
            val action = LocationListFragmentDirections
                .actionLocationListFragmentToAddLocationFragment(targetUserId)
            findNavController().navigate(action)
        }

        viewModel.loadLocations()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        binding.toolbar.setupWithNavController(
            findNavController(),
            (activity as MainActivity).appBarConfiguration
        )
    }

    private fun setupRecyclerView() {
        adapter = LocationAdapter(
            onLocationClick = { location ->
                val action = LocationListFragmentDirections
                    .actionLocationListFragmentToLocationDetailFragment(
                        locationId = location.id,
                        userId = targetUserId
                    )
                findNavController().navigate(action)
            },
            onEditClick = { location ->
                val action = LocationListFragmentDirections
                    .actionLocationListFragmentToEditLocationFragment(
                        locationId = location.id,
                        userId = targetUserId
                    )
                findNavController().navigate(action)
            },
            onDeleteClick = { location ->
                showDeleteConfirmationDialog(location)
            },
            onSetDefaultClick = { location ->
                viewModel.setAsDefault(location.id)
            }
        )

        binding.locationsRecyclerView.adapter = adapter
        binding.locationsRecyclerView.addItemDecoration(
            DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        )
    }

    private fun setupObservers() {
        viewModel.locations.observe(viewLifecycleOwner) { locations ->
            adapter.submitList(locations)

            // Zobraz empty state pokud není žádná lokace
            val isEmpty = locations.isEmpty()
            binding.emptyStateLayout.isVisible = isEmpty && viewModel.isLoading.value != true
            binding.locationsRecyclerView.isVisible = !isEmpty
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
        }

        viewModel.deleteResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is LocationListViewModel.DeleteResult.Success -> {
                    Toast.makeText(context, R.string.location_deleted, Toast.LENGTH_SHORT).show()
                    viewModel.resetDeleteResult()
                }
                is LocationListViewModel.DeleteResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    viewModel.resetDeleteResult()
                }
                else -> {}
            }
        }

        viewModel.setDefaultResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is LocationListViewModel.SetDefaultResult.Success -> {
                    Toast.makeText(context, R.string.default_location_updated, Toast.LENGTH_SHORT).show()
                    viewModel.resetSetDefaultResult()
                }
                is LocationListViewModel.SetDefaultResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    viewModel.resetSetDefaultResult()
                }
                else -> {}
            }
        }
    }

    private fun showDeleteConfirmationDialog(location: Location) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_location_title)
            .setMessage(getString(R.string.delete_location_message, location.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteLocation(location.id)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
