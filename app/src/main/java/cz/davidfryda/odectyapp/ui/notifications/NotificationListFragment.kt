package cz.davidfryda.odectyapp.ui.notifications

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log // <-- Přidej import
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import cz.davidfryda.odectyapp.databinding.FragmentNotificationListBinding

class NotificationListFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentNotificationListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NotificationViewModel by viewModels()
    private lateinit var adapter: NotificationAdapter

    // Přidáme Handler
    private val handler = Handler(Looper.getMainLooper())
    private var markReadRunnable: Runnable? = null
    private val TAG = "NotificationListFrag" // Tag pro logování

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Fragment zobrazen.") // LOG

        adapter = NotificationAdapter()
        binding.notificationRecyclerView.adapter = adapter

        viewModel.notifications.observe(viewLifecycleOwner) {
            adapter.submitList(it)
            Log.d(TAG, "Notifikace načteny a zobrazeny (počet: ${it.size}).") // LOG
        }

        viewModel.loadNotifications()

        // Zrušíme předchozí odložené spuštění, pokud existuje
        markReadRunnable?.let { handler.removeCallbacks(it) }

        // Odložíme označení jako přečtené o pár sekund
        markReadRunnable = Runnable {
            Log.d(TAG, "Odložené spuštění markAllAsRead.") // LOG
            viewModel.markAllAsRead()
        }
        // Označíme jako přečtené po 3 sekundách od zobrazení seznamu
        handler.postDelayed(markReadRunnable!!, 3000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: Fragment ničen.") // LOG
        // Zrušíme odložené spuštění, pokud fragment zanikne dříve
        markReadRunnable?.let { handler.removeCallbacks(it) }
        _binding = null
    }
}