package io.github.jtama.openrewrite;

import static java.util.Collections.emptyList;
import static java.util.function.Predicate.not;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.jtama.openrewrite.model.JavaSourceFileExcludedReport;
import io.github.jtama.openrewrite.model.JavaTypesNotHandledReport;
import io.github.jtama.openrewrite.model.Link;
import io.github.jtama.openrewrite.model.LinksReport;
import io.github.jtama.openrewrite.model.Node;
import io.github.jtama.openrewrite.model.NodesReport;

/**
 * An OpenRewrite recipe that scans a Java project and generates it's internal dependency graph
 */
public class ProjectAerialViewGenerator extends ScanningRecipe<ProjectAerialViewGenerator.@NotNull GraphScanAccumulator> {

    @Option(displayName = "Maximum nodes", description = "The maximum number of nodes to display in the graph. We will try to retain the largest nodes.", required = false)
    private Integer maxNodes;

    @Option(displayName = "Base packages", description = "A list of colon separated base packages that will be considered as ***your code***. If empty, the project `groupId` will be used.", example = "com.yourorg.project:com.yourorg.app", required = false)
    private String basePackages;

    @Option(displayName = "Include tests", description = "Should the test code be included in the generated graph. Defaults to `false`", example = "true", required = false)
    private Boolean includeTests;

    @Option(displayName = "Generate HTML view", description = "Should the recipe generate an HTML view of the graph. Defaults to `true`.", example = "true", required = false)
    private Boolean generateHTMLView;

    private List<String> packages = new ArrayList<>();

    transient NodesReport nodesReport = new NodesReport(this);

    transient LinksReport linksReport = new LinksReport(this);

    transient JavaTypesNotHandledReport javaTypesNotHandledReport = new JavaTypesNotHandledReport(this);

    transient JavaSourceFileExcludedReport javaSourceFileExcludedReport = new JavaSourceFileExcludedReport(this);

    public ProjectAerialViewGenerator() {
    }

    @JsonCreator
    public ProjectAerialViewGenerator(Integer maxNodes, String basePackages, Boolean includeTests, Boolean generateHTMLView) {
        this.maxNodes = maxNodes;
        this.basePackages = basePackages;
        this.includeTests = includeTests;
        this.generateHTMLView = generateHTMLView;
    }

    public Boolean includeTests() {
        return includeTests != null && includeTests;
    }

    public Boolean generateHTMLView() {
        return generateHTMLView == null || generateHTMLView;
    }

    public List<String> packages() {
        if (packages.isEmpty()) {
            this.packages = this.basePackages != null ? Arrays.stream(this.basePackages.split(":")).map(String::trim).toList()
                    : new ArrayList<>();
        }
        return packages;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Project graph generator";
    }

    @Override
    public @NotNull String getDescription() {
        return """
                Generates the internal dependency graph of your java project.
                With multiple output formats, this recipe will help you get a *better* grasp of your internal dependencies.
                Not the ones that were designed, but the ones that emerged over time.
                By default this recipe generates a ***standalone*** html documents that will help you play with the produced graph.""";
    }

    @Override
    public GraphScanAccumulator getInitialValue(@NotNull ExecutionContext ctx) {
        return new GraphScanAccumulator();
    }

    @Override
    public @NotNull TreeVisitor<?, @NotNull ExecutionContext> getScanner(GraphScanAccumulator graph) {
        return new JavaIsoVisitor<>() {

            @Override
            public J preVisit(@NotNull J tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile javaSourceFile) {
                    javaSourceFile.getMarkers().findAll(JavaProject.class).forEach(javaProject -> {
                        if (javaProject.getPublication() != null
                                && StringUtils.isNotEmpty(javaProject.getPublication().getGroupId())) {
                            if (packages().isEmpty())
                                packages().add(javaProject.getPublication().getGroupId());
                        }
                    });
                    if (javaSourceFile.getPackageDeclaration() == null
                            || isPackageExcluded(javaSourceFile.getPackageDeclaration().getPackageName())) {
                        javaSourceFileExcludedReport.insertRow(ctx, new JavaSourceFileExcludedReport.Row(javaSourceFile));
                        stopAfterPreVisit();
                    }
                }
                return tree;
            }

            @Override
            public J.@NotNull ClassDeclaration visitClassDeclaration(J.@NotNull ClassDeclaration classDecl,
                    ExecutionContext executionContext) {
                if (includeTests() || not(isTestClass()).test(getCursor())) {
                    J.CompilationUnit cu = getCursor().dropParentUntil(value -> value instanceof J.CompilationUnit).getValue();
                    cu.getMarkers().findFirst(JavaProject.class).ifPresent(javaProject -> {
                        if (javaProject.getPublication() != null
                                && StringUtils.isNotEmpty(javaProject.getPublication().getArtifactId())) {
                            String artifact = javaProject.getPublication().getArtifactId();
                            if (classDecl.getType() != null) {
                                String fq = classDecl.getType().getFullyQualifiedName();
                                graph.findNode(fq).orElseGet(() -> {
                                    Node newNode = new Node(fq, classDecl.getType().getPackageName());
                                    newNode.setArtifactId(artifact);
                                    graph.nodes.add(newNode);
                                    return newNode;
                                }).setArtifactId(artifact);
                            }
                        }
                    });
                    return super.visitClassDeclaration(classDecl, executionContext);
                }
                return classDecl;
            }

            private Predicate<Cursor> isTestClass() {
                return cursor -> {
                    var sourceFile = getCursor().firstEnclosing(JavaSourceFile.class);
                    return sourceFile != null && sourceFile.getSourcePath().toString()
                            .contains("src/test");
                };
            }

            @Override
            public J.@NotNull MemberReference visitMemberReference(J.@NotNull MemberReference memberReference,
                    ExecutionContext ctx) {
                var member = super.visitMemberReference(memberReference, ctx);
                JavaType.Method methodType = memberReference.getMethodType();
                //Method type could be null if there was a problem resolving dependencies or parsing the related class
                if (methodType != null) {
                    addLink(methodType.getDeclaringType());
                }
                return member;
            }

            @Override
            public J.@NotNull FieldAccess visitFieldAccess(J.@NotNull FieldAccess fieldAccess,
                    ExecutionContext ctx) {
                var fa = super.visitFieldAccess(fieldAccess, ctx);
                addLinkForType(fa.getTarget().getType(), ctx);
                return fa;
            }

            @Override
            public J.@NotNull MethodInvocation visitMethodInvocation(J.@NotNull MethodInvocation method, ExecutionContext ctx) {
                var mi = super.visitMethodInvocation(method, ctx);
                JavaType.FullyQualified targetType = mi.getMethodType() != null ? mi.getMethodType().getDeclaringType() : null;
                if (targetType != null) {
                    // Method target type could be null for groovy or kotlin projects
                    addLink(targetType);
                }
                return mi;
            }

            @Override
            public J.@NotNull NewClass visitNewClass(J.@NotNull NewClass newClass, ExecutionContext ctx) {
                J.NewClass visitedNewClass = super.visitNewClass(newClass, ctx);
                if (visitedNewClass.getMethodType() != null) {
                    addLink(visitedNewClass.getMethodType().getDeclaringType());
                }
                return visitedNewClass;
            }

            private void addLinkForType(JavaType type, ExecutionContext ctx) {

                switch (type) {
                    case null -> {
                    }
                    case JavaType.Primitive primitive -> {
                    }
                    case JavaType.FullyQualified fq -> addLink(fq);
                    case JavaType.Array array -> addLink((JavaType.FullyQualified) array.getElemType());
                    case JavaType.GenericTypeVariable gtv -> gtv.getBounds().stream().forEach(b -> addLinkForType(b, ctx));
                    default -> javaTypesNotHandledReport.insertRow(ctx, new JavaTypesNotHandledReport.Row(type));
                }
            }

            private boolean isPackageExcluded(String packageName) {
                return packages().stream().noneMatch(packageName::contains);
            }

            private void addLink(@NotNull JavaType.FullyQualified targetType) {

                if (isPackageExcluded(targetType.getPackageName()))
                    return;
                J.ClassDeclaration enclosingClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (enclosingClass == null) {
                    return;
                }
                JavaType.FullyQualified sourceType = enclosingClass.getType();
                if (sourceType != null && !sourceType.equals(targetType)) {
                    String sourceFq = sourceType.getFullyQualifiedName();
                    String targetFq = targetType.getFullyQualifiedName();

                    Node sourceNode = graph.findNode(sourceFq).orElseGet(() -> {
                        Node newNode = new Node(sourceFq, sourceType.getPackageName());
                        graph.nodes.add(newNode);
                        return newNode;
                    });
                    Node targetNode = graph.findNode(targetFq).orElseGet(() -> {
                        Node newNode = new Node(targetFq, targetType.getPackageName());
                        graph.nodes.add(newNode);
                        return newNode;
                    });

                    Link link = graph.findLink(sourceFq, targetFq).orElseGet(() -> {
                        Link newLink = new Link(sourceFq, targetFq);
                        graph.links.add(newLink);
                        sourceNode.incrementOutgoing();
                        targetNode.incrementIncoming();
                        return newLink;
                    });
                    link.incrementWeight();
                }
            }
        };
    }

    @Override
    public @NotNull Collection<J.CompilationUnit> generate(GraphScanAccumulator graph, @NotNull ExecutionContext ctx) {
        GraphScanAccumulator finalGraph = filterGraph(graph);
        finalGraph.nodes.forEach(node -> nodesReport.insertRow(ctx, node));
        finalGraph.links.forEach(link -> linksReport.insertRow(ctx, link));
        if (generateHTMLView()) {
            try (InputStream templateStream = getClass().getResourceAsStream("template.html")) {
                String json = new ObjectMapper().writeValueAsString(finalGraph);
                if (templateStream == null) {
                    throw new IllegalStateException("template.html not found");
                }
                String template = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);
                String renderedTemplate = template.replace("'{{graphData}}'", json);

                Path projectDir = Paths.get(System.getProperty("user.dir"));
                Files.writeString(projectDir.resolve("class-diagram.html"), renderedTemplate);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return emptyList();
    }

    private GraphScanAccumulator filterGraph(GraphScanAccumulator graph) {
        if (maxNodes == null || graph.nodes.size() <= maxNodes) {
            return graph;
        }
        GraphScanAccumulator filteredGraph = new GraphScanAccumulator();
        graph.nodes.sort(
                Comparator.comparingInt((Node node) -> Math.max(node.getIncomingConnections(), node.getOutgoingConnections()))
                        .reversed());
        filteredGraph.nodes = new ArrayList<>(graph.nodes.subList(0, maxNodes));
        List<String> topNodeIds = filteredGraph.nodes.stream().map(Node::getClassName).toList();

        filteredGraph.links = graph.links.stream()
                .filter(l -> topNodeIds.contains(l.getSource()) && topNodeIds.contains(l.getTarget()))
                .collect(Collectors.toList());

        return filteredGraph;
    }

    /**
     * The accumulator for the recipe, holding the graph data.
     */
    public static class GraphScanAccumulator {
        public List<Node> nodes = new ArrayList<>();
        public List<Link> links = new ArrayList<>();

        public Optional<Node> findNode(String id) {
            return nodes.stream().filter(n -> n.getClassName().equals(id)).findFirst();
        }

        public Optional<Link> findLink(String source, String target) {
            return links.stream().filter(l -> l.getSource().equals(source) && l.getTarget().equals(target)).findFirst();
        }
    }
}
