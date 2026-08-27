package rs.ove.crypt.proto

import ru.e6atb.chat.BuildConfig
object CryptIdentity {
    const val SERVER_PUBLIC_PROPERTY = "rs.ove.crypt.server_public_key_b64"

    @JvmStatic fun serverPublicKeyBase64(): String {
        val value = System.getProperty(SERVER_PUBLIC_PROPERTY).takeUnless { it.isNullOrBlank() } ?: BuildConfig.CRYPT_SERVER_PUBLIC_KEY_B64
        check(!value.isNullOrBlank()) { "server public key pin is not configured" }
        return value.trim()
    }
}
