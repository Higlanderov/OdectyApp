package cz.davidfryda.odectyapp.ui.location

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs // <-- NOVÝ IMPORT
import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.Location
import cz.davidfryda.odectyapp.databinding.FragmentLocationListBinding

class LocationListFragment : Fragment() {

    private var _binding: FragmentLocationListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LocationListViewModel by viewModels()
    private lateinit var adapter: LocationAdapter

    // NOVÉ: Načtení argumentů z navigace
    private val args: LocationListFragmentArgs by navArgs()
    private var currentUserId: String? = null

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

        // NOVÉ: Získání userId z argumentů
        currentUserId = args.userId
        Log.d("LocationListFragment", "Zobrazení lokací pro userId: $currentUserId")

        // NOVÉ: Změna titulku, pokud jsme v Master režimu
        if (currentUserId != null) {
            (activity as? AppCompatActivity)?.supportActionBar?.title = "Lokace uživatele"
            // V režimu mastera skryj FAB pro přidání lokace
            binding.fabAddLocation.visibility = View.GONE
        } else {
            (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.locations)
            binding.fabAddLocation.visibility = View.VISIBLE
        }

        setupRecyclerView()
        setupObservers()

        binding.fabAddLocation.setOnClickListener {
            // UPRAVENO: Předání userId do AddLocationFragment
            val action = LocationListFragmentDirections
                .actionLocationListFragmentToAddLocationFragment(currentUserId)
            findNavController().navigate(action)
        }

        // UPRAVENO: Zavolání nové metody ve ViewModelu
        viewModel.loadLocationsForUser(currentUserId)
    }

    private fun setupRecyclerView() {
        adapter = LocationAdapter(
            onLocationClick = { location ->
                // UPRAVENO: Předání locationId A userId do detailu
                val action = LocationListFragmentDirections
                    .actionLocationListFragmentToLocationDetailFragment(location.id, currentUserId)
                findNavController().navigate(action)
            },
            onEditClick = { location ->
                // UPRAVENO: Předání locationId A userId do editace
                val action = LocationListFragmentDirections
                    .actionLocationListFragmentToEditLocationFragment(location.id, currentUserId)
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
            binding.emptyStateLayout.isVisible = isEmpty && viewModel.isLoading.value == false
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

    // NOVÉ: Obnovení titulku při opuštění fragmentu
    override fun onStop() {
        super.onStop()
        // Vrátí titulky zpět (MainActivity si to stejně přepíše, ale pro jistotu)
        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.app_name)
    }
}