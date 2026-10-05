package top.ntutn.sonovelreader.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, CategoryEntity::class, ReadingProgressEntity::class, BookmarkEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ],
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun libraryDao(): LibraryDao
}
