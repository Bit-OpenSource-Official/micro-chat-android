@file:Suppress("EXPOSED_FUNCTION_RETURN_TYPE", "EXPOSED_PARAMETER_TYPE")

package ru.e6atb.chat

object MessageAuthorResolver {
    @JvmStatic fun resolve(message: MST5.Message?, currentRoom: MST5.User?): MST5.User? {
        message ?: return null
        return preferMatchingMetadata(preferMatchingMetadata(message.from, message.to), currentRoom)
    }

    private fun preferMatchingMetadata(current: MST5.User?, candidate: MST5.User?): MST5.User? {
        candidate ?: return current
        current ?: return candidate
        return if (sameIdentity(current, candidate) && metadataScore(candidate) > metadataScore(current)) candidate else current
    }

    private fun sameIdentity(left: MST5.User, right: MST5.User) =
        (!left.id.isNullOrEmpty() && left.id == right.id) || (!left.login.isNullOrEmpty() && left.login == right.login)

    private fun metadataScore(user: MST5.User): Int =
        (if (user.nick.isNullOrEmpty()) 0 else 8) + (if (user.login.isNullOrEmpty()) 0 else 4) +
            (if (user.roomKind.isNullOrEmpty()) 0 else 2) + (if (user.id.isNullOrEmpty()) 0 else 1)
}
