package com.unifiedcomms.widgets.email

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import com.unifiedcomms.R
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.repository.AccountRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class EmailWidgetConfigureActivity : Activity() {

    @Inject
    lateinit var accountRepo: AccountRepository

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedAccountIds: MutableSet<String> = mutableSetOf()
    private var showUnreadOnly = false
    private var maxItems = 5

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

        setContentView(R.layout.widget_email_configure)
        setResult(Activity.RESULT_CANCELED)

        loadPreferences()
        setupUI()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("widget_email_$widgetId", Context.MODE_PRIVATE)
        selectedAccountIds = prefs.getStringSet("account_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        showUnreadOnly = prefs.getBoolean("show_unread_only", false)
        maxItems = prefs.getInt("max_items", 5)
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("widget_email_$widgetId", Context.MODE_PRIVATE).edit()
        prefs.putStringSet("account_ids", selectedAccountIds)
        prefs.putBoolean("show_unread_only", showUnreadOnly)
        prefs.putInt("max_items", maxItems)
        prefs.apply()
    }

    private fun setupUI() {
        val accountsContainer = findViewById<LinearLayout>(R.id.accounts_container)
        val showUnreadCheckbox = findViewById<CheckBox>(R.id.show_unread_checkbox)
        val maxItemsInput = findViewById<android.widget.EditText>(R.id.max_items_input)
        val saveButton = findViewById<Button>(R.id.save_button)

        showUnreadCheckbox.isChecked = showUnreadOnly
        showUnreadCheckbox.setOnCheckedChangeListener { _, isChecked ->
            showUnreadOnly = isChecked
        }

        maxItemsInput.setText(maxItems.toString())
        maxItemsInput.doAfterTextChanged { maxItems = it.toString().toIntOrNull() ?: 5 }

        // Load accounts
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
            EmailWidgetReceiver.updateWidget(this, widgetId)
            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }
}