package fr.refusbot.core

/**
 * Applications à risque élevé pour une automatisation d'accessibilité.
 *
 * Cette liste est volontairement conservatrice et sert uniquement à les exclure de l'action
 * groupée « Tout cocher ». L'utilisateur garde toujours la possibilité de les sélectionner
 * individuellement après avoir évalué le risque.
 */
object SensitiveAppPolicy {
    private val exactPackages = setOf(
        "com.n26",
        "com.epicgames.fortnite",
        "com.activision.callofduty.shooter",
        "com.supercell.brawlstars",
        "com.google.android.apps.walletnfcrel",
        "com.google.android.apps.authenticator2",
        "com.microsoft.authenticator",
        "com.authy.authy",
        "com.google.android.passwordmanager",
        "com.android.settings",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.android.systemui",
    )

    private val packageMarkers = listOf(
        "bank", "banking", "wallet", "pay", "payment", "authenticator",
        "password", "passwordmanager", "otp", "2fa", "mfa",
        "impots", "ameli", "caf",
        // Audit du 21/11 (n° 6) : banques, paiement et santé françaises dont le nom technique ne
        // contient ni « bank » ni « pay ».
        "banque", "bourso", "creditagricole", "bnpparibas", "societegenerale", "lcl", "caisseepargne",
        "creditmutuel", "fortuneo", "hellobank", "revolut", "lydia", "qonto", "nickel", "epargne",
        "mutuelle", "doctolib", "sante",
    )

    fun isSensitivePackage(packageName: String): Boolean {
        val normalized = packageName.trim().lowercase()
        if (normalized.isEmpty()) return false
        return normalized in exactPackages || packageMarkers.any { marker ->
            normalized.split('.').any { segment -> segment.contains(marker) }
        }
    }

    fun safeForBulkSelection(packageNames: Collection<String>): List<String> =
        packageNames.filterNot(::isSensitivePackage)
}
