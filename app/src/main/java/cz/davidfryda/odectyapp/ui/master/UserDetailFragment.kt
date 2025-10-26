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
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.databinding.FragmentUserDetailBinding
import cz.davidfryda.odectyapp.ui.user.SaveResult
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

    // Proměnná pro uložení aktuálního stavu blokace
    private var currentUserIsDisabled: Boolean = false

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

        binding.blockUserButton.setOnClickListener {
            showBlockConfirmationDialog()
        }
    }

    // Funkce pro zobrazení potvrzovacího dialogu (zůstává stejná)
    private fun showBlockConfirmationDialog() {
        val title = getString(R.string.block_user_confirm_title)
        val message = if (currentUserIsDisabled) {
            getString(R.string.unblock_user_confirm_message)
        } else {
            getString(R.string.block_user_confirm_message)
        }
        val positiveButtonText = if (currentUserIsDisabled) {
            getString(R.string.unblock_user)
        } else {
            getString(R.string.block_user)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(positiveButtonText) { _, _ ->
                Log.d(tag, "Calling toggleBlockUser with userId=${args.userId}, currentDisabledState=$currentUserIsDisabled")
                viewModel.toggleBlockUser(args.userId, currentUserIsDisabled)
            }
            .show()
    }

    private fun setupObservers() {

        // Listener pro obnovení dat po editaci (zůstává stejný)
        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("refresh_data")
            ?.observe(viewLifecycleOwner) { shouldRefresh ->
                if (shouldRefresh == true) {
                    Log.d(tag, "Přijat signál k obnovení dat (refresh_data=true). Načítám znovu.")
                    viewModel.loadUserDetails(args.userId)
                    findNavController().currentBackStackEntry?.savedStateHandle?.set("refresh_data", false)
                }
            }

        // --- ✨ ZAČÁTEK OPRAVY: Úprava logiky viditelnosti při načítání ---
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            Log.d(tag, "Observer isLoading triggered. isLoading = $isLoading")
            binding.progressBar.isVisible = isLoading

            // Skryjeme hlavní obsah, POUZE pokud jde o úplně první načítání (když ještě nemáme data)
            // Během operace blokace/odblokace obsah neskrýváme, jen deaktivujeme tlačítka.
            val isInitialLoading = isLoading && viewModel.userData.value == null
            val contentVisibility = if (isInitialLoading) View.GONE else View.VISIBLE

            // Viditelnost prvků řídíme podle 'contentVisibility'
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
            binding.editDetailsButton.visibility = contentVisibility
            binding.blockUserButton.visibility = contentVisibility

            // Poznámku a ikonu skryjeme jen při úplně prvním načítání, jinak je řídí data
            if (isInitialLoading) {
                binding.noteGroup.visibility = View.GONE
                binding.blockIcon.visibility = View.GONE
            }

            // Deaktivujeme tlačítka během JAKÉKOLI operace (hlavní načítání NEBO blokace)
            binding.editDetailsButton.isEnabled = !isLoading
            binding.blockUserButton.isEnabled = !isLoading
            Log.d(tag, "Buttons enabled = ${!isLoading}")
        }
        // --- ✨ KONEC OPRAVY ---

        viewModel.userData.observe(viewLifecycleOwner) { user ->
            Log.d(tag, "Observer userData triggered. User: $user") // Log přijatých dat
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

                // Logování stavu isDisabled PŘED aktualizací UI
                Log.d(tag, "User data received. isDisabled = ${user.isDisabled}")

                // Aktualizace stavu a UI pro blokaci
                currentUserIsDisabled = user.isDisabled // VŽDY aktualizujeme lokální stav
                binding.blockIcon.isVisible = user.isDisabled // VŽDY aktualizujeme viditelnost ikony
                Log.d(tag, "Setting blockIcon visibility to: ${user.isDisabled}")

                // VŽDY aktualizujeme text tlačítka
                if (user.isDisabled) {
                    binding.blockUserButton.setText(R.string.unblock_user)
                    Log.d(tag, "Setting button text to: Unblock")
                } else {
                    binding.blockUserButton.setText(R.string.block_user)
                    Log.d(tag, "Setting button text to: Block")
                }

                this.userAddress = user.address
                updateMapDisplay()

            } else { // Pokud user == null
                Log.d(tag, "User data is null.")
                binding.userNameValue.text = notAvailable
                binding.userAddressValue.text = notAvailable
                binding.userUidValue.text = notAvailable
                binding.userPhoneValue.text = notAvailable
                binding.userEmailValue.text = notAvailable

                binding.noteGroup.isVisible = false
                binding.blockIcon.isVisible = false // Skryjeme ikonu
                Log.d(tag, "Setting blockIcon visibility to: false (user is null)")
                binding.blockUserButton.setText(R.string.block_user) // Výchozí text
                Log.d(tag, "Setting button text to: Block (user is null)")
                currentUserIsDisabled = false // Reset stavu

                this.userAddress = null
                updateMapDisplay()
            }
        }

        viewModel.meterCount.observe(viewLifecycleOwner) { count ->
            Log.d(tag, "Observer meterCount triggered. Count: $count")
            binding.meterCountValue.text = count?.toString() ?: getString(R.string.not_available)
        }

        viewModel.lastReadingDate.observe(viewLifecycleOwner) { date ->
            Log.d(tag, "Observer lastReadingDate triggered. Date: $date")
            binding.lastReadingValue.text = date?.let { dateFormat.format(it) } ?: getString(R.string.no_reading_yet)
        }

        // Observer pro výsledek blokace (zobrazení Toast) (zůstává stejný)
        viewModel.blockResult.observe(viewLifecycleOwner) { result ->
            Log.d(tag, "Observer blockResult triggered. Result: $result")
            when (result) {
                is SaveResult.Success -> {
                    // --- ✨ ZAČÁTEK OPRAVY TOAST ZPRÁVY ---
                    // Text Toastu určíme podle toho, jaký byl stav PŘED akcí
                    // Proměnná 'currentUserIsDisabled' obsahuje stav před kliknutím na tlačítko
                    val messageResId = if (!currentUserIsDisabled) {
                        // Pokud uživatel NEBYL zablokovaný (currentUserIsDisabled == false),
                        // znamená to, že jsme ho právě ZABLOKOVALI.
                        R.string.user_blocked_successfully
                    } else {
                        // Pokud uživatel BYL zablokovaný (currentUserIsDisabled == true),
                        // znamená to, že jsme ho právě ODBLOKOVALI.
                        R.string.user_unblocked_successfully
                    }
                    Log.d(tag, "Block operation successful. MessageResId = $messageResId (based on previous state: $currentUserIsDisabled)")
                    // --- ✨ KONEC OPRAVY TOAST ZPRÁVY ---

                    Toast.makeText(context, getString(messageResId), Toast.LENGTH_SHORT).show()
                    viewModel.doneHandlingBlockResult() // Resetujeme stav pro Toast
                }
                is SaveResult.Error -> {
                    Log.w(tag, "Block operation failed: ${result.message}")
                    Toast.makeText(context, getString(R.string.user_block_failed) + ": ${result.message}", Toast.LENGTH_LONG).show()
                    viewModel.doneHandlingBlockResult() // Resetujeme stav pro Toast
                }
                SaveResult.Idle -> { Log.d(tag, "Block result is Idle.") }
                is SaveResult.Loading -> { Log.d(tag, "Block operation is Loading.") }
            }
        }
    }

    // ... (zbytek souboru UserDetailFragment.kt zůstává beze změny) ...

    override fun onMapReady(map: GoogleMap) {
        this.googleMap = map
        googleMap?.uiSettings?.isMapToolbarEnabled = false
        googleMap?.uiSettings?.isZoomControlsEnabled = true
        googleMap?.setOnMapClickListener { launchNavigation() }
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
            _binding?.mapView?.isVisible = false
            return
        }
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                geocoder.getFromLocationName(addressString, 1) { addresses ->
                    activity?.runOnUiThread {
                        if (_binding == null) return@runOnUiThread // Fragment už neexistuje
                        if (addresses.isNotEmpty()) {
                            val location = addresses[0]
                            userLatLng = LatLng(location.latitude, location.longitude)
                            updateMapLocation()
                        } else {
                            Log.w(tag, "Pro adresu '$addressString' nebyly nalezeny žádné souřadnice.")
                            binding.mapView.isVisible = false
                        }
                    }
                }
            } catch (e: Exception) { // Zachycení obecné výjimky pro jistotu
                Log.e(tag, "Chyba při geokódování (API 33+): ${e.message}", e)
                activity?.runOnUiThread { _binding?.mapView?.isVisible = false }
            }
        } else {
            @Suppress("DEPRECATION")
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val addresses: List<Address>? = geocoder.getFromLocationName(addressString, 1)
                    withContext(Dispatchers.Main) {
                        if (_binding == null) return@withContext // Fragment už neexistuje
                        if (!addresses.isNullOrEmpty()) {
                            val location = addresses[0]
                            userLatLng = LatLng(location.latitude, location.longitude)
                            updateMapLocation()
                        } else {
                            Log.w(tag, "Pro adresu '$addressString' nebyly nalezeny žádné souřadnice.")
                            binding.mapView.isVisible = false
                        }
                    }
                } catch (e: IOException) {
                    Log.e(tag, "Chyba při geokódování adresy '$addressString'", e)
                    withContext(Dispatchers.Main) { _binding?.mapView?.isVisible = false }
                } catch (e: Exception) { // Zachycení obecné výjimky
                    Log.e(tag, "Neočekávaná chyba při geokódování: ${e.message}", e)
                    withContext(Dispatchers.Main) { _binding?.mapView?.isVisible = false }
                }
            }
        }
    }

    private fun updateMapLocation() {
        // Kontrola, zda Fragment a jeho View stále existují
        if (_binding == null || googleMap == null || userLatLng == null) {
            Log.w(tag, "updateMapLocation called but binding, map or latLng is null.")
            return
        }
        val latLng = userLatLng!!
        val map = googleMap!!

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
    override fun onResume() { super.onResume(); _binding?.mapView?.onResume() }
    override fun onStart() { super.onStart(); _binding?.mapView?.onStart() }
    override fun onStop() { super.onStop(); _binding?.mapView?.onStop() }
    override fun onPause() { super.onPause(); _binding?.mapView?.onPause() }
    override fun onDestroyView() { super.onDestroyView(); _binding?.mapView?.onDestroy(); googleMap = null; _binding = null }
    override fun onLowMemory() { super.onLowMemory(); _binding?.mapView?.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); _binding?.mapView?.onSaveInstanceState(outState) }
}