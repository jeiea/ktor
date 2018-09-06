package io.ktor.client.features.cookies

import io.ktor.http.*

/**
 * Storage for [Cookie].
 */
interface CookiesStorage {
    /**
     * Gets a map of [String] to [Cookie] for a specific [host].
     */
    suspend fun get(requestUrl: Url): List<Cookie>

    /**
     * Sets a [cookie] for the specified [host].
     */
    suspend fun addCookie(requestUrl: Url, cookie: Cookie)
}

suspend fun CookiesStorage.addCookie(urlString: String, cookie: Cookie) {
    addCookie(Url(urlString), cookie)
}

internal fun Cookie.matches(requestUrl: Url): Boolean {
    val domain = domain ?: error("Domain field should have the default value")
    val path = path ?: error("Path field should have the default value")

    if (requestUrl.host != domain && !requestUrl.host.endsWith(".$domain")) return false
    if (path != "/" &&
        requestUrl.encodedPath != path &&
        (!requestUrl.encodedPath.startsWith("$path/"))
    ) return false

    if (secure && !requestUrl.protocol.isSecure()) return false
    if (httpOnly && requestUrl.protocol != URLProtocol.HTTP) return false

    return true
}

internal fun Cookie.fillDefaults(requestUrl: Url): Cookie {
    var result = this

    if (result.path == null) {
        result = result.copy(path = requestUrl.encodedPath)
    }

    if (result.domain.isNullOrBlank()) {
        result = result.copy(domain = requestUrl.host)
    }

    return result
}