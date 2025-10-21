package cz.davidfryda.odectyapp.ui.masterdetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import cz.davidfryda.odectyapp.databinding.FragmentMasterUserDetailBinding
import cz.davidfryda.odectyapp.ui.main.MeterAdapter

class MasterUserDetailFragment : Fragment() {
    private var _binding: FragmentMasterUserDetailBinding? = null
    private val binding get() = _binding!!

    private val args: MasterUserDetailFragmentArgs by navArgs()
    private val viewModel: MasterUserDetailViewModel by viewModels()
    private lateinit var meterAdapter: MeterAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMasterUserDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        viewModel.fetchMetersForUser(args.userId)

        viewModel.meters.observe(viewLifecycleOwner) { meters ->
            meterAdapter.submitList(meters)
        }
    }

    private fun setupRecyclerView() {
        meterAdapter = MeterAdapter()
        meterAdapter.ownerId = args.userId
        binding.usersRecyclerView.adapter = meterAdapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}