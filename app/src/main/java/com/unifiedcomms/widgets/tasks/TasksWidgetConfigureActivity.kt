package com.unifiedcomms.widgets.tasks

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
class TasksWidgetConfigureActivity : Activity() {

    @Inject
    lateinit var accountRepo: AccountRepository

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedAccountIds: MutableSet<String> = mutableSetOf()
    private var showCompletedTasks = false
    private var filterOverdue = false
    private var filterToday = false
    private var filterStarred = false
    private var maxTasks = 5

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

        setContentView(R.layout.widget_tasks_configure)
        setResult(Activity.RESULT_CANCELED)

        loadPreferences()
        setupUI()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("widget_tasks_$widgetId", Context.MODE_PRIVATE)
        selectedAccountIds = prefs.getStringSet("account_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        showCompletedTasks = prefs.getBoolean("show_completed", false)
        filterOverdue = prefs.getBoolean("filter_overdue", false)
        filterToday = prefs.getBoolean("filter_today", false)
        filterStarred = prefs.getBoolean("filter_starred", false)
        maxTasks = prefs.getInt("max_tasks", 5)
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("widget_tasks_$widgetId", Context.MODE_PRIVATE).edit()
        prefs.putStringSet("account_ids", selectedAccountIds)
        prefs.putBoolean("show_completed", showCompletedTasks)
        prefs.putBoolean("filter_overdue", filterOverdue)
        prefs.putBoolean("filter_today", filterToday)
        prefs.putBoolean("filter_starred", filterStarred)
        prefs.putInt("max_tasks", maxTasks)
        prefs.apply()
    }

    private fun setupUI() {
        val accountsContainer = findViewById<LinearLayout>(R.id.accounts_container)
        val showCompletedCheckbox = findViewById<CheckBox>(R.id.show_completed_checkbox)
        val filterOverdueCheckbox = findViewById<CheckBox>(R.id.filter_overdue_checkbox)
        val filterTodayCheckbox = findViewById<CheckBox>(R.id.filter_today_checkbox)
        val filterStarredCheckbox = findViewById<CheckBox>(R.id.filter_starred_checkbox)
        val maxTasksInput = findViewById<EditText>(R.id.max_tasks_input)
        val saveButton = findViewById<Button>(R.id.save_button)

        showCompletedCheckbox.isChecked = showCompletedTasks
        showCompletedCheckbox.setOnCheckedChangeListener { _, isChecked ->
            showCompletedTasks = isChecked
        }

        filterOverdueCheckbox.isChecked = filterOverdue
        filterOverdueCheckbox.setOnCheckedChangeListener { _, isChecked ->
            filterOverdue = isChecked
        }

        filterTodayCheckbox.isChecked = filterToday
        filterTodayCheckbox.setOnCheckedChangeListener { _, isChecked ->
            filterToday = isChecked
        }

        filterStarredCheckbox.isChecked = filterStarred
        filterStarredCheckbox.setOnCheckedChangeListener { _, isChecked ->
            filterStarred = isChecked
        }

        maxTasksInput.setText(maxTasks.toString())
        maxTasksInput.doAfterTextChanged { maxTasks = it.toString().toIntOrNull() ?: 5 }

        accountRepo.getAllActive().first().filter { it.syncConfig.syncTasks }.forEach { account ->
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
            TasksWidgetReceiver.updateWidget(this, widgetId)
            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }
}