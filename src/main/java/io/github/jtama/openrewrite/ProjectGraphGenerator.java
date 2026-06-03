package io.github.jtama.openrewrite;

import static java.util.Collections.emptyList;
import static java.util.function.Predicate.not;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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

import io.github.jtama.openrewrite.model.Link;
import io.github.jtama.openrewrite.model.Node;
import io.github.jtama.openrewrite.report.JavaSourceFileExcludedReport;
import io.github.jtama.openrewrite.report.JavaTypesNotHandledReport;
import io.github.jtama.openrewrite.report.LinksReport;
import io.github.jtama.openrewrite.report.NodesReport;

/**
 * An OpenRewrite recipe that scans a Java project and generates it's internal dependency graph
 */
public class ProjectGraphGenerator extends ScanningRecipe<ProjectGraphGenerator.@NonNull GraphScanAccumulator> {

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

    public ProjectGraphGenerator() {
    }

    @JsonCreator
    public ProjectGraphGenerator(Integer maxNodes, String basePackages, Boolean includeTests, Boolean generateHTMLView) {
        this.maxNodes = maxNodes;
        this.basePackages = basePackages;
        this.includeTests = includeTests;
        this.generateHTMLView = generateHTMLView;
    }

    public boolean includeTests() {
        return includeTests != null && includeTests;
    }

    public boolean generateHTMLView() {
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
    public @NonNull String getDisplayName() {
        return "Project graph generator";
    }

    @Override
    public @NonNull String getDescription() {
        return """
                Generates the internal dependency graph of your java project.
                With multiple output formats, this recipe will help you get a *better* grasp of your internal dependencies.
                Not the ones that were designed, but the ones that emerged over time.
                By default this recipe generates a ***standalone*** html documents that will help you play with the produced graph.""";
    }

    @Override
    public @NonNull GraphScanAccumulator getInitialValue(@NonNull ExecutionContext ctx) {
        return new GraphScanAccumulator();
    }

    @Override
    public @NonNull TreeVisitor<?, @NonNull ExecutionContext> getScanner(@NonNull GraphScanAccumulator graph) {
        return new JavaIsoVisitor<>() {

            @Override
            public J preVisit(@NonNull J tree, @NonNull ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile javaSourceFile) {
                    javaSourceFile.getMarkers().findFirst(JavaProject.class).ifPresent(javaProject -> {
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
            public J.@NonNull ClassDeclaration visitClassDeclaration(J.@NonNull ClassDeclaration classDecl,
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
            public @Nullable JavaType visitType(@Nullable JavaType javaType, ExecutionContext executionContext) {
                if (javaType != null && !(javaType instanceof JavaType.Unknown)) {
                    addLinkForType(javaType, executionContext);
                }
                return super.visitType(javaType, executionContext);
            }

            private void addLinkForType(JavaType jType, ExecutionContext ctx) {
                if (jType == null || jType instanceof JavaType.Primitive || jType instanceof JavaType.Unknown
                        || jType instanceof JavaType.Array || jType instanceof JavaType.GenericTypeVariable
                        || jType instanceof JavaType.Method || jType instanceof JavaType.Variable) {
                    return;
                }

                if (jType instanceof JavaType.FullyQualified fq) {
                    addLink(fq);
                } else {
                    javaTypesNotHandledReport.insertRow(ctx, new JavaTypesNotHandledReport.Row(jType));
                }
            }

            private boolean isPackageExcluded(String packageName) {
                return packages().stream().noneMatch(packageName::contains);
            }

            private void addLink(JavaType.@NonNull FullyQualified targetType) {
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
    public @NonNull Collection<J.CompilationUnit> generate(GraphScanAccumulator graph, @NonNull ExecutionContext ctx) {
        GraphScanAccumulator finalGraph = filterGraph(graph);
        finalGraph.nodes.forEach(node -> nodesReport.insertRow(ctx, node));
        finalGraph.links.forEach(link -> linksReport.insertRow(ctx, link));
        if (generateHTMLView()) {
            HtmlPageGenerator.generate(ctx);
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
        public List<Node> nodes;
        public List<Link> links;

        public GraphScanAccumulator() {
            super();
            this.nodes = new ArrayList<>();
            this.links = new ArrayList<>();
        }

        public GraphScanAccumulator(Stream<@NonNull Node> nodes, Stream<@NonNull Link> links) {
            super();
            this.nodes = nodes.collect(Collectors.toList());
            this.links = links.collect(Collectors.toList());
        }

        public Optional<Node> findNode(String id) {
            return nodes.stream().filter(n -> n.getClassName().equals(id)).findFirst();
        }

        public Optional<Link> findLink(String source, String target) {
            return links.stream().filter(l -> l.getSource().equals(source) && l.getTarget().equals(target)).findFirst();
        }
    }
}
