# AGENTS.md

## 数据库迁移

数据库升级时优先使用 Room 的 `@AutoMigration`，而非手写 `Migration`。

**原因**：手写迁移容易在索引重建顺序、外键约束等细节上出错（例如 `CREATE INDEX IF NOT EXISTS` 因同名索引仍存在于旧表而静默跳过，导致新表缺索引 → Room schema 校验崩溃）。AutoMigration 由 KSP 处理器对比 schema JSON 自动生成迁移代码，能正确处理表重建顺序。

**用法**：
```kotlin
@Database(
    entities = [...],
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ],
)
```

仅在 AutoMigration 无法覆盖的复杂场景（如列重命名、跨表数据搬运）才回退到手写 `Migration`，并搭配 `AutoMigrationSpec`。

## 文件规模

- 项目文本文件默认不超过 500 行；空行、注释和末尾没有换行的最后一行均计入。按 UTF-8 文本检查，含 NUL 或不能按 UTF-8 解码的文件视为二进制并跳过。
- 检查 Git 已跟踪文件（即使匹配忽略规则）及未被忽略的新文件；跳过二进制、符号链接（含链接目录中的路径）、已删除文件和子模块目录。
- 优先按职责拆分超限文件，不通过压缩代码、删除必要说明或修改忽略规则规避限制。
- 确需豁免时，在 `.codex/file-line-exceptions.json` 按精确项目相对文件路径登记非空 `reason` 和大于 500 的整数 `max_lines`。禁止目录、通配符、绝对路径及 `..` 路径。每个文件独立限制，存量豁免不代表允许继续增长；当前豁免清单为空。
- 登记格式示例：`{"path/to/file.kt": {"reason": "无法拆分的具体原因", "max_lines": 600}}`。该示例不是已批准的豁免。
- 完成修改前从项目根目录运行 `python3 scripts/check_file_lines.py`；通过退出 0，超限或配置/执行错误退出 1。脚本按自身位置定位项目，也可从其他目录通过绝对路径运行。
- 回归测试：`python3 -B -m unittest discover -s scripts -p 'test_check_file_lines.py' -v`。
- 项目级 `.codex/hooks.json` 的 Stop hook 调用同一检查器。`--hook` 从标准输入读取 Stop 事件 JSON；通过输出 `{}`，超限或检查出错输出 `{"decision":"block","reason":"具体文件、实际行数和上限，或检查失败原因"}`，协议输出时退出 0。重复 Stop 仍检查，不因 `stop_hook_active` 而放行。
- **hook 已配置不等于已信任并生效。** 首次启用或修改 hook 后，应在可信项目中使用 Codex CLI `/hooks` 审阅并信任当前定义；未信任的 hook 会被跳过。已有会话可能需要重新加载配置或重开；应再确认真实 Stop 事件执行记录。禁止通过跳过信任检查启用。手动运行脚本/协议测试只能证明检查器行为，不能证明 Codex 已信任或加载 hook。
- Hook 协议和信任流程参考 [OpenAI 官方 Hooks 文档](https://learn.chatgpt.com/docs/hooks)。后续添加 hook 时合并现有配置，不覆盖其他 hook。
