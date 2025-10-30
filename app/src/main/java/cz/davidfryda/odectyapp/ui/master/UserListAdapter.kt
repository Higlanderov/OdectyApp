package cz.davidfryda.odectyapp.ui.master

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.databinding.ListItemUserBinding
import java.util.concurrent.TimeUnit

class UserListAdapter : ListAdapter<UserWithStatus, UserListAdapter.UserViewHolder>(UserWithStatusDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ListItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class UserViewHolder(private val binding: ListItemUserBinding) : RecyclerView.ViewHolder(binding.root) {

        private val infoButton: ImageButton = binding.infoButton

        fun bind(userWithStatus: UserWithStatus) {
            val user = userWithStatus.user
            binding.userName.text = itemView.context.getString(R.string.user_full_name, user.name, user.surname)
            binding.userAddress.text = user.address

            // ✨ Zobrazit hvězdičku pro nové uživatele (prvních 48 hodin od vytvoření účtu)
            val isNewUser = user.createdAt?.let { timestamp ->
                val creationTime = timestamp.toDate().time
                val currentTime = System.currentTimeMillis()
                val hoursSinceCreation = TimeUnit.MILLISECONDS.toHours(currentTime - creationTime)
                hoursSinceCreation < 48
            } ?: false

            binding.newUserIcon.isVisible = isNewUser

            // ✨ NOVÉ: Zobrazit ikonu zámku pro zablokované uživatele
            binding.blockedIcon.isVisible = user.isDisabled

            // KLÍČOVÁ LOGIKA: Nastavení barvy karty
            val backgroundColor = if (userWithStatus.hasReadingForCurrentMonth) {
                ContextCompat.getColor(itemView.context, R.color.status_ok)
            } else {
                ContextCompat.getColor(itemView.context, R.color.status_pending)
            }
            (itemView as com.google.android.material.card.MaterialCardView).setCardBackgroundColor(backgroundColor)

            // Kliknutí na CELOU KARTU stále vede do seznamu měřáků
            itemView.setOnClickListener {
                val action = MasterUserListFragmentDirections.actionMasterUserListFragmentToMasterUserDetailFragment(user.uid)
                itemView.findNavController().navigate(action)
            }

            // Listener pro Info tlačítko
            infoButton.setOnClickListener {
                // Navigace na nový UserDetailFragment
                val action = MasterUserListFragmentDirections.actionMasterUserListFragmentToUserDetailFragment(user.uid)
                itemView.findNavController().navigate(action)
            }
        }
    }
}

// DiffUtil zůstává stejný
class UserWithStatusDiffCallback : DiffUtil.ItemCallback<UserWithStatus>() {
    override fun areItemsTheSame(oldItem: UserWithStatus, newItem: UserWithStatus): Boolean {
        return oldItem.user.uid == newItem.user.uid
    }

    override fun areContentsTheSame(oldItem: UserWithStatus, newItem: UserWithStatus): Boolean {
        return oldItem == newItem
    }
}