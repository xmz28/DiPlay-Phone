package com.shilapi.xcertplay.vehicle

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.shilapi.xcertplay.DiPlayActivity
import com.shilapi.xcertplay.host.R

/** Separate from CarPlay: vehicle telemetry survives page switches and screen mirroring. */
class VehicleBleService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            VehicleBleSettings.setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!VehicleBleSettings.enabled(this) || !VehicleBleSettings.hasPermissions(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "车辆 BLE 数据", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 20, Intent(this, DiPlayActivity::class.java).putExtra("page", "settings"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 21, Intent(this, VehicleBleService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_diplay_notification)
            .setContentTitle("DiPlay · 车辆数据")
            .setContentText("正在连接已绑定的 ESP32；断连时仪表恢复演示")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止车辆连接", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(20, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(20, notification)
        Esp32VehicleBleClient.getInstance(this).start()
        return START_STICKY
    }
    override fun onDestroy() {
        Esp32VehicleBleClient.getInstance(this).stop()
        super.onDestroy()
    }
    companion object {
        const val ACTION_STOP = "com.shihab.diplay.STOP_VEHICLE_BLE"
        private const val CHANNEL = "diplay_vehicle_ble"
    }
}
