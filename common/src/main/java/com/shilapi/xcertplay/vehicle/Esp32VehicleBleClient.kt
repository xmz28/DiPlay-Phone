package com.shilapi.xcertplay.vehicle

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@SuppressLint("MissingPermission")
class Esp32VehicleBleClient private constructor(
    context: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : VehicleStateProvider {
    private data class PendingStateDelivery(val state: VehicleState, val receivedAtMs: Long)

    private data class FrameAssembly(
        val messageType: Int,
        val sequence: Long,
        val totalLength: Int,
        val buffer: ByteArray,
        var nextOffset: Int = 0,
    )

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val stateLock = Any()
    private val frameLock = Any()
    private val scanLock = Any()
    private val pendingState = AtomicReference<PendingStateDelivery?>(null)
    private val stateDispatchQueued = AtomicBoolean(false)
    private val pendingDiagnostics = AtomicReference<DiagnosticsState?>(null)
    private val diagnosticsDispatchQueued = AtomicBoolean(false)
    private val assemblies = mutableMapOf<Long, FrameAssembly>()
    private val scanCandidates = mutableMapOf<String, ScanResult>()

    private var listener: (VehicleState) -> Unit = {}
    private var diagnosticsListener: (DiagnosticsState) -> Unit = {}
    @Volatile private var running = false
    @Volatile private var bluetoothGatt: BluetoothGatt? = null
    @Volatile private var notificationsReady = false
    @Volatile private var lastState = VehicleState()
    @Volatile private var lastStatusReceivedAtMs = 0L
    @Volatile private var lastRealtimeReceivedAtMs = 0L
    private var scanning = false
    private var bluetoothReceiverRegistered = false
    private var connectingPreferred = false
    private var bondRepairAttempted = false
    private var diagnosticsEnabled = false
    private var diagnosticsState = DiagnosticsState()
    private var lastRealtimeSequence = 0L
    private var lastBootId = -1L
    private var performanceWindowStartedMs = SystemClock.elapsedRealtime()
    private var performanceRealtimeCount = 0L
    private var performanceFullCount = 0L
    private var performanceMissingCount = 0L
    private var performanceMaxRxGapMs = 0L
    private var performanceLastRxMs = 0L

    private val stateDispatchRunnable = object : Runnable {
        override fun run() {
            val delivery = pendingState.getAndSet(null)
            if (delivery != null && running) listener(delivery.state)
            stateDispatchQueued.set(false)
            if (pendingState.get() != null) queueStateDispatch()
        }
    }

    private val diagnosticsDispatchRunnable = object : Runnable {
        override fun run() {
            pendingDiagnostics.getAndSet(null)?.let(diagnosticsListener)
            diagnosticsDispatchQueued.set(false)
            if (pendingDiagnostics.get() != null) queueDiagnosticsDispatch()
        }
    }

    private val statusTimeoutRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!running) return
            val ageMs = SystemClock.elapsedRealtime() - lastStatusReceivedAtMs
            if (lastStatusReceivedAtMs > 0L && ageMs < STATUS_TIMEOUT_MS) {
                mainHandler.postDelayed(this, STATUS_TIMEOUT_MS - ageMs)
            } else {
                publish(currentState().asDisconnected())
            }
        }
    }

    private val scanWindowFinishedRunnable = Runnable { finishScanAndConnect() }
    private val retryRunnable = Runnable { connectPreferredOrScan() }
    private val connectTimeoutRunnable = Runnable {
        val gatt = bluetoothGatt ?: return@Runnable
        if (notificationsReady) return@Runnable
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.w(TAG, "connect timeout preferred=$connectingPreferred")
        val repairBond = gatt.device.bondState == BluetoothDevice.BOND_BONDED &&
            !bondRepairAttempted
        if (repairBond) bondRepairAttempted = true
        bluetoothGatt = null
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
        connectingPreferred = false
        if (repairBond && removeBond(gatt.device)) {
            Log.w(TAG, "removed stale BLE bond address=${gatt.device.address}; pairing again")
            scheduleRetry(BOND_REPAIR_DELAY_MS)
        } else {
            startScan()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                mainHandler.post { handleBluetoothAdapterState(state) }
                return
            }
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            val gatt = bluetoothGatt ?: return
            if (device.address != gatt.device.address) return
            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            val previous = intent.getIntExtra(
                BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                BluetoothDevice.ERROR,
            )
            if (Log.isLoggable(TAG, Log.DEBUG)) Log.i(TAG, "bond state=$state previous=$previous address=${device.address}")
            mainHandler.post {
                if (!running || gatt !== bluetoothGatt) return@post
                when {
                    state == BluetoothDevice.BOND_BONDED -> discoverServices(gatt)
                    state == BluetoothDevice.BOND_BONDING -> waitForBondConfirmation(gatt)
                    state == BluetoothDevice.BOND_NONE && previous == BluetoothDevice.BOND_BONDING -> {
                        Log.w(TAG, "BLE pairing was cancelled or failed")
                        failGattAndScan(gatt)
                    }
                }
            }
        }
    }

    override fun setListener(listener: (VehicleState) -> Unit) {
        this.listener = listener
    }

    override fun setDiagnosticsListener(listener: (DiagnosticsState) -> Unit) {
        diagnosticsListener = listener
        publishDiagnostics()
    }

    override fun setDiagnosticsEnabled(enabled: Boolean) {
        diagnosticsEnabled = enabled
        diagnosticsState = if (enabled) DiagnosticsState(enabled = true) else DiagnosticsState()
        lastRealtimeSequence = 0L
        publishDiagnostics()
    }

    override fun start() {
        if (running) return
        if (!hasSavedDevice(appContext) || !VehicleBleSettings.hasPermissions(appContext)) return
        running = true
        synchronized(stateLock) { lastState = VehicleState() }
        registerBluetoothReceiver()
        lastStatusReceivedAtMs = 0L
        lastRealtimeReceivedAtMs = 0L
        mainHandler.post { connectPreferredOrScan() }
    }

    override fun stop() {
        running = false
        unregisterBluetoothReceiver()
        mainHandler.removeCallbacks(scanWindowFinishedRunnable)
        mainHandler.removeCallbacks(retryRunnable)
        mainHandler.removeCallbacks(connectTimeoutRunnable)
        mainHandler.removeCallbacks(statusTimeoutRunnable)
        mainHandler.removeCallbacks(stateDispatchRunnable)
        mainHandler.removeCallbacks(diagnosticsDispatchRunnable)
        stopScan()
        val gatt = bluetoothGatt
        bluetoothGatt = null
        notificationsReady = false
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        synchronized(stateLock) { lastState = VehicleState() }
        synchronized(frameLock) { assemblies.clear() }
        pendingState.set(null)
        pendingDiagnostics.set(null)
        stateDispatchQueued.set(false)
        diagnosticsDispatchQueued.set(false)
    }

    private fun connectPreferredOrScan() {
        if (!running) return
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            publish(currentState().asDisconnected())
            scheduleRetry(BLUETOOTH_OFF_RETRY_MS)
            return
        }
        // A foreground scan followed by a direct GATT connection is more
        // reliable than Android's autoConnect path after Bluetooth is toggled
        // or the vehicle returns into range. Saved devices are still matched by
        // exact address; there is no fallback to another nearby vehicle.
        startScan()
    }

    private fun registerBluetoothReceiver() {
        if (bluetoothReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        bluetoothReceiverRegistered = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(bluetoothReceiver, filter)
            }
            true
        }.getOrElse {
            Log.w(TAG, "failed to register Bluetooth receiver", it)
            false
        }
    }

    private fun unregisterBluetoothReceiver() {
        if (!bluetoothReceiverRegistered) return
        runCatching { appContext.unregisterReceiver(bluetoothReceiver) }
        bluetoothReceiverRegistered = false
    }

    private fun handleBluetoothAdapterState(state: Int) {
        if (!running) return
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.i(TAG, "Bluetooth adapter state=$state")
        when (state) {
            BluetoothAdapter.STATE_TURNING_OFF,
            BluetoothAdapter.STATE_OFF -> {
                mainHandler.removeCallbacks(retryRunnable)
                stopScan()
                val gatt = bluetoothGatt
                bluetoothGatt = null
                notificationsReady = false
                connectingPreferred = false
                mainHandler.removeCallbacks(connectTimeoutRunnable)
                runCatching { gatt?.disconnect() }
                runCatching { gatt?.close() }
                publish(currentState().asDisconnected())
                scheduleRetry(BLUETOOTH_OFF_RETRY_MS)
            }
            BluetoothAdapter.STATE_ON -> {
                mainHandler.removeCallbacks(retryRunnable)
                connectPreferredOrScan()
            }
        }
    }

    private fun scheduleRetry(delayMs: Long) {
        if (!running) return
        mainHandler.removeCallbacks(retryRunnable)
        mainHandler.postDelayed(retryRunnable, delayMs)
    }

    private fun startScan() {
        if (!running || scanning || bluetoothGatt != null) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            scheduleRetry(SCAN_RETRY_MS)
            return
        }
        synchronized(scanLock) { scanCandidates.clear() }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()
        val started = runCatching {
            scanner.startScan(listOf(filter), settings, scanCallback)
            true
        }.getOrElse {
            Log.w(TAG, "scan start failed", it)
            false
        }
        if (!started) {
            scheduleRetry(SCAN_RETRY_MS)
            return
        }
        scanning = true
        mainHandler.removeCallbacks(scanWindowFinishedRunnable)
        mainHandler.postDelayed(scanWindowFinishedRunnable, SCAN_WINDOW_MS)
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.i(TAG, "scan started service=$SERVICE_UUID")
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        mainHandler.removeCallbacks(scanWindowFinishedRunnable)
        runCatching { bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    private fun finishScanAndConnect() {
        if (!running || !scanning) return
        stopScan()
        val savedAddress = preferences.getString(KEY_LAST_ADDRESS, null)
        val selected = synchronized(scanLock) {
            if (savedAddress.isNullOrBlank()) {
                null // First binding is always chosen explicitly in settings.
            } else {
                scanCandidates.values.firstOrNull {
                    it.device.address.equals(savedAddress, ignoreCase = true)
                }
            }
        }
        if (selected == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.i(
                    TAG,
                    if (savedAddress.isNullOrBlank()) {
                        "no matching BLE device, scanning again"
                    } else {
                        "saved BLE device not in range address=$savedAddress, scanning again"
                    },
                )
            }
            scheduleRetry(SCAN_RETRY_MS)
            return
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.i(
                TAG,
                "connect ${if (savedAddress.isNullOrBlank()) "unbound" else "saved"} " +
                    "address=${selected.device.address} " +
                    "name=${selected.scanRecord?.deviceName} rssi=${selected.rssi}",
            )
        }
        connectTo(selected.device, preferred = !savedAddress.isNullOrBlank())
    }

    private fun connectTo(device: BluetoothDevice, preferred: Boolean) {
        if (!running) return
        stopScan()
        bluetoothGatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        notificationsReady = false
        connectingPreferred = preferred
        bluetoothGatt = runCatching {
            device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }.getOrElse {
            Log.w(TAG, "connectGatt failed address=${device.address}", it)
            null
        }
        if (bluetoothGatt == null) {
            connectingPreferred = false
            scheduleRetry(SCAN_RETRY_MS)
            return
        }
        mainHandler.removeCallbacks(connectTimeoutRunnable)
        mainHandler.postDelayed(connectTimeoutRunnable, CONNECT_TIMEOUT_MS)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            synchronized(scanLock) {
                val old = scanCandidates[result.device.address]
                if (old == null || result.rssi > old.rssi) scanCandidates[result.device.address] = result
            }
            val savedAddress = preferences.getString(KEY_LAST_ADDRESS, null)
            if (!savedAddress.isNullOrBlank() &&
                result.device.address.equals(savedAddress, ignoreCase = true)
            ) {
                mainHandler.post {
                    if (!running || !scanning || bluetoothGatt != null) return@post
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.i(
                            TAG,
                            "saved BLE device found address=${result.device.address} " +
                                "name=${result.scanRecord?.deviceName} rssi=${result.rssi}",
                        )
                    }
                    connectTo(result.device, preferred = true)
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            mainHandler.post {
                scanning = false
                Log.w(TAG, "scan failed code=$errorCode")
                scheduleRetry(SCAN_RETRY_MS)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            mainHandler.post { handleConnectionState(gatt, status, newState) }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            mainHandler.post {
                if (gatt !== bluetoothGatt) return@post
                val tx = gatt.getService(SERVICE_UUID)?.getCharacteristic(TX_UUID)
                val rx = gatt.getService(SERVICE_UUID)?.getCharacteristic(RX_UUID)
                if (status != BluetoothGatt.GATT_SUCCESS || tx == null || rx == null) {
                    Log.w(TAG, "required BLE service missing status=$status")
                    failGattAndScan(gatt)
                    return@post
                }
                val mtuRequested = runCatching { gatt.requestMtu(REQUESTED_MTU) }.getOrDefault(false)
                if (!mtuRequested) writeAuthentication(gatt, rx)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            mainHandler.post {
                if (gatt !== bluetoothGatt) return@post
                if (Log.isLoggable(TAG, Log.DEBUG)) Log.i(TAG, "mtu=$mtu status=$status")
                val rx = gatt.getService(SERVICE_UUID)?.getCharacteristic(RX_UUID)
                if (rx == null) failGattAndScan(gatt) else writeAuthentication(gatt, rx)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            mainHandler.post {
                if (gatt !== bluetoothGatt || characteristic.uuid != RX_UUID) return@post
                val tx = gatt.getService(SERVICE_UUID)?.getCharacteristic(TX_UUID)
                if (status != BluetoothGatt.GATT_SUCCESS || tx == null) {
                    Log.w(TAG, "BLE authentication write failed status=$status")
                    failGattAndScan(gatt)
                } else {
                    enableNotifications(gatt, tx)
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            mainHandler.post {
                if (gatt !== bluetoothGatt || descriptor.uuid != CCCD_UUID) return@post
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "CCCD write failed status=$status")
                    failGattAndScan(gatt)
                    return@post
                }
                notificationsReady = true
                connectingPreferred = false
                bondRepairAttempted = false
                mainHandler.removeCallbacks(connectTimeoutRunnable)
                preferences.edit().putString(KEY_LAST_ADDRESS, gatt.device.address).apply()
                if (Log.isLoggable(TAG, Log.DEBUG)) Log.i(TAG, "notifications ready address=${gatt.device.address}")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (gatt === bluetoothGatt && characteristic.uuid == TX_UUID) handleFrame(value)
        }

        @Deprecated("Legacy Android callback")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (gatt === bluetoothGatt && characteristic.uuid == TX_UUID) {
                @Suppress("DEPRECATION")
                handleFrame(characteristic.value ?: return)
            }
        }
    }

    private fun handleConnectionState(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (gatt !== bluetoothGatt) {
            runCatching { gatt.close() }
            return
        }
        when {
            status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED -> {
                runCatching { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
                when (gatt.device.bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        // Android restores an existing BLE bond asynchronously after the ACL
                        // connection. Starting an encrypted characteristic write immediately can
                        // race that process on some phones and leave the GATT request pending until
                        // the link times out. Give the stack a short quiet period first.
                        mainHandler.postDelayed({
                            if (running && gatt === bluetoothGatt) discoverServices(gatt)
                        }, BONDED_SECURITY_SETTLE_MS)
                    }
                    BluetoothDevice.BOND_BONDING -> {
                        if (Log.isLoggable(TAG, Log.DEBUG)) Log.i(TAG, "waiting for BLE pairing confirmation")
                        waitForBondConfirmation(gatt)
                    }
                    else -> {
                        if (Log.isLoggable(TAG, Log.DEBUG)) Log.i(TAG, "requesting no-PIN BLE pairing confirmation")
                        if (!gatt.device.createBond()) failGattAndScan(gatt)
                    }
                }
            }
            newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS -> {
                if (Log.isLoggable(TAG, Log.DEBUG)) Log.w(TAG, "disconnected status=$status state=$newState")
                val wasReady = notificationsReady
                bluetoothGatt = null
                notificationsReady = false
                connectingPreferred = false
                mainHandler.removeCallbacks(connectTimeoutRunnable)
                runCatching { gatt.close() }
                publish(currentState().asDisconnected())
                if (running) {
                    scheduleRetry(if (wasReady) RECONNECT_DELAY_MS else SCAN_RETRY_MS)
                }
            }
        }
    }

    private fun discoverServices(gatt: BluetoothGatt) {
        if (gatt !== bluetoothGatt || !gatt.discoverServices()) failGattAndScan(gatt)
    }

    private fun waitForBondConfirmation(gatt: BluetoothGatt) {
        if (gatt !== bluetoothGatt) return
        mainHandler.removeCallbacks(connectTimeoutRunnable)
        mainHandler.postDelayed(connectTimeoutRunnable, PAIRING_CONFIRM_TIMEOUT_MS)
    }

    private fun removeBond(device: BluetoothDevice): Boolean = runCatching {
        BluetoothDevice::class.java.getMethod("removeBond").invoke(device) == true
    }.getOrElse {
        Log.w(TAG, "unable to remove stale BLE bond address=${device.address}", it)
        false
    }

    private fun enableNotifications(gatt: BluetoothGatt, tx: BluetoothGattCharacteristic) {
        if (!gatt.setCharacteristicNotification(tx, true)) {
            failGattAndScan(gatt)
            return
        }
        val descriptor = tx.getDescriptor(CCCD_UUID)
        if (descriptor == null) {
            failGattAndScan(gatt)
            return
        }
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
        if (!started) failGattAndScan(gatt)
    }

    private fun writeAuthentication(gatt: BluetoothGatt, rx: BluetoothGattCharacteristic) {
        val token = preferences.getString(KEY_AUTH_TOKEN, null) ?: ByteArray(AUTH_TOKEN_BYTES).also {
            SecureRandom().nextBytes(it)
        }.joinToString(separator = "") { "%02x".format(it) }.also {
            preferences.edit().putString(KEY_AUTH_TOKEN, it).apply()
        }
        val command = "K1:$token".toByteArray(StandardCharsets.US_ASCII)
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(rx, command, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            rx.value = command
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(rx)
        }
        if (!started) failGattAndScan(gatt)
    }

    private fun failGattAndScan(gatt: BluetoothGatt) {
        publish(VehicleState())
        if (gatt === bluetoothGatt) bluetoothGatt = null
        notificationsReady = false
        connectingPreferred = false
        mainHandler.removeCallbacks(connectTimeoutRunnable)
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
        scheduleRetry(SCAN_RETRY_MS)
    }

    private fun handleFrame(frame: ByteArray) {
        if (!notificationsReady || frame.size < FRAME_HEADER_BYTES) return
        if ((frame[0].toInt() and 0xFF) != FRAME_MAGIC || (frame[1].toInt() and 0xFF) != FRAME_VERSION) return
        val messageType = frame[2].toInt() and 0xFF
        val flags = frame[3].toInt() and 0xFF
        val sequence = frame.readU32(4)
        val totalLength = frame.readU16(8)
        val offset = frame.readU16(10)
        val payloadLength = frame.size - FRAME_HEADER_BYTES
        if (totalLength <= 0 || totalLength > MAX_MESSAGE_BYTES || offset + payloadLength > totalLength) return
        val key = (messageType.toLong() shl 32) or sequence
        val complete = synchronized(frameLock) {
            if (flags and FLAG_START != 0) {
                assemblies.entries.removeAll { it.value.messageType == messageType }
                assemblies[key] = FrameAssembly(messageType, sequence, totalLength, ByteArray(totalLength))
            }
            val assembly = assemblies[key] ?: return@synchronized null
            if (assembly.totalLength != totalLength || assembly.nextOffset != offset) {
                assemblies.remove(key)
                return@synchronized null
            }
            frame.copyInto(
                destination = assembly.buffer,
                destinationOffset = offset,
                startIndex = FRAME_HEADER_BYTES,
                endIndex = frame.size,
            )
            assembly.nextOffset += payloadLength
            if (flags and FLAG_END != 0 && assembly.nextOffset == assembly.totalLength) {
                assemblies.remove(key)
                assembly
            } else {
                null
            }
        } ?: return
        val jsonText = String(complete.buffer, StandardCharsets.UTF_8)
        val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return
        handleMessage(complete.messageType, root)
    }

    private fun handleMessage(messageType: Int, root: JSONObject) {
        val receivedAtMs = SystemClock.elapsedRealtime()
        val parsed = synchronized(stateLock) {
            val previous = lastState
            val next = when (messageType) {
                MESSAGE_REALTIME -> VehicleStateJsonCodec.parseRealtime(root, previous)
                MESSAGE_FULL -> VehicleStateJsonCodec.parseFullStatus(
                    root,
                    previous,
                    acceptRealtimeFields = receivedAtMs - lastRealtimeReceivedAtMs >= STATUS_TIMEOUT_MS,
                )
                else -> null
            } ?: return
            lastState = next
            lastStatusReceivedAtMs = receivedAtMs
            if (messageType == MESSAGE_REALTIME) lastRealtimeReceivedAtMs = receivedAtMs
            next
        }
        if (messageType == MESSAGE_REALTIME) recordRealtime(root, receivedAtMs) else performanceFullCount++
        enqueueState(parsed, receivedAtMs)
        mainHandler.removeCallbacks(statusTimeoutRunnable)
        mainHandler.postDelayed(statusTimeoutRunnable, STATUS_TIMEOUT_MS)
        maybeLogPerformance(receivedAtMs)
    }

    private fun recordRealtime(root: JSONObject, receivedAtMs: Long) {
        val bootId = root.optLong("b", -1L)
        val sequence = root.optLong("q", 0L)
        if (bootId != lastBootId) {
            lastBootId = bootId
            lastRealtimeSequence = 0L
        }
        var missing = 0L
        if (sequence > 0L && lastRealtimeSequence > 0L && sequence > lastRealtimeSequence + 1L) {
            missing = sequence - lastRealtimeSequence - 1L
            performanceMissingCount += missing
        }
        if (sequence > 0L) lastRealtimeSequence = sequence
        performanceRealtimeCount++
        val packetGapMs = if (performanceLastRxMs > 0L) receivedAtMs - performanceLastRxMs else null
        if (packetGapMs != null) performanceMaxRxGapMs = maxOf(performanceMaxRxGapMs, packetGapMs)
        performanceLastRxMs = receivedAtMs
        if (diagnosticsEnabled) {
            diagnosticsState = diagnosticsState.copy(
                enabled = true,
                statusPackets = diagnosticsState.statusPackets + 1L,
                lostPackets = diagnosticsState.lostPackets + missing,
                lastSeq = sequence,
                lastPacketGapMs = packetGapMs,
            )
            publishDiagnostics()
        }
    }

    private fun maybeLogPerformance(nowMs: Long) {
        if (!Log.isLoggable(TAG, Log.DEBUG) || nowMs - performanceWindowStartedMs < PERFORMANCE_LOG_INTERVAL_MS) return
        Log.i(
            PERFORMANCE_TAG,
            "windowMs=${nowMs - performanceWindowStartedMs} rt=$performanceRealtimeCount " +
                "full=$performanceFullCount missing=$performanceMissingCount " +
                "rxGapMaxMs=$performanceMaxRxGapMs address=${bluetoothGatt?.device?.address} " +
                "speed=${lastState.vehicle.speedKmh}/${lastState.vehicle.speedValid} " +
                "voltage=${lastState.vehicle.voltageV}/${lastState.vehicle.voltageValid} " +
                "current=${lastState.vehicle.currentA}/${lastState.vehicle.currentValid} " +
                "electricalAgeMs=${lastState.vehicle.electricalAgeMs} powerKw=${lastState.vehicle.displayPowerKw}",
        )
        performanceWindowStartedMs = nowMs
        performanceRealtimeCount = 0L
        performanceFullCount = 0L
        performanceMissingCount = 0L
        performanceMaxRxGapMs = 0L
    }

    private fun publish(state: VehicleState, receivedAtMs: Long = SystemClock.elapsedRealtime()) {
        synchronized(stateLock) { lastState = state }
        enqueueState(state, receivedAtMs)
    }

    private fun enqueueState(state: VehicleState, receivedAtMs: Long) {
        pendingState.set(PendingStateDelivery(state, receivedAtMs))
        queueStateDispatch()
    }

    private fun queueStateDispatch() {
        if (stateDispatchQueued.compareAndSet(false, true)) mainHandler.post(stateDispatchRunnable)
    }

    private fun currentState(): VehicleState = synchronized(stateLock) { lastState }

    fun snapshot(): VehicleState = synchronized(stateLock) {
        if (!running || lastStatusReceivedAtMs == 0L ||
            SystemClock.elapsedRealtime() - lastStatusReceivedAtMs >= STATUS_TIMEOUT_MS) {
            lastState.asDisconnected()
        } else lastState
    }

    fun bindDevice(address: String) {
        require(BluetoothAdapter.checkBluetoothAddress(address))
        stop()
        preferences.edit().putString(KEY_LAST_ADDRESS, address).apply()
        synchronized(stateLock) { lastState = VehicleState() }
    }

    private fun publishDiagnostics() {
        pendingDiagnostics.set(diagnosticsState)
        queueDiagnosticsDispatch()
    }

    private fun queueDiagnosticsDispatch() {
        if (diagnosticsDispatchQueued.compareAndSet(false, true)) mainHandler.post(diagnosticsDispatchRunnable)
    }

    private fun ByteArray.readU16(offset: Int): Int {
        return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun ByteArray.readU32(offset: Int): Long {
        return (this[offset].toLong() and 0xFF) or
            ((this[offset + 1].toLong() and 0xFF) shl 8) or
            ((this[offset + 2].toLong() and 0xFF) shl 16) or
            ((this[offset + 3].toLong() and 0xFF) shl 24)
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("7d2e4001-6b3a-4f1b-9c7d-5a0e2f6b1001")
        val TX_UUID: UUID = UUID.fromString("7d2e4002-6b3a-4f1b-9c7d-5a0e2f6b1001")
        val RX_UUID: UUID = UUID.fromString("7d2e4003-6b3a-4f1b-9c7d-5a0e2f6b1001")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val PREFERENCES_NAME = "vehicle_ble"
        const val KEY_LAST_ADDRESS = "last_address"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val AUTH_TOKEN_BYTES = 16
        const val FRAME_MAGIC = 0xA5
        const val FRAME_VERSION = 1
        const val FRAME_HEADER_BYTES = 12
        const val FLAG_START = 1
        const val FLAG_END = 2
        const val MESSAGE_REALTIME = 1
        const val MESSAGE_FULL = 2
        const val MAX_MESSAGE_BYTES = 8192
        const val REQUESTED_MTU = 517
        const val SCAN_WINDOW_MS = 4_000L
        const val SCAN_RETRY_MS = 2_000L
        const val CONNECT_TIMEOUT_MS = 12_000L
        const val PAIRING_CONFIRM_TIMEOUT_MS = 60_000L
        const val BONDED_SECURITY_SETTLE_MS = 1_500L
        const val BOND_REPAIR_DELAY_MS = 2_000L
        const val RECONNECT_DELAY_MS = 1_000L
        const val BLUETOOTH_OFF_RETRY_MS = 5_000L
        const val STATUS_TIMEOUT_MS = 3_000L
        const val PERFORMANCE_LOG_INTERVAL_MS = 5_000L
        const val TAG = "VehicleBle"
        const val PERFORMANCE_TAG = "VehicleBlePerf"

        @Volatile
        private var instance: Esp32VehicleBleClient? = null

        fun getInstance(context: Context): Esp32VehicleBleClient {
            return instance ?: synchronized(this) {
                instance ?: Esp32VehicleBleClient(context.applicationContext).also { instance = it }
            }
        }

        fun hasSavedDevice(context: Context): Boolean {
            return !context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_ADDRESS, null)
                .isNullOrBlank()
        }
    }
}
