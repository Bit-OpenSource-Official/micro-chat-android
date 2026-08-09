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
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
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
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
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
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ProgressBar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Date;
import java.util.UUID;
import java.text.SimpleDateFormat;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageView;

public final class MainActivity extends Activity {
	public static final String DEFAULT_SERVER = "ms.ove.rs:8080";
	public static final String ACTION_ACCEPT_CALL = "ru.e6atb.chat.ACCEPT_CALL";
	public static final String ACTION_OPEN_CALL = "ru.e6atb.chat.OPEN_CALL";
	public static final String EXTRA_PEER = "peer";
	public static final String EXTRA_CALL = "call_peer";
	public static final String EXTRA_CHAT = "chat_peer";

	private static final int HISTORY_PAGE = 40;
	private static final long PAID_REACTION_BATCH_DELAY_MS = 500;
	private static final String[] QUICK_REACTIONS = {"👍", "❤️", "😂", "😮", "😢", "👎"};
	private static final String[] ALL_REACTIONS = {
		"👍", "❤️", "😂", "😮", "😢", "👎", "🔥", "🥰", "👏", "😁", "🤔", "🤯",
		"😱", "🤬", "🎉", "🤩", "🤮", "💩", "🙏", "👌", "🕊️", "🤡", "🥱", "🥴",
		"😍", "🐳", "❤‍🔥", "🌚", "🌭", "💯", "🤣", "⚡", "🍌", "🏆", "💔", "🤨",
		"😐", "🍓", "🍾", "💋", "🖕", "😈", "😴", "😭", "🤓", "👻", "👨‍💻", "👀",
		"🎃", "🙈", "😇", "😨", "🤝", "✍️", "🤗", "🫡", "🎅", "🎄", "☃️", "💅",
		"🤪", "🗿", "🆒", "💘", "🙉", "🦄", "😘", "💊", "🙊", "😎", "👾", "🤷"
	};
	private static final int REQ_NOTIFICATIONS = 10;
	private static final int REQ_MICROPHONE = 11;
	private static final int REQ_READ_STORAGE = 12;
	private static final int REQ_PICK_IMAGE = 13;
	private static final int REQ_PICK_FILE = 14;
	private static final int REQ_CAMERA = 15;
	private static final int REQ_QR_SCAN = 16;
	private static final String CALL_NOTIFICATION_CHANNEL = "calls_visual";
	private static final int ACTIVE_CALL_NOTIFICATION_ID = 3;
	private static final int MAX_UPLOAD_BYTES = 12 * 1024 * 1024;
	private static final int MAX_IMAGE_PREVIEW_BYTES = 12 * 1024 * 1024;
	private static final int MAX_IMAGE_PREVIEW_PX = 1280;
	private static final int USERNAME_RESERVATION_FEE_DSR = 20;
	private static final int MAX_INCOMING_CALL_AGE_SEC = 120;
	private static final long EMAIL_CODE_RESEND_DELAY_MS = 5 * 60 * 1000L;
	private static final long GITHUB_UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;
	private static final String PERMISSION_RECORD_AUDIO = "android.permission.RECORD_AUDIO";
	private static final String PERMISSION_READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";
	private static final String PERMISSION_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";
	private static final String PERMISSION_CAMERA = "android.permission.CAMERA";
	private static final String EXTRA_BOT_LINK_CONSUMED = "bot_link_consumed";
	private static final int LANGUAGE_SYSTEM_ID = 1001;
	private static final int LANGUAGE_ENGLISH_ID = 1002;
	private static final int LANGUAGE_RUSSIAN_ID = 1003;
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
	private final ExecutorService historyCacheIo = Executors.newSingleThreadExecutor();
	private final ExecutorService paidReactionIo = Executors.newSingleThreadExecutor();
	private final Map < Long, Long > pendingPaidReactionDeltas = new HashMap < Long, Long > ();
	private final Map < Long, PaidReactionBatch > paidReactionBatches = new HashMap < Long, PaidReactionBatch > ();
	private final Set < Long > seenMessages = new HashSet < Long > ();
	private final Map < String, Bitmap > imagePreviewCache = new HashMap < String, Bitmap > ();
	private final Map < String, String > imagePreviewErrors = new HashMap < String, String > ();
	private final Set < String > imagePreviewLoading = new HashSet < String > ();
	private final ArrayList < MiniTaLib.Chat > chatData = new ArrayList < MiniTaLib.Chat > ();
	private final ArrayList<ComposerMedia> composerMedia = new ArrayList<ComposerMedia>();
	private final VoiceCall voiceCall = new VoiceCall();
	private LinearLayout rootView;
	private LinearLayout content;
	private LinearLayout bottomNav;
	private EditText serverUrl;
	private EditText email;
	private EditText accountUsername;
	private EditText accountName;
	private EditText accountDescription;
	private EditText emailCode;
	private EditText password;
	private EditText accountCloudPassword;
	private EditText accountCloudPasswordCode;
	private EditText accountDeleteCode;
	private EditText contactAddress;
	private EditText peer;
	private EditText text;
	private LinearLayout composerMediaBar;
	private boolean composerSending;
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
	private LinearLayout commentInputContainer;
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
	private String myDescription = "";
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
	private MiniTaLib.Message currentCommentPost;
	private MiniTaLib.Message replyToMessage;
	private MiniTaLib.Message editingMessage;

	private static final class ComposerMedia {
		Uri uri;
		String name;
		String mime;
		String localPath;
		String fileId;
		long size;
	}
	private final Runnable channelHistoryReload = new Runnable() {
		@Override public void run() {
			if (page == Page.CHAT && currentPeerIsChannel()) loadHistory();
		}
	};
	private long oldestMessage;
	private boolean historyLoaded;
	private boolean hasOlderMessages;
	private boolean loadingOlderMessages;
	private volatile int chatOpenGeneration;
	private volatile int historyRequestGeneration;
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
		setContentView(shell());
		setStatusBarColorCompat(bg);
		createCallNotificationChannel();
		requestNotifications();
		requestReadStoragePermission();
		restoreSession();
		handleIntent(getIntent());
		maybeOfferGithubUpdate();
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
		if (page == Page.CHANNEL_COMMENTS || page == Page.CHANNEL_SETTINGS) {
			showChat();
			loadHistory();
			return true;
		}
		if (page == Page.CHAT || page == Page.SETTINGS) {
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
				openChatImmediately(peerName, null, false, false, false, null);
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
				|| page == Page.SETTINGS_AUTHORIZATION
				|| page == Page.SETTINGS_DELETE_ACCOUNT
				|| page == Page.SETTINGS_LOGOUT
				|| page == Page.SETTINGS_SERVER
				|| page == Page.SETTINGS_LANGUAGE
				|| page == Page.SETTINGS_INTERFACE;
	}

	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == REQ_CAMERA) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				startOAuthQrScanner();
			} else {
				status.setText(getString(R.string.oauth_camera_required));
			}
			return;
		}
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
		maybeOfferGithubUpdate();
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
		for (PaidReactionBatch batch : paidReactionBatches.values()) {
			main.removeCallbacks(batch.flush);
		}
		paidReactionBatches.clear();
		paidReactionIo.shutdownNow();
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
		myID = SessionStore.userId(this);
		myLogin = SessionStore.login(this);
		lastUpdate = SessionStore.lastUpdate(this);
		ta = new MiniTaLib(this, url, token, myID, myLogin);
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

				SessionStore.save(MainActivity.this, url, token, myID, myLogin);

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
		if (isOAuthIntent(intent)) {
			Uri data = intent.getData();
			String userCode = data == null ? "" : data.getQueryParameter("user_code");
			if (userCode != null && !userCode.trim().isEmpty()) {
				openOAuthDeviceRequest(userCode.trim());
			}
			return;
		}
		if (isBotIntent(intent)) {
			if (!intent.getBooleanExtra(EXTRA_BOT_LINK_CONSUMED, false)) {
				BotDeepLinkParser.Link link = BotDeepLinkParser.parse(
						intent.getData() == null ? "" : intent.getData().toString());
				if (link != null) {
					intent.putExtra(EXTRA_BOT_LINK_CONSUMED, true);
					openBotDeepLink(link);
				}
			}
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
		if (ACTION_ACCEPT_CALL.equals(intent.getAction())) {
			String peerName = intent.getStringExtra(EXTRA_PEER);
			if (peerName != null && !peerName.trim().isEmpty()) {
				acceptIncomingCall(peerName.trim());
				return;
			}
		}
		if (intent.hasExtra(EXTRA_CHAT)) {
			String chatPeer = intent.getStringExtra(EXTRA_CHAT);
			if (chatPeer != null && !chatPeer.isEmpty()) {
				openChatIfExists(chatPeer.trim());
			}
		}
	}

	private boolean requiresSession(Intent intent) {
		if (intent == null) return false;
		return isOAuthIntent(intent)
				|| isBotIntent(intent)
				|| ACTION_OPEN_CALL.equals(intent.getAction())
				|| ACTION_ACCEPT_CALL.equals(intent.getAction())
				|| intent.hasExtra(EXTRA_CALL)
				|| intent.hasExtra(EXTRA_CHAT);
	}

	private boolean isBotIntent(Intent intent) {
		return intent != null
				&& Intent.ACTION_VIEW.equals(intent.getAction())
				&& intent.getData() != null
				&& BotDeepLinkParser.parse(intent.getData().toString()) != null;
	}

	private boolean isOAuthIntent(Intent intent) {
		if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return false;
		Uri data = intent.getData();
		if (data == null) return false;
		boolean custom = "ovechat".equalsIgnoreCase(data.getScheme())
				&& "authorize".equalsIgnoreCase(data.getHost());
		boolean web = "https".equalsIgnoreCase(data.getScheme())
				&& "ms.ove.rs".equalsIgnoreCase(data.getHost())
				&& "/oauth/device".equals(data.getPath());
		return custom || web;
	}

	private void openBotDeepLink(final BotDeepLinkParser.Link link) {
		final MiniTaLib c = ta;
		if (c == null || link == null) return;
		openChatImmediately(link.login, null, false, false, false, new Runnable() {
			@Override public void run() {
				sendChatMessage(currentPeer, link.startCommand(), false);
			}
		});
	}

	private void openOAuthDeviceRequest(String rawCode) {
		final String userCode = OAuthCodeParser.parse(rawCode);
		if (userCode.length() == 0) {
			status.setText(getString(R.string.oauth_invalid_code));
			return;
		}
		final MiniTaLib client = ta;
		if (client == null) return;
		run("oauth_device", new Task() {
			@Override
			public void run() throws Exception {
				final MiniTaLib.OAuthDeviceRequest request = client.oauthDeviceRequest(userCode);
				ui(new Runnable() {
					@Override
					public void run() {
						if (!"pending".equals(request.status)) {
							status.setText(getString(R.string.oauth_already_decided));
							return;
						}
						showOAuthConfirmDialog(request);
					}
				});
			}
		});
	}

	private void showOAuthConfirmDialog(final MiniTaLib.OAuthDeviceRequest request) {
		final Dialog dialog = new Dialog(this);
		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(pad, pad, pad, pad);
		box.setBackgroundDrawable(shape(surface, 0, buttonRadius()));
		box.addView(title(getString(R.string.oauth_authorize_title)), new LinearLayout.LayoutParams(-1, -2));
		String detail = getString(
				R.string.oauth_authorize_body,
				request.clientName.length() == 0 ? request.clientID : request.clientName,
				request.audience,
				request.userCode
		);
		if (request.actionDescription.length() > 0) {
			detail += "\n\n" + getString(R.string.oauth_action_description, request.actionDescription);
		}
		TextView details = label(detail);
		details.setTextColor(muted);
		box.addView(spaced(details));
		final PaymentSliderView slider = paymentSlider(getString(R.string.oauth_swipe_approve));
		slider.setOnConfirmAction(new Runnable() {
			@Override public void run() {
				dialog.dismiss();
				decideOAuth(request.userCode, true);
			}
		});
		box.addView(slider, new LinearLayout.LayoutParams(-1, dp(56)));
		Button reject = button(getString(R.string.action_reject), new View.OnClickListener() {
			@Override public void onClick(View view) {
				dialog.dismiss();
				decideOAuth(request.userCode, false);
			}
		});
		box.addView(spaced(reject));
		setScrollableDialogContent(dialog, box);
		showStyledDialog(dialog);
	}

	private void decideOAuth(final String userCode, final boolean approve) {
		final MiniTaLib client = ta;
		if (client == null) return;
		run("oauth_decision", new Task() {
			@Override public void run() throws Exception {
				client.oauthDeviceDecision(userCode, approve);
				ui(new Runnable() {
					@Override public void run() {
						status.setText(getString(approve ? R.string.oauth_authorized : R.string.oauth_rejected));
					}
				});
			}
		});
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
		TextView cloudPasswordWarning = label(getString(R.string.auth_cloud_password_warning));
		cloudPasswordWarning.setTextColor(textColor);
		cloudPasswordWarning.setPadding(gap, gap, gap, gap);
		cloudPasswordWarning.setBackgroundDrawable(shape(surfaceHi, primary, elementRadius()));
		content.addView(spaced(cloudPasswordWarning));
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
					PaymentSliderView resetSlider = paymentSlider(getString(R.string.reset_cloud_password_slide_hint), true);
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
		replyToMessage = null;
		commentInputContainer = null;
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
				String chatPeer = resolvedPeerName(chat.peer, chat.id);
				openChatImmediately(chatPeer, chat.peer, chat.banned, chat.bannedByMe, chat.bannedMe, null);
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
		showActionDialog(getString(R.string.add_chat_choose_title), new String[] {
			getString(R.string.add_chat_private),
			getString(R.string.add_chat_group),
			getString(R.string.add_chat_channel)
		}, new ChoiceHandler() {
			@Override public void onChoice(int which) {
				if (which == 0) showNewPrivateChatDialog();
				else showNewRoomDialog(which == 2);
			}
		});
	}

	private void showNewPrivateChatDialog() {
		final Dialog dialog = new Dialog(this);
		LinearLayout box = dialogBox();
		box.addView(title(getString(R.string.add_chat_private)), new LinearLayout.LayoutParams(-1, -2));
		final EditText loginField = input(getString(R.string.hint_username_or_id), false);
		box.addView(spaced(loginField));
		Button add = primaryButton(getString(R.string.action_add), new View.OnClickListener() {
			@Override public void onClick(View v) {
				String value = loginField.getText().toString().trim();
				if (value.length() == 0) {
					loginField.setError(getString(R.string.field_required));
					return;
				}
				dialog.dismiss();
				openChatIfExists(value, null, true);
			}
		});
		Button cancel = button(getString(R.string.action_cancel), new View.OnClickListener() {
			@Override public void onClick(View v) { dialog.dismiss(); }
		});
		box.addView(row(cancel, add), new LinearLayout.LayoutParams(-1, -2));
		setScrollableDialogContent(dialog, box);
		showStyledDialog(dialog);
	}

	private void showNewRoomDialog(final boolean channel) {
		final Dialog dialog = new Dialog(this);
		LinearLayout box = dialogBox();
		box.addView(title(getString(channel ? R.string.add_chat_channel : R.string.add_chat_group)), new LinearLayout.LayoutParams(-1, -2));
		final EditText titleField = input(getString(R.string.hint_room_title), false);
		final EditText usernameField = input(getString(R.string.hint_channel_username), false);
		final EditText membersField = input(getString(R.string.hint_room_members), false);
		box.addView(spaced(titleField));
		if (channel) box.addView(spaced(usernameField));
		box.addView(spaced(membersField));
		Button create = primaryButton(getString(channel ? R.string.action_create_channel : R.string.action_create_group), new View.OnClickListener() {
			@Override public void onClick(View v) {
				String titleValue = titleField.getText().toString().trim();
				if (titleValue.length() == 0) {
					titleField.setError(getString(R.string.field_required));
					return;
				}
				dialog.dismiss();
				createRoom(channel, titleValue, channel ? usernameField.getText().toString().trim() : "", splitMembers(membersField.getText().toString()));
			}
		});
		Button cancel = button(getString(R.string.action_cancel), new View.OnClickListener() {
			@Override public void onClick(View v) { dialog.dismiss(); }
		});
		box.addView(row(cancel, create), new LinearLayout.LayoutParams(-1, -2));
		setScrollableDialogContent(dialog, box);
		showStyledDialog(dialog);
	}

	private void showChat() {
		boolean leavingCommentThread = page == Page.CHANNEL_COMMENTS;
		page = Page.CHAT;
		if (leavingCommentThread) replyToMessage = null;
		commentInputContainer = null;
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

	private void showChannelComments(final MiniTaLib.Message post) {
		if (!currentPeerIsChannel() || currentPeerUser == null || !currentPeerUser.commentsEnabled || post == null) return;
		currentCommentPost = post;
		replyToMessage = null;
		page = Page.CHANNEL_COMMENTS;
		if (bottomNav != null) bottomNav.setVisibility(View.GONE);
		content.removeAllViews();
		text = input(getString(R.string.channel_comment_hint), false);
		text.setSingleLine(false);
		text.setMinLines(1);
		text.setMaxLines(3);
		text.setImeOptions(EditorInfo.IME_ACTION_SEND);
		text.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override public boolean onEditorAction(TextView v, int action, KeyEvent e) {
				if (action == EditorInfo.IME_ACTION_SEND) {
					sendChannelComment();
					return true;
				}
				return false;
			}
		});
		LinearLayout header = new LinearLayout(this);
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);
		ImageButton back = headerIconButton(R.drawable.ic_back, getString(R.string.action_back), new View.OnClickListener() {
			@Override public void onClick(View v) { showChat(); loadHistory(); }
		});
		header.addView(back, new LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight));
		TextView heading = title(getString(R.string.channel_comments));
		LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(0, -2, 1);
		headingLp.setMargins(gap, 0, 0, 0);
		header.addView(heading, headingLp);
		content.addView(spaced(header));

		LinearLayout original = new LinearLayout(this);
		original.setOrientation(LinearLayout.VERTICAL);
		original.setPadding(pad, gap, pad, gap);
		original.setBackgroundDrawable(shape(surfaceHi, 0, elementRadius()));
		TextView author = label(currentPeerUser.nick);
		author.setTextColor(muted);
		author.setTextSize(13);
		original.addView(author, new LinearLayout.LayoutParams(-1, -2));
		TextView body = label(post.text == null ? "" : post.text);
		body.setText(renderMarkdown(post.text == null ? "" : post.text));
		body.setMovementMethod(LinkMovementMethod.getInstance());
		body.setTextColor(textColor);
		original.addView(body, new LinearLayout.LayoutParams(-1, -2));
		content.addView(spaced(original));

		messageRows = adapter();
		messageList = new ListView(this);
		messageList.setBackgroundColor(bg);
		messageList.setCacheColorHint(bg);
		styleList(messageList, true);
		messageList.setAdapter(messageRows);
		messageList.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
		messageList.setOnScrollListener(new AbsListView.OnScrollListener() {
			@Override public void onScrollStateChanged(AbsListView view, int scrollState) {}
			@Override public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
				if (historyLoaded && hasOlderMessages && firstVisibleItem == 0 && visibleItemCount > 0) loadOlderChannelComments();
			}
		});
		content.addView(messageList, fill());
		commentInputContainer = new LinearLayout(this);
		commentInputContainer.setOrientation(LinearLayout.VERTICAL);
		refreshCommentInput();
		content.addView(spaced(commentInputContainer));
		loadCachedChannelComments(post.id);
		loadChannelComments(post.id);
	}

	private LinearLayout commentMessageBar() {
		LinearLayout bar = new LinearLayout(this);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(Gravity.BOTTOM);
		LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, -2, 1);
		inputLp.setMargins(0, 0, gap, 0);
		bar.addView(text, inputLp);
		ImageButton send = inputIconButton(R.drawable.ic_send, getString(R.string.action_send), new View.OnClickListener() {
			@Override public void onClick(View v) { sendChannelComment(); }
		});
		bar.addView(send, new LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight));
		return bar;
	}

	private void refreshCommentInput() {
		if (commentInputContainer == null) return;
		commentInputContainer.removeAllViews();
		if (replyToMessage != null) commentInputContainer.addView(replyComposerView());
		commentInputContainer.addView(commentMessageBar());
	}

	private View replyComposerView() {
		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.HORIZONTAL);
		box.setGravity(Gravity.CENTER_VERTICAL);
		box.setPadding(gap, gap, gap, gap);
		box.setBackgroundDrawable(shape(surfaceHi, 0, elementRadius()));
		LinearLayout details = new LinearLayout(this);
		details.setOrientation(LinearLayout.VERTICAL);
		TextView heading = label(getString(R.string.replying_to, replyAuthor(replyToMessage)));
		heading.setTextColor(primary);
		heading.setTextSize(13);
		details.addView(heading, new LinearLayout.LayoutParams(-1, -2));
		TextView preview = label(replySummary(replyToMessage));
		preview.setTextColor(muted);
		preview.setTextSize(14);
		preview.setSingleLine(true);
		preview.setEllipsize(TextUtils.TruncateAt.END);
		details.addView(preview, new LinearLayout.LayoutParams(-1, -2));
		details.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) {
				if (replyToMessage != null) focusMessage(replyToMessage.id);
			}
		});
		box.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
		Button cancel = button("×", new View.OnClickListener() {
			@Override public void onClick(View v) { clearReply(); }
		});
		cancel.setContentDescription(getString(R.string.action_cancel));
		LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight);
		cancelLp.setMargins(gap, 0, 0, 0);
		box.addView(cancel, cancelLp);
		LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(-1, -2);
		boxLp.setMargins(0, 0, 0, gap / 2);
		box.setLayoutParams(boxLp);
		return box;
	}

	private void startReply(MiniTaLib.Message message) {
		if (message == null || message.id <= 0) return;
		replyToMessage = message;
		if (page == Page.CHANNEL_COMMENTS) refreshCommentInput();
		else refreshChatInput();
		if (text != null) {
			text.requestFocus();
			text.setSelection(text.length());
		}
	}

	private void clearReply() {
		replyToMessage = null;
		if (page == Page.CHANNEL_COMMENTS) refreshCommentInput();
		else if (page == Page.CHAT) refreshChatInput();
	}

	private String replyAuthor(MiniTaLib.Message message) {
		MiniTaLib.User author = messageAuthor(message);
		if (author == null) return getString(R.string.reply_to_message);
		String value = displayUser(author);
		return value.length() == 0 ? getString(R.string.reply_to_message) : value;
	}

	private MiniTaLib.User messageAuthor(MiniTaLib.Message message) {
		return MessageAuthorResolver.resolve(message, currentPeerUser);
	}

	private String replySummary(MiniTaLib.Message message) {
		if (message == null) return getString(R.string.reply_message_unavailable);
		String value;
		if (message.text != null && message.text.trim().length() > 0) {
			value = message.text.replace('\n', ' ').trim();
		} else if (message.media != null && !message.media.isEmpty()) {
			MiniTaLib.FileInfo first = message.media.get(0);
			value = first.name == null || first.name.length() == 0
					? getString(R.string.file_fallback_name) : first.name;
			if (message.media.size() > 1) value += " +" + (message.media.size() - 1);
		} else {
			value = "";
		}
		if (value.length() == 0) value = getString(R.string.reply_message_unavailable);
		return value.length() > 120 ? value.substring(0, 117) + "…" : value;
	}

	private void focusMessage(long messageId) {
		if (messageRows == null || messageList == null || messageId <= 0) return;
		int position = messageRows.positionOfMessage(messageId);
		if (position >= 0) messageList.smoothScrollToPosition(position);
	}

	private void sendChannelComment() {
		final String value = text == null ? "" : text.getText().toString().trim();
		if (value.length() == 0 || currentCommentPost == null || ta == null) return;
		final long replyToMessageId = replyToMessage == null ? 0 : replyToMessage.id;
		try {
			OutboxStore.Entry entry = OutboxStore.enqueueComment(
					this, SessionStore.server(this, DEFAULT_SERVER), OutboxDispatcher.accountKey(this),
					currentPeer, currentCommentPost.id, value, replyToMessageId);
			addMessageRow(outboxMessage(entry), false);
			text.setText("");
			if (replyToMessageId > 0) clearReply();
			if (messageList != null) messageList.setSelection(messageRows.getCount() - 1);
			dispatchOutbox(ta);
		} catch (Exception error) {
			status.setText(errorText(error));
		}
	}

	private void loadChannelComments(final long postId) {
		final MiniTaLib c = ta;
		final String channel = currentPeer;
		if (c == null || channel == null || channel.length() == 0) return;
		historyLoaded = false;
		hasOlderMessages = false;
		loadingOlderMessages = false;
		run("channel_comments_history", new Task() {
			@Override public void run() throws Exception {
				final MiniTaLib.CommentPage comments = c.getChannelComments(channel, postId, 0, HISTORY_PAGE);
				final String cachePeer = OutboxStore.cachePeer(channel, postId);
				cacheSaveHistory(cachePeer, comments.messages);
				ui(new Runnable() {
					@Override public void run() {
						if (page != Page.CHANNEL_COMMENTS || currentCommentPost == null || currentCommentPost.id != postId) return;
						if (comments.peer != null) currentPeerUser = comments.peer;
						if (comments.post != null) currentCommentPost = comments.post;
						renderChannelComments(comments.messages, postId, false);
					}
				});
			}
		});
	}

	private void loadCachedChannelComments(final long postId) {
		final String channel = currentPeer;
		final String cachePeer = OutboxStore.cachePeer(channel, postId);
		enqueueCache(new Runnable() {
			@Override public void run() {
				final List<MiniTaLib.Message> cached = ChatCache.loadHistory(MainActivity.this, SessionStore.server(MainActivity.this, DEFAULT_SERVER), myLogin, cachePeer);
				ui(new Runnable() { @Override public void run() {
					if (page == Page.CHANNEL_COMMENTS && currentCommentPost != null && currentCommentPost.id == postId) renderChannelComments(cached, postId, true);
				} });
			}
		});
	}

	private void renderChannelComments(List<MiniTaLib.Message> comments, long postId, boolean cached) {
		if (messageRows == null || comments == null) return;
		seenMessages.clear();
		oldestMessage = 0;
		ArrayList<MessageRow> rows = new ArrayList<MessageRow>();
		for (MiniTaLib.Message comment : comments) {
			if (comment != null && comment.commentPostId == postId && seenMessages.add(comment.id)) {
				if (oldestMessage == 0 || comment.id < oldestMessage) oldestMessage = comment.id;
				rows.add(toMessageRow(comment));
			}
		}
		for (OutboxStore.Entry entry : OutboxStore.load(this, SessionStore.server(this, DEFAULT_SERVER), OutboxDispatcher.accountKey(this))) {
			if (!currentPeer.equals(entry.peer) || entry.commentPostId != postId) continue;
			MiniTaLib.Message pending = outboxMessage(entry);
			if (seenMessages.add(pending.id)) rows.add(toMessageRow(pending));
		}
		messageRows.replaceRows(rows);
		historyLoaded = !cached;
		hasOlderMessages = !cached && comments.size() == HISTORY_PAGE;
		if (messageList != null && messageRows.getCount() > 0) messageList.setSelection(messageRows.getCount() - 1);
	}

	private void loadOlderChannelComments() {
		final MiniTaLib c = ta;
		if (c == null || currentCommentPost == null || loadingOlderMessages || oldestMessage <= 0) return;
		final long postId = currentCommentPost.id;
		final long before = oldestMessage;
		final String channel = currentPeer;
		loadingOlderMessages = true;
		run("older_channel_comments", new Task() {
			@Override public void run() throws Exception {
				final MiniTaLib.CommentPage pageData = c.getChannelComments(channel, postId, before, HISTORY_PAGE);
				ui(new Runnable() { @Override public void run() {
					loadingOlderMessages = false;
					if (page != Page.CHANNEL_COMMENTS || currentCommentPost == null || currentCommentPost.id != postId) return;
					ArrayList<MessageRow> rows = new ArrayList<MessageRow>();
					for (MiniTaLib.Message comment : pageData.messages) if (seenMessages.add(comment.id)) {
						oldestMessage = Math.min(oldestMessage, comment.id);
						rows.add(toMessageRow(comment));
					}
					hasOlderMessages = pageData.messages.size() == HISTORY_PAGE && !rows.isEmpty();
					messageRows.insertRows(rows, 0);
				} });
			}
		});
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
		openChatImmediately(peerName, null, false, false, false, null);
	}

	private void openChatImmediately(
			String peerName,
			MiniTaLib.User knownPeer,
			boolean banned,
			boolean bannedByMe,
			boolean bannedMe,
			Runnable afterServerLoad
	) {
		String normalized = peerName == null ? "" : peerName.trim();
		if (normalized.length() == 0) return;
		++chatOpenGeneration;
		++historyRequestGeneration;
		currentPeer = normalized;
		currentPeerUser = knownPeer;
		currentPeerBanned = banned;
		currentPeerBannedByMe = bannedByMe;
		currentPeerBannedMe = bannedMe;
		currentCommentPost = null;
		replyToMessage = null;
		oldestMessage = 0;
		historyLoaded = false;
		hasOlderMessages = false;
		loadingOlderMessages = false;
		showChat();
		loadHistory(afterServerLoad);
	}

	private String resolvedPeerName(MiniTaLib.User user, String fallback) {
		if (user != null && user.login != null && user.login.length() > 0) return user.login;
		if (user != null && user.id != null && user.id.length() > 0) return user.id;
		return fallback == null ? "" : fallback;
	}

	private void createRoom(final boolean channel, final String titleValue, final String usernameValue, final ArrayList<String> members) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
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
		createRoomConfirmed(channel, titleValue, usernameValue, members, null, false);
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
						status.setText(getString(channel ? R.string.status_channel_created : R.string.status_group_created));
						openChatImmediately(resolvedPeerName(chat.peer, chat.id), chat.peer, false, false, false, null);
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

		final PaymentSliderView slider = paymentSlider(getString(R.string.payment_slide_hint));
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

		setScrollableDialogContent(dialog, box);
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
		if (replyToMessage != null) chatInputContainer.addView(replyComposerView());
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
		return currentPeerCanManageRoom();
	}

	private boolean currentPeerCanManageRoom() {
		return currentPeerIsRoom()
			&& (currentPeerUser.canManage
				|| (myID != null && myID.length() > 0 && myID.equals(currentPeerUser.ownerId)));
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
		String accountKey = myID == null || myID.trim().length() == 0
				? (myLogin == null ? "" : myLogin.trim())
				: myID.trim();
		if (accountKey.length() == 0) {
			return new MiniTaLib.NodeStatus("e2e", getString(R.string.node_e2e_keys), "check_failed", 0, 1);
		}
		try {
			rs.ove.crypt.proto.E2ECipher.Identity local = SessionStore.e2eIdentity(this, accountKey);
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
		settings.addView(settingsRow(getString(R.string.settings_authorization), getString(R.string.settings_authorization_subtitle), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showSettingsAuthorization();
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
		settings.addView(settingsVersionText());

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
						downloadAndInstallGithubUpdate(update);
					}
				});
			}
		});
	}

	private void maybeOfferGithubUpdate() {
		final String repository = BuildConfig.GITHUB_REPOSITORY == null ? "" : BuildConfig.GITHUB_REPOSITORY.trim();
		if (repository.length() == 0) return;
		long now = System.currentTimeMillis();
		long lastCheck = SessionStore.lastGithubUpdateCheckAt(this);
		if (lastCheck > 0 && now - lastCheck < GITHUB_UPDATE_CHECK_INTERVAL_MS) return;
		SessionStore.lastGithubUpdateCheckAt(this, now);
		io.execute(new Runnable() {
			@Override
			public void run() {
				try {
					final GithubOtaUpdater.Update update = GithubOtaUpdater.findLatest(
							repository,
							getPackageName(),
							BuildConfig.VERSION_NAME,
							BuildConfig.VERSION_CODE);
					if (update == null) return;
					ui(new Runnable() {
						@Override
						public void run() {
							if (isFinishing()) return;
							showGithubUpdateOffer(update);
						}
					});
				} catch (Exception ignored) {
				}
			}
		});
	}

	private void showGithubUpdateOffer(final GithubOtaUpdater.Update update) {
		showConfirmDialog(
				getString(R.string.update_available_title),
				getString(R.string.update_available_body, BuildConfig.VERSION_NAME, updateVersionLabel(update)),
				getString(R.string.action_update),
				new Runnable() {
					@Override
					public void run() {
						downloadAndInstallGithubUpdate(update);
					}
				});
	}

	private void downloadAndInstallGithubUpdate(final GithubOtaUpdater.Update update) {
		final String versionName = updateVersionLabel(update);
		status.setText(getString(R.string.status_update_downloading, versionName));
		run("github_update_download", new Task() {
			@Override
			public void run() throws Exception {
				final File apk = GithubOtaUpdater.download(MainActivity.this, update);
				ui(new Runnable() {
					@Override
					public void run() {
						installGithubUpdate(apk, versionName);
					}
				});
			}
		});
	}

	private String updateVersionLabel(GithubOtaUpdater.Update update) {
		if (update != null && update.versionName != null && update.versionName.length() > 0) {
			return update.versionName;
		}
		return "latest";
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

	private LinearLayout settingsToggleRow(String name, String detail, boolean checked, View.OnClickListener listener) {
		LinearLayout row = settingsRow(name, detail, listener);
		if (row.getChildCount() > 1) row.removeViewAt(row.getChildCount() - 1);
		android.widget.Switch toggle = new android.widget.Switch(this);
		toggle.setChecked(checked);
		toggle.setClickable(false);
		toggle.setFocusable(false);
		row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
		return row;
	}

	private TextView settingsVersionText() {
		TextView version = label(getString(R.string.settings_app_version) + ": " + BuildConfig.VERSION_NAME);
		version.setTextSize(13);
		version.setTextColor(muted);
		version.setGravity(Gravity.CENTER);
		version.setPadding(pad, gap, pad, pad);

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.setMargins(0, gap / 2, 0, 0);
		version.setLayoutParams(lp);
		return version;
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
		box.addView(spaced(title(getString(R.string.profile_description))));
		accountDescription = input(getString(R.string.settings_description_hint), false);
		accountDescription.setSingleLine(false);
		accountDescription.setMinLines(3);
		accountDescription.setMaxLines(6);
		accountDescription.setFilters(new android.text.InputFilter[] {
				new android.text.InputFilter.LengthFilter(200)
		});
		accountDescription.setText(myDescription == null ? "" : myDescription);
		box.addView(spaced(accountDescription));
		box.addView(spaced(row(primaryButton(getString(R.string.settings_save_description), new View.OnClickListener() {
			@Override public void onClick(View v) { saveOwnDescription(v); }
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
		PaymentSliderView resetSlider = paymentSlider(getString(R.string.reset_cloud_password_slide_hint), true);
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
		PaymentSliderView resetSlider = paymentSlider(getString(R.string.reset_e2e_key_slide_hint), true);
		resetSlider.setContentDescription(getString(R.string.reset_e2e_key_slide_hint));
		resetSlider.setOnConfirmAction(new Runnable() {
			@Override
			public void run() {
				resetE2EKey();
			}
		});
		box.addView(spaced(resetSlider), new LinearLayout.LayoutParams(-1, dp(56)));
	}

	private void showSettingsAuthorization() {
		LinearLayout box = settingsPage(getString(R.string.settings_authorization), Page.SETTINGS_AUTHORIZATION);
		box.addView(spaced(label(getString(R.string.oauth_settings_help))));
		final EditText code = input(getString(R.string.oauth_code_hint), false);
		code.setSingleLine(true);
		code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
		box.addView(spaced(code));
		box.addView(spaced(row(
				primaryButton(getString(R.string.oauth_open_request), new View.OnClickListener() {
					@Override public void onClick(View view) {
						openOAuthDeviceRequest(code.getText().toString());
					}
				}),
				button(getString(R.string.action_paste), new View.OnClickListener() {
					@Override public void onClick(View view) {
						String pasted = clipboardText();
						if (pasted.length() > 0) code.setText(pasted);
					}
				})
		)));
		box.addView(spaced(primaryButton(getString(R.string.oauth_scan_qr), new View.OnClickListener() {
			@Override public void onClick(View view) {
				if (!hasPermissionCompat(PERMISSION_CAMERA)) {
					requestPermissionsCompat(new String[] { PERMISSION_CAMERA }, REQ_CAMERA);
					return;
				}
				startOAuthQrScanner();
			}
		})));
	}

	private void startOAuthQrScanner() {
		startActivityForResult(new Intent(this, QrScannerActivity.class), REQ_QR_SCAN);
	}

	private String clipboardText() {
		Object clipboard = getSystemService(CLIPBOARD_SERVICE);
		if (clipboard == null) return "";
		try {
			Object value = clipboard.getClass().getMethod("getText").invoke(clipboard);
			return value == null ? "" : value.toString();
		} catch (Exception ignored) {
			return "";
		}
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
		PaymentSliderView deleteSlider = paymentSlider(getString(R.string.delete_account_slide_hint), true);
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
		PaymentSliderView logoutSlider = paymentSlider(getString(R.string.logout_slide_hint), true);
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
									openChatImmediately(resolvedPeerName(user, user.id), user, false, false, false, null);
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
		RadioButton button = new ChoiceRadioButton(this, choiceButtonTextInset());
		button.setId(id);
		button.setText(safeDisplayText(label));
		styleChoiceButton(button, true);
		RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(-1, -2);
		lp.setMargins(0, 0, 0, gap / 2);
		group.addView(button, lp);
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
		RadioButton button = new ChoiceRadioButton(this, choiceButtonTextInset());
		button.setId(id);
		button.setText(safeDisplayText(label));
		styleChoiceButton(button, true);
		RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(-1, -2);
		lp.setMargins(0, 0, 0, gap / 2);
		group.addView(button, lp);
	}

	private int languageId(String language) {
		if (AppLocale.ENGLISH.equals(language)) return LANGUAGE_ENGLISH_ID;
		if (AppLocale.RUSSIAN.equals(language)) return LANGUAGE_RUSSIAN_ID;
		return LANGUAGE_SYSTEM_ID;
	}

	private String selectedLanguage() {
		if (languageGroup == null) return AppLocale.SYSTEM;
		int checked = languageGroup.getCheckedRadioButtonId();
		if (checked == LANGUAGE_ENGLISH_ID) return AppLocale.ENGLISH;
		if (checked == LANGUAGE_RUSSIAN_ID) return AppLocale.RUSSIAN;
		return AppLocale.SYSTEM;
	}

	private String languageLabel(String language) {
		if (AppLocale.ENGLISH.equals(language)) return getString(R.string.language_english);
		if (AppLocale.RUSSIAN.equals(language)) return getString(R.string.language_russian);
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
				SessionStore.save(MainActivity.this, server(), c.token(), myID, myLogin);
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
				SessionStore.save(MainActivity.this, server(), c.token(), myID, myLogin);
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

	private void saveOwnDescription(final View actionButton) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		final String value = accountDescription == null ? "" : accountDescription.getText().toString().trim();
		status.setText(getString(R.string.status_saving_description));
		runButtonTask("profile-description", actionButton, true, new Task() {
			@Override public void run() throws Exception {
				final MiniTaLib.User user = c.setProfileDescription("", value);
				applyOwnUser(user);
				ui(new Runnable() {
					@Override public void run() {
						status.setText(getString(R.string.status_description_saved));
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

		SessionStore.save(MainActivity.this, url, ta.token(), myID, myLogin);
		SessionStore.lastUpdate(MainActivity.this, 0);
		SessionStore.backgroundLastUpdate(MainActivity.this, 0);
		SessionStore.notificationBootstrapComplete(MainActivity.this, false);

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
		myDescription = u.description;
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
			ta = new MiniTaLib(this, url, ta.token(), myID, myLogin);
			SessionStore.save(this, url, ta.token(), myID, myLogin);
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
		final String server = SessionStore.server(this, DEFAULT_SERVER);
		final String account = OutboxDispatcher.accountKey(this);
		int pending = OutboxStore.count(this, server, account);
		if (pending > 0) {
			showConfirmDialog(
					getString(R.string.logout_pending_title),
					getString(R.string.logout_pending_message, pending),
					getString(R.string.settings_logout),
					new Runnable() {
						@Override public void run() {
							OutboxStore.clear(MainActivity.this, server, account);
							clearSessionAndShowLogin(R.string.status_logged_out);
						}
					});
			return;
		}
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
			chatRows.add(MessageRow.chat(
					chatPeerTitle(chat.peer),
					last,
					chat.peer != null && chat.peer.verified
			));
		}
		status.setText(getString(R.string.status_chats_count, chats.size(), source));
	}

	private void loadHistory() {
		loadHistory(null);
	}

	private void loadHistory(final Runnable afterServerLoad) {
		final MiniTaLib c = ta;
		if (c == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		String requestedPeer = peer == null ? currentPeer : peer.getText().toString().trim();
		if (requestedPeer.isEmpty()) return;
		if (!requestedPeer.equals(currentPeer)) {
			openChatImmediately(requestedPeer, null, false, false, false, afterServerLoad);
			return;
		}
		final String peerName = currentPeer;
		final int openGeneration = chatOpenGeneration;
		final int requestGeneration = ++historyRequestGeneration;
		loadingOlderMessages = false;
		historyLoaded = false;
		hasOlderMessages = false;
		final android.content.Context context = getApplicationContext();
		final String server = SessionStore.server(this, DEFAULT_SERVER);
		final String login = myLogin;
		historyCacheIo.execute(new Runnable() {
			@Override public void run() {
				final List<MiniTaLib.Message> cached = ChatCache.loadHistory(context, server, login, peerName);
				ui(new Runnable() {
					@Override public void run() {
						if (acceptsHistoryResult(openGeneration, requestGeneration)) {
							renderHistory(cached, peerName, true);
						}
					}
				});
				if (openGeneration != chatOpenGeneration || requestGeneration != historyRequestGeneration) return;
				MainActivity.this.run("history", new Task() {
					@Override public void run() throws Exception {
						final MiniTaLib.HistoryPage pageData;
						try {
							pageData = c.getHistoryPageBefore(peerName, 0, HISTORY_PAGE);
						} catch (final Exception error) {
							ui(new Runnable() {
								@Override public void run() {
									if (!acceptsHistoryResult(openGeneration, requestGeneration)) return;
									if (MiniTaLib.isInvalidTokenError(error)) handleInvalidToken();
									else status.setText(getString(R.string.status_operation_error, errorText(error)));
								}
							});
							return;
						}
						try {
							c.markRead(peerName);
						} catch (Exception ignored) {
						}
						final String resolvedPeer = resolvedPeerName(pageData.peer, peerName);
						cacheSaveHistory(resolvedPeer, pageData.messages);
						if (!resolvedPeer.equals(peerName)) cacheSaveHistory(peerName, pageData.messages);
						ui(new Runnable() {
							@Override public void run() {
								if (!acceptsHistoryResult(openGeneration, requestGeneration)) return;
								currentPeer = resolvedPeer;
								if (peer != null) peer.setText(resolvedPeer);
								if (pageData.peer != null) currentPeerUser = pageData.peer;
								refreshCurrentPeerNameView();
								updateCallButton();
								renderHistory(pageData.messages, resolvedPeer, false);
								if (afterServerLoad != null) afterServerLoad.run();
							}
						});
					}
				});
			}
		});
	}

	private boolean acceptsHistoryResult(int openGeneration, int requestGeneration) {
		return page == Page.CHAT
				&& openGeneration == chatOpenGeneration
				&& requestGeneration == historyRequestGeneration
				&& messageRows != null;
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
		for (OutboxStore.Entry entry : OutboxStore.load(
				this, SessionStore.server(this, DEFAULT_SERVER), OutboxDispatcher.accountKey(this))) {
			if (!peerName.equals(entry.peer) || entry.commentPostId > 0) continue;
			MiniTaLib.Message pending = outboxMessage(entry);
			if (seenMessages.add(pending.id)) rows.add(toMessageRow(pending));
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
		final int openGeneration = chatOpenGeneration;
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

						if (openGeneration != chatOpenGeneration ||
							messageRows == null ||
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
		if (currentPeer.isEmpty() || (msg.isEmpty() && composerMedia.isEmpty()) || composerSending) return;
		if (editingMessage != null) {
			sendEditedMediaMessage(msg, new ArrayList<ComposerMedia>(composerMedia));
			return;
		}
		if (composerMedia.isEmpty()) {
			sendChatMessage(currentPeer, msg, true);
			return;
		}
		final MiniTaLib client = ta;
		if (client == null) return;
		final String peerName = currentPeer;
		final boolean room = currentPeerIsRoom();
		final long commentPostId = page == Page.CHANNEL_COMMENTS && currentCommentPost != null ? currentCommentPost.id : 0;
		final long replyId = replyToMessage == null ? 0 : replyToMessage.id;
		final ArrayList<ComposerMedia> snapshot = new ArrayList<ComposerMedia>(composerMedia);
		composerSending = true;
		run("quote_media_message", new Task() {
			@Override public void run() throws Exception {
				try {
					ArrayList<MiniTaLib.MessageMedia> media = miniMedia(snapshot, false);
					final MiniTaLib.MediaQuote quote = client.quoteMedia(media);
					if (quote.dsrRequired <= 0) {
						queueMediaMessage(client, peerName, room, msg, snapshot, 0, commentPostId, replyId);
					} else {
						ui(new Runnable() { @Override public void run() {
							composerSending = false;
							String label = snapshot.size() == 1 ? snapshot.get(0).name : snapshot.size() + " files";
							showSwipeConfirmDialog(
									getString(R.string.media_payment_title),
									getString(R.string.media_payment_detail, label, formatBytes(quote.sizeBytes), quote.dsrRequired),
									getString(R.string.media_payment_slide_hint, quote.dsrRequired),
									new Runnable() { @Override public void run() {
										if (composerSending) return;
										composerSending = true;
										MainActivity.this.run("queue_media_message", new Task() { @Override public void run() throws Exception {
											try {
												queueMediaMessage(client, peerName, room, msg, snapshot, quote.dsrRequired, commentPostId, replyId);
											} catch (Exception error) {
												ui(new Runnable() { @Override public void run() { composerSending = false; } });
												throw error;
											}
										} });
									} },
									new Runnable() { @Override public void run() { composerSending = false; } }
							);
						} });
					}
				} catch (Exception error) {
					ui(new Runnable() { @Override public void run() { composerSending = false; } });
					throw error;
				}
			}
		});
	}

	private ArrayList<MiniTaLib.MessageMedia> miniMedia(final List<ComposerMedia> items, boolean withSources) {
		ArrayList<MiniTaLib.MessageMedia> out = new ArrayList<MiniTaLib.MessageMedia>();
		for (int index = 0; index < items.size(); index++) {
			final ComposerMedia item = items.get(index);
			MiniTaLib.UploadSource source = null;
			if (withSources) source = new MiniTaLib.UploadSource() {
				@Override public InputStream open() throws Exception {
					if (item.localPath != null && item.localPath.length() > 0) return new FileInputStream(new File(item.localPath));
					if (item.uri != null) return getContentResolver().openInputStream(item.uri);
					throw new IOException(getString(R.string.status_file_not_available));
				}
			};
			out.add(new MiniTaLib.MessageMedia(String.format(Locale.US, "attachment-%06d", index),
					item.fileId == null ? "" : item.fileId,
					item.name, item.mime, item.size, source));
		}
		return out;
	}

	private void sendEditedMediaMessage(final String messageText, final ArrayList<ComposerMedia> items) {
		final MiniTaLib client = ta;
		final MiniTaLib.Message original = editingMessage;
		if (client == null || original == null || (messageText.length() == 0 && items.isEmpty())) return;
		composerSending = true;
		run("quote_media_edit", new Task() {
			@Override public void run() throws Exception {
				try {
					final MiniTaLib.MediaQuote quote = client.quoteMedia(miniMedia(items, false));
					if (quote.dsrRequired <= 0) {
						commitEditedMediaMessage(client, original, messageText, items, 0);
					} else {
						ui(new Runnable() { @Override public void run() {
							composerSending = false;
							showSwipeConfirmDialog(
									getString(R.string.media_payment_title),
									getString(R.string.media_payment_detail, items.size() + " files", formatBytes(quote.sizeBytes), quote.dsrRequired),
									getString(R.string.media_payment_slide_hint, quote.dsrRequired),
									new Runnable() { @Override public void run() {
										if (composerSending) return;
										composerSending = true;
										MainActivity.this.run("commit_media_edit", new Task() { @Override public void run() throws Exception {
											try {
												commitEditedMediaMessage(client, original, messageText, items, quote.dsrRequired);
											} catch (Exception error) {
												ui(new Runnable() { @Override public void run() { composerSending = false; } });
												throw error;
											}
										} });
									} },
									new Runnable() { @Override public void run() { composerSending = false; } }
							);
						} });
					}
				} catch (Exception error) {
					ui(new Runnable() { @Override public void run() { composerSending = false; } });
					throw error;
				}
			}
		});
	}

	private void commitEditedMediaMessage(final MiniTaLib client, final MiniTaLib.Message original,
	                                     String messageText, List<ComposerMedia> items,
	                                     long maxDsr) throws Exception {
		String clientMessageId = UUID.randomUUID().toString();
		JSONObject prepared = client.prepareMessage(currentPeer, messageText, clientMessageId, currentPeerIsRoom(), original.replyToMessageId);
		prepared.put("message_id", original.id);
		if (original.commentPostId > 0) prepared.put("comment_post_id", original.commentPostId);
		MiniTaLib.TransferControl transfer = new MiniTaLib.TransferControl(new MiniTaLib.ProgressListener() {
			@Override public void onProgress(final long completed, final long total) {
				ui(new Runnable() { @Override public void run() {
					status.setText(total <= 0 ? getString(R.string.status_preparing_file) : (completed * 100L / total) + "%");
				} });
			}
		});
		final MiniTaLib.Message updated = client.sendMessageWithMedia(prepared, miniMedia(items, true), transfer, maxDsr);
		ui(new Runnable() { @Override public void run() {
			composerSending = false;
			editingMessage = null;
			composerMedia.clear();
			renderComposerMedia();
			if (text != null) text.setText("");
			status.setText("");
			applyMessageUpdate(updated);
		} });
	}

	private void queueMediaMessage(final MiniTaLib client, final String peerName, final boolean room,
	                              final String messageText, final List<ComposerMedia> items,
	                              final long maxDsr, final long commentPostId, final long replyId) throws Exception {
		final String clientMessageId = UUID.randomUUID().toString();
		JSONObject prepared = client.prepareMessage(peerName, messageText, clientMessageId, room, replyId);
		if (commentPostId > 0) prepared.put("comment_post_id", commentPostId);
		ArrayList<OutboxStore.Attachment> attachments = new ArrayList<OutboxStore.Attachment>();
		for (int index = 0; index < items.size(); index++) {
			ComposerMedia item = items.get(index);
			OutboxStore.Attachment attachment = new OutboxStore.Attachment();
			attachment.clientId = String.format(Locale.US, "attachment-%06d", index);
			attachment.fileId = item.fileId == null ? "" : item.fileId;
			attachment.name = item.name;
			attachment.mime = item.mime;
			attachment.localPath = item.localPath == null ? "" : item.localPath;
			attachment.sourceUri = item.uri == null ? "" : item.uri.toString();
			attachment.size = item.size;
			attachments.add(attachment);
		}
		final OutboxStore.Entry entry = OutboxStore.enqueueMedia(
				this, SessionStore.server(this, DEFAULT_SERVER), OutboxDispatcher.accountKey(this),
				peerName, room, messageText, attachments, clientMessageId, maxDsr,
				commentPostId, replyId, prepared.toString());
		ui(new Runnable() { @Override public void run() {
			composerSending = false;
			composerMedia.clear();
			renderComposerMedia();
			if (text != null) text.setText("");
			if (replyId > 0) clearReply();
			addMessageRow(outboxMessage(entry), false);
			if (messageList != null) messageList.setSelection(messageRows.getCount() - 1);
			dispatchOutbox(client);
		} });
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
		final long replyToMessageId = clearInput && replyToMessage != null ? replyToMessage.id : 0;
		setActionButtonLoading(actionButton, true, primaryStyle);
		try {
			OutboxStore.Entry entry = OutboxStore.enqueueText(
					this, SessionStore.server(this, DEFAULT_SERVER), OutboxDispatcher.accountKey(this),
					peerName, currentPeerIsRoom(), msg, replyToMessageId);
			addMessageRow(outboxMessage(entry), false);
			if (clearInput && text != null) text.setText("");
			if (replyToMessageId > 0) clearReply();
			if (messageList != null) messageList.setSelection(messageRows.getCount() - 1);
			dispatchOutbox(c);
		} catch (Exception e) {
			status.setText(errorText(e));
		} finally {
			setActionButtonLoading(actionButton, false, primaryStyle);
		}
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
		if (button.swipeConfirm) {
			showCallbackSwipeConfirmation(message, button, clickedButton);
			return;
		}
		sendMessageCallback(message, button, clickedButton);
	}

	private void showCallbackSwipeConfirmation(final MiniTaLib.Message message, final MiniTaLib.Button button, final Button clickedButton) {
		final Dialog dialog = new Dialog(this);
		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(pad, pad, pad, pad);
		box.setBackgroundDrawable(shape(surface, 0, buttonRadius()));
		box.addView(title(getString(R.string.callback_confirm_title)), new LinearLayout.LayoutParams(-1, -2));
		TextView details = label(getString(R.string.callback_confirm_body, button.text));
		details.setTextColor(muted);
		LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(-1, -2);
		detailsLp.setMargins(0, gap / 2, 0, gap);
		box.addView(details, detailsLp);
		final PaymentSliderView slider = paymentSlider(getString(R.string.callback_swipe_confirm));
		slider.setContentDescription(getString(R.string.callback_swipe_confirm));
		slider.setOnConfirmAction(new Runnable() {
			@Override public void run() {
				dialog.dismiss();
				sendMessageCallback(message, button, clickedButton);
			}
		});
		box.addView(slider, new LinearLayout.LayoutParams(-1, dp(56)));
		Button cancel = button(getString(R.string.action_cancel), new View.OnClickListener() {
			@Override public void onClick(View v) { dialog.dismiss(); }
		});
		LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(-1, -2);
		cancelLp.setMargins(0, gap, 0, 0);
		box.addView(cancel, cancelLp);
		setScrollableDialogContent(dialog, box);
		showStyledDialog(dialog);
	}

	private void sendMessageCallback(final MiniTaLib.Message message, final MiniTaLib.Button button, final Button clickedButton) {
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
		if (currentPeerIsSelfChat()) {
			status.setText(getString(R.string.status_self_calls_not_available));
			return;
		}
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
		if (currentPeerIsSelfChat()) {
			status.setText(getString(R.string.status_self_calls_not_available));
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

	private void pickImage() {
		Intent i = new Intent(Build.VERSION.SDK_INT >= 19 ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_GET_CONTENT);
		i.setType("image/*");
		i.addCategory(Intent.CATEGORY_OPENABLE);
		if (Build.VERSION.SDK_INT >= 18) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
		if (Build.VERSION.SDK_INT >= 19) i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
		startActivityForResult(Intent.createChooser(i, getString(R.string.chooser_select_picture)), REQ_PICK_IMAGE);
	}

	private void pickFile() {
		Intent i = new Intent(Build.VERSION.SDK_INT >= 19 ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_GET_CONTENT);
		i.setType("*/*");
		i.addCategory(Intent.CATEGORY_OPENABLE);
		if (Build.VERSION.SDK_INT >= 18) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
		if (Build.VERSION.SDK_INT >= 19) i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
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

		final PaymentSliderView slider = paymentSlider(getString(R.string.payment_slide_hint));
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

		setScrollableDialogContent(dialog, box);
		showStyledDialog(dialog);
	}

	private void setScrollableDialogContent(Dialog dialog, View contentView) {
		BoundedScrollView scroll = new BoundedScrollView(
				this,
				getResources().getDisplayMetrics().heightPixels * 4 / 5
		);
		scroll.setFillViewport(false);
		scroll.setBackgroundColor(Color.TRANSPARENT);
		scroll.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
		scroll.setVerticalScrollBarEnabled(true);
		scroll.addView(contentView, new ScrollView.LayoutParams(-1, -2));
		dialog.setContentView(scroll);
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
		if (requestCode == REQ_QR_SCAN) {
			openOAuthDeviceRequest(data.getStringExtra(QrScannerActivity.EXTRA_RESULT));
			return;
		}
		ArrayList<Uri> selected = new ArrayList<Uri>();
		if (Build.VERSION.SDK_INT >= 16 && data.getClipData() != null) {
			android.content.ClipData clips = data.getClipData();
			for (int i = 0; i < clips.getItemCount() && selected.size() < 10 - composerMedia.size(); i++) {
				Uri uri = clips.getItemAt(i).getUri();
				if (uri != null) selected.add(uri);
			}
		} else if (data.getData() != null) {
			selected.add(data.getData());
		}
		for (Uri uri : selected) {
			if (Build.VERSION.SDK_INT >= 19) {
				try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
				catch (Exception ignored) {}
			}
			addComposerMedia(uri, requestCode == REQ_PICK_IMAGE);
		}
	}

	private void addComposerMedia(final Uri pickedUri, final boolean imageOnly) {
		if (composerMedia.size() >= 10) return;
		status.setText(imageOnly ? getString(R.string.status_preparing_image) : getString(R.string.status_preparing_file));
		run("prepare_media", new Task() {
			@Override public void run() throws Exception {
				String type = getContentResolver().getType(pickedUri);
				if (type == null || type.length() == 0) type = "application/octet-stream";
				if (imageOnly && !type.toLowerCase(Locale.US).startsWith("image/")) throw new IOException(getString(R.string.status_not_an_image));
				String displayName = queryDisplayName(pickedUri);
				if (displayName == null || displayName.trim().length() == 0) displayName = imageOnly ? getString(R.string.image_fallback_name) : getString(R.string.file_fallback_name);
				long detectedSize = queryFileSize(pickedUri);
				String localPath = "";
				if (detectedSize < 0) {
					File staged = stagePickedFile(pickedUri);
					detectedSize = staged.length();
					localPath = staged.getAbsolutePath();
				}
				if (detectedSize <= 0) throw new IOException(getString(R.string.status_empty_file));
				if (detectedSize > MAX_UPLOAD_BYTES) throw new IOException(getString(R.string.status_file_too_large, formatBytes(detectedSize)));
				final ComposerMedia item = new ComposerMedia();
				item.uri = localPath.length() == 0 ? pickedUri : null;
				item.localPath = localPath;
				item.name = displayName;
				item.mime = type;
				item.size = detectedSize;
				ui(new Runnable() { @Override public void run() {
					if (composerMedia.size() < 10) composerMedia.add(item);
					else if (item.localPath.length() > 0) new File(item.localPath).delete();
					renderComposerMedia();
					status.setText("");
				} });
			}
		});
	}

	private long queryFileSize(Uri uri) {
		android.database.Cursor cursor = null;
		try {
			cursor = getContentResolver().query(uri, new String[]{"_size"}, null, null, null);
			if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0);
		} catch (Exception ignored) {
		} finally { if (cursor != null) cursor.close(); }
		try {
			android.content.res.AssetFileDescriptor descriptor = getContentResolver().openAssetFileDescriptor(uri, "r");
			if (descriptor != null) {
				try { if (descriptor.getLength() >= 0) return descriptor.getLength(); }
				finally { descriptor.close(); }
			}
		} catch (Exception ignored) {}
		return -1;
	}

	private File stagePickedFile(Uri uri) throws IOException {
		File target = OutboxStore.payloadFile(this, "picked-" + UUID.randomUUID().toString());
		InputStream input = getContentResolver().openInputStream(uri);
		if (input == null) throw new IOException(getString(R.string.status_file_not_available));
		FileOutputStream output = new FileOutputStream(target);
		long completed = 0;
		try {
			byte[] buffer = new byte[64 * 1024];
			int count;
			while ((count = input.read(buffer)) >= 0) {
				completed += count;
				if (completed > MAX_UPLOAD_BYTES) throw new IOException(getString(R.string.status_file_too_large, formatBytes(completed)));
				output.write(buffer, 0, count);
			}
		} catch (IOException error) {
			target.delete();
			throw error;
		} finally {
			try { output.close(); } finally { input.close(); }
		}
		return target;
	}

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
					c.downloadFile(file.id, local, maxBytes, null);
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
		if (page != Page.CALL) {
			if (page != Page.CHAT || !peerName.equals(currentPeer)) {
				openChatImmediately(peerName, null, false, false, false, null);
			} else {
				currentPeer = peerName;
				if (peer != null) peer.setText(peerName);
			}
		} else {
			currentPeer = peerName;
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
						dispatchOutbox(client);
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
			if (isOAuthRequestMessage(u.message)) {
				final String code = oauthRequestCode(u.message);
				ui(new Runnable() {
					@Override public void run() {
						openOAuthDeviceRequest(code);
					}
				});
			}
			append(u.message);
			return;
		}
		if ("channel_comment".equals(u.type)) {
			appendChannelComment(u.message);
			return;
		}
		if ("message_read".equals(u.type) || "message_edit".equals(u.type) || "message_reaction".equals(u.type)) {
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

	private boolean isOAuthRequestMessage(MiniTaLib.Message message) {
		JSONObject data = systemMessageData(message);
		return data != null && "oauth_request".equals(data.optString("kind"));
	}

	private String oauthRequestCode(MiniTaLib.Message message) {
		JSONObject data = systemMessageData(message);
		return data == null ? "" : OAuthCodeParser.parse(data.optString("user_code"));
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

	static boolean isOwnAddressFor(String ownID, String ownLogin, String address) {
		if (address == null) return false;
		String value = address.trim();
		if (value.startsWith("@")) value = value.substring(1);
		if (value.length() == 0) return false;
		if (ownID != null && ownID.length() > 0 && ownID.equals(value)) return true;
		return ownLogin != null && ownLogin.length() > 0 && ownLogin.equals(value);
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
		callButton.setVisibility(currentPeerUser == null || currentPeerIsBot() || currentPeerIsChannel() || currentPeerIsSelfChat()
				? View.GONE : View.VISIBLE);
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
					if (!room.commentsEnabled) {
						ChatCache.deleteCommentThreads(MainActivity.this, SessionStore.server(MainActivity.this, DEFAULT_SERVER), myLogin, currentPeer);
						OutboxStore.removeChannelComments(MainActivity.this, SessionStore.server(MainActivity.this, DEFAULT_SERVER), OutboxDispatcher.accountKey(MainActivity.this), currentPeer);
					}
					if (page == Page.CHANNEL_COMMENTS && !room.commentsEnabled) {
						status.setText(getString(R.string.channel_comments_deleted));
						showChat();
						loadHistory();
					} else if (page == Page.CHANNEL_SETTINGS) {
						showChannelSettings();
					} else if (page == Page.CHAT) {
						refreshChatInput();
						loadHistory();
					}
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

	private boolean currentPeerIsSelfChat() {
		return !currentPeerIsRoom()
				&& (isOwnUser(currentPeerUser) || isOwnAddressFor(myID, myLogin, currentPeer));
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
				openChatImmediately(peerName, null, false, false, false, null);
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

	private MiniTaLib.Message outboxMessage(OutboxStore.Entry entry) {
		MiniTaLib.User me = new MiniTaLib.User(myID, myEmail, myLogin, myNick, myVerified, myBot, 0);
		return entry.localMessage(me, currentPeer != null && currentPeer.equals(entry.peer) ? currentPeerUser : null);
	}

	private void dispatchOutbox(MiniTaLib client) {
		OutboxDispatcher.dispatch(this, client, new OutboxDispatcher.Listener() {
			@Override
			public void onChanged(final OutboxStore.Entry entry, final MiniTaLib.Message sent) {
				ui(new Runnable() {
					@Override
					public void run() {
						if (sent != null) {
							if (entry.commentPostId > 0) appendChannelComment(sent);
							else append(sent);
						} else if (page == Page.CHAT && entry.peer.equals(currentPeer) && messageRows != null) {
							messageRows.updateMessage(outboxMessage(entry));
						} else if (page == Page.CHANNEL_COMMENTS && entry.peer.equals(currentPeer)
								&& currentCommentPost != null && entry.commentPostId == currentCommentPost.id && messageRows != null) {
							messageRows.updateMessage(outboxMessage(entry));
						}
					}
				});
			}
		});
	}

	private void append(final MiniTaLib.Message m) {
		if (m == null) return;
		if (m.clientMessageId != null && m.clientMessageId.length() > 0) {
			OutboxStore.complete(this, SessionStore.server(this, DEFAULT_SERVER), OutboxDispatcher.accountKey(this), m.clientMessageId);
		}
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
						if (!messageRows.updateMessage(m)) addMessageRow(m, false);
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

	private void appendChannelComment(final MiniTaLib.Message message) {
		if (message == null || message.commentPostId <= 0) return;
		if (message.clientMessageId != null && message.clientMessageId.length() > 0) {
			OutboxStore.complete(this, SessionStore.server(this, DEFAULT_SERVER), OutboxDispatcher.accountKey(this), message.clientMessageId);
		}
		final String channel = messagePeer(message);
		cacheAppendMessage(OutboxStore.cachePeer(channel, message.commentPostId), message);
		ui(new Runnable() {
			@Override public void run() {
				if (page == Page.CHANNEL_COMMENTS && channel.equals(currentPeer)
						&& currentCommentPost != null && currentCommentPost.id == message.commentPostId && messageRows != null) {
					if (!messageRows.updateMessage(message)) addMessageRow(message, false);
					if (messageList != null && messageRows.getCount() > 0) messageList.setSelection(messageRows.getCount() - 1);
				} else if (page == Page.CHAT && channel.equals(currentPeer)) {
					scheduleChannelHistoryReload();
				}
			}
		});
	}

	private void applyMessageUpdate(final MiniTaLib.Message m) {
		if (m == null) return;
		final String cachedPeer = messagePeer(m);
		final String historyPeer = m.commentPostId > 0 ? OutboxStore.cachePeer(cachedPeer, m.commentPostId) : cachedPeer;
		cacheAppendMessage(historyPeer, m);
		ui(new Runnable() {
			@Override
			public void run() {
				if (m.commentPostId > 0 && page == Page.CHANNEL_COMMENTS && cachedPeer.equals(currentPeer)
						&& currentCommentPost != null && currentCommentPost.id == m.commentPostId && messageRows != null) {
					messageRows.updateMessage(m);
				} else if (m.commentPostId == 0 && page == Page.CHAT && cachedPeer.equals(currentPeer) && messageRows != null) {
					messageRows.updateMessage(m);
				}
				if (page == Page.CHATS) loadChats();
			}
		});
	}

	private void applyMessageDelete(final MiniTaLib.Message m) {
		if (m == null) return;
		final String cachedPeer = messagePeer(m);
		cacheDeleteMessage(m.commentPostId > 0 ? OutboxStore.cachePeer(cachedPeer, m.commentPostId) : cachedPeer, m.id);
		ui(new Runnable() {
			@Override
			public void run() {
				if (m.commentPostId == 0 && page == Page.CHANNEL_COMMENTS
						&& currentCommentPost != null && currentCommentPost.id == m.id) {
					status.setText(getString(R.string.channel_post_deleted));
					showChat();
					loadHistory();
					return;
				}
				if (m.commentPostId > 0) {
					if (page == Page.CHANNEL_COMMENTS && cachedPeer.equals(currentPeer)
							&& currentCommentPost != null && currentCommentPost.id == m.commentPostId && messageRows != null) {
						messageRows.removeMessage(m.id);
					} else if (page == Page.CHAT && cachedPeer.equals(currentPeer)) {
						scheduleChannelHistoryReload();
					}
				} else if (messageRows != null) messageRows.removeMessage(m.id);
				if (seenMessages != null) seenMessages.remove(Long.valueOf(m.id));
				if (page == Page.CHATS) loadChats();
			}
		});
	}

	private void scheduleChannelHistoryReload() {
		main.removeCallbacks(channelHistoryReload);
		main.postDelayed(channelHistoryReload, 150L);
	}

	private String messagePeer(MiniTaLib.Message m) {
		return MessagePeerResolver.peer(m, myLogin, myID, currentPeer, currentPeerUser);
	}

	private MiniTaLib.User messagePeerUser(MiniTaLib.Message m) {
		return MessagePeerResolver.peerUser(m, myLogin, myID, currentPeerUser);
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

	private MiniTaLib.Message withCommentsCount(MiniTaLib.Message message, int commentsCount) {
		return new MiniTaLib.Message(
				message.id, message.chatId, message.from, message.to, message.text, message.date,
				message.readAt, message.media, message.buttons, message.encrypted, message.system,
				message.data, message.clientMessageId, message.editedAt, message.deliveryState,
				message.localFilePath, message.reactions, message.paidReaction, message.reactionVersion,
				message.commentPostId, commentsCount, message.replyToMessageId
		);
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
		if (!"sent".equals(message.deliveryState) && !"sent-own".equals(message.deliveryState)) {
			final boolean failed = OutboxStore.FAILED.equals(message.deliveryState);
			final ArrayList<String> actions = new ArrayList<String>();
			actions.add(getString(R.string.action_copy));
			if (failed) actions.add(getString(R.string.action_retry));
			actions.add(getString(R.string.action_cancel));
			showActionDialog(actions.toArray(new String[actions.size()]), new ChoiceHandler() {
				@Override public void onChoice(int which) {
					String action = actions.get(which);
					if (action.equals(getString(R.string.action_copy))) copyMessage(message);
					else if (action.equals(getString(R.string.action_retry))) retryOutboxMessage(message);
					else removeOutboxMessage(message);
				}
			});
			return;
		}
		final boolean editable = canEditMessage(message);
		final ArrayList<String> actionList = new ArrayList<String>();
		if (canReplyToMessage(message)) actionList.add(getString(R.string.action_reply));
		if (canForwardMessage(message)) actionList.add(getString(R.string.action_forward));
		actionList.add(getString(R.string.action_copy));
		if (editable) actionList.add(getString(R.string.action_edit));
		if (canDeleteMessage(message)) actionList.add(getString(R.string.action_delete));
		if (message.commentPostId == 0) actionList.add(getString(R.string.action_save_favorite));
		showMessageActionDialog(message, actionList.toArray(new String[actionList.size()]), new ChoiceHandler() {
			@Override
			public void onChoice(int which) {
				String action = actionList.get(which);
				if (action.equals(getString(R.string.action_reply))) startReply(message);
				else if (action.equals(getString(R.string.action_forward))) forwardMessage(message);
				else if (action.equals(getString(R.string.action_copy))) copyMessage(message);
				else if (action.equals(getString(R.string.action_edit))) editMessage(message);
				else if (action.equals(getString(R.string.action_delete))) deleteMessage(message);
				else if (action.equals(getString(R.string.action_save_favorite))) saveToFavorites(message);
			}
		});
	}

	private boolean canReplyToMessage(MiniTaLib.Message message) {
		if (message == null || message.id <= 0 || currentPeerBanned) return false;
		return page == Page.CHANNEL_COMMENTS || (page == Page.CHAT && currentPeerCanWrite());
	}

	private void showMessageActionDialog(final MiniTaLib.Message message, final String[] actions, final ChoiceHandler handler) {
		final Dialog dialog = new Dialog(this);
		LinearLayout box = dialogBox();
		TextView reactionTitle = label(getString(R.string.reaction_title));
		reactionTitle.setTextColor(muted);
		box.addView(spaced(reactionTitle));
		android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
		scroll.setHorizontalScrollBarEnabled(false);
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		if (canPayReaction(message)) {
			ImageButton star = compactDastarsButton(new View.OnClickListener() {
				@Override public void onClick(View v) {
					dialog.dismiss();
					handlePaidReaction(message, v);
				}
			});
			row.addView(star, compactReactionLayout());
		}
		for (final String emoji : QUICK_REACTIONS) {
			Button reaction = compactReactionButton(emoji, new View.OnClickListener() {
				@Override public void onClick(View v) {
					dialog.dismiss();
					sendFreeReaction(message, ownReaction(message, emoji) ? "" : emoji);
				}
			});
			row.addView(reaction, compactReactionLayout());
		}
		Button more = compactReactionButton("…", new View.OnClickListener() {
			@Override public void onClick(View v) {
				dialog.dismiss();
				showAllReactions(message);
			}
		});
		more.setContentDescription(getString(R.string.reaction_more));
		row.addView(more, compactReactionLayout());
		scroll.addView(row, new android.widget.HorizontalScrollView.LayoutParams(-2, -2));
		LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, -2);
		scrollLp.setMargins(0, 0, 0, gap);
		box.addView(scroll, scrollLp);
		for (int i = 0; i < actions.length; i++) {
			final int which = i;
			Button action = button(actions[i], new View.OnClickListener() {
				@Override public void onClick(View v) {
					dialog.dismiss();
					if (handler != null) handler.onChoice(which);
				}
			});
			box.addView(spaced(action));
		}
		setScrollableDialogContent(dialog, box);
		showStyledDialog(dialog);
	}

	private LinearLayout.LayoutParams compactReactionLayout() {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
		lp.setMargins(0, 0, gap / 2, 0);
		return lp;
	}

	private Button compactReactionButton(String emoji, View.OnClickListener listener) {
		Button button = new Button(this);
		button.setText(emoji);
		button.setTextSize(22);
		button.setTextColor(textColor);
		button.setPadding(0, 0, 0, 0);
		button.setMinWidth(0);
		button.setMinimumWidth(0);
		button.setMinHeight(0);
		button.setMinimumHeight(0);
		button.setBackgroundDrawable(pressable(surfaceHi, blend(surfaceHi, primary, 0.18f), 0, elementRadius()));
		button.setOnClickListener(listener);
		return button;
	}

	private ImageButton compactDastarsButton(View.OnClickListener listener) {
		ImageButton button = new ImageButton(this);
		Drawable icon = getResources().getDrawable(R.drawable.ic_dastars).mutate();
		icon.setColorFilter(textColor, android.graphics.PorterDuff.Mode.SRC_IN);
		button.setImageDrawable(icon);
		button.setScaleType(ImageView.ScaleType.CENTER);
		button.setPadding(0, 0, 0, 0);
		button.setMinimumWidth(0);
		button.setMinimumHeight(0);
		button.setBackgroundDrawable(pressable(surfaceHi, blend(surfaceHi, primary, 0.18f), 0, elementRadius()));
		button.setOnClickListener(listener);
		button.setContentDescription(getString(R.string.paid_reaction_title));
		return button;
	}

	private void showAllReactions(final MiniTaLib.Message message) {
		final Dialog dialog = new Dialog(this);
		LinearLayout box = dialogBox();
		box.addView(title(getString(R.string.reaction_title)), new LinearLayout.LayoutParams(-1, -2));
		if (canPayReaction(message)) {
			ImageButton star = compactDastarsButton(new View.OnClickListener() {
				@Override public void onClick(View v) {
					dialog.dismiss();
					handlePaidReaction(message, v);
				}
			});
			LinearLayout first = new LinearLayout(this);
			first.setOrientation(LinearLayout.HORIZONTAL);
			first.setGravity(Gravity.CENTER_HORIZONTAL);
			first.addView(star, compactReactionLayout());
			box.addView(spaced(first));
		}
		for (int offset = 0; offset < ALL_REACTIONS.length; offset += 6) {
			LinearLayout row = new LinearLayout(this);
			row.setOrientation(LinearLayout.HORIZONTAL);
			for (int index = offset; index < Math.min(offset + 6, ALL_REACTIONS.length); index++) {
				final String emoji = ALL_REACTIONS[index];
				Button reaction = compactReactionButton(emoji, new View.OnClickListener() {
					@Override public void onClick(View v) {
						dialog.dismiss();
						sendFreeReaction(message, ownReaction(message, emoji) ? "" : emoji);
					}
				});
				row.addView(reaction, new LinearLayout.LayoutParams(0, dp(48), 1));
			}
			box.addView(row, new LinearLayout.LayoutParams(-1, -2));
		}
		Button close = button(getString(R.string.action_close), new View.OnClickListener() {
			@Override public void onClick(View v) { dialog.dismiss(); }
		});
		box.addView(spaced(close));
		setScrollableDialogContent(dialog, box);
		showStyledDialog(dialog);
	}

	private boolean ownReaction(MiniTaLib.Message message, String emoji) {
		if (message == null || message.reactions == null) return false;
		for (MiniTaLib.Reaction reaction : message.reactions) {
			if (reaction.mine && emoji.equals(reaction.emoji)) return true;
		}
		return false;
	}

	private boolean canPayReaction(MiniTaLib.Message message) {
		return message != null
				&& message.id > 0
				&& !message.system
				&& ("sent".equals(message.deliveryState) || "sent-own".equals(message.deliveryState))
				&& !isOwnUser(message.from);
	}

	private void sendFreeReaction(final MiniTaLib.Message message, final String emoji) {
		final MiniTaLib client = ta;
		if (client == null || message == null || message.id <= 0) return;
		run("reaction", new Task() {
			@Override public void run() throws Exception {
				applyMessageUpdate(client.reactMessage(message.id, emoji));
			}
		});
	}

	private void handlePaidReaction(final MiniTaLib.Message message, View source) {
		if (!canPayReaction(message)) return;
		if (hasWalletBalance && walletBalance <= 0) {
			showDastarsTopUpDialog();
			return;
		}
		if ((message.paidReaction != null && message.paidReaction.mineAmount > 0)
				|| pendingPaidReactionDelta(message.id) > 0) {
			animateReactionView(source);
			sendPaidReaction(message, 1);
			return;
		}
		final EditText amount = input(getString(R.string.paid_reaction_amount_hint), false);
		amount.setInputType(InputType.TYPE_CLASS_NUMBER);
		showContentDialog(
				getString(R.string.paid_reaction_title),
				amount,
				getString(R.string.action_send),
				new Runnable() {
					@Override public void run() {
						String raw = amount.getText().toString().trim();
						if (raw.length() == 0) return;
						try {
							long value = Long.parseLong(raw);
							if (value > 0) sendPaidReaction(message, value);
						} catch (NumberFormatException e) {
							status.setText(getString(R.string.status_bad_dsr_invoice));
						}
					}
				},
				getString(R.string.action_cancel)
		);
	}

	private void sendPaidReaction(final MiniTaLib.Message message, final long amount) {
		final MiniTaLib client = ta;
		if (client == null || amount <= 0) return;
		if (hasWalletBalance && amount > walletBalance) {
			showDastarsTopUpDialog();
			return;
		}
		final boolean adjustKnownBalance = hasWalletBalance;
		if (adjustKnownBalance) walletBalance = Math.max(0, walletBalance - amount);
		adjustPendingPaidReaction(message.id, amount);
		final Long messageId = Long.valueOf(message.id);
		PaidReactionBatch batch = paidReactionBatches.get(messageId);
		if (batch == null) {
			final long id = message.id;
			batch = new PaidReactionBatch();
			batch.flush = new Runnable() {
				@Override public void run() {
					flushPaidReactionBatch(id);
				}
			};
			paidReactionBatches.put(messageId, batch);
		}
		batch.message = message;
		batch.amount = safeAdd(batch.amount, amount);
		batch.adjustKnownBalance = batch.adjustKnownBalance || adjustKnownBalance;
		main.removeCallbacks(batch.flush);
		main.postDelayed(batch.flush, PAID_REACTION_BATCH_DELAY_MS);
	}

	private void flushPaidReactionBatch(long messageId) {
		PaidReactionBatch batch = paidReactionBatches.remove(Long.valueOf(messageId));
		if (batch == null || batch.message == null || batch.amount <= 0) return;
		executePaidReaction(batch.message, batch.amount, batch.adjustKnownBalance);
	}

	private void executePaidReaction(
			final MiniTaLib.Message message,
			final long amount,
			final boolean adjustKnownBalance
	) {
		final MiniTaLib client = ta;
		if (client == null) {
			adjustPendingPaidReaction(message.id, -amount);
			if (adjustKnownBalance) walletBalance = safeAdd(walletBalance, amount);
			return;
		}
		final String key = "paid-reaction:" + UUID.randomUUID().toString();
		paidReactionIo.execute(new Runnable() {
			@Override public void run() {
				try {
					final MiniTaLib.Message updated = client.sendPaidReaction(message.id, amount, key);
					final String cachedPeer = messagePeer(updated);
					cacheAppendMessage(cachedPeer, updated);
					ui(new Runnable() {
						@Override public void run() {
							adjustPendingPaidReaction(message.id, -amount);
							if (page == Page.CHAT && cachedPeer.equals(currentPeer) && messageRows != null) {
								messageRows.updateMessage(updated);
							}
							if (page == Page.CHATS) loadChats();
						}
					});
				} catch (final Exception error) {
					ui(new Runnable() {
						@Override public void run() {
							adjustPendingPaidReaction(message.id, -amount);
							if (adjustKnownBalance) walletBalance = safeAdd(walletBalance, amount);
							if (MiniTaLib.isInvalidTokenError(error)) {
								handleInvalidToken();
								return;
							}
							if (isInsufficientDastarsError(error)) {
								showDastarsTopUpDialog();
								return;
							}
							status.setText(getString(R.string.status_operation_error, errorText(error)));
						}
					});
				}
			}
		});
	}

	private static final class PaidReactionBatch {
		MiniTaLib.Message message;
		long amount;
		boolean adjustKnownBalance;
		Runnable flush;
	}

	private long pendingPaidReactionDelta(long messageId) {
		Long value = pendingPaidReactionDeltas.get(Long.valueOf(messageId));
		return value == null ? 0 : value.longValue();
	}

	private void adjustPendingPaidReaction(long messageId, long delta) {
		long next = safeAdd(pendingPaidReactionDelta(messageId), delta);
		if (next <= 0) pendingPaidReactionDeltas.remove(Long.valueOf(messageId));
		else pendingPaidReactionDeltas.put(Long.valueOf(messageId), Long.valueOf(next));
		if (messageRows != null) messageRows.notifyDataSetChanged();
	}

	private long safeAdd(long left, long right) {
		if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
		if (right < 0 && left < Long.MIN_VALUE - right) return Long.MIN_VALUE;
		return left + right;
	}

	private boolean isInsufficientDastarsError(Exception error) {
		String value = errorText(error).toLowerCase(Locale.US);
		return value.contains("insufficient dsr") || value.contains("insufficient dastars");
	}

	private boolean isMediaPriceChangedError(Exception error) {
		String value = errorText(error).toLowerCase(Locale.US);
		return value.contains("media price changed") || value.contains("media purchase confirmation required");
	}

	private void showDastarsTopUpDialog() {
		showConfirmDialog(
				getString(R.string.paid_reaction_no_dastars_title),
				getString(R.string.paid_reaction_no_dastars_message),
				getString(R.string.wallet_buy_dastars),
				new Runnable() {
					@Override public void run() {
						openChatIfExists("dastarsbot", null, true);
					}
				}
		);
	}

	private void showMediaTopUpDialog() {
		showConfirmDialog(
				getString(R.string.paid_reaction_no_dastars_title),
				getString(R.string.media_payment_no_dastars_message),
				getString(R.string.wallet_buy_dastars),
				new Runnable() {
					@Override public void run() { openChatIfExists("dastarsbot", null, true); }
				}
		);
	}

	private void animateReactionView(View view) {
		if (view == null) return;
		ScaleAnimation animation = new ScaleAnimation(
				1f, 1.18f, 1f, 1.18f,
				Animation.RELATIVE_TO_SELF, 0.5f,
				Animation.RELATIVE_TO_SELF, 0.5f
		);
		animation.setDuration(160);
		animation.setRepeatCount(1);
		animation.setRepeatMode(Animation.REVERSE);
		view.startAnimation(animation);
	}

	private boolean canEditMessage(MiniTaLib.Message message) {
		if (message == null || message.system) return false;
		if (System.currentTimeMillis() / 1000L - message.date > 48L * 60L * 60L) return false;
		if (message.commentPostId > 0) return isOwnUser(message.from);
		return currentPeerIsChannel() ? currentPeerCanManageRoom() : isOwnUser(message.from);
	}

	private boolean canDeleteMessage(MiniTaLib.Message message) {
		if (message == null || message.id <= 0) return false;
		if (message.commentPostId > 0) return isOwnUser(message.from) || currentPeerCanManageRoom();
		if (currentPeerIsChannel()) return currentPeerCanManageRoom();
		return isOwnUser(message.from);
	}

	private void editMessage(final MiniTaLib.Message message) {
		if (message == null || text == null) return;
		editingMessage = message;
		text.setText(message.text == null ? "" : message.text);
		text.setSelection(text.length());
		composerMedia.clear();
		if (message.media != null) for (MiniTaLib.FileInfo file : message.media) {
			ComposerMedia item = new ComposerMedia();
			item.fileId = file.id;
			item.name = file.name;
			item.mime = file.mime;
			item.size = file.size;
			item.localPath = "";
			composerMedia.add(item);
		}
		renderComposerMedia();
		status.setText(getString(R.string.action_edit));
	}

	private void retryOutboxMessage(MiniTaLib.Message message) {
		OutboxStore.retry(this, SessionStore.server(this, DEFAULT_SERVER), OutboxDispatcher.accountKey(this), message.clientMessageId);
		for (OutboxStore.Entry entry : OutboxStore.load(this, SessionStore.server(this, DEFAULT_SERVER), OutboxDispatcher.accountKey(this))) {
			if (entry.id.equals(message.clientMessageId) && messageRows != null) messageRows.updateMessage(outboxMessage(entry));
		}
		dispatchOutbox(ta);
	}

	private void removeOutboxMessage(MiniTaLib.Message message) {
		final String server = SessionStore.server(this, DEFAULT_SERVER);
		final String account = OutboxDispatcher.accountKey(this);
		OutboxStore.Entry entry = OutboxStore.find(this, server, account, message.clientMessageId);
		OutboxStore.requestCancel(this, server, account, message.clientMessageId);
		OutboxDispatcher.cancel(message.clientMessageId);
		OutboxStore.complete(this, server, account, message.clientMessageId);
		final MiniTaLib client = ta;
		final String clientMessageId = message.clientMessageId;
		if (client != null && clientMessageId.length() > 0) {
			run("cancel_media", new Task() {
				@Override public void run() throws Exception {
					JSONObject result = client.cancelMessageOperation(clientMessageId);
					if (result.optBoolean("complete") && result.optJSONObject("message") != null) {
						final MiniTaLib.Message sent = client.message(result.getJSONObject("message")).asOutgoing();
						ui(new Runnable() { @Override public void run() { append(sent); } });
					}
				}
			});
		}
		if (messageRows != null) messageRows.removeMessage(message.id);
		seenMessages.remove(Long.valueOf(message.id));
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
		String value = message.text == null ? "" : message.text;
		if (message.media != null && !message.media.isEmpty()) {
			StringBuilder names = new StringBuilder();
			for (MiniTaLib.FileInfo file : message.media) {
				if (names.length() > 0) names.append('\n');
				names.append(file.name == null || file.name.length() == 0 ? "file" : file.name);
			}
			if (value.length() == 0) return names.toString();
			return value + "\n" + names;
		}
		return value;
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

	private boolean canForwardMessage(MiniTaLib.Message message) {
		return message != null
				&& message.id > 0
				&& !message.system
				&& message.text != null
				&& message.text.trim().length() > 0
				&& ("sent".equals(message.deliveryState) || "sent-own".equals(message.deliveryState));
	}

	private void forwardMessage(final MiniTaLib.Message message) {
		final MiniTaLib client = ta;
		if (client == null || !canForwardMessage(message)) return;
		String resolvedAuthor = displayUser(messageAuthor(message));
		if (resolvedAuthor.length() == 0) resolvedAuthor = getString(R.string.reply_to_message);
		final String originalAuthor = resolvedAuthor;
		run("forward_targets", new Task() {
			@Override public void run() throws Exception {
				final List<MiniTaLib.Chat> chats = client.getChats();
				cacheSaveChats(chats);
				ui(new Runnable() {
					@Override public void run() {
						showForwardTargetDialog(message, originalAuthor, chats);
					}
				});
			}
		});
	}

	private void showForwardTargetDialog(
			final MiniTaLib.Message message,
			final String originalAuthor,
			List<MiniTaLib.Chat> chats
	) {
		final Dialog dialog = new Dialog(this);
		LinearLayout box = dialogBox();
		box.addView(title(getString(R.string.forward_choose_chat)), new LinearLayout.LayoutParams(-1, -2));
		int targetCount = 0;
		if (chats != null) {
			for (final MiniTaLib.Chat chat : chats) {
				if (!canForwardToChat(chat)) continue;
				final String target = resolvedPeerName(chat.peer, chat.id);
				if (target.length() == 0) continue;
				Button targetButton = button(chatPeerTitle(chat.peer), new View.OnClickListener() {
					@Override public void onClick(View v) {
						dialog.dismiss();
						forwardMessageTo(message, originalAuthor, chat, target);
					}
				});
				box.addView(spaced(targetButton));
				targetCount++;
			}
		}
		if (targetCount == 0) {
			status.setText(getString(R.string.status_no_forward_targets));
			return;
		}
		Button cancel = button(getString(R.string.action_cancel), new View.OnClickListener() {
			@Override public void onClick(View v) { dialog.dismiss(); }
		});
		box.addView(cancel, new LinearLayout.LayoutParams(-1, -2));
		setScrollableDialogContent(dialog, box);
		showStyledDialog(dialog);
	}

	private boolean canForwardToChat(MiniTaLib.Chat chat) {
		if (chat == null || chat.peer == null || chat.banned || chat.bannedByMe || chat.bannedMe) return false;
		if (!"channel".equals(chat.peer.roomKind)) return true;
		return chat.peer.canManage
				|| (myID != null && myID.length() > 0 && myID.equals(chat.peer.ownerId));
	}

	private void forwardMessageTo(
			MiniTaLib.Message source,
			String originalAuthor,
			MiniTaLib.Chat targetChat,
			String target
	) {
		if (source == null || targetChat == null || targetChat.peer == null || ta == null) return;
		if (source.media != null && !source.media.isEmpty()) {
			final MiniTaLib client = ta;
			final long sourceMessageId = source.id;
			final String targetPeer = target;
			final String clientMessageId = UUID.randomUUID().toString();
			run("forward_media", new Task() {
				@Override public void run() throws Exception {
					final MiniTaLib.Message sent = client.forwardMedia(sourceMessageId, targetPeer, clientMessageId).asOutgoing();
					ui(new Runnable() {
						@Override public void run() {
							append(sent);
							status.setText(getString(R.string.status_forwarded_to, chatPeerTitle(targetChat.peer)));
						}
					});
				}
			});
			return;
		}
		String forwarded = ForwardMessageFormatter.compose(
				getString(R.string.forwarded_from, originalAuthor),
				source.text
		);
		if (!ForwardMessageFormatter.fitsServerLimit(forwarded)) {
			status.setText(getString(R.string.status_forward_too_long));
			return;
		}
		try {
			boolean room = targetChat.peer.roomKind != null && targetChat.peer.roomKind.length() > 0;
			OutboxStore.Entry entry = OutboxStore.enqueueText(
					this,
					SessionStore.server(this, DEFAULT_SERVER),
					OutboxDispatcher.accountKey(this),
					target,
					room,
					forwarded,
					0
			);
			if (page == Page.CHAT && target.equals(currentPeer) && messageRows != null) {
				addMessageRow(outboxMessage(entry), false);
				if (messageList != null) messageList.setSelection(messageRows.getCount() - 1);
			}
			dispatchOutbox(ta);
			status.setText(getString(R.string.status_forwarded_to, chatPeerTitle(targetChat.peer)));
		} catch (Exception e) {
			status.setText(errorText(e));
		}
	}

	private String chatLastText(MiniTaLib.Message m) {
		if (m.text != null && m.text.trim().length() > 0) return m.text;
		if (m.media != null && !m.media.isEmpty()) {
			MiniTaLib.FileInfo first = m.media.get(0);
			String kind = first.mime != null && first.mime.toLowerCase(Locale.US).startsWith("image/") ? getString(R.string.message_image_prefix) : getString(R.string.message_file_prefix);
			String name = first.name == null || first.name.length() == 0 ? getString(R.string.file_fallback_name) : first.name;
			return kind + name + (m.media.size() > 1 ? " +" + (m.media.size() - 1) : "");
		}
		return "";
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

	private String chatPeerTitle(MiniTaLib.User user) {
		if (user != null && (user.roomKind == null || user.roomKind.length() == 0) && isOwnUser(user)) {
			return getString(R.string.chat_favorites_title);
		}
		return displayUser(user);
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
		return new MarkdownRenderer(new MarkdownRenderer.Callbacks() {
			@Override
			public void copyCode(String code) {
				copyToClipboard("code", code);
			}

			@Override
			public void openUrl(String url) {
				MainActivity.this.openUrl(url);
			}

			@Override
			public void openMention(String login) {
				MainActivity.this.openMention(login);
			}

			@Override
			public int linkColor() {
				return primary;
			}
		}).render(value);
	}

	static String safeDisplayText(String value) {
		return DisplayText.safe(value);
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
		e.setPadding(buttonPadX, buttonPadY, buttonPadX, buttonPadY);
		e.setMinHeight(buttonMinHeight);
		e.setMinimumHeight(buttonMinHeight);
		e.setIncludeFontPadding(false);
		e.setGravity(Gravity.CENTER_VERTICAL);
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
		CheckBox b = new ChoiceCheckBox(this, choiceButtonTextInset());
		b.setText(safeDisplayText(s));
		styleChoiceButton(b, false);
		b.setChecked(checked);
		return b;
	}

	private void styleChoiceButton(CompoundButton button, boolean radio) {
		button.setTextColor(textColor);
		button.setButtonDrawable(choiceButtonDrawable(radio));
		button.setCompoundDrawablePadding(choiceButtonGap());
		button.setGravity(Gravity.CENTER_VERTICAL);
		button.setMinHeight(dp(48));
		button.setMinimumHeight(dp(48));
		button.setSingleLine(false);
		button.setPadding(pad, gap, pad, gap);
		button.setBackgroundDrawable(pressable(surface, surfaceHi, 0, elementRadius()));
	}

	private int choiceButtonTextInset() {
		return choiceButtonLeadingInset() + choiceButtonSize() + choiceButtonGap();
	}

	private int choiceButtonSize() {
		return dp(22);
	}

	private int choiceButtonLeadingInset() {
		return pad;
	}

	private int choiceButtonGap() {
		return dp(12);
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

	private void setDastarsButtonIcon(Button button, int color, int iconSize) {
		if (button == null) return;
		Drawable icon = getResources().getDrawable(R.drawable.ic_dastars).mutate();
		icon.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
		icon.setBounds(0, 0, iconSize, iconSize);
		button.setCompoundDrawables(icon, null, null, null);
		button.setCompoundDrawablePadding(gap / 2);
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

	private PaymentSliderView paymentSlider(String hint) {
		return paymentSlider(hint, false);
	}

	private PaymentSliderView paymentSlider(String hint, boolean confirmLeft) {
		return new PaymentSliderView(this, hint, confirmLeft, paymentSliderTheme());
	}

	private PaymentSliderView.Theme paymentSliderTheme() {
		return new PaymentSliderView.Theme() {
			@Override public int dp(int value) { return MainActivity.this.dp(value); }
			@Override public int elementRadius() { return MainActivity.this.elementRadius(); }
			@Override public int blend(int a, int b, float t) { return MainActivity.this.blend(a, b, t); }
			@Override public int surfaceHi() { return surfaceHi; }
			@Override public int primary() { return primary; }
			@Override public int muted() { return muted; }
			@Override public int onPrimary() { return onPrimary; }
		};
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

			boolean updateMessage(MiniTaLib.Message message) {
				if (message == null) return false;
				for (int i = 0; i < rows.size(); i++) {
					MessageRow row = rows.get(i);
					if (row.message != null && (row.message.id == message.id
							|| (message.clientMessageId.length() > 0
							&& message.clientMessageId.equals(row.message.clientMessageId)))) {
						if (message.reactionVersion < row.message.reactionVersion) return false;
						if (message.commentPostId == 0 && message.commentsCount == 0 && row.message.commentsCount > 0) {
							message = withCommentsCount(message, row.message.commentsCount);
						}
						rows.set(i, toMessageRow(message));
						notifyDataSetChanged();
						return true;
					}
				}
				return false;
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

		MiniTaLib.Message messageById(long messageId) {
			for (MessageRow row : rows) {
				if (row.message != null && row.message.id == messageId) return row.message;
			}
			return null;
		}

		int positionOfMessage(long messageId) {
			for (int i = 0; i < rows.size(); i++) {
				MessageRow row = rows.get(i);
				if (row.message != null && row.message.id == messageId) return i;
			}
			return -1;
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
					title.setMaxWidth(Math.max(
							dp(120),
							getResources().getDisplayMetrics().widthPixels - pad * 4
					));
					box.addView(title, new LinearLayout.LayoutParams(-2, -2));

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
				if (row.chatVerified) {
					Drawable badge = verifiedDrawable(dp(18));
					badge.setBounds(0, 0, dp(18), dp(18));
					title.setCompoundDrawables(null, null, badge, null);
					title.setCompoundDrawablePadding(gap / 2);
					title.setContentDescription(
							safeDisplayText(row.chatTitle) + ", " + getString(R.string.verified)
					);
				} else {
					title.setCompoundDrawables(null, null, null, null);
					title.setCompoundDrawablePadding(0);
					title.setContentDescription(safeDisplayText(row.chatTitle));
				}
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
				installMessageLongPress(box, row.message);
				box.addView(userNameRow(messageAuthor(row.message), 14), new LinearLayout.LayoutParams(-1, -2));
				addReplyReference(box, row.message);
				if (row.imageData != null) {
					LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(-1, -2);
					contentLp.setMargins(0, gap / 3, 0, 0);
					box.addView(imageContent(row.imageData), contentLp);
				}
				if (row.message != null && row.message.media != null) {
					for (int mediaIndex = 0; mediaIndex < row.message.media.size(); mediaIndex++) {
						MiniTaLib.FileInfo file = row.message.media.get(mediaIndex);
						String kind = isImageFile(file) ? getString(R.string.message_image_prefix) : getString(R.string.message_file_prefix);
						String name = file.name == null || file.name.length() == 0 ? getString(R.string.file_fallback_name) : file.name;
						MessageRow mediaRow = MessageRow.file(kind + name + " (" + formatBytes(file.size) + ")", file, row.message);
						LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
						labelLp.setMargins(0, gap / 3, 0, 0);
						box.addView(fileLabel(mediaRow.text), labelLp);
						if (mediaIndex == 0 && row.message.localFilePath.length() > 0) {
							if (isImageFile(file)) addLocalImagePreview(box, row.message.localFilePath);
						} else if (file.id != null && file.id.length() > 0) {
							if (isImageFile(file)) addImagePreview(box, mediaRow);
							addDownloadButton(box, mediaRow);
						}
					}
				}
				String messageText = row.message == null || row.message.text == null ? "" : row.message.text;
				if (messageText.length() > 0) {
					TextView body = messageTextLabel(messageText);
					installMessageLongPress(body, row.message);
					LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
					bodyLp.setMargins(0, gap / 3, 0, 0);
					box.addView(body, bodyLp);
				}
				addMessageMeta(box, row.message);
				addChannelCommentsLink(box, row.message);
				outer.addView(box, new LinearLayout.LayoutParams(-1, -2));
				addMessageButtons(outer, row.message);
				return listItemFrame(outer);
			}

			private void installMessageLongPress(View view, final MiniTaLib.Message message) {
				if (view == null || message == null) return;
				view.setLongClickable(true);
				view.setOnLongClickListener(new View.OnLongClickListener() {
					@Override public boolean onLongClick(View v) {
						showMessageMenu(message);
						return true;
					}
				});
			}

			private void addReplyReference(LinearLayout box, final MiniTaLib.Message message) {
				if (message == null || message.replyToMessageId <= 0) return;
				MiniTaLib.Message target = messageById(message.replyToMessageId);
				LinearLayout reference = new LinearLayout(MainActivity.this);
				reference.setOrientation(LinearLayout.HORIZONTAL);
				reference.setPadding(gap, gap / 2, gap, gap / 2);
				reference.setBackgroundDrawable(pressable(
						blend(surfaceHi, surface, 0.42f),
						blend(surfaceHi, primary, 0.16f), 0, elementRadius()));
				View stripe = new View(MainActivity.this);
				stripe.setBackgroundColor(primary);
				LinearLayout.LayoutParams stripeLp = new LinearLayout.LayoutParams(dp(3), -1);
				stripeLp.setMargins(0, 0, gap, 0);
				reference.addView(stripe, stripeLp);
				LinearLayout details = new LinearLayout(MainActivity.this);
				details.setOrientation(LinearLayout.VERTICAL);
				TextView author = label(target == null ? getString(R.string.reply_to_message) : replyAuthor(target));
				author.setTextColor(primary);
				author.setTextSize(13);
				details.addView(author, new LinearLayout.LayoutParams(-1, -2));
				TextView preview = label(target == null ? getString(R.string.reply_message_unavailable) : replySummary(target));
				preview.setTextColor(muted);
				preview.setTextSize(14);
				preview.setSingleLine(true);
				preview.setEllipsize(TextUtils.TruncateAt.END);
				details.addView(preview, new LinearLayout.LayoutParams(-1, -2));
				reference.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
				reference.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) { focusMessage(message.replyToMessageId); }
				});
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
				lp.setMargins(0, gap / 3, 0, gap / 3);
				box.addView(reference, lp);
			}

			private void addChannelCommentsLink(LinearLayout box, final MiniTaLib.Message message) {
				if (page != Page.CHAT || !currentPeerIsChannel() || currentPeerUser == null
						|| !currentPeerUser.commentsEnabled || message == null || message.commentPostId > 0) return;
				String value = message.commentsCount > 0
						? getString(R.string.channel_comments_count, message.commentsCount)
						: getString(R.string.channel_comments);
				View divider = new View(MainActivity.this);
				divider.setBackgroundColor(blend(muted, surface, 0.72f));
				LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
				dividerLp.setMargins(0, gap / 2, 0, 0);
				box.addView(divider, dividerLp);
				TextView comments = label(value);
				comments.setTextColor(primary);
				comments.setGravity(Gravity.CENTER_VERTICAL);
				comments.setMinHeight(buttonMinHeight);
				comments.setPadding(0, gap / 2, 0, 0);
				comments.setBackgroundDrawable(pressable(Color.TRANSPARENT, blend(surfaceHi, surface, 0.35f), 0, 0));
				comments.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) { showChannelComments(message); }
				});
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
				box.addView(comments, lp);
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
				pill.setOnLongClickListener(new View.OnLongClickListener() {
					@Override public boolean onLongClick(View v) {
						showMessageMenu(row.message);
						return true;
					}
				});
				outer.addView(pill, new LinearLayout.LayoutParams(-2, -2));
				addMessageReactions(outer, row.message);
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

			private void addLocalImagePreview(LinearLayout box, String path) {
				Bitmap bmp = null;
				if (path != null && path.startsWith("content://")) {
					InputStream input = null;
					try {
						input = getContentResolver().openInputStream(Uri.parse(path));
						bmp = BitmapFactory.decodeStream(input);
					} catch (Exception ignored) {
					} finally { if (input != null) try { input.close(); } catch (Exception ignored) {} }
				} else {
					bmp = BitmapFactory.decodeFile(path);
				}
				if (bmp == null) return;
				ImageView preview = new ImageView(MainActivity.this);
				preview.setAdjustViewBounds(true);
				preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
				preview.setMaxHeight(dp(360));
				preview.setImageBitmap(bmp);
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
				lp.setMargins(0, gap, 0, 0);
				box.addView(preview, lp);
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
				for (List<MiniTaLib.Button> row : MessageButtonRows.group(message.buttons)) {
					if (row == null || row.isEmpty()) continue;
					LinearLayout actions = new LinearLayout(MainActivity.this);
					actions.setOrientation(LinearLayout.HORIZONTAL);
					for (final MiniTaLib.Button item : row) {
						Button action = messageActionButton(item.text, new View.OnClickListener() {
							@Override public void onClick(View v) {
								handleMessageButton(message, item, v instanceof Button ? (Button) v : null);
							}
						});
						LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(0, -2, 1);
						if (actions.getChildCount() > 0) actionLp.setMargins(gap / 2, 0, 0, 0);
						actions.addView(action, actionLp);
					}
					LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
					rowLp.setMargins(0, gap / 2, 0, 0);
					box.addView(actions, rowLp);
				}
			}

			private void addMessageReactions(LinearLayout box, final MiniTaLib.Message message) {
				if (message == null || !messageHasReactions(message)) return;
				MessageMetaLayout reactions = new MessageMetaLayout(MainActivity.this, gap / 2, gap / 2);
				addReactionChips(reactions, message);
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
				lp.setMargins(0, gap / 2, 0, 0);
				box.addView(reactions, lp);
			}

			private void addMessageMeta(LinearLayout box, MiniTaLib.Message message) {
				addTransferProgress(box, message);
				MessageMetaLayout meta = new MessageMetaLayout(MainActivity.this, gap / 2, gap / 2);
				if (message != null) addReactionChips(meta, message);
				meta.setFooter(messageFooter(message));
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
				lp.setMargins(0, gap / 3, 0, 0);
				box.addView(meta, lp);
			}

			private void addTransferProgress(LinearLayout box, MiniTaLib.Message message) {
				if (message == null || message.media == null || message.media.isEmpty()
						|| "sent".equals(message.deliveryState) || "sent-own".equals(message.deliveryState)
						|| OutboxStore.FAILED.equals(message.deliveryState)) return;
				LinearLayout progressBox = new LinearLayout(MainActivity.this);
				progressBox.setOrientation(LinearLayout.VERTICAL);
				TextView progressText = label(getString(R.string.file_progress_sending, message.deliveryProgress));
				progressText.setTextColor(muted);
				progressText.setTextSize(12);
				progressBox.addView(progressText, new LinearLayout.LayoutParams(-1, -2));
				ProgressBar progress = new ProgressBar(MainActivity.this, null, android.R.attr.progressBarStyleHorizontal);
				progress.setMax(100);
				progress.setProgress(message.deliveryProgress);
				LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, dp(5));
				barLp.setMargins(0, gap / 3, 0, 0);
				progressBox.addView(progress, barLp);
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
				lp.setMargins(0, gap / 2, 0, 0);
				box.addView(progressBox, lp);
			}

			private boolean messageHasReactions(MiniTaLib.Message message) {
				if (message == null) return false;
				final long pendingPaid = pendingPaidReactionDelta(message.id);
				final long serverPaid = message.paidReaction == null ? 0 : message.paidReaction.amount;
				final long displayedPaid = safeAdd(serverPaid, pendingPaid);
				return displayedPaid > 0 || (message.reactions != null && !message.reactions.isEmpty());
			}

			private void addReactionChips(MessageMetaLayout reactions, final MiniTaLib.Message message) {
				final long pendingPaid = pendingPaidReactionDelta(message.id);
				final long serverPaid = message.paidReaction == null ? 0 : message.paidReaction.amount;
				final long displayedPaid = safeAdd(serverPaid, pendingPaid);
				if (displayedPaid > 0) {
					boolean mine = pendingPaid > 0
							|| (message.paidReaction != null && message.paidReaction.mineAmount > 0);
					final Button paid = reactionChip(String.valueOf(displayedPaid), mine);
					setDastarsButtonIcon(paid, mine ? onPrimary : textColor, dp(19));
					paid.setOnClickListener(new View.OnClickListener() {
						@Override public void onClick(View v) {
							handlePaidReaction(message, paid);
						}
					});
					reactions.addView(paid, reactionChipLayout());
				}
				if (message.reactions != null) {
					for (final MiniTaLib.Reaction item : message.reactions) {
						final Button chip = reactionChip(item.emoji + " " + item.count, item.mine);
						chip.setOnClickListener(new View.OnClickListener() {
							@Override public void onClick(View v) {
								animateReactionView(chip);
								sendFreeReaction(message, item.mine ? "" : item.emoji);
							}
						});
						reactions.addView(chip, reactionChipLayout());
					}
				}
			}

			private ViewGroup.LayoutParams reactionChipLayout() {
				return new ViewGroup.LayoutParams(-2, dp(38));
			}

			private Button reactionChip(String text, boolean selected) {
				Button chip = new Button(MainActivity.this);
				chip.setText(text);
				chip.setTextSize(14);
				chip.setTextColor(selected ? onPrimary : textColor);
				chip.setMinWidth(0);
				chip.setMinimumWidth(0);
				chip.setMinHeight(0);
				chip.setMinimumHeight(0);
				chip.setPadding(gap, 0, gap, 0);
				int normal = selected ? primary : surfaceHi;
				int pressed = selected ? blend(primary, Color.WHITE, 0.18f) : blend(surfaceHi, primary, 0.18f);
				chip.setBackgroundDrawable(pressable(normal, pressed, 0, dp(19)));
				return chip;
			}

			private View messageFooter(MiniTaLib.Message message) {
				LinearLayout footer = new LinearLayout(MainActivity.this);
				footer.setOrientation(LinearLayout.HORIZONTAL);
				footer.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
				boolean delivered = message == null || "sent".equals(message.deliveryState) || "sent-own".equals(message.deliveryState);
				if (delivered) {
					TextView time = new TextView(MainActivity.this);
					time.setTextColor(muted);
					time.setTextSize(12);
					time.setText(formatMessageTime(message == null ? 0 : message.date));
					footer.addView(time, new LinearLayout.LayoutParams(-2, -2));
				}
				if (delivered && message != null && message.editedAt > 0) {
					TextView edited = new TextView(MainActivity.this);
					edited.setTextColor(muted);
					edited.setTextSize(12);
					edited.setText(getString(R.string.message_edited));
					LinearLayout.LayoutParams editedLp = new LinearLayout.LayoutParams(-2, -2);
					editedLp.setMargins(gap / 2, 0, 0, 0);
					footer.addView(edited, editedLp);
				}
				if (message != null && message.from != null
						&& (message.from.login.equals(myLogin) || !"sent".equals(message.deliveryState))) {
					ImageView statusIcon = new ImageView(MainActivity.this);
					if (OutboxStore.FAILED.equals(message.deliveryState)) {
						statusIcon.setImageResource(R.drawable.ic_status_failed);
						statusIcon.setContentDescription(getString(R.string.failed_status));
					} else if (!"sent".equals(message.deliveryState) && !"sent-own".equals(message.deliveryState)) {
						statusIcon.setImageResource(R.drawable.ic_status_pending);
						statusIcon.setContentDescription(getString(R.string.pending_status));
					} else {
						statusIcon.setImageResource(message.readAt > 0 ? R.drawable.ic_status_read : R.drawable.ic_status_sent);
						statusIcon.setContentDescription(message.readAt > 0 ? getString(R.string.read_status) : getString(R.string.sent_status));
					}
					LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(18), dp(18));
					iconLp.setMargins(gap / 2, 0, 0, 0);
					footer.addView(statusIcon, iconLp);
				}
				return footer;
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
		currentPeerNameView = userNameRow(currentHeaderUser(), 18, true);
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
		return userNameRow(user, textSizeSp, false);
	}

	private LinearLayout userNameRow(MiniTaLib.User user, int textSizeSp, boolean chatTitle) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.VERTICAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		populateUserNameRow(row, user, textSizeSp, chatTitle);
		return row;
	}

	private void refreshCurrentPeerNameView() {
		if (currentPeerNameView == null) return;
		currentPeerNameView.removeAllViews();
		populateUserNameRow(currentPeerNameView, currentHeaderUser(), 18, true);
	}

	private void populateUserNameRow(LinearLayout row, MiniTaLib.User user, int textSizeSp, boolean chatTitle) {
		LinearLayout nameLine = new LinearLayout(this);
		nameLine.setOrientation(LinearLayout.HORIZONTAL);
		nameLine.setGravity(Gravity.CENTER_VERTICAL);
		TextView name = new TextView(this);
		name.setTextColor(blend(primary, Color.WHITE, 0.18f));
		name.setTextSize(textSizeSp);
		name.setSingleLine(true);
		String titleText = chatTitle && currentPeerIsSelfChat()
				? getString(R.string.chat_favorites_title)
				: (chatTitle ? chatPeerTitle(user) : displayUser(user));
		name.setText(safeDisplayText(titleText));
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
				if (currentPeerCanManageRoom()) actions.add(getString(R.string.action_invite));
				if (currentPeerCanManageRoom() && currentPeerIsChannel()) {
					actions.add(getString(R.string.channel_settings));
				} else if (currentPeerCanManageRoom()) {
					actions.add(getString(R.string.action_edit_title));
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
				} else if (action.equals(getString(R.string.channel_settings))) {
					showChannelSettings();
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
			if (!"channel".equals(user.roomKind) || currentPeerCanManageRoom()) {
				box.addView(spaced(row(primaryButton(getString(R.string.action_invite), new View.OnClickListener() {
					@Override public void onClick(View v) { showInviteMemberDialog(); }
				}))));
			}
			if (currentPeerCanManageRoom()) {
				if ("channel".equals(user.roomKind)) {
					box.addView(spaced(row(primaryButton(getString(R.string.channel_settings), new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							showChannelSettings();
						}
					}))));
				} else {
					box.addView(spaced(row(
						button(getString(R.string.action_edit_title), new View.OnClickListener() {
							@Override public void onClick(View v) { showEditRoomTitleDialog(); }
						}),
						button(getString(R.string.action_remove_member), new View.OnClickListener() {
							@Override public void onClick(View v) { showRemoveMemberDialog(); }
						})
					)));
				}
			}
		}
		if (user.login != null && user.login.length() > 0) {
			box.addView(spaced(userProfileRow(getString(R.string.profile_username), "@" + user.login, "username")));
		}
		if (user.nick != null && user.nick.length() > 0) {
			box.addView(spaced(userProfileRow(getString(R.string.profile_name), user.nick, null)));
		}
		box.addView(spaced(userProfileRow(
				getString(R.string.profile_description),
				user.description == null || user.description.length() == 0
						? getString(R.string.profile_description_empty) : user.description,
				null
		)));
		if (canEditProfileDescription(user)) {
			box.addView(spaced(row(button(getString(R.string.action_edit_description), new View.OnClickListener() {
				@Override public void onClick(View v) { showEditProfileDescriptionDialog(currentHeaderUser()); }
			}))));
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

	private void showChannelSettings() {
		if (!currentPeerIsChannel() || !currentPeerCanManageRoom()) return;
		page = Page.CHANNEL_SETTINGS;
		if (bottomNav != null) bottomNav.setVisibility(View.GONE);
		content.removeAllViews();
		ScrollView scroll = pageScrollView();
		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(0, 0, 0, gap);
		ImageButton back = headerIconButton(R.drawable.ic_back, getString(R.string.action_back), new View.OnClickListener() {
			@Override public void onClick(View v) {
				showChat();
				loadHistory();
			}
		});
		box.addView(spaced(row(back)));
		box.addView(spaced(title(getString(R.string.channel_settings))));
		box.addView(settingsSection(getString(R.string.channel_settings_general)));
		box.addView(settingsRow(
				getString(R.string.action_edit_title),
				currentPeerUser == null ? "" : currentPeerUser.nick,
				new View.OnClickListener() { @Override public void onClick(View v) { showEditRoomTitleDialog(); } }
		));
		box.addView(settingsRow(
				getString(R.string.action_edit_username),
				currentPeerUser == null || currentPeerUser.login.length() == 0 ? getString(R.string.channel_username_empty) : "@" + currentPeerUser.login,
				new View.OnClickListener() { @Override public void onClick(View v) { showEditChannelUsernameDialog(); } }
		));
		box.addView(settingsRow(
				getString(R.string.action_edit_description),
				currentPeerUser == null || currentPeerUser.description.length() == 0
						? getString(R.string.profile_description_empty) : currentPeerUser.description,
				new View.OnClickListener() { @Override public void onClick(View v) { showEditProfileDescriptionDialog(currentHeaderUser()); } }
		));
		box.addView(settingsSection(getString(R.string.channel_settings_discussion)));
		final boolean commentsEnabled = currentPeerUser != null && currentPeerUser.commentsEnabled;
		box.addView(settingsToggleRow(
				getString(R.string.channel_comments),
				commentsEnabled ? getString(R.string.channel_comments_enabled) : getString(R.string.channel_comments_disabled),
				commentsEnabled,
				new View.OnClickListener() {
					@Override public void onClick(View v) {
						if (commentsEnabled) confirmDisableChannelComments();
						else updateChannelComments(true);
					}
				}
		));
		box.addView(settingsSection(getString(R.string.channel_settings_subscribers)));
		box.addView(settingsRow(getString(R.string.action_members), String.valueOf(currentPeerUser.memberCount), new View.OnClickListener() {
			@Override public void onClick(View v) { showCurrentRoomMembersDialog(); }
		}));
		box.addView(settingsRow(getString(R.string.action_invite), "", new View.OnClickListener() {
			@Override public void onClick(View v) { showInviteMemberDialog(); }
		}));
		box.addView(settingsRow(getString(R.string.action_remove_member), "", new View.OnClickListener() {
			@Override public void onClick(View v) { showRemoveMemberDialog(); }
		}));
		box.addView(settingsSection(getString(R.string.channel_settings_danger)));
		box.addView(spaced(row(button(getString(R.string.confirm_delete_chat), new View.OnClickListener() {
			@Override public void onClick(View v) { confirmDeleteCurrentChat(); }
		}))));
		scroll.addView(box, new ScrollView.LayoutParams(-1, -2));
		content.addView(scroll, fill());
	}

	private void confirmDisableChannelComments() {
		showConfirmDialog(
				getString(R.string.channel_comments_disable_title),
				getString(R.string.channel_comments_disable_message),
				getString(R.string.channel_comments_disable_action),
				new Runnable() { @Override public void run() { updateChannelComments(false); } }
		);
	}

	private void updateChannelComments(final boolean enabled) {
		final MiniTaLib c = ta;
		final String channel = currentPeer;
		if (c == null || channel == null || channel.length() == 0) return;
		status.setText(getString(R.string.status_saving_room));
		run("channel_comments", new Task() {
			@Override public void run() throws Exception {
				final MiniTaLib.Chat chat = c.setChannelComments(channel, enabled);
				if (!enabled) {
					ChatCache.deleteCommentThreads(MainActivity.this, SessionStore.server(MainActivity.this, DEFAULT_SERVER), myLogin, channel);
					OutboxStore.removeChannelComments(MainActivity.this, SessionStore.server(MainActivity.this, DEFAULT_SERVER), OutboxDispatcher.accountKey(MainActivity.this), channel);
				}
				ui(new Runnable() {
					@Override public void run() {
						updateCurrentRoom(chat);
						status.setText(enabled ? getString(R.string.channel_comments_enabled) : getString(R.string.channel_comments_deleted));
					}
				});
			}
		});
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

	private boolean canEditProfileDescription(MiniTaLib.User user) {
		if (user == null) return false;
		if (isOwnUser(user)) return true;
		if (user.roomKind != null && user.roomKind.length() > 0) return currentPeerCanManageRoom();
		return user.bot && "0000000000000001".equals(myID);
	}

	private void showEditProfileDescriptionDialog(final MiniTaLib.User user) {
		if (!canEditProfileDescription(user)) return;
		final EditText input = input(getString(R.string.settings_description_hint), false);
		input.setSingleLine(false);
		input.setMinLines(3);
		input.setMaxLines(6);
		input.setFilters(new android.text.InputFilter[] {
				new android.text.InputFilter.LengthFilter(200)
		});
		input.setText(user.description == null ? "" : user.description);
		showContentDialog(getString(R.string.action_edit_description), input, getString(R.string.action_save), new Runnable() {
			@Override public void run() {
				updateProfileDescription(user, input.getText().toString().trim());
			}
		}, getString(R.string.action_cancel));
	}

	private void updateProfileDescription(final MiniTaLib.User target, final String description) {
		final MiniTaLib c = ta;
		if (c == null || target == null) {
			status.setText(getString(R.string.status_sign_in_first));
			return;
		}
		final boolean own = isOwnUser(target);
		final String address = own ? "" : target.id;
		status.setText(getString(R.string.status_saving_description));
		run("profile_description", new Task() {
			@Override public void run() throws Exception {
				final MiniTaLib.User updated = c.setProfileDescription(address, description);
				ui(new Runnable() {
					@Override public void run() {
						if (own) applyOwnUser(updated);
						else if (currentPeerUser != null && target.id.equals(currentPeerUser.id)) {
							currentPeerUser = updated;
							refreshCurrentPeerNameView();
						}
						status.setText(getString(R.string.status_description_saved));
						if (page == Page.CHANNEL_SETTINGS) showChannelSettings();
					}
				});
			}
		});
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
		boolean reopenChannelSettings = page == Page.CHANNEL_SETTINGS;
		currentPeerUser = chat.peer;
		currentPeer = resolvedPeerName(chat.peer, chat.id);
		refreshCurrentPeerNameView();
		refreshChatInput();
		loadChats();
		if (reopenChannelSettings && currentPeerIsChannel() && currentPeerCanManageRoom()) showChannelSettings();
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
		showSwipeConfirmDialog(titleText, detailText, hintText, onConfirm, null);
	}

	private void showSwipeConfirmDialog(String titleText, String detailText, String hintText,
	                                  final Runnable onConfirm, final Runnable onCancel) {
		final Dialog dialog = new Dialog(this);
		final boolean[] confirmed = {false};
		dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
			@Override public void onDismiss(android.content.DialogInterface ignored) {
				if (!confirmed[0] && onCancel != null) onCancel.run();
			}
		});
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
		final PaymentSliderView slider = paymentSlider(hintText);
		slider.setContentDescription(hintText);
		slider.setOnConfirmAction(new Runnable() {
			@Override
			public void run() {
				confirmed[0] = true;
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
		showActionDialog(null, actions, handler);
	}

	private void showActionDialog(String titleText, final String[] actions, final ChoiceHandler handler) {
		if (actions == null || actions.length == 0) return;
		final Dialog dialog = new Dialog(this);
		LinearLayout box = dialogBox();
		if (titleText != null && titleText.length() > 0) {
			box.addView(title(titleText), new LinearLayout.LayoutParams(-1, -2));
		}
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
		setScrollableDialogContent(dialog, box);
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

	private void showStyledDialog(Dialog dialog) {
		configureDialogWindow(dialog);
		dialog.show();
		installSwipeDismiss(dialog);
		configureDialogWindow(dialog);
	}

	private void installSwipeDismiss(final Dialog dialog) {
		Window window = dialog.getWindow();
		if (window == null) return;
		View contentView = window.findViewById(android.R.id.content);
		if (!(contentView instanceof ViewGroup)) return;
		ViewGroup contentRoot = (ViewGroup) contentView;
		if (contentRoot.getChildCount() != 1 || contentRoot.getChildAt(0) instanceof SwipeDismissLayout) return;
		View sheet = contentRoot.getChildAt(0);
		contentRoot.removeView(sheet);
		SwipeDismissLayout swipe = new SwipeDismissLayout(this);
		swipe.setDismissAction(new Runnable() {
			@Override public void run() { dialog.dismiss(); }
		});
		swipe.addView(sheet, new android.widget.FrameLayout.LayoutParams(-1, -2));
		contentRoot.addView(swipe, new ViewGroup.LayoutParams(-1, -2));
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
		LinearLayout outer = new LinearLayout(this);
		outer.setOrientation(LinearLayout.VERTICAL);
		composerMediaBar = new LinearLayout(this);
		composerMediaBar.setOrientation(LinearLayout.VERTICAL);
		outer.addView(composerMediaBar, new LinearLayout.LayoutParams(-1, -2));
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
		outer.addView(r, new LinearLayout.LayoutParams(-1, -2));
		renderComposerMedia();
		return outer;
	}

	private void renderComposerMedia() {
		if (composerMediaBar == null) return;
		composerMediaBar.removeAllViews();
		for (int index = 0; index < composerMedia.size(); index++) {
			final int position = index;
			ComposerMedia item = composerMedia.get(index);
			LinearLayout row = new LinearLayout(this);
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setPadding(gap, gap / 2, gap, gap / 2);
			row.setBackgroundDrawable(shape(surfaceHi, 0, elementRadius()));
			TextView label = label(item.name + " · " + formatBytes(item.size));
			row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
			Button remove = button("×", new View.OnClickListener() {
				@Override public void onClick(View v) {
					if (position < 0 || position >= composerMedia.size()) return;
					ComposerMedia removed = composerMedia.remove(position);
					if (removed.localPath != null && removed.localPath.length() > 0) new File(removed.localPath).delete();
					renderComposerMedia();
				}
			});
			row.addView(remove, new LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight));
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
			lp.setMargins(0, 0, 0, gap / 2);
			composerMediaBar.addView(row, lp);
		}
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

	private Drawable choiceButtonDrawable(boolean radio) {
		int size = choiceButtonSize();
		return new ChoiceButtonDrawable(
			radio,
			size,
			choiceButtonLeadingInset(),
			blend(surface, Color.WHITE, 0.04f),
			blend(muted, bg, 0.28f),
			blend(surfaceHi, primary, 0.08f),
			primary,
			blend(primary, Color.WHITE, 0.10f),
			blend(surface, bg, 0.35f),
			blend(muted, bg, 0.58f),
			onPrimary,
			blend(textColor, bg, 0.58f)
		);
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

	private static final class ChoiceCheckBox extends CheckBox {
		private final int minTextInset;

		ChoiceCheckBox(android.content.Context context, int minTextInset) {
			super(context);
			this.minTextInset = minTextInset;
		}

		@Override
		public int getCompoundPaddingLeft() {
			return Math.max(super.getCompoundPaddingLeft(), getPaddingLeft() + minTextInset);
		}
	}

	private static final class ChoiceRadioButton extends RadioButton {
		private final int minTextInset;

		ChoiceRadioButton(android.content.Context context, int minTextInset) {
			super(context);
			this.minTextInset = minTextInset;
		}

		@Override
		public int getCompoundPaddingLeft() {
			return Math.max(super.getCompoundPaddingLeft(), getPaddingLeft() + minTextInset);
		}
	}

	private static final class ChoiceButtonDrawable extends Drawable {
		private final boolean radio;
		private final int size;
		private final int leadingInset;
		private final int uncheckedFill;
		private final int uncheckedStroke;
		private final int pressedFill;
		private final int checkedFill;
		private final int checkedPressedFill;
		private final int disabledFill;
		private final int disabledStroke;
		private final int mark;
		private final int disabledMark;
		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Path path = new Path();
		private final RectF rect = new RectF();
		private boolean checked;
		private boolean enabled = true;
		private boolean pressed;
		private int alpha = 255;

		ChoiceButtonDrawable(
				boolean radio,
				int size,
				int leadingInset,
				int uncheckedFill,
				int uncheckedStroke,
				int pressedFill,
				int checkedFill,
				int checkedPressedFill,
				int disabledFill,
				int disabledStroke,
				int mark,
				int disabledMark) {
			this.radio = radio;
			this.size = size;
			this.leadingInset = leadingInset;
			this.uncheckedFill = uncheckedFill;
			this.uncheckedStroke = uncheckedStroke;
			this.pressedFill = pressedFill;
			this.checkedFill = checkedFill;
			this.checkedPressedFill = checkedPressedFill;
			this.disabledFill = disabledFill;
			this.disabledStroke = disabledStroke;
			this.mark = mark;
			this.disabledMark = disabledMark;
		}

		@Override
		public void draw(Canvas canvas) {
			Rect bounds = getBounds();
			float s = Math.min(size, Math.min(Math.max(0, bounds.width() - leadingInset), bounds.height()));
			float left = bounds.left + leadingInset;
			float top = bounds.top + (bounds.height() - s) / 2.0f;
			float strokeWidth = Math.max(1.0f, s * 0.095f);
			int fill = fillColor();
			int stroke = strokeColor();
			int markColor = enabled ? mark : disabledMark;
			paint.setAlpha(alpha);
			if (radio) {
				drawRadio(canvas, left, top, s, strokeWidth, fill, stroke, markColor);
			} else {
				drawCheckBox(canvas, left, top, s, strokeWidth, fill, stroke, markColor);
			}
		}

		private int fillColor() {
			if (!enabled) return disabledFill;
			if (checked && pressed) return checkedPressedFill;
			if (checked) return checkedFill;
			if (pressed) return pressedFill;
			return uncheckedFill;
		}

		private int strokeColor() {
			if (!enabled) return disabledStroke;
			if (checked || pressed) return checkedFill;
			return uncheckedStroke;
		}

		private void drawRadio(Canvas canvas, float left, float top, float s, float strokeWidth, int fill, int stroke, int markColor) {
			float cx = left + s / 2.0f;
			float cy = top + s / 2.0f;
			float radius = s / 2.0f - strokeWidth / 2.0f;
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(fill);
			canvas.drawCircle(cx, cy, radius, paint);
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(strokeWidth);
			paint.setColor(stroke);
			canvas.drawCircle(cx, cy, radius, paint);
			if (!checked) return;
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(markColor);
			canvas.drawCircle(cx, cy, s * 0.27f, paint);
		}

		private void drawCheckBox(Canvas canvas, float left, float top, float s, float strokeWidth, int fill, int stroke, int markColor) {
			float radius = s * 0.22f;
			rect.set(
				left + strokeWidth / 2.0f,
				top + strokeWidth / 2.0f,
				left + s - strokeWidth / 2.0f,
				top + s - strokeWidth / 2.0f
			);
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(fill);
			canvas.drawRoundRect(rect, radius, radius, paint);
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(strokeWidth);
			paint.setColor(stroke);
			canvas.drawRoundRect(rect, radius, radius, paint);
			if (!checked) return;
			paint.setColor(markColor);
			paint.setStrokeWidth(Math.max(2.0f, s * 0.13f));
			paint.setStrokeCap(Paint.Cap.ROUND);
			paint.setStrokeJoin(Paint.Join.ROUND);
			path.reset();
			path.moveTo(left + s * 0.28f, top + s * 0.52f);
			path.lineTo(left + s * 0.43f, top + s * 0.67f);
			path.lineTo(left + s * 0.73f, top + s * 0.35f);
			canvas.drawPath(path, paint);
			paint.setStrokeCap(Paint.Cap.BUTT);
			paint.setStrokeJoin(Paint.Join.MITER);
		}

		@Override
		public boolean isStateful() {
			return true;
		}

		@Override
		protected boolean onStateChange(int[] state) {
			boolean nextEnabled = false;
			boolean nextChecked = false;
			boolean nextPressed = false;
			if (state != null) {
				for (int value : state) {
					if (value == android.R.attr.state_enabled) nextEnabled = true;
					else if (value == android.R.attr.state_checked) nextChecked = true;
					else if (value == android.R.attr.state_pressed || value == android.R.attr.state_focused) nextPressed = true;
				}
			}
			if (enabled == nextEnabled && checked == nextChecked && pressed == nextPressed) return false;
			enabled = nextEnabled;
			checked = nextChecked;
			pressed = nextPressed;
			invalidateSelf();
			return true;
		}

		@Override
		public void setAlpha(int alpha) {
			this.alpha = alpha;
			invalidateSelf();
		}

		@Override
		public void setColorFilter(ColorFilter colorFilter) {
			paint.setColorFilter(colorFilter);
			invalidateSelf();
		}

		@Override
		public int getOpacity() {
			return PixelFormat.TRANSLUCENT;
		}

		@Override
		public int getIntrinsicWidth() {
			return leadingInset + size;
		}

		@Override
		public int getIntrinsicHeight() {
			return size;
		}
	}

	private interface Task {
		void run() throws Exception;
	}

	private interface ChoiceHandler {
		void onChoice(int which);
	}
}
