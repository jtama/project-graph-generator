package io.github.jtama.openrewrite.report;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

public class MethodInvocationCountReport extends DataTable<MethodInvocationCountReport.@NotNull Row> {

    public MethodInvocationCountReport(@Nullable Recipe recipe) {
        super(recipe, "Public method invocation counter",
                "Records the number of invocations for your public methods");
    }

    public static class Row {

        @Column(displayName = "Method", description = """
                The fully qualified name of the method. ie: "java.lang.String#equals"
                """)
        String methodName;

        @Column(displayName = "Count", description = "The number of method invocation.")
        Integer count;

        @Column(displayName = "Constructor", description = "Indicates if the method is a constructor.")
        boolean constructor;

        public Row(String methodName, boolean constructor) {
            this.methodName = methodName;
            this.constructor = constructor;
            count = 0;
        }

        public void incrementCount() {
            count++;
        }

        public int getCount() {
            return count;
        }

        public String getMethodName() {
            return methodName;
        }

        public boolean getConstructor() {
            return constructor;
        }
    }
}
