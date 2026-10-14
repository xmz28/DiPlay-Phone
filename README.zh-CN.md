# DiPlay 手机融合仪表版

将 Android 手机用作 CarPlay 接收端，在完整 CarPlay 主屏之外增加融合仪表：右侧显示真实 CarPlay 副屏地图，左侧显示仪表，底栏显示真实音乐封面、歌曲和播放进度。

本分支为 `xmz28/DiPlay-Phone` 的 `research/android-baseline`，基于 [ZedingZhang 的手机适配版](https://github.com/ZedingZhang/DiPlay-Phone)继续修改。以下说明对应本分支的融合仪表版本。

[下载融合仪表 Debug APK](https://github.com/xmz28/DiPlay-Phone/releases/tag/v0.2.11-fusion-debug.1) · [本仓库全部 Release](https://github.com/xmz28/DiPlay-Phone/releases) · [源码与讨论](https://github.com/xmz28/DiPlay-Phone/pull/1)

## 本分支新增功能

- **融合仪表和真实地图**：仪表页面使用 iPhone 输出的 CarPlay 副屏地图；音乐区读取真实封面、歌名、歌手和播放进度。
- **主副屏切换**：CarPlay 主屏双指右滑进入仪表，仪表双指左滑返回主屏；也可点击 CarPlay 中的“仪表”入口或仪表音乐区切页。两页共用显示 Activity，保留视频视图挂载。
- **显示比例与横屏**：支持手机默认布局和六寸仪表 2:1 预设；2:1 内容按物理屏幕居中，兼顾左右刘海，支持两个横屏方向。
- **日夜主题**：仪表日夜配色、过渡时长，以及跟随仪表或独立设置的地图主题。
- **仪表视觉调整**：自绘状态指示灯、地图边缘渐隐、速度环与底栏布局，改善小屏可读性。
- **保留手机适配**：紧凑设置页面、显示尺寸设置、方向处理、音乐、诊断和现有连接功能。

车辆速度、电量、功率、电压、电流、续航和里程目前是**演示值**，未接入 CAN/BLE。副屏地图暂不转发触摸。本项目尚未实现完整 CarPlay Ultra 协议。

## 下载与安装

从[本分支 Release](https://github.com/xmz28/DiPlay-Phone/releases/tag/v0.2.11-fusion-debug.1) 下载 `DiPlay-fusion-debug.apk`，安装到 Android 手机上。发布的是带运行时认证资源的 **debug APK，使用本机 debug 签名**。

| 项目 | 本次融合仪表 Debug 包 |
| --- | --- |
| Release 标签 | `v0.2.11-fusion-debug.1` |
| Android 包名 | `com.shihab.diplay.hudtest` |
| APK 内版本名 | `0.2.11-orientation-lock-hud-test`（沿用原工程） |
| versionCode | `30`（沿用原工程） |
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

本分支公开前的 29 项相关回归测试通过，覆盖显示尺寸、方向、双指切页和显示预设；mobile 源码构建及 lint 通过，lint 有 18 个警告、无错误。完整单元测试套件因测试运行时下载等待未完成，不将基础版的 932 项测试结果作为本分支验收结果。本次发布重新构建并核验 debug APK；没有新增实机验收。

## 文档

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
