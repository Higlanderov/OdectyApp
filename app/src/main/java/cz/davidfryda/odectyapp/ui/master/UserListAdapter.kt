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

        // Kliknutí na info tlačítko
        holder.infoButton.setOnClickListener {
            onInfoClickListener?.invoke(item)
        }
    }

    // === ZAČÁTEK ÚPRAVY ViewHolderu ===
    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userName: TextView = itemView.findViewById(R.id.userName)
        private val userAddress: TextView = itemView.findViewById(R.id.userAddress)
        private val statusTextView: TextView = itemView.findViewById(R.id.statusTextView)
        private val blockedIcon: ImageView = itemView.findViewById(R.id.blockedIcon)
        val infoButton: ImageButton = itemView.findViewById(R.id.infoButton)
        // Případně ikona nového uživatele, pokud existuje
        // private val newUserIcon: ImageView = itemView.findViewById(R.id.newUserIcon)


        fun bind(userWithStatus: UserWithStatus) {
            val user = userWithStatus.user
            userName.text = "${user.name} ${user.surname}"
            userAddress.text = user.address

            // Zobrazení ikony zablokovaného uživatele
            blockedIcon.isVisible = user.isDisabled

            // Nastavení statusu odečtu (nová logika)
            val readingsDone = userWithStatus.readingsDone
            val totalMeters = userWithStatus.totalMeters
            val context = itemView.context

            if (totalMeters == 0) {
                // Případ, kdy uživatel nemá přiřazené žádné měřáky
                statusTextView.text = "Žádné měřáky"
                // Neutrální barva (můžete změnit na ?attr/colorOnSurfaceVariant pro přizpůsobení tématu)
                statusTextView.setTextColor(
                    context.getColor(android.R.color.darker_gray)
                )
            } else if (readingsDone == totalMeters) {
                // Hotovo: "✓ Potvrzeno (3/3)"
                statusTextView.text = context.getString(
                    R.string.status_confirmed_format,
                    readingsDone,
                    totalMeters
                )
                statusTextView.setTextColor(
                    context.getColor(android.R.color.holo_green_dark)
                )
            } else {
                // Čeká se: "⏳ Čekající (2/3)"
                statusTextView.text = context.getString(
                    R.string.status_pending_format,
                    readingsDone,
                    totalMeters
                )
                statusTextView.setTextColor(
                    context.getColor(android.R.color.holo_orange_dark)
                )
            }
        }
    }
    // === KONEC ÚPRAVY ViewHolderu ===

    // === ZAČÁTEK ÚPRAVY DiffCallbacku ===
    class UserDiffCallback : DiffUtil.ItemCallback<UserWithStatus>() {
        override fun areItemsTheSame(oldItem: UserWithStatus, newItem: UserWithStatus): Boolean {
            return oldItem.user.uid == newItem.user.uid
        }

        override fun areContentsTheSame(oldItem: UserWithStatus, newItem: UserWithStatus): Boolean {
            // Nyní porovnáváme celý objekt.
            // To je důležité, aby se seznam aktualizoval, když se změní
            // stav z (2/3) na (3/3).
            return oldItem == newItem
        }
    }
    // === KONEC ÚPRAVY DiffCallbacku ===
}