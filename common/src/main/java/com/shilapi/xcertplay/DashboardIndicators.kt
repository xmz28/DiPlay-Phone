package com.shilapi.xcertplay

/** Names mirror MyApplication's dashboard status fields; no CAN/BLE dependency. */
enum class DashboardIndicator(val label: String) {
    LEFT_TURN("左转向灯"), RIGHT_TURN("右转向灯"), HIGH_BEAM("远光灯"), LOW_BEAM("近光灯"),
    POSITION_LIGHT("位置灯"), VEHICLE_BLUETOOTH("车辆蓝牙"), PHONE_BLUETOOTH("手机蓝牙"),
    GPS("GPS 状态"), GSM("蜂窝网络状态"), FAULT("车辆故障"), ENERGY_RECOVERY("能量回收"),
    READY("车辆就绪"), SIDE_STAND("侧撑状态"), REPAIR_MODE("维修模式"), SEAT("座桶状态"),
    ABS("ABS 状态"), RIDER_UNSEATED("骑手未就座"), HDC("陡坡缓降"), CRUISE("巡航状态"),
    TCS("牵引力控制状态"), CHARGING("充电状态")
}

data class DashboardIndicatorSignal(val active: Boolean = false, val blinking: Boolean = false)
data class DashboardIndicatorSnapshot(val signals: Map<DashboardIndicator, DashboardIndicatorSignal> = emptyMap(), val demonstration: Boolean = true) {
    fun signal(indicator: DashboardIndicator): DashboardIndicatorSignal = signals[indicator] ?: DashboardIndicatorSignal()
}

/** Supply a coherent snapshot; a future adapter can map existing DashboardData fields here. */
fun interface DashboardIndicatorSource { fun snapshot(): DashboardIndicatorSnapshot }

internal object DemoDashboardIndicators : DashboardIndicatorSource {
    private val value = DashboardIndicatorSnapshot(mapOf(
        DashboardIndicator.LEFT_TURN to DashboardIndicatorSignal(true, true),
        DashboardIndicator.LOW_BEAM to DashboardIndicatorSignal(true),
        DashboardIndicator.POSITION_LIGHT to DashboardIndicatorSignal(true),
        DashboardIndicator.PHONE_BLUETOOTH to DashboardIndicatorSignal(true),
        DashboardIndicator.READY to DashboardIndicatorSignal(true),
    ))
    override fun snapshot(): DashboardIndicatorSnapshot = value
}
