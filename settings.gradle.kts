rootProject.name = "artifacts-validator-plugin"

pluginManagement {
    includeBuild("build-logic")
    plugins {
        kotlin("jvm") version embeddedKotlinVersion
    }
}
