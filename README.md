# Generate marvelous aerial view of your project !

[![All Contributors](https://img.shields.io/github/all-contributors/jtama/project-graph-generator?color=ee8449&style=flat-square)](#contributors)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.jtama/project-graph-generator.svg?label=Maven%20Central)](https://search.maven.org/artifact/io.github.jtama/project-graph-generator) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) [![Build](https://github.com/jtama/project-graph-generator/workflows/EarlyAccess/badge.svg)](https://github.com/jtama/project-graph-generator/actions?query=workflow%3AEarlyAccess)

![Small previex](images/project-graph-preview.gif)

## Available options

None of the following are mandatory.

* **`maxNodes`**: The maximum number of nodes in the final graph. Will drop the nodes with less weight
* **`basePackages`**: A list of semicolon separated package names included in the scan.
* **`includeTests`**: Whether the test code should be included in the scan or not.
* **`generateHTMLView`**: Whether the recipe should generate an HTML result.

This recipe is also able to output its result using [OpenRewrite's data tables](https://docs.openrewrite.org/authoring-recipes/data-tables#step-1-enable-data-table-functionality). If enabled it will produce on csv file
with the graph nodes, and one with the links.

## Smart default

Here are the following default values : 

* **`maxNodes`**: No limit, all classes found are included in the final scan result
* **`basePackages`**: The project `groupId`
* **`includeTests`**: By default, test code is not scanned
* **`generateHTMLView`**: `true`, setting this to false only makes sense if data table export is enabled.


## How to execute

For early access builds, use artifact version ``

### With Maven

With default options

```console
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
-Drewrite.recipeArtifactCoordinates=io.github.jtama:project-graph-generator:1.0.2 \
-Drewrite.activeRecipes=io.github.jtama.openrewrite.ProjectAerialViewGenerator
```

With full options

```console
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
-Drewrite.recipeArtifactCoordinates=io.github.jtama:project-graph-generator:1.0.2 \
-Drewrite.activeRecipes=io.github.jtama.openrewrite.ProjectAerialViewGenerator \
-Drewrite.options=maxNodes=8,basePackages=com.foo:io.github.jtama,includeTests=true,generateHTMLView=false,includeTests=true \
-Drewrite.exportDatatables=true
```

### With Gradle

It's a bit more complicated.

1. First of all, create a file named `init.gradle` in the root of your project.

```groovy title="init.gradle"
initscript {
    repositories {
        maven { url "https://plugins.gradle.org/m2" }
    }
    dependencies { classpath("org.openrewrite:plugin:7.20.0") }
}
rootProject {
    plugins.apply(org.openrewrite.gradle.RewritePlugin)
    dependencies {
        rewrite("io.github.jtama:project-graph-generator:1.0.0")
    }
    rewrite {
        activeRecipe("io.github.jtama.openrewrite.ProjectAerialViewGenerator")
        setExportDatatables(true)
    }
    afterEvaluate {
        if (repositories.isEmpty()) {
            repositories {
                mavenCentral()
            }
        }
    }
}
```

2. Run the recipe with default values.

```shell title="shell"
gradle --init-script init.gradle rewriteRun
```

2. Run the recipe with full options

```console
gradle --init-script init.gradle rewriteRun -Drewrite.options=maxNodes=8,basePackages=com.foo
```

### Run with pre-release version

To try pre-release version use the `1.0.1-SNAPSHOT`

## Contributors ✨

Thanks goes to these wonderful people ([emoji key](https://allcontributors.org/docs/en/emoji-key)):

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/jtama"><img src="https://avatars0.githubusercontent.com/u/39991688?v=4?s=100" width="100px;" alt="jtama"/><br /><sub><b>jtama</b></sub></a><br /><a href="https://github.com/jtama/project-graph-generator/commits?author=jtama" title="Code">💻</a> <a href="#maintenance-jtama" title="Maintenance">🚧</a> <a href="https://github.com/jtama/project-graph-generator/commits?author=jtama" title="Documentation">📖</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://william-aventin.fr"><img src="https://avatars.githubusercontent.com/u/11073539?v=4?s=100" width="100px;" alt="William AVENTIN"/><br /><sub><b>William AVENTIN</b></sub></a><br /><a href="https://github.com/jtama/project-graph-generator/commits?author=Will33ELS" title="Documentation">📖</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/nicolasguilhem"><img src="https://avatars.githubusercontent.com/u/17413327?v=4?s=100" width="100px;" alt="nicolasguilhem"/><br /><sub><b>nicolasguilhem</b></sub></a><br /><a href="https://github.com/jtama/project-graph-generator/commits?author=nicolasguilhem" title="Documentation">📖</a></td>
    </tr>
  </tbody>
</table>

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

This project follows the [all-contributors](https://github.com/all-contributors/all-contributors) specification.
Contributions of any kind welcome!
