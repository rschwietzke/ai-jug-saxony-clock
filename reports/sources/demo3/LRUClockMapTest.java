package org.jugsaxony.clock.demo3;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class LRUClockMapTest
{
    @Test
    void rejectsMaximumSizesBelowFour()
    {
        for (final var maxSize : new int[] {Integer.MIN_VALUE, -1, 0, 1, 2, 3})
        {
            assertThrows(
                IllegalArgumentException.class,
                () -> newMap(maxSize),
                () -> "maxSize " + maxSize + " must be rejected");
        }
    }

    @Test
    void factoryCreatesFreshIndependentMaps()
    {
        final LRUClockMap<String, Integer> first = newMap(4);
        final LRUClockMap<String, Integer> second = newMap(4);

        assertNotSame(first, second);
        assertNull(first.put("key", 1));

        assertAll(
            () -> assertEquals(1, first.size()),
            () -> assertEquals(1, first.getRaw("key")),
            () -> assertEquals(0, second.size()),
            () -> assertNull(second.getRaw("key")),
            () -> assertTrue(second.keys().isEmpty()));
    }

    @Test
    void minimumMaximumSizeCreatesAnEmptyMap()
    {
        final LRUClockMap<String, Integer> map = newMap(4);

        assertAll(
            () -> assertEquals(0, map.size()),
            () -> assertNotNull(map.keys()),
            () -> assertTrue(map.keys().isEmpty()),
            () -> assertNull(map.get("missing")),
            () -> assertNull(map.getRaw("missing")),
            () -> assertNull(map.remove("missing")),
            () -> assertEquals(0, map.size()));
    }

    @Test
    void insertsAndReadsMappings()
    {
        final LRUClockMap<String, Integer> map = newMap(8);

        assertNull(map.put("one", 1));
        assertNull(map.put("two", 2));
        assertNull(map.put("three", 3));

        assertAll(
            () -> assertEquals(3, map.size()),
            () -> assertEquals(1, map.get("one")),
            () -> assertEquals(2, map.getRaw("two")),
            () -> assertEquals(3, map.get("three")),
            () -> assertEquals(Set.of("one", "two", "three"), keySet(map)));
    }

    @Test
    void replacingMappingReturnsOldValueWithoutChangingSize()
    {
        final LRUClockMap<String, String> map = newMap(4);
        map.put("key", "old");

        assertEquals("old", map.put("key", "new"));

        assertAll(
            () -> assertEquals(1, map.size()),
            () -> assertEquals("new", map.getRaw("key")),
            () -> assertEquals(Set.of("key"), keySet(map)));
    }

    @Test
    void usesKeyEqualityRatherThanIdentity()
    {
        final LRUClockMap<TestKey, String> map = newMap(4);
        final var storedKey = new TestKey("equal", 17);
        final var equalKey = new TestKey("equal", 17);

        assertNotSame(storedKey, equalKey);
        assertNull(map.put(storedKey, "value"));

        assertAll(
            () -> assertEquals("value", map.get(equalKey)),
            () -> assertEquals("value", map.put(equalKey, "updated")),
            () -> assertEquals(1, map.size()),
            () -> assertEquals("updated", map.getRaw(storedKey)));
    }

    @Test
    void handlesCollisionsAcrossReplacementRemovalAndReinsertion()
    {
        final LRUClockMap<TestKey, String> map = newMap(8);
        final var first = new TestKey("first", 42);
        final var second = new TestKey("second", 42);
        final var third = new TestKey("third", 42);
        final var fourth = new TestKey("fourth", 42);
        final var replacement = new TestKey("replacement", 42);

        map.put(first, "one");
        map.put(second, "two");
        map.put(third, "three");
        map.put(fourth, "four");

        assertEquals("two", map.remove(second));
        assertEquals("three", map.put(third, "updated-three"));
        assertNull(map.put(replacement, "replacement-value"));

        assertAll(
            () -> assertEquals(4, map.size()),
            () -> assertEquals("one", map.getRaw(first)),
            () -> assertNull(map.getRaw(second)),
            () -> assertEquals("updated-three", map.getRaw(third)),
            () -> assertEquals("four", map.getRaw(fourth)),
            () -> assertEquals("replacement-value", map.getRaw(replacement)),
            () -> assertEquals(Set.of(first, third, fourth, replacement), keySet(map)));
    }

    @Test
    void handlesNegativeAndMinimumHashCodes()
    {
        final LRUClockMap<TestKey, Integer> map = newMap(4);
        final var minusOne = new TestKey("minus-one", -1);
        final var minimum = new TestKey("minimum", Integer.MIN_VALUE);
        final var negative = new TestKey("negative", -31);

        map.put(minusOne, 1);
        map.put(minimum, 2);
        map.put(negative, 3);

        assertAll(
            () -> assertEquals(1, map.getRaw(minusOne)),
            () -> assertEquals(2, map.getRaw(minimum)),
            () -> assertEquals(3, map.getRaw(negative)),
            () -> assertEquals(Set.of(minusOne, minimum, negative), keySet(map)));
    }

    @Test
    void removesOnlyRequestedMappingAndAllowsReinsertion()
    {
        final LRUClockMap<String, Integer> map = newMap(4);
        map.put("first", 1);
        map.put("second", 2);

        assertEquals(1, map.remove("first"));
        assertAll(
            () -> assertEquals(1, map.size()),
            () -> assertNull(map.get("first")),
            () -> assertEquals(2, map.getRaw("second")),
            () -> assertNull(map.remove("first")),
            () -> assertNull(map.remove("missing")));

        assertNull(map.put("first", 11));
        assertAll(
            () -> assertEquals(2, map.size()),
            () -> assertEquals(11, map.getRaw("first")),
            () -> assertEquals(2, map.getRaw("second")));
    }

    @Test
    void clearEmptiesMapAndRetainsItsUsability()
    {
        final LRUClockMap<Integer, String> map = newMap(4);
        for (var key = 0; key < 4; key++)
        {
            map.put(key, valueFor(key));
        }

        map.clear();

        assertAll(
            () -> assertEquals(0, map.size()),
            () -> assertTrue(map.keys().isEmpty()),
            () -> assertNull(map.getRaw(0)),
            () -> assertNull(map.getRaw(3)));

        for (var key = 10; key < 15; key++)
        {
            assertNull(map.put(key, valueFor(key)));
        }
        assertAll(
            () -> assertEquals(4, map.size()),
            () -> assertEquals(valueFor(14), map.getRaw(14)),
            () -> assertEquals(4, keySet(map).size()));
    }

    @Test
    void keysReturnsAnIndependentSnapshotWithEveryKeyExactlyOnce()
    {
        final LRUClockMap<String, Integer> map = newMap(8);
        map.put("alpha", 1);
        map.put("beta", 2);
        map.put("gamma", 3);

        final var returnedKeys = map.keys();
        assertAll(
            () -> assertNotNull(returnedKeys),
            () -> assertEquals(3, returnedKeys.size()),
            () -> assertEquals(Set.of("alpha", "beta", "gamma"), new HashSet<>(returnedKeys)));

        try
        {
            returnedKeys.clear();
        }
        catch (final UnsupportedOperationException ignored)
        {
            // An immutable snapshot also cannot expose the map's internal structure.
        }

        assertAll(
            () -> assertEquals(3, map.size()),
            () -> assertEquals(Set.of("alpha", "beta", "gamma"), keySet(map)),
            () -> assertEquals(1, map.getRaw("alpha")));
    }

    @Test
    void firstOverflowRetainsNewMappingAndEvictsExactlyOneOldMapping()
    {
        final LRUClockMap<Integer, String> map = newMap(4);
        for (var key = 0; key < 4; key++)
        {
            assertNull(map.put(key, valueFor(key)));
        }
        final var beforeOverflow = keySet(map);

        assertNull(map.put(4, valueFor(4)));

        final var afterOverflow = keySet(map);
        assertAll(
            () -> assertEquals(4, map.size()),
            () -> assertEquals(4, beforeOverflow.size()),
            () -> assertEquals(4, afterOverflow.size()),
            () -> assertTrue(afterOverflow.contains(4)),
            () -> assertEquals(valueFor(4), map.getRaw(4)),
            () -> assertEquals(1, difference(beforeOverflow, afterOverflow).size()),
            () -> assertEquals(1, difference(afterOverflow, beforeOverflow).size()));
    }

    @Test
    void insertionAfterRemovalAtCapacityDoesNotEvictASurvivor()
    {
        final LRUClockMap<Integer, String> map = newMap(4);
        for (var key = 0; key < 4; key++)
        {
            map.put(key, valueFor(key));
        }

        assertEquals(valueFor(1), map.remove(1));
        assertNull(map.put(4, valueFor(4)));

        assertAll(
            () -> assertEquals(4, map.size()),
            () -> assertEquals(Set.of(0, 2, 3, 4), keySet(map)),
            () -> assertEquals(valueFor(0), map.getRaw(0)),
            () -> assertEquals(valueFor(2), map.getRaw(2)),
            () -> assertEquals(valueFor(3), map.getRaw(3)),
            () -> assertEquals(valueFor(4), map.getRaw(4)));
    }

    @Test
    void repeatedOverflowNeverExceedsNonPowerOfTwoMaximumSize()
    {
        final var maxSize = 7;
        final LRUClockMap<Integer, Integer> map = newMap(maxSize);

        for (var key = 0; key < 100; key++)
        {
            assertNull(map.put(key, key), "key " + key + " is new");
            assertEquals(Math.min(key + 1, maxSize), map.size());
            assertTrue(map.size() <= maxSize);
            assertEquals(key, map.getRaw(key), "new key " + key + " must be retained");
            assertEquals(map.size(), keySet(map).size());
        }
    }

    @Test
    void equalKeyReplacementAtCapacityDoesNotEvictAnotherMapping()
    {
        final LRUClockMap<TestKey, String> map = newMap(4);
        for (var key = 0; key < 4; key++)
        {
            map.put(new TestKey("key-" + key, key), valueFor(key));
        }
        final var keysBeforeReplacement = keySet(map);
        final var equalReplacementKey = new TestKey("key-2", 2);

        assertEquals(valueFor(2), map.put(equalReplacementKey, "updated"));

        assertAll(
            () -> assertEquals(4, map.size()),
            () -> assertEquals(keysBeforeReplacement, keySet(map)),
            () -> assertEquals("updated", map.getRaw(equalReplacementKey)),
            () -> assertEquals(valueFor(0), map.getRaw(new TestKey("key-0", 0))),
            () -> assertEquals(valueFor(1), map.getRaw(new TestKey("key-1", 1))),
            () -> assertEquals(valueFor(3), map.getRaw(new TestKey("key-3", 3))));

        assertEquals("updated", map.remove(new TestKey("key-2", 2)));
        assertAll(
            () -> assertEquals(3, map.size()),
            () -> assertNull(map.getRaw(equalReplacementKey)),
            () -> assertEquals(3, keySet(map).size()));
    }

    private static String valueFor(final int key)
    {
        return "value-" + key;
    }

    private static <K> Set<K> keySet(final LRUClockMap<K, ?> map)
    {
        final var keys = map.keys();
        assertNotNull(keys, "keys must not return null");
        assertEquals(keys.size(), new HashSet<>(keys).size(), "keys must not contain duplicates");
        return Set.copyOf(keys);
    }

    private static <K> Set<K> difference(final Set<K> left, final Set<K> right)
    {
        final var difference = new HashSet<>(left);
        difference.removeAll(right);
        return Set.copyOf(difference);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> LRUClockMap<K, V> newMap(final int maxSize)
    {
        try
        {
            final var factory = LRUClockMap.class.getMethod("of", int.class);
            if (!Modifier.isStatic(factory.getModifiers()))
            {
                throw new AssertionError("LRUClockMap.of(int) must be static");
            }

            final var result = factory.invoke(null, maxSize);
            if (!(result instanceof LRUClockMap<?, ?> map))
            {
                throw new AssertionError("LRUClockMap.of(int) must return an LRUClockMap");
            }
            return (LRUClockMap<K, V>) map;
        }
        catch (final NoSuchMethodException exception)
        {
            throw new AssertionError("LRUClockMap must provide the documented public static of(int) factory", exception);
        }
        catch (final IllegalAccessException exception)
        {
            throw new AssertionError("LRUClockMap.of(int) must be publicly accessible", exception);
        }
        catch (final InvocationTargetException exception)
        {
            switch (exception.getCause())
            {
                case RuntimeException cause -> throw cause;
                case Error cause -> throw cause;
                case Throwable cause -> throw new AssertionError("LRUClockMap.of(int) threw a checked exception", cause);
                case null -> throw new AssertionError("LRUClockMap.of(int) failed without a cause", exception);
            }
        }
    }

    private record TestKey(String id, int forcedHash)
    {
        @Override
        public int hashCode()
        {
            return forcedHash;
        }
    }
}
