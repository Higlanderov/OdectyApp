package cz.davidfryda.odectyapp.ui.meter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.Meter
import cz.davidfryda.odectyapp.databinding.ItemMeterBinding

class MeterAdapter(
    private val onMeterClick: (Meter) -> Unit,
    private val onEditClick: (Meter) -> Unit,
    private val onDeleteClick: (Meter) -> Unit
) : ListAdapter<Meter, MeterAdapter.MeterViewHolder>(MeterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeterViewHolder {
        val binding = ItemMeterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MeterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MeterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MeterViewHolder(
        private val binding: ItemMeterBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(meter: Meter) {
            binding.meterNameTextView.text = meter.name
            binding.meterTypeChip.text = meter.type

            // Ikona podle typu měřáku
            val iconRes = when (meter.type) {
                "Elektřina" -> R.drawable.ic_electricity
                "Plyn" -> R.drawable.ic_gas
                "Voda studená", "Voda teplá" -> R.drawable.ic_water
                "Teplo" -> R.drawable.ic_heating
                else -> R.drawable.ic_meter
            }
            binding.meterIcon.setImageResource(iconRes)
            binding.meterTypeChip.setChipIconResource(iconRes)

            // Kliknutí na kartu
            binding.root.setOnClickListener {
                onMeterClick(meter)
            }

            // Menu s více možnostmi
            binding.moreButton.setOnClickListener {
                showPopupMenu(it, meter)
            }
        }

        private fun showPopupMenu(view: android.view.View, meter: Meter) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.meter_item_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit_meter -> {
                        onEditClick(meter)
                        true
                    }
                    R.id.action_delete_meter -> {
                        onDeleteClick(meter)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    class MeterDiffCallback : DiffUtil.ItemCallback<Meter>() {
        override fun areItemsTheSame(oldItem: Meter, newItem: Meter): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Meter, newItem: Meter): Boolean {
            return oldItem == newItem
        }
    }
}