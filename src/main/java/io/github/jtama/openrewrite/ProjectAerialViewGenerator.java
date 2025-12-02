package io.github.jtama.openrewrite;

import static java.util.Collections.emptyList;

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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.jtama.openrewrite.model.Link;
import io.github.jtama.openrewrite.model.LinksReport;
import io.github.jtama.openrewrite.model.Node;
import io.github.jtama.openrewrite.model.NodesReport;

/**
 * An OpenRewrite recipe that scans a Java project and generates it's internal dependency graph
 */
public class ProjectAerialViewGenerator extends ScanningRecipe<ProjectAerialViewGenerator.@NotNull Graph> {

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
                By default this recipe generates a ***standalone*** html documents that will help you play with the produced graph.
                """;
    }

    @Override
    public Graph getInitialValue(@NotNull ExecutionContext ctx) {
        return new Graph();
    }

    @Override
    public @NotNull TreeVisitor<?, @NotNull ExecutionContext> getScanner(Graph graph) {
        return new JavaIsoVisitor<>() {

            @Override
            public J preVisit(@NotNull J tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    tree.getMarkers().findFirst(JavaProject.class).ifPresent(javaProject -> {
                        if (packages().isEmpty() && javaProject.getPublication() != null
                                && StringUtils.isNotEmpty(javaProject.getPublication().getGroupId())) {
                            packages().add(javaProject.getPublication().getGroupId());
                        }
                    });
                }
                return tree;
            }

            @Override
            public J.@NotNull ClassDeclaration visitClassDeclaration(J.@NotNull ClassDeclaration classDecl,
                    ExecutionContext executionContext) {
                if (includeTests()
                        || !Objects.requireNonNull(getCursor().firstEnclosing(JavaSourceFile.class)).getSourcePath().toString()
                                .contains("src/test"))
                    return super.visitClassDeclaration(classDecl, executionContext);
                return classDecl;
            }

            @Override
            public J.@NotNull MemberReference visitMemberReference(J.@NotNull MemberReference memberReference,
                    ExecutionContext executionContext) {
                var member = super.visitMemberReference(memberReference, executionContext);
                JavaType.Method methodType = memberReference.getMethodType();
                //Method type could be null if there was a problem resolving dependencies or parsing the related class
                if (methodType != null) {
                    addLink(methodType.getDeclaringType());
                }
                return member;
            }

            @Override
            public J.@NotNull FieldAccess visitFieldAccess(J.@NotNull FieldAccess fieldAccess,
                    ExecutionContext executionContext) {
                var fa = super.visitFieldAccess(fieldAccess, executionContext);
                if (fa.getTarget().getType() == null) {
                    return fa;
                }

                JavaType.FullyQualified targetType = switch (fa.getTarget().getType()) {
                    case JavaType.FullyQualified fq -> fq;
                    case JavaType.Array array -> (JavaType.FullyQualified) array.getElemType();
                    default -> null;
                };

                addLink(targetType);
                return fa;
            }

            @Override
            public J.@NotNull MethodInvocation visitMethodInvocation(J.@NotNull MethodInvocation method, ExecutionContext ctx) {

                var mi = super.visitMethodInvocation(method, ctx);
                JavaType.FullyQualified targetType = mi.getMethodType() != null ? mi.getMethodType().getDeclaringType() : null;
                addLink(targetType);
                return mi;
            }

            @Override
            public J.@NotNull NewClass visitNewClass(J.@NotNull NewClass newClass, ExecutionContext executionContext) {
                J.NewClass visitedNewClass = super.visitNewClass(newClass, executionContext);
                if (visitedNewClass.getMethodType() != null) {
                    addLink(visitedNewClass.getMethodType().getDeclaringType());
                }
                return visitedNewClass;
            }

            private void addLink(JavaType.FullyQualified targetType) {
                if (packages().stream().noneMatch(basePackage -> targetType.getPackageName().contains(basePackage)))
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
    public @NotNull Collection<J.CompilationUnit> generate(Graph graph, @NotNull ExecutionContext ctx) {
        Graph finalGraph = filterGraph(graph);
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

    private Graph filterGraph(Graph graph) {
        if (maxNodes == null || graph.nodes.size() <= maxNodes) {
            return graph;
        }
        Graph filteredGraph = new Graph();
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
    public static class Graph {
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
