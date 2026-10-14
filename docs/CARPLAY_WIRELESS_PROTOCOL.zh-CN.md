# DiPlay 无线 CarPlay：连接、握手、鉴权与 Wi-Fi 切换

整理日期：2026-10-13。

本文依据当前 `DiPlay-Phone` 本地源码，重点说明经典蓝牙连接、iAP2 链路协商、设备识别、配件鉴权及 Wi-Fi 参数交接。正文中的顺序和消息号描述本项目实现，不代表所有车机或所有 iOS 版本必须采用相同实现。本次没有通过实机抓包验证时序；示意报文不包含实际证书、私钥或热点密码。

## 1. 两端分别负责什么

安卓运行 DiPlay，扮演车机/配件端；iPhone 是 CarPlay 内容和应用的运行端。

| 功能 | 安卓 / DiPlay | iPhone / iOS |
|---|---|---|
| 无线网络 | 准备或使用热点，提供接收服务 | 加入热点 |
| 蓝牙引导 | 连接 iPhone 的 iAP2 RFCOMM 服务 | 提供服务，参与 iAP2 交互 |
| 配件身份 | 提供证书、对挑战签名 | 校验证书和签名，决定是否接受 |
| CarPlay 画面 | 接收、解密、解码、显示 | 渲染并编码画面 |
| 声音 | 接收并播放音频，按协商回传麦克风 | 发送音频，接收麦克风 |
| 操作 | 回传触摸、旋钮、按键 | 处理操作并更新画面 |

因此，“传输 iOS”实际是传输 CarPlay 画面、音频、输入和状态数据。安卓不需要运行 iOS，也不负责运行 iPhone 上的导航应用。

Apple 的公开介绍同样把蓝牙作为无线启动链路，把 Wi-Fi 用于后续音视频和 iAP2 数据传输：[Developing Wireless CarPlay Systems](https://developer.apple.com/videos/play/wwdc2017/717/)。下面的具体消息号和代码行为以本地源码为依据。

## 2. 总体阶段与调用链

```text
准备鉴权提供者、选择 iPhone
    ↓
准备热点、绑定 AirPlay 接收服务、启动 Bonjour
    ↓
经典蓝牙 RFCOMM 连接
    ↓
iAP2 检测和链路同步
    ↓
设备识别：1D00 → 1D01 → 1D02
    ↓
配件鉴权：AA00 → AA01 → AA02 → AA03 → AA05
    ↓
发送状态订阅，响应 Wi-Fi 和 CarPlay 会话请求
    ↓
iPhone 加入 Wi-Fi，建立 AirPlay 会话
    ↓
在 AirPlay type-130 数据流内建立新的 iAP2 链路
    ↓
隧道内再次识别、鉴权、订阅
    ↓
完成蓝牙交接，持续传输音视频和控制数据
```

上图表示逻辑阶段。热点、AirPlay 监听和 Bonjour 是在蓝牙引导前准备的；Wi-Fi 配置请求、CarPlay 可用通知及传输标识通知由 iPhone 驱动，并非只能按一个固定消息顺序到达。

主要调用关系：

```text
CarPlayController.runWireless
  ├─ 准备热点和 AirPlay 服务
  ├─ CarPlayBonjour
  ├─ BluetoothRfcommDuplexStream
  ├─ Iap2Session.openWireless
  └─ Iap2WirelessControlClient.run
       ├─ Iap2IdentificationClient.identify
       ├─ Iap2MfiAuthenticationClient.run
       ├─ 发送 subscriptions
       └─ 处理 5702 / 4300 / 4E0D / 4E0E 等消息

AirPlay type-130 数据流
  └─ CarPlayController.startWirelessTunnelControl
       ├─ Iap2Session.openTunnel
       └─ Iap2WirelessControlClient.run
```

## 3. 蓝牙阶段：系统配对与协议连接

### 3.1 系统蓝牙配对不是 CarPlay 鉴权

Android 与 iPhone 的系统蓝牙配对建立蓝牙连接所需的信任关系。CarPlay 还需要后续设备识别、配件身份校验及网络会话。

“系统蓝牙列表里显示已配对”只说明蓝牙层已有配对记录，不说明 iAP2 已就绪，更不说明 iPhone 已接受 CarPlay 配件身份。

### 3.2 本项目主动连接 iPhone RFCOMM 服务

`CarPlayController` 使用：

```kotlin
device.createRfcommSocketToServiceRecord(
    UUID.fromString("00000000-deca-fade-deca-deafdecacafe")
)
```

这是经典蓝牙 RFCOMM 流式连接，不是 BLE GATT，不需要通过 BLE 特征值收发这些报文。

连接成功后，`BluetoothRfcommDuplexStream` 将 socket 包装成双向字节流。上层 iAP2 不依赖一次读取恰好对应一条消息：一条协议帧可能分多次读取，多条协议帧也可能在一次读取中到达。

控制器给 RFCOMM 连接设置了 15 秒超时。连接失败时应先检查所选 iPhone、蓝牙权限、配对状态及服务连接，尚未进入证书验证阶段。

## 4. iAP2 链路握手：先建立可靠消息通道

### 4.1 协议有多层，不能把消息号当成整个包

```text
RFCOMM 双向字节流
  └─ iAP2 Link：检测、同步、序号、确认、校验、重传
       └─ Control Session / CSM
            └─ 消息 ID，例如 1D01、AA03、5703
                 └─ 参数列表及嵌套参数组
```

同一套识别/鉴权逻辑也可运行在 USB 或 Wi-Fi 隧道上，但底层链路配置有所不同。

### 4.2 检测标记与同步

本项目发送 iAP2 检测标记：

```text
FF 55 02 00 EE 10
```

无线 RFCOMM 模式调用 `openWireless`，发送检测标记后让 iPhone 发起同步。链路引擎参与同步参数交换和确认；接收对端同步、确认协商条件满足后，链路进入 `NORMAL`，控制会话才可正常写入。

同步载荷包括协议版本、最大帧长度、重传/确认时间、重传/确认限制和会话描述。本实现声明 Control、External Accessory、File Transfer 会话，ID 分别为 10、11、12。

链路引擎在检测状态每 1000 ms 重发检测标记，在协商状态每 500 ms 重发同步包。这些是协议重试计时，不是整个 CarPlay 连接的总超时。

### 4.3 Link 帧结构

`Iap2LinkEngine` 的基本帧布局：

```text
FF 5A | 总长度:u16 | 控制位:u8 | 序号:u8 | 确认号:u8
      | 会话ID:u8 | 头校验:u8 | [载荷 | 载荷校验:u8]
```

头部为 9 字节；存在载荷时追加载荷校验字节。长度字段为大端，包含整帧长度。消息识别与重组不能只查找 `AA` 或 `57` 字节，也不能把每次 RFCOMM read 当成一帧。

蓝牙配置启用确认机制。丢包、乱序、校验失败和对端 reset 属于链路问题，需要先于设备识别/鉴权排查。

### 4.4 CSM 参数结构

CSM 消息载荷使用参数列表：

```text
参数长度:u16 | 参数ID:u16 | 参数内容
```

参数长度包括 4 字节参数头。参数可以嵌套成组，同一 ID 可以重复出现，因此本项目保留参数顺序和重复项，不直接把整个消息转成普通 map。

标量按大端编码；字符串由 UTF-8 字节加 NUL 终止符组成。具体消息帧封装见 `Iap2CsmFramer`，参数编码见 `Iap2Wire` 和 `Iap2WireCodec`。

## 5. 设备识别：声明身份和能力

`Iap2IdentificationClient.identify` 先等待链路就绪，再处理以下消息：

| 顺序 | 方向 | 消息 | 处理 |
|---|---|---|---|
| 1 | iPhone → 安卓 | `0x1D00` StartIdentification | 要求提供设备识别信息 |
| 2 | 安卓 → iPhone | `0x1D01` IdentificationInformation | 返回设备信息和能力 |
| 3 | iPhone → 安卓 | `0x1D02` IdentificationAccepted | 进入鉴权阶段 |
| 失败分支 | iPhone → 安卓 | `0x1D03` IdentificationRejected | 解析被拒绝参数并抛出异常 |

`0x1D01` 不是证书，也不是签名。它用于告诉 iPhone：这个配件是什么、能收发哪些消息、使用哪些传输组件。

主要参数包括：

| 参数 ID | 当前内容 |
|---|---|
| 0～5 | 名称、型号、厂商、序列号、固件版本、硬件版本 |
| 6 / 7 | 配件发送 / 接收的消息 ID 列表 |
| 10 | External Accessory 协议组件 |
| 12 / 13 | 当前语言 / 支持的语言 |
| 17 | 无线模式下的蓝牙传输组件，包括安卓端蓝牙 MAC |
| 24 | 无线 CarPlay 传输组件，包括 SSID 和传输能力标记 |
| 22 | 按配置启用的定位组件 |
| 30 | RouteGuidance 组件和道路名称等字段长度限制 |

有线模式使用 USB 传输组件参数 16，不能原样复制无线组件配置。

本项目为无线两条链路生成不同配置：蓝牙引导阶段关闭定位、车辆状态和车速能力声明；Wi-Fi 运行隧道保留对应配置。源码注释解释，这可避免 iOS 把长期定位/车辆数据绑定到即将关闭的蓝牙端点，之后不接受 Wi-Fi 端点的数据。

因此，能力声明既要与实际实现一致，也要与当前链路职责一致。随意增加“支持的消息”并不会自动实现对应功能。

## 6. 配件鉴权：证书与挑战签名

### 6.1 完整交互

```text
iPhone                                      安卓 / 鉴权提供者
  | ---- AA00：请求证书 ------------------------> |
  | <--- AA01：返回配件证书 --------------------- |
  | ---- AA02：挑战数据，参数 0 ----------------> |
  |                        使用身份私钥签名       |
  | <--- AA03：签名响应 ------------------------- |
  | ---- AA05：成功，或 AA04：失败 -------------> |
```

`Iap2MfiAuthenticationClient` 进入该阶段时先加载证书，随后等待请求；不会仅因读取证书成功就宣布鉴权成功。

收到 `AA02` 后解析参数 0，要求挑战长度在 1～128 字节内，再调用 `MfiAuthenticator.signChallenge`。这是通用客户端限制，各鉴权提供者还有自己的输入要求。

只有收到 `AA05` 才返回成功。`AA04`、挑战参数缺失、非法长度、意外消息和超时都会产生鉴权异常。

### 6.2 本地离线身份

`LocalMfiAuthenticationClient` 从应用私有目录 `offline-mfi` 加载：

- `identity.pk8`：PKCS#8 编码的 EC 私钥。
- `certificate.p7b`：配件证书容器。

加载时检查证书数量、P-256 曲线，并用随机挑战做一次签名/验签，验证私钥与证书公钥匹配。

实际挑战要求恰好 32 字节，使用 `NONEwithECDSA` 签名。这一层收到的是摘要，不能再用 `SHA256withECDSA` 重复哈希。签名从 DER 编码转换为固定 64 字节的 `r || s` 格式，各部分不足 32 字节时前补零。

三个结论需要分开看：

1. 文件可解析：格式和基础配置正确。
2. 本地签名可验：私钥与证书公钥一致。
3. iPhone 返回 `AA05`：对端接受了本次身份鉴权。

前两个结论不能替代第三个。自己生成一对匹配的密钥和证书，不能据此推断 iPhone 会信任。

### 6.3 CH341 与鉴权芯片

硬件路径：

```text
DiPlay → Android USB → CH341 USB/I²C 桥 → 配件鉴权芯片
                                                ├─ 读取证书
                                                └─ 对挑战签名
```

CH341 提供总线访问，鉴权芯片提供身份和签名能力。iPhone 看到的仍然是 iAP2 证书/签名响应，不需要知道安卓背后使用哪一种桥接器。

鉴权提供者与 CarPlay 传输方式是两个独立选择：接上 USB CH341 可以继续使用无线 CarPlay；它并不意味着 iPhone 的音视频改走 USB。

当前连接说明规定，选择 USB/CH341 后不回退到已有本地身份，缺硬件时等待硬件；切回本地时先验证本地身份。设置行为见 [CONNECTION_SETUP.md](CONNECTION_SETUP.md)。

## 7. 鉴权后订阅与 Wi-Fi 参数交接

### 7.1 先发送五项订阅

识别和鉴权通过后，`Iap2WirelessControlClient` 调用共用的 `subscriptions()`，发送以下订阅：

| 消息 ID | 订阅内容 |
|---|---|
| `0x5000` | Now Playing 媒体状态 |
| `0x5200` | Route Guidance 导航引导 |
| `0xAE00` | 电源状态 |
| `0x4157` | 通信状态 |
| `0x4154` | 通话状态 |

发送完订阅会触发 `onReady`，但在蓝牙引导阶段，这不代表画面已经建立。

### 7.2 请求 Wi-Fi 配置：5702 / 5703

收到 `0x5702` RequestAccessoryWiFiConfiguration 后，安卓发送 `0x5703`，内容由当前热点信息构造：

- SSID。
- Passphrase。
- 信道。
- 安全类型：当前枚举为 NONE=0、WEP=1、WPA/WPA2=2。

这些是安卓/车机热点的参数，不是 iPhone 个人热点参数。对于内置车机热点，应用保存的 SSID、密码必须与车机实际配置一致。

当前 `accessoryWiFiConfiguration` 构造函数没有传入 BSSID，不能从返回消息中推断它总会携带 BSSID。

### 7.3 CarPlay 会话启动：4300 / 4301

收到 `0x4300` CarPlayAvailability 后，当前实现解读可用性信息用于诊断，并发送 `0x4301` CarPlayStartSession。

无线启动参数包括：

| 参数 | 用途 |
|---|---|
| SSID、密码、信道、安全类型 | 加入正确的无线网络 |
| IP 地址列表 | 指向安卓接收端 |
| AirPlay 端口 | 指向实际绑定的服务端口 |
| Device Identifier | 标识接收设备 |
| Public Key | AirPlay 长期配对身份公钥 |
| Source Version | 协议实现版本信息 |

这里的 Public Key 是 AirPlay 配对身份公钥，不能与配件证书公钥或一次连接中的临时密钥混为一谈。端口来自实际监听结果，不应在调试工具中假定所有连接都固定使用同一端口。

### 7.4 无线状态与传输标识：4E0D / 4E0E

`0x4E0D` 更新无线 CarPlay 可用状态。程序记录 available，并保留是否曾收到可用状态的信息。

`0x4E0E` 通知设备传输标识；程序解析其中的蓝牙/USB 标识，标记已经收到传输通知，并再次发送 `0x5703`。

重发有次数限制：传输通知前最多发送 5 次 Wi-Fi 配置，通知后最多 2 次。`5702` 请求和 `4E0E` 触发共享相应阶段的计数，超过限制时记录 ignored，不无限发送。

该实现是事件驱动的：不要把 `4E0D`、`4E0E`、`5702`、`4300` 排成只能发生一次的固定四步握手。

## 8. Bonjour、Wi-Fi 和 AirPlay 的衔接

安卓在启动蓝牙引导前已经把 AirPlay 服务绑定到所选热点的地址，并启动 Bonjour/mDNS。

`CarPlayBonjour` 广播 `_airplay._tcp`，TXT 信息包括设备标识、功能位、型号、版本、配对 ID 和配对公钥；也发现 iPhone 的 `_carplay-ctrl._tcp` 控制服务，并使用 `GET /ctrl-int/1/connect` 探测连接。

这两种方向都存在：安卓提供媒体接收服务，同时也主动探测 iPhone 控制服务。不能把所有网络交互都理解成“安卓一直等 iPhone 连接”。

iPhone 加入 Wi-Fi 后仍需完成 AirPlay 协议交互：

| 请求 | 作用 |
|---|---|
| `/pair-setup` | 建立并保存长期配对关系；本实现使用 SRP-6a，随后交换 Ed25519 身份信息 |
| `/pair-verify` | 使用 X25519 临时密钥协商，并验证长期 Ed25519 身份签名 |
| `/auth-setup` | AirPlay MFiSAP 配件身份验证，返回证书和针对双方临时公钥的签名 |
| `/info` | 返回屏幕、音频、输入等接收端能力 |
| `SETUP` | 建立事件、时钟及媒体流，返回对应接收端口 |
| `RECORD` | 通知会话进入运行状态 |

具体请求次序由 iPhone 驱动，首次配对与已配对重连可能不同。源码支持这些入口，不能仅凭入口存在断言每次连接都会依次调用全部入口。

`pair-verify` 为控制通道派生 ChaCha20-Poly1305 密钥。`auth-setup` 则使用新的 X25519 协商结果，在本实现中对配件签名做 AES-128-CTR 加密。两者解决的事情不同：配对身份用于会话信任和加密，配件证书用于证明配件身份。

## 9. Wi-Fi 隧道内再次识别和鉴权

这是本项目很容易遗漏的阶段。

AirPlay type-130 数据流用于承载数据；媒体引擎会根据流信息处理 iAP 隧道等数据用途。对于无线 iAP 隧道，控制器调用 `startWirelessTunnelControl`，在新字节流上打开 `Iap2Session.openTunnel`。

该链路由安卓主动发起同步，使用与蓝牙不同的 zero-acknowledgement 配置。随后再次执行：

```text
链路就绪 → 1D00/1D01/1D02 → AA 鉴权 → 五项订阅 → onReady
```

长期定位、车辆状态和导航控制数据由运行隧道承担。程序收到隧道 `onReady` 后标记 `wirelessTunnelReady`；满足交接条件时关闭蓝牙引导通道。

因此日志中出现两次识别/鉴权可能是正常行为：第一次发生在 RFCOMM，第二次发生在 Wi-Fi iAP 隧道。应通过 `wireless-rfcomm` 与 `wireless-tunnel` 上下文区分。

关闭蓝牙引导通道不等于关闭系统全部蓝牙功能；其他蓝牙用途需要分别判断。

## 10. 什么时候才算连接成功

| 已看到的现象 | 能证明什么 | 还缺什么 |
|---|---|---|
| 系统蓝牙已配对 | 系统配对记录存在 | RFCOMM 和所有上层协议 |
| RFCOMM connected | 蓝牙 socket 建立 | iAP2 链路、识别和鉴权 |
| identification accepted | 配件能力声明被接受 | 配件鉴权 |
| AA05 authentication-succeeded | 当前 iAP2 链路鉴权通过 | Wi-Fi、AirPlay 和媒体流 |
| 5703 / 4301 已发出 | 已提供网络/会话启动参数 | iPhone 实际加入并连接服务 |
| AirPlay SETUP | 已处理相应流协商 | 数据到达、解密、解码、渲染 |
| wireless iAP2 tunnel ready | 运行隧道识别、鉴权和订阅完成 | 渲染结果 |
| 隧道已就绪且首帧已渲染 | 满足本项目无线成功确认条件 | 音频、输入等功能仍需分别验证 |

`WirelessConnectionProof` 同时等待当前连接 generation 的 authenticated 和 rendered 标记，避免把上一轮会话的回调算到新连接里。蓝牙交接的 `WirelessActive` 状态和这个成功证明是不同机制。

正式媒体方向：iPhone 下发 TCP 视频和 UDP/RTP 音频；安卓解密后用 `MediaCodec` 显示画面、用 `AudioTrack` 播放声音，触摸通过 HID 事件上行，麦克风按协商上行。

## 11. 按最后成功阶段排查

| 停在什么位置 | 优先检查 |
|---|---|
| RFCOMM 无法连接 | 目标 iPhone、系统配对、Android 蓝牙权限、iAP2 服务、连接超时 |
| iAP2 control session readiness 超时 | 检测标记、同步发起角色、字节读写、帧校验与链路参数 |
| 收到 1D03 | 被拒绝参数 ID、消息能力列表、传输组件、必需字段和编码 |
| 证书加载/本地自检失败 | 证书格式、私钥格式、P-256 曲线、证书和私钥是否匹配 |
| AA02 后签名失败 | 挑战参数 0、输入长度、重复哈希、签名格式、芯片 I/O |
| 收到 AA04 | iPhone 拒绝当前鉴权；需要结合证书和签名检查，不能只据此确定单一原因 |
| 已发 5703，但没有 Wi-Fi 会话 | 热点实际开关、SSID/密码、信道、安全类型、iPhone 提示是否接受 |
| 已发 4301，但无法连接服务 | 上报 IP/端口、服务实际绑定地址、网络接口、Bonjour 与控制探测 |
| pair-verify 失败 | 双端配对记录、长期身份是否发生变化、签名和密钥协商 |
| 蓝牙鉴权成功，隧道鉴权失败 | 区分两条 iAP2 链路、运行隧道声明与鉴权日志 |
| SETUP 后无画面 | 媒体端口连接、收包计数、解密结果、编码配置、MediaCodec 和 Surface |

建议在日志中依次查找：`wireless RFCOMM connected`、`iap2 identification accepted`、`authentication-succeeded`、`accessory-wifi-configuration`、`carplay-start-session`、`airplay SETUP`、`wireless iAP2 tunnel ready`、首帧渲染记录。

读日志时保留 RX/TX、消息 ID 和链路上下文；不同线程的日志可能交错，不应只按相邻文本推断两个阶段属于同一链路。

## 12. 源码阅读索引

以下链接相对于本文所在 `docs` 目录：

| 文件 | 重点 |
|---|---|
| [CarPlayController.kt](../shared/src/main/java/com/shilapi/xcertplay/orchestration/CarPlayController.kt) | 热点、监听、蓝牙、隧道及交接总流程 |
| [BluetoothRfcommDuplexStream.kt](../shared/src/main/java/com/shilapi/xcertplay/transport/BluetoothRfcommDuplexStream.kt) | 蓝牙字节流适配 |
| [Iap2LinkChannel.kt](../shared/src/main/java/com/shilapi/xcertplay/transport/Iap2LinkChannel.kt) | 无线与隧道链路配置差异 |
| [Iap2LinkEngine.kt](../shared/src/main/java/com/shilapi/xcertplay/transport/Iap2LinkEngine.kt) | 检测、同步、确认、帧结构和重传 |
| [Iap2Session.kt](../shared/src/main/java/com/shilapi/xcertplay/iap2/session/Iap2Session.kt) | 控制消息收发与上下文日志 |
| [Iap2CsmFramer.kt](../shared/src/main/java/com/shilapi/xcertplay/iap2/wire/Iap2CsmFramer.kt) | CSM 帧封装 |
| [Iap2IdentificationClient.kt](../shared/src/main/java/com/shilapi/xcertplay/transport/Iap2IdentificationClient.kt) | 1D 消息及能力参数 |
| [Iap2MfiAuthenticationClient.kt](../shared/src/main/java/com/shilapi/xcertplay/mfi/Iap2MfiAuthenticationClient.kt) | AA 证书/挑战响应状态机 |
| [LocalMfiAuthenticationClient.kt](../shared/src/main/java/com/shilapi/xcertplay/mfi/LocalMfiAuthenticationClient.kt) | 本地身份加载、P-256 签名 |
| [MfiAuthenticationClient.kt](../shared/src/main/java/com/shilapi/xcertplay/mfi/MfiAuthenticationClient.kt) | 鉴权芯片访问 |
| [Iap2WirelessControlClient.kt](../shared/src/main/java/com/shilapi/xcertplay/transport/Iap2WirelessControlClient.kt) | 订阅、Wi-Fi 配置、会话启动与重发限制 |
| [Iap2Messages.kt](../shared/src/main/java/com/shilapi/xcertplay/iap2/message/Iap2Messages.kt) | 控制消息参数构造 |
| [CarPlayBonjour.kt](../shared/src/main/java/com/shilapi/xcertplay/network/CarPlayBonjour.kt) | 服务广播、发现及 connect 探测 |
| [AirPlaySession.kt](../shared/src/main/java/com/shilapi/xcertplay/airplay/AirPlaySession.kt) | 配对/鉴权入口、SETUP 与事件通道 |
| [MfiSapAuthSetup.kt](../shared/src/main/java/com/shilapi/xcertplay/airplay/MfiSapAuthSetup.kt) | AirPlay 配件鉴权 |
| [WirelessConnectionProof.kt](../shared/src/main/java/com/shilapi/xcertplay/orchestration/WirelessConnectionProof.kt) | 隧道鉴权与实际渲染的成功确认 |

## 13. 仍需实机确认的边界

本文确认的是本地实现如何发送和处理协议，尚未证明特定 iPhone/iOS、特定安卓硬件的实际连接结果。实机核对重点为：RFCOMM 与 Wi-Fi 两段真实消息时序、iPhone 返回的鉴权结果、实际使用的热点地址/端口、隧道交接及首帧/音频/输入行为。

首次连接需要验证系统配对、CarPlay 用户确认和 AirPlay 配对；重连则要验证保存的配对记录与身份是否稳定。修改证书、AirPlay 身份或热点配置后，应分别观察受影响阶段，避免把不同身份机制的失败混为一个“鉴权失败”。
