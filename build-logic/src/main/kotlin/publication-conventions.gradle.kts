plugins {
    `maven-publish`
    signing
}

@Suppress("UnstableApiUsage")
publishing {
    publications {
        repositories {
            maven {
                name = "buildLocal"
                setUrl(project.layout.buildDirectory.dir("repo"))
            }

            val repositoryUrl = project.getSensitiveProperty("libs.repo.url")
            if (!repositoryUrl.isNullOrBlank()) {
                maven {
                    url = uri(repositoryUrl)
                    credentials {
                        username = project.getSensitiveProperty("libs.repo.user")
                        password = project.getSensitiveProperty("libs.repo.password")
                    }
                }
            }
        }

        withType<MavenPublication>().configureEach {
            signPublicationIfKeyPresent(project, this)

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

fun signPublicationIfKeyPresent(project: Project, publication: MavenPublication) {
    val keyId = project.getSensitiveProperty("libs.sign.key.id")
    val signingKey = project.getSensitiveProperty("libs.sign.key.private")
    val signingKeyPassphrase = project.getSensitiveProperty("libs.sign.passphrase")
    if (!signingKey.isNullOrBlank()) {
        project.extensions.configure<SigningExtension>("signing") {
            useInMemoryPgpKeys(keyId, signingKey, signingKeyPassphrase)
            sign(publication)

            // Temporary workaround, see https://github.com/gradle/gradle/issues/26091#issuecomment-1722947958
            tasks.withType<AbstractPublishToMaven>().configureEach {
                val signingTasks = tasks.withType<Sign>()
                mustRunAfter(signingTasks)
            }
        }
    } else {
        signing.isRequired = false
    }
}

fun Project.getSensitiveProperty(name: String): String? {
    return project.findProperty(name) as? String ?: System.getenv(name)
}
