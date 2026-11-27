import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Module Kotlin pur : aucune dépendance Android → logique testable sur la JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    // Les tests utilisent les vrais fichiers de règles et le catalogue CMP de l'appli.
    val rulesFile = rootProject.file("app/src/main/assets/rules.json")
    val cmpCatalogFile = rootProject.file("app/src/main/assets/cmp-catalog.json")
    systemProperty("refusbot.rules", rulesFile.absolutePath)
    systemProperty("refusbot.cmp.catalog", cmpCatalogFile.absolutePath)
    inputs.file(rulesFile)
    inputs.file(cmpCatalogFile)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
