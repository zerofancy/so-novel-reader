package top.ntutn.sonovelreader.data.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

internal fun updateBody(
    version: JsonElement = JsonPrimitive("2"),
    arch: String = "ARM64",
    url: String = "https://example.com/reader.apk",
    size: JsonElement? = JsonNull,
    name: String = "1.1",
    extra: List<JsonElement> = emptyList(),
): String = buildJsonObject {
    putJsonArray("items") {
        addJsonObject {
            put("software", UPDATE_SOFTWARE); put("os", "Android"); put("arch", arch); put("packageType", "apk")
            putJsonObject("latest") {
                put("packageType", "apk"); put("versionCode", version); put("versionName", name)
                put("distributionTitle", "正式发布"); put("downloadUrl", url)
                if (size != null) put("packageSize", size)
                put("futureField", true)
            }
        }
        extra.forEach(::add)
    }
}.toString()

class UpdateCheckerTest {
    @Test fun selectsOnlyFirstItemRegardlessOfLaterSchemaOrVersion() {
        val later = Json.parseToJsonElement(updateBody(JsonPrimitive("100"))).jsonObject["items"]!!.jsonArray.first()
        val result = decodeUpdate(updateBody(extra = listOf(JsonNull, JsonPrimitive("unrelated"), later)), "ARM64")
        assertEquals(2L, result.versionCode)
        assertEquals("正式发布", result.distributionTitle)
    }

    @Test fun invalidFirstDoesNotFallBackToValidSecond() {
        val later = Json.parseToJsonElement(updateBody()).jsonObject["items"]!!.jsonArray.first()
        assertThrows(InvalidUpdateResponse::class.java) {
            decodeUpdate("""{"items":[null,$later]}""", "ARM64")
        }
    }

    @Test fun comparesLongValuesNumericallyAndAcceptsLongMaximum() {
        assertTrue(decodeUpdate(updateBody(JsonPrimitive("10")), "ARM64").isNewerThan(9))
        assertFalse(decodeUpdate(updateBody(JsonPrimitive("9")), "ARM64").isNewerThan(10))
        assertFalse(decodeUpdate(updateBody(), "ARM64").isNewerThan(2))
        assertEquals(Long.MAX_VALUE, decodeUpdate(updateBody(JsonPrimitive(Long.MAX_VALUE.toString())), "ARM64").versionCode)
    }

    @Test fun rejectsInvalidVersionsAndUrlsAndDimensions() {
        val invalid = listOf(
            updateBody(JsonPrimitive(2)), updateBody(JsonPrimitive("0")), updateBody(JsonPrimitive("-1")),
            updateBody(JsonPrimitive("2.5")), updateBody(JsonPrimitive("9223372036854775808")),
            updateBody(arch = "X86_64"), updateBody(name = "  "),
            updateBody(url = "intent://install"), updateBody(url = "/reader.apk"),
            updateBody(url = "https://user:pass@example.com/reader.apk"),
            "{}", """{"items":[]}""", "<html>failure</html>",
        )
        invalid.forEach { body -> assertThrows(body, InvalidUpdateResponse::class.java) { decodeUpdate(body, "ARM64") } }
    }

    @Test fun sizeMayBeMissingNullZeroOrLongButNotNegativeOrNumeric() {
        assertNull(decodeUpdate(updateBody(size = null), "ARM64").packageSize)
        assertNull(decodeUpdate(updateBody(), "ARM64").packageSize)
        assertEquals(0L, decodeUpdate(updateBody(size = JsonPrimitive("0")), "ARM64").packageSize)
        listOf(JsonPrimitive("-1"), JsonPrimitive(1), JsonPrimitive("9223372036854775808")).forEach {
            assertThrows(InvalidUpdateResponse::class.java) { decodeUpdate(updateBody(size = it), "ARM64") }
        }
    }

    @Test fun buildsExactQueryAndAcceptsUnknownFields() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("so-novel-reader", request.url.parameters["software"])
            assertEquals("Android", request.url.parameters["os"])
            assertEquals("X86_64", request.url.parameters["arch"])
            assertEquals("apk", request.url.parameters["packageType"])
            assertEquals(4, request.url.parameters.names().size)
            assertEquals("application/json", request.headers["Accept"])
            respond(updateBody(arch = "X86_64"))
        }
        HttpClient(engine).use { assertEquals(2L, UpdateChecker(it).check("X86_64").versionCode) }
    }

    @Test fun mapsServiceErrorsWithoutTreatingThemAsCurrent() = runBlocking {
        for ((status, code) in listOf(404 to "SOFTWARE_NOT_FOUND", 404 to "NO_MATCHING_PACKAGE", 409 to "AMBIGUOUS_LATEST", 503 to "SERVICE_UNAVAILABLE")) {
            HttpClient(MockEngine { respond("""{"error":{"code":"$code"}}""", HttpStatusCode.fromValue(status)) }).use {
                try {
                    UpdateChecker(it).check("ARM64")
                    fail("Expected service error")
                } catch (e: UpdateServiceError) {
                    assertEquals(status, e.status); assertEquals(code, e.code)
                }
            }
        }
    }

    @Test fun honorsRetryAfterAndRejectsOversizedResponse() = runBlocking {
        HttpClient(MockEngine { respond("{}", HttpStatusCode.TooManyRequests, headersOf("Retry-After", "3")) }).use {
            try { UpdateChecker(it).check("ARM64"); fail("Expected 429") }
            catch (e: UpdateServiceError) { assertEquals(3000L, e.retryAfterMillis) }
        }
        HttpClient(MockEngine { respond(" ".repeat(MAX_UPDATE_BYTES + 1)) }).use {
            try { UpdateChecker(it).check("ARM64"); fail("Expected size rejection") }
            catch (_: InvalidUpdateResponse) { /* expected */ }
        }
    }

    @Test fun retryAfterSupportsDatesAndRejectsOverflow() {
        assertEquals(5000L, retryAfterMillis("Thu, 1 Jan 1970 00:00:05 GMT", 0))
        assertNull(retryAfterMillis("-1")); assertNull(retryAfterMillis(Long.MAX_VALUE.toString()))
        assertNull(retryAfterMillis("invalid"))
    }

    @Test fun mapsInstalledAbiWithoutGuessingFromHardware() {
        assertEquals("ARM64", installedArchitecture("/data/app/pkg/lib/arm64", true))
        assertEquals("X86_64", installedArchitecture("/data/app/pkg/lib/x86_64", true))
        assertNull(installedArchitecture("/data/app/pkg/lib/arm64", false))
        assertNull(installedArchitecture("/data/app/pkg/lib/arm", false))
        assertNull(installedArchitecture("/data/app/pkg/lib/unknown", true))
    }
}
