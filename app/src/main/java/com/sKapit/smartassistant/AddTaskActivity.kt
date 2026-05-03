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
import okhttp3.*
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
    private val networkManager = NetworkManager()
    private var selectedFullAddress: String = ""
    private var bestTimeDialog: BottomSheetDialog? = null
    private val startAutocomplete = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val intent = result.data
            if (intent != null) {
                val place = Autocomplete.getPlaceFromIntent(intent)
                selectedAddress = place.displayName ?: ""

                // Store full formatted address for BestTime API
                selectedFullAddress = place.formattedAddress ?: selectedAddress

                selectedLat = place.location?.latitude ?: 0.0
                selectedLng = place.location?.longitude ?: 0.0
                findViewById<EditText>(R.id.inputLocation).setText(selectedAddress)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_task)

        // Initialize Places SDK if not already done
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, BuildConfig.MAPS_API_KEY)
        }

        val inputTitle = findViewById<EditText>(R.id.inputTitle)
        val inputTime = findViewById<EditText>(R.id.inputTime)
        val inputLocation = findViewById<EditText>(R.id.inputLocation)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val toggleGroupTransport = findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupTransport)
        val topAppBar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBarAdd)
        val btnBestTime = findViewById<Button>(R.id.btnBestTime)

        btnBestTime.setOnClickListener {
            startBestTimeAnalysis()
        }

        // Fill fields if in edit mode
        isEditMode = intent.getBooleanExtra("isEdit", false)
        if (isEditMode) {
            existingTaskId = intent.getIntExtra("id", -1)
            inputTitle.setText(intent.getStringExtra("title"))

            selectedTimeInMillis = intent.getLongExtra("time", System.currentTimeMillis())
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            inputTime.setText(sdf.format(Date(selectedTimeInMillis)))

            selectedAddress = intent.getStringExtra("location") ?: ""
            inputLocation.setText(selectedAddress)
            selectedLat = intent.getDoubleExtra("lat", 0.0)
            selectedLng = intent.getDoubleExtra("lng", 0.0)

            val travelMode = intent.getStringExtra("travelMode") ?: "driving"
            val checkedId = when (travelMode) {
                "walking" -> R.id.btnWalk
                "transit" -> R.id.btnTransit
                else -> R.id.btnDrive
            }
            toggleGroupTransport.check(checkedId)

            btnSave.text = "Обнови планирането"
            topAppBar.title = "Редактиране"
        }

        topAppBar.setNavigationOnClickListener { finish() }

        inputTime.setOnClickListener { showDateTimePicker(inputTime) }
        inputLocation.setOnClickListener { launchPlacesAutocomplete() }

        btnSave.setOnClickListener {
            val title = inputTitle.text.toString().trim()
            
            if (title.isEmpty()) {
                Toast.makeText(this, "Моля, въведете име на събитието", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (selectedAddress.isEmpty()) {
                Toast.makeText(this, "Моля, изберете локация", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val travelMode = when (toggleGroupTransport.checkedButtonId) {
                R.id.btnWalk -> "walking"
                R.id.btnTransit -> "transit"
                else -> "driving"
            }

            val resultIntent = Intent().apply {
                putExtra("id", existingTaskId)
                putExtra("title", title)
                putExtra("time", selectedTimeInMillis)
                putExtra("location", selectedAddress)
                putExtra("lat", selectedLat)
                putExtra("lng", selectedLng)
                putExtra("travelMode", travelMode)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun launchPlacesAutocomplete() {
        val fields = listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION, Place.Field.FORMATTED_ADDRESS)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .setCountries(listOf("BG"))
            .build(this)
        startAutocomplete.launch(intent)
    }

    // Date and Time Picker
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

    private fun showBestTimeBottomSheet(hourlyList: List<HourlyForecast>) {
        // НОВО: Ако вече има отворен диалог, не прави нищо
        if (bestTimeDialog?.isShowing == true) {
            return
        }

        bestTimeDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_best_time_bottom_sheet, null)
        bestTimeDialog?.setContentView(view)

        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerHourlyForecast)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)

        // ПОДАВАМЕ selectedTimeInMillis на адаптера!
        val adapter = HourlyForecastAdapter(hourlyList, selectedTimeInMillis) { selectedHour ->
            bestTimeDialog?.dismiss()
            bestTimeDialog = null // Изчистваме референцията

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

        if (targetIndex != -1) {
            recyclerView.scrollToPosition(targetIndex)
        } else if (hourlyList.isNotEmpty()) {
            recyclerView.scrollToPosition(0)
        }

        // Изчистваме референцията, ако потребителят затвори диалога ръчно (чрез плъзгане надолу)
        bestTimeDialog?.setOnDismissListener {
            bestTimeDialog = null
        }

        bestTimeDialog?.show()
    }

    private fun startBestTimeAnalysis() {
        if (selectedAddress.isEmpty()) {
            Toast.makeText(this, "Първо изберете локация!", Toast.LENGTH_SHORT).show()
            return
        }

        // ПРОВЕРКА ЗА КЛЮЧ
        if (BuildConfig.BEST_TIME_API_KEY.isEmpty()) {
            Toast.makeText(this, "BestTime API ключът не е конфигуриран!", Toast.LENGTH_SHORT).show()
            return
        }

        networkManager.fetchBestTimeData(
            venueName = selectedAddress,
            venueAddress = selectedFullAddress, // ТУК ВЕЧЕ ПОДАВАМЕ АДРЕСА
            targetTimeInMillis = selectedTimeInMillis,
            onSuccess = { hourlyList ->
                runOnUiThread {
                    showBestTimeBottomSheet(hourlyList)
                }
            },
            onError = { errorMessage ->
                runOnUiThread {
                    Toast.makeText(this@AddTaskActivity, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
