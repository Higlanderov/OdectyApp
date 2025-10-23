package cz.davidfryda.odectyapp.ui.main

import android.util.Log // Import pro logování
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu // Import PopupMenu
import android.widget.Toast // Import Toast
import androidx.core.view.isVisible
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.Meter
import cz.davidfryda.odectyapp.databinding.ListItemMeterBinding
import cz.davidfryda.odectyapp.ui.masterdetail.MasterUserDetailFragmentDirections // Import pro Master navigaci

/**
 * Listener pro interakce s položkou měřáku v RecyclerView.
 * Zahrnuje akce pro úpravu, smazání (pro uživatele) a přidání popisu (pro mastera).
 */
interface MeterInteractionListener {
    fun onEditMeterClicked(meter: Meter) // Voláno, když uživatel klikne na "Upravit název"
    fun onDeleteMeterClicked(meter: Meter) // Voláno, když uživatel klikne na "Smazat měřák"
    fun onAddDescriptionClicked(meter: Meter, ownerUserId: String) // Voláno, když master klikne na "Přidat/Upravit popis"
}

class MeterAdapter : ListAdapter<Meter, MeterAdapter.MeterViewHolder>(MeterDiffCallback()) {

    // ID uživatele, jehož měřáky zobrazujeme (null pro přihlášeného uživatele, ID pro mastera)
    var ownerId: String? = null
    // Listener pro komunikaci s fragmentem
    var listener: MeterInteractionListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeterViewHolder {
        val binding = ListItemMeterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Předáme listener a ownerId do ViewHolderu
        return MeterViewHolder(binding, listener, ownerId)
    }

    override fun onBindViewHolder(holder: MeterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MeterViewHolder(
        private val binding: ListItemMeterBinding,
        private val listener: MeterInteractionListener?,
        private val ownerId: String? // ViewHolder si pamatuje, v jakém je režimu a ID vlastníka
    ) : RecyclerView.ViewHolder(binding.root) {

        private lateinit var currentMeter: Meter // Uchováme si aktuální měřák pro listenery

        init {
            // Nastavení listeneru pro tlačítko menu
            binding.meterOptionsMenuButton.setOnClickListener { view ->
                // Zobrazíme vyskakovací menu, pokud máme listener
                if (listener != null) {
                    showPopupMenu(view)
                } else {
                    Log.w("MeterAdapter", "Listener is null, cannot show popup menu.")
                }
            }
        }

        fun bind(meter: Meter) {
            currentMeter = meter // Uložíme si aktuální měřák

            // Rozlišení master vs. běžný uživatel pro zobrazení názvů
            if (ownerId != null) { // Master režim
                binding.meterName.text = meter.masterDescription ?: meter.name // Priorita je popis od mastera
                if (meter.masterDescription != null && meter.masterDescription != meter.name) { // Zobrazíme původní, jen pokud se liší od popisu
                    binding.originalMeterName.text = itemView.context.getString(R.string.original_meter_name_label, meter.name) // Použití string resource
                    binding.originalMeterName.isVisible = true // Zobrazíme původní název
                } else {
                    binding.originalMeterName.isVisible = false // Skryjeme původní název
                }
                binding.meterOptionsMenuButton.isVisible = true // Master může vždy přidat popis
            } else { // Běžný uživatelský režim
                binding.meterName.text = meter.name // Zobrazíme jen název měřáku
                binding.originalMeterName.isVisible = false // Skryjeme TextView pro původní název
                binding.meterOptionsMenuButton.isVisible = true // Uživatel může upravit/smazat
            }

            // Nastavení ikony měřáku
            val iconRes = when (meter.type) {
                "Plyn" -> R.drawable.ic_meter_gas
                "Voda" -> R.drawable.ic_meter_water
                "Elektřina" -> R.drawable.ic_meter_electricity
                else -> R.drawable.ic_launcher_foreground // Výchozí ikona
            }
            binding.meterIcon.setImageResource(iconRes)

            // Placeholder text (může být nahrazen reálným stavem později)
            binding.lastReadingStatus.text = itemView.context.getString(R.string.click_for_detail)

            // Kliknutí na celou položku pro navigaci do detailu
            itemView.setOnClickListener {
                 // KLÍČOVÁ LOGIKA NAVIGACE: Rozlišíme, odkud navigujeme
                val navController = itemView.findNavController()
                val currentDestinationId = navController.currentDestination?.id

                try {
                    if (currentDestinationId == R.id.masterUserDetailFragment && ownerId != null) {
                        // Jsme v Master režimu (na obrazovce MasterUserDetailFragment)
                        if (ownerId.isBlank()) {
                             Log.e("MeterAdapter", "Cannot navigate to detail in master mode, ownerId is blank for meter: ${meter.id}")
                             Toast.makeText(itemView.context, "Chyba: Chybí ID uživatele.", Toast.LENGTH_SHORT).show()
                             return@setOnClickListener
                        }

                        Log.d("MeterAdapter", "Navigating from MasterUserDetail to MeterDetail (meterId: ${meter.id}, userId: $ownerId)")
                        val action = MasterUserDetailFragmentDirections
                            .actionMasterUserDetailFragmentToMeterDetailFragment(
                                meterId = meter.id,
                                userId = ownerId // Předáme ID uživatele, kterého prohlížíme
                            )
                        navController.navigate(action)

                    } else if (currentDestinationId == R.id.mainFragment && ownerId == null) {
                        // Jsme v běžném režimu (na obrazovce MainFragment)
                        // V tomto režimu se spoléháme na to, že MeterDetailFragment si zjistí ID aktuálního uživatele sám.
                        Log.d("MeterAdapter", "Navigating from MainFragment to MeterDetail (meterId: ${meter.id})")
                        val action = MainFragmentDirections
                            .actionMainFragmentToMeterDetailFragment(
                                meterId = meter.id
                                // userId se nepředává, bude null (výchozí hodnota v MeterDetailFragment a signál pro "použij aktuálního uživatele")
                            )
                        navController.navigate(action)
                    } else {
                        // Fallback nebo logování chyby, pokud jsme na neočekávané obrazovce
                        Log.w("MeterAdapter", "Unexpected navigation state: currentDestination=$currentDestinationId, ownerId=$ownerId")
                    }
                } catch (e: Exception) { // Zachycení obecnější výjimky pro jistotu
                    // Zachycení chyby, pokud akce není nalezena nebo nastane jiný navigační problém
                    Log.e("MeterAdapter", "Navigation failed for meter ${meter.id} from destination $currentDestinationId", e)
                    Toast.makeText(itemView.context, "Chyba navigace.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        /**
         * Zobrazí vyskakovací menu s příslušnými akcemi podle režimu (master/uživatel).
         * @param view Pohled (ImageButton), ke kterému se menu ukotví.
         */
        private fun showPopupMenu(view: View) {
            val popup = PopupMenu(view.context, view)
            if (ownerId != null) { // Master režim
                Log.d("MeterAdapter", "Showing master menu for meter: ${currentMeter.id}")
                popup.menuInflater.inflate(R.menu.master_meter_options_menu, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_add_description -> {
                            // Zavoláme listener s ID uživatele, kterému měřák patří
                            Log.d("MeterAdapter", "Add description clicked for meter: ${currentMeter.id}, owner: $ownerId")
                            listener?.onAddDescriptionClicked(currentMeter, ownerId)
                            true
                        }
                        else -> false
                    }
                }
            } else { // Běžný uživatelský režim
                Log.d("MeterAdapter", "Showing user menu for meter: ${currentMeter.id}")
                popup.menuInflater.inflate(R.menu.meter_options_menu, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit_meter -> {
                            Log.d("MeterAdapter", "Edit meter clicked for meter: ${currentMeter.id}")
                            listener?.onEditMeterClicked(currentMeter)
                            true
                        }
                        R.id.action_delete_meter -> {
                            Log.d("MeterAdapter", "Delete meter clicked for meter: ${currentMeter.id}")
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

// DiffUtil pro efektivní aktualizace RecyclerView
class MeterDiffCallback : DiffUtil.ItemCallback<Meter>() {
    override fun areItemsTheSame(oldItem: Meter, newItem: Meter): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Meter, newItem: Meter): Boolean = oldItem == newItem
}
