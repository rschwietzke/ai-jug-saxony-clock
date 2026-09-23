# LRUClockMap Unit Test Implementation Plan

## Constraints

- Treat `src/main/java/org/jugsaxony/clock/demo3/LRUClockMap.java` as the only behavioral specification.
- Use JUnit Jupiter only; do not use AssertJ, Mockito, or any other test/support library.
- Compile and run on Java 21 without preview features.
- Exercise only the public API and do not inspect implementation fields or backing arrays.
- Do not require a particular hash-table slot order or hard-code which entry the clock evicts first.

## Factory Strategy

The interface documents `public static <K, V> LRUClockMap<K, V> of(int maxSize)` as the only creation mechanism, but leaves the declaration commented out until the interface is replaced by an implementation. Tests will invoke this documented factory through Java reflection and return the result as `LRUClockMap<K, V>`. This keeps the test source compilable while the placeholder interface is present and automatically targets a same-name replacement implementation without changing the tests.

The factory helper will unwrap `InvocationTargetException` so tests can assert the implementation's original exception for invalid capacities rather than a reflection wrapper.

Validation after replacing the interface must use a clean recompilation so bytecode compiled against the interface is not reused after `LRUClockMap` becomes a class.

## Test Coverage

1. Verify the minimum supported capacity is four and all smaller representative values are rejected with `IllegalArgumentException`.
2. Verify every factory call returns a fresh map whose contents are independent from other maps.
3. Verify a new map is empty and missing-key operations return `null` without changing its size.
4. Verify `put` inserts mappings, returns `null` for new keys, and makes values available through both `get` and `getRaw`.
5. Verify replacing an existing key returns the old value, exposes the new value, and does not change the size or key set.
6. Verify keys use equality rather than object identity, including replacement and removal through equal-but-distinct keys at capacity.
7. Verify colliding keys remain independently retrievable, replaceable, and removable, including removal from the middle of an open-addressing collision cluster.
8. Verify keys with negative hash codes, including `Integer.MIN_VALUE`, work correctly.
9. Verify `remove` returns the removed value, removes only the requested mapping, and permits reinsertion.
10. Verify removal from a full map frees capacity so the next new mapping does not evict a survivor.
11. Verify `clear` empties the map and that the cleared allocation can be reused normally.
12. Verify `keys` returns every key exactly once, does not promise internal order, and cannot be changed in a way that affects the map.
13. Verify reaching `maxSize` does not evict an entry, while inserting a new key beyond it keeps the size bounded, retains the new mapping, and evicts exactly one old mapping.
14. Verify repeated overflow insertions never let the map exceed its configured maximum.
15. Verify replacing a value at capacity does not evict another entry.

## Test Data and Helpers

- Use records for purpose-built keys with controlled hash codes and a Java 21 pattern-matching `switch` when unwrapping factory failures.
- Compare key snapshots as sets where order is explicitly unspecified.
- Infer eviction victims by taking key snapshots before and after one overflow insertion; do not rely on backing-array positions.
- Keep assertions focused on documented behavior. Null inputs are outside the supported domain, but the interface does not specify their result or exception behavior, so the suite will not prescribe one.
- Do not compare exact eviction victims across instances. The interface explicitly says its approximate LRU result is not predictable and does not require deterministic layouts, clock positions, or randomization across maps.
- Exercise both `get` and `getRaw` as lookups. Their different clock-flag side effects cannot be asserted through the public API without assuming an eviction order that the interface deliberately leaves unpredictable.

## Validation

- Compile the test sources against the placeholder interface to catch syntax, generic type, and JUnit API errors.
- Expect execution against the placeholder to fail at the absent factory; full behavioral execution becomes possible after `LRUClockMap` is replaced by the implementation.
- Run `mvn clean test` against the same-name replacement implementation and investigate any failure as either an implementation defect or an assertion stricter than the interface contract.
