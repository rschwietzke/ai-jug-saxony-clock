package org.jugsaxony.report;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalJolReportTest {

    @Test
    public void testGenerateDashboard() throws Exception {
        File rootDir = GlobalDashboardGenerator.findRootDir();

        File outputDir = new File(rootDir, "reports");
        outputDir.mkdirs();

        // Generate master dashboard and sub-section pages
        GlobalDashboardGenerator.generateDashboard(outputDir, rootDir);

        File dashboardMd = new File(outputDir, "global-dashboard.md");
        File dashboardHtml = new File(outputDir, "global-dashboard.html");
        File indexHtml = new File(outputDir, "index.html");
        File lruHtml = new File(outputDir, "lruclockmap.html");
        File surefireHtml = new File(outputDir, "surefire.html");

        assertThat(dashboardMd).exists().isNotEmpty();
        assertThat(dashboardHtml).exists().isNotEmpty();
        assertThat(indexHtml).exists().isNotEmpty();
        assertThat(lruHtml).exists().isNotEmpty();
        assertThat(surefireHtml).exists().isNotEmpty();

        File fastHtml = new File(outputDir, "fasthashmap.html");
        File xltHtml = new File(outputDir, "xlt-util.html");
        File simpleMathHtml = new File(outputDir, "simplemath.html");
        assertThat(fastHtml).doesNotExist();
        assertThat(xltHtml).doesNotExist();
        assertThat(simpleMathHtml).doesNotExist();

        File demo0LruHtml = new File(outputDir, "sources/demo0/LRUClockMap.html");
        File demo0LruJava = new File(outputDir, "sources/demo0/LRUClockMap.java");
        assertThat(demo0LruHtml).exists().isNotEmpty();
        assertThat(demo0LruJava).exists().isNotEmpty();

        File demo0LruTestHtml = new File(outputDir, "sources/demo0/LRUClockMapTest.html");
        File demo0LruTestJava = new File(outputDir, "sources/demo0/LRUClockMapTest.java");
        assertThat(demo0LruTestHtml).exists().isNotEmpty();
        assertThat(demo0LruTestJava).exists().isNotEmpty();
    }
}
