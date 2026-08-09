# 拾光阅读

<p align="center">
  <img src="artwork/app-icon.png" alt="拾光阅读图标" width="128" height="128">
</p>

拾光阅读（SoNovelReader）是一款简洁、专注的 Android EPUB 阅读器。书籍、阅读进度和偏好设置均保存在本地，无需登录即可使用。

## 功能特性

- 从文件选择器或其他应用导入单本、批量导入 EPUB
- 展示书籍封面、标题、作者与阅读进度
- 支持目录跳转，并自动保存上次阅读位置
- 提供滚动阅读与分页阅读两种模式
- 可调整字号、行距和屏幕常亮设置
- 提供跟随系统、浅色、深色和护眼色阅读主题
- 支持从书架删除书籍及其本地阅读记录
- 对导入文件执行路径、体积和外部内容安全检查

> 本项目不提供书籍内容，请导入你有权使用的无 DRM EPUB 文件。

## 环境要求

- Android Studio（建议使用可支持项目所需 Android Gradle Plugin 的最新稳定版）
- JDK 25（项目已配置 Gradle Daemon JVM 工具链）
- Android SDK 37
- Android 10（API 29）或更高版本的设备或模拟器

## 构建与运行

克隆项目后，使用 Android Studio 打开项目并等待 Gradle 同步完成，然后运行 `app` 配置。

也可以在项目根目录使用 Gradle Wrapper：

```bash
# Windows
./gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

生成的调试 APK 位于 `app/build/outputs/apk/debug/`。

运行单元测试：

```bash
# Windows
./gradlew.bat test

# macOS / Linux
./gradlew test
```

## 技术栈

- Kotlin 与 Jetpack Compose
- Material 3
- Room
- DataStore
- epub4j
- Jsoup
- Coil

## 数据与隐私

应用将导入的书籍、封面、阅读进度及阅读偏好保存在设备本地。项目当前不包含账号、云同步或分析服务。卸载应用会由 Android 按系统规则清理应用数据；从书架删除书籍会同时删除该书籍的本地副本与阅读进度。

## 许可证

本项目采用 [MIT License](LICENSE) 开源。
