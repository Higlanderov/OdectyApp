package cz.davidfryda.odectyapp.ui.location

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.Location
import cz.davidfryda.odectyapp.databinding.ItemLocationBinding

class LocationAdapter(
    private val onLocationClick: (Location) -> Unit,
    private val onEditClick: (Location) -> Unit,
    private val onDeleteClick: (Location) -> Unit,
    private val onSetDefaultClick: (Location) -> Unit
) : ListAdapter<Location, LocationAdapter.LocationViewHolder>(LocationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val binding = ItemLocationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LocationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LocationViewHolder(
        private val binding: ItemLocationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(location: Location) {
            binding.apply {
                // Název
                locationNameTextView.text = location.name

                // Adresa
                locationAddressTextView.text = location.address

                // Poznámka (zobraz jen pokud není prázdná)
                if (location.note.isNotBlank()) {
                    locationNoteTextView.text = location.note
                    locationNoteTextView.isVisible = true
                } else {
                    locationNoteTextView.isVisible = false
                }

                // Výchozí místo
                defaultChip.isVisible = location.isDefault

                // Počet měřáků
                val meterCountText = when (location.meterCount) {
                    0 -> root.context.getString(R.string.no_meters)
                    1 -> "1 ${root.context.getString(R.string.meter)}"
                    else -> "${location.meterCount} ${root.context.getString(R.string.meters)}"
                }
                meterCountTextView.text = meterCountText

                // Kliknutí na kartu
                root.setOnClickListener {
                    onLocationClick(location)
                }

                // Menu tlačítko
                menuButton.setOnClickListener { view ->
                    val popup = PopupMenu(view.context, view)
                    popup.menuInflater.inflate(R.menu.location_item_menu, popup.menu)

                    // Skryj "Nastavit jako výchozí" pokud už je výchozí
                    popup.menu.findItem(R.id.action_set_default)?.isVisible = !location.isDefault

                    popup.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.action_edit -> {
                                onEditClick(location)
                                true
                            }
                            R.id.action_set_default -> {
                                onSetDefaultClick(location)
                                true
                            }
                            R.id.action_delete -> {
                                onDeleteClick(location)
                                true
                            }
                            else -> false
                        }
                    }
                    popup.show()
                }
            }
        }
    }

    private class LocationDiffCallback : DiffUtil.ItemCallback<Location>() {
        override fun areItemsTheSame(oldItem: Location, newItem: Location): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Location, newItem: Location): Boolean {
            return oldItem == newItem
        }
    }
}