package com.shilapi.xcertplay.vehicle

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object VehicleBleSettings {
    private const val ENABLED = "enabled"
    private fun preferences(context: Context) = context.getSharedPreferences(Esp32VehicleBleClient.PREFERENCES_NAME, Context.MODE_PRIVATE)
    fun enabled(context: Context) = preferences(context).getBoolean(ENABLED, false)
    fun address(context: Context): String? = preferences(context).getString(Esp32VehicleBleClient.KEY_LAST_ADDRESS, null)
    fun permissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasPermissions(context: Context) = permissions().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    fun setEnabled(context: Context, value: Boolean) {
        require(!value || (hasPermissions(context) && BluetoothAdapter.checkBluetoothAddress(address(context) ?: "")))
        preferences(context).edit().putBoolean(ENABLED, value).apply()
        if (value) context.startForegroundService(Intent(context, VehicleBleService::class.java))
        else {
            context.stopService(Intent(context, VehicleBleService::class.java))
            Esp32VehicleBleClient.getInstance(context).stop()
        }
    }
    fun resume(context: Context) {
        if (enabled(context) && hasPermissions(context) && Esp32VehicleBleClient.hasSavedDevice(context)) {
            context.startForegroundService(Intent(context, VehicleBleService::class.java))
        }
    }
}
