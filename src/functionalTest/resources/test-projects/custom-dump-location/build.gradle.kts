plugins {
    base
    `maven-publish`
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
        create<org.gradle.api.publish.maven.MavenPublication>("test") {
            artifactId = "basic-test-project"
            artifact(publishedJar)
        }
    }
}

artifactsValidation {
    dumpFileNamePrefix.set("custom-artifacts")
    dumpFileRootDirectory.set(rootDir.resolve("expected"))
}
