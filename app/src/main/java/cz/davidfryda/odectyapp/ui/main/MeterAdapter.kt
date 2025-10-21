package cz.davidfryda.odectyapp.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.databinding.ListItemMeterBinding
import cz.davidfryda.odectyapp.ui.masterdetail.MasterUserDetailFragmentDirections

class MeterAdapter : ListAdapter<Meter, MeterAdapter.MeterViewHolder>(MeterDiffCallback()) {

    // Nová proměnná, která nám řekne, pro kterého uživatele data zobrazujeme.
    // Pro běžného uživatele bude null, pro mastera bude obsahovat ID.
    var ownerId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeterViewHolder {
        val binding = ListItemMeterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Předáme `ownerId` do ViewHolderu
        return MeterViewHolder(binding, ownerId)
    }

    override fun onBindViewHolder(holder: MeterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MeterViewHolder(
        private val binding: ListItemMeterBinding,
        private val ownerId: String? // ViewHolder si pamatuje, v jakém je režimu
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(meter: Meter) {
            binding.meterName.text = meter.name
            binding.lastReadingStatus.text = "Klikněte pro detail"

            val iconRes = when (meter.type) {
                "Plyn" -> R.drawable.ic_meter_gas
                "Voda" -> R.drawable.ic_meter_water
                "Elektřina" -> R.drawable.ic_meter_electricity
                else -> R.drawable.ic_launcher_foreground
            }
            binding.meterIcon.setImageResource(iconRes)

            itemView.setOnClickListener {
                // KLÍČOVÁ LOGIKA: Rozlišíme, odkud navigujeme
                if (ownerId != null) {
                    // Jsme v Master režimu (na obrazovce MasterUserDetailFragment)
                    val action = MasterUserDetailFragmentDirections
                        .actionMasterUserDetailFragmentToMeterDetailFragment(
                            meterId = meter.id,
                            userId = ownerId // Předáme ID uživatele, kterého prohlížíme
                        )
                    itemView.findNavController().navigate(action)
                } else {
                    // Jsme v běžném režimu (na obrazovce MainFragment)
                    val action = MainFragmentDirections
                        .actionMainFragmentToMeterDetailFragment(
                            meterId = meter.id
                            // userId nepředáváme, bude null (výchozí hodnota)
                        )
                    itemView.findNavController().navigate(action)
                }
            }
        }
    }
}

class MeterDiffCallback : DiffUtil.ItemCallback<Meter>() {
    override fun areItemsTheSame(oldItem: Meter, newItem: Meter): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Meter, newItem: Meter): Boolean = oldItem == newItem
}
