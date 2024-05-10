package ru.cororo.mcipsauth

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.util.*

private val httpClient = HttpClient.newHttpClient()
private val gson = Gson()

fun formRequest(url: String, formParams: Map<String, String>, method: String = "POST"): String {
    val request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .method(method, BodyPublishers.ofString(formParams.entries.joinToString("&") { "${URLEncoder.encode(it.key, Charsets.UTF_8)}=${URLEncoder.encode(it.value, Charsets.UTF_8)}" }))
        .header("Authorization", "Basic ${Base64.getEncoder().encodeToString("$apiKey:".toByteArray())}")
        .build()

    return httpClient.send(request, BodyHandlers.ofString()).body()
}

fun String.asJsonObject(): JsonObject? {
    return try {
        gson.fromJson(replace("\n", "").trim(), JsonObject::class.java)
    } catch (ex: JsonSyntaxException) {
        ex.printStackTrace()
        return null
    }
}