package org.jugsaxony.clock.demo2;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for the {@link LRUClockMap} interface contract.
 *
 * <p>These tests are written against the interface Javadoc only. They never
 * assert <em>which</em> key is evicted (the LRU part is documented as "not
 * really predictable"), only victim-independent properties such as the size
 * bound, continued functionality under overflow, and the documented
 * second-chance flag semantics of {@link LRUClockMap#get(Object)} versus
 * {@link LRUClockMap#getRaw(Object)}.
 *
 * <p><b>Wiring to the implementation:</b> the interface publishes
 * {@code public static <K, V> LRUClockMap<K, V> of(int maxSize)} as
 * implementation guidance. Once the interface is replaced by the
 * implementation (a same-named class with that static factory), this suite
 * compiles unchanged. Should the factory end up on a differently named class,
 * the single private helper {@link #newMap(int)} is the only line to adjust.
 */
@DisplayName("LRUClockMap")
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD) // safety net: no test may hang (e.g. broken clock sweep or probing loop)
class LRUClockMapTest
{
    /**
     * The only place that knows how to create a map under test.
     * Adjust this one line if the real factory lives on a differently
     * named class than documented in the interface.
     */
    private static <K, V> LRUClockMap<K, V> newMap(final int maxSize)
    {
        return LRUClockMap.of(maxSize);
    }

    // ------------------------------------------------------------------
    // Shared test key/value types (Java 21: records, sealed types)
    // ------------------------------------------------------------------

    /** A key type that forces all instances into the same hash bucket. */
    record CollidingKey(String id)
    {
        @Override
        public int hashCode()
        {
            return 42; // constant on purpose: forces collisions in the open-addressing table
        }
    }

    /** Simple record value type for generic type coverage. */
    record Point(int x, int y)
    {
    }

    /** Sealed value hierarchy to exercise pattern matching on stored values. */
    sealed interface Shape permits Circle, Box
    {
    }

    record Circle(double radius) implements Shape
    {
    }

    record Box(double width, double height) implements Shape
    {
    }

    private static double area(final Shape shape)
    {
        return switch (shape)
        {
            case Circle(double r)        -> Math.PI * r * r;
            case Box(double w, double h) -> w * h;
        };
    }

    // ==================================================================
    // Factory: of(int maxSize)
    // ==================================================================

    @Nested
    @DisplayName("factory of(maxSize)")
    class FactoryTests
    {
        @Test
        @DisplayName("of(4) creates an empty map (minimum legal capacity)")
        void minimumCapacityIsAccepted()
        {
            final LRUClockMap<String, Integer> map = newMap(4);

            assertEquals(0, map.size(), "new map must be empty");
            assertTrue(map.keys().isEmpty(), "new map must have no keys");
            assertNull(map.get("missing"), "get on empty map must return null");
            assertNull(map.getRaw("missing"), "getRaw on empty map must return null");
        }

        @ParameterizedTest(name = "of({0}) is accepted")
        @ValueSource(ints = {4, 5, 6, 7, 8, 16, 100, 1_000})
        @DisplayName("capacities >= 4 are accepted")
        void capacitiesFromFourUpAreAccepted(final int maxSize)
        {
            assertDoesNotThrow(() -> newMap(maxSize));
        }

        @ParameterizedTest(name = "of({0}) throws IllegalArgumentException")
        @ValueSource(ints = {3, 2, 1, 0, -1, -100, Integer.MIN_VALUE})
        @DisplayName("capacities < 4 throw IllegalArgumentException")
        void capacitiesBelowFourAreRejected(final int maxSize)
        {
            assertThrows(IllegalArgumentException.class, () -> newMap(maxSize),
                "maxSize < 4 must be rejected per interface Javadoc");
        }

        @Test
        @DisplayName("two maps created from the same factory are independent")
        void instancesAreIndependent()
        {
            final LRUClockMap<String, Integer> a = newMap(8);
            final LRUClockMap<String, Integer> b = newMap(8);

            a.put("only-in-a", 1);

            assertEquals(1, a.size());
            assertEquals(0, b.size(), "maps must not share state");
            assertNull(b.getRaw("only-in-a"));
        }
    }

    // ==================================================================
    // put / get / getRaw / size basics
    // ==================================================================

    @Nested
    @DisplayName("put, get, getRaw, size")
    class PutGetTests
    {
        @Test
        @DisplayName("put returns null for new keys and stores the value")
        void putNewKeyReturnsNullAndStores()
        {
            final LRUClockMap<String, Integer> map = newMap(16);

            assertNull(map.put("a", 1), "put of a new key must return null");
            assertEquals(1, map.get("a"));
            assertEquals(1, map.getRaw("a"));
            assertEquals(1, map.size());
        }

        @Test
        @DisplayName("re-put returns the previous value and replaces it without growing")
        void rePutReplacesValue()
        {
            final LRUClockMap<String, Integer> map = newMap(16);

            assertNull(map.put("k", 1));
            assertEquals(1, map.put("k", 2), "re-put must return the previous value");
            assertEquals(2, map.put("k", 3));
            assertEquals(3, map.get("k"), "latest value must be stored");
            assertEquals(1, map.size(), "replacing a value must not change the size");
        }

        @Test
        @DisplayName("get and getRaw return null for missing keys")
        void missingKeysReturnNull()
        {
            final LRUClockMap<String, Integer> map = newMap(8);
            map.put("present", 7);

            assertNull(map.get("absent"));
            assertNull(map.getRaw("absent"));
            assertEquals(1, map.size(), "misses must not change the size");
        }

        @Test
        @DisplayName("size tracks distinct inserts up to capacity")
        void sizeTracksDistinctInserts()
        {
            final int capacity = 32;
            final LRUClockMap<Integer, String> map = newMap(capacity);

            for (var i = 0; i < capacity; i++)
            {
                map.put(i, "v" + i);
                assertEquals(i + 1, map.size());
            }

            for (var i = 0; i < capacity; i++)
            {
                assertEquals("v" + i, map.getRaw(i), "every inserted key must be retrievable");
            }
        }

        @Test
        @DisplayName("works with different generic type combinations")
        void genericTypeCombinations()
        {
            final LRUClockMap<Integer, String> intToString = newMap(8);
            intToString.put(1, "one");
            assertEquals("one", intToString.get(1));

            final LRUClockMap<String, Point> stringToRecord = newMap(8);
            stringToRecord.put("origin", new Point(0, 0));
            assertEquals(new Point(0, 0), stringToRecord.get("origin"));

            final LRUClockMap<Point, Point> recordToRecord = newMap(8);
            recordToRecord.put(new Point(1, 2), new Point(3, 4));
            assertEquals(new Point(3, 4), recordToRecord.get(new Point(1, 2)));
        }

        @Test
        @DisplayName("stored sealed-type values survive a round trip (pattern matching)")
        void sealedValuesRoundTrip()
        {
            final LRUClockMap<String, Shape> map = newMap(8);
            map.put("c", new Circle(1.0));
            map.put("b", new Box(2.0, 3.0));

            assertEquals(Math.PI, area(Objects.requireNonNull(map.get("c"))), 1e-9);
            assertEquals(6.0, area(Objects.requireNonNull(map.get("b"))), 1e-9);
        }
    }

    // ==================================================================
    // remove
    // ==================================================================

    @Nested
    @DisplayName("remove")
    class RemoveTests
    {
        @Test
        @DisplayName("remove returns the previous value and deletes the mapping")
        void removeReturnsPreviousAndDeletes()
        {
            final LRUClockMap<String, Integer> map = newMap(16);
            map.put("a", 1);

            assertEquals(1, map.remove("a"), "remove must return the previous value");
            assertNull(map.get("a"), "removed key must not be found by get");
            assertNull(map.getRaw("a"), "removed key must not be found by getRaw");
            assertFalse(map.keys().contains("a"), "removed key must not appear in keys()");
            assertEquals(0, map.size());
        }

        @Test
        @DisplayName("removing a missing key returns null and changes nothing")
        void removeMissingKeyIsANoOp()
        {
            final LRUClockMap<String, Integer> map = newMap(16);
            map.put("a", 1);

            assertNull(map.remove("missing"));
            assertEquals(1, map.size());
            assertEquals(1, map.getRaw("a"));
        }

        @Test
        @DisplayName("removing all entries empties the map")
        void removeAllEmptiesTheMap()
        {
            final LRUClockMap<Integer, Integer> map = newMap(16);
            for (var i = 0; i < 10; i++)
            {
                map.put(i, i * 10);
            }

            for (var i = 0; i < 10; i++)
            {
                assertEquals(i * 10, map.remove(i));
            }

            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
        }

        @Test
        @DisplayName("remove does not disturb the remaining entries")
        void removeDoesNotDisturbOthers()
        {
            final LRUClockMap<Integer, String> map = newMap(32);
            for (var i = 0; i < 20; i++)
            {
                map.put(i, "v" + i);
            }

            map.remove(7);
            map.remove(13);

            assertEquals(18, map.size());
            for (var i = 0; i < 20; i++)
            {
                if (i == 7 || i == 13)
                {
                    assertNull(map.getRaw(i), "removed key " + i + " must stay gone");
                }
                else
                {
                    assertEquals("v" + i, map.getRaw(i), "unrelated key " + i + " must be intact");
                }
            }
        }

        @Test
        @DisplayName("a removed key can be re-inserted cleanly")
        void removedKeyCanBeReinserted()
        {
            final LRUClockMap<String, Integer> map = newMap(8);
            map.put("k", 1);
            map.remove("k");

            assertNull(map.put("k", 2), "re-insert after remove must behave like a new key");
            assertEquals(2, map.getRaw("k"));
            assertEquals(1, map.size());
        }
    }

    // ==================================================================
    // keys()
    // ==================================================================

    @Nested
    @DisplayName("keys()")
    class KeysTests
    {
        @Test
        @DisplayName("keys() contains exactly the current keys (compared as a set)")
        void keysReflectContents()
        {
            final LRUClockMap<Integer, Integer> map = newMap(32);
            final Set<Integer> expected = new HashSet<>();
            for (var i = 0; i < 15; i++)
            {
                map.put(i, i);
                expected.add(i);
            }

            final List<Integer> keys = map.keys();
            assertEquals(expected.size(), keys.size(), "keys() must not contain duplicates or free slots");
            assertEquals(expected, new HashSet<>(keys),
                "keys() must contain all keys (order is internal and not asserted)");
        }

        @Test
        @DisplayName("keys() reflects removals")
        void keysReflectRemovals()
        {
            final LRUClockMap<String, Integer> map = newMap(16);
            map.put("a", 1);
            map.put("b", 2);
            map.put("c", 3);
            map.remove("b");

            assertEquals(Set.of("a", "c"), new HashSet<>(map.keys()));
        }

        @Test
        @DisplayName("keys() of an empty map is an empty list")
        void keysOfEmptyMap()
        {
            assertNotNull(newMap(8).keys());
            assertTrue(newMap(8).keys().isEmpty());
        }

        @Test
        @DisplayName("mutating the returned list does not change the map")
        void mutatingReturnedListDoesNotAffectMap()
        {
            final LRUClockMap<String, Integer> map = newMap(16);
            map.put("a", 1);
            map.put("b", 2);

            final List<String> keys = map.keys();

            // The Javadoc only guarantees that changing the returned list does
            // not change the map; whether the list supports mutation is left
            // open, so attempts are wrapped leniently.
            assertDoesNotThrow(() ->
            {
                try
                {
                    keys.clear();
                    keys.add("bogus");
                }
                catch (UnsupportedOperationException expectedForImmutableLists)
                {
                    // acceptable as well
                }
            });

            assertEquals(2, map.size(), "map size must be unaffected by list mutation");
            assertEquals(1, map.getRaw("a"));
            assertEquals(2, map.getRaw("b"));
            assertNull(map.getRaw("bogus"), "map must not contain keys added to the returned list");
        }

        @Test
        @DisplayName("a previously returned list does not leak into later calls")
        void returnedListsAreDecoupled()
        {
            final LRUClockMap<String, Integer> map = newMap(16);
            map.put("a", 1);
            map.put("b", 2);

            final List<String> first = map.keys();
            assertDoesNotThrow(() ->
            {
                try
                {
                    first.clear();
                    first.add("bogus");
                }
                catch (UnsupportedOperationException expectedForImmutableLists)
                {
                    // acceptable as well
                }
            });

            // a later call must still reflect the real map contents
            assertEquals(Set.of("a", "b"), new HashSet<>(map.keys()),
                "mutating a returned list must not leak into the map or later keys() calls");
        }
    }

    // ==================================================================
    // clear()
    // ==================================================================

    @Nested
    @DisplayName("clear()")
    class ClearTests
    {
        @Test
        @DisplayName("clear() empties the map completely")
        void clearEmptiesTheMap()
        {
            final LRUClockMap<Integer, Integer> map = newMap(16);
            for (var i = 0; i < 10; i++)
            {
                map.put(i, i);
            }

            map.clear();

            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
            for (var i = 0; i < 10; i++)
            {
                assertNull(map.getRaw(i), "cleared key " + i + " must be gone");
                assertNull(map.get(i), "cleared key " + i + " must be gone");
            }
        }

        @Test
        @DisplayName("map is fully reusable after clear(), including refill to capacity")
        void mapIsReusableAfterClear()
        {
            final int capacity = 16;
            final LRUClockMap<Integer, Integer> map = newMap(capacity);

            for (var round = 0; round < 3; round++)
            {
                for (var i = 0; i < capacity; i++)
                {
                    map.put(i, round * 1000 + i);
                }
                assertEquals(capacity, map.size(), "round " + round + ": refill to capacity must work");

                for (var i = 0; i < capacity; i++)
                {
                    assertEquals(round * 1000 + i, map.getRaw(i),
                        "round " + round + ": no stale values may survive");
                }
                map.clear();
                assertEquals(0, map.size());
            }
        }

        @Test
        @DisplayName("clear() on an empty map is harmless")
        void clearOnEmptyMapIsHarmless()
        {
            final LRUClockMap<String, Integer> map = newMap(8);
            assertDoesNotThrow(map::clear);
            assertDoesNotThrow(map::clear);
            assertEquals(0, map.size());
        }
    }

    // ==================================================================
    // Null handling — "No support for null keys or null values"
    // ==================================================================

    @Nested
    @DisplayName("null handling")
    class NullHandlingTests
    {
        @Test
        @DisplayName("null keys are rejected by get, getRaw, put and remove")
        void nullKeysAreRejected()
        {
            final LRUClockMap<String, Integer> map = newMap(8);

            assertThrows(RuntimeException.class, () -> map.get(null),
                "get(null) must be rejected (no null key support)");
            assertThrows(RuntimeException.class, () -> map.getRaw(null),
                "getRaw(null) must be rejected (no null key support)");
            assertThrows(RuntimeException.class, () -> map.put(null, 1),
                "put(null, v) must be rejected (no null key support)");
            assertThrows(RuntimeException.class, () -> map.remove(null),
                "remove(null) must be rejected (no null key support)");
        }

        @Test
        @DisplayName("null values are rejected by put")
        void nullValuesAreRejected()
        {
            final LRUClockMap<String, Integer> map = newMap(8);

            assertThrows(RuntimeException.class, () -> map.put("k", null),
                "put(k, null) must be rejected (no null value support)");
        }

        @Test
        @DisplayName("rejected null calls do not corrupt the map")
        void rejectedNullCallsDoNotCorruptTheMap()
        {
            final LRUClockMap<String, Integer> map = newMap(8);
            map.put("a", 1);

            assertThrows(RuntimeException.class, () -> map.put(null, 2));
            assertThrows(RuntimeException.class, () -> map.put("b", null));

            assertEquals(1, map.size(), "failed puts must not change the size");
            assertEquals(1, map.getRaw("a"), "existing entries must survive failed null calls");
            assertNull(map.getRaw("b"), "a failed put must not leave a mapping behind");
        }
    }

    // ==================================================================
    // Collisions in the open-addressing table
    // ==================================================================

    @Nested
    @DisplayName("hash collisions (open addressing)")
    class CollisionTests
    {
        @Test
        @DisplayName("keys that all share one hash bucket are stored and found correctly")
        void fullyCollidingKeysWork()
        {
            final LRUClockMap<CollidingKey, String> map = newMap(32);
            final var keys = new ArrayList<CollidingKey>();
            for (var i = 0; i < 12; i++)
            {
                keys.add(new CollidingKey("k" + i));
            }

            for (var i = 0; i < keys.size(); i++)
            {
                assertNull(map.put(keys.get(i), "v" + i));
            }
            assertEquals(keys.size(), map.size());

            for (var i = 0; i < keys.size(); i++)
            {
                assertEquals("v" + i, map.getRaw(keys.get(i)),
                    "colliding key " + i + " must resolve to its own value");
            }
        }

        @Test
        @DisplayName("update and remove inside a collision cluster affect only the targeted key")
        void updateAndRemoveInsideCollisionCluster()
        {
            final LRUClockMap<CollidingKey, Integer> map = newMap(32);
            final var a = new CollidingKey("a");
            final var b = new CollidingKey("b");
            final var c = new CollidingKey("c");
            map.put(a, 1);
            map.put(b, 2);
            map.put(c, 3);

            assertEquals(2, map.put(b, 22), "update inside the cluster must return the old value");
            assertEquals(1, map.remove(a), "remove inside the cluster must return the old value");

            assertNull(map.getRaw(a));
            assertEquals(22, map.getRaw(b), "updated entry must be intact");
            assertEquals(3, map.getRaw(c), "neighbor in the cluster must be intact");
            assertEquals(2, map.size());
        }

        @Test
        @DisplayName("colliding keys and normal keys coexist")
        void collidingAndNormalKeysCoexist()
        {
            final LRUClockMap<Object, String> map = newMap(32);
            map.put("normal-string", "s");
            map.put(12345, "i");
            map.put(new CollidingKey("x"), "cx");
            map.put(new CollidingKey("y"), "cy");

            assertEquals(4, map.size());
            assertEquals("s", map.getRaw("normal-string"));
            assertEquals("i", map.getRaw(12345));
            assertEquals("cx", map.getRaw(new CollidingKey("x")));
            assertEquals("cy", map.getRaw(new CollidingKey("y")));
        }
    }

    // ==================================================================
    // Eviction — victim-independent properties only
    // ==================================================================

    @Nested
    @DisplayName("eviction (size bound and stability)")
    class EvictionTests
    {
        @Test
        @DisplayName("size never exceeds maxSize under heavy insertion")
        void sizeNeverExceedsMaxSize()
        {
            final int capacity = 16;
            final LRUClockMap<Integer, Integer> map = newMap(capacity);

            for (var i = 0; i < capacity * 20; i++)
            {
                map.put(i, i);
                assertTrue(map.size() <= capacity,
                    "size " + map.size() + " must never exceed maxSize " + capacity);
            }
            assertEquals(capacity, map.size(), "a continuously overflowed map should stay full");
        }

        @Test
        @DisplayName("the key whose insert triggered an eviction is retrievable immediately after")
        void triggeringInsertIsRetrievable()
        {
            final int capacity = 8;
            final LRUClockMap<Integer, Integer> map = newMap(capacity);
            for (var i = 0; i < capacity; i++)
            {
                map.put(i, i);
            }

            // any sane clock variant never evicts the entry it just inserted
            for (var i = capacity; i < capacity * 5; i++)
            {
                map.put(i, i);
                assertEquals(i, map.getRaw(i), "freshly inserted key " + i + " must be present");
                assertTrue(map.size() <= capacity);
            }
        }

        @Test
        @DisplayName("untouched keys are evictable (eviction makes progress)")
        void untouchedKeysAreEvictable()
        {
            final int capacity = 8;
            final LRUClockMap<Integer, Integer> map = newMap(capacity);
            for (var i = 0; i < capacity; i++)
            {
                map.put(i, i);
            }

            // Overflow far beyond capacity without any get(): the original
            // keys must eventually be evicted, otherwise eviction is broken.
            for (var i = capacity; i < capacity * 10; i++)
            {
                map.put(i, i);
            }

            var survivors = 0;
            for (var i = 0; i < capacity; i++)
            {
                if (map.getRaw(i) != null)
                {
                    survivors++;
                }
            }
            assertTrue(survivors < capacity,
                "after overflowing 10x capacity, not all original keys may survive");
        }

        @Test
        @DisplayName("existing entries can still be updated and removed after evictions happened")
        void mapStaysFullyFunctionalAfterEvictions()
        {
            final int capacity = 8;
            final LRUClockMap<Integer, Integer> map = newMap(capacity);
            for (var i = 0; i < capacity * 3; i++)
            {
                map.put(i, i);
            }

            // pick any key currently present, then update and remove it
            final List<Integer> presentKeys = map.keys();
            assertFalse(presentKeys.isEmpty());
            final int key = presentKeys.getFirst(); // sequenced collection API (Java 21)

            final int oldValue = Objects.requireNonNull(map.getRaw(key));
            assertEquals(oldValue, map.put(key, -1), "update after evictions must return the old value");
            assertEquals(-1, map.getRaw(key));
            assertEquals(-1, map.remove(key), "remove after evictions must return the current value");
            assertNull(map.getRaw(key));
        }

        @Test
        @DisplayName("minimum capacity (4) evicts correctly")
        void minimumCapacityEvictsCorrectly()
        {
            final LRUClockMap<Integer, Integer> map = newMap(4);
            for (var i = 0; i < 100; i++)
            {
                map.put(i, i);
                assertTrue(map.size() <= 4, "size must stay <= 4");
                assertEquals(i, map.getRaw(i), "freshly inserted key must be present");
            }
        }
    }

    // ==================================================================
    // Second-chance (clock) flag semantics.
    //
    // The exact eviction victim is undocumented and depends on the clock
    // hand position, the slot layout and the new-entry flag policy. These
    // tests therefore use a probe technique:
    //
    //   1. Fill the map, touch every entry via get() (all flags set), then
    //      insert one sacrificial key. The sweep finds all flags set, gives
    //      every entry its second chance (clears all flags) and evicts
    //      exactly one entry. Afterwards every survivor is unprotected —
    //      independent of how new-entry flags are initialized.
    //   2. On a probe instance, insert one more key and observe which entry
    //      disappears: that is the clock's next victim for this exact,
    //      deterministic scenario.
    //   3. On an identically constructed test instance, read the known
    //      next victim with get() (must protect it) or getRaw() (must not
    //      protect it), repeat the insert, and compare.
    // ==================================================================

    @Nested
    @DisplayName("second-chance (clock) flag semantics")
    class SecondChanceTests
    {
        private static final int CAPACITY = 8;
        private static final int SACRIFICIAL = 10_000;
        private static final int PROBE = 20_000;

        /**
         * Creates a full map in which every second-chance flag is cleared:
         * fill to capacity, touch every entry with {@code get()} (sets all
         * flags under any new-entry flag policy), then insert one
         * sacrificial key. The resulting sweep passes every entry once
         * (clearing all flags) and evicts exactly one original entry.
         */
        private LRUClockMap<Integer, Integer> mapWithAllFlagsCleared()
        {
            final LRUClockMap<Integer, Integer> map = newMap(CAPACITY);
            for (var i = 0; i < CAPACITY; i++)
            {
                map.put(i, i);
            }
            for (var i = 0; i < CAPACITY; i++)
            {
                assertEquals(i, map.get(i), "precondition: freshly inserted key must be present");
            }
            map.put(SACRIFICIAL, SACRIFICIAL);
            return map;
        }

        /**
         * Returns the single key that is present in {@code before} but
         * missing in {@code after}.
         */
        private int singleEvictedKey(final List<Integer> before, final List<Integer> after)
        {
            final Set<Integer> missing = new HashSet<>(before);
            missing.removeAll(after);
            assertEquals(1, missing.size(), "exactly one entry must have been evicted");
            return missing.iterator().next();
        }

        @Test
        @DisplayName("a full-flag sweep clears all flags and evicts exactly one entry")
        void sacrificialInsertClearsAllFlagsAndEvictsExactlyOne()
        {
            final LRUClockMap<Integer, Integer> map = mapWithAllFlagsCleared();

            assertEquals(CAPACITY, map.size(), "size must stay at capacity after one eviction");
            assertEquals(SACRIFICIAL, map.getRaw(SACRIFICIAL), "the triggering insert must be present");

            var originalsLeft = 0;
            for (var i = 0; i < CAPACITY; i++)
            {
                if (map.getRaw(i) != null)
                {
                    originalsLeft++;
                }
            }
            assertEquals(CAPACITY - 1, originalsLeft,
                "exactly one original entry must have been evicted by the sweep");
        }

        @Test
        @DisplayName("get() sets the second-chance flag and protects the entry from the next eviction")
        void getSetsSecondChanceFlag()
        {
            // probe: discover the clock's next victim for this exact scenario
            final LRUClockMap<Integer, Integer> probe = mapWithAllFlagsCleared();
            final List<Integer> before = probe.keys();
            probe.put(PROBE, PROBE);
            final int nextVictim = singleEvictedKey(before, probe.keys());

            // verify: identical scenario, but the known victim is get()-touched first
            final LRUClockMap<Integer, Integer> map = mapWithAllFlagsCleared();
            assertEquals(nextVictim, map.get(nextVictim), "precondition: known victim must be present");
            map.put(PROBE, PROBE);

            assertEquals(nextVictim, map.getRaw(nextVictim),
                "get() must set the second-chance flag: the entry must survive the eviction that would otherwise hit it");
            assertEquals(PROBE, map.getRaw(PROBE), "the triggering insert must be present");
            assertEquals(CAPACITY, map.size());
        }

        @Test
        @DisplayName("getRaw() does not set the second-chance flag (no LRU effect)")
        void getRawDoesNotSetSecondChanceFlag()
        {
            // probe: discover the clock's next victim for this exact scenario
            final LRUClockMap<Integer, Integer> probe = mapWithAllFlagsCleared();
            final List<Integer> before = probe.keys();
            probe.put(PROBE, PROBE);
            final int nextVictim = singleEvictedKey(before, probe.keys());

            // verify: identical scenario, plus a getRaw() on the known victim
            final LRUClockMap<Integer, Integer> map = mapWithAllFlagsCleared();
            assertEquals(nextVictim, map.getRaw(nextVictim), "precondition: known victim must be present");
            map.put(PROBE, PROBE);

            assertNull(map.getRaw(nextVictim),
                "getRaw() must not protect the entry: it must be evicted exactly as if the read never happened");
            assertEquals(new HashSet<>(probe.keys()), new HashSet<>(map.keys()),
                "getRaw() must not change eviction behavior at all");
        }
    }

    // ==================================================================
    // Deterministic stress test against a HashMap model
    // ==================================================================

    @Nested
    @DisplayName("stress: mixed operations vs. HashMap model")
    class StressTests
    {
        @Test
        @DisplayName("random put/get/remove/clear/keys sequences stay consistent with a model")
        void mixedOperationsMatchModel()
        {
            assertTimeoutPreemptively(Duration.ofSeconds(30), () ->
            {
                final int capacity = 64;
                final long seed = 0x5EEDL;
                final Random rng = new Random(seed);
                final LRUClockMap<Integer, Integer> map = newMap(capacity);

                // Model of the latest committed state. Because eviction may
                // drop entries, the model tracks every key ever inserted with
                // its latest value; actual membership is read from the map.
                final Map<Integer, Integer> latestValues = new HashMap<>();
                final var keySpace = capacity * 3; // small key space forces updates and removals

                for (var step = 0; step < 20_000; step++)
                {
                    final int key = rng.nextInt(keySpace);
                    switch (rng.nextInt(100))
                    {
                        case 0, 1, 2, 3, 4 -> // 5% remove
                        {
                            final Integer expected = latestValues.remove(key);
                            final Integer removed = map.remove(key);
                            if (expected == null)
                            {
                                assertNull(removed, "step " + step + ": remove of unknown key must return null");
                            }
                            else if (removed != null)
                            {
                                // the map may already have evicted the key, but
                                // if it returns a value it must be the latest one
                                assertEquals(expected, removed,
                                    "step " + step + ": remove must return the latest value");
                            }
                        }
                        case 5 -> // 1% clear
                        {
                            map.clear();
                            latestValues.clear();
                            assertEquals(0, map.size(), "step " + step + ": size after clear");
                            assertTrue(map.keys().isEmpty(), "step " + step + ": keys after clear");
                        }
                        case 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 -> // 10% get (LRU-affecting)
                        {
                            map.get(key); // content correctness is verified by the scans below
                        }
                        default -> // ~79% put
                        {
                            final int value = step;
                            final Integer previous = map.put(key, value);
                            final Integer modelPrevious = latestValues.put(key, value);
                            if (modelPrevious == null)
                            {
                                assertNull(previous, "step " + step + ": put of new key must return null");
                            }
                            else if (previous != null)
                            {
                                // the map may already have evicted the key, but
                                // if it returns a value it must be the latest one
                                assertEquals(modelPrevious, previous,
                                    "step " + step + ": put must return the latest previous value");
                            }
                        }
                    }

                    assertTrue(map.size() <= capacity,
                        "step " + step + ": size must never exceed capacity");

                    // Full content scan every 500 steps: every key the map
                    // reports must carry the latest committed value, and the
                    // map must not report keys that were removed or cleared.
                    if (step % 500 == 0)
                    {
                        final List<Integer> actualKeys = map.keys();
                        assertEquals(map.size(), actualKeys.size(),
                            "step " + step + ": keys().size() must match size()");
                        assertEquals(actualKeys.size(), new HashSet<>(actualKeys).size(),
                            "step " + step + ": keys() must not contain duplicates");

                        for (final int k : actualKeys)
                        {
                            assertTrue(latestValues.containsKey(k),
                                "step " + step + ": map reports removed/cleared/never-inserted key " + k);
                            assertEquals(latestValues.get(k), map.getRaw(k),
                                "step " + step + ": stale value for key " + k);
                        }
                    }
                }

                // Final full verification of all remaining entries
                for (final int k : map.keys())
                {
                    assertEquals(latestValues.get(k), map.getRaw(k), "final value mismatch for key " + k);
                }
            }, "mixed-operation stress run must terminate (guard against clock/probing infinite loops)");
        }

        @Test
        @DisplayName("heavy sequential overflow keeps values consistent for every survivor")
        void sequentialOverflowKeepsValuesConsistent()
        {
            assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
            {
                final int capacity = 32;
                final LRUClockMap<Integer, Integer> map = newMap(capacity);

                for (var i = 0; i < 5_000; i++)
                {
                    map.put(i, i);
                }

                assertEquals(capacity, map.size());
                assertEquals(4_999, map.getRaw(4_999), "the last inserted key must be present");
                for (final int k : map.keys())
                {
                    assertEquals(k, map.getRaw(k), "value must equal the key for every survivor");
                }
            });
        }
    }
}
