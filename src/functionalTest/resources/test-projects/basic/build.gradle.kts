plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.kotlinx.artifacts-validator-plugin")
}

group = "org.jetbrains.kotlinx"
version = "0.0.1"

kotlin {
    jvmToolchain(17)
}
