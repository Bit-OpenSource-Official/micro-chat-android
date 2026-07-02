package ru.e6atb.chat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

public final class MessageSyncService extends Service {
	private static final String ACTION_REJECT_CALL = "ru.e6atb.chat.REJECT_CALL";
	private static final String SYNC_CHANNEL = "sync";
	private static final String MESSAGE_CHANNEL = "messages";
	private static final String CALL_CHANNEL = "calls_visual";
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
			if (MainActivity.isForegroundPollingActive()) {
				failures = 0;
				sleepWhileRunning(5000);
				continue;
			}
			String server = SessionStore.server(this, MainActivity.DEFAULT_SERVER);
			String token = SessionStore.token(this);
			String login = SessionStore.login(this);
			MiniTaLib ta = new MiniTaLib(this, server, token, login);
			long after = SessionStore.backgroundLastUpdate(this);
			try {
				startForeground(FOREGROUND_ID, notification(
						SYNC_CHANNEL,
						getString(R.string.app_name),
						login.isEmpty() ? getString(R.string.status_online) : getString(R.string.status_online_as, login),
						true
				));
				List<MiniTaLib.Update> updates = ta.getUpdates(after, 30);
				long newestUpdate = after;
				for (MiniTaLib.Update u : updates) {
					if (u.id > newestUpdate) newestUpdate = u.id;
					MiniTaLib.Message m = u.message;
					if ("message".equals(u.type) && m != null) {
						String other = m.from.login.equals(login) ? m.to.login : m.from.login;
						if (!m.from.login.equals(login)) {
							showMessage(MESSAGE_BASE_ID + (int) (m.id % 100000), other, m.text);
						}
					} else if ("call_invite".equals(u.type) && u.call != null && u.call.from != null
							&& !u.call.from.login.equals(login) && !isStaleIncomingCall(u.call)) {
						showIncomingCall(u.call.from.login);
					} else if (u.type != null && u.type.startsWith("call_")) {
						NotificationManager nm = notificationManager();
						if (nm != null) {
							nm.cancel(CALL_NOTIFICATION_ID);
						}
					}
				}
				if (newestUpdate > after) {
					after = newestUpdate;
					SessionStore.backgroundLastUpdate(this, after);
				}
				failures = 0;
			} catch (Exception e) {
				if (MiniTaLib.isInvalidTokenError(e)) {
					SessionStore.clear(this);
					stopSelf();
					return;
				}
				sleepWhileRunning(pollRetryDelayMs(failures++));
			}
		}
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
				try {
					MiniTaLib ta = new MiniTaLib(
							MessageSyncService.this,
							SessionStore.server(MessageSyncService.this, MainActivity.DEFAULT_SERVER),
							SessionStore.token(MessageSyncService.this),
							SessionStore.login(MessageSyncService.this)
					);
					ta.sendCall(peer.trim(), "reject");
					NotificationManager nm = notificationManager();
					if (nm != null) {
						nm.cancel(CALL_NOTIFICATION_ID);
					}
				} catch (Exception ignored) {
				}
			}
		}, "e6atb-call-reject").start();
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
