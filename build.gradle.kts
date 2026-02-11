import org.gradle.api.attributes.TestSuiteType.FUNCTIONAL_TEST
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    signing
    `maven-publish`
    alias(libs.plugins.gradle.publish.plugin)
}

group = "org.jetbrains.kotlinx"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(gradleApi())
    testImplementation(kotlin("test"))
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

@Suppress("UnstableApiUsage")
testing {
    suites {
        withType<JvmTestSuite>().configureEach {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation(libs.kotlin.test)
                implementation(libs.kotlin.test.junit5)
            }
        }

        val functionalTest by creating(JvmTestSuite::class) {
            testType.set(FUNCTIONAL_TEST)

            dependencies {
                implementation(gradleApi())
                implementation(gradleTestKit())
            }
        }

        gradlePlugin.testSourceSets(functionalTest.sources)

        tasks.check {
            dependsOn(functionalTest)
        }
    }
}

@Suppress("UnstableApiUsage")
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

@Suppress("UnstableApiUsage")
publishing {
    publications {
        repositories {
            maven {
                name = "buildLocal"
                setUrl(project.layout.buildDirectory.dir("repo"))
            }
        }

        withType<MavenPublication>().configureEach {
            pom {
                name = project.name
                description = "Maven artifacts validator plugin"
                url = "https://github.com/Kotlin/" // TBD

                licenses {
                    license {
                        name = "Apache-2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                        distribution = "repo"
                    }
                }

                developers {
                    developer {
                        id = "JetBrains"
                        name = "JetBrains Team"
                        organization = "JetBrains"
                        organizationUrl = "https://www.jetbrains.com"
                    }
                }

                scm {
                    url = "https://github.com/Kotlin/" // TBD
                }
            }
        }
    }
}

signing {
    isRequired = false // TODO: configure later
}
