# ⏰ AI JUG Saxony — LRUClockMap Evaluation Dashboard

Comprehensive evaluation matrix across AI-generated second-chance LRU clock eviction cache implementations comparing code quality, test suites, JaCoCo coverage, and PIT mutation resilience against the Demo 0 baseline.

## ⏰ LRUClockMap — Quality, Coverage & Mutation Verification

| Module | AI Model / Implementation | Storage Strategy | Unit Tests | Instruction Coverage | Line Coverage | Branch Coverage | PIT Mutation Score | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **demo0** | Human Baseline | Circular buffer + Wrapper nodes | 37 (0.72s) | 97.3% (686/705) | 98.2% (161/164) | 96.8% (60/62) | 92.6% (125/135 killed) | 100% Passing ✅ |
| **demo1** | Gemini 3.7 Flash High (Antigravity) | Circular buffer + Wrapper nodes | 52 (0.94s) | 71.5% (504/705) | 78.7% (129/164) | 77.4% (48/62) | 77.0% (104/135 killed) | 1 Failed ❌ |
| **demo2** | Kimi K3 (Kilo Code) | Circular buffer + Wrapper nodes | 52 (1.49s) | 71.4% (503/704) | 77.1% (128/166) | 77.4% (48/62) | 68.6% (94/137 killed) | 2 Failed ❌ |
| **demo3** | OpenAI 5.6 Sol Max (Kilo Code) | Circular buffer + Wrapper nodes | 15 (0.19s) | 69.2% (487/704) | 74.1% (123/166) | 69.4% (43/62) | 67.2% (92/137 killed) | 100% Passing ✅ |
| **demo4** | Gemma 4 31B Thinking (Kilo Code) | Circular buffer + Wrapper nodes | 15 (0.59s) | 57.5% (405/704) | 60.8% (101/166) | 54.8% (34/62) | 54.7% (75/137 killed) | 6 Failed ❌ |
| **demo5** | Deepseek V4 Flash Max (Kilo Code) | Circular buffer + Wrapper nodes | 44 (0.78s) | 71.4% (503/704) | 77.1% (128/166) | 77.4% (48/62) | 74.5% (102/137 killed) | 2 Failed ❌ |
| **demo6** | Claude Opus 5 Ultra (Claude) | Circular buffer + Wrapper nodes | 130 (2.73s) | 71.4% (503/704) | 77.1% (128/166) | 77.4% (48/62) | 77.4% (106/137 killed) | 2 Failed ❌ |
| **demo7** | Qwen 38 max XHigh (Kilo Code) | Circular buffer + Wrapper nodes | 72 (1.02s) | 67.2% (473/704) | 72.3% (120/166) | 66.1% (41/62) | 59.1% (81/137 killed) | 4 Failed ❌ |
| **demo8** | Gemini 3.7 Flash High (Kilo Code) | Circular buffer + Wrapper nodes | 42 (1.04s) | 71.4% (503/704) | 77.1% (128/166) | 77.4% (48/62) | 73.0% (100/137 killed) | 1 Failed ❌ |
| **demo9** | Gemini 3.8 Flash High (Antigravity) | Circular buffer + Wrapper nodes | 46 (0.77s) | 71.4% (503/704) | 77.1% (128/166) | 77.4% (48/62) | 75.9% (104/137 killed) | 1 Failed ❌ |

---

## 🔗 Quick Links

- 📊 **Interactive HTML Dashboard**: [index.html](index.html)
- 🧪 **Surefire Unit Test Reports**: [surefire.html](surefire.html)
