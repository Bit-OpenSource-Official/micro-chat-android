package ru.e6atb.chat;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

final class ChatCache {
	private ChatCache() {
	}

	static synchronized void saveChats(Context context, String server, String login, List<MiniTaLib.Chat> chats) {
		JSONArray array = new JSONArray();
		try {
			for (MiniTaLib.Chat chat : chats) array.put(chatToJSON(chat));
			write(context, key(server, login, "chats"), array.toString());
		} catch (Exception ignored) {
		}
	}

	static synchronized List<MiniTaLib.Chat> loadChats(Context context, String server, String login) {
		ArrayList<MiniTaLib.Chat> out = new ArrayList<MiniTaLib.Chat>();
		try {
			JSONArray array = new JSONArray(read(context, key(server, login, "chats")));
			for (int i = 0; i < array.length(); i++) out.add(chat(array.getJSONObject(i)));
		} catch (Exception ignored) {
		}
		return out;
	}

	static synchronized void saveHistory(Context context, String server, String login, String peer, List<MiniTaLib.Message> messages) {
		JSONArray array = new JSONArray();
		try {
			for (MiniTaLib.Message message : messages) array.put(messageToJSON(message));
			write(context, key(server, login, "history\n" + peer), array.toString());
		} catch (Exception ignored) {
		}
	}

	static synchronized List<MiniTaLib.Message> loadHistory(Context context, String server, String login, String peer) {
		ArrayList<MiniTaLib.Message> out = new ArrayList<MiniTaLib.Message>();
		try {
			JSONArray array = new JSONArray(read(context, key(server, login, "history\n" + peer)));
			for (int i = 0; i < array.length(); i++) out.add(message(array.getJSONObject(i)));
		} catch (Exception ignored) {
		}
		return out;
	}

	static synchronized void appendMessage(Context context, String server, String login, String peer, MiniTaLib.Message message) {
		List<MiniTaLib.Message> history = loadHistory(context, server, login, peer);
		boolean replaced = false;
		for (int i = 0; i < history.size(); i++) {
			if (history.get(i).id == message.id) {
				history.set(i, message);
				replaced = true;
				break;
			}
		}
		if (!replaced) {
			history.add(message);
			while (history.size() > 200) history.remove(0);
		}
		saveHistory(context, server, login, peer, history);
		upsertChat(context, server, login, peer, message);
	}

	static synchronized void deleteMessage(Context context, String server, String login, String peer, long messageID) {
		List<MiniTaLib.Message> history = loadHistory(context, server, login, peer);
		boolean removed = false;
		for (int i = 0; i < history.size(); i++) {
			if (history.get(i).id == messageID) {
				history.remove(i);
				removed = true;
				break;
			}
		}
		if (!removed) return;
		saveHistory(context, server, login, peer, history);
		List<MiniTaLib.Chat> chats = loadChats(context, server, login);
		for (int i = 0; i < chats.size(); i++) {
			if (!peerKey(chats.get(i).peer).equals(peer)) continue;
			boolean banned = chats.get(i).banned;
			chats.remove(i);
			if (!history.isEmpty()) {
				MiniTaLib.Message last = history.get(history.size() - 1);
				MiniTaLib.User other = messagePeerUser(last, login);
				chats.add(i, new MiniTaLib.Chat(last.chatId, other, last, banned));
			}
			break;
		}
		saveChats(context, server, login, chats);
	}

	static synchronized void deleteChat(Context context, String server, String login, String peer) {
		try {
			write(context, key(server, login, "history\n" + peer), "[]");
		} catch (Exception ignored) {
		}
		List<MiniTaLib.Chat> chats = loadChats(context, server, login);
		for (int i = chats.size() - 1; i >= 0; i--) {
			if (peerKey(chats.get(i).peer).equals(peer)) {
				chats.remove(i);
			}
		}
		saveChats(context, server, login, chats);
	}

	private static void upsertChat(Context context, String server, String login, String peer, MiniTaLib.Message message) {
		List<MiniTaLib.Chat> chats = loadChats(context, server, login);
		MiniTaLib.User other = messagePeerUser(message, login);
		boolean banned = false;
		for (int i = 0; i < chats.size(); i++) {
			if (peerKey(chats.get(i).peer).equals(peer)) {
				banned = chats.get(i).banned;
				chats.remove(i);
				break;
			}
		}
		MiniTaLib.Chat updated = new MiniTaLib.Chat(message.chatId, other, message, banned);
		chats.add(0, updated);
		saveChats(context, server, login, chats);
	}

	private static MiniTaLib.User messagePeerUser(MiniTaLib.Message message, String login) {
		if (message == null) return new MiniTaLib.User("", "", "", "", false, false, 0);
		if (message != null && message.to != null && message.to.roomKind != null && message.to.roomKind.length() > 0) {
			return message.to;
		}
		return message.from == null || message.from.login.equals(login) ? message.to : message.from;
	}

	private static String peerKey(MiniTaLib.User user) {
		if (user == null) return "";
		if (user.login != null && user.login.length() > 0) return user.login;
		return user.id == null ? "" : user.id;
	}

	private static JSONObject chatToJSON(MiniTaLib.Chat chat) throws Exception {
		JSONObject out = new JSONObject();
		out.put("id", chat.id);
		out.put("peer", userToJSON(chat.peer));
		if (chat.last != null) out.put("last", messageToJSON(chat.last));
		out.put("banned", chat.banned);
		out.put("banned_by_me", chat.bannedByMe);
		out.put("banned_me", chat.bannedMe);
		return out;
	}

	private static JSONObject messageToJSON(MiniTaLib.Message message) throws Exception {
		JSONObject out = new JSONObject();
		out.put("id", message.id);
		out.put("chat_id", message.chatId);
		out.put("from", userToJSON(message.from));
		out.put("to", userToJSON(message.to));
			out.put("text", message.text);
			out.put("date", message.date);
			out.put("read_at", message.readAt);
			out.put("encrypted", message.encrypted);
			out.put("system", message.system);
			if (message.data != null && message.data.length() > 0) out.put("data", new JSONObject(message.data));
		if (message.file != null) {
			JSONObject file = new JSONObject();
			file.put("id", message.file.id);
			file.put("name", message.file.name);
			file.put("mime", message.file.mime);
			file.put("size", message.file.size);
			out.put("file", file);
		}
		if (message.buttons != null && !message.buttons.isEmpty()) {
			JSONArray buttons = new JSONArray();
			for (MiniTaLib.Button button : message.buttons) {
				JSONObject raw = new JSONObject();
				raw.put("text", button.text);
				if (button.url != null && button.url.length() > 0) raw.put("url", button.url);
				if (button.callback != null && button.callback.length() > 0) raw.put("callback", button.callback);
				if (button.payDsr > 0) raw.put("pay_dsr", button.payDsr);
				buttons.put(raw);
			}
			out.put("buttons", buttons);
		}
		return out;
	}

	private static JSONObject userToJSON(MiniTaLib.User user) throws Exception {
		JSONObject out = new JSONObject();
		out.put("id", user.id);
		if (user.email != null && user.email.length() > 0) out.put("email", user.email);
		out.put("username", user.login);
		if (user.nick != null && user.nick.length() > 0) out.put("name", user.nick);
		out.put("verified", user.verified);
		out.put("bot", user.bot);
		if (user.createdAt > 0) out.put("created_at", user.createdAt);
		if (user.roomKind != null && user.roomKind.length() > 0) {
			out.put("room_kind", user.roomKind);
			out.put("owner_id", user.ownerId);
			out.put("members", user.memberCount);
			out.put("admins", user.adminCount);
			JSONArray members = new JSONArray();
			for (MiniTaLib.User member : user.memberUsers) {
				members.put(userToJSON(member));
			}
			out.put("member_users", members);
		}
		return out;
	}

	private static MiniTaLib.Chat chat(JSONObject raw) {
		JSONObject last = raw.optJSONObject("last");
		return new MiniTaLib.Chat(
				raw.optString("id"), user(raw.optJSONObject("peer")),
				last == null ? null : message(last),
				raw.optBoolean("banned"),
				raw.optBoolean("banned_by_me", raw.optBoolean("banned")),
				raw.optBoolean("banned_me", false)
		);
	}

	private static MiniTaLib.Message message(JSONObject raw) {
		JSONObject file = raw.optJSONObject("file");
		return new MiniTaLib.Message(
				raw.optLong("id"), raw.optString("chat_id"),
					user(raw.optJSONObject("from")), user(raw.optJSONObject("to")),
					raw.optString("text"), raw.optLong("date"),
					raw.optLong("read_at"),
					file == null ? null : new MiniTaLib.FileInfo(
							file.optString("id"), file.optString("name"),
							file.optString("mime"), file.optLong("size")
				),
				buttons(raw.optJSONArray("buttons")),
				raw.optBoolean("encrypted"),
				raw.optBoolean("system"),
				raw.optJSONObject("data") == null ? "" : raw.optJSONObject("data").toString()
		);
	}

	private static ArrayList<MiniTaLib.Button> buttons(JSONArray raw) {
		ArrayList<MiniTaLib.Button> out = new ArrayList<MiniTaLib.Button>();
		if (raw == null) return out;
		for (int i = 0; i < raw.length(); i++) {
			JSONObject item = raw.optJSONObject(i);
			if (item == null) continue;
			out.add(new MiniTaLib.Button(
					item.optString("text"),
					item.optString("url"),
					item.optString("callback"),
					item.optLong("pay_dsr")
			));
		}
		return out;
	}

	private static MiniTaLib.User user(JSONObject raw) {
		if (raw == null) return new MiniTaLib.User("", "", "", "", false, false, 0);
		JSONArray rawMembers = raw.optJSONArray("member_users");
		ArrayList<MiniTaLib.User> members = new ArrayList<MiniTaLib.User>();
		if (rawMembers != null) {
			for (int i = 0; i < rawMembers.length(); i++) {
				JSONObject item = rawMembers.optJSONObject(i);
				if (item != null) members.add(user(item));
			}
		}
		return new MiniTaLib.User(
				raw.optString("id"),
				raw.optString("email"),
				raw.optString("username", raw.optString("login")),
				raw.optString("name", raw.optString("nick")),
				raw.optBoolean("verified"),
				raw.optBoolean("bot"),
				raw.optLong("created_at"),
				"everyone",
				"everyone",
				"everyone",
				raw.optString("room_kind"),
				raw.optString("owner_id"),
				raw.optInt("members"),
				raw.optInt("admins"),
				members
		);
	}

	private static String key(String server, String login, String suffix) throws Exception {
		byte[] hash = MessageDigest.getInstance("SHA-256").digest(
				(server + "\n" + login + "\n" + suffix).getBytes("UTF-8")
		);
		StringBuilder out = new StringBuilder(hash.length * 2);
		for (byte value : hash) {
			int b = value & 0xff;
			if (b < 16) out.append('0');
			out.append(Integer.toHexString(b));
		}
		return out.toString() + ".json";
	}

	private static File cacheFile(Context context, String name) {
		File dir = new File(context.getFilesDir(), "chat-cache");
		if (!dir.exists()) dir.mkdirs();
		return new File(dir, name);
	}

	private static void write(Context context, String name, String value) throws Exception {
		File target = cacheFile(context, name);
		File temp = new File(target.getParentFile(), target.getName() + ".tmp");
		FileOutputStream output = new FileOutputStream(temp);
		try {
			output.write(value.getBytes("UTF-8"));
			output.flush();
		} finally {
			output.close();
		}
		if (!temp.renameTo(target)) {
			target.delete();
			temp.renameTo(target);
		}
	}

	private static String read(Context context, String name) throws Exception {
		FileInputStream input = new FileInputStream(cacheFile(context, name));
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int count;
			while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
			return new String(output.toByteArray(), "UTF-8");
		} finally {
			input.close();
		}
	}
}
