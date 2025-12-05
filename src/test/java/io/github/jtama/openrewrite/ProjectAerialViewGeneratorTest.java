package io.github.jtama.openrewrite;

import static org.openrewrite.java.Assertions.java;

import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class ProjectAerialViewGeneratorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ProjectAerialViewGenerator())
                .parser(JavaParser.fromJavaVersion()
                        .logCompilationWarningsAndErrors(true));
    }

    @Test
    @Disabled
    void addsHelloToFooBar() {
        rewriteRun(
                java(
                        """
                                package com.yourorg;

                                class FooBar {
                                }
                                """,
                        null,
                        spec -> spec.markers(
                                new JavaProject(
                                        UUID.randomUUID(),
                                        "NoUseForAName",
                                        new JavaProject.Publication("com.yourorg", "my-app", "1")))),
                java(
                        """
                                package com.yourorg;

                                class BarBar {
                                    private FooBar foo = new FooBar();
                                }
                                """,
                        null,
                        spec -> spec.markers(
                                new JavaProject(
                                        UUID.randomUUID(),
                                        "NoUseForAName",
                                        new JavaProject.Publication("com.yourorg", "my-app", "1"))))

        );
    }

}
