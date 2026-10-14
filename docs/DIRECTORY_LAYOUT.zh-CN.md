# 本机目录整理记录

2026-10-14 从嵌套目录迁移为独立工程。

```text
C:\Users\10145294\AndroidStudioProjects\
├─ DiPlay-Phone\             实际源码、Git 仓库、构建输出
├─ DiPlay-local-support\
│  ├─ tools\                 JDK 25 等工具
│  ├─ backups\               原版 APK 与实机截图
│  ├─ downloads\             下载包
│  └─ original-template\     原外层 Android 模板工程归档
└─ Diplay\                   迁移说明及旧 IDE 自动生成的配置缓存
```

Android Studio 应打开 `C:\Users\10145294\AndroidStudioProjects\DiPlay-Phone`。
本机 Gradle JDK 的路径已调整到支持目录，编译脚本自动寻找该 JDK。
认证资源仍在 `C:\Users\10145294\DiPlay-runtime-assets`，SDK 仍在系统原有位置。

迁移前后核验：666 个源码文件 SHA-256 完全一致；迁移时 Git HEAD 为 `367aa8414037d718da78ace22eb60cd202b94f61`，分支为 `research/android-baseline`，所有已有 Git 修改均保留。迁移后仅额外调整目录引用、JDK 配置及说明文档。

在新目录运行 `scripts\research-build.ps1 -Offline`，完整执行 81 个任务，构建成功；APK 内认证资源哈希校验通过。没有重新安装或操作手机。

旧模板已归档到支持目录。旧源码根目录被进程占用，改为逐项移动其内容，随后通过哈希核验。旧 `Diplay` 目录目前只剩迁移说明及 IDE 自动生成的配置缓存，新工程不依赖它。两个新目录不需要合并，关闭旧 IDE 工程后可以删除旧 `Diplay` 目录。
