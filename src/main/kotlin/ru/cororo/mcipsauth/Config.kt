package ru.cororo.mcipsauth

import java.net.URI
import java.net.URISyntaxException

internal data class Config(
    val forumUrl: String,
    val apiKey: String,
    val startGroup: Int,
    val startValidated: Int,
) {
    /** Whether the API key and player passwords would be sent over an unencrypted connection. */
    val isInsecure: Boolean
        get() = forumUrl.startsWith("http://", ignoreCase = true)

    companion object {
        const val PLACEHOLDER_API_KEY = "API_KEY"

        /**
         * Validates raw values from config.yml.
         * @throws IllegalArgumentException with a human-readable reason if the config is unusable
         */
        fun parse(forumUrl: String?, apiKey: String?, startGroup: Int, startValidated: Int): Config {
            val url = forumUrl?.trim()?.trimEnd('/')
            require(!url.isNullOrEmpty()) { "forum_url is not set" }

            val uri = try {
                URI(url)
            } catch (_: URISyntaxException) {
                throw IllegalArgumentException("forum_url is not a valid URL: $url")
            }
            val scheme = uri.scheme?.lowercase()
            require(scheme == "https" || scheme == "http") { "forum_url must start with https:// (got $url)" }
            require(!uri.host.isNullOrEmpty()) { "forum_url has no host: $url" }
            require(uri.rawQuery == null && uri.rawFragment == null) { "forum_url must not contain ? or #: $url" }

            val key = apiKey?.trim()
            require(!key.isNullOrEmpty() && key != PLACEHOLDER_API_KEY) { "api_key is not set" }

            return Config(url, key, startGroup, startValidated)
        }
    }
}
