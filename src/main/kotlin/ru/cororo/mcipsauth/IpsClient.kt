package ru.cororo.mcipsauth

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.util.*

internal sealed interface IpsResult<out T> {
    data class Success<T>(val value: T) : IpsResult<T>

    data class Failure(val message: String) : IpsResult<Nothing>
}

/**
 * Blocking client for the IPS REST API members endpoint. Must not be called from the server thread.
 */
internal class IpsClient(
    forumUrl: String,
    apiKey: String,
    private val requestTimeout: Duration = Duration.ofSeconds(15),
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
) {
    private val membersUrl = "${forumUrl.trimEnd('/')}/api/index.php?/core/members"
    private val authorization = "Basic ${Base64.getEncoder().encodeToString("$apiKey:".toByteArray())}"

    fun createMember(
        name: String,
        password: String,
        group: Int,
        validated: Int,
        registrationIp: String?,
    ): IpsResult<Long> {
        val params = mutableMapOf(
            "name" to name,
            "password" to password,
            "group" to group.toString(),
            "validated" to validated.toString(),
        )
        if (registrationIp != null) {
            params["registrationIpAddress"] = registrationIp
        }

        return when (val result = send(membersUrl, "POST", params)) {
            is IpsResult.Failure -> result
            is IpsResult.Success -> {
                val id = try {
                    result.value.get("id")?.takeIf { it.isJsonPrimitive }?.asLong
                } catch (_: NumberFormatException) {
                    null
                }
                if (id == null) IpsResult.Failure("Response has no member id") else IpsResult.Success(id)
            }
        }
    }

    fun changePassword(memberId: Long, password: String): IpsResult<Unit> =
        send("$membersUrl/$memberId", "POST", mapOf("password" to password)).map { }

    fun deleteMember(memberId: Long): IpsResult<Unit> =
        send("$membersUrl/$memberId", "DELETE", emptyMap()).map { }

    private fun send(url: String, method: String, formParams: Map<String, String>): IpsResult<JsonObject> {
        val body = formParams.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, Charsets.UTF_8)}=${URLEncoder.encode(it.value, Charsets.UTF_8)}"
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(requestTimeout)
            .header("Content-Type", FORM_CONTENT_TYPE)
            .header("Authorization", authorization)
            .method(method, if (body.isEmpty()) BodyPublishers.noBody() else BodyPublishers.ofString(body))
            .build()

        val response = try {
            httpClient.send(request, BodyHandlers.ofString())
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            return IpsResult.Failure("Request interrupted")
        } catch (ex: Exception) {
            return IpsResult.Failure("Request failed: $ex")
        }

        val status = response.statusCode()
        val json = parseJsonObject(response.body())
            ?: return IpsResult.Failure("Unexpected response (HTTP $status): ${response.body().abbreviate()}")

        if (json.has("errorMessage")) {
            val code = json.get("errorCode")?.takeIf { it.isJsonPrimitive }?.asString
            val message = json.get("errorMessage").takeIf { it.isJsonPrimitive }?.asString
            return IpsResult.Failure("HTTP $status, ${listOfNotNull(code, message).joinToString(" ")}")
        }
        if (status !in 200..299) {
            return IpsResult.Failure("HTTP $status: ${response.body().abbreviate()}")
        }
        return IpsResult.Success(json)
    }

    private companion object {
        const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded"
        const val MAX_LOGGED_BODY = 200

        fun parseJsonObject(body: String): JsonObject? =
            try {
                // Instance API kept for Gson 2.2.4 bundled with old servers
                @Suppress("DEPRECATION")
                JsonParser().parse(body).takeIf { it.isJsonObject }?.asJsonObject
            } catch (_: JsonParseException) {
                null
            }

        fun String.abbreviate() = if (length <= MAX_LOGGED_BODY) this else take(MAX_LOGGED_BODY) + "..."

        inline fun <T, R> IpsResult<T>.map(transform: (T) -> R): IpsResult<R> = when (this) {
            is IpsResult.Success -> IpsResult.Success(transform(value))
            is IpsResult.Failure -> this
        }
    }
}
