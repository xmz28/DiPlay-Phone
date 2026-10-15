# 红米 K30 副屏地图实测

2026-10-13，设备 Redmi K30 5G / Android 13，ADB serial `<DEVICE_SERIAL>`。

## 已验证

- 新增普通 Activity 验证页面 `ClusterMapTestActivity`，使用 TextureView 接收 `MapMirrors` 的 stream 111。无需 root、悬浮窗权限或真实第二块屏。
- 设置页“副屏地图验证（实验）”可以打开页面；“启用副屏并重新连接”保存 cluster_map_enabled=true 并按现有连接模式重新建立会话。
- 虚拟副屏配置使用现有代码的 1280×720；独立流和主屏同时建立：17:15:15.338 type=111 dataPort=38345；17:15:15.442 type=110 dataPort=42437。
- 实机页面显示独立地图、定位标记和导航路线，TextureView 更新计数持续增长。
- 17:15:41 一段 5 秒统计：rx=25.8fps shown=25.8fps maxGap=100ms recoveries=0。静止导航时后续窗口为 6.8–8.2fps；帧率随源端更新变化，这不是稳定满帧性能结论。
- 切回 CarPlay 主屏显示正常，再打开验证页，111 地图继续更新，保留同一应用进程和连接。
- 本次连接日志标签为 xcertplay-usb，仅作为有线模式实测证据；无线副屏尚未单独实测。
- assembleStandaloneDebug 构建成功，认证资源哈希校验通过，覆盖安装成功，git diff --check 通过。

APK SHA256：`8EADCA3D4B3A9F1C5066E112C0D9857C21F455BF30C469A1546BA07B2B850AE2`。

实机截图保存在同级支持目录 `DiPlay-local-support/backups/cluster-test.png`（副屏）与 `DiPlay-local-support/backups/cluster-main.png`（主屏）。

## 验证范围

这证明现有 CarPlay 仪表地图流可以作为 Android 本地合成界面的地图层。尚未实现雅迪车辆仪表融合，也不证明完整 CarPlay Ultra 的资源、布局和动画同步协议已可用。没有验证其他导航应用、副屏触摸交互、长时间稳定性或实车动态刷新。

ADB 可从主入口打开验证页（非导出的测试 Activity 无法由外部直接启动）：

```powershell
adb -s <DEVICE_SERIAL> shell am start -f 0x10020000 -n com.shihab.diplay.hudtest/com.shilapi.xcertplay.DiPlayActivity --ez cluster_test true
```
