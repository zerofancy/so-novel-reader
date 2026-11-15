# 阅读字体与关于页：实现和设备验证

日期：2026-11-15

状态：首版功能已实现，已在连接设备安装并验证；下文明确列出未覆盖范围。

## 1. 实现范围

- 设置页新增「阅读字体」及独立「关于」入口。系统默认不变，字体选择对所有书籍生效。
- 三款原样官方静态 Regular 字体随包离线分发：朱雀仿宋 v0.212（官方技术预览）、寒蝉端黑宋 v1.200（非 Pro）、霞鹜文楷 v1.530（原版非等宽）。来源、SHA-256 和原始许可证见[资源清单](../app/src/main/assets/fonts/manifest.json)。
- 支持系统文件选择器导入静态 TTF/OTF，最多 50 MiB，提供所有文件兼容入口。按内容校验、按哈希去重、私有复制、原子目录提交、删除确认及当前字体删除回退。
- 阅读设置与解析完成的字体以同一快照发布。三种模式的正文、章首页标题、分页测量、相邻章和仿真翻页纹理共享字体；UI、目录和沉浸信息栏字体不变。
- 字体切换按正文位置重排；捕获不可变布局代次和跳转令牌，旧布局回调不能覆盖新进度。朗读跟随状态下恢复到最新句子位置，不因字体变化主动停止朗读。
- 关于页统一展示字体、直接/传递依赖、本地第三方模块及已识别内嵌组件的作者、版本、LICENSE/NOTICE 和来源链接。授权全文离线随包，可展开与复制；用户私有字体不加入分发清单。
- Preferences DataStore 新增字体 ID；不变更 Room schema，不使用破坏性迁移。
- Release 构建自动执行实际运行时依赖清单校验及字体资源哈希校验，防止新增/升级依赖后仍打包过期声明。维护流程见[第三方声明说明](credits/README.md)。

## 2. 验证环境与产物

- 真机：NX888J，Android 16 / API 36，ARM64，分辨率 1260 × 2800。
- 设备安装并保留的是正常应用包名的 **Debug 构建**，版本 1.0.2 (3)；没有发布线上版本。
- Debug、AndroidTest APK 编译通过；Release 使用 `-PkeyringSigning=false` 完成构建与 lint vital 检查，产物未签名，不能直接安装。
- 未启用涉及线上更新的 LiveUpdate 测试；未为验证而修改设备网络、系统字体缩放等全局偏好。

本地构建输出与截图位于被 Git 忽略的构建目录，不作为源码或大批截图提交：

- [设备回归原始输出](../build/font-verification/device-regression.txt)
- [字体设置截图](../build/font-verification/font-settings.png)
- [真实 SAF 导入结果](../build/font-verification/saf-import-result.png)
- [删除外部原文件并重启后仍保留字体选择](../build/font-verification/import-after-restart.png)
- [独立关于页截图](../build/font-verification/about-page-final.png)
- [阅读器设备截图示例](../build/font-verification/device-cache/font-PAGED-lxgw-wenkai.png)
- [性能原始记录](../build/font-verification/font-performance.json)
- [包体及安装目录测量](../build/font-verification/package-measurements.json)

## 3. 自动测试结果

| 检查 | 结果与范围 |
| --- | --- |
| JVM 单元测试 | 73 项通过；包括字体名称、结构、哈希、真实 50 MiB 边界、未知大小流、读中断、零长度读取、变量字体拒绝、随机畸形输入及永久/临时故障分类 |
| 真机回归集合 | 59 项通过，原始结果 `OK (59 tests)`；字体功能 2、字体状态协调 2、字体仓库 8，以及阅读器、书签、信息栏、音量键/动画、数据库、书架和更新设置回归 |
| 独立真机性能记录 | 1 项通过，测量三款字体首次仓库解析、缓存解析与大文件导入；不作为通用性能阈值 |
| Python 回归 | 21 项通过：项目行数检查器 13、授权提取与完整文本分段 4、发布授权校验门禁 4（含同版本 SNAPSHOT 内容变更拒绝） |
| 第三方声明校验 | 138 个组件通过：131 个实际 release 外部依赖坐标、PTQFlipper、3 个已识别内嵌组件、3 款字体；与实际 release 图一致，无待补元数据字段 |
| 字体资源校验 | 三款二进制及原始 LICENSE 的字节数、SHA-256、静态 SFNT 结构通过 |
| 项目行数与格式 | 文件行数检查及 `git diff --check` 通过，无新增豁免 |

设备字体专项覆盖：

1. 三款字体分别在 SCROLL/PAGED/FLIP 的章中位置切换，九组组合均保持当前章与附近正文位置；保存截图。
2. 验证字体设置页选择、导入、不自动应用、手动应用、私有字体重新解析、取消删除、确认删除与系统回退。
3. 打开关于页并展开内置字体离线 LICENSE，验证全文读取及复制入口。
4. 目录损坏不等于空列表；暂时读取失败不清除持久化选择，修复目录后可重试；永久失效回退，并保留启动时提示供 UI 稍后消费。
5. 仓库验证真实 TTF/OTF 加载、去重、原文件移除、重新创建仓库、受控路径、AtomicFile 恢复/提交失败回滚、读取中断及复制期间取消清理。
6. 既有书签、跨章翻页、仿真翻页、信息栏及 TTS 高亮回归通过；这不等于已完成完整有声朗读人工验收。

执行示例（系统文件选择器另做真实流程检查）：

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-configuration-cache
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r \
  -e class top.ntutn.sonovelreader.ui.FontFeatureTest,top.ntutn.sonovelreader.data.fonts.ReaderFontManagerTest,top.ntutn.sonovelreader.data.fonts.FontRepositoryTest \
  top.ntutn.sonovelreader.test/androidx.test.runner.AndroidJUnitRunner
./gradlew :app:assembleRelease -PkeyringSigning=false --no-configuration-cache
python3 scripts/verify_credits.py app/build/reports/credits/release-runtime.json
python3 scripts/verify_font_resources.py
python3 -B -m unittest discover -s scripts -p 'test_*.py' -v
python3 scripts/check_file_lines.py
```

直接调用 Android instrumentation 时，即使测试失败，adb 也可能返回退出 0；必须检查输出中的失败堆栈与最终 `OK`，不能只以命令退出码判定通过。

## 4. 真机手动检查

通过实际 MainActivity 导航进入阅读设置和字体页，使用 Android DocumentsUI 从 Download 选择测试 TTF，成功返回字体列表，并保持系统默认，未自动切换。

随后手动应用该导入字体，确认预览发生字体变化；删除本次生成的外部测试原文件，再强制停止和重新启动应用，字体选择仍存在且正常解析。最后通过字体「更多 → 删除」确认回退系统默认，移除本次私有测试字体，未遗留测试选择。

关于入口直接位于阅读设置，而非字体页子页面；实际页面显示应用版本、字体作者、LICENSE 和来源链接，已有真机截图。自动测试另外验证离线授权全文展开。

测试夹具只使用本次生成的文件；清理外部夹具前核对完整解析路径，未删除用户原有外部文件。测试期间安装过基线 APK 用于配对测量，最后已覆盖安装并启动当前 Debug 版本。

## 5. 包体与安装测量

基线为开始实现时 HEAD `8344bd1150dce1f657838fc32be5b679068f46da` 的独立源码导出，在同一机器、SDK、Gradle 与依赖缓存下分别构建；未使用先前遗留 APK 代替基线。

| 指标 | 基线 | 当前 | 增量 |
| --- | ---: | ---: | ---: |
| Release 未签名 APK | 55,440,774 B | 83,557,032 B | 28,116,258 B（约 26.81 MiB） |
| Debug 设备实际 base.apk | 74,301,227 B | 106,201,294 B | 31,900,067 B |
| Debug 安装代码目录 `du -sk` | 72,631 KiB | 103,819 KiB | 31,188 KiB（约 30.46 MiB） |

三款原始字体合计 47,674,716 B（约 45.47 MiB）。安装目录统计包括该设备本次安装的 APK/代码目录，不包含系统共享 ART 缓存、用户书籍和其他应用数据，因此不是系统设置页“总占用”的等价值。临时导入字体已删除，no-backup 目录当时为 11 KiB。

增量包含字体功能与统一关于页全部资源/代码，不将原始字体字节数误称为 APK 增量。最终 Release APK SHA-256：`86f21657490eed0f5a76bc476e0ecdc8eae5dd10b659b9dc4a0c12e6ca1b7a06`。

## 6. 性能观察

独立 instrumentation 进程中的单次设备观察（仓库冷加载不等于设备/系统字体缓存全冷）：

| 操作 | 耗时 |
| --- | ---: |
| 朱雀仿宋首次仓库解析 | 52.85 ms |
| 寒蝉端黑宋首次仓库解析 | 62.74 ms |
| 霞鹜文楷首次仓库解析 | 111.77 ms |
| 缓存解析（每款重复 20 次均值） | 0.18–0.21 ms |
| 导入 25,928,941 B 静态 TTF，含复制/校验/目录提交 | 59.72 ms |

进程 PSS 快照由 121,581 KiB 到 171,655 KiB；这不是峰值内存，也不是纯字体增量隔离实验。文件 IO 与字体构建在 IO dispatcher，设备验证期间未发生 ANR 或崩溃；未据此声称所有低内存设备均达到相同性能。

## 7. 验证边界与发布提醒

- 本次实际设备为 API 36；API 29、其他厂商设备、完整横屏/小窗口/超大字体/TalkBack 组合没有全部逐项人工验收。
- 50 MiB 边界、损坏与不支持格式通过自动测试；真实设备磁盘耗尽、每个事务指令点的进程强杀及所有文档提供器异常未穷举。
- 测试覆盖朗读高亮及书签联动，没有对多种真实 TTS 引擎执行长时间持续播报、切换字体和后台播放人工组合测试。
- 关于页资源校验确认字段/清单/全文存在，不等同于法律合规审计。epub4j 中识别的 JazzLib（GPL + Classpath exception）等保留独立声明和对应版本源码获取链接；正式发布的源码提供等义务仍需按[授权维护说明](credits/README.md)核对。
- 本次没有生成或发布正式签名升级包；设备保留调试版供复查。
