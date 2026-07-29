package ru.e6atb.chat;

import android.content.Context;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class OutboxDispatcher {
	interface Listener {
		void onChanged(OutboxStore.Entry entry, MiniTaLib.Message sent);
	}

	private static final ExecutorService WORKERS = Executors.newFixedThreadPool(3);
	private static final Set<String> ACTIVE = new HashSet<String>();

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
				@Override
				public void run() {
					try {
						drainPeer(context, client, server, user, peer, listener);
					} finally {
						synchronized (ACTIVE) {
							ACTIVE.remove(key);
						}
					}
				}
			});
		}
	}

	private static void drainPeer(Context context, MiniTaLib client, String server, String user,
	                              String peer, Listener listener) {
		while (SessionStore.hasSession(context)) {
			OutboxStore.Entry entry = OutboxStore.claimNext(context, server, user, peer, System.currentTimeMillis());
			if (entry == null) return;
			notifyListener(listener, entry, null);
			try {
				MiniTaLib.Message sent;
				if ("file".equals(entry.kind)) {
					sent = client.uploadFile(entry.peer, entry.name, entry.mime, OutboxStore.payload(entry), entry.id).asOutgoing();
				} else {
					if (entry.preparedBody == null || entry.preparedBody.length() == 0) {
						JSONObject prepared = client.prepareMessage(entry.peer, entry.text, entry.id, entry.room);
						entry.preparedBody = prepared.toString();
						OutboxStore.update(context, server, user, entry);
					}
					sent = client.sendPreparedMessage(new JSONObject(entry.preparedBody)).asOutgoing();
				}
				ChatCache.appendMessage(context, server, SessionStore.login(context), entry.peer, sent);
				OutboxStore.complete(context, server, user, entry.id);
				notifyListener(listener, entry, sent);
			} catch (Throwable error) {
				entry.attempts++;
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
