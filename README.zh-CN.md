# DiPlay 手机融合仪表版

将 Android 手机用作 CarPlay 接收端，在完整 CarPlay 主屏之外增加融合仪表：右侧显示真实 CarPlay 副屏地图，左侧显示仪表，底栏显示真实音乐封面、歌曲和播放进度。

本分支为 `xmz28/DiPlay-Phone` 的 `research/android-baseline`，基于 [ZedingZhang 的手机适配版](https://github.com/ZedingZhang/DiPlay-Phone)继续修改。以下说明对应本分支的融合仪表版本。

[下载融合仪表 Debug APK](https://github.com/xmz28/DiPlay-Phone/releases/tag/v0.2.11-fusion-debug.3) · [本仓库全部 Release](https://github.com/xmz28/DiPlay-Phone/releases) · [源码与讨论](https://github.com/xmz28/DiPlay-Phone/pull/1)

## 装车反馈修正（Debug 3）

新增电量 × 0.85 的估算续航、速度 45 ms / 功率圈 60 ms 的短过渡、VSync 目标 60 Hz 显示、加粗指示灯和正确的 FREE 挡位及配色。BLE 遥测保持 20 Hz，真实数据未变时数字不强制变化。本轮 Android 显示调整兼容 ESP32 v0.1.8，无需重刷 ESP32。[实现与验证说明](docs/ROAD_TEST_DISPLAY_REFINEMENT.zh-CN.md)。

本次 Debug 3 已在红米覆盖安装验证：稳定统计窗口的 Android 仪表前景绘制约 59.8～59.9 FPS，平均绘制记录耗时约 1.8 ms。遥测采样仍为 20 Hz，不能据此宣称外置镜像屏或 CarPlay 地图视频也达到 60 FPS。

## 本分支新增功能

- **融合仪表和真实地图**：仪表页面使用 iPhone 输出的 CarPlay 副屏地图；音乐区读取真实封面、歌名、歌手和播放进度。
- **主副屏切换**：CarPlay 主屏双指右滑进入仪表，仪表双指左滑返回主屏；也可点击 CarPlay 中的“仪表”入口或仪表音乐区切页。两页共用显示 Activity，保留视频视图挂载。
- **显示比例与横屏**：支持手机默认布局和六寸仪表 2:1 预设；2:1 内容按物理屏幕居中，兼顾左右刘海，支持两个横屏方向。
- **日夜主题**：仪表日夜配色及独立地图主题；夜间→日间、日间→夜间可分别设置 0～3 秒过渡。
- **ESP32 车辆数据**：在设置中选择“车辆数据 · ESP32 BLE”，配对并绑定目标设备，只重连已绑定地址，车辆 BLE 与 CarPlay 链路独立。
- **功率圆弧**：速度数字外圈按功率变化，驱动为蓝色、回收为绿色；正向满量程 1.4 kW，回收满量程 0.6 kW，超量程钳制。
- **仪表视觉调整**：自绘状态指示灯、地图边缘渐隐、速度环与底栏布局，改善小屏可读性。
- **保留手机适配**：紧凑设置页面、显示尺寸设置、方向处理、音乐、诊断和现有连接功能。

支持通过 ESP32 BLE 接收车辆速度、挡位、电量、电压、电流、功率和指示灯。电气数据有效时用电压 × 电流计算功率，保留回收负功率；里程由 ESP32 按速度累计，不是车辆原厂总里程。协议不提供真实续航，本分支按有效电量 × 0.85 显示“估算续航”，电量无效则显示未知。未连接、关闭 BLE 或状态超时 3 秒时恢复演示模式，已连接但无效的字段显示“--”。副屏地图暂不转发触摸，尚未实现完整 CarPlay Ultra 协议。

## 配套 ESP32 项目

本项目可与 [ESP32 Vehicle Dashboard](https://github.com/xmz28/esp32-vehicle-dashboard) 配套使用：ESP32 采集 CAN／RS485 车辆数据，经 BLE 传给 Android 融合仪表；地图和音乐继续由 CarPlay 提供。ESP32 固件、硬件接线及用户资料见关联仓库，ESP32 程序源码不公开。

本次配套 [ESP32 固件 v0.1.8](https://github.com/xmz28/esp32-vehicle-dashboard/releases/tag/v0.1.8)。公开旧固件 v0.1.5 不作为本次 BLE 配套版本；较旧 BLE 固件缺少电压、电流扩展时，手机可回退显示旧功率，电压和电流显示“--”。详细步骤见 [车辆 BLE 接入说明](docs/VEHICLE_BLE_INTEGRATION.zh-CN.md)。

## 下载与安装

从[本分支 Release](https://github.com/xmz28/DiPlay-Phone/releases/tag/v0.2.11-fusion-debug.3) 下载 `DiPlay-fusion-ble-debug.apk`，安装到 Android 手机上。发布的是带运行时认证资源的 **debug APK，使用本机 debug 签名**。

| 项目 | 本次融合仪表 Debug 包 |
| --- | --- |
| Release 标签 | `v0.2.11-fusion-debug.3` |
| Android 包名 | `com.shihab.diplay.hudtest` |
| APK 内版本名 | `0.2.11-fusion-ble-debug.3` |
| versionCode | `32` |
| 构建类型 | debug |
| 校验和及源码提交 | Release 附件 `SHA256SUMS.txt`、`SOURCE.txt` |

相同包名和签名可以覆盖安装；如果系统提示签名冲突，先记录设置，卸载旧包再安装，卸载会清除应用数据。安装完成后进入“CarPlay 主屏”或“融合仪表 · 演示”。

ZedingZhang 的 [`v0.2.11-phone-fixes.2`](https://github.com/ZedingZhang/DiPlay-Phone/releases/tag/v0.2.11-phone-fixes.2) 是本分支使用的基础手机适配版本，**不包含这里新增的融合仪表功能**。

## 连接与使用

1. 开启 Android 手机蓝牙和个人热点；设备支持时可选择 5 GHz。
2. 在 DiPlay 的连接设置中选择仍标为“车载热点”的连接方式，填写这台 Android 手机的热点名称和密码。
3. 保持 iPhone 的 Wi-Fi 和蓝牙开启，允许使用 CarPlay，完成配对与连接。
4. 连接后检查画面、音乐、主副屏切换和设置返回；默认三指下滑打开设置。

手机使用不需要开启 BYD 高级车辆数据或车机热点自动化。Wi-Fi Direct 的手机兼容性仍需逐设备验证；基础版在小米 Mi 10 / Android 11 上的 Wi-Fi Direct 问题尚未解决，不宣称本分支已修复。

## 验证范围

融合仪表研究记录使用 **Redmi K30 5G / Android 13**，配合 **iPhone 15 Pro / iOS 26.7**；已验证真实副屏地图、歌曲封面与进度、2:1 布局及双向横屏。具体实机记录见下方文档。基础版的小米测试与本分支红米测试分别记录，不等同于所有手机或车辆面板通过验收。

2026-10-15 的车辆协议、BLE 分片/超时、功率圈、分方向主题、显示缓冲和显示预设共 36 项定向测试通过。板端演示数据与 CarPlay 地图并行验证记录包含正负电流、计算功率和 ESP32 复位重连；尚未接实车，不视为 CAN/RS485 实车采样或长期稳定性验收。完整测试套件尚未完成；不沿用基础版 932 项测试结果作为本分支结果。本次 mobile lint 与 standalone debug 构建通过，lint 为 18 个警告、无错误；APK 签名与上一融合仪表版本一致，认证资源及 ARM64 库校验通过。完整结果见 Release 说明。

## 文档

- [车辆 BLE 接入与字段有效性](docs/VEHICLE_BLE_INTEGRATION.zh-CN.md)
- [功率圆弧与分方向过渡](docs/POWER_RING_AND_DIRECTIONAL_THEME.zh-CN.md)
- [研究环境与本机构建](docs/RESEARCH.zh-CN.md)
- [融合仪表功能与限制](docs/FUSION_DASHBOARD.zh-CN.md)
- [主副屏切换与显示适配](docs/DISPLAY_SWITCHING.zh-CN.md)
- [2:1 预设、音乐与实机验证](docs/FUSION_CLUSTER_PRESET_AND_MEDIA.zh-CN.md)
- [日夜主题与指示灯](docs/DASHBOARD_THEME_AND_INDICATORS.zh-CN.md)
- [独立地图主题与布局](docs/DASHBOARD_LAYOUT_AND_INDEPENDENT_MAP_THEME.zh-CN.md)
- [仪表视觉精修](docs/DASHBOARD_VISUAL_REFINEMENT.zh-CN.md)
- [连接原理](docs/CARPLAY_WIRELESS_PROTOCOL.zh-CN.md)
- [通用构建说明](docs/BUILD.md)

普通源码/CI 构建不含运行时认证资源；本机独立连接构建通过外部目录显式提供资源。诊断报告由用户选择分享，分享前检查并移除热点密码等个人信息。

## 来源与许可

本分支基于 [ZedingZhang/DiPlay-Phone](https://github.com/ZedingZhang/DiPlay-Phone)，其基础来自 [DiPlay](https://github.com/shihabal3amri/DiPlay) 和 [xcertplay](https://github.com/shilapi/xcertplay)。保留 GPLv3、DiAuto 相关 AGPLv3 及各第三方素材声明；BYD HUD 图标包含单独的非商业许可。详见 [LICENSE](LICENSE) 和 [第三方声明](docs/THIRD_PARTY_NOTICES.md)。

CarPlay 及其图标属于 Apple Inc.；本项目未经 Apple 认证。APK 使用既有实验性配件身份，Android debug 签名与配件身份是两套独立机制。Android 签名密钥、独立认证文件、私有参考图片和本机支持目录不进入源码仓库，APK 作为 Release 附件提供。
