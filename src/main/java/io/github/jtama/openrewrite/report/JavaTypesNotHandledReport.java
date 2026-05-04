package io.github.jtama.openrewrite.report;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;
import org.openrewrite.java.tree.JavaType;

public class JavaTypesNotHandledReport extends DataTable<JavaTypesNotHandledReport.@NotNull Row> {

    public JavaTypesNotHandledReport(@Nullable Recipe recipe) {
        super(recipe, "JavaType not handled",
                "Records the JavaTypes that were not handled by the recipe.");
    }

    public static class Row {

        @Column(displayName = "Java type class name", description = "The unhandled JavaType's class name.")
        String className;

        @Column(displayName = "Java type", description = "The unhandled Java Type.")
        String javaType;

        public Row(JavaType javaType) {
            this.className = javaType.getClass().getName();
            this.javaType = javaType.toString();
        }
    }
}
