package cz.davidfryda.odectyapp.ui.main

import android.graphics.Color // <-- PŘIDÁN IMPORT
import android.os.Bundle
import android.util.TypedValue // <-- PŘIDÁN IMPORT
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import cz.davidfryda.odectyapp.R
import cz.davidfryda.odectyapp.data.Reading
import cz.davidfryda.odectyapp.databinding.FragmentChartBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ChartFragment : Fragment() {
    private var _binding: FragmentChartBinding? = null
    private val binding get() = _binding!!
    private val args: ChartFragmentArgs by navArgs()
    private val viewModel: MeterDetailViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Zjistíme, pro kterého uživatele máme načíst data
        val targetUserId = if (args.userId != null) {
            args.userId!!
        } else {
            Firebase.auth.currentUser!!.uid
        }

        // Voláme ViewModel se správným userId
        viewModel.initializeForUser(targetUserId, args.meterId, requireContext())

        viewModel.readingHistory.observe(viewLifecycleOwner) { history ->
            // KLÍČOVÁ LOGIKA: Zkontrolujeme, jestli máme dostatek dat
            if (history.size < 2) {
                binding.detailBarChart.isVisible = false
                binding.emptyChartText.isVisible = true
            } else {
                binding.detailBarChart.isVisible = true
                binding.emptyChartText.isVisible = false
                setupBarChart(history)
            }
        }
    }

    // === ZAČÁTEK ÚPRAVY (Nová pomocná funkce) ===
    /**
     * Získá barvu z aktuálního tématu (theme attribute).
     */
    private fun getThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
    // === KONEC ÚPRAVY ===

    private fun setupBarChart(readings: List<Reading>) {
        // === ZAČÁTEK ÚPRAVY (Získání barev z tématu) ===
        val textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        val gridColor = getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        // === KONEC ÚPRAVY ===

        val sortedReadings = readings.sortedBy { it.timestamp }
        val entries = sortedReadings.mapIndexed { index, reading ->
            BarEntry(index.toFloat(), reading.finalValue?.toFloat() ?: 0f)
        }

        val dataSet = BarDataSet(entries, "Historie odečtů").apply {
            color = requireContext().getColor(R.color.colorPrimary)
            valueTextSize = 10f
            valueTextColor = textColor // <-- PŘIDÁNO
        }

        val xAxis = binding.detailBarChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.valueFormatter = IndexAxisValueFormatter(sortedReadings.map {
            it.timestamp?.let { date -> SimpleDateFormat("d.M", Locale.getDefault()).format(date) } ?: ""
        })
        xAxis.textColor = textColor // <-- PŘIDÁNO
        xAxis.axisLineColor = textColor // <-- PŘIDÁNO

        binding.detailBarChart.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            axisRight.isEnabled = false
            legend.isEnabled = false

            // === ZAČÁTEK ÚPRAVY (Nastavení barev os) ===
            axisLeft.textColor = textColor
            axisLeft.axisLineColor = textColor
            axisLeft.gridColor = gridColor

            legend.textColor = textColor
            description.textColor = textColor
            // === KONEC ÚPRAVY ===

            setFitBars(true)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}