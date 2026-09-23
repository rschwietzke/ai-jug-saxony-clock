package org.jugsaxony.clock.demo5;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Black-box tests for {@link LRUClockMap}.
 *
 * <p>The suite only relies on the contract documented by the interface. The
 * single creation point is {@link #newMap(int)}, which is expected to call the
 * documented static factory {@code LRUClockMap.of(maxSize)} once the interface
 * is backed by a real implementation.</p>
 *
 * <p>The clock eviction policy is only "good enough LRU"; therefore tests never
 * hard-code which key is evicted. They assert the invariants that every correct
 * clock implementation must uphold. The one semantic guarantee that is tested
 * is the second chance: a key read via {@link LRUClockMap#get(Object)} must not
 * be evicted by the next single eviction while untouched entries exist.</p>
 */
@DisplayName("LRUClockMap")
class LRUClockMapTest {

    /** The only creation point of the tests, matches the documented factory. */
    private static <K, V> LRUClockMap<K, V> newMap(final int maxSize) {
        return LRUClockMap.of(maxSize);
    }

    // ------------------------------------------------------------------
    // 1. creation / factory
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("creation")
    class Creation {

        @ParameterizedTest(name = "of({0}) is rejected")
        @ValueSource(ints = {Integer.MIN_VALUE, -1000, -4, -1, 0, 1, 2, 3})
        @DisplayName("rejects a maxSize less than 4")
        void rejectsMaxSizeBelowFour(final int maxSize) {
            assertThrows(IllegalArgumentException.class, () -> LRUClockMap.of(maxSize));
        }

        @ParameterizedTest(name = "of({0}) is accepted")
        @ValueSource(ints = {4, 5, 8, 16, 1000})
        @DisplayName("accepts a maxSize of at least 4 and starts empty")
        void acceptsMaxSizeOfAtLeastFour(final int maxSize) {
            final var map = LRUClockMap.<String, Integer>of(maxSize);

            assertAll(
                    () -> assertEquals(0, map.size()),
                    () -> assertTrue(map.keys().isEmpty(), "a fresh map has no keys"));
        }

        @Test
        @DisplayName("rejects maxSize just below the documented minimum")
        void boundaryAtFour() {
            assertThrows(IllegalArgumentException.class, () -> newMap(3));
            assertDoesNotThrow(() -> newMap(4));
        }
    }

    // ------------------------------------------------------------------
    // 2. basic read / write
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("basic read / write")
    class BasicReadWrite {

        @Test
        @DisplayName("put of a new key returns null and grows the map")
        void putNewKeyReturnsNullAndGrows() {
            final LRUClockMap<String, Integer> map = newMap(8);

            assertEquals(null, map.put("a", 1));
            assertAll(
                    () -> assertEquals(1, map.size()),
                    () -> assertEquals(1, map.get("a")),
                    () -> assertEquals(1, map.getRaw("a")));
        }

        @Test
        @DisplayName("get returns the stored value")
        void getReturnsStoredValue() {
            final LRUClockMap<String, Integer> map = newMap(8);
            map.put("key", 42);

            assertEquals(42, map.get("key"));
        }

        @Test
        @DisplayName("getRaw returns the stored value")
        void getRawReturnsStoredValue() {
            final LRUClockMap<String, Integer> map = newMap(8);
            map.put("key", 42);

            assertEquals(42, map.getRaw("key"));
        }

        @Test
        @DisplayName("get of a missing key returns null and changes nothing")
        void getMissingKeyReturnsNull() {
            final LRUClockMap<String, Integer> map = newMap(8);
            map.put("present", 1);

            assertAll(
                    () -> assertNull(map.get("missing")),
                    () -> assertEquals(1, map.size()));
        }

        @Test
        @DisplayName("getRaw of a missing key returns null and changes nothing")
        void getRawMissingKeyReturnsNull() {
            final LRUClockMap<String, Integer> map = newMap(8);
            map.put("present", 1);

            assertAll(
                    () -> assertNull(map.getRaw("missing")),
                    () -> assertEquals(1, map.size()));
        }

        @Test
        @DisplayName("put of an existing key replaces the value and returns the old one")
        void putExistingKeyReplacesValue() {
            final LRUClockMap<String, Integer> map = newMap(8);
            map.put("a", 1);

            final Integer previous = map.put("a", 2);

            assertAll(
                    () -> assertEquals(1, previous),
                    () -> assertEquals(1, map.size(), "replacement must not grow the map"),
                    () -> assertEquals(2, map.getRaw("a")));
        }

        @Test
        @DisplayName("get and getRaw do not change size or the key set")
        void readsDoNotChangeStructure() {
            final LRUClockMap<String, Integer> map = newMap(8);
            map.put("a", 1);
            map.put("b", 2);
            final Set<String> before = Set.copyOf(map.keys());

            map.get("a");
            map.getRaw("b");

            assertAll(
                    () -> assertEquals(2, map.size()),
                    () -> assertEquals(before, new HashSet<>(map.keys())));
        }

        @Test
        @DisplayName("works with record keys (equals / hashCode contract)")
        void worksWithRecordKeys() {
            record Coord(int x, int y) {
            }

            final LRUClockMap<Coord, String> map = newMap(8);
            final Coord origin = new Coord(0, 0);
            final Coord other = new Coord(3, 4);

            map.put(origin, "origin");
            map.put(other, "other");

            assertAll(
                    () -> assertEquals("origin", map.getRaw(new Coord(0, 0))),
                    () -> assertEquals("other", map.getRaw(new Coord(3, 4))),
                    () -> assertNull(map.getRaw(new Coord(9, 9))),
                    () -> assertEquals(2, map.size()));
        }

        @Test
        @DisplayName("handles keys with identical hash codes (open addressing)")
        void handlesHashCollisions() {
            // "Aa" and "BB" are the classic Strings with the same hashCode.
            assertEquals("Aa".hashCode(), "BB".hashCode(), "precondition for the test");

            final LRUClockMap<String, Integer> map = newMap(16);
            map.put("Aa", 1);
            map.put("BB", 2);

            assertAll(
                    () -> assertEquals(1, map.getRaw("Aa")),
                    () -> assertEquals(2, map.getRaw("BB")),
                    () -> assertEquals(2, map.size()));

            assertEquals(1, map.remove("Aa"));

            assertAll(
                    () -> assertNull(map.getRaw("Aa")),
                    () -> assertEquals(2, map.getRaw("BB"), "the colliding entry must survive a removal"),
                    () -> assertEquals(1, map.size()));

            map.put("Aa", 9);

            assertAll(
                    () -> assertEquals(9, map.getRaw("Aa"), "re-inserted colliding key must be found"),
                    () -> assertEquals(2, map.getRaw("BB")),
                    () -> assertEquals(2, map.size()));
        }
    }

    // ------------------------------------------------------------------
    // 3. removal
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("removal")
    class Removal {

        @Test
        @DisplayName("remove returns the previous value and shrinks the map")
        void removeReturnsPreviousValue() {
            final LRUClockMap<String, Integer> map = newMap(8);
            map.put("a", 1);
            map.put("b", 2);

            final Integer removed = map.remove("a");

            assertAll(
                    () -> assertEquals(1, removed),
                    () -> assertEquals(1, map.size()),
                    () -> assertNull(map.getRaw("a")));
        }

        @Test
        @DisplayName("remove of a missing key returns null and changes nothing")
        void removeMissingKeyReturnsNull() {
            final LRUClockMap<String, Integer> map = newMap(8);
            map.put("a", 1);

            assertAll(
                    () -> assertNull(map.remove("missing")),
                    () -> assertEquals(1, map.size()));
        }

        @Test
        @DisplayName("a removed key cannot be read and removing it twice returns null")
        void removeIsIdempotent() {
            final LRUClockMap<String, Integer> map = newMap(8);
            map.put("a", 1);
            map.remove("a");

            assertAll(
                    () -> assertNull(map.get("a")),
                    () -> assertNull(map.getRaw("a")),
                    () -> assertNull(map.remove("a")),
                    () -> assertEquals(0, map.size()));
        }
    }

    // ------------------------------------------------------------------
    // 4. key listing
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("key listing")
    class KeyListing {

        @Test
        @DisplayName("keys is empty for a new map")
        void keysEmptyForNewMap() {
            final LRUClockMap<String, Integer> map = newMap(8);

            final List<String> keys = map.keys();

            assertAll(
                    () -> assertNotNull(keys),
                    () -> assertTrue(keys.isEmpty()));
        }

        @Test
        @DisplayName("keys contains exactly the live keys without duplicates")
        void keysContainsLiveKeys() {
            final LRUClockMap<String, Integer> map = newMap(8);
            final Set<String> inserted = Set.of("a", "b", "c", "d");
            inserted.forEach(k -> map.put(k, k.length()));

            final List<String> keys = map.keys();

            assertAll(
                    () -> assertEquals(map.size(), keys.size()),
                    () -> assertEquals(inserted, new HashSet<>(keys)),
                    () -> assertFalse(keys.contains(null)),
                    () -> assertEquals(keys.size(), new HashSet<>(keys).size(), "no duplicates"));
        }

        @Test
        @DisplayName("keys order is stable while the map is unchanged")
        void keysOrderIsStable() {
            final LRUClockMap<String, Integer> map = newMap(8);
            IntStream.range(0, 6).forEach(i -> map.put("k" + i, i));

            assertEquals(map.keys(), map.keys());
        }

        @Test
        @DisplayName("keys returns an independent snapshot, not a live view")
        void keysReturnsSnapshot() {
            final LRUClockMap<String, Integer> map = newMap(8);
            map.put("a", 1);
            map.put("b", 2);

            final List<String> snapshot = map.keys();
            final int sizeBefore = map.size();

            map.put("later", 3);

            assertAll(
                    () -> assertEquals(sizeBefore, snapshot.size(), "an old snapshot must not grow"),
                    () -> assertFalse(snapshot.contains("later"), "an old snapshot must not see new keys"));
        }

        @Test
        @DisplayName("modifying the returned list never changes the map")
        void modifyingKeyListDoesNotChangeMap() {
            final LRUClockMap<String, Integer> map = newMap(8);
            map.put("a", 1);
            map.put("b", 2);

            final List<String> keys = map.keys();
            final int sizeBefore = map.size();

            // Either the list is a mutable copy (then it must be independent)
            // or an unmodifiable snapshot; in both cases the map stays intact.
            try {
                keys.clear();
            } catch (UnsupportedOperationException acceptable) {
                // An unmodifiable snapshot also guarantees the map cannot change.
            }

            assertAll(
                    () -> assertEquals(sizeBefore, map.size()),
                    () -> assertNotNull(map.getRaw("a")),
                    () -> assertNotNull(map.getRaw("b")),
                    () -> assertEquals(Set.of("a", "b"), new HashSet<>(map.keys())));
        }
    }

    // ------------------------------------------------------------------
    // 5. clearing
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("clearing")
    class Clearing {

        @Test
        @DisplayName("clear removes all mappings")
        void clearRemovesAllMappings() {
            final LRUClockMap<String, Integer> map = newMap(8);
            map.put("a", 1);
            map.put("b", 2);

            map.clear();

            assertAll(
                    () -> assertEquals(0, map.size()),
                    () -> assertTrue(map.keys().isEmpty()),
                    () -> assertNull(map.get("a")),
                    () -> assertNull(map.getRaw("b")));
        }

        @Test
        @DisplayName("the map stays usable after clear")
        void mapIsReusableAfterClear() {
            final LRUClockMap<String, Integer> map = newMap(8);
            map.put("a", 1);
            map.clear();

            map.put("b", 2);

            assertAll(
                    () -> assertEquals(1, map.size()),
                    () -> assertEquals(2, map.getRaw("b")),
                    () -> assertNull(map.getRaw("a")));
        }

        @Test
        @DisplayName("clear on an empty map is a no-op")
        void clearOnEmptyMap() {
            final LRUClockMap<String, Integer> map = newMap(8);

            map.clear();

            assertEquals(0, map.size());
        }
    }

    // ------------------------------------------------------------------
    // 6. eviction
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("eviction")
    class Eviction {

        private static LRUClockMap<String, Integer> filledMap(final int maxSize) {
            final LRUClockMap<String, Integer> map = newMap(maxSize);
            IntStream.range(0, maxSize).forEach(i -> map.put("k" + i, i));
            return map;
        }

        @Test
        @DisplayName("filling to maxSize is possible")
        void canFillToMaxSize() {
            final int maxSize = 8;
            final LRUClockMap<String, Integer> map = filledMap(maxSize);

            assertEquals(maxSize, map.size());
        }

        @Test
        @DisplayName("a new key at maxSize keeps the size and evicts exactly one key")
        void evictsExactlyOneWhenFull() {
            final int maxSize = 8;
            final LRUClockMap<String, Integer> map = filledMap(maxSize);
            final Set<String> before = new HashSet<>(map.keys());

            map.put("newcomer", 99);

            final Set<String> after = new HashSet<>(map.keys());
            final Set<String> vanished = new HashSet<>(before);
            vanished.removeAll(after);

            assertAll(
                    () -> assertEquals(maxSize, map.size(), "size must stay bounded"),
                    () -> assertEquals(99, map.getRaw("newcomer"), "the new key must be present"),
                    () -> assertEquals(1, vanished.size(), "exactly one victim is expected, was " + vanished),
                    () -> assertTrue(after.contains("newcomer")),
                    () -> assertEquals(maxSize, after.size()));
        }

        @Test
        @DisplayName("updating an existing key while full evicts nothing")
        void updateWhileFullDoesNotEvict() {
            final int maxSize = 8;
            final LRUClockMap<String, Integer> map = filledMap(maxSize);
            final Set<String> before = new HashSet<>(map.keys());

            final Integer previous = map.put("k3", 333);

            assertAll(
                    () -> assertEquals(3, previous),
                    () -> assertEquals(333, map.getRaw("k3")),
                    () -> assertEquals(maxSize, map.size()),
                    () -> assertEquals(before, new HashSet<>(map.keys())));
        }

        @Test
        @DisplayName("inserting after a remove does not evict")
        void insertBelowMaxSizeDoesNotEvict() {
            final int maxSize = 8;
            final LRUClockMap<String, Integer> map = filledMap(maxSize);
            map.remove("k0");

            map.put("replacement", 1);

            final Set<String> expected = new HashSet<>(map.keys());
            assertAll(
                    () -> assertEquals(maxSize, map.size()),
                    () -> assertEquals(1, map.getRaw("replacement")),
                    () -> assertNull(map.getRaw("k0")),
                    () -> assertTrue(expected.containsAll(Set.of(
                            "k1", "k2", "k3", "k4", "k5", "k6", "k7", "replacement"))));
        }

        @Test
        @DisplayName("repeated insertions stay bounded and every new key is retrievable")
        void manyInsertionsStayBounded() {
            final int maxSize = 8;
            final LRUClockMap<String, Integer> map = newMap(maxSize);

            for (int i = 0; i < 200; i++) {
                final String key = "k" + i;
                final int value = i;
                final int expectedSize = Math.min(i + 1, maxSize);
                map.put(key, value);

                assertAll(
                        () -> assertTrue(map.size() <= maxSize, "size must never exceed maxSize"),
                        () -> assertEquals(expectedSize, map.size()),
                        () -> assertEquals(value, map.getRaw(key), "the freshly inserted key must be present"),
                        () -> assertEquals(map.size(), map.keys().size()));
            }
        }
    }

    // ------------------------------------------------------------------
    // 7. second chance / clock semantics
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("second chance")
    class SecondChance {

        @Test
        @DisplayName("a key read via get survives the next single eviction")
        void readKeyGetsASecondChance() {
            final int maxSize = 4;
            final LRUClockMap<String, Integer> map = newMap(maxSize);
            final List<String> untouched = List.of("B", "C", "D");
            map.put("A", 1);
            untouched.forEach(k -> map.put(k, 2));
            assertEquals(maxSize, map.size(), "precondition: map is full");

            map.get("A");

            map.put("E", 5);

            final long evictedAmongUntouched =
                    untouched.stream().filter(k -> map.getRaw(k) == null).count();

            assertAll(
                    () -> assertNotNull(map.getRaw("A"), "the recently read key must get a second chance"),
                    () -> assertEquals(5, map.getRaw("E")),
                    () -> assertEquals(maxSize, map.size()),
                    () -> assertEquals(1, evictedAmongUntouched,
                            "exactly one untouched key should have been evicted"));
        }

        @Test
        @DisplayName("repeated reads keep a key present across several evictions")
        void repeatedReadsProtectTheKey() {
            final int maxSize = 4;
            final LRUClockMap<String, Integer> map = newMap(maxSize);
            map.put("hot", 1);
            map.put("B", 2);
            map.put("C", 3);
            map.put("D", 4);

            for (int i = 0; i < 3; i++) {
                map.get("hot");
                map.put("new" + i, i);
            }

            assertNotNull(map.getRaw("hot"), "a hot key should survive short bursts of eviction");
            assertEquals(maxSize, map.size());
        }
    }

    // ------------------------------------------------------------------
    // 8. null arguments
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("null arguments")
    class NullArguments {

        @Test
        @DisplayName("null keys are rejected")
        void nullKeysAreRejected() {
            final LRUClockMap<String, Integer> map = newMap(8);

            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> map.put(null, 1)),
                    () -> assertThrows(NullPointerException.class, () -> map.get(null)),
                    () -> assertThrows(NullPointerException.class, () -> map.getRaw(null)),
                    () -> assertThrows(NullPointerException.class, () -> map.remove(null)));
        }

        @Test
        @DisplayName("null values are rejected")
        void nullValuesAreRejected() {
            final LRUClockMap<String, Integer> map = newMap(8);

            assertThrows(NullPointerException.class, () -> map.put("a", null));
        }
    }

    // ------------------------------------------------------------------
    // 9. randomized invariants
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("randomized invariants")
    class RandomizedInvariants {

        @Test
        @DisplayName("mixed put/get/remove keeps size, keys and values consistent")
        void mixedOperationsPreserveInvariants() {
            final int maxSize = 8;
            final LRUClockMap<Integer, Integer> map = newMap(maxSize);
            final Random random = new Random(42);
            final Set<Integer> live = new HashSet<>();
            int valueCounter = 0;

            for (int i = 0; i < 5_000; i++) {
                switch (random.nextInt(4)) {
                    case 0, 1 -> {
                        final int key = random.nextInt(50);
                        final int value = ++valueCounter;
                        final boolean wasLive = live.contains(key);

                        map.put(key, value);

                        if (!wasLive && live.size() == maxSize) {
                            live.removeIf(k -> map.getRaw(k) == null);
                            assertEquals(maxSize - 1, live.size(),
                                    "a new key inserted while full must evict exactly one key");
                        }
                        live.add(key);
                        assertEquals(value, map.getRaw(key));
                    }
                    case 2 -> {
                        final int key = random.nextInt(50);
                        final Integer expected = live.contains(key) ? map.getRaw(key) : null;
                        assertEquals(expected, map.get(key));
                    }
                    default -> {
                        final int key = random.nextInt(50);
                        final boolean wasLive = live.remove(key);
                        final Integer removed = map.remove(key);
                        if (wasLive) {
                            assertNotNull(removed, "removing a live key must return its value");
                        } else {
                            assertNull(removed, "removing an absent key must return null");
                        }
                    }
                }

                assertInvariants(map, maxSize, live);
            }

            map.clear();

            assertAll(
                    () -> assertEquals(0, map.size()),
                    () -> assertTrue(map.keys().isEmpty()));
        }

        private static void assertInvariants(final LRUClockMap<Integer, Integer> map,
                                             final int maxSize,
                                             final Set<Integer> live) {
            final List<Integer> keys = map.keys();
            final Set<Integer> keySet = new HashSet<>(keys);

            assertTrue(map.size() <= maxSize, "size must stay bounded");
            assertEquals(map.size(), keys.size(), "size() must match keys().size()");
            assertEquals(live, keySet, "keys() must match the tracked live keys");
            assertFalse(keys.contains(null), "keys() must not contain null");
            assertEquals(keys.size(), keySet.size(), "keys() must not contain duplicates");
            keys.forEach(k -> assertNotNull(map.getRaw(k), "listed keys must be readable"));
        }
    }
}
