package ru.e6atb.chat;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class OutboxStore {
	static final String PREPARING = "preparing";
	static final String QUEUED = "queued";
	static final String SENDING = "sending";
	static final String FAILED = "failed";
	static final String CANCEL_REQUESTED = "cancel_requested";
	static final long MAX_TOTAL_BYTES = 128L * 1024L * 1024L;

	private static Helper helper;

	private OutboxStore() {
	}

	interface Progress {
		void onProgress(String phase, long completed, long total);
	}

	static final class Attachment {
		String clientId;
		String fileId;
		String name;
		String mime;
		String localPath;
		String sourceUri;
		long size;

		MiniTaLib.FileInfo fileInfo() { return new MiniTaLib.FileInfo(safe(fileId), safe(name), safe(mime), size); }
	}

	static final class Entry {
		String account;
		String id;
		String peer;
		boolean room;
		String kind;
		String text;
		String name;
		String mime;
		String localPath;
		String sourceUri;
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
		ArrayList<Attachment> attachments = new ArrayList<Attachment>();
		transient int progressPercent;
		transient String progressPhase;

		MiniTaLib.Message localMessage(MiniTaLib.User me, MiniTaLib.User knownPeer) {
			MiniTaLib.User target = knownPeer;
			if (target == null) {
				target = room
						? new MiniTaLib.User(peer, "", peer, peer, false, false, 0, "everyone", "everyone", "everyone", "group", "", 0, 0, null)
						: new MiniTaLib.User("", "", peer, peer, false, false, 0);
			}
			ArrayList<MiniTaLib.FileInfo> media = new ArrayList<MiniTaLib.FileInfo>();
			for (Attachment attachment : attachments) media.add(attachment.fileInfo());
			if (media.isEmpty() && "file".equals(kind)) media.add(new MiniTaLib.FileInfo("", name, mime, size));
			long localId = -Math.max(1L, Math.abs((long)id.hashCode()));
			String previewPath = localPath != null && localPath.length() > 0 ? localPath : sourceUri;
			if (previewPath == null || previewPath.length() == 0) {
				for (Attachment attachment : attachments) {
					if (attachment.localPath != null && attachment.localPath.length() > 0) {
						previewPath = attachment.localPath;
						break;
					}
					if (attachment.sourceUri != null && attachment.sourceUri.length() > 0) {
						previewPath = attachment.sourceUri;
						break;
					}
				}
			}
			return new MiniTaLib.Message(
					localId, room ? "chat:" + peer : "", me, target,
					text, createdAt, 0, media, null,
					false, false, "", id, 0, state,
					previewPath,
					new ArrayList<MiniTaLib.Reaction>(), null, 0, commentPostId, 0,
					replyToMessageId, progressPercent, progressPhase
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
		insert(context, server, user, entry);
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
		insert(context, server, user, entry);
		return entry;
	}

	static String cachePeer(String peer, long postId) {
		return postId > 0 ? "comments:" + peer + ":" + postId : peer;
	}

	static synchronized Entry enqueueMedia(Context context, String server, String user, String peer,
	                                      boolean room, String text, List<Attachment> attachments,
	                                      String clientMessageId, long maxDsrAmount,
	                                      long commentPostId, long replyToMessageId,
	                                      String preparedBody) throws Exception {
		Entry entry = base(peer, room);
		entry.id = clientMessageId;
		entry.kind = "media";
		entry.text = text == null ? "" : text;
		entry.maxDsrAmount = Math.max(0, maxDsrAmount);
		entry.commentPostId = Math.max(0, commentPostId);
		entry.replyToMessageId = Math.max(0, replyToMessageId);
		entry.preparedBody = preparedBody == null ? "" : preparedBody;
		if (attachments != null) entry.attachments.addAll(attachments);
		long total = 0;
		for (Attachment attachment : entry.attachments) total += Math.max(0, attachment.size);
		for (Entry value : load(context, server, user)) total += Math.max(0, value.size);
		if (total > MAX_TOTAL_BYTES) throw new IllegalStateException("outbox is full (128 MiB)");
		entry.size = 0;
		for (Attachment attachment : entry.attachments) entry.size += Math.max(0, attachment.size);
		insert(context, server, user, entry);
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
		entry.sourceUri = "";
		entry.createdAt = System.currentTimeMillis() / 1000L;
		entry.state = QUEUED;
		entry.error = "";
		entry.preparedBody = "";
		entry.mediaReservationId = "";
		entry.progressPhase = "";
		return entry;
	}

	static synchronized List<Entry> load(Context context, String server, String user) {
		String account = account(server, user);
		migrateLegacy(context, server, user, account);
		ArrayList<Entry> out = new ArrayList<Entry>();
		Cursor cursor = db(context).query("outbox", null, "account=?", new String[]{account}, null, null, "created_at,id");
		try {
			while (cursor.moveToNext()) {
				Entry entry = fromCursor(cursor);
				loadAttachments(db(context), entry);
				out.add(entry);
			}
		} finally {
			cursor.close();
		}
		return out;
	}

	static synchronized List<String> peersReady(Context context, String server, String user, long now) {
		ArrayList<String> out = new ArrayList<String>();
		Set<String> seen = new HashSet<String>();
		for (Entry entry : load(context, server, user)) {
			if (FAILED.equals(entry.state) || CANCEL_REQUESTED.equals(entry.state)
					|| entry.retryAt > now || seen.contains(entry.peer)) continue;
			seen.add(entry.peer);
			out.add(entry.peer);
		}
		return out;
	}

	static synchronized Entry claimNext(Context context, String server, String user, String peer, long now) {
		for (Entry entry : load(context, server, user)) {
			if (!peer.equals(entry.peer)) continue;
			if (FAILED.equals(entry.state) || CANCEL_REQUESTED.equals(entry.state) || entry.retryAt > now) return null;
			entry.state = SENDING;
			update(context, server, user, entry);
			return entry;
		}
		return null;
	}

	static synchronized void update(Context context, String server, String user, Entry changed) {
		String account = account(server, user);
		changed.account = account;
		db(context).update("outbox", values(changed), "account=? AND id=?", new String[]{account, changed.id});
	}

	static synchronized Entry find(Context context, String server, String user, String id) {
		for (Entry entry : load(context, server, user)) if (entry.id.equals(id)) return entry;
		return null;
	}

	static synchronized boolean requestCancel(Context context, String server, String user, String id) {
		Entry entry = find(context, server, user, id);
		if (entry == null) return false;
		entry.state = CANCEL_REQUESTED;
		entry.retryAt = 0;
		update(context, server, user, entry);
		return true;
	}

	static synchronized void complete(Context context, String server, String user, String id) {
		Entry entry = find(context, server, user, id);
		if (entry != null) {
			deletePayload(entry);
			if (Build.VERSION.SDK_INT >= 19 && entry.sourceUri != null && entry.sourceUri.length() > 0) {
				try { context.getContentResolver().releasePersistableUriPermission(Uri.parse(entry.sourceUri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION); }
				catch (Exception ignored) {}
			}
			if (Build.VERSION.SDK_INT >= 19) for (Attachment attachment : entry.attachments) {
				if (attachment.sourceUri == null || attachment.sourceUri.length() == 0) continue;
				try { context.getContentResolver().releasePersistableUriPermission(Uri.parse(attachment.sourceUri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION); }
				catch (Exception ignored) {}
			}
		}
		db(context).delete("outbox_media", "account=? AND message_id=?", new String[]{account(server, user), id});
		db(context).delete("outbox", "account=? AND id=?", new String[]{account(server, user), id});
	}

	static synchronized void retry(Context context, String server, String user, String id) {
		Entry entry = find(context, server, user, id);
		if (entry == null) return;
		entry.state = QUEUED;
		entry.retryAt = 0;
		entry.error = "";
		update(context, server, user, entry);
	}

	static synchronized int count(Context context, String server, String user) {
		return load(context, server, user).size();
	}

	static synchronized void clear(Context context, String server, String user) {
		for (Entry entry : load(context, server, user)) deletePayload(entry);
		db(context).delete("outbox_media", "account=?", new String[]{account(server, user)});
		db(context).delete("outbox", "account=?", new String[]{account(server, user)});
	}

	static synchronized void removeChannelComments(Context context, String server, String user, String peer) {
		for (Entry entry : load(context, server, user)) {
			if (entry.commentPostId > 0 && peer.equals(entry.peer)) complete(context, server, user, entry.id);
		}
	}

	static File payloadFile(Context context, String id) {
		File dir = new File(context.getFilesDir(), "outbox-payloads");
		if (!dir.exists()) dir.mkdirs();
		return new File(dir, id + ".bin");
	}

	private static void insert(Context context, String server, String user, Entry entry) throws Exception {
		entry.account = account(server, user);
		migrateLegacy(context, server, user, entry.account);
		SQLiteDatabase database = db(context);
		database.beginTransaction();
		try {
			database.insertOrThrow("outbox", null, values(entry));
			insertAttachments(database, entry);
			database.setTransactionSuccessful();
		} finally { database.endTransaction(); }
	}

	private static void insertAttachments(SQLiteDatabase database, Entry entry) {
		for (int position = 0; position < entry.attachments.size(); position++) {
			Attachment attachment = entry.attachments.get(position);
			ContentValues value = new ContentValues();
			value.put("account", safe(entry.account));
			value.put("message_id", safe(entry.id));
			value.put("position", position);
			value.put("client_id", safe(attachment.clientId));
			value.put("file_id", safe(attachment.fileId));
			value.put("name", safe(attachment.name));
			value.put("mime", safe(attachment.mime));
			value.put("local_path", safe(attachment.localPath));
			value.put("source_uri", safe(attachment.sourceUri));
			value.put("size", attachment.size);
			database.insertOrThrow("outbox_media", null, value);
		}
	}

	private static void loadAttachments(SQLiteDatabase database, Entry entry) {
		Cursor cursor = database.query("outbox_media", null, "account=? AND message_id=?",
				new String[]{entry.account, entry.id}, null, null, "position");
		try {
			while (cursor.moveToNext()) {
				Attachment attachment = new Attachment();
				attachment.clientId = string(cursor, "client_id");
				attachment.fileId = string(cursor, "file_id");
				attachment.name = string(cursor, "name");
				attachment.mime = string(cursor, "mime");
				attachment.localPath = string(cursor, "local_path");
				attachment.sourceUri = string(cursor, "source_uri");
				attachment.size = number(cursor, "size");
				entry.attachments.add(attachment);
			}
		} finally { cursor.close(); }
	}

	private static ContentValues values(Entry entry) {
		ContentValues value = new ContentValues();
		value.put("account", safe(entry.account));
		value.put("id", safe(entry.id));
		value.put("peer", safe(entry.peer));
		value.put("room", entry.room ? 1 : 0);
		value.put("kind", safe(entry.kind));
		value.put("text", safe(entry.text));
		value.put("name", safe(entry.name));
		value.put("mime", safe(entry.mime));
		value.put("local_path", safe(entry.localPath));
		value.put("source_uri", safe(entry.sourceUri));
		value.put("size", entry.size);
		value.put("created_at", entry.createdAt);
		value.put("state", safe(entry.state));
		value.put("attempts", entry.attempts);
		value.put("retry_at", entry.retryAt);
		value.put("error", safe(entry.error));
		value.put("prepared_body", safe(entry.preparedBody));
		value.put("comment_post_id", entry.commentPostId);
		value.put("reply_to_message_id", entry.replyToMessageId);
		value.put("media_reservation_id", safe(entry.mediaReservationId));
		value.put("max_dsr_amount", entry.maxDsrAmount);
		return value;
	}

	private static Entry fromCursor(Cursor cursor) {
		Entry entry = new Entry();
		entry.account = string(cursor, "account");
		entry.id = string(cursor, "id");
		entry.peer = string(cursor, "peer");
		entry.room = number(cursor, "room") != 0;
		entry.kind = string(cursor, "kind");
		entry.text = string(cursor, "text");
		entry.name = string(cursor, "name");
		entry.mime = string(cursor, "mime");
		entry.localPath = string(cursor, "local_path");
		entry.sourceUri = string(cursor, "source_uri");
		entry.size = number(cursor, "size");
		entry.createdAt = number(cursor, "created_at");
		entry.state = string(cursor, "state");
		entry.attempts = (int)number(cursor, "attempts");
		entry.retryAt = number(cursor, "retry_at");
		entry.error = string(cursor, "error");
		entry.preparedBody = string(cursor, "prepared_body");
		entry.commentPostId = number(cursor, "comment_post_id");
		entry.replyToMessageId = number(cursor, "reply_to_message_id");
		entry.mediaReservationId = string(cursor, "media_reservation_id");
		entry.maxDsrAmount = number(cursor, "max_dsr_amount");
		entry.progressPhase = "";
		return entry;
	}

	private static String string(Cursor cursor, String column) {
		String value = cursor.getString(cursor.getColumnIndexOrThrow(column));
		return value == null ? "" : value;
	}

	private static long number(Cursor cursor, String column) {
		return cursor.getLong(cursor.getColumnIndexOrThrow(column));
	}

	private static String safe(String value) { return value == null ? "" : value; }

	private static SQLiteDatabase db(Context context) {
		if (helper == null) helper = new Helper(context.getApplicationContext());
		return helper.getWritableDatabase();
	}

	private static String account(String server, String user) {
		return digest(server + "\n" + user);
	}

	private static String digest(String value) {
		try {
			byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));
			StringBuilder out = new StringBuilder();
			for (byte item : bytes) out.append(String.format("%02x", item & 0xff));
			return out.toString();
		} catch (Exception error) {
			throw new IllegalStateException(error);
		}
	}

	private static void migrateLegacy(Context context, String server, String user, String account) {
		File legacy = new File(new File(context.getFilesDir(), "outbox"), account + ".json");
		if (!legacy.isFile()) return;
		SQLiteDatabase database = db(context);
		database.beginTransaction();
		try {
			JSONArray array = new JSONArray(read(legacy));
			for (int i = 0; i < array.length(); i++) {
				JSONObject raw = array.optJSONObject(i);
				if (raw == null) continue;
				Entry entry = fromJson(raw);
				entry.account = account;
				database.insertWithOnConflict("outbox", null, values(entry), SQLiteDatabase.CONFLICT_IGNORE);
				if ("file".equals(entry.kind) && entry.size > 0) {
					Attachment attachment = new Attachment();
					attachment.clientId = "attachment-000001";
					attachment.name = entry.name;
					attachment.mime = entry.mime;
					attachment.localPath = entry.localPath;
					attachment.sourceUri = entry.sourceUri;
					attachment.size = entry.size;
					entry.attachments.add(attachment);
					insertAttachments(database, entry);
				}
			}
			database.setTransactionSuccessful();
		} catch (Exception ignored) {
			return;
		} finally {
			database.endTransaction();
		}
		legacy.delete();
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
		entry.sourceUri = "";
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

	private static void deletePayload(Entry entry) {
		if (entry.localPath != null && entry.localPath.length() > 0) {
			try { new File(entry.localPath).delete(); } catch (Exception ignored) {}
		}
		for (Attachment attachment : entry.attachments) {
			if (attachment.localPath != null && attachment.localPath.length() > 0) {
				try { new File(attachment.localPath).delete(); } catch (Exception ignored) {}
			}
			if (Build.VERSION.SDK_INT >= 19 && attachment.sourceUri != null && attachment.sourceUri.length() > 0) {
				try { /* released by complete(Context,...) where a resolver is available */ } catch (Exception ignored) {}
			}
		}
	}

	private static String read(File file) throws Exception {
		FileInputStream input = new FileInputStream(file);
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int count;
			while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
			return new String(output.toByteArray(), "UTF-8");
		} finally { input.close(); }
	}

	private static final class Helper extends SQLiteOpenHelper {
		Helper(Context context) { super(context, "outbox-v2.db", null, 2); }

		@Override public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE outbox (account TEXT NOT NULL,id TEXT NOT NULL,peer TEXT NOT NULL,room INTEGER NOT NULL,kind TEXT NOT NULL,text TEXT NOT NULL,name TEXT NOT NULL,mime TEXT NOT NULL,local_path TEXT NOT NULL,source_uri TEXT NOT NULL,size INTEGER NOT NULL,created_at INTEGER NOT NULL,state TEXT NOT NULL,attempts INTEGER NOT NULL,retry_at INTEGER NOT NULL,error TEXT NOT NULL,prepared_body TEXT NOT NULL,comment_post_id INTEGER NOT NULL,reply_to_message_id INTEGER NOT NULL,media_reservation_id TEXT NOT NULL,max_dsr_amount INTEGER NOT NULL,PRIMARY KEY(account,id))");
			db.execSQL("CREATE INDEX outbox_ready ON outbox(account,peer,state,retry_at,created_at)");
			db.execSQL("CREATE TABLE outbox_media (account TEXT NOT NULL,message_id TEXT NOT NULL,position INTEGER NOT NULL,client_id TEXT NOT NULL,file_id TEXT NOT NULL,name TEXT NOT NULL,mime TEXT NOT NULL,local_path TEXT NOT NULL,source_uri TEXT NOT NULL,size INTEGER NOT NULL,PRIMARY KEY(account,message_id,position))");
			db.execSQL("CREATE INDEX outbox_media_message ON outbox_media(account,message_id,position)");
		}

		@Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			if (oldVersion < 2) {
				db.execSQL("CREATE TABLE IF NOT EXISTS outbox_media (account TEXT NOT NULL,message_id TEXT NOT NULL,position INTEGER NOT NULL,client_id TEXT NOT NULL,file_id TEXT NOT NULL,name TEXT NOT NULL,mime TEXT NOT NULL,local_path TEXT NOT NULL,source_uri TEXT NOT NULL,size INTEGER NOT NULL,PRIMARY KEY(account,message_id,position))");
				db.execSQL("CREATE INDEX IF NOT EXISTS outbox_media_message ON outbox_media(account,message_id,position)");
				db.execSQL("INSERT OR IGNORE INTO outbox_media(account,message_id,position,client_id,file_id,name,mime,local_path,source_uri,size) SELECT account,id,0,'attachment-000001','',name,mime,local_path,source_uri,size FROM outbox WHERE kind='file' AND size>0");
				db.execSQL("UPDATE outbox SET kind='media',text='' WHERE kind='file'");
			}
		}

		@Override public void onOpen(SQLiteDatabase db) {
			super.onOpen(db);
			ContentValues values = new ContentValues();
			values.put("state", QUEUED);
			db.update("outbox", values, "state=? OR state=?", new String[]{SENDING, PREPARING});
		}
	}
}
