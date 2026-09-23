# Implementation Plan: Unit Tests for LRUClockMap

## Goal

Create a JUnit 5 test suite that validates a future implementation of the
`LRUClockMap<K, V>` interface (`src/main/java/org/jugsaxony/clock/demo7/LRUClockMap.java`)
purely from its documented contract. No additional test or support libraries
(no AssertJ, no Mockito) — JUnit Jupiter only. Java 21 features are used
throughout.

## Constraints Derived from the Interface

The interface documentation defines what is testable and what is explicitly
unspecified:

| Contract point | Source | Testable? |
|---|---|---|
| Factory `of(int maxSize)`, load factor fixed at 0.5f | Javadoc "implementation guidance" | yes (via reflection, see below) |
| `IllegalArgumentException` if `maxSize < 4` | factory Javadoc | yes |
| `get` returns value or null, sets second-chance flag | method Javadoc | yes (flag effect tested behaviorally) |
| `getRaw` returns value or null, does **not** touch the flag | method Javadoc | partially (see "Non-deterministic behavior") |
| `put` returns previous value or null, replaces, evicts at max size | method Javadoc | yes |
| Eviction picks "**another** key" (never the one just inserted) | class Javadoc of `of` | yes |
| `remove` returns previous value or null | method Javadoc | yes |
| `size` = number of mappings | method Javadoc | yes |
| `keys()` in internal array order, no LRU effect, detached copy | method Javadoc | content/detachment yes, exact order no |
| `clear()` empties but reuses the structure | method Javadoc | yes (observable: empty + reusable) |
| No null keys / null values | class Javadoc | yes (rejection expected, exception type kept lenient) |
| Exact eviction victim / LRU order | "not really predictable" | **never asserted** |

## Design Decisions

### 1. Implementation discovery via reflection (`TestMapFactory`)

The tests must compile and run before the implementation exists, and the
implementation class name is unknown. `LRUClockMap.of(int)` is commented out
in the interface, so it cannot be called directly. `TestMapFactory` resolves
the implementation at runtime in this order:

1. Class named by system property `lruclockmap.impl`
   (e.g. `mvn test -Dlruclockmap.impl=com.example.MyMap`).
2. `public static of(int)` on the `LRUClockMap` interface itself (in case the
   implementation uncomments/adds it, as the Javadoc suggests).
3. Conventional name `org.jugsaxony.clock.demo7.LRUClockMapImpl`.
4. Best-effort scan of the `org.jugsaxony.clock.demo7` package directory on
   the classpath for a concrete class implementing `LRUClockMap` with either
   a `static of(int)` or an `(int)` constructor.

Accepted factory shapes: `static of(int)` returning an `LRUClockMap`, or a
public `(int)` constructor. Exceptions thrown by the factory (notably the
`IllegalArgumentException` for `maxSize < 4`) are propagated unchanged — the
factory unwraps `InvocationTargetException` using a Java 21 switch pattern
match (including `case null`).

If nothing is found, tests fail with a descriptive `IllegalStateException`
listing every candidate tried.

### 2. Test organization

One test class `LRUClockMapTest` with `@Nested` groups mirroring the contract
areas, `@DisplayName` on every level, and `@ParameterizedTest` +
`@ValueSource` for capacity-dependent behavior (maxSize 4, 8, 16, 64):

- **FactoryTests** — rejection of maxSize < 4 (including `MIN_VALUE`, 0, 3),
  acceptance of the boundary 4, usability for various sizes, instance
  independence.
- **PutGetTests** — return values of `put` (null / previous), `get`/`getRaw`
  hit and miss, agreement between `get` and `getRaw`, repeatability,
  value identity (`assertSame`), record keys via `equals`/`hashCode`,
  string keys, size stability on replace.
- **NullTests** — `put(null, v)` and `put(k, null)` must throw a
  `RuntimeException`. Deliberately lenient about the exact type
  (`NullPointerException` is the natural choice, but the Javadoc only says
  "no support").
- **RemoveTests** — return values, double remove, unknown key, size effects,
  re-insertion, removing everything.
- **SizeKeysClearTests** — empty map invariants, `size` counting distinct
  keys, `keys()` content (set equality — order is internal-array order and
  unspecified to the test), `keys()` stability across calls, detachment of
  the returned list (tolerates unmodifiable lists), `keys().size() == size()`,
  `clear` semantics and full reusability afterwards (including "old keys do
  not resurrect").
- **EvictionTests** — no eviction at or below capacity; `size` capped at
  maxSize under 10x overfill; the newest key is never the eviction victim;
  overwrite of a present key when full never evicts; `remove` frees capacity;
  second-chance protection (below); consistency under heavy churn
  (`size <= max`, `keys()` matches `size()`, every reported key resolves via
  `getRaw`); interleaved put/remove/get rounds.
- **LargerMapTests** — 1000 entries in a 1024-capacity map, keys snapshot.

### 3. Testing the second-chance (clock) flag

The flag is internal state, so it is tested behaviorally, and only where the
outcome is contract-guaranteed:

- **`get` protects**: fill to capacity, then repeatedly `get(hotKey)`
  immediately before each over-filling `put`. In a correct second-chance
  clock, a single eviction sweep can at most clear the hot key's flag
  (giving it the second chance) and must then evict some other entry,
  because the hand only evicts entries whose flag it finds cleared *at the
  moment it arrives* — the hot key was re-armed before every sweep. After
  3x maxSize churn the hot key must still be present.
- **`getRaw` does not protect**: *not asserted deterministically.* Whether a
  cold key is evicted depends on hand position and probing locality, which
  the contract declares unpredictable. A strict assertion here would be
  flaky against correct implementations, so `getRaw` is only tested for its
  documented return-value contract and used as the neutral lookup in all
  eviction tests (to avoid accidentally arming flags).

### 4. Non-deterministic behavior explicitly not asserted

- Which key is evicted (other than "not the one just inserted").
- The order of `keys()` (only stability between calls and set content).
- Internal capacity/layout (load factor 0.5f is implementation guidance, not
  observable through the interface).

### 5. Java 21 features used

- `record` types (`Point`, `Data`) as test keys/values.
- `var` for local type inference throughout.
- Sequenced collections: `List.getFirst()` / `getLast()` (Java 21).
- Switch pattern matching with `case null` in `TestMapFactory` exception
  unwrapping.
- Text blocks for the multi-line factory failure message.

## Files

```
demo7/
├── doc/implementation-plan.md                          (this document)
└── src/test/java/org/jugsaxony/clock/demo7/
    ├── TestMapFactory.java                             (runtime implementation discovery)
    └── LRUClockMapTest.java                            (72 test executions, @Nested groups)
```

No changes to `pom.xml` are required: `junit-jupiter` is already declared;
AssertJ/JMH/JOL are present in the POM but deliberately unused.

## Validation Performed

1. A throwaway reference implementation (open addressing, linear probing,
   clock hand with second-chance flags, rebuild-on-removal) was written
   **outside the deliverable** and temporarily compiled into `src/main` to
   validate the suite: **72/72 tests green**. It caught one real bug in the
   scratch implementation (naive slot-clearing on eviction breaks probe
   chains), demonstrating the churn tests have teeth. The scratch file was
   deleted afterwards; `src/main` contains only the original interface.
2. Without any implementation on the classpath, all tests fail fast with the
   descriptive "No LRUClockMap implementation found" message listing tried
   candidates — `mvn test-compile` still succeeds.

## How to Run Against the Real Implementation

```bash
mvn test                                        # auto-discovers of(int)/impl class
mvn test -Dlruclockmap.impl=com.acme.MyClockMap # explicit implementation class
```

Requirements for the implementation to be discovered: a `public static
of(int)` returning `LRUClockMap`, or a public constructor taking `int`,
either on the interface, on a class named `LRUClockMapImpl` in
`org.jugsaxony.clock.demo7`, on any concrete `LRUClockMap` subclass in that
package, or on the class named by `lruclockmap.impl`.
