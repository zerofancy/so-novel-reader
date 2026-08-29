package top.ntutn.sonovelreader.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import top.ntutn.sonovelreader.AppContainer
import top.ntutn.sonovelreader.data.ShelfGroup

@Serializable
data object BookshelfRoute

@Serializable
data class GroupShelfRoute(
    val groupId: String,
    val groupName: String,
)

@Serializable
data object SettingsRoute

@Serializable
data class ReaderRoute(val bookId: String)

@Composable
fun SoNovelReaderApp(
    container: AppContainer,
    sharedUris: StateFlow<List<Uri>>,
    consumeSharedUris: () -> Unit,
    requestedTtsBookId: StateFlow<String?>,
    consumeRequestedTtsBook: () -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    val libraryViewModel: LibraryViewModel = viewModel(factory = AppViewModelFactory(container))
    val settingsViewModel: SettingsViewModel = viewModel(factory = AppViewModelFactory(container))
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val ttsVoices by settingsViewModel.ttsVoices.collectAsStateWithLifecycle()
    val incomingUris by sharedUris.collectAsStateWithLifecycle()
    val ttsBookId by requestedTtsBookId.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        libraryViewModel.importBooks(it)
    }

    LaunchedEffect(incomingUris) {
        if (incomingUris.isNotEmpty()) {
            libraryViewModel.importBooks(incomingUris)
            consumeSharedUris()
        }
    }

    LaunchedEffect(ttsBookId) {
        ttsBookId?.let { bookId ->
            navController.navigate(ReaderRoute(bookId)) { launchSingleTop = true }
            consumeRequestedTtsBook()
        }
    }

    LaunchedEffect(libraryViewModel) {
        libraryViewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.Message -> snackbarHostState.showSnackbar(event.text)
                LibraryEvent.PopBackStack -> navController.popBackStack()
                is LibraryEvent.ImportFinished -> {
                    val result = event.result
                    val message = buildString {
                        append("导入 ${result.importedCount} 本")
                        if (result.existingCount > 0) append("，已存在 ${result.existingCount} 本")
                        if (result.failedCount > 0) append("，失败 ${result.failedCount} 本")
                        result.items.filterIsInstance<top.ntutn.sonovelreader.data.ImportItemResult.Failed>()
                            .firstOrNull()?.let { append("：${it.message}") }
                    }
                    if (result.items.size == 1 && result.successfulBookIds.size == 1) {
                        navController.navigate(ReaderRoute(result.successfulBookIds.first()))
                    } else {
                        navController.navigate(BookshelfRoute) { launchSingleTop = true }
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showNavigation =
        destination?.hasRoute<ReaderRoute>() != true && destination?.hasRoute<GroupShelfRoute>() != true

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showNavigation) {
                NavigationBar {
                    NavigationBarItem(
                        selected = destination?.hasRoute<BookshelfRoute>() == true,
                        onClick = {
                            navController.navigate(BookshelfRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.AutoStories, contentDescription = null) },
                        label = { Text("书架") },
                    )
                    NavigationBarItem(
                        selected = destination?.hasRoute<SettingsRoute>() == true,
                        onClick = {
                            navController.navigate(SettingsRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("设置") },
                    )
                }
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = BookshelfRoute,
            modifier = Modifier,
        ) {
            // ========= 书架顶层 =========
            composable<BookshelfRoute> {
                BookshelfScreen(
                    state = libraryState,
                    onImport = {
                        picker.launch(
                            arrayOf(
                                "application/epub+zip",
                                "application/zip",
                                "application/octet-stream",
                            )
                        )
                    },
                    onOpenBook = { navController.navigate(ReaderRoute(it)) },
                    onDeleteBook = libraryViewModel::requestDelete,
                    onConfirmDelete = libraryViewModel::confirmDelete,
                    onCancelDelete = libraryViewModel::cancelDelete,
                    onOpenGroup = { group: ShelfGroup ->
                        navController.navigate(GroupShelfRoute(group.id, group.name))
                    },
                    onCreateGroup = libraryViewModel::showCreateGroupDialog,
                    onRenameGroup = libraryViewModel::showRenameGroupDialog,
                    onRequestDeleteGroup = libraryViewModel::requestDeleteGroup,
                    onCancelDeleteGroup = libraryViewModel::cancelDeleteGroup,
                    onConfirmDeleteGroup = libraryViewModel::confirmDeleteGroup,
                    onDismissEditGroup = libraryViewModel::dismissEditGroupDialog,
                    onConfirmEditGroup = libraryViewModel::confirmEditGroup,
                    onRequestMoveBook = { bookId, bookTitle, currentGroupId ->
                        libraryViewModel.requestMoveBook(bookId, bookTitle, currentGroupId)
                    },
                    onCancelMoveBook = libraryViewModel::cancelMoveBook,
                    onConfirmMoveBook = libraryViewModel::confirmMoveBook,
                    onRequestMoveToNewGroup = libraryViewModel::requestMoveToNewGroup,
                    modifier = Modifier.padding(contentPadding),
                )
            }

            // ========= 分组详情 =========
            composable<GroupShelfRoute> { entry ->
                val route = entry.toRoute<GroupShelfRoute>()
                val groupViewModel: GroupShelfViewModel = viewModel(
                    key = "group-${route.groupId}",
                    factory = AppViewModelFactory(
                        container,
                        groupId = route.groupId,
                        groupName = route.groupName,
                    ),
                )
                val groupState by groupViewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(groupViewModel) {
                    groupViewModel.events.collect { event ->
                        when (event) {
                            is LibraryEvent.Message -> snackbarHostState.showSnackbar(event.text)
                            LibraryEvent.PopBackStack -> navController.popBackStack()
                            is LibraryEvent.ImportFinished -> { /* noop */ }
                        }
                    }
                }

                GroupShelfScreen(
                    state = groupState,
                    groups = libraryState.groups,
                    onBack = navController::popBackStack,
                    onOpenBook = { navController.navigate(ReaderRoute(it)) },
                    onDeleteBook = groupViewModel::requestDelete,
                    onConfirmDelete = groupViewModel::confirmDelete,
                    onCancelDelete = groupViewModel::cancelDelete,
                    onRenameGroup = {
                        // 复用顶层 LibraryViewModel 的「重命名分组」对话框状态机，
                        // 通过它来编辑，因为 LibraryViewModel 管着 groups 列表。
                        libraryViewModel.showRenameGroupDialog(route.groupId)
                    },
                    onRequestDeleteGroup = {
                        libraryViewModel.requestDeleteGroup(route.groupId)
                    },
                    onCancelDeleteGroup = libraryViewModel::cancelDeleteGroup,
                    onConfirmDeleteGroup = libraryViewModel::confirmDeleteGroup,
                    onDismissEditGroup = libraryViewModel::dismissEditGroupDialog,
                    onConfirmEditGroup = libraryViewModel::confirmEditGroup,
                    onRequestMoveBook = { bookId, bookTitle ->
                        groupViewModel.requestMoveBook(bookId, bookTitle)
                    },
                    onCancelMoveBook = groupViewModel::cancelMoveBook,
                    onConfirmMoveBook = groupViewModel::confirmMoveBook,
                    onRequestMoveToNewGroup = { bookId ->
                        groupViewModel.cancelMoveBook()
                        libraryViewModel.requestMoveToNewGroup(bookId)
                    },
                    showRenameDialog = libraryState.editingGroup?.editingId == route.groupId,
                    modifier = Modifier.padding(contentPadding),
                )

                // 如果顶层 ViewModel 的删除分组确认框正在作用于当前分组，也在这里渲染一次确认对话框，
                // 让用户在分组详情页点"删除分组"菜单时能看到确认。
                val deletingGid = libraryState.deletingGroupId
                if (deletingGid == route.groupId) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = libraryViewModel::cancelDeleteGroup,
                        title = { androidx.compose.material3.Text("删除分组？") },
                        text = {
                            val g = libraryState.groups.find { it.id == deletingGid }
                            androidx.compose.material3.Text(
                                if (g != null) {
                                    "删除「${g.name}」？\n组内的 ${g.bookCount} 本书会回到书架顶层，不会被删除。"
                                } else {
                                    "分组不存在或已被删除"
                                }
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = libraryViewModel::confirmDeleteGroup,
                            ) { androidx.compose.material3.Text("删除") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(
                                onClick = libraryViewModel::cancelDeleteGroup,
                            ) { androidx.compose.material3.Text("取消") }
                        },
                        icon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        },
                    )
                }
            }

            // ========= 设置 =========
            composable<SettingsRoute> {
                LaunchedEffect(Unit) { settingsViewModel.loadTtsVoices() }
                SettingsScreen(
                    settings = settings,
                    onModeChange = settingsViewModel::setMode,
                    onFontSizeChange = settingsViewModel::setFontSize,
                    onLineHeightChange = settingsViewModel::setLineHeight,
                    onFirstLineIndentChange = settingsViewModel::setFirstLineIndent,
                    onParagraphSpacingChange = settingsViewModel::setParagraphSpacing,
                    onThemeChange = settingsViewModel::setTheme,
                    onKeepScreenOnChange = settingsViewModel::setKeepScreenOn,
                    ttsVoices = ttsVoices,
                    onTtsRateChange = settingsViewModel::setTtsRate,
                    onTtsPitchChange = settingsViewModel::setTtsPitch,
                    onTtsVoiceChange = settingsViewModel::setTtsVoiceName,
                    modifier = Modifier.padding(contentPadding),
                )
            }

            // ========= 阅读器 =========
            composable<ReaderRoute> { entry ->
                val route = entry.toRoute<ReaderRoute>()
                val readerViewModel: ReaderViewModel = viewModel(
                    key = route.bookId,
                    factory = AppViewModelFactory(container, bookId = route.bookId),
                )
                ReaderScreen(
                    viewModel = readerViewModel,
                    onBack = navController::popBackStack,
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onMessage = { message ->
                        appScope.launch { snackbarHostState.showSnackbar(message) }
                    },
                )
            }
        }
    }
}
