rootProject.name = "artifacts-validator-plugin"

pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2")
    }
    includeBuild("build-logic")
    plugins {
        kotlin("jvm") version embeddedKotlinVersion
    }
}

plugins {
    id("org.jetbrains.kotlinx.artifacts-validator-plugin") version "0.0.2"
}
