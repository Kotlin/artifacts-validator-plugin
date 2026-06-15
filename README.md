# Artifacts validator Gradle plugin

[![Kotlin Alpha](https://kotl.in/badges/alpha.svg)](https://kotlinlang.org/docs/components-stability.html)
[![JetBrains incubator project](https://jb.gg/badges/incubator.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![GitHub license](https://img.shields.io/github/license/kotlin/kotlinx-artifacts-validator-plugin)](LICENSE)
[![Download](https://img.shields.io/maven-central/v/org.jetbrains.kotlinx/kotlinx-artifacts-validator-plugin)](https://central.sonatype.com/artifact/org.jetbrains.kotlinx/kotlinx-artifacts-validator-plugin/)

## Introduction

The artifacts validator plugin checks that a project publishes exactly the Maven artifacts you expect.

It supports two related workflows:

- Validate the publications declared in the current Gradle build without building the artifacts themselves.
- Validate the contents of a standalone local Maven repository containing built and published artifacts.

## Requirements

- Minimum supported Gradle version: `8.5`

## Applying the plugin

This is a settings plugin, so apply it in `settings.gradle.kts`:

```kotlin
plugins {
    id("org.jetbrains.kotlinx.artifacts-validator-plugin") version "0.0.1"
}
```

Projects whose publications should be tracked must apply `maven-publish` (only Maven publications are checked).

## Registered tasks

The plugin registers these tasks in each and every project:

- `checkArtifacts`: validates current `maven-publish` publications against artifact rule files.
- `dumpArtifacts`: generates artifact dump files from current `maven-publish` publications.

And it also registers the following task to the root project:

- `validateLocalMavenRepo`: validates an arbitrary local Maven repository against rule files.


## Settings extension

The plugin adds an `artifactsValidation` extension to Gradle settings. 

If you use Gradle 8.8 or newer, you can access it in `settings.gradle.kts` using a type safe accessor:
```kotlin
artifactsValidation {
    dumpFileRootDirectory.set(layout.rootDirectory.dir("artifacts"))
}
```

For older Gradle versions, a more verbose syntax is required:
```kotlin
extensions.configure<kotlinx.validation.ArtifactsValidatorPluginSettingsExtension>("artifactsValidation") {
    dumpFileRootDirectory.set(layout.rootDirectory.dir("artifacts"))
}
```

Supported properties:

- `dumpFileRootDirectory`: directory where dump files are stored. Default: `artifacts`

Depending on these settings, files containing lists of artifacts will have the following names:

```text
<dumpFileRootDirectory>/<project-name>.txt
```

## Typical workflow

1. Apply the plugin in `settings.gradle.kts`.
2. Configure your publications with `maven-publish`.
3. Generate expected artifact dumps with `./gradlew dumpArtifacts`.
4. Commit the generated dump files.
5. Run `./gradlew checkArtifacts` to ensure publications still match.

## Using `checkArtifacts`

`checkArtifacts` compares the publications currently declared in the build to the configured dump files.

Example:

Configure Maven publications using [maven-publish](https://docs.gradle.org/current/userguide/publishing_maven.html)
plugin.

Then run:

```bash
./gradlew checkArtifacts
```

## Using `dumpArtifacts`

`dumpArtifacts` writes rule files describing the publications found in the current build.

With default settings it writes:

```text
artifacts/<project-name>.txt
```

## Using `validateLocalMavenRepo`

`validateLocalMavenRepo` scans a Maven repository directory or ZIP archive and compares its contents to one or more rule files.

Basic example:

```bash
./gradlew validateLocalMavenRepo \
  --artifacts-dir=build/test-repo \
  --artifacts-list=artifacts/artifacts.txt:0.0.1
```

ZIP input works as well:

```bash
./gradlew validateLocalMavenRepo \
  --artifacts-zip=build/test-repo.zip \
  --artifacts-list=artifacts/artifacts.txt:0.0.1
```

Multiple rule files with independent expected versions are supported:

```bash
./gradlew validateLocalMavenRepo \
  --artifacts-dir=build/test-repo \
  --artifacts-list=artifacts/artifacts-core.txt:0.0.1 \
  --artifacts-list=artifacts/artifacts-ext.txt:2025a-0.0.1
```

For multi-module projects where artifacts share the same version,
you can specify a path to a directory with artifacts lists instead of individual files:

```bash
./gradlew validateLocalMavenRepo \
  --artifacts-dir=build/test-repo \
  --artifacts-list=artifacts/:0.0.1
```

Additional options:

- `--require-signatures`: require an `.asc` file for every artifact
- `--require-checksums=MD5,SHA1,SHA256,SHA512`: require checksum files for the listed algorithms
- exactly one of `--artifacts-dir` or `--artifacts-zip` must be supplied

You can inspect the full CLI help with:

```bash
./gradlew -q help --task validateLocalMavenRepo
```

## Artifact rules format

Artifact rule files describe expected artifacts using lines in this format:

```text
<groupId>:<artifactId>/<comma separated list of classifier.extension pairs>
```

Examples:

```text
org.jetbrains.kotlinx:kotlinx-io-core/.jar,.pom,.module,sources.jar,javadoc.jar,kotlin-tooling-metadata.json
```

Equivalent verbose form:

```text
org.jetbrains.kotlinx:kotlinx-io-core/.jar
org.jetbrains.kotlinx:kotlinx-io-core/.pom
org.jetbrains.kotlinx:kotlinx-io-core/.module
org.jetbrains.kotlinx:kotlinx-io-core/sources.jar
org.jetbrains.kotlinx:kotlinx-io-core/javadoc.jar
org.jetbrains.kotlinx:kotlinx-io-core/kotlin-tooling-metadata.json
```

If an artifact has no classifier, the classifier part is empty, such as `.jar` or `.pom`.

See [Maven artifact properties](https://maven.apache.org/repositories/artifacts.html#artifact-properties) for the terminology.

## Validation behavior

`checkArtifacts`:

- Reads configured dump files.
- Collects artifacts declared by current `maven-publish` publications.
- Fails if expected and actual artifact sets differ.

`validateLocalMavenRepo`:

- Reads all provided rule files.
- Associates each rule file with the version supplied in `--artifacts-list=<file>:<version>`.
- Scans the target repository.
- Fails if the repository contains invalid Maven-layout files.
- Fails if expected and actual artifact sets differ.
- Optionally verifies presence of signature and checksum sidecar files.

Signature and checksum validation check only file presence, not file contents.
