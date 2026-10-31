package top.ntutn.sonovelreader.data.update

import top.ntutn.sonovelreader.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds

const val UPDATE_ENDPOINT = "https://zp.ntutn.top/api/v1/updates"
const val UPDATE_SOFTWARE = BuildConfig.UPDATE_SOFTWARE
internal const val MAX_UPDATE_BYTES = 65_536

/** Shared by the application; callers cancel individual calls, not the client. */
fun createUpdateHttpClient() = HttpClient(OkHttp) {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 10_000
        requestTimeoutMillis = 25_000
    }
    engine {
        config {
            followRedirects(false)
            followSslRedirects(false)
            retryOnConnectionFailure(false)
            cache(null)
            addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                // OkHttp retries 503 + Retry-After: 0 even with connection retries disabled.
                // We handle all 503s in the UI, so suppress this engine-only retry hint.
                if (response.code == 503) response.newBuilder().removeHeader("Retry-After").build()
                else response
            }
        }
    }
}

data class AvailableUpdate(
    val versionCode: Long,
    val versionName: String,
    val distributionTitle: String,
    val downloadUrl: String,
    val packageSize: Long?,
) {
    fun isNewerThan(installed: Long) = versionCode > installed
}

class InvalidUpdateResponse(cause: Throwable? = null) : Exception("Invalid update response", cause)
class UpdateServiceError(val status: Int, val code: String?, val retryAfterMillis: Long? = null) :
    Exception("Update service HTTP $status")

class UpdateChecker(
    private val client: HttpClient,
    private val endpoint: String = UPDATE_ENDPOINT,
    private val totalTimeoutMillis: Long = 25_000,
) {
    suspend fun check(arch: String): AvailableUpdate = withTimeout(totalTimeoutMillis.milliseconds) {
        require(arch in setOf("ARM64", "X86_64"))
        // execute's streaming scope releases the response even on cancellation or a size violation.
        client.prepareGet(endpoint) {
            parameter("software", UPDATE_SOFTWARE)
            parameter("os", "Android")
            parameter("arch", arch)
            parameter("packageType", "apk")
            accept(ContentType.Application.Json)
            header(HttpHeaders.CacheControl, "no-cache, no-store")
        }.execute { response ->
            val channel = response.bodyAsChannel()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = channel.readAvailable(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_UPDATE_BYTES) throw InvalidUpdateResponse()
                output.write(buffer, 0, count)
            }
            val status = response.status.value
            if (status != 200) {
                val code = runCatching {
                    Json.parseToJsonElement(output.toString("UTF-8")).jsonObject["error"]
                        ?.jsonObject?.get("code")?.jsonPrimitive?.content
                }.getOrNull()
                throw UpdateServiceError(status, code,
                    if (status == 429) retryAfterMillis(response.headers[HttpHeaders.RetryAfter]) else null)
            }
            decodeUpdate(output.toString("UTF-8"), arch)
        }
    }
}

internal fun decodeUpdate(body: String, arch: String): AvailableUpdate {
    try {
        // Only the first item participates. Later items need not have our business schema.
        val item = Json.parseToJsonElement(body).jsonObject.getValue("items").jsonArray.first().jsonObject
        fun JsonObject.string(key: String): String {
            val value = getValue(key).jsonPrimitive
            require(value.isString)
            return value.content
        }
        fun positiveLong(text: String): Long {
            require(text.matches(Regex("[0-9]+")))
            return text.toLong().also { require(it > 0) }
        }
        require(item.string("software") == UPDATE_SOFTWARE && item.string("os") == "Android")
        require(item.string("arch") == arch && item.string("packageType") == "apk")
        val latest = item.getValue("latest").jsonObject
        require(latest.string("packageType") == "apk")
        val version = positiveLong(latest.string("versionCode"))
        val name = latest.string("versionName").also { require(it.isNotBlank()) }
        val url = latest.string("downloadUrl")
        val uri = URI(url)
        require(uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank())
        require(uri.userInfo == null)
        val size = when (latest["packageSize"]) {
            null, JsonNull -> null
            else -> latest.string("packageSize").let {
                require(it.matches(Regex("[0-9]+")))
                it.toLong().also { bytes -> require(bytes >= 0) }
            }
        }
        return AvailableUpdate(version, name, latest.string("distributionTitle"), url, size)
    } catch (e: Exception) {
        throw InvalidUpdateResponse(e)
    }
}

internal fun retryAfterMillis(value: String?, nowMillis: Long = System.currentTimeMillis()): Long? {
    if (value == null) return null
    value.toLongOrNull()?.let { seconds ->
        return seconds.takeIf { it >= 0 && it <= Long.MAX_VALUE / 1000 }?.times(1000)
    }
    return runCatching {
        (ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - nowMillis)
            .coerceAtLeast(0)
    }.getOrNull()
}
