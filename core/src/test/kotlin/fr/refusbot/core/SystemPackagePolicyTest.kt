package fr.refusbot.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPackagePolicyTest {
    @Test fun android_settings_and_system_surfaces_are_ignored() {
        assertTrue(SystemPackagePolicy.shouldIgnore("com.android.settings"))
        assertTrue(SystemPackagePolicy.shouldIgnore("com.android.systemui"))
        assertTrue(SystemPackagePolicy.shouldIgnore("com.google.android.permissioncontroller"))
    }

    @Test fun samsung_launcher_is_ignored() {
        assertTrue(SystemPackagePolicy.shouldIgnore("com.sec.android.app.launcher"))
    }

    @Test fun ordinary_preinstalled_apps_are_not_blanket_excluded() {
        assertFalse(SystemPackagePolicy.shouldIgnore("com.samsung.android.email.provider"))
        assertFalse(SystemPackagePolicy.shouldIgnore("com.google.android.apps.chrome"))
    }

    @Test fun system_and_own_app_are_never_monitorable() {
        assertFalse(SystemPackagePolicy.isMonitorable("com.android.settings", "fr.cyrava"))
        assertFalse(SystemPackagePolicy.isMonitorable("fr.cyrava", "fr.cyrava"))
        assertFalse(SystemPackagePolicy.isMonitorable("com.samsung.android.lool", "fr.cyrava"))
        assertTrue(SystemPackagePolicy.isMonitorable("com.google.android.apps.chrome", "fr.cyrava"))
    }

    @Test fun package_matching_ignores_case_and_whitespace() {
        assertTrue(SystemPackagePolicy.shouldIgnore(" COM.ANDROID.SETTINGS "))
    }
}
