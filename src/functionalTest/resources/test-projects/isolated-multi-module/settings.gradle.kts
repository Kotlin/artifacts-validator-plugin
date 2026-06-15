plugins {
    id("org.jetbrains.kotlinx.artifacts-validator-plugin")
}

rootProject.name = "isolated-multi-module"

include(":lib")
include(":ext")
