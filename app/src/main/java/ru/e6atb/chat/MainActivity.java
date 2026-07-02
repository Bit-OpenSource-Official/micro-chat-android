package ru.e6atb.chat;

import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;

import org.json.JSONObject;

// Added imports for image handling and permissions
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageView;

public final class MainActivity extends Activity {
	public static final String DEFAULT_SERVER = "danila.e6atb.ru:8080";
	// Updated to match new package name.
	public static final String ACTION_ACCEPT_CALL = "ru.e6atb.chat.ACCEPT_CALL";
	public static final String ACTION_OPEN_CALL = "ru.e6atb.chat.OPEN_CALL";
	public static final String EXTRA_PEER = "peer";
	public static final String EXTRA_CALL = "call_peer";
	// Extra used to open a specific chat from a notification.
	public static final String EXTRA_CHAT = "chat_peer";

	private static final int HISTORY_PAGE = 40;
	private static final int REQ_NOTIFICATIONS = 10;
	private static final int REQ_MICROPHONE = 11;
	// Permission request code for reading external storage (image picker)
	private static final int REQ_READ_STORAGE = 12;
	private static final int REQ_PICK_IMAGE = 13;
	// Generic file picker (any mime type)
	private static final int REQ_PICK_FILE = 14;
	private static final String CALL_NOTIFICATION_CHANNEL = "calls_visual";
	private static final int ACTIVE_CALL_NOTIFICATION_ID = 3;
	private static final int MAX_UPLOAD_BYTES = 12 * 1024 * 1024;
	private static final int MAX_IMAGE_PREVIEW_BYTES = 12 * 1024 * 1024;
	private static final int MAX_IMAGE_PREVIEW_PX = 1280;
	private static final int USERNAME_RESERVATION_FEE_DSR = 20;
	private static final int MAX_INCOMING_CALL_AGE_SEC = 120;
	private static final long EMAIL_CODE_RESEND_DELAY_MS = 5 * 60 * 1000L;
	private static final String PERMISSION_RECORD_AUDIO = "android.permission.RECORD_AUDIO";
	private static final String PERMISSION_READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";
	private static final String PERMISSION_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";
	private static final int LANGUAGE_SYSTEM_ID = 1001;
	private static final int LANGUAGE_ENGLISH_ID = 1002;
	private static final int LANGUAGE_RUSSIAN_ID = 1003;
	private static final int LANGUAGE_HEBREW_ID = 1004;
	private static final int MESSAGE_PRIVACY_EVERYONE_ID = 1101;
	private static final int MESSAGE_PRIVACY_CHATS_ID = 1102;
	private static final int MESSAGE_PRIVACY_NOBODY_ID = 1103;
	private static final int MESSAGE_PRIVACY_CONTACTS_ID = 1104;
	private static final int CALL_PRIVACY_EVERYONE_ID = 1201;
	private static final int CALL_PRIVACY_CHATS_ID = 1202;
	private static final int CALL_PRIVACY_NOBODY_ID = 1203;
	private static final int CALL_PRIVACY_CONTACTS_ID = 1204;
	private static final int INVITE_PRIVACY_EVERYONE_ID = 1301;
	private static final int INVITE_PRIVACY_CONTACTS_ID = 1302;
	private static final int INVITE_PRIVACY_NOBODY_ID = 1303;
	private static volatile boolean foregroundPollingActive;

	private enum Page {
		NONE,
		SERVER,
		LOGIN,
		CHATS,
		CHAT,
		ADD_CHAT,
		CALL,
		WALLET,
		WALLET_HISTORY,
		NODES,
		SETTINGS,
		SETTINGS_PROFILE,
		SETTINGS_SESSIONS,
		SETTINGS_CLOUD_PASSWORD,
		SETTINGS_E2E_KEYS,
		SETTINGS_DELETE_ACCOUNT,
		SETTINGS_LOGOUT,
		SETTINGS_CONTACTS,
		SETTINGS_PRIVACY,
		SETTINGS_SERVER,
		SETTINGS_LANGUAGE,
		SETTINGS_INTERFACE
	}

	private int pad;
	private int gap;
	private int buttonPadX;
	private int buttonPadY;
	private int buttonMinHeight;
	private int bg;
	private int surface;
	private int surfaceHi;
	private int textColor;
	private int muted;
	private int primary;
	private int onPrimary;

	private final Handler main = new Handler(Looper.getMainLooper());
	private final ExecutorService io = Executors.newFixedThreadPool(2);
	private final ExecutorService cacheIo = Executors.newSingleThreadExecutor();
	private final Set < Long > seenMessages = new HashSet < Long > ();
	private final Map < String, Bitmap > imagePreviewCache = new HashMap < String, Bitmap > ();
	private final Map < String, String > imagePreviewErrors = new HashMap < String, String > ();
	private final Set < String > imagePreviewLoading = new HashSet < String > ();
	private final ArrayList < MiniTaLib.Chat > chatData = new ArrayList < MiniTaLib.Chat > ();
	private final VoiceCall voiceCall = new VoiceCall();
	private LinearLayout rootView;
	private LinearLayout content;
	private LinearLayout bottomNav;
	private EditText serverUrl;
	private EditText email;
	private EditText accountUsername;
	private EditText accountName;
	private EditText emailCode;
	private EditText password;
	private EditText accountCloudPassword;
	private EditText accountCloudPasswordCode;
	private EditText accountDeleteCode;
	private EditText contactAddress;
	private EditText peer;
	private EditText text;
	private EditText roomTitle;
	private EditText roomUsername;
	private EditText roomMembers;
	private EditText walletTo;
	private EditText walletAmount;
	private EditText walletComment;
	private TextView status;
	private TextView walletBalanceView;
	private TextView walletReceiveView;
	private TextView walletInstructionView;
	private LinearLayout walletHistoryView;
	private boolean hasWalletBalance;
	private long walletBalance;
	private String walletCode = "DSR";
	private LinearLayout nodeStatusListView;
	private LinearLayout accountSessionsView;
	private LinearLayout contactsView;
	private TextView callStateView;
	private TextView callPeerView;
	private TextView callDurationView;
	private TextView callHintView;
	private TextView callParticipantsView;
	private LinearLayout chatInputContainer;
	private TextView cloudPasswordState;
	private LinearLayout currentPeerNameView;
	private ImageButton callButton;
	private Button callPrimaryAction;
	private Button callSecondaryAction;
	private Button callChatAction;
	private Button loginButton;
	private Button resendEmailCodeButton;
	private Button cloudPasswordSaveButton;
	private Button cloudPasswordClearButton;
	private Button deleteAccountCodeButton;
	private ImageButton sendButton;
	private Button chatsTab;
	private Button settingsTab;
	private CheckBox showStatusCheck;
	private CheckBox useInsetsCheck;
	private RadioGroup languageGroup;
	private RadioGroup messagePrivacyGroup;
	private RadioGroup callPrivacyGroup;
	private RadioGroup invitePrivacyGroup;
	// Custom adapters that support image rendering.
	private MessageAdapter chatRows;
	private MessageAdapter messageRows;
	private ListView messageList;
	private MiniTaLib ta;
	private volatile boolean polling;
	private volatile boolean activityResumed;
	private volatile int pollingGeneration;
	private volatile long lastUpdate;
	private String myID = "";
	private String myEmail = "";
	private String myLogin = "";
	private String myNick = "";
	private boolean myVerified;
	private boolean myBot;
	private String myMessagePrivacy = "everyone";
	private String myCallPrivacy = "everyone";
	private String myInvitePrivacy = "everyone";
	private String currentPeer = "";
	private MiniTaLib.User currentPeerUser;
	private boolean currentPeerBanned;
	private boolean currentPeerBannedByMe;
	private boolean currentPeerBannedMe;
	private String pendingAcceptedPeer = "";
	private String pendingOutgoingConnectPeer = "";
	private String pendingVoiceRoom = "";
	private Intent pendingSessionIntent;
	private String activeCallPeer = "";
	private boolean activeVoiceRoom;
	private long callStartedAtMs;
	private String callState = "idle";
	private volatile int voiceConnectGeneration;
	private final Runnable callClock = new Runnable() {
		@Override
		public void run() {
			updateCallDuration();
			updateActiveCallNotification();
			if (!"idle".equals(callState)) main.postDelayed(this, 1000);
		}
	};
	private final Runnable voiceParticipantsPoll = new Runnable() {
		@Override
		public void run() {
			if (activeVoiceRoom && !"idle".equals(callState)) {
				loadVoiceParticipants();
				main.postDelayed(this, 2000);
			}
		}
	};
	private Page page = Page.NONE;
	private long oldestMessage;
	private boolean historyLoaded;
	private boolean hasOlderMessages;
	private boolean loadingOlderMessages;
	private boolean waitingEmailCode;
	private boolean authNeedsCloudPassword;
	private String pendingEmailCode = "";
	private long emailCodeSentAtMs;
	private String emailCodeCooldownEmail = "";
	private final Runnable emailCodeCooldownTick = new Runnable() {
		@Override
		public void run() {
			updateEmailCodeCooldown();
			if (resendEmailCodeButton != null && emailCodeResendRemainingMs(currentEmailText()) > 0) {
				main.postDelayed(this, 1000);
			}
		}
	};

	@Override
	protected void onCreate(Bundle b) {
		super.onCreate(b);
		AppLocale.apply(this);
		initDimens();
		loadPalette();
		// Initialise the UI hierarchy before any view is accessed.
		setContentView(shell());
		setStatusBarColorCompat(bg);
		createCallNotificationChannel();
		requestNotifications();
		requestReadStoragePermission();
		restoreSession();
		handleIntent(getIntent());
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		handleIntent(intent);
	}

	@Override
	public void onBackPressed() {
		if (handleBackNavigation()) {
			return;
		}
		super.onBackPressed();
	}

	private boolean handleBackNavigation() {
		if (page == Page.CHAT || page == Page.ADD_CHAT || page == Page.SETTINGS) {
			showChats();
			return true;
		}
		if (isSettingsDetailPage()) {
			showSettings();
			return true;
		}
		if (page == Page.CALL) {
			String peerName = activeCallPeer.length() == 0 ? currentPeer : activeCallPeer;
			if (peerName.length() > 0) {
				currentPeer = peerName;
				showChat();
				loadHistory();
				return true;
			}
		}
		return false;
	}

	private boolean isSettingsDetailPage() {
		return page == Page.SETTINGS_PROFILE
				|| page == Page.SETTINGS_SESSIONS
				|| page == Page.SETTINGS_CLOUD_PASSWORD
				|| page == Page.SETTINGS_E2E_KEYS
				|| page == Page.SETTINGS_DELETE_ACCOUNT
				|| page == Page.SETTINGS_LOGOUT
				|| page == Page.SETTINGS_SERVER
				|| page == Page.SETTINGS_LANGUAGE
				|| page == Page.SETTINGS_INTERFACE;
	}

	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == REQ_MICROPHONE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			String outgoingPeer = pendingOutgoingConnectPeer;
			pendingOutgoingConnectPeer = "";
			if (outgoingPeer != null && !outgoingPeer.isEmpty()) {
				++voiceConnectGeneration;
				setCallState("connecting", outgoingPeer);
				startVoiceConnection(ta, outgoingPeer, getString(R.string.status_peer_accepted_call, outgoingPeer));
				return;
			}
			String voiceRoom = pendingVoiceRoom;
			pendingVoiceRoom = "";
			if (voiceRoom != null && !voiceRoom.isEmpty()) {
				currentPeer = voiceRoom;
				startGroupVoice();
				return;
			}
			String peerName = pendingAcceptedPeer;
			pendingAcceptedPeer = "";
			if (peerName != null && !peerName.isEmpty()) acceptIncomingCall(peerName);
		} else if (requestCode == REQ_MICROPHONE) {
			String deniedPeer = pendingOutgoingConnectPeer.length() > 0 ? pendingOutgoingConnectPeer : pendingAcceptedPeer;
			if ((deniedPeer == null || deniedPeer.length() == 0) && pendingVoiceRoom.length() > 0) deniedPeer = pendingVoiceRoom;
			pendingAcceptedPeer = "";
			pendingOutgoingConnectPeer = "";
			pendingVoiceRoom = "";
			clearIncomingCallUi();
			if (deniedPeer != null && deniedPeer.length() > 0) {
				setCallState("failed", deniedPeer);
			}
			status.setText(getString(R.string.status_microphone_denied));
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		activityResumed = true;
		if (ta != null) startPolling();
	}

	@Override
	protected void onPause() {
		activityResumed = false;
		stopPolling();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		stopPolling();
		main.removeCallbacks(emailCodeCooldownTick);
		cancelActiveCallNotification();
		dismissCallWindow();
		voiceCall.stop();
		io.shutdownNow();
		cacheIo.shutdownNow();
		super.onDestroy();
	}

	static boolean isForegroundPollingActive() {
		return foregroundPollingActive;
	}

	private LinearLayout shell() {
		LinearLayout root = new LinearLayout(this);
		rootView = root;
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(bg);
		applyRootPadding(root);
		installInsetsCompat(root);
		status = new TextView(this);
		status.setText(getString(R.string.status_offline));
		status.setTextColor(muted);
		status.setGravity(Gravity.CENTER_VERTICAL);
		status.setPadding(gap, gap, gap, gap);
		content = new LinearLayout(this);
		content.setOrientation(LinearLayout.VERTICAL);
		content.setPadding(0, gap, 0, 0);

		root.addView(spaced(status));
		updateStatusVisibility();
		root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
		bottomNav = navRow(
			iconButton(R.drawable.ic_nav_chats, getString(R.string.nav_chats), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showChats();
				}
			}),
			iconButton(R.drawable.ic_dastars, getString(R.string.nav_wallet), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showWallet();
				}
			}),
			iconButton(R.drawable.ic_nodes, getString(R.string.nav_nodes), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showNodeStatus();
				}
			}),
			iconButton(R.drawable.ic_nav_settings, getString(R.string.nav_settings), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showSettings();
				}
			})
		);
		bottomNav.setVisibility(View.GONE);
		root.addView(spaced(bottomNav));
		return root;
	}

	private void restoreSession() {
		if (!SessionStore.hasSession(this)) {
			showLogin();
			return;
		}
		final String url = SessionStore.server(this, DEFAULT_SERVER);
		final String token = SessionStore.token(this);
		myLogin = SessionStore.login(this);
		lastUpdate = SessionStore.lastUpdate(this);
		ta = new MiniTaLib(this, url, token, myLogin);
		final MiniTaLib c = ta;
		status.setText(getString(R.string.status_online));
		showChats();
		startPolling();
		startSyncService();
		run("session", new Task() {
			@Override
			public void run() throws Exception {
				MiniTaLib.User u = c.me();
				applyOwnUser(u);

				SessionStore.save(MainActivity.this, url, token, myLogin);

				ui(new Runnable() {
					@Override
					public void run() {
						status.setText(getString(R.string.status_online_as, displayOwnUser()));
						flushPendingSessionIntent();
					}
				});
			}
		});
	}

	private void showServer() {
		page = Page.SERVER;
		if (bottomNav != null) bottomNav.setVisibility(View.GONE);
		content.removeAllViews();
		serverUrl = serverInput();
		status.setText(getString(R.string.status_choose_server));
		content.addView(spaced(title(getString(R.string.screen_server))));
		content.addView(spaced(serverUrl));
		content.addView(spaced(row(primaryButton(getString(R.string.action_continue), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				SessionStore.saveServer(MainActivity.this, server());
				showLogin();
			}
		}))));
	}

	private void handleIntent(Intent intent) {
		if (intent == null) return;
		if (requiresSession(intent) && ta == null) {
			pendingSessionIntent = new Intent(intent);
			return;
		}
		if (ACTION_OPEN_CALL.equals(intent.getAction()) || intent.hasExtra(EXTRA_CALL)) {
			String peerName = intent.getStringExtra(EXTRA_PEER);
			if (peerName == null || peerName.trim().isEmpty()) {
				peerName = intent.getStringExtra(EXTRA_CALL);
			}
			if ((peerName == null || peerName.trim().isEmpty()) && !"idle".equals(callState) && activeCallPeer.length() > 0) {
				updateCallWindow();
				return;
			}
			if (peerName != null && !peerName.trim().isEmpty()) {
				openIncomingCall(peerName.trim());
				return;
			}
		}
		// Incoming call acceptance
		if (ACTION_ACCEPT_CALL.equals(intent.getAction())) {
			String peerName = intent.getStringExtra(EXTRA_PEER);
			if (peerName != null && !peerName.trim().isEmpty()) {
				acceptIncomingCall(peerName.trim());
				return;
			}
		}
		// Notification click to open a chat
		if (intent.hasExtra(EXTRA_CHAT)) {
			String chatPeer = intent.getStringExtra(EXTRA_CHAT);
			if (chatPeer != null && !chatPeer.isEmpty()) {
				openChatIfExists(chatPeer.trim());
			}
		}
	}

	private boolean requiresSession(Intent intent) {
		if (intent == null) return false;
		return ACTION_OPEN_CALL.equals(intent.getAction())
				|| ACTION_ACCEPT_CALL.equals(intent.getAction())
				|| intent.hasExtra(EXTRA_CALL)
				|| intent.hasExtra(EXTRA_CHAT);
	}

	private void flushPendingSessionIntent() {
		if (pendingSessionIntent == null || ta == null) return;
		Intent intent = pendingSessionIntent;
		pendingSessionIntent = null;
		handleIntent(intent);
	}

	private void showLogin() {
		page = Page.LOGIN;
		if (bottomNav != null) bottomNav.setVisibility(View.GONE);
		content.removeAllViews();
		main.removeCallbacks(emailCodeCooldownTick);
		resendEmailCodeButton = null;
		final String currentEmail = email == null ? "" : email.getText().toString().trim();
		final String currentCode = emailCode == null ? pendingEmailCode : emailCode.getText().toString().trim();
		final String currentPassword = password == null ? "" : password.getText().toString();
		email = input(getString(R.string.hint_email), false);
		email.setText(currentEmail);
		status.setText(getString(R.string.status_server, SessionStore.server(this, DEFAULT_SERVER)));
		content.addView(spaced(title(getString(R.string.screen_account))));
		content.addView(spaced(email));
		if (!waitingEmailCode) {
			loginButton = primaryButton(getString(R.string.action_next), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					requestEmailCode();
				}
			});
			LinearLayout authRow = row(loginButton);
			content.addView(spaced(authRow));
		} else {
			email.setEnabled(false);
			if (authNeedsCloudPassword) {
				emailCode = null;
				pendingEmailCode = currentCode;
				password = input(getString(R.string.hint_cloud_password), true);
				password.setText(currentPassword);
				content.addView(spaced(password));
				content.addView(spaced(label(getString(R.string.auth_reset_cloud_password_help))));
			} else {
				emailCode = input(getString(R.string.hint_email_code), false);
				emailCode.setText(currentCode);
				content.addView(spaced(emailCode));
				password = null;
			}
			loginButton = primaryButton(getString(R.string.action_login), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					authEmail();
				}
			});
			ImageButton back = headerIconButton(R.drawable.ic_back, getString(R.string.action_back), new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if (authNeedsCloudPassword) {
							authNeedsCloudPassword = false;
						} else {
							waitingEmailCode = false;
							pendingEmailCode = "";
						}
						showLogin();
					}
				});
			LinearLayout authRow = mixedRow(back, loginButton, true);
				content.addView(spaced(authRow));
				if (!authNeedsCloudPassword) {
					resendEmailCodeButton = button(getString(R.string.action_send_again), new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							requestEmailCode();
						}
					});
					updateEmailCodeCooldown();
					content.addView(spaced(row(
						resendEmailCodeButton
					)));
				}
				if (authNeedsCloudPassword) {
					PaymentSliderView resetSlider = new PaymentSliderView(this, getString(R.string.reset_cloud_password_slide_hint), true);
					resetSlider.setContentDescription(getString(R.string.reset_cloud_password_slide_hint));
					resetSlider.setOnConfirmAction(new Runnable() {
						@Override
						public void run() {
							resetAuthCloudPassword();
						}
					});
					content.addView(spaced(resetSlider), new LinearLayout.LayoutParams(-1, dp(56)));
				}
			}
			content.addView(spaced(row(
				button(getString(R.string.action_change_server), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					waitingEmailCode = false;
					authNeedsCloudPassword = false;
					pendingEmailCode = "";
					showServer();
				}
			})
		)));
	}

	private void showChats() {
		page = Page.CHATS;
		if (bottomNav != null) bottomNav.setVisibility(View.VISIBLE);
		content.removeAllViews();
		chatRows = adapter();
		ListView list = new ListView(this);
		list.setBackgroundColor(bg);
		list.setCacheColorHint(bg);
		styleList(list, false);
		list.setAdapter(chatRows);
		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView < ? > p, View v, int pos, long id) {
				MiniTaLib.Chat chat = chatData.get(pos);
				currentPeerUser = chat.peer;
				currentPeerBanned = chat.banned;
				currentPeerBannedByMe = chat.bannedByMe;
				currentPeerBannedMe = chat.bannedMe;
				currentPeer = currentPeerUser.login != null && currentPeerUser.login.length() > 0
						? currentPeerUser.login
						: currentPeerUser.id;
				showChat();
				loadHistory();
			}
		});
		loadCachedChats();
		content.addView(spaced(row(
			button(getString(R.string.action_refresh), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					loadChats(v, false);
				}
			})
		)));
		content.addView(list, fill());
		content.addView(spaced(row(
			primaryButton(getString(R.string.screen_add_chat), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showAddChat();
				}
			})
		)));
		loadChats();
	}

	private void showAddChat() {
		page = Page.ADD_CHAT;
		if (bottomNav != null) bottomNav.setVisibility(View.GONE);
		content.removeAllViews();
		final EditText loginField = input(getString(R.string.hint_username_or_id), false);
		roomTitle = input(getString(R.string.hint_room_title), false);
		roomUsername = input(getString(R.string.hint_channel_username), false);
		roomMembers = input(getString(R.string.hint_room_members), false);
		content.addView(spaced(title(getString(R.string.screen_add_chat))));
		content.addView(spaced(loginField));
		Button add = primaryButton(getString(R.string.action_add), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					String value = loginField.getText().toString().trim();
					if (value.length() == 0) return;
					openChatIfExists(value, v, true);
				}
			});
		ImageButton back = headerIconButton(R.drawable.ic_back, getString(R.string.action_back), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showChats();
				}
			});
		content.addView(spaced(mixedRow(add, back, false)));
		content.addView(spaced(title(getString(R.string.screen_new_room))));
		content.addView(spaced(roomTitle));
		content.addView(spaced(roomUsername));
		content.addView(spaced(roomMembers));
		content.addView(spaced(row(
			primaryButton(getString(R.string.action_create_group), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					createRoom(false, v, true);
				}
			}),
			button(getString(R.string.action_create_channel), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					createRoom(true, v, false);
				}
			})
		)));
	}

	private void showChat() {
		page = Page.CHAT;
		// Remove any pending notification for this chat so the user sees a clean UI.
		if (currentPeer != null && !currentPeer.isEmpty()) cancelMessageNotification(currentPeer);
		if (bottomNav != null) bottomNav.setVisibility(View.GONE);
		content.removeAllViews();
		peer = input(getString(R.string.hint_username_or_id), false);
		peer.setText(currentPeer);
		text = input(getString(R.string.hint_message), false);
		text.setSingleLine(false);
		text.setMinLines(1);
		text.setMaxLines(3);
		text.setImeOptions(EditorInfo.IME_ACTION_SEND);
		text.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int action, KeyEvent e) {
				if (action == EditorInfo.IME_ACTION_SEND) {
					send();
					return true;
				}

				return false;
			}
		});

		messageRows = adapter();
		messageList = new ListView(this);
		messageList.setBackgroundColor(bg);
		messageList.setCacheColorHint(bg);
		styleList(messageList, true);
		messageList.setAdapter(messageRows);
		messageList.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
		messageList.setOnScrollListener(new AbsListView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
				if (historyLoaded && hasOlderMessages && firstVisibleItem == 0 && visibleItemCount > 0) {
					loadOlderHistory();
				}
			}
		});

		callButton = headerIconButton(R.drawable.ic_call, getString(R.string.action_call), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				toggleVoice();
			}
		});
		callButton.setBackgroundDrawable(pressable(primary, blend(primary, Color.WHITE, 0.18f), 0, buttonRadius()));
		updateCallButton();
		content.addView(spaced(chatHeader()));
		content.addView(messageList, fill());
		chatInputContainer = new LinearLayout(this);
		chatInputContainer.setOrientation(LinearLayout.VERTICAL);
		content.addView(spaced(chatInputContainer));
		refreshChatInput();
	}

	private void showE2EFingerprint() {
		if (currentPeerIsRoom()) {
			status.setText(getString(R.string.status_e2e_not_available_for_rooms));
			return;
		}
		final MiniTaLib c = ta;
		final String peerName = currentPeer;
		if (c == null || peerName == null || peerName.length() == 0) return;
		run("e2e_fingerprint", new Task() {
			@Override
			public void run() throws Exception {
				final String fingerprint = c.e2eFingerprint(peerName);
				ui(new Runnable() {
					@Override
					public void run() {
						showInfoDialog(getString(R.string.dialog_e2e_title, peerName), getString(R.string.dialog_e2e_body, fingerprint));
					}
				});
			}
		});
	}

	private void openChatIfExists(final String peerName) {
		openChatIfExists(peerName, null, false);
	}

	private void openChatIfExists(final String peerName, final View actionButton, final boolean primaryStyle) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		status.setText(getString(R.string.loading));
		runButtonTask("open_chat", actionButton, primaryStyle, new Task() {
			@Override
			public void run() throws Exception {
				final MiniTaLib.HistoryPage pageData = c.getHistoryPageBefore(peerName, 0, HISTORY_PAGE);
				try {
					c.markRead(peerName);
				} catch (Exception ignored) {
				}
				final String resolvedPeer = resolvedPeerName(pageData.peer, peerName);
				cacheSaveHistory(resolvedPeer, pageData.messages);
				ui(new Runnable() {
					@Override
					public void run() {
						currentPeer = resolvedPeer;
						currentPeerUser = pageData.peer;
						currentPeerBanned = false;
						currentPeerBannedByMe = false;
						currentPeerBannedMe = false;
						showChat();
						renderHistory(pageData.messages, resolvedPeer, false);
					}
				});
			}
		});
	}

	private String resolvedPeerName(MiniTaLib.User user, String fallback) {
		if (user != null && user.login != null && user.login.length() > 0) return user.login;
		if (user != null && user.id != null && user.id.length() > 0) return user.id;
		return fallback == null ? "" : fallback;
	}

	private void createRoom(final boolean channel) {
		createRoom(channel, null, false);
	}

	private void createRoom(final boolean channel, final View actionButton, final boolean primaryStyle) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		final String titleValue = roomTitle == null ? "" : roomTitle.getText().toString().trim();
		final String usernameValue = roomUsername == null ? "" : roomUsername.getText().toString().trim();
		final ArrayList<String> members = splitMembers(roomMembers == null ? "" : roomMembers.getText().toString());
		if (titleValue.length() == 0) return;
		if (channel && usernameValue.length() > 0) {
			showUsernameReservationPaymentSheet(
				usernameValue,
				getString(R.string.username_reservation_payment_details_channel, titleValue),
				new Runnable() {
					@Override
					public void run() {
						createRoomConfirmed(true, titleValue, usernameValue, members);
					}
				}
			);
			return;
		}
		createRoomConfirmed(channel, titleValue, usernameValue, members, actionButton, primaryStyle);
	}

	private void createRoomConfirmed(final boolean channel, final String titleValue, final String usernameValue, final ArrayList<String> members) {
		createRoomConfirmed(channel, titleValue, usernameValue, members, null, false);
	}

	private void createRoomConfirmed(final boolean channel, final String titleValue, final String usernameValue, final ArrayList<String> members, final View actionButton, final boolean primaryStyle) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		status.setText(getString(channel ? R.string.status_creating_channel : R.string.status_creating_group));
		runButtonTask("create_room", actionButton, primaryStyle, new Task() {
			@Override
			public void run() throws Exception {
				final MiniTaLib.Chat chat = channel ? c.createChannel(titleValue, usernameValue, members) : c.createGroup(titleValue, members);
				ui(new Runnable() {
					@Override
					public void run() {
						currentPeerUser = chat.peer;
						currentPeer = resolvedPeerName(chat.peer, chat.id);
						status.setText(getString(channel ? R.string.status_channel_created : R.string.status_group_created));
						showChat();
						loadHistory();
					}
				});
			}
		});
	}

	private void showUsernameReservationPaymentSheet(final String usernameValue, final String detailText, final Runnable onConfirm) {
		final Dialog dialog = new Dialog(this);
		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(pad, pad, pad, pad);
		box.setBackgroundDrawable(shape(surface, 0, buttonRadius()));

		TextView title = title(getString(R.string.username_reservation_payment_title, usernameValue, USERNAME_RESERVATION_FEE_DSR));
		box.addView(title, new LinearLayout.LayoutParams(-1, -2));

		TextView details = label(detailText == null ? "" : detailText);
		details.setTextColor(muted);
		LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(-1, -2);
		detailsLp.setMargins(0, 0, 0, gap);
		box.addView(details, detailsLp);

		final PaymentSliderView slider = new PaymentSliderView(this, getString(R.string.payment_slide_hint));
		slider.setContentDescription(getString(R.string.payment_slide_hint));
		slider.setOnConfirmAction(new Runnable() {
			@Override
			public void run() {
				dialog.dismiss();
				if (onConfirm != null) onConfirm.run();
			}
		});
		box.addView(slider, new LinearLayout.LayoutParams(-1, dp(56)));

		Button cancel = button(getString(R.string.action_cancel), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(-1, -2);
		cancelLp.setMargins(0, gap, 0, 0);
		box.addView(cancel, cancelLp);

		dialog.setContentView(box);
		showStyledDialog(dialog);
	}

	private ArrayList<String> splitMembers(String raw) {
		ArrayList<String> out = new ArrayList<String>();
		if (raw == null) return out;
		String[] parts = raw.split(",");
		for (String part : parts) {
			String value = part.trim();
			if (value.length() > 0) out.add(value);
		}
		return out;
	}

	private void refreshChatInput() {
		if (chatInputContainer == null) return;
		chatInputContainer.removeAllViews();
		if (currentPeerBanned) {
			chatInputContainer.addView(bannedChatBlock());
			return;
		}
		if (isEmptyBotDialog()) {
			chatInputContainer.addView(startBotButton());
			return;
		}
		if (currentPeerIsRoom() && !currentPeerCanWrite()) {
			chatInputContainer.addView(readOnlyRoomBlock());
			return;
		}
		chatInputContainer.addView(messageBar());
	}

	private boolean currentPeerIsRoom() {
		return currentPeerUser != null
			&& currentPeerUser.roomKind != null
			&& currentPeerUser.roomKind.length() > 0
			&& currentPeer != null
			&& currentPeer.equals(resolvedPeerName(currentPeerUser, currentPeer));
	}

	private boolean currentPeerIsChannel() {
		return currentPeerIsRoom() && "channel".equals(currentPeerUser.roomKind);
	}

	private boolean currentPeerIsGroup() {
		return currentPeerIsRoom() && "group".equals(currentPeerUser.roomKind);
	}

	private boolean currentPeerCanWrite() {
		if (!currentPeerIsRoom()) return true;
		if (!currentPeerIsChannel()) return true;
		return myID != null && myID.length() > 0 && myID.equals(currentPeerUser.ownerId);
	}

	private boolean currentPeerCanManageRoom() {
		return currentPeerIsRoom()
			&& myID != null
			&& myID.length() > 0
			&& myID.equals(currentPeerUser.ownerId);
	}

	private boolean isEmptyBotDialog() {
		return currentPeerIsBot()
			&& historyLoaded
			&& messageRows != null
			&& messageRows.getCount() == 0;
	}

	private Button startBotButton() {
		return primaryButton("/start", new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				sendBotStart(v);
			}
		});
	}

	private void showWallet() {
		page = Page.WALLET;
		if (bottomNav != null) bottomNav.setVisibility(View.VISIBLE);
		content.removeAllViews();
		walletHistoryView = null;

		ScrollView scroll = pageScrollView();

		LinearLayout wallet = new LinearLayout(this);
		wallet.setOrientation(LinearLayout.VERTICAL);
		wallet.setPadding(0, 0, 0, gap);

		wallet.addView(spaced(title(getString(R.string.wallet_title))));
		LinearLayout asset = new LinearLayout(this);
		asset.setOrientation(LinearLayout.HORIZONTAL);
		asset.setGravity(Gravity.CENTER_VERTICAL);
		asset.setPadding(pad, pad, pad, pad);
		asset.setBackgroundDrawable(shape(surface, 0, elementRadius()));

		ImageView icon = new ImageView(this);
		icon.setImageResource(R.drawable.ic_dastars);
		icon.setColorFilter(primary);
		LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(48), dp(48));
		iconLp.setMargins(0, 0, pad, 0);
		asset.addView(icon, iconLp);

		LinearLayout info = new LinearLayout(this);
		info.setOrientation(LinearLayout.VERTICAL);
		TextView name = label("dastars");
		name.setTextSize(18);
		name.setTextColor(blend(primary, Color.WHITE, 0.18f));
		TextView code = label("DSR");
		code.setTextColor(muted);
		code.setTextSize(14);
		TextView balance = label("0 DSR");
		balance.setTextSize(24);
		walletBalanceView = balance;
		info.addView(name, new LinearLayout.LayoutParams(-1, -2));
		info.addView(code, new LinearLayout.LayoutParams(-1, -2));
		info.addView(balance, new LinearLayout.LayoutParams(-1, -2));
		asset.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

		wallet.addView(spaced(asset));
		wallet.addView(spaced(title(getString(R.string.wallet_receive_title))));
		walletReceiveView = label(getString(R.string.loading_short));
		walletReceiveView.setTextColor(primary);
		walletReceiveView.setTextSize(18);
		walletReceiveView.setTypeface(Typeface.MONOSPACE);
		wallet.addView(spaced(walletReceiveView));
		walletInstructionView = label("");
		walletInstructionView.setTextColor(muted);
		wallet.addView(spaced(walletInstructionView));
		wallet.addView(spaced(row(
			button(getString(R.string.wallet_copy_code), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (walletReceiveView != null) copyToClipboard("dastars", walletReceiveView.getText().toString());
				}
			})
		)));
		wallet.addView(spaced(row(
			primaryButton(getString(R.string.wallet_buy_dastars), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					openChatIfExists("dastarsbot", v, true);
				}
			})
		)));

		wallet.addView(spaced(title(getString(R.string.wallet_send_title))));
		walletTo = input(getString(R.string.wallet_to_hint), false);
		walletAmount = input(getString(R.string.wallet_amount_hint), false);
		walletComment = input(getString(R.string.wallet_comment_hint), false);
		walletAmount.setInputType(InputType.TYPE_CLASS_NUMBER);
		wallet.addView(spaced(walletTo));
		wallet.addView(spaced(walletAmount));
		wallet.addView(spaced(walletComment));
		wallet.addView(spaced(row(
			primaryButton(getString(R.string.wallet_send_dsr), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					sendDastars(v);
				}
			})
		)));
		wallet.addView(spaced(row(
			button(getString(R.string.wallet_payment_history), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showWalletHistory();
				}
			})
		)));

		scroll.addView(wallet, new ScrollView.LayoutParams(-1, -2));
		content.addView(scroll, fill());
		loadWallet();
	}

	private void loadWallet() {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		run("wallet", new Task() {
			@Override
			public void run() throws Exception {
				final MiniTaLib.WalletInfo info = c.getWallet();
				ui(new Runnable() {
					@Override
					public void run() {
						if (page != Page.WALLET) return;
						renderWallet(info);
					}
				});
			}
		});
	}

	private void renderWallet(MiniTaLib.WalletInfo info) {
		if (info != null) {
			setCachedWalletInfo(info);
			if (walletBalanceView != null) walletBalanceView.setText(info.balance + " " + info.code);
			if (walletReceiveView != null) walletReceiveView.setText(info.receiveCode == null ? "" : info.receiveCode);
			if (walletInstructionView != null) walletInstructionView.setText(info.instruction == null ? "" : info.instruction);
		}
		status.setText(getString(R.string.status_wallet_updated));
	}

	private void showWalletHistory() {
		page = Page.WALLET_HISTORY;
		if (bottomNav != null) bottomNav.setVisibility(View.VISIBLE);
		content.removeAllViews();

		ScrollView scroll = pageScrollView();
		LinearLayout history = new LinearLayout(this);
		history.setOrientation(LinearLayout.VERTICAL);
		history.setPadding(0, 0, 0, gap);
		history.addView(spaced(title(getString(R.string.wallet_payment_history))));
		ImageButton back = headerIconButton(R.drawable.ic_back, getString(R.string.action_back), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showWallet();
				}
			});
		history.addView(spaced(mixedRow(
			back,
			button(getString(R.string.action_refresh), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					loadWalletHistory(v, false);
				}
			})
		, true)));

		walletHistoryView = new LinearLayout(this);
		walletHistoryView.setOrientation(LinearLayout.VERTICAL);
		walletHistoryView.addView(walletHistoryRow(getString(R.string.loading), muted));
		history.addView(spaced(walletHistoryView));

		scroll.addView(history, new ScrollView.LayoutParams(-1, -2));
		content.addView(scroll, fill());
		loadWalletHistory();
	}

	private void loadWalletHistory() {
		loadWalletHistory(null, false);
	}

	private void loadWalletHistory(final View actionButton, final boolean primaryStyle) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		runButtonTask("wallet_history", actionButton, primaryStyle, new Task() {
			@Override
			public void run() throws Exception {
				final MiniTaLib.WalletInfo info = c.getWallet();
				final List<MiniTaLib.WalletTransaction> history = c.getWalletHistory(50);
				ui(new Runnable() {
					@Override
					public void run() {
						if (page != Page.WALLET_HISTORY) return;
						renderWalletHistory(info, history);
					}
				});
			}
		});
	}

	private void renderWalletHistory(MiniTaLib.WalletInfo info, List<MiniTaLib.WalletTransaction> history) {
		if (info != null) setCachedWalletInfo(info);
		if (walletHistoryView == null) return;
		walletHistoryView.removeAllViews();
		if (history == null || history.isEmpty()) {
			walletHistoryView.addView(walletHistoryRow(getString(R.string.wallet_history_empty), muted));
			return;
		}
		long myID = info == null ? 0 : info.userId;
		for (final MiniTaLib.WalletTransaction tx : history) {
			boolean incoming = tx.toUserId == myID;
			TextView row = walletHistoryRow(formatWalletHistoryRow(tx, incoming), incoming ? blend(primary, Color.WHITE, 0.18f) : textColor, new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showWalletTransactionDetails(tx);
				}
			});
			walletHistoryView.addView(row);
		}
		status.setText(getString(R.string.status_wallet_history_updated));
	}

	private String formatWalletHistoryRow(MiniTaLib.WalletTransaction tx, boolean incoming) {
		String sign = incoming ? "+" : "-";
		String peerName = walletPartyLabel(
				incoming ? tx.fromUserId : tx.toUserId,
				incoming ? tx.fromNick : tx.toNick,
				incoming ? tx.fromLogin : tx.toLogin);
		String direction = incoming
				? getString(R.string.wallet_history_from)
				: getString(R.string.wallet_history_to);
		String text = getString(R.string.wallet_history_row, sign, tx.amount, "DSR", direction, peerName, formatMessageDateTime(tx.date));
		if (tx.comment != null && tx.comment.length() > 0) text += "  " + tx.comment;
		return text;
	}

	private void setCachedWalletInfo(MiniTaLib.WalletInfo info) {
		if (info == null) return;
		hasWalletBalance = true;
		walletBalance = info.balance;
		walletCode = info.code == null || info.code.length() == 0 ? "DSR" : info.code;
	}

	private String walletBalanceLabel() {
		return hasWalletBalance
				? getString(R.string.wallet_balance_format, walletBalance, walletCode)
				: getString(R.string.wallet_balance_loading);
	}

	private void refreshWalletBalanceLabel(final TextView balanceView) {
		if (balanceView == null) return;
		final MiniTaLib c = ta;
		if (c == null) {
			balanceView.setText(getString(R.string.wallet_balance_sign_in));
			return;
		}
		run("wallet", new Task() {
			@Override
			public void run() throws Exception {
				final MiniTaLib.WalletInfo info = c.getWallet();
				ui(new Runnable() {
					@Override
					public void run() {
						setCachedWalletInfo(info);
						balanceView.setText(walletBalanceLabel());
					}
				});
			}
		});
	}

	private String walletPartyLabel(long userId, String nick, String fallbackLogin) {
		if (nick != null && nick.length() > 0) {
			return "@" + nick;
		}
		if (fallbackLogin != null && fallbackLogin.length() > 0) {
			return "@" + fallbackLogin;
		}
		if (userId > 0) {
			return formatPublicUserID(userId);
		}
		return "";
	}

	private TextView walletHistoryRow(String value, int color) {
		return walletHistoryRow(value, color, null);
	}

	private TextView walletHistoryRow(String value, int color, View.OnClickListener listener) {
		TextView row = label(value);
		row.setTextColor(color);
		row.setTextSize(15);
		row.setPadding(pad, gap, pad, gap);
		row.setBackgroundDrawable(listener == null ? shape(surface, 0, elementRadius()) : pressable(surface, surfaceHi, 0, elementRadius()));
		if (listener != null) row.setOnClickListener(listener);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.setMargins(0, 0, 0, gap / 2);
		row.setLayoutParams(lp);
		return row;
	}

	private void showWalletTransactionDetails(MiniTaLib.WalletTransaction tx) {
		if (tx == null) return;
		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(0, gap, 0, 0);
		if (tx.id > 0) box.addView(spaced(systemDetailRow(getString(R.string.system_detail_transaction), formatTransactionID(tx.id))));
		box.addView(spaced(systemDetailRow(getString(R.string.system_detail_type), getString(R.string.system_type_wallet_transfer))));
		if (tx.date > 0) box.addView(spaced(systemDetailRow(getString(R.string.system_detail_time), formatMessageDateTime(tx.date))));
		box.addView(spaced(systemDetailRow(getString(R.string.system_detail_amount), tx.amount + " DSR")));
		box.addView(spaced(systemDetailRow(getString(R.string.system_detail_from), walletPartyLabel(tx.fromUserId, tx.fromNick, tx.fromLogin))));
		box.addView(spaced(systemDetailRow(getString(R.string.system_detail_to), walletPartyLabel(tx.toUserId, tx.toNick, tx.toLogin))));
		if (tx.fromUserId > 0) box.addView(spaced(systemDetailRow(getString(R.string.system_detail_from_id), formatPublicUserID(tx.fromUserId))));
		if (tx.toUserId > 0) box.addView(spaced(systemDetailRow(getString(R.string.system_detail_to_id), formatPublicUserID(tx.toUserId))));
		if (tx.comment != null && tx.comment.length() > 0) {
			box.addView(spaced(systemDetailRow(getString(R.string.system_detail_comment), tx.comment)));
		}
		showContentDialog(getString(R.string.system_details_title), box, getString(R.string.action_close), null, null);
	}

	private void sendDastars() {
		sendDastars(null);
	}

	private void sendDastars(final View actionButton) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		final String to = walletTo == null ? "" : walletTo.getText().toString().trim();
		String rawAmount = walletAmount == null ? "" : walletAmount.getText().toString().trim();
		final String comment = walletComment == null ? "" : walletComment.getText().toString().trim();
		if (to.length() == 0 || rawAmount.length() == 0) return;
		final long amount;
		try {
			amount = Long.parseLong(rawAmount);
		} catch (NumberFormatException e) {
			status.setText(getString(R.string.status_bad_dsr_amount));
			return;
		}
		if (amount <= 0) {
			status.setText(getString(R.string.status_bad_dsr_amount));
			return;
		}
		runButtonTask("wallet_send", actionButton, true, new Task() {
			@Override
			public void run() throws Exception {
				c.sendDastars(to, amount, comment);
				final MiniTaLib.WalletInfo info = c.getWallet();
				ui(new Runnable() {
					@Override
					public void run() {
						if (page != Page.WALLET) return;
						if (walletAmount != null) walletAmount.setText("");
						if (walletComment != null) walletComment.setText("");
						renderWallet(info);
						status.setText(getString(R.string.status_dsr_sent));
					}
				});
			}
		});
	}

	private void showNodeStatus() {
		page = Page.NODES;
		if (bottomNav != null) bottomNav.setVisibility(View.VISIBLE);
		content.removeAllViews();

		ScrollView scroll = pageScrollView();

		LinearLayout nodes = new LinearLayout(this);
		nodes.setOrientation(LinearLayout.VERTICAL);
		nodes.setPadding(0, 0, 0, gap);
		nodes.addView(spaced(title(getString(R.string.nodes_title))));

		nodeStatusListView = new LinearLayout(this);
		nodeStatusListView.setOrientation(LinearLayout.VERTICAL);
		nodeStatusListView.addView(nodeStatusRow(getString(R.string.node_main), nodeStatusText("loading"), -1, -1, muted));
		nodeStatusListView.addView(nodeStatusRow(getString(R.string.node_calls), nodeStatusText("loading"), -1, -1, muted));
		nodeStatusListView.addView(nodeStatusRow(getString(R.string.node_media), nodeStatusText("loading"), -1, -1, muted));
		nodeStatusListView.addView(nodeStatusRow(getString(R.string.node_wallet), nodeStatusText("loading"), -1, -1, muted));
		nodeStatusListView.addView(nodeStatusRow(getString(R.string.node_e2e_keys), nodeStatusText("loading"), -1, -1, muted));
		nodes.addView(spaced(nodeStatusListView));

		nodes.addView(spaced(row(
			button(getString(R.string.action_refresh), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					loadNodeStatus(v, false);
				}
			})
		)));

		scroll.addView(nodes, new ScrollView.LayoutParams(-1, -2));
		content.addView(scroll, fill());
		loadNodeStatus();
	}

	private void loadNodeStatus() {
		loadNodeStatus(null, false);
	}

	private void loadNodeStatus(final View actionButton, final boolean primaryStyle) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		runButtonTask("nodes", actionButton, primaryStyle, new Task() {
			@Override
			public void run() throws Exception {
				final List<MiniTaLib.NodeStatus> nodes = c.getNodeStatuses();
				final MiniTaLib.NodeStatus e2e = e2eKeyStatus(c);
				ui(new Runnable() {
					@Override
					public void run() {
						if (page != Page.NODES) return;
						renderNodeStatus(nodes, e2e);
					}
				});
			}
		});
	}

	private void renderNodeStatus(List<MiniTaLib.NodeStatus> nodes, MiniTaLib.NodeStatus e2e) {
		if (nodeStatusListView == null || nodes == null) return;
		nodeStatusListView.removeAllViews();
		nodeStatusListView.addView(nodeStatusRow(
			getString(R.string.node_main),
			nodeStatusText("online"),
			1,
			1,
			nodeStatusColor("online")
		));
		for (MiniTaLib.NodeStatus item : nodes) {
			nodeStatusListView.addView(nodeStatusRow(
				nodeDisplayName(item),
				nodeStatusText(item.status),
				item.available,
				item.total,
				nodeStatusColor(item.status)
			));
		}
		if (e2e != null) {
			nodeStatusListView.addView(nodeStatusRow(
				e2e.name,
				nodeStatusText(e2e.status),
				e2e.available,
				e2e.total,
				nodeStatusColor(e2e.status)
			));
		}
		status.setText(getString(R.string.status_nodes_updated));
	}

	private String nodeDisplayName(MiniTaLib.NodeStatus item) {
		if (item == null) return "";
		if ("call".equals(item.type)) return getString(R.string.node_calls);
		if ("file".equals(item.type)) return getString(R.string.node_media);
		if ("wallet".equals(item.type)) return getString(R.string.node_wallet);
		if ("e2e".equals(item.type)) return getString(R.string.node_e2e_keys);
		return item.name == null || item.name.length() == 0 ? item.type : item.name;
	}

	private MiniTaLib.NodeStatus e2eKeyStatus(MiniTaLib c) {
		String login = myLogin == null ? "" : myLogin.trim();
		if (login.length() == 0) {
			return new MiniTaLib.NodeStatus("e2e", getString(R.string.node_e2e_keys), "username_required", 0, 1);
		}
		try {
			rs.ove.crypt.proto.E2ECipher.Identity local = SessionStore.e2eIdentity(this, login);
			String registered = c == null ? "" : c.ownE2EPublicKey();
			if (local != null && registered.length() > 0 && local.publicKeyB64.equals(registered)) {
				return new MiniTaLib.NodeStatus("e2e", getString(R.string.node_e2e_keys), "online", 1, 1);
			}
			if (local == null && registered.length() == 0) {
				return new MiniTaLib.NodeStatus("e2e", getString(R.string.node_e2e_keys), "not_generated", 0, 1);
			}
			if (local != null && registered.length() == 0) {
				return new MiniTaLib.NodeStatus("e2e", getString(R.string.node_e2e_keys), "local_only", 0, 1);
			}
			if (local == null) {
				return new MiniTaLib.NodeStatus("e2e", getString(R.string.node_e2e_keys), "server_only", 0, 1);
			}
			return new MiniTaLib.NodeStatus("e2e", getString(R.string.node_e2e_keys), "mismatch", 0, 1);
		} catch (Exception e) {
			return new MiniTaLib.NodeStatus("e2e", getString(R.string.node_e2e_keys), "check_failed", 0, 1);
		}
	}

	private LinearLayout nodeStatusRow(String name, String state, int available, int total, int stateColor) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(pad, gap, pad, gap);
		row.setBackgroundDrawable(shape(surface, 0, elementRadius()));

		LinearLayout labels = new LinearLayout(this);
		labels.setOrientation(LinearLayout.VERTICAL);
		TextView title = label(name);
		title.setTextSize(16);
		TextView count = label(total < 0 ? "..." : available + "/" + total);
		count.setTextSize(13);
		count.setTextColor(muted);
		labels.addView(title, new LinearLayout.LayoutParams(-1, -2));
		labels.addView(count, new LinearLayout.LayoutParams(-1, -2));
		row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

		TextView badge = label(state);
		badge.setTextColor(stateColor);
		badge.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
		row.addView(badge, new LinearLayout.LayoutParams(-2, -2));

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.setMargins(0, 0, 0, gap / 2);
		row.setLayoutParams(lp);
		return row;
	}

	private String nodeStatusText(String value) {
		if ("loading".equals(value)) return getString(R.string.node_status_loading);
		if ("online".equals(value)) return getString(R.string.node_status_online);
		if ("partial".equals(value)) return getString(R.string.node_status_partial);
		if ("username_required".equals(value)) return getString(R.string.node_status_username_required);
		if ("not_generated".equals(value)) return getString(R.string.node_status_not_generated);
		if ("local_only".equals(value)) return getString(R.string.node_status_local_only);
		if ("server_only".equals(value)) return getString(R.string.node_status_server_only);
		if ("mismatch".equals(value)) return getString(R.string.node_status_mismatch);
		if ("check_failed".equals(value)) return getString(R.string.node_status_check_failed);
		return getString(R.string.node_status_offline);
	}

	private int nodeStatusColor(String value) {
		if ("online".equals(value)) return blend(primary, Color.WHITE, 0.18f);
		if ("partial".equals(value)
				|| "username_required".equals(value)
				|| "not_generated".equals(value)
				|| "local_only".equals(value)
				|| "server_only".equals(value)
				|| "check_failed".equals(value)) return Color.rgb(245, 166, 35);
		return Color.rgb(231, 76, 60);
	}

	private void showSettings() {
		page = Page.SETTINGS;
		if (bottomNav != null) bottomNav.setVisibility(View.VISIBLE);
		content.removeAllViews();
		accountSessionsView = null;

		ScrollView scroll = pageScrollView();

		LinearLayout settings = new LinearLayout(this);
		settings.setOrientation(LinearLayout.VERTICAL);
		settings.setPadding(0, 0, 0, gap);

		settings.addView(spaced(title(getString(R.string.settings_title))));
		settings.addView(spaced(settingsProfileHeader()));
		settings.addView(settingsSection(getString(R.string.settings_section_account)));
		settings.addView(settingsRow(getString(R.string.settings_profile), ownUserSettingsSubtitle(), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showSettingsProfile();
			}
		}));
		settings.addView(settingsRow(getString(R.string.settings_sessions), getString(R.string.settings_sessions_subtitle), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showSettingsSessions();
			}
		}));
		settings.addView(settingsRow(getString(R.string.settings_cloud_password), getString(R.string.settings_cloud_password_subtitle), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showSettingsCloudPassword();
			}
		}));
		settings.addView(settingsRow(getString(R.string.settings_e2e_keys), getString(R.string.settings_e2e_keys_subtitle), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showSettingsE2EKeys();
			}
		}));
		settings.addView(settingsRow(getString(R.string.settings_contacts), getString(R.string.settings_contacts_subtitle), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showSettingsContacts();
			}
		}));
		settings.addView(settingsRow(getString(R.string.settings_privacy), privacySettingsSubtitle(), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showSettingsPrivacy();
			}
		}));
		settings.addView(settingsSection(getString(R.string.settings_section_app)));
		settings.addView(settingsRow(getString(R.string.settings_server), server(), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showSettingsServer();
			}
		}));
		settings.addView(settingsRow(getString(R.string.settings_language), languageLabel(SessionStore.language(this)), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showSettingsLanguage();
			}
		}));
		settings.addView(settingsRow(getString(R.string.settings_interface), SessionStore.showStatus(this) ? getString(R.string.settings_status_visible) : getString(R.string.settings_status_hidden), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showSettingsInterface();
			}
		}));
		settings.addView(settingsRow(getString(R.string.settings_updates), updateSettingsSubtitle(), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				checkGithubUpdate();
			}
		}));
		settings.addView(settingsSection(getString(R.string.settings_section_actions)));
		settings.addView(settingsRow(getString(R.string.settings_delete_account), getString(R.string.settings_delete_account_subtitle), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showSettingsDeleteAccount();
			}
		}));
		settings.addView(settingsRow(getString(R.string.settings_logout), getString(R.string.settings_logout_subtitle), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showSettingsLogout();
			}
		}));
		settings.addView(settingsInfoRow(getString(R.string.settings_app_version), BuildConfig.VERSION_NAME));

		scroll.addView(settings, new ScrollView.LayoutParams(-1, -2));
		content.addView(scroll, fill());
	}

	private String updateSettingsSubtitle() {
		String repository = BuildConfig.GITHUB_REPOSITORY == null ? "" : BuildConfig.GITHUB_REPOSITORY.trim();
		if (repository.length() == 0) return getString(R.string.settings_updates_not_configured);
		return getString(R.string.settings_updates_subtitle, BuildConfig.VERSION_NAME);
	}

	private void checkGithubUpdate() {
		final String repository = BuildConfig.GITHUB_REPOSITORY == null ? "" : BuildConfig.GITHUB_REPOSITORY.trim();
		if (repository.length() == 0) {
			status.setText(getString(R.string.status_update_not_configured));
			return;
		}
		status.setText(getString(R.string.status_update_checking));
		run("github_update", new Task() {
			@Override
			public void run() throws Exception {
				final GithubOtaUpdater.Update update = GithubOtaUpdater.findLatest(
						repository,
						getPackageName(),
						BuildConfig.VERSION_NAME,
						BuildConfig.VERSION_CODE);
				if (update == null) {
					ui(new Runnable() {
						@Override
						public void run() {
							status.setText(getString(R.string.status_update_none));
						}
					});
					return;
				}
				ui(new Runnable() {
					@Override
					public void run() {
						status.setText(getString(R.string.status_update_downloading, update.versionName));
					}
				});
				final File apk = GithubOtaUpdater.download(MainActivity.this, update);
				ui(new Runnable() {
					@Override
					public void run() {
						installGithubUpdate(apk, update.versionName);
					}
				});
			}
		});
	}

	private void installGithubUpdate(File apk, String versionName) {
		if (apk == null || !apk.isFile()) {
			status.setText(getString(R.string.status_download_folder_not_available));
			return;
		}
		if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
			status.setText(getString(R.string.status_update_install_permission));
			try {
				Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
				startActivity(intent);
			} catch (Exception e) {
				openUrl("https://github.com/" + BuildConfig.GITHUB_REPOSITORY + "/releases/latest");
			}
			return;
		}
		try {
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(localFileUri(apk), "application/vnd.android.package-archive");
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			startActivity(intent);
			status.setText(getString(R.string.status_update_ready, versionName));
		} catch (ActivityNotFoundException e) {
			status.setText(getString(R.string.status_no_app_to_open, apk.getName()));
		} catch (Exception e) {
			status.setText(getString(R.string.status_update_install_error, errorText(e)));
		}
	}

	private LinearLayout settingsProfileHeader() {
		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(pad, pad, pad, pad);
		box.setBackgroundDrawable(shape(surface, 0, elementRadius()));
		TextView name = label(displayOwnUser());
		name.setTextSize(18);
		name.setTextColor(textColor);
		box.addView(name, new LinearLayout.LayoutParams(-1, -2));
		if (myID != null && myID.length() > 0) {
			TextView id = clickableUserID(myID);
			id.setTextColor(muted);
			box.addView(id, new LinearLayout.LayoutParams(-1, -2));
		}
		return box;
	}

	private String ownUserSettingsSubtitle() {
		if (myLogin != null && myLogin.length() > 0) return "@" + myLogin;
		if (myID != null && myID.length() > 0) return myID;
		return getString(R.string.settings_profile_default_subtitle);
	}

	private TextView settingsSection(String value) {
		TextView section = label(value);
		section.setTextColor(muted);
		section.setTextSize(13);
		section.setPadding(pad, gap, pad, gap / 2);
		return section;
	}

	private LinearLayout settingsRow(String name, String detail, View.OnClickListener listener) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(pad, gap, pad, gap);
		row.setBackgroundDrawable(pressable(surface, surfaceHi, 0, elementRadius()));
		row.setOnClickListener(listener);

		LinearLayout texts = new LinearLayout(this);
		texts.setOrientation(LinearLayout.VERTICAL);
		TextView title = label(name);
		title.setTextSize(16);
		title.setTextColor(textColor);
		texts.addView(title, new LinearLayout.LayoutParams(-1, -2));
		if (detail != null && detail.length() > 0) {
			TextView subtitle = label(detail);
			subtitle.setTextSize(13);
			subtitle.setTextColor(muted);
			texts.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
		}
		row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

		TextView arrow = label(">");
		arrow.setTextColor(muted);
		arrow.setTextSize(18);
		arrow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
		row.addView(arrow, new LinearLayout.LayoutParams(dp(24), -2));

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.setMargins(0, 0, 0, gap / 2);
		row.setLayoutParams(lp);
		return row;
	}

	private LinearLayout settingsInfoRow(String name, String detail) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(pad, gap, pad, gap);
		row.setBackgroundDrawable(shape(surface, 0, elementRadius()));

		TextView title = label(name);
		title.setTextSize(16);
		title.setTextColor(textColor);
		row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

		TextView value = label(detail);
		value.setTextSize(13);
		value.setTextColor(muted);
		value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
		row.addView(value, new LinearLayout.LayoutParams(0, -2, 1));

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.setMargins(0, 0, 0, gap / 2);
		row.setLayoutParams(lp);
		return row;
	}

	private LinearLayout settingsPage(String titleText, Page pageValue) {
		page = pageValue;
		if (bottomNav != null) bottomNav.setVisibility(View.VISIBLE);
		content.removeAllViews();
		accountSessionsView = null;

		ScrollView scroll = pageScrollView();

		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(0, 0, 0, gap);
		ImageButton back = headerIconButton(R.drawable.ic_back, getString(R.string.action_back), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showSettings();
			}
		});
		box.addView(spaced(row(back)));
		box.addView(spaced(title(titleText)));
		scroll.addView(box, new ScrollView.LayoutParams(-1, -2));
		content.addView(scroll, fill());
		return box;
	}

	private void showSettingsProfile() {
		LinearLayout box = settingsPage(getString(R.string.settings_profile), Page.SETTINGS_PROFILE);
		boolean signedIn = (myID != null && myID.length() > 0) || (myLogin != null && myLogin.length() > 0);
		box.addView(spaced(label(signedIn ? getString(R.string.status_online_as, displayOwnUser()) : getString(R.string.settings_not_logged_in))));
		if (myID != null && myID.length() > 0) {
			box.addView(spaced(title(getString(R.string.profile_id))));
			box.addView(spaced(clickableUserID(myID)));
		}
		box.addView(spaced(title(getString(R.string.profile_username))));
		accountUsername = input(getString(R.string.hint_username), false);
		accountUsername.setText(myLogin == null ? "" : myLogin);
		box.addView(spaced(accountUsername));
		box.addView(spaced(row(primaryButton(getString(R.string.settings_save_username), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				saveUsername(v);
			}
		}))));
		box.addView(spaced(title(getString(R.string.settings_name))));
		accountName = input(getString(R.string.settings_public_name_hint), false);
		accountName.setText(myNick == null ? "" : myNick);
		box.addView(spaced(accountName));
		box.addView(spaced(row(primaryButton(getString(R.string.settings_save_name), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				saveName(v);
			}
		}))));
	}

	private void showSettingsSessions() {
		LinearLayout box = settingsPage(getString(R.string.settings_sessions), Page.SETTINGS_SESSIONS);
		accountSessionsView = new LinearLayout(this);
		accountSessionsView.setOrientation(LinearLayout.VERTICAL);
		accountSessionsView.addView(sessionRow(getString(R.string.loading), muted));
		box.addView(spaced(accountSessionsView));
		box.addView(spaced(row(
			button(getString(R.string.action_refresh), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					loadSessions(v, false);
				}
			}),
			primaryButton(getString(R.string.settings_logout_others), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					revokeOtherSessions(v, true);
				}
			})
		)));
		loadSessions();
	}

	private void showSettingsCloudPassword() {
		LinearLayout box = settingsPage(getString(R.string.settings_cloud_password), Page.SETTINGS_CLOUD_PASSWORD);
		box.addView(spaced(label(getString(R.string.settings_cloud_password_help))));
		accountCloudPassword = input(getString(R.string.settings_optional_password), true);
		box.addView(spaced(accountCloudPassword));
		cloudPasswordSaveButton = primaryButton(getString(R.string.settings_save_password), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				saveCloudPassword();
			}
		});
		box.addView(spaced(row(cloudPasswordSaveButton)));
		box.addView(spaced(title(getString(R.string.settings_reset_cloud_password))));
		box.addView(spaced(label(getString(R.string.settings_reset_cloud_password_help, accountEmailText()))));
		accountCloudPasswordCode = input(getString(R.string.hint_email_code), false);
		box.addView(spaced(accountCloudPasswordCode));
		cloudPasswordClearButton = button(getString(R.string.action_send_code), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				requestAccountEmailCode(accountCloudPasswordCode, getString(R.string.status_cloud_password_reset_code_sent), v, false);
			}
		});
		box.addView(spaced(row(cloudPasswordClearButton)));
		PaymentSliderView resetSlider = new PaymentSliderView(this, getString(R.string.reset_cloud_password_slide_hint), true);
		resetSlider.setContentDescription(getString(R.string.reset_cloud_password_slide_hint));
		resetSlider.setOnConfirmAction(new Runnable() {
			@Override
			public void run() {
				resetCloudPassword();
			}
		});
		box.addView(spaced(resetSlider), new LinearLayout.LayoutParams(-1, dp(56)));
		cloudPasswordState = label("");
		cloudPasswordState.setTextColor(muted);
		LinearLayout stateRow = new LinearLayout(this);
		stateRow.setOrientation(LinearLayout.HORIZONTAL);
		stateRow.setGravity(Gravity.CENTER_VERTICAL);
		stateRow.addView(cloudPasswordState, new LinearLayout.LayoutParams(0, -2, 1));
		box.addView(spaced(stateRow));
	}

	private void showSettingsE2EKeys() {
		LinearLayout box = settingsPage(getString(R.string.settings_e2e_keys), Page.SETTINGS_E2E_KEYS);
		box.addView(spaced(label(getString(R.string.settings_e2e_keys_help))));
		PaymentSliderView resetSlider = new PaymentSliderView(this, getString(R.string.reset_e2e_key_slide_hint), true);
		resetSlider.setContentDescription(getString(R.string.reset_e2e_key_slide_hint));
		resetSlider.setOnConfirmAction(new Runnable() {
			@Override
			public void run() {
				resetE2EKey();
			}
		});
		box.addView(spaced(resetSlider), new LinearLayout.LayoutParams(-1, dp(56)));
	}

	private void showSettingsDeleteAccount() {
		LinearLayout box = settingsPage(getString(R.string.settings_delete_account), Page.SETTINGS_DELETE_ACCOUNT);
		box.addView(spaced(label(getString(R.string.settings_delete_account_help, accountEmailText()))));
		accountDeleteCode = input(getString(R.string.hint_email_code), false);
		box.addView(spaced(accountDeleteCode));
		deleteAccountCodeButton = button(getString(R.string.action_send_code), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				requestAccountEmailCode(accountDeleteCode, getString(R.string.status_delete_account_code_sent), v, false);
			}
		});
		box.addView(spaced(row(deleteAccountCodeButton)));
		PaymentSliderView deleteSlider = new PaymentSliderView(this, getString(R.string.delete_account_slide_hint), true);
		deleteSlider.setContentDescription(getString(R.string.delete_account_slide_hint));
		deleteSlider.setOnConfirmAction(new Runnable() {
			@Override
			public void run() {
				deleteAccount();
			}
		});
		box.addView(spaced(deleteSlider), new LinearLayout.LayoutParams(-1, dp(56)));
	}

	private void showSettingsLogout() {
		LinearLayout box = settingsPage(getString(R.string.settings_logout), Page.SETTINGS_LOGOUT);
		box.addView(spaced(label(getString(R.string.settings_logout_subtitle))));
		PaymentSliderView logoutSlider = new PaymentSliderView(this, getString(R.string.logout_slide_hint), true);
		logoutSlider.setContentDescription(getString(R.string.logout_slide_hint));
		logoutSlider.setOnConfirmAction(new Runnable() {
			@Override
			public void run() {
				logout();
			}
		});
		box.addView(spaced(logoutSlider), new LinearLayout.LayoutParams(-1, dp(56)));
	}

	private void showSettingsContacts() {
		LinearLayout box = settingsPage(getString(R.string.settings_contacts), Page.SETTINGS_CONTACTS);
		contactAddress = input(getString(R.string.hint_username_or_id), false);
		box.addView(spaced(contactAddress));
		box.addView(spaced(row(
			primaryButton(getString(R.string.action_add_contact), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					saveContact(true, v, true);
				}
			}),
			button(getString(R.string.action_delete_contact), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					saveContact(false, v, false);
				}
			})
		)));
		contactsView = new LinearLayout(this);
		contactsView.setOrientation(LinearLayout.VERTICAL);
		contactsView.addView(settingsSection(getString(R.string.loading)));
		box.addView(spaced(contactsView));
		loadContacts();
	}

	private void loadContacts() {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		run("contacts", new Task() {
			@Override
			public void run() throws Exception {
				final List<MiniTaLib.User> contacts = c.getContacts();
				ui(new Runnable() {
					@Override
					public void run() {
						if (page != Page.SETTINGS_CONTACTS || contactsView == null) return;
						contactsView.removeAllViews();
						if (contacts.isEmpty()) {
							contactsView.addView(settingsSection(getString(R.string.contacts_empty)));
							return;
						}
						for (final MiniTaLib.User user : contacts) {
							contactsView.addView(settingsRow(displayUser(user), user.id, new View.OnClickListener() {
								@Override
								public void onClick(View v) {
									currentPeerUser = user;
									currentPeer = resolvedPeerName(user, user.id);
									showChat();
									loadHistory();
								}
							}));
						}
					}
				});
			}
		});
	}

	private void saveContact(final boolean add) {
		saveContact(add, null, add);
	}

	private void saveContact(final boolean add, final View actionButton, final boolean primaryStyle) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		final String value = contactAddress == null ? "" : contactAddress.getText().toString().trim();
		if (value.length() == 0) return;
		status.setText(getString(add ? R.string.status_saving_contact : R.string.status_deleting_contact));
		runButtonTask("contact", actionButton, primaryStyle, new Task() {
			@Override
			public void run() throws Exception {
				if (add) c.addContact(value); else c.deleteContact(value);
				ui(new Runnable() {
					@Override
					public void run() {
						status.setText(getString(add ? R.string.status_contact_saved : R.string.status_contact_deleted));
						loadContacts();
					}
				});
			}
		});
	}

	private void showSettingsPrivacy() {
		LinearLayout box = settingsPage(getString(R.string.settings_privacy), Page.SETTINGS_PRIVACY);
		box.addView(spaced(title(getString(R.string.settings_privacy_messages))));
		messagePrivacyGroup = new RadioGroup(this);
		messagePrivacyGroup.setOrientation(RadioGroup.VERTICAL);
		addPrivacyOption(messagePrivacyGroup, MESSAGE_PRIVACY_EVERYONE_ID, getString(R.string.privacy_everyone));
		addPrivacyOption(messagePrivacyGroup, MESSAGE_PRIVACY_CONTACTS_ID, getString(R.string.privacy_contacts));
		addPrivacyOption(messagePrivacyGroup, MESSAGE_PRIVACY_CHATS_ID, getString(R.string.privacy_chats));
		addPrivacyOption(messagePrivacyGroup, MESSAGE_PRIVACY_NOBODY_ID, getString(R.string.privacy_nobody));
		messagePrivacyGroup.check(messagePrivacyId(myMessagePrivacy));
		box.addView(spaced(messagePrivacyGroup));

		box.addView(spaced(title(getString(R.string.settings_privacy_calls))));
		callPrivacyGroup = new RadioGroup(this);
		callPrivacyGroup.setOrientation(RadioGroup.VERTICAL);
		addPrivacyOption(callPrivacyGroup, CALL_PRIVACY_EVERYONE_ID, getString(R.string.privacy_everyone));
		addPrivacyOption(callPrivacyGroup, CALL_PRIVACY_CONTACTS_ID, getString(R.string.privacy_contacts));
		addPrivacyOption(callPrivacyGroup, CALL_PRIVACY_CHATS_ID, getString(R.string.privacy_chats));
		addPrivacyOption(callPrivacyGroup, CALL_PRIVACY_NOBODY_ID, getString(R.string.privacy_nobody));
		callPrivacyGroup.check(callPrivacyId(myCallPrivacy));
		box.addView(spaced(callPrivacyGroup));

		box.addView(spaced(title(getString(R.string.settings_privacy_invites))));
		invitePrivacyGroup = new RadioGroup(this);
		invitePrivacyGroup.setOrientation(RadioGroup.VERTICAL);
		addPrivacyOption(invitePrivacyGroup, INVITE_PRIVACY_EVERYONE_ID, getString(R.string.privacy_everyone));
		addPrivacyOption(invitePrivacyGroup, INVITE_PRIVACY_CONTACTS_ID, getString(R.string.privacy_contacts));
		addPrivacyOption(invitePrivacyGroup, INVITE_PRIVACY_NOBODY_ID, getString(R.string.privacy_nobody));
		invitePrivacyGroup.check(invitePrivacyId(myInvitePrivacy));
		box.addView(spaced(invitePrivacyGroup));

		box.addView(spaced(row(primaryButton(getString(R.string.action_save), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				savePrivacy(v);
			}
		}))));
	}

	private void addPrivacyOption(RadioGroup group, int id, String label) {
		RadioButton button = new RadioButton(this);
		button.setId(id);
		button.setText(safeDisplayText(label));
		button.setTextColor(textColor);
		button.setPadding(gap, gap, gap, gap);
		group.addView(button, new RadioGroup.LayoutParams(-1, -2));
	}

	private void showSettingsServer() {
		LinearLayout box = settingsPage(getString(R.string.settings_server), Page.SETTINGS_SERVER);
		serverUrl = serverInput();
		box.addView(spaced(serverUrl));
		box.addView(spaced(row(primaryButton(getString(R.string.action_save), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				applySettings();
				showSettings();
			}
		}))));
	}

	private void showSettingsLanguage() {
		LinearLayout box = settingsPage(getString(R.string.settings_language), Page.SETTINGS_LANGUAGE);
		languageGroup = new RadioGroup(this);
		languageGroup.setOrientation(RadioGroup.VERTICAL);
		addLanguageOption(languageGroup, LANGUAGE_SYSTEM_ID, getString(R.string.language_system));
		addLanguageOption(languageGroup, LANGUAGE_ENGLISH_ID, getString(R.string.language_english));
		addLanguageOption(languageGroup, LANGUAGE_RUSSIAN_ID, getString(R.string.language_russian));
		addLanguageOption(languageGroup, LANGUAGE_HEBREW_ID, getString(R.string.language_hebrew));
		languageGroup.check(languageId(SessionStore.language(this)));
		box.addView(spaced(languageGroup));
		box.addView(spaced(row(primaryButton(getString(R.string.action_save), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				applyLanguage();
			}
		}))));
	}

	private void addLanguageOption(RadioGroup group, int id, String label) {
		RadioButton button = new RadioButton(this);
		button.setId(id);
		button.setText(safeDisplayText(label));
		button.setTextColor(textColor);
		button.setPadding(gap, gap, gap, gap);
		group.addView(button, new RadioGroup.LayoutParams(-1, -2));
	}

	private int languageId(String language) {
		if (AppLocale.ENGLISH.equals(language)) return LANGUAGE_ENGLISH_ID;
		if (AppLocale.RUSSIAN.equals(language)) return LANGUAGE_RUSSIAN_ID;
		if (AppLocale.HEBREW.equals(language)) return LANGUAGE_HEBREW_ID;
		return LANGUAGE_SYSTEM_ID;
	}

	private String selectedLanguage() {
		if (languageGroup == null) return AppLocale.SYSTEM;
		int checked = languageGroup.getCheckedRadioButtonId();
		if (checked == LANGUAGE_ENGLISH_ID) return AppLocale.ENGLISH;
		if (checked == LANGUAGE_RUSSIAN_ID) return AppLocale.RUSSIAN;
		if (checked == LANGUAGE_HEBREW_ID) return AppLocale.HEBREW;
		return AppLocale.SYSTEM;
	}

	private String languageLabel(String language) {
		if (AppLocale.ENGLISH.equals(language)) return getString(R.string.language_english);
		if (AppLocale.RUSSIAN.equals(language)) return getString(R.string.language_russian);
		if (AppLocale.HEBREW.equals(language)) return getString(R.string.language_hebrew);
		return getString(R.string.language_system);
	}

	private String normalizePrivacy(String value) {
		if ("contacts".equals(value) || "chats".equals(value) || "nobody".equals(value)) return value;
		return "everyone";
	}

	private String normalizeInvitePrivacy(String value) {
		if ("contacts".equals(value) || "nobody".equals(value)) return value;
		return "everyone";
	}

	private String privacyLabel(String value) {
		value = normalizePrivacy(value);
		if ("contacts".equals(value)) return getString(R.string.privacy_contacts);
		if ("chats".equals(value)) return getString(R.string.privacy_chats);
		if ("nobody".equals(value)) return getString(R.string.privacy_nobody);
		return getString(R.string.privacy_everyone);
	}

	private String invitePrivacyLabel(String value) {
		value = normalizeInvitePrivacy(value);
		if ("contacts".equals(value)) return getString(R.string.privacy_contacts);
		if ("nobody".equals(value)) return getString(R.string.privacy_nobody);
		return getString(R.string.privacy_everyone);
	}

	private String privacySettingsSubtitle() {
		return getString(R.string.settings_privacy_subtitle, privacyLabel(myMessagePrivacy), privacyLabel(myCallPrivacy), invitePrivacyLabel(myInvitePrivacy));
	}

	private int messagePrivacyId(String value) {
		value = normalizePrivacy(value);
		if ("contacts".equals(value)) return MESSAGE_PRIVACY_CONTACTS_ID;
		if ("chats".equals(value)) return MESSAGE_PRIVACY_CHATS_ID;
		if ("nobody".equals(value)) return MESSAGE_PRIVACY_NOBODY_ID;
		return MESSAGE_PRIVACY_EVERYONE_ID;
	}

	private int callPrivacyId(String value) {
		value = normalizePrivacy(value);
		if ("contacts".equals(value)) return CALL_PRIVACY_CONTACTS_ID;
		if ("chats".equals(value)) return CALL_PRIVACY_CHATS_ID;
		if ("nobody".equals(value)) return CALL_PRIVACY_NOBODY_ID;
		return CALL_PRIVACY_EVERYONE_ID;
	}

	private int invitePrivacyId(String value) {
		value = normalizeInvitePrivacy(value);
		if ("contacts".equals(value)) return INVITE_PRIVACY_CONTACTS_ID;
		if ("nobody".equals(value)) return INVITE_PRIVACY_NOBODY_ID;
		return INVITE_PRIVACY_EVERYONE_ID;
	}

	private String selectedMessagePrivacy() {
		if (messagePrivacyGroup == null) return myMessagePrivacy;
		int checked = messagePrivacyGroup.getCheckedRadioButtonId();
		if (checked == MESSAGE_PRIVACY_CONTACTS_ID) return "contacts";
		if (checked == MESSAGE_PRIVACY_CHATS_ID) return "chats";
		if (checked == MESSAGE_PRIVACY_NOBODY_ID) return "nobody";
		return "everyone";
	}

	private String selectedCallPrivacy() {
		if (callPrivacyGroup == null) return myCallPrivacy;
		int checked = callPrivacyGroup.getCheckedRadioButtonId();
		if (checked == CALL_PRIVACY_CONTACTS_ID) return "contacts";
		if (checked == CALL_PRIVACY_CHATS_ID) return "chats";
		if (checked == CALL_PRIVACY_NOBODY_ID) return "nobody";
		return "everyone";
	}

	private String selectedInvitePrivacy() {
		if (invitePrivacyGroup == null) return myInvitePrivacy;
		int checked = invitePrivacyGroup.getCheckedRadioButtonId();
		if (checked == INVITE_PRIVACY_CONTACTS_ID) return "contacts";
		if (checked == INVITE_PRIVACY_NOBODY_ID) return "nobody";
		return "everyone";
	}

	private void showSettingsInterface() {
		LinearLayout box = settingsPage(getString(R.string.settings_interface), Page.SETTINGS_INTERFACE);
		showStatusCheck = checkBox(getString(R.string.settings_show_status), SessionStore.showStatus(this));
		box.addView(spaced(showStatusCheck));
		box.addView(spaced(row(primaryButton(getString(R.string.action_save), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				applySettings();
				showSettings();
			}
		}))));
	}

	private void loadSessions() {
		loadSessions(null, false);
	}

	private void loadSessions(final View actionButton, final boolean primaryStyle) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		runButtonTask("sessions", actionButton, primaryStyle, new Task() {
			@Override
			public void run() throws Exception {
				final List<MiniTaLib.SessionInfo> sessions = c.getSessions();
				ui(new Runnable() {
					@Override
					public void run() {
						if (page != Page.SETTINGS_SESSIONS) return;
						renderSessions(sessions);
					}
				});
			}
		});
	}

	private void saveUsername() {
		saveUsername(null);
	}

	private void saveUsername(final View actionButton) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		final String value = accountUsername == null ? "" : accountUsername.getText().toString().trim();
		if (value.length() == 0) return;
		if ((myLogin == null || myLogin.length() == 0)) {
			showUsernameReservationPaymentSheet(
				value,
				getString(R.string.username_reservation_payment_details_account),
				new Runnable() {
					@Override
					public void run() {
						saveUsernameConfirmed(value);
					}
				}
			);
			return;
		}
		saveUsernameConfirmed(value, actionButton);
	}

	private void saveUsernameConfirmed(final String value) {
		saveUsernameConfirmed(value, null);
	}

	private void saveUsernameConfirmed(final String value, final View actionButton) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		status.setText(getString(R.string.status_saving_username));
		runButtonTask("username", actionButton, true, new Task() {
			@Override
			public void run() throws Exception {
				final MiniTaLib.User user = c.setUsername(value);
				applyOwnUser(user);
				SessionStore.save(MainActivity.this, server(), c.token(), myLogin);
				ui(new Runnable() {
					@Override
					public void run() {
						status.setText(getString(R.string.status_username_saved));
						showSettingsProfile();
					}
				});
			}
		});
	}

	private void saveName() {
		saveName(null);
	}

	private void saveName(final View actionButton) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		final String value = accountName == null ? "" : accountName.getText().toString().trim();
		status.setText(getString(R.string.status_saving_name));
		runButtonTask("name", actionButton, true, new Task() {
			@Override
			public void run() throws Exception {
				final MiniTaLib.User user = c.setName(value);
				applyOwnUser(user);
				SessionStore.save(MainActivity.this, server(), c.token(), myLogin);
				ui(new Runnable() {
					@Override
					public void run() {
						status.setText(getString(R.string.status_name_saved));
						showSettingsProfile();
					}
				});
			}
		});
	}

	private void savePrivacy() {
		savePrivacy(null);
	}

	private void savePrivacy(final View actionButton) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		final String messageMode = selectedMessagePrivacy();
		final String callMode = selectedCallPrivacy();
		final String inviteMode = selectedInvitePrivacy();
		status.setText(getString(R.string.status_saving_privacy));
		runButtonTask("privacy", actionButton, true, new Task() {
			@Override
			public void run() throws Exception {
				final MiniTaLib.User user = c.setPrivacy(messageMode, callMode, inviteMode);
				applyOwnUser(user);
				ui(new Runnable() {
					@Override
					public void run() {
						status.setText(getString(R.string.status_privacy_saved));
						showSettings();
					}
				});
			}
		});
	}

	private void renderSessions(List<MiniTaLib.SessionInfo> sessions) {
		if (accountSessionsView == null) return;
		accountSessionsView.removeAllViews();
		if (sessions == null || sessions.isEmpty()) {
			accountSessionsView.addView(sessionRow(getString(R.string.settings_no_sessions), muted));
			return;
		}
		for (MiniTaLib.SessionInfo item : sessions) {
			String text = (item.current ? getString(R.string.settings_current_session) : getString(R.string.settings_other_device)) +
					"  " + formatMessageTime(item.lastSeen) +
					"  #" + item.id;
			accountSessionsView.addView(sessionRow(text, item.current ? blend(primary, Color.WHITE, 0.18f) : textColor));
		}
		status.setText(getString(R.string.status_sessions_count, sessions.size()));
	}

	private TextView sessionRow(String value, int color) {
		TextView row = label(value);
		row.setTextColor(color);
		row.setTextSize(15);
		row.setPadding(pad, gap, pad, gap);
		row.setBackgroundDrawable(shape(surface, 0, elementRadius()));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.setMargins(0, 0, 0, gap / 2);
		row.setLayoutParams(lp);
		return row;
	}

	private void revokeOtherSessions() {
		revokeOtherSessions(null, true);
	}

	private void revokeOtherSessions(final View actionButton, final boolean primaryStyle) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		runButtonTask("revoke_sessions", actionButton, primaryStyle, new Task() {
			@Override
			public void run() throws Exception {
				final int revoked = c.revokeOtherSessions();
				final List<MiniTaLib.SessionInfo> sessions = c.getSessions();
				ui(new Runnable() {
					@Override
					public void run() {
						if (page == Page.SETTINGS_SESSIONS) renderSessions(sessions);
						status.setText(getString(R.string.status_revoked_sessions, revoked));
					}
				});
			}
		});
	}

	private void saveCloudPassword() {
		String value = accountCloudPassword == null ? "" : accountCloudPassword.getText().toString();
		setCloudPassword(value);
	}

	private void clearCloudPassword() {
		if (accountCloudPassword != null) accountCloudPassword.setText("");
		setCloudPassword("");
	}

	private String accountEmailText() {
		return myEmail == null ? "" : myEmail.trim();
	}

	private void requestAccountEmailCode(final EditText target, final String sentMessage) {
		requestAccountEmailCode(target, sentMessage, null, false);
	}

	private void requestAccountEmailCode(final EditText target, final String sentMessage, final View actionButton, final boolean primaryStyle) {
		final MiniTaLib c = ta;
		final String mail = accountEmailText();
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		if (mail.length() == 0) {
			status.setText(getString(R.string.status_account_email_required));
			return;
		}
		status.setText(getString(R.string.status_sending_code));
		runButtonTask("account_email_code", actionButton, primaryStyle, new Task() {
			@Override
			public void run() throws Exception {
				final String debugCode = c.startEmailAuth(mail);
				ui(new Runnable() {
					@Override
					public void run() {
						if (debugCode != null && debugCode.length() > 0 && target != null) {
							target.setText(debugCode);
						}
						status.setText(sentMessage == null || sentMessage.length() == 0 ? getString(R.string.status_email_code_sent) : sentMessage);
					}
				});
			}
		});
	}

	private void setCloudPassword(final String value) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		setCloudPasswordSaving(true, getString(R.string.cloud_password_saving));
		status.setText(getString(R.string.status_saving_cloud_password));
		run("cloud_password", new Task() {
			@Override
			public void run() throws Exception {
				try {
					c.setCloudPassword(value);
					ui(new Runnable() {
						@Override
						public void run() {
							String message = value == null || value.length() == 0 ? getString(R.string.cloud_password_cleared) : getString(R.string.cloud_password_saved);
							setCloudPasswordSaving(false, message);
							status.setText(value == null || value.length() == 0 ? getString(R.string.status_cloud_password_cleared) : getString(R.string.status_cloud_password_saved));
							if (accountCloudPassword != null) accountCloudPassword.setText("");
						}
					});
				} catch (final Exception e) {
					ui(new Runnable() {
						@Override
						public void run() {
							setCloudPasswordSaving(false, getString(R.string.cloud_password_save_failed, errorText(e)));
						}
					});
					throw e;
				}
			}
		});
	}

	private void setCloudPasswordSaving(boolean saving, String message) {
		setButtonBusy(cloudPasswordSaveButton, saving, getString(R.string.cloud_password_saving), getString(R.string.settings_save_password), true);
		setButtonEnabledStyle(cloudPasswordClearButton, !saving, false);
		if (accountCloudPassword != null) accountCloudPassword.setEnabled(!saving);
		if (accountCloudPasswordCode != null) accountCloudPasswordCode.setEnabled(!saving);
		if (cloudPasswordState != null) cloudPasswordState.setText(message == null ? "" : message);
	}

	private void resetCloudPassword() {
		final MiniTaLib c = ta;
		final String code = accountCloudPasswordCode == null ? "" : accountCloudPasswordCode.getText().toString().trim();
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		if (code.length() == 0) {
			status.setText(getString(R.string.status_email_code_required));
			return;
		}
		setCloudPasswordSaving(true, getString(R.string.cloud_password_resetting));
		status.setText(getString(R.string.status_resetting_cloud_password));
		run("cloud_password_reset", new Task() {
			@Override
			public void run() throws Exception {
				try {
					c.resetCloudPassword(code);
					ui(new Runnable() {
						@Override
						public void run() {
							if (accountCloudPasswordCode != null) accountCloudPasswordCode.setText("");
							if (accountCloudPassword != null) accountCloudPassword.setText("");
							setCloudPasswordSaving(false, getString(R.string.cloud_password_cleared));
							status.setText(getString(R.string.status_cloud_password_cleared));
						}
					});
				} catch (final Exception e) {
					ui(new Runnable() {
						@Override
						public void run() {
							setCloudPasswordSaving(false, getString(R.string.cloud_password_save_failed, errorText(e)));
						}
					});
					throw e;
				}
			}
		});
	}

	private void resetE2EKey() {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		status.setText(getString(R.string.status_resetting_e2e_key));
		run("e2e_reset", new Task() {
			@Override
			public void run() throws Exception {
				try {
					c.resetE2EKey();
					ui(new Runnable() {
						@Override
						public void run() {
							status.setText(getString(R.string.status_e2e_key_reset));
						}
					});
				} catch (final Exception e) {
					ui(new Runnable() {
						@Override
						public void run() {
							status.setText(getString(R.string.e2e_key_reset_failed, errorText(e)));
						}
					});
					throw e;
				}
			}
		});
	}

	private void deleteAccount() {
		final MiniTaLib c = ta;
		final String code = accountDeleteCode == null ? "" : accountDeleteCode.getText().toString().trim();
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		if (code.length() == 0) {
			status.setText(getString(R.string.status_email_code_required));
			return;
		}
		status.setText(getString(R.string.status_deleting_account));
		if (deleteAccountCodeButton != null) setButtonEnabledStyle(deleteAccountCodeButton, false, false);
		run("delete_account", new Task() {
			@Override
			public void run() throws Exception {
				try {
					c.deleteAccount(code);
					ui(new Runnable() {
						@Override
						public void run() {
							clearSessionAndShowLogin(R.string.status_account_deleted);
						}
					});
				} catch (final Exception e) {
					ui(new Runnable() {
						@Override
						public void run() {
							if (deleteAccountCodeButton != null) setButtonEnabledStyle(deleteAccountCodeButton, true, false);
							status.setText(getString(R.string.delete_account_failed, errorText(e)));
						}
					});
					throw e;
				}
			}
		});
	}

	private void requestEmailCode() {
		final String url = server();
		final String mail = email == null ? "" : email.getText().toString().trim();
		if (mail.length() == 0) return;
		long resendRemainingMs = emailCodeResendRemainingMs(mail);
		if (resendRemainingMs > 0) {
			status.setText(getString(R.string.status_send_code_again_in, formatCodeCooldown(resendRemainingMs)));
			updateEmailCodeCooldown();
			return;
		}
		setAuthLoading(true, true);
		status.setText(getString(R.string.status_sending_code));
		run("email_code", new Task() {
			@Override
			public void run() throws Exception {
				try {
					final MiniTaLib c = new MiniTaLib(MainActivity.this, url);
					final String debugCode = c.startEmailAuth(mail);
					ui(new Runnable() {
						@Override
						public void run() {
							emailCodeCooldownEmail = cooldownEmailKey(mail);
							emailCodeSentAtMs = System.currentTimeMillis();
							waitingEmailCode = true;
							authNeedsCloudPassword = false;
							pendingEmailCode = "";
							showLogin();
							if (debugCode != null && debugCode.length() > 0 && emailCode != null) {
								emailCode.setText(debugCode);
							}
							status.setText(getString(R.string.status_email_code_sent));
						}
					});
				} finally {
					ui(new Runnable() {
						@Override
						public void run() {
							setAuthLoading(false, true);
						}
					});
				}
			}
		});
	}

	private String currentEmailText() {
		return email == null ? "" : email.getText().toString().trim();
	}

	private String cooldownEmailKey(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.US);
	}

	private long emailCodeResendRemainingMs(String mail) {
		if (emailCodeSentAtMs <= 0) return 0;
		if (!emailCodeCooldownEmail.equals(cooldownEmailKey(mail))) return 0;
		long elapsed = System.currentTimeMillis() - emailCodeSentAtMs;
		long remaining = EMAIL_CODE_RESEND_DELAY_MS - elapsed;
		return remaining > 0 ? remaining : 0;
	}

	private String formatCodeCooldown(long remainingMs) {
		long seconds = Math.max(1, (remainingMs + 999) / 1000);
		return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
	}

	private void updateEmailCodeCooldown() {
		if (resendEmailCodeButton == null) return;
		long remaining = emailCodeResendRemainingMs(currentEmailText());
		if (remaining > 0) {
			setButtonEnabledStyle(resendEmailCodeButton, false, false);
			resendEmailCodeButton.setText(getString(R.string.action_send_again_timer, formatCodeCooldown(remaining)));
			main.removeCallbacks(emailCodeCooldownTick);
			main.postDelayed(emailCodeCooldownTick, 1000);
		} else {
			setButtonEnabledStyle(resendEmailCodeButton, true, false);
			resendEmailCodeButton.setText(getString(R.string.action_send_again));
			main.removeCallbacks(emailCodeCooldownTick);
		}
	}

	private void authEmail() {
		final String url = server();
		final String mail = email == null ? "" : email.getText().toString().trim();
		final String code = authNeedsCloudPassword ?
			pendingEmailCode :
			(emailCode == null ? "" : emailCode.getText().toString().trim());
		final String cloud = password == null ? "" : password.getText().toString();
		if (mail.length() == 0 || code.length() == 0) return;
		setAuthLoading(true, false);
		status.setText(getString(R.string.status_checking_code));
		run("email_auth", new Task() {
			@Override
			public void run() throws Exception {
				try {
					ta = new MiniTaLib(MainActivity.this, url);
					MiniTaLib.User u = ta.verifyEmailAuth(mail, code, cloud);
					finishAuth(url, u);
				} catch (RuntimeException e) {
					if (MiniTaLib.isCloudPasswordRequiredError(e)) {
						ui(new Runnable() {
							@Override
							public void run() {
								pendingEmailCode = code;
								authNeedsCloudPassword = true;
								showLogin();
								status.setText(getString(R.string.status_cloud_password_required));
							}
						});
						return;
					}
					throw e;
				} finally {
					ui(new Runnable() {
						@Override
						public void run() {
							setAuthLoading(false, false);
						}
					});
				}
			}
		});
	}

	private void resetAuthCloudPassword() {
		final String url = server();
		final String mail = email == null ? "" : email.getText().toString().trim();
		final String code = pendingEmailCode == null ? "" : pendingEmailCode.trim();
		if (mail.length() == 0 || code.length() == 0) {
			status.setText(getString(R.string.status_email_code_required));
			return;
		}
		setAuthLoading(true, false);
		status.setText(getString(R.string.status_resetting_cloud_password));
		run("email_auth_cloud_reset", new Task() {
			@Override
			public void run() throws Exception {
				try {
					ta = new MiniTaLib(MainActivity.this, url);
					MiniTaLib.User u = ta.resetCloudPassword(mail, code);
					finishAuth(url, u);
					ui(new Runnable() {
						@Override
						public void run() {
							status.setText(getString(R.string.status_cloud_password_cleared));
						}
					});
				} finally {
					ui(new Runnable() {
						@Override
						public void run() {
							setAuthLoading(false, false);
						}
					});
				}
			}
		});
	}

	private void finishAuth(final String url, MiniTaLib.User u) {
		applyOwnUser(u);
		lastUpdate = 0;
		waitingEmailCode = false;
		authNeedsCloudPassword = false;
		pendingEmailCode = "";

		seenMessages.clear();

		SessionStore.save(MainActivity.this, url, ta.token(), myLogin);
		SessionStore.lastUpdate(MainActivity.this, 0);
		SessionStore.backgroundLastUpdate(MainActivity.this, 0);

		startSyncService();

		ui(new Runnable() {
			@Override
			public void run() {
				status.setText(getString(R.string.status_online_as, displayOwnUser()));
				showChats();
			}
		});

		startPolling();
	}

	private void applyOwnUser(MiniTaLib.User u) {
		if (u == null) return;
		myID = u.id;
		myEmail = u.email;
		myLogin = u.login;
		myNick = u.nick;
		myVerified = u.verified;
		myBot = u.bot;
		myMessagePrivacy = normalizePrivacy(u.messagePrivacy);
		myCallPrivacy = normalizePrivacy(u.callPrivacy);
		myInvitePrivacy = normalizeInvitePrivacy(u.invitePrivacy);
	}

	private void applySettings() {
		String url = server();
		if (showStatusCheck != null) {
			SessionStore.showStatus(this, showStatusCheck.isChecked());
		}
		updateStatusVisibility();
		applyRootPadding(rootView);
		requestApplyInsetsCompat(rootView);
		if (ta != null && !ta.token().isEmpty()) {
			ta = new MiniTaLib(this, url, ta.token(), myLogin);
			SessionStore.save(this, url, ta.token(), myLogin);
			startSyncService();
		} else {
			ta = null;
			stopPolling();
		}
		status.setText(getString(R.string.status_server_set));
	}

	private void applyLanguage() {
		SessionStore.language(this, selectedLanguage());
		AppLocale.apply(this);
		setContentView(shell());
		setStatusBarColorCompat(bg);
		showSettings();
		status.setText(getString(R.string.status_language_set));
	}

	private void logout() {
		clearSessionAndShowLogin(R.string.status_logged_out);
	}

	private void handleInvalidToken() {
		clearSessionAndShowLogin(R.string.status_invalid_token);
	}

	private void clearSessionAndShowLogin(int statusRes) {
		stopPolling();
		voiceCall.stop();
		stopService(new Intent(this, MessageSyncService.class));
		SessionStore.clear(this);
		ta = null;
		myID = "";
		myEmail = "";
		myLogin = "";
		myNick = "";
		myVerified = false;
		myBot = false;
		myMessagePrivacy = "everyone";
		myCallPrivacy = "everyone";
		myInvitePrivacy = "everyone";
		currentPeer = "";
		currentPeerUser = null;
		currentPeerBanned = false;
		currentPeerBannedByMe = false;
		currentPeerBannedMe = false;
		waitingEmailCode = false;
		authNeedsCloudPassword = false;
		pendingEmailCode = "";
		seenMessages.clear();
		showLogin();
		status.setText(getString(statusRes));
	}

	private void loadChats() {
		loadChats(null, false);
	}

	private void loadChats(final View actionButton, final boolean primaryStyle) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		runButtonTask("chats", actionButton, primaryStyle, new Task() {
			@Override
			public void run() throws Exception {
				final List < MiniTaLib.Chat > chats = c.getChats();
				cacheSaveChats(chats);

				ui(new Runnable() {
					@Override
					public void run() {
						renderChats(chats, getString(R.string.source_online));
					}
				});
			}
		});
	}

	private void renderChats(List<MiniTaLib.Chat> chats, String source) {
		if (chats == null || chatRows == null) return;
		chatData.clear();
		chatData.addAll(chats);
		chatRows.clear();
		for (MiniTaLib.Chat chat : chats) {
			if (chat.peer != null && chat.peer.login != null && chat.peer.login.equals(currentPeer)) {
				currentPeerBanned = chat.banned;
				currentPeerBannedByMe = chat.bannedByMe;
				currentPeerBannedMe = chat.bannedMe;
			}
			String last = chat.last == null ? "" : chatLastText(chat.last);
			chatRows.add(MessageRow.chat(displayUser(chat.peer), last));
		}
		status.setText(getString(R.string.status_chats_count, chats.size(), source));
	}

	private void loadHistory() {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		currentPeer = peer == null ? currentPeer : peer.getText().toString().trim();
		if (currentPeer.isEmpty()) return;
		final String peerName = currentPeer;
		loadingOlderMessages = false;
		historyLoaded = false;
		hasOlderMessages = false;
		loadCachedHistory(peerName);
		run("history", new Task() {
			@Override
			public void run() throws Exception {
				final MiniTaLib.HistoryPage pageData =
					c.getHistoryPageBefore(peerName, 0, HISTORY_PAGE);
				try {
					c.markRead(peerName);
				} catch (Exception ignored) {
				}
				final String resolvedPeer = resolvedPeerName(pageData.peer, peerName);
				cacheSaveHistory(resolvedPeer, pageData.messages);

				ui(new Runnable() {
					@Override
					public void run() {
						if (pageData.peer != null
								&& ((pageData.peer.login != null && pageData.peer.login.length() > 0)
								|| (pageData.peer.id != null && pageData.peer.id.length() > 0))) {
							currentPeer = resolvedPeer;
							currentPeerUser = pageData.peer;
							refreshCurrentPeerNameView();
							updateCallButton();
						}
						renderHistory(pageData.messages, resolvedPeer, false);
					}
				});
			}
		});
	}

	private void renderHistory(List<MiniTaLib.Message> history, String peerName, boolean cached) {
		if (messageRows == null || !peerName.equals(currentPeer) || history == null) return;
		updateCurrentPeerUser(history, peerName);
		seenMessages.clear();
		oldestMessage = 0;
		ArrayList<MessageRow> rows = new ArrayList<MessageRow>();
		for (MiniTaLib.Message message : history) {
			if (message != null && seenMessages.add(message.id)) {
				if (oldestMessage == 0 || message.id < oldestMessage) oldestMessage = message.id;
				rows.add(toMessageRow(message));
			}
		}
		messageRows.replaceRows(rows);
		historyLoaded = !cached;
		hasOlderMessages = !cached && history.size() == HISTORY_PAGE;
		if (messageList != null && messageRows.getCount() > 0) {
			messageList.setSelection(messageRows.getCount() - 1);
		}
		refreshChatInput();
		status.setText(getString(R.string.status_messages_count, history.size(), cached ? getString(R.string.status_cached_suffix) : ""));
	}

	private void updateCurrentPeerUser(List<MiniTaLib.Message> history, String peerName) {
		if (peerName == null || history == null) return;
		if (currentPeerUser != null && peerName.equals(resolvedPeerName(currentPeerUser, peerName))) return;
		for (MiniTaLib.Message message : history) {
			MiniTaLib.User candidate = messagePeerUser(message);
			if (candidate != null && peerName.equals(resolvedPeerName(candidate, peerName))) {
				currentPeerUser = candidate;
				refreshCurrentPeerNameView();
				updateCallButton();
				return;
			}
		}
	}

	private void loadOlderHistory() {
		final MiniTaLib c = ta;
		if (c == null || loadingOlderMessages || oldestMessage <= 0) return;
		final String peerName = currentPeer;
		final long before = oldestMessage;
		loadingOlderMessages = true;
		run("older", new Task() {
			@Override
			public void run() throws Exception {
				final List < MiniTaLib.Message > history =
					c.getHistoryBefore(peerName, before, HISTORY_PAGE);

				ui(new Runnable() {
					@Override
					public void run() {
						loadingOlderMessages = false;

						if (messageRows == null ||
							messageList == null ||
							!peerName.equals(currentPeer)) {
							return;
						}

						int first = messageList.getFirstVisiblePosition();

						View topView = messageList.getChildAt(0);

						int top = topView == null ?
							0 :
							topView.getTop();

						ArrayList<MessageRow> rows = new ArrayList<MessageRow>();

						for (MiniTaLib.Message m: history) {
							if (seenMessages.add(m.id)) {

								if (oldestMessage == 0 || m.id < oldestMessage) {
									oldestMessage = m.id;
								}

								rows.add(toMessageRow(m));
							}
						}

						hasOlderMessages =
							history.size() == HISTORY_PAGE && rows.size() > 0;

						if (!rows.isEmpty()) {
							messageRows.insertRows(rows, 0);
							messageList.setSelectionFromTop(first + rows.size(), top);
						}
					}
				});
			}
		});
	}

	private void send() {
		if (currentPeerBanned) {
			status.setText(getString(R.string.chat_banned));
			return;
		}
		currentPeer = peer == null ? currentPeer : peer.getText().toString().trim();
		final String msg = text == null ? "" : text.getText().toString().trim();
		if (currentPeer.isEmpty() || msg.isEmpty()) return;
		sendChatMessage(currentPeer, msg, true);
	}

	private void sendBotStart() {
		sendBotStart(null);
	}

	private void sendBotStart(final View actionButton) {
		if (currentPeer == null || currentPeer.length() == 0) return;
		sendChatMessage(currentPeer, "/start", false, actionButton, true);
	}

	private void sendChatMessage(final String peerName, final String msg, final boolean clearInput) {
		sendChatMessage(peerName, msg, clearInput, null, false);
	}

	private void sendChatMessage(final String peerName, final String msg, final boolean clearInput, final View actionButton, final boolean primaryStyle) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		if (peerName == null || peerName.length() == 0 || msg == null || msg.length() == 0) return;
		setSendLoading(true);
		setActionButtonLoading(actionButton, true, primaryStyle);
		run("send", new Task() {
			@Override
			public void run() throws Exception {
				try {
					append(currentPeerIsRoom() ? c.sendPlainMessage(peerName, msg) : c.sendMessage(peerName, msg));

					ui(new Runnable() {
						@Override
						public void run() {
							if (clearInput && text != null) text.setText("");
						}
					});
				} finally {
					ui(new Runnable() {
						@Override
						public void run() {
							setSendLoading(false);
							setActionButtonLoading(actionButton, false, primaryStyle);
						}
					});
				}
			}
		});
	}

	private void handleMessageButton(final MiniTaLib.Message message, final MiniTaLib.Button button, final Button clickedButton) {
		if (button == null) return;
		if (button.url != null && button.url.length() > 0) {
			openUrl(button.url);
			return;
		}
		if (button.payDsr > 0) {
			String payTo = message == null || message.from == null ? "" : message.from.login;
			showDastarsPaymentSheet(payTo, button.payDsr);
			return;
		}
		if (button.callback == null || button.callback.length() == 0) return;
		final MiniTaLib c = ta;
		if (c == null || message == null || message.from == null || message.from.login.length() == 0) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		final String botLogin = message.from.login;
		if (clickedButton != null) {
			setButtonEnabledStyle(clickedButton, false, true);
			setButtonRequestBusy(clickedButton, true);
		}
		run("button_callback", new Task() {
			@Override
			public void run() throws Exception {
				try {
					c.sendCallback(botLogin, message.id, button.callback);
				} finally {
					ui(new Runnable() {
						@Override
						public void run() {
							if (clickedButton != null) {
								setButtonRequestBusy(clickedButton, false);
								setButtonEnabledStyle(clickedButton, true, true);
							}
						}
					});
				}
			}
		});
	}

	private void startVoice() {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		currentPeer = peer == null ? currentPeer : peer.getText().toString().trim();
		if (currentPeer.isEmpty()) return;
		final String peerName = currentPeer;
		if (!hasPermissionCompat(PERMISSION_RECORD_AUDIO)) {
			requestPermissionsCompat(new String[] {
				PERMISSION_RECORD_AUDIO
			}, REQ_MICROPHONE);
			status.setText(getString(R.string.status_allow_microphone_call_again));
			return;
		}
		if (voiceCall.running()) {
			status.setText(getString(R.string.status_call_already_active));
			return;
		}
		activeVoiceRoom = false;
		++voiceConnectGeneration;
		setCallState("calling", peerName);
		run("call", new Task() {
			@Override
			public void run() throws Exception {
				try {
					c.sendCall(peerName, "invite");
				} catch (final Exception e) {
					ui(new Runnable() {
						@Override
						public void run() {
							if (peerName.equals(activeCallPeer) && "calling".equals(callState)) {
								setCallState("failed", peerName);
							}
							status.setText(getString(R.string.status_call_error, errorText(e)));
						}
					});
				}
			}
		});
		status.setText(getString(R.string.status_calling_peer, peerName));
	}

	private void toggleVoice() {
		if (currentPeerBanned) {
			status.setText(getString(R.string.chat_banned));
			return;
		}
		if (currentPeerIsBot()) {
			status.setText(getString(R.string.status_bots_cannot_receive_calls));
			return;
		}
		if (currentPeerIsChannel()) {
			status.setText(getString(R.string.status_voice_not_available_for_channels));
			return;
		}
		if (currentPeerIsGroup()) {
			if (voiceCall.running() || (currentPeer.equals(activeCallPeer) && !"idle".equals(callState) && !"failed".equals(callState))) {
				endVoice();
			} else {
				startGroupVoice();
			}
			return;
		}
		if (voiceCall.running() || (!"idle".equals(callState) && !"failed".equals(callState))) endVoice();
		else startVoice();
	}

	private void startGroupVoice() {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		if (currentPeer == null || currentPeer.length() == 0) return;
		if (!hasPermissionCompat(PERMISSION_RECORD_AUDIO)) {
			pendingVoiceRoom = currentPeer;
			requestPermissionsCompat(new String[] { PERMISSION_RECORD_AUDIO }, REQ_MICROPHONE);
			status.setText(getString(R.string.status_allow_microphone_call_again));
			return;
		}
		if (voiceCall.running()) {
			status.setText(getString(R.string.status_call_already_active));
			return;
		}
		final String roomName = currentPeer;
		activeVoiceRoom = true;
		++voiceConnectGeneration;
		setCallState("connecting", roomName);
		startVoiceConnection(c, roomName, getString(R.string.status_joining_voice_channel));
	}

	// ---------------------------------------------------------------------
	// Image picking and sending helpers
	// ---------------------------------------------------------------------

	/** Launches an intent to let the user pick an image from any provider. */
	private void pickImage() {
		Intent i = new Intent(Intent.ACTION_GET_CONTENT);
		i.setType("image/*");
		i.addCategory(Intent.CATEGORY_OPENABLE);
		startActivityForResult(Intent.createChooser(i, getString(R.string.chooser_select_picture)), REQ_PICK_IMAGE);
	}

	// Generic file picker - any mime type
	private void pickFile() {
		Intent i = new Intent(Intent.ACTION_GET_CONTENT);
		i.setType("*/*");
		i.addCategory(Intent.CATEGORY_OPENABLE);
		startActivityForResult(Intent.createChooser(i, getString(R.string.chooser_select_file)), REQ_PICK_FILE);
	}

	private void showAttachmentActions() {
		showActionDialog(new String[] {
			getString(R.string.attachment_photo),
			getString(R.string.attachment_file),
			getString(R.string.payment_transfer_title)
		}, new ChoiceHandler() {
			@Override
			public void onChoice(int which) {
				if (which == 0) {
					pickImage();
				} else if (which == 1) {
					pickFile();
				} else if (which == 2) {
					showDastarsTransferDialog(currentPeer);
				}
			}
		});
	}

	private void showDastarsTransferDialog(String defaultRecipient) {
		final String recipient = defaultRecipient == null ? "" : defaultRecipient.trim();
		final EditText amountField = input(getString(R.string.wallet_amount_hint), false);
		amountField.setInputType(InputType.TYPE_CLASS_NUMBER);
		final EditText commentField = input(getString(R.string.wallet_comment_hint), false);
		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(0, gap, 0, 0);
		TextView recipientView = label(getString(R.string.payment_recipient, recipient));
		recipientView.setTextColor(muted);
		final TextView balanceView = label(walletBalanceLabel());
		balanceView.setTextColor(blend(primary, Color.WHITE, 0.18f));
		box.addView(spaced(recipientView));
		box.addView(spaced(balanceView));
		box.addView(spaced(amountField));
		box.addView(spaced(commentField));
		refreshWalletBalanceLabel(balanceView);
		showContentDialog(getString(R.string.payment_transfer_title), box, getString(R.string.action_send), new Runnable() {
			@Override
			public void run() {
				transferDastars(recipient, amountField.getText().toString().trim(), commentField.getText().toString().trim());
			}
		}, getString(R.string.action_cancel));
	}

	private void showDastarsPaymentSheet(final String to, final long amount) {
		if (to == null || to.length() == 0 || amount <= 0) {
			status.setText(getString(R.string.status_bad_dsr_invoice));
			return;
		}
		final Dialog dialog = new Dialog(this);
		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(pad, pad, pad, pad);
		box.setBackgroundDrawable(shape(surface, 0, buttonRadius()));

		TextView title = title(getString(R.string.payment_title));
		title.setText(getString(R.string.payment_pay_title, amount));
		box.addView(title, new LinearLayout.LayoutParams(-1, -2));

		TextView details = label(getString(R.string.payment_recipient, to));
		details.setTextColor(muted);
		LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(-1, -2);
		detailsLp.setMargins(0, 0, 0, gap);
		box.addView(details, detailsLp);

		final PaymentSliderView slider = new PaymentSliderView(this, getString(R.string.payment_slide_hint));
		slider.setContentDescription(getString(R.string.payment_slide_hint));
		slider.setOnConfirmAction(new Runnable() {
			@Override
			public void run() {
				dialog.dismiss();
				transferDastars(to, String.valueOf(amount), "");
			}
		});
		box.addView(slider, new LinearLayout.LayoutParams(-1, dp(56)));

		Button cancel = button(getString(R.string.action_cancel), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(-1, -2);
		cancelLp.setMargins(0, gap, 0, 0);
		box.addView(cancel, cancelLp);

		dialog.setContentView(box);
		showStyledDialog(dialog);
	}

	private void transferDastars(final String to, String rawAmount, final String comment) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		if (to == null || to.length() == 0 || rawAmount == null || rawAmount.length() == 0) return;
		final long amount;
		try {
			amount = Long.parseLong(rawAmount);
		} catch (NumberFormatException e) {
			status.setText(getString(R.string.status_bad_dsr_amount));
			return;
		}
		if (amount <= 0) {
			status.setText(getString(R.string.status_bad_dsr_amount));
			return;
		}
		run("wallet_send", new Task() {
			@Override
			public void run() throws Exception {
				c.sendDastars(to, amount, comment == null ? "" : comment.trim());
				ui(new Runnable() {
					@Override
					public void run() {
						status.setText(getString(R.string.status_dsr_sent));
						if (page == Page.WALLET) loadWallet();
						if (page == Page.WALLET_HISTORY) loadWalletHistory();
					}
				});
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode != RESULT_OK || data == null) return;
		Uri uri = data.getData();
		if (uri == null) return;
		if (requestCode == REQ_PICK_IMAGE) {
			sendImageUri(uri);
		} else if (requestCode == REQ_PICK_FILE) {
			sendFileUri(uri);
		}
	}

	private void sendImageUri(Uri uri) {
		sendPickedFile(uri, true);
	}

	private void sendFileUri(Uri uri) {
		sendPickedFile(uri, false);
	}

	private void sendPickedFile(Uri uri, boolean imageOnly) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		currentPeer = peer == null ? currentPeer : peer.getText().toString().trim();
		if (currentPeer.isEmpty()) return;
		final String peerName = currentPeer;
		final Uri pickedUri = uri;
		status.setText(imageOnly ? getString(R.string.status_preparing_image) : getString(R.string.status_preparing_file));
		run(imageOnly ? "send_image" : "send_file", new Task() {
			@Override
			public void run() throws Exception {
				String type = getContentResolver().getType(pickedUri);
				if (type == null || type.length() == 0) type = "application/octet-stream";
				if (imageOnly && !type.toLowerCase(Locale.US).startsWith("image/")) {
					throw new IOException(getString(R.string.status_not_an_image));
				}
				String displayName = queryDisplayName(pickedUri);
				if (displayName == null || displayName.trim().isEmpty()) displayName = imageOnly ? getString(R.string.image_fallback_name) : getString(R.string.file_fallback_name);
				InputStream is = null;
				try {
					is = getContentResolver().openInputStream(pickedUri);
					if (is == null) throw new IOException(getString(R.string.status_file_not_available));
					final byte[] bytes = readAllLimited(is, MAX_UPLOAD_BYTES);
					if (bytes.length == 0) throw new IOException(getString(R.string.status_empty_file));
					final String name = displayName;
					final String mime = type;
					append(c.uploadFile(peerName, name, mime, bytes));
					ui(new Runnable() {
						@Override
						public void run() {
							if (text != null) text.setText("");
						}
					});
				} finally {
					if (is != null) {
						try {
							is.close();
						} catch (IOException ignored) {
						}
					}
				}
			}
		});
	}

	private byte[] readAllLimited(InputStream is, int maxBytes) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = is.read(buffer)) != -1) {
			if (out.size() + read > maxBytes) {
				throw new IOException(getString(R.string.status_file_too_large, formatBytes((long)out.size() + read)));
			}
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	/** Helper to obtain the display name of a content URI. */
	private String queryDisplayName(Uri uri) {
		android.database.Cursor cursor = null;
		try {
			cursor = getContentResolver().query(uri, new String[] {
				"_display_name"
			}, null, null, null);
			if (cursor != null && cursor.moveToFirst()) {
				return cursor.getString(0);
			}
		} catch (Exception ignored) {
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return null;
	}

	private void downloadFile(final MiniTaLib.FileInfo file) {
		downloadFile(file, null);
	}

	private void downloadFile(final MiniTaLib.FileInfo file, final View actionButton) {
		if (openDownloadedFile(file)) {
			return;
		}

		final MiniTaLib c = ta;
		if (c == null || file == null || file.id == null || file.id.length() == 0) {
			status.setText(getString(R.string.status_file_not_available));
			return;
		}
		final String fileName = safeFileName(file.name);
		final File local = downloadedFileFor(file);
		if (local == null) {
			status.setText(getString(R.string.status_download_folder_not_available));
			return;
		}
		status.setText(getString(R.string.status_downloading, fileName));
		setActionButtonLoading(actionButton, true, false);
		io.execute(new Runnable() {
			@Override
			public void run() {
				try {
					if (local.exists() && !isCompleteDownloadedFile(local, file)) {
						local.delete();
					}
					int maxBytes = file.size > 0 && file.size < Integer.MAX_VALUE
							? (int)Math.min(file.size + 1024, 64L * 1024 * 1024)
							: 64 * 1024 * 1024;
					byte[] data = c.downloadFileBytes(file.id, maxBytes);
					writeFile(local, data);
					ui(new Runnable() {
						@Override
						public void run() {
							status.setText(getString(R.string.status_downloaded, fileName));
							openDownloadedFile(file);
						}
					});
				} catch (final Exception e) {
					ui(new Runnable() {
						@Override
						public void run() {
							status.setText(getString(R.string.status_download_error, e.getMessage()));
						}
					});
				} finally {
					ui(new Runnable() {
						@Override
						public void run() {
							setActionButtonLoading(actionButton, false, false);
						}
					});
				}
			}
		});
	}

	private void writeFile(File target, byte[] data) throws IOException {
		FileOutputStream output = null;
		try {
			output = new FileOutputStream(target);
			output.write(data);
		} finally {
			if (output != null) {
				output.close();
			}
		}
	}

	private boolean openDownloadedFile(MiniTaLib.FileInfo file) {
		File local = downloadedFileFor(file);
		if (!isCompleteDownloadedFile(local, file)) {
			return false;
		}
		try {
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(localFileUri(local), fileMimeType(file));
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			startActivity(intent);
			status.setText(getString(R.string.status_opened, local.getName()));
		} catch (ActivityNotFoundException e) {
			status.setText(getString(R.string.status_no_app_to_open, local.getName()));
		} catch (Exception e) {
			status.setText(getString(R.string.status_open_error, e.getMessage()));
		}
		return true;
	}

	private File downloadedFileFor(MiniTaLib.FileInfo file) {
		if (file == null) return null;
		File dir = getExternalFilesDir(null);
		if (dir == null) dir = getFilesDir();
		if (dir == null) return null;
		return new File(dir, safeFileName(file.name));
	}

	private boolean isCompleteDownloadedFile(File local, MiniTaLib.FileInfo file) {
		if (local == null || !local.isFile() || local.length() <= 0) {
			return false;
		}
		return file == null || file.size <= 0 || local.length() >= file.size;
	}

	private Uri localFileUri(File local) {
		if (Build.VERSION.SDK_INT >= 24) {
			return Uri.parse("content://" + getPackageName() + ".localfiles/" + Uri.encode(local.getName()));
		}
		return Uri.fromFile(local);
	}

	private String fileMimeType(MiniTaLib.FileInfo file) {
		if (file != null && file.mime != null && file.mime.length() > 0) {
			return file.mime;
		}
		return "application/octet-stream";
	}

	private String safeFileName(String name) {
		if (name == null || name.trim().isEmpty()) return "file";
		String clean = name.replace('/', '_').replace('\\', '_');
		return clean.length() > 120 ? clean.substring(0, 120) : clean;
	}

	private String formatBytes(long value) {
		if (value < 1024) return value + " B";
		double kb = value / 1024.0;
		if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
		double mb = kb / 1024.0;
		return String.format(Locale.US, "%.1f MB", mb);
	}

	private boolean isImageFile(MiniTaLib.FileInfo file) {
		if (file == null) return false;
		String mime = file.mime == null ? "" : file.mime.toLowerCase(Locale.US);
		if (mime.startsWith("image/")) return true;
		String name = file.name == null ? "" : file.name.toLowerCase(Locale.US);
		return name.endsWith(".jpg")
				|| name.endsWith(".jpeg")
				|| name.endsWith(".png")
				|| name.endsWith(".gif")
				|| name.endsWith(".webp")
				|| name.endsWith(".bmp");
	}

	private String imageCacheKey(MiniTaLib.FileInfo file) {
		if (file == null) return "";
		if (file.id != null && file.id.length() > 0) return file.id;
		return (file.name == null ? "" : file.name) + ":" + file.size;
	}

	private Bitmap cachedImagePreview(String key) {
		synchronized (imagePreviewLoading) {
			return imagePreviewCache.get(key);
		}
	}

	private String cachedImagePreviewError(String key) {
		synchronized (imagePreviewLoading) {
			return imagePreviewErrors.get(key);
		}
	}

	private void startImagePreviewLoad(final MiniTaLib.FileInfo file, final android.widget.BaseAdapter adapter) {
		final MiniTaLib c = ta;
		if (c == null || file == null || file.id == null || file.id.length() == 0) return;
		final String key = imageCacheKey(file);
		if (key.length() == 0) return;
		synchronized (imagePreviewLoading) {
			if (imagePreviewCache.containsKey(key) || imagePreviewErrors.containsKey(key) || imagePreviewLoading.contains(key)) {
				return;
			}
			imagePreviewLoading.add(key);
		}
		io.execute(new Runnable() {
			@Override
			public void run() {
				Bitmap decoded = null;
				String error = null;
				try {
					byte[] data = c.downloadFileBytes(file.id, MAX_IMAGE_PREVIEW_BYTES);
					decoded = decodePreviewBitmap(data, MAX_IMAGE_PREVIEW_PX);
					if (decoded == null) {
						error = "invalid image";
					}
				} catch (Exception e) {
					error = e.getMessage();
					if (error == null || error.length() == 0) error = e.getClass().getSimpleName();
				}
				final Bitmap out = decoded;
				final String outError = error;
				ui(new Runnable() {
					@Override
					public void run() {
						synchronized (imagePreviewLoading) {
							imagePreviewLoading.remove(key);
							if (out != null) {
								imagePreviewCache.put(key, out);
								imagePreviewErrors.remove(key);
							} else {
								imagePreviewErrors.put(key, outError == null ? "preview error" : outError);
							}
						}
						adapter.notifyDataSetChanged();
					}
				});
			}
		});
	}

	private Bitmap decodePreviewBitmap(byte[] data, int maxSide) {
		if (data == null || data.length == 0) return null;
		BitmapFactory.Options probe = new BitmapFactory.Options();
		probe.inJustDecodeBounds = true;
		BitmapFactory.decodeByteArray(data, 0, data.length, probe);
		if (probe.outWidth <= 0 || probe.outHeight <= 0) return null;
		int sample = 1;
		while (probe.outWidth / sample > maxSide || probe.outHeight / sample > maxSide) {
			sample *= 2;
		}
		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inSampleSize = sample;
		return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
	}

	private void acceptIncomingCall(final String peerName) {
		clearIncomingCallUi();
		currentPeer = peerName;
		if (page != Page.CALL) {
			if (page != Page.CHAT) showChat();
			if (peer != null) peer.setText(peerName);
		}
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		if (!hasPermissionCompat(PERMISSION_RECORD_AUDIO)) {
			pendingAcceptedPeer = peerName;
			requestPermissionsCompat(new String[] {
				PERMISSION_RECORD_AUDIO
			}, REQ_MICROPHONE);
			status.setText(getString(R.string.status_allow_microphone_accept));
			return;
		}
		++voiceConnectGeneration;
		activeVoiceRoom = false;
		setCallState("connecting", peerName);
		status.setText(getString(R.string.status_answering_peer, peerName));
		run("call", new Task() {
			@Override
			public void run() throws Exception {
				try {
					c.sendCall(peerName, "accept");
					ui(new Runnable() {
						@Override
						public void run() {
							if (peerName.equals(activeCallPeer) && "connecting".equals(callState)) {
								startVoiceConnection(c, peerName, getString(R.string.status_answering_peer, peerName));
							}
						}
					});
				} catch (final Exception e) {
					ui(new Runnable() {
						@Override
						public void run() {
							if (peerName.equals(activeCallPeer) && "connecting".equals(callState)) {
								setCallState("failed", peerName);
							}
							status.setText(getString(R.string.status_call_error, errorText(e)));
						}
					});
				}
			}
		});
	}

	private void declineIncomingCall(final String peerName) {
		clearIncomingCallUi();
		final MiniTaLib c = ta;
		if (c != null) {
			run("call", new Task() {
				@Override
				public void run() throws Exception {
					c.sendCall(peerName, "reject");
				}
			});
		}
		setCallState("idle", "");
		status.setText(getString(R.string.status_call_declined));
	}

	private void startVoiceConnection(MiniTaLib c, String peerName, String connectingText) {
		if (c == null) {
			setCallState("failed", peerName);
			status.setText(getString(R.string.status_voice_sign_in_first));
			return;
		}
		if (voiceCall.running()) {
			status.setText(getString(R.string.status_call_already_active));
			return;
		}
		status.setText(connectingText);
		activeCallPeer = peerName;
		if ("idle".equals(callState)) setCallState("connecting", peerName);
		if (activeVoiceRoom) {
			loadVoiceParticipants();
			main.removeCallbacks(voiceParticipantsPoll);
			main.postDelayed(voiceParticipantsPoll, 1000);
		}
		final MiniTaLib client = c;
		final String targetPeer = peerName;
		final int generation = voiceConnectGeneration;
		run("voice", new Task() {
			@Override
			public void run() throws Exception {
				try {
					final String url = client.voiceUrl(targetPeer);
					if (generation != voiceConnectGeneration || "idle".equals(callState)) {
						return;
					}
					voiceCall.start(MainActivity.this, url, new VoiceCall.Listener() {
						@Override
						public void onState(final String s) {
							ui(new Runnable() {
								@Override
								public void run() {
									status.setText(voiceStatusText(s));
									if (VoiceCall.STATE_CONNECTED.equals(s)) {
										if ("connecting".equals(callState)) markCallStarted(activeCallPeer);
										if (activeVoiceRoom) {
											loadVoiceParticipants();
										}
									} else if (VoiceCall.STATE_CONNECTION_CLOSED.equals(s)) {
										finishCall(activeCallPeer, getString(R.string.call_ended));
									} else if (s != null && (s.startsWith(VoiceCall.STATE_ERROR_PREFIX) || s.startsWith(VoiceCall.STATE_SEND_ERROR_PREFIX))) {
										setCallState("failed", activeCallPeer);
									}
									updateCallButton();
								}
							});
						}
					});
				} catch (final Exception e) {
					ui(new Runnable() {
						@Override
						public void run() {
							status.setText(getString(R.string.status_voice_error, errorText(e)));
							setCallState("failed", targetPeer);
						}
					});
				}
			}
		});
	}

	private void endVoice() {
		voiceConnectGeneration++;
		final MiniTaLib c = ta;
		final String peerName = activeCallPeer.isEmpty() ? currentPeer : activeCallPeer;
		if (!activeVoiceRoom && c != null && peerName != null && !peerName.isEmpty()) {
			run("call", new Task() {
				@Override
				public void run() throws Exception {
					c.sendCall(peerName, "end");
				}
			});
		}
		finishCall(peerName, getString(R.string.call_ended));
	}

	private void startPolling() {
		if (!activityResumed || ta == null || polling) return;
		polling = true;
		foregroundPollingActive = true;
		final int generation = ++pollingGeneration;
		io.execute(new Runnable() {
			@Override
			public void run() {
				int failures = 0;
				while (polling && generation == pollingGeneration && ta != null) {
					try {
						MiniTaLib client = ta;
						if (client == null) break;
						long newestUpdate = lastUpdate;
						List<MiniTaLib.Update> updates = client.getUpdates(lastUpdate, 30);
						if (!polling || generation != pollingGeneration || !activityResumed) {
							break;
						}
						for (MiniTaLib.Update u: updates) {
							if (u.id > newestUpdate) newestUpdate = u.id;

							handleUpdate(u);
						}
						if (newestUpdate > lastUpdate) {
							lastUpdate = newestUpdate;
							SessionStore.lastUpdate(MainActivity.this, lastUpdate);
						}
						failures = 0;
					} catch (final Exception e) {
						if (!polling || generation != pollingGeneration || !activityResumed) {
							break;
						}
						if (MiniTaLib.isInvalidTokenError(e)) {
							ui(new Runnable() {
								@Override
								public void run() {
									handleInvalidToken();
								}
							});
							break;
						}
						final long retryDelay = pollRetryDelayMs(failures++);
						ui(new Runnable() {
							@Override
							public void run() {
								status.setText(getString(R.string.status_poll_retry, errorText(e), retryDelay / 1000));
							}
						});

						if (!sleepPollingRetry(retryDelay, generation)) {
							break;
						}
					}
				}
				if (generation == pollingGeneration) {
					polling = false;
					foregroundPollingActive = false;
				}
			}
		});
	}

	private void stopPolling() {
		handoffForegroundOffsetToBackground();
		polling = false;
		foregroundPollingActive = false;
		pollingGeneration++;
	}

	private long pollRetryDelayMs(int failures) {
		long delay = 1000L << Math.min(failures, 4);
		return Math.min(delay, 30000L);
	}

	private boolean sleepPollingRetry(long ms, int generation) {
		long until = System.currentTimeMillis() + ms;
		while (polling && generation == pollingGeneration && activityResumed) {
			long remaining = until - System.currentTimeMillis();
			if (remaining <= 0) return true;
			sleep(Math.min(remaining, 250L));
		}
		return false;
	}

	private void handoffForegroundOffsetToBackground() {
		if (lastUpdate <= 0 || !SessionStore.hasSession(this)) return;
		long background = SessionStore.backgroundLastUpdate(this);
		if (lastUpdate > background) {
			SessionStore.backgroundLastUpdate(this, lastUpdate);
		}
	}

	private void handleUpdate(final MiniTaLib.Update u) {
		if (u == null) return;
		if ("chat_update".equals(u.type) || "chat_removed".equals(u.type) || "chat_deleted".equals(u.type)) {
			handleRoomUpdate(u);
			return;
		}
		if ("message".equals(u.type)) {
			append(u.message);
			return;
		}
		if ("message_read".equals(u.type)) {
			applyMessageUpdate(u.message);
			return;
		}
		if ("message_delete".equals(u.type)) {
			applyMessageDelete(u.message);
			return;
		}
		MiniTaLib.Call call = u.call;
		if (call == null || call.from == null) return;
		final String peerName = callPeer(call);
		final boolean fromMe = isOwnUser(call.from);
		ui(new Runnable() {
			@Override
			public void run() {
				if ("call_invite".equals(u.type)) {
					if (fromMe || isStaleIncomingCall(call)) {
						cancelIncomingCallNotification();
						return;
					}
					showIncomingCall(peerName);

					} else if ("call_accept".equals(u.type)) {
						if (fromMe) {
						finishIncomingOnOtherDevice(peerName, getString(R.string.call_answered_other_device));
						return;
					}
					if (voiceCall.running()) {
						markCallStarted(peerName);
						status.setText(getString(R.string.status_peer_accepted_call, peerName));
					} else if (peerName.equals(activeCallPeer)
							&& ("calling".equals(callState) || "connecting".equals(callState) || "failed".equals(callState))) {
						if (!hasPermissionCompat(PERMISSION_RECORD_AUDIO)) {
							pendingOutgoingConnectPeer = peerName;
							requestPermissionsCompat(new String[] { PERMISSION_RECORD_AUDIO }, REQ_MICROPHONE);
							status.setText(getString(R.string.status_allow_microphone_connect));
							return;
						}
						++voiceConnectGeneration;
						setCallState("connecting", peerName);
						startVoiceConnection(ta, peerName, getString(R.string.status_peer_accepted_call, peerName));
					} else {
						setCallState("connecting", peerName);
						status.setText(getString(R.string.status_peer_accepted_call, peerName));
					}

				} else if ("call_reject".equals(u.type)) {
					finishCall(peerName, fromMe ? getString(R.string.call_declined_other_device) : getString(R.string.status_call_declined));
					status.setText(fromMe ? getString(R.string.status_call_declined_other_device) : getString(R.string.status_peer_declined_call, peerName));

				} else if ("call_end".equals(u.type)) {
					finishCall(peerName, fromMe ? getString(R.string.call_ended_other_device) : getString(R.string.call_ended));
					status.setText(fromMe ? getString(R.string.status_call_ended_other_device) : getString(R.string.status_peer_ended_call, peerName));
				}
			}
		});
	}

	private String callPeer(MiniTaLib.Call call) {
		return callPeerFor(myID, myLogin, call);
	}

	private boolean isOwnUser(MiniTaLib.User user) {
		return isOwnUserFor(myID, myLogin, user);
	}

	static String callPeerFor(String ownID, String ownLogin, MiniTaLib.Call call) {
		if (call == null) return "";
		if (call.from != null && !isOwnUserFor(ownID, ownLogin, call.from)) return userAddress(call.from);
		if (call.to != null) return userAddress(call.to);
		return userAddress(call.from);
	}

	static boolean isOwnUserFor(String ownID, String ownLogin, MiniTaLib.User user) {
		if (user == null) return false;
		if (ownID != null && ownID.length() > 0 && ownID.equals(user.id)) return true;
		return ownLogin != null && ownLogin.length() > 0 && ownLogin.equals(user.login);
	}

	private static String userAddress(MiniTaLib.User user) {
		if (user == null) return "";
		if (user.login != null && user.login.length() > 0) return user.login;
		return user.id == null ? "" : user.id;
	}

	private boolean isStaleIncomingCall(MiniTaLib.Call call) {
		if (call == null || call.date <= 0) return false;
		long age = System.currentTimeMillis() / 1000L - call.date;
		return age > MAX_INCOMING_CALL_AGE_SEC;
	}

	private void finishIncomingOnOtherDevice(String peerName, String label) {
		if (!"incoming".equals(callState)) return;
		if (peerName == null || peerName.length() == 0 || !peerName.equals(activeCallPeer)) return;
		finishCall(peerName, label);
	}

	private void markCallStarted(String peerName) {
		activeCallPeer = peerName;
		if (callStartedAtMs == 0) callStartedAtMs = System.currentTimeMillis();
		setCallState("active", peerName);
		updateActiveCallNotification();
	}

	private void finishCall(String peerName, String label) {
		voiceConnectGeneration++;
		main.removeCallbacks(voiceParticipantsPoll);
		if (peerName != null && peerName.equals(pendingAcceptedPeer)) pendingAcceptedPeer = "";
		if (peerName != null && peerName.equals(pendingOutgoingConnectPeer)) pendingOutgoingConnectPeer = "";
		if (peerName != null && peerName.equals(pendingVoiceRoom)) pendingVoiceRoom = "";
		long durationMs = callStartedAtMs == 0 ? 0 : System.currentTimeMillis() - callStartedAtMs;
		boolean hadCall = voiceCall.running() || callStartedAtMs != 0 || (peerName != null && peerName.equals(activeCallPeer));
		boolean hadIncomingCall = "incoming".equals(callState) && page == Page.CALL;
		clearIncomingCallUi();
		voiceCall.stop();
		cancelActiveCallNotification();
		if (!activeVoiceRoom && (hadCall || hadIncomingCall)) {
			addCallSystemRow(peerName, label, durationMs);
		}
		activeCallPeer = "";
		activeVoiceRoom = false;
		callStartedAtMs = 0;
		setCallState("idle", "");
	}

	private void updateCallButton() {
		if (callButton == null) return;
		callButton.setVisibility(currentPeerIsBot() || currentPeerIsChannel() ? View.GONE : View.VISIBLE);
		boolean busy = !"idle".equals(callState) && !"failed".equals(callState);
		callButton.setEnabled(!currentPeerBanned || busy);
		String description;
		if (currentPeerIsGroup()) {
			description = busy && currentPeer.equals(activeCallPeer) ? getString(R.string.action_leave_voice) : getString(R.string.action_join_voice);
		} else {
			description = busy ? getString(R.string.action_end_call) : getString(R.string.action_call);
		}
		callButton.setContentDescription(description);
		setButtonRequestBusy(callButton, "calling".equals(callState) || "connecting".equals(callState));
	}

	private void handleRoomUpdate(final MiniTaLib.Update u) {
		final MiniTaLib.User room = u.room;
		if (room == null || room.id == null || room.id.length() == 0) return;
		ui(new Runnable() {
			@Override
			public void run() {
				boolean currentRoom = currentPeerIsSameRoom(room);
				if ("chat_removed".equals(u.type) || "chat_deleted".equals(u.type)) {
					ChatCache.deleteChat(MainActivity.this, SessionStore.server(MainActivity.this, DEFAULT_SERVER), myLogin, resolvedPeerName(room, room.id));
					ChatCache.deleteChat(MainActivity.this, SessionStore.server(MainActivity.this, DEFAULT_SERVER), myLogin, room.id);
					if (currentRoom) {
						currentPeer = "";
						currentPeerUser = null;
						currentPeerBanned = false;
						currentPeerBannedByMe = false;
						currentPeerBannedMe = false;
						showChats();
					} else if (page == Page.CHATS) {
						loadChats();
					}
					return;
				}
				if (currentRoom) {
					currentPeerUser = room;
					currentPeer = resolvedPeerName(room, currentPeer);
					refreshCurrentPeerNameView();
					refreshChatInput();
					loadHistory();
				}
				if (page == Page.CHATS || currentRoom) {
					loadChats();
				}
			}
		});
	}

	private boolean currentPeerIsSameRoom(MiniTaLib.User room) {
		if (room == null || room.id == null || room.id.length() == 0) return false;
		if (currentPeerUser != null && room.id.equals(currentPeerUser.id)) return true;
		String resolved = resolvedPeerName(room, room.id);
		return currentPeer != null && currentPeer.equals(resolved);
	}

	private boolean currentPeerIsBot() {
		return currentPeerUser != null && currentPeer != null && currentPeer.equals(currentPeerUser.login) && currentPeerUser.bot;
	}

	private void addCallSystemRow(String peerName, String label, long durationMs) {
		if (messageRows == null || peerName == null || !peerName.equals(currentPeer)) return;
		String text = label;
		if (durationMs > 0) text += " - " + formatDuration(durationMs);
		MiniTaLib.User peerUser = currentHeaderUser();
		MiniTaLib.User ownUser = new MiniTaLib.User(myID, "", myLogin, myNick, myVerified, myBot, 0);
		String data = "{\"type\":\"call_end\",\"duration_ms\":" + Math.max(0, durationMs) + "}";
		MiniTaLib.Message message = new MiniTaLib.Message(
			0,
			"",
			ownUser,
			peerUser,
			text,
			System.currentTimeMillis() / 1000L,
			0,
			null,
			null,
			false,
			true,
			data
		);
		messageRows.add(MessageRow.messageText(text, message));
		if (messageList != null && messageRows.getCount() > 0) {
			messageList.setSelection(messageRows.getCount() - 1);
		}
	}

	private String formatDuration(long ms) {
		long total = Math.max(1, ms / 1000);
		long minutes = total / 60;
		long seconds = total % 60;
		return String.format(Locale.US, "%d:%02d", minutes, seconds);
	}

	private void showIncomingCall(final String from) {
		cancelIncomingCallNotification();
		status.setText(getString(R.string.status_incoming_call_from, from));
		setCallState("incoming", from);
	}

	private void openIncomingCall(String from) {
		if (from == null || from.trim().length() == 0) return;
		currentPeer = from.trim();
		if ("idle".equals(callState) || "failed".equals(callState)) {
			showIncomingCall(currentPeer);
		} else {
			updateCallWindow();
		}
	}

	private void setCallState(String state, String peerName) {
		callState = state == null ? "idle" : state;
		if ("idle".equals(callState)) activeCallPeer = "";
		else if (peerName != null && peerName.length() > 0) activeCallPeer = peerName;
		main.removeCallbacks(callClock);
		if (!"idle".equals(callState)) main.post(callClock);
		if ("idle".equals(callState) || "failed".equals(callState) || "incoming".equals(callState)) {
			cancelActiveCallNotification();
		}
		updateCallWindow();
		updateCallButton();
	}

	private void updateCallWindow() {
		if ("idle".equals(callState)) {
			dismissCallWindow();
			return;
		}
		ensureCallWindow();
		String title;
		if (activeVoiceRoom && "connecting".equals(callState)) title = getString(R.string.voice_channel_connecting);
		else if (activeVoiceRoom && "active".equals(callState)) title = getString(R.string.voice_channel_active);
		else if ("calling".equals(callState)) title = getString(R.string.call_state_calling);
		else if ("incoming".equals(callState)) title = getString(R.string.call_state_incoming);
		else if ("connecting".equals(callState)) title = getString(R.string.call_state_connecting);
		else if ("active".equals(callState)) title = getString(R.string.call_state_active);
		else if ("failed".equals(callState)) title = getString(R.string.call_state_failed);
		else title = getString(R.string.call_state_none);
		callStateView.setText(title);
		callPeerView.setText(activeCallPeer.length() == 0 ? "" : activeCallPeer);
		if (callHintView != null) {
			if (activeVoiceRoom) callHintView.setText(getString(R.string.voice_channel_hint));
			else if ("incoming".equals(callState)) callHintView.setText(getString(R.string.call_hint_incoming));
			else if ("calling".equals(callState)) callHintView.setText(getString(R.string.call_hint_calling));
			else if ("connecting".equals(callState)) callHintView.setText(getString(R.string.call_hint_connecting));
			else if ("active".equals(callState)) callHintView.setText(getString(R.string.call_hint_active));
			else if ("failed".equals(callState)) callHintView.setText(getString(R.string.call_state_failed));
			else callHintView.setText("");
		}
		configureCallActions();
		updateCallDuration();
	}

	private void ensureCallWindow() {
		if (page == Page.CALL && callStateView != null) return;
		page = Page.CALL;
		if (bottomNav != null) bottomNav.setVisibility(View.GONE);
		content.removeAllViews();
		LinearLayout panel = new LinearLayout(this);
		panel.setOrientation(LinearLayout.VERTICAL);
		panel.setPadding(pad, pad, pad, pad);
		panel.setGravity(Gravity.CENTER_HORIZONTAL);
		panel.setBackgroundColor(bg);

		TextView heading = title(getString(activeVoiceRoom ? R.string.voice_channel_title : R.string.call_title));
		heading.setGravity(Gravity.CENTER);
		callStateView = label("");
		callStateView.setGravity(Gravity.CENTER);
		callPeerView = title("");
		callPeerView.setGravity(Gravity.CENTER);
		callDurationView = label("");
		callDurationView.setGravity(Gravity.CENTER);
		callHintView = label("");
		callHintView.setGravity(Gravity.CENTER);

		panel.addView(heading, new LinearLayout.LayoutParams(-1, -2));
		panel.addView(callStateView, spacedParams());
		panel.addView(callPeerView, spacedParams());
		panel.addView(callDurationView, spacedParams());
		panel.addView(callHintView, spacedParams());
		callParticipantsView = label("");
		callParticipantsView.setGravity(Gravity.CENTER);
		panel.addView(callParticipantsView, spacedParams());

		LinearLayout actions = new LinearLayout(this);
		actions.setOrientation(LinearLayout.HORIZONTAL);
		callSecondaryAction = button(getString(R.string.action_decline), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String peerName = activeCallPeer;
				if (peerName.length() > 0) declineIncomingCall(peerName);
			}
		});
		callPrimaryAction = primaryButton(getString(R.string.action_end_call), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				handleCallPrimaryAction();
			}
		});
		LinearLayout.LayoutParams secondaryLp = new LinearLayout.LayoutParams(0, -2, 1);
		secondaryLp.setMargins(0, 0, gap / 2, 0);
		LinearLayout.LayoutParams primaryLp = new LinearLayout.LayoutParams(0, -2, 1);
		primaryLp.setMargins(gap / 2, 0, 0, 0);
		actions.addView(callSecondaryAction, secondaryLp);
		actions.addView(callPrimaryAction, primaryLp);
		panel.addView(actions, new LinearLayout.LayoutParams(-1, -2));

		callChatAction = button(getString(R.string.action_open_chat), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String peerName = activeCallPeer.length() == 0 ? currentPeer : activeCallPeer;
				if (peerName.length() == 0) return;
				currentPeer = peerName;
				showChat();
				loadHistory();
			}
		});
		LinearLayout.LayoutParams chatLp = new LinearLayout.LayoutParams(-1, -2);
		chatLp.setMargins(0, gap, 0, 0);
		panel.addView(callChatAction, chatLp);

		content.addView(panel, fill());
	}

	private LinearLayout.LayoutParams spacedParams() {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.setMargins(0, gap, 0, gap);
		return lp;
	}

	private void configureCallActions() {
		if (callPrimaryAction == null || callSecondaryAction == null) return;
		if ("incoming".equals(callState)) {
			callSecondaryAction.setVisibility(View.VISIBLE);
			callSecondaryAction.setText(getString(R.string.action_decline));
			callPrimaryAction.setText(getString(R.string.action_accept));
			setButtonRequestBusy(callPrimaryAction, false);
			if (callChatAction != null) callChatAction.setVisibility(View.GONE);
			return;
		}
		callSecondaryAction.setVisibility(View.GONE);
		if ("failed".equals(callState)) {
			callPrimaryAction.setText(getString(R.string.action_close));
		} else if (activeVoiceRoom) {
			callPrimaryAction.setText(getString(R.string.action_leave_voice));
		} else {
			callPrimaryAction.setText(getString(R.string.action_end_call));
		}
		setButtonRequestBusy(callPrimaryAction, "calling".equals(callState) || "connecting".equals(callState));
		if (callChatAction != null) callChatAction.setVisibility(activeCallPeer.length() == 0 ? View.GONE : View.VISIBLE);
	}

	private void loadVoiceParticipants() {
		final MiniTaLib c = ta;
		final String room = activeCallPeer == null || activeCallPeer.length() == 0 ? currentPeer : activeCallPeer;
		if (!activeVoiceRoom || c == null || room == null || room.length() == 0) return;
		run("voice_participants", new Task() {
			@Override
			public void run() throws Exception {
				final List<MiniTaLib.User> participants = c.voiceParticipants(room);
				ui(new Runnable() {
					@Override
					public void run() {
						renderVoiceParticipants(participants);
					}
				});
			}
		});
	}

	private void renderVoiceParticipants(List<MiniTaLib.User> participants) {
		if (callParticipantsView == null || !activeVoiceRoom) return;
		if (participants == null || participants.isEmpty()) {
			callParticipantsView.setText(getString(R.string.voice_channel_no_participants));
			return;
		}
		StringBuilder out = new StringBuilder();
		out.append(getString(R.string.voice_channel_participants)).append("\n");
		for (MiniTaLib.User user : participants) {
			if (user == null) continue;
			if (out.charAt(out.length() - 1) != '\n') out.append("\n");
			out.append(displayUser(user));
		}
		callParticipantsView.setText(out.toString());
	}

	private void handleCallPrimaryAction() {
		if ("incoming".equals(callState)) {
			String peerName = activeCallPeer;
			if (peerName.length() > 0) acceptIncomingCall(peerName);
			return;
		}
		if ("failed".equals(callState)) {
			setCallState("idle", "");
			return;
		}
		endVoice();
	}

	private void dismissCallWindow() {
		boolean wasCallPage = page == Page.CALL;
		callStateView = null;
		callPeerView = null;
		callDurationView = null;
		callHintView = null;
		callParticipantsView = null;
		callPrimaryAction = null;
		callSecondaryAction = null;
		callChatAction = null;
		if (wasCallPage && content != null && !isFinishing()) {
			if (currentPeer != null && currentPeer.length() > 0) {
				showChat();
				loadHistory();
			} else {
				showChats();
			}
		}
	}

	private void updateCallDuration() {
		if (callDurationView == null) return;
		if ("active".equals(callState) && callStartedAtMs > 0) {
			callDurationView.setText(formatDuration(System.currentTimeMillis() - callStartedAtMs));
		} else {
			callDurationView.setText("");
		}
	}

	private String errorText(Throwable error) {
		if (error == null) return getString(R.string.status_unknown_error);
		String message = error.getMessage();
		if (message == null || message.trim().length() == 0) {
			message = error.getClass().getSimpleName();
		}
		return safeDisplayText(message);
	}

	private String voiceStatusText(String state) {
		if (VoiceCall.STATE_ENDED.equals(state)) return getString(R.string.call_ended);
		if (VoiceCall.STATE_CONNECTED.equals(state)) return getString(R.string.status_voice_connected);
		if (VoiceCall.STATE_CONNECTION_CLOSED.equals(state)) return getString(R.string.status_voice_connection_closed);
		if (VoiceCall.STATE_MICROPHONE_PERMISSION_DENIED.equals(state)) return getString(R.string.status_microphone_denied);
		if (state != null && state.startsWith(VoiceCall.STATE_SEND_ERROR_PREFIX)) {
			return getString(R.string.status_voice_send_error, state.substring(VoiceCall.STATE_SEND_ERROR_PREFIX.length()));
		}
		if (state != null && state.startsWith(VoiceCall.STATE_ERROR_PREFIX)) {
			return getString(R.string.status_voice_error, state.substring(VoiceCall.STATE_ERROR_PREFIX.length()));
		}
		return state == null ? "" : safeDisplayText(state);
	}

	private void clearIncomingCallUi() {
		cancelIncomingCallNotification();
	}

	private void cancelIncomingCallNotification() {
		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		if (nm != null) nm.cancel(MessageSyncService.CALL_NOTIFICATION_ID);
	}

	private void createCallNotificationChannel() {
		if (Build.VERSION.SDK_INT < 26) return;
		try {
			NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			if (nm == null) return;
			Class<?> channelClass = Class.forName("android.app.NotificationChannel");
			Constructor<?> constructor = channelClass.getConstructor(String.class, CharSequence.class, int.class);
			Method method = NotificationManager.class.getMethod("createNotificationChannel", channelClass);
			int high = NotificationManager.class.getField("IMPORTANCE_HIGH").getInt(null);
			Object channel = constructor.newInstance(CALL_NOTIFICATION_CHANNEL, getString(R.string.notification_channel_calls), high);
			makeNotificationChannelSilent(channelClass, channel);
			method.invoke(nm, channel);
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

	private void updateActiveCallNotification() {
		if (!"active".equals(callState) || activeCallPeer == null || activeCallPeer.length() == 0 || callStartedAtMs <= 0) {
			return;
		}
		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		if (nm == null) return;
		String title = activeVoiceRoom
			? getString(R.string.notification_active_voice_channel)
			: getString(R.string.notification_active_call);
		String text = getString(
			R.string.notification_active_call_body,
			activeCallPeer,
			formatDuration(System.currentTimeMillis() - callStartedAtMs)
		);
		Intent open = new Intent(this, MainActivity.class);
		open.setAction(ACTION_OPEN_CALL);
		open.putExtra(EXTRA_PEER, activeCallPeer);
		open.putExtra(EXTRA_CALL, activeCallPeer);
		open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		PendingIntent pending = PendingIntent.getActivity(this, ACTIVE_CALL_NOTIFICATION_ID, open, pendingIntentFlags());
		Notification n = activeCallNotification(title, text, pending);
		try {
			nm.notify(ACTIVE_CALL_NOTIFICATION_ID, n);
		} catch (SecurityException ignored) {
		}
	}

	private Notification activeCallNotification(String title, String text, PendingIntent pending) {
		Notification n;
		if (Build.VERSION.SDK_INT >= 11) {
			n = buildActivityNotification(CALL_NOTIFICATION_CHANNEL, title, text, pending, true);
		} else {
			n = new Notification(android.R.drawable.ic_menu_call, text, System.currentTimeMillis());
			setLatestEventInfoCompat(n, title, text, pending);
		}
		n.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
		return n;
	}

	private Notification buildActivityNotification(String channel, String title, String text, PendingIntent pending, boolean ongoing) {
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
			builderClass.getMethod("setSmallIcon", int.class).invoke(builder, android.R.drawable.ic_menu_call);
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
			Notification n = new Notification(android.R.drawable.ic_menu_call, text, System.currentTimeMillis());
			setLatestEventInfoCompat(n, title, text, pending);
			return n;
		}
	}

	private void setLatestEventInfoCompat(Notification n, String title, String text, PendingIntent pending) {
		try {
			Method method = Notification.class.getMethod("setLatestEventInfo", android.content.Context.class, CharSequence.class, CharSequence.class, PendingIntent.class);
			method.invoke(n, this, title, text, pending);
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

	private void cancelActiveCallNotification() {
		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		if (nm != null) nm.cancel(ACTIVE_CALL_NOTIFICATION_ID);
	}

	/** Cancel the aggregated message notification for a specific chat. */
	private void cancelMessageNotification(String peer) {
		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		if (nm == null) return;
		int id = MessageSyncService.MESSAGE_BASE_ID + Math.abs(peer.hashCode()) % 100000;
		nm.cancel(id);
	}

	private void loadCachedChats() {
		final android.content.Context context = getApplicationContext();
		final String server = SessionStore.server(this, DEFAULT_SERVER);
		final String login = myLogin;
		enqueueCache(new Runnable() {
			@Override
			public void run() {
				final List<MiniTaLib.Chat> chats = ChatCache.loadChats(context, server, login);
				ui(new Runnable() {
					@Override
					public void run() {
						if (page == Page.CHATS && chatRows != null) {
							renderChats(chats, getString(R.string.source_cached));
						}
					}
				});
			}
		});
	}

	private void loadCachedHistory(final String peerName) {
		if (peerName == null || peerName.length() == 0) return;
		final android.content.Context context = getApplicationContext();
		final String server = SessionStore.server(this, DEFAULT_SERVER);
		final String login = myLogin;
		enqueueCache(new Runnable() {
			@Override
			public void run() {
				final List<MiniTaLib.Message> history = ChatCache.loadHistory(context, server, login, peerName);
				ui(new Runnable() {
					@Override
					public void run() {
						if (page == Page.CHAT && peerName.equals(currentPeer) && messageRows != null) {
							renderHistory(history, peerName, true);
						}
					}
				});
			}
		});
	}

	private void cacheSaveChats(List<MiniTaLib.Chat> chats) {
		if (chats == null) return;
		final android.content.Context context = getApplicationContext();
		final String server = SessionStore.server(this, DEFAULT_SERVER);
		final String login = myLogin;
		final List<MiniTaLib.Chat> copy = new ArrayList<MiniTaLib.Chat>(chats);
		enqueueCache(new Runnable() {
			@Override
			public void run() {
				ChatCache.saveChats(context, server, login, copy);
			}
		});
	}

	private void cacheSaveHistory(final String peerName, List<MiniTaLib.Message> history) {
		if (peerName == null || peerName.length() == 0 || history == null) return;
		final android.content.Context context = getApplicationContext();
		final String server = SessionStore.server(this, DEFAULT_SERVER);
		final String login = myLogin;
		final List<MiniTaLib.Message> copy = new ArrayList<MiniTaLib.Message>(history);
		enqueueCache(new Runnable() {
			@Override
			public void run() {
				ChatCache.saveHistory(context, server, login, peerName, copy);
			}
		});
	}

	private void cacheAppendMessage(final String peerName, final MiniTaLib.Message message) {
		if (peerName == null || peerName.length() == 0 || message == null) return;
		final android.content.Context context = getApplicationContext();
		final String server = SessionStore.server(this, DEFAULT_SERVER);
		final String login = myLogin;
		enqueueCache(new Runnable() {
			@Override
			public void run() {
				ChatCache.appendMessage(context, server, login, peerName, message);
			}
		});
	}

	private void cacheDeleteMessage(final String peerName, final long messageID) {
		if (peerName == null || peerName.length() == 0) return;
		final android.content.Context context = getApplicationContext();
		final String server = SessionStore.server(this, DEFAULT_SERVER);
		final String login = myLogin;
		enqueueCache(new Runnable() {
			@Override
			public void run() {
				ChatCache.deleteMessage(context, server, login, peerName, messageID);
			}
		});
	}

	private void enqueueCache(Runnable task) {
		try {
			cacheIo.execute(task);
		} catch (Exception ignored) {
		}
	}

	private void append(final MiniTaLib.Message m) {
		if (m == null) return;
		String cachedPeer = messagePeer(m);
		cacheAppendMessage(cachedPeer, m);
		ui(new Runnable() {
			@Override
			public void run() {
				String other = messagePeer(m);

				if (page == Page.CHAT &&
					other.equals(currentPeer) &&
					messageRows != null) {

						currentPeerUser = messagePeerUser(m);
						updateCallButton();
						addMessageRow(m, false);
						refreshChatInput();
						markReadIfIncoming(m, other);

						if (messageList != null &&
						messageRows.getCount() > 0) {

						messageList.setSelection(
							messageRows.getCount() - 1
						);
					}

				} else if (page != Page.CHAT) {

					status.setText(
						getString(R.string.status_new_message_from, other)
					);

					if (page == Page.CHATS) {
						loadChats();
					}
				}
			}
			});
	}

	private void applyMessageUpdate(final MiniTaLib.Message m) {
		if (m == null) return;
		final String cachedPeer = messagePeer(m);
		cacheAppendMessage(cachedPeer, m);
		ui(new Runnable() {
			@Override
			public void run() {
				if (page == Page.CHAT && cachedPeer.equals(currentPeer) && messageRows != null) {
					messageRows.updateMessage(m);
				}
				if (page == Page.CHATS) loadChats();
			}
		});
	}

	private void applyMessageDelete(final MiniTaLib.Message m) {
		if (m == null) return;
		final String cachedPeer = messagePeer(m);
		cacheDeleteMessage(cachedPeer, m.id);
		ui(new Runnable() {
			@Override
			public void run() {
				if (messageRows != null) messageRows.removeMessage(m.id);
				if (seenMessages != null) seenMessages.remove(Long.valueOf(m.id));
				if (page == Page.CHATS) loadChats();
			}
		});
	}

	private String messagePeer(MiniTaLib.Message m) {
		if (m == null || m.from == null || m.to == null) return "";
		if (m.to.roomKind != null && m.to.roomKind.length() > 0) return resolvedPeerName(m.to, m.to.id);
		return m.from.login.equals(myLogin) ? m.to.login : m.from.login;
	}

	private MiniTaLib.User messagePeerUser(MiniTaLib.Message m) {
		if (m == null || m.from == null || m.to == null) return null;
		if (m.to.roomKind != null && m.to.roomKind.length() > 0) return m.to;
		return m.from.login.equals(myLogin) ? m.to : m.from;
	}

	private void markReadIfIncoming(MiniTaLib.Message m, String peerName) {
		if (m == null || m.from == null || m.from.login.equals(myLogin)) return;
		if (m.to != null && m.to.roomKind != null && m.to.roomKind.length() > 0) return;
		markRead(peerName);
	}

	private void markRead(final String peerName) {
		final MiniTaLib c = ta;
		if (c == null || peerName == null || peerName.length() == 0) return;
		run("read", new Task() {
			@Override
			public void run() throws Exception {
				c.markRead(peerName);
			}
		});
	}

	private void addMessageRow(MiniTaLib.Message m, boolean atTop) {
		if (m == null || messageRows == null || !seenMessages.add(m.id)) return;
		if (oldestMessage == 0 || m.id < oldestMessage) oldestMessage = m.id;
		MessageRow row = toMessageRow(m);
		if (atTop) messageRows.insert(row, 0);
		else messageRows.add(row);
	}

	private MessageRow toMessageRow(MiniTaLib.Message m) {
		if (m.text != null && m.text.startsWith("data:image")) {
			return MessageRow.inlineImage(m.text, m);
		}
		if (m.file != null) {
			String kind = m.file.mime != null && m.file.mime.toLowerCase(Locale.US).startsWith("image/") ? getString(R.string.message_image_prefix) : getString(R.string.message_file_prefix);
			String name = m.file.name == null || m.file.name.length() == 0 ? getString(R.string.file_fallback_name) : m.file.name;
			String label = kind + name + " (" + formatBytes(m.file.size) + ")";
			return MessageRow.file(label, m.file, m);
		}
		return MessageRow.messageText(formatMessage(m), m);
	}

	private String formatMessage(MiniTaLib.Message m) {
		return m.text == null ? "" : m.text;
	}

	private String formatMessageTime(long seconds) {
		if (seconds <= 0) return "";
		return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(seconds * 1000L));
	}

	private String formatMessageDateTime(long seconds) {
		if (seconds <= 0) return "";
		return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(seconds * 1000L));
	}

	private void showSystemMessageDetails(MiniTaLib.Message message) {
		if (message == null) return;
		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(0, gap, 0, 0);
		box.addView(spaced(systemDetailRow(getString(R.string.system_detail_message), message.text == null ? "" : message.text)));
		if (message.date > 0) box.addView(spaced(systemDetailRow(getString(R.string.system_detail_time), formatMessageDateTime(message.date))));
		if (message.from != null) box.addView(spaced(systemDetailRow(getString(R.string.system_detail_from), displayUser(message.from))));
		if (message.to != null) box.addView(spaced(systemDetailRow(getString(R.string.system_detail_to), displayUser(message.to))));
		JSONObject data = systemMessageData(message);
		String type = data == null ? "" : data.optString("type");
		if ("wallet_transfer".equals(type)) {
			box.addView(spaced(systemDetailRow(getString(R.string.system_detail_type), getString(R.string.system_type_wallet_transfer))));
			long amount = data.optLong("amount", 0);
			if (amount > 0) box.addView(spaced(systemDetailRow(getString(R.string.system_detail_amount), amount + " DSR")));
			String comment = data.optString("comment", "");
			if (comment.length() > 0) box.addView(spaced(systemDetailRow(getString(R.string.system_detail_comment), comment)));
			long tx = data.optLong("transaction_id", 0);
			if (tx > 0) box.addView(spaced(systemDetailRow(getString(R.string.system_detail_transaction), formatTransactionID(tx))));
			if (data.has("from_user_id")) box.addView(spaced(systemDetailRow(getString(R.string.system_detail_from_id), formatPublicUserID(data.optLong("from_user_id")))));
			if (data.has("to_user_id")) box.addView(spaced(systemDetailRow(getString(R.string.system_detail_to_id), formatPublicUserID(data.optLong("to_user_id")))));
		} else if ("call_end".equals(type)) {
			box.addView(spaced(systemDetailRow(getString(R.string.system_detail_type), getString(R.string.system_type_call))));
			long durationMs = data.optLong("duration_ms", 0);
			if (durationMs <= 0) durationMs = data.optLong("duration_sec", 0) * 1000L;
			if (durationMs > 0) box.addView(spaced(systemDetailRow(getString(R.string.system_detail_duration), formatDuration(durationMs))));
		} else if ("call_missed".equals(type)) {
			box.addView(spaced(systemDetailRow(getString(R.string.system_detail_type), getString(R.string.system_type_missed_call))));
		} else if (data != null) {
			box.addView(spaced(systemDetailRow(getString(R.string.system_detail_type), type.length() == 0 ? getString(R.string.system_type_event) : type)));
			box.addView(spaced(systemDetailRow(getString(R.string.system_detail_data), data.toString())));
		}
		showContentDialog(getString(R.string.system_details_title), box, getString(R.string.action_close), null, null);
	}

	private JSONObject systemMessageData(MiniTaLib.Message message) {
		if (message == null || message.data == null || message.data.length() == 0) return null;
		try {
			return new JSONObject(message.data);
		} catch (Exception ignored) {
			return null;
		}
	}

	private String formatPublicUserID(long userID) {
		return String.format(Locale.US, "%016x", userID);
	}

	private String formatTransactionID(long transactionID) {
		return String.format(Locale.US, "%016x", transactionID);
	}

	private LinearLayout systemDetailRow(String titleText, String value) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.VERTICAL);
		row.setPadding(pad, gap, pad, gap);
		row.setBackgroundDrawable(shape(surface, 0, elementRadius()));
		TextView titleView = label(titleText);
		titleView.setTextColor(muted);
		titleView.setTextSize(13);
		row.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
		TextView valueView = label(value == null ? "" : value);
		valueView.setTextColor(textColor);
		row.addView(valueView, new LinearLayout.LayoutParams(-1, -2));
		return row;
	}

	private void showMessageMenu(final MiniTaLib.Message message) {
		if (message == null) return;
		showActionDialog(new String[] {
			getString(R.string.action_copy),
			getString(R.string.action_delete),
			getString(R.string.action_save_favorite)
		}, new ChoiceHandler() {
			@Override
			public void onChoice(int which) {
				if (which == 0) {
					copyMessage(message);
				} else if (which == 1) {
					deleteMessage(message);
				} else if (which == 2) {
					saveToFavorites(message);
				}
			}
		});
	}

	private void copyMessage(MiniTaLib.Message message) {
		String value = copyText(message);
		if (value.length() == 0) return;
		copyToClipboard(getString(R.string.clipboard_message), value);
	}

	private void copyToClipboard(String label, String value) {
		if (value == null || value.length() == 0) return;
		if (Build.VERSION.SDK_INT >= 11) {
			if (!copyToModernClipboard(label, value)) return;
		} else {
			android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
			if (clipboard == null) {
				status.setText(getString(R.string.status_clipboard_unavailable));
				return;
			}
			clipboard.setText(value);
		}
		status.setText(getString(R.string.status_copied));
	}

	private boolean copyToModernClipboard(String label, String value) {
		Object clipboard = getSystemService(CLIPBOARD_SERVICE);
		if (clipboard == null) {
			status.setText(getString(R.string.status_clipboard_unavailable));
			return false;
		}
		try {
			Class<?> clipDataClass = Class.forName("android.content.ClipData");
			Object clip = clipDataClass
					.getMethod("newPlainText", CharSequence.class, CharSequence.class)
					.invoke(null, label == null ? "text" : label, value);
			clipboard.getClass().getMethod("setPrimaryClip", clipDataClass).invoke(clipboard, clip);
			return true;
		} catch (Exception e) {
			status.setText(getString(R.string.status_clipboard_error, errorText(e)));
			return false;
		}
	}

	private String copyText(MiniTaLib.Message message) {
		if (message == null) return "";
		if (message.file != null) {
			String name = message.file.name == null || message.file.name.length() == 0 ? "file" : message.file.name;
			return name;
		}
		return message.text == null ? "" : message.text;
	}

	private void deleteMessage(final MiniTaLib.Message message) {
		final MiniTaLib c = ta;
		if (c == null || message == null) return;
		run("delete", new Task() {
			@Override
			public void run() throws Exception {
				final MiniTaLib.Message deleted = c.deleteMessage(message.id);
				applyMessageDelete(deleted == null ? message : deleted);
			}
		});
	}

	private void saveToFavorites(final MiniTaLib.Message message) {
		final MiniTaLib c = ta;
		if (c == null || message == null) return;
		run("favorite", new Task() {
			@Override
			public void run() throws Exception {
				MiniTaLib.Message saved = c.favoriteMessage(message.id);
				append(saved);
				ui(new Runnable() {
					@Override
					public void run() {
						status.setText(getString(R.string.status_saved_to_favorites));
					}
				});
			}
		});
	}

	private String chatLastText(MiniTaLib.Message m) {
		if (m.file != null) {
			String kind = m.file.mime != null && m.file.mime.toLowerCase(Locale.US).startsWith("image/") ? getString(R.string.message_image_prefix) : getString(R.string.message_file_prefix);
			String name = m.file.name == null || m.file.name.length() == 0 ? getString(R.string.file_fallback_name) : m.file.name;
			return kind + name;
		}
		return m.text;
	}

	private String displayUser(MiniTaLib.User user) {
		if (user == null) return "";
		if (user.roomKind != null && user.roomKind.length() > 0) {
			String title = user.nick != null && user.nick.length() > 0 ? user.nick : user.id;
			return safeDisplayText(title);
		}
		if (user.nick != null && user.nick.length() > 0) {
			return safeDisplayText(user.nick);
		}
		if (user.login != null && user.login.length() > 0) {
			return displayLogin(user.login, user.verified, user.bot);
		}
		return safeDisplayText(user.id);
	}

	private String displayOwnUser() {
		if (myNick != null && myNick.length() > 0) {
			return safeDisplayText(myNick);
		}
		if (myLogin != null && myLogin.length() > 0) {
			return displayLogin(myLogin, myVerified, myBot);
		}
		return safeDisplayText(myID);
	}

	private String displayLogin(String login, boolean verified, boolean bot) {
		return safeDisplayText(login);
	}

	private CharSequence renderMarkdown(String value) {
		value = safeDisplayText(value);
		if (value.length() == 0) return "";
		SpannableStringBuilder out = new SpannableStringBuilder();
		int i = 0;
		while (i < value.length()) {
			if (value.charAt(i) == '`') {
				int end = value.indexOf('`', i + 1);
				if (end > i + 1) {
					appendCodeSpan(out, value.substring(i + 1, end));
					i = end + 1;
					continue;
				}
			}
			int urlEnd = linkEnd(value, i);
			if (urlEnd > i) {
				String label = value.substring(i, urlEnd);
				String url = label.startsWith("www.") ? "https://" + label : label;
				appendUrlSpan(out, label, url);
				i = urlEnd;
				continue;
			}
			int mentionEnd = mentionEnd(value, i);
			if (mentionEnd > i) {
				String mention = value.substring(i + 1, mentionEnd).toLowerCase(Locale.US);
				appendMentionSpan(out, value.substring(i, mentionEnd), mention);
				i = mentionEnd;
				continue;
			}
			if (startsWith(value, i, "**")) {
				int end = value.indexOf("**", i + 2);
				if (end > i + 2) {
					appendStyleSpan(out, value.substring(i + 2, end), new StyleSpan(Typeface.BOLD));
					i = end + 2;
					continue;
				}
			}
			if (startsWith(value, i, "__")) {
				int end = value.indexOf("__", i + 2);
				if (end > i + 2) {
					appendStyleSpan(out, value.substring(i + 2, end), new StyleSpan(Typeface.BOLD));
					i = end + 2;
					continue;
				}
			}
			if (startsWith(value, i, "~~")) {
				int end = value.indexOf("~~", i + 2);
				if (end > i + 2) {
					appendStyleSpan(out, value.substring(i + 2, end), new StrikethroughSpan());
					i = end + 2;
					continue;
				}
			}
			if (value.charAt(i) == '*') {
				int end = value.indexOf('*', i + 1);
				if (end > i + 1) {
					appendStyleSpan(out, value.substring(i + 1, end), new StyleSpan(Typeface.ITALIC));
					i = end + 1;
					continue;
				}
			}
			if (value.charAt(i) == '_') {
				int end = value.indexOf('_', i + 1);
				if (end > i + 1) {
					appendStyleSpan(out, value.substring(i + 1, end), new StyleSpan(Typeface.ITALIC));
					i = end + 1;
					continue;
				}
			}
			int cp = value.codePointAt(i);
			out.append(value, i, i + Character.charCount(cp));
			i += Character.charCount(cp);
		}
		return out;
	}

	static String safeDisplayText(String value) {
		if (value == null || value.length() == 0) return "";
		boolean stripSupplementary = legacyEmojiLayoutBug();
		boolean changed = false;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (Character.isHighSurrogate(c)) {
				if (i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
					if (stripSupplementary) {
						changed = true;
						break;
					}
					i++;
				} else {
					changed = true;
					break;
				}
			} else if (Character.isLowSurrogate(c)) {
				changed = true;
				break;
			}
		}
		if (!changed) return value;
		StringBuilder out = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (Character.isHighSurrogate(c)) {
				if (i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
					if (stripSupplementary) {
						out.append('\uFFFD');
					} else {
						out.append(c);
						out.append(value.charAt(i + 1));
					}
					i++;
				} else {
					out.append('\uFFFD');
				}
			} else if (Character.isLowSurrogate(c)) {
				out.append('\uFFFD');
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}

	private static boolean legacyEmojiLayoutBug() {
		return Build.VERSION.SDK_INT > 0 && Build.VERSION.SDK_INT < 21;
	}

	private boolean startsWith(String value, int offset, String prefix) {
		return offset + prefix.length() <= value.length() && value.startsWith(prefix, offset);
	}

	private int linkEnd(String value, int offset) {
		if (!startsWith(value, offset, "http://") &&
			!startsWith(value, offset, "https://") &&
			!startsWith(value, offset, "www.")) {
			return -1;
		}
		int end = offset;
		while (end < value.length() && !Character.isWhitespace(value.charAt(end))) {
			end++;
		}
		while (end > offset && ".,;:!?)]}".indexOf(value.charAt(end - 1)) >= 0) {
			end--;
		}
		return end > offset ? end : -1;
	}

	private int mentionEnd(String value, int offset) {
		if (value.charAt(offset) != '@') return -1;
		if (offset > 0) {
			char prev = value.charAt(offset - 1);
			if (Character.isLetterOrDigit(prev) || prev == '_' || prev == '.') return -1;
		}
		int end = offset + 1;
		while (end < value.length()) {
			char c = value.charAt(end);
			if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != '.') break;
			end++;
		}
		return end > offset + 1 ? end : -1;
	}

	private void appendStyleSpan(SpannableStringBuilder out, String value, Object span) {
		int start = out.length();
		out.append(value);
		out.setSpan(span, start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	}

	private void appendCodeSpan(SpannableStringBuilder out, final String value) {
		int start = out.length();
		out.append(value);
		out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		out.setSpan(new ClickableSpan() {
			@Override
			public void onClick(View widget) {
				copyToClipboard("code", value);
			}

			@Override
			public void updateDrawState(TextPaint ds) {
				super.updateDrawState(ds);
				ds.setTypeface(Typeface.MONOSPACE);
				ds.setColor(primary);
				ds.setUnderlineText(false);
			}
		}, start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	}

	private void appendUrlSpan(SpannableStringBuilder out, String label, final String url) {
		int start = out.length();
		out.append(label);
		out.setSpan(new ClickableSpan() {
			@Override
			public void onClick(View widget) {
				openUrl(url);
			}

			@Override
			public void updateDrawState(TextPaint ds) {
				super.updateDrawState(ds);
				ds.setColor(primary);
				ds.setUnderlineText(true);
			}
		}, start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	}

	private void appendMentionSpan(SpannableStringBuilder out, String label, final String login) {
		int start = out.length();
		out.append(label);
		out.setSpan(new ClickableSpan() {
			@Override
			public void onClick(View widget) {
				openMention(login);
			}

			@Override
			public void updateDrawState(TextPaint ds) {
				super.updateDrawState(ds);
				ds.setColor(primary);
				ds.setUnderlineText(false);
			}
		}, start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	}

	private void openUrl(String url) {
		try {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
		} catch (Exception e) {
			status.setText(getString(R.string.status_open_link_error, errorText(e)));
		}
	}

	private void openMention(String login) {
		if (login == null || login.length() == 0) return;
		openChatIfExists(login);
	}

	private String server() {
		if (serverUrl == null) return SessionStore.server(this, DEFAULT_SERVER);
		String s = serverUrl.getText().toString().trim();
		return s.isEmpty() ? SessionStore.server(this, DEFAULT_SERVER) : s;
	}

	private EditText serverInput() {
		EditText field = input(getString(R.string.hint_server), false);
		field.setText(SessionStore.server(this, DEFAULT_SERVER));
		field.setSelection(field.getText().length());
		return field;
	}

	private void updateStatusVisibility() {
		if (status == null) {
			return;
		}
		status.setVisibility(SessionStore.showStatus(this) ? View.VISIBLE : View.GONE);
	}

	private void applyRootPadding(View root) {
		if (root == null) {
			return;
		}
		root.setPadding(pad, pad, pad, pad);
	}

	private void installInsetsCompat(final View root) {
	    if (root == null || Build.VERSION.SDK_INT < 20) {
		    return;
	    }

	    try {
		    Class listenerClass = Class.forName("android.view.View$OnApplyWindowInsetsListener");

		    Object listener = java.lang.reflect.Proxy.newProxyInstance(
				    listenerClass.getClassLoader(),
				    new Class[] { listenerClass },
				    new java.lang.reflect.InvocationHandler() {
					    @Override
					    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
						    if (!"onApplyWindowInsets".equals(method.getName())
								    || args == null
								    || args.length < 2
								    || args[1] == null) {
							    return args == null || args.length == 0 ? null : args[args.length - 1];
						    }

						    Object insets = args[1];

						    int left = getInset(insets, "getSystemWindowInsetLeft");
						    int top = getInset(insets, "getSystemWindowInsetTop");
						    int right = getInset(insets, "getSystemWindowInsetRight");
						    int bottom = getInset(insets, "getSystemWindowInsetBottom");

						    root.setPadding(
								    pad + left,
								    pad + top,
								    pad + right,
								    pad + bottom
						    );

						    return insets;
					    }
				    }
		    );

		    View.class.getMethod("setOnApplyWindowInsetsListener", listenerClass).invoke(root, listener);
		    requestApplyInsetsCompat(root);

	    } catch (Exception ignored) {
		    applyRootPadding(root);
	    }
	}

	private int getInset(Object insets, String methodName) {
		try {
			return ((Integer) insets.getClass().getMethod(methodName).invoke(insets)).intValue();
		} catch (Exception ignored) {
			return 0;
		}
	}

	private void requestApplyInsetsCompat(View root) {
		if (root == null || Build.VERSION.SDK_INT < 20) {
			return;
		}
		try {
			View.class.getMethod("requestApplyInsets").invoke(root);
		} catch (Exception ignored) {
		}
	}

	private void startSyncService() {
		Intent intent = new Intent(this, MessageSyncService.class);
		if (Build.VERSION.SDK_INT >= 26) {
			try {
				getClass().getMethod("startForegroundService", Intent.class).invoke(this, intent);
				return;
			} catch (Exception ignored) {
			}
		}
		startService(intent);
	}

	private void setStatusBarColorCompat(int color) {
		if (Build.VERSION.SDK_INT < 21) {
			return;
		}
		try {
			getWindow().getClass().getMethod("setStatusBarColor", int.class).invoke(getWindow(), color);
		} catch (Exception ignored) {
		}
	}

	private boolean hasPermissionCompat(String permission) {
		if (Build.VERSION.SDK_INT < 23) {
			return true;
		}
		try {
			Object result = Activity.class.getMethod("checkSelfPermission", String.class).invoke(this, permission);
			return ((Integer) result).intValue() == PackageManager.PERMISSION_GRANTED;
		} catch (Exception ignored) {
			return true;
		}
	}

	private void requestPermissionsCompat(String[] permissions, int requestCode) {
		if (Build.VERSION.SDK_INT < 23) {
			return;
		}
		try {
			Activity.class.getMethod("requestPermissions", String[].class, int.class).invoke(this, permissions, Integer.valueOf(requestCode));
		} catch (Exception ignored) {
		}
	}

	private void requestNotifications() {
		if (Build.VERSION.SDK_INT >= 33 && !hasPermissionCompat(PERMISSION_POST_NOTIFICATIONS)) {
			requestPermissionsCompat(new String[] {
				PERMISSION_POST_NOTIFICATIONS
			}, REQ_NOTIFICATIONS);
		}
	}

	// Request permission to read external storage for image picking.
	private void requestReadStoragePermission() {
		if (Build.VERSION.SDK_INT >= 23 && !hasPermissionCompat(PERMISSION_READ_EXTERNAL_STORAGE)) {
			requestPermissionsCompat(new String[] {
				PERMISSION_READ_EXTERNAL_STORAGE
			}, REQ_READ_STORAGE);
		}
	}

	private void run(final String op, final Task task) {
		io.execute(new Runnable() {
			@Override
			public void run() {
				try {
					task.run();
				} catch (final Exception e) {
					ui(new Runnable() {
						@Override
						public void run() {
							if (MiniTaLib.isInvalidTokenError(e)) {
								handleInvalidToken();
								return;
							}
							status.setText(getString(R.string.status_operation_error, errorText(e)));
						}
					});
				}
			}
		});
	}

	private void ui(Runnable r) {
		main.post(r);
	}

	private EditText input(String hint, boolean secret) {
		EditText e = new EditText(this);
		e.setHint(hint);
		e.setTextColor(textColor);
		e.setHintTextColor(muted);
		e.setBackgroundDrawable(shape(surfaceHi, primary, elementRadius()));
		e.setPadding(pad, gap + dp(2), pad, gap + dp(2));
		e.setSingleLine(true);
		e.setFilters(new android.text.InputFilter[] {
			new android.text.InputFilter() {
				@Override
				public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
					if (source == null || start >= end) return null;
					String raw = source.subSequence(start, end).toString();
					String safe = safeDisplayText(raw);
					return raw.equals(safe) ? null : safe;
				}
			}
		});
		if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
		return e;
	}

	private CheckBox checkBox(String s, boolean checked) {
		CheckBox b = new CheckBox(this);
		b.setText(safeDisplayText(s));
		b.setTextColor(textColor);
		b.setPadding(gap, gap, gap, gap);
		b.setChecked(checked);
		return b;
	}

	private Button button(String s, android.view.View.OnClickListener l) {
		Button b = new Button(this);
		b.setText(safeDisplayText(s));
		b.setTextColor(textColor);
		b.setBackgroundDrawable(pressable(surface, surfaceHi, 0, buttonRadius()));
		b.setPadding(buttonPadX, buttonPadY, buttonPadX, buttonPadY);
		b.setMinHeight(buttonMinHeight);
		b.setMinimumHeight(buttonMinHeight);
		b.setMinWidth(0);
		b.setMinimumWidth(0);
		b.setSingleLine(false);
		b.setMaxLines(2);
		b.setOnClickListener(l);
		return b;
	}

	private Button primaryButton(String s, android.view.View.OnClickListener l) {
		Button b = button(s, l);
		b.setTextColor(onPrimary);
		b.setBackgroundDrawable(pressable(primary, blend(primary, Color.WHITE, 0.18f), 0, buttonRadius()));
		return b;
	}

	private void setButtonBusy(Button button, boolean busy, String busyText, String idleText, boolean primaryStyle) {
		if (button == null) return;
		button.setText(safeDisplayText(busy ? busyText : idleText));
		setButtonEnabledStyle(button, !busy, primaryStyle);
		setButtonRequestBusy(button, busy);
	}

	private void setButtonEnabledStyle(Button button, boolean enabled, boolean primaryStyle) {
		if (button == null) return;
		button.setEnabled(enabled);
		if (primaryStyle) {
			int normal = enabled ? primary : blend(primary, Color.BLACK, 0.30f);
			int pressed = enabled ? blend(primary, Color.WHITE, 0.18f) : blend(primary, Color.BLACK, 0.22f);
			button.setTextColor(enabled ? onPrimary : blend(onPrimary, bg, 0.42f));
			button.setBackgroundDrawable(pressable(normal, pressed, 0, buttonRadius()));
		} else {
			int normal = enabled ? surface : blend(surface, Color.BLACK, 0.25f);
			int pressed = enabled ? surfaceHi : blend(surface, Color.BLACK, 0.18f);
			button.setTextColor(enabled ? textColor : blend(textColor, bg, 0.55f));
			button.setBackgroundDrawable(pressable(normal, pressed, 0, buttonRadius()));
		}
	}

	private int buttonRadius() {
		return elementRadius();
	}

	private int elementRadius() {
		return dp(8);
	}

	private void setButtonRequestBusy(View button, boolean busy) {
		if (button == null) return;
		if (busy) startButtonBusyAnimation(button);
		else stopButtonBusyAnimation(button);
	}

	private void setActionButtonLoading(View button, boolean loading, boolean primaryStyle) {
		if (button == null) return;
		if (button instanceof Button) {
			setButtonEnabledStyle((Button) button, !loading, primaryStyle);
		} else {
			button.setEnabled(!loading);
		}
		setButtonRequestBusy(button, loading);
	}

	private void runButtonTask(String name, final View actionButton, final boolean primaryStyle, final Task task) {
		setActionButtonLoading(actionButton, true, primaryStyle);
		run(name, new Task() {
			@Override
			public void run() throws Exception {
				try {
					task.run();
				} finally {
					ui(new Runnable() {
						@Override
						public void run() {
							setActionButtonLoading(actionButton, false, primaryStyle);
						}
					});
				}
			}
		});
	}

	private void startButtonBusyAnimation(View view) {
		if (view == null) return;
		view.clearAnimation();
		AnimationSet set = new AnimationSet(true);
		set.setInterpolator(new AccelerateDecelerateInterpolator());
		set.setFillAfter(true);
		AlphaAnimation alpha = new AlphaAnimation(0.58f, 1.0f);
		alpha.setDuration(520);
		alpha.setRepeatCount(Animation.INFINITE);
		alpha.setRepeatMode(Animation.REVERSE);
		ScaleAnimation scale = new ScaleAnimation(
			0.96f,
			1.0f,
			0.96f,
			1.0f,
			Animation.RELATIVE_TO_SELF,
			0.5f,
			Animation.RELATIVE_TO_SELF,
			0.5f
		);
		scale.setDuration(520);
		scale.setRepeatCount(Animation.INFINITE);
		scale.setRepeatMode(Animation.REVERSE);
		set.addAnimation(alpha);
		set.addAnimation(scale);
		view.startAnimation(set);
	}

	private void stopButtonBusyAnimation(View view) {
		if (view == null) return;
		view.clearAnimation();
	}

	private Button messageActionButton(String s, android.view.View.OnClickListener l) {
		Button b = button(s, l);
		b.setTextColor(onPrimary);
		b.setBackgroundDrawable(pressable(
			primary,
			blend(primary, Color.WHITE, 0.18f),
			blend(primary, Color.WHITE, 0.35f),
			buttonRadius()
		));
		return b;
	}

	private ImageButton iconButton(int iconRes, String description, android.view.View.OnClickListener l) {
		ImageButton b = new ImageButton(this);
		configureIconButton(b, iconRes, description, l, dp(24), buttonRadius());
		return b;
	}

	private ImageButton headerIconButton(int iconRes, String description, android.view.View.OnClickListener l) {
		ImageButton b = new ImageButton(this);
		configureIconButton(b, iconRes, description, l, dp(20), buttonRadius());
		return b;
	}

	private ImageButton inputIconButton(int iconRes, String description, android.view.View.OnClickListener l) {
		ImageButton b = new ImageButton(this);
		configureIconButton(b, iconRes, description, l, dp(22), buttonRadius());
		return b;
	}

	private void configureIconButton(ImageButton b, int iconRes, String description, android.view.View.OnClickListener l, int iconSize, int radius) {
		Drawable icon = getResources().getDrawable(iconRes);
		icon.setBounds(0, 0, iconSize, iconSize);
		b.setImageDrawable(icon);
		b.setScaleType(ImageView.ScaleType.CENTER);
		b.setBackgroundDrawable(pressable(surface, surfaceHi, 0, radius));
		b.setPadding(0, 0, 0, 0);
		b.setMinimumWidth(buttonMinHeight);
		b.setMinimumHeight(buttonMinHeight);
		b.setContentDescription(description);
		b.setOnClickListener(l);
	}

	private TextView title(String s) {
		TextView v = label(s);
		v.setTextSize(18);
		v.setPadding(gap, pad, gap, gap);
		return v;
	}

	private TextView label(String s) {
		TextView v = new TextView(this);
		v.setText(safeDisplayText(s));
		v.setTextColor(textColor);
		return v;
	}

	private MessageAdapter adapter() {
		return new MessageAdapter();
	}

	private final class PaymentSliderView extends View {
		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final RectF trackRect = new RectF();
		private final RectF fillRect = new RectF();
		private final RectF thumbRect = new RectF();
		private final String hint;
		private final boolean confirmLeft;
		private Runnable confirmAction;
		private Runnable resetAnimation;
		private float progress;
		private float touchOffset;
		private boolean tracking;

		PaymentSliderView(android.content.Context context, String hint) {
			this(context, hint, false);
		}

		PaymentSliderView(android.content.Context context, String hint, boolean confirmLeft) {
			super(context);
			this.hint = hint;
			this.confirmLeft = confirmLeft;
			this.progress = confirmLeft ? 1f : 0f;
			setFocusable(true);
		}

		void setOnConfirmAction(Runnable confirmAction) {
			this.confirmAction = confirmAction;
		}

		@Override
		public boolean performClick() {
			return super.performClick();
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			int desiredHeight = dp(56);
			int desiredWidth = dp(240);
			setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec));
		}

		@Override
		protected void onDraw(Canvas canvas) {
			super.onDraw(canvas);
			int w = getWidth();
			int h = getHeight();
			float inset = dp(2);
			float trackLeft = inset;
			float trackTop = inset;
			float trackRight = Math.max(trackLeft, w - inset);
			float trackBottom = Math.max(trackTop, h - inset);
			float radius = Math.min(elementRadius(), Math.max(0f, (trackBottom - trackTop) / 4f));
			float thumbWidth = thumbWidth();
			float thumbHeight = thumbHeight();
			float thumbXInset = thumbHorizontalInset();
			float thumbYInset = thumbVerticalInset();
			float thumbTop = trackTop + thumbYInset;
			float thumbLeft = trackLeft + thumbXInset + (trackRight - trackLeft - thumbWidth - thumbXInset * 2f) * progress;

			trackRect.set(trackLeft, trackTop, trackRight, trackBottom);
			paint.setColor(blend(surfaceHi, Color.BLACK, 0.35f));
			canvas.drawRoundRect(trackRect, radius, radius, paint);

			if (confirmLeft) {
				fillRect.set(
					Math.max(trackLeft, thumbLeft - thumbXInset),
					trackTop,
					trackRight,
					trackBottom
				);
			} else {
				fillRect.set(
					trackLeft,
					trackTop,
					Math.min(trackRight, thumbLeft + thumbWidth + thumbXInset),
					trackBottom
				);
			}
			paint.setColor(blend(primary, Color.BLACK, 0.55f));
			canvas.drawRoundRect(fillRect, radius, radius, paint);

			textPaint.setTextSize(dp(14));
			textPaint.setTypeface(Typeface.DEFAULT_BOLD);
			textPaint.setTextAlign(Paint.Align.CENTER);
			textPaint.setColor(muted);
			Paint.FontMetrics fm = textPaint.getFontMetrics();
			float textY = h / 2f - (fm.ascent + fm.descent) / 2f;
			canvas.drawText(hint, w / 2f, textY, textPaint);

			thumbRect.set(thumbLeft, thumbTop, thumbLeft + thumbWidth, thumbTop + thumbHeight);
			paint.setColor(blend(primary, Color.WHITE, 0.14f));
			float thumbRadius = Math.min(elementRadius(), thumbHeight / 3f);
			canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, paint);

			textPaint.setTextSize(dp(18));
			textPaint.setTypeface(Typeface.DEFAULT_BOLD);
			textPaint.setTextAlign(Paint.Align.CENTER);
			textPaint.setColor(onPrimary);
			Paint.FontMetrics arrowFm = textPaint.getFontMetrics();
			float arrowY = thumbRect.centerY() - (arrowFm.ascent + arrowFm.descent) / 2f;
			canvas.drawText(confirmLeft ? "<" : ">", thumbRect.centerX(), arrowY, textPaint);
		}

		@Override
		public boolean onTouchEvent(MotionEvent event) {
			int action = event.getAction();
			if (action == MotionEvent.ACTION_DOWN) {
				if (!touchInsideThumb(event.getX(), event.getY())) return false;
				cancelResetAnimation();
				tracking = true;
				touchOffset = event.getX() - currentThumbLeft();
				if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
				return true;
			}
			if (action == MotionEvent.ACTION_MOVE && tracking) {
				updateProgressFromTouch(event.getX());
				return true;
			}
			if (action == MotionEvent.ACTION_UP && tracking) {
				updateProgressFromTouch(event.getX());
				boolean confirmed = confirmLeft ? progress <= 0.14f : progress >= 0.86f;
				tracking = false;
				if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
				if (confirmed) {
					cancelResetAnimation();
					progress = confirmLeft ? 0f : 1f;
					invalidate();
					performClick();
					if (confirmAction != null) confirmAction.run();
				} else {
					resetThumb();
				}
				return true;
			}
			if (action == MotionEvent.ACTION_CANCEL && tracking) {
				tracking = false;
				if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
				resetThumb();
				return true;
			}
			return false;
		}

		private void updateProgressFromTouch(float x) {
			float trackLeft = dp(2);
			float thumbInset = thumbHorizontalInset();
			float usable = Math.max(1f, getWidth() - dp(4) - thumbWidth() - thumbInset * 2f);
			progress = Math.max(0f, Math.min(1f, (x - touchOffset - trackLeft - thumbInset) / usable));
			invalidate();
		}

		private boolean touchInsideThumb(float x, float y) {
			float thumbWidth = thumbWidth();
			float thumbHeight = thumbHeight();
			float left = currentThumbLeft();
			float top = (getHeight() - thumbHeight) / 2f;
			float slop = dp(8);
			return x >= left - slop && x <= left + thumbWidth + slop && y >= top - slop && y <= top + thumbHeight + slop;
		}

		private float currentThumbLeft() {
			float trackLeft = dp(2);
			float thumbInset = thumbHorizontalInset();
			float usable = Math.max(1f, getWidth() - dp(4) - thumbWidth() - thumbInset * 2f);
			return trackLeft + thumbInset + usable * progress;
		}

		private float thumbWidth() {
			return Math.max(dp(64), Math.min(dp(82), thumbHeight() * 1.65f));
		}

		private float thumbHeight() {
			return Math.max(dp(36), Math.min(dp(44), getHeight() - dp(10)));
		}

		private float thumbHorizontalInset() {
			return dp(4);
		}

		private float thumbVerticalInset() {
			float trackHeight = Math.max(0f, getHeight() - dp(4));
			return Math.max(0f, (trackHeight - thumbHeight()) / 2f);
		}

		private void resetThumb() {
			animateThumbToStart();
		}

		private void animateThumbToStart() {
			cancelResetAnimation();
			final float startProgress = progress;
			final float targetProgress = confirmLeft ? 1f : 0f;
			if (Math.abs(startProgress - targetProgress) <= 0.001f) {
				progress = targetProgress;
				invalidate();
				return;
			}
			final long startTime = System.currentTimeMillis();
			final long durationMs = 180L;
			resetAnimation = new Runnable() {
				@Override
				public void run() {
					float t = Math.min(1f, (System.currentTimeMillis() - startTime) / (float)durationMs);
					float eased = 1f - (1f - t) * (1f - t) * (1f - t);
					progress = startProgress + (targetProgress - startProgress) * eased;
					invalidate();
					if (t < 1f) {
						postDelayed(this, 16L);
					} else {
						progress = targetProgress;
						resetAnimation = null;
						invalidate();
					}
				}
			};
			post(resetAnimation);
		}

		private void cancelResetAnimation() {
			if (resetAnimation != null) {
				removeCallbacks(resetAnimation);
				resetAnimation = null;
			}
		}
	}

		private static final class MessageRow {
			final String text;
			final String imageData;
			final MiniTaLib.FileInfo file;
			final MiniTaLib.Message message;
			final String chatTitle;
			final String chatPreview;

			private MessageRow(String text, String imageData, MiniTaLib.FileInfo file, MiniTaLib.Message message, String chatTitle, String chatPreview) {
				this.text = text;
				this.imageData = imageData;
				this.file = file;
				this.message = message;
				this.chatTitle = chatTitle;
				this.chatPreview = chatPreview;
			}

			static MessageRow text(String text) {
				return new MessageRow(text, null, null, null, null, null);
			}

			static MessageRow messageText(String text, MiniTaLib.Message message) {
				return new MessageRow(text, null, null, message, null, null);
			}

			static MessageRow inlineImage(String data, MiniTaLib.Message message) {
				return new MessageRow(null, data, null, message, null, null);
			}

			static MessageRow file(String text, MiniTaLib.FileInfo file, MiniTaLib.Message message) {
				return new MessageRow(text, null, file, message, null, null);
			}

			static MessageRow chat(String title, String preview) {
				return new MessageRow(null, null, null, null, title, preview);
			}
		}

		private static final class ChatPreviewHolder {
			final TextView title;
			final TextView preview;

			ChatPreviewHolder(TextView title, TextView preview) {
				this.title = title;
				this.preview = preview;
			}
		}

	private class MessageAdapter extends android.widget.BaseAdapter {
		private final List < MessageRow > rows = new ArrayList < MessageRow > ();

		void clear() {
			rows.clear();
			notifyDataSetChanged();
		}

		void add(String s) {
			add(MessageRow.text(s));
		}

		void add(MessageRow row) {
			rows.add(row);
			notifyDataSetChanged();
		}

		void replaceRows(List<MessageRow> nextRows) {
			rows.clear();
			if (nextRows != null) rows.addAll(nextRows);
			notifyDataSetChanged();
		}

			void insert(MessageRow row, int index) {
				rows.add(index, row);
				notifyDataSetChanged();
			}

		void insertRows(List<MessageRow> nextRows, int index) {
			if (nextRows == null || nextRows.isEmpty()) return;
			rows.addAll(index, nextRows);
			notifyDataSetChanged();
		}

			void updateMessage(MiniTaLib.Message message) {
				if (message == null) return;
				for (int i = 0; i < rows.size(); i++) {
					MessageRow row = rows.get(i);
					if (row.message != null && row.message.id == message.id) {
						rows.set(i, toMessageRow(message));
						notifyDataSetChanged();
						return;
					}
				}
			}

			void removeMessage(long messageID) {
				for (int i = 0; i < rows.size(); i++) {
					MessageRow row = rows.get(i);
					if (row.message != null && row.message.id == messageID) {
						rows.remove(i);
						notifyDataSetChanged();
						return;
					}
				}
			}

		@Override public int getCount() {
			return rows.size();
		}

		@Override public Object getItem(int position) {
			return rows.get(position);
		}

		@Override public long getItemId(int position) {
			return position;
		}

			@Override
			public View getView(int pos, View convertView, ViewGroup parent) {
				MessageRow row = rows.get(pos);
				if (row.message != null) {
					return messageView(row);
				}
				if (row.imageData != null) {
					return imageView(row.imageData, convertView);
				}
				if (row.file != null) {
				if (isImageFile(row.file)) {
					return imageFileView(row);
				}
				return fileView(row);
			}
				if (row.chatTitle != null) {
					return chatPreviewView(row, convertView);
				}
				return textView(row.text, convertView);
			}

			private View chatPreviewView(MessageRow row, View convertView) {
				LinearLayout box;
				TextView title;
				TextView preview;
				if (convertView instanceof LinearLayout && convertView.getTag() instanceof ChatPreviewHolder) {
					box = (LinearLayout) convertView;
					ChatPreviewHolder holder = (ChatPreviewHolder) box.getTag();
					title = holder.title;
					preview = holder.preview;
				} else {
					box = new LinearLayout(MainActivity.this);
					box.setOrientation(LinearLayout.VERTICAL);
					box.setPadding(pad, gap, pad, gap);
					box.setBackgroundDrawable(shape(surface, 0, elementRadius()));

					title = new TextView(MainActivity.this);
					title.setTextColor(textColor);
					title.setTextSize(16);
					title.setTypeface(Typeface.DEFAULT_BOLD);
					title.setSingleLine(true);
					title.setEllipsize(TextUtils.TruncateAt.END);
					box.addView(title, new LinearLayout.LayoutParams(-1, -2));

					preview = new TextView(MainActivity.this);
					preview.setTextColor(muted);
					preview.setTextSize(14);
					preview.setMaxLines(2);
					preview.setEllipsize(TextUtils.TruncateAt.END);
					LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, -2);
					previewLp.setMargins(0, gap / 3, 0, 0);
					box.addView(preview, previewLp);

					box.setTag(new ChatPreviewHolder(title, preview));
				}
				title.setText(safeDisplayText(row.chatTitle));
				preview.setText(safeDisplayText(row.chatPreview));
				preview.setVisibility(row.chatPreview == null || row.chatPreview.length() == 0 ? View.GONE : View.VISIBLE);
				return listItemFrame(box);
			}

			private View messageView(final MessageRow row) {
				if (row.message != null && row.message.system) {
					return systemMessageView(row);
				}
				LinearLayout outer = new LinearLayout(MainActivity.this);
				outer.setOrientation(LinearLayout.VERTICAL);
				LinearLayout box = fileBox();
				box.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {
						showMessageMenu(row.message);
						return true;
					}
				});
				box.addView(userNameRow(row.message == null ? null : row.message.from, 14), new LinearLayout.LayoutParams(-1, -2));
				if (row.imageData != null) {
					LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(-1, -2);
					contentLp.setMargins(0, gap / 3, 0, 0);
					box.addView(imageContent(row.imageData), contentLp);
				} else if (row.file != null) {
					LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
					labelLp.setMargins(0, gap / 3, 0, 0);
					box.addView(fileLabel(row.text), labelLp);
					if (isImageFile(row.file)) {
						addImagePreview(box, row);
					}
					addDownloadButton(box, row);
				} else {
					TextView body = messageTextLabel(row.text);
					LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
					bodyLp.setMargins(0, gap / 3, 0, 0);
					box.addView(body, bodyLp);
				}
				addMessageFooter(box, row.message);
				outer.addView(box, new LinearLayout.LayoutParams(-1, -2));
				addMessageButtons(outer, row.message);
				return listItemFrame(outer);
			}

			private View systemMessageView(final MessageRow row) {
				LinearLayout outer = new LinearLayout(MainActivity.this);
				outer.setOrientation(LinearLayout.VERTICAL);
				outer.setGravity(Gravity.CENTER_HORIZONTAL);
				outer.setPadding(0, gap / 2, 0, gap / 2);

				TextView pill = new TextView(MainActivity.this);
				pill.setTextColor(muted);
				pill.setTextSize(13);
				pill.setGravity(Gravity.CENTER);
				pill.setMaxWidth(Math.max(dp(180), getResources().getDisplayMetrics().widthPixels - pad * 4));
				pill.setPadding(pad, gap, pad, gap);
				pill.setBackgroundDrawable(pressable(
					blend(surface, bg, 0.48f),
					blend(surfaceHi, bg, 0.38f),
					0,
					elementRadius()
				));
				pill.setText(safeDisplayText(row.text));
				pill.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						showSystemMessageDetails(row.message);
					}
				});
				outer.addView(pill, new LinearLayout.LayoutParams(-2, -2));
				return listItemFrame(outer);
			}

			private View textView(String value, View convertView) {
				TextView tv;
			if (convertView instanceof TextView) {
				tv = (TextView) convertView;
			} else {
				tv = new TextView(MainActivity.this);
			}
			tv.setTextColor(textColor);
			tv.setTextSize(16);
			tv.setPadding(pad, pad, pad, pad);
			tv.setBackgroundDrawable(shape(surface, 0, elementRadius()));
			tv.setText(safeDisplayText(value));
			return listItemFrame(tv);
		}

			private View imageView(String payload, View convertView) {
				ImageView iv;
				if (convertView instanceof ImageView) {
				iv = (ImageView) convertView;
			} else {
				iv = new ImageView(MainActivity.this);
				iv.setAdjustViewBounds(true);
				iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
				lp.setMargins(pad, pad, pad, pad);
				iv.setLayoutParams(lp);
			}
			try {
				String base64Part = payload.substring(payload.indexOf(',') + 1);
				byte[] data = Base64.decode(base64Part, Base64.DEFAULT);
				Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
				iv.setImageBitmap(bmp);
			} catch (Exception e) {
				return textView(getString(R.string.invalid_image), null);
			}
				iv.setBackgroundDrawable(shape(surface, 0, elementRadius()));
				return listItemFrame(iv);
			}

			private View imageContent(String payload) {
				ImageView iv = new ImageView(MainActivity.this);
				iv.setAdjustViewBounds(true);
				iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
				try {
					String base64Part = payload.substring(payload.indexOf(',') + 1);
					byte[] data = Base64.decode(base64Part, Base64.DEFAULT);
					Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
					iv.setImageBitmap(bmp);
				} catch (Exception e) {
					return fileLabel(getString(R.string.invalid_image));
				}
				iv.setBackgroundDrawable(shape(surfaceHi, 0, elementRadius()));
				return iv;
			}

			private View imageFileView(final MessageRow row) {
				LinearLayout box = fileBox();
				TextView label = fileLabel(row.text);
				box.addView(label, new LinearLayout.LayoutParams(-1, -2));
			addImagePreview(box, row);
			addDownloadButton(box, row);
			return listItemFrame(box);
		}

			private void addImagePreview(LinearLayout box, final MessageRow row) {
				String key = imageCacheKey(row.file);
				Bitmap bmp = cachedImagePreview(key);
				String error = cachedImagePreviewError(key);
			if (bmp != null) {
				ImageView preview = new ImageView(MainActivity.this);
				preview.setAdjustViewBounds(true);
				preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
				preview.setMaxHeight(dp(360));
				preview.setImageBitmap(bmp);
				preview.setBackgroundDrawable(shape(surfaceHi, 0, elementRadius()));
				preview.setPadding(gap, gap, gap, gap);
				preview.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						downloadFile(row.file);
					}
				});
				LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(-1, -2);
				imageLp.setMargins(0, gap, 0, 0);
				box.addView(preview, imageLp);
			} else {
				TextView placeholder = new TextView(MainActivity.this);
				placeholder.setTextColor(muted);
				placeholder.setTextSize(14);
				placeholder.setGravity(Gravity.CENTER);
				placeholder.setMinHeight(dp(120));
				placeholder.setPadding(pad, pad, pad, pad);
				placeholder.setBackgroundDrawable(shape(surfaceHi, 0, elementRadius()));
				placeholder.setText(error == null ? getString(R.string.loading_image) : getString(R.string.preview_unavailable));
				LinearLayout.LayoutParams placeholderLp = new LinearLayout.LayoutParams(-1, -2);
				placeholderLp.setMargins(0, gap, 0, 0);
				box.addView(placeholder, placeholderLp);
				if (error == null) {
					startImagePreviewLoad(row.file, this);
					}
				}
			}

		private View fileView(final MessageRow row) {
			LinearLayout box = fileBox();
			box.addView(fileLabel(row.text), new LinearLayout.LayoutParams(-1, -2));
			addDownloadButton(box, row);
			return listItemFrame(box);
		}

		private View listItemFrame(View child) {
			LinearLayout frame = new LinearLayout(MainActivity.this);
			frame.setOrientation(LinearLayout.VERTICAL);
			int vertical = gap / 2;
			frame.setPadding(0, vertical, 0, vertical);
			frame.addView(child, new LinearLayout.LayoutParams(-1, -2));
			return frame;
		}

		private LinearLayout fileBox() {
			LinearLayout box = new LinearLayout(MainActivity.this);
			box.setOrientation(LinearLayout.VERTICAL);
			int inset = Math.max(gap, pad / 2);
			box.setPadding(inset, inset, inset, inset);
			box.setBackgroundDrawable(shape(surface, 0, elementRadius()));
			return box;
		}

		private TextView fileLabel(String value) {
			TextView label = new TextView(MainActivity.this);
			label.setTextColor(textColor);
			label.setTextSize(16);
			label.setText(safeDisplayText(value));
			return label;
		}

		private TextView messageTextLabel(String value) {
			TextView label = fileLabel("");
			label.setText(renderMarkdown(value));
			label.setMovementMethod(LinkMovementMethod.getInstance());
			label.setHighlightColor(Color.TRANSPARENT);
			label.setLinksClickable(true);
			return label;
		}

			private void addDownloadButton(LinearLayout box, final MessageRow row) {
			String title = isCompleteDownloadedFile(downloadedFileFor(row.file), row.file) ? getString(R.string.action_open) : getString(R.string.action_download);
			Button download = button(title, new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					downloadFile(row.file, v);
				}
			});
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
			lp.setMargins(0, gap, 0, 0);
				box.addView(download, lp);
			}

			private void addMessageButtons(LinearLayout box, final MiniTaLib.Message message) {
				if (message == null || message.buttons == null || message.buttons.isEmpty()) return;
				for (final MiniTaLib.Button item : message.buttons) {
					if (item == null || item.text == null || item.text.length() == 0) continue;
					Button action = messageActionButton(item.text, new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							handleMessageButton(message, item, v instanceof Button ? (Button) v : null);
						}
					});
					LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
					lp.setMargins(0, gap / 2, 0, 0);
					box.addView(action, lp);
				}
			}

			private void addMessageFooter(LinearLayout box, MiniTaLib.Message message) {
				LinearLayout footer = new LinearLayout(MainActivity.this);
				footer.setOrientation(LinearLayout.HORIZONTAL);
				footer.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
				TextView time = new TextView(MainActivity.this);
				time.setTextColor(muted);
				time.setTextSize(12);
				time.setText(formatMessageTime(message == null ? 0 : message.date));
				footer.addView(time, new LinearLayout.LayoutParams(-2, -2));
				if (message != null && message.from != null && message.from.login.equals(myLogin)) {
					ImageView statusIcon = new ImageView(MainActivity.this);
					statusIcon.setImageResource(message.readAt > 0 ? R.drawable.ic_status_read : R.drawable.ic_status_sent);
					statusIcon.setContentDescription(message.readAt > 0 ? getString(R.string.read_status) : getString(R.string.sent_status));
					LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(18), dp(18));
					iconLp.setMargins(gap / 2, 0, 0, 0);
					footer.addView(statusIcon, iconLp);
				}
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
				lp.setMargins(0, gap / 3, 0, 0);
				box.addView(footer, lp);
			}
		}

	private LinearLayout chatHeader() {
		LinearLayout r = new LinearLayout(this);
		r.setOrientation(LinearLayout.HORIZONTAL);
		r.setGravity(Gravity.CENTER_VERTICAL);

		ImageButton back = headerIconButton(R.drawable.ic_back, getString(R.string.action_back), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showChats();
			}
		});
		LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight);
		backLp.setMargins(0, 0, gap, 0);
		r.addView(back, backLp);

		LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, -1, 1);
		currentPeerNameView = userNameRow(currentHeaderUser(), 18);
		currentPeerNameView.setClickable(true);
		currentPeerNameView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showCurrentPeerProfile();
			}
		});
		r.addView(currentPeerNameView, nameLp);

		LinearLayout.LayoutParams callLp = new LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight);
		callLp.setMargins(gap, 0, 0, 0);
		r.addView(callButton, callLp);

		ImageButton menu = headerIconButton(R.drawable.ic_more_vertical, getString(R.string.chat_actions), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showChatActionsMenu();
			}
		});
		LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight);
		menuLp.setMargins(gap, 0, 0, 0);
		r.addView(menu, menuLp);
		return r;
	}

	private MiniTaLib.User currentHeaderUser() {
		if (currentPeerUser != null && currentPeer != null && currentPeer.equals(resolvedPeerName(currentPeerUser, currentPeer))) {
			return currentPeerUser;
		}
		return new MiniTaLib.User("", "", currentPeer == null ? "" : currentPeer, "", false, false, 0);
	}

	private LinearLayout userNameRow(MiniTaLib.User user, int textSizeSp) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.VERTICAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		populateUserNameRow(row, user, textSizeSp);
		return row;
	}

	private void refreshCurrentPeerNameView() {
		if (currentPeerNameView == null) return;
		currentPeerNameView.removeAllViews();
		populateUserNameRow(currentPeerNameView, currentHeaderUser(), 18);
	}

	private void populateUserNameRow(LinearLayout row, MiniTaLib.User user, int textSizeSp) {
		LinearLayout nameLine = new LinearLayout(this);
		nameLine.setOrientation(LinearLayout.HORIZONTAL);
		nameLine.setGravity(Gravity.CENTER_VERTICAL);
		TextView name = new TextView(this);
		name.setTextColor(blend(primary, Color.WHITE, 0.18f));
		name.setTextSize(textSizeSp);
		name.setSingleLine(true);
		name.setText(safeDisplayText(displayUser(user)));
		nameLine.addView(name, new LinearLayout.LayoutParams(-2, -2));
		if (user != null && user.verified) {
			ImageView verified = new ImageView(this);
			verified.setImageDrawable(verifiedDrawable(dp(18)));
			verified.setContentDescription(getString(R.string.verified));
			LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(18), dp(18));
			iconLp.setMargins(gap / 2, 0, 0, 0);
			nameLine.addView(verified, iconLp);
		}
		row.addView(nameLine, new LinearLayout.LayoutParams(-2, -2));
		String subtitle = roomMemberCountLabel(user);
		if (subtitle.length() > 0) {
			TextView members = new TextView(this);
			members.setTextColor(muted);
			members.setTextSize(12);
			members.setSingleLine(true);
			members.setText(subtitle);
			row.addView(members, new LinearLayout.LayoutParams(-2, -2));
		}
	}

	private String roomMemberCountLabel(MiniTaLib.User user) {
		if (user == null || user.roomKind == null || user.roomKind.length() == 0) return "";
		return "channel".equals(user.roomKind)
			? getString(R.string.channel_subscribers_count, user.memberCount)
			: getString(R.string.group_members_count, user.memberCount);
	}

	private Drawable verifiedDrawable(int size) {
		Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		float scale = size / 24f;
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setColor(primary);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(2f * scale);
		paint.setStrokeCap(Paint.Cap.ROUND);
		paint.setStrokeJoin(Paint.Join.ROUND);
		Path badge = new Path();
		badge.moveTo(12f * scale, 2f * scale);
		badge.lineTo(14.74f * scale, 4.05f * scale);
		badge.lineTo(18.14f * scale, 3.69f * scale);
		badge.lineTo(19.1f * scale, 6.97f * scale);
		badge.lineTo(22f * scale, 8.79f * scale);
		badge.lineTo(20.66f * scale, 11.94f * scale);
		badge.lineTo(22f * scale, 15.09f * scale);
		badge.lineTo(19.1f * scale, 16.91f * scale);
		badge.lineTo(18.14f * scale, 20.19f * scale);
		badge.lineTo(14.74f * scale, 19.83f * scale);
		badge.lineTo(12f * scale, 21.88f * scale);
		badge.lineTo(9.26f * scale, 19.83f * scale);
		badge.lineTo(5.86f * scale, 20.19f * scale);
		badge.lineTo(4.9f * scale, 16.91f * scale);
		badge.lineTo(2f * scale, 15.09f * scale);
		badge.lineTo(3.34f * scale, 11.94f * scale);
		badge.lineTo(2f * scale, 8.79f * scale);
		badge.lineTo(4.9f * scale, 6.97f * scale);
		badge.lineTo(5.86f * scale, 3.69f * scale);
		badge.lineTo(9.26f * scale, 4.05f * scale);
		badge.close();
		canvas.drawPath(badge, paint);
		Path check = new Path();
		check.moveTo(7f * scale, 12.2f * scale);
		check.lineTo(10.3f * scale, 15.5f * scale);
		check.lineTo(17f * scale, 8.8f * scale);
		canvas.drawPath(check, paint);
		return new BitmapDrawable(getResources(), bitmap);
	}

	private void showChatActionsMenu() {
		final ArrayList<String> actions = new ArrayList<String>();
		if (currentPeer != null && currentPeer.length() > 0) {
			actions.add(getString(R.string.action_profile));
			if (currentPeerIsRoom()) {
				if (!currentPeerIsChannel() || currentPeerCanManageRoom()) {
					actions.add(getString(R.string.action_members));
				}
				actions.add(getString(R.string.action_invite));
				if (currentPeerCanManageRoom()) {
					actions.add(getString(R.string.action_edit_title));
					if (currentPeerIsChannel()) actions.add(getString(R.string.action_edit_username));
					actions.add(getString(R.string.action_remove_member));
				}
			} else {
				actions.add(getString(R.string.action_verify_e2e));
			}
			actions.add(getString(R.string.action_copy_id));
			if (currentPeerIsRoom() && !currentPeerCanManageRoom()) {
				actions.add(getString(R.string.action_leave_chat));
			} else {
				actions.add(getString(R.string.action_delete_chat));
			}
			if (!currentPeerIsRoom()) {
				actions.add(currentPeerBannedByMe ? getString(R.string.action_unban_user) : getString(R.string.action_ban_user));
			}
		}
		if (actions.isEmpty()) {
			return;
		}
		showActionDialog(actions.toArray(new String[actions.size()]), new ChoiceHandler() {
			@Override
			public void onChoice(int which) {
				String action = actions.get(which);
				if (action.equals(getString(R.string.action_profile))) {
					showCurrentPeerProfile();
				} else if (action.equals(getString(R.string.action_members))) {
					showCurrentRoomMembersDialog();
				} else if (action.equals(getString(R.string.action_invite))) {
					showInviteMemberDialog();
				} else if (action.equals(getString(R.string.action_edit_title))) {
					showEditRoomTitleDialog();
				} else if (action.equals(getString(R.string.action_edit_username))) {
					showEditChannelUsernameDialog();
				} else if (action.equals(getString(R.string.action_remove_member))) {
					showRemoveMemberDialog();
				} else if (action.equals(getString(R.string.action_verify_e2e))) {
					showE2EFingerprint();
				} else if (action.equals(getString(R.string.action_copy_id))) {
					copyCurrentPeerID();
				} else if (action.equals(getString(R.string.action_delete_chat))) {
					confirmDeleteCurrentChat();
				} else if (action.equals(getString(R.string.action_leave_chat))) {
					confirmLeaveCurrentRoom();
				} else if (action.equals(getString(R.string.action_ban_user)) || action.equals(getString(R.string.action_unban_user))) {
					if (currentPeerBannedByMe) confirmUnbanCurrentPeer();
					else confirmBanCurrentPeer();
				}
			}
		});
	}

	private void showCurrentPeerProfile() {
		MiniTaLib.User user = currentHeaderUser();
		if (user == null || user.id.length() == 0) {
			status.setText(getString(R.string.status_id_not_loaded));
			return;
		}
		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(0, gap, 0, 0);
		box.addView(spaced(userProfileRow(getString(R.string.profile_id), user.id, "user id")));
		if (user.roomKind != null && user.roomKind.length() > 0) {
			box.addView(spaced(userProfileRow(getString(R.string.profile_type), roomKindLabel(user), null)));
			box.addView(spaced(userProfileRow(
				"channel".equals(user.roomKind) ? getString(R.string.profile_subscribers) : getString(R.string.profile_participants),
				String.valueOf(user.memberCount),
				null
			)));
			box.addView(spaced(row(primaryButton(getString(R.string.action_invite), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showInviteMemberDialog();
				}
			}))));
			if (currentPeerCanManageRoom()) {
				box.addView(spaced(row(
					button(getString(R.string.action_edit_title), new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							showEditRoomTitleDialog();
						}
					}),
					button(getString(R.string.action_remove_member), new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							showRemoveMemberDialog();
						}
					})
				)));
				if ("channel".equals(user.roomKind)) {
					box.addView(spaced(row(button(getString(R.string.action_edit_username), new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							showEditChannelUsernameDialog();
						}
					}))));
				}
			}
		}
		if (user.login != null && user.login.length() > 0) {
			box.addView(spaced(userProfileRow(getString(R.string.profile_username), "@" + user.login, "username")));
		}
		if (user.nick != null && user.nick.length() > 0) {
			box.addView(spaced(userProfileRow(getString(R.string.profile_name), user.nick, null)));
		}
		if (user.verified) {
			box.addView(spaced(userProfileRow(getString(R.string.profile_verification), getString(R.string.profile_verified), null)));
		}
		if (user.roomKind == null || user.roomKind.length() == 0) {
			if (!isOwnUser(user) && !isNegativePublicID(user.id)) {
				box.addView(spaced(row(primaryButton(getString(R.string.action_add_contact), new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						addProfileUserToContacts(v);
					}
				}))));
			}
		}
		showContentDialog(profileTitle(user), box, getString(R.string.action_close), null, null);
	}

	private static boolean isNegativePublicID(String value) {
		if (value == null) return false;
		String raw = value.trim();
		if (raw.length() != 16) return false;
		for (int i = 0; i < raw.length(); i++) {
			if (Character.digit(raw.charAt(i), 16) < 0) return false;
		}
		try {
			return Long.parseUnsignedLong(raw, 16) < 0;
		} catch (NumberFormatException ignored) {
			return false;
		}
	}

	private void showCurrentRoomMembersDialog() {
		if (!currentPeerIsRoom()) return;
		if (currentPeerIsChannel() && !currentPeerCanManageRoom()) return;
		MiniTaLib.User user = currentHeaderUser();
		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(0, gap, 0, 0);
		if (user.memberUsers != null && !user.memberUsers.isEmpty()) {
			for (MiniTaLib.User member : user.memberUsers) {
				box.addView(spaced(userProfileRow(displayUser(member), member.id, "user id")));
			}
		} else {
			TextView empty = label(getString(R.string.profile_no_members));
			empty.setTextColor(muted);
			box.addView(spaced(empty));
		}
		showContentDialog(getString(R.string.action_members), box, getString(R.string.action_close), null, null);
	}

	private void addProfileUserToContacts() {
		addProfileUserToContacts(null);
	}

	private void addProfileUserToContacts(final View actionButton) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		final MiniTaLib.User user = currentHeaderUser();
		final String address = resolvedPeerName(user, currentPeer);
		if (address == null || address.length() == 0) return;
		status.setText(getString(R.string.status_saving_contact));
		runButtonTask("profile_add_contact", actionButton, true, new Task() {
			@Override
			public void run() throws Exception {
				c.addContact(address);
				ui(new Runnable() {
					@Override
					public void run() {
						status.setText(getString(R.string.status_contact_saved));
					}
				});
			}
		});
	}

	private String profileTitle(MiniTaLib.User user) {
		if (user != null && "channel".equals(user.roomKind)) return getString(R.string.profile_channel);
		if (user != null && "group".equals(user.roomKind)) return getString(R.string.profile_group);
		return user != null && user.bot ? getString(R.string.profile_bot) : getString(R.string.profile_user);
	}

	private String roomKindLabel(MiniTaLib.User user) {
		if (user != null && "channel".equals(user.roomKind)) return getString(R.string.profile_channel);
		return getString(R.string.profile_group);
	}

	private void showInviteMemberDialog() {
		if (!currentPeerIsRoom()) return;
		final EditText input = input(getString(R.string.hint_username_or_id), false);
		showContentDialog(getString(R.string.action_invite), input, getString(R.string.action_invite), new Runnable() {
			@Override
			public void run() {
				inviteMember(input.getText().toString().trim());
			}
		}, getString(R.string.action_cancel));
	}

	private void inviteMember(final String member) {
		final MiniTaLib c = ta;
		final String room = currentPeer;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		if (room == null || room.length() == 0 || member == null || member.length() == 0) return;
		status.setText(getString(R.string.status_inviting_member));
		run("invite_member", new Task() {
			@Override
			public void run() throws Exception {
				final MiniTaLib.Chat chat = c.addChatMember(room, member);
				ui(new Runnable() {
					@Override
					public void run() {
						currentPeerUser = chat.peer;
						currentPeer = resolvedPeerName(chat.peer, room);
						status.setText(getString(R.string.status_member_invited));
						refreshCurrentPeerNameView();
						refreshChatInput();
						loadHistory();
					}
				});
			}
		});
	}

	private void showEditRoomTitleDialog() {
		if (!currentPeerCanManageRoom()) return;
		final EditText input = input(getString(R.string.hint_room_title), false);
		if (currentPeerUser != null && currentPeerUser.nick != null) input.setText(currentPeerUser.nick);
		showContentDialog(getString(R.string.action_edit_title), input, getString(R.string.action_save), new Runnable() {
			@Override
			public void run() {
				updateRoomTitle(input.getText().toString().trim());
			}
		}, getString(R.string.action_cancel));
	}

	private void updateRoomTitle(final String titleValue) {
		final MiniTaLib c = ta;
		final String room = currentPeer;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		if (room == null || room.length() == 0 || titleValue == null || titleValue.length() == 0) return;
		status.setText(getString(R.string.status_saving_room));
		run("room_title", new Task() {
			@Override
			public void run() throws Exception {
				final MiniTaLib.Chat chat = c.setChatTitle(room, titleValue);
				ui(new Runnable() {
					@Override
					public void run() {
						updateCurrentRoom(chat);
						status.setText(getString(R.string.status_room_saved));
					}
				});
			}
		});
	}

	private void showEditChannelUsernameDialog() {
		if (!currentPeerCanManageRoom() || !currentPeerIsChannel()) return;
		final EditText input = input(getString(R.string.hint_channel_username), false);
		if (currentPeerUser != null && currentPeerUser.login != null) input.setText(currentPeerUser.login);
		showContentDialog(getString(R.string.action_edit_username), input, getString(R.string.action_save), new Runnable() {
			@Override
			public void run() {
				updateChannelUsername(input.getText().toString().trim());
			}
		}, getString(R.string.action_cancel));
	}

	private void updateChannelUsername(final String usernameValue) {
		final MiniTaLib c = ta;
		final String room = currentPeer;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		if (room == null || room.length() == 0) return;
		if (currentPeerUser != null
				&& (currentPeerUser.login == null || currentPeerUser.login.length() == 0)
				&& usernameValue != null
				&& usernameValue.length() > 0) {
			showUsernameReservationPaymentSheet(
				usernameValue,
				getString(R.string.username_reservation_payment_details_channel, currentPeerUser.nick == null ? room : currentPeerUser.nick),
				new Runnable() {
					@Override
					public void run() {
						updateChannelUsernameConfirmed(room, usernameValue);
					}
				}
			);
			return;
		}
		updateChannelUsernameConfirmed(room, usernameValue);
	}

	private void updateChannelUsernameConfirmed(final String room, final String usernameValue) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		status.setText(getString(R.string.status_saving_room));
		run("channel_username", new Task() {
			@Override
			public void run() throws Exception {
				final MiniTaLib.Chat chat = c.setChannelUsername(room, usernameValue);
				ui(new Runnable() {
					@Override
					public void run() {
						updateCurrentRoom(chat);
						status.setText(getString(R.string.status_room_saved));
					}
				});
			}
		});
	}

	private void showRemoveMemberDialog() {
		if (!currentPeerCanManageRoom()) return;
		final EditText input = input(getString(R.string.hint_username_or_id), false);
		showContentDialog(getString(R.string.action_remove_member), input, getString(R.string.action_remove_member), new Runnable() {
			@Override
			public void run() {
				removeMember(input.getText().toString().trim());
			}
		}, getString(R.string.action_cancel));
	}

	private void removeMember(final String member) {
		final MiniTaLib c = ta;
		final String room = currentPeer;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		if (room == null || room.length() == 0 || member == null || member.length() == 0) return;
		status.setText(getString(R.string.status_removing_member));
		run("remove_member", new Task() {
			@Override
			public void run() throws Exception {
				final MiniTaLib.Chat chat = c.removeChatMember(room, member);
				ui(new Runnable() {
					@Override
					public void run() {
						updateCurrentRoom(chat);
						status.setText(getString(R.string.status_member_removed));
						loadHistory();
					}
				});
			}
		});
	}

	private void updateCurrentRoom(MiniTaLib.Chat chat) {
		if (chat == null || chat.peer == null) return;
		currentPeerUser = chat.peer;
		currentPeer = resolvedPeerName(chat.peer, chat.id);
		refreshCurrentPeerNameView();
		refreshChatInput();
		loadChats();
	}

	private LinearLayout userProfileRow(String titleText, final String value, final String copyLabel) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.VERTICAL);
		row.setPadding(pad, gap, pad, gap);
		row.setBackgroundDrawable(copyLabel == null ? shape(surface, 0, elementRadius()) : pressable(surface, surfaceHi, 0, elementRadius()));
		TextView titleView = label(titleText);
		titleView.setTextColor(muted);
		titleView.setTextSize(13);
		row.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
		TextView valueView = label(value);
		valueView.setTextColor(textColor);
		if ("user id".equals(copyLabel)) valueView.setTypeface(Typeface.MONOSPACE);
		row.addView(valueView, new LinearLayout.LayoutParams(-1, -2));
		if (copyLabel != null && value != null && value.length() > 0) {
			row.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					String copyValue = "username".equals(copyLabel) && value.startsWith("@") ? value.substring(1) : value;
					copyToClipboard(copyLabel, copyValue);
				}
			});
		}
		return row;
	}

	private void copyCurrentPeerID() {
		MiniTaLib.User user = currentHeaderUser();
		if (user == null || user.id.length() == 0) {
			status.setText(getString(R.string.status_id_not_loaded));
			return;
		}
		copyToClipboard("user id", user.id);
	}

	private TextView clickableUserID(final String value) {
		TextView id = label(value);
		id.setTextColor(textColor);
		id.setTypeface(Typeface.MONOSPACE);
		id.setPadding(pad, gap, pad, gap);
		id.setBackgroundDrawable(pressable(surface, surfaceHi, 0, elementRadius()));
		id.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				copyToClipboard("user id", value);
			}
		});
		return id;
	}

	private void confirmDeleteCurrentChat() {
		final String peerName = currentPeer == null ? "" : currentPeer;
		if (peerName.length() == 0) return;
		showSwipeConfirmDialog(
			getString(R.string.confirm_delete_chat),
			peerName,
			getString(R.string.delete_slide_hint),
			new Runnable() {
				@Override
				public void run() {
					deleteCurrentChat(peerName);
				}
			}
		);
	}

	private void confirmLeaveCurrentRoom() {
		final String peerName = currentPeer == null ? "" : currentPeer;
		if (peerName.length() == 0 || !currentPeerIsRoom()) return;
		showConfirmDialog(getString(R.string.confirm_leave_chat), peerName, getString(R.string.action_leave_chat), new Runnable() {
			@Override
			public void run() {
				leaveCurrentRoom(peerName);
			}
		});
	}

	private void showSwipeConfirmDialog(String titleText, String detailText, String hintText, final Runnable onConfirm) {
		final Dialog dialog = new Dialog(this);
		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(pad, pad, pad, pad);
		box.setBackgroundDrawable(shape(surface, 0, buttonRadius()));
		TextView title = title(titleText);
		box.addView(title, new LinearLayout.LayoutParams(-1, -2));
		TextView details = label(detailText == null ? "" : detailText);
		details.setTextColor(muted);
		LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(-1, -2);
		detailsLp.setMargins(0, 0, 0, gap);
		box.addView(details, detailsLp);
		final PaymentSliderView slider = new PaymentSliderView(this, hintText);
		slider.setContentDescription(hintText);
		slider.setOnConfirmAction(new Runnable() {
			@Override
			public void run() {
				dialog.dismiss();
				if (onConfirm != null) onConfirm.run();
			}
		});
		box.addView(slider, new LinearLayout.LayoutParams(-1, dp(56)));
		Button cancel = button(getString(R.string.action_cancel), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(-1, -2);
		cancelLp.setMargins(0, gap, 0, 0);
		box.addView(cancel, cancelLp);
		dialog.setContentView(box);
		showStyledDialog(dialog);
	}

	private void deleteCurrentChat(final String peerName) {
		final MiniTaLib c = ta;
		if (c == null || peerName.length() == 0) return;
		run("delete_chat", new Task() {
			@Override
			public void run() throws Exception {
				c.deleteChat(peerName);
				ChatCache.deleteChat(MainActivity.this, SessionStore.server(MainActivity.this, DEFAULT_SERVER), myLogin, peerName);
				ui(new Runnable() {
					@Override
					public void run() {
						if (peerName.equals(currentPeer)) {
							currentPeer = "";
							currentPeerUser = null;
							currentPeerBanned = false;
							currentPeerBannedByMe = false;
							currentPeerBannedMe = false;
							showChats();
						}
						status.setText(getString(R.string.status_chat_deleted));
					}
				});
			}
		});
	}

	private void leaveCurrentRoom(final String peerName) {
		final MiniTaLib c = ta;
		if (c == null || peerName.length() == 0) return;
		final String me = myID != null && myID.length() > 0 ? myID : myLogin;
		status.setText(getString(R.string.status_leaving_chat));
		run("leave_chat", new Task() {
			@Override
			public void run() throws Exception {
				c.leaveChat(peerName, me);
				ChatCache.deleteChat(MainActivity.this, SessionStore.server(MainActivity.this, DEFAULT_SERVER), myLogin, peerName);
				ui(new Runnable() {
					@Override
					public void run() {
						if (peerName.equals(currentPeer)) {
							currentPeer = "";
							currentPeerUser = null;
							currentPeerBanned = false;
							currentPeerBannedByMe = false;
							currentPeerBannedMe = false;
							showChats();
						}
						status.setText(getString(R.string.status_left_chat));
					}
				});
			}
		});
	}

	private void confirmBanCurrentPeer() {
		final String peerName = currentPeer == null ? "" : currentPeer;
		if (peerName.length() == 0) return;
		showConfirmDialog(getString(R.string.confirm_ban_user), peerName, getString(R.string.action_ban_user), new Runnable() {
			@Override
			public void run() {
				banCurrentPeer(peerName);
			}
		});
	}

	private void confirmUnbanCurrentPeer() {
		final String peerName = currentPeer == null ? "" : currentPeer;
		if (peerName.length() == 0) return;
		showConfirmDialog(getString(R.string.confirm_unban_user), peerName, getString(R.string.action_unban_user), new Runnable() {
			@Override
			public void run() {
				unbanCurrentPeer(peerName);
			}
		});
	}

	private void banCurrentPeer(final String peerName) {
		final MiniTaLib c = ta;
		if (c == null || peerName.length() == 0) return;
		run("ban_user", new Task() {
			@Override
			public void run() throws Exception {
				c.banUser(peerName);
				ui(new Runnable() {
					@Override
					public void run() {
						status.setText(getString(R.string.status_user_banned));
						if (peerName.equals(currentPeer)) {
							currentPeerBanned = true;
							currentPeerBannedByMe = true;
							showChat();
							loadHistory();
						}
						loadChats();
					}
				});
			}
		});
	}

	private void unbanCurrentPeer(final String peerName) {
		final MiniTaLib c = ta;
		if (c == null || peerName.length() == 0) return;
		run("unban_user", new Task() {
			@Override
			public void run() throws Exception {
				c.unbanUser(peerName);
				ui(new Runnable() {
					@Override
					public void run() {
						status.setText(getString(R.string.status_user_unbanned));
						if (peerName.equals(currentPeer)) {
							currentPeerBannedByMe = false;
							currentPeerBanned = currentPeerBannedMe;
							showChat();
							loadHistory();
						}
						loadChats();
					}
				});
			}
		});
	}

	private void styleList(ListView list, boolean messages) {
		list.setDivider(new ColorDrawable(Color.TRANSPARENT));
		list.setPadding(0, 0, 0, 0);
		list.setSelector(new ColorDrawable(Color.TRANSPARENT));
		list.setClipToPadding(false);
	}

	private void showInfoDialog(String titleText, String message) {
		TextView body = label(message == null ? "" : message);
		body.setTextColor(muted);
		body.setPadding(gap, 0, gap, 0);
		showContentDialog(titleText, body, getString(R.string.action_ok), null, null);
	}

	private void showConfirmDialog(String titleText, String message, String primaryText, Runnable primaryAction) {
		TextView body = label(message == null ? "" : message);
		body.setTextColor(muted);
		body.setPadding(gap, 0, gap, 0);
		showContentDialog(titleText, body, primaryText, primaryAction, getString(R.string.action_cancel));
	}

	private void showActionDialog(final String[] actions, final ChoiceHandler handler) {
		if (actions == null || actions.length == 0) return;
		final Dialog dialog = new Dialog(this);
		LinearLayout box = dialogBox();
		for (int i = 0; i < actions.length; i++) {
			final int which = i;
			Button action = button(actions[i], new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					dialog.dismiss();
					if (handler != null) handler.onChoice(which);
				}
			});
			box.addView(spaced(action));
		}
		dialog.setContentView(box);
		showStyledDialog(dialog);
	}

	private void showContentDialog(String titleText, View contentView, String primaryText, final Runnable primaryAction, String secondaryText) {
		final Dialog dialog = new Dialog(this);
		LinearLayout box = dialogBox();
		if (titleText != null && titleText.length() > 0) {
			box.addView(title(titleText), new LinearLayout.LayoutParams(-1, -2));
		}
		if (contentView != null) {
			ScrollView scroll = new BoundedScrollView(this, getResources().getDisplayMetrics().heightPixels * 3 / 5);
			scroll.setFillViewport(false);
			scroll.setBackgroundColor(Color.TRANSPARENT);
			scroll.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
			scroll.addView(contentView, new ScrollView.LayoutParams(-1, -2));
			LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, -2);
			scrollLp.setMargins(0, 0, 0, gap);
			box.addView(scroll, scrollLp);
		}
		LinearLayout buttons = new LinearLayout(this);
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		if (secondaryText != null && secondaryText.length() > 0) {
			Button secondary = button(secondaryText, new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					dialog.dismiss();
				}
			});
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
			lp.setMargins(0, 0, gap / 2, 0);
			buttons.addView(secondary, lp);
		}
		if (primaryText != null && primaryText.length() > 0) {
			Button primaryActionButton = primaryButton(primaryText, new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					dialog.dismiss();
					if (primaryAction != null) primaryAction.run();
				}
			});
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
			lp.setMargins(secondaryText != null && secondaryText.length() > 0 ? gap / 2 : 0, 0, 0, 0);
			buttons.addView(primaryActionButton, lp);
		}
		if (buttons.getChildCount() > 0) {
			box.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
		}
		dialog.setContentView(box);
		showStyledDialog(dialog);
	}

	private LinearLayout dialogBox() {
		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(pad, pad, pad, pad);
		box.setBackgroundDrawable(shape(surface, 0, buttonRadius()));
		return box;
	}

	private static final class BoundedScrollView extends ScrollView {
		private final int maxHeight;

		BoundedScrollView(android.content.Context context, int maxHeight) {
			super(context);
			this.maxHeight = maxHeight;
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			int mode = MeasureSpec.getMode(heightMeasureSpec);
			int size = MeasureSpec.getSize(heightMeasureSpec);
			int height = mode == MeasureSpec.UNSPECIFIED ? maxHeight : Math.min(size, maxHeight);
			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
		}
	}

	private void showStyledDialog(Dialog dialog) {
		configureDialogWindow(dialog);
		dialog.show();
		configureDialogWindow(dialog);
	}

	private void configureDialogWindow(Dialog dialog) {
		Window window = dialog.getWindow();
		if (window == null) return;
		window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		window.setGravity(Gravity.BOTTOM);
		window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
	}

	private LinearLayout row(Button...buttons) {
		LinearLayout r = new LinearLayout(this);
		r.setOrientation(LinearLayout.HORIZONTAL);
		for (Button b: buttons) {
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
			lp.setMargins(gap / 2, 0, gap / 2, 0);
			r.addView(b, lp);
		}
		return r;
	}

	private LinearLayout row(ImageButton button) {
		LinearLayout r = new LinearLayout(this);
		r.setOrientation(LinearLayout.HORIZONTAL);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight);
		lp.setMargins(gap / 2, 0, gap / 2, 0);
		r.addView(button, lp);
		return r;
	}

	private LinearLayout mixedRow(View first, View second, boolean firstFixed) {
		LinearLayout r = new LinearLayout(this);
		r.setOrientation(LinearLayout.HORIZONTAL);
		LinearLayout.LayoutParams firstLp = new LinearLayout.LayoutParams(
			firstFixed ? buttonMinHeight : 0,
			firstFixed ? buttonMinHeight : -2,
			firstFixed ? 0 : 1
		);
		firstLp.setMargins(gap / 2, 0, gap / 2, 0);
		r.addView(first, firstLp);
		LinearLayout.LayoutParams secondLp = new LinearLayout.LayoutParams(
			firstFixed ? 0 : buttonMinHeight,
			firstFixed ? -2 : buttonMinHeight,
			firstFixed ? 1 : 0
		);
		secondLp.setMargins(gap / 2, 0, gap / 2, 0);
		r.addView(second, secondLp);
		return r;
	}

	private LinearLayout navRow(View...buttons) {
		LinearLayout r = new LinearLayout(this);
		r.setOrientation(LinearLayout.HORIZONTAL);
		r.setGravity(Gravity.CENTER);
		int height = dp(56);
		for (View b: buttons) {
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, height, 1);
			lp.setMargins(gap / 2, 0, gap / 2, 0);
			r.addView(b, lp);
		}
		return r;
	}

	private ScrollView pageScrollView() {
		ScrollView scroll = new ScrollView(this);
		scroll.setFillViewport(false);
		scroll.setBackgroundColor(bg);
		scroll.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
		return scroll;
	}

	private LinearLayout messageBar() {
		LinearLayout r = new LinearLayout(this);
		r.setOrientation(LinearLayout.HORIZONTAL);
		r.setGravity(Gravity.BOTTOM);
		LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, -2, 1);
		inputLp.setMargins(0, 0, gap, 0);
		if (text.getParent() instanceof ViewGroup) {
			((ViewGroup) text.getParent()).removeView(text);
		}
		r.addView(text, inputLp);
		ImageButton attachButton = inputIconButton(R.drawable.ic_attach, getString(R.string.attachment_attach), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showAttachmentActions();
			}
		});
		LinearLayout.LayoutParams attachLp = new LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight);
		attachLp.setMargins(0, 0, gap, 0);
		r.addView(attachButton, attachLp);

		sendButton = inputIconButton(R.drawable.ic_send, getString(R.string.action_send), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					send();
				}
			});
		LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight);
		r.addView(sendButton, sendLp);
		return r;
	}

	private TextView bannedChatBlock() {
		TextView block = label(getString(R.string.chat_banned));
		block.setTextColor(muted);
		block.setTextSize(16);
		block.setGravity(Gravity.CENTER);
		block.setPadding(pad, pad, pad, pad);
		block.setBackgroundDrawable(shape(surface, 0, elementRadius()));
		return block;
	}

	private View readOnlyRoomBlock() {
		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(pad, pad, pad, pad);
		box.setBackgroundDrawable(shape(surface, 0, elementRadius()));
		if (currentPeerIsChannel()) {
			box.addView(primaryButton(getString(R.string.action_donate), new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showDastarsTransferDialog(currentPeer);
				}
			}), new LinearLayout.LayoutParams(-1, -2));
		} else {
			TextView block = label(getString(R.string.room_read_only));
			block.setTextColor(muted);
			block.setTextSize(16);
			block.setGravity(Gravity.CENTER);
			box.addView(block, new LinearLayout.LayoutParams(-1, -2));
		}
		return box;
	}

	private void setAuthLoading(boolean loading, boolean sendingCode) {
		String idle = waitingEmailCode ? getString(R.string.action_login) : getString(R.string.action_next);
		String busy = sendingCode ? getString(R.string.status_sending_code) : getString(R.string.status_checking_code);
		setButtonBusy(loginButton, loading, busy, idle, true);
		if (resendEmailCodeButton != null) {
			if (loading) {
				setButtonEnabledStyle(resendEmailCodeButton, false, false);
				setButtonRequestBusy(resendEmailCodeButton, true);
			} else {
				setButtonRequestBusy(resendEmailCodeButton, false);
				updateEmailCodeCooldown();
			}
		}
		if (loading) status.setText(sendingCode ? getString(R.string.status_sending_code) : getString(R.string.status_checking_code));
	}

	private void setSendLoading(boolean loading) {
		if (sendButton != null) {
			sendButton.setEnabled(!loading);
			setButtonRequestBusy(sendButton, loading);
			sendButton.setBackgroundDrawable(pressable(
				loading ? blend(surface, Color.BLACK, 0.25f) : surface,
				loading ? blend(surface, Color.BLACK, 0.18f) : surfaceHi,
				0,
				elementRadius()
			));
			sendButton.setColorFilter(loading ? blend(textColor, bg, 0.55f) : textColor);
			sendButton.setContentDescription(loading ? getString(R.string.status_sending) : getString(R.string.action_send));
		}
		if (text != null) text.setEnabled(!loading);
		if (loading) status.setText(getString(R.string.status_sending));
	}

	private View spaced(View v) {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.setMargins(0, 0, 0, gap);
		v.setLayoutParams(lp);
		return v;
	}

	private LinearLayout.LayoutParams fill() {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1);
		lp.setMargins(0, 0, 0, gap);
		return lp;
	}

	private GradientDrawable shape(int fill, int stroke, int radius) {
		GradientDrawable d = new GradientDrawable();
		d.setColor(fill);
		d.setCornerRadius(radius);
		if (stroke != 0) d.setStroke(dp(1), stroke);
		return d;
	}

	private Drawable pressable(int normal, int pressed, int stroke, int radius) {
		StateListDrawable s = new StateListDrawable();
		s.addState(new int[] {
			android.R.attr.state_pressed
		}, shape(pressed, stroke, radius));
		s.addState(new int[] {
			android.R.attr.state_focused
		}, shape(pressed, stroke, radius));
		s.addState(new int[] {}, shape(normal, stroke, radius));
		return s;
	}

	private void initDimens() {
		float inches = screenDiagonalInches();
		float scale = (Math.max(4.0f, Math.min(6.0f, inches)) - 4.0f) / 2.0f;
		int padDp = clampInt(Math.round(12.0f + 6.0f * scale), 12, 18);
		int gapDp = clampInt(Math.round(padDp * 0.55f), 6, 10);
		int buttonPadXDp = clampInt(Math.round(padDp * 0.70f), 8, 12);
		int buttonPadYDp = clampInt(Math.round(padDp * 0.45f), 6, 8);
		int buttonMinHeightDp = clampInt(Math.round(38.0f + 6.0f * scale), 38, 44);
		pad = dp(padDp);
		gap = dp(gapDp);
		buttonPadX = dp(buttonPadXDp);
		buttonPadY = dp(buttonPadYDp);
		buttonMinHeight = dp(buttonMinHeightDp);
	}

	private float screenDiagonalInches() {
		android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
		float xdpi = metrics.xdpi;
		float ydpi = metrics.ydpi;
		if (xdpi > 0.0f && ydpi > 0.0f) {
			float widthIn = metrics.widthPixels / xdpi;
			float heightIn = metrics.heightPixels / ydpi;
			float diagonal = (float)Math.sqrt(widthIn * widthIn + heightIn * heightIn);
			if (diagonal >= 2.5f && diagonal <= 20.0f) {
				return diagonal;
			}
		}
		float densityDpi = metrics.densityDpi > 0 ? metrics.densityDpi : 160.0f;
		float widthIn = metrics.widthPixels / densityDpi;
		float heightIn = metrics.heightPixels / densityDpi;
		return (float)Math.sqrt(widthIn * widthIn + heightIn * heightIn);
	}

	private int clampInt(int value, int min, int max) {
		if (value < min) return min;
		if (value > max) return max;
		return value;
	}

	private int dp(int value) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
	}

	private void loadPalette() {
		primary = systemColor("system_accent1_600", themeColorByName("colorAccent", Color.rgb(127, 180, 255)));
		int neutral = systemColor("system_neutral1_900", Color.rgb(18, 18, 18));
		int neutral2 = systemColor("system_neutral2_800", Color.rgb(31, 31, 31));
		bg = blend(neutral, Color.BLACK, 0.35f);
		surface = blend(neutral2, Color.BLACK, 0.20f);
		surfaceHi = blend(surface, primary, 0.10f);
		textColor = systemColor("system_neutral1_50", Color.rgb(238, 238, 238));
		muted = systemColor("system_neutral2_200", Color.rgb(180, 180, 180));
		onPrimary = contrast(primary);
	}

	private int systemColor(String name, int fallback) {
		if (Build.VERSION.SDK_INT < 31) return fallback;
		int id = getResources().getIdentifier(name, "color", "android");
		return id == 0 ? fallback : getResources().getColor(id);
	}

	private int themeColor(int attr, int fallback) {
		TypedValue v = new TypedValue();
		return getTheme().resolveAttribute(attr, v, true) ? v.data : fallback;
	}

	private int themeColorByName(String attrName, int fallback) {
		if (Build.VERSION.SDK_INT < 21) return fallback;
		try {
			Class<?> attrs = Class.forName("android.R$attr");
			return themeColor(attrs.getField(attrName).getInt(null), fallback);
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private int blend(int a, int b, float t) {
		return Color.rgb(
			(int)(Color.red(a) * (1 - t) + Color.red(b) * t),
			(int)(Color.green(a) * (1 - t) + Color.green(b) * t),
			(int)(Color.blue(a) * (1 - t) + Color.blue(b) * t)
		);
	}

	private int contrast(int c) {
		double y = (Color.red(c) * 0.299 + Color.green(c) * 0.587 + Color.blue(c) * 0.114);
		return y > 150 ? Color.BLACK : Color.WHITE;
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}

	private interface Task {
		void run() throws Exception;
	}

	private interface ChoiceHandler {
		void onChoice(int which);
	}
}
