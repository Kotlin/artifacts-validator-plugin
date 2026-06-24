import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.kotlin.dsl.signing
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension

plugins {
    base
    `maven-publish`
    signing
}

group = "org.jetbrains.kotlinx"
version = "0.0.1"

val localTestRepository = rootProject.layout.buildDirectory.dir("test-repo")
val publishedJar = tasks.register<org.gradle.api.tasks.bundling.Jar>("publishedJar") {
    archiveBaseName.set("basic-test-project")
}

publishing {
    repositories {
        maven {
            name = "test"
            url = uri(localTestRepository)
        }
    }
    publications {
        val pub = create<org.gradle.api.publish.maven.MavenPublication>("test") {
            artifactId = "basic-test-project"
            artifact(publishedJar)
        }
        project.extensions.configure<SigningExtension>("signing") {
            useInMemoryPgpKeys("0000000000000000", "NOT A KEY", "password")
            sign(pub)

            // Temporary workaround, see https://github.com/gradle/gradle/issues/26091#issuecomment-1722947958
            tasks.withType<AbstractPublishToMaven>().configureEach {
                val signingTasks = tasks.withType<Sign>()
                mustRunAfter(signingTasks)
            }
        }
    }
}


