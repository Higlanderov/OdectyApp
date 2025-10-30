package cz.davidfryda.odectyapp.ui.notifications

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import cz.davidfryda.odectyapp.NavGraphDirections
import cz.davidfryda.odectyapp.data.NotificationItem
import cz.davidfryda.odectyapp.databinding.FragmentNotificationListBinding

class NotificationListFragment : BottomSheetDialogFragment(), NotificationClickListener {

    private var _binding: FragmentNotificationListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NotificationViewModel by viewModels()
    private lateinit var adapter: NotificationAdapter

    private val handler = Handler(Looper.getMainLooper())
    private var markReadRunnable: Runnable? = null
    private val tag = "NotificationListFrag"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(tag, "onViewCreated: Fragment zobrazen.")

        adapter = NotificationAdapter(this)
        binding.notificationRecyclerView.adapter = adapter

        viewModel.notifications.observe(viewLifecycleOwner) {
            adapter.submitList(it)
            Log.d(tag, "Notifikace načteny a zobrazeny (počet: ${it.size}).")
        }

        viewModel.loadNotifications()

        markReadRunnable?.let { handler.removeCallbacks(it) }

        markReadRunnable = Runnable {
            Log.d(tag, "Odložené spuštění markAllAsRead.")
            viewModel.markAllAsRead()
        }
        handler.postDelayed(markReadRunnable!!, 3000)
    }

    override fun onNotificationClicked(notification: NotificationItem) {
        Log.d(tag, "onNotificationClicked: Metoda volána pro notifikaci ID: ${notification.id}")
        Log.d(tag, "Notification type: ${notification.type}")

        // Rozlišení podle typu notifikace
        when (notification.type) {
            "new_reading" -> {
                // Notifikace o novém odečtu
                if (notification.readingId != null && notification.userId != null && notification.meterId != null) {
                    try {
                        val action = NavGraphDirections.actionGlobalReadingDetailFragment(
                            readingId = notification.readingId,
                            meterType = notification.meterType ?: "Obecný",
                            isMasterView = true
                        )
                        dismiss()
                        findNavController().navigate(action)
                        Log.d(tag, "Navigace na ReadingDetailFragment s readingId=${notification.readingId}.")
                    } catch (e: Exception) {
                        Log.e(tag, "Chyba při navigaci z notifikace: ${e.message}", e)
                        Toast.makeText(context, "Chyba při otevírání detailu.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.w(tag, "Nedostatek dat v notifikaci o odečtu.")
                    Toast.makeText(context, "Nelze otevřít detail, chybí informace.", Toast.LENGTH_SHORT).show()
                }
            }

            "user_registered" -> {
                // Notifikace o registraci nového uživatele
                val userName = notification.userName ?: "Nový uživatel"
                val userAddress = notification.userAddress ?: "bez adresy"

                Log.d(tag, "Notifikace o registraci: $userName ($userAddress)")
                Toast.makeText(
                    context,
                    "Nový uživatel: $userName\nAdresa: $userAddress",
                    Toast.LENGTH_LONG
                ).show()

                // Můžete přidat navigaci na seznam uživatelů, pokud existuje
                // findNavController().navigate(R.id.masterUserListFragment)

                dismiss()
            }

            else -> {
                // Neznámý typ notifikace nebo starší notifikace bez typu
                Log.w(tag, "Neznámý typ notifikace nebo chybí type field")

                // Fallback pro starší notifikace (bez type field) - pokusíme se o navigaci na odečet
                if (notification.readingId != null) {
                    try {
                        val action = NavGraphDirections.actionGlobalReadingDetailFragment(
                            readingId = notification.readingId,
                            meterType = notification.meterType ?: "Obecný",
                            isMasterView = true
                        )
                        dismiss()
                        findNavController().navigate(action)
                        Log.d(tag, "Fallback navigace na ReadingDetailFragment.")
                    } catch (e: Exception) {
                        Log.e(tag, "Chyba při fallback navigaci: ${e.message}", e)
                        Toast.makeText(context, "Nelze otevřít detail notifikace.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Tuto notifikaci nelze otevřít.", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(tag, "onDestroyView: Fragment ničen.")
        markReadRunnable?.let { handler.removeCallbacks(it) }
        _binding = null
    }
}