package fr.refusbot.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String, val system: Boolean = false)

/**
 * Applis visibles grâce à la balise <queries> (intent MAIN/LAUNCHER) du manifeste :
 * pas de QUERY_ALL_PACKAGES.
 */
object AppsRepository {
    // Cache borné en octets (pas en nombre d'icônes). Les icônes sont normalisées à 96×96 px
    // (~36 Ko chacune) : 12 Mo ≈ 340 icônes, largement de quoi couvrir l'écran de sélection
    // sans rechargements perceptibles, avec une borne mémoire explicite quelle que soit la densité.
    private val iconCache = object : LruCache<String, ImageBitmap>(MAX_ICON_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4 // ARGB_8888
    }

    suspend fun launchableApps(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        infos.asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                val flags = it.activityInfo.applicationInfo.flags
                InstalledApp(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(pm).toString(),
                    system = flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    fun label(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
        pm.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    fun cachedIcon(packageName: String): ImageBitmap? = iconCache.get(packageName)

    suspend fun icon(context: Context, packageName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        iconCache.get(packageName) ?: try {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(ICON_PX, ICON_PX)
                .asImageBitmap()
                .also { iconCache.put(packageName, it) }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private const val ICON_PX = 96
    private const val MAX_ICON_CACHE_BYTES = 12 * 1024 * 1024
}
