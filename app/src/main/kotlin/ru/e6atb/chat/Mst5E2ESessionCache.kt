package ru.e6atb.chat

import rs.ove.crypt.proto.NativeE2E

/**
 * Small, synchronized state holder for the E2E transport facade.
 *
 * Keeping this state outside the Android activity and the HTTP facade makes
 * cache invalidation explicit: a changed peer key always drops every derived
 * session before it can be reused.
 */
class Mst5E2ESessionCache {
    private val lock = Any()
    private val peerKeys = HashMap<String, String>()
    private val sessions = HashMap<String, NativeE2E.Session>()

    fun peerKeyChanged(peerId: String, publicKey: String): Boolean = synchronized(lock) {
        val changed = peerKeys[peerId]?.let { it != publicKey } ?: false
        peerKeys[peerId] = publicKey
        if (changed) sessions.clear()
        changed
    }

    fun clearSessions() = synchronized(lock) {
        sessions.clear()
    }

    fun clear() = synchronized(lock) {
        peerKeys.clear()
        sessions.clear()
    }

    fun session(cacheKey: String): NativeE2E.Session? = synchronized(lock) {
        sessions[cacheKey]
    }

    fun rememberSession(cacheKey: String, session: NativeE2E.Session): NativeE2E.Session = synchronized(lock) {
        sessions[cacheKey] ?: session.also { sessions[cacheKey] = it }
    }
}
