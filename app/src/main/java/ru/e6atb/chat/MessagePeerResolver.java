package ru.e6atb.chat;

final class MessagePeerResolver {
	private MessagePeerResolver() {
	}

	static String peer(
			MiniTaLib.Message message,
			String myLogin,
			String myID,
			String currentPeer,
			MiniTaLib.User currentPeerUser
	) {
		if (message == null || message.from == null || message.to == null) return "";
		if (isRoomMessage(message)) {
			if (isSameUser(message.to, currentPeerUser) && currentPeer != null && currentPeer.length() > 0) {
				return currentPeer;
			}
			return address(message.to);
		}
		return isMe(message.from, myLogin, myID) ? address(message.to) : address(message.from);
	}

	static MiniTaLib.User peerUser(
			MiniTaLib.Message message,
			String myLogin,
			String myID,
			MiniTaLib.User currentPeerUser
	) {
		if (message == null || message.from == null || message.to == null) return null;
		if (isRoomMessage(message)) {
			if (isRoom(currentPeerUser) && isSameUser(message.to, currentPeerUser)) return currentPeerUser;
			return message.to;
		}
		return isMe(message.from, myLogin, myID) ? message.to : message.from;
	}

	private static boolean isRoomMessage(MiniTaLib.Message message) {
		return message.commentPostId > 0
				|| (message.chatId != null && message.chatId.startsWith("chat:"))
				|| (message.to.roomKind != null && message.to.roomKind.length() > 0);
	}

	private static boolean isRoom(MiniTaLib.User user) {
		return user != null && user.roomKind != null && user.roomKind.length() > 0;
	}

	private static boolean isMe(MiniTaLib.User user, String myLogin, String myID) {
		return user != null && ((myLogin != null && myLogin.length() > 0 && myLogin.equals(user.login))
				|| (myID != null && myID.length() > 0 && myID.equals(user.id)));
	}

	private static boolean isSameUser(MiniTaLib.User left, MiniTaLib.User right) {
		if (left == null || right == null) return false;
		if (left.id.length() > 0 && left.id.equals(right.id)) return true;
		return left.login.length() > 0 && left.login.equals(right.login);
	}

	private static String address(MiniTaLib.User user) {
		if (user == null) return "";
		return user.login.length() > 0 ? user.login : user.id;
	}

}
