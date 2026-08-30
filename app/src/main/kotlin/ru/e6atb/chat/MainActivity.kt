package ru.e6atb.chat

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter.LengthFilter
import android.text.InputType
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.util.Base64
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Switch
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.HashMap
import java.util.HashSet
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONObject

private fun String?.equalsIgnoreCase(other: String?): Boolean = this.equals(other, ignoreCase = true)
private fun String.charAt(index: Int): Char = this[index]
private fun TextView.setTextSize(size: Int) = setTextSize(size.toFloat())
private val TextView.length: Int get() = this.length()
private typealias Entry = OutboxStore.Entry
private typealias NodeStatus = MST5.NodeStatus
private typealias User = MST5.User
private typealias MessageMedia = MST5.MessageMedia
private typealias TransferControl = MST5.TransferControl
private typealias Attachment = OutboxStore.Attachment
private typealias Message = MST5.Message

class MainActivity : Activity() {
    private var pad = 0
    private var gap = 0
    private var buttonPadX = 0
    private var buttonPadY = 0
    private var buttonMinHeight = 0
    private var bg = 0
    private var surface = 0
    private var surfaceHi = 0
    private var textColor = 0
    private var muted = 0
    private var primary = 0
    private var onPrimary = 0
    private var border = 0
    private var accentSurface = 0
    private var danger = 0
    private var success = 0

    private val main: Handler = Handler(Looper.getMainLooper())
    private val io: ExecutorService = Executors.newFixedThreadPool(2)
    private val cacheIo: ExecutorService = Executors.newSingleThreadExecutor()
    private val historyCacheIo: ExecutorService = Executors.newSingleThreadExecutor()
    private val paidReactionIo: ExecutorService = Executors.newSingleThreadExecutor()
    private val pendingPaidReactionDeltas: MutableMap<Long, Long> = HashMap()
    private val paidReactionBatches: MutableMap<Long, PaidReactionBatch> = HashMap()
    private val seenMessages: MutableSet<Long> = HashSet()
    private val imagePreviewCache: android.util.LruCache<String?, Bitmap?> =
        object : LruCache<String?, Bitmap?>(32 * 1024 * 1024) {
            override fun sizeOf(key: String?, value: Bitmap?): Int {
                if (value == null) return 0
                if (Build.VERSION.SDK_INT >= 19) return value.getAllocationByteCount()
                return value.getRowBytes() * value.getHeight()
            }
        }
    private val imagePreviewErrors: MutableMap<String, String> = HashMap()
    private val imagePreviewLoading: MutableSet<String> = HashSet()
    private val chatData = ArrayList<MST5.Chat>()
    private val visibleChatData = ArrayList<MST5.Chat>()
    private val composerMedia = ArrayList<ComposerMedia>()
    private val botCommands = ArrayList<MST5.BotCommand>()
    private var botCommandsPeer: String? = ""
    private val stickerPacks = ArrayList<MST5.StickerPack>()
    private var stickerPacksLoaded = false
    private var stickerPacksLoading = false
    private val voiceCall: VoiceCall = VoiceCall()
    private lateinit var rootView: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var bottomNav: LinearLayout
    private lateinit var serverUrl: EditText
    private lateinit var email: EditText
    private lateinit var accountUsername: EditText
    private lateinit var accountName: EditText
    private lateinit var accountDescription: EditText
    private lateinit var emailCode: EditText
    private lateinit var password: EditText
    private lateinit var accountCloudPassword: EditText
    private lateinit var accountCloudPasswordCode: EditText
    private lateinit var accountDeleteCode: EditText
    private lateinit var contactAddress: EditText
    private lateinit var peer: EditText
    private lateinit var chatSearch: EditText
    private lateinit var text: EditText
    private lateinit var composerMediaBar: LinearLayout
    private var composerSending = false
    private lateinit var walletTo: EditText
    private lateinit var walletAmount: EditText
    private lateinit var walletComment: EditText
    private lateinit var status: TextView
    private lateinit var walletBalanceView: TextView
    private lateinit var walletCodeView: TextView
    private lateinit var walletReceiveView: TextView
    private lateinit var walletInstructionView: TextView
    private lateinit var walletHistoryView: LinearLayout
    private lateinit var walletRecentView: LinearLayout
    private var hasWalletBalance = false
    private var walletBalance: Long = 0
    private var walletCode: String? = "DSR"
    private lateinit var nodeStatusListView: LinearLayout
    private lateinit var accountSessionsView: LinearLayout
    private lateinit var contactsView: LinearLayout
    private lateinit var callStateView: TextView
    private lateinit var callPeerView: TextView
    private lateinit var callDurationView: TextView
    private lateinit var callHintView: TextView
    private lateinit var callParticipantsView: TextView
    private lateinit var chatInputContainer: LinearLayout
    private lateinit var commentInputContainer: LinearLayout
    private lateinit var cloudPasswordState: TextView
    private lateinit var currentPeerNameView: LinearLayout
    private lateinit var callButton: ImageButton
    private lateinit var callPrimaryAction: Button
    private lateinit var callSecondaryAction: Button
    private lateinit var callChatAction: Button
    private lateinit var loginButton: Button
    private lateinit var resendEmailCodeButton: Button
    private lateinit var cloudPasswordSaveButton: Button
    private lateinit var cloudPasswordClearButton: Button
    private lateinit var deleteAccountCodeButton: Button
    private lateinit var sendButton: ImageButton
    private lateinit var chatsTab: Button
    private lateinit var settingsTab: Button
    private lateinit var showStatusCheck: CheckBox
    private lateinit var useInsetsCheck: CheckBox
    private lateinit var languageGroup: RadioGroup
    private lateinit var protocolGroup: RadioGroup
    private lateinit var messagePrivacyGroup: RadioGroup
    private lateinit var callPrivacyGroup: RadioGroup
    private lateinit var invitePrivacyGroup: RadioGroup
    private lateinit var chatRows: MessageAdapter
    private lateinit var messageRows: MessageAdapter
    private lateinit var messageList: ListView
    private var ta: MST5? = null

    @Volatile
    private var polling = false

    @Volatile
    private var activityResumed = false

    @Volatile
    private var pollingGeneration = 0

    @Volatile
    private var lastUpdate: Long = 0
    private var syncReceiverRegistered = false
    private val syncReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null || !MessageSyncService.ACTION_SYNC_UPDATED.equals(intent.getAction())) return
            lastUpdate = Math.max(lastUpdate, intent.getLongExtra("cursor", 0))
            loadCachedChats()
            if (page === Page.CHAT && currentPeer != null && currentPeer.length > 0) {
                loadCachedHistory(currentPeer)
            }
        }
    }
    private var myID = ""
    private var myEmail = ""
    private var myLogin = ""
    private var myNick = ""
    private var myDescription = ""
    private var myAvatar: MST5.FileInfo? = null
    private var myVerified = false
    private var myBot = false
    private var myMessagePrivacy = "everyone"
    private var myCallPrivacy = "everyone"
    private var myInvitePrivacy = "everyone"
    private var currentPeer = ""
    private var currentPeerUser: MST5.User? = null
    private var currentPeerBanned = false
    private var currentPeerBannedByMe = false
    private var currentPeerBannedMe = false
    private var pendingAcceptedPeer = ""
    private var pendingOutgoingConnectPeer = ""
    private var pendingVoiceRoom = ""
    private var pendingSessionIntent: Intent? = null
    private var activeCallPeer = ""
    private var activeVoiceRoom = false
    private var callStartedAtMs: Long = 0
    private var callState = "idle"

    @Volatile
    private var voiceConnectGeneration = 0
    private val callClock: Runnable = object : Runnable {
        override fun run() {
            updateCallDuration()
            updateActiveCallNotification()
            if (!"idle".equals(callState)) main.postDelayed(this, 1000)
        }
    }
    private val voiceParticipantsPoll: Runnable = object : Runnable {
        override fun run() {
            if (activeVoiceRoom && !"idle".equals(callState)) {
                loadVoiceParticipants()
                main.postDelayed(this, 2000)
            }
        }
    }
    private var page: Page? = Page.NONE
    private var currentCommentPost: MST5.Message? = null
    private var replyToMessage: MST5.Message? = null
    private var editingMessage: MST5.Message? = null

    private class ComposerMedia {
        var uri: Uri? = null
        var name: String? = null
        var mime: String? = null
        var localPath: String? = null
        var fileId: String? = null
        var size: Long = 0
        var preview: Bitmap? = null
        var photo: Boolean = false
    }

    private val channelHistoryReload: Runnable = object : Runnable {
        override fun run() {
            if (page === Page.CHAT && currentPeerIsChannel()) loadHistory()
        }
    }
    private var oldestMessage: Long = 0
    private var historyLoaded = false
    private var hasOlderMessages = false
    private var loadingOlderMessages = false

    @Volatile
    private var chatOpenGeneration = 0

    @Volatile
    private var historyRequestGeneration = 0
    private var waitingEmailCode = false
    private var authNeedsCloudPassword = false
    private var pendingEmailCode: String? = ""
    private var emailCodeSentAtMs: Long = 0
    private var emailCodeCooldownEmail = ""
    private val emailCodeCooldownTick: Runnable = object : Runnable {
        override fun run() {
            updateEmailCodeCooldown()
            if (resendEmailCodeButton != null && emailCodeResendRemainingMs(currentEmailText()) > 0) {
                main.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        AppLocale.apply(this)
        initDimens()
        loadPalette()
        setContentView(shell())
        setStatusBarColorCompat(bg)
        createCallNotificationChannel()
        requestNotifications()
        requestReadStoragePermission()
        restoreSession()
        handleIntent(getIntent())
        maybeOfferGithubUpdate()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onBackPressed() {
        if (handleBackNavigation()) {
            return
        }
        super.onBackPressed()
    }

    private fun handleBackNavigation(): Boolean {
        if (page === Page.CHANNEL_COMMENTS || page === Page.CHANNEL_SETTINGS) {
            showChat()
            loadHistory()
            return true
        }
        if (page === Page.CHAT || page === Page.SETTINGS) {
            showChats()
            return true
        }
        if (this.isSettingsDetailPage) {
            showSettings()
            return true
        }
        if (page === Page.CALL) {
            val peerName: String = (if (activeCallPeer.length == 0) currentPeer else activeCallPeer)!!
            if (peerName.length > 0) {
                openChatImmediately(peerName, null, false, false, false, null)
                return true
            }
        }
        return false
    }

    private val isSettingsDetailPage: Boolean
        get() = page === Page.SETTINGS_PROFILE || page === Page.SETTINGS_SESSIONS || page === Page.SETTINGS_CLOUD_PASSWORD || page === Page.SETTINGS_E2E_KEYS || page === Page.SETTINGS_AUTHORIZATION || page === Page.SETTINGS_DELETE_ACCOUNT || page === Page.SETTINGS_LOGOUT || page === Page.SETTINGS_SERVER || page === Page.SETTINGS_LANGUAGE || page === Page.SETTINGS_PROTOCOL || page === Page.SETTINGS_INTERFACE

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == ru.e6atb.chat.MainActivity.Companion.REQ_CAMERA) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startOAuthQrScanner()
            } else {
                status.setText(getString(R.string.oauth_camera_required))
            }
            return
        }
        if (requestCode == ru.e6atb.chat.MainActivity.Companion.REQ_MICROPHONE && grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            val outgoingPeer: String? = pendingOutgoingConnectPeer
            pendingOutgoingConnectPeer = ""
            if (outgoingPeer != null && !outgoingPeer.isEmpty()) {
                ++voiceConnectGeneration
                setCallState("connecting", outgoingPeer)
                startVoiceConnection(ta, outgoingPeer, getString(R.string.status_peer_accepted_call, outgoingPeer))
                return
            }
            val voiceRoom = pendingVoiceRoom
            pendingVoiceRoom = ""
            if (voiceRoom != null && !voiceRoom.isEmpty()) {
                currentPeer = voiceRoom
                startGroupVoice()
                return
            }
            val peerName = pendingAcceptedPeer
            pendingAcceptedPeer = ""
            if (peerName != null && !peerName.isEmpty()) acceptIncomingCall(peerName)
        } else if (requestCode == ru.e6atb.chat.MainActivity.Companion.REQ_MICROPHONE) {
            var deniedPeer =
                if (pendingOutgoingConnectPeer.length > 0) pendingOutgoingConnectPeer else pendingAcceptedPeer
            if ((deniedPeer == null || deniedPeer.length == 0) && pendingVoiceRoom.length > 0) deniedPeer =
                pendingVoiceRoom
            pendingAcceptedPeer = ""
            pendingOutgoingConnectPeer = ""
            pendingVoiceRoom = ""
            clearIncomingCallUi()
            if (deniedPeer != null && deniedPeer.length > 0) {
                setCallState("failed", deniedPeer)
            }
            status.setText(getString(R.string.status_microphone_denied))
        }
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        registerSyncReceiver()
        maybeOfferGithubUpdate()
        if (ta != null) startPolling()
    }

    override fun onPause() {
        activityResumed = false
        stopPolling()
        unregisterSyncReceiver()
        super.onPause()
    }

    override fun onDestroy() {
        stopPolling()
        unregisterSyncReceiver()
        main.removeCallbacks(emailCodeCooldownTick)
        cancelActiveCallNotification()
        dismissCallWindow()
        voiceCall.stop()
        io.shutdownNow()
        cacheIo.shutdownNow()
        for (batch in paidReactionBatches.values) {
            batch.flush?.let(main::removeCallbacks)
        }
        paidReactionBatches.clear()
        paidReactionIo.shutdownNow()
        recycleComposerPreviews()
        ta?.close()
        super.onDestroy()
    }


    private fun registerSyncReceiver() {
        if (syncReceiverRegistered) return
        val filter: IntentFilter = IntentFilter(MessageSyncService.ACTION_SYNC_UPDATED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(syncReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(syncReceiver, filter)
        syncReceiverRegistered = true
    }

    private fun unregisterSyncReceiver() {
        if (!syncReceiverRegistered) return
        try {
            unregisterReceiver(syncReceiver)
        } catch (ignored: Exception) {
        }
        syncReceiverRegistered = false
    }

    private fun shell(): LinearLayout {
        val root: LinearLayout = LinearLayout(this)
        rootView = root
        root.setOrientation(LinearLayout.VERTICAL)
        root.setBackgroundColor(bg)
        applyRootPadding(root)
        installInsetsCompat(root)
        status = TextView(this)
        status.setText(getString(R.string.status_offline))
        status.setTextColor(muted)
        status.setTextSize(12)
        status.setGravity(Gravity.CENTER_VERTICAL)
        status.setPadding(pad, gap / 2, pad, gap / 2)
        status.setBackgroundColor(surface)
        content = LinearLayout(this)
        content.setOrientation(LinearLayout.VERTICAL)
        content.setPadding(0, 0, 0, 0)

        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        updateStatusVisibility()
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        bottomNav = navRow(
            iconButton(R.drawable.ic_nav_chats, getString(R.string.nav_chats), object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showChats()
                }
            }),
            iconButton(R.drawable.ic_dastars, getString(R.string.nav_wallet), object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showWallet()
                }
            }),
            iconButton(R.drawable.ic_nodes, getString(R.string.nav_nodes), object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showNodeStatus()
                }
            }),
            iconButton(R.drawable.ic_nav_settings, getString(R.string.nav_settings), object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showSettings()
                }
            })
        )
        bottomNav.setVisibility(View.GONE)
        bottomNav.setBackgroundDrawable(shape(surface, 0, 0))
        root.addView(bottomNav, LinearLayout.LayoutParams(-1, -2))
        return root
    }

    private fun restoreSession() {
        if (!SessionStore.hasSession(this)) {
            showLogin()
            return
        }
        val url: String? = SessionStore.transportEndpoint(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER)
        val token: String? = SessionStore.token(this)
        myID = SessionStore.userId(this)
        myLogin = SessionStore.login(this)
        lastUpdate = SessionStore.lastUpdate(this)
        ta = MST5(this, url.orEmpty(), token.orEmpty(), myID, myLogin)
        val c: MST5? = ta
        io.execute(object : Runnable {
            override fun run() {
                try {
                    CrashReportDispatcher.dispatch(this@MainActivity, c)
                } catch (ignored: Exception) {
                    // The report remains pending and is retried by the foreground service.
                }
            }
        })
        status.setText(getString(R.string.status_online))
        showChats()
        startPolling()
        startSyncService()
        run("session", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val client = c ?: return
                val u: MST5.User? = client.me()
                applyOwnUser(u)

                SessionStore.save(this@MainActivity, url, token, myID, myLogin)

                ui(object : Runnable {
                    override fun run() {
                        status.setText(getString(R.string.status_online_as, displayOwnUser()))
                        flushPendingSessionIntent()
                    }
                })
            }
        })
    }

    private fun showServer() {
        SessionStore.saveServer(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER)
        showLogin()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (ru.e6atb.chat.MainActivity.Companion.ACTION_OPEN_UPDATE.equals(intent.getAction())) {
            intent.setAction(null)
            cancelGithubUpdateNotification()
            checkGithubUpdate(true)
            return
        }
        if (requiresSession(intent) && ta == null) {
            pendingSessionIntent = Intent(intent)
            return
        }
        if (isQrLoginIntent(intent)) {
            val code = intent.getData()?.getQueryParameter("code") ?: ""
            if (code.isNotBlank()) openQrLoginRequest(code.trim())
            return
        }
        if (isOAuthIntent(intent)) {
            val data: Uri? = intent.getData()
            val userCode: String? = if (data == null) "" else data.getQueryParameter("user_code")
            if (userCode != null && !userCode.trim().isEmpty()) {
                openOAuthDeviceRequest(userCode.trim())
            }
            return
        }
        if (isBotIntent(intent)) {
            if (!intent.getBooleanExtra(ru.e6atb.chat.MainActivity.Companion.EXTRA_BOT_LINK_CONSUMED, false)) {
                val link: BotDeepLinkParser.Link? = BotDeepLinkParser.parse(
                    if (intent.getData() == null) "" else intent.getData().toString()
                )
                if (link != null) {
                    intent.putExtra(ru.e6atb.chat.MainActivity.Companion.EXTRA_BOT_LINK_CONSUMED, true)
                    openBotDeepLink(link)
                }
            }
            return
        }
        if (ru.e6atb.chat.MainActivity.Companion.ACTION_OPEN_CALL.equals(intent.getAction()) || intent.hasExtra(ru.e6atb.chat.MainActivity.Companion.EXTRA_CALL)) {
            var peerName: String? = intent.getStringExtra(ru.e6atb.chat.MainActivity.Companion.EXTRA_PEER)
            if (peerName == null || peerName.trim().isEmpty()) {
                peerName = intent.getStringExtra(ru.e6atb.chat.MainActivity.Companion.EXTRA_CALL)
            }
            if ((peerName == null || peerName.trim()
                    .isEmpty()) && !"idle".equals(callState) && activeCallPeer.length > 0
            ) {
                updateCallWindow()
                return
            }
            if (peerName != null && !peerName.trim().isEmpty()) {
                openIncomingCall(peerName.trim())
                return
            }
        }
        if (ru.e6atb.chat.MainActivity.Companion.ACTION_ACCEPT_CALL.equals(intent.getAction())) {
            val peerName: String? = intent.getStringExtra(ru.e6atb.chat.MainActivity.Companion.EXTRA_PEER)
            if (peerName != null && !peerName.trim().isEmpty()) {
                acceptIncomingCall(peerName.trim())
                return
            }
        }
        if (intent.hasExtra(ru.e6atb.chat.MainActivity.Companion.EXTRA_CHAT)) {
            val chatPeer: String? = intent.getStringExtra(ru.e6atb.chat.MainActivity.Companion.EXTRA_CHAT)
            if (chatPeer != null && !chatPeer.isEmpty()) {
                openChatIfExists(chatPeer.trim())
            }
        }
    }

    private fun requiresSession(intent: Intent?): Boolean {
        if (intent == null) return false
        return isOAuthIntent(intent)
                || isBotIntent(intent)
                || ru.e6atb.chat.MainActivity.Companion.ACTION_OPEN_CALL.equals(intent.getAction())
                || ru.e6atb.chat.MainActivity.Companion.ACTION_ACCEPT_CALL.equals(intent.getAction())
                || intent.hasExtra(ru.e6atb.chat.MainActivity.Companion.EXTRA_CALL)
                || intent.hasExtra(ru.e6atb.chat.MainActivity.Companion.EXTRA_CHAT)
    }

    private fun isBotIntent(intent: Intent?): Boolean {
        return intent != null && Intent.ACTION_VIEW.equals(intent.getAction())
                && intent.getData() != null && BotDeepLinkParser.parse(intent.getData().toString()) != null
    }

    private fun isOAuthIntent(intent: Intent?): Boolean {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return false
        val data: Uri? = intent.getData()
        if (data == null) return false
        val custom = "ovechat".equalsIgnoreCase(data.getScheme())
                && "authorize".equalsIgnoreCase(data.getHost())
        val web = "https".equalsIgnoreCase(data.getScheme())
                && "ms.ove.rs".equalsIgnoreCase(data.getHost())
                && "/oauth/device".equals(data.getPath())
        return custom || web
    }

    private fun isQrLoginIntent(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        return "ovechat".equalsIgnoreCase(data.scheme) && "login".equalsIgnoreCase(data.host)
    }

    private fun openQrLoginRequest(code: String) {
        val client = ta ?: return
        AlertDialog.Builder(this).setTitle("Вход в web-клиент").setMessage("Разрешить вход на другом устройстве?").setNegativeButton("Отклонить") { _, _ -> run("qr_reject", object : Task { override fun run() { client.approveQrLogin(code, false) } }) }.setPositiveButton("Разрешить") { _, _ -> run("qr_approve", object : Task { override fun run() { client.approveQrLogin(code, true) } }) }.show()
    }

    private fun openBotDeepLink(link: BotDeepLinkParser.Link?) {
        val c: MST5? = ta
        if (c == null || link == null) return
        openChatImmediately(link.login, null, false, false, false, object : Runnable {
            override fun run() {
                sendChatMessage(currentPeer, link.startCommand(), false)
            }
        })
    }

    private fun openOAuthDeviceRequest(rawCode: String?) {
        val userCode: String = OAuthCodeParser.parse(rawCode)
        if (userCode.length == 0) {
            status.setText(getString(R.string.oauth_invalid_code))
            return
        }
        val client: MST5? = ta
        if (client == null) return
        run("oauth_device", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val request: MST5.OAuthDeviceRequest = client.oauthDeviceRequest(userCode)
                ui(object : Runnable {
                    override fun run() {
                        if (!"pending".equals(request.status)) {
                            status.setText(getString(R.string.oauth_already_decided))
                            return
                        }
                        showOAuthConfirmDialog(request)
                    }
                })
            }
        })
    }

    private fun showOAuthConfirmDialog(request: MST5.OAuthDeviceRequest) {
        val dialog: Dialog = Dialog(this)
        val box: LinearLayout = dialogBox()
        box.addView(title(getString(R.string.oauth_authorize_title)), LinearLayout.LayoutParams(-1, -2))
        var detail: String? = getString(
            R.string.oauth_authorize_body,
            if (request.clientName.length == 0) request.clientID else request.clientName,
            request.audience,
            request.userCode
        )
        if (request.actionDescription.length > 0) {
            detail += "\n\n" + getString(R.string.oauth_action_description, request.actionDescription)
        }
        val details: TextView = label(detail)
        details.setTextColor(muted)
        box.addView(spaced(details))
        val slider: PaymentSliderView = paymentSlider(getString(R.string.oauth_swipe_approve))
        slider.setOnConfirmAction(object : Runnable {
            override fun run() {
                dialog.dismiss()
                decideOAuth(request.userCode, true)
            }
        })
        box.addView(slider, LinearLayout.LayoutParams(-1, dp(56)))
        val reject: Button = button(getString(R.string.action_reject), object : View.OnClickListener {
            override fun onClick(view: View?) {
                dialog.dismiss()
                decideOAuth(request.userCode, false)
            }
        })
        box.addView(spaced(reject))
        setScrollableDialogContent(dialog, box)
        showStyledDialog(dialog)
    }

    private fun decideOAuth(userCode: String?, approve: Boolean) {
        val client: MST5? = ta
        if (client == null) return
        run("oauth_decision", object : Task {
            @Throws(Exception::class)
            override fun run() {
                client.oauthDeviceDecision(userCode.orEmpty(), approve)
                ui(object : Runnable {
                    override fun run() {
                        status.setText(getString(if (approve) R.string.oauth_authorized else R.string.oauth_rejected))
                    }
                })
            }
        })
    }

    private fun flushPendingSessionIntent() {
        if (pendingSessionIntent == null || ta == null) return
        val intent: Intent? = pendingSessionIntent
        pendingSessionIntent = null
        handleIntent(intent)
    }

    private fun showLogin() {
        page = Page.LOGIN
        if (bottomNav != null) bottomNav.setVisibility(View.GONE)
        content.removeAllViews()
        content.setPadding(pad, pad, pad, pad)
        main.removeCallbacks(emailCodeCooldownTick)
        val currentEmail: String = if (::email.isInitialized) email.text.toString().trim() else ""
        val currentCode: String = if (::emailCode.isInitialized) emailCode.text.toString().trim() else pendingEmailCode.orEmpty()
        val currentPassword: String = if (::password.isInitialized) password.text.toString() else ""
        email = input(getString(R.string.hint_email), false)
        email.setText(currentEmail)
        status.setText(
            getString(
                R.string.status_server,
                SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER)
            )
        )
        val cloudPasswordWarning: TextView = label(getString(R.string.auth_cloud_password_warning))
        cloudPasswordWarning.setTextColor(textColor)
        cloudPasswordWarning.setPadding(gap, gap, gap, gap)
        cloudPasswordWarning.setBackgroundDrawable(shape(surfaceHi, primary, elementRadius()))
        content.addView(spaced(cloudPasswordWarning))
        content.addView(spaced(title(getString(R.string.screen_account))))
        content.addView(spaced(email))
        if (!waitingEmailCode) {
            loginButton = primaryButton(getString(R.string.action_next), object : View.OnClickListener {
                override fun onClick(v: View?) {
                    requestEmailCode()
                }
            })
            val authRow: LinearLayout = row(loginButton)
            content.addView(spaced(authRow))
        } else {
            email.setEnabled(false)
            if (authNeedsCloudPassword) {
                pendingEmailCode = currentCode
                password = input(getString(R.string.hint_cloud_password), true)
                password.setText(currentPassword)
                content.addView(spaced(password))
                content.addView(spaced(label(getString(R.string.auth_reset_cloud_password_help))))
            } else {
                emailCode = input(getString(R.string.hint_email_code), false)
                emailCode.setText(currentCode)
                content.addView(spaced(emailCode))
            }
            loginButton = primaryButton(getString(R.string.action_login), object : View.OnClickListener {
                override fun onClick(v: View?) {
                    authEmail()
                }
            })
            val back: ImageButton =
                headerIconButton(R.drawable.ic_back, getString(R.string.action_back), object : View.OnClickListener {
                    override fun onClick(v: View?) {
                        if (authNeedsCloudPassword) {
                            authNeedsCloudPassword = false
                        } else {
                            waitingEmailCode = false
                            pendingEmailCode = ""
                        }
                        showLogin()
                    }
                })
            val authRow: LinearLayout = mixedRow(back, loginButton, true)
            content.addView(spaced(authRow))
            if (!authNeedsCloudPassword) {
                resendEmailCodeButton = button(getString(R.string.action_send_again), object : View.OnClickListener {
                    override fun onClick(v: View?) {
                        requestEmailCode()
                    }
                })
                updateEmailCodeCooldown()
                content.addView(
                    spaced(
                        row(
                            resendEmailCodeButton
                        )
                    )
                )
            }
            if (authNeedsCloudPassword) {
                val resetSlider: PaymentSliderView =
                    paymentSlider(getString(R.string.reset_cloud_password_slide_hint), true)
                resetSlider.setContentDescription(getString(R.string.reset_cloud_password_slide_hint))
                resetSlider.setOnConfirmAction(object : Runnable {
                    override fun run() {
                        resetAuthCloudPassword()
                    }
                })
                content.addView(spaced(resetSlider), LinearLayout.LayoutParams(-1, dp(56)))
            }
        }
        content.addView(
            spaced(
                row(
                    button(getString(R.string.action_change_server), object : View.OnClickListener {
                        override fun onClick(v: View?) {
                            waitingEmailCode = false
                            authNeedsCloudPassword = false
                            pendingEmailCode = ""
                            showServer()
                        }
                    })
                )
            )
        )
    }

    private fun showChats() {
        page = Page.CHATS
        updateBottomNavSelection()
        replyToMessage = null
        if (bottomNav != null) bottomNav.setVisibility(View.VISIBLE)
        content.removeAllViews()
        content.setPadding(0, 0, 0, 0)
        chatRows = adapter()
        content.addView(chatsHeader())
        chatSearch = input(getString(R.string.action_search), false)
        chatSearch.setTextSize(14)
        chatSearch.setSingleLine(true)
        chatSearch.setPadding(dp(14), gap, dp(14), gap)
        chatSearch.setBackgroundDrawable(shape(surfaceHi, 0, dp(22)))
        chatSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                renderChatRows(if (value == null) "" else value.toString())
            }

            override fun afterTextChanged(value: Editable?) {
            }
        })
        val searchLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        searchLp.setMargins(pad, 0, pad, gap)
        content.addView(chatSearch, searchLp)
        val list: ListView = ListView(this)
        list.setBackgroundColor(bg)
        list.setCacheColorHint(bg)
        styleList(list, false)
        list.setDivider(ColorDrawable(border))
        list.setDividerHeight(dp(1))
        list.setAdapter(chatRows)
        list.setOnItemClickListener(object : AdapterView.OnItemClickListener {
            override fun onItemClick(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos < 0 || pos >= visibleChatData.size) return
                val chat: MST5.Chat = visibleChatData.get(pos)
                val chatPeer = resolvedPeerName(chat.peer, chat.id)
                openChatImmediately(chatPeer, chat.peer, chat.banned, chat.bannedByMe, chat.bannedMe, null)
            }
        })
        loadCachedChats()
        content.addView(list, fill())
        loadChats()
    }

    private fun chatsHeader(): LinearLayout {
        val header: LinearLayout = LinearLayout(this)
        header.setOrientation(LinearLayout.HORIZONTAL)
        header.setGravity(Gravity.CENTER_VERTICAL)
        header.setPadding(pad, pad, pad, gap)
        val heading: TextView = label(getString(R.string.nav_chats))
        heading.setTextSize(22)
        heading.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
        header.addView(heading, LinearLayout.LayoutParams(0, buttonMinHeight, 1f))
        val refresh: Button = button("↻", object : View.OnClickListener {
            override fun onClick(v: View?) {
                loadChats(v, false)
            }
        })
        refresh.setTextSize(18)
        refresh.setContentDescription(getString(R.string.action_refresh))
        header.addView(refresh, LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight))
        val add: Button = primaryButton("+", object : View.OnClickListener {
            override fun onClick(v: View?) {
                showAddChat()
            }
        })
        add.setTextSize(21)
        add.setContentDescription(getString(R.string.screen_add_chat))
        val addLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight)
        addLp.setMargins(gap, 0, 0, 0)
        header.addView(add, addLp)
        val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, 0, 0, gap / 2)
        header.setLayoutParams(lp)
        return header
    }

    private fun showAddChat() {
        showActionDialog(
            getString(R.string.add_chat_choose_title), arrayOf(
                getString(R.string.add_chat_private),
                getString(R.string.add_chat_group),
                getString(R.string.add_chat_channel)
            ), object : ChoiceHandler {
                override fun onChoice(which: Int) {
                    if (which == 0) showNewPrivateChatDialog()
                    else showNewRoomDialog(which == 2)
                }
            })
    }

    private fun showNewPrivateChatDialog() {
        val dialog: Dialog = Dialog(this)
        val box: LinearLayout = dialogBox()
        box.addView(title(getString(R.string.add_chat_private)), LinearLayout.LayoutParams(-1, -2))
        val loginField: EditText = input(getString(R.string.hint_username_or_id), false)
        box.addView(spaced(loginField))
        val add: Button = primaryButton(getString(R.string.action_add), object : View.OnClickListener {
            override fun onClick(v: View?) {
                val value: String = loginField.getText().toString().trim()
                if (value.length == 0) {
                    loginField.setError(getString(R.string.field_required))
                    return
                }
                dialog.dismiss()
                openChatIfExists(value, null, true)
            }
        })
        val cancel: Button = button(getString(R.string.action_cancel), object : View.OnClickListener {
            override fun onClick(v: View?) {
                dialog.dismiss()
            }
        })
        box.addView(row(cancel, add), LinearLayout.LayoutParams(-1, -2))
        setScrollableDialogContent(dialog, box)
        showStyledDialog(dialog)
    }

    private fun showNewRoomDialog(channel: Boolean) {
        val dialog: Dialog = Dialog(this)
        val box: LinearLayout = dialogBox()
        box.addView(
            title(getString(if (channel) R.string.add_chat_channel else R.string.add_chat_group)),
            LinearLayout.LayoutParams(-1, -2)
        )
        val titleField: EditText = input(getString(R.string.hint_room_title), false)
        val usernameField: EditText = input(getString(R.string.hint_channel_username), false)
        val membersField: EditText = input(getString(R.string.hint_room_members), false)
        box.addView(spaced(titleField))
        if (channel) box.addView(spaced(usernameField))
        box.addView(spaced(membersField))
        val create: Button = primaryButton(
            getString(if (channel) R.string.action_create_channel else R.string.action_create_group),
            object : View.OnClickListener {
                override fun onClick(v: View?) {
                    val titleValue: String = titleField.getText().toString().trim()
                    if (titleValue.length == 0) {
                        titleField.setError(getString(R.string.field_required))
                        return
                    }
                    dialog.dismiss()
                    createRoom(
                        channel,
                        titleValue,
                        if (channel) usernameField.getText().toString().trim() else "",
                        splitMembers(membersField.getText().toString())
                    )
                }
            })
        val cancel: Button = button(getString(R.string.action_cancel), object : View.OnClickListener {
            override fun onClick(v: View?) {
                dialog.dismiss()
            }
        })
        box.addView(row(cancel, create), LinearLayout.LayoutParams(-1, -2))
        setScrollableDialogContent(dialog, box)
        showStyledDialog(dialog)
    }

    private fun showChat() {
        val leavingCommentThread = page === Page.CHANNEL_COMMENTS
        page = Page.CHAT
        if (leavingCommentThread) replyToMessage = null
        if (currentPeer != null && !currentPeer.isEmpty()) cancelMessageNotification(currentPeer!!)
        if (bottomNav != null) bottomNav.setVisibility(View.GONE)
        content.removeAllViews()
        content.setPadding(0, 0, 0, 0)
        peer = input(getString(R.string.hint_username_or_id), false)
        peer.setText(currentPeer)
        text = input(getString(R.string.hint_message), false)
        text.setBackgroundDrawable(shape(surface, 0, dp(22)))
        text.setPadding(dp(14), buttonPadY, dp(14), buttonPadY)
        text.setSingleLine(false)
        text.setMinLines(1)
        text.setMaxLines(3)
        text.setImeOptions(EditorInfo.IME_ACTION_SEND)
        text.setOnEditorActionListener(object : TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView?, action: Int, e: KeyEvent?): Boolean {
                if (action == EditorInfo.IME_ACTION_SEND) {
                    send()
                    return true
                }

                return false
            }
        })

        messageRows = adapter()
        messageList = ListView(this)
        messageList.setBackgroundColor(bg)
        messageList.setCacheColorHint(bg)
        styleList(messageList, true)
        messageList.setAdapter(messageRows)
        messageList.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL)
        messageList.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
            }

            override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                if (historyLoaded && hasOlderMessages && firstVisibleItem == 0 && visibleItemCount > 0) {
                    loadOlderHistory()
                }
            }
        })

        callButton = headerIconButton(R.drawable.ic_call, getString(R.string.action_call), object : View.OnClickListener {
            override fun onClick(v: View?) {
                toggleVoice()
            }
        })
        callButton.setBackgroundDrawable(pressable(primary, blend(primary, Color.WHITE, 0.18f), 0, buttonRadius()))
        callButton.setColorFilter(onPrimary)
        updateCallButton()
        content.addView(chatHeader(), LinearLayout.LayoutParams(-1, -2))
        content.addView(messageList, fill())
        chatInputContainer = LinearLayout(this)
        chatInputContainer.setOrientation(LinearLayout.VERTICAL)
        content.addView(chatInputContainer, LinearLayout.LayoutParams(-1, -2))
        refreshChatInput()
    }

    private fun showChannelComments(post: MST5.Message?) {
        if (!currentPeerIsChannel() || currentPeerUser == null || !currentPeerUser!!.commentsEnabled || post == null) return
        currentCommentPost = post
        replyToMessage = null
        page = Page.CHANNEL_COMMENTS
        if (bottomNav != null) bottomNav.setVisibility(View.GONE)
        content.removeAllViews()
        text = input(getString(R.string.channel_comment_hint), false)
        text.setSingleLine(false)
        text.setMinLines(1)
        text.setMaxLines(3)
        text.setImeOptions(EditorInfo.IME_ACTION_SEND)
        text.setOnEditorActionListener(object : TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView?, action: Int, e: KeyEvent?): Boolean {
                if (action == EditorInfo.IME_ACTION_SEND) {
                    sendChannelComment()
                    return true
                }
                return false
            }
        })
        val header: LinearLayout = LinearLayout(this)
        header.setOrientation(LinearLayout.HORIZONTAL)
        header.setGravity(Gravity.CENTER_VERTICAL)
        val back: ImageButton =
            headerIconButton(R.drawable.ic_back, getString(R.string.action_back), object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showChat()
                    loadHistory()
                }
            })
        header.addView(back, LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight))
        val heading: TextView = title(getString(R.string.channel_comments))
        val headingLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        headingLp.setMargins(gap, 0, 0, 0)
        header.addView(heading, headingLp)
        content.addView(spaced(header))

        val original: LinearLayout = LinearLayout(this)
        original.setOrientation(LinearLayout.VERTICAL)
        original.setPadding(pad, gap, pad, gap)
        original.setBackgroundDrawable(shape(surfaceHi, 0, elementRadius()))
        val author: TextView = label(currentPeerUser!!.nick)
        author.setTextColor(muted)
        author.setTextSize(13)
        original.addView(author, LinearLayout.LayoutParams(-1, -2))
        val body: TextView = label(if (post.text == null) "" else post.text)
        body.setText(renderMarkdown(if (post.text == null) "" else post.text))
        body.setMovementMethod(LinkMovementMethod.getInstance())
        body.setTextColor(textColor)
        original.addView(body, LinearLayout.LayoutParams(-1, -2))
        content.addView(spaced(original))

        messageRows = adapter()
        messageList = ListView(this)
        messageList.setBackgroundColor(bg)
        messageList.setCacheColorHint(bg)
        styleList(messageList, true)
        messageList.setAdapter(messageRows)
        messageList.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL)
        messageList.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
            }

            override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                if (historyLoaded && hasOlderMessages && firstVisibleItem == 0 && visibleItemCount > 0) loadOlderChannelComments()
            }
        })
        content.addView(messageList, fill())
        commentInputContainer = LinearLayout(this)
        commentInputContainer.setOrientation(LinearLayout.VERTICAL)
        refreshCommentInput()
        content.addView(spaced(commentInputContainer))
        loadCachedChannelComments(post.id)
        loadChannelComments(post.id)
    }

    private fun commentMessageBar(): LinearLayout {
        val bar: LinearLayout = LinearLayout(this)
        bar.setOrientation(LinearLayout.HORIZONTAL)
        bar.setGravity(Gravity.BOTTOM)
        val inputLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        inputLp.setMargins(0, 0, gap, 0)
        bar.addView(text, inputLp)
        val send: ImageButton =
            inputIconButton(R.drawable.ic_send, getString(R.string.action_send), object : View.OnClickListener {
                override fun onClick(v: View?) {
                    sendChannelComment()
                }
            })
        bar.addView(send, LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight))
        return bar
    }

    private fun refreshCommentInput() {
        if (commentInputContainer == null) return
        commentInputContainer.removeAllViews()
        if (replyToMessage != null) commentInputContainer.addView(replyComposerView())
        commentInputContainer.addView(commentMessageBar())
    }

    private fun replyComposerView(): View {
        val box: LinearLayout = LinearLayout(this)
        box.setOrientation(LinearLayout.HORIZONTAL)
        box.setGravity(Gravity.CENTER_VERTICAL)
        box.setPadding(gap, gap, gap, gap)
        box.setBackgroundDrawable(shape(surfaceHi, 0, elementRadius()))
        val details: LinearLayout = LinearLayout(this)
        details.setOrientation(LinearLayout.VERTICAL)
        val heading: TextView = label(getString(R.string.replying_to, replyAuthor(replyToMessage)))
        heading.setTextColor(primary)
        heading.setTextSize(13)
        details.addView(heading, LinearLayout.LayoutParams(-1, -2))
        val preview: TextView = label(replySummary(replyToMessage))
        preview.setTextColor(muted)
        preview.setTextSize(14)
        preview.setSingleLine(true)
        preview.setEllipsize(TextUtils.TruncateAt.END)
        details.addView(preview, LinearLayout.LayoutParams(-1, -2))
        details.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                if (replyToMessage != null) focusMessage(replyToMessage!!.id)
            }
        })
        box.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        val cancel: Button = button("×", object : View.OnClickListener {
            override fun onClick(v: View?) {
                clearReply()
            }
        })
        cancel.setContentDescription(getString(R.string.action_cancel))
        val cancelLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight)
        cancelLp.setMargins(gap, 0, 0, 0)
        box.addView(cancel, cancelLp)
        val boxLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        boxLp.setMargins(0, 0, 0, gap / 2)
        box.setLayoutParams(boxLp)
        return box
    }

    private fun startReply(message: MST5.Message?) {
        if (message == null || message.id <= 0) return
        replyToMessage = message
        if (page === Page.CHANNEL_COMMENTS) refreshCommentInput()
        else refreshChatInput()
        if (text != null) {
            text.requestFocus()
            text.setSelection(text.length)
        }
    }

    private fun clearReply() {
        replyToMessage = null
        if (page === Page.CHANNEL_COMMENTS) refreshCommentInput()
        else if (page === Page.CHAT) refreshChatInput()
    }

    private fun replyAuthor(message: MST5.Message?): String? {
        val author: MST5.User? = messageAuthor(message)
        if (author == null) return getString(R.string.reply_to_message)
        val value = displayUser(author)
        return if (value.length == 0) getString(R.string.reply_to_message) else value
    }

    private fun messageAuthor(message: MST5.Message?): MST5.User {
        return MessageAuthorResolver.resolve(message, currentPeerUser)
            ?: MST5.User("", "", "", "", false, false, 0)
    }

    private fun replySummary(message: MST5.Message?): String? {
        if (message == null) return getString(R.string.reply_message_unavailable)
        var value: String
        if (message.text != null && message.text.trim().length > 0) {
            value = message.text.replace('\n', ' ').trim()
        } else if (message.media != null && !message.media.isEmpty()) {
            val first: MST5.FileInfo = message.media.get(0)
            value = if (first.name == null || first.name.length == 0)
                getString(R.string.file_fallback_name)
            else
                first.name
            if (message.media.size > 1) value += " +" + (message.media.size - 1)
        } else {
            value = ""
        }
        if (value.length == 0) value = getString(R.string.reply_message_unavailable)
        return if (value.length > 120) value.substring(0, 117) + "…" else value
    }

    private fun focusMessage(messageId: Long) {
        if (messageRows == null || messageList == null || messageId <= 0) return
        val position = messageRows!!.positionOfMessage(messageId)
        if (position >= 0) messageList.smoothScrollToPosition(position)
    }

    private fun sendChannelComment() {
        val value = if (text == null) "" else text.getText().toString().trim()
        if (value.length == 0 || currentCommentPost == null || ta == null) return
        val replyToMessageId: Long = if (replyToMessage == null) 0 else replyToMessage!!.id
        try {
            val entry: Entry = OutboxStore.enqueueComment(
                this,
                SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
                OutboxDispatcher.accountKey(this),
                currentPeer,
                currentCommentPost!!.id,
                value,
                replyToMessageId
            )
            addMessageRow(outboxMessage(entry), false)
            text.setText("")
            if (replyToMessageId > 0) clearReply()
            if (messageList != null) messageList.setSelection(messageRows!!.count - 1)
            dispatchOutbox(ta)
        } catch (error: Exception) {
            status.setText(errorText(error))
        }
    }

    private fun loadChannelComments(postId: Long) {
        val c: MST5? = ta
        val channel = currentPeer
        if (c == null || channel == null || channel.length == 0) return
        historyLoaded = false
        hasOlderMessages = false
        loadingOlderMessages = false
        run("channel_comments_history", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val comments: MST5.CommentPage =
                    c.getChannelComments(channel, postId, 0, ru.e6atb.chat.MainActivity.Companion.HISTORY_PAGE)
                val cachePeer: String? = OutboxStore.cachePeer(channel, postId)
                cacheSaveHistory(cachePeer, comments.messages)
                ui(object : Runnable {
                    override fun run() {
                        if (page !== Page.CHANNEL_COMMENTS || currentCommentPost == null || currentCommentPost!!.id !== postId) return
                        if (comments.peer != null) currentPeerUser = comments.peer
                        if (comments.post != null) currentCommentPost = comments.post
                        renderChannelComments(comments.messages, postId, false)
                    }
                })
            }
        })
    }

    private fun loadCachedChannelComments(postId: Long) {
        val channel = currentPeer
        val cachePeer: String? = OutboxStore.cachePeer(channel, postId)
        enqueueCache(object : Runnable {
            override fun run() {
                val cached: List<MST5.Message>? = ChatCache.loadHistory(
                    this@MainActivity,
                    SessionStore.server(this@MainActivity, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
                    myLogin,
                    cachePeer
                )
                ui(object : Runnable {
                    override fun run() {
                        if (page === Page.CHANNEL_COMMENTS && currentCommentPost != null && currentCommentPost!!.id === postId) renderChannelComments(
                            cached,
                            postId,
                            true
                        )
                    }
                })
            }
        })
    }

    private fun renderChannelComments(comments: List<MST5.Message>?, postId: Long, cached: Boolean) {
        if (messageRows == null || comments == null) return
        seenMessages.clear()
        oldestMessage = 0
        val rows: ArrayList<MessageRow> = ArrayList<MessageRow>()
        for (comment in comments) {
            if (comment != null && comment.commentPostId === postId && seenMessages.add(comment.id)) {
                if (oldestMessage == 0L || comment.id < oldestMessage) oldestMessage = comment.id
                rows.add(toMessageRow(comment))
            }
        }
        for (entry in OutboxStore.load(
            this,
            SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
            OutboxDispatcher.accountKey(this)
        )) {
            if (!currentPeer!!.equals(entry.peer) || entry.commentPostId !== postId) continue
            val pending: MST5.Message = outboxMessage(entry)
            if (seenMessages.add(pending.id)) rows.add(toMessageRow(pending))
        }
        messageRows!!.replaceRows(rows)
        historyLoaded = !cached
        hasOlderMessages = !cached && comments.size === ru.e6atb.chat.MainActivity.Companion.HISTORY_PAGE
        if (messageList != null && messageRows!!.count > 0) messageList.setSelection(messageRows!!.count - 1)
    }

    private fun loadOlderChannelComments() {
        val c: MST5? = ta
        if (c == null || currentCommentPost == null || loadingOlderMessages || oldestMessage <= 0) return
        val postId: Long = currentCommentPost!!.id
        val before = oldestMessage
        val channel = currentPeer
        loadingOlderMessages = true
        run("older_channel_comments", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val pageData: MST5.CommentPage =
                    c.getChannelComments(channel, postId, before, ru.e6atb.chat.MainActivity.Companion.HISTORY_PAGE)
                ui(object : Runnable {
                    override fun run() {
                        loadingOlderMessages = false
                        if (page !== Page.CHANNEL_COMMENTS || currentCommentPost == null || currentCommentPost!!.id !== postId) return
                        val rows: ArrayList<MessageRow> = ArrayList<MessageRow>()
                        for (comment in pageData.messages) if (seenMessages.add(comment.id)) {
                            oldestMessage = Math.min(oldestMessage, comment.id)
                            rows.add(toMessageRow(comment))
                        }
                        hasOlderMessages =
                            pageData.messages.size === ru.e6atb.chat.MainActivity.Companion.HISTORY_PAGE && !rows.isEmpty()
                        messageRows!!.insertRows(rows, 0)
                    }
                })
            }
        })
    }

    private fun showE2EFingerprint() {
        if (currentPeerIsRoom()) {
            status.setText(getString(R.string.status_e2e_not_available_for_rooms))
            return
        }
        val c: MST5? = ta
        val peerName = currentPeer
        if (c == null || peerName == null || peerName.length == 0) return
        run("e2e_fingerprint", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val fingerprint: String? = c.e2eFingerprint(peerName)
                ui(object : Runnable {
                    override fun run() {
                        showInfoDialog(
                            getString(R.string.dialog_e2e_title, peerName),
                            getString(R.string.dialog_e2e_body, fingerprint)
                        )
                    }
                })
            }
        })
    }

    private fun openChatIfExists(peerName: String?) {
        openChatIfExists(peerName, null, false)
    }

    private fun openChatIfExists(peerName: String?, actionButton: View?, primaryStyle: Boolean) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        openChatImmediately(peerName, null, false, false, false, null)
    }

    private fun openChatImmediately(
        peerName: String?,
        knownPeer: MST5.User?,
        banned: Boolean,
        bannedByMe: Boolean,
        bannedMe: Boolean,
        afterServerLoad: Runnable?
    ) {
        val normalized = if (peerName == null) "" else peerName.trim()
        if (normalized.length == 0) return
        ++chatOpenGeneration
        ++historyRequestGeneration
        currentPeer = normalized
        currentPeerUser = knownPeer
        currentPeerBanned = banned
        currentPeerBannedByMe = bannedByMe
        currentPeerBannedMe = bannedMe
        currentCommentPost = null
        replyToMessage = null
        oldestMessage = 0
        historyLoaded = false
        hasOlderMessages = false
        loadingOlderMessages = false
        showChat()
        loadHistory(afterServerLoad)
    }

    private fun resolvedPeerName(user: MST5.User?, fallback: String?): String {
        if (user != null && user.login != null && user.login.length > 0) return user.login
        if (user != null && user.id != null && user.id.length > 0) return user.id
        return if (fallback == null) "" else fallback
    }

    private fun createRoom(channel: Boolean, titleValue: String, usernameValue: String, members: ArrayList<String>?) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        if (titleValue.length == 0) return
        if (channel && usernameValue.length > 0) {
            showUsernameReservationPaymentSheet(
                usernameValue,
                getString(R.string.username_reservation_payment_details_channel, titleValue),
                object : Runnable {
                    override fun run() {
                        createRoomConfirmed(true, titleValue, usernameValue, members)
                    }
                }
            )
            return
        }
        createRoomConfirmed(channel, titleValue, usernameValue, members, null, false)
    }

    private fun createRoomConfirmed(
        channel: Boolean,
        titleValue: String?,
        usernameValue: String?,
        members: ArrayList<String>?
    ) {
        createRoomConfirmed(channel, titleValue, usernameValue, members, null, false)
    }

    private fun createRoomConfirmed(
        channel: Boolean,
        titleValue: String?,
        usernameValue: String?,
        members: ArrayList<String>?,
        actionButton: View?,
        primaryStyle: Boolean
    ) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        status.setText(getString(if (channel) R.string.status_creating_channel else R.string.status_creating_group))
        runButtonTask("create_room", actionButton, primaryStyle, object : Task {
            @Throws(Exception::class)
            override fun run() {
                val chat: MST5.Chat =
                    if (channel) c.createChannel(titleValue.orEmpty(), usernameValue.orEmpty(), members?.filterNotNull()) else c.createGroup(
                        titleValue.orEmpty(),
                        members?.filterNotNull()
                    )
                ui(object : Runnable {
                    override fun run() {
                        status.setText(getString(if (channel) R.string.status_channel_created else R.string.status_group_created))
                        openChatImmediately(resolvedPeerName(chat.peer, chat.id), chat.peer, false, false, false, null)
                    }
                })
            }
        })
    }

    private fun showUsernameReservationPaymentSheet(usernameValue: String?, detailText: String?, onConfirm: Runnable?) {
        val dialog: Dialog = Dialog(this)
        val box: LinearLayout = dialogBox()

        val title: TextView = title(
            getString(
                R.string.username_reservation_payment_title,
                usernameValue,
                ru.e6atb.chat.MainActivity.Companion.USERNAME_RESERVATION_FEE_DSR
            )
        )
        box.addView(title, LinearLayout.LayoutParams(-1, -2))

        val details: TextView = label(if (detailText == null) "" else detailText)
        details.setTextColor(muted)
        val detailsLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        detailsLp.setMargins(0, 0, 0, gap)
        box.addView(details, detailsLp)

        val slider: PaymentSliderView = paymentSlider(getString(R.string.payment_slide_hint))
        slider.setContentDescription(getString(R.string.payment_slide_hint))
        slider.setOnConfirmAction(object : Runnable {
            override fun run() {
                dialog.dismiss()
                if (onConfirm != null) onConfirm.run()
            }
        })
        box.addView(slider, LinearLayout.LayoutParams(-1, dp(56)))

        val cancel: Button = button(getString(R.string.action_cancel), object : View.OnClickListener {
            override fun onClick(v: View?) {
                dialog.dismiss()
            }
        })
        val cancelLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        cancelLp.setMargins(0, gap, 0, 0)
        box.addView(cancel, cancelLp)

        setScrollableDialogContent(dialog, box)
        showStyledDialog(dialog)
    }

    private fun splitMembers(raw: String?): ArrayList<String> {
        val out: ArrayList<String> = ArrayList<String>()
        if (raw == null) return out
        val parts: List<String> = raw.split(",")
        for (part in parts) {
            val value: String = part.trim()
            if (value.length > 0) out.add(value)
        }
        return out
    }

    private fun refreshChatInput() {
        if (chatInputContainer == null) return
        chatInputContainer.removeAllViews()
        if (currentPeerBanned) {
            chatInputContainer.addView(bannedChatBlock())
            return
        }
        if (this.isEmptyBotDialog) {
            chatInputContainer.addView(startBotButton())
            return
        }
        if (currentPeerIsRoom() && !currentPeerCanWrite()) {
            chatInputContainer.addView(readOnlyRoomBlock())
            return
        }
        if (replyToMessage != null) chatInputContainer.addView(replyComposerView())
        loadBotCommandsIfNeeded()
        chatInputContainer.addView(messageBar())
    }

    private fun loadBotCommandsIfNeeded() {
        if (!currentPeerIsBot() || currentPeer == null || currentPeer.length == 0) return
        val peerName = currentPeer
        if (peerName!!.equals(botCommandsPeer)) return
        botCommandsPeer = peerName
        botCommands.clear()
        val client: MST5? = ta
        if (client == null) return
        run("bot_commands", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val commands: List<MST5.BotCommand> = client.getBotCommands(peerName)
                ui(object : Runnable {
                    override fun run() {
                        if (!peerName!!.equals(currentPeer) || page !== Page.CHAT) return
                        botCommands.clear()
                        botCommands.addAll(commands)
                        refreshChatInput()
                    }
                })
            }
        })
    }

    private fun showBotCommandsMenu() {
        if (botCommands.isEmpty() || currentPeer == null || currentPeer.length == 0) return
        val dialog: Dialog = Dialog(this)
        val box: LinearLayout = dialogBox()
        box.addView(title("Commands"), LinearLayout.LayoutParams(-1, -2))
        for (command in botCommands) {
            val label =
                "/" + command.command + (if (command.description.length == 0) "" else " — " + command.description)
            val choice: Button = sheetActionButton(label, object : View.OnClickListener {
                override fun onClick(v: View?) {
                    dialog.dismiss()
                    sendChatMessage(currentPeer, "/" + command.command, true)
                }
            }, false)
            val choiceLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
            choiceLp.setMargins(0, gap / 2, 0, 0)
            box.addView(choice, choiceLp)
        }
        setScrollableDialogContent(dialog, box)
        showStyledDialog(dialog)
    }

    private fun showStickerPicker() {
        val client: MST5? = ta
        if (client == null || currentPeer == null || currentPeer.length == 0) return
        if (!stickerPacksLoaded) {
            if (stickerPacksLoading) return
            stickerPacksLoading = true
            run("sticker_packs", object : Task {
                @Throws(Exception::class)
                override fun run() {
                val packs: List<MST5.StickerPack> = client.stickerPacks
                    ui(object : Runnable {
                        override fun run() {
                            stickerPacksLoading = false
                            stickerPacksLoaded = true
                            stickerPacks.clear()
                            stickerPacks.addAll(packs)
                            showStickerPicker()
                        }
                    })
                }
            })
            return
        }
        if (stickerPacks.isEmpty()) {
            val hint: TextView =
                label("No sticker packs are available yet. Create one with stickerbot: choose an ID, title and DSR price, then send PNG or WebP stickers to the bot.")
            hint.setTextColor(muted)
            hint.setTextSize(14)
            showContentDialog("Stickers", hint, "Open stickerbot", object : Runnable {
                override fun run() {
                    openChatIfExists("stickerbot", null, true)
                }
            }, getString(R.string.action_cancel))
            return
        }
        val dialog: Dialog = Dialog(this)
        val box: LinearLayout = dialogBox()
        box.addView(title("Stickers"), LinearLayout.LayoutParams(-1, -2))
        for (pack in stickerPacks) {
            if (!pack.owned) {
                val purchase: Button = button(
                    "Buy " + pack.title + " · " + pack.priceDsr + " DSR",
                    object : View.OnClickListener {
                        override fun onClick(view: View?) {
                            dialog.dismiss()
                            purchaseStickerPack(pack.id)
                        }
                    }
                )
                val purchaseLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
                purchaseLp.setMargins(0, gap / 2, 0, 0)
                box.addView(purchase, purchaseLp)
                continue
            }
            for (sticker in pack.stickers) {
                val choice: Button = button(
                    pack.title + " · " + (if (sticker.name.length == 0) "Sticker" else sticker.name),
                    object : View.OnClickListener {
                        override fun onClick(view: View?) {
                            dialog.dismiss()
                            sendSticker(pack.id, sticker.id)
                        }
                    }
                )
                val choiceLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
                choiceLp.setMargins(0, gap / 2, 0, 0)
                box.addView(choice, choiceLp)
            }
        }
        setScrollableDialogContent(dialog, box)
        showStyledDialog(dialog)
    }

    private fun purchaseStickerPack(packId: String?) {
        val client: MST5? = ta
        if (client == null) return
        run("sticker_purchase", object : Task {
            @Throws(Exception::class)
            override fun run() {
                client.purchaseStickerPack(packId.orEmpty())
                ui(object : Runnable {
                    override fun run() {
                        stickerPacksLoaded = false
                        showStickerPicker()
                    }
                })
            }
        })
    }

    private fun sendSticker(packId: String?, fileId: String?) {
        val client: MST5? = ta
        val peerName = currentPeer
        if (client == null || peerName == null || peerName.length == 0) return
        run("sticker_send", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val message: MST5.Message? = client.sendSticker(
                    peerName, packId.orEmpty(), fileId.orEmpty(), UUID.randomUUID().toString()
                )
                ui(object : Runnable {
                    override fun run() {
                        if (!peerName.equals(currentPeer) || page !== Page.CHAT) return
                        addMessageRow(message, false)
                        if (messageList != null) messageList.setSelection(messageRows!!.count - 1)
                    }
                })
            }
        })
    }

    private fun currentPeerIsRoom(): Boolean {
        return currentPeerUser != null && currentPeerUser!!.roomKind != null && currentPeerUser!!.roomKind.length > 0 && currentPeer != null && currentPeer!!.equals(
            resolvedPeerName(currentPeerUser, currentPeer)
        )
    }

    private fun currentPeerIsChannel(): Boolean {
        return currentPeerIsRoom() && "channel".equals(currentPeerUser!!.roomKind)
    }

    private fun currentPeerIsGroup(): Boolean {
        return currentPeerIsRoom() && "group".equals(currentPeerUser!!.roomKind)
    }

    private fun currentRoomChatId(): String {
        return if (currentPeerUser == null || !currentPeerIsRoom()) "" else "chat:" + currentPeerUser!!.id
    }

    private fun currentPeerE2EEnabled(): Boolean {
        if (!currentPeerIsRoom()) return false
        val chatId = currentRoomChatId()
        return chatId.isNotEmpty() && SessionStore.chatE2EEnabled(
            this,
            SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
            myLogin,
            chatId
        )
    }

    private fun currentPeerCanWrite(): Boolean {
        if (!currentPeerIsRoom()) return true
        if (!currentPeerIsChannel()) return true
        return currentPeerCanManageRoom()
    }

    private fun currentPeerCanManageRoom(): Boolean {
        return currentPeerIsRoom()
                && (currentPeerUser!!.canManage
                || (myID != null && myID.length > 0 && myID!!.equals(currentPeerUser!!.ownerId)))
    }

    private val isEmptyBotDialog: Boolean
        get() = currentPeerIsBot()
                && historyLoaded
                && messageRows != null && messageRows!!.count == 0

    private fun startBotButton(): Button {
        return primaryButton("/start", object : View.OnClickListener {
            override fun onClick(v: View?) {
                sendBotStart(v)
            }
        })
    }

    private fun showWallet() {
        page = Page.WALLET
        updateBottomNavSelection()
        if (bottomNav != null) bottomNav.setVisibility(View.VISIBLE)
        content.removeAllViews()
        content.setPadding(0, 0, 0, 0)

        val scroll: ScrollView = pageScrollView()

        val wallet: LinearLayout = LinearLayout(this)
        wallet.setOrientation(LinearLayout.VERTICAL)
        wallet.setPadding(pad, pad, pad, dp(28))

        val heading: TextView = label(getString(R.string.wallet_title))
        heading.setTextSize(22)
        heading.setTextColor(textColor)
        heading.setPadding(0, 0, 0, dp(14))
        wallet.addView(heading, LinearLayout.LayoutParams(-1, -2))

        val asset: FrameLayout = FrameLayout(this)
        asset.setPadding(dp(18), dp(18), dp(18), dp(18))
        asset.setBackgroundDrawable(shape(primary, 0, dp(18)))
        val texture: ImageView = ImageView(this)
        texture.setImageResource(R.drawable.ic_dastars)
        texture.setColorFilter(Color.WHITE)
        texture.setAlpha(0.14f)
        val textureLp: FrameLayout.LayoutParams = FrameLayout.LayoutParams(dp(126), dp(126), Gravity.RIGHT or Gravity.TOP)
        textureLp.setMargins(0, dp(-28), dp(-30), 0)
        asset.addView(texture, textureLp)
        val info: LinearLayout = LinearLayout(this)
        info.setOrientation(LinearLayout.VERTICAL)
        val name: TextView = label("Balance")
        name.setTextSize(13)
        name.setTextColor(blend(Color.WHITE, primary, 0.15f))
        info.addView(name, LinearLayout.LayoutParams(-1, -2))
        val amountLine: LinearLayout = LinearLayout(this)
        amountLine.setGravity(Gravity.CENTER_VERTICAL)
        val icon: ImageView = ImageView(this)
        icon.setImageResource(R.drawable.ic_dastars)
        icon.setColorFilter(Color.WHITE)
        amountLine.addView(icon, LinearLayout.LayoutParams(dp(22), dp(22)))
        val balance: TextView = label("0")
        balance.setTextSize(28)
        balance.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
        balance.setTextColor(Color.WHITE)
        val balanceLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-2, -2)
        balanceLp.setMargins(gap, 0, gap, 0)
        amountLine.addView(balance, balanceLp)
        val code: TextView = label("DSR")
        code.setTextSize(15)
        code.setTextColor(blend(Color.WHITE, primary, 0.15f))
        amountLine.addView(code, LinearLayout.LayoutParams(-2, -2))
        walletBalanceView = balance
        walletCodeView = code
        info.addView(amountLine, LinearLayout.LayoutParams(-1, -2))
        val estimate: TextView = label("DaStars")
        estimate.setTextSize(13)
        estimate.setTextColor(blend(Color.WHITE, primary, 0.26f))
        info.addView(estimate, LinearLayout.LayoutParams(-1, -2))
        asset.addView(info, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
        wallet.addView(asset, LinearLayout.LayoutParams(-1, -2))

        val quickActions: LinearLayout = LinearLayout(this)
        quickActions.setGravity(Gravity.CENTER)
        quickActions.setPadding(0, pad, 0, gap)
        quickActions.addView(walletQuickAction("+", getString(R.string.wallet_buy_dastars), object : View.OnClickListener {
            override fun onClick(v: View?) {
                openChatIfExists("dastarsbot", v, true)
            }
        }), LinearLayout.LayoutParams(0, -2, 1f))
        quickActions.addView(walletQuickAction("↗", getString(R.string.wallet_send_title), object : View.OnClickListener {
            override fun onClick(v: View?) {
                showDastarsTransferDialog("")
            }
        }), LinearLayout.LayoutParams(0, -2, 1f))
        quickActions.addView(
            walletQuickAction(
                "↓",
                getString(R.string.wallet_receive_title),
                object : View.OnClickListener {
                    override fun onClick(v: View?) {
                        if (walletReceiveView != null && walletReceiveView.getText().length > 0) {
                            copyToClipboard("dastars", walletReceiveView.getText().toString())
                        }
                    }
                }), LinearLayout.LayoutParams(0, -2, 1f)
        )
        quickActions.addView(
            walletQuickAction(
                "↺",
                getString(R.string.wallet_payment_history),
                object : View.OnClickListener {
                    override fun onClick(v: View?) {
                        loadWalletHistory(v, false)
                    }
                }), LinearLayout.LayoutParams(0, -2, 1f)
        )
        wallet.addView(quickActions, LinearLayout.LayoutParams(-1, -2))
        val historyHeader: LinearLayout = LinearLayout(this)
        historyHeader.setGravity(Gravity.CENTER_VERTICAL)
        val historyTitle: TextView = label(getString(R.string.wallet_payment_history))
        historyTitle.setTextColor(textColor)
        historyTitle.setTextSize(16)
        historyHeader.addView(historyTitle, LinearLayout.LayoutParams(0, -2, 1f))
        val showAll: Button = sheetActionButton(getString(R.string.action_refresh), object : View.OnClickListener {
            override fun onClick(v: View?) {
                loadWalletHistory(v, false)
            }
        }, false)
        historyHeader.addView(showAll, LinearLayout.LayoutParams(-2, dp(40)))
        wallet.addView(historyHeader, LinearLayout.LayoutParams(-1, -2))

        walletRecentView = LinearLayout(this)
        walletRecentView.setOrientation(LinearLayout.VERTICAL)
        walletRecentView.setPadding(0, gap, 0, gap)
        walletRecentView.setBackgroundDrawable(shape(Color.TRANSPARENT, 0, 0))
        walletRecentView.addView(walletHistoryRow(getString(R.string.loading_short), muted))
        wallet.addView(walletRecentView, LinearLayout.LayoutParams(-1, -2))

        // Keep the DSR address in memory for the Receive quick action.  The former
        // receive and transfer forms duplicated the quick actions and made the page
        // read like two wallets.
        walletReceiveView = label("")
        walletInstructionView = label("")

        scroll.addView(wallet, LinearLayout.LayoutParams(-1, -2))
        content.addView(scroll, fill())
        loadWallet()
    }

    private fun walletQuickAction(glyph: String?, caption: String?, listener: View.OnClickListener?): View {
        val action: LinearLayout = LinearLayout(this)
        action.setOrientation(LinearLayout.VERTICAL)
        action.setGravity(Gravity.CENTER)
        action.setPadding(gap / 2, 0, gap / 2, 0)
        action.setOnClickListener(listener)
        val icon: TextView = TextView(this)
        icon.setText(glyph)
        icon.setTextColor(primary)
        icon.setTextSize(21)
        icon.setGravity(Gravity.CENTER)
        icon.setBackgroundDrawable(pressable(surface, surfaceHi, 0, dp(14)))
        action.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
        val text: TextView = label(caption)
        text.setTextColor(muted)
        text.setTextSize(11)
        text.setGravity(Gravity.CENTER)
        text.setSingleLine(true)
        text.setEllipsize(TextUtils.TruncateAt.END)
        val textLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        textLp.setMargins(0, gap / 2, 0, 0)
        action.addView(text, textLp)
        return action
    }

    private fun loadWallet() {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        run("wallet", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val info: MST5.WalletInfo? = c.wallet
                var loadedRecent: List<MST5.WalletTransaction>?
                try {
                    loadedRecent = c.getWalletHistory(3)
                } catch (ignored: Exception) {
                    loadedRecent = ArrayList<MST5.WalletTransaction>()
                }
                val recent: List<MST5.WalletTransaction>? = loadedRecent
                ui(object : Runnable {
                    override fun run() {
                        if (page !== Page.WALLET) return
                        renderWallet(info, recent)
                    }
                })
            }
        })
    }

    private fun renderWallet(info: MST5.WalletInfo?) {
        renderWallet(info, null)
    }

    private fun renderWallet(info: MST5.WalletInfo?, recent: List<MST5.WalletTransaction>?) {
        if (info != null) {
            setCachedWalletInfo(info)
            if (walletBalanceView != null) walletBalanceView.setText(java.lang.String.valueOf(info.balance))
            if (walletCodeView != null) walletCodeView.setText(if (info.code == null || info.code.length == 0) "DSR" else info.code)
            if (walletReceiveView != null) walletReceiveView.setText(if (info.receiveCode == null) "" else info.receiveCode)
            if (walletInstructionView != null) walletInstructionView.setText(if (info.instruction == null) "" else info.instruction)
        }
        if (recent != null && walletRecentView != null) renderWalletRecent(recent, if (info == null) 0 else info.userId)
        status.setText(getString(R.string.status_wallet_updated))
    }

    private fun renderWalletRecent(recent: List<MST5.WalletTransaction>?, ownId: Long) {
        if (walletRecentView == null) return
        walletRecentView.removeAllViews()
        if (recent == null || recent.isEmpty()) {
            walletRecentView.addView(walletHistoryRow(getString(R.string.wallet_history_empty), muted))
            return
        }
        for (index in 0..<recent.size) {
            val tx: MST5.WalletTransaction = recent.get(index)
            if (index > 0) walletRecentView.addView(listDivider())
            val incoming = tx.toUserId === ownId
            val item: TextView = walletHistoryRow(
                formatWalletHistoryRow(tx, incoming),
                if (incoming) success else textColor,
                object : View.OnClickListener {
                    override fun onClick(v: View?) {
                        showWalletTransactionDetails(tx)
                    }
                })
            walletRecentView.addView(item)
        }
    }

    private fun showWalletHistory() {
        page = Page.WALLET_HISTORY
        if (bottomNav != null) bottomNav.setVisibility(View.VISIBLE)
        content.removeAllViews()
        content.setPadding(0, 0, 0, 0)

        val scroll: ScrollView = pageScrollView()
        val history: LinearLayout = LinearLayout(this)
        history.setOrientation(LinearLayout.VERTICAL)
        history.setPadding(0, 0, 0, gap)
        history.addView(spaced(title(getString(R.string.wallet_payment_history))))
        val back: ImageButton =
            headerIconButton(R.drawable.ic_back, getString(R.string.action_back), object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showWallet()
                }
            })
        history.addView(
            spaced(
                mixedRow(
                    back,
                    button(getString(R.string.action_refresh), object : View.OnClickListener {
                        override fun onClick(v: View?) {
                            loadWalletHistory(v, false)
                        }
                    }),
                    true
                )
            )
        )

        walletHistoryView = LinearLayout(this)
        walletHistoryView.setOrientation(LinearLayout.VERTICAL)
        walletHistoryView.addView(walletHistoryRow(getString(R.string.loading), muted))
        history.addView(spaced(walletHistoryView))

        scroll.addView(history, LinearLayout.LayoutParams(-1, -2))
        content.addView(scroll, fill())
        loadWalletHistory()
    }

    private fun loadWalletHistory(actionButton: View? = null, primaryStyle: Boolean = false) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        runButtonTask("wallet_history", actionButton, primaryStyle, object : Task {
            @Throws(Exception::class)
            override fun run() {
                val info: MST5.WalletInfo? = c.wallet
                val history: List<MST5.WalletTransaction>? = c.getWalletHistory(50)
                ui(object : Runnable {
                    override fun run() {
                        if (page !== Page.WALLET && page !== Page.WALLET_HISTORY) return
                        renderWalletHistory(info, history)
                    }
                })
            }
        })
    }

    private fun renderWalletHistory(info: MST5.WalletInfo?, history: List<MST5.WalletTransaction>?) {
        if (info != null) setCachedWalletInfo(info)
        val target: LinearLayout? = if (page === Page.WALLET) walletRecentView else walletHistoryView
        if (target == null) return
        target.removeAllViews()
        if (history == null || history.isEmpty()) {
            target.addView(walletHistoryRow(getString(R.string.wallet_history_empty), muted))
            return
        }
        val myID: Long = if (info == null) 0 else info.userId
        for (index in 0..<history.size) {
            val tx: MST5.WalletTransaction = history.get(index)
            if (index > 0) target.addView(listDivider())
            val incoming = tx.toUserId === myID
            val row: TextView = walletHistoryRow(
                formatWalletHistoryRow(tx, incoming),
                if (incoming) blend(primary, Color.WHITE, 0.18f) else textColor,
                object : View.OnClickListener {
                    override fun onClick(v: View?) {
                        showWalletTransactionDetails(tx)
                    }
                })
            target.addView(row)
        }
        status.setText(getString(R.string.status_wallet_history_updated))
    }

    private fun formatWalletHistoryRow(tx: MST5.WalletTransaction, incoming: Boolean): String? {
        val sign = if (incoming) "+" else "-"
        val peerName = walletPartyLabel(
            if (incoming) tx.fromUserId else tx.toUserId,
            if (incoming) tx.fromNick else tx.toNick,
            if (incoming) tx.fromLogin else tx.toLogin
        )
        val direction: String? = if (incoming)
            getString(R.string.wallet_history_from)
        else
            getString(R.string.wallet_history_to)
        var text: String? = getString(
            R.string.wallet_history_row,
            sign,
            tx.amount,
            "DSR",
            direction,
            peerName,
            formatMessageDateTime(tx.date)
        )
        if (tx.comment != null && tx.comment.length > 0) text += "  " + tx.comment
        return text
    }

    private fun setCachedWalletInfo(info: MST5.WalletInfo?) {
        if (info == null) return
        hasWalletBalance = true
        walletBalance = info.balance
        walletCode = if (info.code == null || info.code.length == 0) "DSR" else info.code
    }

    private fun walletBalanceLabel(): String {
        return if (hasWalletBalance)
            getString(R.string.wallet_balance_format, walletBalance, walletCode)
        else
            getString(R.string.wallet_balance_loading)
    }

    private fun refreshWalletBalanceLabel(balanceView: TextView?) {
        if (balanceView == null) return
        val c: MST5? = ta
        if (c == null) {
            balanceView.setText(getString(R.string.wallet_balance_sign_in))
            return
        }
        run("wallet", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val info: MST5.WalletInfo? = c.wallet
                ui(object : Runnable {
                    override fun run() {
                        setCachedWalletInfo(info)
                        balanceView.setText(walletBalanceLabel())
                    }
                })
            }
        })
    }

    private fun walletPartyLabel(userId: Long, nick: String?, fallbackLogin: String?): String? {
        if (nick != null && nick.length > 0) {
            return "@" + nick
        }
        if (fallbackLogin != null && fallbackLogin.length > 0) {
            return "@" + fallbackLogin
        }
        if (userId > 0) {
            return formatPublicUserID(userId)
        }
        return ""
    }

    private fun walletHistoryRow(value: String?, color: Int): TextView {
        return walletHistoryRow(value, color, null)
    }

    private fun walletHistoryRow(value: String?, color: Int, listener: View.OnClickListener?): TextView {
        val row: TextView = label(value)
        row.setTextColor(color)
        row.setTextSize(14)
        row.setPadding(dp(4), dp(12), dp(4), dp(12))
        row.setBackgroundDrawable(
            if (listener == null) shape(Color.TRANSPARENT, 0, 0) else pressable(
                Color.TRANSPARENT,
                surfaceHi,
                0,
                dp(10)
            )
        )
        if (listener != null) row.setOnClickListener(listener)
        val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        row.setLayoutParams(lp)
        return row
    }

    private fun listDivider(): View {
        val divider: View = View(this)
        divider.setBackgroundColor(border)
        val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, Math.max(1, dp(1)))
        lp.setMargins(0, 0, 0, 0)
        divider.setLayoutParams(lp)
        return divider
    }

    private fun showWalletTransactionDetails(tx: MST5.WalletTransaction?) {
        if (tx == null) return
        val box: LinearLayout = LinearLayout(this)
        box.setOrientation(LinearLayout.VERTICAL)
        box.setPadding(0, gap, 0, 0)
        if (tx.id > 0) box.addView(
            spaced(
                systemDetailRow(
                    getString(R.string.system_detail_transaction),
                    formatTransactionID(tx.id)
                )
            )
        )
        box.addView(
            spaced(
                systemDetailRow(
                    getString(R.string.system_detail_type),
                    getString(R.string.system_type_wallet_transfer)
                )
            )
        )
        if (tx.date > 0) box.addView(
            spaced(
                systemDetailRow(
                    getString(R.string.system_detail_time),
                    formatMessageDateTime(tx.date)
                )
            )
        )
        box.addView(spaced(systemDetailRow(getString(R.string.system_detail_amount), "${tx.amount} DSR")))
        box.addView(
            spaced(
                systemDetailRow(
                    getString(R.string.system_detail_from),
                    walletPartyLabel(tx.fromUserId, tx.fromNick, tx.fromLogin)
                )
            )
        )
        box.addView(
            spaced(
                systemDetailRow(
                    getString(R.string.system_detail_to),
                    walletPartyLabel(tx.toUserId, tx.toNick, tx.toLogin)
                )
            )
        )
        if (tx.fromUserId > 0) box.addView(
            spaced(
                systemDetailRow(
                    getString(R.string.system_detail_from_id),
                    formatPublicUserID(tx.fromUserId)
                )
            )
        )
        if (tx.toUserId > 0) box.addView(
            spaced(
                systemDetailRow(
                    getString(R.string.system_detail_to_id),
                    formatPublicUserID(tx.toUserId)
                )
            )
        )
        if (tx.comment != null && tx.comment.length > 0) {
            box.addView(spaced(systemDetailRow(getString(R.string.system_detail_comment), tx.comment)))
        }
        showContentDialog(getString(R.string.system_details_title), box, getString(R.string.action_close), null, null)
    }

    private fun sendDastars(actionButton: View? = null) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        val to = if (walletTo == null) "" else walletTo.getText().toString().trim()
        val rawAmount = if (walletAmount == null) "" else walletAmount.getText().toString().trim()
        val comment: String? = if (walletComment == null) "" else walletComment.getText().toString().trim()
        if (to.length == 0 || rawAmount.length == 0) return
        val amount: Long
        try {
            amount = java.lang.Long.parseLong(rawAmount)
        } catch (e: NumberFormatException) {
            status.setText(getString(R.string.status_bad_dsr_amount))
            return
        }
        if (amount <= 0) {
            status.setText(getString(R.string.status_bad_dsr_amount))
            return
        }
        runButtonTask("wallet_send", actionButton, true, object : Task {
            @Throws(Exception::class)
            override fun run() {
                c.sendDastars(to.orEmpty(), amount, comment.orEmpty())
                val info: MST5.WalletInfo? = c.wallet
                ui(object : Runnable {
                    override fun run() {
                        if (page !== Page.WALLET) return
                        if (walletAmount != null) walletAmount.setText("")
                        if (walletComment != null) walletComment.setText("")
                        renderWallet(info)
                        status.setText(getString(R.string.status_dsr_sent))
                    }
                })
            }
        })
    }

    private fun showNodeStatus() {
        page = Page.NODES
        updateBottomNavSelection()
        if (bottomNav != null) bottomNav.setVisibility(View.VISIBLE)
        content.removeAllViews()

        val scroll: ScrollView = pageScrollView()

        val nodes: LinearLayout = LinearLayout(this)
        nodes.setOrientation(LinearLayout.VERTICAL)
        nodes.setPadding(pad, pad, pad, dp(28))
        nodes.addView(spaced(title(getString(R.string.nodes_title))))

        nodeStatusListView = LinearLayout(this)
        nodeStatusListView.setOrientation(LinearLayout.VERTICAL)
        nodeStatusListView.addView(
            nodeStatusRow(
                getString(R.string.node_main),
                nodeStatusText("loading"),
                -1,
                -1,
                muted
            )
        )
        nodeStatusListView.addView(
            nodeStatusRow(
                getString(R.string.node_calls),
                nodeStatusText("loading"),
                -1,
                -1,
                muted
            )
        )
        nodeStatusListView.addView(
            nodeStatusRow(
                getString(R.string.node_media),
                nodeStatusText("loading"),
                -1,
                -1,
                muted
            )
        )
        nodeStatusListView.addView(
            nodeStatusRow(
                getString(R.string.node_wallet),
                nodeStatusText("loading"),
                -1,
                -1,
                muted
            )
        )
        nodeStatusListView.addView(
            nodeStatusRow(
                getString(R.string.node_e2e_keys),
                nodeStatusText("loading"),
                -1,
                -1,
                muted
            )
        )
        nodes.addView(spaced(nodeStatusListView))

        nodes.addView(
            spaced(
                row(
                    button(getString(R.string.action_refresh), object : View.OnClickListener {
                        override fun onClick(v: View?) {
                            loadNodeStatus(v, false)
                        }
                    })
                )
            )
        )

        scroll.addView(nodes, LinearLayout.LayoutParams(-1, -2))
        content.addView(scroll, fill())
        loadNodeStatus()
    }

    private fun loadNodeStatus(actionButton: View? = null, primaryStyle: Boolean = false) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        runButtonTask("nodes", actionButton, primaryStyle, object : Task {
            @Throws(Exception::class)
            override fun run() {
                val nodes: List<MST5.NodeStatus>? = c.nodeStatuses
                val e2e: MST5.NodeStatus = e2eKeyStatus(c)
                ui(object : Runnable {
                    override fun run() {
                        if (page !== Page.NODES) return
                        renderNodeStatus(nodes, e2e)
                    }
                })
            }
        })
    }

    private fun renderNodeStatus(nodes: List<MST5.NodeStatus>?, e2e: MST5.NodeStatus?) {
        if (nodeStatusListView == null || nodes == null) return
        nodeStatusListView.removeAllViews()
        nodeStatusListView.addView(
            nodeStatusRow(
                getString(R.string.node_main),
                nodeStatusText("online"),
                1,
                1,
                nodeStatusColor("online")
            )
        )
        for (item in nodes) {
            nodeStatusListView.addView(
                nodeStatusRow(
                    nodeDisplayName(item),
                    nodeStatusText(item.status),
                    item.available,
                    item.total,
                    nodeStatusColor(item.status)
                )
            )
        }
        if (e2e != null) {
            nodeStatusListView.addView(
                nodeStatusRow(
                    e2e.name,
                    nodeStatusText(e2e.status),
                    e2e.available,
                    e2e.total,
                    nodeStatusColor(e2e.status)
                )
            )
        }
        status.setText(getString(R.string.status_nodes_updated))
    }

    private fun nodeDisplayName(item: MST5.NodeStatus?): String? {
        if (item == null) return ""
        if ("call".equals(item.type)) return getString(R.string.node_calls)
        if ("file".equals(item.type)) return getString(R.string.node_media)
        if ("wallet".equals(item.type)) return getString(R.string.node_wallet)
        if ("e2e".equals(item.type)) return getString(R.string.node_e2e_keys)
        return if (item.name == null || item.name.length == 0) item.type else item.name
    }

    private fun e2eKeyStatus(c: MST5?): MST5.NodeStatus {
        val accountKey = if (myID == null || myID.trim().length == 0)
            (if (myLogin == null) "" else myLogin.trim())
        else
            myID.trim()
        if (accountKey.length == 0) {
            return NodeStatus("e2e", getString(R.string.node_e2e_keys), "check_failed", 0, 1)
        }
        try {
            val local: rs.ove.crypt.proto.NativeE2E.Identity? = SessionStore.e2eIdentity(this, accountKey)
            val registered = if (c == null) "" else c.ownE2EPublicKey()
            if (local != null && registered.length > 0 && local.publicKeyB64.equals(registered)) {
                return NodeStatus("e2e", getString(R.string.node_e2e_keys), "online", 1, 1)
            }
            if (local == null && registered.length == 0) {
                return NodeStatus("e2e", getString(R.string.node_e2e_keys), "not_generated", 0, 1)
            }
            if (local != null && registered.length == 0) {
                return NodeStatus("e2e", getString(R.string.node_e2e_keys), "local_only", 0, 1)
            }
            if (local == null) {
                return NodeStatus("e2e", getString(R.string.node_e2e_keys), "server_only", 0, 1)
            }
            return NodeStatus("e2e", getString(R.string.node_e2e_keys), "mismatch", 0, 1)
        } catch (e: Exception) {
            return NodeStatus("e2e", getString(R.string.node_e2e_keys), "check_failed", 0, 1)
        }
    }

    private fun nodeStatusRow(
        name: String?,
        state: String?,
        available: Int,
        total: Int,
        stateColor: Int
    ): LinearLayout {
        val row: LinearLayout = LinearLayout(this)
        row.setOrientation(LinearLayout.HORIZONTAL)
        row.setGravity(Gravity.CENTER_VERTICAL)
        row.setPadding(pad, dp(10), pad, dp(10))
        row.setBackgroundDrawable(shape(bg, 0, 0))
        val avatar: TextView = chatAvatar(name, dp(44))
        avatar.setTextSize(13)
        row.addView(avatar, LinearLayout.LayoutParams(dp(44), dp(44)))

        val labels: LinearLayout = LinearLayout(this)
        labels.setOrientation(LinearLayout.VERTICAL)
        val title: TextView = label(name)
        title.setTextSize(16)
        val count: TextView = label(if (total < 0) "..." else available.toString() + "/" + total)
        count.setTextSize(13)
        count.setTextColor(muted)
        labels.addView(title, LinearLayout.LayoutParams(-1, -2))
        labels.addView(count, LinearLayout.LayoutParams(-1, -2))
        val labelsLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        labelsLp.setMargins(dp(12), 0, gap, 0)
        row.addView(labels, labelsLp)

        val badge: TextView = label(state)
        badge.setTextColor(contrast(stateColor))
        badge.setTextSize(12)
        badge.setGravity(Gravity.CENTER)
        badge.setPadding(gap, dp(3), gap, dp(3))
        badge.setBackgroundDrawable(shape(stateColor, 0, dp(12)))
        row.addView(badge, LinearLayout.LayoutParams(-2, -2))

        val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, 0, 0, gap / 2)
        row.setLayoutParams(lp)
        return row
    }

    private fun nodeStatusText(value: String?): String {
        if ("loading".equals(value)) return getString(R.string.node_status_loading)
        if ("online".equals(value)) return getString(R.string.node_status_online)
        if ("partial".equals(value)) return getString(R.string.node_status_partial)
        if ("not_generated".equals(value)) return getString(R.string.node_status_not_generated)
        if ("local_only".equals(value)) return getString(R.string.node_status_local_only)
        if ("server_only".equals(value)) return getString(R.string.node_status_server_only)
        if ("mismatch".equals(value)) return getString(R.string.node_status_mismatch)
        if ("check_failed".equals(value)) return getString(R.string.node_status_check_failed)
        return getString(R.string.node_status_offline)
    }

    private fun nodeStatusColor(value: String?): Int {
        if ("online".equals(value)) return success
        if ("partial".equals(value)
            || "not_generated".equals(value)
            || "local_only".equals(value)
            || "server_only".equals(value)
            || "check_failed".equals(value)
        ) return Color.rgb(245, 166, 35)
        return danger
    }

    private fun showSettings() {
        page = Page.SETTINGS
        updateBottomNavSelection()
        if (bottomNav != null) bottomNav.setVisibility(View.VISIBLE)
        content.removeAllViews()
        content.setPadding(0, 0, 0, 0)

        val scroll: ScrollView = pageScrollView()

        val settings: LinearLayout = LinearLayout(this)
        settings.setOrientation(LinearLayout.VERTICAL)
        settings.setPadding(0, dp(6), 0, dp(28))

        val heading = title(getString(R.string.settings_title))
        heading.setTextSize(22)
        heading.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
        heading.setPadding(pad, dp(8), pad, dp(10))
        settings.addView(heading)
        settings.addView(settingsProfileHeader(), settingsOuterParams(dp(4), dp(12)))

        settings.addView(settingsGroup(
            settingsMenuRow("A", getString(R.string.settings_profile), ownUserSettingsSubtitle(), primary, object : View.OnClickListener {
                override fun onClick(v: View?) { showSettingsProfile() }
            }),
            settingsMenuRow("◈", getString(R.string.settings_privacy), privacySettingsSubtitle(), success, object : View.OnClickListener {
                override fun onClick(v: View?) { showSettingsPrivacy() }
            }),
            settingsMenuRow("!", getString(R.string.settings_sessions), getString(R.string.settings_sessions_subtitle), danger, object : View.OnClickListener {
                override fun onClick(v: View?) { showSettingsSessions() }
            })
        ), settingsOuterParams(0, dp(14)))

        settings.addView(settingsGroup(
            settingsMenuRow("K", getString(R.string.settings_cloud_password), getString(R.string.settings_cloud_password_subtitle), muted, object : View.OnClickListener {
                override fun onClick(v: View?) { showSettingsCloudPassword() }
            }),
            settingsMenuRow("E", getString(R.string.settings_e2e_keys), getString(R.string.settings_e2e_keys_subtitle), muted, object : View.OnClickListener {
                override fun onClick(v: View?) { showSettingsE2EKeys() }
            }),
            settingsMenuRow("@", getString(R.string.settings_authorization), getString(R.string.settings_authorization_subtitle), muted, object : View.OnClickListener {
                override fun onClick(v: View?) { showSettingsAuthorization() }
            }),
            settingsMenuRow("C", getString(R.string.settings_contacts), getString(R.string.settings_contacts_subtitle), muted, object : View.OnClickListener {
                override fun onClick(v: View?) { showSettingsContacts() }
            })
        ), settingsOuterParams(0, dp(14)))

        settings.addView(settingsGroup(
            settingsMenuRow("A", getString(R.string.settings_language), languageLabel(SessionStore.language(this)), muted, object : View.OnClickListener {
                override fun onClick(v: View?) { showSettingsLanguage() }
            }),
            settingsMenuRow("M", getString(R.string.settings_protocol), protocolLabel(SessionStore.transportProtocol(this)), muted, object : View.OnClickListener {
                override fun onClick(v: View?) { showSettingsProtocol() }
            }),
            settingsMenuRow("◌", getString(R.string.settings_interface), if (SessionStore.showStatus(this)) getString(R.string.settings_status_visible) else getString(R.string.settings_status_hidden), muted, object : View.OnClickListener {
                override fun onClick(v: View?) { showSettingsInterface() }
            }),
            settingsMenuRow("↥", getString(R.string.settings_updates), updateSettingsSubtitle(), muted, object : View.OnClickListener {
                override fun onClick(v: View?) { checkGithubUpdate() }
            })
        ), settingsOuterParams(0, dp(14)))

        settings.addView(settingsGroup(
            settingsMenuRow("×", getString(R.string.settings_delete_account), getString(R.string.settings_delete_account_subtitle), danger, object : View.OnClickListener {
                override fun onClick(v: View?) { showSettingsDeleteAccount() }
            }, true),
            settingsMenuRow("↪", getString(R.string.settings_logout), getString(R.string.settings_logout_subtitle), muted, object : View.OnClickListener {
                override fun onClick(v: View?) { showSettingsLogout() }
            })
        ), settingsOuterParams(0, dp(14)))
        settings.addView(settingsVersionText())

        scroll.addView(settings, LinearLayout.LayoutParams(-1, -2))
        content.addView(scroll, fill())
    }

    private fun updateSettingsSubtitle(): String {
        val repository = if (BuildConfig.GITHUB_REPOSITORY == null) "" else BuildConfig.GITHUB_REPOSITORY.trim()
        if (repository.length == 0) return getString(R.string.settings_updates_not_configured)
        return getString(R.string.settings_updates_subtitle, BuildConfig.VERSION_NAME)
    }

    private fun checkGithubUpdate(showOffer: Boolean = true) {
        val repository = if (BuildConfig.GITHUB_REPOSITORY == null) "" else BuildConfig.GITHUB_REPOSITORY.trim()
        if (repository.length == 0) {
            status.setText(getString(R.string.status_update_not_configured))
            return
        }
        SessionStore.lastGithubUpdateCheckAt(this, System.currentTimeMillis())
        status.setText(getString(R.string.status_update_checking))
        run("github_update", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val update: GithubOtaUpdater.Update? = GithubOtaUpdater.findLatest(
                    repository,
                    getPackageName(),
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                )
                if (update == null) {
                    ui(object : Runnable {
                        override fun run() {
                            cancelGithubUpdateNotification()
                            status.setText(getString(R.string.status_update_none))
                        }
                    })
                    return
                }
                ui(object : Runnable {
                    override fun run() {
                        if (showOffer) showGithubUpdateOffer(update)
                    }
                })
            }
        })
    }

    private fun maybeOfferGithubUpdate() {
        val repository = if (BuildConfig.GITHUB_REPOSITORY == null) "" else BuildConfig.GITHUB_REPOSITORY.trim()
        if (repository.length == 0) return
        val now: Long = System.currentTimeMillis()
        val lastCheck: Long = SessionStore.lastGithubUpdateCheckAt(this)
        if (lastCheck > 0 && now - lastCheck < ru.e6atb.chat.MainActivity.Companion.GITHUB_UPDATE_CHECK_INTERVAL_MS) return
        SessionStore.lastGithubUpdateCheckAt(this, now)
        io.execute(object : Runnable {
            override fun run() {
                try {
                    val update: GithubOtaUpdater.Update? = GithubOtaUpdater.findLatest(
                        repository,
                        getPackageName(),
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    )
                    if (update == null) {
                        ui(object : Runnable {
                            override fun run() {
                                cancelGithubUpdateNotification()
                            }
                        })
                        return
                    }
                    ui(object : Runnable {
                        override fun run() {
                            if (isFinishing()) return
                            postGithubUpdateNotification(update)
                        }
                    })
                } catch (ignored: Exception) {
                }
            }
        })
    }

    private fun showGithubUpdateOffer(update: GithubOtaUpdater.Update?) {
        showConfirmDialog(
            getString(R.string.update_available_title),
            getString(R.string.update_available_body, BuildConfig.VERSION_NAME, updateVersionLabel(update)),
            getString(R.string.action_update),
            object : Runnable {
                override fun run() {
                    downloadAndInstallGithubUpdate(update)
                }
            })
    }

    private fun postGithubUpdateNotification(update: GithubOtaUpdater.Update?) {
        val nm: NotificationManager? = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
        if (nm == null) return
        val version = updateVersionLabel(update)
        val title: String? = getString(R.string.update_available_title)
        val text: String? = getString(R.string.notification_update_available, version)
        val open: Intent = Intent(this, ru.e6atb.chat.MainActivity::class.java)
        open.setAction(ru.e6atb.chat.MainActivity.Companion.ACTION_OPEN_UPDATE)
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending: PendingIntent? = PendingIntent.getActivity(
            this,
            ru.e6atb.chat.MainActivity.Companion.UPDATE_NOTIFICATION_ID,
            open,
            pendingIntentFlags()
        )
        val notification: Notification = buildActivityNotification(
            ru.e6atb.chat.MainActivity.Companion.UPDATE_NOTIFICATION_CHANNEL,
            title,
            text,
            pending,
            false,
            android.R.drawable.stat_sys_download_done
        )
        notification.flags = notification.flags or Notification.FLAG_AUTO_CANCEL
        try {
            nm.notify(ru.e6atb.chat.MainActivity.Companion.UPDATE_NOTIFICATION_ID, notification)
        } catch (ignored: SecurityException) {
        }
    }

    private fun cancelGithubUpdateNotification() {
        val nm: NotificationManager? = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
        if (nm != null) nm.cancel(ru.e6atb.chat.MainActivity.Companion.UPDATE_NOTIFICATION_ID)
    }

    private fun downloadAndInstallGithubUpdate(update: GithubOtaUpdater.Update?) {
        val versionName = updateVersionLabel(update)
        status.setText(getString(R.string.status_update_downloading, versionName))
        run("github_update_download", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val apk: File? = GithubOtaUpdater.download(this@MainActivity, update)
                ui(object : Runnable {
                    override fun run() {
                        installGithubUpdate(apk, versionName)
                    }
                })
            }
        })
    }

    private fun updateVersionLabel(update: GithubOtaUpdater.Update?): String? {
        if (update != null && update.versionName != null && update.versionName.length > 0) {
            return update.versionName
        }
        return "latest"
    }

    private fun installGithubUpdate(apk: File?, versionName: String?) {
        if (apk == null || !apk.isFile()) {
            status.setText(getString(R.string.status_download_folder_not_available))
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            status.setText(getString(R.string.status_update_install_permission))
            try {
                val intent: Intent =
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))
                startActivity(intent)
            } catch (e: Exception) {
                openUrl("https://github.com/" + BuildConfig.GITHUB_REPOSITORY + "/releases/latest")
            }
            return
        }
        try {
            val intent: Intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(localFileUri(apk), "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
            status.setText(getString(R.string.status_update_ready, versionName))
        } catch (e: ActivityNotFoundException) {
            status.setText(getString(R.string.status_no_app_to_open, apk.getName()))
        } catch (e: Exception) {
            status.setText(getString(R.string.status_update_install_error, errorText(e)))
        }
    }

    private fun settingsProfileHeader(): LinearLayout {
        val box: LinearLayout = LinearLayout(this)
        box.setOrientation(LinearLayout.HORIZONTAL)
        box.setGravity(Gravity.CENTER_VERTICAL)
        box.setPadding(pad, dp(12), pad, dp(12))
        box.setBackgroundDrawable(pressable(accentSurface, blend(accentSurface, primary, 0.12f), 0, dp(16)))
        box.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                showSettingsProfile()
            }
        })
        val avatar: FrameLayout = ownProfileAvatar(dp(52))
        box.addView(avatar, LinearLayout.LayoutParams(dp(52), dp(52)))
        val details: LinearLayout = LinearLayout(this)
        details.setOrientation(LinearLayout.VERTICAL)
        val nameLine: LinearLayout = LinearLayout(this)
        nameLine.setGravity(Gravity.CENTER_VERTICAL)
        val name: TextView = label(displayOwnUser())
        name.setTextSize(16)
        name.setSingleLine(true)
        nameLine.addView(name, LinearLayout.LayoutParams(-2, -2))
        if (myVerified) {
            val badge: ImageView = ImageView(this)
            badge.setImageDrawable(verifiedDrawable(dp(18)))
            val badgeLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
            badgeLp.setMargins(gap / 2, 0, 0, 0)
            nameLine.addView(badge, badgeLp)
        }
        details.addView(nameLine, LinearLayout.LayoutParams(-1, -2))
        val identity: TextView = label(ownUserSettingsSubtitle())
        identity.setTextColor(muted)
        identity.setTextSize(13)
        details.addView(identity, LinearLayout.LayoutParams(-1, -2))
        if (myDescription != null && myDescription.length > 0) {
            val description: TextView = label(myDescription)
            description.setTextColor(muted)
            description.setTextSize(13)
            description.setSingleLine(true)
            description.setEllipsize(TextUtils.TruncateAt.END)
            details.addView(description, LinearLayout.LayoutParams(-1, -2))
        }
        val detailsLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        detailsLp.setMargins(dp(12), 0, 0, 0)
        box.addView(details, detailsLp)
        val arrow: TextView = label("›")
        arrow.setTextSize(24)
        arrow.setTextColor(muted)
        arrow.setGravity(Gravity.CENTER)
        box.addView(arrow, LinearLayout.LayoutParams(dp(28), dp(52)))
        return box
    }

    private fun settingsOuterParams(top: Int, bottom: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(pad, top, pad, bottom)
        return lp
    }

    private fun settingsGroup(vararg rows: View): LinearLayout {
        val group = LinearLayout(this)
        group.setOrientation(LinearLayout.VERTICAL)
        group.setBackgroundDrawable(shape(surface, 0, dp(14)))
        for (index in rows.indices) {
            group.addView(rows[index], LinearLayout.LayoutParams(-1, -2))
            if (index + 1 < rows.size) {
                val divider = View(this)
                divider.setBackgroundColor(border)
                val dividerLp = LinearLayout.LayoutParams(-1, Math.max(1, dp(1)))
                dividerLp.setMargins(dp(14), 0, dp(14), 0)
                group.addView(divider, dividerLp)
            }
        }
        return group
    }

    private fun settingsMenuRow(
        glyph: String,
        name: String?,
        detail: String?,
        glyphColor: Int,
        listener: View.OnClickListener?,
        destructive: Boolean = false
    ): LinearLayout {
        val row = LinearLayout(this)
        row.setOrientation(LinearLayout.HORIZONTAL)
        row.setGravity(Gravity.CENTER_VERTICAL)
        row.setMinimumHeight(dp(54))
        row.setPadding(dp(14), dp(10), dp(10), dp(10))
        row.setBackgroundDrawable(pressable(surface, surfaceHi, 0, 0))
        row.setOnClickListener(listener)

        val glyphView = TextView(this)
        glyphView.setText(glyph)
        glyphView.setTextColor(glyphColor)
        glyphView.setTextSize(14)
        glyphView.setGravity(Gravity.CENTER)
        glyphView.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        glyphView.setBackgroundDrawable(shape(blend(glyphColor, surface, 0.86f), 0, dp(9)))
        row.addView(glyphView, LinearLayout.LayoutParams(dp(30), dp(30)))

        val texts = LinearLayout(this)
        texts.setOrientation(LinearLayout.VERTICAL)
        val title = label(name)
        title.setTextSize(15)
        title.setTextColor(if (destructive) danger else textColor)
        title.setSingleLine(true)
        texts.addView(title, LinearLayout.LayoutParams(-1, -2))
        if (!detail.isNullOrEmpty()) {
            val subtitle = label(detail)
            subtitle.setTextSize(12)
            subtitle.setTextColor(muted)
            subtitle.setSingleLine(true)
            subtitle.setEllipsize(TextUtils.TruncateAt.END)
            texts.addView(subtitle, LinearLayout.LayoutParams(-1, -2))
        }
        val textsLp = LinearLayout.LayoutParams(0, -2, 1f)
        textsLp.setMargins(dp(12), 0, dp(8), 0)
        row.addView(texts, textsLp)

        val arrow = label("›")
        arrow.setTextColor(muted)
        arrow.setTextSize(22)
        arrow.setGravity(Gravity.CENTER)
        row.addView(arrow, LinearLayout.LayoutParams(dp(20), dp(30)))
        return row
    }

    private fun ownUserSettingsSubtitle(): String? {
        if (myLogin != null && myLogin.length > 0) return "@" + myLogin
        if (myID != null && myID.length > 0) return myID
        return getString(R.string.settings_profile_default_subtitle)
    }

    private fun settingsSection(value: String?): TextView {
        val section: TextView = label(value)
        section.setTextColor(muted)
        section.setTextSize(13)
        section.setPadding(0, pad, 0, gap / 2)
        return section
    }

    private fun settingsRow(name: String?, detail: String?, listener: View.OnClickListener?): LinearLayout {
        val row: LinearLayout = LinearLayout(this)
        row.setOrientation(LinearLayout.HORIZONTAL)
        row.setGravity(Gravity.CENTER_VERTICAL)
        row.setPadding(pad, dp(11), pad, dp(11))
        row.setBackgroundDrawable(pressable(bg, accentSurface, 0, 0))
        row.setOnClickListener(listener)
        val avatar: TextView = chatAvatar(name, dp(38))
        avatar.setTextSize(12)
        row.addView(avatar, LinearLayout.LayoutParams(dp(38), dp(38)))

        val texts: LinearLayout = LinearLayout(this)
        texts.setOrientation(LinearLayout.VERTICAL)
        val title: TextView = label(name)
        title.setTextSize(16)
        title.setTextColor(textColor)
        texts.addView(title, LinearLayout.LayoutParams(-1, -2))
        if (detail != null && detail.length > 0) {
            val subtitle: TextView = label(detail)
            subtitle.setTextSize(13)
            subtitle.setTextColor(muted)
            texts.addView(subtitle, LinearLayout.LayoutParams(-1, -2))
        }
        val textsLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        textsLp.setMargins(dp(12), 0, 0, 0)
        row.addView(texts, textsLp)

        val arrow: TextView = label(">")
        arrow.setTextColor(muted)
        arrow.setTextSize(18)
        arrow.setGravity(Gravity.RIGHT or Gravity.CENTER_VERTICAL)
        row.addView(arrow, LinearLayout.LayoutParams(dp(24), -2))

        val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, 0, 0, gap / 2)
        row.setLayoutParams(lp)
        return row
    }

    private fun settingsToggleRow(
        name: String?,
        detail: String?,
        checked: Boolean,
        listener: View.OnClickListener?
    ): LinearLayout {
        val row: LinearLayout = settingsRow(name, detail, listener)
        if (row.getChildCount() > 1) row.removeViewAt(row.getChildCount() - 1)
        val toggle: android.widget.Switch = Switch(this)
        toggle.setChecked(checked)
        toggle.setClickable(false)
        toggle.setFocusable(false)
        row.addView(toggle, LinearLayout.LayoutParams(-2, -2))
        return row
    }

    private fun settingsVersionText(): TextView {
        val version: TextView = label(getString(R.string.settings_app_version) + ": " + BuildConfig.VERSION_NAME)
        version.setTextSize(13)
        version.setTextColor(muted)
        version.setGravity(Gravity.CENTER)
        version.setPadding(pad, gap, pad, pad)

        val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, gap / 2, 0, 0)
        version.setLayoutParams(lp)
        return version
    }

    private fun settingsPage(titleText: String?, pageValue: Page?): LinearLayout {
        page = pageValue
        if (bottomNav != null) bottomNav.setVisibility(View.VISIBLE)
        content.removeAllViews()
        content.setPadding(0, 0, 0, 0)

        val scroll: ScrollView = pageScrollView()

        val box: LinearLayout = LinearLayout(this)
        box.setOrientation(LinearLayout.VERTICAL)
        box.setPadding(pad, dp(6), pad, dp(28))
        val back: ImageButton =
            headerIconButton(R.drawable.ic_back, getString(R.string.action_back), object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showSettings()
                }
            })
        val header = LinearLayout(this)
        header.setOrientation(LinearLayout.HORIZONTAL)
        header.setGravity(Gravity.CENTER_VERTICAL)
        header.setPadding(0, dp(4), 0, dp(4))
        header.addView(back, LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight))
        val heading = label(titleText)
        heading.setTextSize(18)
        heading.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
        val headingLp = LinearLayout.LayoutParams(0, -2, 1f)
        headingLp.setMargins(dp(6), 0, 0, 0)
        header.addView(heading, headingLp)
        box.addView(header, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(box, LinearLayout.LayoutParams(-1, -2))
        content.addView(scroll, fill())
        return box
    }

    private fun showSettingsProfile() {
        val box: LinearLayout = settingsPage(getString(R.string.settings_profile), Page.SETTINGS_PROFILE)
        val avatarBlock = LinearLayout(this)
        avatarBlock.setOrientation(LinearLayout.VERTICAL)
        avatarBlock.setGravity(Gravity.CENTER_HORIZONTAL)
        avatarBlock.setPadding(0, dp(14), 0, dp(8))
        val avatar = ownProfileAvatar(dp(84))
        avatar.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) { pickAvatar() }
        })
        avatarBlock.addView(avatar, LinearLayout.LayoutParams(dp(84), dp(84)))
        val changeAvatar = label(getString(R.string.settings_change_photo))
        changeAvatar.setTextColor(primary)
        changeAvatar.setTextSize(13)
        changeAvatar.setGravity(Gravity.CENTER)
        changeAvatar.setPadding(0, dp(8), 0, 0)
        changeAvatar.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) { pickAvatar() }
        })
        avatarBlock.addView(changeAvatar, LinearLayout.LayoutParams(-2, -2))
        box.addView(avatarBlock, LinearLayout.LayoutParams(-1, -2))
        val signedIn = (myID != null && myID.length > 0) || (myLogin != null && myLogin.length > 0)
        val identity = label(if (signedIn) getString(R.string.status_online_as, displayOwnUser()) else getString(R.string.settings_not_logged_in))
        identity.setTextColor(muted)
        identity.setTextSize(13)
        identity.setGravity(Gravity.CENTER)
        box.addView(spaced(identity))
        if (myID != null && myID.length > 0) {
            box.addView(spaced(title(getString(R.string.profile_id))))
            box.addView(spaced(clickableUserID(myID)))
        }
        box.addView(spaced(title(getString(R.string.profile_username))))
        accountUsername = input(getString(R.string.hint_username), false)
        accountUsername.setText(if (myLogin == null) "" else myLogin)
        box.addView(spaced(accountUsername))
        box.addView(spaced(row(primaryButton(getString(R.string.settings_save_username), object : View.OnClickListener {
            override fun onClick(v: View?) {
                saveUsername(v)
            }
        }))))
        box.addView(spaced(title(getString(R.string.settings_name))))
        accountName = input(getString(R.string.settings_public_name_hint), false)
        accountName.setText(if (myNick == null) "" else myNick)
        box.addView(spaced(accountName))
        box.addView(spaced(row(primaryButton(getString(R.string.settings_save_name), object : View.OnClickListener {
            override fun onClick(v: View?) {
                saveName(v)
            }
        }))))
        box.addView(spaced(title(getString(R.string.profile_description))))
        accountDescription = input(getString(R.string.settings_description_hint), false)
        accountDescription.setSingleLine(false)
        accountDescription.setMinLines(3)
        accountDescription.setMaxLines(6)
        accountDescription.setFilters(
            arrayOf<android.text.InputFilter>(
                LengthFilter(200)
            )
        )
        accountDescription.setText(if (myDescription == null) "" else myDescription)
        box.addView(spaced(accountDescription))
        box.addView(spaced(row(primaryButton(getString(R.string.settings_save_description), object : View.OnClickListener {
            override fun onClick(v: View?) {
                saveOwnDescription(v)
            }
        }))))
        box.addView(spaced(row(primaryButton("Set public avatar — 1 DSR", object : View.OnClickListener {
            override fun onClick(v: View?) {
                pickAvatar()
            }
        }))))
        box.addView(spaced(row(button(getString(R.string.profile_qr), object : View.OnClickListener {
            override fun onClick(v: View?) { showProfileQr() }
        }))))
        if (myAvatar != null && myAvatar!!.id.isNotEmpty()) {
            box.addView(spaced(row(button(getString(R.string.action_delete), object : View.OnClickListener {
                override fun onClick(v: View?) {
                    deleteAvatar()
                }
            }))))
        }
    }

    private fun showProfileQr() {
        val address = if (!myLogin.isNullOrEmpty()) myLogin else myID.orEmpty()
        if (address.isEmpty()) { status.setText(getString(R.string.status_sign_in_first)); return }
        try {
            val matrix = QRCodeWriter().encode("ove://user/$address", BarcodeFormat.QR_CODE, 640, 640)
            val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            for (x in 0 until matrix.width) for (y in 0 until matrix.height) bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            val image = ImageView(this).apply { setImageBitmap(bitmap); setPadding(gap, gap, gap, gap); setBackgroundColor(Color.WHITE); contentDescription = getString(R.string.profile_qr) }
            val caption = label("ove://user/$address").also { it.setGravity(Gravity.CENTER); it.setTextColor(muted) }
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(image, LinearLayout.LayoutParams(-1, dp(300))); addView(caption, LinearLayout.LayoutParams(-1, -2)) }
            showContentDialog(getString(R.string.profile_qr), content, getString(R.string.action_close), null, null)
        } catch (error: Exception) { status.setText(errorText(error)) }
    }

    private fun showSettingsSessions() {
        val box: LinearLayout = settingsPage(getString(R.string.settings_sessions), Page.SETTINGS_SESSIONS)
        accountSessionsView = LinearLayout(this)
        accountSessionsView.setOrientation(LinearLayout.VERTICAL)
        accountSessionsView.addView(sessionRow(getString(R.string.loading), muted))
        box.addView(spaced(accountSessionsView))
        box.addView(
            spaced(
                row(
                    button(getString(R.string.action_refresh), object : View.OnClickListener {
                        override fun onClick(v: View?) {
                            loadSessions(v, false)
                        }
                    }),
                    primaryButton(getString(R.string.settings_logout_others), object : View.OnClickListener {
                        override fun onClick(v: View?) {
                            revokeOtherSessions(v, true)
                        }
                    })
                )
            )
        )
        loadSessions()
    }

    private fun showSettingsCloudPassword() {
        val box: LinearLayout = settingsPage(getString(R.string.settings_cloud_password), Page.SETTINGS_CLOUD_PASSWORD)
        box.addView(spaced(label(getString(R.string.settings_cloud_password_help))))
        accountCloudPassword = input(getString(R.string.settings_optional_password), true)
        box.addView(spaced(accountCloudPassword))
        cloudPasswordSaveButton = primaryButton(getString(R.string.settings_save_password), object : View.OnClickListener {
            override fun onClick(v: View?) {
                saveCloudPassword()
            }
        })
        box.addView(spaced(row(cloudPasswordSaveButton)))
        box.addView(spaced(title(getString(R.string.settings_reset_cloud_password))))
        box.addView(spaced(label(getString(R.string.settings_reset_cloud_password_help, accountEmailText()))))
        accountCloudPasswordCode = input(getString(R.string.hint_email_code), false)
        box.addView(spaced(accountCloudPasswordCode))
        cloudPasswordClearButton = button(getString(R.string.action_send_code), object : View.OnClickListener {
            override fun onClick(v: View?) {
                requestAccountEmailCode(
                    accountCloudPasswordCode,
                    getString(R.string.status_cloud_password_reset_code_sent),
                    v,
                    false
                )
            }
        })
        box.addView(spaced(row(cloudPasswordClearButton)))
        val resetSlider: PaymentSliderView = paymentSlider(getString(R.string.reset_cloud_password_slide_hint), true)
        resetSlider.setContentDescription(getString(R.string.reset_cloud_password_slide_hint))
        resetSlider.setOnConfirmAction(object : Runnable {
            override fun run() {
                resetCloudPassword()
            }
        })
        box.addView(spaced(resetSlider), LinearLayout.LayoutParams(-1, dp(56)))
        cloudPasswordState = label("")
        cloudPasswordState.setTextColor(muted)
        val stateRow: LinearLayout = LinearLayout(this)
        stateRow.setOrientation(LinearLayout.HORIZONTAL)
        stateRow.setGravity(Gravity.CENTER_VERTICAL)
        stateRow.addView(cloudPasswordState, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(spaced(stateRow))
    }

    private fun showSettingsE2EKeys() {
        val box: LinearLayout = settingsPage(getString(R.string.settings_e2e_keys), Page.SETTINGS_E2E_KEYS)
        box.addView(spaced(label(getString(R.string.settings_e2e_keys_help))))
        val resetSlider: PaymentSliderView = paymentSlider(getString(R.string.reset_e2e_key_slide_hint), true)
        resetSlider.setContentDescription(getString(R.string.reset_e2e_key_slide_hint))
        resetSlider.setOnConfirmAction(object : Runnable {
            override fun run() {
                resetE2EKey()
            }
        })
        box.addView(spaced(resetSlider), LinearLayout.LayoutParams(-1, dp(56)))
    }

    private fun showSettingsAuthorization() {
        val box: LinearLayout = settingsPage(getString(R.string.settings_authorization), Page.SETTINGS_AUTHORIZATION)
        box.addView(spaced(label(getString(R.string.oauth_settings_help))))
        val code: EditText = input(getString(R.string.oauth_code_hint), false)
        code.setSingleLine(true)
        code.setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        box.addView(spaced(code))
        box.addView(
            spaced(
                row(
                    primaryButton(getString(R.string.oauth_open_request), object : View.OnClickListener {
                        override fun onClick(view: View?) {
                            openOAuthDeviceRequest(code.getText().toString())
                        }
                    }),
                    button(getString(R.string.action_paste), object : View.OnClickListener {
                        override fun onClick(view: View?) {
                            val pasted = clipboardText()
                            if (pasted.length > 0) code.setText(pasted)
                        }
                    })
                )
            )
        )
        box.addView(spaced(primaryButton(getString(R.string.oauth_scan_qr), object : View.OnClickListener {
            override fun onClick(view: View?) {
                if (!hasPermissionCompat(ru.e6atb.chat.MainActivity.Companion.PERMISSION_CAMERA)) {
                    requestPermissionsCompat(
                        arrayOf<String>(ru.e6atb.chat.MainActivity.Companion.PERMISSION_CAMERA),
                        ru.e6atb.chat.MainActivity.Companion.REQ_CAMERA
                    )
                    return
                }
                startOAuthQrScanner()
            }
        })))
    }

    private fun startOAuthQrScanner() {
        startActivityForResult(
            Intent(this, QrScannerActivity::class.java),
            ru.e6atb.chat.MainActivity.Companion.REQ_QR_SCAN
        )
    }

    private fun clipboardText(): String {
        val clipboard: Any? = getSystemService(CLIPBOARD_SERVICE)
        if (clipboard == null) return ""
        try {
            val value: Any? = clipboard.javaClass.getMethod("getText").invoke(clipboard)
            return if (value == null) "" else value.toString()
        } catch (ignored: Exception) {
            return ""
        }
    }

    private fun showSettingsDeleteAccount() {
        val box: LinearLayout = settingsPage(getString(R.string.settings_delete_account), Page.SETTINGS_DELETE_ACCOUNT)
        box.addView(spaced(label(getString(R.string.settings_delete_account_help, accountEmailText()))))
        accountDeleteCode = input(getString(R.string.hint_email_code), false)
        box.addView(spaced(accountDeleteCode))
        deleteAccountCodeButton = button(getString(R.string.action_send_code), object : View.OnClickListener {
            override fun onClick(v: View?) {
                requestAccountEmailCode(
                    accountDeleteCode,
                    getString(R.string.status_delete_account_code_sent),
                    v,
                    false
                )
            }
        })
        box.addView(spaced(row(deleteAccountCodeButton)))
        val deleteSlider: PaymentSliderView = paymentSlider(getString(R.string.delete_account_slide_hint), true)
        deleteSlider.setContentDescription(getString(R.string.delete_account_slide_hint))
        deleteSlider.setOnConfirmAction(object : Runnable {
            override fun run() {
                deleteAccount()
            }
        })
        box.addView(spaced(deleteSlider), LinearLayout.LayoutParams(-1, dp(56)))
    }

    private fun showSettingsLogout() {
        val box: LinearLayout = settingsPage(getString(R.string.settings_logout), Page.SETTINGS_LOGOUT)
        box.addView(spaced(label(getString(R.string.settings_logout_subtitle))))
        val logoutSlider: PaymentSliderView = paymentSlider(getString(R.string.logout_slide_hint), true)
        logoutSlider.setContentDescription(getString(R.string.logout_slide_hint))
        logoutSlider.setOnConfirmAction(object : Runnable {
            override fun run() {
                logout()
            }
        })
        box.addView(spaced(logoutSlider), LinearLayout.LayoutParams(-1, dp(56)))
    }

    private fun showSettingsContacts() {
        val box: LinearLayout = settingsPage(getString(R.string.settings_contacts), Page.SETTINGS_CONTACTS)
        contactAddress = input(getString(R.string.hint_username_or_id), false)
        box.addView(spaced(contactAddress))
        box.addView(
            spaced(
                row(
                    primaryButton(getString(R.string.action_add_contact), object : View.OnClickListener {
                        override fun onClick(v: View?) {
                            saveContact(true, v, true)
                        }
                    }),
                    button(getString(R.string.action_delete_contact), object : View.OnClickListener {
                        override fun onClick(v: View?) {
                            saveContact(false, v, false)
                        }
                    })
                )
            )
        )
        contactsView = LinearLayout(this)
        contactsView.setOrientation(LinearLayout.VERTICAL)
        contactsView.addView(settingsSection(getString(R.string.loading)))
        box.addView(spaced(contactsView))
        loadContacts()
    }

    private fun loadContacts() {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        run("contacts", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val contacts: List<MST5.User> = c.contacts
                ui(object : Runnable {
                    override fun run() {
                        if (page !== Page.SETTINGS_CONTACTS || contactsView == null) return
                        contactsView.removeAllViews()
                        if (contacts.isEmpty()) {
                            contactsView.addView(settingsSection(getString(R.string.contacts_empty)))
                            return
                        }
                        for (user in contacts) {
                            contactsView.addView(settingsRow(displayUser(user), user.id, object : View.OnClickListener {
                                override fun onClick(v: View?) {
                                    openChatImmediately(
                                        resolvedPeerName(user, user.id),
                                        user,
                                        false,
                                        false,
                                        false,
                                        null
                                    )
                                }
                            }))
                        }
                    }
                })
            }
        })
    }

    private fun saveContact(add: Boolean, actionButton: View? = null, primaryStyle: Boolean = add) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        val value = if (contactAddress == null) "" else contactAddress.getText().toString().trim()
        if (value.length == 0) return
        status.setText(getString(if (add) R.string.status_saving_contact else R.string.status_deleting_contact))
        runButtonTask("contact", actionButton, primaryStyle, object : Task {
            @Throws(Exception::class)
            override fun run() {
                if (add) c.addContact(value) else c.deleteContact(value)
                ui(object : Runnable {
                    override fun run() {
                        status.setText(getString(if (add) R.string.status_contact_saved else R.string.status_contact_deleted))
                        loadContacts()
                    }
                })
            }
        })
    }

    private fun showSettingsPrivacy() {
        val box: LinearLayout = settingsPage(getString(R.string.settings_privacy), Page.SETTINGS_PRIVACY)
        box.addView(spaced(title(getString(R.string.settings_privacy_messages))))
        messagePrivacyGroup = RadioGroup(this)
        messagePrivacyGroup.setOrientation(RadioGroup.VERTICAL)
        addPrivacyOption(
            messagePrivacyGroup,
            ru.e6atb.chat.MainActivity.Companion.MESSAGE_PRIVACY_EVERYONE_ID,
            getString(R.string.privacy_everyone)
        )
        addPrivacyOption(
            messagePrivacyGroup,
            ru.e6atb.chat.MainActivity.Companion.MESSAGE_PRIVACY_CONTACTS_ID,
            getString(R.string.privacy_contacts)
        )
        addPrivacyOption(
            messagePrivacyGroup,
            ru.e6atb.chat.MainActivity.Companion.MESSAGE_PRIVACY_CHATS_ID,
            getString(R.string.privacy_chats)
        )
        addPrivacyOption(
            messagePrivacyGroup,
            ru.e6atb.chat.MainActivity.Companion.MESSAGE_PRIVACY_NOBODY_ID,
            getString(R.string.privacy_nobody)
        )
        messagePrivacyGroup.check(messagePrivacyId(myMessagePrivacy))
        styleSelectionGroup(messagePrivacyGroup)
        box.addView(spaced(messagePrivacyGroup))

        box.addView(spaced(title(getString(R.string.settings_privacy_calls))))
        callPrivacyGroup = RadioGroup(this)
        callPrivacyGroup.setOrientation(RadioGroup.VERTICAL)
        addPrivacyOption(
            callPrivacyGroup,
            ru.e6atb.chat.MainActivity.Companion.CALL_PRIVACY_EVERYONE_ID,
            getString(R.string.privacy_everyone)
        )
        addPrivacyOption(
            callPrivacyGroup,
            ru.e6atb.chat.MainActivity.Companion.CALL_PRIVACY_CONTACTS_ID,
            getString(R.string.privacy_contacts)
        )
        addPrivacyOption(
            callPrivacyGroup,
            ru.e6atb.chat.MainActivity.Companion.CALL_PRIVACY_CHATS_ID,
            getString(R.string.privacy_chats)
        )
        addPrivacyOption(
            callPrivacyGroup,
            ru.e6atb.chat.MainActivity.Companion.CALL_PRIVACY_NOBODY_ID,
            getString(R.string.privacy_nobody)
        )
        callPrivacyGroup.check(callPrivacyId(myCallPrivacy))
        styleSelectionGroup(callPrivacyGroup)
        box.addView(spaced(callPrivacyGroup))

        box.addView(spaced(title(getString(R.string.settings_privacy_invites))))
        invitePrivacyGroup = RadioGroup(this)
        invitePrivacyGroup.setOrientation(RadioGroup.VERTICAL)
        addPrivacyOption(
            invitePrivacyGroup,
            ru.e6atb.chat.MainActivity.Companion.INVITE_PRIVACY_EVERYONE_ID,
            getString(R.string.privacy_everyone)
        )
        addPrivacyOption(
            invitePrivacyGroup,
            ru.e6atb.chat.MainActivity.Companion.INVITE_PRIVACY_CONTACTS_ID,
            getString(R.string.privacy_contacts)
        )
        addPrivacyOption(
            invitePrivacyGroup,
            ru.e6atb.chat.MainActivity.Companion.INVITE_PRIVACY_NOBODY_ID,
            getString(R.string.privacy_nobody)
        )
        invitePrivacyGroup.check(invitePrivacyId(myInvitePrivacy))
        styleSelectionGroup(invitePrivacyGroup)
        box.addView(spaced(invitePrivacyGroup))

        box.addView(spaced(row(primaryButton(getString(R.string.action_save), object : View.OnClickListener {
            override fun onClick(v: View?) {
                savePrivacy(v)
            }
        }))))
    }

    private fun addPrivacyOption(group: RadioGroup, id: Int, label: String?) {
        val button: RadioButton = ru.e6atb.chat.MainActivity.ChoiceRadioButton(this, choiceButtonTextInset())
        button.setId(id)
        button.setText(ru.e6atb.chat.MainActivity.Companion.safeDisplayText(label))
        styleChoiceButton(button, true)
        val lp: RadioGroup.LayoutParams = RadioGroup.LayoutParams(-1, -2)
        group.addView(button, lp)
    }

    private fun styleSelectionGroup(group: RadioGroup) {
        group.setBackgroundDrawable(shape(surface, 0, dp(14)))
        for (index in 0 until group.childCount) {
            group.getChildAt(index).setBackgroundDrawable(pressable(surface, surfaceHi, 0, 0))
        }
    }

    private fun showSettingsServer() {
        showSettings()
    }

    private fun showSettingsLanguage() {
        val box: LinearLayout = settingsPage(getString(R.string.settings_language), Page.SETTINGS_LANGUAGE)
        languageGroup = RadioGroup(this)
        languageGroup.setOrientation(RadioGroup.VERTICAL)
        addLanguageOption(
            languageGroup,
            ru.e6atb.chat.MainActivity.Companion.LANGUAGE_SYSTEM_ID,
            getString(R.string.language_system)
        )
        addLanguageOption(
            languageGroup,
            ru.e6atb.chat.MainActivity.Companion.LANGUAGE_ENGLISH_ID,
            getString(R.string.language_english)
        )
        addLanguageOption(
            languageGroup,
            ru.e6atb.chat.MainActivity.Companion.LANGUAGE_RUSSIAN_ID,
            getString(R.string.language_russian)
        )
        languageGroup.check(languageId(SessionStore.language(this)))
        styleSelectionGroup(languageGroup)
        box.addView(spaced(languageGroup))
        box.addView(spaced(row(primaryButton(getString(R.string.action_save), object : View.OnClickListener {
            override fun onClick(v: View?) {
                applyLanguage()
            }
        }))))
    }

    private fun addLanguageOption(group: RadioGroup, id: Int, label: String?) {
        val button: RadioButton = ru.e6atb.chat.MainActivity.ChoiceRadioButton(this, choiceButtonTextInset())
        button.setId(id)
        button.setText(ru.e6atb.chat.MainActivity.Companion.safeDisplayText(label))
        styleChoiceButton(button, true)
        val lp: RadioGroup.LayoutParams = RadioGroup.LayoutParams(-1, -2)
        group.addView(button, lp)
    }

    private fun languageId(language: String?): Int {
        if (AppLocale.ENGLISH.equals(language)) return ru.e6atb.chat.MainActivity.Companion.LANGUAGE_ENGLISH_ID
        if (AppLocale.RUSSIAN.equals(language)) return ru.e6atb.chat.MainActivity.Companion.LANGUAGE_RUSSIAN_ID
        return ru.e6atb.chat.MainActivity.Companion.LANGUAGE_SYSTEM_ID
    }

    private fun selectedLanguage(): String {
        if (languageGroup == null) return AppLocale.SYSTEM
        val checked: Int = languageGroup.getCheckedRadioButtonId()
        if (checked == ru.e6atb.chat.MainActivity.Companion.LANGUAGE_ENGLISH_ID) return AppLocale.ENGLISH
        if (checked == ru.e6atb.chat.MainActivity.Companion.LANGUAGE_RUSSIAN_ID) return AppLocale.RUSSIAN
        return AppLocale.SYSTEM
    }

    private fun languageLabel(language: String?): String {
        if (AppLocale.ENGLISH.equals(language)) return getString(R.string.language_english)
        if (AppLocale.RUSSIAN.equals(language)) return getString(R.string.language_russian)
        return getString(R.string.language_system)
    }

    private fun showSettingsProtocol() {
        val box: LinearLayout = settingsPage(getString(R.string.settings_protocol), Page.SETTINGS_PROTOCOL)
        protocolGroup = RadioGroup(this)
        protocolGroup.setOrientation(RadioGroup.VERTICAL)
        addLanguageOption(
            protocolGroup,
            ru.e6atb.chat.MainActivity.Companion.PROTOCOL_AUTO_ID,
            getString(R.string.settings_protocol_auto)
        )
        addLanguageOption(
            protocolGroup,
            ru.e6atb.chat.MainActivity.Companion.PROTOCOL_MST5_ID,
            getString(R.string.settings_protocol_mst5)
        )
        addLanguageOption(
            protocolGroup,
            ru.e6atb.chat.MainActivity.Companion.PROTOCOL_M5OH_ID,
            getString(R.string.settings_protocol_m5oh)
        )
        protocolGroup.check(protocolId(SessionStore.transportProtocol(this)))
        styleSelectionGroup(protocolGroup)
        box.addView(spaced(protocolGroup))
        box.addView(spaced(row(primaryButton(getString(R.string.action_save), object : View.OnClickListener {
            override fun onClick(v: View?) {
                applyProtocol()
                showSettings()
            }
        }))))
    }

    private fun protocolId(protocol: String?): Int {
        if (SessionStore.TRANSPORT_MST5.equals(protocol)) return ru.e6atb.chat.MainActivity.Companion.PROTOCOL_MST5_ID
        if (SessionStore.TRANSPORT_M5OH.equals(protocol)) return ru.e6atb.chat.MainActivity.Companion.PROTOCOL_M5OH_ID
        return ru.e6atb.chat.MainActivity.Companion.PROTOCOL_AUTO_ID
    }

    private fun selectedProtocol(): String {
        if (protocolGroup == null) return SessionStore.TRANSPORT_AUTO
        val checked: Int = protocolGroup.getCheckedRadioButtonId()
        if (checked == ru.e6atb.chat.MainActivity.Companion.PROTOCOL_MST5_ID) return SessionStore.TRANSPORT_MST5
        if (checked == ru.e6atb.chat.MainActivity.Companion.PROTOCOL_M5OH_ID) return SessionStore.TRANSPORT_M5OH
        return SessionStore.TRANSPORT_AUTO
    }

    private fun protocolLabel(protocol: String?): String {
        if (SessionStore.TRANSPORT_MST5.equals(protocol)) return getString(R.string.settings_protocol_mst5)
        if (SessionStore.TRANSPORT_M5OH.equals(protocol)) return getString(R.string.settings_protocol_m5oh)
        return getString(R.string.settings_protocol_auto)
    }

    private fun applyProtocol() {
        val protocol = selectedProtocol()
        SessionStore.setTransportProtocol(this, protocol)
        val previous = ta
        if (previous != null && previous.token().isNotEmpty()) {
            val next = MST5(this, connectionServer(), previous.token(), myID, myLogin)
            ta = next
            previous.close()
            SessionStore.save(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER, next.token(), myID, myLogin)
            startSyncService()
        }
        status.setText(getString(R.string.status_protocol_set))
    }

    private fun normalizePrivacy(value: String?): String? {
        if ("contacts".equals(value) || "chats".equals(value) || "nobody".equals(value)) return value
        return "everyone"
    }

    private fun normalizeInvitePrivacy(value: String?): String? {
        if ("contacts".equals(value) || "nobody".equals(value)) return value
        return "everyone"
    }

    private fun privacyLabel(value: String?): String {
        var value = value
        value = normalizePrivacy(value)
        if ("contacts".equals(value)) return getString(R.string.privacy_contacts)
        if ("chats".equals(value)) return getString(R.string.privacy_chats)
        if ("nobody".equals(value)) return getString(R.string.privacy_nobody)
        return getString(R.string.privacy_everyone)
    }

    private fun invitePrivacyLabel(value: String?): String {
        var value = value
        value = normalizeInvitePrivacy(value)
        if ("contacts".equals(value)) return getString(R.string.privacy_contacts)
        if ("nobody".equals(value)) return getString(R.string.privacy_nobody)
        return getString(R.string.privacy_everyone)
    }

    private fun privacySettingsSubtitle(): String {
        return getString(
            R.string.settings_privacy_subtitle,
            privacyLabel(myMessagePrivacy),
            privacyLabel(myCallPrivacy),
            invitePrivacyLabel(myInvitePrivacy)
        )
    }

    private fun messagePrivacyId(value: String?): Int {
        var value = value
        value = normalizePrivacy(value)
        if ("contacts".equals(value)) return ru.e6atb.chat.MainActivity.Companion.MESSAGE_PRIVACY_CONTACTS_ID
        if ("chats".equals(value)) return ru.e6atb.chat.MainActivity.Companion.MESSAGE_PRIVACY_CHATS_ID
        if ("nobody".equals(value)) return ru.e6atb.chat.MainActivity.Companion.MESSAGE_PRIVACY_NOBODY_ID
        return ru.e6atb.chat.MainActivity.Companion.MESSAGE_PRIVACY_EVERYONE_ID
    }

    private fun callPrivacyId(value: String?): Int {
        var value = value
        value = normalizePrivacy(value)
        if ("contacts".equals(value)) return ru.e6atb.chat.MainActivity.Companion.CALL_PRIVACY_CONTACTS_ID
        if ("chats".equals(value)) return ru.e6atb.chat.MainActivity.Companion.CALL_PRIVACY_CHATS_ID
        if ("nobody".equals(value)) return ru.e6atb.chat.MainActivity.Companion.CALL_PRIVACY_NOBODY_ID
        return ru.e6atb.chat.MainActivity.Companion.CALL_PRIVACY_EVERYONE_ID
    }

    private fun invitePrivacyId(value: String?): Int {
        var value = value
        value = normalizeInvitePrivacy(value)
        if ("contacts".equals(value)) return ru.e6atb.chat.MainActivity.Companion.INVITE_PRIVACY_CONTACTS_ID
        if ("nobody".equals(value)) return ru.e6atb.chat.MainActivity.Companion.INVITE_PRIVACY_NOBODY_ID
        return ru.e6atb.chat.MainActivity.Companion.INVITE_PRIVACY_EVERYONE_ID
    }

    private fun selectedMessagePrivacy(): String? {
        if (messagePrivacyGroup == null) return myMessagePrivacy
        val checked: Int = messagePrivacyGroup.getCheckedRadioButtonId()
        if (checked == ru.e6atb.chat.MainActivity.Companion.MESSAGE_PRIVACY_CONTACTS_ID) return "contacts"
        if (checked == ru.e6atb.chat.MainActivity.Companion.MESSAGE_PRIVACY_CHATS_ID) return "chats"
        if (checked == ru.e6atb.chat.MainActivity.Companion.MESSAGE_PRIVACY_NOBODY_ID) return "nobody"
        return "everyone"
    }

    private fun selectedCallPrivacy(): String? {
        if (callPrivacyGroup == null) return myCallPrivacy
        val checked: Int = callPrivacyGroup.getCheckedRadioButtonId()
        if (checked == ru.e6atb.chat.MainActivity.Companion.CALL_PRIVACY_CONTACTS_ID) return "contacts"
        if (checked == ru.e6atb.chat.MainActivity.Companion.CALL_PRIVACY_CHATS_ID) return "chats"
        if (checked == ru.e6atb.chat.MainActivity.Companion.CALL_PRIVACY_NOBODY_ID) return "nobody"
        return "everyone"
    }

    private fun selectedInvitePrivacy(): String? {
        if (invitePrivacyGroup == null) return myInvitePrivacy
        val checked: Int = invitePrivacyGroup.getCheckedRadioButtonId()
        if (checked == ru.e6atb.chat.MainActivity.Companion.INVITE_PRIVACY_CONTACTS_ID) return "contacts"
        if (checked == ru.e6atb.chat.MainActivity.Companion.INVITE_PRIVACY_NOBODY_ID) return "nobody"
        return "everyone"
    }

    private fun showSettingsInterface() {
        val box: LinearLayout = settingsPage(getString(R.string.settings_interface), Page.SETTINGS_INTERFACE)
        showStatusCheck = checkBox(getString(R.string.settings_show_status), SessionStore.showStatus(this))
        box.addView(spaced(showStatusCheck))
        box.addView(spaced(row(primaryButton(getString(R.string.action_save), object : View.OnClickListener {
            override fun onClick(v: View?) {
                applySettings()
                showSettings()
            }
        }))))
    }

    private fun loadSessions(actionButton: View? = null, primaryStyle: Boolean = false) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        runButtonTask("sessions", actionButton, primaryStyle, object : Task {
            @Throws(Exception::class)
            override fun run() {
                val sessions: List<MST5.SessionInfo>? = c.sessions
                ui(object : Runnable {
                    override fun run() {
                        if (page !== Page.SETTINGS_SESSIONS) return
                        renderSessions(sessions)
                    }
                })
            }
        })
    }

    private fun saveUsername(actionButton: View? = null) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        val value = if (accountUsername == null) "" else accountUsername.getText().toString().trim()
        if (value.length == 0) return
        if ((myLogin == null || myLogin.length == 0)) {
            showUsernameReservationPaymentSheet(
                value,
                getString(R.string.username_reservation_payment_details_account),
                object : Runnable {
                    override fun run() {
                        saveUsernameConfirmed(value)
                    }
                }
            )
            return
        }
        saveUsernameConfirmed(value, actionButton)
    }

    private fun saveUsernameConfirmed(value: String?) {
        saveUsernameConfirmed(value, null)
    }

    private fun saveUsernameConfirmed(value: String?, actionButton: View?) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        status.setText(getString(R.string.status_saving_username))
        runButtonTask("username", actionButton, true, object : Task {
            @Throws(Exception::class)
            override fun run() {
                val user: MST5.User? = c.setUsername(value.orEmpty())
                applyOwnUser(user)
                SessionStore.save(this@MainActivity, server(), c.token(), myID, myLogin)
                ui(object : Runnable {
                    override fun run() {
                        status.setText(getString(R.string.status_username_saved))
                        showSettingsProfile()
                    }
                })
            }
        })
    }

    private fun saveName(actionButton: View? = null) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        val value: String = if (::accountName.isInitialized) accountName.text.toString().trim() else ""
        status.setText(getString(R.string.status_saving_name))
        runButtonTask("name", actionButton, true, object : Task {
            @Throws(Exception::class)
            override fun run() {
                val user: MST5.User? = c.setName(value)
                applyOwnUser(user)
                SessionStore.save(this@MainActivity, server(), c.token(), myID, myLogin)
                ui(object : Runnable {
                    override fun run() {
                        status.setText(getString(R.string.status_name_saved))
                        showSettingsProfile()
                    }
                })
            }
        })
    }

    private fun saveOwnDescription(actionButton: View?) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        val value: String = if (::accountDescription.isInitialized) accountDescription.text.toString().trim() else ""
        status.setText(getString(R.string.status_saving_description))
        runButtonTask("profile-description", actionButton, true, object : Task {
            @Throws(Exception::class)
            override fun run() {
                val user: MST5.User? = c.setProfileDescription("", value)
                applyOwnUser(user)
                ui(object : Runnable {
                    override fun run() {
                        status.setText(getString(R.string.status_description_saved))
                        showSettingsProfile()
                    }
                })
            }
        })
    }

    private fun savePrivacy(actionButton: View? = null) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        val messageMode = selectedMessagePrivacy()
        val callMode = selectedCallPrivacy()
        val inviteMode = selectedInvitePrivacy()
        status.setText(getString(R.string.status_saving_privacy))
        runButtonTask("privacy", actionButton, true, object : Task {
            @Throws(Exception::class)
            override fun run() {
                val user: MST5.User? = c.setPrivacy(messageMode.orEmpty(), callMode.orEmpty(), inviteMode.orEmpty())
                applyOwnUser(user)
                ui(object : Runnable {
                    override fun run() {
                        status.setText(getString(R.string.status_privacy_saved))
                        showSettings()
                    }
                })
            }
        })
    }

    private fun renderSessions(sessions: List<MST5.SessionInfo>?) {
        if (accountSessionsView == null) return
        accountSessionsView.removeAllViews()
        if (sessions == null || sessions.isEmpty()) {
            accountSessionsView.addView(sessionRow(getString(R.string.settings_no_sessions), muted))
            return
        }
        for (index in 0..<sessions.size) {
            if (index > 0) accountSessionsView.addView(listDivider())
            val item: MST5.SessionInfo = sessions.get(index)
            val session: MST5.SessionInfo = item
            val name: String? = if (item.label.length == 0)
                (if (item.current) getString(R.string.settings_current_session) else getString(R.string.settings_other_device))
            else
                item.label
            val text =
                name.toString() + (if (item.current) " · " + getString(R.string.settings_current_session) else "") +
                        "  " + formatMessageTime(item.lastSeen) +
                        "  #" + item.id
            val row: TextView = sessionRow(text, if (item.current) blend(primary, Color.WHITE, 0.18f) else textColor)
            row.setClickable(true)
            row.setOnClickListener(object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showSessionDetails(session)
                }
            })
            accountSessionsView.addView(row)
        }
        status.setText(getString(R.string.status_sessions_count, sessions.size))
    }

    private fun showSessionDetails(session: MST5.SessionInfo) {
        val name: String? = if (session.label.length == 0)
            (if (session.current) getString(R.string.settings_current_session) else getString(R.string.settings_other_device))
        else
            session.label
        val message = (getString(R.string.session_details_id, session.id) + "\n"
                + getString(R.string.session_details_created, Date(session.createdAt * 1000L).toString()) + "\n"
                + getString(R.string.session_details_last_seen, Date(session.lastSeen * 1000L).toString()) + "\n"
                + "Device: " + (if (session.deviceModel.length == 0) "Unknown" else session.deviceModel) + "\n"
                + getString(
            R.string.session_details_status,
            if (session.current) getString(R.string.settings_current_session) else getString(R.string.settings_other_device)
        ))
        val details: TextView = label(message)
        details.setTextColor(muted)
        details.setTextSize(14)
        details.setPadding(0, gap / 2, 0, gap / 2)
        // Session details deliberately use the same swipeable bottom sheet as
        // message actions, rather than the platform alert dialog.
        showContentDialog(
            name,
            details,
            getString(if (session.current) R.string.settings_logout else R.string.session_logout_device),
            object : Runnable {
                override fun run() {
                    revokeSession(session)
                }
            },
            getString(R.string.action_cancel)
        )
    }

    private fun revokeSession(session: MST5.SessionInfo) {
        val client: MST5? = ta
        if (client == null) return
        run("revoke_session", object : Task {
            @Throws(Exception::class)
            override fun run() {
                client.revokeSession(session.id)
                if (session.current) {
                    ui(object : Runnable {
                        override fun run() {
                            clearSessionAndShowLogin(R.string.status_logged_out)
                        }
                    })
                    return
                }
                val sessions: List<MST5.SessionInfo>? = client.sessions
                ui(object : Runnable {
                    override fun run() {
                        if (page === Page.SETTINGS_SESSIONS) renderSessions(sessions)
                    }
                })
            }
        })
    }

    private fun sessionRow(value: String?, color: Int): TextView {
        val row: TextView = label(value)
        row.setTextColor(color)
        row.setTextSize(15)
        row.setPadding(gap, dp(12), gap, dp(12))
        row.setBackgroundDrawable(pressable(Color.TRANSPARENT, surfaceHi, 0, dp(10)))
        val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        row.setLayoutParams(lp)
        return row
    }

    private fun revokeOtherSessions(actionButton: View? = null, primaryStyle: Boolean = true) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        runButtonTask("revoke_sessions", actionButton, primaryStyle, object : Task {
            @Throws(Exception::class)
            override fun run() {
                val revoked: Int = c.revokeOtherSessions()
                val sessions: List<MST5.SessionInfo>? = c.sessions
                ui(object : Runnable {
                    override fun run() {
                        if (page === Page.SETTINGS_SESSIONS) renderSessions(sessions)
                        status.setText(getString(R.string.status_revoked_sessions, revoked))
                    }
                })
            }
        })
    }

    private fun saveCloudPassword() {
        val value: String? = if (accountCloudPassword == null) "" else accountCloudPassword.getText().toString()
        setCloudPassword(value)
    }

    private fun clearCloudPassword() {
        if (accountCloudPassword != null) accountCloudPassword.setText("")
        setCloudPassword("")
    }

    private fun accountEmailText(): String {
        return if (myEmail == null) "" else myEmail.trim()
    }

    private fun requestAccountEmailCode(target: EditText?, sentMessage: String?) {
        requestAccountEmailCode(target, sentMessage, null, false)
    }

    private fun requestAccountEmailCode(
        target: EditText?,
        sentMessage: String?,
        actionButton: View?,
        primaryStyle: Boolean
    ) {
        val c: MST5? = ta
        val mail = accountEmailText()
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        if (mail.length == 0) {
            status.setText(getString(R.string.status_account_email_required))
            return
        }
        status.setText(getString(R.string.status_sending_code))
        runButtonTask("account_email_code", actionButton, primaryStyle, object : Task {
            @Throws(Exception::class)
            override fun run() {
                val debugCode: String? = c.startEmailAuth(mail)
                ui(object : Runnable {
                    override fun run() {
                        if (debugCode != null && debugCode.length > 0 && target != null) {
                            target.setText(debugCode)
                        }
                        status.setText(if (sentMessage == null || sentMessage.length == 0) getString(R.string.status_email_code_sent) else sentMessage)
                    }
                })
            }
        })
    }

    private fun setCloudPassword(value: String?) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        setCloudPasswordSaving(true, getString(R.string.cloud_password_saving))
        status.setText(getString(R.string.status_saving_cloud_password))
        run("cloud_password", object : Task {
            @Throws(Exception::class)
            override fun run() {
                try {
                    c.setCloudPassword(value.orEmpty())
                    ui(object : Runnable {
                        override fun run() {
                            val message: String? =
                                if (value == null || value.length == 0) getString(R.string.cloud_password_cleared) else getString(
                                    R.string.cloud_password_saved
                                )
                            setCloudPasswordSaving(false, message)
                            status.setText(
                                if (value == null || value.length == 0) getString(R.string.status_cloud_password_cleared) else getString(
                                    R.string.status_cloud_password_saved
                                )
                            )
                            if (accountCloudPassword != null) accountCloudPassword.setText("")
                        }
                    })
                } catch (e: Exception) {
                    ui(object : Runnable {
                        override fun run() {
                            setCloudPasswordSaving(false, getString(R.string.cloud_password_save_failed, errorText(e)))
                        }
                    })
                    throw e
                }
            }
        })
    }

    private fun setCloudPasswordSaving(saving: Boolean, message: String?) {
        setButtonBusy(
            cloudPasswordSaveButton,
            saving,
            getString(R.string.cloud_password_saving),
            getString(R.string.settings_save_password),
            true
        )
        setButtonEnabledStyle(cloudPasswordClearButton, !saving, false)
        if (accountCloudPassword != null) accountCloudPassword.setEnabled(!saving)
        if (accountCloudPasswordCode != null) accountCloudPasswordCode.setEnabled(!saving)
        if (cloudPasswordState != null) cloudPasswordState.setText(if (message == null) "" else message)
    }

    private fun resetCloudPassword() {
        val c: MST5? = ta
        val code = if (accountCloudPasswordCode == null) "" else accountCloudPasswordCode.getText().toString().trim()
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        if (code.length == 0) {
            status.setText(getString(R.string.status_email_code_required))
            return
        }
        setCloudPasswordSaving(true, getString(R.string.cloud_password_resetting))
        status.setText(getString(R.string.status_resetting_cloud_password))
        run("cloud_password_reset", object : Task {
            @Throws(Exception::class)
            override fun run() {
                try {
                    c.resetCloudPassword(code)
                    ui(object : Runnable {
                        override fun run() {
                            if (accountCloudPasswordCode != null) accountCloudPasswordCode.setText("")
                            if (accountCloudPassword != null) accountCloudPassword.setText("")
                            setCloudPasswordSaving(false, getString(R.string.cloud_password_cleared))
                            status.setText(getString(R.string.status_cloud_password_cleared))
                        }
                    })
                } catch (e: Exception) {
                    ui(object : Runnable {
                        override fun run() {
                            setCloudPasswordSaving(false, getString(R.string.cloud_password_save_failed, errorText(e)))
                        }
                    })
                    throw e
                }
            }
        })
    }

    private fun resetE2EKey() {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        status.setText(getString(R.string.status_resetting_e2e_key))
        run("e2e_reset", object : Task {
            @Throws(Exception::class)
            override fun run() {
                try {
                    c.resetE2EKey()
                    ui(object : Runnable {
                        override fun run() {
                            status.setText(getString(R.string.status_e2e_key_reset))
                        }
                    })
                } catch (e: Exception) {
                    ui(object : Runnable {
                        override fun run() {
                            status.setText(getString(R.string.e2e_key_reset_failed, errorText(e)))
                        }
                    })
                    throw e
                }
            }
        })
    }

    private fun deleteAccount() {
        val c: MST5? = ta
        val code = if (accountDeleteCode == null) "" else accountDeleteCode.getText().toString().trim()
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        if (code.length == 0) {
            status.setText(getString(R.string.status_email_code_required))
            return
        }
        status.setText(getString(R.string.status_deleting_account))
        if (deleteAccountCodeButton != null) setButtonEnabledStyle(deleteAccountCodeButton, false, false)
        run("delete_account", object : Task {
            @Throws(Exception::class)
            override fun run() {
                try {
                    c.deleteAccount(code)
                    ui(object : Runnable {
                        override fun run() {
                            clearSessionAndShowLogin(R.string.status_account_deleted)
                        }
                    })
                } catch (e: Exception) {
                    ui(object : Runnable {
                        override fun run() {
                            if (deleteAccountCodeButton != null) setButtonEnabledStyle(
                                deleteAccountCodeButton,
                                true,
                                false
                            )
                            status.setText(getString(R.string.delete_account_failed, errorText(e)))
                        }
                    })
                    throw e
                }
            }
        })
    }

    private fun requestEmailCode() {
        val url = server()
        val mail = if (::email.isInitialized) email.text.toString().trim() else ""
        if (mail.length == 0) return
        val resendRemainingMs = emailCodeResendRemainingMs(mail)
        if (resendRemainingMs > 0) {
            status.setText(getString(R.string.status_send_code_again_in, formatCodeCooldown(resendRemainingMs)))
            updateEmailCodeCooldown()
            return
        }
        setAuthLoading(true, true)
        status.setText(getString(R.string.status_sending_code))
        run("email_code", object : Task {
            @Throws(Exception::class)
            override fun run() {
                try {
                    val c: MST5 = MST5(this@MainActivity, url)
                    val debugCode: String? = c.startEmailAuth(mail)
                    ui(object : Runnable {
                        override fun run() {
                            emailCodeCooldownEmail = cooldownEmailKey(mail)
                            emailCodeSentAtMs = System.currentTimeMillis()
                            waitingEmailCode = true
                            authNeedsCloudPassword = false
                            pendingEmailCode = ""
                            showLogin()
                            if (debugCode != null && debugCode.length > 0 && emailCode != null) {
                                emailCode.setText(debugCode)
                            }
                            status.setText(getString(R.string.status_email_code_sent))
                        }
                    })
                } finally {
                    ui(object : Runnable {
                        override fun run() {
                            setAuthLoading(false, true)
                        }
                    })
                }
            }
        })
    }

    private fun currentEmailText(): String? {
        return if (email == null) "" else email.getText().toString().trim()
    }

    private fun cooldownEmailKey(value: String?): String {
        return if (value == null) "" else value.trim().toLowerCase(Locale.US)
    }

    private fun emailCodeResendRemainingMs(mail: String?): Long {
        if (emailCodeSentAtMs <= 0) return 0
        if (!emailCodeCooldownEmail.equals(cooldownEmailKey(mail))) return 0
        val elapsed: Long = System.currentTimeMillis() - emailCodeSentAtMs
        val remaining: Long = ru.e6atb.chat.MainActivity.Companion.EMAIL_CODE_RESEND_DELAY_MS - elapsed
        return if (remaining > 0) remaining else 0
    }

    private fun formatCodeCooldown(remainingMs: Long): String {
        val seconds: Long = Math.max(1, (remainingMs + 999) / 1000)
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
    }

    private fun updateEmailCodeCooldown() {
        if (resendEmailCodeButton == null) return
        val remaining = emailCodeResendRemainingMs(currentEmailText())
        if (remaining > 0) {
            setButtonEnabledStyle(resendEmailCodeButton, false, false)
            resendEmailCodeButton.setText(getString(R.string.action_send_again_timer, formatCodeCooldown(remaining)))
            main.removeCallbacks(emailCodeCooldownTick)
            main.postDelayed(emailCodeCooldownTick, 1000)
        } else {
            setButtonEnabledStyle(resendEmailCodeButton, true, false)
            resendEmailCodeButton.setText(getString(R.string.action_send_again))
            main.removeCallbacks(emailCodeCooldownTick)
        }
    }

    private fun authEmail() {
        val url = server()
        val mail = if (email == null) "" else email.getText().toString().trim()
        val code: String =
            (if (authNeedsCloudPassword) pendingEmailCode else (if (!::emailCode.isInitialized) "" else emailCode.getText()
                .toString()
                .trim()))!!
        val cloud: String = if (::password.isInitialized) password.text.toString() else ""
        if (mail.length == 0 || code.length == 0) return
        setAuthLoading(true, false)
        status.setText(getString(R.string.status_checking_code))
        run("email_auth", object : Task {
            @Throws(Exception::class)
            override fun run() {
                try {
                    ta?.close()
                    val client = MST5(this@MainActivity, url.orEmpty())
                    ta = client
                    val u: MST5.User? = client.verifyEmailAuth(mail, code, cloud)
                    finishAuth(url, u)
                } catch (e: RuntimeException) {
                    if (MST5.isCloudPasswordRequiredError(e)) {
                        ui(object : Runnable {
                            override fun run() {
                                pendingEmailCode = code
                                authNeedsCloudPassword = true
                                showLogin()
                                status.setText(getString(R.string.status_cloud_password_required))
                            }
                        })
                        return
                    }
                    throw e
                } finally {
                    ui(object : Runnable {
                        override fun run() {
                            setAuthLoading(false, false)
                        }
                    })
                }
            }
        })
    }

    private fun resetAuthCloudPassword() {
        val url = server()
        val mail = if (::email.isInitialized) email.text.toString().trim() else ""
        val code = pendingEmailCode.orEmpty().trim()
        if (mail.length == 0 || code.length == 0) {
            status.setText(getString(R.string.status_email_code_required))
            return
        }
        setAuthLoading(true, false)
        status.setText(getString(R.string.status_resetting_cloud_password))
        run("email_auth_cloud_reset", object : Task {
            @Throws(Exception::class)
            override fun run() {
                try {
                    ta?.close()
                    val client = MST5(this@MainActivity, url.orEmpty())
                    ta = client
                    val u: MST5.User? = client.resetCloudPassword(mail, code)
                    finishAuth(url, u)
                    ui(object : Runnable {
                        override fun run() {
                            status.setText(getString(R.string.status_cloud_password_cleared))
                        }
                    })
                } finally {
                    ui(object : Runnable {
                        override fun run() {
                            setAuthLoading(false, false)
                        }
                    })
                }
            }
        })
    }

    private fun finishAuth(url: String?, u: MST5.User?) {
        applyOwnUser(u)
        lastUpdate = 0
        waitingEmailCode = false
        authNeedsCloudPassword = false
        pendingEmailCode = ""

        seenMessages.clear()

        val client = ta ?: return
        SessionStore.save(this@MainActivity, url.orEmpty(), client.token(), myID, myLogin)
        SessionStore.lastUpdate(this@MainActivity, 0)
        SessionStore.backgroundLastUpdate(this@MainActivity, 0)
        SessionStore.notificationBootstrapComplete(this@MainActivity, false)

        startSyncService()

        ui(object : Runnable {
            override fun run() {
                status.setText(getString(R.string.status_online_as, displayOwnUser()))
                showChats()
            }
        })

        startPolling()
    }

    private fun applyOwnUser(u: MST5.User?) {
        if (u == null) return
        myID = u.id
        myEmail = u.email
        myLogin = u.login
        myNick = u.nick
        myDescription = u.description
        // Some profile-edit responses omit a pending avatar.  Keep the local
        // preview until /me supplies it again or the owner explicitly removes it.
        if (u.avatar != null) myAvatar = u.avatar
        myVerified = u.verified
        myBot = u.bot
        myMessagePrivacy = normalizePrivacy(u.messagePrivacy).orEmpty()
        myCallPrivacy = normalizePrivacy(u.callPrivacy).orEmpty()
        myInvitePrivacy = normalizeInvitePrivacy(u.invitePrivacy).orEmpty()
    }

    private fun applySettings() {
        val url = server()
        if (showStatusCheck != null) {
            SessionStore.showStatus(this, showStatusCheck.isChecked())
        }
        updateStatusVisibility()
        applyRootPadding(rootView)
        requestApplyInsetsCompat(rootView)
        val previous = ta
        if (previous != null && previous.token().isNotEmpty()) {
            val next = MST5(this, url.orEmpty(), previous.token(), myID, myLogin)
            ta = next
            previous.close()
            SessionStore.save(this, url.orEmpty(), next.token(), myID, myLogin)
            startSyncService()
        } else {
            ta?.close()
            ta = null
            stopPolling()
        }
        status.setText(getString(R.string.status_server_set))
    }

    private fun applyLanguage() {
        SessionStore.language(this, selectedLanguage())
        AppLocale.apply(this)
        setContentView(shell())
        setStatusBarColorCompat(bg)
        showSettings()
        status.setText(getString(R.string.status_language_set))
    }

    private fun logout() {
        val server: String? = SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER)
        val account: String? = OutboxDispatcher.accountKey(this)
        val pending: Int = OutboxStore.count(this, server, account)
        if (pending > 0) {
            showConfirmDialog(
                getString(R.string.logout_pending_title),
                getString(R.string.logout_pending_message, pending),
                getString(R.string.settings_logout),
                object : Runnable {
                    override fun run() {
                        OutboxStore.clear(this@MainActivity, server, account)
                        clearSessionAndShowLogin(R.string.status_logged_out)
                    }
                })
            return
        }
        clearSessionAndShowLogin(R.string.status_logged_out)
    }

    private fun handleInvalidToken() {
        clearSessionAndShowLogin(R.string.status_invalid_token)
    }

    private fun clearSessionAndShowLogin(statusRes: Int) {
        stopPolling()
        voiceCall.stop()
        stopService(Intent(this, MessageSyncService::class.java))
        SessionStore.clear(this)
        ta?.close()
        ta = null
        myID = ""
        myEmail = ""
        myLogin = ""
        myNick = ""
        myAvatar = null
        myVerified = false
        myBot = false
        myMessagePrivacy = "everyone"
        myCallPrivacy = "everyone"
        myInvitePrivacy = "everyone"
        currentPeer = ""
        currentPeerUser = null
        currentPeerBanned = false
        currentPeerBannedByMe = false
        currentPeerBannedMe = false
        waitingEmailCode = false
        authNeedsCloudPassword = false
        pendingEmailCode = ""
        seenMessages.clear()
        showLogin()
        status.setText(getString(statusRes))
    }

    private fun loadChats(actionButton: View? = null, primaryStyle: Boolean = false) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        runButtonTask("chats", actionButton, primaryStyle, object : Task {
            @Throws(Exception::class)
            override fun run() {
                val chats: List<MST5.Chat>? = c.chats
                cacheSaveChats(chats)

                ui(object : Runnable {
                    override fun run() {
                        renderChats(chats, getString(R.string.source_online))
                    }
                })
            }
        })
    }

    private fun renderChats(chats: List<MST5.Chat>?, source: String?) {
        if (chats == null || chatRows == null) return
        chatData.clear()
        chatData.addAll(chats)
        renderChatRows(if (chatSearch == null) "" else chatSearch.getText().toString())
        status.setText(getString(R.string.status_chats_count, chats.size, source))
    }

    private fun renderChatRows(query: String?) {
        if (chatRows == null) return
        val filter = if (query == null) "" else query.trim().toLowerCase(Locale.US)
        visibleChatData.clear()
        chatRows!!.clear()
        for (chat in chatData) {
            if (chat.peer != null && chat.peer.login != null && chat.peer.login.equals(currentPeer)) {
                currentPeerBanned = chat.banned
                currentPeerBannedByMe = chat.bannedByMe
                currentPeerBannedMe = chat.bannedMe
            }
            val last: String = (if (chat.last == null) "" else chatLastText(chat.last))!!
            val title = chatPeerTitle(chat.peer)
            if (filter.length > 0 && !title.toLowerCase(Locale.US).contains(filter) && !last.toLowerCase(Locale.US)
                    .contains(filter)
            ) continue
            visibleChatData.add(chat)
            chatRows.add(
                MessageRow.chat(
                    title,
                    last,
                    chat.peer != null && chat.peer.verified,
                    if (chat.last == null) 0 else chat.last.date,
                    chat.unreadCount
                )
            )
        }
    }

    private fun loadHistory(afterServerLoad: Runnable? = null) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        val requestedPeer: String = (if (peer == null) currentPeer else peer.getText().toString().trim())!!
        if (requestedPeer.isEmpty()) return
        if (!requestedPeer.equals(currentPeer)) {
            openChatImmediately(requestedPeer, null, false, false, false, afterServerLoad)
            return
        }
        val peerName = currentPeer
        val openGeneration = chatOpenGeneration
        val requestGeneration = ++historyRequestGeneration
        loadingOlderMessages = false
        historyLoaded = false
        hasOlderMessages = false
        val context: android.content.Context? = getApplicationContext()
        val server: String? = SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER)
        val login = myLogin
        historyCacheIo.execute(object : Runnable {
            override fun run() {
                val cached: List<MST5.Message>? = ChatCache.loadHistory(context, server, login, peerName)
                ui(object : Runnable {
                    override fun run() {
                        if (acceptsHistoryResult(openGeneration, requestGeneration)) {
                            renderHistory(cached, peerName!!, true)
                        }
                    }
                })
                if (openGeneration != chatOpenGeneration || requestGeneration != historyRequestGeneration) return
                this@MainActivity.run("history", object : Task {
                    @Throws(Exception::class)
                    override fun run() {
                        val pageData: MST5.HistoryPage
                        try {
                            pageData =
                                c.getHistoryPageBefore(peerName, 0, ru.e6atb.chat.MainActivity.Companion.HISTORY_PAGE)
                        } catch (error: Exception) {
                            ui(object : Runnable {
                                override fun run() {
                                    if (!acceptsHistoryResult(openGeneration, requestGeneration)) return
                                    if (MST5.isInvalidTokenError(error)) handleInvalidToken()
                                    else status.setText(getString(R.string.status_operation_error, errorText(error)))
                                }
                            })
                            return
                        }
                        try {
                            c.markRead(peerName)
                        } catch (ignored: Exception) {
                        }
                        val resolvedPeer = resolvedPeerName(pageData.peer, peerName)
                        cacheSaveHistory(resolvedPeer, pageData.messages)
                        if (!resolvedPeer.equals(peerName)) cacheSaveHistory(peerName, pageData.messages)
                        ui(object : Runnable {
                            override fun run() {
                                if (!acceptsHistoryResult(openGeneration, requestGeneration)) return
                                currentPeer = resolvedPeer
                                if (peer != null) peer.setText(resolvedPeer)
                                if (pageData.peer != null) currentPeerUser = pageData.peer
                                refreshCurrentPeerNameView()
                                updateCallButton()
                                renderHistory(pageData.messages, resolvedPeer, false)
                                if (afterServerLoad != null) afterServerLoad.run()
                            }
                        })
                    }
                })
            }
        })
    }

    private fun acceptsHistoryResult(openGeneration: Int, requestGeneration: Int): Boolean {
        return page === Page.CHAT && openGeneration == chatOpenGeneration && requestGeneration == historyRequestGeneration && messageRows != null
    }

    private fun renderHistory(history: List<MST5.Message>?, peerName: String, cached: Boolean) {
        if (messageRows == null || !peerName.equals(currentPeer) || history == null) return
        updateCurrentPeerUser(history, peerName)
        seenMessages.clear()
        oldestMessage = 0
        val rows: ArrayList<MessageRow> = ArrayList<MessageRow>()
        for (message in history) {
            if (message != null && seenMessages.add(message.id)) {
                if (oldestMessage == 0L || message.id < oldestMessage) oldestMessage = message.id
                rows.add(toMessageRow(message))
            }
        }
        for (entry in OutboxStore.load(
            this,
            SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
            OutboxDispatcher.accountKey(this)
        )) {
            if (!peerName.equals(entry.peer) || entry.commentPostId > 0) continue
            val pending: MST5.Message = outboxMessage(entry)
            if (seenMessages.add(pending.id)) rows.add(toMessageRow(pending))
        }
        messageRows!!.replaceRows(rows)
        historyLoaded = !cached
        hasOlderMessages = !cached && history.size === ru.e6atb.chat.MainActivity.Companion.HISTORY_PAGE
        if (messageList != null && messageRows!!.count > 0) {
            messageList.setSelection(messageRows!!.count - 1)
        }
        refreshChatInput()
        status.setText(
            getString(
                R.string.status_messages_count,
                history.size,
                if (cached) getString(R.string.status_cached_suffix) else ""
            )
        )
    }

    private fun updateCurrentPeerUser(history: List<MST5.Message>?, peerName: String?) {
        if (peerName == null || history == null) return
        if (currentPeerUser != null && peerName.equals(resolvedPeerName(currentPeerUser, peerName))) return
        for (message in history) {
            val candidate: MST5.User? = messagePeerUser(message)
            if (candidate != null && peerName.equals(resolvedPeerName(candidate, peerName))) {
                currentPeerUser = candidate
                refreshCurrentPeerNameView()
                updateCallButton()
                return
            }
        }
    }

    private fun loadOlderHistory() {
        val c: MST5? = ta
        if (c == null || loadingOlderMessages || oldestMessage <= 0) return
        val peerName = currentPeer
        val openGeneration = chatOpenGeneration
        val before = oldestMessage
        loadingOlderMessages = true
        run("older", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val history: List<MST5.Message> =
                    c.getHistoryBefore(peerName, before, ru.e6atb.chat.MainActivity.Companion.HISTORY_PAGE)

                ui(object : Runnable {
                    override fun run() {
                        loadingOlderMessages = false

                        if (openGeneration != chatOpenGeneration || messageRows == null || messageList == null || !peerName!!.equals(
                                currentPeer
                            )
                        ) {
                            return
                        }

                        val first: Int = messageList.getFirstVisiblePosition()

                        val topView: View? = messageList.getChildAt(0)

                        val top = if (topView == null) 0 else topView.getTop()

                        val rows: ArrayList<MessageRow> = ArrayList<MessageRow>()

                        for (m in history) {
                            if (seenMessages.add(m.id)) {
                                if (oldestMessage == 0L || m.id < oldestMessage) {
                                    oldestMessage = m.id
                                }

                                rows.add(toMessageRow(m))
                            }
                        }

                        hasOlderMessages =
                            history.size === ru.e6atb.chat.MainActivity.Companion.HISTORY_PAGE && rows.size > 0

                        if (!rows.isEmpty()) {
                            messageRows!!.insertRows(rows, 0)
                            messageList.setSelectionFromTop(first + rows.size, top)
                        }
                    }
                })
            }
        })
    }

    private fun send() {
        if (currentPeerBanned) {
            status.setText(getString(R.string.chat_banned))
            return
        }
        currentPeer = if (peer == null) currentPeer else peer.getText().toString().trim()
        val msg = if (text == null) "" else text.getText().toString().trim()
        if (currentPeer.isEmpty() || (msg.isEmpty() && composerMedia.isEmpty()) || composerSending) return
        if (editingMessage != null) {
            sendEditedMediaMessage(msg, ArrayList<ComposerMedia>(composerMedia))
            return
        }
        if (composerMedia.isEmpty()) {
            sendChatMessage(currentPeer, msg, true)
            return
        }
        val client: MST5? = ta
        if (client == null) return
        val peerName = currentPeer
        val room = currentPeerIsRoom()
        val commentPostId: Long =
            if (page === Page.CHANNEL_COMMENTS && currentCommentPost != null) currentCommentPost!!.id else 0
        val replyId: Long = if (replyToMessage == null) 0 else replyToMessage!!.id
        val snapshot: ArrayList<ComposerMedia> = ArrayList<ComposerMedia>(composerMedia)
        composerSending = true
        run("quote_media_message", object : Task {
            @Throws(Exception::class)
            override fun run() {
                try {
                    val media: ArrayList<MST5.MessageMedia> = miniMedia(snapshot, false)
                    val quote: MST5.MediaQuote = client.quoteMedia(media)
                    if (quote.dsrRequired <= 0) {
                        queueMediaMessage(client, peerName, room, msg, snapshot, 0, commentPostId, replyId)
                    } else {
                        ui(object : Runnable {
                            override fun run() {
                                composerSending = false
                                val label: String? =
                                    if (snapshot.size === 1) snapshot[0].name.orEmpty() else "${snapshot.size} files"
                                showSwipeConfirmDialog(
                                    getString(R.string.media_payment_title),
                                    getString(
                                        R.string.media_payment_detail,
                                        label,
                                        formatBytes(quote.sizeBytes),
                                        quote.dsrRequired
                                    ),
                                    getString(R.string.media_payment_slide_hint, quote.dsrRequired),
                                    object : Runnable {
                                        override fun run() {
                                            if (composerSending) return
                                            composerSending = true
                                            this@MainActivity.run("queue_media_message", object : Task {
                                                @Throws(Exception::class)
                                                override fun run() {
                                                    try {
                                                        queueMediaMessage(
                                                            client,
                                                            peerName,
                                                            room,
                                                            msg,
                                                            snapshot,
                                                            quote.dsrRequired,
                                                            commentPostId,
                                                            replyId
                                                        )
                                                    } catch (error: Exception) {
                                                        ui(object : Runnable {
                                                            override fun run() {
                                                                composerSending = false
                                                            }
                                                        })
                                                        throw error
                                                    }
                                                }
                                            })
                                        }
                                    },
                                    object : Runnable {
                                        override fun run() {
                                            composerSending = false
                                        }
                                    }
                                )
                            }
                        })
                    }
                } catch (error: Exception) {
                    ui(object : Runnable {
                        override fun run() {
                            composerSending = false
                        }
                    })
                    throw error
                }
            }
        })
    }

    private fun miniMedia(items: List<ComposerMedia>, withSources: Boolean): ArrayList<MST5.MessageMedia> {
        val out: ArrayList<MST5.MessageMedia> = ArrayList<MST5.MessageMedia>()
        for (index in 0..<items.size) {
            val item: ComposerMedia = items[index]
            var source: MST5.UploadSource? = null
            if (withSources) source = object : MST5.UploadSource {
                @Throws(Exception::class)
                override fun open(): InputStream {
                    if (!item.localPath.isNullOrEmpty()) return FileInputStream(File(item.localPath!!))
                    if (item.uri != null) return contentResolver.openInputStream(item.uri!!)
                        ?: throw IOException(getString(R.string.status_file_not_available))
                    throw IOException(getString(R.string.status_file_not_available))
                }

                @Throws(Exception::class)
                override fun openDescriptor(): ParcelFileDescriptor? {
                    if (!item.localPath.isNullOrEmpty()) {
                        return ParcelFileDescriptor.open(File(item.localPath!!), ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                    return if (item.uri == null) null else contentResolver.openFileDescriptor(item.uri!!, "r")
                }
            }
            out.add(
                MessageMedia(
                    String.format(Locale.US, "attachment-%06d", index),
                    item.fileId.orEmpty(),
                    item.name.orEmpty(), item.mime.orEmpty(), item.size, source, item.photo
                )
            )
        }
        return out
    }

    private fun sendEditedMediaMessage(messageText: String, items: ArrayList<ComposerMedia>) {
        val client: MST5? = ta
        val original: MST5.Message? = editingMessage
        if (client == null || original == null || (messageText.length == 0 && items.isEmpty())) return
        composerSending = true
        run("quote_media_edit", object : Task {
            @Throws(Exception::class)
            override fun run() {
                try {
                    val quote: MST5.MediaQuote = client.quoteMedia(miniMedia(items, false))
                    if (quote.dsrRequired <= 0) {
                        commitEditedMediaMessage(client, original, messageText, items, 0)
                    } else {
                        ui(object : Runnable {
                            override fun run() {
                                composerSending = false
                                showSwipeConfirmDialog(
                                    getString(R.string.media_payment_title),
                                    getString(
                                        R.string.media_payment_detail,
                                        "${items.size} files",
                                        formatBytes(quote.sizeBytes),
                                        quote.dsrRequired
                                    ),
                                    getString(R.string.media_payment_slide_hint, quote.dsrRequired),
                                    object : Runnable {
                                        override fun run() {
                                            if (composerSending) return
                                            composerSending = true
                                            this@MainActivity.run("commit_media_edit", object : Task {
                                                @Throws(Exception::class)
                                                override fun run() {
                                                    try {
                                                        commitEditedMediaMessage(
                                                            client,
                                                            original,
                                                            messageText,
                                                            items,
                                                            quote.dsrRequired
                                                        )
                                                    } catch (error: Exception) {
                                                        ui(object : Runnable {
                                                            override fun run() {
                                                                composerSending = false
                                                            }
                                                        })
                                                        throw error
                                                    }
                                                }
                                            })
                                        }
                                    },
                                    object : Runnable {
                                        override fun run() {
                                            composerSending = false
                                        }
                                    }
                                )
                            }
                        })
                    }
                } catch (error: Exception) {
                    ui(object : Runnable {
                        override fun run() {
                            composerSending = false
                        }
                    })
                    throw error
                }
            }
        })
    }

    @Throws(Exception::class)
    private fun commitEditedMediaMessage(
        client: MST5, original: MST5.Message,
        messageText: String?, items: List<ComposerMedia>,
        maxDsr: Long
    ) {
        val clientMessageId: String? = UUID.randomUUID().toString()
        val prepared: JSONObject = client.prepareMessage(
            currentPeer,
            messageText.orEmpty(),
            clientMessageId,
            currentPeerIsRoom(),
            original.replyToMessageId
        )
        prepared.put("message_id", original.id)
        if (original.commentPostId > 0) prepared.put("comment_post_id", original.commentPostId)
        val transfer: MST5.TransferControl = TransferControl(object : MST5.ProgressListener {
            override fun onProgress(completed: Long, total: Long) {
                ui(object : Runnable {
                    override fun run() {
                        status.setText(if (total <= 0) getString(R.string.status_preparing_file) else (completed * 100L / total).toString() + "%")
                    }
                })
            }
        })
        val updated: MST5.Message? = client.sendMessageWithMedia(prepared, miniMedia(items, true), transfer, maxDsr)
        ui(object : Runnable {
            override fun run() {
                composerSending = false
                editingMessage = null
                recycleComposerPreviews()
                composerMedia.clear()
                renderComposerMedia()
                if (text != null) text.setText("")
                status.setText("")
                applyMessageUpdate(updated)
            }
        })
    }

    @Throws(Exception::class)
    private fun queueMediaMessage(
        client: MST5, peerName: String?, room: Boolean,
        messageText: String?, items: List<ComposerMedia>,
        maxDsr: Long, commentPostId: Long, replyId: Long
    ) {
        val clientMessageId: String? = UUID.randomUUID().toString()
        val attachments: ArrayList<OutboxStore.Attachment> = ArrayList<OutboxStore.Attachment>()
        for (index in 0..<items.size) {
            val item: ComposerMedia = items[index]
            val attachment: OutboxStore.Attachment = Attachment()
            attachment.clientId = String.format(Locale.US, "attachment-%06d", index)
            attachment.fileId = item.fileId.orEmpty()
            attachment.name = item.name.orEmpty()
            attachment.mime = item.mime.orEmpty()
            attachment.localPath = item.localPath.orEmpty()
            attachment.sourceUri = if (item.uri == null) "" else item.uri.toString()
            attachment.size = item.size
            attachment.photo = item.photo
            attachments.add(attachment)
        }
        val entry: Entry = OutboxStore.enqueueMedia(
            this,
            SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
            OutboxDispatcher.accountKey(this),
            peerName,
            room,
            messageText,
            attachments,
            clientMessageId,
            maxDsr,
            commentPostId,
            replyId,
            room && currentPeerE2EEnabled()
        )
        ui(object : Runnable {
            override fun run() {
                composerSending = false
                recycleComposerPreviews()
                composerMedia.clear()
                renderComposerMedia()
                if (text != null) text.setText("")
                if (replyId > 0) clearReply()
                addMessageRow(outboxMessage(entry), false)
                if (messageList != null) messageList.setSelection(messageRows!!.count - 1)
                dispatchOutbox(client)
            }
        })
    }

    private fun sendBotStart(actionButton: View? = null) {
        if (currentPeer == null || currentPeer.length == 0) return
        sendChatMessage(currentPeer, "/start", false, actionButton, true)
    }

    private fun sendChatMessage(peerName: String?, msg: String?, clearInput: Boolean) {
        sendChatMessage(peerName, msg, clearInput, null, false)
    }

    private fun sendChatMessage(
        peerName: String?,
        msg: String?,
        clearInput: Boolean,
        actionButton: View?,
        primaryStyle: Boolean
    ) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        if (peerName == null || peerName.length == 0 || msg == null || msg.length == 0) return
        val replyToMessageId: Long = if (clearInput && replyToMessage != null) replyToMessage!!.id else 0
        setActionButtonLoading(actionButton, true, primaryStyle)
        try {
            val entry: Entry = OutboxStore.enqueueText(
                this,
                SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
                OutboxDispatcher.accountKey(this),
                peerName,
                currentPeerIsRoom(),
                msg,
                replyToMessageId,
                currentPeerE2EEnabled()
            )
            addMessageRow(outboxMessage(entry), false)
            if (clearInput && text != null) text.setText("")
            if (replyToMessageId > 0) clearReply()
            if (messageList != null) messageList.setSelection(messageRows!!.count - 1)
            dispatchOutbox(c)
        } catch (e: Exception) {
            status.setText(errorText(e))
        } finally {
            setActionButtonLoading(actionButton, false, primaryStyle)
        }
    }

    private fun handleMessageButton(message: MST5.Message?, button: MST5.Button?, clickedButton: Button?) {
        if (button == null) return
        if (button.url != null && button.url.length > 0) {
            openUrl(button.url)
            return
        }
        if (button.payDsr > 0) {
            val payTo: String? = if (message == null || message.from == null) "" else message.from.login
            showDastarsPaymentSheet(payTo, button.payDsr)
            return
        }
        if (button.callback == null || button.callback.length == 0) return
        if (button.swipeConfirm) {
            showCallbackSwipeConfirmation(message, button, clickedButton)
            return
        }
        sendMessageCallback(message, button, clickedButton)
    }

    private fun showCallbackSwipeConfirmation(message: MST5.Message?, button: MST5.Button, clickedButton: Button?) {
        val dialog: Dialog = Dialog(this)
        val box: LinearLayout = dialogBox()
        box.addView(title(getString(R.string.callback_confirm_title)), LinearLayout.LayoutParams(-1, -2))
        val details: TextView = label(getString(R.string.callback_confirm_body, button.text))
        details.setTextColor(muted)
        val detailsLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        detailsLp.setMargins(0, gap / 2, 0, gap)
        box.addView(details, detailsLp)
        val slider: PaymentSliderView = paymentSlider(getString(R.string.callback_swipe_confirm))
        slider.setContentDescription(getString(R.string.callback_swipe_confirm))
        slider.setOnConfirmAction(object : Runnable {
            override fun run() {
                dialog.dismiss()
                sendMessageCallback(message, button, clickedButton)
            }
        })
        box.addView(slider, LinearLayout.LayoutParams(-1, dp(56)))
        val cancel: Button = button(getString(R.string.action_cancel), object : View.OnClickListener {
            override fun onClick(v: View?) {
                dialog.dismiss()
            }
        })
        val cancelLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        cancelLp.setMargins(0, gap, 0, 0)
        box.addView(cancel, cancelLp)
        setScrollableDialogContent(dialog, box)
        showStyledDialog(dialog)
    }

    private fun sendMessageCallback(message: MST5.Message?, button: MST5.Button, clickedButton: Button?) {
        val c: MST5? = ta
        if (c == null || message == null || message.from == null || message.from.login.length == 0) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        val botLogin: String? = message.from.login
        if (clickedButton != null) {
            setButtonEnabledStyle(clickedButton, false, true)
            setButtonRequestBusy(clickedButton, true)
        }
        run("button_callback", object : Task {
            @Throws(Exception::class)
            override fun run() {
                try {
                    c.sendCallback(botLogin.orEmpty(), message.id, button.callback)
                } finally {
                    ui(object : Runnable {
                        override fun run() {
                            if (clickedButton != null) {
                                setButtonRequestBusy(clickedButton, false)
                                setButtonEnabledStyle(clickedButton, true, true)
                            }
                        }
                    })
                }
            }
        })
    }

    private fun startVoice() {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        currentPeer = if (peer == null) currentPeer else peer.getText().toString().trim()
        if (currentPeer.isEmpty()) return
        val peerName = currentPeer
        if (currentPeerIsSelfChat()) {
            status.setText(getString(R.string.status_self_calls_not_available))
            return
        }
        if (!hasPermissionCompat(ru.e6atb.chat.MainActivity.Companion.PERMISSION_RECORD_AUDIO)) {
            requestPermissionsCompat(
                arrayOf<String>(
                    ru.e6atb.chat.MainActivity.Companion.PERMISSION_RECORD_AUDIO
                ), ru.e6atb.chat.MainActivity.Companion.REQ_MICROPHONE
            )
            status.setText(getString(R.string.status_allow_microphone_call_again))
            return
        }
        if (voiceCall.running()) {
            status.setText(getString(R.string.status_call_already_active))
            return
        }
        activeVoiceRoom = false
        ++voiceConnectGeneration
        setCallState("calling", peerName)
        run("call", object : Task {
            @Throws(Exception::class)
            override fun run() {
                try {
                    c.sendCall(peerName, "invite")
                } catch (e: Exception) {
                    ui(object : Runnable {
                        override fun run() {
                            if (peerName!!.equals(activeCallPeer) && "calling".equals(callState)) {
                                setCallState("failed", peerName)
                            }
                            status.setText(getString(R.string.status_call_error, errorText(e)))
                        }
                    })
                }
            }
        })
        status.setText(getString(R.string.status_calling_peer, peerName))
    }

    private fun toggleVoice() {
        if (currentPeerBanned) {
            status.setText(getString(R.string.chat_banned))
            return
        }
        if (currentPeerIsSelfChat()) {
            status.setText(getString(R.string.status_self_calls_not_available))
            return
        }
        if (currentPeerIsBot()) {
            status.setText(getString(R.string.status_bots_cannot_receive_calls))
            return
        }
        if (currentPeerIsChannel()) {
            status.setText(getString(R.string.status_voice_not_available_for_channels))
            return
        }
        if (currentPeerIsGroup()) {
            if (voiceCall.running() || (currentPeer!!.equals(activeCallPeer) && !"idle".equals(callState) && !"failed".equals(
                    callState
                ))
            ) {
                endVoice()
            } else {
                startGroupVoice()
            }
            return
        }
        if (voiceCall.running() || (!"idle".equals(callState) && !"failed".equals(callState))) endVoice()
        else startVoice()
    }

    private fun startGroupVoice() {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        if (currentPeer == null || currentPeer.length == 0) return
        if (!hasPermissionCompat(ru.e6atb.chat.MainActivity.Companion.PERMISSION_RECORD_AUDIO)) {
            pendingVoiceRoom = currentPeer
            requestPermissionsCompat(
                arrayOf<String>(ru.e6atb.chat.MainActivity.Companion.PERMISSION_RECORD_AUDIO),
                ru.e6atb.chat.MainActivity.Companion.REQ_MICROPHONE
            )
            status.setText(getString(R.string.status_allow_microphone_call_again))
            return
        }
        if (voiceCall.running()) {
            status.setText(getString(R.string.status_call_already_active))
            return
        }
        val roomName = currentPeer
        activeVoiceRoom = true
        ++voiceConnectGeneration
        setCallState("connecting", roomName)
        startVoiceConnection(c, roomName, getString(R.string.status_joining_voice_channel))
    }

    private fun pickImage() {
        val i: Intent =
            Intent(if (Build.VERSION.SDK_INT >= 19) Intent.ACTION_OPEN_DOCUMENT else Intent.ACTION_GET_CONTENT)
        i.setType("image/*")
        i.addCategory(Intent.CATEGORY_OPENABLE)
        if (Build.VERSION.SDK_INT >= 18) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        if (Build.VERSION.SDK_INT >= 19) i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        startActivityForResult(
            Intent.createChooser(i, getString(R.string.chooser_select_picture)),
            ru.e6atb.chat.MainActivity.Companion.REQ_PICK_IMAGE
        )
    }

    private fun pickAvatar() {
        val i: Intent =
            Intent(if (Build.VERSION.SDK_INT >= 19) Intent.ACTION_OPEN_DOCUMENT else Intent.ACTION_GET_CONTENT)
        i.setType("image/*")
        i.addCategory(Intent.CATEGORY_OPENABLE)
        if (Build.VERSION.SDK_INT >= 19) i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        startActivityForResult(
            Intent.createChooser(i, "Select avatar"),
            ru.e6atb.chat.MainActivity.Companion.REQ_PICK_AVATAR
        )
    }

    private fun pickFile() {
        val i: Intent =
            Intent(if (Build.VERSION.SDK_INT >= 19) Intent.ACTION_OPEN_DOCUMENT else Intent.ACTION_GET_CONTENT)
        i.setType("*/*")
        i.addCategory(Intent.CATEGORY_OPENABLE)
        if (Build.VERSION.SDK_INT >= 18) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        if (Build.VERSION.SDK_INT >= 19) i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        startActivityForResult(
            Intent.createChooser(i, getString(R.string.chooser_select_file)),
            ru.e6atb.chat.MainActivity.Companion.REQ_PICK_FILE
        )
    }

    private fun showAttachmentActions() {
        showActionDialog(
            arrayOf(
                getString(R.string.attachment_photo),
                getString(R.string.attachment_file),
                getString(R.string.payment_transfer_title)
            ), object : ChoiceHandler {
                override fun onChoice(which: Int) {
                    if (which == 0) {
                        pickImage()
                    } else if (which == 1) {
                        pickFile()
                    } else if (which == 2) {
                        showDastarsTransferDialog(currentPeer)
                    }
                }
            })
    }

    private fun showDastarsTransferDialog(defaultRecipient: String?) {
        val recipient = if (defaultRecipient == null) "" else defaultRecipient.trim()
        val recipientField: EditText? = if (recipient.length == 0)
            input(getString(R.string.wallet_to_hint), false)
        else
            null
        val amountField: EditText = input(getString(R.string.wallet_amount_hint), false)
        amountField.setInputType(InputType.TYPE_CLASS_NUMBER)
        val commentField: EditText = input(getString(R.string.wallet_comment_hint), false)
        val box: LinearLayout = LinearLayout(this)
        box.setOrientation(LinearLayout.VERTICAL)
        box.setPadding(0, gap, 0, 0)
        val recipientView: TextView = label(getString(R.string.payment_recipient, recipient))
        recipientView.setTextColor(muted)
        val balanceView: TextView = label(walletBalanceLabel())
        balanceView.setTextColor(blend(primary, Color.WHITE, 0.18f))
        if (recipientField == null) box.addView(spaced(recipientView))
        else box.addView(spaced(recipientField))
        box.addView(spaced(balanceView))
        box.addView(spaced(amountField))
        box.addView(spaced(commentField))
        refreshWalletBalanceLabel(balanceView)
        showContentDialog(
            getString(R.string.payment_transfer_title),
            box,
            getString(R.string.action_send),
            object : Runnable {
                override fun run() {
                    val target: String? =
                        if (recipientField == null) recipient else recipientField.getText().toString().trim()
                    transferDastars(
                        target,
                        amountField.getText().toString().trim(),
                        commentField.getText().toString().trim()
                    )
                }
            },
            getString(R.string.action_cancel)
        )
    }

    private fun showDastarsPaymentSheet(to: String?, amount: Long) {
        if (to == null || to.length == 0 || amount <= 0) {
            status.setText(getString(R.string.status_bad_dsr_invoice))
            return
        }
        val dialog: Dialog = Dialog(this)
        val box: LinearLayout = dialogBox()

        val title: TextView = title(getString(R.string.payment_title))
        title.setText(getString(R.string.payment_pay_title, amount))
        box.addView(title, LinearLayout.LayoutParams(-1, -2))

        val details: TextView = label(getString(R.string.payment_recipient, to))
        details.setTextColor(muted)
        val detailsLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        detailsLp.setMargins(0, 0, 0, gap)
        box.addView(details, detailsLp)

        val slider: PaymentSliderView = paymentSlider(getString(R.string.payment_slide_hint))
        slider.setContentDescription(getString(R.string.payment_slide_hint))
        slider.setOnConfirmAction(object : Runnable {
            override fun run() {
                dialog.dismiss()
                transferDastars(to, java.lang.String.valueOf(amount), "")
            }
        })
        box.addView(slider, LinearLayout.LayoutParams(-1, dp(56)))

        val cancel: Button = button(getString(R.string.action_cancel), object : View.OnClickListener {
            override fun onClick(v: View?) {
                dialog.dismiss()
            }
        })
        val cancelLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        cancelLp.setMargins(0, gap, 0, 0)
        box.addView(cancel, cancelLp)

        setScrollableDialogContent(dialog, box)
        showStyledDialog(dialog)
    }

    private fun setScrollableDialogContent(dialog: Dialog, contentView: View?) {
        val scroll: BoundedScrollView = BoundedScrollView(
            this,
            getResources().getDisplayMetrics().heightPixels * 4 / 5
        )
        scroll.setFillViewport(false)
        scroll.setBackgroundColor(Color.TRANSPARENT)
        scroll.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY)
        scroll.setVerticalScrollBarEnabled(true)
        scroll.addView(contentView, LinearLayout.LayoutParams(-1, -2))
        dialog.setContentView(scroll)
    }

    private fun transferDastars(to: String?, rawAmount: String?, comment: String?) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        if (to == null || to.length == 0 || rawAmount == null || rawAmount.length == 0) return
        val amount: Long
        try {
            amount = java.lang.Long.parseLong(rawAmount)
        } catch (e: NumberFormatException) {
            status.setText(getString(R.string.status_bad_dsr_amount))
            return
        }
        if (amount <= 0) {
            status.setText(getString(R.string.status_bad_dsr_amount))
            return
        }
        run("wallet_send", object : Task {
            @Throws(Exception::class)
            override fun run() {
                c.sendDastars(to, amount, if (comment == null) "" else comment.trim())
                ui(object : Runnable {
                    override fun run() {
                        status.setText(getString(R.string.status_dsr_sent))
                        if (page === Page.WALLET) loadWallet()
                        if (page === Page.WALLET_HISTORY) loadWalletHistory()
                    }
                })
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        if (requestCode == ru.e6atb.chat.MainActivity.Companion.REQ_QR_SCAN) {
            openOAuthDeviceRequest(data.getStringExtra(QrScannerActivity.EXTRA_RESULT))
            return
        }
        if (requestCode == ru.e6atb.chat.MainActivity.Companion.REQ_PICK_AVATAR) {
            val avatar: Uri? = data.getData()
            if (avatar != null) uploadAvatar(avatar)
            return
        }
        val selected: ArrayList<Uri> = ArrayList<Uri>()
        if (Build.VERSION.SDK_INT >= 16 && data.getClipData() != null) {
            val clips: android.content.ClipData = data.clipData!!
            var i = 0
            while (i < clips.getItemCount() && selected.size < 10 - composerMedia.size) {
                val uri: Uri? = clips.getItemAt(i).getUri()
                if (uri != null) selected.add(uri)
                i++
            }
        } else if (data.getData() != null) {
            selected.add(data.data!!)
        }
        for (uri in selected) {
            if (Build.VERSION.SDK_INT >= 19) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (ignored: Exception) {
                }
            }
            addComposerMedia(uri, requestCode == ru.e6atb.chat.MainActivity.Companion.REQ_PICK_IMAGE)
        }
    }

    private fun uploadAvatar(uri: Uri?) {
        val client: MST5? = ta
        if (client == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        status.setText("Preparing avatar…")
        run("upload_avatar", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val avatar: File = cropAvatar(uri)
                try {
                    val uploaded = client.uploadPublicAvatar(avatar, TransferControl(object : MST5.ProgressListener {
                        override fun onProgress(done: Long, total: Long) {
                            ui(object : Runnable {
                                override fun run() {
                                    status.setText("Uploading avatar " + (if (total <= 0) "" else (done * 100 / total).toString() + "%"))
                                }
                            })
                        }
                    }))
                    ui(object : Runnable {
                        override fun run() {
                            myAvatar = uploaded
                            status.setText(getString(R.string.status_avatar_updated))
                            if (page === Page.SETTINGS_PROFILE) showSettingsProfile()
                        }
                    })
                } finally {
                    avatar.delete()
                }
            }
        })
    }

    @Throws(Exception::class)
    private fun cropAvatar(uri: Uri?): File {
        val input: InputStream = contentResolver.openInputStream(uri ?: throw IOException("cannot open avatar image"))
            ?: throw IOException("cannot open avatar image")
        val original: ByteArrayOutputStream = ByteArrayOutputStream()
        try {
            val buffer = ByteArray(16 * 1024)
            var read: Int
            while ((input.read(buffer).also { read = it }) >= 0) {
                if (original.size() + read > ru.e6atb.chat.MainActivity.Companion.MAX_UPLOAD_BYTES) throw IOException("avatar image is too large")
                original.write(buffer, 0, read)
            }
        } finally {
            try {
                input.close()
            } catch (ignored: Exception) {
            }
        }
        val webp: ByteArray? = rs.ove.crypt.proto.Mst5ImageDecoder.prepareWebp(original.toByteArray(), 256, true)
        val output: File = File.createTempFile("mst5-avatar-", ".webp", getCacheDir())
        val stream: FileOutputStream = FileOutputStream(output)
        try {
            stream.write(webp)
        } finally {
            try {
                stream.close()
            } catch (ignored: Exception) {
            }
        }
        if (output.length() <= 0 || output.length() > 1048576L) {
            output.delete()
            throw IOException("avatar image is too large")
        }
        return output
    }

    private fun addComposerMedia(pickedUri: Uri?, imageOnly: Boolean) {
        if (composerMedia.size >= 10) return
        status.setText(if (imageOnly) getString(R.string.status_preparing_image) else getString(R.string.status_preparing_file))
        run("prepare_media", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val uri = pickedUri ?: throw IOException(getString(R.string.status_file_not_available))
                var type: String = contentResolver.getType(uri).orEmpty()
                if (type.isEmpty()) type = "application/octet-stream"
                if (imageOnly && !type.lowercase(Locale.US)
                        .startsWith("image/")
                ) throw IOException(getString(R.string.status_not_an_image))
                var displayName = queryDisplayName(uri)
                if (displayName == null || displayName.trim().length == 0) displayName =
                    if (imageOnly) getString(R.string.image_fallback_name) else getString(R.string.file_fallback_name)
                var detectedSize = queryFileSize(uri)
                var localPath = ""
                if (detectedSize < 0) {
                    val staged: File = stagePickedFile(uri)
                    detectedSize = staged.length()
                    localPath = staged.getAbsolutePath()
                }
                if (detectedSize <= 0) throw IOException(getString(R.string.status_empty_file))
                if (detectedSize > ru.e6atb.chat.MainActivity.Companion.MAX_UPLOAD_BYTES) throw IOException(
                    getString(
                        R.string.status_file_too_large,
                        formatBytes(detectedSize)
                    )
                )
                val item: ComposerMedia = ru.e6atb.chat.MainActivity.ComposerMedia()
                item.uri = if (localPath.isEmpty()) uri else null
                item.localPath = localPath
                item.name = displayName
                item.mime = type
                item.photo = imageOnly
                item.size = detectedSize
                ui(object : Runnable {
                    override fun run() {
                        if (composerMedia.size < 10) composerMedia.add(item)
                        else if (!item.localPath.isNullOrEmpty()) File(item.localPath!!).delete()
                        renderComposerMedia()
                        status.setText("")
                        if (item.mime.orEmpty().lowercase(Locale.US).startsWith("image/")) loadComposerPreview(item)
                    }
                })
            }
        })
    }

    private fun loadComposerPreview(item: ComposerMedia?) {
        io.execute(object : Runnable {
            override fun run() {
                val decoded: Bitmap? =
                    decodeComposerPreview(item, ru.e6atb.chat.MainActivity.Companion.MAX_IMAGE_PREVIEW_PX)
                if (decoded == null) return
                ui(object : Runnable {
                    override fun run() {
                        if (!composerMedia.contains(item)) {
                            decoded.recycle()
                            return
                        }
                        item!!.preview = decoded
                        renderComposerMedia()
                    }
                })
            }
        })
    }

    private fun decodeComposerPreview(item: ComposerMedia?, maxSide: Int): Bitmap? {
        if (item == null) return null
        val localPath = item.localPath
        if (!localPath.isNullOrEmpty()) {
            return decodePreviewBitmap(File(localPath), maxSide)
        }
        val uri = item.uri ?: return null
        var descriptor: ParcelFileDescriptor? = null
        try {
            descriptor = contentResolver.openFileDescriptor(uri, "r")
            return rs.ove.crypt.proto.Mst5ImageDecoder.decode(descriptor, maxSide)
        } catch (ignored: Exception) {
            return null
        } finally {
            if (descriptor != null) try {
                descriptor.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun queryFileSize(uri: Uri?): Long {
        val targetUri = uri ?: return -1
        var cursor: android.database.Cursor? = null
        try {
            cursor = contentResolver.query(targetUri, arrayOf("_size"), null, null, null)
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0)
        } catch (ignored: Exception) {
        } finally {
            if (cursor != null) cursor.close()
        }
        try {
            val descriptor: android.content.res.AssetFileDescriptor? =
                contentResolver.openAssetFileDescriptor(targetUri, "r")
            if (descriptor != null) {
                try {
                    if (descriptor.getLength() >= 0) return descriptor.getLength()
                } finally {
                    descriptor.close()
                }
            }
        } catch (ignored: Exception) {
        }
        return -1
    }

    @Throws(IOException::class)
    private fun stagePickedFile(uri: Uri?): File {
        val target: File = OutboxStore.payloadFile(this, "picked-" + UUID.randomUUID().toString())
        val input: InputStream = contentResolver.openInputStream(uri ?: throw IOException(getString(R.string.status_file_not_available)))
            ?: throw IOException(getString(R.string.status_file_not_available))
        val output: FileOutputStream = FileOutputStream(target)
        var completed: Long = 0
        try {
            val buffer = ByteArray(64 * 1024)
            var count: Int
            while ((input.read(buffer).also { count = it }) >= 0) {
                completed += count.toLong()
                if (completed > ru.e6atb.chat.MainActivity.Companion.MAX_UPLOAD_BYTES) throw IOException(
                    getString(
                        R.string.status_file_too_large,
                        formatBytes(completed)
                    )
                )
                output.write(buffer, 0, count)
            }
        } catch (error: IOException) {
            target.delete()
            throw error
        } finally {
            try {
                output.close()
            } finally {
                input.close()
            }
        }
        return target
    }

    private fun queryDisplayName(uri: Uri?): String? {
        val targetUri = uri ?: return null
        var cursor: android.database.Cursor? = null
        try {
            cursor = getContentResolver().query(
                targetUri, arrayOf(
                    "_display_name"
                ), null, null, null
            )
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        } catch (ignored: Exception) {
        } finally {
            if (cursor != null) {
                cursor.close()
            }
        }
        return null
    }

    private fun downloadFile(file: MST5.FileInfo?) {
        downloadFile(file, null)
    }

    private fun downloadFile(file: MST5.FileInfo?, actionButton: View?) {
        if (openDownloadedFile(file)) {
            return
        }

        val c: MST5? = ta
        if (c == null || file == null || file.id == null || file.id.length == 0) {
            status.setText(getString(R.string.status_file_not_available))
            return
        }
        val fileName = safeFileName(file.name)
        val local: File? = downloadedFileFor(file)
        if (local == null) {
            status.setText(getString(R.string.status_download_folder_not_available))
            return
        }
        status.setText(getString(R.string.status_downloading, fileName))
        setActionButtonLoading(actionButton, true, false)
        io.execute(object : Runnable {
            override fun run() {
                try {
                    if (local.exists() && !isCompleteDownloadedFile(local, file)) {
                        local.delete()
                    }
                    val maxBytes: Long = if (file.size > 0 && file.size < Integer.MAX_VALUE)
                        Math.min(file.size + 1024, 64L * 1024 * 1024)
                    else
                        64 * 1024 * 1024
                    c.downloadFile(file.id, local, maxBytes, null)
                    ui(object : Runnable {
                        override fun run() {
                            status.setText(getString(R.string.status_downloaded, fileName))
                            openDownloadedFile(file)
                        }
                    })
                } catch (e: Exception) {
                    ui(object : Runnable {
                        override fun run() {
                            status.setText(getString(R.string.status_download_error, e.message))
                        }
                    })
                } finally {
                    ui(object : Runnable {
                        override fun run() {
                            setActionButtonLoading(actionButton, false, false)
                        }
                    })
                }
            }
        })
    }

    private fun openDownloadedFile(file: MST5.FileInfo?): Boolean {
        val local: File = downloadedFileFor(file) ?: return false
        if (!isCompleteDownloadedFile(local, file)) {
            return false
        }
        try {
            val intent: Intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(localFileUri(local), fileMimeType(file))
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
            status.setText(getString(R.string.status_opened, local.getName()))
        } catch (e: ActivityNotFoundException) {
            status.setText(getString(R.string.status_no_app_to_open, local.getName()))
        } catch (e: Exception) {
            status.setText(getString(R.string.status_open_error, e.message))
        }
        return true
    }

    private fun downloadedFileFor(file: MST5.FileInfo?): File? {
        if (file == null) return null
        var dir: File? = getExternalFilesDir(null)
        if (dir == null) dir = getFilesDir()
        if (dir == null) return null
        return File(dir, safeFileName(file.name))
    }

    private fun isCompleteDownloadedFile(local: File?, file: MST5.FileInfo?): Boolean {
        if (local == null || !local.isFile || local.length() <= 0) {
            return false
        }
        if (file != null && ta?.isEncryptedMediaFile(file.id) == true) return true
        return file == null || file.size <= 0 || local.length() >= file.size
    }

    private fun localFileUri(local: File): Uri {
        if (Build.VERSION.SDK_INT >= 24) {
            return Uri.parse("content://" + getPackageName() + ".localfiles/" + Uri.encode(local.getName()))
        }
        return Uri.fromFile(local)
    }

    private fun fileMimeType(file: MST5.FileInfo?): String? {
        if (file != null && file.mime != null && file.mime.length > 0) {
            return file.mime
        }
        return "application/octet-stream"
    }

    private fun safeFileName(name: String?): String {
        if (name == null || name.trim().isEmpty()) return "file"
        val clean: String = name.replace('/', '_').replace('\\', '_')
        return if (clean.length > 120) clean.substring(0, 120) else clean
    }

    private fun formatBytes(value: Long): String {
        if (value < 1024) return value.toString() + " B"
        val kb = value / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB", mb)
    }

    private fun isImageFile(file: MST5.FileInfo?): Boolean {
        if (file == null) return false
        val mime = if (file.mime == null) "" else file.mime.toLowerCase(Locale.US)
        if (mime.startsWith("image/")) return true
        val name = if (file.name == null) "" else file.name.toLowerCase(Locale.US)
        return name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".png")
                || name.endsWith(".gif")
                || name.endsWith(".webp")
                || name.endsWith(".bmp")
    }

    private fun imageCacheKey(file: MST5.FileInfo?): String {
        if (file == null) return ""
        if (file.id != null && file.id.length > 0) return file.id
        return (if (file.name == null) "" else file.name) + ":" + file.size
    }

    private fun cachedImagePreview(key: String?): Bitmap? {
        kotlin.synchronized(imagePreviewLoading) {
            return imagePreviewCache.get(key)
        }
    }

    private fun cachedImagePreviewError(key: String?): String? {
        kotlin.synchronized(imagePreviewLoading) {
            return imagePreviewErrors.get(key)!!
        }
    }

    private fun startImagePreviewLoad(file: MST5.FileInfo?, adapter: android.widget.BaseAdapter) {
        val c: MST5? = ta
        if (c == null || file == null || file.id == null || file.id.length == 0) return
        val key = imageCacheKey(file)
        if (key.length == 0) return
        kotlin.synchronized(imagePreviewLoading) {
            if (imagePreviewCache.get(key) != null || imagePreviewErrors.containsKey(key) || imagePreviewLoading.contains(
                    key
                )
            ) {
                return
            }
            imagePreviewLoading.add(key)
        }
        io.execute(object : Runnable {
            override fun run() {
                var decoded: Bitmap? = null
                var error: String? = null
                var downloaded: File? = null
                try {
                    downloaded = File.createTempFile("mst5-preview-", ".image", getCacheDir())
                    val limit: Long =
                        if (file.size > 0) file.size else ru.e6atb.chat.MainActivity.Companion.MAX_UPLOAD_BYTES.toLong()
                    c.downloadFile(file.id, downloaded, limit, null)
                    decoded = decodePreviewBitmap(downloaded, ru.e6atb.chat.MainActivity.Companion.MAX_IMAGE_PREVIEW_PX)
                    if (decoded == null) {
                        error = "invalid image"
                    }
                } catch (e: Exception) {
                    error = e.message
                    if (error == null || error.length == 0) error = e.javaClass.getSimpleName()
                } finally {
                    if (downloaded != null) downloaded.delete()
                }
                val out: Bitmap? = decoded
                val outError = error
                ui(object : Runnable {
                    override fun run() {
                        kotlin.synchronized(imagePreviewLoading) {
                            imagePreviewLoading.remove(key)
                            if (out != null) {
                                imagePreviewCache.put(key, out)
                                imagePreviewErrors.remove(key)
                            } else {
                                imagePreviewErrors.put(key, if (outError == null) "preview error" else outError)
                            }
                        }
                        adapter.notifyDataSetChanged()
                    }
                })
            }
        })
    }

    private fun decodePreviewBitmap(file: File?, maxSide: Int): Bitmap? {
        if (file == null || !file.isFile || file.length() == 0L) return null
        var descriptor: ParcelFileDescriptor? = null
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return rs.ove.crypt.proto.Mst5ImageDecoder.decode(descriptor, maxSide)
        } catch (ignored: Exception) {
            return null
        } finally {
            if (descriptor != null) try {
                descriptor.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun acceptIncomingCall(peerName: String) {
        clearIncomingCallUi()
        if (page !== Page.CALL) {
            if (page !== Page.CHAT || !peerName.equals(currentPeer)) {
                openChatImmediately(peerName, null, false, false, false, null)
            } else {
                currentPeer = peerName
                if (peer != null) peer.setText(peerName)
            }
        } else {
            currentPeer = peerName
        }
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        if (!hasPermissionCompat(ru.e6atb.chat.MainActivity.Companion.PERMISSION_RECORD_AUDIO)) {
            pendingAcceptedPeer = peerName
            requestPermissionsCompat(
                arrayOf<String>(
                    ru.e6atb.chat.MainActivity.Companion.PERMISSION_RECORD_AUDIO
                ), ru.e6atb.chat.MainActivity.Companion.REQ_MICROPHONE
            )
            status.setText(getString(R.string.status_allow_microphone_accept))
            return
        }
        ++voiceConnectGeneration
        activeVoiceRoom = false
        setCallState("connecting", peerName)
        status.setText(getString(R.string.status_answering_peer, peerName))
        run("call", object : Task {
            @Throws(Exception::class)
            override fun run() {
                try {
                    c.sendCall(peerName, "accept")
                    ui(object : Runnable {
                        override fun run() {
                            if (peerName.equals(activeCallPeer) && "connecting".equals(callState)) {
                                startVoiceConnection(c, peerName, getString(R.string.status_answering_peer, peerName))
                            }
                        }
                    })
                } catch (e: Exception) {
                    ui(object : Runnable {
                        override fun run() {
                            if (peerName.equals(activeCallPeer) && "connecting".equals(callState)) {
                                setCallState("failed", peerName)
                            }
                            status.setText(getString(R.string.status_call_error, errorText(e)))
                        }
                    })
                }
            }
        })
    }

    private fun declineIncomingCall(peerName: String?) {
        clearIncomingCallUi()
        val c: MST5? = ta
        if (c != null) {
            run("call", object : Task {
                @Throws(Exception::class)
                override fun run() {
                    c.sendCall(peerName.orEmpty(), "reject")
                }
            })
        }
        setCallState("idle", "")
        status.setText(getString(R.string.status_call_declined))
    }

    private fun startVoiceConnection(c: MST5?, peerName: String?, connectingText: String?) {
        if (c == null) {
            setCallState("failed", peerName)
            status.setText(getString(R.string.status_voice_sign_in_first))
            return
        }
        if (voiceCall.running()) {
            status.setText(getString(R.string.status_call_already_active))
            return
        }
        status.setText(connectingText)
        activeCallPeer = peerName.orEmpty()
        if ("idle".equals(callState)) setCallState("connecting", peerName)
        if (activeVoiceRoom) {
            loadVoiceParticipants()
            main.removeCallbacks(voiceParticipantsPoll)
            main.postDelayed(voiceParticipantsPoll, 1000)
        }
        val client: MST5? = c
        val targetPeer = peerName
        val generation = voiceConnectGeneration
        run("voice", object : Task {
            @Throws(Exception::class)
            override fun run() {
                try {
                    val access: MST5.VoiceAccess? = client?.voiceAccess(targetPeer.orEmpty())
                    if (generation != voiceConnectGeneration || "idle".equals(callState)) {
                        return
                    }
                    voiceCall.start(this@MainActivity, access, object : VoiceCall.Listener {
                        override fun onState(s: String?) {
                            ui(object : Runnable {
                                override fun run() {
                                    status.setText(voiceStatusText(s))
                                    if (VoiceCall.STATE_CONNECTED.equals(s)) {
                                        if ("connecting".equals(callState)) markCallStarted(activeCallPeer)
                                        if (activeVoiceRoom) {
                                            loadVoiceParticipants()
                                        }
                                    } else if (VoiceCall.STATE_CONNECTION_CLOSED.equals(s)) {
                                        finishCall(activeCallPeer, getString(R.string.call_ended))
                                    } else if (s != null && (s.startsWith(VoiceCall.STATE_ERROR_PREFIX) || s.startsWith(
                                            VoiceCall.STATE_SEND_ERROR_PREFIX
                                        ))
                                    ) {
                                        setCallState("failed", activeCallPeer)
                                    }
                                    updateCallButton()
                                }
                            })
                        }
                    })
                } catch (e: Exception) {
                    ui(object : Runnable {
                        override fun run() {
                            status.setText(getString(R.string.status_voice_error, errorText(e)))
                            setCallState("failed", targetPeer)
                        }
                    })
                }
            }
        })
    }

    private fun endVoice() {
        voiceConnectGeneration++
        val c: MST5? = ta
        val peerName = if (activeCallPeer.isEmpty()) currentPeer else activeCallPeer
        if (!activeVoiceRoom && c != null && peerName != null && !peerName.isEmpty()) {
            run("call", object : Task {
                @Throws(Exception::class)
                override fun run() {
                    c.sendCall(peerName, "end")
                }
            })
        }
        finishCall(peerName, getString(R.string.call_ended))
    }

    private fun startPolling() {
        if (!activityResumed || ta == null) return
        startSyncService()
        loadCachedChats()
        if (page === Page.CHAT) loadCachedHistory(currentPeer)
    }

    private fun stopPolling() {
        polling = false
        pollingGeneration++
    }

    private fun pollRetryDelayMs(failures: Int): Long {
        val delay = 1000L shl Math.min(failures, 4)
        return Math.min(delay, 30000L)
    }

    private fun sleepPollingRetry(ms: Long, generation: Int): Boolean {
        val until: Long = System.currentTimeMillis() + ms
        while (polling && generation == pollingGeneration && activityResumed) {
            val remaining: Long = until - System.currentTimeMillis()
            if (remaining <= 0) return true
            ru.e6atb.chat.MainActivity.Companion.sleep(Math.min(remaining, 250L))
        }
        return false
    }

    private fun handoffForegroundOffsetToBackground() {
        if (lastUpdate <= 0 || !SessionStore.hasSession(this)) return
        val background: Long = SessionStore.backgroundLastUpdate(this)
        if (lastUpdate > background) {
            SessionStore.backgroundLastUpdate(this, lastUpdate)
        }
    }

    private fun handleUpdate(u: MST5.Update?) {
        if (u == null) return
        if ("chat_update".equals(u.type) || "chat_removed".equals(u.type) || "chat_deleted".equals(u.type)) {
            handleRoomUpdate(u)
            return
        }
        if ("message".equals(u.type)) {
            if (isOAuthRequestMessage(u.message)) {
                val code = oauthRequestCode(u.message)
                ui(object : Runnable {
                    override fun run() {
                        openOAuthDeviceRequest(code)
                    }
                })
            }
            append(u.message)
            return
        }
        if ("channel_comment".equals(u.type)) {
            appendChannelComment(u.message)
            return
        }
        if ("message_read".equals(u.type) || "message_edit".equals(u.type) || "message_reaction".equals(u.type)) {
            applyMessageUpdate(u.message)
            return
        }
        if ("message_delete".equals(u.type)) {
            applyMessageDelete(u.message)
            return
        }
        val call: MST5.Call? = u.call
        if (call == null || call.from == null) return
        val peerName = callPeer(call)
        val fromMe = isOwnUser(call.from)
        ui(object : Runnable {
            override fun run() {
                if ("call_invite".equals(u.type)) {
                    if (fromMe || isStaleIncomingCall(call)) {
                        cancelIncomingCallNotification()
                        return
                    }
                    showIncomingCall(peerName)
                } else if ("call_accept".equals(u.type)) {
                    if (fromMe) {
                        finishIncomingOnOtherDevice(peerName, getString(R.string.call_answered_other_device))
                        return
                    }
                    if (voiceCall.running()) {
                        markCallStarted(peerName)
                        status.setText(getString(R.string.status_peer_accepted_call, peerName))
                    } else if (peerName.equals(activeCallPeer)
                        && ("calling".equals(callState) || "connecting".equals(callState) || "failed".equals(callState))
                    ) {
                        if (!hasPermissionCompat(ru.e6atb.chat.MainActivity.Companion.PERMISSION_RECORD_AUDIO)) {
                            pendingOutgoingConnectPeer = peerName
                            requestPermissionsCompat(
                                arrayOf<String>(ru.e6atb.chat.MainActivity.Companion.PERMISSION_RECORD_AUDIO),
                                ru.e6atb.chat.MainActivity.Companion.REQ_MICROPHONE
                            )
                            status.setText(getString(R.string.status_allow_microphone_connect))
                            return
                        }
                        ++voiceConnectGeneration
                        setCallState("connecting", peerName)
                        startVoiceConnection(ta, peerName, getString(R.string.status_peer_accepted_call, peerName))
                    } else {
                        setCallState("connecting", peerName)
                        status.setText(getString(R.string.status_peer_accepted_call, peerName))
                    }
                } else if ("call_reject".equals(u.type)) {
                    finishCall(
                        peerName,
                        if (fromMe) getString(R.string.call_declined_other_device) else getString(R.string.status_call_declined)
                    )
                    status.setText(
                        if (fromMe) getString(R.string.status_call_declined_other_device) else getString(
                            R.string.status_peer_declined_call,
                            peerName
                        )
                    )
                } else if ("call_end".equals(u.type)) {
                    finishCall(
                        peerName,
                        if (fromMe) getString(R.string.call_ended_other_device) else getString(R.string.call_ended)
                    )
                    status.setText(
                        if (fromMe) getString(R.string.status_call_ended_other_device) else getString(
                            R.string.status_peer_ended_call,
                            peerName
                        )
                    )
                }
            }
        })
    }

    private fun isOAuthRequestMessage(message: MST5.Message?): Boolean {
        val data: JSONObject? = systemMessageData(message)
        return data != null && "oauth_request".equals(data.optString("kind"))
    }

    private fun oauthRequestCode(message: MST5.Message?): String? {
        val data: JSONObject? = systemMessageData(message)
        return if (data == null) "" else OAuthCodeParser.parse(data.optString("user_code"))
    }

    private fun callPeer(call: MST5.Call?): String {
        return ru.e6atb.chat.MainActivity.Companion.callPeerFor(myID, myLogin, call)
    }

    private fun isOwnUser(user: MST5.User?): Boolean {
        return ru.e6atb.chat.MainActivity.Companion.isOwnUserFor(myID, myLogin, user)
    }

    private fun isStaleIncomingCall(call: MST5.Call?): Boolean {
        if (call == null || call.date <= 0) return false
        val age: Long = System.currentTimeMillis() / 1000L - call.date
        return age > ru.e6atb.chat.MainActivity.Companion.MAX_INCOMING_CALL_AGE_SEC
    }

    private fun finishIncomingOnOtherDevice(peerName: String?, label: String?) {
        if (!"incoming".equals(callState)) return
        if (peerName == null || peerName.length == 0 || !peerName.equals(activeCallPeer)) return
        finishCall(peerName, label)
    }

    private fun markCallStarted(peerName: String?) {
        activeCallPeer = peerName.orEmpty()
        if (callStartedAtMs == 0L) callStartedAtMs = System.currentTimeMillis()
        setCallState("active", peerName)
        updateActiveCallNotification()
    }

    private fun finishCall(peerName: String?, label: String?) {
        voiceConnectGeneration++
        main.removeCallbacks(voiceParticipantsPoll)
        if (peerName != null && peerName.equals(pendingAcceptedPeer)) pendingAcceptedPeer = ""
        if (peerName != null && peerName.equals(pendingOutgoingConnectPeer)) pendingOutgoingConnectPeer = ""
        if (peerName != null && peerName.equals(pendingVoiceRoom)) pendingVoiceRoom = ""
        val durationMs: Long = if (callStartedAtMs == 0L) 0 else System.currentTimeMillis() - callStartedAtMs
        val hadCall =
            voiceCall.running() || callStartedAtMs != 0L || (peerName != null && peerName.equals(activeCallPeer))
        val hadIncomingCall = "incoming".equals(callState) && page === Page.CALL
        clearIncomingCallUi()
        voiceCall.stop()
        cancelActiveCallNotification()
        if (!activeVoiceRoom && (hadCall || hadIncomingCall)) {
            addCallSystemRow(peerName, label, durationMs)
        }
        activeCallPeer = ""
        activeVoiceRoom = false
        callStartedAtMs = 0
        setCallState("idle", "")
    }

    private fun updateCallButton() {
        if (callButton == null) return
        callButton.setVisibility(
            if (currentPeerUser == null || currentPeerIsBot() || currentPeerIsChannel() || currentPeerIsSelfChat())
                View.GONE
            else
                View.VISIBLE
        )
        val busy = !"idle".equals(callState) && !"failed".equals(callState)
        callButton.setEnabled(!currentPeerBanned || busy)
        val description: String?
        if (currentPeerIsGroup()) {
            description =
                if (busy && currentPeer!!.equals(activeCallPeer)) getString(R.string.action_leave_voice) else getString(
                    R.string.action_join_voice
                )
        } else {
            description = if (busy) getString(R.string.action_end_call) else getString(R.string.action_call)
        }
        callButton.setContentDescription(description)
        setButtonRequestBusy(callButton, "calling".equals(callState) || "connecting".equals(callState))
    }

    private fun handleRoomUpdate(u: MST5.Update) {
        val room: MST5.User? = u.room
        if (room == null || room.id == null || room.id.length == 0) return
        ui(object : Runnable {
            override fun run() {
                val currentRoom = currentPeerIsSameRoom(room)
                if ("chat_removed".equals(u.type) || "chat_deleted".equals(u.type)) {
                    ChatCache.deleteChat(
                        this@MainActivity,
                        SessionStore.server(this@MainActivity, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
                        myLogin,
                        resolvedPeerName(room, room.id)
                    )
                    ChatCache.deleteChat(
                        this@MainActivity,
                        SessionStore.server(this@MainActivity, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
                        myLogin,
                        room.id
                    )
                    if (currentRoom) {
                        currentPeer = ""
                        currentPeerUser = null
                        currentPeerBanned = false
                        currentPeerBannedByMe = false
                        currentPeerBannedMe = false
                        showChats()
                    } else if (page === Page.CHATS) {
                        loadChats()
                    }
                    return
                }
                if (currentRoom) {
                    currentPeerUser = room
                    currentPeer = resolvedPeerName(room, currentPeer)
                    refreshCurrentPeerNameView()
                    if (!room.commentsEnabled) {
                        ChatCache.deleteCommentThreads(
                            this@MainActivity,
                            SessionStore.server(this@MainActivity, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
                            myLogin,
                            currentPeer
                        )
                        OutboxStore.removeChannelComments(
                            this@MainActivity,
                            SessionStore.server(this@MainActivity, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
                            OutboxDispatcher.accountKey(this@MainActivity),
                            currentPeer
                        )
                    }
                    if (page === Page.CHANNEL_COMMENTS && !room.commentsEnabled) {
                        status.setText(getString(R.string.channel_comments_deleted))
                        showChat()
                        loadHistory()
                    } else if (page === Page.CHANNEL_SETTINGS) {
                        showChannelSettings()
                    } else if (page === Page.CHAT) {
                        refreshChatInput()
                        loadHistory()
                    }
                }
                if (page === Page.CHATS || currentRoom) {
                    loadChats()
                }
            }
        })
    }

    private fun currentPeerIsSameRoom(room: MST5.User?): Boolean {
        if (room == null || room.id == null || room.id.length == 0) return false
        if (currentPeerUser != null && room.id.equals(currentPeerUser!!.id)) return true
        val resolved = resolvedPeerName(room, room.id)
        return currentPeer != null && currentPeer!!.equals(resolved)
    }

    private fun currentPeerIsBot(): Boolean {
        return currentPeerUser != null && currentPeer != null && currentPeer!!.equals(currentPeerUser!!.login) && currentPeerUser!!.bot
    }

    private fun currentPeerIsSelfChat(): Boolean {
        return !currentPeerIsRoom()
                && (isOwnUser(currentPeerUser) || ru.e6atb.chat.MainActivity.Companion.isOwnAddressFor(
            myID,
            myLogin,
            currentPeer
        ))
    }

    private fun addCallSystemRow(peerName: String?, label: String?, durationMs: Long) {
        if (messageRows == null || peerName == null || !peerName.equals(currentPeer)) return
        var text = label
        if (durationMs > 0) text += " - " + formatDuration(durationMs)
        val peerUser: MST5.User = currentHeaderUser()
        val ownUser: MST5.User = User(myID, "", myLogin, myNick, myVerified, myBot, 0)
        val data = "{\"type\":\"call_end\",\"duration_ms\":" + Math.max(0, durationMs) + "}"
        val message: MST5.Message = Message(
            0,
            "",
            ownUser,
            peerUser,
            text.orEmpty(),
            System.currentTimeMillis() / 1000L,
            0,
            null,
            null,
            false,
            true,
            data
        )
        messageRows.add(MessageRow.messageText(text, message))
        if (messageList != null && messageRows!!.count > 0) {
            messageList.setSelection(messageRows!!.count - 1)
        }
    }

    private fun formatDuration(ms: Long): String {
        val total: Long = Math.max(1, ms / 1000)
        val minutes = total / 60
        val seconds = total % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    private fun showIncomingCall(from: String?) {
        cancelIncomingCallNotification()
        status.setText(getString(R.string.status_incoming_call_from, from))
        setCallState("incoming", from)
    }

    private fun openIncomingCall(from: String?) {
        if (from == null || from.trim().length == 0) return
        currentPeer = from.trim()
        if ("idle".equals(callState) || "failed".equals(callState)) {
            showIncomingCall(currentPeer)
        } else {
            updateCallWindow()
        }
    }

    private fun setCallState(state: String?, peerName: String?) {
        callState = if (state == null) "idle" else state
        if ("idle".equals(callState)) activeCallPeer = ""
        else if (peerName != null && peerName.length > 0) activeCallPeer = peerName
        main.removeCallbacks(callClock)
        if (!"idle".equals(callState)) main.post(callClock)
        if ("idle".equals(callState) || "failed".equals(callState) || "incoming".equals(callState)) {
            cancelActiveCallNotification()
        }
        updateCallWindow()
        updateCallButton()
    }

    private fun updateCallWindow() {
        if ("idle".equals(callState)) {
            dismissCallWindow()
            return
        }
        ensureCallWindow()
        val title: String?
        if (activeVoiceRoom && "connecting".equals(callState)) title = getString(R.string.voice_channel_connecting)
        else if (activeVoiceRoom && "active".equals(callState)) title = getString(R.string.voice_channel_active)
        else if ("calling".equals(callState)) title = getString(R.string.call_state_calling)
        else if ("incoming".equals(callState)) title = getString(R.string.call_state_incoming)
        else if ("connecting".equals(callState)) title = getString(R.string.call_state_connecting)
        else if ("active".equals(callState)) title = getString(R.string.call_state_active)
        else if ("failed".equals(callState)) title = getString(R.string.call_state_failed)
        else title = getString(R.string.call_state_none)
        callStateView.setText(title)
        callPeerView.setText(if (activeCallPeer.length == 0) "" else activeCallPeer)
        if (callHintView != null) {
            if (activeVoiceRoom) callHintView.setText(getString(R.string.voice_channel_hint))
            else if ("incoming".equals(callState)) callHintView.setText(getString(R.string.call_hint_incoming))
            else if ("calling".equals(callState)) callHintView.setText(getString(R.string.call_hint_calling))
            else if ("connecting".equals(callState)) callHintView.setText(getString(R.string.call_hint_connecting))
            else if ("active".equals(callState)) callHintView.setText(getString(R.string.call_hint_active))
            else if ("failed".equals(callState)) callHintView.setText(getString(R.string.call_state_failed))
            else callHintView.setText("")
        }
        configureCallActions()
        updateCallDuration()
    }

    private fun ensureCallWindow() {
        if (page === Page.CALL && callStateView != null) return
        page = Page.CALL
        if (bottomNav != null) bottomNav.setVisibility(View.GONE)
        content.removeAllViews()
        val panel: LinearLayout = LinearLayout(this)
        panel.setOrientation(LinearLayout.VERTICAL)
        panel.setPadding(pad, pad, pad, pad)
        panel.setGravity(Gravity.CENTER_HORIZONTAL)
        panel.setBackgroundColor(bg)

        val heading: TextView =
            title(getString(if (activeVoiceRoom) R.string.voice_channel_title else R.string.call_title))
        heading.setGravity(Gravity.CENTER)
        callStateView = label("")
        callStateView.setGravity(Gravity.CENTER)
        callPeerView = title("")
        callPeerView.setGravity(Gravity.CENTER)
        callDurationView = label("")
        callDurationView.setGravity(Gravity.CENTER)
        callHintView = label("")
        callHintView.setGravity(Gravity.CENTER)

        panel.addView(heading, LinearLayout.LayoutParams(-1, -2))
        panel.addView(callStateView, spacedParams())
        panel.addView(callPeerView, spacedParams())
        panel.addView(callDurationView, spacedParams())
        panel.addView(callHintView, spacedParams())
        callParticipantsView = label("")
        callParticipantsView.setGravity(Gravity.CENTER)
        panel.addView(callParticipantsView, spacedParams())

        val actions: LinearLayout = LinearLayout(this)
        actions.setOrientation(LinearLayout.HORIZONTAL)
        callSecondaryAction = button(getString(R.string.action_decline), object : View.OnClickListener {
            override fun onClick(v: View?) {
                val peerName = activeCallPeer
                if (peerName.length > 0) declineIncomingCall(peerName)
            }
        })
        callPrimaryAction = primaryButton(getString(R.string.action_end_call), object : View.OnClickListener {
            override fun onClick(v: View?) {
                handleCallPrimaryAction()
            }
        })
        val secondaryLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        secondaryLp.setMargins(0, 0, gap / 2, 0)
        val primaryLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        primaryLp.setMargins(gap / 2, 0, 0, 0)
        actions.addView(callSecondaryAction, secondaryLp)
        actions.addView(callPrimaryAction, primaryLp)
        panel.addView(actions, LinearLayout.LayoutParams(-1, -2))

        callChatAction = button(getString(R.string.action_open_chat), object : View.OnClickListener {
            override fun onClick(v: View?) {
                val peerName: String = (if (activeCallPeer.length == 0) currentPeer else activeCallPeer)!!
                if (peerName.length == 0) return
                openChatImmediately(peerName, null, false, false, false, null)
            }
        })
        val chatLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        chatLp.setMargins(0, gap, 0, 0)
        panel.addView(callChatAction, chatLp)

        content.addView(panel, fill())
    }

    private fun spacedParams(): LinearLayout.LayoutParams {
        val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, gap, 0, gap)
        return lp
    }

    private fun configureCallActions() {
        if (callPrimaryAction == null || callSecondaryAction == null) return
        if ("incoming".equals(callState)) {
            callSecondaryAction.setVisibility(View.VISIBLE)
            callSecondaryAction.setText(getString(R.string.action_decline))
            callPrimaryAction.setText(getString(R.string.action_accept))
            setButtonRequestBusy(callPrimaryAction, false)
            if (callChatAction != null) callChatAction.setVisibility(View.GONE)
            return
        }
        callSecondaryAction.setVisibility(View.GONE)
        if ("failed".equals(callState)) {
            callPrimaryAction.setText(getString(R.string.action_close))
        } else if (activeVoiceRoom) {
            callPrimaryAction.setText(getString(R.string.action_leave_voice))
        } else {
            callPrimaryAction.setText(getString(R.string.action_end_call))
        }
        setButtonRequestBusy(callPrimaryAction, "calling".equals(callState) || "connecting".equals(callState))
        if (callChatAction != null) callChatAction.setVisibility(if (activeCallPeer.length == 0) View.GONE else View.VISIBLE)
    }

    private fun loadVoiceParticipants() {
        val c: MST5? = ta
        val room = if (activeCallPeer == null || activeCallPeer.length == 0) currentPeer else activeCallPeer
        if (!activeVoiceRoom || c == null || room == null || room.length == 0) return
        run("voice_participants", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val participants: List<MST5.User?>? = c.voiceParticipants(room)
                ui(object : Runnable {
                    override fun run() {
                        renderVoiceParticipants(participants)
                    }
                })
            }
        })
    }

    private fun renderVoiceParticipants(participants: List<MST5.User?>?) {
        if (callParticipantsView == null || !activeVoiceRoom) return
        if (participants == null || participants.isEmpty()) {
            callParticipantsView.setText(getString(R.string.voice_channel_no_participants))
            return
        }
        val out: StringBuilder = StringBuilder()
        out.append(getString(R.string.voice_channel_participants)).append("\n")
        for (user in participants) {
            if (user == null) continue
            if (out[out.length - 1] != '\n') out.append("\n")
            out.append(displayUser(user))
        }
        callParticipantsView.setText(out.toString())
    }

    private fun handleCallPrimaryAction() {
        if ("incoming".equals(callState)) {
            val peerName = activeCallPeer
            if (peerName.length > 0) acceptIncomingCall(peerName!!)
            return
        }
        if ("failed".equals(callState)) {
            setCallState("idle", "")
            return
        }
        endVoice()
    }

    private fun dismissCallWindow() {
        val wasCallPage = page === Page.CALL
        if (wasCallPage && content != null && !isFinishing()) {
            if (currentPeer != null && currentPeer.length > 0) {
                showChat()
                loadHistory()
            } else {
                showChats()
            }
        }
    }

    private fun updateCallDuration() {
        if (callDurationView == null) return
        if ("active".equals(callState) && callStartedAtMs > 0) {
            callDurationView.setText(formatDuration(System.currentTimeMillis() - callStartedAtMs))
        } else {
            callDurationView.setText("")
        }
    }

    private fun errorText(error: Throwable?): String {
        if (error == null) return getString(R.string.status_unknown_error)
        var message: String? = error.message
        if (message == null || message.trim().length == 0) {
            message = error.javaClass.getSimpleName()
        }
        return ru.e6atb.chat.MainActivity.Companion.safeDisplayText(message)
    }

    private fun voiceStatusText(state: String?): String? {
        if (VoiceCall.STATE_ENDED.equals(state)) return getString(R.string.call_ended)
        if (VoiceCall.STATE_CONNECTED.equals(state)) return getString(R.string.status_voice_connected)
        if (VoiceCall.STATE_CONNECTION_CLOSED.equals(state)) return getString(R.string.status_voice_connection_closed)
        if (VoiceCall.STATE_MICROPHONE_PERMISSION_DENIED.equals(state)) return getString(R.string.status_microphone_denied)
        if (state != null && state.startsWith(VoiceCall.STATE_SEND_ERROR_PREFIX)) {
            return getString(
                R.string.status_voice_send_error,
                state.substring(VoiceCall.STATE_SEND_ERROR_PREFIX.length)
            )
        }
        if (state != null && state.startsWith(VoiceCall.STATE_ERROR_PREFIX)) {
            return getString(R.string.status_voice_error, state.substring(VoiceCall.STATE_ERROR_PREFIX.length))
        }
        return if (state == null) "" else ru.e6atb.chat.MainActivity.Companion.safeDisplayText(state)
    }

    private fun clearIncomingCallUi() {
        cancelIncomingCallNotification()
    }

    private fun cancelIncomingCallNotification() {
        val nm: NotificationManager? = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
        if (nm != null) nm.cancel(MessageSyncService.CALL_NOTIFICATION_ID)
    }

    private fun createCallNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        try {
            val nm: NotificationManager? = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
            if (nm == null) return
            val channelClass: Class<*> = Class.forName("android.app.NotificationChannel")
            val constructor: Constructor<*> =
                channelClass.getConstructor(String::class.java, CharSequence::class.java, Int::class.javaPrimitiveType)
            val method: Method = NotificationManager::class.java.getMethod("createNotificationChannel", channelClass)
            val high: Int = NotificationManager::class.java.getField("IMPORTANCE_HIGH").getInt(null)
            val channel: Any? = constructor.newInstance(
                ru.e6atb.chat.MainActivity.Companion.CALL_NOTIFICATION_CHANNEL,
                getString(R.string.notification_channel_calls),
                high
            )
            makeNotificationChannelSilent(channelClass, channel)
            method.invoke(nm, channel)
            val normal: Int = NotificationManager::class.java.getField("IMPORTANCE_DEFAULT").getInt(null)
            val updates: Any? = constructor.newInstance(
                ru.e6atb.chat.MainActivity.Companion.UPDATE_NOTIFICATION_CHANNEL,
                getString(R.string.notification_channel_updates),
                normal
            )
            method.invoke(nm, updates)
        } catch (ignored: Exception) {
        }
    }

    private fun makeNotificationChannelSilent(channelClass: Class<*>, channel: Any?) {
        try {
            channelClass.getMethod(
                "setSound",
                android.net.Uri::class.java,
                Class.forName("android.media.AudioAttributes")
            ).invoke(channel, null, null)
        } catch (ignored: Exception) {
        }
        try {
            channelClass.getMethod("enableVibration", Boolean::class.javaPrimitiveType).invoke(channel, false)
        } catch (ignored: Exception) {
        }
        try {
            channelClass.getMethod("setVibrationPattern", LongArray::class.java).invoke(
                channel, arrayOf<Any>(
                    LongArray(0)
                )
            )
        } catch (ignored: Exception) {
        }
    }

    private fun updateActiveCallNotification() {
        if (!"active".equals(callState) || activeCallPeer == null || activeCallPeer.length == 0 || callStartedAtMs <= 0) {
            return
        }
        val nm: NotificationManager? = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
        if (nm == null) return
        val title: String? = if (activeVoiceRoom)
            getString(R.string.notification_active_voice_channel)
        else
            getString(R.string.notification_active_call)
        val text: String? = getString(
            R.string.notification_active_call_body,
            activeCallPeer,
            formatDuration(System.currentTimeMillis() - callStartedAtMs)
        )
        val open: Intent = Intent(this, ru.e6atb.chat.MainActivity::class.java)
        open.setAction(ru.e6atb.chat.MainActivity.Companion.ACTION_OPEN_CALL)
        open.putExtra(ru.e6atb.chat.MainActivity.Companion.EXTRA_PEER, activeCallPeer)
        open.putExtra(ru.e6atb.chat.MainActivity.Companion.EXTRA_CALL, activeCallPeer)
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending: PendingIntent? = PendingIntent.getActivity(
            this,
            ru.e6atb.chat.MainActivity.Companion.ACTIVE_CALL_NOTIFICATION_ID,
            open,
            pendingIntentFlags()
        )
        val n: Notification = activeCallNotification(title, text, pending)
        try {
            nm.notify(ru.e6atb.chat.MainActivity.Companion.ACTIVE_CALL_NOTIFICATION_ID, n)
        } catch (ignored: SecurityException) {
        }
    }

    private fun activeCallNotification(title: String?, text: String?, pending: PendingIntent?): Notification {
        val n: Notification
        if (Build.VERSION.SDK_INT >= 11) {
            n = buildActivityNotification(
                ru.e6atb.chat.MainActivity.Companion.CALL_NOTIFICATION_CHANNEL,
                title,
                text,
                pending,
                true,
                android.R.drawable.ic_menu_call
            )
        } else {
            n = Notification(android.R.drawable.ic_menu_call, text, System.currentTimeMillis())
            setLatestEventInfoCompat(n, title, text, pending)
        }
        n.flags = n.flags or (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR)
        return n
    }

    private fun buildActivityNotification(
        channel: String?, title: String?, text: String?, pending: PendingIntent?,
        ongoing: Boolean, icon: Int
    ): Notification {
        try {
            val builderClass: Class<*> = Class.forName("android.app.Notification\$Builder")
            val builder: Any?
            if (Build.VERSION.SDK_INT >= 26) {
                val constructor: Constructor<*> =
                    builderClass.getConstructor(android.content.Context::class.java, String::class.java)
                builder = constructor.newInstance(this, channel)
            } else {
                val constructor: Constructor<*> = builderClass.getConstructor(android.content.Context::class.java)
                builder = constructor.newInstance(this)
            }
            builderClass.getMethod("setSmallIcon", Int::class.javaPrimitiveType).invoke(builder, icon)
            builderClass.getMethod("setContentTitle", CharSequence::class.java).invoke(builder, title)
            builderClass.getMethod("setContentText", CharSequence::class.java).invoke(builder, text)
            builderClass.getMethod("setContentIntent", PendingIntent::class.java).invoke(builder, pending)
            builderClass.getMethod("setOngoing", Boolean::class.javaPrimitiveType).invoke(builder, ongoing)
            builderClass.getMethod("setAutoCancel", Boolean::class.javaPrimitiveType).invoke(builder, !ongoing)
            if (Build.VERSION.SDK_INT >= 16) {
                return builderClass.getMethod("build").invoke(builder) as Notification
            }
            return builderClass.getMethod("getNotification").invoke(builder) as Notification
        } catch (e: Exception) {
            val n: Notification = Notification(icon, text, System.currentTimeMillis())
            setLatestEventInfoCompat(n, title, text, pending)
            return n
        }
    }

    private fun setLatestEventInfoCompat(n: Notification?, title: String?, text: String?, pending: PendingIntent?) {
        try {
            val method: Method = Notification::class.java.getMethod(
                "setLatestEventInfo",
                android.content.Context::class.java,
                CharSequence::class.java,
                CharSequence::class.java,
                PendingIntent::class.java
            )
            method.invoke(n, this, title, text, pending)
        } catch (ignored: Exception) {
        }
    }

    private fun pendingIntentFlags(): Int {
        var flags: Int = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                flags = flags or PendingIntent::class.java.getField("FLAG_IMMUTABLE").getInt(null)
            } catch (ignored: Exception) {
            }
        }
        return flags
    }

    private fun cancelActiveCallNotification() {
        val nm: NotificationManager? = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
        if (nm != null) nm.cancel(ru.e6atb.chat.MainActivity.Companion.ACTIVE_CALL_NOTIFICATION_ID)
    }

    private fun cancelMessageNotification(peer: String) {
        val nm: NotificationManager? = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
        if (nm == null) return
        val id: Int = MessageSyncService.MESSAGE_BASE_ID + Math.abs(peer.hashCode()) % 100000
        nm.cancel(id)
    }

    private fun loadCachedChats() {
        val context: android.content.Context? = getApplicationContext()
        val server: String? = SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER)
        val login = myLogin
        enqueueCache(object : Runnable {
            override fun run() {
                val chats: List<MST5.Chat>? = ChatCache.loadChats(context, server, login)
                ui(object : Runnable {
                    override fun run() {
                        if (page === Page.CHATS && chatRows != null) {
                            renderChats(chats, getString(R.string.source_cached))
                        }
                    }
                })
            }
        })
    }

    private fun loadCachedHistory(peerName: String?) {
        if (peerName == null || peerName.length == 0) return
        val context: android.content.Context? = getApplicationContext()
        val server: String? = SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER)
        val login = myLogin
        enqueueCache(object : Runnable {
            override fun run() {
                val history: List<MST5.Message>? = ChatCache.loadHistory(context, server, login, peerName)
                ui(object : Runnable {
                    override fun run() {
                        if (page === Page.CHAT && peerName.equals(currentPeer) && messageRows != null) {
                            renderHistory(history, peerName, true)
                        }
                    }
                })
            }
        })
    }

    private fun cacheSaveChats(chats: List<MST5.Chat>?) {
        if (chats == null) return
        val context: android.content.Context? = getApplicationContext()
        val server: String? = SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER)
        val login = myLogin
        val copy: List<MST5.Chat> = ArrayList<MST5.Chat>(chats)
        enqueueCache(object : Runnable {
            override fun run() {
                ChatCache.saveChats(context, server, login, copy)
            }
        })
    }

    private fun cacheSaveHistory(peerName: String?, history: List<MST5.Message>?) {
        if (peerName == null || peerName.length == 0 || history == null) return
        val context: android.content.Context? = getApplicationContext()
        val server: String? = SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER)
        val login = myLogin
        val copy: List<MST5.Message> = ArrayList<MST5.Message>(history)
        enqueueCache(object : Runnable {
            override fun run() {
                ChatCache.saveHistory(context, server, login, peerName, copy)
            }
        })
    }

    private fun cacheAppendMessage(peerName: String?, message: MST5.Message?) {
        if (peerName == null || peerName.length == 0 || message == null) return
        val context: android.content.Context? = getApplicationContext()
        val server: String? = SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER)
        val login = myLogin
        enqueueCache(object : Runnable {
            override fun run() {
                ChatCache.appendMessage(context, server, login, peerName, message)
            }
        })
    }

    private fun cacheDeleteMessage(peerName: String?, messageID: Long) {
        if (peerName == null || peerName.length == 0) return
        val context: android.content.Context? = getApplicationContext()
        val server: String? = SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER)
        val login = myLogin
        enqueueCache(object : Runnable {
            override fun run() {
                ChatCache.deleteMessage(context, server, login, peerName, messageID)
            }
        })
    }

    private fun enqueueCache(task: Runnable?) {
        try {
            cacheIo.execute(task)
        } catch (ignored: Exception) {
        }
    }

    private fun outboxMessage(entry: Entry): MST5.Message {
        val me: MST5.User = User(myID, myEmail, myLogin, myNick, myVerified, myBot, 0)
        return entry.localMessage(
            me,
            if (currentPeer != null && currentPeer!!.equals(entry.peer)) currentPeerUser else null
        )
    }

    private fun dispatchOutbox(client: MST5?) {
        OutboxDispatcher.dispatch(this, client, object : OutboxDispatcher.Listener {
            override fun onChanged(entry: Entry?, sent: MST5.Message?) {
                if (entry == null) return
                ui(object : Runnable {
                    override fun run() {
                        if (sent != null) {
                            if (entry.commentPostId > 0) appendChannelComment(sent)
                            else append(sent)
                        } else if (page === Page.CHAT && entry.peer.equals(currentPeer) && messageRows != null) {
                            messageRows!!.updateMessage(outboxMessage(entry))
                        } else if (page === Page.CHANNEL_COMMENTS && entry.peer.equals(currentPeer)
                            && currentCommentPost != null && entry.commentPostId === currentCommentPost!!.id && messageRows != null
                        ) {
                            messageRows!!.updateMessage(outboxMessage(entry))
                        }
                    }
                })
            }
        })
    }

    private fun append(m: MST5.Message?) {
        if (m == null) return
        if (m.clientMessageId != null && m.clientMessageId.length > 0) {
            OutboxStore.complete(
                this,
                SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
                OutboxDispatcher.accountKey(this),
                m.clientMessageId
            )
        }
        val cachedPeer = messagePeer(m)
        cacheAppendMessage(cachedPeer, m)
        ui(object : Runnable {
            override fun run() {
                val other = messagePeer(m)

                if (page === Page.CHAT &&
                    other.equals(currentPeer) && messageRows != null
                ) {
                    currentPeerUser = messagePeerUser(m)
                    updateCallButton()
                    if (!messageRows!!.updateMessage(m)) addMessageRow(m, false)
                    refreshChatInput()
                    markReadIfIncoming(m, other)

                    if (messageList != null &&
                        messageRows!!.count > 0
                    ) {
                        messageList.setSelection(
                            messageRows!!.count - 1
                        )
                    }
                } else if (page !== Page.CHAT) {
                    status.setText(
                        getString(R.string.status_new_message_from, other)
                    )

                    if (page === Page.CHATS) {
                        loadChats()
                    }
                }
            }
        })
    }

    private fun appendChannelComment(message: MST5.Message?) {
        if (message == null || message.commentPostId <= 0) return
        if (message.clientMessageId != null && message.clientMessageId.length > 0) {
            OutboxStore.complete(
                this,
                SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
                OutboxDispatcher.accountKey(this),
                message.clientMessageId
            )
        }
        val channel = messagePeer(message)
        cacheAppendMessage(OutboxStore.cachePeer(channel, message.commentPostId), message)
        ui(object : Runnable {
            override fun run() {
                if (page === Page.CHANNEL_COMMENTS && channel.equals(currentPeer)
                    && currentCommentPost != null && currentCommentPost!!.id === message.commentPostId && messageRows != null
                ) {
                    if (!messageRows!!.updateMessage(message)) addMessageRow(message, false)
                    if (messageList != null && messageRows!!.count > 0) messageList.setSelection(messageRows!!.count - 1)
                } else if (page === Page.CHAT && channel.equals(currentPeer)) {
                    scheduleChannelHistoryReload()
                }
            }
        })
    }

    private fun applyMessageUpdate(m: MST5.Message?) {
        if (m == null) return
        val cachedPeer = messagePeer(m)
        val historyPeer: String? =
            if (m.commentPostId > 0) OutboxStore.cachePeer(cachedPeer, m.commentPostId) else cachedPeer
        cacheAppendMessage(historyPeer, m)
        ui(object : Runnable {
            override fun run() {
                if (m.commentPostId > 0 && page === Page.CHANNEL_COMMENTS && cachedPeer.equals(currentPeer)
                    && currentCommentPost != null && currentCommentPost!!.id === m.commentPostId && messageRows != null
                ) {
                    messageRows!!.updateMessage(m)
                } else if (m.commentPostId == 0L && page === Page.CHAT && cachedPeer.equals(currentPeer) && messageRows != null) {
                    messageRows!!.updateMessage(m)
                }
                if (page === Page.CHATS) loadChats()
            }
        })
    }

    private fun applyMessageDelete(m: MST5.Message?) {
        if (m == null) return
        val cachedPeer = messagePeer(m)
        cacheDeleteMessage(
            if (m.commentPostId > 0) OutboxStore.cachePeer(cachedPeer, m.commentPostId) else cachedPeer,
            m.id
        )
        ui(object : Runnable {
            override fun run() {
                if (m.commentPostId == 0L && page === Page.CHANNEL_COMMENTS && currentCommentPost != null && currentCommentPost!!.id === m.id) {
                    status.setText(getString(R.string.channel_post_deleted))
                    showChat()
                    loadHistory()
                    return
                }
                if (m.commentPostId > 0) {
                    if (page === Page.CHANNEL_COMMENTS && cachedPeer.equals(currentPeer)
                        && currentCommentPost != null && currentCommentPost!!.id === m.commentPostId && messageRows != null
                    ) {
                        messageRows!!.removeMessage(m.id)
                    } else if (page === Page.CHAT && cachedPeer.equals(currentPeer)) {
                        scheduleChannelHistoryReload()
                    }
                } else if (messageRows != null) messageRows!!.removeMessage(m.id)
                if (seenMessages != null) seenMessages.remove(java.lang.Long.valueOf(m.id))
                if (page === Page.CHATS) loadChats()
            }
        })
    }

    private fun scheduleChannelHistoryReload() {
        main.removeCallbacks(channelHistoryReload)
        main.postDelayed(channelHistoryReload, 150L)
    }

    private fun messagePeer(m: MST5.Message?): String {
        return MessagePeerResolver.peer(m, myLogin, myID, currentPeer, currentPeerUser)
    }

    private fun messagePeerUser(m: MST5.Message?): MST5.User {
        return MessagePeerResolver.peerUser(m, myLogin, myID, currentPeerUser)
            ?: MST5.User("", "", "", "", false, false, 0)
    }

    private fun markReadIfIncoming(m: MST5.Message?, peerName: String?) {
        if (m == null || m.from == null || m.from.login.equals(myLogin)) return
        if (m.to != null && m.to.roomKind != null && m.to.roomKind.length > 0) return
        markRead(peerName)
    }

    private fun markRead(peerName: String?) {
        val c: MST5? = ta
        if (c == null || peerName == null || peerName.length == 0) return
        run("read", object : Task {
            @Throws(Exception::class)
            override fun run() {
                c.markRead(peerName)
            }
        })
    }

    private fun addMessageRow(m: MST5.Message?, atTop: Boolean) {
        if (m == null || messageRows == null || !seenMessages.add(m.id)) return
        if (oldestMessage == 0L || m.id < oldestMessage) oldestMessage = m.id
        val row: MessageRow = toMessageRow(m)
        if (atTop) messageRows!!.insert(row, 0)
        else messageRows.add(row)
    }

    private fun toMessageRow(m: MST5.Message): MessageRow {
        if (m.text != null && m.text.startsWith("data:image")) {
            return MessageRow.inlineImage(m.text, m)
        }
        if (m.file != null) {
            val kind: String? = if (m.file.mime != null && m.file.mime.toLowerCase(Locale.US)
                    .startsWith("image/")
            ) getString(R.string.message_image_prefix) else getString(R.string.message_file_prefix)
            val name: String? =
                if (m.file.name == null || m.file.name.length == 0) getString(R.string.file_fallback_name) else m.file.name
            val label = (kind + name).toString() + " (" + formatBytes(m.file.size) + ")"
            return MessageRow.file(label, m.file, m)
        }
        return MessageRow.messageText(formatMessage(m), m)
    }

    private fun formatMessage(m: MST5.Message): String? {
        return if (m.text == null) "" else m.text
    }

    private fun withCommentsCount(message: MST5.Message, commentsCount: Int): MST5.Message {
        return Message(
            message.id, message.chatId, message.from, message.to, message.text, message.date,
            message.readAt, message.media, message.buttons, message.encrypted, message.system,
            message.data, message.clientMessageId, message.editedAt, message.deliveryState,
            message.localFilePath, message.reactions, message.paidReaction, message.reactionVersion,
            message.commentPostId, commentsCount, message.replyToMessageId
        )
    }

    private fun formatMessageTime(seconds: Long): String? {
        if (seconds <= 0) return ""
        return SimpleDateFormat("HH:mm", Locale.US).format(Date(seconds * 1000L))
    }

    private fun formatMessageDateTime(seconds: Long): String? {
        if (seconds <= 0) return ""
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(seconds * 1000L))
    }

    private fun showSystemMessageDetails(message: MST5.Message?) {
        if (message == null) return
        val box: LinearLayout = LinearLayout(this)
        box.setOrientation(LinearLayout.VERTICAL)
        box.setPadding(0, gap, 0, 0)
        box.addView(
            spaced(
                systemDetailRow(
                    getString(R.string.system_detail_message),
                    if (message.text == null) "" else message.text
                )
            )
        )
        if (message.date > 0) box.addView(
            spaced(
                systemDetailRow(
                    getString(R.string.system_detail_time),
                    formatMessageDateTime(message.date)
                )
            )
        )
        if (message.from != null) box.addView(
            spaced(
                systemDetailRow(
                    getString(R.string.system_detail_from),
                    displayUser(message.from)
                )
            )
        )
        if (message.to != null) box.addView(
            spaced(
                systemDetailRow(
                    getString(R.string.system_detail_to),
                    displayUser(message.to)
                )
            )
        )
        val data = systemMessageData(message) ?: return
        val type = data.optString("type")
        if ("wallet_transfer".equals(type)) {
            box.addView(
                spaced(
                    systemDetailRow(
                        getString(R.string.system_detail_type),
                        getString(R.string.system_type_wallet_transfer)
                    )
                )
            )
            val amount: Long = data.optLong("amount", 0)
            if (amount > 0) box.addView(
                spaced(
                    systemDetailRow(
                        getString(R.string.system_detail_amount),
                        amount.toString() + " DSR"
                    )
                )
            )
            val comment: String = data.optString("comment", "")
            if (comment.length > 0) box.addView(
                spaced(
                    systemDetailRow(
                        getString(R.string.system_detail_comment),
                        comment
                    )
                )
            )
            val tx: Long = data.optLong("transaction_id", 0)
            if (tx > 0) box.addView(
                spaced(
                    systemDetailRow(
                        getString(R.string.system_detail_transaction),
                        formatTransactionID(tx)
                    )
                )
            )
            if (data.has("from_user_id")) box.addView(
                spaced(
                    systemDetailRow(
                        getString(R.string.system_detail_from_id),
                        formatPublicUserID(data.optLong("from_user_id"))
                    )
                )
            )
            if (data.has("to_user_id")) box.addView(
                spaced(
                    systemDetailRow(
                        getString(R.string.system_detail_to_id),
                        formatPublicUserID(data.optLong("to_user_id"))
                    )
                )
            )
        } else if ("call_end".equals(type)) {
            box.addView(
                spaced(
                    systemDetailRow(
                        getString(R.string.system_detail_type),
                        getString(R.string.system_type_call)
                    )
                )
            )
            var durationMs: Long = data.optLong("duration_ms", 0)
            if (durationMs <= 0) durationMs = data.optLong("duration_sec", 0) * 1000L
            if (durationMs > 0) box.addView(
                spaced(
                    systemDetailRow(
                        getString(R.string.system_detail_duration),
                        formatDuration(durationMs)
                    )
                )
            )
        } else if ("call_missed".equals(type)) {
            box.addView(
                spaced(
                    systemDetailRow(
                        getString(R.string.system_detail_type),
                        getString(R.string.system_type_missed_call)
                    )
                )
            )
        } else if (data != null) {
            box.addView(
                spaced(
                    systemDetailRow(
                        getString(R.string.system_detail_type),
                        if (type.length == 0) getString(R.string.system_type_event) else type
                    )
                )
            )
            box.addView(spaced(systemDetailRow(getString(R.string.system_detail_data), data.toString())))
        }
        showContentDialog(getString(R.string.system_details_title), box, getString(R.string.action_close), null, null)
    }

    private fun systemMessageData(message: MST5.Message?): JSONObject? {
        if (message == null || message.data == null || message.data.length == 0) return null
        try {
            return JSONObject(message.data)
        } catch (ignored: Exception) {
            return null
        }
    }

    private fun formatPublicUserID(userID: Long): String {
        return String.format(Locale.US, "%016x", userID)
    }

    private fun formatTransactionID(transactionID: Long): String {
        return String.format(Locale.US, "%016x", transactionID)
    }

    private fun systemDetailRow(titleText: String?, value: String?): LinearLayout {
        val row: LinearLayout = LinearLayout(this)
        row.setOrientation(LinearLayout.VERTICAL)
        row.setPadding(pad, gap, pad, gap)
        row.setBackgroundDrawable(shape(surface, 0, elementRadius()))
        val titleView: TextView = label(titleText)
        titleView.setTextColor(muted)
        titleView.setTextSize(13)
        row.addView(titleView, LinearLayout.LayoutParams(-1, -2))
        val valueView: TextView = label(if (value == null) "" else value)
        valueView.setTextColor(textColor)
        row.addView(valueView, LinearLayout.LayoutParams(-1, -2))
        return row
    }

    private fun showMessageMenu(message: MST5.Message?) {
        if (message == null) return
        if (!"sent".equals(message.deliveryState) && !"sent-own".equals(message.deliveryState)) {
            val failed: Boolean = OutboxStore.FAILED.equals(message.deliveryState)
            val actions: ArrayList<String> = ArrayList<String>()
            actions.add(getString(R.string.action_copy))
            if (failed) actions.add(getString(R.string.action_retry))
            actions.add(getString(R.string.action_cancel))
            showActionDialog(actions.toTypedArray(), object : ChoiceHandler {
                override fun onChoice(which: Int) {
                    val action: String = actions.get(which)
                    if (action.equals(getString(R.string.action_copy))) copyMessage(message)
                    else if (action.equals(getString(R.string.action_retry))) retryOutboxMessage(message)
                    else removeOutboxMessage(message)
                }
            })
            return
        }
        val editable = canEditMessage(message)
        val actionList: ArrayList<String> = ArrayList<String>()
        if (canReplyToMessage(message)) actionList.add(getString(R.string.action_reply))
        if (canForwardMessage(message)) actionList.add(getString(R.string.action_forward))
        actionList.add(getString(R.string.action_copy))
        if (editable) actionList.add(getString(R.string.action_edit))
        if (canDeleteMessage(message)) actionList.add(getString(R.string.action_delete))
        if (message.commentPostId == 0L) actionList.add(getString(R.string.action_save_favorite))
        showMessageActionDialog(
            message,
            actionList.toTypedArray(),
            object : ChoiceHandler {
                override fun onChoice(which: Int) {
                    val action: String = actionList.get(which)
                    if (action.equals(getString(R.string.action_reply))) startReply(message)
                    else if (action.equals(getString(R.string.action_forward))) forwardMessage(message)
                    else if (action.equals(getString(R.string.action_copy))) copyMessage(message)
                    else if (action.equals(getString(R.string.action_edit))) editMessage(message)
                    else if (action.equals(getString(R.string.action_delete))) deleteMessage(message)
                    else if (action.equals(getString(R.string.action_save_favorite))) saveToFavorites(message)
                }
            })
    }

    private fun canReplyToMessage(message: MST5.Message?): Boolean {
        if (message == null || message.id <= 0 || currentPeerBanned) return false
        return page === Page.CHANNEL_COMMENTS || (page === Page.CHAT && currentPeerCanWrite())
    }

    private fun showMessageActionDialog(message: MST5.Message, actions: Array<out String>, handler: ChoiceHandler?) {
        val dialog: Dialog = Dialog(this)
        val box: LinearLayout = dialogBox()
        val messageTitle: TextView = label(getString(R.string.system_detail_message))
        messageTitle.setTextColor(textColor)
        messageTitle.setTextSize(16)
        box.addView(messageTitle, LinearLayout.LayoutParams(-1, -2))
        val preview: TextView = label(if (message.text == null) "" else message.text)
        preview.setTextColor(muted)
        preview.setTextSize(13)
        preview.setSingleLine(true)
        preview.setEllipsize(TextUtils.TruncateAt.END)
        val previewLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        previewLp.setMargins(0, dp(2), 0, gap)
        box.addView(preview, previewLp)
        val scroll: android.widget.HorizontalScrollView = HorizontalScrollView(this)
        scroll.setHorizontalScrollBarEnabled(false)
        val row: LinearLayout = LinearLayout(this)
        row.setOrientation(LinearLayout.HORIZONTAL)
        if (canPayReaction(message)) {
            val star: ImageButton = compactDastarsButton(object : View.OnClickListener {
                override fun onClick(v: View?) {
                    dialog.dismiss()
                    handlePaidReaction(message, v)
                }
            })
            row.addView(star, compactReactionLayout())
        }
        for (emoji in ru.e6atb.chat.MainActivity.Companion.QUICK_REACTIONS) {
            val reaction: Button = compactReactionButton(emoji, object : View.OnClickListener {
                override fun onClick(v: View?) {
                    dialog.dismiss()
                    sendFreeReaction(message, if (ownReaction(message, emoji.orEmpty())) "" else emoji.orEmpty())
                }
            })
            row.addView(reaction, compactReactionLayout())
        }
        val more: Button = compactReactionButton("…", object : View.OnClickListener {
            override fun onClick(v: View?) {
                dialog.dismiss()
                showAllReactions(message)
            }
        })
        more.setContentDescription(getString(R.string.reaction_more))
        row.addView(more, compactReactionLayout())
        scroll.addView(row, LinearLayout.LayoutParams(-2, -2))
        val scrollLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        scrollLp.setMargins(0, 0, 0, gap)
        box.addView(scroll, scrollLp)
        for (i in actions.indices) {
            val which: Int = i
            val action: Button = sheetActionButton(actions[i], object : View.OnClickListener {
                override fun onClick(v: View?) {
                    dialog.dismiss()
                    if (handler != null) handler.onChoice(which)
                }
            }, isDestructiveAction(actions[i]))
            box.addView(action, LinearLayout.LayoutParams(-1, -2))
        }
        val cancel: Button = button(getString(R.string.action_cancel), object : View.OnClickListener {
            override fun onClick(v: View?) {
                dialog.dismiss()
            }
        })
        val cancelLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        cancelLp.setMargins(0, gap, 0, 0)
        box.addView(cancel, cancelLp)
        setScrollableDialogContent(dialog, box)
        showStyledDialog(dialog)
    }

    private fun compactReactionLayout(): LinearLayout.LayoutParams {
        val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        lp.setMargins(0, 0, gap / 2, 0)
        return lp
    }

    private fun compactReactionButton(emoji: String?, listener: View.OnClickListener?): Button {
        val button: Button = Button(this)
        button.setText(emoji)
        button.setTextSize(22)
        button.setTextColor(textColor)
        button.setPadding(0, 0, 0, 0)
        button.setMinWidth(0)
        button.setMinimumWidth(0)
        button.setMinHeight(0)
        button.setMinimumHeight(0)
        button.setBackgroundDrawable(pressable(surfaceHi, blend(surfaceHi, primary, 0.18f), 0, elementRadius()))
        button.setOnClickListener(listener)
        return button
    }

    private fun compactDastarsButton(listener: View.OnClickListener?): ImageButton {
        val button: ImageButton = ImageButton(this)
        val icon: Drawable = getResources().getDrawable(R.drawable.ic_dastars).mutate()
        icon.setColorFilter(textColor, android.graphics.PorterDuff.Mode.SRC_IN)
        button.setImageDrawable(icon)
        button.setScaleType(ImageView.ScaleType.CENTER)
        button.setPadding(0, 0, 0, 0)
        button.setMinimumWidth(0)
        button.setMinimumHeight(0)
        button.setBackgroundDrawable(pressable(surfaceHi, blend(surfaceHi, primary, 0.18f), 0, elementRadius()))
        button.setOnClickListener(listener)
        button.setContentDescription(getString(R.string.paid_reaction_title))
        return button
    }

    private fun showAllReactions(message: MST5.Message?) {
        val dialog: Dialog = Dialog(this)
        val box: LinearLayout = dialogBox()
        box.addView(title(getString(R.string.reaction_title)), LinearLayout.LayoutParams(-1, -2))
        if (canPayReaction(message)) {
            val star: ImageButton = compactDastarsButton(object : View.OnClickListener {
                override fun onClick(v: View?) {
                    dialog.dismiss()
                    handlePaidReaction(message, v)
                }
            })
            val first: LinearLayout = LinearLayout(this)
            first.setOrientation(LinearLayout.HORIZONTAL)
            first.setGravity(Gravity.CENTER_HORIZONTAL)
            first.addView(star, compactReactionLayout())
            box.addView(spaced(first))
        }
        var offset = 0
        while (offset < ru.e6atb.chat.MainActivity.Companion.ALL_REACTIONS.size) {
            val row: LinearLayout = LinearLayout(this)
            row.setOrientation(LinearLayout.HORIZONTAL)
            for (index in offset..<Math.min(offset + 6, ru.e6atb.chat.MainActivity.Companion.ALL_REACTIONS.size)) {
                val emoji: String = ru.e6atb.chat.MainActivity.Companion.ALL_REACTIONS[index]
                val reaction: Button = compactReactionButton(emoji, object : View.OnClickListener {
                    override fun onClick(v: View?) {
                        dialog.dismiss()
                        sendFreeReaction(message, if (ownReaction(message, emoji)) "" else emoji)
                    }
                })
                row.addView(reaction, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            box.addView(row, LinearLayout.LayoutParams(-1, -2))
            offset += 6
        }
        val close: Button = button(getString(R.string.action_close), object : View.OnClickListener {
            override fun onClick(v: View?) {
                dialog.dismiss()
            }
        })
        box.addView(spaced(close))
        setScrollableDialogContent(dialog, box)
        showStyledDialog(dialog)
    }

    private fun ownReaction(message: MST5.Message?, emoji: String): Boolean {
        if (message == null || message.reactions == null) return false
        for (reaction in message.reactions) {
            if (reaction.mine && emoji.equals(reaction.emoji)) return true
        }
        return false
    }

    private fun canPayReaction(message: MST5.Message?): Boolean {
        return message != null && message.id > 0 && !message.system && ("sent".equals(message.deliveryState) || "sent-own".equals(
            message.deliveryState
        ))
                && !isOwnUser(message.from)
    }

    private fun sendFreeReaction(message: MST5.Message?, emoji: String?) {
        val client: MST5? = ta
        if (client == null || message == null || message.id <= 0) return
        run("reaction", object : Task {
            @Throws(Exception::class)
            override fun run() {
                applyMessageUpdate(client.reactMessage(message.id, emoji.orEmpty()))
            }
        })
    }

    private fun handlePaidReaction(message: MST5.Message?, source: View?) {
        val targetMessage = message ?: return
        if (!canPayReaction(targetMessage)) return
        if (hasWalletBalance && walletBalance <= 0) {
            showDastarsTopUpDialog()
            return
        }
        if ((targetMessage.paidReaction != null && targetMessage.paidReaction!!.mineAmount > 0)
            || pendingPaidReactionDelta(targetMessage.id) > 0
        ) {
            animateReactionView(source)
            sendPaidReaction(targetMessage, 1)
            return
        }
        val amount: EditText = input(getString(R.string.paid_reaction_amount_hint), false)
        amount.setInputType(InputType.TYPE_CLASS_NUMBER)
        showContentDialog(
            getString(R.string.paid_reaction_title),
            amount,
            getString(R.string.action_send),
            object : Runnable {
                override fun run() {
                    val raw: String = amount.getText().toString().trim()
                    if (raw.length == 0) return
                    try {
                        val value: Long = java.lang.Long.parseLong(raw)
                        if (value > 0) sendPaidReaction(targetMessage, value)
                    } catch (e: NumberFormatException) {
                        status.setText(getString(R.string.status_bad_dsr_invoice))
                    }
                }
            },
            getString(R.string.action_cancel)
        )
    }

    private fun sendPaidReaction(message: MST5.Message, amount: Long) {
        val client: MST5? = ta
        if (client == null || amount <= 0) return
        if (hasWalletBalance && amount > walletBalance) {
            showDastarsTopUpDialog()
            return
        }
        val adjustKnownBalance = hasWalletBalance
        if (adjustKnownBalance) walletBalance = Math.max(0, walletBalance - amount)
        adjustPendingPaidReaction(message.id, amount)
        val messageId = message.id
        var batch = paidReactionBatches.get(messageId)
        if (batch == null) {
            val id: Long = message.id
            batch = ru.e6atb.chat.MainActivity.PaidReactionBatch()
            batch!!.flush = object : Runnable {
                override fun run() {
                    flushPaidReactionBatch(id)
                }
            }
            paidReactionBatches.put(messageId, batch)
        }
        batch.message = message
        batch.amount = safeAdd(batch.amount, amount)
        batch.adjustKnownBalance = batch.adjustKnownBalance || adjustKnownBalance
        batch.flush?.let {
            main.removeCallbacks(it)
            main.postDelayed(it, ru.e6atb.chat.MainActivity.Companion.PAID_REACTION_BATCH_DELAY_MS)
        }
    }

    private fun flushPaidReactionBatch(messageId: Long) {
        val batch: PaidReactionBatch? = paidReactionBatches.remove(java.lang.Long.valueOf(messageId))
        if (batch == null || batch.message == null || batch.amount <= 0) return
        executePaidReaction(batch.message ?: return, batch.amount, batch.adjustKnownBalance)
    }

    private fun executePaidReaction(
        message: MST5.Message,
        amount: Long,
        adjustKnownBalance: Boolean
    ) {
        val client: MST5? = ta
        if (client == null) {
            adjustPendingPaidReaction(message.id, -amount)
            if (adjustKnownBalance) walletBalance = safeAdd(walletBalance, amount)
            return
        }
        val key = "paid-reaction:" + UUID.randomUUID().toString()
        paidReactionIo.execute(object : Runnable {
            override fun run() {
                try {
                    val updated: MST5.Message? = client.sendPaidReaction(message.id, amount, key)
                    val cachedPeer = messagePeer(updated)
                    cacheAppendMessage(cachedPeer, updated)
                    ui(object : Runnable {
                        override fun run() {
                            adjustPendingPaidReaction(message.id, -amount)
                            if (page === Page.CHAT && cachedPeer.equals(currentPeer) && messageRows != null) {
                                messageRows!!.updateMessage(updated)
                            }
                            if (page === Page.CHATS) loadChats()
                        }
                    })
                } catch (error: Exception) {
                    ui(object : Runnable {
                        override fun run() {
                            adjustPendingPaidReaction(message.id, -amount)
                            if (adjustKnownBalance) walletBalance = safeAdd(walletBalance, amount)
                            if (MST5.isInvalidTokenError(error)) {
                                handleInvalidToken()
                                return
                            }
                            if (isInsufficientDastarsError(error)) {
                                showDastarsTopUpDialog()
                                return
                            }
                            status.setText(getString(R.string.status_operation_error, errorText(error)))
                        }
                    })
                }
            }
        })
    }

    private class PaidReactionBatch {
        var message: MST5.Message? = null
        var amount: Long = 0
        var adjustKnownBalance: Boolean = false
        var flush: Runnable? = null
    }

    private fun pendingPaidReactionDelta(messageId: Long): Long {
        val value = pendingPaidReactionDeltas.get(java.lang.Long.valueOf(messageId))
        return if (value == null) 0 else value.toLong()
    }

    private fun adjustPendingPaidReaction(messageId: Long, delta: Long) {
        val next = safeAdd(pendingPaidReactionDelta(messageId), delta)
        if (next <= 0) pendingPaidReactionDeltas.remove(java.lang.Long.valueOf(messageId))
        else pendingPaidReactionDeltas.put(java.lang.Long.valueOf(messageId), java.lang.Long.valueOf(next))
        if (messageRows != null) messageRows.notifyDataSetChanged()
    }

    private fun safeAdd(left: Long, right: Long): Long {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE
        if (right < 0 && left < Long.MIN_VALUE - right) return Long.MIN_VALUE
        return left + right
    }

    private fun isInsufficientDastarsError(error: Exception?): Boolean {
        val value: String = errorText(error).toLowerCase(Locale.US)
        return value.contains("insufficient dsr") || value.contains("insufficient dastars")
    }

    private fun isMediaPriceChangedError(error: Exception?): Boolean {
        val value: String = errorText(error).toLowerCase(Locale.US)
        return value.contains("media price changed") || value.contains("media purchase confirmation required")
    }

    private fun showDastarsTopUpDialog() {
        showConfirmDialog(
            getString(R.string.paid_reaction_no_dastars_title),
            getString(R.string.paid_reaction_no_dastars_message),
            getString(R.string.wallet_buy_dastars),
            object : Runnable {
                override fun run() {
                    openChatIfExists("dastarsbot", null, true)
                }
            }
        )
    }

    private fun showMediaTopUpDialog() {
        showConfirmDialog(
            getString(R.string.paid_reaction_no_dastars_title),
            getString(R.string.media_payment_no_dastars_message),
            getString(R.string.wallet_buy_dastars),
            object : Runnable {
                override fun run() {
                    openChatIfExists("dastarsbot", null, true)
                }
            }
        )
    }

    private fun animateReactionView(view: View?) {
        if (view == null) return
        val animation: ScaleAnimation = ScaleAnimation(
            1f, 1.18f, 1f, 1.18f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        animation.setDuration(160)
        animation.setRepeatCount(1)
        animation.setRepeatMode(Animation.REVERSE)
        view.startAnimation(animation)
    }

    private fun canEditMessage(message: MST5.Message?): Boolean {
        if (message == null || message.system) return false
        if (System.currentTimeMillis() / 1000L - message.date > 48L * 60L * 60L) return false
        if (message.commentPostId > 0) return isOwnUser(message.from)
        return if (currentPeerIsChannel()) currentPeerCanManageRoom() else isOwnUser(message.from)
    }

    private fun canDeleteMessage(message: MST5.Message?): Boolean {
        if (message == null || message.id <= 0) return false
        if (message.commentPostId > 0) return isOwnUser(message.from) || currentPeerCanManageRoom()
        if (currentPeerIsChannel()) return currentPeerCanManageRoom()
        return isOwnUser(message.from)
    }

    private fun editMessage(message: MST5.Message?) {
        if (message == null || text == null) return
        editingMessage = message
        text.setText(if (message.text == null) "" else message.text)
        text.setSelection(text.length)
        recycleComposerPreviews()
        composerMedia.clear()
        if (message.media != null) for (file in message.media) {
            val item: ComposerMedia = ru.e6atb.chat.MainActivity.ComposerMedia()
            item.fileId = file.id
            item.name = file.name
            item.mime = file.mime
            item.size = file.size
            item.localPath = ""
            composerMedia.add(item)
        }
        renderComposerMedia()
        status.setText(getString(R.string.action_edit))
    }

    private fun retryOutboxMessage(message: MST5.Message) {
        OutboxStore.retry(
            this,
            SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
            OutboxDispatcher.accountKey(this),
            message.clientMessageId
        )
        for (entry in OutboxStore.load(
            this,
            SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
            OutboxDispatcher.accountKey(this)
        )) {
            if (entry.id.equals(message.clientMessageId) && messageRows != null) messageRows!!.updateMessage(
                outboxMessage(entry)
            )
        }
        dispatchOutbox(ta)
    }

    private fun removeOutboxMessage(message: MST5.Message) {
        val server: String? = SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER)
        val account: String? = OutboxDispatcher.accountKey(this)
        val entry: Entry? = OutboxStore.find(this, server, account, message.clientMessageId)
        OutboxStore.requestCancel(this, server, account, message.clientMessageId)
        OutboxDispatcher.cancel(message.clientMessageId)
        OutboxStore.complete(this, server, account, message.clientMessageId)
        val client: MST5? = ta
        val clientMessageId: String = message.clientMessageId
        if (client != null && clientMessageId.length > 0) {
            run("cancel_media", object : Task {
                @Throws(Exception::class)
                override fun run() {
                    val result: JSONObject = client.cancelMessageOperation(clientMessageId)
                    if (result.optBoolean("complete") && result.optJSONObject("message") != null) {
                        val sent = client.message(result.getJSONObject("message"))?.asOutgoing() ?: return
                        ui(object : Runnable {
                            override fun run() {
                                append(sent)
                            }
                        })
                    }
                }
            })
        }
        if (messageRows != null) messageRows!!.removeMessage(message.id)
        seenMessages.remove(java.lang.Long.valueOf(message.id))
    }

    private fun copyMessage(message: MST5.Message?) {
        val value = copyText(message)
        if (value.length == 0) return
        copyToClipboard(getString(R.string.clipboard_message), value)
    }

    private fun copyToClipboard(label: String?, value: String?) {
        if (value == null || value.length == 0) return
        if (Build.VERSION.SDK_INT >= 11) {
            if (!copyToModernClipboard(label, value)) return
        } else {
            val clipboard: android.text.ClipboardManager? =
                getSystemService(CLIPBOARD_SERVICE) as android.text.ClipboardManager?
            if (clipboard == null) {
                status.setText(getString(R.string.status_clipboard_unavailable))
                return
            }
            clipboard.setText(value)
        }
        status.setText(getString(R.string.status_copied))
    }

    private fun copyToModernClipboard(label: String?, value: String?): Boolean {
        val clipboard: Any? = getSystemService(CLIPBOARD_SERVICE)
        if (clipboard == null) {
            status.setText(getString(R.string.status_clipboard_unavailable))
            return false
        }
        try {
            val clipDataClass: Class<*> = Class.forName("android.content.ClipData")
            val clip: Any? = clipDataClass
                .getMethod("newPlainText", CharSequence::class.java, CharSequence::class.java)
                .invoke(null, if (label == null) "text" else label, value)
            clipboard.javaClass.getMethod("setPrimaryClip", clipDataClass).invoke(clipboard, clip)
            return true
        } catch (e: Exception) {
            status.setText(getString(R.string.status_clipboard_error, errorText(e)))
            return false
        }
    }

    private fun copyText(message: MST5.Message?): String {
        if (message == null) return ""
        val value = if (message.text == null) "" else message.text
        if (message.media != null && !message.media.isEmpty()) {
            val names: StringBuilder = StringBuilder()
            for (file in message.media) {
                if (names.length > 0) names.append('\n')
                names.append(if (file.name == null || file.name.length == 0) "file" else file.name)
            }
            if (value.length == 0) return names.toString()
            return value.toString() + "\n" + names
        }
        return value
    }

    private fun deleteMessage(message: MST5.Message?) {
        val c: MST5? = ta
        if (c == null || message == null) return
        run("delete", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val deleted: MST5.Message? = c.deleteMessage(message.id)
                applyMessageDelete(if (deleted == null) message else deleted)
            }
        })
    }

    private fun saveToFavorites(message: MST5.Message?) {
        val c: MST5? = ta
        if (c == null || message == null) return
        run("favorite", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val saved: MST5.Message? = c.favoriteMessage(message.id)
                append(saved)
                ui(object : Runnable {
                    override fun run() {
                        status.setText(getString(R.string.status_saved_to_favorites))
                    }
                })
            }
        })
    }

    private fun canForwardMessage(message: MST5.Message?): Boolean {
        return message != null && message.id > 0 && !message.system && message.text != null && message.text.trim()
            .length > 0 && ("sent".equals(message.deliveryState) || "sent-own".equals(message.deliveryState))
    }

    private fun forwardMessage(message: MST5.Message?) {
        val client: MST5? = ta
        if (client == null || !canForwardMessage(message)) return
        var resolvedAuthor = displayUser(messageAuthor(message))
        if (resolvedAuthor.length == 0) resolvedAuthor = getString(R.string.reply_to_message)
        val originalAuthor: String? = resolvedAuthor
        run("forward_targets", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val chats: List<MST5.Chat>? = client.chats
                cacheSaveChats(chats)
                ui(object : Runnable {
                    override fun run() {
                        showForwardTargetDialog(message, originalAuthor, chats)
                    }
                })
            }
        })
    }

    private fun showForwardTargetDialog(
        message: MST5.Message?,
        originalAuthor: String?,
        chats: List<MST5.Chat>?
    ) {
        val dialog: Dialog = Dialog(this)
        val box: LinearLayout = dialogBox()
        box.addView(title(getString(R.string.forward_choose_chat)), LinearLayout.LayoutParams(-1, -2))
        var targetCount = 0
        if (chats != null) {
            for (chat in chats) {
                if (!canForwardToChat(chat)) continue
                val target = resolvedPeerName(chat.peer, chat.id)
                if (target.length == 0) continue
                val targetButton: Button = button(chatPeerTitle(chat.peer), object : View.OnClickListener {
                    override fun onClick(v: View?) {
                        dialog.dismiss()
                        forwardMessageTo(message, originalAuthor, chat, target)
                    }
                })
                box.addView(spaced(targetButton))
                targetCount++
            }
        }
        if (targetCount == 0) {
            status.setText(getString(R.string.status_no_forward_targets))
            return
        }
        val cancel: Button = button(getString(R.string.action_cancel), object : View.OnClickListener {
            override fun onClick(v: View?) {
                dialog.dismiss()
            }
        })
        box.addView(cancel, LinearLayout.LayoutParams(-1, -2))
        setScrollableDialogContent(dialog, box)
        showStyledDialog(dialog)
    }

    private fun canForwardToChat(chat: MST5.Chat?): Boolean {
        if (chat == null || chat.peer == null || chat.banned || chat.bannedByMe || chat.bannedMe) return false
        if (!"channel".equals(chat.peer.roomKind)) return true
        return chat.peer.canManage
                || (myID != null && myID.length > 0 && myID!!.equals(chat.peer.ownerId))
    }

    private fun forwardMessageTo(
        source: MST5.Message?,
        originalAuthor: String?,
        targetChat: MST5.Chat?,
        target: String
    ) {
        if (source == null || targetChat == null || targetChat.peer == null || ta == null) return
        if (source.media != null && !source.media.isEmpty()) {
            val client: MST5? = ta
            val sourceMessageId: Long = source.id
            val targetPeer: String? = target
            val clientMessageId: String? = UUID.randomUUID().toString()
            run("forward_media", object : Task {
                @Throws(Exception::class)
                override fun run() {
                    val sent: MST5.Message? =
                        client!!.forwardMedia(sourceMessageId, targetPeer.orEmpty(), clientMessageId.orEmpty()).asOutgoing()
                    ui(object : Runnable {
                        override fun run() {
                            append(sent)
                            status.setText(getString(R.string.status_forwarded_to, chatPeerTitle(targetChat.peer)))
                        }
                    })
                }
            })
            return
        }
        val forwarded: String? = ForwardMessageFormatter.compose(
            getString(R.string.forwarded_from, originalAuthor),
            source.text
        )
        if (!ForwardMessageFormatter.fitsServerLimit(forwarded)) {
            status.setText(getString(R.string.status_forward_too_long))
            return
        }
        try {
            val room = targetChat.peer.roomKind != null && targetChat.peer.roomKind.length > 0
            val entry: Entry = OutboxStore.enqueueText(
                this,
                SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
                OutboxDispatcher.accountKey(this),
                target,
                room,
                forwarded,
                0
            )
            if (page === Page.CHAT && target.equals(currentPeer) && messageRows != null) {
                addMessageRow(outboxMessage(entry), false)
                if (messageList != null) messageList.setSelection(messageRows!!.count - 1)
            }
            dispatchOutbox(ta)
            status.setText(getString(R.string.status_forwarded_to, chatPeerTitle(targetChat.peer)))
        } catch (e: Exception) {
            status.setText(errorText(e))
        }
    }

    private fun chatLastText(m: MST5.Message): String? {
        if (m.text != null && m.text.trim().length > 0) return m.text
        if (m.media != null && !m.media.isEmpty()) {
            val first: MST5.FileInfo = m.media.get(0)
            val kind: String? = if (first.mime != null && first.mime.toLowerCase(Locale.US)
                    .startsWith("image/")
            ) getString(R.string.message_image_prefix) else getString(R.string.message_file_prefix)
            val name: String? =
                if (first.name == null || first.name.length == 0) getString(R.string.file_fallback_name) else first.name
            return (kind + name).toString() + (if (m.media.size > 1) " +" + (m.media.size - 1) else "")
        }
        return ""
    }

    private fun displayUser(user: MST5.User?): String {
        if (user == null) return ""
        if (user.roomKind != null && user.roomKind.length > 0) {
            val title: String? = if (user.nick != null && user.nick.length > 0) user.nick else user.id
            return ru.e6atb.chat.MainActivity.Companion.safeDisplayText(title)
        }
        if (user.nick != null && user.nick.length > 0) {
            return ru.e6atb.chat.MainActivity.Companion.safeDisplayText(user.nick)
        }
        if (user.login != null && user.login.length > 0) {
            return displayLogin(user.login, user.verified, user.bot)
        }
        return ru.e6atb.chat.MainActivity.Companion.safeDisplayText(user.id)
    }

    private fun chatPeerTitle(user: MST5.User?): String {
        if (user != null && (user.roomKind == null || user.roomKind.length == 0) && isOwnUser(user)) {
            return getString(R.string.chat_favorites_title)
        }
        return displayUser(user)
    }

    private fun displayOwnUser(): String {
        if (myNick != null && myNick.length > 0) {
            return ru.e6atb.chat.MainActivity.Companion.safeDisplayText(myNick)
        }
        if (myLogin != null && myLogin.length > 0) {
            return displayLogin(myLogin, myVerified, myBot)
        }
        return ru.e6atb.chat.MainActivity.Companion.safeDisplayText(myID)
    }

    private fun displayLogin(login: String?, verified: Boolean, bot: Boolean): String {
        return ru.e6atb.chat.MainActivity.Companion.safeDisplayText(login)
    }

    private fun renderMarkdown(value: String?, linkColor: Int = primary): CharSequence {
        return MarkdownRenderer(object : MarkdownRenderer.Callbacks {
            override fun copyCode(code: String?) {
                copyToClipboard("code", code)
            }

            override fun openUrl(url: String?) {
                this@MainActivity.openUrl(url)
            }

            override fun openMention(login: String?) {
                this@MainActivity.openMention(login)
            }

            override fun canRunBotCommand(): Boolean {
                return page === Page.CHAT && currentPeerIsBot() && currentPeer != null && currentPeer.length > 0
            }

            override fun runBotCommand(command: String?) {
                if (currentPeer != null && currentPeer.length > 0) sendChatMessage(currentPeer, command, true)
            }

            override fun linkColor(): Int {
                return linkColor
            }
        }).render(value)
    }

    private fun openUrl(url: String?) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            status.setText(getString(R.string.status_open_link_error, errorText(e)))
        }
    }

    private fun openMention(login: String?) {
        if (login == null || login.length == 0) return
        openChatIfExists(login)
    }

    private fun server(): String {
        return connectionServer()
    }

    private fun connectionServer(): String {
        return SessionStore.transportEndpoint(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER)
    }

    private fun serverInput(): EditText {
        val field: EditText = input(getString(R.string.hint_server), false)
        field.setText(ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER)
        field.setSelection(field.getText().length)
        return field
    }

    private fun updateStatusVisibility() {
        if (status == null) {
            return
        }
        status.setVisibility(if (SessionStore.showStatus(this)) View.VISIBLE else View.GONE)
    }

    private fun applyRootPadding(root: View?) {
        if (root == null) {
            return
        }
        // Pages own their horizontal rhythm.  The app shell only reserves the
        // system bars; a global content gutter made every screen unnecessarily
        // narrow and doubled the padding of cards and chat lists.
        root.setPadding(0, 0, 0, 0)
    }

    private fun installInsetsCompat(root: View?) {
        if (root == null || Build.VERSION.SDK_INT < 20) {
            return
        }

        try {
            val listenerClass: Class<*> = Class.forName("android.view.View\$OnApplyWindowInsetsListener")

            val listener: Any? = java.lang.reflect.Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass),
                object : InvocationHandler {
                    @Throws(Throwable::class)
                    override fun invoke(proxy: Any?, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? {
                        if (!"onApplyWindowInsets".equals(method.name) || args == null || args.size < 2 || args[1] == null) {
                            return if (args == null || args.size == 0) null else args[args.size - 1]
                        }

                        val insets: Any = args[1]!!

                        val left = getInset(insets, "getSystemWindowInsetLeft")
                        val top = getInset(insets, "getSystemWindowInsetTop")
                        val right = getInset(insets, "getSystemWindowInsetRight")
                        val bottom = getInset(insets, "getSystemWindowInsetBottom")

                        root.setPadding(left, top, right, bottom)

                        return insets
                    }
                }
            )

            View::class.java.getMethod("setOnApplyWindowInsetsListener", listenerClass).invoke(root, listener)
            requestApplyInsetsCompat(root)
        } catch (ignored: Exception) {
            applyRootPadding(root)
        }
    }

    private fun getInset(insets: Any, methodName: String?): Int {
        try {
            return (insets.javaClass.getMethod(methodName).invoke(insets) as Number).toInt()
        } catch (ignored: Exception) {
            return 0
        }
    }

    private fun requestApplyInsetsCompat(root: View?) {
        if (root == null || Build.VERSION.SDK_INT < 20) {
            return
        }
        try {
            View::class.java.getMethod("requestApplyInsets").invoke(root)
        } catch (ignored: Exception) {
        }
    }

    private fun startSyncService() {
        val intent: Intent = Intent(this, MessageSyncService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                javaClass.getMethod("startForegroundService", Intent::class.java).invoke(this, intent)
                return
            } catch (ignored: Exception) {
            }
        }
        startService(intent)
    }

    private fun setStatusBarColorCompat(color: Int) {
        if (Build.VERSION.SDK_INT < 21) {
            return
        }
        try {
            window.javaClass.getMethod("setStatusBarColor", Int::class.javaPrimitiveType)
                .invoke(window, color)
        } catch (ignored: Exception) {
        }
    }

    private fun hasPermissionCompat(permission: String?): Boolean {
        if (Build.VERSION.SDK_INT < 23) {
            return true
        }
        try {
            val result: Any =
                Activity::class.java.getMethod("checkSelfPermission", String::class.java).invoke(this, permission)
            return (result as Number).toInt() == PackageManager.PERMISSION_GRANTED
        } catch (ignored: Exception) {
            return true
        }
    }

    private fun requestPermissionsCompat(permissions: Array<out String>?, requestCode: Int) {
        if (Build.VERSION.SDK_INT < 23) {
            return
        }
        try {
            Activity::class.java.getMethod(
                "requestPermissions",
                Array<String>::class.java,
                Int::class.javaPrimitiveType
            ).invoke(this, permissions, java.lang.Integer.valueOf(requestCode))
        } catch (ignored: Exception) {
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !hasPermissionCompat(ru.e6atb.chat.MainActivity.Companion.PERMISSION_POST_NOTIFICATIONS)) {
            requestPermissionsCompat(
                arrayOf<String>(
                    ru.e6atb.chat.MainActivity.Companion.PERMISSION_POST_NOTIFICATIONS
                ), ru.e6atb.chat.MainActivity.Companion.REQ_NOTIFICATIONS
            )
        }
    }

    private fun requestReadStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23 && !hasPermissionCompat(ru.e6atb.chat.MainActivity.Companion.PERMISSION_READ_EXTERNAL_STORAGE)) {
            requestPermissionsCompat(
                arrayOf<String>(
                    ru.e6atb.chat.MainActivity.Companion.PERMISSION_READ_EXTERNAL_STORAGE
                ), ru.e6atb.chat.MainActivity.Companion.REQ_READ_STORAGE
            )
        }
    }

    private fun run(op: String?, task: Task) {
        io.execute(object : Runnable {
            override fun run() {
                try {
                    task.run()
                } catch (e: Exception) {
                    ui(object : Runnable {
                        override fun run() {
                            if (MST5.isInvalidTokenError(e)) {
                                handleInvalidToken()
                                return
                            }
                            status.setText(getString(R.string.status_operation_error, errorText(e)))
                        }
                    })
                }
            }
        })
    }

    private fun ui(r: Runnable) {
        main.post(r)
    }

    private fun input(hint: String?, secret: Boolean): EditText {
        val e: EditText = EditText(this)
        e.setHint(hint)
        e.setTextColor(textColor)
        e.setHintTextColor(muted)
        e.setBackgroundDrawable(shape(surfaceHi, border, elementRadius()))
        e.setPadding(buttonPadX, buttonPadY, buttonPadX, buttonPadY)
        e.setMinHeight(buttonMinHeight)
        e.setMinimumHeight(buttonMinHeight)
        e.setIncludeFontPadding(false)
        e.setGravity(Gravity.CENTER_VERTICAL)
        e.setSingleLine(true)
        e.setFilters(arrayOf<android.text.InputFilter>(object : android.text.InputFilter {
            override fun filter(
                source: CharSequence?,
                start: Int,
                end: Int,
                dest: Spanned?,
                dstart: Int,
                dend: Int
            ): CharSequence? {
                if (source == null || start >= end) return null
                val raw = source.subSequence(start, end).toString()
                val safe: String = ru.e6atb.chat.MainActivity.Companion.safeDisplayText(raw)
                return if (raw.equals(safe)) null else safe
            }
        }
        ))
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        return e
    }

    private fun checkBox(s: String?, checked: Boolean): CheckBox {
        val b: CheckBox = ru.e6atb.chat.MainActivity.ChoiceCheckBox(this, choiceButtonTextInset())
        b.setText(ru.e6atb.chat.MainActivity.Companion.safeDisplayText(s))
        styleChoiceButton(b, false)
        b.setChecked(checked)
        return b
    }

    private fun styleChoiceButton(button: CompoundButton, radio: Boolean) {
        button.setTextColor(textColor)
        button.setButtonDrawable(choiceButtonDrawable(radio))
        button.setCompoundDrawablePadding(choiceButtonGap())
        button.setGravity(Gravity.CENTER_VERTICAL)
        button.setMinHeight(dp(48))
        button.setMinimumHeight(dp(48))
        button.setSingleLine(false)
        button.setPadding(pad, gap, pad, gap)
        button.setBackgroundDrawable(pressable(surface, surfaceHi, 0, elementRadius()))
    }

    private fun choiceButtonTextInset(): Int {
        return choiceButtonLeadingInset() + choiceButtonSize() + choiceButtonGap()
    }

    private fun choiceButtonSize(): Int {
        return dp(22)
    }

    private fun choiceButtonLeadingInset(): Int {
        return pad
    }

    private fun choiceButtonGap(): Int {
        return dp(12)
    }

    private fun button(s: String?, l: android.view.View.OnClickListener?): Button {
        val b: Button = Button(this)
        b.setText(ru.e6atb.chat.MainActivity.Companion.safeDisplayText(s))
        b.setTextColor(textColor)
        b.setBackgroundDrawable(pressable(surface, surfaceHi, 0, buttonRadius()))
        b.setPadding(buttonPadX, buttonPadY, buttonPadX, buttonPadY)
        b.setMinHeight(buttonMinHeight)
        b.setMinimumHeight(buttonMinHeight)
        b.setMinWidth(0)
        b.setMinimumWidth(0)
        b.setSingleLine(false)
        b.setMaxLines(2)
        b.setOnClickListener(l)
        return b
    }

    private fun primaryButton(s: String?, l: android.view.View.OnClickListener?): Button {
        val b: Button = button(s, l)
        b.setTextColor(onPrimary)
        b.setBackgroundDrawable(pressable(primary, blend(primary, Color.WHITE, 0.18f), 0, buttonRadius()))
        return b
    }

    private fun setButtonBusy(
        button: Button?,
        busy: Boolean,
        busyText: String?,
        idleText: String?,
        primaryStyle: Boolean
    ) {
        if (button == null) return
        button.setText(ru.e6atb.chat.MainActivity.Companion.safeDisplayText(if (busy) busyText else idleText))
        setButtonEnabledStyle(button, !busy, primaryStyle)
        setButtonRequestBusy(button, busy)
    }

    private fun setButtonEnabledStyle(button: Button?, enabled: Boolean, primaryStyle: Boolean) {
        if (button == null) return
        button.setEnabled(enabled)
        if (primaryStyle) {
            val normal = if (enabled) primary else blend(primary, Color.BLACK, 0.30f)
            val pressed = if (enabled) blend(primary, Color.WHITE, 0.18f) else blend(primary, Color.BLACK, 0.22f)
            button.setTextColor(if (enabled) onPrimary else blend(onPrimary, bg, 0.42f))
            button.setBackgroundDrawable(pressable(normal, pressed, 0, buttonRadius()))
        } else {
            val normal = if (enabled) surface else blend(surface, Color.BLACK, 0.25f)
            val pressed = if (enabled) surfaceHi else blend(surface, Color.BLACK, 0.18f)
            button.setTextColor(if (enabled) textColor else blend(textColor, bg, 0.55f))
            button.setBackgroundDrawable(pressable(normal, pressed, 0, buttonRadius()))
        }
    }

    private fun buttonRadius(): Int {
        return elementRadius()
    }

    private fun elementRadius(): Int {
        return dp(18)
    }

    private fun setButtonRequestBusy(button: View?, busy: Boolean) {
        if (button == null) return
        if (busy) startButtonBusyAnimation(button)
        else stopButtonBusyAnimation(button)
    }

    private fun setActionButtonLoading(button: View?, loading: Boolean, primaryStyle: Boolean) {
        if (button == null) return
        if (button is Button) {
            setButtonEnabledStyle(button as Button, !loading, primaryStyle)
        } else {
            button.setEnabled(!loading)
        }
        setButtonRequestBusy(button, loading)
    }

    private fun runButtonTask(name: String?, actionButton: View?, primaryStyle: Boolean, task: Task) {
        setActionButtonLoading(actionButton, true, primaryStyle)
        run(name, object : Task {
            @Throws(Exception::class)
            override fun run() {
                try {
                    task.run()
                } finally {
                    ui(object : Runnable {
                        override fun run() {
                            setActionButtonLoading(actionButton, false, primaryStyle)
                        }
                    })
                }
            }
        })
    }

    private fun startButtonBusyAnimation(view: View?) {
        if (view == null) return
        view.clearAnimation()
        val set: AnimationSet = AnimationSet(true)
        set.setInterpolator(AccelerateDecelerateInterpolator())
        set.setFillAfter(true)
        val alpha: AlphaAnimation = AlphaAnimation(0.58f, 1.0f)
        alpha.setDuration(520)
        alpha.setRepeatCount(Animation.INFINITE)
        alpha.setRepeatMode(Animation.REVERSE)
        val scale: ScaleAnimation = ScaleAnimation(
            0.96f,
            1.0f,
            0.96f,
            1.0f,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f
        )
        scale.setDuration(520)
        scale.setRepeatCount(Animation.INFINITE)
        scale.setRepeatMode(Animation.REVERSE)
        set.addAnimation(alpha)
        set.addAnimation(scale)
        view.startAnimation(set)
    }

    private fun stopButtonBusyAnimation(view: View?) {
        if (view == null) return
        view.clearAnimation()
    }

    private fun messageActionButton(s: String?, l: android.view.View.OnClickListener?): Button {
        val b: Button = button(s, l)
        b.setTextColor(onPrimary)
        b.setBackgroundDrawable(
            pressable(
                primary,
                blend(primary, Color.WHITE, 0.18f),
                blend(primary, Color.WHITE, 0.35f),
                buttonRadius()
            )
        )
        return b
    }

    private fun iconButton(iconRes: Int, description: String?, l: android.view.View.OnClickListener?): ImageButton {
        val b: ImageButton = ImageButton(this)
        configureIconButton(b, iconRes, description, l, dp(24), buttonRadius())
        return b
    }

    private fun headerIconButton(
        iconRes: Int,
        description: String?,
        l: android.view.View.OnClickListener?
    ): ImageButton {
        val b: ImageButton = ImageButton(this)
        configureIconButton(b, iconRes, description, l, dp(20), buttonRadius())
        return b
    }

    private fun inputIconButton(
        iconRes: Int,
        description: String?,
        l: android.view.View.OnClickListener?
    ): ImageButton {
        val b: ImageButton = ImageButton(this)
        configureIconButton(b, iconRes, description, l, dp(22), buttonRadius())
        return b
    }

    private fun configureIconButton(
        b: ImageButton,
        iconRes: Int,
        description: String?,
        l: android.view.View.OnClickListener?,
        iconSize: Int,
        radius: Int
    ) {
        val icon: Drawable = getResources().getDrawable(iconRes)
        icon.setBounds(0, 0, iconSize, iconSize)
        b.setImageDrawable(icon)
        b.setScaleType(ImageView.ScaleType.CENTER)
        b.setColorFilter(muted)
        b.setBackgroundDrawable(pressable(surface, surfaceHi, 0, radius))
        b.setPadding(0, 0, 0, 0)
        b.setMinimumWidth(buttonMinHeight)
        b.setMinimumHeight(buttonMinHeight)
        b.setContentDescription(description)
        b.setOnClickListener(l)
    }

    private fun setDastarsButtonIcon(button: Button?, color: Int, iconSize: Int) {
        if (button == null) return
        val icon: Drawable = getResources().getDrawable(R.drawable.ic_dastars).mutate()
        icon.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
        icon.setBounds(0, 0, iconSize, iconSize)
        button.setCompoundDrawables(icon, null, null, null)
        button.setCompoundDrawablePadding(gap / 2)
    }

    private fun title(s: String?): TextView {
        val v: TextView = label(s)
        v.setTextSize(18)
        v.setPadding(gap, pad, gap, gap)
        return v
    }

    private fun label(s: String?): TextView {
        val v: TextView = TextView(this)
        v.setText(ru.e6atb.chat.MainActivity.Companion.safeDisplayText(s))
        v.setTextColor(textColor)
        return v
    }

    private fun adapter(): MessageAdapter {
        return MessageAdapter()
    }

    private fun chatAvatar(value: String?, size: Int): TextView {
        val avatar: TextView = TextView(this)
        val safe: String = ru.e6atb.chat.MainActivity.Companion.safeDisplayText(value).trim()
        val initials: String? = if (safe.length == 0) "?" else safe.substring(0, 1).toUpperCase(Locale.US)
        val tint = blend(primary, Color.rgb(72, 96, 150), (safe.hashCode() and 3) * 0.12f)
        avatar.setText(initials)
        avatar.setTextColor(onPrimary)
        avatar.setTextSize(15)
        avatar.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        avatar.setGravity(Gravity.CENTER)
        avatar.setBackgroundDrawable(shape(tint, 0, size / 2))
        avatar.setContentDescription(safe)
        return avatar
    }

    private fun ownProfileAvatar(size: Int): FrameLayout {
        val box = FrameLayout(this)
        box.addView(chatAvatar(displayOwnUser(), size), FrameLayout.LayoutParams(size, size))
        val avatar = myAvatar ?: return box
        if (avatar.id.isEmpty()) return box
        val image = ImageView(this)
        image.setScaleType(ImageView.ScaleType.CENTER_CROP)
        image.setBackgroundDrawable(shape(surfaceHi, 0, size / 2))
        box.addView(image, FrameLayout.LayoutParams(size, size))
        loadAvatarInto(avatar, image)
        return box
    }

    private fun loadAvatarInto(avatar: MST5.FileInfo, target: ImageView) {
        val client = ta ?: return
        io.execute(object : Runnable {
            override fun run() {
                try {
                    val image = rs.ove.crypt.proto.Mst5ImageDecoder.decode(client.downloadFileBytes(avatar.id, 1_048_576), 256)
                    ui(object : Runnable {
                        override fun run() {
                            // The view may still be waiting to be attached while its profile
                            // screen is being assembled.  Setting the bitmap is safe in either
                            // case and prevents an intermittent initials-only preview.
                            if (image != null && !isFinishing) target.setImageBitmap(image)
                        }
                    })
                } catch (_: Exception) {
                    // The initials placeholder remains visible when the file is unavailable.
                }
            }
        })
    }

    private fun deleteAvatar() {
        val client = ta ?: return
        run("delete_avatar", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val user = client.deletePublicAvatar()
                myAvatar = null
                applyOwnUser(user)
                ui(object : Runnable {
                    override fun run() {
                        if (page === Page.SETTINGS_PROFILE) showSettingsProfile()
                    }
                })
            }
        })
    }

    private class BubbleLayout(context: Context?, private val maxBubbleWidth: Int) : LinearLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available: Int = MeasureSpec.getSize(widthMeasureSpec)
            val cap = if (available > 0) Math.min(available, maxBubbleWidth) else maxBubbleWidth
            super.onMeasure(MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST), heightMeasureSpec)
        }
    }

    private fun paymentSlider(hint: String?): PaymentSliderView {
        return paymentSlider(hint, false)
    }

    private fun paymentSlider(hint: String?, confirmLeft: Boolean): PaymentSliderView {
        return PaymentSliderView(this, hint, confirmLeft, paymentSliderTheme())
    }

    private fun paymentSliderTheme(): PaymentSliderView.Theme {
        return object : PaymentSliderView.Theme {
            override fun dp(value: Int): Int {
                return this@MainActivity.dp(value)
            }

            override fun elementRadius(): Int {
                return this@MainActivity.elementRadius()
            }

            override fun blend(a: Int, b: Int, t: Float): Int {
                return this@MainActivity.blend(a, b, t)
            }

            override fun surfaceHi(): Int {
                return surfaceHi
            }

            override fun primary(): Int {
                return primary
            }

            override fun muted(): Int {
                return muted
            }

            override fun onPrimary(): Int {
                return onPrimary
            }
        }
    }

    private inner class MessageAdapter : android.widget.BaseAdapter() {
        private val rows: MutableList<MessageRow> = ArrayList<MessageRow>()

        fun clear() {
            rows.clear()
            notifyDataSetChanged()
        }

        fun add(s: String?) {
            add(MessageRow.text(s)!!)
        }

        fun add(row: MessageRow) {
            rows.add(row)
            notifyDataSetChanged()
        }

        fun replaceRows(nextRows: List<MessageRow>?) {
            rows.clear()
            if (nextRows != null) rows.addAll(nextRows)
            notifyDataSetChanged()
        }

        fun insert(row: MessageRow, index: Int) {
            rows.add(index, row)
            notifyDataSetChanged()
        }

        fun insertRows(nextRows: List<MessageRow>?, index: Int) {
            if (nextRows == null || nextRows.isEmpty()) return
            rows.addAll(index, nextRows)
            notifyDataSetChanged()
        }

        fun updateMessage(message: MST5.Message?): Boolean {
            var next = message ?: return false
            for (i in 0..<rows.size) {
                val row: MessageRow = rows[i]
                val existing = row.message ?: continue
                if (existing.id == next.id
                            || (next.clientMessageId.length > 0
                            && next.clientMessageId.equals(existing.clientMessageId))
                ) {
                    if (next.reactionVersion < existing.reactionVersion) return false
                    if (next.commentPostId == 0L && next.commentsCount == 0 && existing.commentsCount > 0) {
                        next = withCommentsCount(next, existing.commentsCount)
                    }
                    rows[i] = toMessageRow(next) ?: return false
                    notifyDataSetChanged()
                    return true
                }
            }
            return false
        }

        fun removeMessage(messageID: Long) {
            for (i in 0..<rows.size) {
                val row: MessageRow = rows[i]
                if (row.message != null && row.message!!.id == messageID) {
                    rows.removeAt(i)
                    notifyDataSetChanged()
                    return
                }
            }
        }

        override fun getCount(): Int = rows.size

        override fun getItem(position: Int): Any {
            return rows[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        fun messageById(messageId: Long): MST5.Message? {
            for (row in rows) {
                if (row.message != null && row.message!!.id == messageId) return row.message
            }
            return null
        }

        fun positionOfMessage(messageId: Long): Int {
            for (i in 0..<rows.size) {
                val row: MessageRow = rows[i]
                if (row.message != null && row.message!!.id == messageId) return i
            }
            return -1
        }

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup?): View {
            val row: MessageRow = rows[pos]
            if (row.message != null) {
                return messageView(row)
            }
            if (row.imageData != null) {
                return imageView(row.imageData, convertView)
            }
            if (row.file != null) {
                if (isImageFile(row.file)) {
                    return imageFileView(row)
                }
                return fileView(row)
            }
            if (row.chatTitle != null) {
                return chatPreviewView(row, convertView)
            }
            return textView(row.text, convertView)
        }

        fun chatPreviewView(row: MessageRow, convertView: View?): View {
            val box: LinearLayout = LinearLayout(this@MainActivity)
            box.setOrientation(LinearLayout.HORIZONTAL)
            box.setGravity(Gravity.CENTER_VERTICAL)
            box.setPadding(pad, dp(10), pad, dp(10))
            box.setBackgroundDrawable(pressable(bg, accentSurface, 0, 0))
            val avatar: TextView = chatAvatar(row.chatTitle, dp(44))
            box.addView(avatar, LinearLayout.LayoutParams(dp(44), dp(44)))
            val details: LinearLayout = LinearLayout(this@MainActivity)
            details.setOrientation(LinearLayout.VERTICAL)
            val top: LinearLayout = LinearLayout(this@MainActivity)
            top.setOrientation(LinearLayout.HORIZONTAL)
            top.setGravity(Gravity.CENTER_VERTICAL)
            val nameLine: LinearLayout = LinearLayout(this@MainActivity)
            nameLine.setOrientation(LinearLayout.HORIZONTAL)
            nameLine.setGravity(Gravity.CENTER_VERTICAL)
            val title: TextView = TextView(this@MainActivity)
            title.setText(ru.e6atb.chat.MainActivity.Companion.safeDisplayText(row.chatTitle))
            title.setTextColor(textColor)
            title.setTextSize(16)
            title.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
            title.setSingleLine(true)
            title.setEllipsize(TextUtils.TruncateAt.END)
            title.setMaxWidth(dp(170))
            nameLine.addView(title, LinearLayout.LayoutParams(-2, -2))
            if (row.chatVerified) {
                val badge: ImageView = ImageView(this@MainActivity)
                badge.setImageDrawable(verifiedDrawable(dp(16)))
                badge.setContentDescription(getString(R.string.verified))
                val badgeLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
                badgeLp.setMargins(gap / 2, 0, 0, 0)
                nameLine.addView(badge, badgeLp)
            }
            top.addView(nameLine, LinearLayout.LayoutParams(0, -2, 1f))
            val time: TextView = TextView(this@MainActivity)
            time.setText(formatMessageTime(row.chatDate))
            time.setTextColor(if (row.chatDate > 0) primary else muted)
            time.setTextSize(12)
            time.setGravity(Gravity.RIGHT)
            top.addView(time, LinearLayout.LayoutParams(-2, -2))
            details.addView(top, LinearLayout.LayoutParams(-1, -2))
            val previewLine: LinearLayout = LinearLayout(this@MainActivity)
            previewLine.setGravity(Gravity.CENTER_VERTICAL)
            val preview: TextView = TextView(this@MainActivity)
            preview.setText(ru.e6atb.chat.MainActivity.Companion.safeDisplayText(row.chatPreview))
            preview.setTextColor(muted)
            preview.setTextSize(13)
            preview.setSingleLine(true)
            preview.setEllipsize(TextUtils.TruncateAt.END)
            val previewLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
            previewLp.setMargins(0, dp(2), 0, 0)
            previewLine.addView(preview, LinearLayout.LayoutParams(0, -2, 1f))
            if (row.chatUnreadCount > 0) {
                val unread: TextView = TextView(this@MainActivity)
                unread.setText(if (row.chatUnreadCount > 99) "99+" else java.lang.String.valueOf(row.chatUnreadCount))
                unread.setTextColor(onPrimary)
                unread.setTextSize(11)
                unread.setGravity(Gravity.CENTER)
                unread.setMinWidth(dp(20))
                unread.setMinHeight(dp(20))
                unread.setPadding(dp(5), 0, dp(5), 0)
                unread.setBackgroundDrawable(shape(primary, 0, dp(10)))
                val unreadLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-2, dp(20))
                unreadLp.setMargins(gap, 0, 0, 0)
                previewLine.addView(unread, unreadLp)
            }
            details.addView(previewLine, previewLp)
            val detailsLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            detailsLp.setMargins(dp(12), 0, 0, 0)
            box.addView(details, detailsLp)
            return box
        }

        fun messageView(row: MessageRow): View {
            if (row.message != null && row.message.system) {
                return systemMessageView(row)
            }
            val own = row.message != null && isOwnUser(row.message.from)
            val outer: LinearLayout = LinearLayout(this@MainActivity)
            outer.setOrientation(LinearLayout.VERTICAL)
            outer.setGravity(if (own) Gravity.RIGHT else Gravity.LEFT)
            val box: LinearLayout = bubbleBox(own)
            installMessageLongPress(box, row.message)
            if (!own || currentPeerIsRoom()) {
                val author: LinearLayout = userNameRow(messageAuthor(row.message), 14)
                author.setClickable(true)
                author.setBackgroundDrawable(pressable(Color.TRANSPARENT, blend(primary, surface, 0.84f), 0, dp(8)))
                val authorUser: MST5.User = messageAuthor(row.message)
                author.setOnClickListener(object : View.OnClickListener {
                    override fun onClick(v: View?) {
                        showUserProfile(authorUser)
                    }
                })
                box.addView(author, LinearLayout.LayoutParams(-1, -2))
            }
            addReplyReference(box, row.message)
            if (row.imageData != null) {
                val contentLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
                contentLp.setMargins(0, gap / 3, 0, 0)
                box.addView(imageContent(row.imageData), contentLp)
            }
            if (row.message != null && row.message.media != null) {
                for (mediaIndex in 0..<row.message.media.size) {
                    // Older server responses could contain a null media entry.
                    // Do not let one malformed attachment crash ListView layout.
                    val file: MST5.FileInfo = row.message.media.getOrNull(mediaIndex) ?: continue
                    val kind: String? =
                        if (isImageFile(file)) getString(R.string.message_image_prefix) else getString(R.string.message_file_prefix)
                    val name: String? =
                        if (file.name.isNullOrEmpty()) getString(R.string.file_fallback_name) else file.name
                    val mediaRow: MessageRow = MessageRow.file(
                        (kind + name).toString() + " (" + formatBytes(file.size) + ")",
                        file,
                        row.message
                    )
                    val labelLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
                    labelLp.setMargins(0, gap / 3, 0, 0)
                    box.addView(fileLabel(mediaRow.text), labelLp)
                    if (mediaIndex == 0 && !row.message.localFilePath.isNullOrEmpty()) {
                        if (isImageFile(file)) addLocalImagePreview(box, row.message.localFilePath)
                    } else if (!file.id.isNullOrEmpty()) {
                        if (isImageFile(file)) addImagePreview(box, mediaRow)
                        addDownloadButton(box, mediaRow)
                    }
                }
            }
            val messageText = if (row.message == null || row.message.text == null) "" else row.message.text
            if (messageText.length > 0) {
                val body: TextView = messageTextLabel(messageText, own)
                installMessageLongPress(body, row.message)
                val bodyLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
                bodyLp.setMargins(0, gap / 3, 0, 0)
                box.addView(body, bodyLp)
            }
            addMessageMeta(box, row.message)
            addChannelCommentsLink(box, row.message)
            outer.addView(box, LinearLayout.LayoutParams(-2, -2))
            addMessageButtons(outer, row.message)
            return listItemFrame(outer)
        }

        fun installMessageLongPress(view: View?, message: MST5.Message?) {
            if (view == null || message == null) return
            view.setLongClickable(true)
            view.setOnLongClickListener(object : View.OnLongClickListener {
                override fun onLongClick(v: View?): Boolean {
                    showMessageMenu(message)
                    return true
                }
            })
        }

        fun addReplyReference(box: LinearLayout, message: MST5.Message?) {
            if (message == null || message.replyToMessageId <= 0) return
            val target: MST5.Message? = messageById(message.replyToMessageId)
            val reference: LinearLayout = LinearLayout(this@MainActivity)
            reference.setOrientation(LinearLayout.HORIZONTAL)
            reference.setPadding(gap, gap / 2, gap, gap / 2)
            reference.setBackgroundDrawable(
                pressable(
                    blend(surfaceHi, surface, 0.42f),
                    blend(surfaceHi, primary, 0.16f), 0, elementRadius()
                )
            )
            val stripe: View = View(this@MainActivity)
            stripe.setBackgroundColor(primary)
            val stripeLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(dp(3), -1)
            stripeLp.setMargins(0, 0, gap, 0)
            reference.addView(stripe, stripeLp)
            val details: LinearLayout = LinearLayout(this@MainActivity)
            details.setOrientation(LinearLayout.VERTICAL)
            val author: TextView =
                label(if (target == null) getString(R.string.reply_to_message) else replyAuthor(target))
            author.setTextColor(primary)
            author.setTextSize(13)
            details.addView(author, LinearLayout.LayoutParams(-1, -2))
            val preview: TextView =
                label(if (target == null) getString(R.string.reply_message_unavailable) else replySummary(target))
            preview.setTextColor(muted)
            preview.setTextSize(14)
            preview.setSingleLine(true)
            preview.setEllipsize(TextUtils.TruncateAt.END)
            details.addView(preview, LinearLayout.LayoutParams(-1, -2))
            reference.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            reference.setOnClickListener(object : View.OnClickListener {
                override fun onClick(v: View?) {
                    focusMessage(message.replyToMessageId)
                }
            })
            val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(0, gap / 3, 0, gap / 3)
            box.addView(reference, lp)
        }

        fun addChannelCommentsLink(box: LinearLayout, message: MST5.Message?) {
            if (page !== Page.CHAT || !currentPeerIsChannel() || currentPeerUser == null || !currentPeerUser!!.commentsEnabled || message == null || message.commentPostId > 0) return
            val value: String? = if (message.commentsCount > 0)
                getString(R.string.channel_comments_count, message.commentsCount)
            else
                getString(R.string.channel_comments)
            val divider: View = View(this@MainActivity)
            divider.setBackgroundColor(blend(muted, surface, 0.72f))
            val dividerLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, dp(1))
            dividerLp.setMargins(0, gap / 2, 0, 0)
            box.addView(divider, dividerLp)
            val comments: TextView = label(value)
            comments.setTextColor(primary)
            comments.setGravity(Gravity.CENTER_VERTICAL)
            comments.setMinHeight(buttonMinHeight)
            comments.setPadding(0, gap / 2, 0, 0)
            comments.setBackgroundDrawable(pressable(Color.TRANSPARENT, blend(surfaceHi, surface, 0.35f), 0, 0))
            comments.setOnClickListener(object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showChannelComments(message)
                }
            })
            val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
            box.addView(comments, lp)
        }

        fun systemMessageView(row: MessageRow): View {
            val outer: LinearLayout = LinearLayout(this@MainActivity)
            outer.setOrientation(LinearLayout.VERTICAL)
            outer.setGravity(Gravity.CENTER_HORIZONTAL)
            outer.setPadding(0, gap / 2, 0, gap / 2)

            val pill: TextView = TextView(this@MainActivity)
            pill.setTextColor(muted)
            pill.setTextSize(13)
            pill.setGravity(Gravity.CENTER)
            pill.setMaxWidth(Math.max(dp(180), getResources().getDisplayMetrics().widthPixels - pad * 4))
            pill.setPadding(pad, gap, pad, gap)
            pill.setBackgroundDrawable(
                pressable(
                    blend(surface, bg, 0.48f),
                    blend(surfaceHi, bg, 0.38f),
                    0,
                    elementRadius()
                )
            )
            pill.setText(ru.e6atb.chat.MainActivity.Companion.safeDisplayText(row.text))
            pill.setOnClickListener(object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showSystemMessageDetails(row.message)
                }
            })
            pill.setOnLongClickListener(object : View.OnLongClickListener {
                override fun onLongClick(v: View?): Boolean {
                    showMessageMenu(row.message)
                    return true
                }
            })
            outer.addView(pill, LinearLayout.LayoutParams(-2, -2))
            addMessageReactions(outer, row.message)
            return listItemFrame(outer)
        }

        fun textView(value: String?, convertView: View?): View {
            val tv: TextView
            if (convertView is TextView) {
                tv = convertView as TextView
            } else {
                tv = TextView(this@MainActivity)
            }
            tv.setTextColor(textColor)
            tv.setTextSize(16)
            tv.setPadding(pad, pad, pad, pad)
            tv.setBackgroundDrawable(shape(surface, 0, elementRadius()))
            tv.setText(ru.e6atb.chat.MainActivity.Companion.safeDisplayText(value))
            return listItemFrame(tv)
        }

        fun imageView(payload: String, convertView: View?): View {
            val iv: ImageView
            if (convertView is ImageView) {
                iv = convertView as ImageView
            } else {
                iv = ImageView(this@MainActivity)
                iv.setAdjustViewBounds(true)
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER)
                val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
                lp.setMargins(pad, pad, pad, pad)
                iv.setLayoutParams(lp)
            }
            try {
                val base64Part: String? = payload.substring(payload.indexOf(',') + 1)
                val data: ByteArray? = Base64.decode(base64Part, Base64.DEFAULT)
                val bmp: Bitmap? = rs.ove.crypt.proto.Mst5ImageDecoder.decode(
                    data,
                    ru.e6atb.chat.MainActivity.Companion.MAX_IMAGE_PREVIEW_PX
                )
                iv.setImageBitmap(bmp)
            } catch (e: Exception) {
                return textView(getString(R.string.invalid_image), null)
            }
            iv.setBackgroundDrawable(shape(surface, 0, elementRadius()))
            return listItemFrame(iv)
        }

        fun imageContent(payload: String): View {
            val iv: ImageView = ImageView(this@MainActivity)
            iv.setAdjustViewBounds(true)
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER)
            try {
                val base64Part: String? = payload.substring(payload.indexOf(',') + 1)
                val data: ByteArray? = Base64.decode(base64Part, Base64.DEFAULT)
                val bmp: Bitmap? = rs.ove.crypt.proto.Mst5ImageDecoder.decode(
                    data,
                    ru.e6atb.chat.MainActivity.Companion.MAX_IMAGE_PREVIEW_PX
                )
                iv.setImageBitmap(bmp)
            } catch (e: Exception) {
                return fileLabel(getString(R.string.invalid_image))
            }
            iv.setBackgroundDrawable(shape(surfaceHi, 0, elementRadius()))
            return iv
        }

        fun addLocalImagePreview(box: LinearLayout, path: String?) {
            val source: ComposerMedia = ru.e6atb.chat.MainActivity.ComposerMedia()
            if (path != null && path.startsWith("content://")) source.uri = Uri.parse(path)
            else source.localPath = path
            val bmp: Bitmap? = decodeComposerPreview(source, ru.e6atb.chat.MainActivity.Companion.MAX_IMAGE_PREVIEW_PX)
            if (bmp == null) return
            val preview: ImageView = ImageView(this@MainActivity)
            preview.setAdjustViewBounds(true)
            preview.setScaleType(ImageView.ScaleType.FIT_CENTER)
            preview.setMaxHeight(dp(360))
            preview.setImageBitmap(bmp)
            val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(0, gap, 0, 0)
            box.addView(preview, lp)
        }

        fun imageFileView(row: MessageRow): View {
            val box: LinearLayout = fileBox()
            val label: TextView = fileLabel(row.text)
            box.addView(label, LinearLayout.LayoutParams(-1, -2))
            addImagePreview(box, row)
            addDownloadButton(box, row)
            return listItemFrame(box)
        }

        fun addImagePreview(box: LinearLayout, row: MessageRow) {
            val key = imageCacheKey(row.file)
            val bmp: Bitmap? = cachedImagePreview(key)
            val error: String? = cachedImagePreviewError(key)
            if (bmp != null) {
                val preview: ImageView = ImageView(this@MainActivity)
                preview.setAdjustViewBounds(true)
                preview.setScaleType(ImageView.ScaleType.FIT_CENTER)
                preview.setMaxHeight(dp(360))
                preview.setImageBitmap(bmp)
                preview.setBackgroundDrawable(shape(surfaceHi, 0, elementRadius()))
                preview.setPadding(gap, gap, gap, gap)
                preview.setOnClickListener(object : View.OnClickListener {
                    override fun onClick(v: View?) {
                        downloadFile(row.file)
                    }
                })
                val imageLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
                imageLp.setMargins(0, gap, 0, 0)
                box.addView(preview, imageLp)
            } else {
                val placeholder: TextView = TextView(this@MainActivity)
                placeholder.setTextColor(muted)
                placeholder.setTextSize(14)
                placeholder.setGravity(Gravity.CENTER)
                placeholder.setMinHeight(dp(120))
                placeholder.setPadding(pad, pad, pad, pad)
                placeholder.setBackgroundDrawable(shape(surfaceHi, 0, elementRadius()))
                placeholder.setText(if (error == null) getString(R.string.loading_image) else getString(R.string.preview_unavailable))
                val placeholderLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
                placeholderLp.setMargins(0, gap, 0, 0)
                box.addView(placeholder, placeholderLp)
                if (error == null) {
                    startImagePreviewLoad(row.file, this)
                }
            }
        }

        fun fileView(row: MessageRow): View {
            val box: LinearLayout = fileBox()
            box.addView(fileLabel(row.text), LinearLayout.LayoutParams(-1, -2))
            addDownloadButton(box, row)
            return listItemFrame(box)
        }

        fun listItemFrame(child: View?): View {
            val frame: LinearLayout = LinearLayout(this@MainActivity)
            frame.setOrientation(LinearLayout.VERTICAL)
            val vertical = gap / 2
            frame.setPadding(pad, vertical, pad, vertical)
            frame.addView(child, LinearLayout.LayoutParams(-1, -2))
            return frame
        }

        fun fileBox(): LinearLayout {
            val box: LinearLayout = LinearLayout(this@MainActivity)
            box.setOrientation(LinearLayout.VERTICAL)
            val inset: Int = Math.max(gap, pad / 2)
            box.setPadding(inset, inset, inset, inset)
            box.setBackgroundDrawable(shape(surface, 0, elementRadius()))
            return box
        }

        fun bubbleBox(own: Boolean): LinearLayout {
            val box: BubbleLayout = ru.e6atb.chat.MainActivity.BubbleLayout(
                this@MainActivity,
                Math.max(dp(180), getResources().getDisplayMetrics().widthPixels * 78 / 100)
            )
            box.setOrientation(LinearLayout.VERTICAL)
            val inset: Int = Math.max(gap, pad / 2)
            box.setPadding(inset, inset, inset, inset)
            box.setBackgroundDrawable(messageBubble(own))
            return box
        }

        fun fileLabel(value: String?): TextView {
            val label: TextView = TextView(this@MainActivity)
            label.setTextColor(textColor)
            label.setTextSize(16)
            label.setText(ru.e6atb.chat.MainActivity.Companion.safeDisplayText(value))
            return label
        }

        fun messageTextLabel(value: String?, own: Boolean = false): TextView {
            val label: TextView = fileLabel("")
            // The outgoing bubble is painted with `primary`, so using the same
            // colour for a URL makes it disappear into its background.
            label.setTextColor(if (own) onPrimary else textColor)
            label.setText(renderMarkdown(value, if (own) blend(onPrimary, primary, 0.12f) else primary))
            label.setMovementMethod(LinkMovementMethod.getInstance())
            label.setHighlightColor(Color.TRANSPARENT)
            label.setLinksClickable(true)
            return label
        }

        fun addDownloadButton(box: LinearLayout, row: MessageRow) {
            val title: String? = if (isCompleteDownloadedFile(
                    downloadedFileFor(row.file),
                    row.file
                )
            ) getString(R.string.action_open) else getString(R.string.action_download)
            val download: Button = button(title, object : View.OnClickListener {
                override fun onClick(v: View?) {
                    downloadFile(row.file, v)
                }
            })
            val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(0, gap, 0, 0)
            box.addView(download, lp)
        }

        fun addMessageButtons(box: LinearLayout, message: MST5.Message?) {
            if (message == null || message.buttons == null || message.buttons.isEmpty()) return
            for (row in MessageButtonRows.group(message.buttons)) {
                if (row == null || row.isEmpty()) continue
                val actions: LinearLayout = LinearLayout(this@MainActivity)
                actions.setOrientation(LinearLayout.HORIZONTAL)
                for (item in row) {
                    if (item == null) continue
                    val action: Button = messageActionButton(item.text, object : View.OnClickListener {
                        override fun onClick(v: View?) {
                            handleMessageButton(message, item, if (v is Button) v as Button? else null)
                        }
                    })
                    val actionLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    if (actions.getChildCount() > 0) actionLp.setMargins(gap / 2, 0, 0, 0)
                    actions.addView(action, actionLp)
                }
                val rowLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
                rowLp.setMargins(0, gap / 2, 0, 0)
                box.addView(actions, rowLp)
            }
        }

        fun addMessageReactions(box: LinearLayout, message: MST5.Message?) {
            if (message == null || !messageHasReactions(message)) return
            val reactions: MessageMetaLayout = MessageMetaLayout(this@MainActivity, gap / 2, gap / 2)
            addReactionChips(reactions, message)
            val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(0, gap / 2, 0, 0)
            box.addView(reactions, lp)
        }

        fun addMessageMeta(box: LinearLayout, message: MST5.Message?) {
            addTransferProgress(box, message)
            val meta: MessageMetaLayout = MessageMetaLayout(this@MainActivity, gap / 2, gap / 2)
            if (message != null) addReactionChips(meta, message)
            meta.setFooter(messageFooter(message))
            val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(0, gap / 3, 0, 0)
            box.addView(meta, lp)
        }

        fun addTransferProgress(box: LinearLayout, message: MST5.Message?) {
            if (message == null || message.media == null || message.media.isEmpty()
                || "sent".equals(message.deliveryState) || "sent-own".equals(message.deliveryState)
                || OutboxStore.FAILED.equals(message.deliveryState)
            ) return
            val progressBox: LinearLayout = LinearLayout(this@MainActivity)
            progressBox.setOrientation(LinearLayout.VERTICAL)
            val progressText: TextView = label(getString(R.string.file_progress_sending, message.deliveryProgress))
            progressText.setTextColor(muted)
            progressText.setTextSize(12)
            progressBox.addView(progressText, LinearLayout.LayoutParams(-1, -2))
            val progress: ProgressBar = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal)
            progress.setMax(100)
            progress.setProgress(message.deliveryProgress)
            val barLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, dp(5))
            barLp.setMargins(0, gap / 3, 0, 0)
            progressBox.addView(progress, barLp)
            val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(0, gap / 2, 0, 0)
            box.addView(progressBox, lp)
        }

        fun messageHasReactions(message: MST5.Message?): Boolean {
            if (message == null) return false
            val pendingPaid = pendingPaidReactionDelta(message.id)
            val serverPaid: Long = if (message.paidReaction == null) 0 else message.paidReaction.amount
            val displayedPaid = safeAdd(serverPaid, pendingPaid)
            return displayedPaid > 0 || (message.reactions != null && !message.reactions.isEmpty())
        }

        fun addReactionChips(reactions: MessageMetaLayout, message: MST5.Message) {
            val pendingPaid = pendingPaidReactionDelta(message.id)
            val serverPaid: Long = if (message.paidReaction == null) 0 else message.paidReaction.amount
            val displayedPaid = safeAdd(serverPaid, pendingPaid)
            if (displayedPaid > 0) {
                val mine = pendingPaid > 0
                        || (message.paidReaction != null && message.paidReaction.mineAmount > 0)
                val paid: Button = reactionChip(java.lang.String.valueOf(displayedPaid), mine)
                setDastarsButtonIcon(paid, if (mine) onPrimary else textColor, dp(19))
                paid.setOnClickListener(object : View.OnClickListener {
                    override fun onClick(v: View?) {
                        handlePaidReaction(message, paid)
                    }
                })
                reactions.addView(paid, reactionChipLayout())
            }
            if (message.reactions != null) {
                for (item in message.reactions) {
                    if (item == null) continue
                    val chip: Button = reactionChip(item.emoji + " " + item.count, item.mine)
                    chip.setOnClickListener(object : View.OnClickListener {
                        override fun onClick(v: View?) {
                            animateReactionView(chip)
                            sendFreeReaction(message, if (item.mine) "" else item.emoji)
                        }
                    })
                    reactions.addView(chip, reactionChipLayout())
                }
            }
        }

        fun reactionChipLayout(): ViewGroup.LayoutParams {
            return LinearLayout.LayoutParams(-2, dp(38))
        }

        fun reactionChip(text: String?, selected: Boolean): Button {
            val chip: Button = Button(this@MainActivity)
            chip.setText(text)
            chip.setTextSize(14)
            chip.setTextColor(if (selected) onPrimary else textColor)
            chip.setMinWidth(0)
            chip.setMinimumWidth(0)
            chip.setMinHeight(0)
            chip.setMinimumHeight(0)
            chip.setPadding(gap, 0, gap, 0)
            val normal = if (selected) primary else surfaceHi
            val pressed = if (selected) blend(primary, Color.WHITE, 0.18f) else blend(surfaceHi, primary, 0.18f)
            chip.setBackgroundDrawable(pressable(normal, pressed, 0, dp(19)))
            return chip
        }

        fun messageFooter(message: MST5.Message?): View {
            val footer: LinearLayout = LinearLayout(this@MainActivity)
            footer.setOrientation(LinearLayout.HORIZONTAL)
            footer.setGravity(Gravity.RIGHT or Gravity.BOTTOM)
            val delivered =
                message == null || "sent".equals(message.deliveryState) || "sent-own".equals(message.deliveryState)
            if (delivered) {
                val time: TextView = TextView(this@MainActivity)
                time.setTextColor(muted)
                time.setTextSize(12)
                time.setText(formatMessageTime(if (message == null) 0 else message.date))
                footer.addView(time, LinearLayout.LayoutParams(-2, -2))
            }
            if (delivered && message != null && message.editedAt > 0) {
                val edited: TextView = TextView(this@MainActivity)
                edited.setTextColor(muted)
                edited.setTextSize(12)
                edited.setText(getString(R.string.message_edited))
                val editedLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-2, -2)
                editedLp.setMargins(gap / 2, 0, 0, 0)
                footer.addView(edited, editedLp)
            }
            if (message != null && message.from != null && (message.from.login.equals(myLogin) || !"sent".equals(message.deliveryState))) {
                val statusIcon: ImageView = ImageView(this@MainActivity)
                if (OutboxStore.FAILED.equals(message.deliveryState)) {
                    statusIcon.setImageResource(R.drawable.ic_status_failed)
                    statusIcon.setContentDescription(getString(R.string.failed_status))
                } else if (!"sent".equals(message.deliveryState) && !"sent-own".equals(message.deliveryState)) {
                    statusIcon.setImageResource(R.drawable.ic_status_pending)
                    statusIcon.setContentDescription(getString(R.string.pending_status))
                } else {
                    statusIcon.setImageResource(if (message.readAt > 0) R.drawable.ic_status_read else R.drawable.ic_status_sent)
                    statusIcon.setContentDescription(
                        if (message.readAt > 0) getString(R.string.read_status) else getString(
                            R.string.sent_status
                        )
                    )
                }
                val iconLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
                iconLp.setMargins(gap / 2, 0, 0, 0)
                footer.addView(statusIcon, iconLp)
            }
            return footer
        }
    }

    private fun chatHeader(): LinearLayout {
        val r: LinearLayout = LinearLayout(this)
        r.setOrientation(LinearLayout.HORIZONTAL)
        r.setGravity(Gravity.CENTER_VERTICAL)
        r.setPadding(pad, dp(10), pad, dp(10))
        r.setBackgroundDrawable(shape(accentSurface, 0, 0))

        val back: ImageButton =
            headerIconButton(R.drawable.ic_back, getString(R.string.action_back), object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showChats()
                }
            })
        val backLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight)
        backLp.setMargins(0, 0, gap, 0)
        r.addView(back, backLp)
        val avatar: TextView = chatAvatar(chatPeerTitle(currentHeaderUser()), dp(38))
        avatar.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                showCurrentPeerProfile()
            }
        })
        val avatarLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
        avatarLp.setMargins(0, 0, gap, 0)
        r.addView(avatar, avatarLp)

        val nameLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        currentPeerNameView = userNameRow(currentHeaderUser(), 16, true)
        currentPeerNameView.setClickable(true)
        currentPeerNameView.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                showCurrentPeerProfile()
            }
        })
        r.addView(currentPeerNameView, nameLp)

        val callLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight)
        callLp.setMargins(gap, 0, 0, 0)
        r.addView(callButton, callLp)

        val menu: ImageButton =
            headerIconButton(R.drawable.ic_more_vertical, getString(R.string.chat_actions), object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showChatActionsMenu()
                }
            })
        val menuLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight)
        menuLp.setMargins(gap, 0, 0, 0)
        r.addView(menu, menuLp)
        return r
    }

    private fun currentHeaderUser(): MST5.User {
        if (currentPeerUser != null && currentPeer != null && currentPeer!!.equals(
                resolvedPeerName(
                    currentPeerUser,
                    currentPeer
                )
            )
        ) {
            return currentPeerUser!!
        }
        return User("", "", if (currentPeer == null) "" else currentPeer, "", false, false, 0)
    }

    private fun userNameRow(user: MST5.User?, textSizeSp: Int): LinearLayout {
        return userNameRow(user, textSizeSp, false)
    }

    private fun userNameRow(user: MST5.User?, textSizeSp: Int, chatTitle: Boolean): LinearLayout {
        val row: LinearLayout = LinearLayout(this)
        row.setOrientation(LinearLayout.VERTICAL)
        row.setGravity(Gravity.CENTER_VERTICAL)
        populateUserNameRow(row, user, textSizeSp, chatTitle)
        return row
    }

    private fun refreshCurrentPeerNameView() {
        if (currentPeerNameView == null) return
        currentPeerNameView.removeAllViews()
        populateUserNameRow(currentPeerNameView, currentHeaderUser(), 18, true)
    }

    private fun populateUserNameRow(row: LinearLayout, user: MST5.User?, textSizeSp: Int, chatTitle: Boolean) {
        val nameLine: LinearLayout = LinearLayout(this)
        nameLine.setOrientation(LinearLayout.HORIZONTAL)
        nameLine.setGravity(Gravity.CENTER_VERTICAL)
        val name: TextView = TextView(this)
        name.setTextColor(if (chatTitle) textColor else blend(primary, Color.WHITE, 0.18f))
        name.setTextSize(textSizeSp)
        name.setSingleLine(true)
        val titleText: String? = if (chatTitle && currentPeerIsSelfChat())
            getString(R.string.chat_favorites_title)
        else
            (if (chatTitle) chatPeerTitle(user) else displayUser(user))
        name.setText(ru.e6atb.chat.MainActivity.Companion.safeDisplayText(titleText))
        nameLine.addView(name, LinearLayout.LayoutParams(-2, -2))
        if (user != null && user.verified) {
            val verified: ImageView = ImageView(this)
            verified.setImageDrawable(verifiedDrawable(dp(18)))
            verified.setContentDescription(getString(R.string.verified))
            val iconLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
            iconLp.setMargins(gap / 2, 0, 0, 0)
            nameLine.addView(verified, iconLp)
        }
        row.addView(nameLine, LinearLayout.LayoutParams(-2, -2))
        val subtitle = roomMemberCountLabel(user)
        if (subtitle.length > 0) {
            val members: TextView = TextView(this)
            members.setTextColor(if (chatTitle) blend(textColor, accentSurface, 0.35f) else muted)
            members.setTextSize(12)
            members.setSingleLine(true)
            members.setText(subtitle)
            row.addView(members, LinearLayout.LayoutParams(-2, -2))
        }
    }

    private fun roomMemberCountLabel(user: MST5.User?): String {
        if (user == null || user.roomKind == null || user.roomKind.length == 0) return ""
        return if ("channel".equals(user.roomKind))
            getString(R.string.channel_subscribers_count, user.memberCount)
        else
            getString(R.string.group_members_count, user.memberCount)
    }

    private fun verifiedDrawable(size: Int): Drawable {
        val bitmap: Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas: Canvas = Canvas(bitmap)
        val scale = size / 24f
        val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.setColor(primary)
        paint.setStyle(Paint.Style.STROKE)
        paint.setStrokeWidth(2f * scale)
        paint.setStrokeCap(Paint.Cap.ROUND)
        paint.setStrokeJoin(Paint.Join.ROUND)
        val badge: Path = Path()
        badge.moveTo(12f * scale, 2f * scale)
        badge.lineTo(14.74f * scale, 4.05f * scale)
        badge.lineTo(18.14f * scale, 3.69f * scale)
        badge.lineTo(19.1f * scale, 6.97f * scale)
        badge.lineTo(22f * scale, 8.79f * scale)
        badge.lineTo(20.66f * scale, 11.94f * scale)
        badge.lineTo(22f * scale, 15.09f * scale)
        badge.lineTo(19.1f * scale, 16.91f * scale)
        badge.lineTo(18.14f * scale, 20.19f * scale)
        badge.lineTo(14.74f * scale, 19.83f * scale)
        badge.lineTo(12f * scale, 21.88f * scale)
        badge.lineTo(9.26f * scale, 19.83f * scale)
        badge.lineTo(5.86f * scale, 20.19f * scale)
        badge.lineTo(4.9f * scale, 16.91f * scale)
        badge.lineTo(2f * scale, 15.09f * scale)
        badge.lineTo(3.34f * scale, 11.94f * scale)
        badge.lineTo(2f * scale, 8.79f * scale)
        badge.lineTo(4.9f * scale, 6.97f * scale)
        badge.lineTo(5.86f * scale, 3.69f * scale)
        badge.lineTo(9.26f * scale, 4.05f * scale)
        badge.close()
        canvas.drawPath(badge, paint)
        val check: Path = Path()
        check.moveTo(7f * scale, 12.2f * scale)
        check.lineTo(10.3f * scale, 15.5f * scale)
        check.lineTo(17f * scale, 8.8f * scale)
        canvas.drawPath(check, paint)
        return BitmapDrawable(getResources(), bitmap)
    }

    private fun showChatActionsMenu() {
        val actions: ArrayList<String> = ArrayList<String>()
        if (currentPeer != null && currentPeer.length > 0) {
            actions.add(getString(R.string.action_profile))
            if (currentPeerIsRoom()) {
                actions.add(if (currentPeerE2EEnabled()) getString(R.string.action_disable_chat_e2e) else getString(R.string.action_enable_chat_e2e))
                if (!currentPeerIsChannel() || currentPeerCanManageRoom()) {
                    actions.add(getString(R.string.action_members))
                }
                if (currentPeerCanManageRoom()) actions.add(getString(R.string.action_invite))
                if (currentPeerCanManageRoom() && currentPeerIsChannel()) {
                    actions.add(getString(R.string.channel_settings))
                } else if (currentPeerCanManageRoom()) {
                    actions.add(getString(R.string.action_edit_title))
                    actions.add(getString(R.string.action_remove_member))
                }
            } else {
                actions.add(getString(R.string.action_verify_e2e))
            }
            actions.add(getString(R.string.action_copy_id))
            if (currentPeerIsRoom() && !currentPeerCanManageRoom()) {
                actions.add(getString(R.string.action_leave_chat))
            } else {
                actions.add(getString(R.string.action_delete_chat))
            }
            if (!currentPeerIsRoom()) {
                actions.add(if (currentPeerBannedByMe) getString(R.string.action_unban_user) else getString(R.string.action_ban_user))
            }
        }
        if (actions.isEmpty()) {
            return
        }
        showActionDialog(actions.toArray(arrayOfNulls<String>(actions.size)), object : ChoiceHandler {
            override fun onChoice(which: Int) {
                val action: String = actions.get(which)
                if (action.equals(getString(R.string.action_profile))) {
                    showCurrentPeerProfile()
                } else if (action.equals(getString(R.string.action_enable_chat_e2e)) || action.equals(getString(R.string.action_disable_chat_e2e))) {
                    toggleCurrentChatE2E(action.equals(getString(R.string.action_enable_chat_e2e)))
                } else if (action.equals(getString(R.string.action_members))) {
                    showCurrentRoomMembersDialog()
                } else if (action.equals(getString(R.string.action_invite))) {
                    showInviteMemberDialog()
                } else if (action.equals(getString(R.string.channel_settings))) {
                    showChannelSettings()
                } else if (action.equals(getString(R.string.action_edit_title))) {
                    showEditRoomTitleDialog()
                } else if (action.equals(getString(R.string.action_edit_username))) {
                    showEditChannelUsernameDialog()
                } else if (action.equals(getString(R.string.action_remove_member))) {
                    showRemoveMemberDialog()
                } else if (action.equals(getString(R.string.action_verify_e2e))) {
                    showE2EFingerprint()
                } else if (action.equals(getString(R.string.action_copy_id))) {
                    copyCurrentPeerID()
                } else if (action.equals(getString(R.string.action_delete_chat))) {
                    confirmDeleteCurrentChat()
                } else if (action.equals(getString(R.string.action_leave_chat))) {
                    confirmLeaveCurrentRoom()
                } else if (action.equals(getString(R.string.action_ban_user)) || action.equals(getString(R.string.action_unban_user))) {
                    if (currentPeerBannedByMe) confirmUnbanCurrentPeer()
                    else confirmBanCurrentPeer()
                }
            }
        })
    }

    private fun toggleCurrentChatE2E(enabled: Boolean) {
        val client = ta
        val room = currentPeerUser
        val chatId = currentRoomChatId()
        if (client == null || room == null || chatId.isEmpty()) return
        if (!enabled) {
            SessionStore.setChatE2EEnabled(this, SessionStore.server(this, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER), myLogin, chatId, false)
            status.setText(getString(R.string.status_chat_e2e_disabled))
            return
        }
        run("chat_e2e", object : Task {
            @Throws(Exception::class)
            override fun run() {
                client.registerChatE2E(chatId)
                SessionStore.setChatE2EEnabled(this@MainActivity, SessionStore.server(this@MainActivity, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER), myLogin, chatId, true)
                ui(object : Runnable { override fun run() { status.setText(getString(R.string.status_chat_e2e_enabled)) } })
            }
        })
    }

    private fun showCurrentPeerProfile() {
        val user: MST5.User? = currentHeaderUser()
        if (user == null || user.id.length == 0) {
            status.setText(getString(R.string.status_id_not_loaded))
            return
        }
        val box: LinearLayout = LinearLayout(this)
        box.setOrientation(LinearLayout.VERTICAL)
        box.setPadding(0, gap, 0, 0)
        if (user.avatar != null && user.avatar.id.length > 0) {
            val avatarView: ImageView = ImageView(this)
            avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP)
            avatarView.setBackgroundDrawable(shape(surface, 0, dp(48)))
            box.addView(spaced(avatarView), LinearLayout.LayoutParams(dp(128), dp(128)))
            val avatar: MST5.FileInfo = user.avatar
            val client: MST5? = ta
            if (client != null) io.execute(object : Runnable {
                override fun run() {
                    try {
                        val image: Bitmap? = rs.ove.crypt.proto.Mst5ImageDecoder.decode(
                            client.downloadFileBytes(avatar.id, 1048576),
                            256
                        )
                        ui(object : Runnable {
                            override fun run() {
                                if (image != null) avatarView.setImageBitmap(image)
                            }
                        })
                    } catch (ignored: Exception) {
                    }
                }
            })
        }
        box.addView(spaced(userProfileRow(getString(R.string.profile_id), user.id, "user id")))
        if (user.roomKind != null && user.roomKind.length > 0) {
            box.addView(spaced(userProfileRow(getString(R.string.profile_type), roomKindLabel(user), null)))
            box.addView(
                spaced(
                    userProfileRow(
                        if ("channel".equals(user.roomKind)) getString(R.string.profile_subscribers) else getString(R.string.profile_participants),
                        java.lang.String.valueOf(user.memberCount),
                        null
                    )
                )
            )
            if (!"channel".equals(user.roomKind) || currentPeerCanManageRoom()) {
                box.addView(spaced(row(primaryButton(getString(R.string.action_invite), object : View.OnClickListener {
                    override fun onClick(v: View?) {
                        showInviteMemberDialog()
                    }
                }))))
            }
            if (currentPeerCanManageRoom()) {
                if ("channel".equals(user.roomKind)) {
                    box.addView(
                        spaced(
                            row(
                                primaryButton(
                                    getString(R.string.channel_settings),
                                    object : View.OnClickListener {
                                        override fun onClick(v: View?) {
                                            showChannelSettings()
                                        }
                                    })
                            )
                        )
                    )
                } else {
                    box.addView(
                        spaced(
                            row(
                                button(getString(R.string.action_edit_title), object : View.OnClickListener {
                                    override fun onClick(v: View?) {
                                        showEditRoomTitleDialog()
                                    }
                                }),
                                button(getString(R.string.action_remove_member), object : View.OnClickListener {
                                    override fun onClick(v: View?) {
                                        showRemoveMemberDialog()
                                    }
                                })
                            )
                        )
                    )
                }
            }
        }
        if (user.login != null && user.login.length > 0) {
            box.addView(spaced(userProfileRow(getString(R.string.profile_username), "@" + user.login, "username")))
        }
        if (user.nick != null && user.nick.length > 0) {
            box.addView(spaced(userProfileRow(getString(R.string.profile_name), user.nick, null)))
        }
        box.addView(
            spaced(
                userProfileRow(
                    getString(R.string.profile_description),
                    if (user.description == null || user.description.length == 0)
                        getString(R.string.profile_description_empty)
                    else
                        user.description,
                    null
                )
            )
        )
        if (canEditProfileDescription(user)) {
            box.addView(spaced(row(button(getString(R.string.action_edit_description), object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showEditProfileDescriptionDialog(currentHeaderUser())
                }
            }))))
        }
        if (user.verified) {
            box.addView(
                spaced(
                    userProfileRow(
                        getString(R.string.profile_verification),
                        getString(R.string.profile_verified),
                        null
                    )
                )
            )
        }
        if (user.roomKind == null || user.roomKind.length == 0) {
            if (!isOwnUser(user) && !ru.e6atb.chat.MainActivity.Companion.isNegativePublicID(user.id)) {
                box.addView(
                    spaced(
                        row(
                            primaryButton(
                                getString(R.string.action_add_contact),
                                object : View.OnClickListener {
                                    override fun onClick(v: View?) {
                                        addProfileUserToContacts(v)
                                    }
                                })
                        )
                    )
                )
            }
        }
        showContentDialog(profileTitle(user), box, getString(R.string.action_close), null, null)
    }

    /** Opens an identity card for a message author without changing the current chat.  */
    private fun showUserProfile(user: MST5.User?) {
        if (user == null || user.id == null || user.id.length == 0) {
            status.setText(getString(R.string.status_id_not_loaded))
            return
        }
        if (currentPeerUser != null && user.id.equals(currentPeerUser!!.id)) {
            showCurrentPeerProfile()
            return
        }
        val box: LinearLayout = LinearLayout(this)
        box.setOrientation(LinearLayout.VERTICAL)
        box.setPadding(0, gap, 0, 0)
        box.addView(spaced(userProfileRow(getString(R.string.profile_id), user.id, "user id")))
        if (user.login != null && user.login.length > 0) {
            box.addView(spaced(userProfileRow(getString(R.string.profile_username), "@" + user.login, "username")))
        }
        if (user.nick != null && user.nick.length > 0) {
            box.addView(spaced(userProfileRow(getString(R.string.profile_name), user.nick, null)))
        }
        box.addView(
            spaced(
                userProfileRow(
                    getString(R.string.profile_description),
                    if (user.description == null || user.description.length == 0)
                        getString(R.string.profile_description_empty)
                    else
                        user.description,
                    null
                )
            )
        )
        box.addView(
            spaced(
                userProfileRow(
                    getString(R.string.profile_type),
                    if (user.bot) getString(R.string.profile_bot) else getString(R.string.profile_user), null
                )
            )
        )
        if (user.verified) {
            box.addView(
                spaced(
                    userProfileRow(
                        getString(R.string.profile_verification),
                        getString(R.string.profile_verified),
                        null
                    )
                )
            )
        }
        showContentDialog(profileTitle(user), box, getString(R.string.action_close), null, null)
    }

    private fun showChannelSettings() {
        if (!currentPeerIsChannel() || !currentPeerCanManageRoom()) return
        page = Page.CHANNEL_SETTINGS
        if (bottomNav != null) bottomNav.setVisibility(View.GONE)
        content.removeAllViews()
        val scroll: ScrollView = pageScrollView()
        val box: LinearLayout = LinearLayout(this)
        box.setOrientation(LinearLayout.VERTICAL)
        box.setPadding(0, 0, 0, gap)
        val back: ImageButton =
            headerIconButton(R.drawable.ic_back, getString(R.string.action_back), object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showChat()
                    loadHistory()
                }
            })
        box.addView(spaced(row(back)))
        box.addView(spaced(title(getString(R.string.channel_settings))))
        box.addView(settingsSection(getString(R.string.channel_settings_general)))
        box.addView(
            settingsRow(
            getString(R.string.action_edit_title),
            if (currentPeerUser == null) "" else currentPeerUser!!.nick,
            object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showEditRoomTitleDialog()
                }
            }
        ))
        box.addView(
            settingsRow(
            getString(R.string.action_edit_username),
            if (currentPeerUser == null || currentPeerUser!!.login.length == 0) getString(R.string.channel_username_empty) else "@" + currentPeerUser!!.login,
            object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showEditChannelUsernameDialog()
                }
            }
        ))
        box.addView(
            settingsRow(
            getString(R.string.action_edit_description),
            if (currentPeerUser == null || currentPeerUser!!.description.length == 0)
                getString(R.string.profile_description_empty)
            else
                currentPeerUser!!.description,
            object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showEditProfileDescriptionDialog(currentHeaderUser())
                }
            }
        ))
        box.addView(settingsSection(getString(R.string.channel_settings_discussion)))
        val commentsEnabled = currentPeerUser != null && currentPeerUser!!.commentsEnabled
        box.addView(
            settingsToggleRow(
            getString(R.string.channel_comments),
            if (commentsEnabled) getString(R.string.channel_comments_enabled) else getString(R.string.channel_comments_disabled),
            commentsEnabled,
            object : View.OnClickListener {
                override fun onClick(v: View?) {
                    if (commentsEnabled) confirmDisableChannelComments()
                    else updateChannelComments(true)
                }
            }
        ))
        box.addView(settingsSection(getString(R.string.channel_settings_subscribers)))
        box.addView(
            settingsRow(
                getString(R.string.action_members),
                java.lang.String.valueOf(currentPeerUser!!.memberCount),
                object : View.OnClickListener {
                    override fun onClick(v: View?) {
                        showCurrentRoomMembersDialog()
                    }
                })
        )
        box.addView(settingsRow(getString(R.string.action_invite), "", object : View.OnClickListener {
            override fun onClick(v: View?) {
                showInviteMemberDialog()
            }
        }))
        box.addView(settingsRow(getString(R.string.action_remove_member), "", object : View.OnClickListener {
            override fun onClick(v: View?) {
                showRemoveMemberDialog()
            }
        }))
        box.addView(settingsSection(getString(R.string.channel_settings_danger)))
        box.addView(spaced(row(button(getString(R.string.confirm_delete_chat), object : View.OnClickListener {
            override fun onClick(v: View?) {
                confirmDeleteCurrentChat()
            }
        }))))
        scroll.addView(box, LinearLayout.LayoutParams(-1, -2))
        content.addView(scroll, fill())
    }

    private fun confirmDisableChannelComments() {
        showConfirmDialog(
            getString(R.string.channel_comments_disable_title),
            getString(R.string.channel_comments_disable_message),
            getString(R.string.channel_comments_disable_action),
            object : Runnable {
                override fun run() {
                    updateChannelComments(false)
                }
            }
        )
    }

    private fun updateChannelComments(enabled: Boolean) {
        val c: MST5? = ta
        val channel = currentPeer
        if (c == null || channel == null || channel.length == 0) return
        status.setText(getString(R.string.status_saving_room))
        run("channel_comments", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val chat: MST5.Chat? = c.setChannelComments(channel, enabled)
                if (!enabled) {
                    ChatCache.deleteCommentThreads(
                        this@MainActivity,
                        SessionStore.server(this@MainActivity, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
                        myLogin,
                        channel
                    )
                    OutboxStore.removeChannelComments(
                        this@MainActivity,
                        SessionStore.server(this@MainActivity, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
                        OutboxDispatcher.accountKey(this@MainActivity),
                        channel
                    )
                }
                ui(object : Runnable {
                    override fun run() {
                        updateCurrentRoom(chat)
                        status.setText(if (enabled) getString(R.string.channel_comments_enabled) else getString(R.string.channel_comments_deleted))
                    }
                })
            }
        })
    }

    private fun showCurrentRoomMembersDialog() {
        if (!currentPeerIsRoom()) return
        if (currentPeerIsChannel() && !currentPeerCanManageRoom()) return
        val user: MST5.User = currentHeaderUser()
        val box: LinearLayout = LinearLayout(this)
        box.setOrientation(LinearLayout.VERTICAL)
        box.setPadding(0, gap, 0, 0)
        if (user.memberUsers != null && !user.memberUsers.isEmpty()) {
            for (member in user.memberUsers) {
                box.addView(spaced(userProfileRow(displayUser(member), member.id, "user id")))
            }
        } else {
            val empty: TextView = label(getString(R.string.profile_no_members))
            empty.setTextColor(muted)
            box.addView(spaced(empty))
        }
        showContentDialog(getString(R.string.action_members), box, getString(R.string.action_close), null, null)
    }

    private fun addProfileUserToContacts(actionButton: View? = null) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        val user: MST5.User = currentHeaderUser()
        val address: String? = resolvedPeerName(user, currentPeer)
        if (address == null || address.length == 0) return
        status.setText(getString(R.string.status_saving_contact))
        runButtonTask("profile_add_contact", actionButton, true, object : Task {
            @Throws(Exception::class)
            override fun run() {
                c.addContact(address)
                ui(object : Runnable {
                    override fun run() {
                        status.setText(getString(R.string.status_contact_saved))
                    }
                })
            }
        })
    }

    private fun profileTitle(user: MST5.User?): String {
        if (user != null && "channel".equals(user.roomKind)) return getString(R.string.profile_channel)
        if (user != null && "group".equals(user.roomKind)) return getString(R.string.profile_group)
        return if (user != null && user.bot) getString(R.string.profile_bot) else getString(R.string.profile_user)
    }

    private fun canEditProfileDescription(user: MST5.User?): Boolean {
        if (user == null) return false
        if (isOwnUser(user)) return true
        if (user.roomKind != null && user.roomKind.length > 0) return currentPeerCanManageRoom()
        return user.bot && "0000000000000001".equals(myID)
    }

    private fun showEditProfileDescriptionDialog(user: MST5.User?) {
        if (!canEditProfileDescription(user)) return
        val input: EditText = input(getString(R.string.settings_description_hint), false)
        input.setSingleLine(false)
        input.setMinLines(3)
        input.setMaxLines(6)
        input.setFilters(
            arrayOf<android.text.InputFilter>(
                LengthFilter(200)
            )
        )
        input.setText(user?.description.orEmpty())
        showContentDialog(
            getString(R.string.action_edit_description),
            input,
            getString(R.string.action_save),
            object : Runnable {
                override fun run() {
                    updateProfileDescription(user, input.getText().toString().trim())
                }
            },
            getString(R.string.action_cancel)
        )
    }

    private fun updateProfileDescription(target: MST5.User?, description: String?) {
        val c: MST5? = ta
        if (c == null || target == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        val own = isOwnUser(target)
        val address: String? = if (own) "" else target.id
        status.setText(getString(R.string.status_saving_description))
        run("profile_description", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val updated: MST5.User? = c.setProfileDescription(address.orEmpty(), description.orEmpty())
                ui(object : Runnable {
                    override fun run() {
                        if (own) applyOwnUser(updated)
                        else if (currentPeerUser != null && target.id.equals(currentPeerUser!!.id)) {
                            currentPeerUser = updated
                            refreshCurrentPeerNameView()
                        }
                        status.setText(getString(R.string.status_description_saved))
                        if (page === Page.CHANNEL_SETTINGS) showChannelSettings()
                    }
                })
            }
        })
    }

    private fun roomKindLabel(user: MST5.User?): String {
        if (user != null && "channel".equals(user.roomKind)) return getString(R.string.profile_channel)
        return getString(R.string.profile_group)
    }

    private fun showInviteMemberDialog() {
        if (!currentPeerIsRoom()) return
        val input: EditText = input(getString(R.string.hint_username_or_id), false)
        showContentDialog(
            getString(R.string.action_invite),
            input,
            getString(R.string.action_invite),
            object : Runnable {
                override fun run() {
                    inviteMember(input.getText().toString().trim())
                }
            },
            getString(R.string.action_cancel)
        )
    }

    private fun inviteMember(member: String?) {
        val c: MST5? = ta
        val room = currentPeer
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        if (room == null || room.length == 0 || member == null || member.length == 0) return
        status.setText(getString(R.string.status_inviting_member))
        run("invite_member", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val chat: MST5.Chat = c.addChatMember(room, member)
                ui(object : Runnable {
                    override fun run() {
                        currentPeerUser = chat.peer
                        currentPeer = resolvedPeerName(chat.peer, room)
                        status.setText(getString(R.string.status_member_invited))
                        refreshCurrentPeerNameView()
                        refreshChatInput()
                        loadHistory()
                    }
                })
            }
        })
    }

    private fun showEditRoomTitleDialog() {
        if (!currentPeerCanManageRoom()) return
        val input: EditText = input(getString(R.string.hint_room_title), false)
        if (currentPeerUser != null && currentPeerUser!!.nick != null) input.setText(currentPeerUser!!.nick)
        showContentDialog(
            getString(R.string.action_edit_title),
            input,
            getString(R.string.action_save),
            object : Runnable {
                override fun run() {
                    updateRoomTitle(input.getText().toString().trim())
                }
            },
            getString(R.string.action_cancel)
        )
    }

    private fun updateRoomTitle(titleValue: String?) {
        val c: MST5? = ta
        val room = currentPeer
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        if (room == null || room.length == 0 || titleValue == null || titleValue.length == 0) return
        status.setText(getString(R.string.status_saving_room))
        run("room_title", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val chat: MST5.Chat? = c.setChatTitle(room, titleValue)
                ui(object : Runnable {
                    override fun run() {
                        updateCurrentRoom(chat)
                        status.setText(getString(R.string.status_room_saved))
                    }
                })
            }
        })
    }

    private fun showEditChannelUsernameDialog() {
        if (!currentPeerCanManageRoom() || !currentPeerIsChannel()) return
        val input: EditText = input(getString(R.string.hint_channel_username), false)
        if (currentPeerUser != null && currentPeerUser!!.login != null) input.setText(currentPeerUser!!.login)
        showContentDialog(
            getString(R.string.action_edit_username),
            input,
            getString(R.string.action_save),
            object : Runnable {
                override fun run() {
                    updateChannelUsername(input.getText().toString().trim())
                }
            },
            getString(R.string.action_cancel)
        )
    }

    private fun updateChannelUsername(usernameValue: String?) {
        val c: MST5? = ta
        val room = currentPeer
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        if (room == null || room.length == 0) return
        if (currentPeerUser != null && (currentPeerUser!!.login == null || currentPeerUser!!.login.length == 0)
            && usernameValue != null && usernameValue.length > 0
        ) {
            showUsernameReservationPaymentSheet(
                usernameValue,
                getString(
                    R.string.username_reservation_payment_details_channel,
                    if (currentPeerUser!!.nick == null) room else currentPeerUser!!.nick
                ),
                object : Runnable {
                    override fun run() {
                        updateChannelUsernameConfirmed(room, usernameValue)
                    }
                }
            )
            return
        }
        updateChannelUsernameConfirmed(room, usernameValue)
    }

    private fun updateChannelUsernameConfirmed(room: String?, usernameValue: String?) {
        val c: MST5? = ta
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        status.setText(getString(R.string.status_saving_room))
        run("channel_username", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val chat: MST5.Chat? = c.setChannelUsername(room.orEmpty(), usernameValue.orEmpty())
                ui(object : Runnable {
                    override fun run() {
                        updateCurrentRoom(chat)
                        status.setText(getString(R.string.status_room_saved))
                    }
                })
            }
        })
    }

    private fun showRemoveMemberDialog() {
        if (!currentPeerCanManageRoom()) return
        val input: EditText = input(getString(R.string.hint_username_or_id), false)
        showContentDialog(
            getString(R.string.action_remove_member),
            input,
            getString(R.string.action_remove_member),
            object : Runnable {
                override fun run() {
                    removeMember(input.getText().toString().trim())
                }
            },
            getString(R.string.action_cancel)
        )
    }

    private fun removeMember(member: String?) {
        val c: MST5? = ta
        val room = currentPeer
        if (c == null) {
            status.setText(getString(R.string.status_sign_in_first))
            return
        }
        if (room == null || room.length == 0 || member == null || member.length == 0) return
        status.setText(getString(R.string.status_removing_member))
        run("remove_member", object : Task {
            @Throws(Exception::class)
            override fun run() {
                val chat: MST5.Chat? = c.removeChatMember(room, member)
                ui(object : Runnable {
                    override fun run() {
                        updateCurrentRoom(chat)
                        status.setText(getString(R.string.status_member_removed))
                        loadHistory()
                    }
                })
            }
        })
    }

    private fun updateCurrentRoom(chat: MST5.Chat?) {
        if (chat == null || chat.peer == null) return
        val reopenChannelSettings = page === Page.CHANNEL_SETTINGS
        currentPeerUser = chat.peer
        currentPeer = resolvedPeerName(chat.peer, chat.id)
        refreshCurrentPeerNameView()
        refreshChatInput()
        loadChats()
        if (reopenChannelSettings && currentPeerIsChannel() && currentPeerCanManageRoom()) showChannelSettings()
    }

    private fun userProfileRow(titleText: String?, value: String?, copyLabel: String?): LinearLayout {
        val row: LinearLayout = LinearLayout(this)
        row.setOrientation(LinearLayout.VERTICAL)
        row.setPadding(pad, gap, pad, gap)
        row.setBackgroundDrawable(
            if (copyLabel == null) shape(surface, 0, elementRadius()) else pressable(
                surface,
                surfaceHi,
                0,
                elementRadius()
            )
        )
        val titleView: TextView = label(titleText)
        titleView.setTextColor(muted)
        titleView.setTextSize(13)
        row.addView(titleView, LinearLayout.LayoutParams(-1, -2))
        val valueView: TextView = label(value)
        valueView.setTextColor(textColor)
        if ("user id".equals(copyLabel)) valueView.setTypeface(Typeface.MONOSPACE)
        row.addView(valueView, LinearLayout.LayoutParams(-1, -2))
        if (copyLabel != null && value != null && value.length > 0) {
            row.setOnClickListener(object : View.OnClickListener {
                override fun onClick(v: View?) {
                    val copyValue: String? =
                        if ("username".equals(copyLabel) && value.startsWith("@")) value.substring(1) else value
                    copyToClipboard(copyLabel, copyValue)
                }
            })
        }
        return row
    }

    private fun copyCurrentPeerID() {
        val user: MST5.User? = currentHeaderUser()
        if (user == null || user.id.length == 0) {
            status.setText(getString(R.string.status_id_not_loaded))
            return
        }
        copyToClipboard("user id", user.id)
    }

    private fun clickableUserID(value: String?): TextView {
        val id: TextView = label(value)
        id.setTextColor(textColor)
        id.setTypeface(Typeface.MONOSPACE)
        id.setPadding(pad, gap, pad, gap)
        id.setBackgroundDrawable(pressable(surface, surfaceHi, 0, elementRadius()))
        id.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                copyToClipboard("user id", value)
            }
        })
        return id
    }

    private fun confirmDeleteCurrentChat() {
        val peerName: String = (if (currentPeer == null) "" else currentPeer)!!
        if (peerName.length == 0) return
        showSwipeConfirmDialog(
            getString(R.string.confirm_delete_chat),
            peerName,
            getString(R.string.delete_slide_hint),
            object : Runnable {
                override fun run() {
                    deleteCurrentChat(peerName)
                }
            }
        )
    }

    private fun confirmLeaveCurrentRoom() {
        val peerName: String = (if (currentPeer == null) "" else currentPeer)!!
        if (peerName.length == 0 || !currentPeerIsRoom()) return
        showConfirmDialog(
            getString(R.string.confirm_leave_chat),
            peerName,
            getString(R.string.action_leave_chat),
            object : Runnable {
                override fun run() {
                    leaveCurrentRoom(peerName)
                }
            })
    }

    private fun showSwipeConfirmDialog(
        titleText: String?,
        detailText: String?,
        hintText: String?,
        onConfirm: Runnable?
    ) {
        showSwipeConfirmDialog(titleText, detailText, hintText, onConfirm, null)
    }

    private fun showSwipeConfirmDialog(
        titleText: String?, detailText: String?, hintText: String?,
        onConfirm: Runnable?, onCancel: Runnable?
    ) {
        val dialog: Dialog = Dialog(this)
        val confirmed = booleanArrayOf(false)
        dialog.setOnDismissListener(object : DialogInterface.OnDismissListener {
            override fun onDismiss(ignored: android.content.DialogInterface?) {
                if (!confirmed[0] && onCancel != null) onCancel.run()
            }
        })
        val box: LinearLayout = dialogBox()
        val title: TextView = title(titleText)
        box.addView(title, LinearLayout.LayoutParams(-1, -2))
        val details: TextView = label(if (detailText == null) "" else detailText)
        details.setTextColor(muted)
        val detailsLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        detailsLp.setMargins(0, 0, 0, gap)
        box.addView(details, detailsLp)
        val slider: PaymentSliderView = paymentSlider(hintText)
        slider.setContentDescription(hintText)
        slider.setOnConfirmAction(object : Runnable {
            override fun run() {
                confirmed[0] = true
                dialog.dismiss()
                if (onConfirm != null) onConfirm.run()
            }
        })
        box.addView(slider, LinearLayout.LayoutParams(-1, dp(56)))
        val cancel: Button = button(getString(R.string.action_cancel), object : View.OnClickListener {
            override fun onClick(v: View?) {
                dialog.dismiss()
            }
        })
        val cancelLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        cancelLp.setMargins(0, gap, 0, 0)
        box.addView(cancel, cancelLp)
        setScrollableDialogContent(dialog, box)
        showStyledDialog(dialog)
    }

    private fun deleteCurrentChat(peerName: String) {
        val c: MST5? = ta
        if (c == null || peerName.length == 0) return
        run("delete_chat", object : Task {
            @Throws(Exception::class)
            override fun run() {
                c.deleteChat(peerName)
                ChatCache.deleteChat(
                    this@MainActivity,
                    SessionStore.server(this@MainActivity, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
                    myLogin,
                    peerName
                )
                ui(object : Runnable {
                    override fun run() {
                        if (peerName.equals(currentPeer)) {
                            currentPeer = ""
                            currentPeerUser = null
                            currentPeerBanned = false
                            currentPeerBannedByMe = false
                            currentPeerBannedMe = false
                            showChats()
                        }
                        status.setText(getString(R.string.status_chat_deleted))
                    }
                })
            }
        })
    }

    private fun leaveCurrentRoom(peerName: String) {
        val c: MST5? = ta
        if (c == null || peerName.length == 0) return
        val me = if (myID != null && myID.length > 0) myID else myLogin
        status.setText(getString(R.string.status_leaving_chat))
        run("leave_chat", object : Task {
            @Throws(Exception::class)
            override fun run() {
                c.leaveChat(peerName, me)
                ChatCache.deleteChat(
                    this@MainActivity,
                    SessionStore.server(this@MainActivity, ru.e6atb.chat.MainActivity.Companion.DEFAULT_SERVER),
                    myLogin,
                    peerName
                )
                ui(object : Runnable {
                    override fun run() {
                        if (peerName.equals(currentPeer)) {
                            currentPeer = ""
                            currentPeerUser = null
                            currentPeerBanned = false
                            currentPeerBannedByMe = false
                            currentPeerBannedMe = false
                            showChats()
                        }
                        status.setText(getString(R.string.status_left_chat))
                    }
                })
            }
        })
    }

    private fun confirmBanCurrentPeer() {
        val peerName: String = (if (currentPeer == null) "" else currentPeer)!!
        if (peerName.length == 0) return
        showConfirmDialog(
            getString(R.string.confirm_ban_user),
            peerName,
            getString(R.string.action_ban_user),
            object : Runnable {
                override fun run() {
                    banCurrentPeer(peerName)
                }
            })
    }

    private fun confirmUnbanCurrentPeer() {
        val peerName: String = (if (currentPeer == null) "" else currentPeer)!!
        if (peerName.length == 0) return
        showConfirmDialog(
            getString(R.string.confirm_unban_user),
            peerName,
            getString(R.string.action_unban_user),
            object : Runnable {
                override fun run() {
                    unbanCurrentPeer(peerName)
                }
            })
    }

    private fun banCurrentPeer(peerName: String) {
        val c: MST5? = ta
        if (c == null || peerName.length == 0) return
        run("ban_user", object : Task {
            @Throws(Exception::class)
            override fun run() {
                c.banUser(peerName)
                ui(object : Runnable {
                    override fun run() {
                        status.setText(getString(R.string.status_user_banned))
                        if (peerName.equals(currentPeer)) {
                            currentPeerBanned = true
                            currentPeerBannedByMe = true
                            showChat()
                            loadHistory()
                        }
                        loadChats()
                    }
                })
            }
        })
    }

    private fun unbanCurrentPeer(peerName: String) {
        val c: MST5? = ta
        if (c == null || peerName.length == 0) return
        run("unban_user", object : Task {
            @Throws(Exception::class)
            override fun run() {
                c.unbanUser(peerName)
                ui(object : Runnable {
                    override fun run() {
                        status.setText(getString(R.string.status_user_unbanned))
                        if (peerName.equals(currentPeer)) {
                            currentPeerBannedByMe = false
                            currentPeerBanned = currentPeerBannedMe
                            showChat()
                            loadHistory()
                        }
                        loadChats()
                    }
                })
            }
        })
    }

    private fun styleList(list: ListView, messages: Boolean) {
        list.setDivider(ColorDrawable(Color.TRANSPARENT))
        list.setPadding(0, 0, 0, 0)
        list.setSelector(ColorDrawable(Color.TRANSPARENT))
        list.setClipToPadding(false)
    }

    private fun showInfoDialog(titleText: String?, message: String?) {
        val body: TextView = label(if (message == null) "" else message)
        body.setTextColor(muted)
        body.setPadding(gap, 0, gap, 0)
        showContentDialog(titleText, body, getString(R.string.action_ok), null, null)
    }

    private fun showConfirmDialog(
        titleText: String?,
        message: String?,
        primaryText: String?,
        primaryAction: Runnable?
    ) {
        val body: TextView = label(if (message == null) "" else message)
        body.setTextColor(muted)
        body.setPadding(gap, 0, gap, 0)
        showContentDialog(titleText, body, primaryText, primaryAction, getString(R.string.action_cancel))
    }

    private fun showActionDialog(actions: Array<out String>?, handler: ChoiceHandler?) {
        showActionDialog(null, actions, handler)
    }

    private fun showActionDialog(titleText: String?, actions: Array<out String>?, handler: ChoiceHandler?) {
        if (actions == null || actions.size == 0) return
        val dialog: Dialog = Dialog(this)
        val box: LinearLayout = dialogBox()
        if (titleText != null && titleText.length > 0) {
            box.addView(title(titleText), LinearLayout.LayoutParams(-1, -2))
        }
        for (i in actions.indices) {
            val which: Int = i
            val action: Button = sheetActionButton(actions[i], object : View.OnClickListener {
                override fun onClick(v: View?) {
                    dialog.dismiss()
                    if (handler != null) handler.onChoice(which)
                }
            }, isDestructiveAction(actions[i]))
            box.addView(action, LinearLayout.LayoutParams(-1, -2))
        }
        val cancel: Button = button(getString(R.string.action_cancel), object : View.OnClickListener {
            override fun onClick(v: View?) {
                dialog.dismiss()
            }
        })
        val cancelLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        cancelLp.setMargins(0, gap, 0, 0)
        box.addView(cancel, cancelLp)
        setScrollableDialogContent(dialog, box)
        showStyledDialog(dialog)
    }

    private fun sheetActionButton(value: String?, listener: View.OnClickListener?, destructive: Boolean): Button {
        val button: Button = Button(this)
        button.setText(ru.e6atb.chat.MainActivity.Companion.safeDisplayText(value))
        button.setTextSize(15)
        button.setGravity(Gravity.CENTER_VERTICAL or Gravity.LEFT)
        button.setTextColor(if (destructive) danger else textColor)
        button.setPadding(gap, 0, gap, 0)
        button.setMinWidth(0)
        button.setMinimumWidth(0)
        button.setMinHeight(dp(46))
        button.setMinimumHeight(dp(46))
        button.setBackgroundDrawable(pressable(Color.TRANSPARENT, surfaceHi, 0, dp(12)))
        button.setOnClickListener(listener)
        return button
    }

    private fun isDestructiveAction(value: String?): Boolean {
        val action = if (value == null) "" else value.toLowerCase(Locale.US)
        return action.contains("delete") || action.contains("remove") || action.contains("удал") || action.contains("выйти")
    }

    private fun showContentDialog(
        titleText: String?,
        contentView: View?,
        primaryText: String?,
        primaryAction: Runnable?,
        secondaryText: String?
    ) {
        val dialog: Dialog = Dialog(this)
        val box: LinearLayout = dialogBox()
        if (titleText != null && titleText.length > 0) {
            box.addView(title(titleText), LinearLayout.LayoutParams(-1, -2))
        }
        if (contentView != null) {
            val scroll: ScrollView = BoundedScrollView(this, getResources().getDisplayMetrics().heightPixels * 3 / 5)
            scroll.setFillViewport(false)
            scroll.setBackgroundColor(Color.TRANSPARENT)
            scroll.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY)
            scroll.addView(contentView, LinearLayout.LayoutParams(-1, -2))
            val scrollLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
            scrollLp.setMargins(0, 0, 0, gap)
            box.addView(scroll, scrollLp)
        }
        val buttons: LinearLayout = LinearLayout(this)
        buttons.setOrientation(LinearLayout.HORIZONTAL)
        if (secondaryText != null && secondaryText.length > 0) {
            val secondary: Button = button(secondaryText, object : View.OnClickListener {
                override fun onClick(v: View?) {
                    dialog.dismiss()
                }
            })
            val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            lp.setMargins(0, 0, gap / 2, 0)
            buttons.addView(secondary, lp)
        }
        if (primaryText != null && primaryText.length > 0) {
            val primaryActionButton: Button = primaryButton(primaryText, object : View.OnClickListener {
                override fun onClick(v: View?) {
                    dialog.dismiss()
                    if (primaryAction != null) primaryAction.run()
                }
            })
            val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            lp.setMargins(if (secondaryText != null && secondaryText.length > 0) gap / 2 else 0, 0, 0, 0)
            buttons.addView(primaryActionButton, lp)
        }
        if (buttons.getChildCount() > 0) {
            box.addView(buttons, LinearLayout.LayoutParams(-1, -2))
        }
        setScrollableDialogContent(dialog, box)
        showStyledDialog(dialog)
    }

    private fun dialogBox(): LinearLayout {
        val box: LinearLayout = LinearLayout(this)
        box.setOrientation(LinearLayout.VERTICAL)
        box.setPadding(pad, gap / 2, pad, dp(14))
        box.setBackgroundDrawable(shape(surface, 0, dp(20)))
        val handle: View = View(this)
        handle.setBackgroundDrawable(shape(blend(muted, surface, 0.45f), 0, dp(2)))
        val handleLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(dp(36), dp(4))
        handleLp.gravity = Gravity.CENTER_HORIZONTAL
        handleLp.setMargins(0, 0, 0, gap)
        box.addView(handle, handleLp)
        return box
    }

    private fun showStyledDialog(dialog: Dialog) {
        configureDialogWindow(dialog)
        dialog.show()
        installSwipeDismiss(dialog)
        configureDialogWindow(dialog)
    }

    private fun installSwipeDismiss(dialog: Dialog) {
        val window: Window? = dialog.getWindow()
        if (window == null) return
        val contentView: View? = window.findViewById(android.R.id.content)
        if (contentView !is ViewGroup) return
        val contentRoot: ViewGroup = contentView as ViewGroup
        if (contentRoot.getChildCount() !== 1 || contentRoot.getChildAt(0) is SwipeDismissLayout) return
        val sheet: View? = contentRoot.getChildAt(0)
        contentRoot.removeView(sheet)
        val swipe: SwipeDismissLayout = SwipeDismissLayout(this)
        swipe.setDismissAction(object : Runnable {
            override fun run() {
                dialog.dismiss()
            }
        })
        swipe.addView(sheet, LinearLayout.LayoutParams(-1, -2))
        contentRoot.addView(swipe, LinearLayout.LayoutParams(-1, -2))
    }

    private fun configureDialogWindow(dialog: Dialog) {
        val window: Window? = dialog.getWindow()
        if (window == null) return
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setGravity(Gravity.BOTTOM)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun row(vararg buttons: Button?): LinearLayout {
        val r: LinearLayout = LinearLayout(this)
        r.setOrientation(LinearLayout.HORIZONTAL)
        for (b in buttons) {
            val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            lp.setMargins(gap / 2, 0, gap / 2, 0)
            r.addView(b, lp)
        }
        return r
    }

    private fun row(button: ImageButton?): LinearLayout {
        val r: LinearLayout = LinearLayout(this)
        r.setOrientation(LinearLayout.HORIZONTAL)
        val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight)
        lp.setMargins(gap / 2, 0, gap / 2, 0)
        r.addView(button, lp)
        return r
    }

    private fun mixedRow(first: View?, second: View?, firstFixed: Boolean): LinearLayout {
        val r: LinearLayout = LinearLayout(this)
        r.setOrientation(LinearLayout.HORIZONTAL)
        val firstLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            if (firstFixed) buttonMinHeight else 0,
            if (firstFixed) buttonMinHeight else -2,
            (if (firstFixed) 0 else 1).toFloat()
        )
        firstLp.setMargins(gap / 2, 0, gap / 2, 0)
        r.addView(first, firstLp)
        val secondLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            if (firstFixed) 0 else buttonMinHeight,
            if (firstFixed) -2 else buttonMinHeight,
            (if (firstFixed) 1 else 0).toFloat()
        )
        secondLp.setMargins(gap / 2, 0, gap / 2, 0)
        r.addView(second, secondLp)
        return r
    }

    private fun navRow(vararg buttons: View): LinearLayout {
        val r: LinearLayout = LinearLayout(this)
        r.setOrientation(LinearLayout.HORIZONTAL)
        r.setGravity(Gravity.CENTER)
        val height = dp(64)
        for (b in buttons) {
            val item: LinearLayout = LinearLayout(this)
            item.setOrientation(LinearLayout.VERTICAL)
            item.setGravity(Gravity.CENTER)
            // The item owns the selected-state surface.  Keeping the icon button
            // transparent prevents the two overlapping active backgrounds.
            b.setBackgroundDrawable(pressable(Color.TRANSPARENT, blend(primary, surface, 0.86f), 0, dp(14)))
            item.addView(b, LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight))
            val caption: TextView = TextView(this)
            caption.setText(b.getContentDescription())
            caption.setTextColor(muted)
            caption.setTextSize(11)
            caption.setSingleLine(true)
            caption.setGravity(Gravity.CENTER)
            item.addView(caption, LinearLayout.LayoutParams(-1, -2))
            val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, height, 1f)
            lp.setMargins(gap, dp(4), gap, dp(4))
            r.addView(item, lp)
        }
        return r
    }

    private fun updateBottomNavSelection() {
        if (bottomNav == null) return
        val active = if (page === Page.CHATS)
            0
        else
            if (page === Page.WALLET || page === Page.WALLET_HISTORY)
                1
            else
                if (page === Page.NODES)
                    2
                else
                    if (page === Page.SETTINGS) 3 else -1
        for (i in 0..<bottomNav.getChildCount()) {
            val item: View? = bottomNav.getChildAt(i)
            if (item !is LinearLayout) continue
            val group: LinearLayout = item as LinearLayout
            val selected = i == active
            group.setBackgroundDrawable(
                shape(
                    if (selected) blend(primary, bg, 0.84f) else Color.TRANSPARENT,
                    0,
                    dp(14)
                )
            )
            if (group.getChildCount() > 0 && group.getChildAt(0) is ImageButton) {
                (group.getChildAt(0) as ImageButton).setColorFilter(if (selected) primary else muted)
            }
            if (group.getChildCount() > 1 && group.getChildAt(1) is TextView) {
                (group.getChildAt(1) as TextView).setTextColor(if (selected) primary else muted)
            }
        }
    }

    private fun pageScrollView(): ScrollView {
        val scroll: ScrollView = ScrollView(this)
        scroll.setFillViewport(false)
        scroll.setBackgroundColor(bg)
        scroll.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY)
        return scroll
    }

    private fun messageBar(): LinearLayout {
        val outer: LinearLayout = LinearLayout(this)
        outer.setOrientation(LinearLayout.VERTICAL)
        outer.setBackgroundColor(bg)
        val topDivider: View = View(this)
        topDivider.setBackgroundColor(border)
        outer.addView(topDivider, LinearLayout.LayoutParams(-1, Math.max(1, dp(1))))
        val body: LinearLayout = LinearLayout(this)
        body.setOrientation(LinearLayout.VERTICAL)
        body.setPadding(pad, gap, pad, gap)
        composerMediaBar = LinearLayout(this)
        composerMediaBar.setOrientation(LinearLayout.VERTICAL)
        body.addView(composerMediaBar, LinearLayout.LayoutParams(-1, -2))
        val r: LinearLayout = LinearLayout(this)
        r.setOrientation(LinearLayout.HORIZONTAL)
        r.setGravity(Gravity.BOTTOM)
        val inputLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        inputLp.setMargins(0, 0, gap, 0)
        if (text.getParent() is ViewGroup) {
            (text.getParent() as ViewGroup).removeView(text)
        }
        if (currentPeer != null && currentPeer!!.equals(botCommandsPeer) && !botCommands.isEmpty()) {
            val commands: ImageButton =
                inputIconButton(R.drawable.ic_more_vertical, "Bot commands", object : View.OnClickListener {
                    override fun onClick(v: View?) {
                        showBotCommandsMenu()
                    }
                })
            val commandsLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight)
            commandsLp.setMargins(0, 0, gap, 0)
            r.addView(commands, commandsLp)
        }
        r.addView(text, inputLp)
        val stickerButton: ImageButton = inputIconButton(R.drawable.ic_sticker, "Stickers", object : View.OnClickListener {
            override fun onClick(v: View?) {
                showStickerPicker()
            }
        })
        val stickerLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight)
        stickerLp.setMargins(0, 0, gap, 0)
        r.addView(stickerButton, stickerLp)
        val attachButton: ImageButton =
            inputIconButton(R.drawable.ic_attach, getString(R.string.attachment_attach), object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showAttachmentActions()
                }
            })
        val attachLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight)
        attachLp.setMargins(0, 0, gap, 0)
        r.addView(attachButton, attachLp)

        sendButton = inputIconButton(R.drawable.ic_send, getString(R.string.action_send), object : View.OnClickListener {
            override fun onClick(v: View?) {
                send()
            }
        })
        val sendLp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight)
        r.addView(sendButton, sendLp)
        sendButton.setBackgroundDrawable(pressable(primary, blend(primary, Color.WHITE, 0.18f), 0, dp(14)))
        sendButton.setColorFilter(onPrimary)
        body.addView(r, LinearLayout.LayoutParams(-1, -2))
        outer.addView(body, LinearLayout.LayoutParams(-1, -2))
        renderComposerMedia()
        return outer
    }

    private fun renderComposerMedia() {
        if (composerMediaBar == null) return
        composerMediaBar.removeAllViews()
        for (index in 0..<composerMedia.size) {
            val position = index
            val item: ComposerMedia = composerMedia.get(index)
            val container: LinearLayout = LinearLayout(this)
            container.setOrientation(LinearLayout.VERTICAL)
            container.setPadding(gap, gap / 2, gap, gap / 2)
            container.setBackgroundDrawable(shape(surfaceHi, 0, elementRadius()))
            if (item.preview != null) {
                val preview: ImageView = ImageView(this)
                preview.setAdjustViewBounds(true)
                preview.setScaleType(ImageView.ScaleType.FIT_CENTER)
                preview.setMaxHeight(dp(360))
                preview.setImageBitmap(item.preview!!)
                container.addView(preview, LinearLayout.LayoutParams(-1, -2))
            }
            val row: LinearLayout = LinearLayout(this)
            row.setOrientation(LinearLayout.HORIZONTAL)
            row.setGravity(Gravity.CENTER_VERTICAL)
            val label: TextView = label(item.name.toString() + " · " + formatBytes(item.size))
            row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            val remove: Button = button("×", object : View.OnClickListener {
                override fun onClick(v: View?) {
                    if (position < 0 || position >= composerMedia.size) return
                    val removed = composerMedia.removeAt(position)
                    removed.preview?.let { if (!it.isRecycled) it.recycle() }
                    removed.localPath?.takeIf { it.isNotEmpty() }?.let { File(it).delete() }
                    renderComposerMedia()
                }
            })
            row.addView(remove, LinearLayout.LayoutParams(buttonMinHeight, buttonMinHeight))
            container.addView(row, LinearLayout.LayoutParams(-1, -2))
            val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(0, 0, 0, gap / 2)
            composerMediaBar.addView(container, lp)
        }
    }

    private fun recycleComposerPreviews() {
        for (item in composerMedia) {
            item.preview?.let { if (!it.isRecycled) it.recycle() }
            item.preview = null
        }
    }

    private fun bannedChatBlock(): TextView {
        val block: TextView = label(getString(R.string.chat_banned))
        block.setTextColor(muted)
        block.setTextSize(16)
        block.setGravity(Gravity.CENTER)
        block.setPadding(pad, pad, pad, pad)
        block.setBackgroundDrawable(shape(surface, 0, elementRadius()))
        return block
    }

    private fun readOnlyRoomBlock(): View {
        val box: LinearLayout = LinearLayout(this)
        box.setOrientation(LinearLayout.VERTICAL)
        box.setPadding(pad, pad, pad, pad)
        box.setBackgroundDrawable(shape(surface, 0, elementRadius()))
        if (currentPeerIsChannel()) {
            box.addView(primaryButton(getString(R.string.action_donate), object : View.OnClickListener {
                override fun onClick(v: View?) {
                    showDastarsTransferDialog(currentPeer)
                }
            }), LinearLayout.LayoutParams(-1, -2))
        } else {
            val block: TextView = label(getString(R.string.room_read_only))
            block.setTextColor(muted)
            block.setTextSize(16)
            block.setGravity(Gravity.CENTER)
            box.addView(block, LinearLayout.LayoutParams(-1, -2))
        }
        return box
    }

    private fun setAuthLoading(loading: Boolean, sendingCode: Boolean) {
        val idle: String? = if (waitingEmailCode) getString(R.string.action_login) else getString(R.string.action_next)
        val busy: String? =
            if (sendingCode) getString(R.string.status_sending_code) else getString(R.string.status_checking_code)
        setButtonBusy(loginButton, loading, busy, idle, true)
        if (resendEmailCodeButton != null) {
            if (loading) {
                setButtonEnabledStyle(resendEmailCodeButton, false, false)
                setButtonRequestBusy(resendEmailCodeButton, true)
            } else {
                setButtonRequestBusy(resendEmailCodeButton, false)
                updateEmailCodeCooldown()
            }
        }
        if (loading) status.setText(if (sendingCode) getString(R.string.status_sending_code) else getString(R.string.status_checking_code))
    }

    private fun setSendLoading(loading: Boolean) {
        if (sendButton != null) {
            sendButton.setEnabled(!loading)
            setButtonRequestBusy(sendButton, loading)
            sendButton.setBackgroundDrawable(
                pressable(
                    if (loading) blend(primary, Color.BLACK, 0.30f) else primary,
                    if (loading) blend(primary, Color.BLACK, 0.20f) else blend(primary, Color.WHITE, 0.18f),
                    0,
                    dp(14)
                )
            )
            sendButton.setColorFilter(if (loading) blend(onPrimary, bg, 0.55f) else onPrimary)
            sendButton.setContentDescription(if (loading) getString(R.string.status_sending) else getString(R.string.action_send))
        }
        if (text != null) text.setEnabled(!loading)
        if (loading) status.setText(getString(R.string.status_sending))
    }

    private fun spaced(v: View): View {
        val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, 0, 0, gap)
        v.setLayoutParams(lp)
        return v
    }

    private fun fill(): LinearLayout.LayoutParams {
        val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        lp.setMargins(0, 0, 0, gap)
        return lp
    }

    private fun shape(fill: Int, stroke: Int, radius: Int): GradientDrawable {
        val d: GradientDrawable = GradientDrawable()
        d.setColor(fill)
        d.setCornerRadius(radius.toFloat())
        if (stroke != 0) d.setStroke(dp(1), stroke)
        return d
    }

    /** Telegram-like message tails, while preserving the generous Material You radius.  */
    private fun messageBubble(own: Boolean): Drawable {
        val large = dp(20).toFloat()
        val tail = dp(6).toFloat()
        val d: GradientDrawable = GradientDrawable()
        d.setColor(if (own) primary else surface)
        if (own) {
            d.setCornerRadii(floatArrayOf(large, large, large, large, tail, tail, large, large))
        } else {
            d.setCornerRadii(floatArrayOf(large, large, large, large, large, large, tail, tail))
        }
        return d
    }

    private fun pressable(normal: Int, pressed: Int, stroke: Int, radius: Int): Drawable {
        val s: StateListDrawable = StateListDrawable()
        s.addState(
            intArrayOf(
                android.R.attr.state_pressed
            ), shape(pressed, stroke, radius)
        )
        s.addState(
            intArrayOf(
                android.R.attr.state_focused
            ), shape(pressed, stroke, radius)
        )
        s.addState(intArrayOf(), shape(normal, stroke, radius))
        return s
    }

    private fun choiceButtonDrawable(radio: Boolean): Drawable {
        val size = choiceButtonSize()
        return ru.e6atb.chat.MainActivity.ChoiceButtonDrawable(
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
        )
    }

    private fun initDimens() {
        val inches = screenDiagonalInches()
        val scale: Float = (Math.max(4.0f, Math.min(6.0f, inches)) - 4.0f) / 2.0f
        val padDp = clampInt(Math.round(12.0f + 6.0f * scale), 12, 18)
        val gapDp = clampInt(Math.round(padDp * 0.55f), 6, 10)
        val buttonPadXDp = clampInt(Math.round(padDp * 0.70f), 8, 12)
        val buttonPadYDp = clampInt(Math.round(padDp * 0.45f), 6, 8)
        val buttonMinHeightDp = clampInt(Math.round(38.0f + 6.0f * scale), 38, 44)
        pad = dp(padDp)
        gap = dp(gapDp)
        buttonPadX = dp(buttonPadXDp)
        buttonPadY = dp(buttonPadYDp)
        buttonMinHeight = dp(buttonMinHeightDp)
    }

    private fun screenDiagonalInches(): Float {
        val metrics: android.util.DisplayMetrics = getResources().getDisplayMetrics()
        val xdpi: Float = metrics.xdpi
        val ydpi: Float = metrics.ydpi
        if (xdpi > 0.0f && ydpi > 0.0f) {
            val widthIn: Float = metrics.widthPixels / xdpi
            val heightIn: Float = metrics.heightPixels / ydpi
            val diagonal = Math.sqrt((widthIn * widthIn + heightIn * heightIn).toDouble()).toFloat()
            if (diagonal >= 2.5f && diagonal <= 20.0f) {
                return diagonal
            }
        }
        val densityDpi: Float = if (metrics.densityDpi > 0) metrics.densityDpi.toFloat() else 160.0f
        val widthIn: Float = metrics.widthPixels / densityDpi
        val heightIn: Float = metrics.heightPixels / densityDpi
        return Math.sqrt((widthIn * widthIn + heightIn * heightIn).toDouble()).toFloat()
    }

    private fun clampInt(value: Int, min: Int, max: Int): Int {
        if (value < min) return min
        if (value > max) return max
        return value
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
    }

    private fun loadPalette() {
        // The mockups use a calm dark neutral base and let the system accent be the
        // only strong colour.  Keeping the accent dynamic makes the client feel at
        // home on Android 12+, while older devices get the same warm default.
        primary = systemColor("system_accent1_600", themeColorByName("colorAccent", Color.rgb(201, 96, 59)))
        val dark = ((getResources().getConfiguration().uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                === android.content.res.Configuration.UI_MODE_NIGHT_YES)
        val systemBase = systemColor(
            if (dark) "system_neutral1_900" else "system_neutral1_10",
            if (dark) Color.rgb(24, 26, 24) else Color.rgb(247, 247, 247)
        )
        bg = blend(systemBase, if (dark) Color.BLACK else Color.WHITE, if (dark) 0.16f else 0.08f)
        surface = blend(systemBase, if (dark) Color.WHITE else Color.BLACK, if (dark) 0.06f else 0.025f)
        surfaceHi = blend(systemBase, if (dark) Color.BLACK else Color.WHITE, if (dark) 0.28f else 0.15f)
        textColor = if (dark) Color.rgb(244, 242, 239) else Color.rgb(31, 31, 31)
        muted = if (dark) Color.rgb(182, 179, 174) else Color.rgb(104, 102, 100)
        border = blend(systemBase, if (dark) Color.WHITE else Color.BLACK, if (dark) 0.18f else 0.13f)
        accentSurface = blend(primary, bg, 0.72f)
        danger = Color.rgb(238, 112, 112)
        success = Color.rgb(90, 202, 126)
        onPrimary = Color.WHITE
    }

    private fun systemColor(name: String?, fallback: Int): Int {
        if (Build.VERSION.SDK_INT < 31) return fallback
        val id: Int = getResources().getIdentifier(name, "color", "android")
        return if (id == 0) fallback else getResources().getColor(id)
    }

    private fun themeColor(attr: Int, fallback: Int): Int {
        val v: TypedValue = TypedValue()
        return if (getTheme().resolveAttribute(attr, v, true)) v.data else fallback
    }

    private fun themeColorByName(attrName: String?, fallback: Int): Int {
        if (Build.VERSION.SDK_INT < 21) return fallback
        try {
            val attrs: Class<*> = Class.forName("android.R\$attr")
            return themeColor(attrs.getField(attrName).getInt(null), fallback)
        } catch (ignored: Exception) {
            return fallback
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        return Color.rgb(
            (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
            (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
            (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
        )
    }

    private fun contrast(c: Int): Int {
        val y: Double = (Color.red(c) * 0.299 + Color.green(c) * 0.587 + Color.blue(c) * 0.114)
        return if (y > 150) Color.BLACK else Color.WHITE
    }

    private class ChoiceCheckBox(context: android.content.Context?, private val minTextInset: Int) : CheckBox(context) {
        override fun getCompoundPaddingLeft(): Int =
            Math.max(super.getCompoundPaddingLeft(), getPaddingLeft() + minTextInset)
    }

    private class ChoiceRadioButton(context: android.content.Context?, private val minTextInset: Int) :
        RadioButton(context) {
        override fun getCompoundPaddingLeft(): Int =
            Math.max(super.getCompoundPaddingLeft(), getPaddingLeft() + minTextInset)
    }

    private class ChoiceButtonDrawable(
        private val radio: Boolean,
        private val drawableHeight: Int,
        private val leadingInset: Int,
        private val uncheckedFill: Int,
        private val uncheckedStroke: Int,
        private val pressedFill: Int,
        private val checkedFill: Int,
        private val checkedPressedFill: Int,
        private val disabledFill: Int,
        private val disabledStroke: Int,
        private val mark: Int,
        private val disabledMark: Int
    ) : Drawable() {
        private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path: Path = Path()
        private val rect: RectF = RectF()
        private var checked = false
        private var enabled = true
        private var pressed = false
        private var alpha = 255

        override fun draw(canvas: Canvas) {
            val bounds: Rect = getBounds()
            val s: Float = Math.min(
                drawableHeight,
                Math.min(Math.max(0, bounds.width() - leadingInset), bounds.height())
            ).toFloat()
            val left: Float = (bounds.left + leadingInset).toFloat()
            val top: Float = bounds.top + (bounds.height() - s) / 2.0f
            val strokeWidth: Float = Math.max(1.0f, s * 0.095f)
            val fill = fillColor()
            val stroke = strokeColor()
            val markColor = if (enabled) mark else disabledMark
            paint.setAlpha(alpha)
            if (radio) {
                drawRadio(canvas, left, top, s, strokeWidth, fill, stroke, markColor)
            } else {
                drawCheckBox(canvas, left, top, s, strokeWidth, fill, stroke, markColor)
            }
        }

        fun fillColor(): Int {
            if (!enabled) return disabledFill
            if (checked && pressed) return checkedPressedFill
            if (checked) return checkedFill
            if (pressed) return pressedFill
            return uncheckedFill
        }

        fun strokeColor(): Int {
            if (!enabled) return disabledStroke
            if (checked || pressed) return checkedFill
            return uncheckedStroke
        }

        fun drawRadio(
            canvas: Canvas,
            left: Float,
            top: Float,
            s: Float,
            strokeWidth: Float,
            fill: Int,
            stroke: Int,
            markColor: Int
        ) {
            val cx = left + s / 2.0f
            val cy = top + s / 2.0f
            val radius = s / 2.0f - strokeWidth / 2.0f
            paint.setStyle(Paint.Style.FILL)
            paint.setColor(fill)
            canvas.drawCircle(cx, cy, radius, paint)
            paint.setStyle(Paint.Style.STROKE)
            paint.setStrokeWidth(strokeWidth)
            paint.setColor(stroke)
            canvas.drawCircle(cx, cy, radius, paint)
            if (!checked) return
            paint.setStyle(Paint.Style.FILL)
            paint.setColor(markColor)
            canvas.drawCircle(cx, cy, s * 0.27f, paint)
        }

        fun drawCheckBox(
            canvas: Canvas,
            left: Float,
            top: Float,
            s: Float,
            strokeWidth: Float,
            fill: Int,
            stroke: Int,
            markColor: Int
        ) {
            val radius = s * 0.22f
            rect.set(
                left + strokeWidth / 2.0f,
                top + strokeWidth / 2.0f,
                left + s - strokeWidth / 2.0f,
                top + s - strokeWidth / 2.0f
            )
            paint.setStyle(Paint.Style.FILL)
            paint.setColor(fill)
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.setStyle(Paint.Style.STROKE)
            paint.setStrokeWidth(strokeWidth)
            paint.setColor(stroke)
            canvas.drawRoundRect(rect, radius, radius, paint)
            if (!checked) return
            paint.setColor(markColor)
            paint.setStrokeWidth(Math.max(2.0f, s * 0.13f))
            paint.setStrokeCap(Paint.Cap.ROUND)
            paint.setStrokeJoin(Paint.Join.ROUND)
            path.reset()
            path.moveTo(left + s * 0.28f, top + s * 0.52f)
            path.lineTo(left + s * 0.43f, top + s * 0.67f)
            path.lineTo(left + s * 0.73f, top + s * 0.35f)
            canvas.drawPath(path, paint)
            paint.setStrokeCap(Paint.Cap.BUTT)
            paint.setStrokeJoin(Paint.Join.MITER)
        }

        override fun getIntrinsicHeight(): Int = drawableHeight

        override fun getIntrinsicWidth(): Int = leadingInset + intrinsicHeight

        override fun isStateful(): Boolean = true

        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        override fun onStateChange(state: IntArray): Boolean {
            var nextEnabled = false
            var nextChecked = false
            var nextPressed = false
            if (state != null) {
                for (value in state) {
                    if (value == android.R.attr.state_enabled) nextEnabled = true
                    else if (value == android.R.attr.state_checked) nextChecked = true
                    else if (value == android.R.attr.state_pressed || value == android.R.attr.state_focused) nextPressed =
                        true
                }
            }
            if (enabled == nextEnabled && checked == nextChecked && pressed == nextPressed) return false
            enabled = nextEnabled
            checked = nextChecked
            pressed = nextPressed
            invalidateSelf()
            return true
        }

        override fun setAlpha(alpha: Int) {
            this.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.setColorFilter(colorFilter)
            invalidateSelf()
        }

    }

    private interface Task {
        @Throws(Exception::class)
        fun run()
    }

    private interface ChoiceHandler {
        fun onChoice(which: Int)
    }

    companion object {
        // Prefer raw MST5, then the central CDN's ordinary HTTP-only M5oH route.
        val DEFAULT_SERVER: String = "mst5://ms.ove.rs:8067/main|http://central-1-cdn.ms.sectorlambda.ru/10.100.2.228:8080"
        val ACTION_ACCEPT_CALL: String = "ru.e6atb.chat.ACCEPT_CALL"
        val ACTION_OPEN_CALL: String = "ru.e6atb.chat.OPEN_CALL"
        val ACTION_OPEN_UPDATE: String = "ru.e6atb.chat.OPEN_UPDATE"
        val EXTRA_PEER: String = "peer"
        val EXTRA_CALL: String = "call_peer"
        val EXTRA_CHAT: String = "chat_peer"

        private const val HISTORY_PAGE = 40
        private const val PAID_REACTION_BATCH_DELAY_MS: Long = 500
        private val QUICK_REACTIONS = arrayOf<String?>("👍", "❤️", "😂", "😮", "😢", "👎")
        private val ALL_REACTIONS = arrayOf<String>(
            "👍", "❤️", "😂", "😮", "😢", "👎", "🔥", "🥰", "👏", "😁", "🤔", "🤯",
            "😱", "🤬", "🎉", "🤩", "🤮", "💩", "🙏", "👌", "🕊️", "🤡", "🥱", "🥴",
            "😍", "🐳", "❤‍🔥", "🌚", "🌭", "💯", "🤣", "⚡", "🍌", "🏆", "💔", "🤨",
            "😐", "🍓", "🍾", "💋", "🖕", "😈", "😴", "😭", "🤓", "👻", "👨‍💻", "👀",
            "🎃", "🙈", "😇", "😨", "🤝", "✍️", "🤗", "🫡", "🎅", "🎄", "☃️", "💅",
            "🤪", "🗿", "🆒", "💘", "🙉", "🦄", "😘", "💊", "🙊", "😎", "👾", "🤷"
        )
        private const val REQ_NOTIFICATIONS = 10
        private const val REQ_MICROPHONE = 11
        private const val REQ_READ_STORAGE = 12
        private const val REQ_PICK_IMAGE = 13
        private const val REQ_PICK_FILE = 14
        private const val REQ_CAMERA = 15
        private const val REQ_QR_SCAN = 16
        private const val REQ_PICK_AVATAR = 17
        private val CALL_NOTIFICATION_CHANNEL = "calls_visual"
        private val UPDATE_NOTIFICATION_CHANNEL = "app_updates"
        private const val ACTIVE_CALL_NOTIFICATION_ID = 3
        private const val UPDATE_NOTIFICATION_ID = 4
        private val MAX_UPLOAD_BYTES = 12 * 1024 * 1024
        private const val MAX_IMAGE_PREVIEW_PX = 1280
        private const val USERNAME_RESERVATION_FEE_DSR = 20
        private const val MAX_INCOMING_CALL_AGE_SEC = 120
        private val EMAIL_CODE_RESEND_DELAY_MS = 5 * 60 * 1000L
        private val GITHUB_UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
        private val PERMISSION_RECORD_AUDIO = "android.permission.RECORD_AUDIO"
        private val PERMISSION_READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
        private val PERMISSION_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
        private val PERMISSION_CAMERA = "android.permission.CAMERA"
        private val EXTRA_BOT_LINK_CONSUMED = "bot_link_consumed"
        private const val LANGUAGE_SYSTEM_ID = 1001
        private const val LANGUAGE_ENGLISH_ID = 1002
        private const val LANGUAGE_RUSSIAN_ID = 1003
        private const val PROTOCOL_AUTO_ID = 1011
        private const val PROTOCOL_MST5_ID = 1012
        private const val PROTOCOL_M5OH_ID = 1013
        private const val MESSAGE_PRIVACY_EVERYONE_ID = 1101
        private const val MESSAGE_PRIVACY_CHATS_ID = 1102
        private const val MESSAGE_PRIVACY_NOBODY_ID = 1103
        private const val MESSAGE_PRIVACY_CONTACTS_ID = 1104
        private const val CALL_PRIVACY_EVERYONE_ID = 1201
        private const val CALL_PRIVACY_CHATS_ID = 1202
        private const val CALL_PRIVACY_NOBODY_ID = 1203
        private const val CALL_PRIVACY_CONTACTS_ID = 1204
        private const val INVITE_PRIVACY_EVERYONE_ID = 1301
        private const val INVITE_PRIVACY_CONTACTS_ID = 1302
        private const val INVITE_PRIVACY_NOBODY_ID = 1303

        @JvmStatic
        fun callPeerFor(ownID: String?, ownLogin: String?, call: MST5.Call?): String {
            if (call == null) return ""
            if (call.from != null && !ru.e6atb.chat.MainActivity.Companion.isOwnUserFor(
                    ownID,
                    ownLogin,
                    call.from
                )
            ) return ru.e6atb.chat.MainActivity.Companion.userAddress(call.from)
            if (call.to != null) return ru.e6atb.chat.MainActivity.Companion.userAddress(call.to)
            return ru.e6atb.chat.MainActivity.Companion.userAddress(call.from)
        }

        @JvmStatic
        fun isOwnUserFor(ownID: String?, ownLogin: String?, user: MST5.User?): Boolean {
            if (user == null) return false
            if (ownID != null && ownID.length > 0 && ownID.equals(user.id)) return true
            return ownLogin != null && ownLogin.length > 0 && ownLogin.equals(user.login)
        }

        @JvmStatic
        fun isOwnAddressFor(ownID: String?, ownLogin: String?, address: String?): Boolean {
            if (address == null) return false
            var value: String = address.trim()
            if (value.startsWith("@")) value = value.substring(1)
            if (value.length == 0) return false
            if (ownID != null && ownID.length > 0 && ownID.equals(value)) return true
            return ownLogin != null && ownLogin.length > 0 && ownLogin.equals(value)
        }

        private fun userAddress(user: MST5.User?): String {
            if (user == null) return ""
            if (user.login != null && user.login.length > 0) return user.login
            return if (user.id == null) "" else user.id
        }

        fun safeDisplayText(value: String?): String {
            return DisplayText.safe(value)
        }

        private fun isNegativePublicID(value: String?): Boolean {
            if (value == null) return false
            val raw: String = value.trim()
            if (raw.length !== 16) return false
            for (i in 0..<raw.length) {
                if (Character.digit(raw.charAt(i), 16) < 0) return false
            }
            try {
                return java.lang.Long.parseUnsignedLong(raw, 16) < 0
            } catch (ignored: NumberFormatException) {
                return false
            }
        }

        private fun sleep(ms: Long) {
            try {
                Thread.sleep(ms)
            } catch (ignored: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }
}
