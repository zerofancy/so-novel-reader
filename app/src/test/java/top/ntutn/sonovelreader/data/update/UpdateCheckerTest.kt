package top.ntutn.sonovelreader.data.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import top.ntutn.updater.*
import kotlin.time.Clock

internal fun updateBody(
    version: JsonElement = JsonPrimitive("2"),
    arch: String = "ARM64",
    url: String = "https://example.com/reader.apk",
    size: JsonElement? = JsonNull,
    name: String = "1.1",
    extra: List<JsonElement> = emptyList(),
): String = buildJsonObject {
    putJsonObject("query") {
        put("software", UPDATE_SOFTWARE); put("os", "Android"); put("arch", arch); put("packageType", "apk")
    }
    putJsonArray("items") {
        addJsonObject {
            put("software", UPDATE_SOFTWARE); put("os", "Android"); put("arch", arch); put("packageType", "apk")
            putJsonObject("latest") {
                put("packageType", "apk"); put("versionCode", version); put("versionName", name)
                put("distributionTitle", "正式发布"); put("downloadUrl", url)
                if (size != null) put("packageSize", size)
                put("futureField", true)
                put("descriptionPlainText", "本次更新修复阅读问题")
                put("descriptionMarkdown", "**Markdown**")
            }
        }
        extra.forEach(::add)
    }
}.toString()

class UpdateCheckerTest {
    private suspend fun check(body: String, version: Long = 1): UpdateCheckResult =
        HttpClient(MockEngine { respond(body, headers = headersOf("Content-Type", "application/json")) }).use {
            UpdateChecker(it, UpdateVersion(version, "1.0")).check("ARM64")
        }

    @Test fun buildsExactQueryAndUsesSdkReleaseDescriptions() = runBlocking {
        HttpClient(MockEngine { request ->
            assertEquals(UPDATE_SOFTWARE, request.url.parameters["software"])
            assertEquals("Android", request.url.parameters["os"])
            assertEquals("X86_64", request.url.parameters["arch"])
            assertEquals("apk", request.url.parameters["packageType"])
            assertEquals(4, request.url.parameters.names().size)
            respond(updateBody(arch = "X86_64"), headers = headersOf("Content-Type", "application/json"))
        }).use {
            val result = UpdateChecker(it, UpdateVersion(1, "1.0")).check("X86_64") as UpdateCheckResult.Available
            assertEquals(2L, result.release.version.code)
            assertEquals("正式发布", result.release.distributionTitle)
            assertEquals("本次更新修复阅读问题", result.release.description)
        }
    }

    @Test fun sdkComparesVersionsAndRejectsAmbiguousOrUnsafeResponses() = runBlocking {
        assertTrue(check(updateBody()) is UpdateCheckResult.Available)
        assertEquals(UpdateCheckResult.UpToDate(UpdateVersion(2, "1.1")), check(updateBody(), 2))
        assertEquals(UpdateCheckResult.UpToDate(UpdateVersion(2, "1.1")), check(updateBody(), 3))
        for (body in listOf(updateBody(JsonPrimitive(2)), updateBody(extra = listOf(JsonNull)), "{}")) {
            assertEquals(UpdateErrorKind.InvalidResponse, (check(body) as UpdateCheckResult.Failed).error.kind)
        }
        for (url in listOf("http://example.com/app.apk", "intent://install")) {
            assertEquals(UpdateErrorKind.UnsafeDownloadUrl,
                (check(updateBody(url = url)) as UpdateCheckResult.Failed).error.kind)
        }
    }

    @Test fun serviceResultsRetainCodesAndCooldown() = runBlocking {
        for ((status, code) in listOf(404 to "SOFTWARE_NOT_FOUND", 404 to "NO_MATCHING_PACKAGE", 409 to "AMBIGUOUS_LATEST", 429 to "RATE_LIMITED")) {
            HttpClient(MockEngine { respond("""{"error":{"code":"$code"}}""", HttpStatusCode.fromValue(status),
                headersOf("Content-Type" to listOf("application/json"), "Retry-After" to listOf("3"))) }).use {
                val before = Clock.System.now()
                val result = UpdateChecker(it, UpdateVersion(1, "1.0")).check("ARM64")
                if (code == "NO_MATCHING_PACKAGE") assertEquals(UpdateCheckResult.NoMatchingPackage, result)
                else {
                    val error = (result as UpdateCheckResult.Failed).error
                    assertEquals(status, error.httpStatus); assertEquals(code, error.serviceCode)
                    if (status == 429) assertTrue(error.retryAt!! > before)
                }
            }
        }
    }

    @Test fun mapsInstalledAbiWithoutGuessingFromHardware() {
        assertEquals("ARM64", installedArchitecture("/data/app/pkg/lib/arm64", true))
        assertEquals("X86_64", installedArchitecture("/data/app/pkg/lib/x86_64", true))
        assertNull(installedArchitecture("/data/app/pkg/lib/arm64", false))
        assertNull(installedArchitecture("/data/app/pkg/lib/unknown", true))
    }
}
