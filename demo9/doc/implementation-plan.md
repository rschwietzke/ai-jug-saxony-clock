# Implementation Plan: Comprehensive Unit Tests for LRUClockMap Interface

Implement a comprehensive JUnit 5 unit test suite for the `LRUClockMap` interface in `demo9/`, utilizing Java 21 features and strict standard JUnit 5 assertions only (no external testing libraries).

## User Review Required

> [!IMPORTANT]
> The test suite is designed to test against the `LRUClockMap` interface and instantiate the implementation using reflection (supporting both `LRUClockMap.of(int maxSize)` static factory method and `new LRUClockMap(int maxSize)` constructor, as well as an overridable `createMap(int maxSize)` template method). This ensures that the test code compiles against the current codebase (`mvn test-compile`) while being immediately ready to validate the real implementation once provided.

## Test Scope & Specifications

Based on the contract in [`LRUClockMap.java`](file:///home/rschwietzke/projects/GIT/ai-jug-saxony-clock/demo9/src/main/java/org/jugsaxony/clock/demo9/LRUClockMap.java):

1. **Map Creation & Sizing Constraints (`maxSize`)**:
   - `maxSize < 4` must throw `IllegalArgumentException` (testing boundary values: `-10`, `0`, `1`, `2`, `3`).
   - `maxSize >= 4` must succeed (testing boundary `4`, plus larger sizes `5`, `10`, `16`, `100`).
   - Initial state: `size() == 0`, `keys()` is empty, `get(...) == null`, `getRaw(...) == null`.

2. **Null Contract**:
   - Rejection of `null` keys and `null` values (`put(null, val)`, `put(key, null)`, `put(null, null)` throw `NullPointerException` or `IllegalArgumentException`).

3. **Core CRUD Behavior**:
   - `put`: returns `null` for new keys; returns previous value for existing keys; updates value without increasing `size()`.
   - `get`: returns value if present, `null` if absent.
   - `getRaw`: returns value without updating second-chance flag.
   - `remove`: returns previous value if present and decrements `size()`; returns `null` if absent; idempotent removals.
   - `size`: accurately tracks count across additions, updates, removals, evictions, and clear.
   - `clear`: empties the map (`size() == 0`, `keys().isEmpty() == true`), allows clean reuse.

4. **Keys List Contract**:
   - `keys()` returns all keys currently mapped.
   - Defensive copy: modifying the returned list does not alter map state.
   - Calling `keys()` does not cause LRU or clock hand side effects.

5. **Second-Chance / Clock Eviction Dynamics**:
   - Map size never exceeds `maxSize`.
   - When full (`size == maxSize`), inserting a new key triggers eviction of one key.
   - Updating an existing key when full does not trigger eviction.
   - Difference between `get` (sets second chance flag) and `getRaw` (does not touch clock flag):
     - An entry touched via `get` is given a second chance, surviving eviction when an entry with second chance `false` is available.
     - An entry touched only via `getRaw` is evicted when its turn comes.

6. **Open Addressing & Hash Collision Resilience**:
   - Keys with identical hash codes (using a Java 21 `record CollidingKey(String id, int forcedHash)`).
   - Collision resolution across insertion, lookup (`get`/`getRaw`), updates, and removal.
   - Tombstone / probe chain integrity: removing an entry in the middle of a collision chain does not orphan subsequent entries.

7. **Stress & Churn Scenarios**:
   - Continuous insertion cycles exceeding capacity to verify eviction stability and absence of probe loops or deadlocks.

## Proposed Changes

### `demo9/src/test/java`

#### [NEW] [`LRUClockMapTest.java`](file:///home/rschwietzke/projects/GIT/ai-jug-saxony-clock/demo9/src/test/java/org/jugsaxony/clock/demo9/LRUClockMapTest.java)
- Main JUnit 5 test suite organized with `@Nested` test classes for readability and focused testing:
  - `InstantiationTests`: testing `maxSize < 4`, initial empty state.
  - `BasicOperationsTests`: `put`, `get`, `getRaw`, `remove`, `size`, `clear`.
  - `NullContractTests`: null key and null value rejections.
  - `KeysContractTests`: `keys()` content, immutability/defensive copying, non-interference with LRU.
  - `ClockEvictionTests`: eviction triggering at `maxSize`, LRU second chance mechanics (`get` vs `getRaw`).
  - `CollisionTests`: open addressing probe chains and tombstone handling with custom colliding hash records.
  - `StressTests`: large volume insertions, churn, capacity bounds verification.
- Uses Java 21 features:
  - Local `record` definitions (e.g., `CollidingKey`).
  - Pattern matching for switch / type checks where appropriate.
  - `var` for concise, type-inferred local variables.
- Uses strictly JUnit 5 (`org.junit.jupiter.api.Assertions.*`).

## Verification Plan

### Automated Tests
- Run `mvn test-compile` to verify compilation with Java 21 and JUnit 5 dependencies.
- Ensure that no external test libraries (AssertJ, Mockito, etc.) are imported.
