package com.sKapit.smartassistant

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.*

class AddTaskActivity : AppCompatActivity() {

    private var selectedTimeInMillis: Long = System.currentTimeMillis()
    private var selectedLat: Double = 0.0
    private var selectedLng: Double = 0.0
    private var selectedAddress: String = ""
    private var isEditMode = false
    private var existingTaskId: Int = -1
    private lateinit var networkManager: NetworkManager
    private var selectedFullAddress: String = ""
    private var bestTimeDialog: BottomSheetDialog? = null
    
    private var selectedStartLat: Double? = null
    private var selectedStartLng: Double? = null
    private var selectedStartAddress: String? = null

    private val destinationAutocompleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val intent = result.data
            if (intent != null) {
                val place = Autocomplete.getPlaceFromIntent(intent)
                selectedAddress = place.displayName ?: ""
                selectedFullAddress = place.formattedAddress ?: selectedAddress
                selectedLat = place.location?.latitude ?: 0.0
                selectedLng = place.location?.longitude ?: 0.0
                findViewById<EditText>(R.id.inputLocation).setText(selectedAddress)
            }
        }
    }

    private val startLocationAutocompleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val intent = result.data
            if (intent != null) {
                val place = Autocomplete.getPlaceFromIntent(intent)
                selectedStartAddress = place.formattedAddress ?: place.displayName ?: ""
                selectedStartLat = place.location?.latitude
                selectedStartLng = place.location?.longitude
                findViewById<EditText>(R.id.inputStartLocation).setText(selectedStartAddress)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_task)

        networkManager = NetworkManager(this)

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, BuildConfig.MAPS_API_KEY)
        }

        val inputTitle = findViewById<EditText>(R.id.inputTitle)
        val inputTime = findViewById<EditText>(R.id.inputTime)
        val inputDuration = findViewById<EditText>(R.id.inputDuration)
        val inputLocation = findViewById<EditText>(R.id.inputLocation)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val toggleGroupTransport = findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupTransport)
        val topAppBar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBarAdd)
        val btnBestTime = findViewById<Button>(R.id.btnBestTime)
        val inputStartLocation = findViewById<EditText>(R.id.inputStartLocation)

        btnBestTime.setOnClickListener { startBestTimeAnalysis() }

        isEditMode = intent.getBooleanExtra("isEdit", false)
        if (isEditMode) {
            existingTaskId = intent.getIntExtra("id", -1)
            inputTitle.setText(intent.getStringExtra("title"))

            selectedTimeInMillis = intent.getLongExtra("time", System.currentTimeMillis())
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            inputTime.setText(sdf.format(Date(selectedTimeInMillis)))
            
            val duration = intent.getIntExtra("duration", 30)
            inputDuration.setText(duration.toString())

            selectedAddress = intent.getStringExtra("location") ?: ""
            inputLocation.setText(selectedAddress)
            selectedLat = intent.getDoubleExtra("lat", 0.0)
            selectedLng = intent.getDoubleExtra("lng", 0.0)

            selectedStartAddress = intent.getStringExtra("startLocation")
            selectedStartLat = intent.getDoubleExtra("startLat", 0.0).takeIf { it != 0.0 }
            selectedStartLng = intent.getDoubleExtra("startLng", 0.0).takeIf { it != 0.0 }

            if (!selectedStartAddress.isNullOrEmpty()) {
                inputStartLocation.setText(selectedStartAddress)
            }

            val travelMode = intent.getStringExtra("travelMode") ?: TravelMode.DRIVING.value
            val checkedId = when (travelMode) {
                TravelMode.WALKING.value -> R.id.btnWalk
                TravelMode.TRANSIT.value -> R.id.btnTransit
                else -> R.id.btnDrive
            }
            toggleGroupTransport.check(checkedId)

            btnSave.text = getString(R.string.btn_update_planning)
            topAppBar.title = getString(R.string.title_edit_planning)
        }

        topAppBar.setNavigationOnClickListener { finish() }
        inputTime.setOnClickListener { showDateTimePicker(inputTime) }
        inputLocation.setOnClickListener { launchAutocomplete(destinationAutocompleteLauncher) }
        inputStartLocation.setOnClickListener { launchAutocomplete(startLocationAutocompleteLauncher) }

        btnSave.setOnClickListener {
            val title = inputTitle.text.toString().trim()
            val durationStr = inputDuration.text.toString().trim()
            
            if (title.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_enter_title), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (selectedAddress.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_select_location), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val duration = durationStr.toIntOrNull() ?: 30

            val travelMode = when (toggleGroupTransport.checkedButtonId) {
                R.id.btnWalk -> TravelMode.WALKING.value
                R.id.btnTransit -> TravelMode.TRANSIT.value
                else -> TravelMode.DRIVING.value
            }

            val hasManualStart = !selectedStartAddress.isNullOrBlank() && 
                               selectedStartLat != null && 
                               selectedStartLng != null

            val resultIntent = Intent().apply {
                putExtra("id", existingTaskId)
                putExtra("title", title)
                putExtra("time", selectedTimeInMillis)
                putExtra("duration", duration)
                putExtra("location", selectedAddress)
                putExtra("lat", selectedLat)
                putExtra("lng", selectedLng)
                putExtra("travelMode", travelMode)

                if (hasManualStart) {
                    putExtra("startLocation", selectedStartAddress)
                    putExtra("startLat", selectedStartLat ?: 0.0)
                    putExtra("startLng", selectedStartLng ?: 0.0)
                } else {
                    putExtra("startLocation", "")
                    putExtra("startLat", 0.0)
                    putExtra("startLng", 0.0)
                }
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun launchAutocomplete(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        val fields = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.LOCATION,
            Place.Field.FORMATTED_ADDRESS
        )

        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .setCountries(listOf("BG"))
            .build(this)

        launcher.launch(intent)
    }

    private fun showDateTimePicker(timeEditText: EditText) {
        val currentCalendar = Calendar.getInstance()
        if (isEditMode || selectedTimeInMillis > 0) {
            currentCalendar.timeInMillis = selectedTimeInMillis
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        val targetCalendar = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        selectedTimeInMillis = targetCalendar.timeInMillis
                        val displayFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                        timeEditText.setText(displayFormat.format(targetCalendar.time))
                    },
                    currentCalendar.get(Calendar.HOUR_OF_DAY),
                    currentCalendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            currentCalendar.get(Calendar.YEAR),
            currentCalendar.get(Calendar.MONTH),
            currentCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun startBestTimeAnalysis() {
        if (selectedAddress.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_select_location_first), Toast.LENGTH_SHORT).show()
            return
        }

        if (BuildConfig.BEST_TIME_API_KEY.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_api_key_not_configured), Toast.LENGTH_SHORT).show()
            return
        }

        networkManager.fetchBestTimeData(
            venueName = selectedAddress,
            venueAddress = selectedFullAddress,
            targetTimeInMillis = selectedTimeInMillis,
            onSuccess = { hourlyList ->
                runOnUiThread { showBestTimeBottomSheet(hourlyList) }
            },
            onError = { errorMessage ->
                runOnUiThread { Toast.makeText(this@AddTaskActivity, errorMessage, Toast.LENGTH_SHORT).show() }
            }
        )
    }

    private fun showBestTimeBottomSheet(hourlyList: List<HourlyForecast>) {
        if (bestTimeDialog?.isShowing == true) return

        bestTimeDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_best_time_bottom_sheet, null)
        bestTimeDialog?.setContentView(view)

        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerHourlyForecast)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)

        val adapter = HourlyForecastAdapter(hourlyList, selectedTimeInMillis) { selectedHour ->
            bestTimeDialog?.dismiss()
            bestTimeDialog = null

            val cal = Calendar.getInstance().apply { timeInMillis = selectedTimeInMillis }
            cal.set(Calendar.HOUR_OF_DAY, selectedHour)
            selectedTimeInMillis = cal.timeInMillis

            TimePickerDialog(
                this,
                { _, finalHour, minute ->
                    cal.set(Calendar.HOUR_OF_DAY, finalHour)
                    cal.set(Calendar.MINUTE, minute)
                    selectedTimeInMillis = cal.timeInMillis
                    val displayFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    findViewById<EditText>(R.id.inputTime).setText(displayFormat.format(cal.time))
                },
                selectedHour,
                0,
                true
            ).show()
        }

        recyclerView.adapter = adapter

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val targetIndex = hourlyList.indexOfFirst { it.hour == currentHour }
        if (targetIndex != -1) recyclerView.scrollToPosition(targetIndex)
        else if (hourlyList.isNotEmpty()) recyclerView.scrollToPosition(0)

        bestTimeDialog?.setOnDismissListener { bestTimeDialog = null }
        bestTimeDialog?.show()
    }
}