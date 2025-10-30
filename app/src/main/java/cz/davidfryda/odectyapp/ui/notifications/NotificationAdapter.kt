package cz.davidfryda.odectyapp.ui.notifications

import android.util.Log
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

interface NotificationClickListener {
    fun onNotificationClicked(notification: NotificationItem)
}

class NotificationAdapter(
    private val clickListener: NotificationClickListener
) : ListAdapter<NotificationItem, NotificationAdapter.NotificationViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ListItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding, clickListener)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NotificationViewHolder(
        private val binding: ListItemNotificationBinding,
        private val clickListener: NotificationClickListener
    ) : RecyclerView.ViewHolder(binding.root) {
        private val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        private var currentNotificationItem: NotificationItem? = null
        private val tagVh = "NotifAdapterVH"  // ✨ ZMĚNA: TAG_VH → tagVh

        init {
            itemView.setOnClickListener {
                currentNotificationItem?.let { notification ->
                    if (itemView.isClickable) {
                        clickListener.onNotificationClicked(notification)
                        Log.d(tagVh, "ClickListener: Volám onNotificationClicked pro notifikaci ID: ${notification.id}")
                    } else {
                        Log.d(tagVh, "ClickListener: Kliknuto, ale itemView není clickable (pravděpodobně chybí data). ID: ${notification.id}")
                    }
                }
            }
        }

        fun bind(item: NotificationItem) {
            currentNotificationItem = item
            binding.notificationMessage.text = item.message
            binding.notificationTimestamp.text = item.timestamp?.let { sdf.format(it) } ?: "Právě teď"

            binding.unreadIndicator.isVisible = !item.read

            val canNavigate = item.readingId != null && item.userId != null && item.meterId != null
            Log.d(tagVh, "Bind ID: ${item.id} - readingId=${item.readingId}, userId=${item.userId}, meterId=${item.meterId}. canNavigate=$canNavigate")
            itemView.isClickable = canNavigate
            itemView.isFocusable = canNavigate
            binding.root.alpha = if (canNavigate) 1.0f else 0.5f
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<NotificationItem>() {
        override fun areItemsTheSame(oldItem: NotificationItem, newItem: NotificationItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: NotificationItem, newItem: NotificationItem): Boolean {
            return oldItem == newItem
        }
    }
}