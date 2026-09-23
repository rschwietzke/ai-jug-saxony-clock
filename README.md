# AI JUG Saxony — AI Model Performance & Quality Evaluation

A comparative benchmark evaluating AI coding assistants and LLMs implementing a concurrent second-chance LRU clock eviction cache: `LRUClockMap`.

## Submodule Overview

| Module | AI Model / Source | Tooling / Environment |
| :--- | :--- | :--- | :--- |
| **`demo0`** | Human Baseline | Manual implementation & test suite |
| **`demo1`** | Gemini 3.7 Flash High | Antigravity (VS Code) | 
| **`demo2`** | Kimi K3 | Kilo Code Max (VS Code) | 
| **`demo3`** | OpenAI 5.6 Sol Max | Kilo Code (VS Code) | 
| **`demo4`** | Gemma 4 31B Thinking | Kilo Code (VS Code) |
| **`demo5`** | DeepSeek V4 Flash Max | Kilo Code (VS Code) |
| **`demo6`** | Claude Opus 5 Ultra | Claude Code | 
| **`demo7`** | Qwen 38 max XHigh | Kilo Code (VS Code) | 
| **`demo8`** | Gemini 3.7 Flash High | Kilo Code (VS Code) |
| **`demo9`** | Gemini 3.8 Flash High | Antigravity (VS Code) | 

---

## Running the Test Suite & Generating Reports

The project uses Maven multi-module architecture with a dedicated `coverage-report` module that aggregates metrics and generates interactive executive reports.

### Step 1: Run Unit Tests & Collect JaCoCo Coverage
Runs all unit tests across all reactor modules (`demo0` through `demo9` and `coverage-report`), creates Surefire test result records, and generates JaCoCo test execution data per submodule:
```bash
mvn clean test
```

### Step 2: Generate Aggregated JaCoCo Code Coverage Report
Aggregates coverage metrics across all submodules into a unified multi-module report:
```bash
mvn verify -pl coverage-report
```
- Aggregated Report: `coverage-report/target/site/jacoco-aggregate/index.html`

### Step 3: Run PIT Mutation Coverage (Optional / Full Evaluation)
Executes PIT mutation testing across submodules to produce `mutations.csv` and mutation analysis reports:
```bash
mvn test-compile pitest:mutationCoverage
```
- Module Output: `<module>/target/pit-reports/`

### Step 4: Run JMH Cross-Project Microbenchmarks with Hardware Counters
Executes the microbenchmarks comparing all `LRUClockMap` implementations (Demo 0 serves as the baseline). On Linux systems with `perf` available, hardware performance counters can be captured automatically using JMH's `LinuxPerfNormProfiler`.

- **Quick Sanity Run (~30s)**:
  ```bash
  mvn test-compile exec:java \
    -Dexec.mainClass="org.jugsaxony.report.GlobalJmhBenchmark" \
    -Dexec.classpathScope="test" \
    -pl coverage-report \
    -Dexec.args="--quick"
  ```

- **Quick Run with Hardware Performance Counters (`perf`)**:
  ```bash
  mvn test-compile exec:java \
    -Dexec.mainClass="org.jugsaxony.report.GlobalJmhBenchmark" \
    -Dexec.classpathScope="test" \
    -pl coverage-report \
    -Dexec.args="--quick --perf"
  ```

- **Full Measurement Run with Perf & GC Profiling**:
  ```bash
  mvn test-compile exec:java \
    -Dexec.mainClass="org.jugsaxony.report.GlobalJmhBenchmark" \
    -Dexec.classpathScope="test" \
    -pl coverage-report \
    -Dexec.args="--perf --gc"
  ```

- **Filtering Specific Demos / Benchmarks**:
  ```bash
  mvn test-compile exec:java \
    -Dexec.mainClass="org.jugsaxony.report.GlobalJmhBenchmark" \
    -Dexec.classpathScope="test" \
    -pl coverage-report \
    -Dexec.args="--quick --perf --filter getHit_demo[01]"
  ```

#### Captured Micro-Architectural Metrics
When `--perf` is specified, the benchmark collects and analyzes low-level CPU performance counters:
- **Cycles / op**: Raw CPU cycles spent per cache operation.
- **Instructions / op**: Total x86/ARM instructions executed per operation.
- **IPC (Instructions Per Cycle)**: Pipeline execution efficiency; higher is better (typically 2.5–4.5 on modern out-of-order cores).
- **CPI (Cycles Per Instruction)**: Reciprocal of IPC ($1 / \text{IPC}$).
- **Branch Miss %**: Rate of branch predictor misses; low misprediction avoids expensive pipeline flushes (~15-20 cycles).
- **L1 D-Cache Miss %**: Rate of L1 data cache misses; highlights cache locality benefits of flat parallel arrays over pointer-chasing node graphs.

Generates `reports/jmh-results.json`, `reports/jmh-report.html`, `reports/jmh-report.md`, and `reports/jmh-report.csv`.

### Step 5: Generate JOL Memory Footprint & Master Executive Dashboard
Runs Java Object Layout (JOL) memory analysis, parses test execution counts, test execution duration, JaCoCo coverage XMLs, mutation scores, and JMH metrics, then generates the consolidated master dashboard into `/reports`:
```bash
mvn test -pl coverage-report
```
Alternatively, invoke the generator directly:
```bash
mvn test-compile exec:java \
  -Dexec.mainClass="org.jugsaxony.report.GlobalDashboardGenerator" \
  -Dexec.classpathScope="test" \
  -pl coverage-report
```

---

## Generated Reports Overview

All consolidated reports are saved in `/reports` (versioned and persistent across `mvn clean`):

| Report | File | Description |
| :--- | :--- | :--- |
| **Master Dashboard** | `reports/index.html` | Complete interactive dashboard with LRUClockMap quality, coverage, JOL memory, and JMH speedup |
| **LRUClockMap Section** | `reports/lruclockmap.html` | Dedicated view for LRUClockMap second-chance cache test verification (with execution time) |
| **Surefire Unit Tests** | `reports/surefire.html` | Aggregated test execution records and failure diagnostics across submodules |
| **Markdown Summary** | `reports/global-dashboard.md` | Executive summary formatted for Markdown documentation & GitHub |
| **JOL Report** | `reports/jol-report.html` | Shallow/deep retained memory and object layout analysis |
| **JMH Report** | `reports/jmh-report.html` | Read hit/miss and write throughput comparison charts & tables |
| **Coverage Aggregate** | `reports/coverage-aggregate/index.html` | Aggregated multi-module JaCoCo coverage drill-down |

---

## End-to-End Execution One-Liner

To run the standard verification, aggregated coverage, quick JMH benchmark, and dashboard generation in a single command:

```bash
mvn clean test && \
mvn verify -pl coverage-report && \
mvn test-compile exec:java -Dexec.mainClass="org.jugsaxony.report.GlobalJmhBenchmark" -Dexec.classpathScope="test" -pl coverage-report -Dexec.args="--perf --gc" && \
mvn test -pl coverage-report
```
