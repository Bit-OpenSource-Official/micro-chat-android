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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class OutboxStore {
	static final String QUEUED = "queued";
	static final String SENDING = "sending";
	static final String FAILED = "failed";
	static final long MAX_TOTAL_BYTES = 128L * 1024L * 1024L;

	private OutboxStore() {
	}

	static final class Entry {
		String id;
		String peer;
		boolean room;
		String kind;
		String text;
		String name;
		String mime;
		String localPath;
		long size;
		long createdAt;
		String state;
		int attempts;
		long retryAt;
		String error;
		String preparedBody;
		long commentPostId;
		long replyToMessageId;
		String mediaReservationId;
		long maxDsrAmount;

		MiniTaLib.Message localMessage(MiniTaLib.User me, MiniTaLib.User knownPeer) {
			MiniTaLib.User target = knownPeer;
			if (target == null) {
				target = room
						? new MiniTaLib.User(peer, "", peer, peer, false, false, 0, "everyone", "everyone", "everyone", "group", "", 0, 0, null)
						: new MiniTaLib.User("", "", peer, peer, false, false, 0);
			}
			MiniTaLib.FileInfo file = "file".equals(kind)
					? new MiniTaLib.FileInfo("", name, mime, size)
					: null;
			long localId = -Math.max(1L, Math.abs((long) id.hashCode()));
			return new MiniTaLib.Message(
					localId, room ? "chat:" + peer : "", me, target,
					"text".equals(kind) ? text : "", createdAt, 0, file, null,
					false, false, "", id, 0, state, localPath,
					new ArrayList<MiniTaLib.Reaction>(), null, 0, commentPostId, 0, replyToMessageId
			);
		}
	}

	static synchronized Entry enqueueText(Context context, String server, String user, String peer, boolean room, String text) throws Exception {
		return enqueueText(context, server, user, peer, room, text, 0);
	}

	static synchronized Entry enqueueText(Context context, String server, String user, String peer, boolean room, String text, long replyToMessageId) throws Exception {
		Entry entry = base(peer, room);
		entry.kind = "text";
		entry.text = text;
		entry.replyToMessageId = Math.max(0, replyToMessageId);
		List<Entry> entries = load(context, server, user);
		entries.add(entry);
		saveRequired(context, server, user, entries);
		return entry;
	}

	static synchronized Entry enqueueComment(Context context, String server, String user, String peer, long postId, String text) throws Exception {
		return enqueueComment(context, server, user, peer, postId, text, 0);
	}

	static synchronized Entry enqueueComment(Context context, String server, String user, String peer, long postId, String text, long replyToMessageId) throws Exception {
		Entry entry = base(peer, true);
		entry.kind = "text";
		entry.text = text;
		entry.commentPostId = postId;
		entry.replyToMessageId = Math.max(0, replyToMessageId);
		List<Entry> entries = load(context, server, user);
		entries.add(entry);
		saveRequired(context, server, user, entries);
		return entry;
	}

	static String cachePeer(String peer, long postId) {
		return postId > 0 ? "comments:" + peer + ":" + postId : peer;
	}

	static synchronized Entry enqueueFile(Context context, String server, String user, String peer, boolean room,
	                                      String name, String mime, byte[] data) throws Exception {
		return enqueueAuthorizedFile(context, server, user, peer, room, name, mime, data,
				UUID.randomUUID().toString(), "", 0, 0, 0);
	}

	static synchronized Entry enqueueAuthorizedFile(Context context, String server, String user, String peer, boolean room,
	                                                String name, String mime, byte[] data, String clientMessageId,
	                                                String mediaReservationId, long maxDsrAmount,
	                                                long commentPostId, long replyToMessageId) throws Exception {
		List<Entry> entries = load(context, server, user);
		long total = data.length;
		for (Entry value : entries) total += Math.max(0, value.size);
		if (total > MAX_TOTAL_BYTES) throw new IllegalStateException("outbox is full (128 MiB)");
		Entry entry = base(peer, room);
		entry.id = clientMessageId;
		entry.kind = "file";
		entry.name = name;
		entry.mime = mime;
		entry.size = data.length;
		entry.mediaReservationId = mediaReservationId == null ? "" : mediaReservationId;
		entry.maxDsrAmount = Math.max(0, maxDsrAmount);
		entry.commentPostId = Math.max(0, commentPostId);
		entry.replyToMessageId = Math.max(0, replyToMessageId);
		File payload = new File(payloadDir(context), entry.id + ".bin");
		writeBytes(payload, data);
		entry.localPath = payload.getAbsolutePath();
		entries.add(entry);
		try {
			saveRequired(context, server, user, entries);
		} catch (Exception error) {
			payload.delete();
			throw error;
		}
		return entry;
	}

	private static Entry base(String peer, boolean room) {
		Entry entry = new Entry();
		entry.id = UUID.randomUUID().toString();
		entry.peer = peer;
		entry.room = room;
		entry.text = "";
		entry.name = "";
		entry.mime = "";
		entry.localPath = "";
		entry.createdAt = System.currentTimeMillis() / 1000L;
		entry.state = QUEUED;
		entry.error = "";
		entry.preparedBody = "";
		entry.mediaReservationId = "";
		return entry;
	}

	static synchronized List<Entry> load(Context context, String server, String user) {
		ArrayList<Entry> out = new ArrayList<Entry>();
		try {
			JSONArray array = new JSONArray(read(file(context, server, user)));
			for (int i = 0; i < array.length(); i++) {
				JSONObject raw = array.optJSONObject(i);
				if (raw != null) out.add(fromJson(raw));
			}
		} catch (Exception ignored) {
		}
		return out;
	}

	static synchronized List<String> peersReady(Context context, String server, String user, long now) {
		ArrayList<String> out = new ArrayList<String>();
		Set<String> seen = new HashSet<String>();
		for (Entry entry : load(context, server, user)) {
			if (FAILED.equals(entry.state) || entry.retryAt > now || seen.contains(entry.peer)) continue;
			seen.add(entry.peer);
			out.add(entry.peer);
		}
		return out;
	}

	static synchronized Entry claimNext(Context context, String server, String user, String peer, long now) {
		List<Entry> entries = load(context, server, user);
		for (Entry entry : entries) {
			if (!peer.equals(entry.peer)) continue;
			if (FAILED.equals(entry.state) || entry.retryAt > now) return null;
			entry.state = SENDING;
			save(context, server, user, entries);
			return entry;
		}
		return null;
	}

	static synchronized void update(Context context, String server, String user, Entry changed) {
		List<Entry> entries = load(context, server, user);
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).id.equals(changed.id)) {
				entries.set(i, changed);
				save(context, server, user, entries);
				return;
			}
		}
	}

	static synchronized void complete(Context context, String server, String user, String id) {
		List<Entry> entries = load(context, server, user);
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			if (!entry.id.equals(id)) continue;
			deletePayload(entry);
			entries.remove(i);
			save(context, server, user, entries);
			return;
		}
	}

	static synchronized void retry(Context context, String server, String user, String id) {
		List<Entry> entries = load(context, server, user);
		for (Entry entry : entries) {
			if (!entry.id.equals(id)) continue;
			entry.state = QUEUED;
			entry.retryAt = 0;
			entry.error = "";
		}
		save(context, server, user, entries);
	}

	static synchronized int count(Context context, String server, String user) {
		return load(context, server, user).size();
	}

	static synchronized void clear(Context context, String server, String user) {
		for (Entry entry : load(context, server, user)) deletePayload(entry);
		save(context, server, user, new ArrayList<Entry>());
	}

	static synchronized void removeChannelComments(Context context, String server, String user, String peer) {
		List<Entry> entries = load(context, server, user);
		for (int i = entries.size() - 1; i >= 0; i--) {
			Entry entry = entries.get(i);
			if (entry.commentPostId > 0 && peer.equals(entry.peer)) {
				deletePayload(entry);
				entries.remove(i);
			}
		}
		save(context, server, user, entries);
	}

	static byte[] payload(Entry entry) throws Exception {
		FileInputStream input = new FileInputStream(entry.localPath);
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int count;
			while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
			return output.toByteArray();
		} finally {
			input.close();
		}
	}

	private static void deletePayload(Entry entry) {
		if (entry.localPath == null || entry.localPath.length() == 0) return;
		try {
			new File(entry.localPath).delete();
		} catch (Exception ignored) {
		}
	}

	private static void save(Context context, String server, String user, List<Entry> entries) {
		try {
			saveRequired(context, server, user, entries);
		} catch (Exception ignored) {
		}
	}

	private static void saveRequired(Context context, String server, String user, List<Entry> entries) throws Exception {
		JSONArray array = new JSONArray();
		for (Entry entry : entries) array.put(toJson(entry));
		write(file(context, server, user), array.toString());
	}

	private static JSONObject toJson(Entry entry) throws Exception {
		JSONObject raw = new JSONObject();
		raw.put("id", entry.id);
		raw.put("peer", entry.peer);
		raw.put("room", entry.room);
		raw.put("kind", entry.kind);
		raw.put("text", entry.text);
		raw.put("name", entry.name);
		raw.put("mime", entry.mime);
		raw.put("local_path", entry.localPath);
		raw.put("size", entry.size);
		raw.put("created_at", entry.createdAt);
		raw.put("state", entry.state);
		raw.put("attempts", entry.attempts);
		raw.put("retry_at", entry.retryAt);
		raw.put("error", entry.error);
		raw.put("prepared_body", entry.preparedBody);
		raw.put("comment_post_id", entry.commentPostId);
		raw.put("reply_to_message_id", entry.replyToMessageId);
		raw.put("media_reservation_id", entry.mediaReservationId);
		raw.put("max_dsr_amount", entry.maxDsrAmount);
		return raw;
	}

	private static Entry fromJson(JSONObject raw) {
		Entry entry = new Entry();
		entry.id = raw.optString("id");
		entry.peer = raw.optString("peer");
		entry.room = raw.optBoolean("room");
		entry.kind = raw.optString("kind");
		entry.text = raw.optString("text");
		entry.name = raw.optString("name");
		entry.mime = raw.optString("mime");
		entry.localPath = raw.optString("local_path");
		entry.size = raw.optLong("size");
		entry.createdAt = raw.optLong("created_at");
		entry.state = raw.optString("state", QUEUED);
		if (SENDING.equals(entry.state)) entry.state = QUEUED;
		entry.attempts = raw.optInt("attempts");
		entry.retryAt = raw.optLong("retry_at");
		entry.error = raw.optString("error");
		entry.preparedBody = raw.optString("prepared_body");
		entry.commentPostId = raw.optLong("comment_post_id");
		entry.replyToMessageId = raw.optLong("reply_to_message_id");
		entry.mediaReservationId = raw.optString("media_reservation_id");
		entry.maxDsrAmount = raw.optLong("max_dsr_amount");
		return entry;
	}

	private static File file(Context context, String server, String user) throws Exception {
		byte[] digest = MessageDigest.getInstance("SHA-256").digest((server + "\n" + user).getBytes("UTF-8"));
		StringBuilder name = new StringBuilder();
		for (byte value : digest) name.append(String.format("%02x", value & 0xff));
		File dir = new File(context.getFilesDir(), "outbox");
		if (!dir.exists()) dir.mkdirs();
		return new File(dir, name + ".json");
	}

	private static File payloadDir(Context context) {
		File dir = new File(context.getFilesDir(), "outbox-payloads");
		if (!dir.exists()) dir.mkdirs();
		return dir;
	}

	private static String read(File file) throws Exception {
		if (!file.isFile()) return "[]";
		FileInputStream input = new FileInputStream(file);
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

	private static void write(File file, String value) throws Exception {
		File temp = new File(file.getParentFile(), file.getName() + ".tmp");
		writeBytes(temp, value.getBytes("UTF-8"));
		if (!temp.renameTo(file)) {
			file.delete();
			if (!temp.renameTo(file)) throw new IllegalStateException("cannot save outbox");
		}
	}

	private static void writeBytes(File file, byte[] value) throws Exception {
		FileOutputStream output = new FileOutputStream(file);
		try {
			output.write(value);
			output.getFD().sync();
		} finally {
			output.close();
		}
	}
}
