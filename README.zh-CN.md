# DiPlay

> **本分支的手机适配：** 基于 `v0.2.11-phone-fixes.2` 开展本地研究，保留旋转、显示尺寸和手机紧凑布局修复。普通手机提供可关闭的原生“仪表”入口，进入融合仪表并保留连接；名称可自定义，比亚迪设备保留原有默认名称与图案。原有手机回归步骤见[手机回归检查](docs/TESTING.md)。PR #6 的 Wi-Fi Direct 专项排查仍未纳入本次修改。

## 本地研究版本（2026-10-14）

已在 Redmi K30 5G / Android 13 上验证独立仪表地图流，并完成融合仪表演示：左侧速度环、顶部状态与导航条、底部电量和里程，右侧使用真实 CarPlay 副屏地图。进入 DiPlay 主界面的“融合仪表 · 演示”即可查看；音乐区域进入完整 CarPlay，“主页”返回 DiPlay。车辆数值明确标记为演示值，尚未接入 CAN/BLE。

主界面现提供“CarPlay 主屏”和“融合仪表 · 演示”两个入口；主屏双指右滑进入仪表，仪表双指左滑返回主屏。两页共用一个显示 Activity，保持视频视图挂载，使用 240 ms 过渡；CarPlay 原生“仪表”图标可直接切页，仪表里的“主页”返回 DiPlay。[切换与分辨率适配记录](docs/DISPLAY_SWITCHING.zh-CN.md)说明具体实现、测试和限制。

这是 Android 接收端的本地融合显示，尚未实现完整 CarPlay Ultra 协议。地图样式由 iPhone 输出决定，副屏当前不转发触摸；不能将短时实测帧率视为长期稳定性保证。研究功能不需要 root 或系统悬浮窗权限。

- [环境配置、编译与本地研究入口](docs/RESEARCH.zh-CN.md)
- [融合仪表功能与限制](docs/FUSION_DASHBOARD.zh-CN.md)
- [日夜主题、地图覆盖与指示灯定义](docs/DASHBOARD_THEME_AND_INDICATORS.zh-CN.md)
- [配色渐变、左侧状态栏与红米有效显示区域](docs/DASHBOARD_TRANSITION_AND_STATUS_RAIL.zh-CN.md)
- [红米副屏地图实测记录](docs/CLUSTER_MAP_VERIFICATION.zh-CN.md)
- [车辆仪表融合与后续接入方案](docs/YADEA_DISPLAY_PLAN.zh-CN.md)
- [无线连接、握手与鉴权原理](docs/CARPLAY_WIRELESS_PROTOCOL.zh-CN.md)
- [源码与本机支持文件的目录整理](docs/DIRECTORY_LAYOUT.zh-CN.md)

源码工程与 `DiPlay-local-support` 支持目录独立存放。普通源码构建不含配件认证资源；本机研究构建通过外部目录显式配置，认证文件、APK 和本机缓存不提交到 Git。

以下为上游公开预览版的说明，其车辆支持范围与本地研究功能应分别理解。

为兼容的比亚迪安卓车机提供有线及无线 CarPlay，采用 DiAuto 风格界面。

> 这些项目专注于比亚迪汽车。它们可能在其他品牌上运行，但其他品牌不在支持范围内，也没有增加支持或修复其品牌特定兼容性问题的计划。

[下载与中文网站](https://shihabal3amri.github.io/DiPlay/zh-Hans/) · [0.2.11 版本](https://github.com/shihabal3amri/DiPlay/releases/tag/v0.2.11) · [完整说明](README.md) · [报告问题](https://github.com/shihabal3amri/DiPlay/issues/new/choose)

## 0.2.11 — 公开预览版

请安装在车机上，而非 iPhone。无需越狱、转接盒、账户或认证服务器。最低支持 Android 9；Wi-Fi Direct 需要 Android 10 或更高版本，也可使用车机内置热点或 USB。有线及无线 CarPlay 核心连接不要求 ADB，可选车辆数据等功能需要已授权的网络 ADB。

### 0.2.11 新增与修正

- **Wi-Fi Direct 首选信道**：默认仍为自动，可保存支持的 2.4/5 GHz 信道，在下次连接生效。车机拒绝或使用了其他信道时会报错，请改回自动或换信道。此功能不代表卡顿问题已解决。
- 在仪表地图上显示可移动的自定义转向卡片，支持大小选择及每次 2% 的位置调整，无需重连。未知转向不会显示猜测的箭头，过期指引会清除。
- 打开设置的下滑手势可选 2、3 或 4 指，默认仍为 3 指。新增 Android TV/遥控器操作，并保留普通车机触摸、返回及旋钮行为。
- 在“设置 → 位置 → 高级车辆数据”中可选旧版车机只读字段探测，默认仍使用 DiLink 5.0 路径。只有已确认并接受的字段及读数才用于运行时；修正过期探测结果及电池数据发布的并发问题。
- 可选自动开启车机已有热点，默认关闭。启用前验证仅针对 DiPlay 自身应用包的权限，保留热点名称与密码。
- 无线位置及车辆数据由实际 Wi-Fi 链路发送；停车视频等待 SETUP 和事件通道就绪。非 P 挡或无法读取挡位时仍会关闭视频。
- 歌曲增量更新未提供歌手时保留已有歌手；媒体会话只在歌曲信息或封面变化时重新发布，播放进度及状态继续更新。
- 修正 Android 9 音频 API 兼容性，释放启动失败的编解码器；重连采用稳定后的屏幕尺寸和正常启动检查。自动信道模式下为精确匹配的 Android 10 P2P 错误提供一次受保护的兼容回退，有线 VPN 仅作用于 DiPlay。
- 新增有界无线、媒体、昼夜模式及本应用退出诊断，不录制音频、视频或数据包内容，也不自动上传报告。

旧版车辆数据、电池、轮速及停车视频需要已授权的网络 ADB 和支持的有效读数；仪表及热点等功能取决于车机固件。请参阅[完整版本说明](docs/RELEASE-NOTES-0.2.11.md)及[验证记录](docs/VALIDATION.md)。Qin Plus 连接、Wi-Fi Direct 卡顿、Siri/麦克风质量、iOS 15 连接和部分固件昼夜模式仍需真机复现，未宣称全部修复。

### 请提供新的诊断报告

更新到 **0.2.11** 后复现问题，再打开“**设置 → 诊断 → 保存诊断报告**”。Android 10 及以上保存到 **Downloads/DiPlay**；Android 9 使用文件选择器。检查 `.txt` 内容后附在原有 [GitHub 问题](https://github.com/shihabal3amri/DiPlay/issues)中，并注明车型/车机、安卓及固件、iPhone/iOS、连接模式、复现步骤和大致发生时间。报告由你决定是否分享，请勿公开热点密码。

这是公开预览版，**未经 Apple 认证**。APK 使用从公开 Carlinkit 固件中提取的既有实验性配件身份，并非为 DiPlay 新签发的 MFi 身份；其中的私钥可被提取，未来 iOS 是否继续接受及其公开分发适用性尚未确定。Android 签名密钥和配件身份不进入 Git 或源代码压缩包；普通源代码/CI 构建默认不配置身份。部分车机仍可能卡顿或无法应用图标大小设置。

标准导航小组件需要支持 Android 小组件的启动器；比亚迪内置主页不接受任意小组件。悬浮地图和嵌入地图需要启用“CarPlay 仪表地图”。应用及网站支持英语、简体中文、阿拉伯语、俄语、乌克兰语和西班牙语。源代码、构建说明及许可证随版本提供。

- [六寸仪表比例预设、音乐及电压电流](docs/FUSION_CLUSTER_PRESET_AND_MEDIA.zh-CN.md)


- [顶部与竖列布局、独立地图日夜和渐变时长](docs/DASHBOARD_LAYOUT_AND_INDEPENDENT_MAP_THEME.zh-CN.md)

- [公开图标与私有参考资料边界](docs/PUBLIC_ICON_ASSET_BOUNDARY.zh-CN.md)
