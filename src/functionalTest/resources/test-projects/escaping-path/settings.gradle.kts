plugins {
    id("org.jetbrains.kotlinx.artifacts-validator-plugin")
}

extensions.configure<kotlinx.validation.ArtifactsValidatorPluginSettingsExtension>("artifactsValidation") {
    dumpFileRootDirectory.set(layout.rootDirectory.dir("../../../../expected"))
}

rootProject.name = "basic-test-project"
