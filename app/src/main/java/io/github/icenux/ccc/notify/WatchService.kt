package io.github.icenux.ccc.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import io.github.icenux.ccc.MainActivity
import io.github.icenux.ccc.R
import io.github.icenux.ccc.data.Alert
import io.github.icenux.ccc.data.Alerts
import io.github.icenux.ccc.data.ApiException
import io.github.icenux.ccc.data.CccApi
import io.github.icenux.ccc.data.Settings
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

/**
 * Keeps ccc's event stream open while the app is closed and turns what
 * needs you into notifications: an approval (with Allow / Deny right in
 * it), an error, a usage limit, a finished reply. A foreground service —
 * the only way Android lets an app keep a connection; there's no push
 * server, everything stays on the tailnet.
 */
class WatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checking = Mutex()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        channels(this)
        val watching = watching(this, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(WATCH_ID, watching, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(WATCH_ID, watching)
        }
        scope.launch { watch() }
    }

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(io.github.icenux.ccc.data.Language.wrap(newBase))

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Started again when a setting changed (the lasting notification on / off): show it at once.
        scope.launch { refreshWatching() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun watch() {
        val settings = Settings(this)
        if (!settings.configured || !settings.notifications) {
            stopSelf()
            return
        }
        val api = CccApi(settings.server, settings.token)
        var wait = 2_000L
        while (true) {
            try {
                api.events().collect {
                    wait = 2_000L
                    check(api, settings)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (e is ApiException && e.code == 401) {
                    settings.notifications = false
                    stopSelf()
                    return
                }
            }
            delay(wait)
            wait = (wait * 2).coerceAtMost(60_000L)
        }
    }

    private suspend fun check(api: CccApi, settings: Settings) = checking.withLock {
        val kinds = settings.alertKinds
        var changes = Alerts.changes(api.permissions(), api.waiting(), settings.shownAlerts, api.status().update, kinds)
        if (changes.show.isNotEmpty()) {
            // A conversation open in the computer's TUI is marked seen within moments: look again before notifying,
            // so what you are looking at never reaches the phone (not even for a flash).
            delay(SETTLE_MS)
            changes = Alerts.changes(api.permissions(), api.waiting(), settings.shownAlerts, api.status().update, kinds)
        }
        val manager = NotificationManagerCompat.from(this)
        changes.gone.forEach { manager.cancel(it, ALERT_ID) }
        changes.show.forEach { post(this, it) }
        settings.shownAlerts = settings.shownAlerts - changes.gone + changes.show.map { it.key }
        refreshWatching(api)
    }

    /** The always-there notification: plain, or (as chosen) with the usage and the conversation counts. */
    private suspend fun refreshWatching(api: CccApi? = null) {
        val settings = Settings(this)
        val stats = if (settings.persistent && settings.configured) {
            try {
                val client = api ?: CccApi(settings.server, settings.token)
                val usage = try { client.usage() } catch (e: ApiException) { null }
                val status = client.status()
                val needYou = client.waiting().size
                val limit = listOfNotNull(
                    usage?.fiveHour?.let { getString(R.string.persistent_limit_5h, it.percent.roundToInt()) },
                    usage?.sevenDay?.let { getString(R.string.persistent_limit_7d, it.percent.roundToInt()) },
                ).joinToString(" · ").ifEmpty { getString(R.string.persistent_limit_unknown) }
                getString(R.string.persistent_counts, status.conversations, needYou) to limit
            } catch (e: IOException) {
                return
            }
        } else {
            null
        }
        lastStats = stats
        try {
            NotificationManagerCompat.from(this).notify(WATCH_ID, watching(this, stats))
        } catch (e: SecurityException) {
            // Notifications aren't allowed (Android 13+): nothing to show.
        }
    }

    companion object {
        const val CHANNEL_WATCH = "watch"
        const val CHANNEL_APPROVALS = "approvals"
        const val CHANNEL_NEEDS_YOU = "needs_you"
        const val SETTLE_MS = 4_000L
        const val WATCH_ID = 1
        const val ALERT_ID = 2
        const val EXTRA_CONVERSATION = "conversation"

        /** What the lasting notification showed last: put back as it was when it is swiped away. */
        @Volatile
        private var lastStats: Pair<String, String>? = null

        /**
         * The lasting notification was swiped away (Android 14+ lets you swipe even an ongoing one):
         * back it comes. Only its switch in Settings takes it away.
         */
        fun restoreWatching(context: Context) {
            val settings = Settings(context)
            if (!settings.configured || !settings.notifications || !settings.persistent) return
            try {
                NotificationManagerCompat.from(context).notify(WATCH_ID, watching(context, lastStats))
            } catch (e: SecurityException) {
                // Notifications aren't allowed (Android 13+): nothing to show.
            }
        }

        /** Start or stop watching, as the setting says. */
        fun sync(context: Context) {
            val settings = Settings(context)
            val intent = Intent(context, WatchService::class.java)
            if (settings.configured && settings.notifications) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }

        /** The lasting "watching" notification; [stats] = (counts, limit) for the lasting one with numbers. */
        fun watching(context: Context, stats: Pair<String, String>?): Notification {
            val builder = NotificationCompat.Builder(context, CHANNEL_WATCH)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(largeIcon(context))
                .setContentIntent(openApp(context, null))
                .setDeleteIntent(WatchDismissedReceiver.intent(context))
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_MIN)
            if (stats == null) {
                builder.setContentTitle(context.getString(R.string.watch_title))
                    .setContentText(context.getString(R.string.watch_text))
            } else {
                builder.setContentTitle(stats.second).setContentText(stats.first)
            }
            return builder.build()
        }

        /** The app's launcher icon as the notification's large icon (the small one is only a white glyph). */
        fun largeIcon(context: Context): android.graphics.Bitmap? =
            ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.let { icon ->
                val size = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
                androidx.core.graphics.drawable.DrawableCompat.wrap(icon).toBitmap(size, size)
            }

        fun channels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(listOf(
                NotificationChannel(CHANNEL_APPROVALS, context.getString(R.string.channel_approvals), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_NEEDS_YOU, context.getString(R.string.channel_needs_you), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_WATCH, context.getString(R.string.channel_watch), NotificationManager.IMPORTANCE_MIN),
            ))
        }

        /** The app, on a conversation when [conversationId] is given. */
        fun openApp(context: Context, conversationId: Int?): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            conversationId?.let { intent.putExtra(EXTRA_CONVERSATION, it) }
            return PendingIntent.getActivity(context, conversationId ?: 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun post(context: Context, alert: Alert) {
            val builder = when (alert) {
                is Alert.Approval -> {
                    val p = alert.permission
                    // On the lock screen only that an approval waits — not the command.
                    val hidden = NotificationCompat.Builder(context, CHANNEL_APPROVALS)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(context.getString(R.string.approval_waiting))
                        .build()
                    val builder = NotificationCompat.Builder(context, CHANNEL_APPROVALS)
                        .setContentTitle(context.getString(R.string.approval_title, p.conversation ?: "Claude", p.tool))
                        .setContentText(p.input.values.firstOrNull()?.toString()?.trim('"')?.take(200).orEmpty())
                        .setContentIntent(openApp(context, p.conversationId))
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                        .setPublicVersion(hidden)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    // Allow / Deny live in the full notification only: on a locked screen the public version has no buttons.
                    // (setAuthenticationRequired(true) is left out: it makes the shade close after every tap.)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        listOf(R.string.allow to "allow", R.string.deny to "deny").forEach { (label, decision) ->
                            builder.addAction(
                                NotificationCompat.Action.Builder(0, context.getString(label),
                                    ApprovalReceiver.intent(context, p.id, decision, alert.key))
                                    .build(),
                            )
                        }
                    }
                    builder
                }
                is Alert.Update -> NotificationCompat.Builder(context, CHANNEL_NEEDS_YOU)
                    .setContentTitle(context.getString(R.string.update_notification_title))
                    .setContentText(context.getString(R.string.update_notification_text, alert.behind))
                    .setContentIntent(openApp(context, null))
                    .setAutoCancel(true)
                is Alert.NeedsYou -> NotificationCompat.Builder(context, CHANNEL_NEEDS_YOU)
                    .setContentTitle(alert.waiting.name)
                    .setContentText(when (alert.waiting.kind) {
                        "done" -> context.getString(R.string.waiting_done)
                        "error" -> context.getString(R.string.waiting_error)
                        "limit" -> context.getString(R.string.waiting_limit)
                        else -> alert.waiting.label
                    })
                    .setContentIntent(openApp(context, alert.waiting.conversationId))
                    .setAutoCancel(true)
            }
            val notification: Notification = builder.setSmallIcon(R.drawable.ic_notification).setLargeIcon(largeIcon(context)).build()
            try {
                NotificationManagerCompat.from(context).notify(alert.key, ALERT_ID, notification)
            } catch (e: SecurityException) {
                // Notifications aren't allowed (Android 13+): nothing to show.
            }
        }
    }
}
