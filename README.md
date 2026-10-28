# 小爱白名单解除 / XiaoAi Allowlist Adapter

基于 LSPosed 的小爱官方智控适配实验模块。当前版本 **0.20**。

> **尚未进行多手机测试。目前仅在一台小米 15 Pro 上验证过加载和部分操作。不能保证其他小米／Redmi 手机、系统版本、小爱版本或所有应用与话术正常工作。**

## 作用

扩展客户端窗口名单，并将符合条件的应用操作请求交给官方小爱智控规划和执行。支持从语音打开应用继续操作，或手动打开应用后说“在当前应用……”。应用目录来自本机已安装应用。

**这不是删除云端白名单。** 模块仍依赖官方服务、账号资格与小爱内部接口，不会补出设备原本没有的官方智控能力。任务建议采用停止旧任务、重新创建官方任务的方式，不能保证保留原任务历史。

## 安装

1. 准备已 Root 的小米／Redmi 手机、可工作的 LSPosed，以及具备官方智控的小爱同学。
2. 安装 [0.20 APK](dist/XiaoAiAllowList-0.20.apk)。
3. 在 LSPosed 中启用“小爱名单适配”，作用域仅选择“小爱同学”（`com.miui.voiceassist`）。关闭旧版同类模块。
4. 重启手机，打开模块并刷新兼容状态。
5. 使用简单页面操作验证实际效果。

Android 9 是模块安装下限，不代表该系统一定支持官方智控。详细说明见 [安装说明](docs/INSTALL.zh-CN.txt)。

## 兼容状态与测试范围

| 项目 | 结果 |
| --- | --- |
| 唯一实测手机 | 小米 15 Pro，2410DPN6CC |
| 系统 | Android 16 / HyperOS 3.0.6.0 |
| 小爱版本 | 7.13.34.3812 / 507013034 |
| 0.20 加载检查 | 文字、语音、窗口组件均加载 |
| 本地检查 | 8 项兼容检测、55 项路由、12 项任务说明检查通过 |
| 第二台手机测试 | **未进行** |
| 游戏内活动签到、任务中途语音建议稳定性 | 尚未完成完整验收 |

已知版本接口匹配时显示已匹配；其他版本只有必需类与方法签名全部匹配才尝试加载，并标为“未实测”；缺少接口时不启用业务 Hook。接口匹配并不证明实际任务成功。内部类名变化的版本需要额外适配，并非任意版本自动适配。

## 源码与构建

源码在 `src/`，LSPosed 入口在 `assets/xposed_init`，本地检查源码在 `test/`。

Windows PowerShell 构建需要 JDK 21、Android SDK Build Tools（含 aapt2、D8、zipalign、apksigner）、Android API 36 的 android.jar，以及 Xposed API 82 的 JAR。依赖需要自行从其官方来源安装；此仓库不包含依赖、签名私钥或小爱反编译代码。

```powershell
.\build-local.ps1 -AndroidBuildTools 'D:\Android\build-tools\36.0.0' -AndroidJar 'D:\Android\platforms\android-36\android.jar' -XposedJar 'D:\deps\xposed-api-82.jar' -JdkHome 'D:\Java\jdk-21' -Keystore 'D:\keys\release.jks' -KeyAlias release
```

签名密码由 apksigner 交互提示输入。自行构建使用自己的密钥，不能覆盖由另一密钥签名的已安装版本。发布包中的 APK 是此前已签名并在唯一测试手机加载验证的成品；参数化构建脚本尚未在全新构建环境复验。

此仓库不含手机日志、账号数据、令牌、设备序列号、签名私钥或官方应用 APK。项目处于实验阶段，与小米官方无关联。

