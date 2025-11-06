package cz.davidfryda.odectyapp.ui.location

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.Location
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.databinding.FragmentMasterLocationListBinding

class MasterLocationListFragment : Fragment() {

    // Používáme binding pro 'fragment_master_location_list.xml'
    private var _binding: FragmentMasterLocationListBinding? = null
    private val binding get() = _binding!!

    // Používáme vámi vytvořený ViewModel
    private val viewModel: MasterLocationListViewModel by viewModels()
    private lateinit var adapter: LocationAdapter

    // Používáme argumenty pro tento fragment
    private val args: MasterLocationListFragmentArgs by navArgs()
    private lateinit var currentUserId: String // ID je zde povinné

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate pomocí správného bindingu
        _binding = FragmentMasterLocationListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Načteme povinné ID uživatele
        currentUserId = args.userId
        Log.d("MasterLocationList", "Zobrazení lokací pro userId: $currentUserId")

        val masterId = Firebase.auth.currentUser?.uid

        if (masterId != null && masterId == currentUserId) {
            // Master se dívá na svůj vlastní profil
            Log.d("MasterLocationList", "Master se dívá na svá data. Zobrazuji FAB.")
            binding.fabAddLocation.isVisible = true
            binding.fabAddLocation.setOnClickListener {
                // Navigujeme na AddLocationFragment.
                // Předáme 'masterId', aby AddLocationFragment věděl, komu lokaci přidat.
                val action = MasterLocationListFragmentDirections
                    .actionMasterLocationListFragmentToAddLocationFragment(masterId)
                findNavController().navigate(action)
            }
        } else {
            // Master se dívá na cizí profil
            Log.d("MasterLocationList", "Master se dívá na cizí data. Skrývám FAB.")
            binding.fabAddLocation.isVisible = false
        }

        setupRecyclerView()
        setupObservers()

        // V tomto fragmentu není FAB pro přidávání lokací, takže listener není potřeba.

        // Načteme data pro konkrétního uživatele
        viewModel.loadLocationsForUser(currentUserId)
    }

    private fun setupRecyclerView() {
        // LocationAdapter je znovupoužitelný
        adapter = LocationAdapter(
            onLocationClick = { location ->
                // Navigace pomocí akce definované v nav_graph.xml
                val action = MasterLocationListFragmentDirections
                    .actionMasterLocationListFragmentToLocationDetailFragment(location.id, currentUserId)
                findNavController().navigate(action)
            },
            onEditClick = { location ->
                // Navigace pomocí akce definované v nav_graph.xml
                val action = MasterLocationListFragmentDirections
                    .actionMasterLocationListFragmentToEditLocationFragment(location.id, currentUserId)
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
            val isEmpty = locations.isEmpty()
            // Ujistěte se, že ID v layoutu odpovídají (progress_bar, empty_state_layout, locations_recycler_view)
            binding.emptyStateLayout.isVisible = isEmpty && viewModel.isLoading.value != true
            binding.locationsRecyclerView.isVisible = !isEmpty
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
        }

        // Zde používáme sealed classes z LocationListViewModel, jak je definováno
        // ve vašem MasterLocationListViewModel.kt
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