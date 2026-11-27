package fr.refusbot.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fr.refusbot.MainActivity
import fr.refusbot.R
import fr.refusbot.ui.summaryText
import fr.refusbot.core.OutcomeStatus
import fr.refusbot.data.LogEntry
import fr.refusbot.data.NotifyLevel

object Notifier {
    private const val CHANNEL_REFUSALS = "refus"
    private const val CHANNEL_ALERTS = "alertes"
    private const val ID_SERVICE_STOPPED = 0x5E5
    private const val ID_PROGRESS = 0x5E6

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REFUSALS,
                context.getString(R.string.channel_refusals_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.channel_refusals_description) },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.channel_alerts_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.channel_alerts_description) },
        )
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Notification après une fenêtre traitée, selon le niveau choisi. Le titre ne parle de refus
     * vérifié que si le moteur a observé les interrupteurs désactivés (statut OPTIONS_SUCCESS) ; un
     * simple clic suivi d'une fermeture est annoncé comme « non vérifiable ».
     */
    fun notifyOutcome(context: Context, entry: LogEntry, level: NotifyLevel, diagnosticSaved: Boolean) {
        val show = when (level) {
            NotifyLevel.TOUTES -> true
            NotifyLevel.ECHECS -> entry.status == OutcomeStatus.PARTIAL || entry.status == OutcomeStatus.INCOMPLETE_CAPTURE ||
                entry.status == OutcomeStatus.IMPOSSIBLE || entry.status == OutcomeStatus.HANDED_OVER
            NotifyLevel.INTERRUPTIONS -> entry.status == OutcomeStatus.INTERRUPTED || entry.interfaceChanged
            NotifyLevel.AUCUNE -> false
        }
        if (!show) return
        val app = entry.appName
        val res = context.resources
        val (title, alert) = when (entry.status) {
            OutcomeStatus.OPTIONS_SUCCESS -> res.getString(R.string.ntf_options, app) to false
            OutcomeStatus.UNVERIFIED, OutcomeStatus.DIRECT_SUCCESS -> res.getString(R.string.ntf_unverified, app) to false
            OutcomeStatus.PARTIAL -> res.getString(R.string.ntf_partial, app) to true
            OutcomeStatus.INCOMPLETE_CAPTURE -> res.getString(R.string.ntf_capture, app) to true
            // Audit M5 : un mur payant est un choix du site, pas un échec de CYRAVA.
            OutcomeStatus.IMPOSSIBLE -> if (fr.refusbot.core.ConsentOrPay.isPaywall(entry.reason)) {
                res.getString(R.string.ntf_paywall, app) to false
            } else {
                res.getString(R.string.ntf_impossible, app) to true
            }
            OutcomeStatus.INTERRUPTED -> res.getString(R.string.ntf_interrupted, app) to true
            OutcomeStatus.OBSERVED -> res.getString(R.string.ntf_observed, app) to entry.interfaceChanged
            OutcomeStatus.HANDED_OVER -> res.getString(R.string.ntf_handed, app) to true
        }
        val text = buildString {
            append(entry.summaryText(res))
            if (entry.platform != "inconnue") append(" (").append(entry.platform).append(')')
            // Choix non vérifiés : dire clairement quoi faire, sans laisser croire que tout est refusé.
            when (entry.status) {
                OutcomeStatus.HANDED_OVER -> append(res.getString(R.string.ntf_handed_hint))
                OutcomeStatus.PARTIAL -> append(res.getString(R.string.ntf_partial_hint))
                else -> Unit
            }
            if (diagnosticSaved) append(res.getString(R.string.ntf_diag))
        }
        post(
            context,
            id = entry.packageName.hashCode() xor if (alert) 0x5A5A else 0,
            notification = NotificationCompat.Builder(context, if (alert) CHANNEL_ALERTS else CHANNEL_REFUSALS)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(if (alert) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .apply { if (!alert) setTimeoutAfter(60_000) }
                .setContentIntent(
                    openApp(context, if (diagnosticSaved) MainActivity.TAB_DIAGNOSTIC else MainActivity.TAB_HISTORY),
                )
                .build(),
        )
    }

    /**
     * Longue liste en cours (21/11 au soir) : « 120 sur 480 choix traités », pour que l'utilisateur sache
     * que CYRAVA travaille et ne termine pas la fenêtre à la main. Silencieuse, mise à jour sur place,
     * retirée à la fin de la session. Respecte le niveau « Aucune ».
     */
    fun notifyProgress(context: Context, done: Int, total: Int, level: NotifyLevel) {
        if (level == NotifyLevel.AUCUNE || total <= 0) return
        post(
            context,
            id = ID_PROGRESS,
            notification = NotificationCompat.Builder(context, CHANNEL_REFUSALS)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(context.getString(R.string.ntf_progress_title))
                .setContentText(context.getString(R.string.ntf_progress_text, done.coerceAtMost(total), total))
                .setProgress(total, done.coerceAtMost(total), false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setOngoing(true)
                .setTimeoutAfter(120_000)
                .build(),
        )
    }

    fun cancelProgress(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_PROGRESS)
    }

    /** Le service s'est arrêté alors que la protection était active (sauf niveau « Aucune »). */
    fun notifyServiceStopped(context: Context, level: NotifyLevel) {
        if (level == NotifyLevel.AUCUNE) return
        val text = context.getString(R.string.ntf_stopped_text)
        post(
            context,
            id = ID_SERVICE_STOPPED,
            notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(context.getString(R.string.ntf_stopped_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openApp(context, MainActivity.TAB_HOME))
                .build(),
        )
    }

    /** Le service est reparti : l'alerte « protection arrêtée » n'a plus lieu d'être. */
    fun cancelServiceStopped(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_SERVICE_STOPPED)
    }

    @SuppressLint("MissingPermission") // Permission vérifiée par canPost().
    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission retirée entre-temps : on ignore.
        }
    }

    private fun openApp(context: Context, tab: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TAB, tab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            tab.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
