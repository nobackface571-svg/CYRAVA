package fr.refusbot.util

import fr.refusbot.R
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.StatusBarManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import fr.refusbot.service.RefusBotAccessibilityService

/** États et raccourcis système utilisés par l'écran d'accueil. */
object SystemStatus {

    fun serviceComponent(context: Context) = ComponentName(context, RefusBotAccessibilityService::class.java)

    /** Le service est-il coché dans les réglages d'accessibilité ? */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = serviceComponent(context)
        val setting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return setting.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Version installée de RefusBot (« ? » si elle est illisible). */
    @Suppress("DEPRECATION") // getPackageInfo(String, Int) : suffisant pour lire sa propre version.
    fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: PackageManager.NameNotFoundException) {
        "?"
    }

    /** Ouvre directement la page du service si possible, sinon la liste d'accessibilité. */
    fun openAccessibilitySettings(context: Context) {
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, serviceComponent(context).flattenToString())
        if (!start(context, details, silent = true)) {
            start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    /** « Infos sur l'appli » : menu ⋮ → « Autoriser les paramètres restreints ». */
    fun openAppDetails(context: Context) {
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }

    /** Demande d'exclusion de l'optimisation de batterie (indispensable sur Samsung). */
    @SuppressLint("BatteryLife") // Un service d'accessibilité tué par l'économie d'énergie ne sert à rien.
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", context.packageName, null))
        if (!start(context, direct, silent = true)) {
            start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    fun openNotificationSettings(context: Context) {
        start(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }

    /** Propose d'ajouter une tuile aux réglages rapides (Android 13+). */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun requestAddTile(activity: Activity, serviceClass: Class<*>, label: String, @DrawableRes icon: Int) {
        val sbm = activity.getSystemService(StatusBarManager::class.java) ?: return
        sbm.requestAddTileService(
            ComponentName(activity, serviceClass),
            label,
            Icon.createWithResource(activity, icon),
            ContextCompat.getMainExecutor(activity),
        ) { result ->
            val message = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> activity.getString(R.string.tile_added, label)
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> activity.getString(R.string.tile_present, label)
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> null
                else -> activity.getString(R.string.tile_manual)
            }
            message?.let { Toast.makeText(activity, it, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun start(context: Context, intent: Intent, silent: Boolean = false): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        if (!silent) Toast.makeText(context, context.getString(R.string.sys_screen_missing), Toast.LENGTH_LONG).show()
        false
    } catch (_: SecurityException) {
        if (!silent) Toast.makeText(context, context.getString(R.string.sys_screen_denied), Toast.LENGTH_LONG).show()
        false
    }
}
