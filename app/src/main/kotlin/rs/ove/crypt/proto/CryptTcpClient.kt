package rs.ove.crypt.proto

import java.io.IOException
import java.util.Collections
import java.util.LinkedHashMap
import java.util.HashMap

/** Thin Android facade; the complete MST5 implementation lives in the Rust mst5-client crate. */
class CryptTcpClient {
    private var account: SharedAccount? = null
    @Throws(Exception::class) fun request(baseUrl: String?, transportMode: String?, token: String?, method: String?, path: String?, body: Any?, timeoutMs: Int): Response {
        val response = NativeMst5.request(connection(baseUrl, transportMode), token, method, path, timeoutMs, body)
        return Response(response.code, emptyMap(), response.payload)
    }
    @Throws(Exception::class) fun request(baseUrl: String?, token: String?, method: String?, path: String?, body: Any?, timeoutMs: Int): Response = request(baseUrl, "auto", token, method, path, body, timeoutMs)
    fun close() = synchronized(ACCOUNTS_LOCK) {
        val current = account ?: return@synchronized
        current.references--
        if (current.references <= 0) { ACCOUNTS.remove("${current.endpoint}\n${current.transportMode}"); NativeMst5.close(current.handle) }
        account = null
    }
    @Suppress("deprecation") protected fun finalize() { close() }
    @Throws(IOException::class) private fun connection(rawEndpoint: String?, rawTransportMode: String?): Long = synchronized(ACCOUNTS_LOCK) {
        val endpoint = rawEndpoint.orEmpty().trim(); val transportMode = rawTransportMode?.trim() ?: "auto"; val key = "$endpoint\n$transportMode"
        account?.let { if (it.endpoint == endpoint && it.transportMode == transportMode) return@synchronized it.handle }
        close()
        val shared = ACCOUNTS[key] ?: SharedAccount(endpoint, transportMode, NativeMst5.open(endpoint, CryptIdentity.serverPublicKeyBase64(), transportMode)).also { ACCOUNTS[key] = it }
        shared.references++; account = shared; shared.handle
    }
    class Response private constructor(private val code: Int, private val headers: Map<String, String>, private val body: Any?) {
        fun code(): Int = code; fun headers(): Map<String, String> = headers; fun body(): Any? = body
        companion object { operator fun invoke(code: Int, headers: Map<String, String>, body: Any?) = Response(code, Collections.unmodifiableMap(LinkedHashMap(headers)), body) }
    }
    private class SharedAccount(val endpoint: String, val transportMode: String, val handle: Long, var references: Int = 0)
    companion object { private val ACCOUNTS_LOCK = Any(); private val ACCOUNTS = HashMap<String, SharedAccount>() }
}
