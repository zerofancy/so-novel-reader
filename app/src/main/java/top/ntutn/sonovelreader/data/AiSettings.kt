package top.ntutn.sonovelreader.data

/** AI 操作（SAEP）相关设置。 */
data class AiSettings(
    /** 是否允许 GUI Agent 操作本应用。默认开启。 */
    val allowAiOperation: Boolean = true,
    /** 是否允许 Agent 删除书架中的书籍等内容。默认关闭。 */
    val allowAiDeleteContent: Boolean = false,
    /** 动态策略版本号，单调递增。 */
    val policyVersion: Int = 1,
)
