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
