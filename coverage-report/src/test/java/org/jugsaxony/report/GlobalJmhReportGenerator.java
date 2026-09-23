package org.jugsaxony.report;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class GlobalJmhReportGenerator {

    public record BenchmarkEntry(
            String fullBenchmarkName,
            String operation,
            String targetId,
            String modelName,
            double score,
            double scoreError,
            String unit,
            Map<String, Double> secondaryMetrics
    ) {
        public BenchmarkEntry(String fullBenchmarkName, String operation, String targetId, String modelName, double score, double scoreError, String unit) {
            this(fullBenchmarkName, operation, targetId, modelName, score, scoreError, unit, Collections.emptyMap());
        }

        public double getMetric(String name) {
            return secondaryMetrics.getOrDefault(name, 0.0);
        }

        public boolean hasMetric(String name) {
            return secondaryMetrics.containsKey(name);
        }

        public boolean hasPerfMetrics() {
            return hasMetric("cycles") || hasMetric("instructions") || hasMetric("IPC");
        }

        public double cycles() { return getMetric("cycles"); }
        public double instructions() { return getMetric("instructions"); }
        public double ipc() { return getMetric("IPC"); }
        public double cpi() { return getMetric("CPI"); }
        public double branches() { return getMetric("branches"); }
        public double branchMisses() { return getMetric("branch-misses"); }
        public double branchMissRate() {
            double b = branches();
            return b > 0 ? (branchMisses() / b) * 100.0 : 0.0;
        }
        public double l1DcacheLoads() { return getMetric("L1-dcache-loads"); }
        public double l1DcacheMisses() { return getMetric("L1-dcache-load-misses"); }
        public double l1DcacheMissRate() {
            double l = l1DcacheLoads();
            return l > 0 ? (l1DcacheMisses() / l) * 100.0 : 0.0;
        }
        public double l1IcacheLoads() { return getMetric("L1-icache-loads"); }
        public double l1IcacheMisses() { return getMetric("L1-icache-load-misses"); }
        public double stalledCyclesFrontend() { return getMetric("stalled-cycles-frontend"); }
    }

    public static final Map<String, String> MODEL_NAMES = Map.ofEntries(
            Map.entry("demo0", "Demo 0 (Human Baseline)"),
            Map.entry("demo1", "Demo 1 (Gemini 3.7 Flash High / Antigravity)"),
            Map.entry("demo2", "Demo 2 (Kimi K3)"),
            Map.entry("demo3", "Demo 3 (OpenAI 5.6 Sol Max)"),
            Map.entry("demo4", "Demo 4 (Gemma 4 31B Thinking)"),
            Map.entry("demo5", "Demo 5 (Deepseek V4 Flash Max)"),
            Map.entry("demo6", "Demo 6 (Claude Opus 5 Ultra)"),
            Map.entry("demo7", "Demo 7 (Qwen 38 max XHigh)"),
            Map.entry("demo8", "Demo 8 (Gemini 3.7 Flash High / Kilo Code)"),
            Map.entry("demo9", "Demo 9 (Gemini 3.8 Flash High / Antigravity)")
    );

    public static List<BenchmarkEntry> parseJmhJson(File jsonFile) throws IOException {
        String content = Files.readString(jsonFile.toPath());
        List<BenchmarkEntry> entries = new ArrayList<>();

        Object parsed;
        try {
            parsed = new MiniJsonParser(content).parse();
        } catch (Exception e) {
            System.err.println("Failed to parse JMH JSON: " + e.getMessage());
            return entries;
        }

        if (!(parsed instanceof List<?> list)) {
            return entries;
        }

        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            String benchmark = (String) map.get("benchmark");
            if (benchmark == null) continue;

            double score = 0.0;
            double scoreError = 0.0;
            String unit = "ops/us";

            if (map.get("primaryMetric") instanceof Map<?, ?> pm) {
                if (pm.get("score") instanceof Number n) score = n.doubleValue();
                if (pm.get("scoreError") instanceof Number n) scoreError = n.doubleValue();
                if (pm.get("scoreUnit") instanceof String s) unit = s;
            }

            Map<String, Double> secondary = new LinkedHashMap<>();
            if (map.get("secondaryMetrics") instanceof Map<?, ?> sm) {
                for (Map.Entry<?, ?> entry : sm.entrySet()) {
                    String metricName = String.valueOf(entry.getKey());
                    if (entry.getValue() instanceof Map<?, ?> metricObj) {
                        if (metricObj.get("score") instanceof Number num) {
                            secondary.put(metricName, num.doubleValue());
                        }
                    }
                }
            }

            String methodName = benchmark.substring(benchmark.lastIndexOf('.') + 1);
            String operation = "unknown";
            String targetId = "unknown";

            if (methodName.contains("_")) {
                String[] parts = methodName.split("_", 2);
                operation = parts[0];
                targetId = parts[1];
            }

            if ("javaMap".equals(targetId)) {
                continue;
            }

            String modelName = MODEL_NAMES.getOrDefault(targetId, targetId);
            entries.add(new BenchmarkEntry(benchmark, operation, targetId, modelName, score, scoreError, unit, secondary));
        }

        return entries;
    }

    public static void generateReports(File jsonFile, File outputDir) throws IOException {
        if (!jsonFile.exists()) {
            System.err.println("JMH JSON file not found: " + jsonFile.getAbsolutePath());
            return;
        }

        List<BenchmarkEntry> entries = parseJmhJson(jsonFile);
        if (entries.isEmpty()) {
            System.err.println("No benchmark entries parsed from: " + jsonFile.getAbsolutePath());
            return;
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        generateMarkdownReport(new File(outputDir, "jmh-report.md"), entries);
        generateHtmlReport(new File(outputDir, "jmh-report.html"), entries);
        generateCsvReport(new File(outputDir, "jmh-report.csv"), entries);
    }

    public static void generateMarkdownReport(File targetFile, List<BenchmarkEntry> entries) throws IOException {
        Map<String, List<BenchmarkEntry>> byOp = new LinkedHashMap<>();
        for (BenchmarkEntry e : entries) {
            byOp.computeIfAbsent(e.operation(), k -> new ArrayList<>()).add(e);
        }

        boolean anyPerf = entries.stream().anyMatch(BenchmarkEntry::hasPerfMetrics);

        try (PrintWriter out = new PrintWriter(new FileWriter(targetFile))) {
            out.println("# JMH Microbenchmark Cross-Project Comparison Report");
            out.println();
            out.println("Microbenchmark results comparing all AI model implementations against `demo0` baseline (Size = 1,000 items).");
            if (anyPerf) {
                out.println();
                out.println("> ⚡ **Hardware Performance Counters Enabled**: Includes Linux `perf` metrics (Cycles/op, Instructions/op, IPC, Branch Mispredictions, L1 D-Cache Misses).");
            }
            out.println();

            for (Map.Entry<String, List<BenchmarkEntry>> group : byOp.entrySet()) {
                String op = group.getKey();
                List<BenchmarkEntry> list = new ArrayList<>(group.getValue());
                list.sort((a, b) -> Double.compare(b.score(), a.score())); // Highest throughput first

                double baselineScore = list.stream()
                        .filter(e -> "demo0".equals(e.targetId()))
                        .mapToDouble(BenchmarkEntry::score)
                        .findFirst()
                        .orElse(1.0);

                boolean opHasPerf = list.stream().anyMatch(BenchmarkEntry::hasPerfMetrics);

                out.println("## Operation: `" + op + "`");
                out.println();

                if (opHasPerf) {
                    out.println("| Rank | Implementation | Model | Throughput (ops/µs) | Margin (±) | Speedup | Cycles/op | Insns/op | IPC | Branch Miss % | L1 D-Cache Miss % |");
                    out.println("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |");

                    int rank = 1;
                    for (BenchmarkEntry e : list) {
                        double speedup = e.score() / baselineScore;
                        String speedupStr = String.format("%.2fx", speedup);
                        if (speedup >= 1.05) {
                            speedupStr = "**" + speedupStr + " 🚀**";
                        } else if (speedup <= 0.95) {
                            speedupStr = speedupStr + " 🔻";
                        }

                        String cyclesStr = e.cycles() > 0 ? String.format("%.1f", e.cycles()) : "-";
                        String insnsStr = e.instructions() > 0 ? String.format("%.1f", e.instructions()) : "-";
                        String ipcStr = e.ipc() > 0 ? String.format("%.2f", e.ipc()) : "-";
                        String branchMissStr = e.branches() > 0 ? String.format("%.2f%%", e.branchMissRate()) : "-";
                        String l1MissStr = e.l1DcacheLoads() > 0 ? String.format("%.2f%%", e.l1DcacheMissRate()) : "-";

                        out.printf("| %d | **%s** | %s | %,.2f | ± %,.2f | %s | %s | %s | %s | %s | %s |%n",
                                rank++,
                                e.targetId(),
                                e.modelName(),
                                e.score(),
                                e.scoreError(),
                                speedupStr,
                                cyclesStr,
                                insnsStr,
                                ipcStr,
                                branchMissStr,
                                l1MissStr
                        );
                    }
                } else {
                    out.println("| Rank | Implementation | Model | Throughput (ops/µs) | Margin (±) | Speedup vs Demo 0 |");
                    out.println("| :--- | :--- | :--- | :--- | :--- | :--- |");

                    int rank = 1;
                    for (BenchmarkEntry e : list) {
                        double speedup = e.score() / baselineScore;
                        String speedupStr = String.format("%.2fx", speedup);
                        if (speedup >= 1.05) {
                            speedupStr = "**" + speedupStr + " 🚀**";
                        } else if (speedup <= 0.95) {
                            speedupStr = speedupStr + " 🔻";
                        }

                        out.printf("| %d | **%s** | %s | %,.2f | ± %,.2f | %s |%n",
                                rank++,
                                e.targetId(),
                                e.modelName(),
                                e.score(),
                                e.scoreError(),
                                speedupStr
                        );
                    }
                }
                out.println();
            }
        }
    }

    public static void generateCsvReport(File targetFile, List<BenchmarkEntry> entries) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(targetFile))) {
            out.println("Operation,TargetId,ModelName,Score_OpsPerMicrosec,ScoreError,Unit,Cycles_Per_Op,Instructions_Per_Op,IPC,Branch_Misses_Per_Op,Branch_Miss_Rate_Pct,L1_Dcache_Misses_Per_Op,L1_Dcache_Miss_Rate_Pct,CPI,Stalled_Cycles_Frontend");
            for (BenchmarkEntry e : entries) {
                out.printf("\"%s\",\"%s\",\"%s\",%.4f,%.4f,\"%s\",%.2f,%.2f,%.3f,%.3f,%.2f,%.3f,%.2f,%.3f,%.2f%n",
                        e.operation(), e.targetId(), e.modelName(), e.score(), e.scoreError(), e.unit(),
                        e.cycles(), e.instructions(), e.ipc(),
                        e.branchMisses(), e.branchMissRate(),
                        e.l1DcacheMisses(), e.l1DcacheMissRate(),
                        e.cpi(), e.stalledCyclesFrontend()
                );
            }
        }
    }

    public static void generateHtmlReport(File targetFile, List<BenchmarkEntry> entries) throws IOException {
        Map<String, List<BenchmarkEntry>> byOp = new LinkedHashMap<>();
        for (BenchmarkEntry e : entries) {
            byOp.computeIfAbsent(e.operation(), k -> new ArrayList<>()).add(e);
        }

        boolean anyPerf = entries.stream().anyMatch(BenchmarkEntry::hasPerfMetrics);

        try (PrintWriter out = new PrintWriter(new FileWriter(targetFile))) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("    <meta charset=\"UTF-8\">");
            out.println("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            out.println("    <title>JMH Cross-Project Performance Report - AI JUG Saxony</title>");
            out.println("    <style>");
            out.println("        :root {");
            out.println("            --bg: #f8fafc; --card-bg: #ffffff; --card-border: #e2e8f0;");
            out.println("            --text: #0f172a; --text-muted: #64748b; --border: #e2e8f0;");
            out.println("            --primary: #0284c7; --success: #16a34a; --accent: #7c3aed;");
            out.println("            --warning: #d97706; --danger: #dc2626;");
            out.println("        }");
            out.println("        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: var(--bg); color: var(--text); margin: 0; padding: 2rem; line-height: 1.5; }");
            out.println("        .container { max-width: 1400px; margin: 0 auto; }");
            out.println("        .header { margin-bottom: 2rem; padding-bottom: 1.5rem; border-bottom: 1px solid var(--border); display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 1rem; }");
            out.println("        .header h1 { margin: 0 0 0.5rem 0; font-size: 2.2rem; font-weight: 800; background: linear-gradient(135deg, #0f172a, #334155); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }");
            out.println("        .header p { margin: 0; color: var(--text-muted); font-size: 1.1rem; }");
            out.println("        .card { background: var(--card-bg); border-radius: 12px; border: 1px solid var(--card-border); padding: 1.75rem; margin-bottom: 2rem; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }");
            out.println("        h2 { font-size: 1.5rem; margin-top: 0; margin-bottom: 1.25rem; color: #0f172a; display: flex; align-items: center; justify-content: space-between; }");
            out.println("        table { width: 100%; border-collapse: separate; border-spacing: 0; margin-top: 1rem; border-radius: 8px; overflow: hidden; border: 1px solid var(--border); }");
            out.println("        th, td { padding: 0.85rem 1rem; text-align: left; border-bottom: 1px solid var(--border); }");
            out.println("        th { background: #f8fafc; font-weight: 600; font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); }");
            out.println("        tr:hover td { background: #f8fafc; }");
            out.println("        tr:last-child td { border-bottom: none; }");
            out.println("        .badge { display: inline-flex; align-items: center; padding: 0.2rem 0.55rem; border-radius: 9999px; font-size: 0.75rem; font-weight: 600; }");
            out.println("        .badge-winner { background: #dcfce7; color: #15803d; border: 1px solid #bbf7d0; }");
            out.println("        .badge-primary { background: #f0f9ff; color: #0284c7; border: 1px solid #bae6fd; }");
            out.println("        .badge-perf { background: #f3e8ff; color: #7e22ce; border: 1px solid #e9d5ff; }");
            out.println("        .numeric { text-align: right; font-variant-numeric: tabular-nums; }");
            out.println("        .bar-container { background: #e2e8f0; border-radius: 4px; width: 90px; height: 10px; display: inline-block; margin-right: 8px; vertical-align: middle; overflow: hidden; }");
            out.println("        .bar-fill { background: linear-gradient(90deg, #0284c7, #6366f1); height: 100%; border-radius: 4px; }");
            out.println("        .speedup-fast { color: #16a34a; font-weight: 700; }");
            out.println("        .speedup-slow { color: #dc2626; }");
            out.println("        .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }");
            out.println("        .kpi-card { background: #f8fafc; padding: 1rem 1.25rem; border-radius: 8px; border: 1px solid var(--border); }");
            out.println("        .kpi-label { font-size: 0.8rem; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 0.25rem; }");
            out.println("        .kpi-val { font-size: 1.4rem; font-weight: 700; color: #0f172a; }");
            out.println("        .kpi-sub { font-size: 0.8rem; color: var(--text-muted); margin-top: 0.2rem; }");
            out.println("        .perf-tag { font-size: 0.75rem; padding: 0.15rem 0.4rem; border-radius: 4px; background: #f1f5f9; border: 1px solid #e2e8f0; color: #334155; font-family: monospace; }");
            out.println("    </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("<div class=\"container\">");
            out.println("    <div class=\"header\">");
            out.println("        <div>");
            out.println("            <h1>⚡ JMH Microbenchmark Performance Report</h1>");
            out.println("            <p>Cross-Project Throughput & Micro-Architectural Analysis (Demo 0 to Demo 11)</p>");
            out.println("        </div>");
            if (anyPerf) {
                out.println("        <div><span class=\"badge badge-perf\">🔬 Linux perf Hardware Counters Active</span></div>");
            }
            out.println("    </div>");

            for (Map.Entry<String, List<BenchmarkEntry>> group : byOp.entrySet()) {
                String op = group.getKey();
                List<BenchmarkEntry> list = new ArrayList<>(group.getValue());
                list.sort((a, b) -> Double.compare(b.score(), a.score()));

                boolean opHasPerf = list.stream().anyMatch(BenchmarkEntry::hasPerfMetrics);
                double maxScore = list.stream().mapToDouble(BenchmarkEntry::score).max().orElse(1.0);
                double baselineScore = list.stream().filter(e -> "demo0".equals(e.targetId())).mapToDouble(BenchmarkEntry::score).findFirst().orElse(1.0);

                out.println("    <div class=\"card\">");
                out.printf("        <h2><span>Operation: <code>%s</code></span>%s</h2>%n",
                        op,
                        opHasPerf ? "<span class=\"badge badge-perf\">Hardware Counters</span>" : ""
                );

                if (opHasPerf) {
                    BenchmarkEntry topIpc = list.stream().filter(e -> e.ipc() > 0).max(Comparator.comparingDouble(BenchmarkEntry::ipc)).orElse(null);
                    BenchmarkEntry lowestCycles = list.stream().filter(e -> e.cycles() > 0).min(Comparator.comparingDouble(BenchmarkEntry::cycles)).orElse(null);
                    BenchmarkEntry lowestL1 = list.stream().filter(e -> e.l1DcacheLoads() > 0).min(Comparator.comparingDouble(BenchmarkEntry::l1DcacheMissRate)).orElse(null);
                    BenchmarkEntry lowestBranchMiss = list.stream().filter(e -> e.branches() > 0).min(Comparator.comparingDouble(BenchmarkEntry::branchMissRate)).orElse(null);

                    out.println("        <div class=\"kpi-grid\">");
                    if (!list.isEmpty()) {
                        out.printf("            <div class=\"kpi-card\"><div class=\"kpi-label\">Top Throughput</div><div class=\"kpi-val\">%,.2f <span style=\"font-size:0.9rem;\">ops/µs</span></div><div class=\"kpi-sub\">%s (%s)</div></div>%n",
                                list.get(0).score(), list.get(0).targetId(), String.format("%.2fx vs demo0", list.get(0).score() / baselineScore));
                    }
                    if (topIpc != null) {
                        out.printf("            <div class=\"kpi-card\"><div class=\"kpi-label\">Highest IPC</div><div class=\"kpi-val\" style=\"color:#0284c7;\">%.2f <span style=\"font-size:0.9rem;\">insns/clk</span></div><div class=\"kpi-sub\">%s</div></div>%n",
                                topIpc.ipc(), topIpc.targetId());
                    }
                    if (lowestCycles != null) {
                        out.printf("            <div class=\"kpi-card\"><div class=\"kpi-label\">Lowest Cycles/op</div><div class=\"kpi-val\" style=\"color:#16a34a;\">%.1f <span style=\"font-size:0.9rem;\">cycles</span></div><div class=\"kpi-sub\">%s</div></div>%n",
                                lowestCycles.cycles(), lowestCycles.targetId());
                    }
                    if (lowestL1 != null) {
                        out.printf("            <div class=\"kpi-card\"><div class=\"kpi-label\">Lowest L1 Miss %%</div><div class=\"kpi-val\" style=\"color:#7c3aed;\">%.2f%%</div><div class=\"kpi-sub\">%s</div></div>%n",
                                lowestL1.l1DcacheMissRate(), lowestL1.targetId());
                    }
                    if (lowestBranchMiss != null) {
                        out.printf("            <div class=\"kpi-card\"><div class=\"kpi-label\">Lowest Branch Miss %%</div><div class=\"kpi-val\" style=\"color:#d97706;\">%.2f%%</div><div class=\"kpi-sub\">%s</div></div>%n",
                                lowestBranchMiss.branchMissRate(), lowestBranchMiss.targetId());
                    }
                    out.println("        </div>");

                    out.println("        <table>");
                    out.println("            <thead>");
                    out.println("                <tr>");
                    out.println("                    <th>Rank</th>");
                    out.println("                    <th>Target</th>");
                    out.println("                    <th>Model</th>");
                    out.println("                    <th class=\"numeric\">Throughput (ops/µs)</th>");
                    out.println("                    <th class=\"numeric\">Margin (±)</th>");
                    out.println("                    <th class=\"numeric\">Speedup</th>");
                    out.println("                    <th class=\"numeric\">Cycles/op</th>");
                    out.println("                    <th class=\"numeric\">Insns/op</th>");
                    out.println("                    <th class=\"numeric\">IPC</th>");
                    out.println("                    <th class=\"numeric\">Branch Miss %</th>");
                    out.println("                    <th class=\"numeric\">L1 Miss %</th>");
                    out.println("                </tr>");
                    out.println("            </thead>");
                    out.println("            <tbody>");

                    int rank = 1;
                    for (BenchmarkEntry e : list) {
                        double pct = (e.score() / maxScore) * 100.0;
                        double speedup = e.score() / baselineScore;
                        String speedupClass = speedup >= 1.05 ? "speedup-fast" : (speedup <= 0.95 ? "speedup-slow" : "");
                        String rankBadge = (rank == 1) ? "<span class=\"badge badge-winner\">🥇 #1</span>" : ("#" + rank);

                        String cyclesStr = e.cycles() > 0 ? String.format("%.1f", e.cycles()) : "-";
                        String insnsStr = e.instructions() > 0 ? String.format("%.1f", e.instructions()) : "-";
                        String ipcStr = e.ipc() > 0 ? String.format("%.2f", e.ipc()) : "-";
                        String branchMissStr = e.branches() > 0 ? String.format("%.2f%%", e.branchMissRate()) : "-";
                        String l1MissStr = e.l1DcacheLoads() > 0 ? String.format("%.2f%%", e.l1DcacheMissRate()) : "-";

                        out.println("                <tr>");
                        out.printf("                    <td>%s</td>%n", rankBadge);
                        out.printf("                    <td><strong>%s</strong></td>%n", e.targetId());
                        out.printf("                    <td><span class=\"badge badge-primary\">%s</span></td>%n", e.modelName());
                        out.printf("                    <td class=\"numeric\"><div class=\"bar-container\"><div class=\"bar-fill\" style=\"width: %.1f%%;\"></div></div> <strong>%,.2f</strong></td>%n", pct, e.score());
                        out.printf("                    <td class=\"numeric\" style=\"color:var(--text-muted);\">± %,.2f</td>%n", e.scoreError());
                        out.printf("                    <td class=\"numeric\"><span class=\"%s\">%.2fx</span></td>%n", speedupClass, speedup);
                        out.printf("                    <td class=\"numeric\"><span class=\"perf-tag\">%s</span></td>%n", cyclesStr);
                        out.printf("                    <td class=\"numeric\"><span class=\"perf-tag\">%s</span></td>%n", insnsStr);
                        out.printf("                    <td class=\"numeric\"><strong>%s</strong></td>%n", ipcStr);
                        out.printf("                    <td class=\"numeric\">%s</td>%n", branchMissStr);
                        out.printf("                    <td class=\"numeric\">%s</td>%n", l1MissStr);
                        out.println("                </tr>");
                        rank++;
                    }

                    out.println("            </tbody>");
                    out.println("        </table>");
                } else {
                    out.println("        <table>");
                    out.println("            <thead>");
                    out.println("                <tr>");
                    out.println("                    <th>Rank</th>");
                    out.println("                    <th>Target</th>");
                    out.println("                    <th>Model</th>");
                    out.println("                    <th class=\"numeric\">Throughput (ops/µs)</th>");
                    out.println("                    <th class=\"numeric\">Margin (±)</th>");
                    out.println("                    <th class=\"numeric\">Speedup vs Demo 0</th>");
                    out.println("                </tr>");
                    out.println("            </thead>");
                    out.println("            <tbody>");

                    int rank = 1;
                    for (BenchmarkEntry e : list) {
                        double pct = (e.score() / maxScore) * 100.0;
                        double speedup = e.score() / baselineScore;
                        String speedupClass = speedup >= 1.05 ? "speedup-fast" : (speedup <= 0.95 ? "speedup-slow" : "");
                        String rankBadge = (rank == 1) ? "<span class=\"badge badge-winner\">🥇 #1</span>" : ("#" + rank);

                        out.println("                <tr>");
                        out.printf("                    <td>%s</td>%n", rankBadge);
                        out.printf("                    <td><strong>%s</strong></td>%n", e.targetId());
                        out.printf("                    <td><span class=\"badge badge-primary\">%s</span></td>%n", e.modelName());
                        out.printf("                    <td class=\"numeric\"><div class=\"bar-container\"><div class=\"bar-fill\" style=\"width: %.1f%%;\"></div></div> <strong>%,.2f</strong></td>%n", pct, e.score());
                        out.printf("                    <td class=\"numeric\" style=\"color:var(--text-muted);\">± %,.2f</td>%n", e.scoreError());
                        out.printf("                    <td class=\"numeric\"><span class=\"%s\">%.2fx</span></td>%n", speedupClass, speedup);
                        out.println("                </tr>");
                        rank++;
                    }

                    out.println("            </tbody>");
                    out.println("        </table>");
                }
                out.println("    </div>");
            }

            out.println("</div>");
            out.println("</body>");
            out.println("</html>");
        }
    }

    public static void main(String[] args) throws Exception {
        File rootDir = GlobalDashboardGenerator.findRootDir();
        File jsonFile = new File(rootDir, "reports/jmh-results.json");
        if (!jsonFile.exists()) {
            jsonFile = new File(rootDir, "coverage-report/target/reports/jmh-results.json");
        }
        if (!jsonFile.exists()) {
            jsonFile = new File(rootDir, "target/reports/jmh-results.json");
        }

        File reportsDir = new File(rootDir, "reports");
        if (args.length > 0) {
            reportsDir = new File(args[0]);
        }
        if (!reportsDir.exists()) {
            reportsDir.mkdirs();
        }
        generateReports(jsonFile, reportsDir);

        System.out.println("Reports generated successfully in " + reportsDir.getAbsolutePath());
    }

    // ==========================================
    // Mini Lightweight Zero-Dependency JSON Parser
    // ==========================================

    public static class MiniJsonParser {
        private final String src;
        private int pos = 0;

        public MiniJsonParser(String src) {
            this.src = src != null ? src : "";
        }

        public Object parse() {
            skipWhitespace();
            Object val = parseValue();
            skipWhitespace();
            return val;
        }

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        private Object parseValue() {
            skipWhitespace();
            if (pos >= src.length()) return null;
            char c = src.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // '{'
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (pos < src.length()) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ':') {
                    pos++;
                }
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ',') {
                    pos++;
                } else if (pos < src.length() && src.charAt(pos) == '}') {
                    pos++;
                    break;
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // '['
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (pos < src.length()) {
                Object val = parseValue();
                list.add(val);
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ',') {
                    pos++;
                } else if (pos < src.length() && src.charAt(pos) == ']') {
                    pos++;
                    break;
                }
            }
            return list;
        }

        private String parseString() {
            pos++; // opening '"'
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') break;
                if (c == '\\') {
                    if (pos < src.length()) {
                        char esc = src.charAt(pos++);
                        if (esc == 'n') sb.append('\n');
                        else if (esc == 'r') sb.append('\r');
                        else if (esc == 't') sb.append('\t');
                        else if (esc == 'b') sb.append('\b');
                        else if (esc == 'f') sb.append('\f');
                        else sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", pos)) { pos += 4; return true; }
            if (src.startsWith("false", pos)) { pos += 5; return false; }
            return false;
        }

        private Object parseNull() {
            if (src.startsWith("null", pos)) { pos += 4; }
            return null;
        }

        private Number parseNumber() {
            int start = pos;
            if (pos < src.length() && (src.charAt(pos) == '-' || src.charAt(pos) == '+')) {
                pos++;
            }
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (Character.isDigit(c) || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    pos++;
                } else {
                    break;
                }
            }
            String s = src.substring(start, pos);
            try {
                if (s.contains(".") || s.contains("e") || s.contains("E")) {
                    return Double.parseDouble(s);
                } else {
                    return Long.parseLong(s);
                }
            } catch (Exception e) {
                return 0.0;
            }
        }
    }
}
