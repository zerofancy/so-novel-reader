package top.ntutn.sonovelreader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import top.ntutn.sonovelreader.AppContainer

class AppViewModelFactory(
    private val container: AppContainer,
    private val bookId: String? = null,
    private val groupId: String? = null,
    private val groupName: String? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(LibraryViewModel::class.java) ->
            LibraryViewModel(container) as T

        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(container) as T

        modelClass.isAssignableFrom(AiOperationSettingsViewModel::class.java) ->
            AiOperationSettingsViewModel(container) as T

        modelClass.isAssignableFrom(GroupShelfViewModel::class.java) -> {
            val id = groupId ?: throw IllegalArgumentException("缺少 groupId 以创建 GroupShelfViewModel")
            val name = groupName ?: throw IllegalArgumentException("缺少 groupName 以创建 GroupShelfViewModel")
            GroupShelfViewModel(id, name, container) as T
        }

        modelClass.isAssignableFrom(ReaderViewModel::class.java) && bookId != null ->
            ReaderViewModel(bookId, container) as T

        else -> throw IllegalArgumentException("未知 ViewModel：${modelClass.name}")
    }
}
