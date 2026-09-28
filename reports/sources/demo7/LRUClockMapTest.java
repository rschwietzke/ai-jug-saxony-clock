package org.jugsaxony.clock.demo7;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Contract tests for {@link LRUClockMap}, written against the interface only.
 *
 * <p>The concrete implementation is discovered at runtime by
 * {@link TestMapFactory}. All tests rely exclusively on behavior guaranteed
 * by the interface documentation; the exact eviction order is explicitly
 * described as "not really predictable" and is therefore never asserted.
 *
 * <p>JUnit 5 only, using Java 21 features (records, var, sequenced
 * collections, switch pattern matching in the factory).
 */
@DisplayName("LRUClockMap contract")
class LRUClockMapTest
{
    /** A comfortably small but valid max size used by most tests. */
    private static final int MAX_SIZE = 8;

    /** Test key type to verify the map works with arbitrary objects. */
    record Point(int x, int y) { }

    /** Test value type. */
    record Data(String name) { }

    private static <K, V> LRUClockMap<K, V> newMap(final int maxSize)
    {
        return TestMapFactory.create(maxSize);
    }

    private static LRUClockMap<Integer, Integer> newMap()
    {
        return TestMapFactory.create(MAX_SIZE);
    }

    /**
     * Creates a map filled to exactly its max size with keys 0..maxSize-1
     * and values key * 10.
     */
    private static LRUClockMap<Integer, Integer> filledMap(final int maxSize)
    {
        final LRUClockMap<Integer, Integer> map = TestMapFactory.create(maxSize);
        for (var i = 0; i < maxSize; i++)
        {
            map.put(i, i * 10);
        }
        return map;
    }

    // ------------------------------------------------------------------
    // Factory / construction contract
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("factory")
    class FactoryTests
    {
        @ParameterizedTest(name = "maxSize {0} is rejected")
        @ValueSource(ints = { Integer.MIN_VALUE, -1, 0, 1, 2, 3 })
        @DisplayName("rejects maxSize below 4 with IllegalArgumentException")
        void rejectsTooSmallMaxSize(final int maxSize)
        {
            assertThrows(IllegalArgumentException.class, () -> newMap(maxSize));
        }

        @Test
        @DisplayName("accepts the minimum maxSize of 4")
        void acceptsMinimumMaxSize()
        {
            final var map = newMap(4);

            assertNotNull(map);
            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
        }

        @ParameterizedTest(name = "maxSize {0} yields a usable map")
        @ValueSource(ints = { 4, 5, 16, 100, 1000 })
        @DisplayName("creates usable maps for various max sizes")
        void createsUsableMaps(final int maxSize)
        {
            final var map = newMap(maxSize);

            assertNull(map.put("k", "v"));
            assertEquals("v", map.get("k"));
            assertEquals(1, map.size());
        }

        @Test
        @DisplayName("creates independent instances")
        void createsIndependentInstances()
        {
            final var map1 = newMap();
            final var map2 = newMap();

            map1.put(1, 1);

            assertEquals(1, map1.size());
            assertEquals(0, map2.size());
            assertNull(map2.get(1));
        }
    }

    // ------------------------------------------------------------------
    // put / get / getRaw
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("put and get")
    class PutGetTests
    {
        @Test
        @DisplayName("put of a new key returns null")
        void putNewKeyReturnsNull()
        {
            final var map = newMap();

            assertNull(map.put(1, 10));
        }

        @Test
        @DisplayName("put returns the previous value when replacing")
        void putReturnsPreviousValue()
        {
            final var map = newMap();

            assertNull(map.put(1, 10));
            assertEquals(10, map.put(1, 20));
            assertEquals(20, map.put(1, 30));
        }

        @Test
        @DisplayName("get returns the value stored via put")
        void getReturnsPutValue()
        {
            final var map = newMap();

            map.put(1, 10);
            map.put(2, 20);

            assertAll(
                () -> assertEquals(10, map.get(1)),
                () -> assertEquals(20, map.get(2)));
        }

        @Test
        @DisplayName("get returns null for unknown keys")
        void getUnknownKeyReturnsNull()
        {
            final var map = newMap();

            map.put(1, 10);

            assertNull(map.get(2));
            assertNull(map.get(Integer.MAX_VALUE));
        }

        @Test
        @DisplayName("getRaw returns the value stored via put")
        void getRawReturnsValue()
        {
            final var map = newMap();

            map.put(1, 10);

            assertEquals(10, map.getRaw(1));
        }

        @Test
        @DisplayName("getRaw returns null for unknown keys")
        void getRawUnknownKeyReturnsNull()
        {
            final var map = newMap();

            assertNull(map.getRaw(42));
        }

        @Test
        @DisplayName("get and getRaw agree on present keys")
        void getAndGetRawAgree()
        {
            final var map = newMap();
            for (var i = 0; i < MAX_SIZE; i++)
            {
                map.put(i, i * 100);
            }

            for (var i = 0; i < MAX_SIZE; i++)
            {
                assertEquals(map.getRaw(i), map.get(i));
            }
        }

        @Test
        @DisplayName("get is repeatable and does not alter the entry")
        void getIsRepeatable()
        {
            final var map = newMap();
            map.put(1, 10);

            for (var i = 0; i < 100; i++)
            {
                assertEquals(10, map.get(1));
            }
            assertEquals(1, map.size());
        }

        @Test
        @DisplayName("get of an unknown key does not change the map")
        void getUnknownKeyDoesNotChangeMap()
        {
            final var map = newMap();
            map.put(1, 10);

            assertNull(map.get(99));

            assertEquals(1, map.size());
            assertEquals(10, map.getRaw(1));
        }

        @Test
        @DisplayName("values are not copied or transformed")
        void valuesAreStoredByIdentity()
        {
            final LRUClockMap<Integer, Data> map = newMap(MAX_SIZE);
            final var value = new Data("identity");

            map.put(1, value);

            assertSame(value, map.get(1));
            assertSame(value, map.getRaw(1));
        }

        @Test
        @DisplayName("works with record keys using equals/hashCode")
        void worksWithRecordKeys()
        {
            final LRUClockMap<Point, Data> map = newMap(MAX_SIZE);

            map.put(new Point(1, 2), new Data("a"));

            // an equal but distinct key instance must find the entry
            var found = map.get(new Point(1, 2));
            assertNotNull(found);
            assertEquals("a", found.name());
            assertEquals(new Data("a"), map.getRaw(new Point(1, 2)));

            // a different key must not
            assertNull(map.get(new Point(2, 1)));
        }

        @Test
        @DisplayName("works with string keys and values")
        void worksWithStrings()
        {
            final LRUClockMap<String, String> map = newMap(MAX_SIZE);

            assertNull(map.put("one", "eins"));
            assertEquals("eins", map.get("one"));
            assertEquals("eins", map.put("one", "uno"));
            assertEquals("uno", map.get("one"));
        }

        @Test
        @DisplayName("replacing a value keeps the size unchanged")
        void replaceKeepsSize()
        {
            final var map = newMap();

            map.put(1, 10);
            map.put(1, 20);
            map.put(1, 30);

            assertEquals(1, map.size());
        }
    }

    // ------------------------------------------------------------------
    // Null handling
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("null handling")
    class NullTests
    {
        @Test
        @DisplayName("put with a null key is rejected")
        void putNullKeyRejected()
        {
            final var map = newMap();

            // the interface documents "no support for null keys or null values",
            // so any runtime rejection (typically NullPointerException) is expected
            assertThrows(RuntimeException.class, () -> map.put(null, 10));
        }

        @Test
        @DisplayName("put with a null value is rejected")
        void putNullValueRejected()
        {
            final var map = newMap();

            assertThrows(RuntimeException.class, () -> map.put(1, null));
        }
    }

    // ------------------------------------------------------------------
    // remove
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("remove")
    class RemoveTests
    {
        @Test
        @DisplayName("remove returns the previous value")
        void removeReturnsValue()
        {
            final var map = newMap();
            map.put(1, 10);

            assertEquals(10, map.remove(1));
        }

        @Test
        @DisplayName("remove of an unknown key returns null")
        void removeUnknownKeyReturnsNull()
        {
            final var map = newMap();
            map.put(1, 10);

            assertNull(map.remove(2));
            assertEquals(1, map.size());
        }

        @Test
        @DisplayName("removing twice returns null the second time")
        void removeTwiceReturnsNull()
        {
            final var map = newMap();
            map.put(1, 10);

            assertEquals(10, map.remove(1));
            assertNull(map.remove(1));
            assertEquals(0, map.size());
        }

        @Test
        @DisplayName("remove makes the entry invisible to get and getRaw")
        void removedEntryIsGone()
        {
            final var map = newMap();
            map.put(1, 10);
            map.put(2, 20);

            map.remove(1);

            assertAll(
                () -> assertNull(map.get(1)),
                () -> assertNull(map.getRaw(1)),
                () -> assertEquals(20, map.get(2)),
                () -> assertEquals(1, map.size()));
        }

        @Test
        @DisplayName("remove decreases the size only for present keys")
        void removeDecreasesSize()
        {
            final var map = newMap();
            for (var i = 0; i < 4; i++)
            {
                map.put(i, i);
            }
            assertEquals(4, map.size());

            map.remove(2);
            assertEquals(3, map.size());

            map.remove(99);
            assertEquals(3, map.size());
        }

        @Test
        @DisplayName("a removed key can be inserted again")
        void reinsertAfterRemove()
        {
            final var map = newMap();
            map.put(1, 10);
            map.remove(1);

            assertNull(map.put(1, 20));
            assertEquals(20, map.get(1));
            assertEquals(1, map.size());
        }

        @Test
        @DisplayName("removing all entries one by one empties the map")
        void removeAllEntries()
        {
            final var map = newMap();
            for (var i = 0; i < MAX_SIZE; i++)
            {
                map.put(i, i * 10);
            }

            for (var i = 0; i < MAX_SIZE; i++)
            {
                assertEquals(i * 10, map.remove(i), "removing key " + i);
            }

            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // size / keys / clear
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("size, keys and clear")
    class SizeKeysClearTests
    {
        @Test
        @DisplayName("a new map is empty")
        void newMapIsEmpty()
        {
            final var map = newMap();

            assertAll(
                () -> assertEquals(0, map.size()),
                () -> assertNotNull(map.keys()),
                () -> assertTrue(map.keys().isEmpty()));
        }

        @Test
        @DisplayName("size counts distinct keys")
        void sizeCountsDistinctKeys()
        {
            final var map = newMap();

            for (var i = 0; i < MAX_SIZE; i++)
            {
                map.put(i, i);
                assertEquals(i + 1, map.size());
            }
        }

        @Test
        @DisplayName("keys of a single-entry map contains that key")
        void keysOfSingleEntryMap()
        {
            final var map = newMap();
            map.put(7, 70);

            final var keys = map.keys();

            assertEquals(1, keys.size());
            // Java 21 SequencedCollection access
            assertEquals(7, keys.getFirst());
            assertEquals(7, keys.getLast());
        }

        @Test
        @DisplayName("keys contains exactly the live keys")
        void keysContainsAllLiveKeys()
        {
            final var map = newMap();
            final var expected = new HashSet<Integer>();
            for (var i = 0; i < MAX_SIZE; i++)
            {
                map.put(i, i * 10);
                expected.add(i);
            }

            final var keys = map.keys();

            assertEquals(expected.size(), keys.size());
            assertEquals(expected, new HashSet<>(keys));
            assertFalse(keys.contains(MAX_SIZE));
        }

        @Test
        @DisplayName("keys reflects removals")
        void keysReflectsRemovals()
        {
            final var map = newMap();
            map.put(1, 10);
            map.put(2, 20);
            map.put(3, 30);

            map.remove(2);

            final var keys = map.keys();
            assertEquals(2, keys.size());
            assertEquals(new HashSet<>(List.of(1, 3)), new HashSet<>(keys));
        }

        @Test
        @DisplayName("keys returns a stable order for an unmodified map")
        void keysOrderIsStable()
        {
            final var map = filledMap(MAX_SIZE);

            assertEquals(map.keys(), map.keys());
        }

        @Test
        @DisplayName("keys result is detached from the internal structure")
        void keysIsDetachedFromMap()
        {
            final var map = filledMap(MAX_SIZE);
            final var sizeBefore = map.size();

            final var keys = map.keys();
            try
            {
                keys.clear();
            }
            catch (final UnsupportedOperationException e)
            {
                // an unmodifiable view is acceptable as long as it is detached
            }

            assertAll(
                () -> assertEquals(sizeBefore, map.size()),
                () -> assertEquals(sizeBefore, map.keys().size()),
                () -> assertNotNull(map.getRaw(0), "entry for key 0 must still exist"));
        }

        @Test
        @DisplayName("keys size always matches size")
        void keysSizeMatchesSize()
        {
            final var map = newMap();

            assertEquals(map.size(), map.keys().size());

            map.put(1, 10);
            assertEquals(map.size(), map.keys().size());

            map.put(1, 20);
            assertEquals(map.size(), map.keys().size());

            map.remove(1);
            assertEquals(map.size(), map.keys().size());
        }

        @Test
        @DisplayName("clear empties the map")
        void clearEmptiesMap()
        {
            final var map = filledMap(MAX_SIZE);

            map.clear();

            assertAll(
                () -> assertEquals(0, map.size()),
                () -> assertTrue(map.keys().isEmpty()));
            for (var i = 0; i < MAX_SIZE; i++)
            {
                final var key = i;
                assertNull(map.get(key), "key " + key + " must be gone after clear");
                assertNull(map.getRaw(key), "key " + key + " must be gone after clear");
            }
        }

        @Test
        @DisplayName("clear on an empty map is a no-op")
        void clearOnEmptyMap()
        {
            final var map = newMap();

            map.clear();
            map.clear();

            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
        }

        @Test
        @DisplayName("the map is fully reusable after clear")
        void reuseAfterClear()
        {
            final var map = filledMap(MAX_SIZE);

            map.clear();

            for (var i = 100; i < 100 + MAX_SIZE; i++)
            {
                assertNull(map.put(i, i), "re-inserting key " + i);
            }

            assertAll(
                () -> assertEquals(MAX_SIZE, map.size()),
                () -> assertEquals(MAX_SIZE, map.keys().size()));
            for (var i = 100; i < 100 + MAX_SIZE; i++)
            {
                final var key = i;
                assertEquals(key, map.get(key), "key " + key + " must be present");
            }

            // old keys must not have resurrected
            for (var i = 0; i < MAX_SIZE; i++)
            {
                final var oldKey = i;
                assertNull(map.getRaw(oldKey), "old key " + oldKey + " must stay gone");
            }
        }
    }

    // ------------------------------------------------------------------
    // Capacity and eviction behavior
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("capacity and eviction")
    class EvictionTests
    {
        @ParameterizedTest(name = "maxSize {0}: no eviction below capacity")
        @ValueSource(ints = { 4, 8, 16, 64 })
        @DisplayName("all keys survive while at most maxSize entries are inserted")
        void noEvictionBelowCapacity(final int maxSize)
        {
            final var map = filledMap(maxSize);

            assertEquals(maxSize, map.size());
            for (var i = 0; i < maxSize; i++)
            {
                final var key = i;
                assertNotNull(map.getRaw(key), "key " + key + " must still be present");
            }
        }

        @ParameterizedTest(name = "maxSize {0}: size is capped")
        @ValueSource(ints = { 4, 8, 16, 64 })
        @DisplayName("size never exceeds maxSize, no matter how much is inserted")
        void sizeNeverExceedsMaxSize(final int maxSize)
        {
            final var map = newMap(maxSize);

            for (var i = 0; i < maxSize * 10; i++)
            {
                map.put(i, i);
                assertTrue(map.size() <= maxSize,
                        "size " + map.size() + " exceeds maxSize " + maxSize + " at insert " + i);
            }

            assertEquals(maxSize, map.size());
            assertEquals(maxSize, map.keys().size());
        }

        @ParameterizedTest(name = "maxSize {0}: newest key survives")
        @ValueSource(ints = { 4, 8, 16 })
        @DisplayName("the key just inserted is always present, eviction picks another key")
        void newestKeyAlwaysPresent(final int maxSize)
        {
            final var map = newMap(maxSize);

            for (var i = 0; i < maxSize * 10; i++)
            {
                map.put(i, i * 10);

                final var key = i;
                assertEquals(i * 10, map.getRaw(key),
                        "newest key " + key + " must never be the eviction victim");
            }
        }

        @ParameterizedTest(name = "maxSize {0}: overwrite when full does not evict")
        @ValueSource(ints = { 4, 8, 16 })
        @DisplayName("replacing a value of a present key never triggers eviction")
        void overwriteWhenFullDoesNotEvict(final int maxSize)
        {
            final var map = filledMap(maxSize);

            for (var round = 0; round < 3; round++)
            {
                for (var i = 0; i < maxSize; i++)
                {
                    final var expectedOld = round == 0 ? i * 10 : round * 1000 + i;
                    final var newValue = (round + 1) * 1000 + i;

                    assertEquals(expectedOld, map.put(i, newValue),
                            "overwrite of key " + i + " must return the old value");
                }

                assertEquals(maxSize, map.size());
                for (var i = 0; i < maxSize; i++)
                {
                    final var key = i;
                    assertEquals((round + 1) * 1000 + key, map.getRaw(key),
                            "key " + key + " must not be evicted by an overwrite");
                }
            }
        }

        @ParameterizedTest(name = "maxSize {0}: remove frees a slot")
        @ValueSource(ints = { 4, 8, 16 })
        @DisplayName("after remove, a new key fits without exceeding maxSize")
        void removeFreesCapacity(final int maxSize)
        {
            final var map = filledMap(maxSize);

            assertEquals(10, map.remove(1));
            map.put(1000, 99);

            assertAll(
                () -> assertEquals(maxSize, map.size()),
                () -> assertEquals(99, map.getRaw(1000)),
                () -> assertNull(map.getRaw(1)));
        }

        @ParameterizedTest(name = "maxSize {0}: second chance protects a hot key")
        @ValueSource(ints = { 4, 8, 16 })
        @DisplayName("a key touched via get before every eviction survives the churn")
        void secondChanceProtectsHotKey(final int maxSize)
        {
            final var map = filledMap(maxSize);
            final var hotKey = 0;

            // every new key triggers an eviction; the hot key re-arms its
            // second chance flag before each of them, so the clock algorithm
            // must never pick it as the victim
            for (var i = maxSize; i < maxSize * 4; i++)
            {
                assertNotNull(map.get(hotKey), "hot key must be present before insert " + i);
                map.put(i, i);
                assertEquals(maxSize, map.size());
            }

            assertNotNull(map.getRaw(hotKey),
                    "hot key must survive because get() sets its second chance flag");
        }

        @ParameterizedTest(name = "maxSize {0}: consistent under churn")
        @ValueSource(ints = { 4, 8, 16 })
        @DisplayName("size, keys and lookups stay consistent under heavy churn")
        void staysConsistentUnderChurn(final int maxSize)
        {
            final var map = newMap(maxSize);

            for (var i = 0; i < maxSize * 50; i++)
            {
                map.put(i, i);
                if (i % 3 == 0)
                {
                    map.get(i - 1);
                }
                if (i % 5 == 0)
                {
                    map.remove(i - 2);
                }

                assertTrue(map.size() <= maxSize);
                assertEquals(map.size(), map.keys().size(),
                        "keys().size() must match size() at insert " + i);
            }

            // every reported key must resolve to a value
            for (final var key : map.keys())
            {
                assertNotNull(map.getRaw(key), "reported key " + key + " must exist");
            }
        }

        @Test
        @DisplayName("interleaved put/remove/get keep the map coherent")
        void interleavedOperations()
        {
            final var map = newMap(MAX_SIZE);

            for (var round = 0; round < 20; round++)
            {
                for (var i = 0; i < MAX_SIZE; i++)
                {
                    map.put(i, round * 100 + i);
                }
                assertEquals(MAX_SIZE, map.size());

                for (var i = 0; i < MAX_SIZE; i += 2)
                {
                    assertEquals(round * 100 + i, map.remove(i));
                }
                assertEquals(MAX_SIZE / 2, map.size());

                for (var i = 1; i < MAX_SIZE; i += 2)
                {
                    assertEquals(round * 100 + i, map.get(i));
                }

                final var liveKeys = new HashSet<>(map.keys());
                for (var i = 1; i < MAX_SIZE; i += 2)
                {
                    assertTrue(liveKeys.contains(i));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Stress with many entries
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("larger maps")
    class LargerMapTests
    {
        @Test
        @DisplayName("handles many entries below capacity")
        void manyEntries()
        {
            final var maxSize = 1024;
            final var map = newMap(maxSize);
            final var count = 1000;

            for (var i = 0; i < count; i++)
            {
                assertNull(map.put(i, "v" + i));
            }

            assertAll(
                () -> assertEquals(count, map.size()),
                () -> assertEquals(count, map.keys().size()));

            for (var i = 0; i < count; i++)
            {
                final var key = i;
                assertEquals("v" + key, map.get(key), "value mismatch for key " + key);
            }
        }

        @Test
        @DisplayName("keys list is an independent snapshot for a large map")
        void keysSnapshotForLargeMap()
        {
            final var maxSize = 256;
            final LRUClockMap<Integer, Integer> map = newMap(maxSize);
            for (var i = 0; i < 200; i++)
            {
                map.put(i, i);
            }

            final List<Integer> snapshot = new ArrayList<>(map.keys());
            assertEquals(200, snapshot.size());
            assertEquals(200, map.size());

            // a second call yields the same content
            assertEquals(new HashSet<>(snapshot), new HashSet<>(map.keys()));
        }
    }
}
