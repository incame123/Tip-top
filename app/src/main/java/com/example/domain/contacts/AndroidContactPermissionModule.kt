package com.example.domain.contacts

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

enum class ContactPermissionStatus {
    GRANTED,
    NOT_REQUESTED_YET,
    DENIED_CAN_RETRY,
    PERMANENTLY_DENIED_OR_REVOKED
}

/**
 * Module 1: Android Contact Permission Module.
 *
 * Strictly enforces least-privilege access to `android.permission.READ_CONTACTS` ONLY when
 * the user explicitly opts into Contact Discovery. Never requests SMS, Call Log, or Accessibility
 * permissions, and never simulates the OS permission dialog.
 */
object AndroidContactPermissionModule {

    const val READ_CONTACTS_PERMISSION = Manifest.permission.READ_CONTACTS

    fun isPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            READ_CONTACTS_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun evaluatePermissionStatus(
        context: Context,
        hasRequestedBefore: Boolean
    ): ContactPermissionStatus {
        if (isPermissionGranted(context)) {
            return ContactPermissionStatus.GRANTED
        }
        val activity = context as? Activity
        if (!hasRequestedBefore) {
            return ContactPermissionStatus.NOT_REQUESTED_YET
        }
        val shouldShowRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, READ_CONTACTS_PERMISSION)
        } ?: false

        return if (shouldShowRationale) {
            ContactPermissionStatus.DENIED_CAN_RETRY
        } else {
            ContactPermissionStatus.PERMANENTLY_DENIED_OR_REVOKED
        }
    }

    /**
     * Opens the official Android OS Application Details Settings page so the user can
     * manually grant or revoke READ_CONTACTS permission at any time.
     */
    fun openAndroidAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
