package io.github.jtama.openrewrite;

import static java.util.Collections.emptyList;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.github.jtama.openrewrite.report.MethodInvocationCountReport;

public class CountPublicMethodInvocations extends ScanningRecipe<Map<String, MethodInvocationCountReport.Row>> {

    @Option(displayName = "Base package", description = "The base package that will be considered as ***your code***. If empty, the project `groupId` will be used.", example = "com.yourorg.project", required = false)
    private String basePackage;

    @Option(displayName = "Only uninvoked", description = "If set to `true`, only keep methods with zero invocation count. Defaults to `false`", example = "true", required = false)
    private Boolean onlyUninvoked;

    @Option(displayName = "Generate HTML view", description = "Should the recipe generate an HTML view of the graph. Defaults to `true`.", example = "true", required = false)
    private Boolean generateHTMLView;

    public CountPublicMethodInvocations() {
        this.onlyUninvoked = false;
        this.generateHTMLView = true;
    }

    @JsonCreator
    public CountPublicMethodInvocations(String basePackage, Boolean onlyUninvoked, Boolean generateHTMLView) {
        this.basePackage = basePackage;
        this.onlyUninvoked = onlyUninvoked != null && onlyUninvoked;
        this.generateHTMLView = generateHTMLView == null || generateHTMLView;
    }

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Find your public methods and count their invocations";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return """
                Find all the public methods in your code base and generates a report counting their invocations.
                The recipe has a flag that allow you to only keep public methods without any invocations.""";
    }

    public Boolean generateHTMLView() {
        return generateHTMLView;
    }

    transient MethodInvocationCountReport report = new MethodInvocationCountReport(this);

    @Override
    public Map<String, MethodInvocationCountReport.Row> getInitialValue(ExecutionContext ctx) {
        return new HashMap<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Map<String, MethodInvocationCountReport.Row> acc) {
        return new InvocationCounterVisitor(acc);
    }

    @Override
    public java.util.Collection<? extends org.openrewrite.SourceFile> generate(Map<String, MethodInvocationCountReport.Row> acc,
            ExecutionContext ctx) {
        acc.values().stream()
                .filter(row -> !onlyUninvoked() || row.getCount() != 0)
                .forEach(row -> report.insertRow(ctx, row));
        return emptyList();
    }

    @Override
    public void onComplete(ExecutionContext ctx) {
        if (generateHTMLView()) {
            HtmlPageGenerator.generate(ctx);
        }
        super.onComplete(ctx);
    }

    public boolean onlyUninvoked() {
        return onlyUninvoked;
    }

    private class InvocationCounterVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final Map<String, MethodInvocationCountReport.Row> acc;

        public InvocationCounterVisitor(Map<String, MethodInvocationCountReport.Row> acc) {
            this.acc = acc;
        }

        private Predicate<Cursor> isTestClass = cursor -> {
            var sourceFile = getCursor().firstEnclosing(JavaSourceFile.class);
            return sourceFile != null && sourceFile.getSourcePath().toString()
                    .contains("src/test");
        };

        private Predicate<JavaType.Method> shouldCountInvocations = method -> method != null
                && method.hasFlags(Flag.Public)
                && method.getDeclaringType().getFullyQualifiedName().startsWith(basePackage);

        private Predicate<J.ClassDeclaration> isTargetClass = classDeclaration -> classDeclaration != null
                && classDeclaration.getType() != null
                && classDeclaration.getType().getFullyQualifiedName().startsWith(basePackage);

        @Override
        public J preVisit(@NotNull J tree, ExecutionContext ctx) {
            if (tree instanceof JavaSourceFile javaSourceFile) {
                javaSourceFile.getMarkers().findFirst(JavaProject.class).ifPresent(javaProject -> {
                    if (javaProject.getPublication() != null
                            && StringUtils.isNotEmpty(javaProject.getPublication().getGroupId())) {
                        if (basePackage == null)
                            basePackage = javaProject.getPublication().getGroupId();
                    }
                });
            }
            return tree;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.@NonNull MethodDeclaration method,
                @NonNull ExecutionContext executionContext) {
            J.ClassDeclaration classDeclaration = getCursor().firstEnclosing(J.ClassDeclaration.class);
            if (!isTestClass.test(getCursor()) && isTargetClass.test(classDeclaration)
                    && shouldCountInvocations.test(method.getMethodType())) {
                // method.getMethodType() can't be null, verified by predicate
                acc.computeIfAbsent(getFQDN(method.getMethodType()),
                        (key) -> new MethodInvocationCountReport.Row(key, method.isConstructor()));
            }
            return super.visitMethodDeclaration(method, executionContext);
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, @NonNull ExecutionContext executionContext) {
            if (!isTestClass.test(getCursor()) && shouldCountInvocations.test(method.getMethodType())) {
                // method.getMethodType() can't be null, verified by predicate
                acc.computeIfAbsent(getFQDN(method.getMethodType()),
                        (key) -> new MethodInvocationCountReport.Row(key, method.getMethodType().isConstructor()))
                        .incrementCount();
            }
            return super.visitMethodInvocation(method, executionContext);
        }

        private String getFQDN(JavaType.Method methodType) {
            return methodType.getDeclaringType().getFullyQualifiedName() + "#" + methodType.getName();
        }
    }

}
