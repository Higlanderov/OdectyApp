package cz.davidfryda.odectyapp.ui.master

import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.navigation.fragment.navArgs
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.databinding.FragmentUserDetailBinding
import java.text.SimpleDateFormat
import java.util.Locale

class UserDetailFragment : Fragment() {

    private var _binding: FragmentUserDetailBinding? = null
    private val binding get() = _binding!!

    private val args: UserDetailFragmentArgs by navArgs()
    private val viewModel: UserDetailViewModel by viewModels()

    private val dateFormat = SimpleDateFormat("d. M. yyyy HH:mm", Locale.getDefault())
    private val TAG = "UserDetailFragment" // Pro logování chyb

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.user_detail_title)
        viewModel.loadUserDetails(args.userId)

        // Sledujeme stav načítání
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
            val contentVisibility = if (isLoading) View.GONE else View.VISIBLE
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
            binding.divider.visibility = contentVisibility
            binding.meterCountLabel.visibility = contentVisibility
            binding.meterCountValue.visibility = contentVisibility
            binding.lastReadingLabel.visibility = contentVisibility
            binding.lastReadingValue.visibility = contentVisibility
        }

        // Sledujeme data uživatele
        viewModel.userData.observe(viewLifecycleOwner) { user ->
            val notAvailable = getString(R.string.not_available)
            if (user != null) {
                binding.userNameValue.text = "${user.name} ${user.surname}"
                binding.userAddressValue.text = user.address
                binding.userUidValue.text = user.uid

                // --- START ZMĚNY: Telefon ---
                if (user.phoneNumber.isNotEmpty()) {
                    binding.userPhoneValue.text = user.phoneNumber
                    binding.userPhoneValue.setOnClickListener {
                        try {
                            // Intent pro otevření dialeru s číslem
                            val intent = Intent(Intent.ACTION_DIAL, "tel:${user.phoneNumber}".toUri())
                            startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Log.e(TAG, "Nelze otevřít aplikaci pro volání.", e)
                            Toast.makeText(context, "Nelze nalézt aplikaci pro volání.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    binding.userPhoneValue.text = notAvailable
                    binding.userPhoneValue.setOnClickListener(null) // Odebrání listeneru
                }
                // --- KONEC ZMĚNY: Telefon ---

                // --- START ZMĚNY: E-mail ---
                if (user.email.isNotEmpty()) {
                    binding.userEmailValue.text = user.email
                    binding.userEmailValue.setOnClickListener {
                        try {
                            // Intent pro otevření e-mailového klienta
                            val intent = Intent(Intent.ACTION_SENDTO)
                            intent.data = "mailto:${user.email}".toUri() // Použijeme mailto:
                            startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Log.e(TAG, "Nelze otevřít e-mailového klienta.", e)
                            Toast.makeText(context, "Nelze nalézt e-mailového klienta.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    binding.userEmailValue.text = notAvailable
                    binding.userEmailValue.setOnClickListener(null) // Odebrání listeneru
                }
                // --- KONEC ZMĚNY: E-mail ---

            } else {
                // Pokud data selžou, zobrazíme N/A a odstraníme listenery
                binding.userNameValue.text = notAvailable
                binding.userAddressValue.text = notAvailable
                binding.userUidValue.text = args.userId
                binding.userPhoneValue.text = notAvailable
                binding.userEmailValue.text = notAvailable
                binding.userPhoneValue.setOnClickListener(null)
                binding.userEmailValue.setOnClickListener(null)
            }
        }

        // Sledujeme počet měřáků
        viewModel.meterCount.observe(viewLifecycleOwner) { count ->
            binding.meterCountValue.text = count?.toString() ?: getString(R.string.not_available)
        }

        // Sledujeme datum posledního odečtu
        viewModel.lastReadingDate.observe(viewLifecycleOwner) { date ->
            binding.lastReadingValue.text = date?.let { dateFormat.format(it) } ?: getString(R.string.no_reading_yet)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}