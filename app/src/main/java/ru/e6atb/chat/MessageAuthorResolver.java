package ru.e6atb.chat;

final class MessageAuthorResolver {
	private MessageAuthorResolver() {
	}

	static MiniTaLib.User resolve(MiniTaLib.Message message, MiniTaLib.User currentRoom) {
		if (message == null) return null;
		MiniTaLib.User author = message.from;
		author = preferMatchingMetadata(author, message.to);
		author = preferMatchingMetadata(author, currentRoom);
		return author;
	}

	private static MiniTaLib.User preferMatchingMetadata(MiniTaLib.User current, MiniTaLib.User candidate) {
		if (candidate == null) return current;
		if (current == null) return candidate;
		if (!sameIdentity(current, candidate)) return current;
		return metadataScore(candidate) > metadataScore(current) ? candidate : current;
	}

	private static boolean sameIdentity(MiniTaLib.User left, MiniTaLib.User right) {
		if (left.id != null && left.id.length() > 0 && left.id.equals(right.id)) return true;
		return left.login != null && left.login.length() > 0 && left.login.equals(right.login);
	}

	private static int metadataScore(MiniTaLib.User user) {
		int score = 0;
		if (user.nick != null && user.nick.length() > 0) score += 8;
		if (user.login != null && user.login.length() > 0) score += 4;
		if (user.roomKind != null && user.roomKind.length() > 0) score += 2;
		if (user.id != null && user.id.length() > 0) score += 1;
		return score;
	}
}
