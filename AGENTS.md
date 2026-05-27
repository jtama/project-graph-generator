# OpenCode Agent Instructions

This repository provides [OpenRewrite](https://docs.openrewrite.org/) recipes for generating architectural graphs and method invocation counts.

## Toolchain & Build
- **Build System**: Maven (`pom.xml`).
- **Java Version**: The codebase compiles to Java 17, but requires **Java 21** to run Maven (as seen in CI and `mise.toml` configurations).
- **Auto-Formatting**: Code formatting and import sorting are handled automatically via `formatter-maven-plugin` and `impsort-maven-plugin`. You do not need to format code manually. Run `mvn clean process-sources` or `mvn compile` to apply the project's formatting rules.
- **Full Build**: Run `mvn clean verify` to auto-format, compile, and execute all tests.
- **Run specific test**: `mvn test -Dtest=ClassNameTest`

## Architecture
- **Entrypoints**: The primary recipes are `io.github.jtama.openrewrite.ProjectGraphGenerator` and `io.github.jtama.openrewrite.CountPublicMethodInvocations`.
- **ScanningRecipes**: Because the recipes need to process the entire AST before generating global graphs and counts, they extend `ScanningRecipe`. They collect state in an accumulator across all files.
- **Output Flow**: During the `generate(accumulator, ctx)` phase, recipes insert rows into OpenRewrite DataTables (`NodesReport`, `LinksReport`, etc.). Immediately after, `HtmlPageGenerator` retrieves these same rows from the `DataTableExecutionContextView` to populate and generate the standalone HTML views (`template.html`).

## Testing Conventions
- **RewriteTest**: Tests are written using OpenRewrite's testing harness (`RewriteTest`).
- **Inline Mocks**: Test classes (e.g., `ProjectAerialViewGeneratorTest`) define inline mock projects using `java(""" ... """)` assertions. When adding a feature or reproducing a bug, add a new inline test case rather than creating external fixture files.