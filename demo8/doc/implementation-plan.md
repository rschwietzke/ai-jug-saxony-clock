# Implementation Plan: LRUClockMap Unit Test Suite

## 1. Overview
The goal is to provide a comprehensive, rigorous unit test suite for the `LRUClockMap<K, V>` interface located in `/home/rschwietzke/projects/GIT/ai-jug-saxony-clock/demo8/`.
The interface defines an open-addressing hash table with Clock (Second-Chance) LRU page replacement algorithm semantics.
The test suite must be built strictly using **JUnit 5 (Jupiter)** without any third-party assertion/mocking libraries (no AssertJ, Mockito, etc.) and leveraging modern **Java 21** features (pattern matching, switch expressions, `var`, records, enhanced collections, etc.).

## 2. Factory and Instantiation Strategy
Since `LRUClockMap` is an interface and the concrete implementation class name might be supplied later:
- A factory method / supplier mechanism will be used to instantiate `LRUClockMap<K, V>`.
- The factory will attempt to instantiate the map via reflection looking for:
  1. A static `LRUClockMap.of(int maxSize)` factory method if present on the interface or concrete class.
  2. Any concrete implementation of `LRUClockMap` in `org.jugsaxony.clock.demo8` package with an `of(int)` method or constructor `(int)`.
  3. A default/fallback implementation or test-provided factory method that can be configured.
- When no concrete implementation is in the classpath yet, tests can use a test factory helper that dynamically discovers or allows injecting the instance, and provides clear diagnostic messages if no implementation is compiled yet.

## 3. Test Categories & Scenarios

### 3.1 Instantiation & Validation
- **Invalid `maxSize`**:
  - `maxSize < 4` (e.g. -10, 0, 1, 2, 3) must throw `IllegalArgumentException`.
- **Valid `maxSize`**:
  - `maxSize >= 4` (e.g. 4, 5, 8, 16, 100, 1000) initializes successfully with `size() == 0` and empty `keys()`.

### 3.2 Basic Map Operations
- **`put(key, value)`**:
  - Inserting a new key into empty/non-full map returns `null`.
  - Updating an existing key returns the previous value.
  - Size increments on new key insertion; size remains unchanged on update.
  - Verifying `get(key)` and `getRaw(key)` return the inserted value.
- **`get(key)` & `getRaw(key)`**:
  - Existing keys return their mapped values.
  - Non-existent keys return `null`.
  - Looking up keys in empty map returns `null`.
- **`remove(key)`**:
  - Removing an existing key returns its previous value, decrements size by 1.
  - Subsequent `get(key)` / `getRaw(key)` return `null`.
  - Key is removed from `keys()`.
  - Removing a non-existent key returns `null` and does not alter size.
  - Re-inserting a previously removed key functions correctly.
- **`size()`**:
  - Reflects exact count of active entries after puts, overwrites, removes, clears, and evictions.
- **`clear()`**:
  - Resets `size()` to 0.
  - `keys()` becomes empty.
  - All previously stored keys return `null` on `get`/`getRaw`.
  - Map can be reused after `clear()` for new insertions up to `maxSize`.
- **`keys()`**:
  - Returns a `List<K>` of current keys.
  - Contains exactly `size()` elements.
  - Modifying the returned list does not affect the map's internal state.
  - Returns empty list when map is empty.

### 3.3 Null Handling & Edge Cases
- Putting `null` key or `null` value (throws `NullPointerException` or `IllegalArgumentException`).
- Getting with `null` key (throws or returns `null` safely).
- Removing with `null` key.

### 3.4 Open Addressing & Hash Collision Handling
- Custom key objects with identical `hashCode()` but different `equals()` to force collisions.
- Probing behavior across inserts, updates, and deletes.
- Verifying tombstone / shift handling in open addressing:
  - Insert colliding keys A, B, C.
  - Remove B.
  - Verify A and C can still be found with `get`/`getRaw`.
  - Re-insert into removed slot.

### 3.5 Clock (Second-Chance) LRU Eviction Logic
- **Second-Chance Bit Semantics**:
  - Newly inserted key has second chance set (or initial chance).
  - Calling `get(key)` sets/renews the second chance bit.
  - Calling `getRaw(key)` does NOT set or renew the second chance bit.
- **Capacity & Eviction Trigger**:
  - Up to `maxSize` elements: No eviction occurs.
  - Inserting `maxSize + 1`-th distinct key triggers eviction of one element. Size stays at `maxSize`.
  - Evicted key is completely removed (`get(key) == null`, not in `keys()`).
- **Clock Hand Traversal & Second Chance Reset**:
  - When all elements have second-chance flag set, clock hand traverses, clearing flags until it sweeps back and evicts.
  - When some elements have second-chance flag cleared (e.g. by prior clock sweeps or untouched after initial insertion where access sets it), entries without second chance are evicted first.
  - Verification of `getRaw` vs `get`: Accessing elements with `getRaw` leaves their second chance unrenewed; accessing with `get` protects them from immediate eviction during clock sweep.

### 3.6 Boundary & Stress / Consistency Testing
- Smallest allowed map size (`maxSize = 4`).
- Filling to capacity, heavy random workload of `put`, `get`, `getRaw`, `remove`, ensuring:
  - `size()` never exceeds `maxSize`.
  - `keys().size() == size()`.
  - Every key in `keys()` returns its non-null mapped value via `getRaw`.
  - Map invariants remain consistent throughout.
- Heavy eviction churn: inserting 10,000 distinct keys into a map of `maxSize = 16`, ensuring steady state size == 16 and valid operations.

## 4. Technical Requirements & Standards
- Framework: JUnit 5 (org.junit.jupiter.api.*, org.junit.jupiter.params.*).
- Assertions: `org.junit.jupiter.api.Assertions.*` only.
- Java Version: Java 21 features (records for test data/colliding keys, pattern matching, typed switch expressions, `var`, `List.of()`, etc.).
- Package: `org.jugsaxony.clock.demo8`.
- Test Files:
  - `LRUClockMapTest.java`: Comprehensive unit tests covering all functional areas, edge cases, open addressing collisions, and clock eviction behavior.
  - `LRUClockMapFactory.java`: Reflection-based factory for instantiating the implementation dynamically.
