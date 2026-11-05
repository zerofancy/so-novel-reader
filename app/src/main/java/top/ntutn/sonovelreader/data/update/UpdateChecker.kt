package top.ntutn.sonovelreader.data.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.client.plugins.HttpTimeout
import top.ntutn.sonovelreader.BuildConfig
import top.ntutn.updater.*
import kotlin.time.Duration.Companion.milliseconds

const val UPDATE_BASE_URL = "https://zp.ntutn.top"
const val UPDATE_SOFTWARE = BuildConfig.UPDATE_SOFTWARE

/** Shared by the application; callers cancel individual calls, not the client. */
fun createUpdateHttpClient() = HttpClient(OkHttp) {
    expectSuccess = false
    followRedirects = false
    defaultRequest { header(HttpHeaders.CacheControl, "no-cache, no-store") }
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

/** Configures the SDK for this installation; the application owns the shared HTTP client. */
class UpdateChecker(
    private val client: HttpClient,
    private val currentVersion: UpdateVersion,
    private val baseUrl: String = UPDATE_BASE_URL,
    private val totalTimeoutMillis: Long = 25_000,
    private val allowInsecureHttp: Boolean = false,
) {
    suspend fun check(arch: String): UpdateCheckResult {
        val checker = createUpdateChecker(
            UpdaterConfig(
                baseUrl = baseUrl,
                software = UPDATE_SOFTWARE,
                currentVersion = currentVersion,
                target = UpdateTarget(UpdateOs.Android, UpdateArch.valueOf(arch)),
                packageType = "apk",
                requestTimeout = totalTimeoutMillis.milliseconds,
                allowInsecureHttp = allowInsecureHttp,
            ),
            client,
        )
        return try { checker.check() } finally { checker.close() }
    }
}
