package io.kronikol.cli;

import io.kronikol.report.merge.FragmentJson;
import io.kronikol.report.merge.MergeableReportMerger;
import io.kronikol.report.merge.MergeableReportRenderer;
import io.kronikol.report.merge.ReportFragment;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code kronikol4j merge <inputs…> -o <output.html> -t <title>} — the "Merging Parallel Reports"
 * feature (plan §5.5). Inputs are files, directories (searched recursively for {@code *.json}); only
 * enriched JSON fragments are mergeable; output is combined HTML.
 */
public final class MergeCommand {

    private MergeCommand() {
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        List<Path> inputs = new ArrayList<>();
        Path output = Path.of("TestRunReport.html");
        String title = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-o", "--output" -> output = Path.of(requireValue(args, ++i, "-o"));
                case "-t", "--title" -> title = requireValue(args, ++i, "-t");
                case "-h", "--help" -> {
                    usage(out);
                    return 0;
                }
                default -> inputs.add(Path.of(args[i]));
            }
        }

        if (inputs.isEmpty()) {
            err.println("error: no inputs given");
            usage(err);
            return 2;
        }

        try {
            List<Path> jsonFiles = collectJsonFiles(inputs);
            if (jsonFiles.isEmpty()) {
                err.println("error: no *.json fragments found in " + inputs);
                return 3;
            }
            List<ReportFragment> fragments = new ArrayList<>();
            for (Path file : jsonFiles) {
                fragments.add(FragmentJson.fromJson(Files.readString(file, StandardCharsets.UTF_8)));
            }
            ReportFragment merged = MergeableReportMerger.merge(fragments);
            if (title != null) {
                merged = new ReportFragment(title, merged.startTime(), merged.endTime(), merged.features());
            }
            String html = MergeableReportRenderer.renderHtml(merged);
            Files.writeString(output, html, StandardCharsets.UTF_8);
            out.println("Merged " + fragments.size() + " fragment(s) from " + jsonFiles.size()
                + " file(s) -> " + output.toAbsolutePath());
            return 0;
        } catch (IOException e) {
            err.println("error: " + e.getMessage());
            return 1;
        }
    }

    static List<Path> collectJsonFiles(List<Path> inputs) throws IOException {
        List<Path> result = new ArrayList<>();
        for (Path input : inputs) {
            if (Files.isDirectory(input)) {
                try (Stream<Path> walk = Files.walk(input)) {
                    walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".json"))
                        .sorted()
                        .forEach(result::add);
                }
            } else if (Files.isRegularFile(input) && input.toString().endsWith(".json")) {
                result.add(input);
            }
        }
        return result;
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("missing value for " + flag);
        }
        return args[index];
    }

    private static void usage(PrintStream stream) {
        stream.println("Usage: kronikol4j merge <inputs...> [-o <output.html>] [-t <title>]");
        stream.println("  <inputs>   files, directories (recursive *.json), of enriched report fragments");
        stream.println("  -o,--output  output HTML path (default: TestRunReport.html)");
        stream.println("  -t,--title   report title");
    }
}
