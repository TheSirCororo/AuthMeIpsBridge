package ru.cororo.mcipsauth

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.Duration
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IpsClientTest {
    private data class Request(val method: String, val uri: String, val authorization: String?, val body: String)

    private lateinit var server: HttpServer
    private val requests = Collections.synchronizedList(mutableListOf<Request>())

    @Volatile
    private var status = 200

    @Volatile
    private var responseBody = "{}"

    @Volatile
    private var responseDelay = Duration.ZERO

    private val baseUrl get() = "http://127.0.0.1:${server.address.port}"

    @BeforeTest
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            try {
                requests += Request(
                    exchange.requestMethod,
                    exchange.requestURI.toString(),
                    exchange.requestHeaders.getFirst("Authorization"),
                    exchange.requestBody.readBytes().toString(Charsets.UTF_8),
                )
                Thread.sleep(responseDelay.toMillis())
                val bytes = responseBody.toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            } finally {
                exchange.close()
            }
        }
        server.start()
    }

    @AfterTest
    fun stopServer() {
        server.stop(0)
    }

    private fun client(url: String = baseUrl) = IpsClient(url, "secret-key", requestTimeout = Duration.ofMillis(500))

    private fun String.formParams(): Map<String, String> =
        split("&").associate { pair ->
            val (key, value) = pair.split("=", limit = 2).map { URLDecoder.decode(it, Charsets.UTF_8) }
            key to value
        }

    @Test
    fun `creates member with form encoded parameters and api key auth`() {
        responseBody = """{"id": 17, "name": "Steve"}"""

        val result = client().createMember("Steve", "p&ss=wo+rd ü", group = 3, validated = 1, registrationIp = "1.2.3.4")

        assertEquals(IpsResult.Success(17L), result)
        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/api/index.php?/core/members", request.uri)
        assertEquals("Basic " + Base64.getEncoder().encodeToString("secret-key:".toByteArray()), request.authorization)
        assertEquals(
            mapOf(
                "name" to "Steve",
                "password" to "p&ss=wo+rd ü",
                "group" to "3",
                "validated" to "1",
                "registrationIpAddress" to "1.2.3.4",
            ),
            request.body.formParams(),
        )
    }

    @Test
    fun `omits unknown registration ip`() {
        responseBody = """{"id": 1}"""

        client().createMember("Steve", "pw", 3, 1, registrationIp = null)

        assertTrue("registrationIpAddress" !in requests.single().body.formParams())
    }

    @Test
    fun `handles trailing slash in forum url`() {
        responseBody = """{"id": 1}"""

        client("$baseUrl/").createMember("Steve", "pw", 3, 1, null)

        assertEquals("/api/index.php?/core/members", requests.single().uri)
    }

    @Test
    fun `reports ips error`() {
        status = 400
        responseBody = """{"errorCode": "1C292/4", "errorMessage": "USERNAME_EXISTS"}"""

        val result = client().createMember("Steve", "pw", 3, 1, null)

        assertEquals(IpsResult.Failure("HTTP 400, 1C292/4 USERNAME_EXISTS"), result)
    }

    @Test
    fun `reports error message even with success status`() {
        responseBody = """{"errorMessage": "NO_PERMISSION"}"""

        assertIs<IpsResult.Failure>(client().changePassword(5, "pw"))
    }

    @Test
    fun `reports non json response`() {
        status = 502
        responseBody = "<html>Bad gateway</html>"

        val result = client().deleteMember(5)

        assertIs<IpsResult.Failure>(result)
        assertTrue("Bad gateway" in result.message)
    }

    @Test
    fun `reports http error without error message`() {
        status = 500
        responseBody = "{}"

        assertIs<IpsResult.Failure>(client().deleteMember(5))
    }

    @Test
    fun `truncates huge responses in error message`() {
        status = 500
        responseBody = "x".repeat(10_000)

        val result = client().deleteMember(5)

        assertIs<IpsResult.Failure>(result)
        assertTrue(result.message.length < 500)
    }

    @Test
    fun `reports missing member id`() {
        responseBody = """{"name": "Steve"}"""

        assertIs<IpsResult.Failure>(client().createMember("Steve", "pw", 3, 1, null))
    }

    @Test
    fun `changes password of member`() {
        responseBody = """{"id": 5}"""

        assertEquals(IpsResult.Success(Unit), client().changePassword(5, "new pw"))

        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/api/index.php?/core/members/5", request.uri)
        assertEquals(mapOf("password" to "new pw"), request.body.formParams())
    }

    @Test
    fun `deletes member`() {
        assertEquals(IpsResult.Success(Unit), client().deleteMember(5))

        val request = requests.single()
        assertEquals("DELETE", request.method)
        assertEquals("/api/index.php?/core/members/5", request.uri)
    }

    @Test
    fun `times out instead of hanging`() {
        responseDelay = Duration.ofSeconds(3)

        val started = System.nanoTime()
        val result = client().deleteMember(5)

        assertIs<IpsResult.Failure>(result)
        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(2))
    }

    @Test
    fun `reports unreachable forum`() {
        val url = baseUrl
        server.stop(0)

        assertIs<IpsResult.Failure>(client(url).deleteMember(5))
    }
}
