package org.jugsaxony.clock.demo6;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Contract tests for {@link LRUClockMap}.
 *
 * <p>Everything here is written against the interface only. The tests never look
 * at the internal array, the clock hand or the second chance flags, because none
 * of that is visible. All expectations are derived from the six published methods
 * and from the behaviour the Javadoc promises.
 *
 * <h2>Assumptions</h2>
 * <ol>
 *   <li><b>Construction.</b> The Javadoc names {@code LRUClockMap.of(int)} as
 *       "the only way to create a new LRUClockMap". Every test goes through
 *       {@link #newMap(int)}, so if the implementation publishes construction in
 *       another way, that one method is the only thing that has to be adapted.</li>
 *   <li><b>Null hostility.</b> "No support for null keys or null values" is read
 *       as the idiomatic contract of a null hostile map (see {@code Map.of} or
 *       {@code ConcurrentHashMap}): a {@link NullPointerException}, not silent
 *       corruption. Those tests sit in {@link NullHandling} alone so they are
 *       easy to relax.</li>
 *   <li><b>Clock semantics.</b> The eviction tests assume a textbook second
 *       chance clock: a hand walks the occupied slots in a fixed order, clears a
 *       set flag and moves on, and evicts at the first entry whose flag is
 *       clear. Nothing assumes where the hand starts or in which order the slots
 *       are visited. The tests build the same map twice from an identical
 *       operation script, do one single extra thing to the second copy, and only
 *       compare the difference.</li>
 *   <li><b>Deterministic hashing.</b> Two maps built from the same operations
 *       end up in the same internal state, so the implementation must not mix a
 *       per instance random seed into the hash.</li>
 * </ol>
 *
 * <p>JUnit 5 only, no other test or assertion library. Java 21 language level.
 *
 * <p>The whole suite runs in well under a second against a working
 * implementation. The generous class level timeout is only there so that an
 * implementation which loops forever - an easy mistake in the clock sweep and
 * in a linear probe - reports a failure instead of hanging the build.
 *
 * @author generated contract test suite
 */
@DisplayName("LRUClockMap")
@Timeout(60)
class LRUClockMapTest
{
    /** The extra key that is pushed into a full map to trigger an eviction. */
    private static final String INTRUDER = "intruder";

    // ------------------------------------------------------------------
    // construction hook - the single place that knows how a map is built
    // ------------------------------------------------------------------

    /**
     * The one and only place in this suite where a map is created. Adapt this
     * method if the implementation publishes construction in another way.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param maxSize the maximum number of entries the map will hold
     * @return a fresh, empty map
     */
    private static <K, V> LRUClockMapInterface<K, V> newMap(final int maxSize)
    {
        return LRUClockMapInterface.of(maxSize);
    }

    // ------------------------------------------------------------------
    // small shared helpers
    // ------------------------------------------------------------------

    /**
     * @param i an index
     * @return a stable, collision free key for that index
     */
    private static String key(final int i)
    {
        return "key-" + i;
    }

    /**
     * @param i an index
     * @return the value belonging to {@link #key(int)}
     */
    private static String value(final int i)
    {
        return "value-" + i;
    }

    /**
     * Puts {@code key(from) .. key(from + count - 1)} into the map.
     *
     * @param map the map to fill
     * @param from the first index, inclusive
     * @param count how many entries to add
     */
    private static void fill(final LRUClockMap<String, String> map, final int from, final int count)
    {
        for (int i = from; i < from + count; i++)
        {
            map.put(key(i), value(i));
        }
    }

    /**
     * @param map the map to inspect
     * @return the keys of the map as a set, for order independent comparison
     */
    private static Set<String> keySet(final LRUClockMap<String, String> map)
    {
        return new LinkedHashSet<>(map.keys());
    }

    // ------------------------------------------------------------------
    // deterministic map construction for the clock tests
    // ------------------------------------------------------------------

    /**
     * How many over capacity inserts {@link #warmedMap(int)} performs after the
     * initial fill. Deliberately smaller than {@code maxSize} so that the map
     * afterwards holds a mix of old and freshly inserted entries, and therefore
     * a mix of set and cleared second chance flags - no matter whether the
     * implementation marks a freshly inserted entry as referenced or not.
     *
     * @param maxSize the maximum size of the map
     * @return the number of warm up inserts
     */
    private static int warmUpCount(final int maxSize)
    {
        return Math.max(2, maxSize / 2);
    }

    /**
     * Builds a map in a completely reproducible way: fill it to {@code maxSize},
     * then perform a few more inserts so the clock hand has left its initial
     * position. Two calls with the same {@code maxSize} execute exactly the same
     * operations in exactly the same order and must therefore end up in exactly
     * the same internal state.
     *
     * <p>This is the basis of all differential tests below: build the same map
     * twice, do one single extra thing to the second copy, and see whether the
     * next eviction changes.
     *
     * @param maxSize the maximum size of the map
     * @return a filled and warmed up map
     */
    private static LRUClockMapInterface<String, String> warmedMap(final int maxSize)
    {
        final var map = LRUClockMapTest.<String, String>newMap(maxSize);

        fill(map, 0, maxSize);
        fill(map, maxSize, warmUpCount(maxSize));

        return map;
    }

    /**
     * Determines which key a {@link #warmedMap(int)} gives up when
     * {@link #INTRUDER} is inserted next.
     *
     * <p>Two throw away maps are used so that the map is never observed between
     * the warm up and the insert. Otherwise the measurement itself could
     * influence the very result it tries to measure.
     *
     * @param maxSize the maximum size of the map
     * @return the key that gets evicted
     */
    private static String nextVictim(final int maxSize)
    {
        // first map: only ever read, never written after the warm up
        final var before = keySet(warmedMap(maxSize));

        // second map: written first, read afterwards
        final var probe = warmedMap(maxSize);
        probe.put(INTRUDER, "x");
        final var after = keySet(probe);

        assertEquals(maxSize, before.size(), "a warmed map must hold exactly maxSize entries");
        assertTrue(after.contains(INTRUDER), "the newly inserted key must never be the one evicted");

        final var evicted = new LinkedHashSet<>(before);
        evicted.removeAll(after);

        assertEquals(1, evicted.size(),
                     () -> "an over capacity insert must evict exactly one entry, but these went away: " + evicted);

        return evicted.iterator().next();
    }

    // ==================================================================
    // creation
    // ==================================================================

    @Nested
    @DisplayName("creation")
    class Creation
    {
        @ParameterizedTest(name = "of({0}) is rejected")
        @ValueSource(ints = { Integer.MIN_VALUE, -100, -1, 0, 1, 2, 3 })
        @DisplayName("a maximum size below 4 is rejected")
        void tooSmallMaxSizeIsRejected(final int maxSize)
        {
            assertThrows(IllegalArgumentException.class, () -> newMap(maxSize));
        }

        @ParameterizedTest(name = "of({0}) is accepted")
        @ValueSource(ints = { 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 64, 100, 1000 })
        @DisplayName("a maximum size of 4 or more is accepted")
        void validMaxSizeIsAccepted(final int maxSize)
        {
            final LRUClockMap<String, String> map = newMap(maxSize);

            assertNotNull(map);
            assertEquals(0, map.size());
        }

        @Test
        @DisplayName("a fresh map is empty")
        void freshMapIsEmpty()
        {
            final LRUClockMap<String, String> map = newMap(8);

            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
            assertNull(map.get("anything"));
            assertNull(map.getRaw("anything"));
            assertNull(map.remove("anything"));
        }

        @Test
        @DisplayName("a large map can be created and filled")
        void largeMapWorks()
        {
            final int maxSize = 50_000;
            final var map = LRUClockMapTest.<String, String>newMap(maxSize);

            fill(map, 0, maxSize);

            assertEquals(maxSize, map.size());
            assertEquals(value(0), map.getRaw(key(0)));
            assertEquals(value(maxSize - 1), map.getRaw(key(maxSize - 1)));
        }
    }

    // ==================================================================
    // put / get / getRaw / remove / size below the capacity limit
    // ==================================================================

    @Nested
    @DisplayName("basic map contract")
    class BasicContract
    {
        @Test
        @DisplayName("put stores a value that get and getRaw return")
        void putThenGet()
        {
            final var map = LRUClockMapTest.<String, String>newMap(8);

            assertNull(map.put("a", "1"), "put of a new key returns null");

            assertEquals("1", map.get("a"));
            assertEquals("1", map.getRaw("a"));
            assertEquals(1, map.size());
        }

        @Test
        @DisplayName("get and getRaw return the very same instance that was put")
        void valueIdentityIsPreserved()
        {
            final var map = LRUClockMapTest.<String, List<String>>newMap(8);
            final var stored = new ArrayList<String>();

            map.put("a", stored);

            assertSame(stored, map.get("a"));
            assertSame(stored, map.getRaw("a"));
        }

        @Test
        @DisplayName("put replaces the value of an existing key and returns the old one")
        void putReplaces()
        {
            final var map = LRUClockMapTest.<String, String>newMap(8);

            map.put("a", "1");

            assertEquals("1", map.put("a", "2"), "put must return the previous value");
            assertEquals("2", map.getRaw("a"));
            assertEquals(1, map.size(), "replacing must not change the size");

            assertEquals("2", map.put("a", "3"));
            assertEquals("3", map.getRaw("a"));
            assertEquals(1, map.size());
        }

        @Test
        @DisplayName("get and getRaw return null for an unknown key")
        void missingKeyReturnsNull()
        {
            final var map = LRUClockMapTest.<String, String>newMap(8);

            fill(map, 0, 4);

            assertNull(map.get("nope"));
            assertNull(map.getRaw("nope"));
            assertEquals(4, map.size(), "a failed lookup must not change the size");
        }

        @Test
        @DisplayName("remove deletes the mapping and returns the previous value")
        void removeReturnsPreviousValue()
        {
            final var map = LRUClockMapTest.<String, String>newMap(8);

            fill(map, 0, 4);

            assertEquals(value(1), map.remove(key(1)));
            assertNull(map.get(key(1)));
            assertNull(map.getRaw(key(1)));
            assertEquals(3, map.size());
        }

        @Test
        @DisplayName("remove of an unknown key returns null and changes nothing")
        void removeOfUnknownKey()
        {
            final var map = LRUClockMapTest.<String, String>newMap(8);

            fill(map, 0, 4);

            assertNull(map.remove("nope"));
            assertEquals(4, map.size());

            map.remove(key(0));
            assertNull(map.remove(key(0)), "removing twice returns null the second time");
            assertEquals(3, map.size());
        }

        @Test
        @DisplayName("a removed key can be put again")
        void removeThenPutAgain()
        {
            final var map = LRUClockMapTest.<String, String>newMap(8);

            fill(map, 0, 4);
            map.remove(key(2));

            assertNull(map.put(key(2), "fresh"), "after a remove the key counts as new again");
            assertEquals("fresh", map.getRaw(key(2)));
            assertEquals(4, map.size());
        }

        @Test
        @DisplayName("size reflects every insert and remove")
        void sizeTracksTheContent()
        {
            final var map = LRUClockMapTest.<String, String>newMap(16);

            for (int i = 0; i < 16; i++)
            {
                map.put(key(i), value(i));
                assertEquals(i + 1, map.size());
            }

            for (int i = 0; i < 16; i++)
            {
                map.remove(key(i));
                assertEquals(15 - i, map.size());
            }
        }

        @Test
        @DisplayName("all entries below the limit stay retrievable")
        void everythingBelowTheLimitIsRetrievable()
        {
            final int maxSize = 64;
            final var map = LRUClockMapTest.<String, String>newMap(maxSize);

            fill(map, 0, maxSize);

            for (int i = 0; i < maxSize; i++)
            {
                assertEquals(value(i), map.getRaw(key(i)), "entry " + i + " was lost before the limit was reached");
            }
        }

        @Test
        @DisplayName("an equal but different key instance addresses the same entry")
        void equalKeysAreTheSameKey()
        {
            final var map = LRUClockMapTest.<String, String>newMap(8);
            final var original = "alpha";
            final var equalButDistinct = new StringBuilder("alpha").toString();

            assertNotSame(original, equalButDistinct, "test setup: the two instances must not be identical");

            map.put(original, "1");

            assertEquals("1", map.getRaw(equalButDistinct));
            assertEquals("1", map.put(equalButDistinct, "2"), "an equal key must replace, not add");
            assertEquals(1, map.size());
            assertEquals("2", map.getRaw(original));
            assertEquals("2", map.remove(equalButDistinct));
            assertEquals(0, map.size());
        }
    }

    // ==================================================================
    // keys()
    // ==================================================================

    @Nested
    @DisplayName("keys()")
    class Keys
    {
        @Test
        @DisplayName("an empty map returns an empty list, never null")
        void emptyMapReturnsEmptyList()
        {
            final var map = LRUClockMapTest.<String, String>newMap(8);

            final var keys = map.keys();

            assertNotNull(keys);
            assertTrue(keys.isEmpty());
        }

        @Test
        @DisplayName("keys contains every key exactly once")
        void keysContainsEveryKeyOnce()
        {
            final var map = LRUClockMapTest.<String, String>newMap(16);

            fill(map, 0, 10);

            final var keys = map.keys();

            assertEquals(10, keys.size(), "keys() must return as many entries as size()");
            assertEquals(map.size(), keys.size());
            assertEquals(10, Set.copyOf(keys).size(), "keys() must not contain duplicates");

            for (int i = 0; i < 10; i++)
            {
                assertTrue(keys.contains(key(i)), "missing key " + key(i));
            }
        }

        @Test
        @DisplayName("keys never contains a removed or an evicted key")
        void keysHasNoStaleEntries()
        {
            final int maxSize = 8;
            final var map = LRUClockMapTest.<String, String>newMap(maxSize);

            fill(map, 0, maxSize);
            map.remove(key(3));

            assertFalse(map.keys().contains(key(3)));
            assertEquals(map.size(), map.keys().size());

            // push far beyond the limit and verify keys() stays consistent
            fill(map, 100, 5 * maxSize);

            assertEquals(maxSize, map.keys().size());
            assertEquals(map.size(), map.keys().size());

            for (final var k : map.keys())
            {
                assertNotNull(map.getRaw(k), "keys() reported " + k + " but getRaw does not know it");
            }
        }

        @Test
        @DisplayName("changing the returned list does not change the map")
        void returnedListIsDetached()
        {
            final var map = LRUClockMapTest.<String, String>newMap(8);

            fill(map, 0, 4);

            final var keys = map.keys();

            // an immutable list is an even stronger form of detachment, so both are fine
            try
            {
                keys.clear();
                keys.add("phantom");
            }
            catch (final UnsupportedOperationException immutableIsFineToo)
            {
                // nothing to do
            }

            assertEquals(4, map.size(), "mutating the returned list must not change the map");
            assertNull(map.getRaw("phantom"));

            final var again = map.keys();

            assertEquals(4, again.size());
            assertEquals(Set.of(key(0), key(1), key(2), key(3)), Set.copyOf(again));
        }

        @Test
        @DisplayName("two calls return independent lists")
        void callsAreIndependent()
        {
            final var map = LRUClockMapTest.<String, String>newMap(8);

            fill(map, 0, 4);

            final var first = map.keys();
            final var second = map.keys();

            assertNotSame(first, second, "each call must hand out its own list");
            assertEquals(Set.copyOf(first), Set.copyOf(second));
        }
    }

    // ==================================================================
    // clear()
    // ==================================================================

    @Nested
    @DisplayName("clear()")
    class Clear
    {
        @Test
        @DisplayName("clear empties the map")
        void clearEmpties()
        {
            final var map = LRUClockMapTest.<String, String>newMap(8);

            fill(map, 0, 8);
            map.clear();

            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());

            for (int i = 0; i < 8; i++)
            {
                assertNull(map.get(key(i)), "get still finds " + key(i) + " after clear");
                assertNull(map.getRaw(key(i)), "getRaw still finds " + key(i) + " after clear");
                assertNull(map.remove(key(i)), "remove still finds " + key(i) + " after clear");
            }
        }

        @Test
        @DisplayName("clear on an empty map is harmless and repeatable")
        void clearIsIdempotent()
        {
            final var map = LRUClockMapTest.<String, String>newMap(8);

            map.clear();
            map.clear();

            assertEquals(0, map.size());

            fill(map, 0, 3);
            map.clear();
            map.clear();

            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
        }

        @Test
        @DisplayName("the map is fully usable again after clear")
        void mapIsReusableAfterClear()
        {
            final int maxSize = 8;
            final var map = LRUClockMapTest.<String, String>newMap(maxSize);

            fill(map, 0, 5 * maxSize);
            map.clear();
            fill(map, 0, maxSize);

            assertEquals(maxSize, map.size());

            for (int i = 0; i < maxSize; i++)
            {
                assertEquals(value(i), map.getRaw(key(i)), "entry " + i + " missing after the refill");
            }
        }

        @Test
        @DisplayName("clear does not change the maximum size")
        void maxSizeSurvivesClear()
        {
            final int maxSize = 8;
            final var map = LRUClockMapTest.<String, String>newMap(maxSize);

            fill(map, 0, maxSize);
            map.clear();
            fill(map, 100, 4 * maxSize);

            assertEquals(maxSize, map.size(), "the limit must still be enforced after a clear");
        }

        @Test
        @DisplayName("clear removes colliding entries as well")
        void clearHandlesCollisions()
        {
            final var map = LRUClockMapTest.<HashedKey, String>newMap(8);

            for (int i = 0; i < 8; i++)
            {
                map.put(HashedKey.colliding(i), value(i));
            }

            map.clear();

            assertEquals(0, map.size());

            for (int i = 0; i < 8; i++)
            {
                assertNull(map.getRaw(HashedKey.colliding(i)));
            }
        }
    }

    // ==================================================================
    // null handling
    // ==================================================================

    @Nested
    @DisplayName("null keys and values")
    class NullHandling
    {
        @Test
        @DisplayName("a null key is rejected by every method")
        void nullKeyIsRejected()
        {
            final var map = LRUClockMapTest.<String, String>newMap(8);

            fill(map, 0, 4);

            assertThrows(NullPointerException.class, () -> map.put(null, "v"));
            assertThrows(NullPointerException.class, () -> map.get(null));
            assertThrows(NullPointerException.class, () -> map.getRaw(null));
            assertThrows(NullPointerException.class, () -> map.remove(null));

            assertEquals(4, map.size(), "a rejected call must not change the map");
        }

        @Test
        @DisplayName("a null value is rejected by put")
        void nullValueIsRejected()
        {
            final var map = LRUClockMapTest.<String, String>newMap(8);

            assertThrows(NullPointerException.class, () -> map.put("a", null));

            assertEquals(0, map.size(), "a rejected put must not insert anything");
            assertNull(map.getRaw("a"));
            assertTrue(map.keys().isEmpty());
        }

        @Test
        @DisplayName("a rejected put does not destroy an existing mapping")
        void rejectedPutKeepsTheOldValue()
        {
            final var map = LRUClockMapTest.<String, String>newMap(8);

            map.put("a", "1");

            assertThrows(NullPointerException.class, () -> map.put("a", null));

            assertEquals("1", map.getRaw("a"), "the old value must survive a rejected put");
            assertEquals(1, map.size());
        }
    }

    // ==================================================================
    // open addressing specifics
    // ==================================================================

    @Nested
    @DisplayName("hashing and collisions")
    class HashingAndCollisions
    {
        @Test
        @DisplayName("keys with an identical hash code are all stored and found")
        void fullCollisionsWork()
        {
            final int maxSize = 16;
            final var map = LRUClockMapTest.<HashedKey, String>newMap(maxSize);

            for (int i = 0; i < maxSize; i++)
            {
                map.put(HashedKey.colliding(i), value(i));
            }

            assertEquals(maxSize, map.size());

            for (int i = 0; i < maxSize; i++)
            {
                assertEquals(value(i), map.getRaw(HashedKey.colliding(i)), "colliding entry " + i + " was lost");
            }
        }

        @Test
        @DisplayName("removing from the middle of a probe chain keeps the rest findable")
        void removeDoesNotBreakTheProbeChain()
        {
            final int maxSize = 16;
            final var map = LRUClockMapTest.<HashedKey, String>newMap(maxSize);

            for (int i = 0; i < 10; i++)
            {
                map.put(HashedKey.colliding(i), value(i));
            }

            // take a few entries out of the middle of the chain
            assertEquals(value(3), map.remove(HashedKey.colliding(3)));
            assertEquals(value(5), map.remove(HashedKey.colliding(5)));
            assertEquals(value(6), map.remove(HashedKey.colliding(6)));

            assertEquals(7, map.size());

            for (int i = 0; i < 10; i++)
            {
                final var probe = HashedKey.colliding(i);

                if (i == 3 || i == 5 || i == 6)
                {
                    assertNull(map.getRaw(probe), "removed entry " + i + " is still there");
                }
                else
                {
                    assertEquals(value(i), map.getRaw(probe),
                                 "entry " + i + " became unreachable after a removal in its probe chain");
                }
            }
        }

        @Test
        @DisplayName("a removed colliding key can be re-inserted and found")
        void reinsertAfterRemoveInAProbeChain()
        {
            final var map = LRUClockMapTest.<HashedKey, String>newMap(16);

            for (int i = 0; i < 8; i++)
            {
                map.put(HashedKey.colliding(i), value(i));
            }

            map.remove(HashedKey.colliding(4));
            map.put(HashedKey.colliding(4), "again");

            assertEquals(8, map.size());
            assertEquals("again", map.getRaw(HashedKey.colliding(4)));

            for (int i = 0; i < 8; i++)
            {
                assertNotNull(map.getRaw(HashedKey.colliding(i)), "entry " + i + " was lost");
            }
        }

        @Test
        @DisplayName("many put/remove cycles on colliding keys do not degrade the map")
        void repeatedPutRemoveCyclesStayHealthy()
        {
            final int maxSize = 8;
            final var map = LRUClockMapTest.<HashedKey, String>newMap(maxSize);

            for (int i = 0; i < 4; i++)
            {
                map.put(HashedKey.colliding(i), value(i));
            }

            // hammer the same slot region over and over again
            for (int round = 0; round < 2_000; round++)
            {
                final var churn = HashedKey.colliding(1_000 + (round % 3));

                map.put(churn, "churn-" + round);
                assertEquals("churn-" + round, map.getRaw(churn), "lost the entry in round " + round);
                map.remove(churn);
                assertNull(map.getRaw(churn), "the entry survived its removal in round " + round);
            }

            assertEquals(4, map.size(), "the map lost or gained entries while churning");

            for (int i = 0; i < 4; i++)
            {
                assertEquals(value(i), map.getRaw(HashedKey.colliding(i)), "stable entry " + i + " was lost");
            }
        }

        @ParameterizedTest(name = "hash code {0}")
        @ValueSource(ints = { Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -123_456, -1, 0, 1, Integer.MAX_VALUE })
        @DisplayName("extreme hash codes are handled")
        void extremeHashCodes(final int hash)
        {
            final var map = LRUClockMapTest.<HashedKey, String>newMap(8);
            final var probe = new HashedKey(1, hash);

            map.put(probe, "v");

            assertEquals("v", map.getRaw(probe));
            assertEquals("v", map.get(probe));
            assertEquals(1, map.size());
            assertEquals(List.of(probe), map.keys());
            assertEquals("v", map.remove(probe));
            assertEquals(0, map.size());
        }

        @Test
        @DisplayName("many keys with extreme hash codes share one map")
        void extremeHashCodesInOneMap()
        {
            final int[] hashes = { Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MIN_VALUE / 2, -1_000_003, -17,
                                   -1, 0, 1, 17, 1_000_003, Integer.MAX_VALUE / 2, Integer.MAX_VALUE - 1,
                                   Integer.MAX_VALUE };

            final var map = LRUClockMapTest.<HashedKey, String>newMap(2 * hashes.length);

            for (int i = 0; i < hashes.length; i++)
            {
                map.put(new HashedKey(i, hashes[i]), value(i));
            }

            assertEquals(hashes.length, map.size());

            for (int i = 0; i < hashes.length; i++)
            {
                final var probe = new HashedKey(i, hashes[i]);

                assertEquals(value(i), map.getRaw(probe), "entry with hash code " + hashes[i] + " was lost");
            }

            // and they all disappear again
            for (int i = 0; i < hashes.length; i++)
            {
                assertEquals(value(i), map.remove(new HashedKey(i, hashes[i])));
            }

            assertEquals(0, map.size());
        }

        @Test
        @DisplayName("keys that are equal are never stored twice")
        void noDuplicatesForEqualKeys()
        {
            final var map = LRUClockMapTest.<HashedKey, String>newMap(8);

            for (int i = 0; i < 20; i++)
            {
                map.put(new HashedKey(7, 99), "round-" + i);
            }

            assertEquals(1, map.size());
            assertEquals("round-19", map.getRaw(new HashedKey(7, 99)));
        }
    }

    // ==================================================================
    // capacity limit and eviction bookkeeping
    // ==================================================================

    @Nested
    @DisplayName("capacity limit")
    class Capacity
    {
        @ParameterizedTest(name = "maxSize {0}")
        @ValueSource(ints = { 4, 5, 7, 8, 16, 17, 64, 100 })
        @DisplayName("the map holds exactly maxSize entries, nothing is evicted earlier")
        void noEarlyEviction(final int maxSize)
        {
            final var map = LRUClockMapTest.<String, String>newMap(maxSize);

            for (int i = 0; i < maxSize; i++)
            {
                map.put(key(i), value(i));
                assertEquals(i + 1, map.size(), "the map evicted before reaching its limit");
            }

            for (int i = 0; i < maxSize; i++)
            {
                assertEquals(value(i), map.getRaw(key(i)), "entry " + i + " was evicted before the limit");
            }
        }

        @ParameterizedTest(name = "maxSize {0}")
        @ValueSource(ints = { 4, 5, 7, 8, 16, 17, 64, 100 })
        @DisplayName("the size never exceeds maxSize, no matter how much is inserted")
        void sizeNeverExceedsMaxSize(final int maxSize)
        {
            final var map = LRUClockMapTest.<String, String>newMap(maxSize);

            for (int i = 0; i < 20 * maxSize; i++)
            {
                map.put(key(i), value(i));

                assertTrue(map.size() <= maxSize, "size " + map.size() + " exceeds the limit " + maxSize);
                assertEquals(map.size(), map.keys().size(), "keys() and size() disagree");
            }

            assertEquals(maxSize, map.size());
        }

        @ParameterizedTest(name = "maxSize {0}")
        @ValueSource(ints = { 4, 5, 7, 8, 16, 17, 64 })
        @DisplayName("the freshly inserted key is never the one evicted")
        void newKeyAlwaysSurvives(final int maxSize)
        {
            final var map = LRUClockMapTest.<String, String>newMap(maxSize);

            for (int i = 0; i < 10 * maxSize; i++)
            {
                map.put(key(i), value(i));

                assertEquals(value(i), map.getRaw(key(i)), "the key just inserted was evicted immediately");
            }
        }

        @ParameterizedTest(name = "maxSize {0}")
        @ValueSource(ints = { 4, 5, 7, 8, 16, 17, 64 })
        @DisplayName("every over capacity insert evicts exactly one entry")
        void exactlyOneEvictionPerInsert(final int maxSize)
        {
            final var map = LRUClockMapTest.<String, String>newMap(maxSize);

            fill(map, 0, maxSize);

            for (int i = 0; i < 3 * maxSize; i++)
            {
                final var before = keySet(map);

                map.put(key(maxSize + i), value(maxSize + i));

                final var gone = new LinkedHashSet<>(before);
                gone.removeAll(keySet(map));

                assertEquals(1, gone.size(), () -> "expected exactly one eviction but lost " + gone);
                assertEquals(maxSize, map.size());
            }
        }

        @Test
        @DisplayName("replacing an existing key never evicts anything")
        void replacingNeverEvicts()
        {
            final int maxSize = 16;
            final var map = LRUClockMapTest.<String, String>newMap(maxSize);

            fill(map, 0, maxSize);

            final var expected = keySet(map);

            for (int round = 0; round < 5; round++)
            {
                for (int i = 0; i < maxSize; i++)
                {
                    map.put(key(i), "round-" + round + "-" + i);
                }
            }

            assertEquals(maxSize, map.size());
            assertEquals(expected, keySet(map), "a replacing put must not evict anything");

            for (int i = 0; i < maxSize; i++)
            {
                assertEquals("round-4-" + i, map.getRaw(key(i)));
            }
        }

        @Test
        @DisplayName("a remove frees a slot, so the next insert evicts nothing")
        void removeFreesASlot()
        {
            final int maxSize = 8;
            final var map = LRUClockMapTest.<String, String>newMap(maxSize);

            fill(map, 0, maxSize);
            assertEquals(value(3), map.remove(key(3)));

            map.put(INTRUDER, "x");

            assertEquals(maxSize, map.size());
            assertEquals("x", map.getRaw(INTRUDER));

            for (int i = 0; i < maxSize; i++)
            {
                if (i == 3)
                {
                    assertNull(map.getRaw(key(i)));
                }
                else
                {
                    assertEquals(value(i), map.getRaw(key(i)),
                                 "entry " + i + " was evicted although a free slot was available");
                }
            }
        }

        @Test
        @DisplayName("a map of the smallest possible size still works")
        void smallestMapWorks()
        {
            final var map = LRUClockMapTest.<String, String>newMap(4);

            fill(map, 0, 4);
            assertEquals(4, map.size());

            for (int i = 4; i < 40; i++)
            {
                map.put(key(i), value(i));

                assertEquals(4, map.size());
                assertEquals(value(i), map.getRaw(key(i)));
            }
        }
    }

    // ==================================================================
    // the clock / second chance algorithm
    // ==================================================================

    /**
     * These tests never guess which entry the clock hand points at. They build
     * the very same map twice from an identical operation script, do exactly one
     * extra thing to the second copy, and then compare which entry the next over
     * capacity insert throws out.
     */
    @Nested
    @DisplayName("clock / second chance")
    class ClockSemantics
    {
        @ParameterizedTest(name = "maxSize {0}")
        @ValueSource(ints = { 4, 5, 7, 8, 16, 17, 64 })
        @DisplayName("get rescues exactly the entry that was about to be evicted")
        void getGivesASecondChance(final int maxSize)
        {
            final var victim = nextVictim(maxSize);

            final var map = warmedMap(maxSize);

            assertNotNull(map.get(victim), "test setup: the victim must still be in the map at this point");

            map.put(INTRUDER, "x");

            assertNotNull(map.getRaw(victim),
                          "get(" + victim + ") must set the second chance flag, so this entry has to survive");
            assertEquals(maxSize, map.size());
            assertEquals("x", map.getRaw(INTRUDER));
        }

        @ParameterizedTest(name = "maxSize {0}")
        @ValueSource(ints = { 4, 5, 7, 8, 16, 17, 64 })
        @DisplayName("getRaw rescues nothing")
        void getRawGivesNoSecondChance(final int maxSize)
        {
            final var victim = nextVictim(maxSize);

            final var map = warmedMap(maxSize);

            assertNotNull(map.getRaw(victim), "test setup: the victim must still be in the map at this point");

            map.put(INTRUDER, "x");

            assertNull(map.getRaw(victim),
                       "getRaw(" + victim + ") must not touch the clock flag, so this entry has to be evicted");
        }

        @Test
        @DisplayName("get protects the next victim and only the next victim")
        void onlyTheVictimIsRescued()
        {
            final int maxSize = 16;
            final var victim = nextVictim(maxSize);

            for (final var touched : warmedMap(maxSize).keys())
            {
                final var map = warmedMap(maxSize);

                map.get(touched);
                map.put(INTRUDER, "x");

                if (touched.equals(victim))
                {
                    assertNotNull(map.getRaw(touched), "get(" + touched + ") must save the next victim");
                }
                else
                {
                    assertNull(map.getRaw(victim),
                               "get(" + touched + ") must not change who gets evicted, " + victim
                                   + " was still the next victim");
                }
            }
        }

        @Test
        @DisplayName("getRaw never changes the eviction outcome, for any key")
        void getRawIsCompletelyNeutral()
        {
            final int maxSize = 16;
            final var victim = nextVictim(maxSize);

            for (final var touched : warmedMap(maxSize).keys())
            {
                final var map = warmedMap(maxSize);

                map.getRaw(touched);
                map.put(INTRUDER, "x");

                assertNull(map.getRaw(victim), "getRaw(" + touched + ") changed which entry was evicted");
            }
        }

        @Test
        @DisplayName("keys(), size() and failed lookups do not change the eviction outcome")
        void readOnlyObserversAreNeutral()
        {
            final int maxSize = 16;
            final var victim = nextVictim(maxSize);

            final var map = warmedMap(maxSize);

            // a lot of observing, but nothing that should count as an access
            map.keys();
            map.size();
            map.keys();
            map.get("a key that is not in the map");
            map.getRaw("another key that is not in the map");
            map.remove("a third key that is not in the map");

            map.put(INTRUDER, "x");

            assertNull(map.getRaw(victim), "observing the map must not give any entry a second chance");
            assertEquals(maxSize, map.size());
        }

        @ParameterizedTest(name = "maxSize {0}")
        @ValueSource(ints = { 4, 8, 16, 64 })
        @DisplayName("a second chance is used up, not permanent")
        void aSecondChanceIsConsumed(final int maxSize)
        {
            final var victim = nextVictim(maxSize);

            final var map = warmedMap(maxSize);

            map.get(victim);
            map.put(INTRUDER, "x");

            assertNotNull(map.getRaw(victim), "test setup: the victim should have survived the first insert");

            // never touch it again, the hand has to come back around and take it
            for (int i = 0; i < 4 * maxSize; i++)
            {
                map.put("filler-" + i, "y");
            }

            assertNull(map.getRaw(victim), "a single get must not protect an entry forever");
        }

        @ParameterizedTest(name = "maxSize {0}")
        @ValueSource(ints = { 4, 8, 16, 64 })
        @DisplayName("entries that are never touched are eventually evicted")
        void coldEntriesAreEvicted(final int maxSize)
        {
            final var map = LRUClockMapTest.<String, String>newMap(maxSize);

            fill(map, 0, maxSize);

            for (int i = 0; i < 4 * maxSize; i++)
            {
                map.put("filler-" + i, "y");
            }

            for (int i = 0; i < maxSize; i++)
            {
                assertNull(map.getRaw(key(i)), "cold entry " + key(i) + " was never evicted");
            }

            assertEquals(maxSize, map.size());
        }

        @ParameterizedTest(name = "maxSize {0}")
        @ValueSource(ints = { 8, 16, 64 })
        @DisplayName("an entry that is read before every insert survives")
        void aHotEntrySurvives(final int maxSize)
        {
            final var map = LRUClockMapTest.<String, String>newMap(maxSize);

            fill(map, 0, maxSize);

            final var hot = key(0);

            for (int i = 0; i < 4 * maxSize; i++)
            {
                assertNotNull(map.get(hot), "the hot entry was evicted in round " + i);
                map.put("filler-" + i, "y");
            }

            assertNotNull(map.getRaw(hot), "an entry that is read before every insert must not be evicted");
        }
    }

    // ==================================================================
    // randomized model based test
    // ==================================================================

    /**
     * Applies a long, seeded and therefore reproducible sequence of operations
     * and checks after every single one that everything the interface promises
     * still holds. The model cannot predict which entry the clock evicts, so it
     * only asserts what is knowable: values are never wrong, removed keys never
     * come back, the size never exceeds the limit, and keys() always agrees with
     * the rest of the map.
     */
    @Nested
    @DisplayName("randomized operations")
    class Randomized
    {
        /** The operations the generator can produce. */
        private sealed interface Op
        {
            /**
             * @param key the key to insert
             * @param value the value to insert
             */
            record Put(String key, String value) implements Op { }

            /** @param key the key to read with an LRU effect */
            record Get(String key) implements Op { }

            /** @param key the key to read without an LRU effect */
            record GetRaw(String key) implements Op { }

            /** @param key the key to delete */
            record Remove(String key) implements Op { }

            /** Drops everything. */
            record Clear() implements Op { }
        }

        @ParameterizedTest(name = "seed {0}")
        @ValueSource(longs = { 1L, 7L, 42L, 2_026L, 987_654_321L, -5L })
        @DisplayName("all invariants hold through a long random operation sequence")
        void randomOperationsKeepTheMapConsistent(final long seed)
        {
            final var random = new Random(seed);
            final int maxSize = 4 + random.nextInt(40);
            final int keySpace = 3 * maxSize;

            final var map = LRUClockMapTest.<String, String>newMap(maxSize);

            // the value a key must carry if it is present at all
            final var expectedValues = new HashMap<String, String>();
            // keys that must not be in the map because they were removed or never added
            final var mustBeAbsent = new HashSet<String>();

            for (int i = 0; i < keySpace; i++)
            {
                mustBeAbsent.add(key(i));
            }

            for (int step = 0; step < 2_000; step++)
            {
                switch (nextOperation(random, keySpace, step))
                {
                    case Op.Put(String k, String v) ->
                    {
                        final var previous = map.getRaw(k);

                        assertEquals(previous, map.put(k, v),
                                     "put must return the value that getRaw reported a moment ago, step " + step);
                        assertEquals(v, map.getRaw(k), "the key just put is missing, step " + step);

                        expectedValues.put(k, v);
                        mustBeAbsent.remove(k);
                    }
                    case Op.Get(String k) ->
                    {
                        final var actual = map.get(k);

                        if (mustBeAbsent.contains(k))
                        {
                            assertNull(actual, "get found " + k + " although it is not in the map, step " + step);
                        }
                        else if (actual != null)
                        {
                            assertEquals(expectedValues.get(k), actual, "get returned a wrong value for " + k);
                        }
                    }
                    case Op.GetRaw(String k) ->
                    {
                        final var actual = map.getRaw(k);

                        if (mustBeAbsent.contains(k))
                        {
                            assertNull(actual, "getRaw found " + k + " although it is not in the map, step " + step);
                        }
                        else if (actual != null)
                        {
                            assertEquals(expectedValues.get(k), actual, "getRaw returned a wrong value for " + k);
                        }
                    }
                    case Op.Remove(String k) ->
                    {
                        final var previous = map.getRaw(k);

                        assertEquals(previous, map.remove(k),
                                     "remove must return the value that was stored, step " + step);
                        assertNull(map.getRaw(k), "the key is still there after remove: " + k + ", step " + step);

                        expectedValues.remove(k);
                        mustBeAbsent.add(k);
                    }
                    case Op.Clear() ->
                    {
                        map.clear();

                        assertEquals(0, map.size(), "size after clear, step " + step);
                        assertTrue(map.keys().isEmpty(), "keys after clear, step " + step);

                        expectedValues.clear();
                        mustBeAbsent.clear();

                        for (int i = 0; i < keySpace; i++)
                        {
                            mustBeAbsent.add(key(i));
                        }
                    }
                }

                verifyInvariants(map, maxSize, expectedValues, mustBeAbsent, step);
            }
        }

        /**
         * @param random the source of randomness
         * @param keySpace how many distinct keys may be used
         * @param step the current step, used to build unique values
         * @return the next operation to apply
         */
        private Op nextOperation(final Random random, final int keySpace, final int step)
        {
            final var k = key(random.nextInt(keySpace));
            final int roll = random.nextInt(100);

            if (roll < 50)
            {
                return new Op.Put(k, "v-" + step);
            }
            if (roll < 70)
            {
                return new Op.Get(k);
            }
            if (roll < 85)
            {
                return new Op.GetRaw(k);
            }
            if (roll < 99)
            {
                return new Op.Remove(k);
            }

            return new Op.Clear();
        }

        /**
         * Checks everything that must be true after every single operation. Only
         * LRU neutral methods are used here so the check itself cannot influence
         * what the map does next.
         *
         * @param map the map under test
         * @param maxSize its maximum size
         * @param expectedValues the value each known key must carry if present
         * @param mustBeAbsent keys that may not be in the map
         * @param step the step number, for the failure message
         */
        private void verifyInvariants(final LRUClockMap<String, String> map,
                                      final int maxSize,
                                      final Map<String, String> expectedValues,
                                      final Set<String> mustBeAbsent,
                                      final int step)
        {
            final int size = map.size();
            final var keys = map.keys();

            assertTrue(size >= 0, "negative size in step " + step);
            assertTrue(size <= maxSize, "size " + size + " exceeds maxSize " + maxSize + " in step " + step);
            assertNotNull(keys, "keys() returned null in step " + step);
            assertEquals(size, keys.size(), "keys() and size() disagree in step " + step);
            assertEquals(size, Set.copyOf(keys).size(), "keys() contains duplicates in step " + step);

            for (final var k : keys)
            {
                assertNotNull(k, "keys() contains null in step " + step);
                assertEquals(expectedValues.get(k), map.getRaw(k), "wrong value for " + k + " in step " + step);
                assertFalse(mustBeAbsent.contains(k), "removed key " + k + " came back in step " + step);
            }

            for (final var k : mustBeAbsent)
            {
                assertNull(map.getRaw(k), "key " + k + " must not be in the map in step " + step);
            }
        }
    }

    // ==================================================================
    // generic usage
    // ==================================================================

    @Nested
    @DisplayName("generics")
    class Generics
    {
        @Test
        @DisplayName("works with boxed numbers as keys")
        void integerKeys()
        {
            final var map = LRUClockMapTest.<Integer, String>newMap(8);

            for (int i = 0; i < 8; i++)
            {
                map.put(i, value(i));
            }

            assertEquals(8, map.size());
            assertEquals(value(3), map.getRaw(3));
            assertEquals(value(3), map.get(3));
            assertEquals(value(3), map.remove(3));

            // beyond the Integer cache, so these are distinct instances
            map.put(100_000, "big");

            assertEquals("big", map.getRaw(100_000));
        }

        @Test
        @DisplayName("works with records as keys and collections as values")
        void recordKeysAndCollectionValues()
        {
            final var map = LRUClockMapTest.<Coordinate, List<String>>newMap(8);

            map.put(new Coordinate(1, 2), List.of("a", "b"));
            map.put(new Coordinate(3, 4), List.of("c"));

            assertEquals(List.of("a", "b"), map.getRaw(new Coordinate(1, 2)));
            assertEquals(List.of("c"), map.get(new Coordinate(3, 4)));
            assertNull(map.getRaw(new Coordinate(9, 9)));
            assertEquals(2, map.size());
        }

        @Test
        @DisplayName("two maps do not share state")
        void mapsAreIndependent()
        {
            final var first = LRUClockMapTest.<String, String>newMap(4);
            final var second = LRUClockMapTest.<String, String>newMap(4);

            fill(first, 0, 4);

            assertEquals(4, first.size());
            assertEquals(0, second.size(), "a second map must start out empty");
            assertNull(second.getRaw(key(0)));

            second.put(key(0), "other");

            assertEquals(value(0), first.getRaw(key(0)), "the maps must not share their storage");
        }
    }

    // ==================================================================
    // test fixtures
    // ==================================================================

    /**
     * A key with a fully controllable hash code. Two keys are equal only when
     * both components match, so equal keys always report equal hash codes and
     * the {@code equals}/{@code hashCode} contract holds.
     *
     * @param id distinguishes keys that are meant to collide
     * @param hash the hash code this key reports
     */
    record HashedKey(int id, int hash)
    {
        /** The hash code shared by every key built with {@link #colliding(int)}. */
        private static final int SHARED_HASH = 0x5EED;

        @Override
        public int hashCode()
        {
            return hash;
        }

        /**
         * @param id the distinguishing id
         * @return a key that collides with every other key built this way
         */
        static HashedKey colliding(final int id)
        {
            return new HashedKey(id, SHARED_HASH);
        }
    }

    /**
     * A plain record key, to show that ordinary value objects work.
     *
     * @param x the first component
     * @param y the second component
     */
    record Coordinate(int x, int y) { }
}
