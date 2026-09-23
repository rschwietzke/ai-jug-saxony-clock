# Implementation Plan - LRUClockMap Unit Test Suite

Implement a comprehensive, robust JUnit 5 unit test suite for the `LRUClockMap` interface based strictly on its specification and contract.

## User Review Required

> [!IMPORTANT]
> The test suite is designed for JUnit 5 only without third-party test or mocking libraries (e.g. no AssertJ, no Mockito), utilizing Java 21 features (records, pattern matching, sequenced collections, `var`).
> It calls `LRUClockMap.of(maxSize)` to instantiate map instances, matching the factory method documented in `LRUClockMap.java`.

## Key Objectives & Requirements

1. **Target**: `org.jugsaxony.clock.demo1.LRUClockMap<K, V>` in `/home/rschwietzke/projects/GIT/ai-jug-saxony-clock/demo1/`.
2. **Library Constraints**: Standard JUnit 5 (`org.junit.jupiter.api.*`, `@ParameterizedTest`, etc.) only.
3. **Language**: Java 21 features (`record`, pattern matching, `var`, etc.).
4. **Scope**: Comprehensive coverage of the interface contract:
   - Factory method / maxSize validation (boundary `< 4`).
   - Null handling (null keys / null values rejected with `NullPointerException`).
   - Core map operations (`put`, `get`, `getRaw`, `remove`, `size`, `clear`, `keys`).
   - Second-chance / Clock eviction policy mechanics (`get` setting second chance bit vs `getRaw` not setting it).
   - Open addressing & hash collision handling (same `hashCode`, different `equals`, probing chains across removals).
   - Re-insertion of removed/evicted keys, map reuse after `clear()`, defensive copy of `keys()`.
   - Churn / stress test under capacity pressure.

---

## Proposed Test Architecture

The test suite will be located at:
`src/test/java/org/jugsaxony/clock/demo1/LRUClockMapTest.java`

Structured cleanly with `@Nested` test classes:

1. **`InstantiationTests`**:
   - `of(maxSize)` succeeds for `maxSize >= 4` (e.g., 4, 5, 10, 100, 1024).
   - `of(maxSize)` throws `IllegalArgumentException` for `maxSize < 4` (tested via `@ValueSource(ints = {3, 2, 1, 0, -1, -100, Integer.MIN_VALUE})`).

2. **`NullContractTests`**:
   - `put(null, value)`, `put(key, null)`, `put(null, null)` throw `NullPointerException`.
   - `get(null)`, `getRaw(null)`, `remove(null)` throw `NullPointerException`.

3. **`BasicOperationsTests`**:
   - Initial state: `size() == 0`, `keys()` is empty, `get(key) == null`, `getRaw(key) == null`.
   - `put(K, V)`:
     - New key returns `null`, increases `size()`.
     - Existing key updates value, returns previous value, does not change `size()`.
   - `get(K)` & `getRaw(K)`:
     - Non-existent key returns `null`.
     - Existing key returns mapped value.
   - `remove(K)`:
     - Non-existent key returns `null`, does not change `size()`.
     - Existing key returns old value, decreases `size()`, subsequent `get`/`getRaw` returns `null`.
     - Re-inserting removed key works cleanly.
   - `clear()`:
     - Resets `size()` to 0, `keys()` to empty list.
     - Previously stored keys return `null`.
     - Map is fully reusable after `clear()`.
   - `keys()`:
     - Returns a `List<K>` containing exactly all active keys without nulls or duplicates.
     - Size matches `size()`.
     - Modifying returned list does not affect internal map state (defensive copy / immutability).
     - Does not cause LRU effects.

4. **`EvictionAndClockPolicyTests`**:
   - **Capacity Invariant**: Adding `N > maxSize` entries keeps `size() <= maxSize`.
   - **Eviction Trigger**: When `size() == maxSize`, putting a new key evicts an entry.
   - **Update vs Evict**: When `size() == maxSize`, putting an existing key updates the value without evicting any entry (`size()` remains `maxSize`).
   - **Clock / Second Chance Flag Differentiation**:
     - Insert `maxSize` entries.
     - Access specific entries via `get(key)` (sets second-chance bit).
     - Leave other entries untouched or accessed via `getRaw(key)` (does not set second-chance bit).
     - Insert new entries to force eviction.
     - Verify that entries with second-chance bit (`get()`) survive longer than untouched / `getRaw()` entries.
   - **Re-insertion after eviction**: An evicted key can be put again and functions normally.

5. **`CollisionAndOpenAddressingTests`**:
   - Using a custom Java 21 `record CollidingKey(int id, int forcedHash)` with customizable hash code.
   - Store multiple keys that hash to the exact same bucket.
   - Verify retrieval (`get`, `getRaw`) of all colliding keys.
   - Verify `remove` of an intermediate key in the collision chain doesn't break probing for remaining keys (tombstone/shift handling).
   - Verify update of colliding keys updates only the targeted key.

6. **`StressAndLifecycleTests`**:
   - Heavy churn test: insert, access (`get` and `getRaw`), remove, and overwrite thousands of entries.
   - Multi-cycle clear and refill tests.
   - Multiple data types (Java 21 records, Strings, Integers).

---

## Verification Plan

### Automated Tests
- Run `mvn -f /home/rschwietzke/projects/GIT/ai-jug-saxony-clock/demo1/pom.xml test-compile` to ensure test files compile cleanly without warnings/errors.
- When an implementation is supplied, run `mvn -f /home/rschwietzke/projects/GIT/ai-jug-saxony-clock/demo1/pom.xml test` to validate the implementation.
