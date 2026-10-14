# DiPlay 手机适配版

本仓库公开手机融合仪表实验源码：[源码分支](https://github.com/xmz28/DiPlay-Phone/tree/research/android-baseline)。下方 `v0.2.11-phone-fixes.2` 下载链接为基础手机适配版，不包含新增融合仪表功能。

将 Android 手机用作 CarPlay 接收端。这个分支基于 [DiPlay](https://github.com/shihabal3amri/DiPlay)，针对手机屏幕、横屏布局、设置入口和屏幕方向进行适配。

目前的实际测试设备是 **小米 Mi 10 / Android 11**，配合 **iPhone 16 Pro Max / iOS 18.7.8**。当前成功使用的连接方式是 Android 手机个人热点；Wi-Fi Direct 在这套设备上仍未解决。其他手机组合需要各自验证。

[下载手机适配版](https://github.com/ZedingZhang/DiPlay-Phone/releases/tag/v0.2.11-phone-fixes.2) · [全部 Release](https://github.com/ZedingZhang/DiPlay-Phone/releases) · [修复记录与讨论](https://github.com/xmz28/DiPlay-Phone/pulls) · [实机复测清单](docs/TESTING.md)

## 融合仪表实验源码（2026-10-14）

本分支增加以下研究功能；已有下载版 `v0.2.11-phone-fixes.2` 尚不包含这些新增功能，本次先公开源码。

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

## 当前已发布 APK

最新预览版为 **`v0.2.11-phone-fixes.2`**，基于上游 v0.2.11，并已同步其发布后的 main 快照 `e6e7cc0eaf175ef397eee7ac6223eb267cdc68bd`。

| 项目 | 当前发布包 |
| --- | --- |
| APK 文件 | `DiPlay-0.2.11-phone-test.apk` |
| 应用版本 | `0.2.11-orientation-lock-hud-test` |
| Android 包名 | `com.shihab.diplay.hudtest` |
| 版本号 | 30 |
| 发布源码 | `367aa8414037d718da78ace22eb60cd202b94f61` |
| 发布形式 | 使用调试签名的手机适配预览版，可与原版 DiPlay 共存 |

仓库名称为 **DiPlay-Phone**；当前 APK 的应用名称、包名和版本标识沿用上述测试包信息。

## 已完成的手机适配

- **统一设置界面**：连接前的设置按钮与 CarPlay 内下滑手势进入同一设置页。手势支持两、三或四指，默认三指；保留的高级选项集中在可展开的高级设置中。该设置入口已实机复测通过，见 [PR #8](https://github.com/ZedingZhang/DiPlay-Phone/pull/8)。
- **手机横屏布局**：首页采用紧凑布局；设置页将较短分组并排；连接设置页将连接方式、热点信息与配对、连接操作分列，减少留白和上下滚动。主要按钮和开关的触控区域至少为 48dp。两页优化已实机复测通过，见 [PR #9](https://github.com/ZedingZhang/DiPlay-Phone/pull/9)。
- **设置自动旋转，CarPlay 锁定方向**：设置页随手机切换正反横屏或竖屏，CarPlay 固定进入时的方向。打开设置后可以旋转，返回 CarPlay 时恢复原方向并保持会话。完全断开后换一个方向重新连接，才锁定新的方向。该修改已合入 [PR #10](https://github.com/ZedingZhang/DiPlay-Phone/pull/10)，方向、状态恢复及后台尺寸回调的自动回归通过。
- **CarPlay 尺寸设置实际生效**：大、中、小尺寸选择会改变实际 CarPlay 画面；主动修改尺寸或分辨率并应用时，仍会执行必要的重连。此前已在小米实机复测通过。
- **非 BYD 设备隐藏 BYD 返回图标**：手机不再显示不适用的 BYD 返回入口。此前已在小米实机复测通过。
- **Android 11 中文设置适配**：应用语言覆盖仅覆盖语言，避免设置页面的方向、窗口尺寸和字号固定在旧配置。

本版还保留上游的画面调整、昼夜模式、分辨率、系统栏、音乐信息、诊断导出及 USB 相关功能。上游的 BYD 仪表、车辆数据和车机热点自动化依赖相应车辆与固件，不是手机适配功能，也没有通过本分支的小米测试验证。

## 下载与安装

1. 从 [当前 Release](https://github.com/ZedingZhang/DiPlay-Phone/releases/tag/v0.2.11-phone-fixes.2) 下载 **[DiPlay-0.2.11-phone-test.apk](https://github.com/ZedingZhang/DiPlay-Phone/releases/download/v0.2.11-phone-fixes.2/DiPlay-0.2.11-phone-test.apk)**，安装到 **Android 手机**上。
2. 当前 Release 的 APK 与 PR #10 最后交付的 `DiPlay-0.2.11-orientation-lock-test.apk` 内容完全相同，仅下载文件名不同。已经安装该测试包的设备无需重新安装。
3. 从旧 Release 或其他调试签名的 HUD Test 包升级时，请先记录热点及其他设置，卸载旧的 **DiPlay HUD Test** 后安装新包。卸载会清除测试应用的数据。相同签名的覆盖安装可以保留设置。
4. 详细步骤见 Release 附件 `INSTALL.zh-CN.txt`；校验值见 `SHA256SUMS.txt`，源码及签名来源见 `SOURCE.txt`。

当前 APK SHA-256：

```text
f42ace8b25e64b63adbe54aa67c8249b8fddb83930d6dc2ec5606a5e9e70bd25
```

## 在手机上连接

优先使用此前已成功的 **Android 手机个人热点**方式：

1. 打开 Android 手机蓝牙和个人热点；设备支持时可选择 5 GHz。
2. 在 DiPlay 的连接设置中选择当前仍标为“车载热点”的连接方式，填写或确认这台 Android 手机的热点名称、密码等信息。
3. 保持 iPhone 的 Wi-Fi 和蓝牙开启，在提示出现时允许使用 CarPlay，然后在 DiPlay 中连接手机。
4. 连接后检查画面、触控和音乐。CarPlay 内默认通过三指下滑打开设置。

手机使用不需要启用 BYD 高级车辆数据或车机热点自动化。USB 入口仍保留；上述手机组合的 USB 连接尚无实机验证记录。

## Wi-Fi Direct 的实际状态

**仍未解决，当前不建议将它作为这套测试设备的首选连接方式。**

在小米 Mi 10 / Android 11 与 iPhone 16 Pro Max / iOS 18.7.8 的测试中，iPhone 可以看到并手动加入 DIRECT 网络，但网络检测页无法打开，CarPlay 没有画面。关闭 iPhone 的 VPN 后，检测页仍无法打开。

[PR #6](https://github.com/ZedingZhang/DiPlay-Phone/pull/6) 的专项连接、恢复和诊断修改已搁置，**没有合入 main，也没有包含在当前 Release 中**。已同步的上游首选信道等更新不能据此视为该问题已修复。

## 验证与问题反馈

当前发布源码对应的 [Android 检查](https://github.com/ZedingZhang/DiPlay-Phone/actions/runs/37219976820) 共 **932 项测试全部通过，零失败、零跳过**；mobile、home、maphost 的 Lint 和源码构建均通过。[独立 APK 构建](https://github.com/ZedingZhang/DiPlay-Phone/actions/runs/37219975687) 已核对包名、版本、签名、ARM64、运行资源及编译后的方向配置。

自动测试验证代码行为，实机状态按上面的各项记录分别说明。请在自己的设备上检查连接、显示、触控、音乐，以及旋转和设置返回行为；复测步骤见 [docs/TESTING.md](docs/TESTING.md)。

复现问题后，进入 **设置 → 诊断 → 保存诊断报告**。Android 10 及以上保存到 **Downloads/DiPlay**。反馈时附上 Android 手机型号及系统、iPhone 型号及 iOS、应用版本、连接方式、复现步骤和失败时间；旋转问题还应说明当时的页面及旋转前后的方向。请先检查日志内容，移除不希望公开的信息和热点密码。报告不会自动上传。

仓库当前未启用 Issues，可在已有的 [修复 PR 讨论](https://github.com/xmz28/DiPlay-Phone/pulls) 中反馈相关问题。

## 源码、构建与上游

- [上游 DiPlay](https://github.com/shihabal3amri/DiPlay)：本分支的直接基础，原项目主要面向 BYD 安卓车机。
- [构建说明](docs/BUILD.md)：普通源码／CI 构建默认不包含运行时认证资源；需要连接 iPhone 的独立 APK 使用明确提供的外部运行资源。
- [源码与第三方许可](docs/THIRD_PARTY_NOTICES.md)：接收端基于 [xcertplay](https://github.com/shilapi/xcertplay)，使用 GPL-3.0；部分首页、设置和网站内容改编自 [DiAuto](https://github.com/shihabal3amri/DiAuto)，相关 AGPL-3.0 声明保留在源码及 `docs/licenses` 中。
- [隐私与诊断说明](docs/PRIVACY.md)。

日志、用户截图、运行时认证文件和 Android 签名密钥不提交到源码仓库。发布 APK 沿用上游公开 v0.2.11 包的实验性配件身份；本项目未经 Apple 认证。CarPlay 及其图标属于 Apple Inc.，项目不代表 Apple 或 BYD。
