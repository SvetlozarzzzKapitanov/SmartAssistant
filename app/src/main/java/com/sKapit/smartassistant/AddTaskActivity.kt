package com.sKapit.smartassistant

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.button.MaterialButtonToggleGroup
import java.text.SimpleDateFormat
import java.util.*

class AddTaskActivity : AppCompatActivity() {

    private var selectedTimeInMillis: Long = System.currentTimeMillis()
    private var selectedLat: Double = 0.0
    private var selectedLng: Double = 0.0
    private var selectedAddress: String = ""
    private var isEditMode = false
    private var existingTaskId: Int = -1
    private var calculatedLeaveTime: Long? = null
    private val client = okhttp3.OkHttpClient()

    private val startAutocomplete = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val intent = result.data
            if (intent != null) {
                val place = Autocomplete.getPlaceFromIntent(intent)
                selectedAddress = place.displayName ?: ""
                selectedLat = place.location?.latitude ?: 0.0
                selectedLng = place.location?.longitude ?: 0.0
                findViewById<EditText>(R.id.inputLocation).setText(selectedAddress)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_task)

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, BuildConfig.MAPS_API_KEY)
        }

        val inputTitle = findViewById<EditText>(R.id.inputTitle)
        val inputTime = findViewById<EditText>(R.id.inputTime)
        val inputLocation = findViewById<EditText>(R.id.inputLocation)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val toggleGroupTransport = findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupTransport)
        val topAppBar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBarAdd)

        // Edit Mode Check
        isEditMode = intent.getBooleanExtra("isEdit", false)
        if (isEditMode) {
            existingTaskId = intent.getIntExtra("id", -1)
            inputTitle.setText(intent.getStringExtra("title"))

            selectedTimeInMillis = intent.getLongExtra("time", System.currentTimeMillis())
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
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

            btnSave.text = "Обнови задачата"
            topAppBar.title = "Редактиране"
        }

        topAppBar.setNavigationOnClickListener { finish() }
        inputTime.setOnClickListener { showTimePicker(inputTime) }
        inputLocation.setOnClickListener { launchPlacesAutocomplete() }

        btnSave.setOnClickListener {
            if (selectedAddress.isEmpty()) return@setOnClickListener

            val travelMode = when (toggleGroupTransport.checkedButtonId) {
                R.id.btnWalk -> "walking"
                R.id.btnTransit -> "transit"
                else -> "driving"
            }

            val resultIntent = Intent().apply {
                putExtra("id", existingTaskId)
                putExtra("title", inputTitle.text.toString())
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
        val fields = listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .setCountries(listOf("BG"))
            .build(this)
        startAutocomplete.launch(intent)
    }

    private fun showTimePicker(timeEditText: EditText) {
        val calendar = Calendar.getInstance()
        if (isEditMode) calendar.timeInMillis = selectedTimeInMillis

        TimePickerDialog(this, { _, hour, minute ->
            val displayTime = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
            timeEditText.setText(displayTime)

            val targetCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }
            selectedTimeInMillis = targetCalendar.timeInMillis
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }
}