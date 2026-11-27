// Application de démonstration E2E : vrai formulaire Google UMP + CMP génériques.
plugins {
    alias(libs.plugins.android.application)
}

val defaultSampleAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val umpDemoTestDeviceHashedId = providers.gradleProperty("umpDemoTestDeviceHashedId").orNull
    ?: providers.environmentVariable("UMP_DEMO_TEST_DEVICE_HASHED_ID").orNull.orEmpty()
fun quotedBuildConfig(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val umpDemoAdMobAppId = providers.gradleProperty("umpDemoAdMobAppId").orNull
    ?.takeIf { it.isNotBlank() }
    ?: providers.environmentVariable("UMP_DEMO_ADMOB_APP_ID").orNull?.takeIf { it.isNotBlank() }
    ?: defaultSampleAdMobAppId

android {
    namespace = "fr.refusbot.demo"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "fr.refusbot.demo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["umpDemoAdMobAppId"] = umpDemoAdMobAppId
        buildConfigField("String", "UMP_DEMO_TEST_DEVICE_HASHED_ID", quotedBuildConfig(umpDemoTestDeviceHashedId))
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.google.ump)
}
