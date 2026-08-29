package top.ntutn.sonovelreader.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["contentHash"], unique = true),
        Index(value = ["categoryId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val authors: String,
    val sourceFileName: String,
    val epubPath: String,
    val contentDirectory: String,
    val coverPath: String?,
    val contentHash: String,
    val addedAt: Long,
    val categoryId: String? = null,
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReadingProgressEntity(
    @PrimaryKey val bookId: String,
    val chapterHref: String,
    val chapterIndex: Int,
    val chapterFraction: Float,
    val updatedAt: Long,
)

data class BookWithProgress(
    @Embedded val book: BookEntity,
    @Relation(parentColumn = "id", entityColumn = "bookId")
    val progress: ReadingProgressEntity?,
)
