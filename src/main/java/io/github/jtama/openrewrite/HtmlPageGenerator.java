package io.github.jtama.openrewrite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.openrewrite.DataTableExecutionContextView;
import org.openrewrite.ExecutionContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.jtama.openrewrite.model.Link;
import io.github.jtama.openrewrite.model.Node;
import io.github.jtama.openrewrite.report.LinksReport;
import io.github.jtama.openrewrite.report.MethodInvocationCountReport;
import io.github.jtama.openrewrite.report.NodesReport;

public class HtmlPageGenerator {

    public static void generate(@NonNull ExecutionContext executionContext) {

        //FIXME - This method is gonna be executed after each recipe which is not ideal, but there aren't any other way to do it elegantly right now.
        Stream<@NotNull Node> nodes = DataTableExecutionContextView.view(executionContext).getDataTableStore()
                .getRows(NodesReport.class);
        Stream<@NotNull Link> links = DataTableExecutionContextView.view(executionContext).getDataTableStore()
                .getRows(LinksReport.class);
        Stream<MethodInvocationCountReport.@NotNull Row> methodInvocations = DataTableExecutionContextView
                .view(executionContext).getDataTableStore().getRows(MethodInvocationCountReport.class);

        try (InputStream templateStream = HtmlPageGenerator.class.getResourceAsStream("template.html")) {
            String graphAsJson = new ObjectMapper()
                    .writeValueAsString(new ProjectGraphGenerator.GraphScanAccumulator(nodes, links));
            String invocationCountAsJson = new ObjectMapper().writeValueAsString(methodInvocations.toList());
            if (templateStream == null) {
                throw new IllegalStateException("template.html not found");
            }
            String template = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);
            String renderedTemplate = template.replace("{{ graphData }}", graphAsJson);
            renderedTemplate = renderedTemplate.replace("{{ invocationCount }}", invocationCountAsJson);

            Path projectDir = Paths.get(System.getProperty("user.dir"));
            Files.writeString(projectDir.resolve("class-diagram.html"), renderedTemplate);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
