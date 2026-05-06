plugins {
    base
    `maven-publish`
}

group = "org.jetbrains.kotlinx"
version = "0.0.1"

artifactsValidation {
    usePerProjectDumps.set(true)
}

subprojects {
    apply(plugin = "base")
    apply(plugin = "maven-publish")

    group = rootProject.group
    version = rootProject.version

    val localTestRepository = rootProject.layout.buildDirectory.dir("test-repo")
    val publishedJar = tasks.register<org.gradle.api.tasks.bundling.Jar>("publishedJar") {
        archiveBaseName.set(name)
    }

    extensions.configure<org.gradle.api.publish.PublishingExtension>("publishing") {
        repositories {
            maven {
                name = "test"
                url = uri(localTestRepository)
            }
        }
        publications {
            create<org.gradle.api.publish.maven.MavenPublication>("test") {
                artifactId = project.name
                artifact(publishedJar)
            }
        }
    }
}
