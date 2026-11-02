# ClassSoftwareHub for Android

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Android CI](https://github.com/c1201y/ClassSoftwareHub-Android/actions/workflows/android.yml/badge.svg)
[![Release](https://img.shields.io/github/v/release/c1201y/ClassSoftwareHub-Android)](https://github.com/c1201y/ClassSoftwareHub-Android/releases)
![minSdk](https://img.shields.io/badge/Android-8.0%2B-green)

把教室电脑上「要装的那堆软件」搬到手机上管：面向班级电教委员的原生 Android 客户端，浏览软件清单、看系统要求、直接下载，并附带一套课堂常用工具（抽号、计时、喊话、取色……）。

将 [ClassSoftwareHub 桌面版](https://github.com/c1201y/ClassSoftwareHub-Desktop) 重构而来，数据与 [站点仓库](https://github.com/c1201y/ClassSoftwareHub) 同源。

- 目标平台：Android 8.0+（API 26+）
- 技术栈：Kotlin + Jetpack Compose + Material 3
- 数据：Retrofit + OkHttp + kotlinx.serialization（远程）/ Room（缓存、下载记录、草稿）/ DataStore（设置）
- 下载：OkHttp 断点续传 + 前台服务（DownloadService）+ WorkManager（进程重启恢复）

## 功能一览

| 模块 | 说明 |
|---|---|
| 首页（软件库） | 顶部平台大分类（全部 / Android / Windows / macOS / Linux / iOS / 网页版，默认 Android；平台清单来自仓库 platforms.json，缺失时从系统要求文案推断）、搜索、业务分类筛选（系统维护 / 教学管理 / 办公通讯 / 远程协助）、软件卡片列表、详情页多来源下载 |
| 下载管理 | 常驻底部 Tab（角标显示进行中数量）；进行中 / 已完成 / 失败三态管理；取消、重试、打开、清理；前台服务后台下载；完成通知 |
| 工具箱 | 图片取色 / 颜色转换、随机抽号（名单点名 / 抽奖，可分名单管理）、课堂计时（倒计时 / 秒表计次）、函数图像、方程求解、化学配平、单位换算、编码 / 哈希、二维码生成、系统镜像下载、教室喊话 |
| 教室喊话 | 手机给教室电脑发一句话：电脑全屏弹窗 + 语音播报（手机只发送，接收在电脑端）；配对码管理、在线状态、多台勾选批量发送 |
| 提交中心 | 提交软件、问题反馈：填表直接送达站点审核服务（无需登录 GitHub）；支持软件包 / 图标直传本站存储，联系方式本地加密后提交 |
| 设置 | 主题（浅色 / 深色 / 跟随系统）、动态取色、更新通道、下载路径（SAF 自定义目录）、更新日志、关于与开源许可 |

## 获取安装包

| 想要的东西 | 去哪拿 |
|---|---|
| 正式版 APK | [Releases](https://github.com/c1201y/ClassSoftwareHub-Android/releases)（打 `v*` tag 由 Actions 自动构建并发布） |
| 任意提交的开发版 | [Actions → Android CI](https://github.com/c1201y/ClassSoftwareHub-Android/actions/workflows/android.yml) 当次运行页底部的 `app-debug` 产物 |

手机上点 APK 安装即可，需要允许「安装未知来源应用」。

## 软件数据源与镜像取数（v1.1.0 起）

手机大多连不上 GitHub，软件清单改为镜像链路拉取（与桌面版同源，数据就是站点仓库 `c1201y/ClassSoftwareHub` 里的「软件数据/」）：

1. **本地缓存时效**：一次成功同步后 12 小时内启动直接读本地 Room 缓存，一个请求都不发；点右上角刷新按钮可强制绕过。
2. **站点仓库镜像**：
   - 文件内容：`gh.classsoftwarehub.cn`（自建 GitHub 反代，首选）→ `cdn.jsdelivr.net` → `gh-proxy` → `ghfast` 逐个回退，并记住上次成功的入口；该反代只放行浏览器风格 UA，整条链路统一按浏览器 UA 发请求；
   - 文件清单：`gh-proxy.com` / `ghfast.top` 代理的 GitHub contents 接口（权威）→ trees 接口 → jsDelivr 数据接口 → 打包清单 `assets/catalog-index.json`（自建反代不镜像 GitHub API，全部在线列目录失败时用它保证仍可更新内容）；
   - 快路径：时效过期后先问分支头提交号，与上次一致时直接跳过，一个文件都不下。
3. **站点接口** `https://api.classsoftwarehub.cn/api/software`（兜底；截至 2026-11-02 该接口返回 404，仅保留代码通道）。
4. **内置目录** `assets/catalog.json`（完全离线时可用，129 款，与仓库同源）。

## 教室喊话（手机 → 教室电脑，只发送）

与桌面端「教室喊话」共用自建 Worker 中继，多入口按序回退（`api.classsoftwarehub.cn` → `submit.132614.xyz`），记住成功入口、单入口 10 秒超时：

- 发送：`POST /api/announce/send`，请求体 `{pairCode, text, from, duration}`；text ≤100 字、署名 ≤20 字、时长 15/30/60 秒；
- 查询：`GET /api/announce/list?pairCode=…` 取机器名与最近轮询时刻（用于设备列表的在线状态）；
- 手机只负责发送；接收、全屏弹窗与语音播报在电脑端完成（电脑端打开「内置工具 → 教室喊话」并保持联网）。

## 提交与反馈（v1.2.0 起）

提交软件与问题反馈不再依赖 GitHub 登录：直接 POST 到自建 Worker（入口 `api.classsoftwarehub.cn` → `submit.132614.xyz` 按序回退、记住成功入口、单入口 10 秒预算）：

1. **联系方式加密**：`_联系方式`（提交软件）/ `contact`（反馈）在手机本地用 age v1（X25519 + ChaCha20-Poly1305，纯 Kotlin 实现）加密成 armor 密文后随请求提交，服务端拒收明文；实现与桌面端 `AgeEncryption.cs` 同口径（单测含 RFC 8439 / 7748 / 5869 与 CCTV age x25519 官方解密向量）。
2. **软件包 / 图标直传**：`POST /api/oss-sign` 换取一次性预签名 PUT 地址后直传对象存储——
   - 软件包（`purpose=file`，上限 2.5 GB，>1 GB 弹窗提示）写入 `oss://<key>`，下载走服务端票据；
   - 图标（`purpose=icon`）在本地压到 128×128 PNG 后上传，写入 `{入口}/api/icon?k=<key>`；
   - 上传成功会提示「请在 N 分钟内提交，否则自动回收」，提交成功自动清理草稿。
3. **反馈**：`kind=report|suggestion`，报告问题再选 `subKind=interaction|link|other`，可指定「涉及软件」；标题 ≤80 字、详情 ≤1800 字；服务端自动建单，无需 GitHub 登录。
4. **兜底**：所有入口都不通时，可改用浏览器打开预填的 GitHub Issue（联系方式仍只放密文）。

## 与桌面版的对应关系

| 桌面版功能 | Android 实现 |
|---|---|
| 窗口背景效果 / 置顶 | 移除，使用 Material 3 动态取色 |
| 系统托盘 | 前台服务 + 通知 |
| 侧边栏贴靠 | 移除，工具独立为底部 Tab |
| 下载管理入口 | 常驻底部 Tab + Badge |
| 本机核实 | PackageManager 检测已安装应用 |

## 项目结构

```
ClassSoftwareHub-Android/
├── app/src/main/
│   ├── assets/
│   │   ├── catalog.json          # 内置软件目录（完全离线时的数据源，与站点仓库同源）
│   │   ├── catalog-index.json    # 仓库内 app 文件清单（在线列目录全部失败时的兜底）
│   │   └── changelog.json        # 更新日志
│   ├── java/com/classsoftwarehub/android/
│   │   ├── ClassSoftwareHubApp.kt     # Application + AppContainer（轻量依赖容器）
│   │   ├── MainActivity.kt            # 单 Activity，处理通知路由跳转
│   │   ├── data/
│   │   │   ├── model/                 # 领域模型：Software / DownloadTask / AppSettings
│   │   │   ├── local/                 # Room：entity / dao / AppDatabase / Mappers
│   │   │   ├── remote/                # Retrofit API / DTO / 镜像取数（RepoCatalogSource）/ 喊话（AnnounceClient）/ 提交与直传（SubmitClient / SubmitPayload）
│   │   │   └── repository/            # Software / Download / Settings / Draft / Announce 仓库
│   │   ├── download/                  # DownloadService / 通知 / WorkManager 恢复
│   │   ├── util/                      # 格式化、文件打开、哈希、颜色、二维码、Issue URL、键值存储、age 加密（util/crypto）、数学表达式 / 方程 / 化学配平（util/math）
│   │   └── ui/
│   │       ├── theme/                 # Material 3 主题（动态取色）
│   │       ├── navigation/            # Routes + AppNavHost
│   │       ├── main/MainScreen.kt     # Scaffold：QQ 风贴底导航（5 Tab，直角通栏 + 发丝分割线 + Haze 轻磨砂）
│   │       ├── home/                  # 首页（软件库）+ 软件详情
│   │       ├── downloads/             # 下载管理
│   │       ├── tools/                 # 工具箱（取色 / 抽号 / 计时 / 函数图像 / 方程 / 化学配平 / 单位换算 / 编码 / 二维码 / 镜像下载 / 教室喊话）
│   │       ├── submit/                # 提交软件 + 问题反馈
│   │       ├── settings/              # 设置 / 更新日志 / 关于
│   │       └── components/            # 软件卡片、图标、空状态等公共组件
│   └── res/                           # 图标、主题、FileProvider 路径
└── keystore.properties（可选）        # Release 签名配置
```

## 从零搭建步骤

### 1. 准备环境

1. **JDK 17**（AGP 8.5 要求）：
   - 下载 Temurin 17：https://adoptium.net/temurin/releases/?version=17
   - 解压后设 `JAVA_HOME` 指向 JDK 目录。
2. **Android SDK**（命令行工具即可，无需 Android Studio）：
   - 下载 commandline-tools：https://developer.android.com/studio#command-line-tools-only
   - 解压到 `<SDK目录>/cmdline-tools/latest/`
   - 安装组件：
     ```powershell
     sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
     sdkmanager --licenses   # 全部输入 y 接受许可
     ```
3. **Gradle 8.7**：可直接用系统 Gradle（`gradle wrapper` 生成包装器），或下载 https://services.gradle.org/distributions/gradle-8.7-bin.zip 解压使用。

### 2. 配置项目

在项目根目录创建 `local.properties`（也可由 Android Studio 自动生成）：

```properties
sdk.dir=C\:\\Android\\sdk
```

### 3. 构建

```powershell
# 使用 Gradle 包装器（推荐）
./gradlew assembleDebug        # 产出 app/build/outputs/apk/debug/app-debug.apk

# 或使用系统 Gradle
gradle assembleDebug
```

首次构建会下载依赖（Compose、Room、Retrofit 等），耗时较长属正常现象。

### 4. Release 签名（可选）

生成签名文件并配置：

```powershell
keytool -genkeypair -v -keystore keystore/release.jks -alias classsoftwarehub `
  -keyalg RSA -keysize 2048 -validity 10000 -storepass <密码> `
  -dname "CN=ClassSoftwareHub, OU=Dev, O=CSH, L=Beijing, C=CN"
```

> JDK 17 的 keytool 默认建 **PKCS12** 库，而 PKCS12 不支持给单把私钥单独设口令（会提示「PKCS12 密钥库不支持单独存储密钥口令」并强制与库口令一致）。所以别再传 `-keypass`，下面 `keystore.properties` 里 `storePassword` 与 `keyPassword` 填同一个值即可。

在项目根目录创建 `keystore.properties`：

```properties
storeFile=keystore/release.jks
storePassword=<密码>
keyAlias=classsoftwarehub
keyPassword=<同一个密码>
```

然后执行 `./gradlew assembleRelease`，产物位于 `app/build/outputs/apk/release/`。可用 `apksigner verify --print-certs app-release.apk` 确认签名者确实是这把密钥（而不是 debug 回退）。未配置签名时会自动回退到 debug 签名，便于内测分发。

**这把密钥就是应用的长期身份**：换库之后老用户无法覆盖升级，务必把 `keystore/release.jks` 和口令备份到仓库之外（GitHub 上的 Secret 只是副本，仓库删了就拿不回来了）。

## 构建产物

| 产物 | 路径 | 说明 |
|---|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | 调试签名，可直接安装测试 |
| Release APK | `app/build/outputs/apk/release/app-release.apk` | release 构建类型（未开 `minify`/`shrinkResources`）；未配置 `keystore.properties` 时使用 debug 签名回退 |

安装：`adb install -r app/build/outputs/apk/debug/app-debug.apk`，或把 APK 发送到手机点击安装（需允许「安装未知应用」）。

## 持续集成与发布（GitHub Actions）

工作流：`.github/workflows/android.yml`

| 触发 | 动作 |
|---|---|
| push 到 `main` / Pull Request / 手动 | JDK 17 + Android SDK 34，跑 `testDebugUnitTest` 与 `assembleDebug`，上传 `app-debug` 产物 |
| 打 `v*` tag | 额外 `assembleRelease`，自动创建 GitHub Release 并挂载 APK |

> Runner 在海外，仓库 wrapper 默认指向腾讯云 Gradle 镜像，工作流里会把 `distributionUrl` 临时改回官方地址再构建（只影响 CI，不改仓库文件）。

> 两个任务都固定跑在 `ubuntu-24.04`（不是 `ubuntu-latest`）：后者正在向 Ubuntu 26 迁移，镜像里的 Android SDK 预装不再稳定。CI 直接用镜像自带 SDK，只用 `sdkmanager` 幂等补齐 `platforms;android-34` 与 `build-tools;34.0.0`。`gradlew` 需要保留 Unix 可执行位（在 Windows 上首次提交请用 `git update-index --chmod=+x gradlew`，否则 Linux runner 会 `Permission denied`）。

发布正式版前，在 **仓库 Settings → Secrets and variables → Actions** 配置下列 Secret（签名文件与口令都不入库，`.gitignore` 已排除 `keystore.properties` 与 `*.jks`）：

| Secret | 内容 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `keystore/release.jks` 的单行 base64（Linux/macOS `base64 -w0 keystore/release.jks`；Windows `certutil -encode` 后去掉头尾拼成一行） |
| `RELEASE_STORE_PASSWORD` | keystore 口令 |
| `RELEASE_KEY_ALIAS` | 密钥别名（对应上文 `classsoftwarehub`） |
| `RELEASE_KEY_PASSWORD` | 私钥口令（PKCS12 下与 `RELEASE_STORE_PASSWORD` 相同） |

未配置这些 Secret 时 Release 任务照样成功，但会走 debug 签名回退，发布说明里会标注。**签名密钥一旦更换，旧包无法覆盖安装**，所以首次对外发版就应把 Secret 配齐。

```bash
git tag v1.2.1 && git push origin v1.2.1   # 触发正式版构建与发布
```

## 网络受限环境构建（实测记录）

在无法直连 Google / Maven 官方的网络环境下，本次构建使用如下替代方案并**已完整跑通**：

1. **工具链**：JDK 17（[aka.ms](https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip)）+ Gradle 8.7（[腾讯镜像](https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip)），解压即用，无需 Android Studio。
2. **Android SDK 组件**：`sdkmanager` 无法访问 dl.google.com 时，可直接从腾讯镜像 `https://mirrors.cloud.tencent.com/AndroidSDK/` 下载并手动放置：
   - `platform-34-ext7_r03.zip` → 解压出的 `android-34/` 放入 `platforms/`
   - `build-tools_r34-windows.zip` → 解压出的 `android-14/` 重命名为 `build-tools/34.0.0/`
   - `platform-tools_r35.0.0-windows.zip` → 解压到 `platform-tools/`
   - 在 `licenses/android-sdk-license` 写入许可哈希（内容为三行：`8933bad161af4178b1185d1a37fbf41ea5269c55`、`d56f5187479451eabf01fb78af6dfcb131a6481e`、`24333f8a63b6825ea9c5514f83c2829b004d1fee`）
3. **Maven 依赖**：`settings.gradle.kts` 已内置阿里云镜像（`maven.aliyun.com/repository/google|public|gradle-plugin`）优先、官方仓库兜底，无需任何改动。
4. **Gradle 包装器**：`gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 已指向腾讯镜像，首次 `gradlew` 运行不会被官方源的慢速拖垮；如需恢复官方源改回 `https://services.gradle.org/distributions/gradle-8.7-bin.zip` 即可。

验证环境就绪：

```powershell
java -version          # 17.x
gradlew.bat --version  # Gradle 8.7
```

## 数据源配置

- 软件数据权威来源：站点仓库 `c1201y/ClassSoftwareHub` 的「软件数据/apps/」，经 `https://gh.classsoftwarehub.cn/gh/raw.githubusercontent.com/...` 取内容（国内直连可达，无需科学上网）。
- 站点主域：`https://classsoftwarehub.cn`（下载站页面，「关于」页里的「软件下载站」指向它）；后端接口统一挂在 `https://api.classsoftwarehub.cn`（喊话中继、提交与反馈、图标代理）。
- 备用远程 API：`https://api.classsoftwarehub.cn/`（接口 `GET /api/software`，返回 JSON 数组，见 `SoftwareDto`），可在 `app/build.gradle.kts` 的 `DEFAULT_API_BASE_URL` 中修改；该接口当前为 404，走的是短超时通道，不会拖慢刷新。
- 远程不可用时自动回退到内置目录 `assets/catalog.json`，并缓存到 Room；刷新按钮为强制拉取（跳过 12 小时本地时效）。
- 内置目录条目可在 `assets/catalog.json` 中自行维护（字段与 `SoftwareDto` 一致）；同步更新 `assets/catalog-index.json` 里的仓库文件路径清单。

## 注意事项

- 应用内下载的软件多为 Windows 安装包（.exe/.msi），下载完成后可通过「打开」调用系统方式查看，或经数据线 / 文件中转传递到电脑使用。
- 「本机核实」需要 `QUERY_ALL_PACKAGES` 权限（面向侧载分发；若上架应用商店需评估该权限政策）。
- 通知权限（Android 13+）用于下载进度与完成提醒，首次启动时申请。

## 开源许可

本项目采用 **GNU General Public License v3.0（或更高版本）**，全文见 [`LICENSE`](LICENSE)。与上游桌面版 [ClassSoftwareHub-Desktop](https://github.com/c1201y/ClassSoftwareHub-Desktop) 保持同一协议，两个仓库的代码可以互相搬运而不产生许可冲突。

要点（非法律意见）：

- 你可以自由使用、研究、修改本项目的代码；
- 一旦**分发**修改版或以本项目为基础的衍生应用（含 APK 分发），必须以 GPL-3.0 开源全部对应源码，并保留版权声明与本许可证；
- 单纯自用、公司内部部署不受分发条款约束；
- 本项目按「原样」提供，不含任何担保。

第三方组件各自遵循其原始许可证（AndroidX / Jetpack Compose / OkHttp / Room / kotlinx.serialization / WorkManager 多为 Apache-2.0），聚合分发时请一并保留其声明。仓库内置的 `assets/catalog.json` 等软件数据来自站点仓库，版权归各软件厂商，仅作为下载指引收录。

## 致谢

- [ClassSoftwareHub-Desktop](https://github.com/c1201y/ClassSoftwareHub-Desktop)：本项目的上游桌面版，软件数据与取数链路以它为准。
- [c1201y/ClassSoftwareHub](https://github.com/c1201y/ClassSoftwareHub)：收录数据的权威仓库，以及 `gh.classsoftwarehub.cn` GitHub 反代。
- 各收录软件的开发者与厂商。
