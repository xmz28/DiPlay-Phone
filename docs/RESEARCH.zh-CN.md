# 本地研究环境

基于手机适配发布标签 `v0.2.11-phone-fixes.2`，原始提交 `367aa8414037d718da78ace22eb60cd202b94f61`。
本地分支：`research/android-baseline`。先复现这个版本，再逐项优化。

## 当前进展（2026-10-14）

已经完成基线复现、普通手机原生 OEM 入口、红米副屏地图实测和融合仪表演示。融合仪表车辆数据尚为固定演示值，导航与音乐信息来自 CarPlay。最新版本将 OEM 入口默认命名为“仪表”，点击进入融合页；该图标的显示和点击已实机确认。仪表里的“主页”仍返回 DiPlay。

主屏和仪表已合并到一个显示 Activity，支持 240 ms 过渡与双指横滑，用户确认两个方向都正常。1920×1080、960×480 的显示配置和控件布局测试通过，具体范围见[切换与分辨率记录](DISPLAY_SWITCHING.zh-CN.md)。此前按日期记录的 Home 名称与双 Activity 实现属于历史版本。

详细结果见[融合仪表记录](FUSION_DASHBOARD.zh-CN.md)、[副屏地图记录](CLUSTER_MAP_VERIFICATION.zh-CN.md)及[目录整理记录](DIRECTORY_LAYOUT.zh-CN.md)。下面按日期保留历史构建信息，旧 APK 哈希不是当前版本的哈希。

源码现为独立工程，同级 `DiPlay-local-support` 保存 JDK、备份和模板归档。两者不需要合并；旧 `Diplay` 目录已不含源码或必需工具，可在关闭旧 IDE 工程后删除。新位置完整离线构建与认证资源校验已通过，本次迁移没有重新安装手机应用。

提交前检查（2026-10-14）：在新目录离线复跑 `CarPlayHostSizeSettingTest`，12 项测试通过，无失败或错误；公开源码凭据检查与 `git diff --cached --check` 通过。未运行完整测试套件或 lint。认证文件、APK、本机 IDE 配置和构建缓存均不纳入提交。

## 文件与设备

- 源码根目录：`C:\Users\<USER>\AndroidStudioProjects\DiPlay-Phone`，Android Studio 应打开此目录。
- 手机安装包备份：同级支持目录 `DiPlay-local-support\backups\DiPlay-Phone-original.apk`。
- 原版 APK SHA-256：`f42ace8b25e64b63adbe54aa67c8249b8fddb83930d6dc2ec5606a5e9e70bd25`。
- 认证资源：`C:\Users\<USER>\DiPlay-runtime-assets\offline-mfi`，包含 `identity.pk8`、`certificate.p7b`。
- 研究设备：Redmi K30 5G / Android 13，ADB 序列号 `<DEVICE_SERIAL>`；iPhone 15 Pro / iOS 26.7。
- 用户已验证发布 APK 有线、无线连接成功，并确认基线自编译版与原版功能一致、可以正常运行。

## 本次验证结果（2026-10-13）

- `:mobile:assembleStandaloneDebug` 成功，81 个任务执行完成。
- 自编译 APK SHA-256：`cfcec6d6146f847664a983c96f1a13ae80e7287f463be103c1620998793a635e`。
- APK 签名校验通过，两个认证资源与原版提取的输入逐字节摘要一致。
- 已安装到 K30，版本 `0.2.11-orientation-lock-hud-test` / versionCode 30，应用进程正常，Activity 处于前台恢复状态。
- 检查的启动日志未发现应用致命异常；出现非致命图形交换行为警告。
- 上述哈希对应首次自编译基线。用户随后确认正常运行；长时间稳定性仍需单独测试。
- 首次基线没有运行完整单元测试及 lint。后续功能改动的验证单独记录。
- 仓库原有凭据检查脚本已通过。

## 编译与安装

需要 JDK 25、SDK Platform 37.0、Build Tools 36.0.0、NDK 28.2.13676358；使用仓库自带 Gradle 9.5.0。
JDK 放在同级 `DiPlay-local-support\tools` 内，脚本自动寻找，也可通过 `-JavaHome` 指定。Android SDK 默认采用系统已有位置。

在源码根目录执行：

```powershell
# 编译；当前电脑需要代理下载依赖时使用下面的 Proxy 参数。
.\scripts\research-build.ps1 -Proxy http://127.0.0.1:7892

# 编译、核验认证资源、安装到指定 K30 并启动。
.\scripts\research-build.ps1 -Proxy http://127.0.0.1:7892 -Install

# 缓存完整后可以离线编译。
.\scripts\research-build.ps1 -Offline -Install

# 修改协议或媒体逻辑后运行已有单元测试及 lint。
.\scripts\research-build.ps1 -Proxy http://127.0.0.1:7892 -Check
```

APK：`mobile\build\outputs\apk\debug\mobile-debug.apk`。
脚本保留 `.hudtest` 包名，使用本机 debug 签名；首次已卸载发布测试版，后续用同一签名覆盖安装即可。
脚本不会自动卸载应用。签名变化时覆盖安装会失败，先备份需要的配置再手动处理。

Android Studio 的 Gradle JDK 设为同级 `DiPlay-local-support\tools` 内的 JDK 25。本机已在项目的 `.gradle/config.properties` 配置该路径，可使用 `GRADLE_LOCAL_JAVA_HOME` 选项。已配置用户环境变量 `DIPLAY_AUTH_ASSETS_DIR`，需要重启 Android Studio 后才会继承；只有使用含认证资源的构建，才能独立连接 iPhone。
证书资源、Android 签名密钥不要加入版本管理。APK 本身包含认证资源，公开发布时身份可以被提取。

## 手机 Home 功能（2026-10-13）

- 普通手机默认向 iPhone 提供原生 OEM 返回图标，默认名称 Home，图案为房子。
- iPhone 的 `requestUI` 回调打开 DiPlay 主页，后台 CarPlay 会话继续运行。主页可再次进入投屏。
- 设置中的高级选项支持开关、名称和自定义图标；这些变更需要重新连接才能更新 iPhone 的应用列表。
- 比亚迪设备沿用原有默认图案。普通手机未设置名称时使用 Home，已有自定义名称保留。
- 不需要 root，也不使用悬浮窗。此入口是接收端协议已有的 OEM 返回入口，不是注册一个任意 iPhone 应用。
- 按用户要求取消了 root 系统热点写入方案。未改动现有手动热点配置流程。
- Home 版已编译并覆盖安装到 K30，应用启动成功。`CarPlayHostSizeSettingTest` 聚焦测试通过，涵盖普通手机原生图标、开关、名称及原有比亚迪图标行为；未运行完整测试及 lint。iPhone 上图标显示及点击保留会话需要用户实机确认。
- Home 版 APK SHA-256：`243c67a480595e4b06baf23fbffa1d94a51fd7ec212ce4ddbca818efda004798`。已扫描 APK，确认不含撤回的系统热点 root 辅助类。

Android 13 的普通应用可使用临时本地热点，但不能通过通用公开接口持久修改系统网络共享热点的名称和密码。
Android 16（API 36）新增公开接口 `startLocalOnlyHotspotWithConfiguration` 可以配置临时本地热点；它仍不等于写入系统热点设置。
来源：[Android WifiManager](https://developer.android.com/reference/android/net/wifi/WifiManager)、[本地热点](https://developer.android.com/develop/connectivity/wifi/localonlyhotspot)。

车辆数据代码现状：`Iap2VehicleStatus.kt` 上报电量、续航、充电及电池能量；轮速通过 iAP2 定位组件上报。当前车辆读取层主要面向比亚迪，未实现通用 OBD/CAN 数据源，也未上报转速。手机电量不能代替车机动力电池电量。上报并不保证 iPhone 在 CarPlay 里显示对应仪表。

原生显示车辆信息的研究路径：iPhone 应用读取 GPS/OBD/CAN 或接收安卓桥接数据，再实现支持 CarPlay 的应用模板、iOS 26 小组件或实时活动。完整 CarPlay 应用需要对应类别和 entitlement；小组件/实时活动可以来自没有完整 CarPlay 应用的 iPhone 应用，但刷新频率和后台运行限制需要实测，不应直接承诺高频仪表能力。
CarPlay Ultra 的原生车速/转速仪表需要专门的车辆集成，当前接收端不是 Ultra 实现。
来源：[Apple iOS 26 CarPlay](https://developer.apple.com/videos/play/wwdc2025/216/)、[车辆集成架构](https://developer.apple.com/videos/play/wwdc2024/10111/)。

## 源码阅读顺序

| 目标 | 文件或目录 |
| --- | --- |
| 主页、设置和横屏布局 | `common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt` |
| CarPlay 显示界面与手势 | `common/src/main/java/com/shilapi/xcertplay/CarPlayHostActivity.kt` |
| 有线、无线连接与状态 | `shared/src/main/java/com/shilapi/xcertplay/orchestration/CarPlayController.kt` |
| iAP2 数据封装、控制、识别 | `shared/src/main/java/com/shilapi/xcertplay/iap2`、`transport` |
| 会话、配对、视频与音频传输 | `shared/src/main/java/com/shilapi/xcertplay/airplay` |
| Android 解码、播放与录音 | `shared/src/main/java/com/shilapi/xcertplay/media/AndroidMediaSink.kt` |
| 触控坐标转换 | `shared/src/main/java/com/shilapi/xcertplay/media/CarPlayTouchMapper.kt` |
| USB 网络桥接 | `shared/src/main/java/com/shilapi/xcertplay/network/CarPlayVpnService.kt`、`transport/NcmUsbBridge.kt` |
| 本地配件认证 | `shared/src/main/java/com/shilapi/xcertplay/mfi/LocalMfiAuthenticationClient.kt` |

第一次修改建议增加诊断显示、快捷操作或调整手机布局。改变显示区域时，同时检查触控坐标；调整视频队列或音频缓冲时，记录延迟、帧率、断音和重连情况。

## 跨平台研究路线

当前 `shared` 是 Android library，其中仍有 Android 类型和 API，不能直接作为纯 JVM 核心搬到桌面。
先把字节封装、协议消息、配对和密码学等逻辑，与平台的蓝牙、USB、网络接口、音视频、窗口系统分开。

- Windows：可研究 JVM 协议核心配合桌面 UI；视频后端可用 Media Foundation，音频和 USB 需另接平台实现。无线首次连接还需要实现经典蓝牙 RFCOMM，不能仅实现 Wi-Fi 接收。USB NCM 和驱动权限是单独的难点。
- macOS：可研究协议核心配合 AppKit/其他桌面 UI，视频采用 VideoToolbox；蓝牙与 USB 主机接口需重新实现。拥有 Apple API 并不自动提供 CarPlay 接收端能力。
- ESP32：需要将 Kotlin/Android 部分重写，并先验证蓝牙链路、协议、内存、解码性能。ESP32-S3 没有经典蓝牙，当前无线启动使用的 RFCOMM 无法直接照搬。完整 CarPlay 主屏接收不宜作为第一步。
- 更可行的 ESP32 实验：K30/电脑负责 CarPlay 会话、解码及媒体控制，ESP32 做按键、旋钮、导航信息小屏，或接收经过桥接端转码的画面。额外转码会增加延迟，需要测量。
- ESP32-P4 的 H.264 硬件模块是编码器，不能据此认定可硬件解码 CarPlay 视频。是否能用软件解码达到目标分辨率和帧率，需要独立测试。

资料：[Windows Media Foundation](https://learn.microsoft.com/en-us/windows/win32/medfound/media-foundation-programming-guide)、[Apple VideoToolbox](https://developer.apple.com/documentation/videotoolbox)、[ESP32-S3 数据手册](https://www.espressif.com/sites/default/files/documentation/esp32-s3_datasheet_en.pdf)、[ESP32-P4 H.264 编码](https://docs.espressif.com/projects/esp-vision/en/latest/esp32p4/api-reference/h264.html)。

这些是移植方案分析，目前尚未完成 Windows、macOS 或 ESP32 接收端。

## ESP32 车辆 BLE 接入（2026-10-15）

已增加手动绑定车辆、后台 BLE 服务、50 ms 仪表刷新、新旧功率字段兼容和断连回演示。电压/电流按 0.1 V / 0.1 A 传输，优先在手机计算功率。来源精度、有效性和测试范围见 [车辆 BLE 接入说明](VEHICLE_BLE_INTEGRATION.zh-CN.md)。私有图标未复制进公开项目。

## 功率圆弧与分方向日夜过渡（2026-10-15）

BLE 接入阶段已提交后继续优化：原固定圆弧绑定功率，蓝色 0～1.4 kW、绿色 0～−0.6 kW，保留原布局；仪表日夜过渡拆为夜→日 1.2 秒、日→夜 1.6 秒的独立可调项。详见 [实现说明](POWER_RING_AND_DIRECTIONAL_THEME.zh-CN.md)。
