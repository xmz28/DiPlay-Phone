package com.shilapi.xcertplay.vehicle

import org.json.JSONObject

internal object VehicleStateJsonCodec {
    fun parseFullStatus(
        root: JSONObject,
        previous: VehicleState,
        acceptRealtimeFields: Boolean,
    ): VehicleState? {
        if (root.optString("type") != "status") return null
        val vehicleJson = root.optJSONObject("vehicle")
        return VehicleState(
            connectionStatus = ConnectionStatus.CONNECTED,
            seq = if (acceptRealtimeFields) root.optLong("seq", previous.seq) else previous.seq,
            ms = if (acceptRealtimeFields) root.optLong("ms", previous.ms) else previous.ms,
            stateVersion = if (acceptRealtimeFields) {
                root.optLong("stateVersion", previous.stateVersion)
            } else {
                previous.stateVersion
            },
            vehicle = if (acceptRealtimeFields) parseVehicle(vehicleJson, previous.vehicle) else {
                // A BLE full packet also carries battery and odometer. Merge only
                // those slow fields so a fragmented snapshot cannot roll back the
                // fresher speed, power, gear, ready or validity values.
                mergeSlowVehicle(vehicleJson, previous.vehicle)
            },
            charging = parseCharging(root.optJSONObject("charging"), previous.charging),
            time = parseTime(root.optJSONObject("time"), previous.time),
            icons = if (acceptRealtimeFields) {
                parseIcons(root.optJSONObject("icons"), previous.icons)
            } else {
                previous.icons
            },
            bluetooth = parseBluetooth(root.optJSONObject("bluetooth"), previous.bluetooth),
        )
    }

    fun parseRealtime(root: JSONObject, previous: VehicleState): VehicleState? {
        if (root.optString("t") != "rt") return null
        val validBits = root.optInt("z", 0)
        val powerValid = validBits and VALID_POWER != 0
        val gearValid = validBits and VALID_GEAR != 0
        val speedValid = validBits and VALID_SPEED != 0
        val iconBits = root.optLong("i", 0L)
        return previous.copy(
            connectionStatus = ConnectionStatus.CONNECTED,
            seq = root.optLong("q", previous.seq),
            ms = root.optLong("m", previous.ms),
            stateVersion = root.optLong("v", previous.stateVersion),
            vehicle = previous.vehicle.copy(
                powerKw = if (powerValid) root.optInt("p", 0) / 10.0 else previous.vehicle.powerKw,
                powerValid = powerValid,
                voltageV = (root.optMixedInt("uv") ?: 0) / 10.0,
                currentA = (root.optMixedInt("ia") ?: 0) / 10.0,
                voltageValid = root.optMixedInt("uv") != null && validBits and VALID_VOLTAGE != 0 && root.optLong("ea", 0) in 0..5000,
                currentValid = root.optMixedInt("ia") != null && validBits and VALID_CURRENT != 0 && root.optLong("ea", 0) in 0..5000,
                electricalAgeMs = root.optLong("ea", 0),
                gear = if (gearValid) root.optInt("g", previous.vehicle.gear.ordinal).toGearState() else {
                    previous.vehicle.gear
                },
                gearValid = gearValid,
                speedKmh = if (speedValid) root.optInt("s", previous.vehicle.speedKmh).coerceAtLeast(0) else {
                    previous.vehicle.speedKmh
                },
                speedOverride = when (root.optInt("o", 0)) {
                    1 -> "A"
                    2 -> "R"
                    else -> ""
                },
                speedValid = speedValid,
                ready = root.optInt("r", if (previous.vehicle.ready) 1 else 0) != 0,
                lowBattery = root.optInt("l", if (previous.vehicle.lowBattery) 1 else 0) != 0,
                updatedMs = root.optLong("u", previous.vehicle.updatedMs),
            ),
            icons = parseIconBits(iconBits),
        )
    }

    private fun mergeSlowVehicle(json: JSONObject?, previous: VehicleSnapshot): VehicleSnapshot {
        if (json == null) return previous
        val batteryPercent = json.optMixedInt("batteryPercent")?.coerceIn(0, 100)
        val batteryValid = json.optBoolean("batteryValid", batteryPercent != null)
        val odoKm = json.optMixedDouble("odoKm")
        val odoValid = json.optBoolean("odoValid", odoKm != null)
        return previous.copy(
            batteryPercent = if (batteryValid && batteryPercent != null) batteryPercent else previous.batteryPercent,
            batteryValid = batteryValid && batteryPercent != null,
            odoKm = if (odoValid) odoKm else previous.odoKm,
            odoValid = odoValid && odoKm != null,
        )
    }

    private fun parseVehicle(json: JSONObject?, previous: VehicleSnapshot): VehicleSnapshot {
        if (json == null) return previous
        val batteryPercent = json.optMixedInt("batteryPercent")?.coerceIn(0, 100)
        val batteryValid = json.optBoolean("batteryValid", batteryPercent != null)
        val odoKm = json.optMixedDouble("odoKm")
        val odoValid = json.optBoolean("odoValid", odoKm != null)
        val gearValid = json.optBoolean("gearValid", json.has("gear"))
        val speedValid = json.optBoolean("speedValid", json.has("speedKmh"))
        return VehicleSnapshot(
            batteryPercent = if (batteryValid && batteryPercent != null) batteryPercent else previous.batteryPercent,
            batteryValid = batteryValid && batteryPercent != null,
            powerKw = json.optDouble("powerKw", previous.powerKw),
            powerValid = json.optBoolean("powerValid", json.has("powerKw")),
            ready = json.optBoolean("ready", false),
            lowBattery = json.optBoolean("lowBattery", previous.lowBattery),
            odoKm = if (odoValid) odoKm else previous.odoKm,
            odoValid = odoValid && odoKm != null,
            gear = if (gearValid) json.optInt("gear", previous.gear.ordinal).toGearState() else previous.gear,
            gearValid = gearValid,
            speedKmh = if (speedValid) json.optInt("speedKmh", previous.speedKmh).coerceAtLeast(0) else {
                previous.speedKmh
            },
            speedOverride = json.optString("speedOverride", previous.speedOverride).takeIf {
                it == "A" || it == "R"
            } ?: "",
            speedValid = speedValid,
            updatedMs = json.optLong("updatedMs", previous.updatedMs),
        )
    }

    private fun parseCharging(json: JSONObject?, previous: ChargingSnapshot): ChargingSnapshot {
        if (json == null) return previous
        val remainingMinutes = json.optMixedInt("remainingMinutes")
        val remainingValid = json.optBoolean("remainingValid", remainingMinutes != null)
        return ChargingSnapshot(
            connected = json.optBoolean("connected", previous.connected),
            state = json.optInt("state", previous.state).coerceIn(0, 7),
            text = json.optString("text", previous.text),
            remainingMinutes = if (remainingValid) remainingMinutes else previous.remainingMinutes,
            remainingValid = remainingValid && remainingMinutes != null,
            updatedMs = json.optLong("updatedMs", previous.updatedMs),
        )
    }

    private fun parseTime(json: JSONObject?, previous: VehicleTime): VehicleTime {
        if (json == null) return previous
        return VehicleTime(
            valid = json.optBoolean("valid", previous.valid),
            hour = json.optInt("hour", previous.hour).coerceIn(0, 23),
            minute = json.optInt("minute", previous.minute).coerceIn(0, 59),
            second = json.optInt("second", previous.second).coerceIn(0, 59),
            updatedMs = json.optLong("updatedMs", previous.updatedMs),
        )
    }

    private fun parseIcons(json: JSONObject?, previous: VehicleIconState): VehicleIconState {
        if (json == null) return previous
        return VehicleIconState(
            leftTurn = json.optBoolean("leftTurn", previous.leftTurn),
            rightTurn = json.optBoolean("rightTurn", previous.rightTurn),
            positionLight = json.optBoolean("positionLight", previous.positionLight),
            lowBeam = json.optBoolean("lowBeam", previous.lowBeam),
            highBeam = json.optBoolean("highBeam", previous.highBeam),
            bluetoothIndicator = json.optBoolean("bluetoothIndicator", previous.bluetoothIndicator),
            screenWake = json.optBoolean("screenWake", previous.screenWake),
            gps = json.optBoolean("gps", previous.gps),
            gsm = json.optBoolean("gsm", previous.gsm),
            tcs = json.optBoolean("tcs", previous.tcs),
            tcsBlink = json.optBoolean("tcsBlink", previous.tcsBlink),
            abs = json.optBoolean("abs", previous.abs),
            absBlink = json.optBoolean("absBlink", previous.absBlink),
            cruise = json.optBoolean("cruise", previous.cruise),
            sideStand = json.optBoolean("sideStand", previous.sideStand),
            seat = json.optBoolean("seat", previous.seat),
            riderUnseated = json.optBoolean("riderUnseated", previous.riderUnseated),
            hdc = json.optBoolean("hdc", previous.hdc),
            hdcBlink = json.optBoolean("hdcBlink", previous.hdcBlink),
            charge = json.optBoolean("charge", previous.charge),
            vehicleFault = json.optBoolean("vehicleFault", previous.vehicleFault),
            energyRecovery = json.optBoolean("energyRecovery", previous.energyRecovery),
            repairMode = json.optBoolean("repairMode", previous.repairMode),
        )
    }

    private fun parseIconBits(bits: Long): VehicleIconState {
        fun bit(index: Int): Boolean = bits and (1L shl index) != 0L
        return VehicleIconState(
            leftTurn = bit(0),
            rightTurn = bit(1),
            positionLight = bit(2),
            lowBeam = bit(3),
            highBeam = bit(4),
            bluetoothIndicator = bit(5),
            screenWake = bit(6),
            gps = bit(7),
            gsm = bit(8),
            tcs = bit(9),
            tcsBlink = bit(10),
            cruise = bit(11),
            sideStand = bit(12),
            seat = bit(13),
            hdc = bit(14),
            hdcBlink = bit(15),
            charge = bit(16),
            vehicleFault = bit(17),
            riderUnseated = bit(18),
            energyRecovery = bit(19),
            repairMode = bit(20),
            abs = bit(21),
            absBlink = bit(22),
        )
    }

    private fun parseBluetooth(json: JSONObject?, previous: BluetoothSnapshot): BluetoothSnapshot {
        if (json == null) return previous
        return BluetoothSnapshot(
            enabled = json.optBoolean("enabled", previous.enabled),
            connected = json.optBoolean("connected", previous.connected),
            playing = json.optBoolean("playing", previous.playing),
            phoneCall = json.optBoolean("phoneCall", previous.phoneCall),
            callStatus = json.optString("callStatus", previous.callStatus),
            callNumber = json.optString("callNumber", previous.callNumber),
            remoteName = json.optString("remoteName", previous.remoteName),
            trackTitle = json.optString("trackTitle", previous.trackTitle),
            trackArtist = json.optString("trackArtist", previous.trackArtist),
            trackAlbum = json.optString("trackAlbum", previous.trackAlbum),
            trackPositionMs = json.optLong("trackPositionMs", previous.trackPositionMs),
            trackDurationMs = json.optLong("trackDurationMs", previous.trackDurationMs),
        )
    }

    private fun JSONObject.optMixedInt(name: String): Int? {
        if (!has(name) || isNull(name)) return null
        return when (val value = opt(name)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun JSONObject.optMixedDouble(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return when (val value = opt(name)) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private const val VALID_POWER = 1 shl 0
    private const val VALID_GEAR = 1 shl 1
    private const val VALID_SPEED = 1 shl 2
    private const val VALID_VOLTAGE = 1 shl 3
    private const val VALID_CURRENT = 1 shl 4
}
