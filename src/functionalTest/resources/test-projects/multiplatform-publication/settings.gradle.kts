pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        kotlin("multiplatform") version embeddedKotlinVersion
    }
}

plugins {
    id("org.jetbrains.kotlinx.artifacts-validator-plugin")
}

rootProject.name = "multiplatform-test-project"
