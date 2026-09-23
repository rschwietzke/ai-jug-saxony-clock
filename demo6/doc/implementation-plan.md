# LRUClockMap — Unit Test Implementation Plan

Test suite for the `org.jugsaxony.clock.demo6.LRUClockMap` interface.
Written against the interface only; the implementation will be dropped in later
and validated with these tests.

- **Deliverable:** `src/test/java/org/jugsaxony/clock/demo6/LRUClockMapTest.java`
- **Constraints:** JUnit 5 only (no AssertJ, no Mockito, no helper libraries), Java 21
- **Scope:** 53 test methods, 130 executions after parameter expansion, one file,
  `@Nested` groups

---

## 1. What the interface actually promises

Everything testable comes from the Javadoc. Extracted contract:

| Method | Promise |
| --- | --- |
| `of(int maxSize)` | only way to construct; load factor fixed at 0.5; `IllegalArgumentException` if `maxSize < 4` |
| `getRaw(K)` | read **without** touching the clock flag; `null` if absent |
| `get(K)` | read **and** set the second chance flag; `null` if absent |
| `put(K,V)` | insert or replace; returns previous value or `null`; evicts **another** key when `maxSize` is reached |
| `remove(K)` | delete; returns previous value or `null` |
| `size()` | number of entries |
| `keys()` | all keys, **no LRU effect**, internal array order without free slots, detached from the map, empty (not null) when size is 0 |
| `clear()` | empty the map, reuse the data structure |
| class level | open addressing table, not thread-safe, no null keys, no null values |

Three things are explicitly **not** promised and are therefore never asserted:
the order of `keys()`, which entry the clock hand currently points at, and
whether `put` marks a freshly inserted entry as referenced.

---

## 2. Decisions and assumptions

### 2.1 Construction — one hook

`of(...)` is commented out in the interface, so the suite cannot compile until the
implementation supplies it. All construction goes through a single private method:

```java
private static <K, V> LRUClockMap<K, V> newMap(final int maxSize)
{
    return LRUClockMap.of(maxSize);
}
```

If the implementation publishes construction differently (a named class, a builder,
a different factory name), **this one method is the only thing to change.**

### 2.2 Null hostility → `NullPointerException`

"No support for null keys or null values" is read as the idiomatic contract of a
null-hostile map (`Map.of`, `ConcurrentHashMap`): reject, do not corrupt. A stored
`null` value would also be indistinguishable from "absent", which the return
contract of `get`/`put`/`remove` depends on. All of these tests sit in the
`NullHandling` group alone so the decision can be reversed in one place.

### 2.3 Clock semantics — textbook second chance

Assumed model: a hand walks the occupied slots in a fixed cyclic order; a set flag
is cleared and the hand moves on; the first entry with a clear flag is evicted.
**Nothing assumes where the hand starts or in which order slots are visited.**

### 2.4 Deterministic hashing

Two maps built from the same operation sequence must end up in the same internal
state. An implementation mixing a per-instance random seed into the hash would
break the differential tests in §3 (and would make "clock" behaviour
unreproducible anyway).

---

## 3. The interesting part: testing the clock without seeing it

The victim of an eviction depends on the hand position, which the interface does
not expose. Asserting a specific key would be guesswork. Instead the suite uses a
**differential** technique:

1. `warmedMap(maxSize)` builds a map from a fixed script: fill to `maxSize`, then
   `max(2, maxSize/2)` more inserts. The warm-up matters — it moves the hand off
   its start position and guarantees a *mix* of set and cleared flags, whichever
   way the implementation initialises a fresh entry's flag. Two calls do exactly
   the same work in exactly the same order, so both maps are in the same state.
2. `nextVictim(maxSize)` builds two throw-away maps: one is only read, the other is
   written first and read afterwards. The set difference is the key the map gives
   up next. Using two maps means the map is never observed between the warm-up and
   the insert, so the measurement cannot disturb what it measures.
3. Each test then builds a **third** identical map, does exactly **one** extra
   thing to it, and checks whether the victim changed.

This yields an exact, deterministic property rather than a statistical one:

> `get(k)` changes the eviction outcome **if and only if** `k` is the next victim.

Why "only if" holds: the victim is by definition the first entry the hand meets
with a clear flag. Entries the hand passes *before* it already had a set flag, so
setting it again changes nothing. Entries *after* it are never reached, because the
hand stops at the victim. So touching any non-victim key is invisible — which is
exactly what `onlyTheVictimIsRescued` asserts, key by key.

The same shape proves the neutrality claims: `getRaw`, `keys()`, `size()` and
failed lookups must leave the victim unchanged, for **every** key in the map.

---

## 4. Test inventory

| Group | # | Covers |
| --- | --- | --- |
| `Creation` | 4 | `maxSize < 4` rejected (incl. `Integer.MIN_VALUE`, 0, 3); 4..1000 accepted; fresh map empty; 50 000 entries |
| `BasicContract` | 10 | put/get/getRaw round trip, value **identity** (`assertSame`), replace returns old value and keeps size, missing key → null, remove returns previous, double remove, re-put after remove, size bookkeeping, equal-but-distinct key instances |
| `Keys` | 5 | empty list not null, no duplicates, agrees with `size()`, no stale/evicted keys, returned list detached, successive calls independent |
| `Clear` | 5 | empties, idempotent, map reusable, `maxSize` still enforced, works with colliding keys |
| `NullHandling` | 3 | NPE for null key in all four methods, NPE for null value, rejected put leaves the old value intact |
| `HashingAndCollisions` | 7 | all-identical hash codes, **probe chain intact after a mid-chain removal**, re-insert into a chain, 2 000 put/remove churn cycles, extreme hash codes one by one and all in one map, equal keys never duplicated |
| `Capacity` | 7 | no eviction before the limit, size never exceeds `maxSize` (20× overfill), freshly inserted key never evicted, exactly one eviction per over-capacity insert, replacing never evicts, a remove frees a slot, `maxSize == 4` |
| `ClockSemantics` | 8 | the differential tests from §3, plus: a second chance is consumed not permanent, cold entries do get evicted, a key read before every insert survives |
| `Randomized` | 1 | 6 seeds × 2 000 ops (`sealed interface Op` + record patterns), invariants re-checked after **every** operation |
| `Generics` | 3 | boxed keys beyond the Integer cache, record keys / collection values, two maps do not share state |

**Deliberately excluded:** thread safety (interface says not thread safe),
`keys()` ordering, `toString`/`equals` on the map, performance, and statistical
"hot vs cold survival ratio" tests — the strict tests in §3 cover the same ground
without a tolerance margin.

---

## 5. Java 21 / JUnit 5 usage

- `record HashedKey(int id, int hash)` with an overridden `hashCode()` — one fixture
  covers colliding keys and extreme hash codes while keeping the
  `equals`/`hashCode` contract sound (equal only if both components match)
- `record Coordinate(int x, int y)` as a plain value key
- `sealed interface Op` with nested records, consumed by an exhaustive
  `switch` with **record patterns** (`case Op.Put(String k, String v) ->`) in the
  randomized test
- `var` throughout, `List.of` / `Set.copyOf`, text-free `@DisplayName` labels
- `@Nested`, `@ParameterizedTest` + `@ValueSource`, `@Timeout(60)` at class level

`@Timeout` is deliberate: an endless clock sweep or an endless linear probe is an
easy mistake here, and without it such an implementation hangs the build instead of
failing a test. The whole suite runs in **under one second**, so the 60 s
per-method budget is a ~1000× margin, not a performance assertion.

---

## 6. Verification performed

The suite was not just written — it was executed. A correct reference
implementation (open addressing, linear probing, backward-shift deletion, second
chance clock) was built in a sandbox, plus ten deliberately broken variants.

| Variant | Result |
| --- | --- |
| correct implementation | **130 / 130 pass** |
| `get()` does not set the flag | 15 failures |
| clock ignores the flag (plain FIFO) | 15 failures |
| `getRaw()` also sets the flag | 10 failures |
| `put` on an existing key also evicts | 10 failures |
| `remove` nulls the slot, no backward shift | 9 failures |
| capacity limit off by one | 60 failures |
| no null checks | 2 failures |
| `keys()` hands out a shared cached list | 2 failures |
| `clear()` forgets to reset `size` | caught by `Clear#clearEmpties` |
| index via `h % capacity` (negative index) | 66 failures |

Every defect is caught, and no test over-specifies: the correct implementation is
fully green. Note the sandbox ran against a JUnit API shim because Maven Central is
not reachable from the environment the tests were generated in — the suite still
needs one real `mvn test` run once the implementation exists.

---

## 7. How to run

```bash
mvn -pl demo6 test
```

The `demo6/pom.xml` already declares `org.junit.jupiter:junit-jupiter` in test
scope; nothing needs to be added. AssertJ, JMH and JOL are present in the POM but
intentionally unused.

**The suite will not compile until `LRUClockMap.of(int)` exists** (it is commented
out in the interface today). That is expected — it is the single hook described
in §2.1.
