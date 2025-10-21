package cz.davidfryda.odectyapp.ui.master

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.UserData
import cz.davidfryda.odectyapp.databinding.ListItemUserBinding

class UserListAdapter : ListAdapter<UserWithStatus, UserListAdapter.UserViewHolder>(UserWithStatusDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ListItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class UserViewHolder(private val binding: ListItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(userWithStatus: UserWithStatus) {
            val user = userWithStatus.user
            binding.userName.text = "${user.name} ${user.surname}"
            binding.userAddress.text = user.address

            // KLÍČOVÁ LOGIKA: Nastavení barvy karty
            val backgroundColor = if (userWithStatus.hasReadingForCurrentMonth) {
                ContextCompat.getColor(itemView.context, R.color.status_ok)
            } else {
                ContextCompat.getColor(itemView.context, R.color.status_pending)
            }
            (itemView as com.google.android.material.card.MaterialCardView).setCardBackgroundColor(backgroundColor)


            itemView.setOnClickListener {
                val action = MasterUserListFragmentDirections.actionMasterUserListFragmentToMasterUserDetailFragment(user.uid)
                itemView.findNavController().navigate(action)
            }
        }
    }
}

class UserWithStatusDiffCallback : DiffUtil.ItemCallback<UserWithStatus>() {
    override fun areItemsTheSame(oldItem: UserWithStatus, newItem: UserWithStatus): Boolean {
        return oldItem.user.uid == newItem.user.uid
    }
    override fun areContentsTheSame(oldItem: UserWithStatus, newItem: UserWithStatus): Boolean {
        return oldItem == newItem
    }
}
