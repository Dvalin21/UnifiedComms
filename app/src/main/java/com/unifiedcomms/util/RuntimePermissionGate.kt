package com.unifiedcomms.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object RuntimePermissionGate {

    fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val notifications: String
        get() = Manifest.permission.POST_NOTIFICATIONS
    val calendar: String
        get() = Manifest.permission.READ_CALENDAR
    val contacts: String
        get() = Manifest.permission.READ_CONTACTS
    val camera: String
        get() = Manifest.permission.CAMERA

    fun request(activity: Activity, permissions: Array<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, permissions.distinct().toTypedArray(), requestCode)
    }

    fun rationaleIfNeeded(activity: Activity, permission: String, rationaleText: CharSequence): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            activity.shouldShowRequestPermissionRationale(permission)
        ) {
            androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("Permission required")
                .setMessage(rationaleText.toString())
                .setPositiveButton("Grant") { _, _ ->
                    request(activity, arrayOf(permission), 9000 + permission.hashCode() % 1000)
                }
                .setCancelable(true)
                .show()
            return true
        }
        return false
    }
}
