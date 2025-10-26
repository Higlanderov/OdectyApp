package cz.davidfryda.odectyapp.ui.master

import android.content.ActivityNotFoundException
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController // Import je již přítomen
import androidx.navigation.fragment.navArgs
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.databinding.FragmentUserDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class UserDetailFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentUserDetailBinding? = null
    private val binding get() = _binding!!

    private val args: UserDetailFragmentArgs by navArgs()
    private val viewModel: UserDetailViewModel by viewModels()

    private val dateFormat = SimpleDateFormat("d. M. yyyy HH:mm", Locale.getDefault())
    private val tag = "UserDetailFragment"

    private var googleMap: GoogleMap? = null
    private var userAddress: String? = null
    private var userLatLng: LatLng? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.user_detail_title)

        viewModel.loadUserDetails(args.userId)

        setupObservers()

        binding.editDetailsButton.setOnClickListener {
            val action = UserDetailFragmentDirections
                .actionUserDetailFragmentToEditUserDetailFragment(args.userId)
            findNavController().navigate(action)
        }
    }

    private fun setupObservers() {

        // --- ✨ ZAČÁTEK OPRAVY: Listener pro obnovení dat ---
        // Naslouchá výsledku z EditUserDetailFragment.
        // Musí být voláno ZDE (v setupObservers nebo onViewCreated), aby se observer zaregistroval.
        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("refresh_data")
            ?.observe(viewLifecycleOwner) { shouldRefresh ->

                // Zkontrolujeme, zda je příznak nastaven na true
                if (shouldRefresh == true) {
                    Log.d(tag, "Přijat signál k obnovení dat (refresh_data=true). Načítám znovu.")

                    // Zavoláme znovu načtení dat z ViewModelu
                    viewModel.loadUserDetails(args.userId)

                    // Resetujeme příznak, aby se data nenačetla znovu (např. při otočení obrazovky)
                    findNavController().currentBackStackEntry?.savedStateHandle?.set("refresh_data", false)
                }
            }
        // --- ✨ KONEC OPRAVY ---


        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
            val contentVisibility = if (isLoading) View.GONE else View.VISIBLE

            // Skrytí/zobrazení VŠECH prvků
            binding.userNameLabel.visibility = contentVisibility
            binding.userNameValue.visibility = contentVisibility
            binding.userAddressLabel.visibility = contentVisibility
            binding.userAddressValue.visibility = contentVisibility
            binding.userPhoneLabel.visibility = contentVisibility
            binding.userPhoneValue.visibility = contentVisibility
            binding.userEmailLabel.visibility = contentVisibility
            binding.userEmailValue.visibility = contentVisibility
            binding.userUidLabel.visibility = contentVisibility
            binding.userUidValue.visibility = contentVisibility
            binding.mapView.visibility = contentVisibility
            binding.divider.visibility = contentVisibility
            binding.meterCountLabel.visibility = contentVisibility
            binding.meterCountValue.visibility = contentVisibility
            binding.lastReadingLabel.visibility = contentVisibility
            binding.lastReadingValue.visibility = contentVisibility

            // Správa viditelnosti nových prvků
            binding.editDetailsButton.visibility = contentVisibility
            if (isLoading) {
                binding.noteGroup.visibility = View.GONE
            }
        }

        viewModel.userData.observe(viewLifecycleOwner) { user ->
            val notAvailable = getString(R.string.not_available)
            if (user != null) {
                binding.userNameValue.text = getString(R.string.user_full_name, user.name, user.surname)
                binding.userAddressValue.text = user.address
                binding.userUidValue.text = user.uid

                binding.userPhoneValue.text = user.phoneNumber.ifEmpty { notAvailable }
                binding.userEmailValue.text = user.email.ifEmpty { notAvailable }

                // Zobrazení/skrytí poznámky
                if (user.note.isNotEmpty()) {
                    binding.userNoteValue.text = user.note
                    binding.noteGroup.isVisible = true
                } else {
                    binding.noteGroup.isVisible = false
                }

                this.userAddress = user.address
                updateMapDisplay()

            } else {
                binding.userNameValue.text = notAvailable
                binding.userAddressValue.text = notAvailable
                binding.userUidValue.text = notAvailable
                binding.userPhoneValue.text = notAvailable
                binding.userEmailValue.text = notAvailable

                // Skrytí poznámky, pokud uživatel neexistuje
                binding.noteGroup.isVisible = false

                this.userAddress = null
                updateMapDisplay()
            }
        }

        viewModel.meterCount.observe(viewLifecycleOwner) { count ->
            binding.meterCountValue.text = count?.toString() ?: getString(R.string.not_available)
        }

        viewModel.lastReadingDate.observe(viewLifecycleOwner) { date ->
            binding.lastReadingValue.text = date?.let { dateFormat.format(it) } ?: getString(R.string.no_reading_yet)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        this.googleMap = map
        googleMap?.uiSettings?.isMapToolbarEnabled = false
        googleMap?.uiSettings?.isZoomControlsEnabled = true

        googleMap?.setOnMapClickListener {
            launchNavigation()
        }

        updateMapDisplay()
    }

    private fun updateMapDisplay() {
        val currentAddress = userAddress
        if (googleMap != null && !currentAddress.isNullOrEmpty() && _binding != null) {
            binding.mapView.isVisible = true
            geocodeAddress(currentAddress)
        } else if (_binding != null) {
            binding.mapView.isVisible = false
        }
    }

    private fun geocodeAddress(addressString: String) {
        if (!Geocoder.isPresent()) {
            Log.w(tag, "Geocoder není dostupný.")
            _binding?.mapView?.isVisible = false // Kontrola null bindingu
            return
        }

        val geocoder = Geocoder(requireContext(), Locale.getDefault())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocationName(addressString, 1) { addresses ->
                if (addresses.isNotEmpty()) {
                    val location = addresses[0]
                    userLatLng = LatLng(location.latitude, location.longitude)
                    activity?.runOnUiThread { _binding?.let { updateMapLocation() } }
                } else {
                    Log.w(tag, "Pro adresu '$addressString' nebyly nalezeny žádné souřadnice.")
                    activity?.runOnUiThread { _binding?.mapView?.isVisible = false }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val addresses: List<Address>? = geocoder.getFromLocationName(addressString, 1)

                    if (!addresses.isNullOrEmpty()) {
                        val location = addresses[0]
                        userLatLng = LatLng(location.latitude, location.longitude)

                        withContext(Dispatchers.Main) {
                            _binding?.let { updateMapLocation() }
                        }
                    } else {
                        Log.w(tag, "Pro adresu '$addressString' nebyly nalezeny žádné souřadnice.")
                        withContext(Dispatchers.Main) {
                            _binding?.mapView?.isVisible = false
                        }
                    }

                } catch (e: IOException) {
                    Log.e(tag, "Chyba při geokódování adresy '$addressString'", e)
                    withContext(Dispatchers.Main) {
                        _binding?.mapView?.isVisible = false
                    }
                }
            }
        }
    }

    private fun updateMapLocation() {
        val latLng = userLatLng ?: return
        val map = googleMap ?: return

        map.clear()
        map.addMarker(MarkerOptions().position(latLng).title(userAddress ?: "Adresa"))
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
    }

    private fun launchNavigation() {
        val address = userAddress
        if (address.isNullOrEmpty()) return

        try {
            val encodedAddress = URLEncoder.encode(address, "UTF-8")
            val gmmIntentUri = "google.navigation:q=$encodedAddress".toUri()
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
        } catch (e: ActivityNotFoundException) {
            Log.w(tag, "Aplikace Google Mapy není nainstalována.", e)
            Toast.makeText(context, "Aplikace Google Mapy není nainstalována.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(tag, "Nelze spustit navigaci v Google Mapách.", e)
            Toast.makeText(context, "Nelze spustit Google Mapy.", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Správa životního cyklu MapView ---
    override fun onResume() {
        super.onResume()
        _binding?.mapView?.onResume() // Kontrola null
    }

    override fun onStart() {
        super.onStart()
        _binding?.mapView?.onStart() // Kontrola null
    }

    override fun onStop() {
        super.onStop()
        _binding?.mapView?.onStop() // Kontrola null
    }
}
