# 仪表日夜主题、地图覆盖及指示灯定义

2026-10-14。本轮修改前已先提交主副屏切换版本：`3d01b6c`，提交信息“优化主副屏切换并完善多比例适配文档”。

## 日夜统一

右下角“主页”左边增加太阳/月亮开关，图案及文字表示当前模式，点击切换到另一模式。选择保存为 CarPlay 的 DAY 或 NIGHT 设置；原设置中的系统/光感模式仍可重新选择。

主显示 Activity 使用原有 CarPlayNightModeController 作为统一状态来源：更新本地仪表配色，并通过 AirPlay 的 setNightMode 请求 iPhone 更新 CarPlay 外观。连接尚未就绪时使用原有待发送机制；不会为改主题重新连接，不改变 Android 系统主题。

本机组合上已观察到仪表和真实副屏地图分别切为浅色日间、深色夜间，命令日志 eventChannelReady=true。该日志仅证明命令写入，实机截图才是地图响应的依据；不保证其他 iOS 设置或所有导航应用都服从车端请求，也不提供源端地图颜色的本地伪造。

## 地图上下留白

之前使用等比例完整显示（fit），地图宽高比与右侧区域不同时，上下会留空；不是视频丢帧或解码缺失。为融合界面改为等比例覆盖（cover/center crop）：地图视图覆盖右侧区域的全部高度，按中心裁掉多余边缘，不把地图拉伸变形。定位标记保留在可见区域，真实导航时仍需进一步验证边缘道路及指引是否受影响。

顶部导航条和底部指标栏继续绘制在地图前景上，属于设计遮挡，不是留白。虚拟副屏的视频配置仍为 1280×720，没有因本地覆盖布局更改协商尺寸。

## 仪表布局

速度圈整体放大约 7%，向右移动 46 个设计坐标单位，保留等比例圆形。顶部增加两排指示灯并与速度圈保持间距；底部日夜开关位于里程与主页之间。1920×1080、960×480 的布局测试检查按钮不重叠、地图覆盖高度以及变换后视频比例正确。

## 状态定义与数据接口

仅参考 MyApplication 的 DashboardData、VehicleIconState 和 HmiDashboardView 中顶部及第二排指示灯字段。不复制协议客户端、不连接 BLE/CAN、不向实车写入任何数据。图标使用当前线条和配色风格重新绘制，未搬入参考项目位图。

| 新定义 | 对应参考字段 | 含义 |
| --- | --- | --- |
| LEFT_TURN / RIGHT_TURN | leftTurnOn / rightTurnOn | 左右转向灯，可闪烁 |
| HIGH_BEAM / LOW_BEAM | highBeamOn / lowBeamOn | 远光灯、近光灯 |
| POSITION_LIGHT | positionLightOn | 位置灯 |
| VEHICLE_BLUETOOTH / PHONE_BLUETOOTH | vehicleBluetoothOn / bluetoothOn | 车辆端、手机端蓝牙状态，分别保留 |
| GPS / GSM | gpsOn / gsmOn | 定位、蜂窝网络状态 |
| FAULT / ENERGY_RECOVERY / READY | faultOn / energyRecoveryOn / readyOn | 故障、能量回收、车辆就绪 |
| SIDE_STAND / REPAIR_MODE | sideStandOn / repairModeOn | 侧撑、维修模式 |
| SEAT / RIDER_UNSEATED | seatOn / riderUnseatedOn | 保留座椅状态字段、骑手未就座状态 |
| ABS / TCS / HDC | absOn / tcsOn / hdcOn及各自 Blink | ABS、牵引力控制、陡坡缓降，支持闪烁 |
| CRUISE / CHARGING | cruiseOn / chargingOn | 巡航、充电状态 |

SEAT 的开闭/占用语义，以及 ABS/TCS/HDC 激活是否表示工作、禁用或告警，需后续结合原协议确认；本轮不猜测原始位值与极性。

DashboardIndicatorSignal 表达 active 和 blinking；DashboardIndicatorSnapshot 提供一致的状态快照；DashboardIndicatorSource 是预留的数据源接口，FusionDashboardPane.setIndicatorSource 可以替换演示源。

目前所有车辆指示灯及车辆数字仍为明确标注的演示数据。默认演示近光、位置灯、手机蓝牙、READY 和左转向闪烁，其余灰色。蓝牙/GPS/网络图标不能被当作已经获得的真实车辆状态。

## 验证范围

已覆盖主屏配置、两种比例的覆盖布局、开关回调及下一模式提示，并在红米检查真实地图主题响应。未完成全部导航应用、多款车机、实车数据、长期温升和电耗测试。

最终验证：13 项显示配置、4 项手势/布局/开关、16 项日夜控制器和 6 项主题持久化测试，共 39 项通过，无失败或错误。最终 APK 编译、认证资源哈希校验及红米覆盖安装成功。日夜切换无需重新建立主副屏流。

实机截图保存在同级支持目录 `DiPlay-local-support/backups/fusion-theme-day.png`、`fusion-theme-night.png`。修改前的 Git 保存点为 3d01b6c；本轮主题和布局优化在该保存点之后继续修改，未自动追加提交。

用户随后确认日夜联动功能正常。本版本本地仪表配色仍立即切换，iPhone 地图约有两秒渐变；下一轮将增加本地配色过渡，把第二排状态改成左侧竖排并改进图标辨识，同时实测屏幕刘海安全区。此处先保存已验证版本，避免把后续计划写成已完成结果。
