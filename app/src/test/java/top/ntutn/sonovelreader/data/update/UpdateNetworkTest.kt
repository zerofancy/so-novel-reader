package top.ntutn.sonovelreader.data.update

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import top.ntutn.updater.*
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class UpdateNetworkTest {
    private fun withServer(test: (HttpServer, String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool()
        server.executor = executor
        server.start()
        try { test(server, "http://127.0.0.1:${server.address.port}") }
        finally { server.stop(0); executor.shutdownNow() }
    }

    @Test fun realEngineReadsJsonWithoutRedirectsOrRetries() = withServer { server, base ->
        val calls = AtomicInteger()
        val status = AtomicInteger(200)
        server.createContext("/api/v1/updates") { exchange ->
            calls.incrementAndGet()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("Location", "$base/unexpected")
            exchange.responseHeaders.add("Retry-After", "0")
            val bytes = updateBody().toByteArray()
            exchange.sendResponseHeaders(status.get(), bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        createUpdateHttpClient().use { client ->
            runBlocking {
                val checker = UpdateChecker(client, UpdateVersion(1, "1.0"), base, allowInsecureHttp = true)
                assertTrue(checker.check("ARM64") is UpdateCheckResult.Available)
                for (value in listOf(302, 503, 408)) {
                    status.set(value)
                    val result = checker.check("ARM64") as UpdateCheckResult.Failed
                    assertEquals(value, result.error.httpStatus)
                }
                assertEquals("Each check must make one request", 4, calls.get())
            }
        }
    }

    @Test fun rejectsChunkedOversizeAndCancelsStalledBody() = withServer { server, base ->
        val slow = AtomicInteger()
        server.createContext("/api/v1/updates") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, 0)
            runCatching {
                exchange.responseBody.use {
                    if (slow.get() == 0) it.write(ByteArray(256 * 1024 + 4096) { 32 })
                    else {
                        it.write('{'.code); it.flush()
                        Thread.sleep(2000)
                        it.write('}'.code)
                    }
                }
            }
        }
        createUpdateHttpClient().use { client ->
            runBlocking {
                fun checker(timeout: Long = 25_000) = UpdateChecker(
                    client, UpdateVersion(1, "1.0"), base, timeout, allowInsecureHttp = true,
                )
                assertEquals(UpdateErrorKind.InvalidResponse,
                    (checker().check("ARM64") as UpdateCheckResult.Failed).error.kind)
                slow.set(1)
                assertEquals(UpdateErrorKind.Timeout,
                    (checker(150).check("ARM64") as UpdateCheckResult.Failed).error.kind)
                val pending = async { checker().check("ARM64") }
                delay(100)
                pending.cancelAndJoin()
                assertTrue(pending.isCancelled)
            }
        }
    }
}
