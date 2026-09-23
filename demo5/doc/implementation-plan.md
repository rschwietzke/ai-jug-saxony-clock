# Implementation Plan — JUnit 5 Unit Tests for `LRUClockMap`

## 1. Objective

Produce a JUnit 5 unit-test suite for the interface
`org.jugsaxony.clock.demo5.LRUClockMap<K, V>` located in
`src/main/java/org/jugsaxony/clock/demo5/LRUClockMap.java`.

The tests must validate the *contract described by the interface Javadoc* and
are intended to be run later against a concrete implementation that replaces /
implements the interface. The tests are the specification of expected behavior.

Constraints:

- JUnit 5 (Jupiter) only. No AssertJ, Mockito, Hamcrest, Truth, etc.
- Java 21 language features where they help readability (records, `var`,
  arrow `switch`, `List.of`, streams, pattern matching).
- The `LRUClockMap` interface file is the only source of truth. No other
  modules, no VCS history, no existing implementations were inspected.

## 2. Deliverable

- `src/test/java/org/jugsaxony/clock/demo5/LRUClockMapTest.java`
- This plan: `doc/implementation-plan.md`

The test class is a black-box test suite: it only uses the public methods of
`LRUClockMap` and the documented static factory.

## 3. How instances are created

The interface documents that the *only* way to create a map is the (currently
commented-out) static factory:

```java
public static <K, V> LRUClockMap<K, V> of(final int maxSize);
```

All test code therefore goes through a single private helper:

```java
private static <K, V> LRUClockMap<K, V> newMap(final int maxSize) {
    return LRUClockMap.of(maxSize);
}
```

This keeps the creation point centralized: if the future implementation exposes
a different factory name/signature, only this one line has to change.

Because the factory is commented out in the current interface, the suite cannot
compile until the implementation is dropped in. That is expected — the suite is
written to be the validation harness for that future implementation.

## 4. Contract extracted from the interface Javadoc

| # | Contract |
|---|----------|
| C1 | `of(maxSize)` throws `IllegalArgumentException` when `maxSize < 4`. |
| C2 | `of(maxSize)` succeeds for `maxSize >= 4`; new map has size 0 and empty `keys()`. |
| C3 | `getRaw(key)` returns the stored value without touching the clock/second-chance flag; `null` if absent. |
| C4 | `get(key)` returns the stored value and sets the second-chance flag; `null` if absent. |
| C5 | `put(key, value)` associates the value, returns the previous value or `null`. |
| C6 | `put` of an existing key replaces the value, does not grow the map, does not evict. |
| C7 | When the map is at `maxSize`, `put` of a *new* key evicts exactly one existing entry (clock / second-chance, "good enough LRU"). |
| C8 | The just-inserted key is always retrievable, even when insertion triggered an eviction. |
| C9 | A key accessed via `get` receives a second chance and is not the victim of the next single eviction while other, untouched entries are present. |
| C10 | `remove(key)` removes the mapping and returns the previous value, or `null` if absent. |
| C11 | `size()` returns the number of mappings; never exceeds `maxSize`. |
| C12 | `keys()` returns the keys in internal-array order without free slots; empty list when empty; no duplicates/null. |
| C13 | Changing the list returned by `keys()` must not change the internal structure (it is a copy / snapshot). `keys()` itself causes no LRU effect. |
| C14 | `clear()` removes all mappings and the map remains usable ("reuses the data structure"). |
| C15 | No support for `null` keys or `null` values (expect `NullPointerException`). |
| C16 | Open addressing: hash collisions must be handled correctly for put/get/remove. |
| C17 | `maxSize` bounds held under arbitrary operation sequences (invariant test). |

## 5. Test structure

One top-level class `LRUClockMapTest` with `@Nested` groups:

1. **Creation**
   - parameterized invalid sizes (`Integer.MIN_VALUE` … `3`) → `IllegalArgumentException`
   - parameterized valid sizes (`4, 5, 8, 16, 1000`) → empty map
2. **BasicReadWrite**
   - new map is empty
   - `put` new key returns `null`, grows size
   - `get` / `getRaw` return stored value
   - `get` / `getRaw` return `null` for missing key, size unchanged
   - replacing a key returns previous value, size unchanged, value updated
   - `get` / `getRaw` do not change size or `keys()`
   - record-typed keys (Java 21 record) work — exercises `equals`/`hashCode`
   - hash collisions (`"Aa"` / `"BB"`, known to share `hashCode`) survive
     put/get/remove/re-put
3. **Removal**
   - `remove` returns previous value and shrinks size
   - `remove` of missing key returns `null`, size unchanged
   - removed key is no longer readable; removing again returns `null`
4. **KeyListing**
   - `keys()` empty for new map
   - contains exactly the live keys, no duplicates, no `null`, size matches `size()`
   - `keys()` order is stable across calls while the map is unchanged
   - `keys()` returns an independent snapshot: later `put`s don't appear in an
     earlier snapshot; attempting to modify the returned list never changes the
     map (mutation is allowed or refused, but the map must be unaffected)
5. **Clearing**
   - `clear` empties size/keys and makes `get` return `null`
   - map is reusable after `clear`
   - `clear` on an empty map is a no-op
6. **Eviction**
   - filling to `maxSize` works; the next new key keeps size at `maxSize`
   - exactly one previously-present key disappears (victim determined by set
     difference, *not* hard-coded — the clock victim is implementation-defined)
   - the newly inserted key is present
   - updating an existing key while full evicts nothing
   - inserting after a `remove` (size < `maxSize`) evicts nothing
   - loop: repeated insertions of fresh keys keep size at `maxSize` and each new
     key stays retrievable
7. **SecondChance**
   - `maxSize = 4`, insert `A,B,C,D`, call `get(A)`, insert `E`:
     `A` must survive, `E` must be present, exactly one of `B,C,D` was evicted.
     This encodes the documented clock semantics ("a marker that gives every
     entry a second chance").
8. **NullArguments**
   - `put(null, v)`, `put(k, null)`, `get(null)`, `getRaw(null)`, `remove(null)`
     → `NullPointerException`.
9. **Invariants** (randomized)
   - seeded `Random`, several thousand mixed `put`/`get`/`remove` operations
     against an independent `HashSet` model of live keys.
   - after every operation assert: `size() <= maxSize`, `keys().size() == size()`,
     `keys()` equals the model, no duplicates/nulls, every listed key is
     readable, and a new key inserted while full displaced exactly one key.
   - finish with `clear()` and re-verify emptiness.

## 6. What is deliberately *not* asserted (over-specification avoidance)

The interface only promises "a good enough LRU concept"; it does not define the
exact victim. Therefore the tests never hard-code *which* key is evicted, except
for the second-chance guarantee (C9) which follows directly from the wording
"every entry a second chance" combined with the fact that only `get` is
documented to set the flag. Tests also do not assert the internal array order
from `keys()` (only stability and content), and do not assert the exact internal
capacity derived from the fixed `0.5f` load factor.

Assumption recorded explicitly: `put` does **not** set the second-chance flag
(the Javadoc names only `get` as setting it); the C9 test relies on this.

## 7. Validation of the plan itself

To prove the suite is correct and actually runs, a throwaway concrete
implementation of the clock algorithm was created in a scratch Maven project
outside the workspace (`/tmp`), together with a standalone `pom.xml` pinning
`junit-jupiter` (no parent POM needed). Results:

- **44 tests, 0 failures, 0 errors** with `mvn -o test`.
- Mutation check 1 — making `put` set the second-chance flag to `true`:
  `SecondChance.readKeyGetsASecondChance` fails (the suite pins the documented
  semantics that only `get` grants the second chance).
- Mutation check 2 — disabling eviction: 5 tests fail
  (`Eviction.evictsExactlyOneWhenFull`, `Eviction.manyInsertionsStayBounded`,
  both `SecondChance` tests and `RandomizedInvariants`).

The scratch artifacts are **not** part of the deliverable; the `demo5` folder
contains only the interface, the test file and this plan.

Test count by group: Creation 14, BasicReadWrite 9, Removal 3, KeyListing 5,
Clearing 3, Eviction 5, SecondChance 2, NullArguments 2, RandomizedInvariants 1.

## 8. How to run later

After the interface is replaced by the implementation:

```bash
mvn -pl demo5 test
# or from inside the module
mvn test
```

No test-only dependencies beyond the already declared `junit-jupiter` are
required.
