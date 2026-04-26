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
import java.util.*

class AddTaskActivity : AppCompatActivity() {

    private var selectedTimeInMillis: Long = System.currentTimeMillis()
    private var selectedLat: Double = 0.0
    private var selectedLng: Double = 0.0
    private var selectedAddress: String = ""

    private val startAutocomplete = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val intent = result.data
            if (intent != null) {
                val place = Autocomplete.getPlaceFromIntent(intent)
                selectedAddress = place.name ?: ""
                selectedLat = place.latLng?.latitude ?: 0.0
                selectedLng = place.latLng?.longitude ?: 0.0

                findViewById<EditText>(R.id.inputLocation).setText(selectedAddress)
            }
        } else if (result.resultCode == AutocompleteActivity.RESULT_ERROR) {
            val intent = result.data
            if (intent != null) {
                val status = Autocomplete.getStatusFromIntent(intent)
                Log.e("PLACES_ERROR", "Status: ${status.statusMessage}")
                Toast.makeText(this, "Google Error: ${status.statusMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_task)

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }

        val inputTitle = findViewById<EditText>(R.id.inputTitle)
        val inputTime = findViewById<EditText>(R.id.inputTime)
        val inputLocation = findViewById<EditText>(R.id.inputLocation)
        val btnSave = findViewById<Button>(R.id.btnSave)

        inputTime.isFocusable = false
        inputTime.setOnClickListener { showTimePicker(inputTime) }

        inputLocation.isFocusable = false
        inputLocation.setOnClickListener {
            val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
            val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                .setCountry("BG") // Ограничава търсенето в България
                .build(this)

            startAutocomplete.launch(intent)
        }

        btnSave.setOnClickListener {
            if (selectedAddress.isEmpty()) {
                Toast.makeText(this, "Please select a location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent().apply {
                putExtra("title", inputTitle.text.toString())
                putExtra("time", selectedTimeInMillis)
                putExtra("location", selectedAddress)
                putExtra("lat", selectedLat)
                putExtra("lng", selectedLng)
            }
            setResult(RESULT_OK, intent)
            finish()
        }
    }

    private fun showTimePicker(timeEditText: EditText) {
        val calendar = Calendar.getInstance()
        val timePickerDialog = TimePickerDialog(this, { _, hour, minute ->
            timeEditText.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute))
            val targetCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            selectedTimeInMillis = targetCalendar.timeInMillis
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
        timePickerDialog.show()
    }
}