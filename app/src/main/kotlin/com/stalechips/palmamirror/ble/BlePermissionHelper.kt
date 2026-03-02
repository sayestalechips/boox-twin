package com.stalechips.palmamirror.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Helper for checking BLE-related permissions and hardware availability.
 */
object BlePermissionHelper {

    /**
     * Check if the device supports BLE.
     */
    fun hasBleSupport(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    /**
     * Check if Bluetooth is enabled.
     */
    fun isBluetoothEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.isEnabled == true
    }

    /**
     * Get list of permissions required for BLE operations.
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    /**
     * Get list of permissions that have not been granted.
     */
    fun getMissingPermissions(context: Context): List<String> {
        return getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if all required permissions are granted.
     */
    fun hasAllPermissions(context: Context): Boolean {
        return getMissingPermissions(context).isEmpty()
    }

    /**
     * Describe what's wrong with the current BLE setup.
     * Returns null if everything is fine.
     */
    fun diagnose(context: Context): BleIssue? {
        if (!hasBleSupport(context)) {
            return BleIssue.NO_BLE_SUPPORT
        }
        if (!isBluetoothEnabled(context)) {
            return BleIssue.BLUETOOTH_DISABLED
        }
        if (!hasAllPermissions(context)) {
            return BleIssue.PERMISSIONS_MISSING
        }
        return null
    }

    enum class BleIssue {
        NO_BLE_SUPPORT,
        BLUETOOTH_DISABLED,
        PERMISSIONS_MISSING
    }
}
