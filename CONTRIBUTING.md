# Contributing Guidelines

One can contribute to the project by reporting issues or submitting changes via pull request.

## Reporting issues

Please use [GitHub issues](https://github.com/Kotlin/artifacts-validator-plugin/issues) for filing feature requests and bug reports.

Questions about usage and general inquiries are better suited for [discussions](https://github.com/Kotlin/artifacts-validator-plugin/discussions) or the #library-development channel in KotlinLang Slack.

## Submitting changes

Submit pull requests [here](https://github.com/Kotlin/artifacts-validator-plugin/pulls).
However, please keep in mind that maintainers will have to support the resulting code of the project,
so do familiarize yourself with the following guidelines.

* All development (both new features and bug fixes) is performed in the `main` branch.
    * The `main` branch contains the sources of the most recently released version.
    * Base your PRs against the `main` branch.
* If you make any code changes:
    * Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/reference/coding-conventions.html).
        * Use 4 spaces for indentation.
        * Use imports with '*'.
    * [Build the project](#building) to make sure it all works and passes the tests.
* If you fix a bug:
    * Write the test that reproduces the bug.
    * Fixes without tests are accepted only in exceptional circumstances if it can be shown that writing the
      corresponding test is too hard or otherwise impractical.
    * Follow the style of writing tests that is used in this project.
* Comment on the existing issue if you want to work on it. Ensure that the issue not only describes a problem, but also describes a solution that has received positive feedback. Propose a solution if none has been suggested.

## Building

This plugin is built with Gradle.

* Run `./gradlew build` to build. It also runs all the tests.
* Run `./gradlew check` to test the module you are looking at to speed
  things up during development.

You can import this project into IDEA, but you have to delegate build actions
to Gradle (in Preferences -> Build, Execution, Deployment -> Build Tools -> Gradle -> Build and run).

### Updating the public API dump

* Use the [Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator/blob/master/README.md) for updates to public API:
    * Run `./gradlew apiDump` to update API index files.
    * Commit the updated API indexes together with other changes.
