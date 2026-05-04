package io.github.jtama.openrewrite.report;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;
import org.openrewrite.java.tree.JavaSourceFile;

public class JavaSourceFileExcludedReport extends DataTable<JavaSourceFileExcludedReport.@NotNull Row> {

    public JavaSourceFileExcludedReport(@Nullable Recipe recipe) {
        super(recipe, "JavaSourceFile excluded",
                "Records JavaSource files that were not handled by the recipe because they don't match the packages list submitted.");
    }

    public static class Row {

        @Column(displayName = "Java source file path", description = "The Java source file path that was excluded.")
        String fileSourcePath;

        @Column(displayName = "Package name", description = "The excluded source file's package, if any.")
        String packageName;

        public Row(JavaSourceFile javaSourceFile) {
            this.fileSourcePath = javaSourceFile.getSourcePath().toString();
            this.packageName = javaSourceFile.getPackageDeclaration() == null ? "null"
                    : javaSourceFile.getPackageDeclaration().getPackageName();
        }
    }
}
