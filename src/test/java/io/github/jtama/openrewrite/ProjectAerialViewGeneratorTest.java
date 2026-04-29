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
        spec.recipe(new CountPublicMethodInvocations("com.yourorg", false, true))
                .parser(JavaParser.fromJavaVersion()
                        .logCompilationWarningsAndErrors(true));
    }

    @Test
    @Disabled
    void addsHelloToFooBar() {
        rewriteRun(
                spec -> spec.expectedCyclesThatMakeChanges(0)
                        .recipes(new CountPublicMethodInvocations("com.yourorg", false, true),
                                new ProjectAerialViewGenerator()),
                java(
                        """
                                package com.yourorg;

                                class FooBar {
                                     @Override
                                     public String toString() {
                                         return super.toString();
                                     }
                                }
                                """,
                        spec -> spec.markers(new JavaProject(UUID.randomUUID(), "NoUseForAName",
                                new JavaProject.Publication("com.yourorg", "my-app", "1")))),
                java(
                        """
                                package com.yourorg;

                                class BarBar {
                                    private FooBar foo = new FooBar();
                                    private String tutu = foo.toString();
                                }
                                """,
                        spec -> spec.markers(new JavaProject(UUID.randomUUID(), "NoUseForAName",
                                new JavaProject.Publication("com.yourorg", "my-app", "1")))));
    }
}
