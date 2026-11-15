# 拾光阅读

<p align="center">
  <img src="artwork/app-icon.png" alt="拾光阅读图标" width="128" height="128">
</p>

拾光阅读（SoNovelReader）是一款简洁、专注的 Android EPUB 阅读器。书籍、阅读进度和偏好设置均保存在本地，无需登录即可使用。

## 功能特性

- 从文件选择器或其他应用导入单本、批量导入 EPUB
- 展示书籍封面、标题、作者与阅读进度
- 支持目录跳转，并自动保存上次阅读位置
- 提供滚动阅读、左右分页与仿真翻页三种模式
- 可调整字号、行距和屏幕常亮设置
- 内置朱雀仿宋、寒蝉端黑宋、霞鹜文楷，支持从文件导入静态 TTF / OTF 阅读字体
- 独立关于页集中展示字体与第三方库的作者、LICENSE / NOTICE 全文和来源链接
- 提供跟随系统、浅色、深色和护眼色阅读主题
- 支持从书架删除书籍及其本地阅读记录
- 对导入文件执行路径、体积和外部内容安全检查
- 正式版可在设置页手动检查更新，并通过浏览器下载新版 APK

> 本项目不提供书籍内容，请导入你有权使用的无 DRM EPUB 文件。

## 环境要求

- Android Studio（建议使用可支持项目所需 Android Gradle Plugin 的最新稳定版）
- JDK 25（项目已配置 Gradle Daemon JVM 工具链）
- Android SDK 37
- Android 10（API 29）或更高版本的 ARM64 / X86_64 设备或模拟器；不再支持 32 位系统和应用 ABI

## 构建与运行

更新检测使用 `io.github.zerofancy:kmp-updater:0.1.0-SNAPSHOT` 核心库，沿用本项目的设置页界面。
该版本尚未发布到 Maven Central，首次同步前需从相邻的 `kmp-updater` 仓库发布到本地 Maven：

```bash
cd ../kmp-updater
./gradlew :updater:publishToMavenLocal
cd ../so-novel-reader
```

修改更新库后也需重新发布。本项目仅对该库及其平台变体启用本地 Maven。
然后使用 Android Studio 打开项目并等待 Gradle 同步完成，运行 `app` 配置。

也可以在项目根目录使用 Gradle Wrapper：

```bash
# Windows
./gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

生成的调试 APK 位于 `app/build/outputs/apk/debug/`。

Release 签名支持通过系统钥匙圈管理密码，配置和初始化步骤见 [Gradle 钥匙圈签名说明](docs/keyring-signing.html)。

运行单元测试：

```bash
# Windows
./gradlew.bat test

# macOS / Linux
./gradlew test
```

## 专用更新测试包

可显式构建与正式版独立安装、独立查询的签名测试包：

```bash
./gradlew --no-configuration-cache -PupdateTestPackage=true -PupdateTestVersionCode=1 :app:assembleRelease
# 保存旧版 APK 后，将 updateTestVersionCode 改为 2 构建新版
```

测试包名为 `top.ntutn.sonovelreader.updatetest`，更新软件名为 `so-novel-reader-update-test`，
应用名显示“拾光阅读·更新测试”。只有提供 `updateTestPackage=true` 才启用，且必须指定正整数版本码。
默认构建继续使用正式包名、`so-novel-reader` 更新序列和原版本号。测试包与正式版均只支持 ARM64/X86_64。

真机更新回归（需已连接设备）：

```bash
./gradlew -PkeyringSigning=false \
  -Pandroid.testInstrumentationRunnerArguments.class=top.ntutn.sonovelreader.ui.UpdateSettingsTest,top.ntutn.sonovelreader.ui.LiveUpdateSettingsTest \
  -Pandroid.testInstrumentationRunnerArguments.liveUpdates=true :app:connectedDebugAndroidTest
```

线上测试默认跳过，仅上述开关显式启用；读取既有独立测试序列的版本 2，并验证外部浏览器跳转。
设备可能要求确认本次浏览器跳转。测试不发布版本、不自动安装浏览器下载的 APK。

## 技术栈

- Kotlin 与 Jetpack Compose
- Material 3
- Room
- DataStore
- epub4j
- Jsoup
- Coil
- KMP Updater 核心库与 Ktor Client（OkHttp 引擎）

## 数据与隐私

应用将导入的书籍、封面、阅读进度及阅读偏好保存在设备本地。项目当前不包含账号、云同步或分析服务。卸载应用会由 Android 按系统规则清理应用数据；从书架删除书籍会同时删除该书籍的本地副本与阅读进度。

正式版在“设置 → 应用更新 → 检查更新”访问 `https://zp.ntutn.top/api/v1/updates`，只发送软件内部名、Android 系统、安装架构与 APK 类型，不发送书籍或阅读数据。Debug 版本禁用检查。发现更新后由用户选择是否通过浏览器下载，不自动安装；服务端尚未发布对应软件时会明确提示。交互与接口约定见 [检查更新 Spec](docs/specs/update-check.html)。

## 字体与第三方声明

阅读设置中可选择内置字体、导入自己的静态 TTF / OTF，或进入独立「关于」页查看字体与第三方库的作者、LICENSE / NOTICE 和链接。
用户导入字体只保存在本机，不上传，也不安装成系统字体。卸载应用会清理私有字体副本。

实现范围与真机结果见[字体 Spec](docs/specs/reader-fonts.md)和[设备验证记录](docs/reader-font-verification.md)。
Release 构建会校验字体资源及第三方声明是否与实际依赖一致；更新依赖后的声明维护流程见[授权维护说明](docs/credits/README.md)。

## 许可证

本项目采用 [MIT License](LICENSE) 开源。
