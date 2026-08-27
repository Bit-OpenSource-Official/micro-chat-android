package ru.e6atb.chat

import android.content.Context
import android.os.Build
import org.json.JSONObject
import rs.ove.crypt.proto.NativeE2E
import rs.ove.crypt.proto.NativeMst5
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.Properties

internal object SessionStore {
    private const val FILE_NAME = "e6atb.session.properties"
    private const val SERVER = "server"
    private const val TOKEN = "token"
    private const val USER_ID = "user_id"
    private const val USERNAME = "username"
    private const val LEGACY_LOGIN = "login"
    private const val LAST_UPDATE = "last_update"
    private const val BACKGROUND_LAST_UPDATE = "background_last_update"
    private const val NOTIFICATION_BOOTSTRAP_COMPLETE = "notification_bootstrap_complete"
    private const val LAST_GITHUB_UPDATE_CHECK_AT = "last_github_update_check_at"
    private const val SHOW_STATUS = "show_status"
    private const val USE_INSETS = "use_insets"
    private const val LANGUAGE = "language"
    private const val TRANSPORT_PROTOCOL = "transport_protocol"
    @JvmField val TRANSPORT_AUTO = "auto"
    @JvmField val TRANSPORT_MST5 = "mst5"
    @JvmField val TRANSPORT_M5OH = "m5oh"
    private const val E2E_PEER_PREFIX = "e2e.peer."

    @JvmStatic fun save(context: Context?, server: String?, token: String?, username: String?) = save(context, server, token, userId(context), username)
    @JvmStatic fun save(context: Context?, server: String?, token: String?, userId: String?, username: String?) {
        val p = load(context)
        p.setProperty(SERVER, normalizeServer(server)); p.setProperty(TOKEN, safe(token)); p.setProperty(USER_ID, safe(userId)); p.setProperty(USERNAME, safe(username)); p.remove(LEGACY_LOGIN)
        store(context, p)
    }
    @JvmStatic fun saveServer(context: Context?, server: String?) { load(context).also { it.setProperty(SERVER, normalizeServer(server)); store(context, it) } }
    @JvmStatic fun clear(context: Context?) { load(context).also { p -> listOf(TOKEN, USER_ID, USERNAME, LEGACY_LOGIN, LAST_UPDATE, BACKGROUND_LAST_UPDATE, NOTIFICATION_BOOTSTRAP_COMPLETE).forEach(p::remove); store(context, p) } }
    @JvmStatic fun hasSession(context: Context?): Boolean = token(context).isNotEmpty()
    @JvmStatic fun server(context: Context?, fallback: String?): String {
        val properties = load(context); val fixed = normalizeServer(fallback)
        if (fixed != normalizeServer(properties.getProperty(SERVER, ""))) { properties.setProperty(SERVER, fixed); store(context, properties) }
        return fixed
    }
    @JvmStatic fun transportProtocol(context: Context?): String = get(context, TRANSPORT_PROTOCOL, TRANSPORT_AUTO).let { if (it == TRANSPORT_MST5 || it == TRANSPORT_M5OH) it else TRANSPORT_AUTO }
    @JvmStatic fun setTransportProtocol(context: Context?, protocol: String?) { load(context).also { p -> p.setProperty(TRANSPORT_PROTOCOL, if (protocol == TRANSPORT_MST5 || protocol == TRANSPORT_M5OH) protocol else TRANSPORT_AUTO); store(context, p) } }
    @JvmStatic fun transportEndpoint(context: Context?, fallback: String?): String = server(context, fallback)
    @JvmStatic fun normalizeServer(server: String?): String {
        var value = safe(server).trim(); while (value.endsWith('/')) value = value.dropLast(1)
        return if (value.lowercase(Locale.US).startsWith("tcp://")) value.substring(6) else value
    }
    @JvmStatic fun token(context: Context?): String = get(context, TOKEN, "")
    @JvmStatic fun login(context: Context?): String = get(context, USERNAME, "").let { if (it.isNotEmpty()) it else get(context, LEGACY_LOGIN, "") }
    @JvmStatic fun userId(context: Context?): String = get(context, USER_ID, "")
    @JvmStatic fun lastUpdate(context: Context?): Long = longValue(context, LAST_UPDATE)
    @JvmStatic fun lastUpdate(context: Context?, id: Long) = setLong(context, LAST_UPDATE, id)
    @JvmStatic fun backgroundLastUpdate(context: Context?): Long = longValue(context, BACKGROUND_LAST_UPDATE)
    @JvmStatic fun backgroundLastUpdate(context: Context?, id: Long) = setLong(context, BACKGROUND_LAST_UPDATE, id)
    @JvmStatic fun notificationBootstrapComplete(context: Context?): Boolean = getBoolean(context, NOTIFICATION_BOOTSTRAP_COMPLETE, true)
    @JvmStatic fun notificationBootstrapComplete(context: Context?, complete: Boolean) = setBoolean(context, NOTIFICATION_BOOTSTRAP_COMPLETE, complete)
    @JvmStatic fun lastGithubUpdateCheckAt(context: Context?): Long = longValue(context, LAST_GITHUB_UPDATE_CHECK_AT)
    @JvmStatic fun lastGithubUpdateCheckAt(context: Context?, timestamp: Long) = setLong(context, LAST_GITHUB_UPDATE_CHECK_AT, timestamp)
    @JvmStatic fun showStatus(context: Context?): Boolean = getBoolean(context, SHOW_STATUS, true)
    @JvmStatic fun showStatus(context: Context?, enabled: Boolean) = setBoolean(context, SHOW_STATUS, enabled)
    @JvmStatic fun useInsets(context: Context?): Boolean = getBoolean(context, USE_INSETS, Build.VERSION.SDK_INT >= 20)
    @JvmStatic fun useInsets(context: Context?, enabled: Boolean) = setBoolean(context, USE_INSETS, enabled)
    @JvmStatic fun language(context: Context?): String = get(context, LANGUAGE, AppLocale.SYSTEM)
    @JvmStatic fun language(context: Context?, language: String?) { load(context).also { p -> p.setProperty(LANGUAGE, safe(language)); store(context, p) } }

    @JvmStatic fun e2eIdentity(context: Context?, login: String?): NativeE2E.Identity? = try { NativeE2E.open(context, keyID(login), false) } catch (_: Exception) { null }
    @JvmStatic fun clearLegacyE2EIdentity(context: Context?, login: String?) { load(context).also { p -> p.remove("e2e.private.${keyID(login)}"); store(context, p) } }
    @JvmStatic fun createE2EIdentity(context: Context?, login: String?): NativeE2E.Identity = try { NativeE2E.open(context, keyID(login), true) } catch (error: Exception) { throw IllegalStateException("cannot create native E2E identity", error) }
    @JvmStatic fun clearE2EIdentity(context: Context?, login: String?) {
        try { e2eIdentity(context, login)?.remove() } catch (_: Exception) { }
        clearLegacyE2EIdentity(context, login)
    }
    @JvmStatic fun pinPeerE2EKey(context: Context?, server: String?, ownLogin: String?, peer: String?, publicKey: String?): Boolean {
        val key = E2E_PEER_PREFIX + keyID("${safe(server)}\n${safe(ownLogin)}\n${safe(peer)}")
        val p = load(context); val current = p.getProperty(key)
        if (current == null || current != publicKey) { p.setProperty(key, safe(publicKey)); store(context, p); return current != null }; return false
    }
    @JvmStatic fun peerE2EFingerprint(context: Context?, server: String?, ownLogin: String?, peer: String?): String {
        val key = E2E_PEER_PREFIX + keyID("${safe(server)}\n${safe(ownLogin)}\n${safe(peer)}"); val publicKey = get(context, key, "")
        return if (publicKey.isEmpty()) "" else try { NativeE2E.fingerprint(publicKey) } catch (_: Exception) { "" }
    }

    private fun longValue(context: Context?, key: String): Long = get(context, key, "0").toLongOrNull() ?: 0
    private fun setLong(context: Context?, key: String, value: Long) { load(context).also { p -> p.setProperty(key, value.toString()); store(context, p) } }
    private fun getBoolean(context: Context?, key: String, fallback: Boolean): Boolean = get(context, key, fallback.toString()) == "true"
    private fun setBoolean(context: Context?, key: String, value: Boolean) { load(context).also { p -> p.setProperty(key, value.toString()); store(context, p) } }
    private fun get(context: Context?, key: String, fallback: String): String = load(context).getProperty(key) ?: fallback
    @Synchronized private fun load(context: Context?): Properties = try {
        val snapshot = JSONObject(NativeMst5.sessionSnapshot())
        if (snapshot.optBoolean("exists", false)) properties(snapshot.optJSONObject("values")) else loadLegacy(context).also { if (it.isNotEmpty()) store(context, it) }
    } catch (_: Exception) { loadLegacy(context) }
    @Synchronized private fun loadLegacy(context: Context?): Properties {
        val properties = Properties(); val file = file(context) ?: return properties; if (!file.exists()) return properties
        try { FileInputStream(file).use(properties::load) } catch (_: Exception) { }; return properties
    }
    @Synchronized private fun store(context: Context?, properties: Properties) { try { NativeMst5.replaceSession(propertiesJson(properties).toString()); return } catch (_: Exception) { }; storeLegacy(context, properties) }
    @Synchronized private fun storeLegacy(context: Context?, properties: Properties) {
        val file = file(context) ?: return; file.parentFile?.let { if (!it.exists()) it.mkdirs() }
        try { FileOutputStream(file).use { properties.store(it, ""); it.flush() } } catch (_: Exception) { }
    }
    private fun properties(values: JSONObject?): Properties = Properties().also { out -> values?.keys()?.forEach { key -> values.opt(key).takeUnless { it == null || it == JSONObject.NULL }?.let { out.setProperty(key, it.toString()) } } }
    private fun propertiesJson(values: Properties): JSONObject = JSONObject().also { out -> values.stringPropertyNames().forEach { out.put(it, values.getProperty(it, "")) } }
    private fun file(context: Context?): File? { val app = context?.applicationContext ?: context ?: return null; val directory = app.filesDir ?: return null; if (!directory.exists()) directory.mkdirs(); return File(directory, FILE_NAME) }
    private fun safe(value: String?): String = value ?: ""
    private fun keyID(value: String?): String = try { MessageDigest.getInstance("SHA-256").digest(safe(value).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } } catch (error: Exception) { throw IllegalStateException(error) }
}
