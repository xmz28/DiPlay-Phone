package com.shilapi.xcertplay.vehicle

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.widget.ArrayAdapter

/** Selection is explicit; service UUID filters the list, exact MAC binds all later reconnects. */
@SuppressLint("MissingPermission")
object VehicleBlePicker {
    fun show(activity: Activity, selected: (String) -> Unit) {
        val scanner = activity.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
        if (scanner == null || !VehicleBleSettings.hasPermissions(activity)) {
            AlertDialog.Builder(activity).setMessage("请先打开手机蓝牙并允许附近设备权限").setPositiveButton("确定", null).show()
            return
        }
        val handler = Handler(Looper.getMainLooper())
        val devices = linkedMapOf<String, ScanResult>()
        val rows = ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1)
        var active = true
        lateinit var dialog: AlertDialog
        val callback = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                handler.post {
                    if (!active || devices.containsKey(result.device.address)) return@post
                    devices[result.device.address] = result
                    rows.add("${result.scanRecord?.deviceName ?: "ESP32 车辆"} · ${result.device.address}")
                    dialog.setTitle("选择车辆 ESP32（${devices.size}）")
                }
            }
            override fun onScanFailed(errorCode: Int) {
                handler.post { if (active) dialog.setTitle("扫描失败 $errorCode，请重试") }
            }
        }
        val stop = Runnable {
            if (active) {
                runCatching { scanner.stopScan(callback) }
                dialog.setTitle(if (devices.isEmpty()) "未发现车辆 ESP32，请确认已开启" else "选择车辆 ESP32")
            }
        }
        dialog = AlertDialog.Builder(activity).setTitle("正在扫描车辆 ESP32…")
            .setAdapter(rows) { _, index -> selected(devices.keys.elementAt(index)) }
            .setNegativeButton("取消", null).create()
        dialog.setOnDismissListener {
            active = false
            handler.removeCallbacks(stop)
            runCatching { scanner.stopScan(callback) }
        }
        dialog.show()
        runCatching {
            scanner.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(Esp32VehicleBleClient.SERVICE_UUID)).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            handler.postDelayed(stop, 10_000)
        }.onFailure { dialog.setTitle("无法扫描，请检查蓝牙权限") }
    }
}
