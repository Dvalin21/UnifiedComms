package com.unifiedcomms.widgets.calendar

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.unifiedcomms.R
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.repository.AccountRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CalendarWidgetConfigureActivity : Activity() {

    @Inject
    lateinit var accountRepo: AccountRepository

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedAccountIds: MutableSet<String> = mutableSetOf()
    private var showAllDayEvents = true
    private var daysAhead = 7
    private var maxEvents = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        }
        
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(R.layout.widget_calendar_configure)
        setResult(Activity.RESULT_CANCELED)

        loadPreferences()
        setupUI()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("widget_calendar_$widgetId", Context.MODE_PRIVATE)
        selectedAccountIds = prefs.getStringSet("account_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        showAllDayEvents = prefs.getBoolean("show_all_day", true)
        daysAhead = prefs.getInt("days_ahead", 7)
        maxEvents = prefs.getInt("max_events", 5)
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("widget_calendar_$widgetId", Context.MODE_PRIVATE).edit()
        prefs.putStringSet("account_ids", selectedAccountIds)
        prefs.putBoolean("show_all_day", showAllDayEvents)
        prefs.putInt("days_ahead", daysAhead)
        prefs.putInt("max_events", maxEvents)
        prefs.apply()
    }

    private fun setupUI() {
        val accountsContainer = findViewById<LinearLayout>(R.id.accounts_container)
        val showAllDayCheckbox = findViewById<CheckBox>(R.id.show_all_day_checkbox)
        val daysAheadInput = findViewById<EditText>(R.id.days_ahead_input)
        val maxEventsInput = findViewById<EditText>(R.id.max_events_input)
        val saveButton = findViewById<Button>(R.id.save_button)

        showAllDayCheckbox.isChecked = showAllDayEvents
        showAllDayCheckbox.setOnCheckedChangeListener { _, isChecked ->
            showAllDayEvents = isChecked
        }

        daysAheadInput.setText(daysAhead.toString())
        daysAheadInput.doAfterTextChanged { daysAhead = it.toString().toIntOrNull() ?: 7 }

        maxEventsInput.setText(maxEvents.toString())
        maxEventsInput.doAfterTextChanged { maxEvents = it.toString().toIntOrNull() ?: 5 }

        accountRepo.getAllActive().first().filter { it.syncConfig.syncCalendar }.forEach { account ->
            val checkBox = CheckBox(this).apply {
                text = "${account.name} (${account.email})"
                isChecked = selectedAccountIds.contains(account.id)
                tag = account.id
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedAccountIds.add(account.id) else selectedAccountIds.remove(account.id)
                }
            }
            accountsContainer.addView(checkBox)
        }

        saveButton.setOnClickListener {
            savePreferences()
            CalendarWidgetReceiver.updateWidget(this, widgetId)
            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }
}