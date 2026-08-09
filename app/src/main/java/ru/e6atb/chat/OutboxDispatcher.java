package ru.e6atb.chat;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class OutboxDispatcher {
	interface Listener {
		void onChanged(OutboxStore.Entry entry, MiniTaLib.Message sent);
	}

	private static final ExecutorService WORKERS = Executors.newFixedThreadPool(3);
	private static final Set<String> ACTIVE = new HashSet<String>();
	private static final Map<String, MiniTaLib.TransferControl> TRANSFERS = new HashMap<String, MiniTaLib.TransferControl>();

	private OutboxDispatcher() {
	}

	static void dispatch(final Context rawContext, final MiniTaLib client, final Listener listener) {
		if (client == null || !SessionStore.hasSession(rawContext)) return;
		final Context context = rawContext.getApplicationContext();
		final String server = SessionStore.server(context, MainActivity.DEFAULT_SERVER);
		final String user = accountKey(context);
		List<String> peers = OutboxStore.peersReady(context, server, user, System.currentTimeMillis());
		for (final String peer : peers) {
			final String key = server + "\n" + user + "\n" + peer;
			synchronized (ACTIVE) {
				if (!ACTIVE.add(key)) continue;
			}
			WORKERS.execute(new Runnable() {
				@Override public void run() {
					try { drainPeer(context, client, server, user, peer, listener); }
					finally {
						synchronized (ACTIVE) { ACTIVE.remove(key); }
					}
				}
			});
		}
	}

	static void cancel(String clientMessageId) {
		synchronized (TRANSFERS) {
			MiniTaLib.TransferControl transfer = TRANSFERS.get(clientMessageId);
			if (transfer != null) transfer.cancel();
		}
	}

	private static void drainPeer(final Context context, MiniTaLib client, final String server,
	                              final String user, String peer, final Listener listener) {
		while (SessionStore.hasSession(context)) {
			final OutboxStore.Entry entry = OutboxStore.claimNext(context, server, user, peer, System.currentTimeMillis());
			if (entry == null) return;
			final long[] lastUpdate = {0L};
			final int[] lastPercent = {-1};
			MiniTaLib.TransferControl transfer = new MiniTaLib.TransferControl(new MiniTaLib.ProgressListener() {
				@Override public void onProgress(long completed, long total) {
					int percent = total <= 0 ? 0 : (int)Math.min(100, completed * 100L / total);
					long now = System.currentTimeMillis();
					if (percent == lastPercent[0] || (now - lastUpdate[0] < 100 && percent < 100)) return;
					lastPercent[0] = percent;
					lastUpdate[0] = now;
					entry.progressPhase = "sending";
					entry.progressPercent = percent;
					notifyListener(listener, entry, null);
				}
			});
			synchronized (TRANSFERS) { TRANSFERS.put(entry.id, transfer); }
			entry.progressPhase = "media".equals(entry.kind) || "file".equals(entry.kind) ? "sending" : "";
			entry.progressPercent = 0;
			notifyListener(listener, entry, null);
			try {
				MiniTaLib.Message sent;
				if ("media".equals(entry.kind) || "file".equals(entry.kind)) {
					if (entry.preparedBody == null || entry.preparedBody.length() == 0) {
						JSONObject prepared = client.prepareMessage(entry.peer, entry.text, entry.id, entry.room, entry.replyToMessageId);
						if (entry.commentPostId > 0) prepared.put("comment_post_id", entry.commentPostId);
						entry.preparedBody = prepared.toString();
						OutboxStore.update(context, server, user, entry);
					}
					ArrayList<MiniTaLib.MessageMedia> media = new ArrayList<MiniTaLib.MessageMedia>();
					for (final OutboxStore.Attachment attachment : entry.attachments) {
						MiniTaLib.UploadSource source = null;
						if (attachment.fileId == null || attachment.fileId.length() == 0) {
							source = new MiniTaLib.UploadSource() {
								@Override public InputStream open() throws Exception {
									if (attachment.localPath != null && attachment.localPath.length() > 0) return new FileInputStream(new File(attachment.localPath));
									if (attachment.sourceUri != null && attachment.sourceUri.length() > 0) return context.getContentResolver().openInputStream(Uri.parse(attachment.sourceUri));
									throw new IllegalStateException("media source is unavailable");
								}
							};
						}
						media.add(new MiniTaLib.MessageMedia(attachment.clientId, attachment.fileId, attachment.name,
								attachment.mime, attachment.size, source));
					}
					sent = client.sendMessageWithMedia(new JSONObject(entry.preparedBody), media, transfer,
							entry.maxDsrAmount).asOutgoing();
				} else if (entry.commentPostId > 0) {
					sent = client.sendChannelComment(entry.peer, entry.commentPostId, entry.text, entry.id, entry.replyToMessageId).asOutgoing();
				} else {
					if (entry.preparedBody == null || entry.preparedBody.length() == 0) {
						JSONObject prepared = client.prepareMessage(entry.peer, entry.text, entry.id, entry.room, entry.replyToMessageId);
						entry.preparedBody = prepared.toString();
						OutboxStore.update(context, server, user, entry);
					}
					sent = client.sendPreparedMessage(new JSONObject(entry.preparedBody)).asOutgoing();
				}
				ChatCache.appendMessage(context, server, SessionStore.login(context),
						OutboxStore.cachePeer(entry.peer, entry.commentPostId), sent);
				OutboxStore.complete(context, server, user, entry.id);
				notifyListener(listener, entry, sent);
			} catch (Throwable error) {
				if (transfer.isCancelled() || OutboxStore.find(context, server, user, entry.id) == null) return;
				entry.attempts++;
				entry.progressPhase = "";
				entry.progressPercent = 0;
				entry.error = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
				if (MiniTaLib.isInvalidTokenError(error)) {
					entry.state = OutboxStore.QUEUED;
					entry.retryAt = System.currentTimeMillis() + 5000L;
				} else if (MiniTaLib.isTransientError(error)) {
					entry.state = OutboxStore.QUEUED;
					long delay = Math.min(60000L, 1000L << Math.min(entry.attempts, 6));
					entry.retryAt = System.currentTimeMillis() + delay;
				} else {
					entry.state = OutboxStore.FAILED;
					entry.retryAt = 0;
				}
				OutboxStore.update(context, server, user, entry);
				notifyListener(listener, entry, null);
				return;
			} finally {
				synchronized (TRANSFERS) { TRANSFERS.remove(entry.id); }
			}
		}
	}

	private static void notifyListener(Listener listener, OutboxStore.Entry entry, MiniTaLib.Message sent) {
		if (listener != null) listener.onChanged(entry, sent);
	}

	static String accountKey(Context context) {
		String id = SessionStore.userId(context);
		return id == null || id.length() == 0 ? SessionStore.login(context) : id;
	}
}
