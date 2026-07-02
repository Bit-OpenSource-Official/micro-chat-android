package ru.e6atb.chat;

import org.json.JSONArray;
import org.json.JSONObject;

import android.util.Base64;
import android.content.Context;

import rs.ove.crypt.proto.CryptTcpClient;
import rs.ove.crypt.proto.E2ECipher;
import rs.ove.crypt.proto.E2EKeyBackup;

import java.net.URI;
import java.net.URLEncoder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

final class MiniTaLib {
	private final String baseUrl;
	private final CryptTcpClient transport = new CryptTcpClient();
	private final Context context;
	private final HashMap<String, String> peerE2EKeys = new HashMap<String, String>();
	private final HashMap<String, E2ECipher.Session> peerE2ESessions = new HashMap<String, E2ECipher.Session>();
	private String token;
	private String login;
	private E2ECipher.Identity e2eIdentity;

	static final class InvalidTokenException extends RuntimeException {
		InvalidTokenException(String message) {
			super(message);
		}
	}

	MiniTaLib(String baseUrl) {
		this(null, baseUrl, "", "");
	}

	MiniTaLib(String baseUrl, String token) {
		this(null, baseUrl, token, "");
	}

	MiniTaLib(Context context, String baseUrl) {
		this(context, baseUrl, "", "");
	}

	MiniTaLib(Context context, String baseUrl, String token, String login) {
		this.context = context == null ? null : context.getApplicationContext();
		this.baseUrl = trimSlash(baseUrl);
		this.token = token;
		this.login = login == null ? "" : login;
		if (this.context != null && this.login.length() > 0) {
			this.e2eIdentity = SessionStore.e2eIdentity(this.context, this.login);
		}
	}

	String baseUrl() {
		return baseUrl;
	}

	String token() {
		return token == null ? "" : token;
	}

	String startEmailAuth(String email) throws Exception {
		JSONObject body = new JSONObject();
		body.put("email", email);
		JSONObject out = post("/auth/email/start", body, 10000);
		return out.optString("debug_code");
	}

	User verifyEmailAuth(String email, String code, String cloudPassword) throws Exception {
		JSONObject body = new JSONObject();
		body.put("email", email);
		body.put("code", code);
		body.put("cloud_password", cloudPassword == null ? "" : cloudPassword);
		JSONObject out = post("/auth/email/verify", body, 10000);
		token = out.getString("token");
		User result = user(out.getJSONObject("user"));
		tryActivateE2E(result.login, cloudPassword);
		return result;
	}

	User me() throws Exception {
		User result = user(get("/me", 10000).getJSONObject("user"));
		tryActivateE2E(result.login, null);
		return result;
	}

	void setCloudPassword(String password) throws Exception {
		String value = password == null ? "" : password;
		JSONObject body = new JSONObject();
		body.put("password", value);
		if (value.length() > 0) {
			E2EKeyBackup.Backup backup = e2eBackupForCloudPassword(value);
			if (backup != null) {
				body.put("e2e_backup", e2eBackupJson(backup));
			}
		}
		post("/cloud-password", body, 10000);
	}

	void resetCloudPassword(String code) throws Exception {
		JSONObject body = new JSONObject();
		body.put("code", code == null ? "" : code.trim());
		post("/cloud-password/reset", body, 10000);
	}

	void resetE2EKey() throws Exception {
		JSONObject body = new JSONObject();
		body.put("confirm", "reset_e2e");
		post("/e2e/reset", body, 10000);
		if (context != null && login != null && login.length() > 0) {
			SessionStore.clearE2EIdentity(context, login);
		}
		e2eIdentity = null;
		peerE2ESessions.clear();
		activateE2E(login, null);
	}

	User resetCloudPassword(String email, String code) throws Exception {
		JSONObject body = new JSONObject();
		body.put("email", email == null ? "" : email.trim());
		body.put("code", code == null ? "" : code.trim());
		JSONObject out = post("/cloud-password/reset", body, 10000);
		token = out.getString("token");
		User result = user(out.getJSONObject("user"));
		tryActivateE2E(result.login, null);
		return result;
	}

	void deleteAccount(String code) throws Exception {
		JSONObject body = new JSONObject();
		body.put("code", code == null ? "" : code.trim());
		post("/account/delete", body, 10000);
		token = "";
	}

	User setUsername(String username) throws Exception {
		JSONObject body = new JSONObject();
		body.put("username", username == null ? "" : username.trim());
		JSONObject out = post("/username", body, 10000);
		User result = user(out.getJSONObject("user"));
		tryActivateE2E(result.login, null);
		return result;
	}

	User setName(String name) throws Exception {
		JSONObject body = new JSONObject();
		body.put("name", name == null ? "" : name.trim());
		JSONObject out = post("/name", body, 10000);
		return user(out.getJSONObject("user"));
	}

	User setPrivacy(String messagePrivacy, String callPrivacy) throws Exception {
		return setPrivacy(messagePrivacy, callPrivacy, "everyone");
	}

	User setPrivacy(String messagePrivacy, String callPrivacy, String invitePrivacy) throws Exception {
		JSONObject body = new JSONObject();
		body.put("message_privacy", messagePrivacy == null ? "" : messagePrivacy.trim());
		body.put("call_privacy", callPrivacy == null ? "" : callPrivacy.trim());
		body.put("invite_privacy", invitePrivacy == null ? "" : invitePrivacy.trim());
		JSONObject out = post("/privacy", body, 10000);
		return user(out.getJSONObject("user"));
	}

	List<User> getContacts() throws Exception {
		JSONArray arr = get("/contacts", 10000).getJSONArray("contacts");
		ArrayList<User> contacts = new ArrayList<User>(arr.length());
		for (int i = 0; i < arr.length(); i++) {
			contacts.add(user(arr.getJSONObject(i)));
		}
		return contacts;
	}

	User addContact(String address) throws Exception {
		JSONObject body = new JSONObject();
		body.put("user", address == null ? "" : address.trim());
		return user(post("/contacts/add", body, 10000).getJSONObject("contact"));
	}

	void deleteContact(String address) throws Exception {
		JSONObject body = new JSONObject();
		body.put("user", address == null ? "" : address.trim());
		post("/contacts/delete", body, 10000);
	}

	Chat addChatMember(String chat, String user) throws Exception {
		JSONObject body = new JSONObject();
		body.put("chat", chat == null ? "" : chat.trim());
		body.put("user", user == null ? "" : user.trim());
		JSONObject out = post("/chats/members/add", body, 10000).getJSONObject("chat");
		User room = roomUser(out);
		return new Chat(room.id, room, null, false);
	}

	Chat removeChatMember(String chat, String user) throws Exception {
		JSONObject body = new JSONObject();
		body.put("chat", chat == null ? "" : chat.trim());
		body.put("user", user == null ? "" : user.trim());
		JSONObject out = post("/chats/members/remove", body, 10000).getJSONObject("chat");
		User room = roomUser(out);
		return new Chat(room.id, room, null, false);
	}

	void leaveChat(String chat, String me) throws Exception {
		removeChatMember(chat, me);
	}

	Chat setChatTitle(String chat, String title) throws Exception {
		JSONObject body = new JSONObject();
		body.put("chat", chat == null ? "" : chat.trim());
		body.put("title", title == null ? "" : title.trim());
		JSONObject out = post("/chats/title", body, 10000).getJSONObject("chat");
		User room = roomUser(out);
		return new Chat(room.id, room, null, false);
	}

	Chat setChannelUsername(String chat, String username) throws Exception {
		JSONObject body = new JSONObject();
		body.put("chat", chat == null ? "" : chat.trim());
		body.put("username", username == null ? "" : username.trim());
		JSONObject out = post("/channels/username", body, 10000).getJSONObject("chat");
		User room = roomUser(out);
		return new Chat(room.id, room, null, false);
	}

	Chat createGroup(String title, List<String> members) throws Exception {
		return createRoom("/groups", title, "", members);
	}

	Chat createChannel(String title, String username, List<String> members) throws Exception {
		return createRoom("/channels", title, username, members);
	}

	private Chat createRoom(String path, String title, String username, List<String> members) throws Exception {
		JSONObject body = new JSONObject();
		body.put("title", title == null ? "" : title.trim());
		if (username != null && username.trim().length() > 0) body.put("username", username.trim());
		JSONArray arr = new JSONArray();
		if (members != null) {
			for (String member : members) {
				if (member != null && member.trim().length() > 0) arr.put(member.trim());
			}
		}
		body.put("members", arr);
		JSONObject out = post(path, body, 10000).getJSONObject("chat");
		User room = roomUser(out);
		return new Chat(room.id, room, null, false);
	}

	Message sendMessage(String to, String text) throws Exception {
		if (e2eIdentity == null) {
			throw new SecurityException("E2E private key is unavailable on this device");
		}
		E2ECipher.Envelope envelope;
		try {
			envelope = E2ECipher.seal(e2eSession(to), login, to, text);
		} catch (RuntimeException e) {
			String message = e.getMessage();
			if (message == null
					|| (!message.contains("e2e public key not registered")
					&& !message.contains("user not found"))) {
				throw e;
			}
			return sendPlainMessage(to, text);
		}
		JSONObject body = new JSONObject();
		body.put("to", to);
		JSONObject e2e = new JSONObject();
		e2e.put("version", envelope.version);
		e2e.put("nonce", envelope.nonce);
		e2e.put("ciphertext", envelope.ciphertext);
		e2e.put("tag", envelope.tag);
		body.put("e2e", e2e);
		return message(post("/send", body, 10000).getJSONObject("message"));
	}

	Message sendPlainMessage(String to, String text) throws Exception {
		JSONObject body = new JSONObject();
		body.put("to", to);
		body.put("text", text);
		return message(post("/send", body, 10000).getJSONObject("message"));
	}

	BotCreation createBot(String login) throws Exception {
		JSONObject body = new JSONObject();
		body.put("username", login);
		JSONObject out = post("/bots", body, 10000);
		return new BotCreation(
				user(out.getJSONObject("user")),
				out.optString("token")
		);
	}

	BotCreation resetBotToken(String login) throws Exception {
		JSONObject body = new JSONObject();
		body.put("username", login);
		JSONObject out = post("/bots/token/reset", body, 10000);
		return new BotCreation(
				user(out.getJSONObject("user")),
				out.optString("token")
		);
	}

	Message uploadFile(String to, String name, String mime, byte[] data) throws Exception {
		JSONObject body = new JSONObject();
		body.put("to", to);
		body.put("name", name == null || name.length() == 0 ? "file" : name);
		body.put("mime", mime == null || mime.length() == 0 ? "application/octet-stream" : mime);
		body.put("data", Base64.encodeToString(data, Base64.NO_WRAP));
		return message(post("/upload", body, 120000).getJSONObject("message"));
	}

	void markRead(String peer) throws Exception {
		JSONObject body = new JSONObject();
		body.put("peer", peer);
		post("/read", body, 10000);
	}

	void sendCallback(String to, long messageId, String callback) throws Exception {
		JSONObject body = new JSONObject();
		body.put("to", to);
		body.put("message_id", messageId);
		body.put("callback", callback == null ? "" : callback);
		post("/callback", body, 10000);
	}

	Message deleteMessage(long id) throws Exception {
		JSONObject body = new JSONObject();
		body.put("id", id);
		return message(post("/delete", body, 10000).getJSONObject("message"));
	}

	Message favoriteMessage(long id) throws Exception {
		JSONObject body = new JSONObject();
		body.put("id", id);
		return message(post("/favorite", body, 10000).getJSONObject("message"));
	}

	void deleteChat(String peer) throws Exception {
		JSONObject body = new JSONObject();
		body.put("peer", peer);
		post("/chats/delete", body, 10000);
	}

	void banUser(String login) throws Exception {
		JSONObject body = new JSONObject();
		body.put("username", login);
		post("/users/ban", body, 10000);
	}

	void unbanUser(String login) throws Exception {
		JSONObject body = new JSONObject();
		body.put("username", login);
		post("/users/unban", body, 10000);
	}

	String fileUrl(String fileID) throws Exception {
		return wsHttpBaseUrl() + "/file/" + enc(fileID) + "?token=" + enc(token());
	}

	byte[] downloadFileBytes(String fileID, int maxBytes) throws Exception {
		String path = "/file/" + enc(fileID);
		CryptTcpClient.Response response = transport.request(baseUrl, token(), "GET", path, null, 30000);
		byte[] data = response.body();
		if (response.code() < 200 || response.code() >= 300) {
			String text = new String(data, "UTF-8");
			throw apiException(response.code(), text.length() == 0 ? "TCP " + response.code() : text);
		}
		if (maxBytes > 0 && data.length > maxBytes) {
			throw new RuntimeException("file is too large");
		}
		return data;
	}

	void sendCall(String to, String action) throws Exception {
		JSONObject body = new JSONObject();
		body.put("to", to);
		body.put("action", action);
		post("/call", body, 10000);
	}

	List<Chat> getChats() throws Exception {
		JSONArray arr = get("/chats", 10000).getJSONArray("chats");
		ArrayList<Chat> chats = new ArrayList<Chat>(arr.length());
		for (int i = 0; i < arr.length(); i++) {
			chats.add(chat(arr.getJSONObject(i)));
		}
		return chats;
	}

	List<Message> getHistory(String peer, long after, int limit) throws Exception {
		String path = "/history?peer=" + enc(peer) + "&after=" + after + "&limit=" + limit;
		return historyPage(path).messages;
	}

	List<Message> getHistoryBefore(String peer, long before, int limit) throws Exception {
		return getHistoryPageBefore(peer, before, limit).messages;
	}

	HistoryPage getHistoryPageBefore(String peer, long before, int limit) throws Exception {
		String path = "/history?peer=" + enc(peer) + "&before=" + before + "&limit=" + limit;
		return historyPage(path);
	}

	private HistoryPage historyPage(String path) throws Exception {
		JSONObject out = get(path, 10000);
		JSONArray arr = out.getJSONArray("messages");
		ArrayList<Message> messages = new ArrayList<Message>(arr.length());
		for (int i = 0; i < arr.length(); i++) {
			messages.add(message(arr.getJSONObject(i)));
		}
		JSONObject peer = out.optJSONObject("peer");
		return new HistoryPage(peer != null && peer.has("title") ? roomUser(peer) : user(peer), messages);
	}

	List<Update> getUpdates(long after, int timeoutSec) throws Exception {
		String path = "/updates?after=" + after + "&timeout=" + timeoutSec;
		JSONArray arr = get(path, (timeoutSec + 5) * 1000).getJSONArray("updates");
		ArrayList<Update> updates = new ArrayList<Update>(arr.length());
		for (int i = 0; i < arr.length(); i++) {
			updates.add(update(arr.getJSONObject(i)));
		}
		return updates;
	}

	WalletInfo getWallet() throws Exception {
		return walletInfo(get("/wallet", 10000).getJSONObject("wallet"));
	}

	WalletInfo sendDastars(String to, long amount, String comment) throws Exception {
		JSONObject body = new JSONObject();
		body.put("to", to);
		body.put("amount", amount);
		body.put("comment", comment == null ? "" : comment.trim());
		return walletInfo(post("/wallet/send", body, 10000).getJSONObject("wallet"));
	}

	List<WalletTransaction> getWalletHistory(int limit) throws Exception {
		JSONArray arr = get("/wallet/history?limit=" + limit, 10000).getJSONArray("transactions");
		ArrayList<WalletTransaction> out = new ArrayList<WalletTransaction>(arr.length());
		for (int i = 0; i < arr.length(); i++) {
			out.add(walletTransaction(arr.getJSONObject(i)));
		}
		return out;
	}

	List<NodeStatus> getNodeStatuses() throws Exception {
		JSONArray arr = get("/nodes/status", 10000).getJSONArray("nodes");
		ArrayList<NodeStatus> out = new ArrayList<NodeStatus>(arr.length());
		for (int i = 0; i < arr.length(); i++) {
			JSONObject item = arr.getJSONObject(i);
			out.add(new NodeStatus(
					item.optString("type"),
					item.optString("name"),
					item.optString("status"),
					item.optInt("available"),
					item.optInt("total")
			));
		}
		return out;
	}

	List<SessionInfo> getSessions() throws Exception {
		JSONArray arr = get("/sessions", 10000).getJSONArray("sessions");
		ArrayList<SessionInfo> out = new ArrayList<SessionInfo>(arr.length());
		for (int i = 0; i < arr.length(); i++) {
			JSONObject item = arr.getJSONObject(i);
			out.add(new SessionInfo(
					item.optString("id"),
					item.optLong("created_at"),
					item.optLong("last_seen"),
					item.optString("label"),
					item.optBoolean("current")
			));
		}
		return out;
	}

	int revokeOtherSessions() throws Exception {
		JSONObject out = post("/sessions/revoke-others", new JSONObject(), 10000);
		return out.optInt("revoked");
	}

	String voiceUrl(String peer) throws Exception {
		JSONObject body = new JSONObject();
		body.put("peer", peer);
		JSONObject response = post("/voice-ticket", body, 10000);
		String ticket = response.optString("ticket");
		if (ticket == null || ticket.length() == 0) {
			throw new IOException("server did not return a voice ticket");
		}
		return voiceSocketUrl(ticket);
	}

	List<User> voiceParticipants(String chat) throws Exception {
		JSONArray arr = get("/voice/participants?chat=" + enc(chat == null ? "" : chat.trim()), 10000)
				.getJSONArray("participants");
		ArrayList<User> out = new ArrayList<User>(arr.length());
		for (int i = 0; i < arr.length(); i++) {
			out.add(user(arr.getJSONObject(i)));
		}
		return out;
	}

	String voiceSocketUrl(String ticket) throws Exception {
		if (ticket == null || ticket.length() == 0) {
			throw new IOException("server did not return a voice ticket");
		}
		return wsBaseUrl() + "/voice?ticket=" + enc(ticket);
	}

	String e2eFingerprint(String peer) throws Exception {
		return E2ECipher.fingerprint(peerE2EKey(peer));
	}

	String ownE2EPublicKey() throws Exception {
		if (login == null || login.length() == 0) {
			return "";
		}
		return fetchE2EPublicKey(login);
	}

	private JSONObject get(String path, int readTimeoutMs) throws Exception {
		return request("GET", path, null, readTimeoutMs);
	}

	private JSONObject post(String path, JSONObject body, int readTimeoutMs) throws Exception {
		return request("POST", path, body, readTimeoutMs);
	}

	private JSONObject request(String method, String path, JSONObject body, int readTimeoutMs) throws Exception {
		byte[] bodyBytes = body == null ? null : body.toString().getBytes("UTF-8");
		CryptTcpClient.Response response = transport.request(baseUrl, token(), method, path, bodyBytes, readTimeoutMs);
		String text = new String(response.body(), "UTF-8");
		JSONObject out = text.length() == 0 ? new JSONObject() : new JSONObject(text);
		if (response.code() < 200 || response.code() >= 300) {
			throw apiException(response.code(), out.optString("error", "TCP " + response.code()));
		}
		return out;
	}

	static boolean isInvalidTokenError(Throwable error) {
		return error instanceof InvalidTokenException
				|| (error != null && isExplicitInvalidTokenMessage(error.getMessage()));
	}

	private RuntimeException apiException(int code, String message) {
		String text = message == null || message.length() == 0 ? "TCP " + code : message;
		if (code == 401 && isCloudPasswordRequiredMessage(text)) {
			return new RuntimeException(text);
		}
		if (token().length() > 0 && (code == 401 || isInvalidTokenMessage(text))) {
			return new InvalidTokenException(text);
		}
		return new RuntimeException(text);
	}

	static boolean isCloudPasswordRequiredError(Throwable error) {
		return error != null && isCloudPasswordRequiredMessage(error.getMessage());
	}

	private static boolean isCloudPasswordRequiredMessage(String message) {
		if (message == null) return false;
		String text = message.toLowerCase(Locale.US);
		return text.contains("cloud password required")
				|| text.contains("cloud_password_required");
	}

	private static boolean isInvalidTokenMessage(String message) {
		if (message == null) return false;
		String text = message.toLowerCase(Locale.US);
		return text.contains("unauthorized")
				|| isExplicitInvalidTokenMessage(message);
	}

	private static boolean isExplicitInvalidTokenMessage(String message) {
		if (message == null) return false;
		String text = message.toLowerCase(Locale.US);
		return text.contains("invalid token")
				|| text.contains("bad token")
				|| (text.contains("token") && text.contains("invalid"))
				|| (text.contains("токен") && (text.contains("невер") || text.contains("не вер")));
	}

	private String wsBaseUrl() throws Exception {
		URI uri = normalizedUri(baseUrl);
		String scheme = uri.getScheme();
		String wsScheme;
		if ("https".equalsIgnoreCase(scheme) || "tcps".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme)) {
			wsScheme = "wss";
		} else {
			wsScheme = "ws";
		}
		return wsScheme + "://" + hostPort(uri);
	}

	private String wsHttpBaseUrl() throws Exception {
		URI uri = normalizedUri(baseUrl);
		String scheme = uri.getScheme();
		String httpScheme = "https".equalsIgnoreCase(scheme) || "tcps".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme) ? "https" : "http";
		return httpScheme + "://" + hostPort(uri);
	}

	private static URI normalizedUri(String raw) {
		String value = raw == null || raw.trim().length() == 0 ? "127.0.0.1:8080" : raw.trim();
		if (value.indexOf("://") < 0) {
			value = "tcp" + "://" + value;
		}
		return URI.create(value);
	}

	private static String hostPort(URI uri) {
		String host = uri.getHost();
		if (host == null || host.length() == 0) {
			throw new IllegalArgumentException("server host is required");
		}
		if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
			host = "[" + host + "]";
		}
		int port = uri.getPort();
		if (port >= 0) {
			return host + ":" + port;
		}
		return host;
	}

	private static String enc(String s) throws Exception {
		return URLEncoder.encode(s, "UTF-8");
	}

	private static String trimSlash(String s) {
		if (s == null || s.length() == 0) {
			return "127.0.0.1:8080";
		}
		s = s.trim();
		while (s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}
		String legacyPrefix = "tcp" + "://";
		if (s.toLowerCase(Locale.US).startsWith(legacyPrefix)) {
			return s.substring(legacyPrefix.length());
		}
		return s;
	}

	private static String normalizePrivacy(String value) {
		if ("contacts".equals(value) || "chats".equals(value) || "nobody".equals(value)) return value;
		return "everyone";
	}

	private static String normalizeInvitePrivacy(String value) {
		if ("contacts".equals(value) || "nobody".equals(value)) return value;
		return "everyone";
	}

	private static User user(JSONObject o) {
		if (o == null) return new User("", "", "", "", false, false, 0);
		return new User(
				o.optString("id"),
				o.optString("email"),
				o.optString("username"),
				o.optString("name", o.optString("nick")),
				o.optBoolean("verified"),
				o.optBoolean("bot"),
				o.optLong("created_at"),
				o.optString("message_privacy", "everyone"),
				o.optString("call_privacy", "everyone"),
				o.optString("invite_privacy", "everyone")
		);
	}

	private static User roomUser(JSONObject o) {
		if (o == null) return new User("", "", "", "", false, false, 0);
		JSONArray members = o.optJSONArray("member_users");
		ArrayList<User> memberUsers = new ArrayList<User>();
		if (members != null) {
			for (int i = 0; i < members.length(); i++) {
				JSONObject item = members.optJSONObject(i);
				if (item != null) memberUsers.add(user(item));
			}
		}
		return new User(
				o.optString("id"),
				"",
				o.optString("username"),
				o.optString("title"),
				false,
				false,
				o.optLong("created_at"),
				"everyone",
				"everyone",
				"everyone",
				o.optString("kind"),
				o.optString("owner_id"),
				o.optInt("members"),
				o.optInt("admins"),
				memberUsers
		);
	}

	private Message message(JSONObject o) {
		if (o == null) return null;
		User from = user(o.optJSONObject("from"));
		User to = user(o.optJSONObject("to"));
		String text = o.optString("text");
		String chatId = o.optString("chat_id");
		boolean roomMessage = chatId != null && chatId.startsWith("chat:");
		JSONObject rawE2E = o.optJSONObject("e2e");
		boolean encrypted = rawE2E != null && !roomMessage;
		boolean system = o.optBoolean("system");
		if (encrypted) {
			text = decryptMessage(from, to, rawE2E);
		} else if (!system
				&& !roomMessage
				&& o.optJSONObject("file") == null
				&& e2eIdentity != null
				&& from.login.length() > 0
				&& to.login.length() > 0) {
			try {
				String peer = from.login.equals(login) ? to.login : from.login;
				peerE2EKey(peer);
				text = "[unencrypted message blocked]";
			} catch (Exception ignored) {
			}
		}
		return new Message(
				o.optLong("id"),
				chatId,
				from,
				to,
					text,
					o.optLong("date"),
					o.optLong("read_at"),
					file(o.optJSONObject("file")),
					buttons(o.optJSONArray("buttons")),
					encrypted,
					system,
					jsonObjectString(o.optJSONObject("data"))
			);
	}

	private static String jsonObjectString(JSONObject o) {
		return o == null ? "" : o.toString();
	}

	private Chat chat(JSONObject o) {
		JSONObject last = o.optJSONObject("last");
		boolean banned = o.optBoolean("banned");
		return new Chat(
				o.optString("id"),
				o.optJSONObject("room") != null ? roomUser(o.optJSONObject("room")) : user(o.optJSONObject("peer")),
				last == null ? null : message(last),
				banned,
				o.optBoolean("banned_by_me", banned),
				o.optBoolean("banned_me", false)
		);
	}

	private Update update(JSONObject o) {
		return new Update(
				o.optLong("id"),
				o.optString("type"),
				message(o.optJSONObject("message")),
				call(o.optJSONObject("call")),
				roomUser(o.optJSONObject("room"))
		);
	}

	private static Call call(JSONObject o) {
		if (o == null) return null;
		return new Call(user(o.optJSONObject("from")), user(o.optJSONObject("to")), o.optLong("date"));
	}

	private static FileInfo file(JSONObject o) {
		if (o == null) return null;
		return new FileInfo(
				o.optString("id"),
				o.optString("name"),
				o.optString("mime"),
				o.optLong("size")
		);
	}

	private static ArrayList<Button> buttons(JSONArray raw) {
		ArrayList<Button> out = new ArrayList<Button>();
		if (raw == null) return out;
		for (int i = 0; i < raw.length(); i++) {
			JSONObject item = raw.optJSONObject(i);
			if (item == null) continue;
			out.add(new Button(
					item.optString("text"),
					item.optString("url"),
					item.optString("callback"),
					item.optLong("pay_dsr")
			));
		}
		return out;
	}

	private static WalletInfo walletInfo(JSONObject o) {
		if (o == null) return new WalletInfo(0, "dastars", "DSR", 0, "", "");
		return new WalletInfo(
				parseUserID(o.opt("user_id")),
				o.optString("currency", "dastars"),
				o.optString("code", "DSR"),
				o.optLong("balance"),
				o.optString("receive_code"),
				o.optString("instruction")
		);
	}

	private static WalletTransaction walletTransaction(JSONObject o) {
		if (o == null) return new WalletTransaction(0, 0, "", "", 0, "", "", 0, "", 0);
		return new WalletTransaction(
				o.optLong("id"),
				o.optLong("from_user_id"),
				o.optString("from_username"),
				"",
				o.optLong("to_user_id"),
				o.optString("to_username"),
				"",
				o.optLong("amount"),
				o.optString("comment"),
				o.optLong("date")
		);
	}

	private static long parseUserID(Object value) {
		if (value == null) return 0;
		if (value instanceof Number) return ((Number) value).longValue();
		String raw = String.valueOf(value).trim();
		if (raw.length() == 0) return 0;
		try {
			return Long.parseLong(raw);
		} catch (NumberFormatException ignored) {
		}
		if (raw.length() == 16) {
			try {
				return Long.parseUnsignedLong(raw, 16);
			} catch (NumberFormatException ignored) {
			}
		}
		return 0;
	}

	private void activateE2E(String userLogin, String password) throws Exception {
		login = userLogin == null ? "" : userLogin;
		peerE2ESessions.clear();
		if (context == null || login.length() == 0) {
			return;
		}
		E2ECipher.Identity local = SessionStore.e2eIdentity(context, login);
		String registered = "";
		try {
			registered = fetchE2EPublicKey(login);
		} catch (RuntimeException ignored) {
		}
		if (registered.length() > 0) {
			if (local != null && local.publicKeyB64.equals(registered)) {
				e2eIdentity = local;
				if (password != null) uploadE2EBackupAsync(local, password);
				return;
			}
			if (password != null) {
				try {
					E2ECipher.Identity restored = downloadE2EBackup(password);
					if (restored != null && restored.publicKeyB64.equals(registered)) {
						SessionStore.saveE2EIdentity(context, login, restored);
						e2eIdentity = restored;
						return;
					}
				} catch (Exception ignored) {
				}
			}
			e2eIdentity = null;
			return;
		}
		if (local == null) {
			local = SessionStore.createE2EIdentity(context, login);
		}
		JSONObject body = new JSONObject();
		body.put("public_key", local.publicKeyB64);
		post("/e2e/key", body, 10000);
		if (password != null) uploadE2EBackupAsync(local, password);
		e2eIdentity = local;
	}

	private void tryActivateE2E(String userLogin, String password) {
		try {
			activateE2E(userLogin, password);
		} catch (Exception ignored) {
			e2eIdentity = null;
		}
	}

	private void uploadE2EBackup(E2ECipher.Identity identity, String password) throws Exception {
		E2EKeyBackup.Backup backup = E2EKeyBackup.seal(identity, password);
		post("/e2e/backup", e2eBackupJson(backup), 10000);
	}

	private E2EKeyBackup.Backup e2eBackupForCloudPassword(String password) throws Exception {
		E2ECipher.Identity identity = e2eIdentityForBackup();
		return identity == null ? null : E2EKeyBackup.seal(identity, password);
	}

	private JSONObject e2eBackupJson(E2EKeyBackup.Backup backup) throws Exception {
		JSONObject body = new JSONObject();
		body.put("version", backup.version);
		body.put("salt", backup.salt);
		body.put("iv", backup.iv);
		body.put("ciphertext", backup.ciphertext);
		body.put("tag", backup.tag);
		return body;
	}

	private E2ECipher.Identity e2eIdentityForBackup() throws Exception {
		if (context == null || login == null || login.length() == 0) {
			return null;
		}
		E2ECipher.Identity local = SessionStore.e2eIdentity(context, login);
		String registered = "";
		try {
			registered = fetchE2EPublicKey(login);
		} catch (RuntimeException ex) {
			if (ex.getMessage() == null || !ex.getMessage().contains("not registered")) {
				throw ex;
			}
		}
		if (registered.length() == 0) {
			if (local == null) {
				local = SessionStore.createE2EIdentity(context, login);
			}
			JSONObject body = new JSONObject();
			body.put("public_key", local.publicKeyB64);
			post("/e2e/key", body, 10000);
			e2eIdentity = local;
			return local;
		}
		if (e2eIdentity != null && e2eIdentity.publicKeyB64.equals(registered)) {
			return e2eIdentity;
		}
		if (local != null && local.publicKeyB64.equals(registered)) {
			e2eIdentity = local;
			return local;
		}
		throw new SecurityException("E2E key mismatch; restore this account before changing cloud password");
	}

	private void uploadE2EBackupAsync(final E2ECipher.Identity identity, final String password) {
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					uploadE2EBackup(identity, password);
				} catch (Exception ignored) {
				}
			}
		}, "e2e-backup");
		thread.setDaemon(true);
		thread.start();
	}

	private E2ECipher.Identity downloadE2EBackup(String password) throws Exception {
		try {
			JSONObject raw = get("/e2e/backup", 10000).getJSONObject("backup");
			return E2EKeyBackup.open(new E2EKeyBackup.Backup(
					raw.optInt("version"), raw.optString("salt"), raw.optString("iv"),
					raw.optString("ciphertext"), raw.optString("tag")
			), password);
		} catch (RuntimeException ex) {
			if (ex.getMessage() != null && ex.getMessage().contains("not registered")) return null;
			throw ex;
		}
	}

	private String fetchE2EPublicKey(String userLogin) throws Exception {
		return get("/e2e/key?username=" + enc(userLogin), 10000).getString("public_key");
	}

	private String peerE2EKey(String peer) throws Exception {
		String normalized = peer == null ? "" : peer.trim().toLowerCase(Locale.US);
		String cached = peerE2EKeys.get(normalized);
		String publicKey = fetchE2EPublicKey(normalized);
		if (cached != null && !cached.equals(publicKey)) {
			synchronized (peerE2ESessions) {
				peerE2ESessions.remove(normalized);
			}
		}
		if (context != null) {
			if (SessionStore.pinPeerE2EKey(context, baseUrl, login, normalized, publicKey)) {
				synchronized (peerE2ESessions) {
					peerE2ESessions.remove(normalized);
				}
			}
		}
		peerE2EKeys.put(normalized, publicKey);
		return publicKey;
	}

	private E2ECipher.Session e2eSession(String peer) throws Exception {
		String normalized = peer == null ? "" : peer.trim().toLowerCase(Locale.US);
		String publicKey = peerE2EKey(normalized);
		synchronized (peerE2ESessions) {
			E2ECipher.Session cached = peerE2ESessions.get(normalized);
			if (cached != null) {
				return cached;
			}
		}
		E2ECipher.Session created = E2ECipher.session(
				e2eIdentity, publicKey, login, normalized
		);
		synchronized (peerE2ESessions) {
			peerE2ESessions.put(normalized, created);
		}
		return created;
	}

	private String decryptMessage(User from, User to, JSONObject raw) {
		if (e2eIdentity == null) {
			return "[encrypted: private key unavailable]";
		}
		String peer = from.login.equals(login) ? to.login : from.login;
		try {
			E2ECipher.Envelope envelope = new E2ECipher.Envelope(
					raw.optInt("version"),
					raw.optString("nonce"),
					raw.optString("ciphertext"),
					raw.optString("tag")
			);
			return E2ECipher.open(e2eSession(peer), from.login, to.login, envelope);
		} catch (Exception ex) {
			return "[encrypted: verification failed]";
		}
	}

	static final class User {
		final String id;
		final String email;
		final String login;
		final String nick;
		final boolean verified;
		final boolean bot;
		final long createdAt;
		final String messagePrivacy;
		final String callPrivacy;
		final String invitePrivacy;
		final String roomKind;
		final String ownerId;
		final int memberCount;
		final int adminCount;
		final List<User> memberUsers;

		User(String id, String email, String login, String nick, boolean verified, boolean bot) {
			this(id, email, login, nick, verified, bot, 0);
		}

		User(String id, String email, String login, String nick, boolean verified, boolean bot, long createdAt) {
			this(id, email, login, nick, verified, bot, createdAt, "everyone", "everyone", "everyone");
		}

		User(String id, String email, String login, String nick, boolean verified, boolean bot, long createdAt, String messagePrivacy, String callPrivacy) {
			this(id, email, login, nick, verified, bot, createdAt, messagePrivacy, callPrivacy, "everyone");
		}

		User(String id, String email, String login, String nick, boolean verified, boolean bot, long createdAt, String messagePrivacy, String callPrivacy, String invitePrivacy) {
			this(id, email, login, nick, verified, bot, createdAt, messagePrivacy, callPrivacy, invitePrivacy, "", "", 0, 0, new ArrayList<User>());
		}

		User(String id, String email, String login, String nick, boolean verified, boolean bot, long createdAt, String messagePrivacy, String callPrivacy, String invitePrivacy, String roomKind, String ownerId, int memberCount, int adminCount, List<User> memberUsers) {
			this.id = id == null ? "" : id;
			this.email = email == null ? "" : email;
			this.login = login == null ? "" : login;
			this.nick = nick == null ? "" : nick;
			this.verified = verified;
			this.bot = bot;
			this.createdAt = createdAt;
			this.messagePrivacy = normalizePrivacy(messagePrivacy);
			this.callPrivacy = normalizePrivacy(callPrivacy);
			this.invitePrivacy = normalizeInvitePrivacy(invitePrivacy);
			this.roomKind = roomKind == null ? "" : roomKind;
			this.ownerId = ownerId == null ? "" : ownerId;
			this.memberCount = Math.max(0, memberCount);
			this.adminCount = Math.max(0, adminCount);
			this.memberUsers = memberUsers == null ? new ArrayList<User>() : memberUsers;
		}
	}

	static final class BotCreation {
		final User user;
		final String token;

		BotCreation(User user, String token) {
			this.user = user;
			this.token = token;
		}
	}

	static final class Message {
		final long id;
		final String chatId;
		final User from;
		final User to;
			final String text;
			final long date;
			final long readAt;
			final FileInfo file;
			final ArrayList<Button> buttons;
			final boolean encrypted;
			final boolean system;
			final String data;

			Message(long id, String chatId, User from, User to, String text, long date, long readAt, FileInfo file, ArrayList<Button> buttons, boolean encrypted, boolean system, String data) {
				this.id = id;
				this.chatId = chatId;
				this.from = from;
				this.to = to;
				this.text = text;
				this.date = date;
				this.readAt = readAt;
				this.file = file;
				this.buttons = buttons == null ? new ArrayList<Button>() : buttons;
				this.encrypted = encrypted;
				this.system = system;
				this.data = data == null ? "" : data;
			}
	}

	static final class Button {
		final String text;
		final String url;
		final String callback;
		final long payDsr;

		Button(String text, String url, String callback, long payDsr) {
			this.text = text;
			this.url = url;
			this.callback = callback;
			this.payDsr = payDsr;
		}
	}

	static final class SessionInfo {
		final String id;
		final long createdAt;
		final long lastSeen;
		final String label;
		final boolean current;

		SessionInfo(String id, long createdAt, long lastSeen, String label, boolean current) {
			this.id = id == null ? "" : id;
			this.createdAt = createdAt;
			this.lastSeen = lastSeen;
			this.label = label == null ? "" : label;
			this.current = current;
		}
	}

	static final class FileInfo {
		final String id;
		final String name;
		final String mime;
		final long size;

		FileInfo(String id, String name, String mime, long size) {
			this.id = id;
			this.name = name;
			this.mime = mime;
			this.size = size;
		}
	}

	static final class WalletInfo {
		final long userId;
		final String currency;
		final String code;
		final long balance;
		final String receiveCode;
		final String instruction;

		WalletInfo(long userId, String currency, String code, long balance, String receiveCode, String instruction) {
			this.userId = userId;
			this.currency = currency;
			this.code = code;
			this.balance = balance;
			this.receiveCode = receiveCode;
			this.instruction = instruction;
		}
	}

	static final class WalletTransaction {
		final long id;
		final long fromUserId;
		final String fromLogin;
		final String fromNick;
		final long toUserId;
		final String toLogin;
		final String toNick;
		final long amount;
		final String comment;
		final long date;

		WalletTransaction(long id, long fromUserId, String fromLogin, String fromNick, long toUserId, String toLogin, String toNick, long amount, String comment, long date) {
			this.id = id;
			this.fromUserId = fromUserId;
			this.fromLogin = fromLogin == null ? "" : fromLogin;
			this.fromNick = fromNick == null ? "" : fromNick;
			this.toUserId = toUserId;
			this.toLogin = toLogin == null ? "" : toLogin;
			this.toNick = toNick == null ? "" : toNick;
			this.amount = amount;
			this.comment = comment == null ? "" : comment;
			this.date = date;
		}
	}

	static final class NodeStatus {
		final String type;
		final String name;
		final String status;
		final int available;
		final int total;

		NodeStatus(String type, String name, String status, int available, int total) {
			this.type = type == null ? "" : type;
			this.name = name == null ? "" : name;
			this.status = status == null ? "" : status;
			this.available = available;
			this.total = total;
		}
	}

	static final class Chat {
		final String id;
		final User peer;
		final Message last;
		final boolean banned;
		final boolean bannedByMe;
		final boolean bannedMe;

		Chat(String id, User peer, Message last, boolean banned) {
			this(id, peer, last, banned, banned, false);
		}

		Chat(String id, User peer, Message last, boolean banned, boolean bannedByMe, boolean bannedMe) {
			this.id = id;
			this.peer = peer;
			this.last = last;
			this.banned = banned;
			this.bannedByMe = bannedByMe;
			this.bannedMe = bannedMe;
		}
	}

	static final class HistoryPage {
		final User peer;
		final List<Message> messages;

		HistoryPage(User peer, List<Message> messages) {
			this.peer = peer;
			this.messages = messages;
		}
	}

	static final class Update {
		final long id;
		final String type;
		final Message message;
		final Call call;
		final User room;

		Update(long id, String type, Message message, Call call, User room) {
			this.id = id;
			this.type = type;
			this.message = message;
			this.call = call;
			this.room = room;
		}
	}

	static final class Call {
		final User from;
		final User to;
		final long date;

		Call(User from, User to, long date) {
			this.from = from;
			this.to = to;
			this.date = date;
		}
	}
}
