package ru.e6atb.chat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONObject;

public final class MessageSyncService extends Service {
	public static final String ACTION_SYNC_UPDATED = "ru.e6atb.chat.SYNC_UPDATED";
	private static final String ACTION_REJECT_CALL = "ru.e6atb.chat.REJECT_CALL";
	private static final String SYNC_CHANNEL = "sync";
	private static final String MESSAGE_CHANNEL = "messages";
	private static final String CALL_CHANNEL = "calls_visual";
	private static final String AUTH_CHANNEL = "authorization";
	private static final int MAX_INCOMING_CALL_AGE_SEC = 120;
	private static final int FOREGROUND_ID = 1;
	public static final int MESSAGE_BASE_ID = 1000;
	public static final int CALL_NOTIFICATION_ID = 2;

	private volatile boolean running;
	private Thread worker;

	@Override
	public void onCreate() {
		super.onCreate();
		AppLocale.apply(this);
		createChannel();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null && ACTION_REJECT_CALL.equals(intent.getAction())) {
			String peer = intent.getStringExtra(MainActivity.EXTRA_PEER);
			rejectCall(peer);
			return START_STICKY;
		}
		startForeground(FOREGROUND_ID, notification(SYNC_CHANNEL, getString(R.string.app_name), getString(R.string.sync_waiting), true));
		if (!running) {
			running = true;
			worker = new Thread(new Runnable() {
				@Override
				public void run() {
					pollLoop();
				}
			}, "e6atb-sync");
			worker.start();
		}
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		running = false;
		if (worker != null) {
			worker.interrupt();
		}
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	private void pollLoop() {
		int failures = 0;
		while (running) {
			if (!SessionStore.hasSession(this)) {
				stopSelf();
				return;
			}
			String server = SessionStore.server(this, MainActivity.DEFAULT_SERVER);
			String endpoint = SessionStore.transportEndpoint(this, MainActivity.DEFAULT_SERVER);
			String token = SessionStore.token(this);
			String userId = SessionStore.userId(this);
			String login = SessionStore.login(this);
			MiniTaLib ta = new MiniTaLib(this, endpoint, token, userId, login);
			try {
				CrashReportDispatcher.dispatch(this, ta);
			} catch (Exception ignored) {
				// Keep the report on disk. The next pass retries it with the same idempotency key.
			}
			OutboxDispatcher.dispatch(this, ta, null);
			long after = unifiedCursor();
			try {
				startForeground(FOREGROUND_ID, notification(
						SYNC_CHANNEL,
						getString(R.string.app_name),
						login.isEmpty() ? getString(R.string.status_online) : getString(R.string.status_online_as, login),
						true
				));
				List<MiniTaLib.Update> updates = ta.getUpdates(after, 30);
				boolean notificationBootstrapComplete =
						SessionStore.notificationBootstrapComplete(this)
								|| after > 0 || SessionStore.lastUpdate(this) > 0;
				Set<Long> readMessageIds = readMessageIds(updates);
				long newestUpdate = after;
				for (MiniTaLib.Update u : updates) {
					if (u.id > newestUpdate) newestUpdate = u.id;
					MiniTaLib.Message m = u.message;
					if (("message".equals(u.type) || "channel_comment".equals(u.type) || "message_edit".equals(u.type) || "message_read".equals(u.type) || "message_reaction".equals(u.type)) && m != null) {
						boolean sentByMe = isOwnUser(m.from, userId, login);
						MiniTaLib.User otherUser = m.commentPostId > 0 ? m.to : m.to != null && m.to.roomKind != null && m.to.roomKind.length() > 0
								? m.to : (sentByMe ? m.to : m.from);
						String other = userAddress(otherUser);
						String historyPeer = m.commentPostId > 0 ? OutboxStore.cachePeer(other, m.commentPostId) : other;
						ChatCache.appendMessage(this, server, login, historyPeer, m);
						if (m.clientMessageId.length() > 0) {
							OutboxStore.complete(this, server, OutboxDispatcher.accountKey(this), m.clientMessageId);
						}
						if ("message".equals(u.type) && MessageNotificationPolicy.shouldNotify(
								notificationBootstrapComplete, sentByMe, m.id, m.readAt, readMessageIds)) {
							String oauthCode = oauthRequestCode(m);
							if (oauthCode.length() > 0) {
								showOAuthRequest(oauthCode, m);
							} else {
								showMessage(MESSAGE_BASE_ID + (int) (m.id % 100000), other, m.text);
							}
						} else if ("message_read".equals(u.type)) {
							cancelMessageNotification(other);
						}
					} else if ("call_invite".equals(u.type) && u.call != null && u.call.from != null
							&& !isOwnUser(u.call.from, userId, login) && !isStaleIncomingCall(u.call)) {
						showIncomingCall(userAddress(u.call.from));
					} else if (u.type != null && u.type.startsWith("call_")) {
						NotificationManager nm = notificationManager();
						if (nm != null) {
							nm.cancel(CALL_NOTIFICATION_ID);
						}
					} else if ("message_delete".equals(u.type) && m != null) {
						String other = m.commentPostId > 0 ? userAddress(m.to) : userAddress(isOwnUser(m.from, userId, login) ? m.to : m.from);
						ChatCache.deleteMessage(this, server, login,
								m.commentPostId > 0 ? OutboxStore.cachePeer(other, m.commentPostId) : other, m.id);
					} else if ("chat_update".equals(u.type) && u.room != null && !u.room.commentsEnabled) {
						ChatCache.deleteCommentThreads(this, server, login, userAddress(u.room));
						OutboxStore.removeChannelComments(this, server, OutboxDispatcher.accountKey(this), userAddress(u.room));
					}
				}
				SessionStore.backgroundLastUpdate(this, newestUpdate);
				SessionStore.lastUpdate(this, newestUpdate);
				if (newestUpdate > after) {
					after = newestUpdate;
					Intent updated = new Intent(ACTION_SYNC_UPDATED);
					updated.setPackage(getPackageName());
					updated.putExtra("cursor", after);
					sendBroadcast(updated);
				}
				if (!SessionStore.notificationBootstrapComplete(this)) {
					SessionStore.notificationBootstrapComplete(this, true);
				}
				failures = 0;
			} catch (Exception e) {
				if (MiniTaLib.isInvalidTokenError(e)) {
					ta.close();
					SessionStore.clear(this);
					stopSelf();
					return;
				}
				sleepWhileRunning(pollRetryDelayMs(failures++));
			}
			ta.close();
		}
	}

	private long unifiedCursor() {
		long foreground = SessionStore.lastUpdate(this);
		long background = SessionStore.backgroundLastUpdate(this);
		if (foreground > 0 && background > 0) return Math.min(foreground, background);
		return Math.max(foreground, background);
	}

	private static Set<Long> readMessageIds(List<MiniTaLib.Update> updates) {
		Set<Long> ids = new HashSet<Long>();
		if (updates == null) return ids;
		for (MiniTaLib.Update update : updates) {
			if (update == null || update.message == null) continue;
			if ("message_read".equals(update.type) || update.message.readAt > 0) {
				ids.add(update.message.id);
			}
		}
		return ids;
	}

	private static long pollRetryDelayMs(int failures) {
		long delay = 5000L << Math.min(failures, 4);
		return Math.min(delay, 60000L);
	}

	private void sleepWhileRunning(long ms) {
		long until = System.currentTimeMillis() + ms;
		while (running) {
			long remaining = until - System.currentTimeMillis();
			if (remaining <= 0) return;
			sleep(Math.min(remaining, 500L));
		}
	}

	private void rejectCall(final String peer) {
		if (peer == null || peer.trim().isEmpty() || !SessionStore.hasSession(this)) {
			return;
		}
		new Thread(new Runnable() {
			@Override
			public void run() {
				MiniTaLib ta = null;
				try {
					ta = new MiniTaLib(
							MessageSyncService.this,
							SessionStore.transportEndpoint(MessageSyncService.this, MainActivity.DEFAULT_SERVER),
							SessionStore.token(MessageSyncService.this),
							SessionStore.userId(MessageSyncService.this),
							SessionStore.login(MessageSyncService.this)
					);
					ta.sendCall(peer.trim(), "reject");
					NotificationManager nm = notificationManager();
					if (nm != null) {
						nm.cancel(CALL_NOTIFICATION_ID);
					}
				} catch (Exception ignored) {
				} finally {
					if (ta != null) ta.close();
				}
			}
		}, "e6atb-call-reject").start();
	}

	private static boolean isOwnUser(MiniTaLib.User user, String userId, String login) {
		if (user == null) return false;
		return userId != null && userId.length() > 0
				? userId.equals(user.id)
				: login != null && login.length() > 0 && login.equals(user.login);
	}

	private static String userAddress(MiniTaLib.User user) {
		if (user == null) return "";
		return user.login.length() > 0 ? user.login : user.id;
	}

	private void showMessage(int idPlaceholder, String from, String text) {
		NotificationManager nm = notificationManager();
		if (nm == null) {
			return;
		}
		int notifId = MESSAGE_BASE_ID + Math.abs(from.hashCode()) % 100000;
		Intent openChat = new Intent(this, MainActivity.class);
		openChat.putExtra(MainActivity.EXTRA_CHAT, from);
		int flags = pendingIntentFlags();
		PendingIntent pending = PendingIntent.getActivity(this, notifId, openChat, flags);
		Notification n = notification(MESSAGE_CHANNEL, from, text, false);
		n.contentIntent = pending;
		n.flags |= Notification.FLAG_AUTO_CANCEL;
		nm.notify(notifId, n);
	}

	private void cancelMessageNotification(String from) {
		if (from == null || from.length() == 0) return;
		NotificationManager nm = notificationManager();
		if (nm != null) nm.cancel(MESSAGE_BASE_ID + Math.abs(from.hashCode()) % 100000);
	}

	private String oauthRequestCode(MiniTaLib.Message message) {
		if (message == null || message.data == null || message.data.length() == 0) return "";
		try {
			JSONObject data = new JSONObject(message.data);
			if (!"oauth_request".equals(data.optString("kind"))) return "";
			return OAuthCodeParser.parse(data.optString("user_code"));
		} catch (Exception ignored) {
			return "";
		}
	}

	private void showOAuthRequest(String code, MiniTaLib.Message message) {
		NotificationManager nm = notificationManager();
		if (nm == null) return;
		String service = "Crypto Gateway";
		try {
			JSONObject data = new JSONObject(message.data);
			service = data.optString("client_name", service);
		} catch (Exception ignored) {
		}
		Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse("ovechat://authorize?user_code=" + code), this, MainActivity.class);
		open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		int notificationId = MESSAGE_BASE_ID + 900000 + Math.abs(code.hashCode()) % 90000;
		PendingIntent pending = PendingIntent.getActivity(this, notificationId, open, pendingIntentFlags());
		Notification n = notification(
				AUTH_CHANNEL,
				getString(R.string.oauth_authorize_title),
				service + " · " + code,
				false
		);
		n.contentIntent = pending;
		n.defaults |= Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE;
		setPriorityCompat(n);
		nm.notify(notificationId, n);
	}

	private void showIncomingCall(String from) {
		NotificationManager nm = notificationManager();
		if (nm == null) {
			return;
		}
		Intent openCall = new Intent(this, MainActivity.class);
		openCall.setAction(MainActivity.ACTION_OPEN_CALL);
		openCall.putExtra(MainActivity.EXTRA_PEER, from);
		openCall.putExtra(MainActivity.EXTRA_CALL, from);
		openCall.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		Intent accept = new Intent(this, MainActivity.class);
		accept.setAction(MainActivity.ACTION_ACCEPT_CALL);
		accept.putExtra(MainActivity.EXTRA_PEER, from);
		int flags = pendingIntentFlags();
		PendingIntent openPending = PendingIntent.getActivity(this, 9, openCall, flags);
		PendingIntent acceptPending = PendingIntent.getActivity(this, 10, accept, flags);
		Intent reject = new Intent(this, MessageSyncService.class);
		reject.setAction(ACTION_REJECT_CALL);
		reject.putExtra(MainActivity.EXTRA_PEER, from);
		PendingIntent rejectPending = PendingIntent.getService(this, 11, reject, flags);
		Notification n = notification(CALL_CHANNEL, getString(R.string.notification_incoming_call), from, true);
		n.contentIntent = openPending;
		n.defaults = 0;
		n.sound = null;
		n.vibrate = null;
		setPriorityCompat(n);
		setFullScreenIntentCompat(n, openPending);
		addActionCompat(n, android.R.drawable.ic_menu_call, getString(R.string.action_accept), acceptPending);
		addActionCompat(n, android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.action_decline), rejectPending);
		nm.notify(CALL_NOTIFICATION_ID, n);
	}

	private Notification notification(String channel, String title, String text, boolean ongoing) {
		Intent intent = new Intent(this, MainActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		int flags = pendingIntentFlags();
		PendingIntent pending = PendingIntent.getActivity(this, 0, intent, flags);
		Notification n;
		if (Build.VERSION.SDK_INT >= 11) {
			n = buildNotificationWithReflection(channel, title, text, pending, ongoing);
		} else {
			n = new Notification(android.R.drawable.ic_dialog_info, text, System.currentTimeMillis());
			setLatestEventInfoCompat(n, title, text, pending);
		}
		if (ongoing) {
			n.flags |= Notification.FLAG_ONGOING_EVENT;
		} else {
			n.flags |= Notification.FLAG_AUTO_CANCEL;
		}
		return n;
	}

	private Notification buildNotificationWithReflection(String channel, String title, String text, PendingIntent pending, boolean ongoing) {
		try {
			Class<?> builderClass = Class.forName("android.app.Notification$Builder");
			Object builder;
			if (Build.VERSION.SDK_INT >= 26) {
				Constructor<?> constructor = builderClass.getConstructor(android.content.Context.class, String.class);
				builder = constructor.newInstance(this, channel);
			} else {
				Constructor<?> constructor = builderClass.getConstructor(android.content.Context.class);
				builder = constructor.newInstance(this);
			}
			builderClass.getMethod("setSmallIcon", int.class).invoke(builder, android.R.drawable.ic_dialog_info);
			builderClass.getMethod("setContentTitle", CharSequence.class).invoke(builder, title);
			builderClass.getMethod("setContentText", CharSequence.class).invoke(builder, text);
			builderClass.getMethod("setContentIntent", PendingIntent.class).invoke(builder, pending);
			builderClass.getMethod("setOngoing", boolean.class).invoke(builder, ongoing);
			builderClass.getMethod("setAutoCancel", boolean.class).invoke(builder, !ongoing);
			if (Build.VERSION.SDK_INT >= 16) {
				return (Notification) builderClass.getMethod("build").invoke(builder);
			}
			return (Notification) builderClass.getMethod("getNotification").invoke(builder);
		} catch (Exception e) {
			Notification n = new Notification(android.R.drawable.ic_dialog_info, text, System.currentTimeMillis());
			setLatestEventInfoCompat(n, title, text, pending);
			return n;
		}
	}

	private void addActionCompat(Notification n, int icon, String title, PendingIntent pending) {
		if (Build.VERSION.SDK_INT < 16) {
			return;
		}
		try {
			Method method = Notification.class.getMethod("addAction", int.class, CharSequence.class, PendingIntent.class);
			method.invoke(n, icon, title, pending);
		} catch (Exception ignored) {
		}
	}

	private void setPriorityCompat(Notification n) {
		try {
			int high = Notification.class.getField("PRIORITY_HIGH").getInt(null);
			Notification.class.getField("priority").setInt(n, high);
		} catch (Exception ignored) {
		}
	}

	private void setFullScreenIntentCompat(Notification n, PendingIntent pending) {
		try {
			Notification.class.getField("fullScreenIntent").set(n, pending);
		} catch (Exception ignored) {
		}
	}

	private void setLatestEventInfoCompat(Notification n, String title, String text, PendingIntent pending) {
		try {
			Method method = Notification.class.getMethod("setLatestEventInfo", android.content.Context.class, CharSequence.class, CharSequence.class, PendingIntent.class);
			method.invoke(n, this, title, text, pending);
		} catch (Exception ignored) {
		}
	}

	private void createChannel() {
		if (Build.VERSION.SDK_INT < 26) {
			return;
		}
		try {
			NotificationManager nm = notificationManager();
			if (nm == null) {
				return;
			}
			Class<?> channelClass = Class.forName("android.app.NotificationChannel");
			Constructor<?> constructor = channelClass.getConstructor(String.class, CharSequence.class, int.class);
			Method method = NotificationManager.class.getMethod("createNotificationChannel", channelClass);
			int low = NotificationManager.class.getField("IMPORTANCE_LOW").getInt(null);
			int def = NotificationManager.class.getField("IMPORTANCE_DEFAULT").getInt(null);
			int high = NotificationManager.class.getField("IMPORTANCE_HIGH").getInt(null);
			method.invoke(nm, constructor.newInstance(SYNC_CHANNEL, getString(R.string.notification_channel_sync), low));
			method.invoke(nm, constructor.newInstance(MESSAGE_CHANNEL, getString(R.string.notification_channel_messages), def));
			method.invoke(nm, constructor.newInstance(AUTH_CHANNEL, getString(R.string.settings_authorization), high));
			Object callChannel = constructor.newInstance(CALL_CHANNEL, getString(R.string.notification_channel_calls), high);
			makeNotificationChannelSilent(channelClass, callChannel);
			method.invoke(nm, callChannel);
		} catch (Exception ignored) {
		}
	}

	private void makeNotificationChannelSilent(Class<?> channelClass, Object channel) {
		try {
			channelClass.getMethod("setSound", android.net.Uri.class, Class.forName("android.media.AudioAttributes")).invoke(channel, null, null);
		} catch (Exception ignored) {
		}
		try {
			channelClass.getMethod("enableVibration", boolean.class).invoke(channel, false);
		} catch (Exception ignored) {
		}
		try {
			channelClass.getMethod("setVibrationPattern", long[].class).invoke(channel, new Object[] { new long[0] });
		} catch (Exception ignored) {
		}
	}

	private int pendingIntentFlags() {
		int flags = PendingIntent.FLAG_UPDATE_CURRENT;
		if (Build.VERSION.SDK_INT >= 23) {
			try {
				flags |= PendingIntent.class.getField("FLAG_IMMUTABLE").getInt(null);
			} catch (Exception ignored) {
			}
		}
		return flags;
	}

	private NotificationManager notificationManager() {
		return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
	}

	private boolean isStaleIncomingCall(MiniTaLib.Call call) {
		if (call == null || call.date <= 0) return false;
		long age = System.currentTimeMillis() / 1000L - call.date;
		return age > MAX_INCOMING_CALL_AGE_SEC;
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}
}
