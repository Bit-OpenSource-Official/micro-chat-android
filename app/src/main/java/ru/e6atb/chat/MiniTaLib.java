package ru.e6atb.chat;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;

import rs.ove.crypt.proto.CryptTcpClient;
import rs.ove.crypt.proto.Mst5MediaClient;
import rs.ove.crypt.proto.E2ECipher;
import rs.ove.crypt.proto.E2EKeyBackup;

import java.net.URI;
import java.net.URLEncoder;
import java.net.Socket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
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
	private String userId;
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
		this(context, baseUrl, token, "", login);
	}

	MiniTaLib(Context context, String baseUrl, String token, String userId, String login) {
		this.context = context == null ? null : context.getApplicationContext();
		this.baseUrl = trimSlash(baseUrl);
		this.token = token;
		this.userId = userId == null ? "" : userId;
		this.login = login == null ? "" : login;
		if (this.context != null && accountKey().length() > 0) {
			this.e2eIdentity = localE2EIdentity();
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
		tryActivateE2E(result, cloudPassword);
		return result;
	}

	User me() throws Exception {
		User result = user(get("/me", 10000).getJSONObject("user"));
		tryActivateE2E(result, null);
		return result;
	}

	OAuthDeviceRequest oauthDeviceRequest(String userCode) throws Exception {
		JSONObject out = get("/oauth/device/request?user_code=" + enc(userCode == null ? "" : userCode.trim()), 10000);
		return new OAuthDeviceRequest(
			out.optString("user_code"),
			out.optString("client_id"),
			out.optString("client_name"),
			out.optString("audience"),
			out.optString("action_description"),
			out.optLong("expires_at"),
			out.optString("status")
		);
	}

	void oauthDeviceDecision(String userCode, boolean approve) throws Exception {
		JSONObject body = new JSONObject();
		body.put("user_code", userCode == null ? "" : userCode.trim());
		body.put("decision", approve ? "approve" : "reject");
		post("/oauth/device/decision", body, 10000);
	}

	static final class OAuthDeviceRequest {
		final String userCode;
		final String clientID;
		final String clientName;
		final String audience;
		final String actionDescription;
		final long expiresAt;
		final String status;

		OAuthDeviceRequest(String userCode, String clientID, String clientName, String audience, String actionDescription, long expiresAt, String status) {
			this.userCode = userCode;
			this.clientID = clientID;
			this.clientName = clientName;
			this.audience = audience;
			this.actionDescription = actionDescription;
			this.expiresAt = expiresAt;
			this.status = status;
		}
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
		if (context != null && accountKey().length() > 0) {
			SessionStore.clearE2EIdentity(context, accountKey());
		}
		e2eIdentity = null;
		peerE2ESessions.clear();
		activateE2E(new User(userId, "", login, "", false, false), null);
	}

	User resetCloudPassword(String email, String code) throws Exception {
		JSONObject body = new JSONObject();
		body.put("email", email == null ? "" : email.trim());
		body.put("code", code == null ? "" : code.trim());
		JSONObject out = post("/cloud-password/reset", body, 10000);
		token = out.getString("token");
		User result = user(out.getJSONObject("user"));
		tryActivateE2E(result, null);
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
		tryActivateE2E(result, null);
		return result;
	}

	User setName(String name) throws Exception {
		JSONObject body = new JSONObject();
		body.put("name", name == null ? "" : name.trim());
		JSONObject out = post("/name", body, 10000);
		return user(out.getJSONObject("user"));
	}

	User setProfileDescription(String profile, String description) throws Exception {
		JSONObject body = new JSONObject();
		if (profile != null && profile.trim().length() > 0) body.put("profile", profile.trim());
		body.put("description", description == null ? "" : description.trim());
		JSONObject out = post("/profiles/description", body, 10000).getJSONObject("profile");
		return out.has("kind") ? roomUser(out) : user(out);
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

	Chat setChannelComments(String chat, boolean enabled) throws Exception {
		JSONObject body = new JSONObject();
		body.put("chat", chat == null ? "" : chat.trim());
		body.put("enabled", enabled);
		JSONObject out = post("/channels/comments/settings", body, 10000).getJSONObject("chat");
		User room = roomUser(out);
		return new Chat(room.id, room, null, false);
	}

	Message sendChannelComment(String chat, long postId, String text, String clientMessageId) throws Exception {
		return sendChannelComment(chat, postId, text, clientMessageId, 0);
	}

	Message sendChannelComment(String chat, long postId, String text, String clientMessageId, long replyToMessageId) throws Exception {
		JSONObject body = new JSONObject();
		body.put("chat", chat == null ? "" : chat.trim());
		body.put("post_id", postId);
		body.put("text", text == null ? "" : text.trim());
		body.put("client_message_id", clientMessageId == null ? "" : clientMessageId);
		if (replyToMessageId > 0) body.put("reply_to_message_id", replyToMessageId);
		return message(post("/channels/comments/send", body, 10000).getJSONObject("message"));
	}

	CommentPage getChannelComments(String chat, long postId, long before, int limit) throws Exception {
		String path = "/channels/comments?chat=" + enc(chat) + "&post_id=" + postId
				+ "&before=" + before + "&limit=" + limit;
		JSONObject out = get(path, 10000);
		JSONArray raw = out.getJSONArray("messages");
		ArrayList<Message> messages = new ArrayList<Message>(raw.length());
		for (int i = 0; i < raw.length(); i++) messages.add(message(raw.getJSONObject(i)));
		return new CommentPage(
				roomUser(out.optJSONObject("peer")),
				message(out.optJSONObject("post")),
				messages
		);
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
		return sendPreparedMessage(prepareMessage(to, text, null, false, 0));
	}

	JSONObject prepareMessage(String to, String text, String clientMessageId, boolean plain) throws Exception {
		return prepareMessage(to, text, clientMessageId, plain, 0);
	}

	JSONObject prepareMessage(String to, String text, String clientMessageId, boolean plain, long replyToMessageId) throws Exception {
		if (plain) {
			JSONObject body = new JSONObject();
			body.put("to", to);
			body.put("text", text);
			if (clientMessageId != null && clientMessageId.length() > 0) body.put("client_message_id", clientMessageId);
			if (replyToMessageId > 0) body.put("reply_to_message_id", replyToMessageId);
			return body;
		}
		if (e2eIdentity == null) {
			throw new SecurityException("E2E private key is unavailable on this device");
		}
		E2ECipher.Envelope envelope;
		try {
			PeerE2EKey peer = peerE2EKey(to);
			envelope = E2ECipher.sealV2(
					e2eSession(peer, accountAddress(), peer.user.id),
					accountAddress(),
					peer.user.id,
					text
			);
		} catch (RuntimeException e) {
			String message = e.getMessage();
			if (message == null
					|| (!message.contains("e2e public key not registered")
					&& !message.contains("user not found"))) {
				throw e;
			}
			return prepareMessage(to, text, clientMessageId, true, replyToMessageId);
		}
		JSONObject body = new JSONObject();
		body.put("to", to);
		if (clientMessageId != null && clientMessageId.length() > 0) body.put("client_message_id", clientMessageId);
		if (replyToMessageId > 0) body.put("reply_to_message_id", replyToMessageId);
		JSONObject e2e = new JSONObject();
		e2e.put("version", envelope.version);
		e2e.put("nonce", envelope.nonce);
		e2e.put("ciphertext", envelope.ciphertext);
		e2e.put("tag", envelope.tag);
		body.put("e2e", e2e);
		return body;
	}

	Message sendPreparedMessage(JSONObject body) throws Exception {
		return message(post("/send", body, 10000).getJSONObject("message"));
	}

	Message sendPlainMessage(String to, String text) throws Exception {
		return sendPreparedMessage(prepareMessage(to, text, null, true, 0));
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

	interface UploadSource {
		InputStream open() throws Exception;
	}

	static final class MessageMedia {
		final String clientId;
		final String fileId;
		final String name;
		final String mime;
		final long size;
		final UploadSource source;

		MessageMedia(String clientId, String fileId, String name, String mime, long size, UploadSource source) {
			this.clientId = clientId == null ? "" : clientId;
			this.fileId = fileId == null ? "" : fileId;
			this.name = name == null || name.length() == 0 ? "file" : name;
			this.mime = mime == null || mime.length() == 0 ? "application/octet-stream" : mime;
			this.size = size;
			this.source = source;
		}

		static MessageMedia existing(FileInfo file) {
			return new MessageMedia("", file == null ? "" : file.id, file == null ? "file" : file.name,
					file == null ? "application/octet-stream" : file.mime, file == null ? 0 : file.size, null);
		}
	}

	interface ProgressListener {
		void onProgress(long completed, long total);
	}

	static final class TransferControl {
		private volatile boolean cancelled;
		private volatile Socket socket;
		private volatile InputStream input;
		private final ProgressListener listener;

		TransferControl(ProgressListener listener) { this.listener = listener; }

		boolean isCancelled() { return cancelled; }

		void cancel() {
			cancelled = true;
			InputStream currentInput = input;
			if (currentInput != null) try { currentInput.close(); } catch (Exception ignored) {}
			Socket currentSocket = socket;
			if (currentSocket != null) try { currentSocket.close(); } catch (Exception ignored) {}
		}

		private void bind(Socket value, InputStream source) {
			socket = value;
			input = source;
			if (cancelled) cancel();
		}

		private void clear() { socket = null; input = null; }

		private void progress(long completed, long total) {
			if (listener != null) listener.onProgress(completed, total);
		}
	}

	MediaQuote quoteMedia(long sizeBytes) throws Exception {
		JSONObject body = new JSONObject();
		JSONArray media = new JSONArray();
		JSONObject item = new JSONObject();
		item.put("client_id", "quote-attachment-0001");
		item.put("name", "file");
		item.put("mime", "application/octet-stream");
		item.put("size", sizeBytes);
		media.put(item);
		body.put("media", media);
		JSONObject out = post("/media/quote", body, 10000);
		return new MediaQuote(out.optLong("size_bytes", sizeBytes), out.optLong("dsr_amount", out.optLong("dsr_required")), out.optBoolean("free"));
	}

	MediaQuote quoteMedia(List<MessageMedia> items) throws Exception {
		JSONObject body = new JSONObject();
		body.put("media", mediaRequest(items));
		long size = 0;
		if (items != null) for (MessageMedia item : items) if (item.fileId.length() == 0) size += item.size;
		JSONObject out = post("/media/quote", body, 10000);
		return new MediaQuote(out.optLong("size_bytes", size), out.optLong("dsr_amount", out.optLong("dsr_required")), out.optBoolean("free"));
	}

	Message sendMessageWithMedia(JSONObject preparedBody, List<MessageMedia> items,
	                            TransferControl transfer, long maxDsrAmount) throws Exception {
		JSONObject body = new JSONObject(preparedBody == null ? "{}" : preparedBody.toString());
		body.put("media", mediaRequest(items));
		body.put("max_dsr_amount", Math.max(0, maxDsrAmount));
		JSONObject prepared = post("/messages/prepare", body, 10000);
		if (prepared.optBoolean("complete") && prepared.optJSONObject("message") != null) {
			return message(prepared.getJSONObject("message"));
		}
		String operationId = prepared.getString("operation_id");
		JSONArray uploads = prepared.optJSONArray("uploads");
		long total = 0;
		if (items != null) for (MessageMedia item : items) if (item.fileId.length() == 0) total += item.size;
		long completed = 0;
		for (int i = 0; uploads != null && i < uploads.length(); i++) {
			JSONObject ticket = uploads.getJSONObject(i);
			String clientId = ticket.getString("client_id");
			MessageMedia source = null;
			if (items != null) for (MessageMedia item : items) if (item.clientId.equals(clientId)) { source = item; break; }
			if (source == null || source.source == null || ticket.optLong("size") != source.size) {
				throw new IOException("server returned an invalid media ticket");
			}
			directUpload(ticket.getString("endpoint"), ticket.getString("server_public_key"),
					ticket.getString("ticket"), ticket.getString("file_id"), source.size,
					source.source, transfer, completed, total);
			completed += source.size;
		}
		JSONObject complete = new JSONObject();
		complete.put("operation_id", operationId);
		return message(post("/messages/commit", complete, 30000).getJSONObject("message"));
	}

	JSONObject cancelMessageOperation(String clientMessageId) throws Exception {
		JSONObject body = new JSONObject();
		body.put("client_message_id", clientMessageId == null ? "" : clientMessageId);
		return post("/messages/cancel", body, 10000);
	}

	private static JSONArray mediaRequest(List<MessageMedia> items) throws Exception {
		JSONArray media = new JSONArray();
		if (items == null) return media;
		for (MessageMedia item : items) {
			JSONObject raw = new JSONObject();
			if (item.fileId.length() > 0) {
				raw.put("file_id", item.fileId);
			} else {
				raw.put("client_id", item.clientId);
				raw.put("name", item.name);
				raw.put("mime", item.mime);
				raw.put("size", item.size);
			}
			media.put(raw);
		}
		return media;
	}

	Message forwardMedia(long messageId, String to, String clientMessageId) throws Exception {
		JSONObject body = new JSONObject();
		body.put("message_id", messageId);
		body.put("to", to);
		if (clientMessageId != null && clientMessageId.length() > 0) body.put("client_message_id", clientMessageId);
		return message(post("/forward", body, 10000).getJSONObject("message"));
	}

	private static void directUpload(String endpoint, String serverPublicKey, String ticket, String fileId,
	                                 long size, UploadSource source,
	                                 TransferControl transfer, long progressBase, long progressTotal) throws Exception {
		InputStream input = null;
		try {
			input = source.open();
			if (input == null) throw new IOException("file is not available");
			final InputStream boundInput = input;
			Mst5MediaClient.upload(endpoint, serverPublicKey, ticket, fileId, size, input,
					transfer == null ? null : new Mst5MediaClient.Observer() {
						public boolean isCancelled() { return transfer.isCancelled(); }
						public void onConnected(Socket socket, InputStream ignored) { transfer.bind(socket, boundInput); }
						public void onProgress(long value, long ignored) { transfer.progress(progressBase + value, progressTotal); }
						public void onClosed() { transfer.clear(); }
					});
		} finally {
			if (input != null) try { input.close(); } catch (Exception ignored) {}
			if (transfer != null) transfer.clear();
		}
	}

	Message editMessage(long id, String peer, String text, boolean plain) throws Exception {
		JSONObject body = prepareMessage(peer, text, null, plain);
		body.remove("to");
		body.put("id", id);
		return message(post("/edit", body, 10000).getJSONObject("message"));
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

	byte[] downloadFileBytes(String fileID, int maxBytes) throws Exception {
		JSONObject ticket = get("/file/ticket?id=" + enc(fileID), 10000);
		long announced = ticket.optLong("size", -1);
		if (maxBytes > 0 && announced > maxBytes) throw new RuntimeException("file is too large");
		return Mst5MediaClient.downloadBytes(
				ticket.getString("endpoint"), ticket.getString("server_public_key"),
				ticket.getString("ticket"), ticket.getString("file_id"), announced, maxBytes);
	}

	void downloadFile(String fileID, File target, long maxBytes, ProgressListener listener) throws Exception {
		JSONObject ticket = get("/file/ticket?id=" + enc(fileID), 10000);
		long announced = ticket.optLong("size", -1);
		if (maxBytes > 0 && announced > maxBytes) throw new IOException("file is too large");
		File temporary = new File(target.getParentFile(), target.getName() + ".part");
		try {
			FileOutputStream output = new FileOutputStream(temporary);
			try {
				Mst5MediaClient.download(
						ticket.getString("endpoint"), ticket.getString("server_public_key"),
						ticket.getString("ticket"), ticket.getString("file_id"), announced, output,
						listener == null ? null : new Mst5MediaClient.Observer() {
							public boolean isCancelled() { return false; }
							public void onConnected(Socket socket, InputStream source) {}
							public void onProgress(long completed, long total) { listener.onProgress(completed, total); }
							public void onClosed() {}
						});
			} finally { output.close(); }
			if (!temporary.renameTo(target)) {
				target.delete();
				if (!temporary.renameTo(target)) throw new IOException("cannot save downloaded file");
			}
		} catch (Exception error) {
			temporary.delete();
			throw error;
		}
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

	Message reactMessage(long messageId, String emoji) throws Exception {
		JSONObject body = new JSONObject();
		body.put("message_id", messageId);
		body.put("emoji", emoji == null ? "" : emoji);
		return message(post("/reactions", body, 10000).getJSONObject("message"));
	}

	Message sendPaidReaction(long messageId, long amount, String idempotencyKey) throws Exception {
		JSONObject body = new JSONObject();
		body.put("message_id", messageId);
		body.put("amount", amount);
		body.put("idempotency_key", idempotencyKey);
		return message(post("/reactions/paid", body, 10000).getJSONObject("message"));
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
		return E2ECipher.fingerprint(peerE2EKey(peer).publicKey);
	}

	String ownE2EPublicKey() throws Exception {
		if (accountAddress().length() == 0) {
			return "";
		}
		return fetchE2EKey(accountAddress()).publicKey;
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

	static boolean isTransientError(Throwable error) {
		if (error instanceof ApiException) {
			int code = ((ApiException) error).code;
			return code == 408 || code == 429 || code >= 500;
		}
		Throwable value = error;
		while (value != null) {
			if (value instanceof IOException) return true;
			value = value.getCause();
		}
		return false;
	}

	private RuntimeException apiException(int code, String message) {
		String text = message == null || message.length() == 0 ? "TCP " + code : message;
		if (code == 401 && isCloudPasswordRequiredMessage(text)) {
			return new RuntimeException(text);
		}
		if (token().length() > 0 && (code == 401 || isInvalidTokenMessage(text))) {
			return new InvalidTokenException(text);
		}
		return new ApiException(code, text);
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
				o.optString("username", o.optString("login")),
				o.optString("name", o.optString("nick", o.optString("title"))),
				o.optBoolean("verified"),
				o.optBoolean("bot"),
				o.optLong("created_at"),
				o.optString("message_privacy", "everyone"),
				o.optString("call_privacy", "everyone"),
				o.optString("invite_privacy", "everyone"),
				o.optString("kind", o.optString("room_kind")),
				o.optString("owner_id"),
				o.optInt("members"),
				o.optInt("admins"),
				null,
				o.optBoolean("can_manage"),
				o.optBoolean("comments_enabled"),
				o.optString("description")
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
				memberUsers,
				o.optBoolean("can_manage"),
				o.optBoolean("comments_enabled"),
				o.optString("description")
		);
	}

	Message message(JSONObject o) {
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
				&& (o.optJSONArray("media") == null || o.optJSONArray("media").length() == 0)
				&& e2eIdentity != null
				&& from.id.length() > 0
				&& to.id.length() > 0) {
			try {
				String peer = from.id.equals(userId) ? to.id : from.id;
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
					media(o.optJSONArray("media")),
					buttons(o.optJSONArray("buttons")),
					encrypted,
					system,
					jsonObjectString(o.optJSONObject("data")),
					o.optString("client_message_id"),
					o.optLong("edited_at"),
					"sent",
					"",
					reactions(o.optJSONArray("reactions")),
					paidReaction(o.optJSONObject("paid_reaction")),
					o.optLong("reaction_version"),
					o.optLong("comment_post_id"),
					o.optInt("comments_count"),
					o.optLong("reply_to_message_id")
			);
	}

	private static ArrayList<Reaction> reactions(JSONArray raw) {
		ArrayList<Reaction> out = new ArrayList<Reaction>();
		if (raw == null) return out;
		for (int i = 0; i < raw.length(); i++) {
			JSONObject item = raw.optJSONObject(i);
			if (item == null) continue;
			String emoji = item.optString("emoji");
			long count = item.optLong("count");
			if (emoji.length() > 0 && count > 0) {
				out.add(new Reaction(emoji, count, item.optBoolean("mine")));
			}
		}
		return out;
	}

	private static ArrayList<FileInfo> media(JSONArray raw) {
		ArrayList<FileInfo> out = new ArrayList<FileInfo>();
		if (raw == null) return out;
		for (int i = 0; i < raw.length(); i++) {
			FileInfo item = file(raw.optJSONObject(i));
			if (item != null) out.add(item);
		}
		return out;
	}

	private static PaidReaction paidReaction(JSONObject raw) {
		if (raw == null || raw.optLong("amount") <= 0) return null;
		return new PaidReaction(raw.optLong("amount"), raw.optLong("mine_amount"));
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
			JSONArray row = raw.optJSONArray(i);
			if (row != null) {
				for (int j = 0; j < row.length(); j++) addButton(out, row.optJSONObject(j), i);
			} else {
				JSONObject item = raw.optJSONObject(i);
				addButton(out, item, item != null && item.has("row") ? item.optInt("row", i) : i);
			}
		}
		return out;
	}

	private static void addButton(ArrayList<Button> out, JSONObject item, int row) {
		if (item == null) return;
		out.add(new Button(
				item.optString("text"),
				item.optString("url"),
				item.optString("callback"),
				item.optLong("pay_dsr"),
				Math.max(0, row),
				"swipe".equals(item.optString("confirm")) || item.optBoolean("swipe_confirm")
		));
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

	private void activateE2E(User user, String password) throws Exception {
		if (user != null) {
			userId = user.id;
			login = user.login;
		}
		peerE2ESessions.clear();
		String key = accountKey();
		String address = accountAddress();
		if (context == null || key.length() == 0 || address.length() == 0) {
			return;
		}
		E2ECipher.Identity local = localE2EIdentity();
		String registered = "";
		try {
			registered = fetchE2EKey(address).publicKey;
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
						SessionStore.saveE2EIdentity(context, key, restored);
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
			local = SessionStore.createE2EIdentity(context, key);
		}
		JSONObject body = new JSONObject();
		body.put("public_key", local.publicKeyB64);
		post("/e2e/key", body, 10000);
		if (password != null) uploadE2EBackupAsync(local, password);
		e2eIdentity = local;
	}

	private void tryActivateE2E(User user, String password) {
		try {
			activateE2E(user, password);
		} catch (Exception ignored) {
			e2eIdentity = null;
		}
	}

	private String accountKey() {
		return userId == null || userId.length() == 0 ? (login == null ? "" : login) : userId;
	}

	private String accountAddress() {
		return accountKey();
	}

	private E2ECipher.Identity localE2EIdentity() {
		if (context == null || accountKey().length() == 0) return null;
		E2ECipher.Identity identity = SessionStore.e2eIdentity(context, accountKey());
		if (identity == null && userId != null && userId.length() > 0
				&& login != null && login.length() > 0 && !userId.equals(login)) {
			identity = SessionStore.e2eIdentity(context, login);
			if (identity != null) {
				SessionStore.saveE2EIdentity(context, userId, identity);
			}
		}
		return identity;
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
		String key = accountKey();
		String address = accountAddress();
		if (context == null || key.length() == 0 || address.length() == 0) {
			return null;
		}
		E2ECipher.Identity local = localE2EIdentity();
		String registered = "";
		try {
			registered = fetchE2EKey(address).publicKey;
		} catch (RuntimeException ex) {
			if (ex.getMessage() == null || !ex.getMessage().contains("not registered")) {
				throw ex;
			}
		}
		if (registered.length() == 0) {
			if (local == null) {
				local = SessionStore.createE2EIdentity(context, key);
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

	private PeerE2EKey fetchE2EKey(String address) throws Exception {
		JSONObject response = get("/e2e/key?user=" + enc(address), 10000);
		User user = user(response.getJSONObject("user"));
		if (user.id.length() == 0) {
			throw new IOException("e2e user id is unavailable");
		}
		return new PeerE2EKey(user, response.getString("public_key"));
	}

	private PeerE2EKey peerE2EKey(String peer) throws Exception {
		String normalized = peer == null ? "" : peer.trim().toLowerCase(Locale.US);
		PeerE2EKey result = fetchE2EKey(normalized);
		String stablePeer = result.user.id.toLowerCase(Locale.US);
		String cached = peerE2EKeys.get(stablePeer);
		String publicKey = result.publicKey;
		if (cached != null && !cached.equals(publicKey)) {
			synchronized (peerE2ESessions) {
				peerE2ESessions.clear();
			}
		}
		if (context != null) {
			if (SessionStore.pinPeerE2EKey(context, baseUrl, accountKey(), stablePeer, publicKey)) {
				synchronized (peerE2ESessions) {
					peerE2ESessions.clear();
				}
			}
		}
		peerE2EKeys.put(stablePeer, publicKey);
		return result;
	}

	private E2ECipher.Session e2eSession(PeerE2EKey peer, String from, String to) throws Exception {
		String cacheKey = peer.user.id + "\n" + from + "\n" + to;
		synchronized (peerE2ESessions) {
			E2ECipher.Session cached = peerE2ESessions.get(cacheKey);
			if (cached != null) {
				return cached;
			}
		}
		E2ECipher.Session created = E2ECipher.session(
				e2eIdentity, peer.publicKey, from, to
		);
		synchronized (peerE2ESessions) {
			peerE2ESessions.put(cacheKey, created);
		}
		return created;
	}

	private String decryptMessage(User from, User to, JSONObject raw) {
		if (e2eIdentity == null) {
			return "[encrypted: private key unavailable]";
		}
		try {
			E2ECipher.Envelope envelope = new E2ECipher.Envelope(
					raw.optInt("version"),
					raw.optString("nonce"),
					raw.optString("ciphertext"),
					raw.optString("tag")
			);
			boolean sentByMe = userId.length() > 0
					? userId.equals(from.id)
					: login.equals(from.login);
			User peerUser = sentByMe ? to : from;
			String peerAddress = peerUser.id.length() > 0 ? peerUser.id : peerUser.login;
			PeerE2EKey peer = peerE2EKey(peerAddress);
			String fromContext = envelope.version == 2 ? from.id : from.login;
			String toContext = envelope.version == 2 ? to.id : to.login;
			return E2ECipher.open(
					e2eSession(peer, fromContext, toContext),
					fromContext,
					toContext,
					envelope
			);
		} catch (Exception ex) {
			return "[encrypted: verification failed]";
		}
	}

	private static final class PeerE2EKey {
		final User user;
		final String publicKey;

		PeerE2EKey(User user, String publicKey) {
			this.user = user;
			this.publicKey = publicKey;
		}
	}

	static final class User {
		final String id;
		final String email;
		final String login;
		final String nick;
		final String description;
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
		final boolean canManage;
		final boolean commentsEnabled;

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
			this(id, email, login, nick, verified, bot, createdAt, messagePrivacy, callPrivacy, invitePrivacy, "", "", 0, 0, new ArrayList<User>(), false, false);
		}

		User(String id, String email, String login, String nick, boolean verified, boolean bot, long createdAt, String messagePrivacy, String callPrivacy, String invitePrivacy, String roomKind, String ownerId, int memberCount, int adminCount, List<User> memberUsers) {
			this(id, email, login, nick, verified, bot, createdAt, messagePrivacy, callPrivacy, invitePrivacy, roomKind, ownerId, memberCount, adminCount, memberUsers, false, false);
		}

		User(String id, String email, String login, String nick, boolean verified, boolean bot, long createdAt, String messagePrivacy, String callPrivacy, String invitePrivacy, String roomKind, String ownerId, int memberCount, int adminCount, List<User> memberUsers, boolean canManage, boolean commentsEnabled) {
			this(id, email, login, nick, verified, bot, createdAt, messagePrivacy, callPrivacy, invitePrivacy, roomKind, ownerId, memberCount, adminCount, memberUsers, canManage, commentsEnabled, "");
		}

		User(String id, String email, String login, String nick, boolean verified, boolean bot, long createdAt, String messagePrivacy, String callPrivacy, String invitePrivacy, String roomKind, String ownerId, int memberCount, int adminCount, List<User> memberUsers, boolean canManage, boolean commentsEnabled, String description) {
			this.id = id == null ? "" : id;
			this.email = email == null ? "" : email;
			this.login = login == null ? "" : login;
			this.nick = nick == null ? "" : nick;
			this.description = description == null ? "" : description;
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
			this.canManage = canManage;
			this.commentsEnabled = commentsEnabled;
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
			final ArrayList<FileInfo> media;
			final ArrayList<Button> buttons;
			final boolean encrypted;
			final boolean system;
			final String data;
			final String clientMessageId;
			final long editedAt;
			final String deliveryState;
			final String localFilePath;
			final ArrayList<Reaction> reactions;
			final PaidReaction paidReaction;
			final long reactionVersion;
			final long commentPostId;
			final int commentsCount;
			final long replyToMessageId;
			final int deliveryProgress;
			final String deliveryPhase;

			Message(long id, String chatId, User from, User to, String text, long date, long readAt, FileInfo file, ArrayList<Button> buttons, boolean encrypted, boolean system, String data) {
				this(id, chatId, from, to, text, date, readAt, file, buttons, encrypted, system, data, "", 0, "sent", "");
			}

			Message(long id, String chatId, User from, User to, String text, long date, long readAt, FileInfo file, ArrayList<Button> buttons, boolean encrypted, boolean system, String data, String clientMessageId, long editedAt, String deliveryState, String localFilePath) {
				this(id, chatId, from, to, text, date, readAt, file, buttons, encrypted, system,
						data, clientMessageId, editedAt, deliveryState, localFilePath,
						new ArrayList<Reaction>(), null, 0);
			}

			Message(long id, String chatId, User from, User to, String text, long date, long readAt, FileInfo file, ArrayList<Button> buttons, boolean encrypted, boolean system, String data, String clientMessageId, long editedAt, String deliveryState, String localFilePath, ArrayList<Reaction> reactions, PaidReaction paidReaction, long reactionVersion) {
				this(id, chatId, from, to, text, date, readAt, file, buttons, encrypted, system, data,
						clientMessageId, editedAt, deliveryState, localFilePath, reactions, paidReaction,
						reactionVersion, 0, 0);
			}

			Message(long id, String chatId, User from, User to, String text, long date, long readAt, FileInfo file, ArrayList<Button> buttons, boolean encrypted, boolean system, String data, String clientMessageId, long editedAt, String deliveryState, String localFilePath, ArrayList<Reaction> reactions, PaidReaction paidReaction, long reactionVersion, long commentPostId, int commentsCount) {
				this(id, chatId, from, to, text, date, readAt, file, buttons, encrypted, system, data,
						clientMessageId, editedAt, deliveryState, localFilePath, reactions, paidReaction,
						reactionVersion, commentPostId, commentsCount, 0);
			}

			Message(long id, String chatId, User from, User to, String text, long date, long readAt, FileInfo file, ArrayList<Button> buttons, boolean encrypted, boolean system, String data, String clientMessageId, long editedAt, String deliveryState, String localFilePath, ArrayList<Reaction> reactions, PaidReaction paidReaction, long reactionVersion, long commentPostId, int commentsCount, long replyToMessageId) {
				this(id, chatId, from, to, text, date, readAt, file, buttons, encrypted, system, data,
						clientMessageId, editedAt, deliveryState, localFilePath, reactions, paidReaction,
						reactionVersion, commentPostId, commentsCount, replyToMessageId, 0, "");
			}

			Message(long id, String chatId, User from, User to, String text, long date, long readAt, FileInfo file, ArrayList<Button> buttons, boolean encrypted, boolean system, String data, String clientMessageId, long editedAt, String deliveryState, String localFilePath, ArrayList<Reaction> reactions, PaidReaction paidReaction, long reactionVersion, long commentPostId, int commentsCount, long replyToMessageId, int deliveryProgress, String deliveryPhase) {
				this(id, chatId, from, to, text, date, readAt, singleMedia(file), buttons, encrypted, system,
						data, clientMessageId, editedAt, deliveryState, localFilePath, reactions, paidReaction,
						reactionVersion, commentPostId, commentsCount, replyToMessageId, deliveryProgress, deliveryPhase);
			}

			Message(long id, String chatId, User from, User to, String text, long date, long readAt, ArrayList<FileInfo> media, ArrayList<Button> buttons, boolean encrypted, boolean system, String data, String clientMessageId, long editedAt, String deliveryState, String localFilePath, ArrayList<Reaction> reactions, PaidReaction paidReaction, long reactionVersion, long commentPostId, int commentsCount, long replyToMessageId) {
				this(id, chatId, from, to, text, date, readAt, media, buttons, encrypted, system, data,
						clientMessageId, editedAt, deliveryState, localFilePath, reactions, paidReaction,
						reactionVersion, commentPostId, commentsCount, replyToMessageId, 0, "");
			}

			Message(long id, String chatId, User from, User to, String text, long date, long readAt, ArrayList<FileInfo> media, ArrayList<Button> buttons, boolean encrypted, boolean system, String data, String clientMessageId, long editedAt, String deliveryState, String localFilePath, ArrayList<Reaction> reactions, PaidReaction paidReaction, long reactionVersion, long commentPostId, int commentsCount, long replyToMessageId, int deliveryProgress, String deliveryPhase) {
				this.id = id;
				this.chatId = chatId;
				this.from = from;
				this.to = to;
				this.text = text;
				this.date = date;
				this.readAt = readAt;
				this.media = media == null ? new ArrayList<FileInfo>() : media;
				this.file = this.media.isEmpty() ? null : this.media.get(0);
				this.buttons = buttons == null ? new ArrayList<Button>() : buttons;
				this.encrypted = encrypted;
				this.system = system;
				this.data = data == null ? "" : data;
				this.clientMessageId = clientMessageId == null ? "" : clientMessageId;
				this.editedAt = editedAt;
				this.deliveryState = deliveryState == null ? "sent" : deliveryState;
				this.localFilePath = localFilePath == null ? "" : localFilePath;
				this.reactions = reactions == null ? new ArrayList<Reaction>() : reactions;
				this.paidReaction = paidReaction;
				this.reactionVersion = reactionVersion;
				this.commentPostId = commentPostId;
				this.commentsCount = Math.max(0, commentsCount);
				this.replyToMessageId = Math.max(0, replyToMessageId);
				this.deliveryProgress = Math.max(0, Math.min(100, deliveryProgress));
				this.deliveryPhase = deliveryPhase == null ? "" : deliveryPhase;
			}

			Message asOutgoing() {
				return new Message(id, chatId, from, to, text, date, readAt, media, buttons, encrypted, system,
						data, clientMessageId, editedAt, "sent-own", localFilePath, reactions, paidReaction,
						reactionVersion, commentPostId, commentsCount, replyToMessageId);
			}

			private static ArrayList<FileInfo> singleMedia(FileInfo file) {
				ArrayList<FileInfo> out = new ArrayList<FileInfo>();
				if (file != null) out.add(file);
				return out;
			}
		}

	static final class Reaction {
		final String emoji;
		final long count;
		final boolean mine;

		Reaction(String emoji, long count, boolean mine) {
			this.emoji = emoji == null ? "" : emoji;
			this.count = count;
			this.mine = mine;
		}
	}

	static final class PaidReaction {
		final long amount;
		final long mineAmount;

		PaidReaction(long amount, long mineAmount) {
			this.amount = amount;
			this.mineAmount = mineAmount;
		}
	}

	static final class MediaQuote {
		final long sizeBytes;
		final long dsrRequired;
		final boolean free;

		MediaQuote(long sizeBytes, long dsrRequired, boolean free) {
			this.sizeBytes = sizeBytes;
			this.dsrRequired = dsrRequired;
			this.free = free;
		}
	}

	static final class ApiException extends RuntimeException {
		final int code;
		ApiException(int code, String message) {
			super(message);
			this.code = code;
		}
	}

	static final class Button {
		final String text;
		final String url;
		final String callback;
		final long payDsr;
		final int row;
		final boolean swipeConfirm;

		Button(String text, String url, String callback, long payDsr, int row, boolean swipeConfirm) {
			this.text = text;
			this.url = url;
			this.callback = callback;
			this.payDsr = payDsr;
			this.row = Math.max(0, Math.min(11, row));
			this.swipeConfirm = swipeConfirm;
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

	static final class CommentPage {
		final User peer;
		final Message post;
		final List<Message> messages;

		CommentPage(User peer, Message post, List<Message> messages) {
			this.peer = peer;
			this.post = post;
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
