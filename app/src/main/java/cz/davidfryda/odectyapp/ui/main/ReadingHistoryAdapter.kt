package cz.davidfryda.odectyapp.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.Reading
import cz.davidfryda.odectyapp.databinding.ListItemReadingBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ReadingHistoryAdapter : ListAdapter<Reading, ReadingHistoryAdapter.ReadingViewHolder>(ReadingDiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd. MM. yyyy HH:mm", Locale.getDefault())
    var meterType: String? = null
    var isMasterView: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReadingViewHolder {
        val binding = ListItemReadingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReadingViewHolder(binding, dateFormat)
    }

    override fun onBindViewHolder(holder: ReadingViewHolder, position: Int) {
        holder.bind(getItem(position), meterType, isMasterView)
    }

    class ReadingViewHolder(
        private val binding: ListItemReadingBinding,
        private val dateFormat: SimpleDateFormat
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(reading: Reading, meterType: String?, isMasterView: Boolean) {
            val unit = when (meterType) {
                "Voda" -> "m³"
                "Plyn" -> "m³"
                "Elektřina" -> "kWh"
                else -> ""
            }
            binding.readingValue.text = itemView.context.getString(R.string.reading_value_with_unit, reading.finalValue.toString(), unit)
            binding.readingDate.text = reading.timestamp?.let { dateFormat.format(it) } ?: itemView.context.getString(R.string.status_waiting_for_sync)

            // Zobrazíme ikonu, pokud odečet není synchronizovaný
            binding.syncStatusIcon.isVisible = !reading.isSynced

            binding.adminEditIcon.isVisible = reading.editedByAdmin

            // Kliknutí povolíme jen na synchronizované odečty
            itemView.isClickable = reading.isSynced
            itemView.setOnClickListener {
                if (reading.isSynced) {
                    val action = MeterDetailFragmentDirections.actionMeterDetailFragmentToReadingDetailFragment(
                        readingId = reading.id,
                        meterType = meterType ?: "Obecný",
                        isMasterView = isMasterView
                    )
                    itemView.findNavController().navigate(action)
                }
            }
        }
    }
}

class ReadingDiffCallback : DiffUtil.ItemCallback<Reading>() {
    override fun areItemsTheSame(oldItem: Reading, newItem: Reading): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Reading, newItem: Reading): Boolean = oldItem == newItem
}