package top.ntutn.sonovelreader.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import androidx.core.net.toUri

/**
 * SAEP（屏幕自动化操作声明协议）接入管理器。
 *
 * 依赖 ObricUI 2.2+ 系统提供的 android.security.obric.* 隐藏接口，当前材料中这些接口
 * 只能反射调用；在标准 AOSP / 不支持的设备上检测失败并静默降级，不影响应用自身功能。
 */
class SaepPolicyManager(private val context: Context) {

    /** 检测当前系统 SAEP 是否生效。 */
    @SuppressLint("PrivateApi")
    fun isSaepEnabled(): Boolean = try {
        val clazz = Class.forName(ROBOTS_HELPER_STUB)
        val instance = clazz.getDeclaredMethod("getInstance").apply { isAccessible = true }.invoke(null)
        clazz.getDeclaredMethod("isRobotsEnabled", Context::class.java)
            .apply { isAccessible = true }
            .invoke(instance, context) as Boolean
    } catch (e: Exception) {
        Timber.tag(TAG).w("isRobotsEnabled failed: ${e.message}")
        false
    }

    /** 按当前设置生成 AGRP-Policy/1.0 策略 JSON。 */
    fun buildPolicyJson(settings: AiSettings): String {
        val globalDisable = !settings.allowAiOperation
        val deleteDisable = !settings.allowAiDeleteContent
        val now = System.currentTimeMillis()
        return buildString {
            appendLine("{")
            appendLine("  \"schema\": \"AGRP-Policy/1.0\",")
            appendLine("  \"policy_version\": ${settings.policyVersion},")
            appendLine("  \"package\": \"$PACKAGE_NAME\",")
            appendLine("  \"updated_at\": \"$now\",")
            appendLine("  \"default_policy\": {")
            appendLine("    \"app\": {")
            appendLine("      \"global_disable\": $globalDisable,")
            appendLine("      \"screenshot_disable\": false,")
            appendLine("      \"input_disable\": false")
            appendLine("    }")
            appendLine("  },")
            appendLine("  \"scope\": {")
            appendLine("    \"app\": { \"global_disable\": $globalDisable },")
            appendLine("    \"activities\": {")
            appendLine("      \"$PACKAGE_NAME.MainActivity\": {")
            appendLine("        \"name\": \"主界面\",")
            appendLine("        \"page_scope\": { \"global_disable\": false, \"screenshot_disable\": false, \"input_disable\": false }")
            appendLine("      },")
            appendLine("      \"$PACKAGE_NAME.AiOperationSettingsActivity\": {")
            appendLine("        \"name\": \"AI操作设置\",")
            appendLine("        \"page_scope\": { \"global_disable\": true, \"screenshot_disable\": true, \"input_disable\": true }")
            appendLine("      }")
            appendLine("    },")
            appendLine("    \"agent_intents\": {")
            appendLine("      \"modify_content\": false,")
            appendLine("      \"post_content\": false,")
            appendLine("      \"delete_content\": $deleteDisable,")
            appendLine("      \"account_incentive\": false")
            appendLine("    }")
            appendLine("  }")
            appendLine("}")
        }
    }

    /**
     * 通过系统 Provider 动态推送策略。
     * @return true 表示更新成功。
     */
    suspend fun pushPolicy(settings: AiSettings): Boolean = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put("policy", buildPolicyJson(settings))
                put("version", settings.policyVersion)
            }
            val rows = context.contentResolver.update(POLICY_UPDATE_URI, values, null, null)
            rows > 0
        } catch (e: Exception) {
            Timber.tag(TAG).w("pushPolicy failed: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "SAEP"
        private const val PACKAGE_NAME = "top.ntutn.sonovelreader"
        private const val ROBOTS_HELPER_STUB = "android.security.obric.robots.RobotsHelperStub"
        private val POLICY_UPDATE_URI = "content://com.obric.agentrobots.provider/policy".toUri()
    }
}
