# ⏰ AI JUG Saxony — LRUClockMap Evaluation Dashboard

Comprehensive evaluation matrix across AI-generated second-chance LRU clock eviction cache implementations comparing code quality, test suites, JaCoCo coverage, and PIT mutation resilience against the Demo 0 baseline.

## ⏰ LRUClockMap — Quality, Coverage & Mutation Verification

| Module | AI Model / Implementation | Unit Tests | Instruction Coverage | Line Coverage | Branch Coverage | PIT Mutation Score | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| [**demo0**](sources/demo0/LRUClockMap.html) | Human Baseline | 37 (0.72s) | 97.3% (686/705) | 98.2% (161/164) | 96.8% (60/62) | 92.6% (125/135 killed) | 100% Passing ✅ |
| [**demo1**](sources/demo1/LRUClockMap.html) | Gemini 3.7 Flash High (Antigravity) | 52 (0.94s) | 71.5% (504/705) | 78.7% (129/164) | 77.4% (48/62) | 77.0% (104/135 killed) | 1 Failed ❌ |
| [**demo2**](sources/demo2/LRUClockMap.html) | Kimi K3 (Kilo Code) | 52 (1.49s) | 71.4% (503/704) | 77.1% (128/166) | 77.4% (48/62) | 68.6% (94/137 killed) | 2 Failed ❌ |
| [**demo3**](sources/demo3/LRUClockMap.html) | OpenAI 5.6 Sol Max (Kilo Code) | 15 (0.19s) | 69.2% (487/704) | 74.1% (123/166) | 69.4% (43/62) | 67.2% (92/137 killed) | 100% Passing ✅ |
| [**demo4**](sources/demo4/LRUClockMap.html) | Gemma 4 31B Thinking (Kilo Code) | 15 (0.59s) | 57.5% (405/704) | 60.8% (101/166) | 54.8% (34/62) | 54.7% (75/137 killed) | 6 Failed ❌ |
| [**demo5**](sources/demo5/LRUClockMap.html) | Deepseek V4 Flash Max (Kilo Code) | 44 (0.78s) | 71.4% (503/704) | 77.1% (128/166) | 77.4% (48/62) | 74.5% (102/137 killed) | 2 Failed ❌ |
| [**demo6**](sources/demo6/LRUClockMap.html) | Claude Opus 5 Ultra (Claude) | 130 (2.73s) | 71.4% (503/704) | 77.1% (128/166) | 77.4% (48/62) | 77.4% (106/137 killed) | 2 Failed ❌ |
| [**demo7**](sources/demo7/LRUClockMap.html) | Qwen 38 max XHigh (Kilo Code) | 72 (1.02s) | 67.2% (473/704) | 72.3% (120/166) | 66.1% (41/62) | 59.1% (81/137 killed) | 4 Failed ❌ |
| [**demo8**](sources/demo8/LRUClockMap.html) | Gemini 3.7 Flash High (Kilo Code) | 42 (1.04s) | 71.4% (503/704) | 77.1% (128/166) | 77.4% (48/62) | 73.0% (100/137 killed) | 1 Failed ❌ |
| [**demo9**](sources/demo9/LRUClockMap.html) | Gemini 3.8 Flash High (Antigravity) | 46 (0.77s) | 71.4% (503/704) | 77.1% (128/166) | 77.4% (48/62) | 75.9% (104/137 killed) | 1 Failed ❌ |

---

## 🔗 Quick Links

- 📊 **Interactive HTML Dashboard**: [index.html](index.html)
- 🧪 **Surefire Unit Test Reports**: [surefire.html](surefire.html)
