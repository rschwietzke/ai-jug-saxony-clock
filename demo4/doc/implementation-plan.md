# Implementation Plan: Unit Tests for LRUClockMap

## Objective
Implement comprehensive JUnit 5 unit tests for the `LRUClockMap` interface to validate any future implementation.

## Test Strategy
Since the `LRUClockMap` is a cache-like map with a Clock (Second Chance) eviction policy, tests must cover basic map operations and the specific LRU/Clock eviction behavior.

### 1. Basic Map Functionality
- `put` and `get`: Verify that values can be stored and retrieved.
- `put` (update): Verify that putting an existing key updates the value.
- `remove`: Verify that keys can be removed and `put` returns the correct previous value.
- `size`: Verify that the size increments/decrements correctly.
- `clear`: Verify that the map is empty after calling clear.
- `keys`: Verify that all current keys are returned.

### 2. Constraint Testing
- `Null Keys/Values`: Verify that putting null keys or values throws an exception (as per interface documentation "No support for null keys or null values").
- `Minimum Size`: Test that attempting to create a map with `maxSize < 4` throws `IllegalArgumentException` (guidance in interface).

### 3. Clock/LRU Eviction Logic
The Clock algorithm uses a "second chance" bit. 
- `get` sets the second chance bit.
- `getRaw` does NOT set the second chance bit.
- When the map reaches `maxSize`, it iterates through entries:
    - If the bit is 1, set it to 0 and move to the next.
    - If the bit is 0, evict this entry.

**Test Scenarios for Eviction:**
- **Simple Eviction**: Fill map to `maxSize`. Add one more. Verify an entry is evicted.
- **Second Chance (get)**:
    1. Fill map to `maxSize`.
    2. Call `get(key)` on some entries (sets bit to 1).
    3. Add a new entry.
    4. Verify that entries called with `get` were preserved (bit was reset to 0) and those not called (or called with `getRaw`) were candidates for eviction.
- **getRaw vs get**: 
    1. Fill map.
    2. Call `getRaw(key)`. This should NOT set the second chance bit.
    3. Add new entry.
    4. Verify that the entry accessed via `getRaw` is evicted before entries accessed via `get`.

## Technical Requirements
- **JUnit 5** only.
- **Java 21** features (e.g., `var`, sequenced collections if applicable).
- No additional libraries.

## Execution Steps
1. Create `LRUClockMapTest.java` in `src/test/java/org/jugsaxony/clock/demo4/`.
2. Use a mock or placeholder implementation if needed for compilation, but the tests will be written against the interface. *Correction*: Since I am providing tests for a future implementation, I will assume the existence of a factory method `LRUClockMap.of(int maxSize)` as suggested in the interface comments, or I will structure the tests to accept an instance. However, the interface says `of` is "implementation guidance". I will implement the tests assuming a static factory method `LRUClockMap.of(int maxSize)` exists in the final implementation.
