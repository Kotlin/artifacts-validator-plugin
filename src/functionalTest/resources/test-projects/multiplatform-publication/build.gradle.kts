plugins {
    kotlin("multiplatform")
    `maven-publish`
}

group = "org.jetbrains.kotlinx"
version = "0.0.1"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    js {
        nodejs()
    }
    linuxX64()
}

val localTestRepository = rootProject.layout.buildDirectory.dir("test-repo")

publishing {
    repositories {
        maven {
            name = "test"
            url = uri(localTestRepository)
        }
    }
}
