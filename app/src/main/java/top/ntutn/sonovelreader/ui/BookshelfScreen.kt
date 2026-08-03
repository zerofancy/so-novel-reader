package top.ntutn.sonovelreader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import top.ntutn.sonovelreader.data.ShelfBook

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    state: LibraryUiState,
    onImport: () -> Unit,
    onOpenBook: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text("我的书架", fontWeight = FontWeight.SemiBold) })
        Box(Modifier.fillMaxSize()) {
            if (state.books.isEmpty() && !state.importing) {
                EmptyShelf(onImport)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.books, key = ShelfBook::id) { book ->
                        BookCard(book, onOpenBook, onDeleteBook)
                    }
                }
            }

            FloatingActionButton(
                onClick = onImport,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "导入 EPUB")
            }

            if (state.importing) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Card { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(14.dp))
                        Text("正在导入 EPUB…")
                    } }
                }
            }
        }
    }

    if (state.deletingBookId != null) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text("删除书籍？") },
            text = { Text("书籍文件和阅读进度都会从本机删除，此操作无法撤销。") },
            confirmButton = { TextButton(onClick = onConfirmDelete) { Text("删除") } },
            dismissButton = { TextButton(onClick = onCancelDelete) { Text("取消") } },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        )
    }
}

@Composable
private fun EmptyShelf(onImport: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.AutoStories,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text("书架还是空的", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("导入 EPUB，开始一段安静的阅读时光", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onImport) { Text("选择书籍") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
    book: ShelfBook,
    onOpenBook: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = { onOpenBook(book.id) },
            onLongClick = { onDeleteBook(book.id) },
        ),
    ) {
        Box {
            Card(
                modifier = Modifier.fillMaxWidth().height(166.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (book.coverPath != null) {
                    AsyncImage(
                        model = File(book.coverPath),
                        contentDescription = "${book.title}封面",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.AutoStories,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            IconButton(
                onClick = { onDeleteBook(book.id) },
                modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)),
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "删除${book.title}")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        Text(
            book.authors,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
