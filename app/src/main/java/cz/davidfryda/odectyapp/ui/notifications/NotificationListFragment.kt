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
        Log.d(tag, "onNotificationClicked: Metoda volána pro notifikaci ID: ${notification.id}, Reading ID: ${notification.readingId}")
        if (notification.readingId != null && notification.userId != null && notification.meterId != null) {
            try {
                val action = NavGraphDirections.actionGlobalReadingDetailFragment(
                    readingId = notification.readingId,
                    meterType = notification.meterType ?: "Obecný",
                    isMasterView = true
                )
                dismiss()
                findNavController().navigate(action)
                Log.d(tag, "onNotificationClicked: Navigace na ReadingDetailFragment spuštěna s readingId=${notification.readingId}.")
            } catch (e: Exception) {
                Log.e(tag, "Chyba při navigaci z notifikace: ${e.message}", e)
                Toast.makeText(context, "Chyba při otevírání detailu.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w(tag, "onNotificationClicked: Nedostatek dat v notifikaci pro navigaci (readingId=${notification.readingId}, userId=${notification.userId}, meterId=${notification.meterId}). Navigace nebude provedena.")
            Toast.makeText(context, "Nelze otevřít detail, chybí informace v notifikaci.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(tag, "onDestroyView: Fragment ničen.")
        markReadRunnable?.let { handler.removeCallbacks(it) }
        _binding = null
    }
}