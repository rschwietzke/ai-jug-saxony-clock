package org.jugsaxony.report;

import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Global Memory Footprint & Layout Analyzer (JOL)
 */
public class GlobalJolReport {

    public record ImplementationMeta(String id, String name, String model, Class<?> mapClass, MapFactory factory) {}

    public interface MapFactory {
        Object create(int maxSize);
        default Object create() {
            return create(16);
        }
        void put(Object map, Object key, Object value);
    }

    public record MemoryMetrics(
            String id,
            String name,
            String model,
            int shallowSize,
            long emptyFootprint,
            long emptyObjectCount,
            Map<Integer, Long> footprintBySize,
            Map<Integer, Long> objectCountBySize,
            Map<Integer, Double> bytesPerEntryBySize,
            String classLayoutSummary
    ) {}

    public static final List<ImplementationMeta> IMPLEMENTATIONS = List.of(
            new ImplementationMeta("demo0", "Demo 0", "Human Baseline", org.jugsaxony.clock.demo0.LRUClockMap.class, new MapFactory() {
                public Object create(int maxSize) { return org.jugsaxony.clock.demo0.LRUClockMap.of(maxSize); }
                @SuppressWarnings("unchecked")
                public void put(Object map, Object key, Object value) { ((org.jugsaxony.clock.demo0.LRUClockMap<String, Integer>) map).put((String) key, (Integer) value); }
            }),
            new ImplementationMeta("demo1", "Demo 1", "Gemini 3.7 Flash High (Antigravity)", org.jugsaxony.clock.demo1.LRUClockMap.class, new MapFactory() {
                public Object create(int maxSize) { return org.jugsaxony.clock.demo1.LRUClockMap.of(maxSize); }
                @SuppressWarnings("unchecked")
                public void put(Object map, Object key, Object value) { ((org.jugsaxony.clock.demo1.LRUClockMap<String, Integer>) map).put((String) key, (Integer) value); }
            }),
            new ImplementationMeta("demo2", "Demo 2", "Kimi K3 (Kilo Code)", org.jugsaxony.clock.demo2.LRUClockMap.class, new MapFactory() {
                public Object create(int maxSize) { return org.jugsaxony.clock.demo2.LRUClockMap.of(maxSize); }
                @SuppressWarnings("unchecked")
                public void put(Object map, Object key, Object value) { ((org.jugsaxony.clock.demo2.LRUClockMap<String, Integer>) map).put((String) key, (Integer) value); }
            }),
            new ImplementationMeta("demo3", "Demo 3", "OpenAI 5.6 Sol Max (Kilo Code)", org.jugsaxony.clock.demo3.LRUClockMap.class, new MapFactory() {
                public Object create(int maxSize) { return org.jugsaxony.clock.demo3.LRUClockMap.of(maxSize); }
                @SuppressWarnings("unchecked")
                public void put(Object map, Object key, Object value) { ((org.jugsaxony.clock.demo3.LRUClockMap<String, Integer>) map).put((String) key, (Integer) value); }
            }),
            new ImplementationMeta("demo4", "Demo 4", "Gemma 4 31B Thinking (Kilo Code)", org.jugsaxony.clock.demo4.LRUClockMap.class, new MapFactory() {
                public Object create(int maxSize) { return org.jugsaxony.clock.demo4.LRUClockMap.of(maxSize); }
                @SuppressWarnings("unchecked")
                public void put(Object map, Object key, Object value) { ((org.jugsaxony.clock.demo4.LRUClockMap<String, Integer>) map).put((String) key, (Integer) value); }
            }),
            new ImplementationMeta("demo5", "Demo 5", "Deepseek V4 Flash Max (Kilo Code)", org.jugsaxony.clock.demo5.LRUClockMap.class, new MapFactory() {
                public Object create(int maxSize) { return org.jugsaxony.clock.demo5.LRUClockMap.of(maxSize); }
                @SuppressWarnings("unchecked")
                public void put(Object map, Object key, Object value) { ((org.jugsaxony.clock.demo5.LRUClockMap<String, Integer>) map).put((String) key, (Integer) value); }
            }),
            new ImplementationMeta("demo6", "Demo 6", "Claude Opus 5 Ultra (Claude)", org.jugsaxony.clock.demo6.LRUClockMap.class, new MapFactory() {
                public Object create(int maxSize) { return org.jugsaxony.clock.demo6.LRUClockMap.of(maxSize); }
                @SuppressWarnings("unchecked")
                public void put(Object map, Object key, Object value) { ((org.jugsaxony.clock.demo6.LRUClockMap<String, Integer>) map).put((String) key, (Integer) value); }
            }),
            new ImplementationMeta("demo7", "Demo 7", "Qwen 38 max XHigh (Kilo Code)", org.jugsaxony.clock.demo7.LRUClockMap.class, new MapFactory() {
                public Object create(int maxSize) { return org.jugsaxony.clock.demo7.LRUClockMap.of(maxSize); }
                @SuppressWarnings("unchecked")
                public void put(Object map, Object key, Object value) { ((org.jugsaxony.clock.demo7.LRUClockMap<String, Integer>) map).put((String) key, (Integer) value); }
            }),
            new ImplementationMeta("demo8", "Demo 8", "Gemini 3.7 Flash High (Kilo Code)", org.jugsaxony.clock.demo8.LRUClockMap.class, new MapFactory() {
                public Object create(int maxSize) { return org.jugsaxony.clock.demo8.LRUClockMap.of(maxSize); }
                @SuppressWarnings("unchecked")
                public void put(Object map, Object key, Object value) { ((org.jugsaxony.clock.demo8.LRUClockMap<String, Integer>) map).put((String) key, (Integer) value); }
            }),
            new ImplementationMeta("demo9", "Demo 9", "Gemini 3.8 Flash High (Antigravity)", org.jugsaxony.clock.demo9.LRUClockMap.class, new MapFactory() {
                public Object create(int maxSize) { return org.jugsaxony.clock.demo9.LRUClockMap.of(maxSize); }
                @SuppressWarnings("unchecked")
                public void put(Object map, Object key, Object value) { ((org.jugsaxony.clock.demo9.LRUClockMap<String, Integer>) map).put((String) key, (Integer) value); }
            })
    );

    public static final int[] TEST_SIZES = {100, 1_000, 10_000};

    public static List<MemoryMetrics> collectMetrics() {
        List<MemoryMetrics> results = new ArrayList<>();

        int maxSize = 10_000;
        String[] keys = new String[maxSize];
        Integer[] values = new Integer[maxSize];
        for (int i = 0; i < maxSize; i++) {
            keys[i] = "key_" + i;
            values[i] = i;
        }

        for (ImplementationMeta impl : IMPLEMENTATIONS) {
            ClassLayout cl = ClassLayout.parseClass(impl.mapClass());
            int shallowSize = (int) cl.instanceSize();

            Object emptyMap = impl.factory().create(16);
            GraphLayout emptyGl = GraphLayout.parseInstance(emptyMap);
            long emptyFootprint = emptyGl.totalSize();
            long emptyObjectCount = emptyGl.totalCount();

            Map<Integer, Long> footprintBySize = new LinkedHashMap<>();
            Map<Integer, Long> objectCountBySize = new LinkedHashMap<>();
            Map<Integer, Double> bytesPerEntryBySize = new LinkedHashMap<>();

            for (int size : TEST_SIZES) {
                Object populatedMap = impl.factory().create(size);
                for (int i = 0; i < size; i++) {
                    impl.factory().put(populatedMap, keys[i], values[i]);
                }
                GraphLayout gl = GraphLayout.parseInstance(populatedMap);
                long totalSize = gl.totalSize();
                long totalCount = gl.totalCount();
                double bpe = (double) totalSize / size;

                footprintBySize.put(size, totalSize);
                objectCountBySize.put(size, totalCount);
                bytesPerEntryBySize.put(size, bpe);
            }

            results.add(new MemoryMetrics(
                    impl.id(),
                    impl.name(),
                    impl.model(),
                    shallowSize,
                    emptyFootprint,
                    emptyObjectCount,
                    footprintBySize,
                    objectCountBySize,
                    bytesPerEntryBySize,
                    cl.toPrintable()
            ));
        }

        return results;
    }

    public static void generateReports(File outputDir) throws IOException {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        List<MemoryMetrics> metrics = collectMetrics();

        generateMarkdownReport(new File(outputDir, "jol-report.md"), metrics);
        generateHtmlReport(new File(outputDir, "jol-report.html"), metrics);
    }

    public static void generateMarkdownReport(File targetFile, List<MemoryMetrics> metrics) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(targetFile))) {
            out.println("# Java Object Layout (JOL) Cross-Project Memory Footprint Report");
            out.println();
            out.println("Comprehensive memory layout and footprint analysis comparing all LRUClockMap implementations.");
            out.println();
            out.println("## 1. Footprint & Efficiency Comparison");
            out.println();
            out.println("| Implementation | Model | Shallow Size | Empty (B) | N=100 (B) | N=100 (B/entry) | N=1,000 (B) | N=1,000 (B/entry) | N=10,000 (B) | N=10,000 (B/entry) |");
            out.println("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |");

            for (MemoryMetrics m : metrics) {
                out.printf("| **%s** | %s | %d B | %,d B | %,d B | %.1f | %,d B | %.1f | %,d B | %.1f |%n",
                        m.name(),
                        m.model(),
                        m.shallowSize(),
                        m.emptyFootprint(),
                        m.footprintBySize().get(100),
                        m.bytesPerEntryBySize().get(100),
                        m.footprintBySize().get(1000),
                        m.bytesPerEntryBySize().get(1000),
                        m.footprintBySize().get(10000),
                        m.bytesPerEntryBySize().get(10000)
                );
            }
            out.println();
            out.println("## 2. Total Internal Object Count (GC Pressure)");
            out.println();
            out.println("| Implementation | Model | Empty Objects | Objects @ N=100 | Objects @ N=1,000 | Objects @ N=10,000 | Memory Strategy |");
            out.println("| :--- | :--- | :--- | :--- | :--- | :--- | :--- |");

            for (MemoryMetrics m : metrics) {
                long n10000Objs = m.objectCountBySize().get(10000);
                String strategy = (n10000Objs > 15000) ? "Node/Entry Objects" : "Flat Arrays (Cache-friendly)";
                out.printf("| **%s** | %s | %,d | %,d | %,d | %,d | %s |%n",
                        m.name(),
                        m.model(),
                        m.emptyObjectCount(),
                        m.objectCountBySize().get(100),
                        m.objectCountBySize().get(1000),
                        n10000Objs,
                        strategy
                );
            }
            out.println();
            out.println("## 3. Class Layout Details");
            out.println();
            for (MemoryMetrics m : metrics) {
                out.println("### " + m.name() + " (" + m.model() + ")");
                out.println("```");
                out.println(m.classLayoutSummary().trim());
                out.println("```");
                out.println();
            }
        }
    }

    public static void generateHtmlReport(File targetFile, List<MemoryMetrics> metrics) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(targetFile))) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("    <meta charset=\"UTF-8\">");
            out.println("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            out.println("    <title>JOL Memory Layout & Footprint Report - AI JUG Saxony</title>");
            out.println("    <style>");
            out.println("        :root { --bg: #f8fafc; --card-bg: #ffffff; --text: #0f172a; --text-muted: #64748b; --border: #e2e8f0; --primary: #3b82f6; --success: #10b981; --accent: #8b5cf6; }");
            out.println("        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: var(--bg); color: var(--text); margin: 0; padding: 2rem; }");
            out.println("        .container { max-width: 1300px; margin: 0 auto; }");
            out.println("        .header { margin-bottom: 2rem; padding-bottom: 1rem; border-bottom: 2px solid var(--border); }");
            out.println("        .header h1 { margin: 0 0 0.5rem 0; font-size: 2rem; color: #1e293b; }");
            out.println("        .header p { margin: 0; color: var(--text-muted); font-size: 1.1rem; }");
            out.println("        .card { background: var(--card-bg); border-radius: 10px; border: 1px solid var(--border); padding: 1.5rem; margin-bottom: 2rem; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }");
            out.println("        h2 { font-size: 1.4rem; margin-top: 0; margin-bottom: 1rem; color: #1e293b; display: flex; align-items: center; gap: 0.5rem; }");
            out.println("        table { width: 100%; border-collapse: collapse; margin-top: 1rem; }");
            out.println("        th, td { padding: 0.75rem 1rem; text-align: left; border-bottom: 1px solid var(--border); }");
            out.println("        th { background: #f1f5f9; font-weight: 600; font-size: 0.875rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); }");
            out.println("        tr:hover { background: #f8fafc; }");
            out.println("        .badge { display: inline-block; padding: 0.25rem 0.5rem; border-radius: 9999px; font-size: 0.75rem; font-weight: 600; }");
            out.println("        .badge-primary { background: #dbeafe; color: #1d4ed8; }");
            out.println("        .badge-success { background: #d1fae5; color: #065f46; }");
            out.println("        .badge-flat { background: #ede9fe; color: #5b21b6; }");
            out.println("        .badge-node { background: #fee2e2; color: #991b1b; }");
            out.println("        .numeric { text-align: right; font-variant-numeric: tabular-nums; }");
            out.println("        pre { background: #f8fafc; color: #0f172a; border: 1px solid #e2e8f0; padding: 1rem; border-radius: 6px; overflow-x: auto; font-size: 0.85rem; font-family: monospace; }");
            out.println("    </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("<div class=\"container\">");
            out.println("    <div class=\"header\">");
            out.println("        <h1>🧠 JOL Cross-Project Memory Footprint Report</h1>");
            out.println("        <p>Evaluating Memory Layout, Footprint & Object Overhead for LRUClockMap Implementations</p>");
            out.println("    </div>");

            out.println("    <div class=\"card\">");
            out.println("        <h2>📊 Memory Footprint & Efficiency (Bytes / Entry)</h2>");
            out.println("        <table>");
            out.println("            <thead>");
            out.println("                <tr>");
            out.println("                    <th>Implementation</th>");
            out.println("                    <th>Model</th>");
            out.println("                    <th class=\"numeric\">Shallow Size</th>");
            out.println("                    <th class=\"numeric\">Empty</th>");
            out.println("                    <th class=\"numeric\">N=100 (Total)</th>");
            out.println("                    <th class=\"numeric\">N=100 (B/entry)</th>");
            out.println("                    <th class=\"numeric\">N=1,000 (Total)</th>");
            out.println("                    <th class=\"numeric\">N=1,000 (B/entry)</th>");
            out.println("                    <th class=\"numeric\">N=10,000 (Total)</th>");
            out.println("                    <th class=\"numeric\">N=10,000 (B/entry)</th>");
            out.println("                </tr>");
            out.println("            </thead>");
            out.println("            <tbody>");

            for (MemoryMetrics m : metrics) {
                out.println("                <tr>");
                out.printf("                    <td><strong>%s</strong></td>%n", m.name());
                out.printf("                    <td><span class=\"badge badge-primary\">%s</span></td>%n", m.model());
                out.printf("                    <td class=\"numeric\">%d B</td>%n", m.shallowSize());
                out.printf("                    <td class=\"numeric\">%,d B</td>%n", m.emptyFootprint());
                out.printf("                    <td class=\"numeric\">%,d B</td>%n", m.footprintBySize().get(100));
                out.printf("                    <td class=\"numeric\"><strong>%.1f</strong></td>%n", m.bytesPerEntryBySize().get(100));
                out.printf("                    <td class=\"numeric\">%,d B</td>%n", m.footprintBySize().get(1000));
                out.printf("                    <td class=\"numeric\"><strong>%.1f</strong></td>%n", m.bytesPerEntryBySize().get(1000));
                out.printf("                    <td class=\"numeric\">%,d B</td>%n", m.footprintBySize().get(10000));
                out.printf("                    <td class=\"numeric\"><strong style=\"color: #2563eb;\">%.1f B</strong></td>%n", m.bytesPerEntryBySize().get(10000));
                out.println("                </tr>");
            }

            out.println("            </tbody>");
            out.println("        </table>");
            out.println("    </div>");

            out.println("    <div class=\"card\">");
            out.println("        <h2>🗑️ Total Internal Objects & GC Footprint</h2>");
            out.println("        <table>");
            out.println("            <thead>");
            out.println("                <tr>");
            out.println("                    <th>Implementation</th>");
            out.println("                    <th>Architecture</th>");
            out.println("                    <th class=\"numeric\">Empty Objects</th>");
            out.println("                    <th class=\"numeric\">Objects @ N=100</th>");
            out.println("                    <th class=\"numeric\">Objects @ N=1,000</th>");
            out.println("                    <th class=\"numeric\">Objects @ N=10,000</th>");
            out.println("                </tr>");
            out.println("            </thead>");
            out.println("            <tbody>");

            for (MemoryMetrics m : metrics) {
                long n10000Objs = m.objectCountBySize().get(10000);
                boolean isFlat = n10000Objs < 15000;
                String badge = isFlat ? "<span class=\"badge badge-flat\">Flat Arrays (Compact)</span>" : "<span class=\"badge badge-node\">Node-Based (High GC)</span>";

                out.println("                <tr>");
                out.printf("                    <td><strong>%s</strong></td>%n", m.name());
                out.printf("                    <td>%s</td>%n", badge);
                out.printf("                    <td class=\"numeric\">%,d</td>%n", m.emptyObjectCount());
                out.printf("                    <td class=\"numeric\">%,d</td>%n", m.objectCountBySize().get(100));
                out.printf("                    <td class=\"numeric\">%,d</td>%n", m.objectCountBySize().get(1000));
                out.printf("                    <td class=\"numeric\"><strong>%,d</strong></td>%n", n10000Objs);
                out.println("                </tr>");
            }

            out.println("            </tbody>");
            out.println("        </table>");
            out.println("    </div>");

            out.println("    <div class=\"card\">");
            out.println("        <h2>📐 Detailed Class Layouts</h2>");
            for (MemoryMetrics m : metrics) {
                out.printf("        <h3 style=\"margin-top: 1.5rem; margin-bottom: 0.5rem;\">%s &mdash; <small style=\"color: #64748b;\">%s</small></h3>%n", m.name(), m.model());
                out.println("        <pre><code>" + escapeHtml(m.classLayoutSummary().trim()) + "</code></pre>");
            }
            out.println("    </div>");

            out.println("</div>");
            out.println("</body>");
            out.println("</html>");
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static void main(String[] args) throws Exception {
        File rootDir = GlobalDashboardGenerator.findRootDir();
        File outputDir = new File(rootDir, "reports");
        if (args.length > 0) {
            outputDir = new File(args[0]);
        }
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        generateReports(outputDir);
        System.out.println("JOL Report generated successfully in: " + outputDir.getAbsolutePath());
    }
}

