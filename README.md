# Artifacts validator Gradle plugin

[![Kotlin Alpha](https://kotl.in/badges/alpha.svg)](https://kotlinlang.org/docs/components-stability.html)
[![JetBrains incubator project](https://jb.gg/badges/incubator.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![GitHub license](https://img.shields.io/github/license/kotlin/kotlinx-artifacts-validator-plugin)](LICENSE)
[![Download](https://img.shields.io/maven-central/v/org.jetbrains.kotlinx/kotlinx-artifacts-validator-plugin)](https://central.sonatype.com/artifact/org.jetbrains.kotlinx/kotlinx-artifacts-validator-plugin/)

## Introduction

The Artifacts validator Gradle plugin tracks Maven artifacts published for a library.

Given a local Maven repository with all the artifacts generated for a library,
the plugin validates that the repository contains all expected artifacts and does not contain
any unexpected artifacts. List of expected artifacts is expressed using [artifact rules](#artifact-rules).

In addition to validating the list of artifacts,
the plugin can perform additional checks to ensure smooth publication to [the Central repository](https://central.sonatype.org/publish/publish-portal-guide/). 

## Requirements
Minimal supported Gradle version is **8.3**. To pass command line arguments to plugin, Gradle **8.5** or newer is needed.

## Configuring and using the plugin

The plugin could be enabled by adding a following code to a `build.gradle.kts` build script:
```kotlin
plugins {
    id("org.jetbrains.kotlinx.artifacts-validator-plugin") version "0.0.1"
}
```

Once added, the plugin registers the `validatePublication` task.

The task could be configured using the `artifactValidation` extension:
```kotlin
artifactsValidation {
    // Configure the location of a file with rules describing expected artifacts
    // and the expected version of these artifacts.
    // By default, the file is "gradle/artifacts.txt" and
    // the version is equal to project.version.
    // Alternatively, you can access the backing MapProperty, artifactLists.
    artifactsList(project.rootDir.resolve("artifacts.txt"), project.version)
    // By default, "build/repo". Local Maven repository with artifacts to validate.
    artifactsRepository = project.layout.buildDirectory.dir("repo")
    // Empty by default. When non-empty, the plugin will check existence of 
    // the corresponding checksum files for every artifact.
    requiredChecksums = setOf("MD5", "SHA1")
    // Disabled by default. When enabled, the plugin will check existence of
    // the PGP-signature file for every artifact.
    requireSignatures = true
    // Enabled by default. When disabled, the task will be skipped.
    enabled = true
    // Task provider, allow access the task. You can use to configure dependencies.
    task
}
```

For projects with complex publications, several artifacts lists with distinct versions could be configured using subsequence `artifactList` calls.

An [artifact rules](#artifact-rules) file could be either created manually, or generated from local repository contents by running `./gradlew validateLocalMavenRepo --dump`.

This command will use all the settings from `artifactValidation` extension, scan the configured Maven repository,
generate rules for all artifacts matching specified versions and dump it into the file.

In addition to the `--dump` option, `artifactValidation` properties has their own command line arguments.
You can learn more about them by running `./gradlew -q help --task validateLocalMavenRepo`.

## Artifact rules

To describe list of expected artifacts, the plugin use special artifacts rule files.
An artifact rule file is a text file where each line has the following syntax:
```
<groupId>:<artifactId>/<comma separated list of classifier.extension pairs> 
```

`groupId` and `artifactId` exactly what you expect them to be, Maven group and artifact identifiers.
`classifier` and `extension` are artifact's classifier (like `sources` or `javadoc`) and extension (like `pom`, `jar` or `klib`).
If an artifact has no classifier, then it should be empty.

Read [Maven Artifacts/Artifact Properties](https://maven.apache.org/repositories/artifacts.html#artifact-properties) to learn more about group and artifact IDs, classifier and extensions.

Here's an example of rule for [org.jetbrains.kotlinx:kotlinx-io-core:0.8.2](https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-io-core/0.8.2/):
```
org.jetbrains.kotlinx:kotlinx-io-core/.jar,.pom,.module,sources.jar,javadoc.jar,kotlin-tooling-metadata.json
```

The same rule could be also rewritten in a move verbose form:
```
org.jetbrains.kotlinx:kotlinx-io-core/.jar
org.jetbrains.kotlinx:kotlinx-io-core/.pom
org.jetbrains.kotlinx:kotlinx-io-core/.module
org.jetbrains.kotlinx:kotlinx-io-core/sources.jar
org.jetbrains.kotlinx:kotlinx-io-core/javadoc.jar
org.jetbrains.kotlinx:kotlinx-io-core/kotlin-tooling-metadata.json
```

Writing a rule file from scratch could be tedious, so instead it could be generated automatically by running `./gradlew validateLocalMavenRepo --dump`.

## Validation procedure

The plugin performs the following procedure to validate artifacts:
- Reads all rules from a file and associate them with corresponding version.
- Scan the repository.
  - Validation fails if the repository contains any invalid files (for example, those that do not conform [Maven repository layout](https://maven.apache.org/repository/layout.html)).
- Compares list of expected artifacts with list of artifacts found in the repository (the version is always taken into account).
  - If some artifacts listed in the rule files are not found in the repository, validation fails.
  - If repository contains any files not listed in the rule files, validation fails.
- If `requireSignature` property was set to `true` (or the task is executed with the `--require-signatures` command-line flag):
  - Check that every artifact has a corresponding `.asc` file.
  - It is not considered a validation error if the property was set to `false` and there are `.asc` files.
- If `requireChecksums` property is not empty (or the task is executed with the `--require-checksums` command-line option):
  - Check that every artifact has a corresponding checksum file (`.md5`, `.sha1`, etc., depending on the property's value).
  - It is not considered a validation error if there are checksum files for algorithms not listed in the property / command line option. 

Note that both `requireSignature` and `requireChecksums` validates only existence of special files, but not their contents.
