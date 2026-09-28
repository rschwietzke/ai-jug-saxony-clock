package org.jugsaxony.report;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GlobalDashboardGenerator {

    public record QualityStats(
            int tests,
            double executionTimeSeconds,
            int failures,
            int errors,
            double instructionCoveragePct,
            double lineCoveragePct,
            double branchCoveragePct,
            int missedInstructions,
            int totalInstructions,
            int missedBranches,
            int totalBranches,
            int missedLines,
            int totalLines,
            int pitKilled,
            int pitTotal,
            double pitScorePct
    ) {}

    public record LruClockMapSummary(
            String id,
            String name,
            String aiModel,
            String storageStrategy,
            QualityStats quality
    ) {}

    public record ModuleInfo(String id, String name, String model, String storageStrategy) {}

    public static final List<ModuleInfo> MODULES = List.of(
            new ModuleInfo("demo0", "Demo 0", "Human Baseline", "Circular buffer + Wrapper nodes"),
            new ModuleInfo("demo1", "Demo 1", "Gemini 3.7 Flash High (Antigravity)", "Circular buffer + Wrapper nodes"),
            new ModuleInfo("demo2", "Demo 2", "Kimi K3 (Kilo Code)", "Circular buffer + Wrapper nodes"),
            new ModuleInfo("demo3", "Demo 3", "OpenAI 5.6 Sol Max (Kilo Code)", "Circular buffer + Wrapper nodes"),
            new ModuleInfo("demo4", "Demo 4", "Gemma 4 31B Thinking (Kilo Code)", "Circular buffer + Wrapper nodes"),
            new ModuleInfo("demo5", "Demo 5", "Deepseek V4 Flash Max (Kilo Code)", "Circular buffer + Wrapper nodes"),
            new ModuleInfo("demo6", "Demo 6", "Claude Opus 5 Ultra (Claude)", "Circular buffer + Wrapper nodes"),
            new ModuleInfo("demo7", "Demo 7", "Qwen 38 max XHigh (Kilo Code)", "Circular buffer + Wrapper nodes"),
            new ModuleInfo("demo8", "Demo 8", "Gemini 3.7 Flash High (Kilo Code)", "Circular buffer + Wrapper nodes"),
            new ModuleInfo("demo9", "Demo 9", "Gemini 3.8 Flash High (Antigravity)", "Circular buffer + Wrapper nodes")
    );

    public static void generateDashboard(File outputDir, File rootProjectDir) throws Exception {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // Copy JaCoCo aggregate report if available
        File jacocoSite = new File(rootProjectDir, "coverage-report/target/site/jacoco-aggregate");
        File destCoverage = new File(outputDir, "coverage-aggregate");
        if (jacocoSite.exists() && jacocoSite.isDirectory()) {
            copyDirectory(jacocoSite, destCoverage);
        }

        String[] modDirs = {"demo0", "demo1", "demo2", "demo3", "demo4", "demo5", "demo6", "demo7", "demo8", "demo9"};

        // Copy individual module JaCoCo reports
        for (String modDirName : modDirs) {
            File modJacoco = new File(rootProjectDir, modDirName + "/target/site/jacoco");
            File destModJacoco = new File(outputDir, "jacoco/" + modDirName);
            if (modJacoco.exists() && modJacoco.isDirectory()) {
                copyDirectory(modJacoco, destModJacoco);
            }
        }

        // Copy PIT reports per module if available
        for (String modDirName : modDirs) {
            File modPit = new File(rootProjectDir, modDirName + "/target/pit-reports");
            File destModPit = new File(outputDir, "pit-reports/" + modDirName);
            if (modPit.exists() && modPit.isDirectory()) {
                copyDirectory(modPit, destModPit);
            }
        }

        // Gather LRUClockMap data per module
        List<LruClockMapSummary> lruMapSummaries = new ArrayList<>();

        for (ModuleInfo meta : MODULES) {
            String modId = meta.id();
            String storageStrategy = meta.storageStrategy();

            File modDir = new File(rootProjectDir, modId);
            File surefireDir = new File(modDir, "target/surefire-reports");
            File jacocoXml = new File(outputDir, "jacoco/" + modId + "/jacoco.xml");
            if (!jacocoXml.exists()) {
                jacocoXml = new File(modDir, "target/site/jacoco/jacoco.xml");
            }
            File pitCsv = new File(outputDir, "pit-reports/" + modId + "/mutations.csv");
            if (!pitCsv.exists()) {
                pitCsv = new File(modDir, "target/pit-reports/mutations.csv");
            }

            QualityStats lruQuality = buildQualityStats(surefireDir, jacocoXml, pitCsv, "LRUClockMapTest", "LRUClockMap.java");

            lruMapSummaries.add(new LruClockMapSummary(
                    modId,
                    meta.name(),
                    meta.model(),
                    storageStrategy,
                    lruQuality
            ));
        }

        Map<String, String[]> moduleMetadata = new HashMap<>();
        for (ModuleInfo m : MODULES) {
            moduleMetadata.put(m.id(), new String[]{m.name(), m.model(), m.storageStrategy()});
        }
        SourceViewerGenerator.generateSourceSnapshots(outputDir, rootProjectDir, modDirs, moduleMetadata);

        // Write Markdown Dashboard
        writeMarkdownDashboard(new File(outputDir, "README.md"), lruMapSummaries);
        writeMarkdownDashboard(new File(outputDir, "global-dashboard.md"), lruMapSummaries);

        // Write Master HTML Dashboard
        writeHtmlDashboard(new File(outputDir, "index.html"), lruMapSummaries);
        writeHtmlDashboard(new File(outputDir, "global-dashboard.html"), lruMapSummaries);
        writeHtmlDashboard(new File(outputDir, "lruclockmap.html"), lruMapSummaries);

        // Write Surefire Aggregated Unit Test Report
        writeSurefireHtmlReport(new File(outputDir, "surefire.html"), outputDir, rootProjectDir, lruMapSummaries);
    }

    private record TestExecutionInfo(int tests, double executionTimeSeconds, int failures, int errors) {}

    private static TestExecutionInfo parseTestExecution(File surefireDir, String testPattern, boolean isPrefix) {
        if (!surefireDir.exists() || !surefireDir.isDirectory()) return new TestExecutionInfo(0, 0.0, 0, 0);
        File[] files = surefireDir.listFiles((dir, name) -> {
            if (!name.startsWith("TEST-") || !name.endsWith(".xml")) return false;
            if (isPrefix) {
                return name.startsWith("TEST-" + testPattern);
            } else {
                return name.contains(testPattern);
            }
        });
        if (files == null) return new TestExecutionInfo(0, 0.0, 0, 0);

        int totalTests = 0;
        double totalTime = 0.0;
        int totalFailures = 0;
        int totalErrors = 0;

        Pattern tcPat = Pattern.compile("<testcase\\b[^>]*\\btime=\"([0-9.]+)\"");
        Pattern suiteTestsPat = Pattern.compile("\\btests=\"([0-9]+)\"");
        Pattern suiteTimePat = Pattern.compile("\\btime=\"([0-9.]+)\"");
        Pattern suiteFailuresPat = Pattern.compile("\\bfailures=\"([0-9]+)\"");
        Pattern suiteErrorsPat = Pattern.compile("\\berrors=\"([0-9]+)\"");

        for (File f : files) {
            try {
                String content = Files.readString(f.toPath());
                int fileTests = 0;
                double fileTime = 0.0;
                int fileFailures = 0;
                int fileErrors = 0;

                Matcher mTests = suiteTestsPat.matcher(content);
                if (mTests.find()) fileTests = Integer.parseInt(mTests.group(1));

                Matcher mTime = suiteTimePat.matcher(content);
                if (mTime.find()) fileTime = Double.parseDouble(mTime.group(1));

                Matcher mFail = suiteFailuresPat.matcher(content);
                if (mFail.find()) fileFailures = Integer.parseInt(mFail.group(1));

                Matcher mErr = suiteErrorsPat.matcher(content);
                if (mErr.find()) fileErrors = Integer.parseInt(mErr.group(1));

                if (fileTests == 0) {
                    Matcher m = tcPat.matcher(content);
                    while (m.find()) {
                        fileTests++;
                        try {
                            fileTime += Double.parseDouble(m.group(1));
                        } catch (Exception ignored) {}
                    }
                }

                totalTests += fileTests;
                totalTime += fileTime;
                totalFailures += fileFailures;
                totalErrors += fileErrors;
            } catch (Exception ignored) {}
        }

        return new TestExecutionInfo(totalTests, totalTime, totalFailures, totalErrors);
    }

    public record FailedTestCase(String className, String testName, String failureType, String message) {}

    private static List<FailedTestCase> parseFailedTestCases(File surefireDir, String testPattern, boolean isPrefix) {
        List<FailedTestCase> failures = new ArrayList<>();
        if (!surefireDir.exists() || !surefireDir.isDirectory()) return failures;

        File[] files = surefireDir.listFiles((dir, name) -> {
            if (!name.startsWith("TEST-") || !name.endsWith(".xml")) return false;
            if (isPrefix) {
                return name.startsWith("TEST-" + testPattern);
            } else {
                return name.contains(testPattern);
            }
        });
        if (files == null) return failures;

        Pattern tcBlockPat = Pattern.compile("<testcase\\b([^>]*?)>((?:(?!<testcase\\b).)*?)</testcase>", Pattern.DOTALL);
        Pattern namePat = Pattern.compile("\\bname=\"([^\"]+)\"");
        Pattern classnamePat = Pattern.compile("\\bclassname=\"([^\"]+)\"");
        Pattern failPat = Pattern.compile("<(failure|error)\\b([^>]*?)>(.*?)</\\1>", Pattern.DOTALL);
        Pattern msgPat = Pattern.compile("\\bmessage=\"([^\"]*)\"");
        Pattern typePat = Pattern.compile("\\btype=\"([^\"]*)\"");

        for (File f : files) {
            try {
                String content = Files.readString(f.toPath());
                Matcher tcMatcher = tcBlockPat.matcher(content);
                while (tcMatcher.find()) {
                    String tcAttrs = tcMatcher.group(1);
                    if (tcAttrs.trim().endsWith("/")) continue;
                    String tcBody = tcMatcher.group(2);

                    Matcher failMatcher = failPat.matcher(tcBody);
                    if (failMatcher.find()) {
                        String failAttrs = failMatcher.group(2);
                        String failBody = failMatcher.group(3).trim();

                        String tName = "unknown";
                        Matcher nm = namePat.matcher(tcAttrs);
                        if (nm.find()) tName = nm.group(1);

                        String cName = f.getName().replace("TEST-", "").replace(".xml", "");
                        Matcher cm = classnamePat.matcher(tcAttrs);
                        if (cm.find()) cName = cm.group(1);

                        String msg = "";
                        Matcher mm = msgPat.matcher(failAttrs);
                        if (mm.find()) {
                            msg = mm.group(1).replace("&#10;", " ").replace("\n", " ").trim();
                        }
                        if (msg.isEmpty() && !failBody.isEmpty()) {
                            int nl = failBody.indexOf('\n');
                            msg = (nl > 0 ? failBody.substring(0, nl) : failBody).trim();
                        }

                        String type = "Failure";
                        Matcher tm = typePat.matcher(failAttrs);
                        if (tm.find()) type = tm.group(1);

                        failures.add(new FailedTestCase(cName, tName, type, msg));
                    }
                }
            } catch (Exception ignored) {}
        }
        return failures;
    }

    private static QualityStats buildQualityStats(File surefireDir, File jacocoXml, File pitCsv, String testPattern, String sourceFileName) {
        TestExecutionInfo testInfo = parseTestExecution(surefireDir, testPattern, false);
        int[] covInst = parseSourceCoverage(jacocoXml, sourceFileName, "INSTRUCTION");
        int[] covBranch = parseSourceCoverage(jacocoXml, sourceFileName, "BRANCH");
        int[] covLine = parseSourceCoverage(jacocoXml, sourceFileName, "LINE");

        double instPct = covInst[1] + covInst[0] > 0 ? (covInst[1] * 100.0) / (covInst[1] + covInst[0]) : 0.0;
        double branchPct = covBranch[1] + covBranch[0] > 0 ? (covBranch[1] * 100.0) / (covBranch[1] + covBranch[0]) : 0.0;
        double linePct = covLine[1] + covLine[0] > 0 ? (covLine[1] * 100.0) / (covLine[1] + covLine[0]) : 0.0;

        int[] pit = parsePitCoverage(pitCsv, sourceFileName);
        double pitPct = pit[1] > 0 ? (pit[0] * 100.0) / pit[1] : 0.0;

        return new QualityStats(
                testInfo.tests(),
                testInfo.executionTimeSeconds(),
                testInfo.failures(),
                testInfo.errors(),
                instPct,
                linePct,
                branchPct,
                covInst[0],
                covInst[0] + covInst[1],
                covBranch[0],
                covBranch[0] + covBranch[1],
                covLine[0],
                covLine[0] + covLine[1],
                pit[0],
                pit[1],
                pitPct
        );
    }

    private static int[] parseSourceCoverage(File jacocoXml, String sourceFileName, String counterType) {
        if (!jacocoXml.exists()) return new int[]{0, 0};
        try {
            String content = Files.readString(jacocoXml.toPath());
            Pattern p = Pattern.compile("<sourcefile\\b[^>]*\\bname=\"" + Pattern.quote(sourceFileName) + "\"(.*?)</sourcefile>", Pattern.DOTALL);
            Matcher m = p.matcher(content);
            if (m.find()) {
                String sfContent = m.group(1);
                Pattern cPattern = Pattern.compile("<counter\\b[^>]*\\btype=\"" + counterType + "\"\\s+missed=\"([0-9]+)\"\\s+covered=\"([0-9]+)\"");
                Matcher cm = cPattern.matcher(sfContent);
                if (cm.find()) {
                    return new int[]{Integer.parseInt(cm.group(1)), Integer.parseInt(cm.group(2))};
                }
            }
        } catch (Exception ignored) {}
        return new int[]{0, 0};
    }

    private static int[] parsePitCoverage(File pitCsv, String sourceFileName) {
        if (!pitCsv.exists()) return new int[]{0, 0};
        int killed = 0;
        int total = 0;
        String simpleClass = sourceFileName.replace(".java", "");
        try (BufferedReader reader = new BufferedReader(new FileReader(pitCsv))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 6) {
                    String fileCol = parts[0].trim();
                    String classCol = parts[1].trim();
                    if (fileCol.equals(sourceFileName) || fileCol.endsWith("/" + sourceFileName)
                            || classCol.equals(simpleClass) || classCol.endsWith("." + simpleClass)) {
                        total++;
                        String status = parts[5].trim();
                        if ("KILLED".equalsIgnoreCase(status) || "TIMED_OUT".equalsIgnoreCase(status) || "MEMORY_ERROR".equalsIgnoreCase(status)) {
                            killed++;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return new int[]{killed, total};
    }

    private static void writeMarkdownDashboard(File targetFile, List<LruClockMapSummary> lruMaps) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(targetFile))) {
            out.println("# ⏰ AI JUG Saxony — LRUClockMap Evaluation Dashboard");
            out.println();
            out.println("Comprehensive evaluation matrix across AI-generated second-chance LRU clock eviction cache implementations comparing code quality, test suites, JaCoCo coverage, and PIT mutation resilience against the Demo 0 baseline.");
            out.println();
            out.println("## ⏰ LRUClockMap — Quality, Coverage & Mutation Verification");
            out.println();
            out.println("| Module | AI Model / Implementation | Unit Tests | Instruction Coverage | Line Coverage | Branch Coverage | PIT Mutation Score | Status |");
            out.println("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |");

            for (LruClockMapSummary s : lruMaps) {
                String pitStr = s.quality().pitTotal() > 0 ? String.format("%.1f%% (%d/%d killed)", s.quality().pitScorePct(), s.quality().pitKilled(), s.quality().pitTotal()) : "N/A";
                String timeStr = s.quality().executionTimeSeconds() > 0 ? String.format(" (%.2fs)", s.quality().executionTimeSeconds()) : "";
                int total = s.quality().tests();
                int fails = s.quality().failures() + s.quality().errors();
                String statusStr = fails == 0 && total > 0 ? "100% Passing ✅" : (total == 0 ? "No Tests" : fails + " Failed ❌");

                out.printf("| %s | %s | %d%s | %.1f%% (%d/%d) | %.1f%% (%d/%d) | %.1f%% (%d/%d) | %s | %s |%n",
                        renderMarkdownModuleLink(s.id(), "LRUClockMap"),
                        s.aiModel(),
                        s.quality().tests(),
                        timeStr,
                        s.quality().instructionCoveragePct(),
                        s.quality().totalInstructions() - s.quality().missedInstructions(),
                        s.quality().totalInstructions(),
                        s.quality().lineCoveragePct(),
                        s.quality().totalLines() - s.quality().missedLines(),
                        s.quality().totalLines(),
                        s.quality().branchCoveragePct(),
                        s.quality().totalBranches() - s.quality().missedBranches(),
                        s.quality().totalBranches(),
                        pitStr,
                        statusStr
                );
            }

            out.println();
            out.println("---");
            out.println();
            out.println("## 🔗 Quick Links");
            out.println();
            out.println("- 📊 **Interactive HTML Dashboard**: [index.html](index.html)");
            out.println("- 🧪 **Surefire Unit Test Reports**: [surefire.html](surefire.html)");
        }
    }

    private static void writeHtmlDashboard(File targetFile, List<LruClockMapSummary> lruMaps) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(targetFile))) {
            int totalTests = lruMaps.stream().mapToInt(s -> s.quality().tests()).sum();
            double totalTime = lruMaps.stream().mapToDouble(s -> s.quality().executionTimeSeconds()).sum();
            int totalFailures = lruMaps.stream().mapToInt(s -> s.quality().failures() + s.quality().errors()).sum();
            int totalPassed = Math.max(0, totalTests - totalFailures);
            double passPct = totalTests > 0 ? (totalPassed * 100.0) / totalTests : 100.0;

            Optional<LruClockMapSummary> demo0Opt = lruMaps.stream().filter(s -> "demo0".equals(s.id())).findFirst();
            double demo0Pit = demo0Opt.map(s -> s.quality().pitScorePct()).orElse(0.0);
            int demo0PitKilled = demo0Opt.map(s -> s.quality().pitKilled()).orElse(0);
            int demo0PitTotal = demo0Opt.map(s -> s.quality().pitTotal()).orElse(0);

            Optional<LruClockMapSummary> topAiOpt = lruMaps.stream()
                    .filter(s -> !"demo0".equals(s.id()))
                    .max(Comparator.comparingDouble(s -> s.quality().pitScorePct()));
            double topAiPit = topAiOpt.map(s -> s.quality().pitScorePct()).orElse(0.0);
            String topAiName = topAiOpt.map(s -> s.name() + " (" + s.aiModel() + ")").orElse("N/A");

            String pageTitle = "AI JUG Saxony - LRUClockMap Evaluation Dashboard";

            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("    <meta charset=\"UTF-8\">");
            out.println("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            out.printf("    <title>%s</title>%n", pageTitle);
            out.println("    <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">");
            out.println("    <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>");
            out.println("    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600;700&display=swap\" rel=\"stylesheet\">");
            out.println("    <style>");
            out.println("        :root {");
            out.println("            --bg: #f8fafc;");
            out.println("            --card-bg: #ffffff;");
            out.println("            --card-border: #e2e8f0;");
            out.println("            --text: #0f172a;");
            out.println("            --text-muted: #64748b;");
            out.println("            --primary: #0284c7;");
            out.println("            --primary-glow: rgba(2, 132, 199, 0.12);");
            out.println("            --accent: #7c3aed;");
            out.println("            --accent-glow: rgba(124, 58, 237, 0.12);");
            out.println("            --success: #16a34a;");
            out.println("            --success-bg: #dcfce7;");
            out.println("            --warning: #d97706;");
            out.println("            --warning-bg: #fef3c7;");
            out.println("            --time-bg: #f1f5f9;");
            out.println("        }");
            out.println("        * { box-sizing: border-box; }");
            out.println("        body { font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: var(--bg); color: var(--text); margin: 0; padding: 2rem 1.5rem; line-height: 1.5; }");
            out.println("        .container { max-width: 1480px; margin: 0 auto; }");
            out.println("        .header { border-bottom: 1px solid var(--card-border); padding-bottom: 1.5rem; margin-bottom: 2rem; display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 1.5rem; }");
            out.println("        .header h1 { margin: 0; font-size: 2.1rem; font-weight: 800; letter-spacing: -0.025em; background: linear-gradient(135deg, #0f172a 0%, #0284c7 100%); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }");
            out.println("        .header p { margin: 0.5rem 0 0 0; color: var(--text-muted); font-size: 1.05rem; }");
            out.println("        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 1.25rem; margin-bottom: 2.25rem; }");
            out.println("        .stat-card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 14px; padding: 1.25rem; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }");
            out.println("        .stat-value { font-size: 1.9rem; font-weight: 800; font-family: 'JetBrains Mono', monospace; margin: 0.25rem 0; }");
            out.println("        .stat-sub { font-size: 0.8rem; color: var(--text-muted); }");
            out.println("        .stat-label { color: var(--text-muted); font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.06em; font-weight: 700; }");
            out.println("        .quick-actions { display: flex; gap: 0.75rem; margin-bottom: 2rem; flex-wrap: wrap; align-items: center; }");
            out.println("        .action-btn { background: var(--card-bg); border: 1px solid var(--card-border); color: #334155; padding: 0.55rem 1.1rem; border-radius: 8px; text-decoration: none; font-size: 0.875rem; font-weight: 600; transition: all 0.2s; display: inline-flex; align-items: center; gap: 0.5rem; box-shadow: 0 1px 2px rgba(0,0,0,0.04); }");
            out.println("        .action-btn:hover { background: #f8fafc; border-color: var(--primary); color: var(--primary); }");
            out.println("        .card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 14px; padding: 1.75rem; margin-bottom: 2.25rem; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }");
            out.println("        .section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem; flex-wrap: wrap; gap: 1rem; border-bottom: 1px solid var(--card-border); padding-bottom: 1rem; }");
            out.println("        .section-header h2 { margin: 0; font-size: 1.45rem; font-weight: 700; display: flex; align-items: center; gap: 0.6rem; color: #0f172a; }");
            out.println("        .section-desc { color: var(--text-muted); font-size: 0.95rem; margin: 0.25rem 0 0 0; }");
            out.println("        .subpage-link { font-size: 0.85rem; font-weight: 600; color: var(--primary); text-decoration: none; border: 1px solid #bae6fd; padding: 0.4rem 0.8rem; border-radius: 6px; background: #f0f9ff; transition: all 0.2s; }");
            out.println("        .subpage-link:hover { background: #e0f2fe; border-color: var(--primary); }");
            out.println("        table { width: 100%; border-collapse: collapse; margin-top: 1rem; font-size: 0.9rem; }");
            out.println("        th, td { padding: 0.8rem 1rem; text-align: left; border-bottom: 1px solid var(--card-border); }");
            out.println("        th { background: #f8fafc; font-weight: 700; font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); }");
            out.println("        tr:hover td { background: #f8fafc; }");
            out.println("        .badge { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.25rem 0.65rem; border-radius: 9999px; font-size: 0.75rem; font-weight: 600; white-space: nowrap; font-family: 'JetBrains Mono', monospace; }");
            out.println("        .badge-success { background: #dcfce7; color: #15803d; border: 1px solid #bbf7d0; }");
            out.println("        .badge-info { background: #f0f9ff; color: #0284c7; border: 1px solid #bae6fd; }");
            out.println("        .badge-warning { background: #fef3c7; color: #b45309; border: 1px solid #fde68a; }");
            out.println("        .badge-time { background: #f1f5f9; color: #475569; border: 1px solid #e2e8f0; font-size: 0.73rem; }");
            out.println("        .badge-perf { background: #f3e8ff; color: #7e22ce; border: 1px solid #e9d5ff; }");
            out.println("        .numeric { text-align: right; font-variant-numeric: tabular-nums; font-family: 'JetBrains Mono', monospace; }");
            out.println("        .speedup-fast { color: #16a34a; font-weight: 700; }");
            out.println("        .speedup-slow { color: #dc2626; }");
            out.println("        .perf-tag { font-size: 0.8rem; padding: 0.15rem 0.4rem; border-radius: 4px; background: #f1f5f9; border: 1px solid #e2e8f0; color: #334155; font-family: 'JetBrains Mono', monospace; }");
            out.println("        .module-source-link { color: var(--primary); text-decoration: none; display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.2rem 0.45rem; border-radius: 6px; background: rgba(2, 132, 199, 0.06); border: 1px solid rgba(2, 132, 199, 0.15); transition: all 0.2s ease; font-weight: 700; }");
            out.println("        .module-source-link:hover { background: rgba(2, 132, 199, 0.14); border-color: var(--primary); text-decoration: none; transform: translateY(-1px); }");
            out.println("        .module-source-link .code-icon { font-size: 0.75rem; opacity: 0.75; }");
            out.println("    </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("<div class=\"container\">");

            // Header
            out.println("    <div class=\"header\">");
            out.println("        <div>");
            out.println("            <h1>⏰ AI JUG Saxony — LRUClockMap Evaluation</h1>");
            out.println("            <p>Comprehensive Evaluation: Quality, JaCoCo Coverage & PIT Mutation Testing across AI Models vs Demo 0 Baseline</p>");
            out.println("        </div>");
            out.println("    </div>");

            // Quick actions
            out.println("    <div class=\"quick-actions\">");
            out.println("        <a href=\"#lruclockmap\" class=\"action-btn\">⏰ Quality, Coverage & Mutation</a>");
            out.println("        <a href=\"surefire.html\" class=\"action-btn\">🧪 Surefire Test Reports</a>");
            out.println("    </div>");

            // Stats grid
            out.println("    <div class=\"stats-grid\">");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Evaluated Models</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: var(--primary);\">%d</div>%n", lruMaps.size());
            out.println("            <div class=\"stat-sub\">demo0–demo9</div>");
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">LRUClockMap Tests</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: #0284c7;\">%d</div>%n", totalTests);
            out.printf("            <div class=\"stat-sub\">Total duration: %.2fs</div>%n", totalTime);
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Test Pass Rate</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: var(--success);\">%.1f%%</div>%n", passPct);
            out.printf("            <div class=\"stat-sub\">%d / %d tests passed</div>%n", totalPassed, totalTests);
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Demo 0 Baseline PIT</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: var(--success);\">%.1f%%</div>%n", demo0Pit);
            out.printf("            <div class=\"stat-sub\">%d / %d mutants killed</div>%n", demo0PitKilled, demo0PitTotal);
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Top AI PIT Score</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: #7c3aed;\">%.1f%%</div>%n", topAiPit);
            out.printf("            <div class=\"stat-sub\">%s</div>%n", escapeHtml(topAiName));
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Eviction Algorithm</div>");
            out.println("            <div class=\"stat-value\" style=\"color: #0284c7; font-size: 1.55rem;\">Second-Chance</div>");
            out.println("            <div class=\"stat-sub\">Clock reference-bit approximation</div>");
            out.println("        </div>");
            out.println("    </div>");

            // SECTION 1: LRUClockMap Quality & Coverage
            out.println("    <div id=\"lruclockmap\" class=\"card\">");
            out.println("        <div class=\"section-header\">");
            out.println("            <div>");
            out.println("                <h2>⏰ LRUClockMap — Quality, Coverage & Mutation Verification</h2>");
            out.println("                <div class=\"section-desc\">Verification matrix for LRUClockMap second-chance eviction cache implementations across all modules.</div>");
            out.println("            </div>");
            out.println("            <div>");
            out.println("                <a href=\"surefire.html\" class=\"subpage-link\">View Detailed Surefire Logs ↗</a>");
            out.println("            </div>");
            out.println("        </div>");
            out.println("        <table>");
            out.println("            <thead>");
            out.println("                <tr>");
            out.println("                    <th>Module</th>");
            out.println("                    <th>AI Model / Implementation</th>");
            out.println("                    <th>Unit Tests</th>");
            out.println("                    <th>Instruction Coverage</th>");
            out.println("                    <th>Line Coverage</th>");
            out.println("                    <th>Branch Coverage</th>");
            out.println("                    <th>PIT Mutation Score</th>");
            out.println("                    <th>Status</th>");
            out.println("                </tr>");
            out.println("            </thead>");
            out.println("            <tbody>");

            for (LruClockMapSummary s : lruMaps) {
                String pkgName = s.id();
                String pitBadge;
                if (s.quality().pitTotal() > 0) {
                    String badgeClass = s.quality().pitScorePct() >= 80.0 ? "badge-success" : (s.quality().pitScorePct() >= 60.0 ? "badge-warning" : "badge-info");
                    pitBadge = String.format("<a href=\"pit-reports/%s/org.jugsaxony.clock.%s/LRUClockMap.java.html\" class=\"badge %s\" style=\"text-decoration: none;\">%.1f%% (%d/%d)</a>",
                            s.id(), pkgName, badgeClass, s.quality().pitScorePct(), s.quality().pitKilled(), s.quality().pitTotal());
                } else {
                    pitBadge = "<span class=\"badge badge-info\">N/A</span>";
                }

                String covLink = String.format("jacoco/%s/org.jugsaxony.clock.%s/LRUClockMap.java.html", s.id(), pkgName);
                String timeBadge = s.quality().executionTimeSeconds() > 0 ?
                        String.format("<span class=\"badge badge-time\">⏱️ %.2fs</span>", s.quality().executionTimeSeconds()) : "";

                int total = s.quality().tests();
                int fails = s.quality().failures() + s.quality().errors();
                int passed = Math.max(0, total - fails);
                String testBadge;
                String statusBadge;

                if (total > 0 && fails == 0) {
                    testBadge = String.format("<a href=\"surefire.html#%s\" class=\"badge badge-success\" style=\"text-decoration: none;\">%d Passed</a> %s", s.id(), total, timeBadge);
                    statusBadge = "<span class=\"badge badge-success\">100% Passed ✅</span>";
                } else if (total > 0) {
                    testBadge = String.format("<a href=\"surefire.html#%s\" class=\"badge badge-warning\" style=\"text-decoration: none;\">%d/%d Passed</a> %s", s.id(), passed, total, timeBadge);
                    statusBadge = String.format("<span class=\"badge badge-warning\">%d Failed ❌</span>", fails);
                } else {
                    testBadge = "<span class=\"badge badge-info\">0 Tests</span>";
                    statusBadge = "<span class=\"badge badge-info\">No Tests</span>";
                }

                String covInstStr = s.quality().totalInstructions() > 0 ?
                        String.format("<a href=\"%s\" class=\"badge badge-success\" style=\"text-decoration: none;\">%.1f%% (%d/%d)</a>",
                                covLink, s.quality().instructionCoveragePct(),
                                s.quality().totalInstructions() - s.quality().missedInstructions(), s.quality().totalInstructions())
                        : "<span class=\"badge badge-info\">N/A</span>";

                String covLineStr = s.quality().totalLines() > 0 ?
                        String.format("<a href=\"%s\" class=\"badge badge-success\" style=\"text-decoration: none;\">%.1f%% (%d/%d)</a>",
                                covLink, s.quality().lineCoveragePct(),
                                s.quality().totalLines() - s.quality().missedLines(), s.quality().totalLines())
                        : "<span class=\"badge badge-info\">N/A</span>";

                String covBranchStr = s.quality().totalBranches() > 0 ?
                        String.format("<a href=\"%s\" class=\"badge badge-success\" style=\"text-decoration: none;\">%.1f%% (%d/%d)</a>",
                                covLink, s.quality().branchCoveragePct(),
                                s.quality().totalBranches() - s.quality().missedBranches(), s.quality().totalBranches())
                        : "<span class=\"badge badge-info\">N/A</span>";

                out.println("                <tr>");
                out.printf("                    <td>%s</td>%n", renderModuleLink(s.id(), "LRUClockMap"));
                out.printf("                    <td>%s</td>%n", s.aiModel());
                out.printf("                    <td>%s</td>%n", testBadge);
                out.printf("                    <td>%s</td>%n", covInstStr);
                out.printf("                    <td>%s</td>%n", covLineStr);
                out.printf("                    <td>%s</td>%n", covBranchStr);
                out.printf("                    <td>%s</td>%n", pitBadge);
                out.printf("                    <td>%s</td>%n", statusBadge);
                out.println("                </tr>");
            }

            out.println("            </tbody>");
            out.println("        </table>");
            out.println("    </div>");

            out.println("</div>");
            out.println("</body>");
            out.println("</html>");
        }
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String renderModuleLink(String modId, String className) {
        if (className != null && !className.isBlank()) {
            return String.format("<a href=\"sources/%s/%s.html\" class=\"module-source-link\" title=\"View Source: %s/%s.java\"><strong>%s</strong> <span class=\"code-icon\">📄</span></a>",
                    modId, className, modId, className, modId);
        }
        return String.format("<strong>%s</strong>", modId);
    }

    private static String renderMarkdownModuleLink(String modId, String className) {
        if (className != null && !className.isBlank()) {
            return String.format("[**%s**](sources/%s/%s.html)", modId, modId, className);
        }
        return String.format("**%s**", modId);
    }

    private static void renderSurefireSuiteRow(
            PrintWriter out,
            String modId,
            String aiModel,
            QualityStats q,
            List<FailedTestCase> failures
    ) {
        int total = q.tests();
        int fails = q.failures();
        int errors = q.errors();
        double time = q.executionTimeSeconds();

        String statusBadge;
        if (total > 0 && fails == 0 && errors == 0) {
            statusBadge = "<span class=\"badge badge-success\">100% Passed ✅</span>";
        } else if (total > 0) {
            statusBadge = String.format("<span class=\"badge badge-warning\">%d FAILED ❌</span>", (fails + errors));
        } else {
            statusBadge = "<span class=\"badge badge-info\">No Tests</span>";
        }

        out.printf("                <tr id=\"%s\">%n", modId);
        out.printf("                    <td>%s</td>%n", renderModuleLink(modId, "LRUClockMapTest"));
        out.printf("                    <td>%s</td>%n", aiModel);
        out.printf("                    <td><strong>%d</strong></td>%n", total);
        out.printf("                    <td class=\"numeric\">%.2fs</td>%n", time);
        out.printf("                    <td class=\"numeric\" style=\"color: %s;\">%d</td>%n", fails > 0 ? "#dc2626" : "inherit", fails);
        out.printf("                    <td class=\"numeric\" style=\"color: %s;\">%d</td>%n", errors > 0 ? "#dc2626" : "inherit", errors);
        out.printf("                    <td>%s</td>%n", statusBadge);

        if (failures.isEmpty()) {
            out.println("                    <td><span style=\"color: #16a34a; font-size: 0.85rem;\">All test cases passed cleanly.</span></td>");
        } else {
            out.println("                    <td>");
            out.printf("                        <details style=\"margin-top: 0.25rem;\"><summary style=\"cursor: pointer; color: #dc2626; font-size: 0.85rem; font-weight: 600;\">Show %d Failure Details</summary>%n", failures.size());
            out.println("                        <div style=\"background: #fef2f2; border: 1px solid #fee2e2; border-radius: 6px; padding: 0.5rem; margin-top: 0.4rem; font-size: 0.8rem; font-family: monospace;\">");
            for (FailedTestCase ftc : failures) {
                out.printf("                            <div style=\"margin-bottom: 0.35rem;\"><span style=\"color: #991b1b; font-weight: bold;\">%s</span>: <code>%s.%s</code> &mdash; %s</div>%n",
                        escapeHtml(ftc.failureType()), escapeHtml(ftc.className()), escapeHtml(ftc.testName()), escapeHtml(ftc.message()));
            }
            out.println("                        </div>");
            out.println("                        </details>");
            out.println("                    </td>");
        }
        out.println("                </tr>");
    }

    private static void writeSurefireHtmlReport(
            File targetFile,
            File outputDir,
            File rootProjectDir,
            List<LruClockMapSummary> lruMaps
    ) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(targetFile))) {
            int totalTests = lruMaps.stream().mapToInt(s -> s.quality().tests()).sum();
            int totalFailures = lruMaps.stream().mapToInt(s -> s.quality().failures() + s.quality().errors()).sum();
            double totalTime = lruMaps.stream().mapToDouble(s -> s.quality().executionTimeSeconds()).sum();

            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("    <meta charset=\"UTF-8\">");
            out.println("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            out.println("    <title>Surefire Aggregated Unit Test Report - AI JUG Saxony</title>");
            out.println("    <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">");
            out.println("    <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>");
            out.println("    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600;700&display=swap\" rel=\"stylesheet\">");
            out.println("    <style>");
            out.println("        :root { --bg: #f8fafc; --card-bg: #ffffff; --card-border: #e2e8f0; --text: #0f172a; --text-muted: #64748b; --primary: #0284c7; --success: #16a34a; --warning: #d97706; --error: #dc2626; }");
            out.println("        * { box-sizing: border-box; }");
            out.println("        body { font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif; background-color: var(--bg); color: var(--text); margin: 0; padding: 2rem 1.5rem; }");
            out.println("        .container { max-width: 1480px; margin: 0 auto; }");
            out.println("        .header { border-bottom: 1px solid var(--card-border); padding-bottom: 1.5rem; margin-bottom: 2rem; display: flex; justify-content: space-between; align-items: flex-start; }");
            out.println("        .header h1 { margin: 0; font-size: 2.1rem; font-weight: 800; background: linear-gradient(135deg, #0f172a 0%, #0284c7 100%); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }");
            out.println("        .header p { margin: 0.5rem 0 0 0; color: var(--text-muted); font-size: 1.05rem; }");
            out.println("        .action-btn { background: var(--card-bg); border: 1px solid var(--card-border); color: #334155; padding: 0.55rem 1.1rem; border-radius: 8px; text-decoration: none; font-size: 0.875rem; font-weight: 600; transition: all 0.2s; display: inline-flex; align-items: center; gap: 0.5rem; }");
            out.println("        .action-btn:hover { background: #f8fafc; border-color: var(--primary); color: var(--primary); }");
            out.println("        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 1.25rem; margin-bottom: 2.25rem; }");
            out.println("        .stat-card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 14px; padding: 1.25rem; text-align: center; }");
            out.println("        .stat-value { font-size: 1.9rem; font-weight: 800; font-family: 'JetBrains Mono', monospace; margin: 0.25rem 0; }");
            out.println("        .stat-sub { font-size: 0.8rem; color: var(--text-muted); }");
            out.println("        .stat-label { color: var(--text-muted); font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.06em; font-weight: 700; }");
            out.println("        .card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 14px; padding: 1.75rem; margin-bottom: 2.25rem; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }");
            out.println("        .section-header { margin-bottom: 1.25rem; border-bottom: 1px solid var(--card-border); padding-bottom: 1rem; }");
            out.println("        .section-header h2 { margin: 0; font-size: 1.45rem; font-weight: 700; color: #0f172a; }");
            out.println("        .section-desc { color: var(--text-muted); font-size: 0.95rem; margin: 0.25rem 0 0 0; }");
            out.println("        table { width: 100%; border-collapse: collapse; margin-top: 1rem; font-size: 0.9rem; }");
            out.println("        th, td { padding: 0.8rem 1rem; text-align: left; border-bottom: 1px solid var(--card-border); }");
            out.println("        th { background: #f8fafc; font-weight: 700; font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); }");
            out.println("        tr:hover td { background: #f8fafc; }");
            out.println("        .badge { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.25rem 0.65rem; border-radius: 9999px; font-size: 0.75rem; font-weight: 600; font-family: 'JetBrains Mono', monospace; }");
            out.println("        .badge-success { background: #dcfce7; color: #15803d; border: 1px solid #bbf7d0; }");
            out.println("        .badge-info { background: #f0f9ff; color: #0284c7; border: 1px solid #bae6fd; }");
            out.println("        .badge-warning { background: #fef3c7; color: #b45309; border: 1px solid #fde68a; }");
            out.println("        .numeric { text-align: right; font-variant-numeric: tabular-nums; font-family: 'JetBrains Mono', monospace; }");
            out.println("        .module-source-link { color: var(--primary); text-decoration: none; display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.2rem 0.45rem; border-radius: 6px; background: rgba(2, 132, 199, 0.06); border: 1px solid rgba(2, 132, 199, 0.15); transition: all 0.2s ease; font-weight: 700; }");
            out.println("        .module-source-link:hover { background: rgba(2, 132, 199, 0.14); border-color: var(--primary); text-decoration: none; transform: translateY(-1px); }");
            out.println("        .module-source-link .code-icon { font-size: 0.75rem; opacity: 0.75; }");
            out.println("    </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("<div class=\"container\">");
            out.println("    <div class=\"header\">");
            out.println("        <div>");
            out.println("            <h1>🧪 Surefire Test Execution Report</h1>");
            out.println("            <p>Aggregated test execution results, execution timings, and failure diagnostics for LRUClockMap across all subprojects.</p>");
            out.println("        </div>");
            out.println("        <div>");
            out.println("            <a href=\"index.html\" class=\"action-btn\">← Back to Dashboard</a>");
            out.println("        </div>");
            out.println("    </div>");

            out.println("    <div class=\"stats-grid\">");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Total Tests Executed</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: var(--primary);\">%d</div>%n", totalTests);
            out.printf("            <div class=\"stat-sub\">Duration: %.2fs</div>%n", totalTime);
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Tests Passed</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: var(--success);\">%d</div>%n", (totalTests - totalFailures));
            out.printf("            <div class=\"stat-sub\">%.1f%% Pass Rate</div>%n", totalTests > 0 ? ((totalTests - totalFailures) * 100.0) / totalTests : 100.0);
            out.println("        </div>");
            out.println("        <div class=\"stat-card\">");
            out.println("            <div class=\"stat-label\">Failures & Errors</div>");
            out.printf("            <div class=\"stat-value\" style=\"color: %s;\">%d</div>%n", totalFailures > 0 ? "var(--error)" : "var(--success)", totalFailures);
            out.printf("            <div class=\"stat-sub\">%s</div>%n", totalFailures == 0 ? "Clean run" : "Requires attention");
            out.println("        </div>");
            out.println("    </div>");

            out.println("    <div id=\"lruclockmap\" class=\"card\">");
            out.println("        <div class=\"section-header\">");
            out.println("            <h2>⏰ LRUClockMap — Bounded Cache Unit Test Suites</h2>");
            out.println("            <div class=\"section-desc\">Execution results for LRUClockMapTest (eviction, capacity bounds, concurrent operations) across demo0 through demo9.</div>");
            out.println("        </div>");
            out.println("        <table>");
            out.println("            <thead>");
            out.println("                <tr>");
            out.println("                    <th>Module</th>");
            out.println("                    <th>AI Model / Implementation</th>");
            out.println("                    <th>Tests</th>");
            out.println("                    <th class=\"numeric\">Duration</th>");
            out.println("                    <th class=\"numeric\">Failures</th>");
            out.println("                    <th class=\"numeric\">Errors</th>");
            out.println("                    <th>Status</th>");
            out.println("                    <th>Diagnostics / Test Results</th>");
            out.println("                </tr>");
            out.println("            </thead>");
            out.println("            <tbody>");

            for (LruClockMapSummary s : lruMaps) {
                File sfDir = new File(rootProjectDir, s.id() + "/target/surefire-reports");
                List<FailedTestCase> failures = parseFailedTestCases(sfDir, "LRUClockMapTest", false);
                renderSurefireSuiteRow(out, s.id(), s.aiModel(), s.quality(), failures);
            }

            out.println("            </tbody>");
            out.println("        </table>");
            out.println("    </div>");

            out.println("</div>");
            out.println("</body>");
            out.println("</html>");
        }
    }

    public static File findRootDir() {
        File cur = new File(".").getAbsoluteFile();
        while (cur != null) {
            File pom = new File(cur, "pom.xml");
            File demo0 = new File(cur, "demo0");
            if (pom.exists() && demo0.exists() && demo0.isDirectory()) {
                try {
                    String content = Files.readString(pom.toPath());
                    if (content.contains("<artifactId>ai-jug-saxony-clock-parent</artifactId>")
                            || content.contains("<artifactId>ai-jug-saxony-parent</artifactId>")) {
                        return cur;
                    }
                } catch (Exception ignored) {}
            }
            cur = cur.getParentFile();
        }
        return new File(".");
    }

    private static void copyDirectory(File src, File dest) throws IOException {
        if (src.isDirectory()) {
            if (!dest.exists()) {
                dest.mkdirs();
            }
            String[] files = src.list();
            if (files != null) {
                for (String file : files) {
                    File srcFile = new File(src, file);
                    File destFile = new File(dest, file);
                    copyDirectory(srcFile, destFile);
                }
            }
        } else {
            Files.copy(src.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void main(String[] args) throws Exception {
        File rootDir = findRootDir();
        File outputDir = new File(rootDir, "reports");
        if (args.length > 0) {
            outputDir = new File(args[0]);
        }
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        generateDashboard(outputDir, rootDir);
        System.out.println("LRUClockMap Master Dashboard generated successfully in: " + outputDir.getAbsolutePath());
    }
}
