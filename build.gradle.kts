@file:Suppress("UnstableApiUsage")

import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.attributes.TestSuiteType.FUNCTIONAL_TEST
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    id("publication-conventions")
    alias(libs.plugins.gradle.publish.plugin)
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.binary.compatibility.validator)
}

group = "org.jetbrains.kotlinx"
properties["DeployVersion"]?.let { version = it }

repositories {
    mavenCentral()
}

configurations.create("functionalTestImplementation")

dependencies {
    compileOnly(gradleApi())
    testImplementation(kotlin("test"))
    "functionalTestImplementation"(kotlin("test"))
    "functionalTestImplementation"(kotlin("test-junit5"))
    "functionalTestImplementation"(gradleApi())
    "functionalTestImplementation"(gradleTestKit())
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    explicitApi()

    jvmToolchain(21)

    compilerOptions {
        // See https://docs.gradle.org/current/userguide/compatibility.html#kotlin
        languageVersion.set(KotlinVersion.KOTLIN_1_8)
        apiVersion.set(KotlinVersion.KOTLIN_1_8)
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

testing {
    suites {
        withType<JvmTestSuite>().configureEach {
            useJUnitJupiter()
        }

        val functionalTest by creating(JvmTestSuite::class) {
            testType.set(FUNCTIONAL_TEST)
        }

        gradlePlugin.testSourceSets(functionalTest.sources)

        tasks.check {
            dependsOn(functionalTest)
        }
    }
}

gradlePlugin {
    website = "https://github.com/Kotlin/" // TBD
    vcsUrl = "https://github.com/Kotlin/" // TBD

    plugins.configureEach {
        tags.addAll("maven", "maven-publish", "artifacts", "check")
    }

    plugins {
        create("artifacts-validator-plugin") {
            id = "org.jetbrains.kotlinx.artifacts-validator-plugin"
            implementationClass = "kotlinx.validation.ArtifactsValidatorPlugin"
            displayName = "Maven artifacts validator plugin"
            description =
                "Runs pre-publication checks on artifacts published to a local M2 repository"
        }
    }
}

kover {
    reports {
        filters {
            excludes {
                packages("kotlinx.validation.test")
            }
        }

        verify {
            rule {
                minBound(95, CoverageUnit.BRANCH)
                minBound(95, CoverageUnit.LINE)
            }
        }
    }
}
