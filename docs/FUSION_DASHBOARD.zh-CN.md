# 融合仪表演示

2026-10-14 更新：主副屏现共用 CarPlayHostActivity；FusionDashboardPane 保持地图视图挂载。主页增加明确的两个页面入口，CarPlay OEM 图标改为“仪表”，双指横滑及过渡动画已实机验证。速度环改为等比例绘制，1920×1080、960×480 的布局与主屏配置测试通过。见[切换与分辨率记录](DISPLAY_SWITCHING.zh-CN.md)。下面保留首版功能与验证信息；旧 FusionDashboardActivity 现仅为兼容跳转入口。

按照选定概念图实现普通 Android Activity：真实 CarPlay 仪表地图 → 本地透明仪表前景 → 可访问的本地按钮。地图由 MediaCodec 直接解码到 TextureView，未使用截屏、JPEG、悬浮窗或 root。

入口：DiPlay 主界面“融合仪表 · 演示预览”。首次打开会启用虚拟副屏地图；已有会话需要先重新连接，然后再次打开预览。未连接时保留演示仪表并显示地图连接提示。

## 页面内容

- 左侧：48 km/h、D、2.6 kW、蓝色速度环。
- 顶部：演示转向灯、灯光图标、连接状态、当前时间、明确的“车辆数据演示”标记。
- 右侧：真实 stream 111 地图；顶部导航条从 `CarPlayGlance` 读取距离、道路和转向类型。没有导航数据时显示开始导航提示，不生成假导航指令。
- 底部：78% 电量、62 km 续航、1,186 km 里程；以上车辆数据均为固定演示值，尚未接入 CAN/BLE。
- 音乐信息来自 CarPlay。点击左下音乐区域进入完整 CarPlay；右下 Home 回 DiPlay 主界面并保留会话。

## 实现与边界

`FusionDashboardView` 专门绘制透明前景，未修改 MyApplication 或 bleconnect，也未引入它们的协议代码。`FusionDashboardActivity` 管理副屏 Surface 生命周期、等比例地图布局、全屏和页面切换。前景使用 1920×864 设计坐标适配横屏；地图按原始比例显示，不拉伸、不裁切。渐变和上下状态栏遮盖部分地图边缘。

前景状态每 350 ms 刷新，真实视频独立刷新；这不是 60 FPS 动画实现或性能承诺。CarPlayGlance 采用轮询读取，避免覆盖现有 widget 的单监听器。

地图颜色、3D 建筑和导航内部布局由 iPhone 输出决定。概念图的深色写实 3D 地图尚未复现；本地前景与布局已落地。地图没有转发触摸；完整 CarPlay 入口用于地图、音乐等交互。

这里实现的是接收端本地融合效果，不是完整 CarPlay Ultra 协议。后续真实车辆数据应替换演示模型，并增加断开/过期状态，不能把演示值当作车辆测量值。

2026-10-13 首次红米实测：stream111=true，计数从 39 增加到 132；一段统计接收 13.8 FPS、呈现 13.6 FPS、recoveries=0。静止时帧率降低，尚未验证长期稳定性。演示 Home 点击已确认回 DiPlay 主界面，进程保留。最新构建和切换结果以实测截图、日志为准。

最终 APK SHA256：`DA6EBA5ADEB560B09A77FEB3EA981AF1C12E5E72DEAEF7B0867BCC910316A41C`。最终构建与覆盖安装通过；真机点击音乐区域后前台为 CarPlayHostActivity，再进入融合页成功。17:35 一段副屏呈现统计为 20.6–22.6 FPS、recoveries=0；源端后续也输出了深色地图。实机截图位于同级支持目录 `DiPlay-local-support/backups/fusion-dashboard.png`。未进行完整单元测试、lint、无线并发和车辆数据验证。
