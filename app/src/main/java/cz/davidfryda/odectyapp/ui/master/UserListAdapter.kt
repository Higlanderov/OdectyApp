package cz.davidfryda.odectyapp.ui.master

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cz.davidfryda.odectyapp.R

class UserListAdapter : ListAdapter<UserWithStatus, UserListAdapter.UserViewHolder>(UserDiffCallback()) {

    // Callbacky
    private var onUserClickListener: ((UserWithStatus) -> Unit)? = null
    private var onInfoClickListener: ((UserWithStatus) -> Unit)? = null

    fun setOnUserClickListener(listener: (UserWithStatus) -> Unit) {
        onUserClickListener = listener
    }

    fun setOnInfoClickListener(listener: (UserWithStatus) -> Unit) {
        onInfoClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)

        // Kliknutí na celou kartu
        holder.itemView.setOnClickListener {
            onUserClickListener?.invoke(item)
        }

        // Kliknutí na info button
        holder.infoButton.setOnClickListener {
            onInfoClickListener?.invoke(item)
        }
    }

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userName: TextView = itemView.findViewById(R.id.userName)
        private val userAddress: TextView = itemView.findViewById(R.id.userAddress)
        private val statusTextView: TextView = itemView.findViewById(R.id.statusTextView)
        private val blockedIcon: ImageView = itemView.findViewById(R.id.blockedIcon)
        val infoButton: ImageButton = itemView.findViewById(R.id.infoButton)

        fun bind(userWithStatus: UserWithStatus) {
            val user = userWithStatus.user
            userName.text = "${user.name} ${user.surname}"
            userAddress.text = user.address

            // Zobrazení ikony zablokovaného uživatele
            blockedIcon.isVisible = user.isDisabled

            // Nastavení statusu odečtu
            if (userWithStatus.hasReadingForCurrentMonth) {
                statusTextView.text = "✓ Provedeno"
                statusTextView.setTextColor(
                    itemView.context.getColor(android.R.color.holo_green_dark)
                )
            } else {
                statusTextView.text = "⏳ Čekající"
                statusTextView.setTextColor(
                    itemView.context.getColor(android.R.color.holo_orange_dark)
                )
            }
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<UserWithStatus>() {
        override fun areItemsTheSame(oldItem: UserWithStatus, newItem: UserWithStatus): Boolean {
            return oldItem.user.uid == newItem.user.uid
        }

        override fun areContentsTheSame(oldItem: UserWithStatus, newItem: UserWithStatus): Boolean {
            return oldItem == newItem
        }
    }
}
