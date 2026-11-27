package fr.refusbot.core

/**
 * Paquets système dont l'interface ne doit jamais être traitée comme une fenêtre de consentement.
 *
 * Liste volontairement limitée aux surfaces système et lanceurs connus : ne pas exclure en bloc
 * toutes les applications système, car certaines applis préinstallées affichent de vrais CMP.
 */
object SystemPackagePolicy {
    private val ignoredPackages = setOf(
        "com.android.settings",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.samsung.android.permissioncontroller",
        "com.samsung.android.lool",
        "com.samsung.android.settings",
        "com.sec.android.app.launcher",
        "com.samsung.android.app.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
    )

    fun shouldIgnore(packageName: String): Boolean =
        packageName.trim().lowercase() in ignoredPackages

    /** RefusBot lui-même et les surfaces système ne sont jamais surveillables. */
    fun isMonitorable(packageName: String, ownPackageName: String): Boolean {
        val normalized = packageName.trim().lowercase()
        return normalized.isNotEmpty() &&
            normalized != ownPackageName.trim().lowercase() &&
            normalized !in ignoredPackages
    }
}
