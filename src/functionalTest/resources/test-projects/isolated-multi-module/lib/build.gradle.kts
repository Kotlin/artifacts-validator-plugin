plugins {
    base
    `maven-publish`
}

group = "org.jetbrains.kotlinx"
version = "0.0.1"

val publishedJar = tasks.register<org.gradle.api.tasks.bundling.Jar>("publishedJar") {
    archiveBaseName.set("lib")
}

publishing {
    publications {
        create<org.gradle.api.publish.maven.MavenPublication>("test") {
            artifactId = "lib"
            artifact(publishedJar)
        }
    }
}
