import java.security.KeyStore
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// --- Version : `cyrava.version` dans gradle.properties (seul endroit à changer, 22/11) ------------
// La CI peut la remplacer (VERSION_NAME) ; sans CI, la compilation locale ajoute « -local ».
val baseVersionName = providers.gradleProperty("cyrava.version").orNull ?: "1.1.0"
// Sans CI, versionCode = 1 : une compilation locale se réinstalle sur elle-même, mais jamais
// par-dessus un APK CodeMagic/Release (numéro plus grand = « plus récent » pour Android) ; il faut
// alors désinstaller d'abord. Un horodatage ici produirait l'inverse : plus aucun APK CodeMagic ne
// s'installerait par-dessus une compilation locale. Le code fixe est le défaut le moins surprenant.
val ciVersionCode = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull() ?: 1
val ciVersionName = providers.environmentVariable("VERSION_NAME").orNull ?: "$baseVersionName-local"

// --- Signature ------------------------------------------------------------------------------
// Deux cas, jamais mélangés :
// - APK de TEST (branches, émulateur, compilation locale) : signé avec la clé de test fournie par
//   CodeMagic (variables TEST_KEYSTORE_*), qui n'est PAS rangée dans le dépôt public. Sans ces
//   variables (compilation locale, émulateur GitHub), la clé « debug » générée par Android sur la
//   machine est utilisée. Une clé de test ne prouve pas qui a compilé l'APK ; la version porte
//   toujours un suffixe (« 1.1.0-dev.42 », « 1.1.0-local »).
// - PUBLICATION (numéro de version « x.y.z » ou REFUSBOT_PUBLICATION=true) : la clé personnelle
//   est OBLIGATOIRE et complète, sinon la compilation échoue. Jamais de Release signée avec la clé
//   de test, jamais de repli silencieux d'une clé vers l'autre.
val releaseKeystorePath = providers.environmentVariable("RELEASE_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
val testKeystorePath = providers.environmentVariable("TEST_KEYSTORE_PATH").orNull
val testStorePassword = providers.environmentVariable("TEST_KEYSTORE_PASSWORD").orNull
val testKeyAlias = providers.environmentVariable("TEST_KEY_ALIAS").orNull
val testKeyPassword = providers.environmentVariable("TEST_KEY_PASSWORD").orNull
val hasTestKey = !testKeystorePath.isNullOrBlank() && file(testKeystorePath).isFile &&
    !testStorePassword.isNullOrBlank() && !testKeyAlias.isNullOrBlank() && !testKeyPassword.isNullOrBlank()
// Clé « debug » qu'Android génère sur chaque machine (mot de passe et alias standard).
val machineDebugKeystore = File(System.getProperty("user.home"), ".android/debug.keystore")

fun certificateSha256(keystore: File, password: String, alias: String): String? {
    for (type in listOf("JKS", "PKCS12")) {
        try {
            val store = KeyStore.getInstance(type)
            keystore.inputStream().use { store.load(it, password.toCharArray()) }
            val certificate = store.getCertificate(alias) ?: continue
            return MessageDigest.getInstance("SHA-256")
                .digest(certificate.encoded)
                .joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            continue
        }
    }
    return null
}

val isPublication = providers.environmentVariable("REFUSBOT_PUBLICATION").orNull == "true" ||
    Regex("""\d+\.\d+\.\d+""").matches(ciVersionName)
val missingReleaseSettings = listOfNotNull(
    "RELEASE_KEYSTORE_PATH (fichier de clé)".takeIf { releaseKeystorePath.isNullOrBlank() || !file(releaseKeystorePath).isFile },
    "RELEASE_KEYSTORE_PASSWORD".takeIf { releaseStorePassword.isNullOrBlank() },
    "RELEASE_KEY_ALIAS".takeIf { releaseKeyAlias.isNullOrBlank() },
    "RELEASE_KEY_PASSWORD".takeIf { releaseKeyPassword.isNullOrBlank() },
)
val hasReleaseKey = missingReleaseSettings.isEmpty()
if (isPublication) {
    if (!hasReleaseKey) {
        throw GradleException(
            "Publication de la version $ciVersionName refusée : clé de signature personnelle absente ou incomplète " +
                "(${missingReleaseSettings.joinToString()}). Une version publiée n'est jamais signée avec la clé de test du dépôt.",
        )
    }
    val releaseFingerprint = certificateSha256(file(releaseKeystorePath!!), releaseStorePassword!!, releaseKeyAlias!!)
    val testFingerprints = listOfNotNull(
        if (hasTestKey) certificateSha256(file(testKeystorePath!!), testStorePassword!!, testKeyAlias!!) else null,
        if (machineDebugKeystore.isFile) certificateSha256(machineDebugKeystore, "android", "androiddebugkey") else null,
    )
    if (releaseFingerprint == null || releaseFingerprint in testFingerprints) {
        throw GradleException("Publication refusée : la clé fournie est une clé de test (ou son empreinte est illisible).")
    }
}

// Un numéro de version « propre » (sans suffixe de développement) qui n'est pas reconnu comme
// publication — par exemple « 1.1.0-beta.4 » : la regex x.y.z ne le couvre pas — ne doit JAMAIS
// être signé avec la clé de test : l'APK aurait l'apparence d'une version officielle sans en avoir
// la clé. Les canaux de développement suffixent toujours (« -local », « -ci.N », « -dev.N »).
val devSuffixes = listOf("-local", "-ci.", "-dev.")
if (!isPublication && devSuffixes.none { ciVersionName.contains(it) }) {
    throw GradleException(
        "Compilation refusée : la version « $ciVersionName » n'a ni le format d'une publication (x.y.z) " +
            "ni un suffixe de développement (${devSuffixes.joinToString()}). " +
            "Ajoutez un suffixe de développement, ou publiez en x.y.z avec la clé personnelle.",
    )
}

android {
    namespace = "fr.refusbot"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        // Identifiant public de l'appli (21/11) ; le code garde ses paquets Kotlin `fr.refusbot`.
        applicationId = "fr.cyrava"
        minSdk = 26
        targetSdk = 37
        versionCode = ciVersionCode
        versionName = ciVersionName
    }

    signingConfigs {
        getByName("debug") {
            if (hasTestKey) {
                storeFile = file(testKeystorePath!!)
                storePassword = testStorePassword
                keyAlias = testKeyAlias
                keyPassword = testKeyPassword
            }
            // Sinon : clé « debug » générée par Android sur la machine (réglage par défaut).
        }
        if (isPublication) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // APK de test (hors publication) : même clé que les APK « debug » (voir plus haut).
            signingConfig = signingConfigs.getByName(if (isPublication) "release" else "debug")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Bibliothèques déjà optimisées par AndroidX : inutile de tenter de les « stripper »
            // (supprime l'avertissement « Unable to strip » quand le NDK n'est pas installé).
            keepDebugSymbols += setOf("**/libandroidx.graphics.path.so", "**/libdatastore_shared_counter.so")
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Le rapport texte complet (app/build/reports/lint-results-release.txt) est affiché par la CI.
        // Avertissements « nouvelle version disponible » : informatifs, pas des erreurs.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
}
