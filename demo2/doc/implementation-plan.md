# Implementation Plan — Unit Tests for `LRUClockMap<K, V>`

## 1. Goal and Scope

Create a JUnit 5 (Jupiter) unit test suite for the interface
`org.jugsaxony.clock.demo2.LRUClockMap<K, V>`. The suite is written **before** the
implementation exists and will later be used to validate a real implementation.
The only specification is the Javadoc of `LRUClockMap.java`; no existing
implementation was consulted.

Constraints honored:

- JUnit 5 (Jupiter) only — no AssertJ, no Hamcrest, no Mockito, no other
  testing/support library. Assertions use `org.junit.jupiter.api.Assertions` only.
- Java 21 language level (records, sealed interfaces, pattern matching,
  sequenced collections, `var`, `@Timeout`).
- Nothing outside `demo2/` was read or used for guidance.

## 2. Contract Extracted from the Interface Javadoc

| # | Contract item | Source |
|---|---------------|--------|
| C1 | Instances are created via `LRUClockMap.of(int maxSize)` (static factory, published as implementation guidance). | `of(...)` comment |
| C2 | `of(maxSize)` throws `IllegalArgumentException` if `maxSize < 4`. | `of(...)` Javadoc |
| C3 | Load factor is fixed at `0.5f` (internal, not directly observable). | `of(...)` Javadoc |
| C4 | `getRaw(key)` returns the value or `null`; must **not** touch the clock/second-chance flag. | `getRaw` Javadoc |
| C5 | `get(key)` returns the value or `null`; **sets** the second-chance flag on a hit. | `get` Javadoc |
| C6 | `put(key, value)` returns the previous value or `null`; replaces the old value on re-put; starts eviction when max size is reached. | `put` Javadoc |
| C7 | Eviction follows a "good enough LRU" clock (second-chance) policy; the exact victim is not predictable. | class Javadoc |
| C8 | `remove(key)` returns the previous value or `null`; removes the mapping if present. | `remove` Javadoc |
| C9 | `size()` returns the number of key-value mappings. | `size` Javadoc |
| C10 | `keys()` returns all keys, in internal array order, without free slots; causes no LRU effects; mutating the returned list must not change the map. | `keys` Javadoc |
| C11 | `clear()` empties the map and reuses the data structure. | `clear` Javadoc |
| C12 | No support for null keys or null values. | class Javadoc |
| C13 | Not thread-safe (no concurrency tests). | class Javadoc |
| C14 | Base structure is an open-addressing hash table (relevant for collision/probing behavior). | class Javadoc |

## 3. Interpretation Decisions (documented assumptions)

1. **Instantiation.** The interface publishes
   `public static <K, V> LRUClockMap<K, V> of(int maxSize)` as implementation
   guidance. The task states the interface will later be *replaced* by the
   implementation, so the tests call `LRUClockMap.of(maxSize)` directly,
   anticipating a same-named class with that static factory. All factory calls
   go through one private helper `newMap(int)`; if the real factory ends up on
   a differently named class, exactly one line needs adjustment.
2. **Eviction victim is never asserted.** The Javadoc explicitly calls the LRU
   part "not really predictable". Tests assert only victim-independent
   properties:
   - `size()` never exceeds `maxSize` (C6/C9);
   - the map stays fully functional under continuous overflow;
   - the newly inserted key that triggered an eviction is retrievable
     afterwards (true for any sane clock variant: a fresh insert is never its
     own eviction victim);
   - untouched keys are evictable (eviction makes progress).
3. **Second-chance semantics via black-box probing.** The exact clock state
   (hand position, slot layout, new-entry flag initialization) is unknown and
   undocumented, so naive "touch key X, insert one, assert X survives" tests
   would be fragile across faithful clock variants. The suite instead uses a
   deterministic probe technique:
   - *Stabilization:* fill the map, touch every entry via `get()` (sets all
     flags under any new-entry flag policy), then insert one sacrificial key.
     The sweep finds all flags set, gives every entry its second chance
     (clearing all flags) and evicts exactly one entry. Afterwards every
     survivor is provably unprotected — independent of the flag-init policy.
   - *Victim discovery:* on a probe instance, insert one more key and observe
     which entry disappears — that is the clock's next victim for this exact,
     deterministic scenario.
   - *Verification:* on an identically constructed instance, read the known
     next victim with `get()` (must protect it → survives the repeated
     insert) or `getRaw()` (must not protect it → is evicted exactly as in
     the probe run; full key sets must match the probe exactly).
4. **Null handling (C12).** "No support for null keys or null values" is
   tested as *rejection*: `get/put/remove/getRaw` with a null key and `put`
   with a null value must throw (any `RuntimeException` is accepted, typically
   `NullPointerException`; `IllegalArgumentException` is also acceptable).
   Silently accepting nulls would contradict the documented contract. This is
   the one normative judgment call in the suite; it is flagged so it can be
   relaxed if the final implementation intentionally differs.
5. **`keys()` mutability (C10).** "Changing the array does not change the
   internal structure" is tested by attempting to mutate the returned list
   (clear/add, wrapped leniently — immutable lists are acceptable too) and
   verifying the map and later `keys()` calls are unaffected.
6. **`keys()` order** is internal array order — unspecified from the outside.
   Tests compare keys as sets, never by position.
7. **Thread safety (C13)** is explicitly out of scope; no concurrency tests.
8. **Load factor (C3)** is internal and not directly testable; covered only
   implicitly (capacity never exceeded, probing works under collisions).

## 4. Test Suite Structure

Single top-level test class with `@Nested` groups (easy to relocate, shares
the `newMap` factory helper), class-level `@Timeout(60)` as a safety net
against hangs (broken clock sweep or probing loop):

`src/test/java/org/jugsaxony/clock/demo2/LRUClockMapTest.java` — **52 tests**

| Nested class | Covers | Tests (highlights) |
|---|---|---|
| `FactoryTests` | C1, C2 | `of(4)` works; capacities 4..1000 accepted (parameterized); 3, 2, 1, 0, negatives, `Integer.MIN_VALUE` rejected with `IllegalArgumentException` (parameterized); new map is empty; instances are independent. |
| `PutGetTests` | C5, C6, C9 | put returns `null` for new keys; get/getRaw return values; size tracks inserts; re-put returns previous value, replaces value, does not grow; misses return `null`; generic type combinations (`Integer→String`, `String→record`, `record→record`); sealed-type values with pattern-matching round trip. |
| `RemoveTests` | C8, C9 | remove returns previous value; mapping gone from `get`/`getRaw`/`keys()`; size decrements; removing a missing key is a no-op; remove-all empties; other entries undisturbed; re-insert after remove behaves like new. |
| `KeysTests` | C10 | keys reflect contents as a set; no duplicates; reflect removals; empty map → empty list; mutating the returned list does not affect the map or later `keys()` calls. |
| `ClearTests` | C11 | clear empties completely; map fully reusable (3 refill-to-capacity rounds, no stale values); clear on empty map harmless. |
| `NullHandlingTests` | C12 | null key rejected by all four operations; null value rejected by `put`; rejected null calls do not corrupt the map. |
| `CollisionTests` | C14 | key type with constant `hashCode()` (single probe cluster): put/get/remove/size all correct; per-key update and removal inside the cluster; colliding and normal keys coexist. |
| `EvictionTests` | C6, C7 | size never exceeds `maxSize` under 20× insertion; triggering insert always retrievable; untouched keys are evictable; update/remove still work after evictions; minimum capacity 4 evicts correctly. |
| `SecondChanceTests` | C4, C5, C7 | probe-based tests from §3.3: stabilization sweep clears all flags and evicts exactly one entry; `get()` sets the flag and protects the known next victim; `getRaw()` does not protect (eviction identical to the untouched probe run). |
| `StressTests` | C6–C11 | seeded (deterministic) random mix of put/get/remove/clear/keys (20,000 steps) validated against a `HashMap` model — size bound after every step, full content scans every 500 steps (no ghosts, no stale values, no duplicates); sequential overflow of 5,000 inserts keeps all survivor values consistent. |

Java 21 features used: `record` (test key/value types), sealed interface with
pattern-matching `switch`, `List.getFirst()` (sequenced collections), `var`,
`@ParameterizedTest`/`@ValueSource`, `@Nested`, `@Timeout`,
`assertTimeoutPreemptively` for stress guards.

## 5. What Is Deliberately Not Tested

- Exact eviction victim / eviction order (unpredictable by design, C7) —
  except via the black-box probe technique, which *discovers* the victim at
  runtime instead of assuming it.
- Internal array order of `keys()` (implementation detail).
- The fixed load factor 0.5f (not observable through the interface).
- Concurrency (explicitly not thread-safe).
- Performance/timing (only generous timeout guards against infinite loops).
- Whether `keys()` returns a mutable or immutable list (unspecified).

## 6. Verification Performed (pre-implementation)

The suite was verified in a throwaway project under `/tmp/kilo/lru-verify/`
(outside the deliverable folder; not part of the deliverable):

1. A scratch reference implementation of `LRUClockMap` was written **solely
   from the interface contract**: open addressing (linear probing, tombstones,
   power-of-two capacity, load factor 0.5), clock hand with per-entry
   second-chance flags, static `of(int)` factory with the `< 4` check.
2. The suite was compiled with `javac --release 21` and run via the JUnit
   Platform Launcher (JUnit 6.0.3 / Jupiter).
3. The scratch implementation was run in **both** common clock variants —
   new-entry flag = `true` and new-entry flag = `false`:

   | Variant | Result |
   |---|---|
   | fresh flag = `true`  | **52/52 green** |
   | fresh flag = `false` | **52/52 green** |

4. Mutation testing (hand-seeded faults in the scratch implementation) to
   confirm the suite actually detects contract violations:

   | Mutation | Result | Killed by |
   |---|---|---|
   | `get()` does not set the second-chance flag | 1 failure | `SecondChanceTests.getSetsSecondChanceFlag` |
   | `getRaw()` sets the second-chance flag | 1 failure | `SecondChanceTests.getRawDoesNotSetSecondChanceFlag` |
   | no eviction (size may exceed maxSize) | 10 failures | `EvictionTests`, `SecondChanceTests`, `StressTests` |
   | factory accepts `maxSize < 4` | 7 failures | `FactoryTests.capacitiesBelowFourAreRejected` |

## 7. Wiring the Suite to the Real Implementation (later)

1. Replace the interface with the implementation (a class named
   `LRUClockMap` with `public static <K, V> LRUClockMap<K, V> of(int maxSize)`
   per the published guidance). The suite compiles unchanged.
2. If the factory lives on a differently named class instead, adjust the
   single line in `newMap(int)` of `LRUClockMapTest`.
3. Run `mvn -pl demo2 test` (or the module's standard test goal).

## 8. Risks / Open Points

- **Null rejection (§3.4)** is the only normative assumption beyond the
  literal Javadoc; if the implementer chooses silent acceptance, only
  `NullHandlingTests` needs adjustment.
- **Second-chance tests** assume a faithful clock/second-chance sweep as
  described by the class Javadoc and its linked reference. They were
  validated against both flag-initialization policies and use runtime victim
  discovery instead of assumed positions, but a drastically different
  "good enough LRU" (e.g. strict FIFO with no flags) could fail
  `SecondChanceTests` while arguably satisfying the loose Javadoc wording.
  That would be a deliberate design deviation to discuss, not a test bug.
- The suite compiles only once a type providing the static `of(int)` factory
  exists (see §3.1).
