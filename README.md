# Generate marvelous aerial view of your project !

[![All Contributors](https://img.shields.io/github/all-contributors/jtama/project-graph-generator?color=ee8449&style=flat-square)](#contributors)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.jtama/project-graph-generator.svg?label=Maven%20Central)](https://search.maven.org/artifact/io.github.jtama/project-graph-generator) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) [![Build](https://github.com/jtama/project-graph-generator/workflows/EarlyAccess/badge.svg)](https://github.com/jtama/project-graph-generator/actions?query=workflow%3AEarlyAccess)

![Small previex](images/project-graph-preview.gif)

## Recipes

Two main recipes are available in this repository.

### `io.github.jtama.openrewrite.ProjectAerialViewGenerator`

Generates the internal dependency graph of your java project.
With multiple output formats, this recipe will help you get a *better* grasp of your internal dependencies.
Not the ones that were designed, but the ones that emerged over time.
By default this recipe generates a ***standalone*** html documents that will help you play with the produced graph.

#### Available options

None of the following are mandatory.

* **`maxNodes`**: The maximum number of nodes in the final graph. Will drop the nodes with less weight
* **`basePackages`**: A list of semicolon separated package names included in the scan.
* **`includeTests`**: Whether the test code should be included in the scan or not.
* **`generateHTMLView`**: Whether the recipe should generate an HTML result.

This recipe is also able to output its result using [OpenRewrite's data tables](https://docs.openrewrite.org/authoring-recipes/data-tables#step-1-enable-data-table-functionality). If enabled it will produce on csv file
with the graph nodes, and one with the links.

### `io.github.jtama.openrewrite.CountPublicMethodInvocations`

Find all the public methods in your code base and generates a report counting their invocations.
The recipe has a flag that allow you to only keep public methods without any invocations.

#### Available options

None of the following are mandatory.

* **`basePackage`**: The package name to be included in the scan.
* **`onlyUninvoked`**: Should we only keep method with 0 invocations.
* **`generateHTMLView`**: Whether the recipe should generate an HTML result.

This recipe is also able to output its result using [OpenRewrite's data tables](https://docs.openrewrite.org/authoring-recipes/data-tables#step-1-enable-data-table-functionality). If enabled it will produce on csv file
with the graph nodes, and one with the links.

### Smart defaults

Here are the following default values :

* **`maxNodes`**: No limit, all classes found are included in the final scan result
* **`basePackages`, `basePackage`**: The project `groupId` (for Maven projects) or `rootProject.name` (for Gradle projects)
* **`includeTests`**: By default, test code is not scanned
* **`onlyUninvoked`**: By default, `false`.
* **`generateHTMLView`**: `true`, setting this to false only makes sense if data table export is enabled.

## Execution & Configuration

### With Maven

With default options

```console
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
-Drewrite.recipeArtifactCoordinates=io.github.jtama:project-graph-generator:RELEASE \
-Drewrite.activeRecipes=io.github.jtama.openrewrite.ProjectAerialViewGenerator
```

With full options

```console
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
-Drewrite.recipeArtifactCoordinates=io.github.jtama:project-graph-generator:RELEASE \
-Drewrite.activeRecipes=io.github.jtama.openrewrite.ProjectAerialViewGenerator \
-Drewrite.options=maxNodes=8,basePackages=com.foo:io.github.jtama,includeTests=true,generateHTMLView=false,includeTests=true \
-Drewrite.exportDatatables=true
```

> You can also set the options using a `rewrite.yml` file [see below](#using-a-rewriteyml-file).

### With Gradle

<details>
<summary>It's a bit more complicated.</summary>

1. Configure your project

```groovy title="build.gradle"
plugins {
    // add OpenRewrite plugin
    id 'org.openrewrite.rewrite' version "7.20.0"
}
dependencies {
    // Add this project as a rewrite dependency to your project
    rewrite "io.github.jtama:project-graph-generator:RELEASE"
}

// Add OpenRewrite configuration
rewrite {
    // Activate the recipe of this project
    activeRecipe("io.github.jtama.openrewrite.ProjectAerialViewGenerator")
    setExportDatatables true
}
```

> More information on how to configure OpenRewrite plugin at [official documentation](https://docs.openrewrite.org/reference/gradle-plugin-configuration).

2. Run the recipe with default values.

```shell
gradle rewriteRun
```

> The `basePackages` option will default to the `rootProject.name` value which probably won't match any package in your project. You should set it explicitly as shown below.

3. Run the recipe with full options

The only way to pass options to the recipe with gradle plugin is to use a `rewrite.yml` file [see below](#using-a-rewriteyml-file).

> If you don't want to change your gradle build file for your project, you can activate the recipe using an init-script [see official documentation](https://docs.openrewrite.org/running-recipes/running-rewrite-on-a-gradle-project-without-modifying-the-build#step-2-create-a-gradle-init-script)

</details>

### Using a `rewrite.yml` file

Another way to set the options is to create a `rewrite.yml` file at the root of your project with the following content :

```yaml title="rewrite.yml"
type: specs.openrewrite.org/v1beta/recipe
name: com.yourorg.ProjectAerialViewGenerator # This is the name of your recipe to activate
displayName: Generate Project Aerial View
recipeList:  # This is the list of recipes to run
  - io.github.jtama.openrewrite.ProjectAerialViewGenerator:
      basePackages: "com.yourorg"
      includeTests: false
      generateHTMLView: true
  - io.github.jtama.openrewrite.CountPublicMethodInvocations:
      basePackages: "com.yourorg"
      onlyUninvoked: false
      generateHTMLView: true
```

> More information on YAML format reference on [the official documentation](https://docs.openrewrite.org/reference/yaml-format-reference)

## Data tables

If enabled the scan will produce data tables that you will find under the `target/rewrite/datatables` folder: 

`io.github.jtama.openrewrite.report.NodesReport.csv` with the following columns :
* Artifact ID : The artifact the class belongs to.
* Class name : The simple name of the class.
* Package name : The class package name.
* Incoming connections : The number of other classes pointing to this class.
* Outgoing connections : The number of other classes this class points to .

`io.github.jtama.openrewrite.report.LinksReport.csv` with the following columns :
* The source class name : The fully qualified name of the source class.
* The target class name : The fully qualified name of the target class.
* The link weight : The number of times these to classes relate to each other

`io.github.jtama.openrewrite.report.JavaSourceFileExcludedReport.csv` (if some files were excluded due to package filtering) with the following columns :
* Java source file path : The path of the excluded java source file.
* Package name : The excluded source file's package, if any.

`io.github.jtama.openrewrite.report.JavaTypesNotHandledReport.csv` (if some types were not handled during the analysis) with the following columns :
* Java type class name : The unhandled JavaType's class name.
* Java type : The unhandled Java Type.

`io.github.jtama.openrewrite.report.MethodInvocationCountReport.csv` :
* Method name : The invoked (or uninvoked) method.
* Count : The number of invocations.

## Run with pre-release version

For early access builds, you can try pre-release versions using the `999-SNAPSHOT` artifact version instead of `RELEASE`.

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
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/nicolasguilhem"><img src="https://avatars.githubusercontent.com/u/17413327?v=4?s=100" width="100px;" alt="nicolasguilhem"/><br /><sub><b>nicolasguilhem</b></sub></a><br /><a href="https://github.com/jtama/project-graph-generator/commits?author=nicolasguilhem" title="Documentation">📖</a> <a href="https://github.com/jtama/project-graph-generator/commits?author=nicolasguilhem" title="Code">💻</a></td>
    </tr>
  </tbody>
</table>

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

This project follows the [all-contributors](https://github.com/all-contributors/all-contributors) specification.
Contributions of any kind welcome!