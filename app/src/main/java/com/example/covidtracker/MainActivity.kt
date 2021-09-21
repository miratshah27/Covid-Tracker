package com.example.covidtracker

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.google.gson.GsonBuilder
import com.robinhood.spark.SparkView
import com.robinhood.ticker.TickerUtils
import com.robinhood.ticker.TickerView
import org.angmarch.views.NiceSpinner
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

public class MainActivity : AppCompatActivity() {

    companion object{
        const val BASE_URL = "https://api.covidtracking.com/v1/"
        const val ALL_STATES = "All (Nationwide)"
    }

    private lateinit var currentlyShownData: List<CovidData>
    private lateinit var adapter: CovidSparkAdapter
    private lateinit var perStateDailyData: Map<String, List<CovidData>>
    private lateinit var nationalDailyData: List<CovidData>

    private lateinit var  tvMetricsLabel: TickerView
    private lateinit var tvDateLabel: TextView
    private lateinit var radioButtonPositive: RadioButton
    private lateinit var radioButtonNegative: RadioButton
    private lateinit var radioButtonDeath: RadioButton
    private lateinit var radioButtonMax: RadioButton
    private lateinit var radioButtonMonth: RadioButton
    private lateinit var radioButtonWeek: RadioButton
    private lateinit var sparkView: SparkView
    private lateinit var radioGroupTimeSelection : RadioGroup
    private lateinit var radioGroupMetricSection: RadioGroup
    private lateinit var spinnerSelect: NiceSpinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.title = getString(R.string.app_description)

        tvMetricsLabel = findViewById(R.id.tickerView)
        tvDateLabel = findViewById(R.id.tvDateLabel)
        radioButtonPositive = findViewById(R.id.radioButtonPositive)
        radioButtonNegative = findViewById(R.id.radioButtonNegative)
        radioButtonDeath = findViewById(R.id.radioButtonDeath)
        radioButtonMax = findViewById(R.id.radioButtonMax)
        radioButtonMonth = findViewById(R.id.radioButtonMonth)
        radioButtonWeek = findViewById(R.id.radioButtonWeek)
        sparkView = findViewById(R.id.sparkView)
        radioGroupTimeSelection = findViewById(R.id.radioGroupTimeSelection)
        radioGroupMetricSection = findViewById(R.id.radioGroupMetricSection)
        spinnerSelect = findViewById(R.id.spinnerSelect)

        val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create()
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        val covidService = retrofit.create(CovidServices::class.java)
        covidService.getNationalData().enqueue(object : Callback<List<CovidData>>{
            override fun onResponse(
                call: Call<List<CovidData>>,
                response: Response<List<CovidData>>
            ) {
                val nationalData = response.body()
                if(nationalData==null) return
                setupEventListeners()
                nationalDailyData = nationalData.reversed()
                updateDisplayWithData(nationalDailyData)
            }

            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {}

        })

        covidService.getStatesData().enqueue(object : Callback<List<CovidData>>{
            override fun onResponse(
                call: Call<List<CovidData>>,
                response: Response<List<CovidData>>
            ) {
                val statesData = response.body()
                if(statesData==null) return
                perStateDailyData = statesData.reversed().groupBy { it.state }
                updateSpinnerWithStateData(perStateDailyData.keys)
            }

            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {}

        })
    }

    private fun updateSpinnerWithStateData(stateNames: Set<String>) {
        val stateAbbreviationList = stateNames.toMutableList()
        stateAbbreviationList.sort()
        stateAbbreviationList.add(0, ALL_STATES)

        spinnerSelect.attachDataSource(stateAbbreviationList)
        spinnerSelect.setOnSpinnerItemSelectedListener { parent, _, position, _ ->
            val selectedState = parent.getItemAtPosition(position) as String
            val selectedData = perStateDailyData[selectedState] ?: nationalDailyData
            updateDisplayWithData(selectedData)
        }
    }

    private fun setupEventListeners() {
        tvMetricsLabel.setCharacterLists(TickerUtils.provideNumberList())

        sparkView.isScrubEnabled = true
        sparkView.setScrubListener {
            if(it is CovidData){
                updateInfoForDate(it)
            }
        }

        radioGroupTimeSelection.setOnCheckedChangeListener { _,checkedId ->
            adapter.daysAgo = when (checkedId) {
                R.id.radioButtonMax -> TimeScale.MAX
                R.id.radioButtonMonth -> TimeScale.MONTH
                else -> TimeScale.WEEK
            }
            adapter.notifyDataSetChanged()
        }
        radioGroupMetricSection.setOnCheckedChangeListener{_,checkedId ->
            when (checkedId){
                R.id.radioButtonPositive -> updateDisplayMetric(Metric.POSITIVE)
                R.id.radioButtonNegative -> updateDisplayMetric(Metric.NEGATIVE)
                R.id.radioButtonDeath -> updateDisplayMetric(Metric.DEATH)
            }
        }
    }

    private fun updateDisplayMetric(metric: Metric){
        val colorRes = when(metric){
            Metric.POSITIVE -> R.color.colorPositive
            Metric.NEGATIVE -> R.color.colorNegative
            Metric.DEATH -> R.color.colorDeath
        }
        @ColorInt val colorInt = ContextCompat.getColor(this, colorRes)
        sparkView.lineColor = colorInt
        tvMetricsLabel.setTextColor(colorInt)

        adapter.metric = metric
        adapter.notifyDataSetChanged()

        updateInfoForDate(currentlyShownData.last())
    }

    private fun updateDisplayWithData(dailyData: List<CovidData>) {
        currentlyShownData = dailyData

        adapter = CovidSparkAdapter(dailyData)
        sparkView.adapter = adapter

        updateDisplayMetric(Metric.POSITIVE)

        radioButtonPositive.isChecked = true
        radioButtonMax.isChecked = true
    }

    private fun updateInfoForDate(covidData: CovidData) {
        val numCases = when(adapter.metric){
            Metric.POSITIVE -> covidData.positiveIncrease
            Metric.NEGATIVE -> covidData.negativeIncrease
            Metric.DEATH -> covidData.deathIncrease
        }
        tvMetricsLabel.text = NumberFormat.getInstance().format(numCases)
        val outputDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        tvDateLabel.text = outputDateFormat.format(covidData.dateChecked)
    }
}