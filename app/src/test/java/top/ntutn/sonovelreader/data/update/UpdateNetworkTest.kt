package top.ntutn.sonovelreader.data.update

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
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
        server.createContext("/ok") { exchange ->
            val bytes = updateBody().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "$base/ok")
            exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        server.createContext("/busy") { exchange ->
            calls.incrementAndGet()
            exchange.responseHeaders.add("Retry-After", "0")
            exchange.sendResponseHeaders(503, -1); exchange.close()
        }
        server.createContext("/timeout") { exchange ->
            calls.incrementAndGet()
            exchange.responseHeaders.add("Retry-After", "0")
            exchange.sendResponseHeaders(408, -1); exchange.close()
        }
        createUpdateHttpClient().use { client ->
            runBlocking {
                assertEquals(2L, UpdateChecker(client, "$base/ok").check("ARM64").versionCode)
                for ((path, status) in listOf("redirect" to 302, "busy" to 503, "timeout" to 408)) {
                    try { UpdateChecker(client, "$base/$path").check("ARM64"); fail("Expected $status") }
                    catch (e: UpdateServiceError) { assertEquals(status, e.status) }
                }
                assertEquals("503 and 408 must each make one request", 2, calls.get())
            }
        }
    }

    @Test fun rejectsChunkedOversizeAndCancelsStalledBody() = withServer { server, base ->
        server.createContext("/large") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            runCatching { exchange.responseBody.use { it.write(ByteArray(MAX_UPDATE_BYTES + 4096) { 32 }) } }
        }
        server.createContext("/slow") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            runCatching {
                exchange.responseBody.use {
                    it.write('{'.code); it.flush()
                    Thread.sleep(2000)
                    it.write('}'.code)
                }
            }
        }
        createUpdateHttpClient().use { client ->
            runBlocking {
                try { UpdateChecker(client, "$base/large").check("ARM64"); fail("Expected size limit") }
                catch (_: InvalidUpdateResponse) { /* expected */ }
                try { UpdateChecker(client, "$base/slow", 150).check("ARM64"); fail("Expected timeout") }
                catch (_: TimeoutCancellationException) { /* whole response, not just headers */ }
                val pending = async { UpdateChecker(client, "$base/slow").check("ARM64") }
                delay(100)
                pending.cancelAndJoin()
                assertTrue(pending.isCancelled)
            }
        }
    }
}
