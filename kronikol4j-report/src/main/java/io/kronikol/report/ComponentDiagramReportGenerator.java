package io.kronikol.report;

import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.diagram.component.ComponentDiagramGenerator;
import io.kronikol.diagram.component.ComponentDiagramRenderOptions;
import io.kronikol.diagram.component.ComponentRelationship;
import io.kronikol.report.component.ComponentDiagramOptions;
import java.util.List;

/**
 * Generates the <strong>standalone</strong> component-diagram HTML page (the .NET
 * {@code ComponentDiagramReportGenerator.GenerateComponentDiagramReport} in its {@code useBrowserJs} mode — a
 * self-contained PlantUML-WASM page, distinct from embedding the diagram in the test-run report). Server-side
 * image rendering (the {@code Server}/{@code Local}/{@code NodeJs} {@code PlantUmlRendering} modes) stays out of
 * scope; Java always renders client-side, matching .NET's default {@code BrowserJs} path byte-for-byte.
 */
public final class ComponentDiagramReportGenerator {

    private ComponentDiagramReportGenerator() {
    }

    /**
     * The self-contained component-diagram HTML for {@code logs}, honouring the participant filter +
     * render options. Byte-identical to the .NET browserJs output (the gzip {@code data-plantuml-z} payload
     * aside, which is asserted decoded). An empty corpus still produces the page (matching .NET).
     */
    public static String generateHtml(List<RequestResponseLog> logs, ComponentDiagramOptions options) {
        List<ComponentRelationship> relationships =
            ComponentDiagramGenerator.extractRelationships(logs, options.participantFilter());
        ComponentDiagramRenderOptions render = ComponentDiagramRenderOptions.builder()
            .title(options.title())
            .plantUmlTheme(options.plantUmlTheme())
            .relationshipLabelFormatter(options.relationshipLabelFormatter())
            .arrowColorMode(options.arrowColorMode())
            .dependencyColors(options.dependencyColors())
            .build();
        String plantUml = ComponentDiagramGenerator.generatePlantUml(relationships, render);
        String compressed = DotNetHtmlReportRenderer.compressToBase64(plantUml);

        // contextMenuStyles = GetStyles() + GetInlineSvgStyles(); contextMenuScripts =
        // GetPlantUmlBrowserRenderScript() + GetContextMenuScript() — the same assets the embedded report uses.
        String contextMenuStyles = DotNetHtmlReportRenderer.asset("context-menu-styles.css")
            + DotNetHtmlReportRenderer.asset("inline-svg-styles.css");
        String contextMenuScripts = DotNetHtmlReportRenderer.asset("plantuml-browser-render-script.js")
                .replace("__PLANTUML_CDN_BASE__", DotNetHtmlReportRenderer.PLANTUML_CDN_BASE)
            + DotNetHtmlReportRenderer.asset("context-menu-script.js");

        return "<html>\n"
            + "    <head>\n"
            + "        <meta charset=\"utf-8\">\n"
            + "        <link rel=\"icon\" href=\"" + DotNetHtmlReportRenderer.FAVICON_DATA_URI + "\">\n"
            + "        <style>\n"
            + "            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 2rem; }\n"
            + "            h1 { color: #333; }\n"
            + "            .diagram-image { margin: 1rem 0; text-align: center; }\n"
            + "            .diagram-image img { max-width: 100%; height: auto; }\n"
            + "            " + contextMenuStyles + "\n"
            + "        </style>\n"
            + "        " + contextMenuScripts + "\n"
            + "    </head>\n"
            + "    <body>\n"
            + "        <h1>" + options.title() + "</h1>\n"
            + "        <div class=\"diagram-image\">\n"
            + "            <div class=\"plantuml-browser\" id=\"comp-diagram\" data-plantuml-z=\"" + compressed
            + "\" data-diagram-type=\"plantuml\"></div>\n"
            + "        </div>\n"
            + "    </body>\n"
            + "</html>";
    }
}
