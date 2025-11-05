package cz.davidfryda.odectyapp.ui.main

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.Meter
import cz.davidfryda.odectyapp.databinding.ListItemMeterBinding
import cz.davidfryda.odectyapp.ui.location.LocationDetailFragmentDirections

/**
 * Listener pro interakce s položkou měřáku v RecyclerView.
 * Zahrnuje akce pro úpravu, smazání (pro uživatele) a přidání popisu (pro mastera).
 */
interface MeterInteractionListener {
    fun onEditMeterClicked(meter: Meter)
    fun onDeleteMeterClicked(meter: Meter)
    fun onAddDescriptionClicked(meter: Meter, ownerUserId: String)
}

class MeterAdapter : ListAdapter<Meter, MeterAdapter.MeterViewHolder>(MeterDiffCallback()) {

    var ownerId: String? = null
    var isMasterOwnProfile: Boolean = false
    var listener: MeterInteractionListener? = null

    // ✨ NOVÉ: LocationId pro navigaci
    var currentLocationId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeterViewHolder {
        val binding = ListItemMeterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MeterViewHolder(binding, listener, ownerId, isMasterOwnProfile, currentLocationId)
    }

    override fun onBindViewHolder(holder: MeterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MeterViewHolder(
        private val binding: ListItemMeterBinding,
        private val listener: MeterInteractionListener?,
        private val ownerId: String?,
        private val isMasterOwnProfile: Boolean,
        private val currentLocationId: String? // ✨ NOVÝ parametr
    ) : RecyclerView.ViewHolder(binding.root) {

        private lateinit var currentMeter: Meter

        init {
            binding.meterOptionsMenuButton.setOnClickListener { view ->
                if (listener != null) {
                    showPopupMenu(view)
                } else {
                    Log.w("MeterAdapter", "Listener is null, cannot show popup menu.")
                }
            }
        }

        fun bind(meter: Meter) {
            currentMeter = meter

            if (ownerId != null) {
                if (isMasterOwnProfile) {
                    binding.meterName.text = meter.name
                    binding.originalMeterName.isVisible = false
                } else {
                    binding.meterName.text = meter.masterDescription ?: meter.name
                    if (meter.masterDescription != null && meter.masterDescription != meter.name) {
                        binding.originalMeterName.text = itemView.context.getString(R.string.original_meter_name_label, meter.name)
                        binding.originalMeterName.isVisible = true
                    } else {
                        binding.originalMeterName.isVisible = false
                    }
                }
                binding.meterOptionsMenuButton.isVisible = true
            } else {
                binding.meterName.text = meter.name
                binding.originalMeterName.isVisible = false
                binding.meterOptionsMenuButton.isVisible = true
            }

            val iconRes = when (meter.type) {
                "Plyn" -> R.drawable.ic_meter_gas
                "Voda" -> R.drawable.ic_meter_water
                "Elektřina" -> R.drawable.ic_meter_electricity
                else -> R.drawable.ic_launcher_foreground
            }
            binding.meterIcon.setImageResource(iconRes)

            binding.lastReadingStatus.text = itemView.context.getString(R.string.click_for_detail)

            itemView.setOnClickListener {
                val navController = itemView.findNavController()

                try {
                    // Použijeme currentLocationId pokud je k dispozici, jinak fallback na meter.locationId
                    val locationId = currentLocationId ?: meter.locationId

                    if (locationId.isBlank()) {
                        Log.e("MeterAdapter", "Cannot navigate, locationId is blank for meter: ${'$'}{meter.id}")
                        Toast.makeText(itemView.context, "Chyba: Chybí ID lokace.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    Log.d("MeterAdapter", "Navigating to MeterDetail (meterId: ${'$'}{meter.id}, userId: $ownerId, locationId: $locationId)")

                    // Navigace se liší podle toho, zda jsme v kontextu mastera nebo běžného uživatele.
                    // Pro zjednodušení použijeme jednu akci, která zvládne oba případy.
                    // Předpokládáme, že navigační graf má globální akci nebo že jsme ve fragmentu, který ji definuje.
                    // V tomto případě je `LocationDetailFragmentDirections` bezpečnější, protože `LocationDetailFragment` zůstal.
                    val action = LocationDetailFragmentDirections.actionLocationDetailFragmentToMeterDetailFragment(
                            meterId = meter.id,
                            userId = ownerId,  // Bude null pro běžného uživatele, což je správně
                            locationId = locationId
                        )
                    navController.navigate(action)

                } catch (e: Exception) {
                    Log.e("MeterAdapter", "Navigation failed for meter ${'$'}{meter.id}", e)
                    Toast.makeText(itemView.context, "Chyba navigace.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        private fun showPopupMenu(view: View) {
            val popup = PopupMenu(view.context, view)

            if (ownerId != null && isMasterOwnProfile) {
                Log.d("MeterAdapter", "Showing user menu for master's own meter: ${'$'}{currentMeter.id}")
                popup.menuInflater.inflate(R.menu.meter_options_menu, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit_meter -> {
                            Log.d("MeterAdapter", "Edit meter clicked for master's own meter: ${'$'}{currentMeter.id}")
                            listener?.onEditMeterClicked(currentMeter)
                            true
                        }
                        R.id.action_delete_meter -> {
                            Log.d("MeterAdapter", "Delete meter clicked for master's own meter: ${'$'}{currentMeter.id}")
                            listener?.onDeleteMeterClicked(currentMeter)
                            true
                        }
                        else -> false
                    }
                }
            } else if (ownerId != null) {
                Log.d("MeterAdapter", "Showing master menu for other user's meter: ${'$'}{currentMeter.id}")
                popup.menuInflater.inflate(R.menu.master_meter_options_menu, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_add_description -> {
                            Log.d("MeterAdapter", "Add description clicked for meter: ${'$'}{currentMeter.id}, owner: $ownerId")
                            listener?.onAddDescriptionClicked(currentMeter, ownerId)
                            true
                        }
                        else -> false
                    }
                }
            } else {
                Log.d("MeterAdapter", "Showing user menu for meter: ${'$'}{currentMeter.id}")
                popup.menuInflater.inflate(R.menu.meter_options_menu, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit_meter -> {
                            Log.d("MeterAdapter", "Edit meter clicked for meter: ${'$'}{currentMeter.id}")
                            listener?.onEditMeterClicked(currentMeter)
                            true
                        }
                        R.id.action_delete_meter -> {
                            Log.d("MeterAdapter", "Delete meter clicked for meter: ${'$'}{currentMeter.id}")
                            listener?.onDeleteMeterClicked(currentMeter)
                            true
                        }
                        else -> false
                    }
                }
            }
            popup.show()
        }
    }
}

class MeterDiffCallback : DiffUtil.ItemCallback<Meter>() {
    override fun areItemsTheSame(oldItem: Meter, newItem: Meter): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Meter, newItem: Meter): Boolean = oldItem == newItem
}
