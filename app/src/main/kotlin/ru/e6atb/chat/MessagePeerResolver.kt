@file:Suppress("EXPOSED_FUNCTION_RETURN_TYPE", "EXPOSED_PARAMETER_TYPE")

package ru.e6atb.chat

object MessagePeerResolver {
    @JvmStatic fun peer(message: MST5.Message?, myLogin: String?, myID: String?, currentPeer: String?, currentPeerUser: MST5.User?): String {
        message ?: return ""
        val from = message.from ?: return ""
        val to = message.to ?: return ""
        return if (isRoomMessage(message)) {
            if (sameUser(to, currentPeerUser) && !currentPeer.isNullOrEmpty()) currentPeer else address(to)
        } else if (isMe(from, myLogin, myID)) address(to) else address(from)
    }

    @JvmStatic fun peerUser(message: MST5.Message?, myLogin: String?, myID: String?, currentPeerUser: MST5.User?): MST5.User? {
        message ?: return null
        val from = message.from ?: return null
        val to = message.to ?: return null
        return if (isRoomMessage(message)) if (isRoom(currentPeerUser) && sameUser(to, currentPeerUser)) currentPeerUser else to else if (isMe(from, myLogin, myID)) to else from
    }

    private fun isRoomMessage(message: MST5.Message) = message.commentPostId > 0 || message.chatId?.startsWith("chat:") == true || !message.to?.roomKind.isNullOrEmpty()
    private fun isRoom(user: MST5.User?) = !user?.roomKind.isNullOrEmpty()
    private fun isMe(user: MST5.User, login: String?, id: String?) = (!login.isNullOrEmpty() && login == user.login) || (!id.isNullOrEmpty() && id == user.id)
    private fun sameUser(left: MST5.User?, right: MST5.User?): Boolean = left != null && right != null && ((!left.id.isNullOrEmpty() && left.id == right.id) || (!left.login.isNullOrEmpty() && left.login == right.login))
    private fun address(user: MST5.User?) = user?.login?.takeIf { it.isNotEmpty() } ?: user?.id.orEmpty()
}
