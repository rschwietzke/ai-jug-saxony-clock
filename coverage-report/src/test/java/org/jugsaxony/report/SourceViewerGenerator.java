package org.jugsaxony.report;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class SourceViewerGenerator {

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "record", "yield", "sealed", "non-sealed", "permits", "var"
    );

    private static final Set<String> JAVA_LITERALS = Set.of("true", "false", "null");

    public record SourceFileInfo(
            String fileName,
            String className,
            String relativePath,
            File originalFile
    ) {}

    public static void generateSourceSnapshots(File outputDir, File rootProjectDir, String[] modDirs, Map<String, String[]> moduleMetadata) throws IOException {
        File sourcesBaseDir = new File(outputDir, "sources");
        if (!sourcesBaseDir.exists()) {
            sourcesBaseDir.mkdirs();
        }

        // Map from modId -> List of source files
        Map<String, List<SourceFileInfo>> moduleFiles = new LinkedHashMap<>();

        for (String modId : modDirs) {
            List<SourceFileInfo> files = new ArrayList<>();
            File srcMainJava = new File(rootProjectDir, modId + "/src/main/java");
            if (srcMainJava.exists() && srcMainJava.isDirectory()) {
                scanJavaFiles(srcMainJava, srcMainJava, files);
            }
            File srcTestJava = new File(rootProjectDir, modId + "/src/test/java");
            if (srcTestJava.exists() && srcTestJava.isDirectory()) {
                scanJavaFiles(srcTestJava, srcTestJava, files);
            }
            files.sort(Comparator.comparing(f -> getSortPriority(f.className()) + "_" + f.className()));
            moduleFiles.put(modId, files);
        }

        // Generate files for each module
        for (String modId : modDirs) {
            List<SourceFileInfo> files = moduleFiles.get(modId);
            if (files == null || files.isEmpty()) continue;

            File modSourcesDir = new File(sourcesBaseDir, modId);
            if (!modSourcesDir.exists()) {
                modSourcesDir.mkdirs();
            }

            String aiModel = "AI Implementation";
            if (moduleMetadata != null && moduleMetadata.containsKey(modId)) {
                String[] meta = moduleMetadata.get(modId);
                if (meta.length > 1) {
                    aiModel = meta[1];
                }
            }

            for (SourceFileInfo fileInfo : files) {
                // 1. Copy raw .java file as an independent snapshot
                File destJavaFile = new File(modSourcesDir, fileInfo.fileName());
                Files.copy(fileInfo.originalFile().toPath(), destJavaFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                // 2. Generate presentation-ready .html viewer
                File destHtmlFile = new File(modSourcesDir, fileInfo.className() + ".html");
                generateHtmlViewer(destHtmlFile, fileInfo, modId, aiModel, files, modDirs);
            }
        }
    }

    private static int getSortPriority(String className) {
        if ("LRUClockMap".equals(className)) return 1;
        if ("LRUClockMapTest".equals(className)) return 2;
        if ("LRUClockMapInterface".equals(className)) return 3;
        return 10;
    }

    private static void scanJavaFiles(File baseDir, File currentDir, List<SourceFileInfo> result) {
        File[] entries = currentDir.listFiles();
        if (entries == null) return;
        for (File f : entries) {
            if (f.isDirectory()) {
                scanJavaFiles(baseDir, f, result);
            } else if (f.isFile() && f.getName().endsWith(".java")) {
                String relPath = baseDir.toPath().relativize(f.toPath()).toString().replace('\\', '/');
                String className = f.getName().substring(0, f.getName().length() - 5);
                result.add(new SourceFileInfo(f.getName(), className, relPath, f));
            }
        }
    }

    private static void generateHtmlViewer(
            File targetHtml,
            SourceFileInfo fileInfo,
            String modId,
            String aiModel,
            List<SourceFileInfo> allFilesInMod,
            String[] allModDirs
    ) throws IOException {

        List<String> rawLines = Files.readAllLines(fileInfo.originalFile().toPath(), StandardCharsets.UTF_8);
        long fileSizeBytes = fileInfo.originalFile().length();
        String fileSizeStr = fileSizeBytes < 1024 ? fileSizeBytes + " B" : String.format("%.1f KB", fileSizeBytes / 1024.0);

        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(targetHtml), StandardCharsets.UTF_8))) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("    <meta charset=\"UTF-8\">");
            out.println("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            out.printf("    <title>%s - %s (Source Code Viewer)</title>%n", modId, fileInfo.fileName());
            out.println("    <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">");
            out.println("    <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>");
            out.println("    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&display=swap\" rel=\"stylesheet\">");
            out.println("    <style>");
            out.println("        :root {");
            out.println("            --bg: #ffffff;");
            out.println("            --header-bg: #f8fafc;");
            out.println("            --border: #e2e8f0;");
            out.println("            --gutter-bg: #f8fafc;");
            out.println("            --gutter-num: #94a3b8;");
            out.println("            --text: #0f172a;");
            out.println("            --primary: #0284c7;");
            out.println("            --primary-glow: rgba(2, 132, 199, 0.12);");
            out.println("            --accent: #7c3aed;");
            out.println("            --line-highlight: #fef9c3;");
            out.println("            --line-highlight-border: #eab308;");
            out.println("            --kw-color: #cf222e;");
            out.println("            --str-color: #0a3069;");
            out.println("            --comment-color: #6e7781;");
            out.println("            --ann-color: #8250df;");
            out.println("            --num-color: #0550ae;");
            out.println("            --type-color: #953800;");
            out.println("        }");
            out.println("        body.dark-theme {");
            out.println("            --bg: #0d1117;");
            out.println("            --header-bg: #161b22;");
            out.println("            --border: #30363d;");
            out.println("            --gutter-bg: #161b22;");
            out.println("            --gutter-num: #6e7681;");
            out.println("            --text: #e6edf3;");
            out.println("            --primary: #38bdf8;");
            out.println("            --primary-glow: rgba(56, 189, 248, 0.15);");
            out.println("            --accent: #a855f7;");
            out.println("            --line-highlight: #3b3014;");
            out.println("            --line-highlight-border: #d29922;");
            out.println("            --kw-color: #ff7b72;");
            out.println("            --str-color: #a5d6ff;");
            out.println("            --comment-color: #8b949e;");
            out.println("            --ann-color: #d2a8ff;");
            out.println("            --num-color: #79c0ff;");
            out.println("            --type-color: #ffa657;");
            out.println("        }");
            out.println("        * { box-sizing: border-box; }");
            out.println("        body {");
            out.println("            margin: 0;");
            out.println("            padding-top: 60px;");
            out.println("            background-color: var(--bg);");
            out.println("            color: var(--text);");
            out.println("            font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;");
            out.println("            font-size: 1rem;");
            out.println("            line-height: 1.5;");
            out.println("        }");
            out.println("        .viewer-header {");
            out.println("            position: fixed;");
            out.println("            top: 0; left: 0; right: 0;");
            out.println("            height: 60px;");
            out.println("            background: var(--header-bg);");
            out.println("            border-bottom: 1px solid var(--border);");
            out.println("            display: flex;");
            out.println("            align-items: center;");
            out.println("            justify-content: space-between;");
            out.println("            padding: 0 1.25rem;");
            out.println("            z-index: 1000;");
            out.println("            box-shadow: 0 1px 3px rgba(0,0,0,0.05);");
            out.println("            gap: 1rem;");
            out.println("        }");
            out.println("        .header-left {");
            out.println("            display: flex;");
            out.println("            align-items: center;");
            out.println("            gap: 0.75rem;");
            out.println("            flex-wrap: nowrap;");
            out.println("            overflow-x: auto;");
            out.println("        }");
            out.println("        .back-btn {");
            out.println("            display: inline-flex;");
            out.println("            align-items: center;");
            out.println("            gap: 0.35rem;");
            out.println("            background: var(--bg);");
            out.println("            border: 1px solid var(--border);");
            out.println("            color: var(--text);");
            out.println("            padding: 0.35rem 0.75rem;");
            out.println("            border-radius: 6px;");
            out.println("            font-size: 0.85rem;");
            out.println("            font-weight: 600;");
            out.println("            text-decoration: none;");
            out.println("            cursor: pointer;");
            out.println("            transition: all 0.15s ease;");
            out.println("        }");
            out.println("        .back-btn:hover {");
            out.println("            border-color: var(--primary);");
            out.println("            color: var(--primary);");
            out.println("        }");
            out.println("        .module-badge {");
            out.println("            background: #0284c7;");
            out.println("            color: #ffffff;");
            out.println("            font-size: 0.82rem;");
            out.println("            font-weight: 700;");
            out.println("            padding: 0.25rem 0.65rem;");
            out.println("            border-radius: 6px;");
            out.println("            letter-spacing: 0.02em;");
            out.println("        }");
            out.println("        .model-badge {");
            out.println("            background: var(--bg);");
            out.println("            border: 1px solid var(--border);");
            out.println("            color: var(--text);");
            out.println("            font-size: 0.82rem;");
            out.println("            font-weight: 500;");
            out.println("            padding: 0.25rem 0.65rem;");
            out.println("            border-radius: 6px;");
            out.println("        }");
            out.println("        .file-title {");
            out.println("            font-family: 'JetBrains Mono', monospace;");
            out.println("            font-size: 0.95rem;");
            out.println("            font-weight: 700;");
            out.println("            color: var(--text);");
            out.println("        }");
            out.println("        .file-meta {");
            out.println("            font-size: 0.8rem;");
            out.println("            color: var(--gutter-num);");
            out.println("        }");
            out.println("        .header-center {");
            out.println("            display: flex;");
            out.println("            align-items: center;");
            out.println("            gap: 0.5rem;");
            out.println("        }");
            out.println("        .nav-select {");
            out.println("            background: var(--bg);");
            out.println("            border: 1px solid var(--border);");
            out.println("            color: var(--text);");
            out.println("            padding: 0.35rem 0.65rem;");
            out.println("            border-radius: 6px;");
            out.println("            font-size: 0.85rem;");
            out.println("            font-weight: 600;");
            out.println("            cursor: pointer;");
            out.println("            outline: none;");
            out.println("        }");
            out.println("        .nav-select:focus {");
            out.println("            border-color: var(--primary);");
            out.println("        }");
            out.println("        .header-right {");
            out.println("            display: flex;");
            out.println("            align-items: center;");
            out.println("            gap: 0.5rem;");
            out.println("        }");
            out.println("        .action-btn {");
            out.println("            background: var(--bg);");
            out.println("            border: 1px solid var(--border);");
            out.println("            color: var(--text);");
            out.println("            padding: 0.35rem 0.75rem;");
            out.println("            border-radius: 6px;");
            out.println("            font-size: 0.82rem;");
            out.println("            font-weight: 600;");
            out.println("            text-decoration: none;");
            out.println("            cursor: pointer;");
            out.println("            display: inline-flex;");
            out.println("            align-items: center;");
            out.println("            gap: 0.35rem;");
            out.println("            transition: all 0.15s ease;");
            out.println("        }");
            out.println("        .action-btn:hover {");
            out.println("            border-color: var(--primary);");
            out.println("            color: var(--primary);");
            out.println("        }");
            out.println("        .code-container {");
            out.println("            width: 100%;");
            out.println("            overflow-x: auto;");
            out.println("            padding: 1rem 0;");
            out.println("        }");
            out.println("        .code-table {");
            out.println("            width: 100%;");
            out.println("            border-collapse: collapse;");
            out.println("            font-family: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;");
            out.println("            font-size: 0.95rem;");
            out.println("            line-height: 1.55;");
            out.println("        }");
            out.println("        .code-row {");
            out.println("            transition: background 0.1s ease;");
            out.println("        }");
            out.println("        .code-row:hover {");
            out.println("            background-color: var(--header-bg);");
            out.println("        }");
            out.println("        .code-row.highlighted {");
            out.println("            background-color: var(--line-highlight) !important;");
            out.println("        }");
            out.println("        .code-row.highlighted td.line-num {");
            out.println("            border-left: 4px solid var(--line-highlight-border);");
            out.println("            color: var(--text);");
            out.println("            font-weight: 700;");
            out.println("        }");
            out.println("        td.line-num {");
            out.println("            width: 1%;");
            out.println("            min-width: 3.5rem;");
            out.println("            padding: 0 1rem 0 0.75rem;");
            out.println("            text-align: right;");
            out.println("            vertical-align: top;");
            out.println("            color: var(--gutter-num);");
            out.println("            user-select: none;");
            out.println("            border-right: 1px solid var(--border);");
            out.println("            background-color: var(--gutter-bg);");
            out.println("        }");
            out.println("        td.line-num a {");
            out.println("            color: inherit;");
            out.println("            text-decoration: none;");
            out.println("            display: block;");
            out.println("        }");
            out.println("        td.line-num a:hover {");
            out.println("            color: var(--primary);");
            out.println("        }");
            out.println("        td.line-code {");
            out.println("            padding: 0 1.25rem;");
            out.println("            vertical-align: top;");
            out.println("            white-space: pre;");
            out.println("        }");
            out.println("        .kw { color: var(--kw-color); font-weight: 700; }");
            out.println("        .lit { color: var(--kw-color); font-weight: 700; }");
            out.println("        .str { color: var(--str-color); }");
            out.println("        .comment { color: var(--comment-color); font-style: italic; }");
            out.println("        .ann { color: var(--ann-color); font-weight: 600; }");
            out.println("        .num { color: var(--num-color); }");
            out.println("        .type { color: var(--type-color); }");
            out.println("    </style>");
            out.println("</head>");
            out.println("<body>");

            // Header bar
            out.println("    <header class=\"viewer-header\">");
            out.println("        <div class=\"header-left\">");
            out.println("            <a href=\"../../index.html\" class=\"back-btn\" onclick=\"if (document.referrer && document.referrer.includes('reports')) { history.back(); return false; }\">← Back</a>");
            out.printf("            <span class=\"module-badge\">%s</span>%n", modId);
            out.printf("            <span class=\"model-badge\">%s</span>%n", escapeHtml(aiModel));
            out.printf("            <span class=\"file-title\">%s</span>%n", escapeHtml(fileInfo.fileName()));
            out.printf("            <span class=\"file-meta\">%d lines • %s</span>%n", rawLines.size(), fileSizeStr);
            out.println("        </div>");

            // Center: Switcher dropdowns
            out.println("        <div class=\"header-center\">");
            // File switcher within current module
            if (allFilesInMod.size() > 1) {
                out.println("            <select class=\"nav-select\" title=\"Switch File in " + modId + "\" onchange=\"window.location.href=this.value;\">");
                for (SourceFileInfo f : allFilesInMod) {
                    boolean sel = f.className().equals(fileInfo.className());
                    out.printf("                <option value=\"%s.html\"%s>%s</option>%n", f.className(), sel ? " selected" : "", f.fileName());
                }
                out.println("            </select>");
            }
            // Module switcher for the same class (if exists in other modules)
            out.println("            <select class=\"nav-select\" title=\"Switch Module for " + fileInfo.fileName() + "\" onchange=\"window.location.href=this.value;\">");
            for (String otherMod : allModDirs) {
                boolean sel = otherMod.equals(modId);
                out.printf("                <option value=\"../%s/%s.html\"%s>%s</option>%n", otherMod, fileInfo.className(), sel ? " selected" : "", otherMod);
            }
            out.println("            </select>");
            out.println("        </div>");

            // Right: Actions (Copy, Raw, Dark mode)
            out.println("        <div class=\"header-right\">");
            out.println("            <button class=\"action-btn\" id=\"copyBtn\" onclick=\"copyCode()\">📋 Copy Code</button>");
            out.printf("            <a href=\"%s\" download=\"%s_%s\" class=\"action-btn\">💾 Raw Java</a>%n",
                    escapeHtml(fileInfo.fileName()), modId, escapeHtml(fileInfo.fileName()));
            out.println("            <button class=\"action-btn\" id=\"themeBtn\" onclick=\"toggleTheme()\">🌙 Dark</button>");
            out.println("        </div>");
            out.println("    </header>");

            // Code Table
            out.println("    <div class=\"code-container\">");
            out.println("        <table class=\"code-table\">");
            out.println("            <tbody>");

            // Highlight lines
            boolean inBlockComment = false;
            boolean inTextBlock = false;

            for (int i = 0; i < rawLines.size(); i++) {
                int lineNum = i + 1;
                String line = rawLines.get(i);
                HighlightResult res = highlightJavaLine(line, inBlockComment, inTextBlock);
                inBlockComment = res.inBlockComment();
                inTextBlock = res.inTextBlock();

                out.printf("                <tr class=\"code-row\" id=\"L%d\">%n", lineNum);
                out.printf("                    <td class=\"line-num\" data-line=\"%d\"><a href=\"#L%d\">%d</a></td>%n", lineNum, lineNum, lineNum);
                out.printf("                    <td class=\"line-code\"><code>%s</code></td>%n", res.highlightedHtml());
                out.println("                </tr>");
            }

            out.println("            </tbody>");
            out.println("        </table>");
            out.println("    </div>");

            // Interactive Script: copy, theme, and line hash highlighting
            out.println("    <script>");
            out.println("        function updateHighlight() {");
            out.println("            document.querySelectorAll('.code-row.highlighted').forEach(r => r.classList.remove('highlighted'));");
            out.println("            const hash = window.location.hash;");
            out.println("            if (hash && hash.startsWith('#L')) {");
            out.println("                const el = document.getElementById(hash.substring(1));");
            out.println("                if (el) {");
            out.println("                    el.classList.add('highlighted');");
            out.println("                    el.scrollIntoView({ behavior: 'smooth', block: 'center' });");
            out.println("                }");
            out.println("            }");
            out.println("        }");
            out.println("        window.addEventListener('hashchange', updateHighlight);");
            out.println("        window.addEventListener('DOMContentLoaded', updateHighlight);");
            out.println();
            out.println("        function toggleTheme() {");
            out.println("            const isDark = document.body.classList.toggle('dark-theme');");
            out.println("            const btn = document.getElementById('themeBtn');");
            out.println("            btn.innerText = isDark ? '☀️ Light' : '🌙 Dark';");
            out.println("            localStorage.setItem('presentation-theme', isDark ? 'dark' : 'light');");
            out.println("        }");
            out.println("        if (localStorage.getItem('presentation-theme') === 'dark') {");
            out.println("            document.body.classList.add('dark-theme');");
            out.println("            const btn = document.getElementById('themeBtn');");
            out.println("            if (btn) btn.innerText = '☀️ Light';");
            out.println("        }");
            out.println();
            out.println("        function copyCode() {");
            out.println("            const codeText = Array.from(document.querySelectorAll('.line-code')).map(td => td.textContent).join('\\n');");
            out.println("            navigator.clipboard.writeText(codeText).then(() => {");
            out.println("                const btn = document.getElementById('copyBtn');");
            out.println("                const orig = btn.innerText;");
            out.println("                btn.innerText = '✅ Copied!';");
            out.println("                setTimeout(() => btn.innerText = orig, 1800);");
            out.println("            });");
            out.println("        }");
            out.println("    </script>");
            out.println("</body>");
            out.println("</html>");
        }
    }

    private record HighlightResult(String highlightedHtml, boolean inBlockComment, boolean inTextBlock) {}

    private static HighlightResult highlightJavaLine(String line, boolean inBlockComment, boolean inTextBlock) {
        if (line.isEmpty()) {
            return new HighlightResult("", inBlockComment, inTextBlock);
        }

        StringBuilder sb = new StringBuilder();
        int len = line.length();
        int i = 0;

        if (inBlockComment) {
            int endComment = line.indexOf("*/");
            if (endComment == -1) {
                sb.append("<span class=\"comment\">").append(escapeHtml(line)).append("</span>");
                return new HighlightResult(sb.toString(), true, false);
            } else {
                sb.append("<span class=\"comment\">").append(escapeHtml(line.substring(0, endComment + 2))).append("</span>");
                i = endComment + 2;
                inBlockComment = false;
            }
        }

        if (inTextBlock) {
            int endTextBlock = line.indexOf("\"\"\"");
            if (endTextBlock == -1) {
                sb.append("<span class=\"str\">").append(escapeHtml(line)).append("</span>");
                return new HighlightResult(sb.toString(), false, true);
            } else {
                sb.append("<span class=\"str\">").append(escapeHtml(line.substring(0, endTextBlock + 3))).append("</span>");
                i = endTextBlock + 3;
                inTextBlock = false;
            }
        }

        while (i < len) {
            char c = line.charAt(i);

            // Block comment start /*
            if (c == '/' && i + 1 < len && line.charAt(i + 1) == '*') {
                int end = line.indexOf("*/", i + 2);
                if (end == -1) {
                    sb.append("<span class=\"comment\">").append(escapeHtml(line.substring(i))).append("</span>");
                    return new HighlightResult(sb.toString(), true, false);
                } else {
                    sb.append("<span class=\"comment\">").append(escapeHtml(line.substring(i, end + 2))).append("</span>");
                    i = end + 2;
                    continue;
                }
            }

            // Single line comment //
            if (c == '/' && i + 1 < len && line.charAt(i + 1) == '/') {
                sb.append("<span class=\"comment\">").append(escapeHtml(line.substring(i))).append("</span>");
                break;
            }

            // Text block """
            if (c == '"' && i + 2 < len && line.charAt(i + 1) == '"' && line.charAt(i + 2) == '"') {
                int end = line.indexOf("\"\"\"", i + 3);
                if (end == -1) {
                    sb.append("<span class=\"str\">").append(escapeHtml(line.substring(i))).append("</span>");
                    return new HighlightResult(sb.toString(), false, true);
                } else {
                    sb.append("<span class=\"str\">").append(escapeHtml(line.substring(i, end + 3))).append("</span>");
                    i = end + 3;
                    continue;
                }
            }

            // String literal "..."
            if (c == '"') {
                int start = i;
                i++;
                boolean escaped = false;
                while (i < len) {
                    char sc = line.charAt(i);
                    if (escaped) {
                        escaped = false;
                    } else if (sc == '\\') {
                        escaped = true;
                    } else if (sc == '"') {
                        i++;
                        break;
                    }
                    i++;
                }
                sb.append("<span class=\"str\">").append(escapeHtml(line.substring(start, i))).append("</span>");
                continue;
            }

            // Character literal '...'
            if (c == '\'') {
                int start = i;
                i++;
                boolean escaped = false;
                while (i < len) {
                    char sc = line.charAt(i);
                    if (escaped) {
                        escaped = false;
                    } else if (sc == '\\') {
                        escaped = true;
                    } else if (sc == '\'') {
                        i++;
                        break;
                    }
                    i++;
                }
                sb.append("<span class=\"str\">").append(escapeHtml(line.substring(start, i))).append("</span>");
                continue;
            }

            // Annotation @AnnotationName
            if (c == '@' && i + 1 < len && Character.isJavaIdentifierStart(line.charAt(i + 1))) {
                int start = i;
                i++;
                while (i < len && Character.isJavaIdentifierPart(line.charAt(i))) {
                    i++;
                }
                sb.append("<span class=\"ann\">").append(escapeHtml(line.substring(start, i))).append("</span>");
                continue;
            }

            // Identifier or Keyword
            if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < len && Character.isJavaIdentifierPart(line.charAt(i))) {
                    i++;
                }
                String word = line.substring(start, i);
                if (JAVA_KEYWORDS.contains(word)) {
                    sb.append("<span class=\"kw\">").append(word).append("</span>");
                } else if (JAVA_LITERALS.contains(word)) {
                    sb.append("<span class=\"lit\">").append(word).append("</span>");
                } else if (Character.isUpperCase(word.charAt(0))) {
                    sb.append("<span class=\"type\">").append(word).append("</span>");
                } else {
                    sb.append(escapeHtml(word));
                }
                continue;
            }

            // Numbers: hex (0x...), binary (0b...), decimal
            if (Character.isDigit(c)) {
                int start = i;
                if (c == '0' && i + 1 < len && (line.charAt(i + 1) == 'x' || line.charAt(i + 1) == 'X' || line.charAt(i + 1) == 'b' || line.charAt(i + 1) == 'B')) {
                    i += 2;
                    while (i < len && (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '_')) {
                        i++;
                    }
                } else {
                    while (i < len && (Character.isDigit(line.charAt(i)) || line.charAt(i) == '.' || line.charAt(i) == '_'
                            || line.charAt(i) == 'f' || line.charAt(i) == 'F' || line.charAt(i) == 'd' || line.charAt(i) == 'D'
                            || line.charAt(i) == 'l' || line.charAt(i) == 'L' || line.charAt(i) == 'e' || line.charAt(i) == 'E')) {
                        i++;
                    }
                }
                sb.append("<span class=\"num\">").append(escapeHtml(line.substring(start, i))).append("</span>");
                continue;
            }

            // Other characters
            if (c == '&') sb.append("&amp;");
            else if (c == '<') sb.append("&lt;");
            else if (c == '>') sb.append("&gt;");
            else sb.append(c);
            i++;
        }

        return new HighlightResult(sb.toString(), inBlockComment, inTextBlock);
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
