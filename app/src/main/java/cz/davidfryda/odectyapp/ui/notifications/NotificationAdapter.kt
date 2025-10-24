package cz.davidfryda.odectyapp.ui.notifications

import android.util.Log // <-- Přidán import pro Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cz.davidfryda.odectyapp.data.NotificationItem
import cz.davidfryda.odectyapp.databinding.ListItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Locale

// --- NOVÝ INTERFACE ---
/**
 * Interface pro komunikaci kliknutí na notifikaci zpět do fragmentu.
 * Umožňuje oddělit logiku zobrazení (Adapter) od logiky reakce na akci (Fragment).
 */
interface NotificationClickListener {
    fun onNotificationClicked(notification: NotificationItem)
}
// --- KONEC INTERFACE ---

class NotificationAdapter(
    // --- PŘIDÁN LISTENER ---
    private val clickListener: NotificationClickListener
    // --- KONEC PŘIDÁNÍ ---
) : ListAdapter<NotificationItem, NotificationAdapter.NotificationViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ListItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // --- PŘEDÁNÍ LISTENERU DO VIEWHOLDERU ---
        return NotificationViewHolder(binding, clickListener)
        // --- KONEC PŘEDÁNÍ ---
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NotificationViewHolder(
        private val binding: ListItemNotificationBinding,
        // --- ULOŽENÍ LISTENERU ---
        private val clickListener: NotificationClickListener
        // --- KONEC ULOŽENÍ ---
    ) : RecyclerView.ViewHolder(binding.root) { // <-- Odstraněna přebytečná )
        private val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        // --- ULOŽENÍ AKTUÁLNÍ POLOŽKY ---
        private var currentNotificationItem: NotificationItem? = null
        // --- KONEC ULOŽENÍ ---
        // --- Přidán TAG pro logování ---
        private val TAG_VH = "NotifAdapterVH"
        // --- Konec TAGu ---


        // --- PŘIDÁNÍ INIT BLOKU PRO ONCLICKLISTENER ---
        init {
            itemView.setOnClickListener {
                currentNotificationItem?.let { notification ->
                    // Zavoláme metodu listeneru, POUZE pokud je položka klikatelná (má data)
                    if (itemView.isClickable) {
                        clickListener.onNotificationClicked(notification)
                        Log.d(TAG_VH, "ClickListener: Volám onNotificationClicked pro notifikaci ID: ${notification.id}") // Log volání listeneru
                    } else {
                        Log.d(TAG_VH, "ClickListener: Kliknuto, ale itemView není clickable (pravděpodobně chybí data). ID: ${notification.id}") // Log neaktivního kliknutí
                    }
                }
            }
        }
        // --- KONEC INIT BLOKU ---

        fun bind(item: NotificationItem) {
            // --- ULOŽENÍ AKTUÁLNÍ POLOŽKY ---
            currentNotificationItem = item
            // --- KONEC ULOŽENÍ ---
            binding.notificationMessage.text = item.message
            binding.notificationTimestamp.text = item.timestamp?.let { sdf.format(it) } ?: "Právě teď"

            binding.unreadIndicator.isVisible = !item.read

            // --- PŘIDÁNÍ - Nastavení, zda je položka klikatelná ---
            // Umožníme kliknutí, jen pokud máme potřebná data pro navigaci
            val canNavigate = item.readingId != null && item.userId != null && item.meterId != null
            // Logování dat a stavu canNavigate
            Log.d(TAG_VH, "Bind ID: ${item.id} - readingId=${item.readingId}, userId=${item.userId}, meterId=${item.meterId}. canNavigate=$canNavigate")
            itemView.isClickable = canNavigate
            itemView.isFocusable = canNavigate // Pro přístupnost
            // Můžeme přidat vizuální zpětnou vazbu, např. změnu popředí nebo pozadí pro neklikatelné
            // itemView.foreground = if (canNavigate) ContextCompat.getDrawable(itemView.context, R.attr.selectableItemBackground) else null // Příklad použití systémového foreground
            binding.root.alpha = if (canNavigate) 1.0f else 0.5f // Příklad ztmavení neklikatelných
            // --- KONEC PŘIDÁNÍ ---
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<NotificationItem>() {
        override fun areItemsTheSame(oldItem: NotificationItem, newItem: NotificationItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: NotificationItem, newItem: NotificationItem): Boolean {
            // Kontrolujeme i nová pole, pokud by se mohla změnit bez změny ID
            return oldItem == newItem
        }
    }
}