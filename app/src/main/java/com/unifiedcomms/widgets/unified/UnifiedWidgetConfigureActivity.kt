package com.unifiedcomms.widgets.unified

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
class UnifiedWidgetConfigureActivity : Activity() {

    @Inject
    lateinit var accountRepo: AccountRepository

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedAccountIds: MutableSet<String> = mutableSetOf()
    private var showEmail = true
    private var showCalendar = true
    private var showTasks = true
    private var emailMaxItems = 3
    private var calendarDaysAhead = 1
    private var calendarMaxEvents = 3
    private var tasksMaxItems = 3
    private var showCompletedTasks = false

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

        setContentView(R.layout.widget_unified_configure)
        setResult(Activity.RESULT_CANCELED)

        loadPreferences()
        setupUI()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("widget_unified_$widgetId", Context.MODE_PRIVATE)
        selectedAccountIds = prefs.getStringSet("account_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        showEmail = prefs.getBoolean("show_email", true)
        showCalendar = prefs.getBoolean("show_calendar", true)
        showTasks = prefs.getBoolean("show_tasks", true)
        emailMaxItems = prefs.getInt("email_max_items", 3)
        calendarDaysAhead = prefs.getInt("calendar_days_ahead", 1)
        calendarMaxEvents = prefs.getInt("calendar_max_events", 3)
        tasksMaxItems = prefs.getInt("tasks_max_items", 3)
        showCompletedTasks = prefs.getBoolean("show_completed_tasks", false)
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("widget_unified_$widgetId", Context.MODE_PRIVATE).edit()
        prefs.putStringSet("account_ids", selectedAccountIds)
        prefs.putBoolean("show_email", showEmail)
        prefs.putBoolean("show_calendar", showCalendar)
        prefs.putBoolean("show_tasks", showTasks)
        prefs.putInt("email_max_items", emailMaxItems)
        prefs.putInt("calendar_days_ahead", calendarDaysAhead)
        prefs.putInt("calendar_max_events", calendarMaxEvents)
        prefs.putInt("tasks_max_items", tasksMaxItems)
        prefs.putBoolean("show_completed_tasks", showCompletedTasks)
        prefs.apply()
    }

    private fun setupUI() {
        val accountsContainer = findViewById<LinearLayout>(R.id.accounts_container)
        val showEmailCheckbox = findViewById<CheckBox>(R.id.show_email_checkbox)
        val showCalendarCheckbox = findViewById<CheckBox>(R.id.show_calendar_checkbox)
        val showTasksCheckbox = findViewById<CheckBox>(R.id.show_tasks_checkbox)
        val showCompletedCheckbox = findViewById<CheckBox>(R.id.show_completed_checkbox)
        val emailMaxInput = findViewById<EditText>(R.id.email_max_input)
        val calendarDaysInput = findViewById<EditText>(R.id.calendar_days_input)
        val calendarMaxInput = findViewById<EditText>(R.id.calendar_max_input)
        val tasksMaxInput = findViewById<EditText>(R.id.tasks_max_input)
        val saveButton = findViewById<Button>(R.id.save_button)

        showEmailCheckbox.isChecked = showEmail
        showEmailCheckbox.setOnCheckedChangeListener { _, isChecked ->
            showEmail = isChecked
        }

        showCalendarCheckbox.isChecked = showCalendar
        showCalendarCheckbox.setOnCheckedChangeListener { _, isChecked ->
            showCalendar = isChecked
        }

        showTasksCheckbox.isChecked = showTasks
        showTasksCheckbox.setOnCheckedChangeListener { _, isChecked ->
            showTasks = isChecked
        }

        showCompletedCheckbox.isChecked = showCompletedTasks
        showCompletedCheckbox.setOnCheckedChangeListener { _, isChecked ->
            showCompletedTasks = isChecked
        }

        emailMaxInput.setText(emailMaxItems.toString())
        emailMaxInput.doAfterTextChanged { emailMaxItems = it.toString().toIntOrNull() ?: 3 }

        calendarDaysInput.setText(calendarDaysAhead.toString())
        calendarDaysInput.doAfterTextChanged { calendarDaysAhead = it.toString().toIntOrNull() ?: 1 }

        calendarMaxInput.setText(calendarMaxEvents.toString())
        calendarMaxInput.doAfterTextChanged { calendarMaxEvents = it.toString().toIntOrNull() ?: 3 }

        tasksMaxInput.setText(tasksMaxItems.toString())
        tasksMaxInput.doAfterTextChanged { tasksMaxItems = it.toString().toIntOrNull() ?: 3 }

        accountRepo.getAllActive().first().forEach { account ->
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
            UnifiedWidgetReceiver.updateWidget(this, widgetId)
            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }
}