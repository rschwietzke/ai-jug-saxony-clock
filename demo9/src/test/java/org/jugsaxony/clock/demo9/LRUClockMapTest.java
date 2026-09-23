package org.jugsaxony.clock.demo9;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive JUnit 5 unit test suite for the {@link LRUClockMap} interface.
 * Validates map lifecycle, sizing constraints, CRUD operations, open addressing
 * collision resolution, second-chance clock eviction dynamics, and null contracts.
 */
public class LRUClockMapTest {

    /**
     * Factory method to instantiate the {@link LRUClockMap} implementation.
     * Searches for:
     * 1. Static factory {@code LRUClockMap.of(int maxSize)}
     * 2. Constructor {@code LRUClockMap(int maxSize)}
     * 3. Implementation class {@code LRUClockMapImpl} in this package
     *
     * @param maxSize maximum capacity of the map
     * @param <K> key type
     * @param <V> value type
     * @return an instance of {@link LRUClockMap}
     */
    @SuppressWarnings("unchecked")
    protected <K, V> LRUClockMap<K, V> createMap(int maxSize) {
        try {
            Method ofMethod = LRUClockMap.class.getMethod("of", int.class);
            return (LRUClockMap<K, V>) ofMethod.invoke(null, maxSize);
        } catch (NoSuchMethodException e) {
            try {
                Constructor<?> ctor = LRUClockMap.class.getConstructor(int.class);
                return (LRUClockMap<K, V>) ctor.newInstance(maxSize);
            } catch (NoSuchMethodException ex) {
                try {
                    Class<?> implClass = Class.forName("org.jugsaxony.clock.demo9.LRUClockMapImpl");
                    try {
                        Method implOf = implClass.getMethod("of", int.class);
                        return (LRUClockMap<K, V>) implOf.invoke(null, maxSize);
                    } catch (NoSuchMethodException ex2) {
                        Constructor<?> implCtor = implClass.getConstructor(int.class);
                        return (LRUClockMap<K, V>) implCtor.newInstance(maxSize);
                    }
                } catch (ClassNotFoundException | NoSuchMethodException notFound) {
                    throw new UnsupportedOperationException(
                        "No implementation available yet. LRUClockMap must provide either static of(int) or a public constructor with int parameter.",
                        ex
                    );
                } catch (InvocationTargetException targetEx) {
                    unwrapAndRethrow(targetEx);
                    throw new RuntimeException(targetEx);
                } catch (ReflectiveOperationException refEx) {
                    throw new RuntimeException("Failed to instantiate LRUClockMapImpl", refEx);
                }
            } catch (InvocationTargetException targetEx) {
                unwrapAndRethrow(targetEx);
                throw new RuntimeException(targetEx);
            } catch (ReflectiveOperationException refEx) {
                throw new RuntimeException("Failed to invoke constructor for LRUClockMap", refEx);
            }
        } catch (InvocationTargetException e) {
            unwrapAndRethrow(e);
            throw new RuntimeException(e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke of(int) on LRUClockMap", e);
        }
    }

    private static void unwrapAndRethrow(InvocationTargetException ite) {
        Throwable cause = ite.getCause();
        if (cause instanceof RuntimeException re) {
            throw re;
        }
        if (cause instanceof Error err) {
            throw err;
        }
        throw new RuntimeException(cause);
    }

    /**
     * Java 21 Record for simulating hash collisions with controllable hash codes.
     */
    public record CollidingKey(String id, int forcedHash) {
        public CollidingKey(String id) {
            this(id, 0);
        }

        @Override
        public int hashCode() {
            return forcedHash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj instanceof CollidingKey other) {
                return Objects.equals(this.id, other.id);
            }
            return false;
        }
    }

    /**
     * Java 21 sealed interface and records for modeling map operations in stress tests.
     */
    public sealed interface MapOp permits MapOp.Put, MapOp.Get, MapOp.GetRaw, MapOp.Remove {
        record Put(String key, String value) implements MapOp {}
        record Get(String key) implements MapOp {}
        record GetRaw(String key) implements MapOp {}
        record Remove(String key) implements MapOp {}
    }

    @Nested
    @DisplayName("1. Instantiation and Boundary Tests")
    class InstantiationTests {

        @ParameterizedTest(name = "maxSize={0} must throw IllegalArgumentException")
        @ValueSource(ints = {Integer.MIN_VALUE, -100, -1, 0, 1, 2, 3})
        @DisplayName("Should reject maxSize less than 4")
        void shouldRejectMaxSizeLessThanFour(int invalidSize) {
            assertThrows(IllegalArgumentException.class, () -> createMap(invalidSize));
        }

        @ParameterizedTest(name = "maxSize={0} must succeed")
        @ValueSource(ints = {4, 5, 10, 16, 64, 100, 1024})
        @DisplayName("Should accept maxSize greater than or equal to 4")
        void shouldAcceptValidMaxSize(int validSize) {
            assertDoesNotThrow(() -> {
                var map = createMap(validSize);
                assertNotNull(map);
                assertEquals(0, map.size());
            });
        }

        @Test
        @DisplayName("Freshly created map has size 0 and empty keys")
        void freshlyCreatedMapHasZeroSizeAndEmptyKeys() {
            LRUClockMap<String, String> map = createMap(4);
            assertAll(
                () -> assertEquals(0, map.size()),
                () -> assertNotNull(map.keys()),
                () -> assertTrue(map.keys().isEmpty()),
                () -> assertNull(map.get("absent")),
                () -> assertNull(map.getRaw("absent")),
                () -> assertNull(map.remove("absent"))
            );
        }
    }

    @Nested
    @DisplayName("2. Basic CRUD Operations Tests")
    class BasicOperationsTests {

        @Test
        @DisplayName("put returns null on new insertion and increments size")
        void putReturnsNullOnNewKey() {
            LRUClockMap<String, String> map = createMap(4);

            var prev = map.put("k1", "v1");

            assertNull(prev);
            assertEquals(1, map.size());
            assertEquals("v1", map.get("k1"));
            assertEquals("v1", map.getRaw("k1"));
        }

        @Test
        @DisplayName("put replaces old value and returns previous value without changing size")
        void putReplacesExistingKey() {
            LRUClockMap<String, String> map = createMap(4);
            map.put("key", "val1");

            var prev1 = map.put("key", "val2");
            assertEquals("val1", prev1);
            assertEquals(1, map.size());
            assertEquals("val2", map.get("key"));

            var prev2 = map.put("key", "val3");
            assertEquals("val2", prev2);
            assertEquals(1, map.size());
            assertEquals("val3", map.get("key"));
        }

        @Test
        @DisplayName("get and getRaw return null for non-existing keys")
        void getReturnsNullForAbsentKey() {
            LRUClockMap<String, String> map = createMap(4);
            map.put("k1", "v1");

            assertNull(map.get("missing"));
            assertNull(map.getRaw("missing"));
        }

        @Test
        @DisplayName("remove removes mapping, returns previous value, and decrements size")
        void removeDeletesMapping() {
            LRUClockMap<String, String> map = createMap(4);
            map.put("k1", "v1");
            map.put("k2", "v2");
            assertEquals(2, map.size());

            var removed = map.remove("k1");

            assertEquals("v1", removed);
            assertEquals(1, map.size());
            assertNull(map.get("k1"));
            assertNull(map.getRaw("k1"));
            assertEquals("v2", map.get("k2"));
        }

        @Test
        @DisplayName("remove returns null and keeps size unchanged for non-existent key")
        void removeNonExistentKeyReturnsNull() {
            LRUClockMap<String, String> map = createMap(4);
            map.put("k1", "v1");

            var removed = map.remove("not_present");

            assertNull(removed);
            assertEquals(1, map.size());
            assertEquals("v1", map.get("k1"));
        }

        @Test
        @DisplayName("remove on already removed key returns null")
        void removeIsIdempotent() {
            LRUClockMap<String, String> map = createMap(4);
            map.put("k1", "v1");

            assertEquals("v1", map.remove("k1"));
            assertNull(map.remove("k1"));
            assertEquals(0, map.size());
        }

        @Test
        @DisplayName("clear empties the map and allows subsequent reuse")
        void clearEmptiesMapAndAllowsReuse() {
            LRUClockMap<String, String> map = createMap(4);
            map.put("k1", "v1");
            map.put("k2", "v2");
            map.put("k3", "v3");
            assertEquals(3, map.size());

            map.clear();

            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
            assertNull(map.get("k1"));
            assertNull(map.getRaw("k1"));

            // Re-populate after clear
            map.put("k4", "v4");
            map.put("k5", "v5");
            assertEquals(2, map.size());
            assertEquals("v4", map.get("k4"));
            assertEquals("v5", map.get("k5"));
        }

        @Test
        @DisplayName("clear on empty map is safe")
        void clearOnEmptyMapIsSafe() {
            LRUClockMap<String, String> map = createMap(4);
            assertDoesNotThrow(map::clear);
            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
        }
    }

    @Nested
    @DisplayName("3. Null Contract Tests")
    class NullContractTests {

        @Test
        @DisplayName("put must reject null key")
        void putRejectsNullKey() {
            LRUClockMap<String, String> map = createMap(4);
            var ex = assertThrows(RuntimeException.class, () -> map.put(null, "value"));
            assertTrue(ex instanceof NullPointerException || ex instanceof IllegalArgumentException,
                "Expected NullPointerException or IllegalArgumentException for null key, but got: " + ex.getClass());
        }

        @Test
        @DisplayName("put must reject null value")
        void putRejectsNullValue() {
            LRUClockMap<String, String> map = createMap(4);
            var ex = assertThrows(RuntimeException.class, () -> map.put("key", null));
            assertTrue(ex instanceof NullPointerException || ex instanceof IllegalArgumentException,
                "Expected NullPointerException or IllegalArgumentException for null value, but got: " + ex.getClass());
        }

        @Test
        @DisplayName("put must reject both null key and null value")
        void putRejectsBothNull() {
            LRUClockMap<String, String> map = createMap(4);
            var ex = assertThrows(RuntimeException.class, () -> map.put(null, null));
            assertTrue(ex instanceof NullPointerException || ex instanceof IllegalArgumentException,
                "Expected NullPointerException or IllegalArgumentException for null key and value, but got: " + ex.getClass());
        }

        @Test
        @DisplayName("get, getRaw, and remove with null key either reject null or return null safely")
        void getAndRemoveWithNullKeyHandledSafely() {
            LRUClockMap<String, String> map = createMap(4);
            map.put("validKey", "validValue");

            // get(null)
            try {
                var res = map.get(null);
                assertNull(res, "get(null) should return null if not throwing an exception");
            } catch (RuntimeException ex) {
                assertTrue(ex instanceof NullPointerException || ex instanceof IllegalArgumentException);
            }

            // getRaw(null)
            try {
                var res = map.getRaw(null);
                assertNull(res, "getRaw(null) should return null if not throwing an exception");
            } catch (RuntimeException ex) {
                assertTrue(ex instanceof NullPointerException || ex instanceof IllegalArgumentException);
            }

            // remove(null)
            try {
                var res = map.remove(null);
                assertNull(res, "remove(null) should return null if not throwing an exception");
            } catch (RuntimeException ex) {
                assertTrue(ex instanceof NullPointerException || ex instanceof IllegalArgumentException);
            }
        }
    }

    @Nested
    @DisplayName("4. Keys List Contract Tests")
    class KeysContractTests {

        @Test
        @DisplayName("keys returns empty list when map is empty")
        void keysReturnsEmptyListWhenEmpty() {
            LRUClockMap<String, String> map = createMap(4);
            var keys = map.keys();
            assertNotNull(keys);
            assertEquals(0, keys.size());
            assertTrue(keys.isEmpty());
        }

        @Test
        @DisplayName("keys returns all inserted keys matching map size")
        void keysReturnsAllPresentKeys() {
            LRUClockMap<String, String> map = createMap(4);
            map.put("a", "1");
            map.put("b", "2");
            map.put("c", "3");

            var keys = map.keys();
            assertEquals(3, keys.size());
            assertTrue(keys.containsAll(List.of("a", "b", "c")));
        }

        @Test
        @DisplayName("modifying returned keys list does not affect the map")
        void keysListIsDefensivelyCopiedOrUnmodifiable() {
            LRUClockMap<String, String> map = createMap(4);
            map.put("k1", "v1");
            map.put("k2", "v2");

            var keys = map.keys();
            try {
                keys.clear();
            } catch (UnsupportedOperationException ignored) {
                // List is unmodifiable, which is also a valid defensive design
            }

            assertEquals(2, map.size());
            assertEquals("v1", map.get("k1"));
            assertEquals("v2", map.get("k2"));
            assertEquals(2, map.keys().size());
        }

        @Test
        @DisplayName("keys does not duplicate updated keys")
        void keysDoesNotDuplicateOnKeyUpdate() {
            LRUClockMap<String, String> map = createMap(4);
            map.put("k1", "v1");
            map.put("k1", "v2");
            map.put("k1", "v3");

            var keys = map.keys();
            assertEquals(1, keys.size());
            assertEquals("k1", keys.getFirst());
        }

        @Test
        @DisplayName("keys does not contain removed keys")
        void keysOmitsRemovedKeys() {
            LRUClockMap<String, String> map = createMap(4);
            map.put("k1", "v1");
            map.put("k2", "v2");
            map.remove("k1");

            var keys = map.keys();
            assertEquals(1, keys.size());
            assertEquals("k2", keys.getFirst());
            assertFalse(keys.contains("k1"));
        }
    }

    @Nested
    @DisplayName("5. Eviction and Clock (Second-Chance) Algorithm Tests")
    class ClockEvictionTests {

        @Test
        @DisplayName("Map never exceeds maxSize during insertions")
        void mapNeverExceedsMaxSize() {
            final int maxSize = 4;
            LRUClockMap<String, String> map = createMap(maxSize);

            for (var i = 0; i < 20; i++) {
                map.put("key" + i, "val" + i);
                assertTrue(map.size() <= maxSize, "Size " + map.size() + " exceeded maxSize " + maxSize);
            }
            assertEquals(maxSize, map.size());
        }

        @Test
        @DisplayName("Inserting into a full map evicts exactly one key")
        void insertingIntoFullMapEvictsOneKey() {
            final int maxSize = 4;
            LRUClockMap<String, String> map = createMap(maxSize);

            map.put("k1", "v1");
            map.put("k2", "v2");
            map.put("k3", "v3");
            map.put("k4", "v4");
            assertEquals(maxSize, map.size());

            // Inserting 5th key triggers eviction
            map.put("k5", "v5");

            assertEquals(maxSize, map.size());
            assertEquals("v5", map.get("k5"), "Newly added key must be present in map");

            // Verify exactly one of the previous keys was evicted
            var keys = map.keys();
            var remainingOriginalKeys = new HashSet<>(keys);
            remainingOriginalKeys.remove("k5");
            assertEquals(maxSize - 1, remainingOriginalKeys.size(), "Exactly one key should have been evicted");
        }

        @Test
        @DisplayName("Updating existing key when full does not trigger eviction")
        void updatingExistingKeyWhenFullDoesNotEvict() {
            final int maxSize = 4;
            LRUClockMap<String, String> map = createMap(maxSize);

            map.put("k1", "v1");
            map.put("k2", "v2");
            map.put("k3", "v3");
            map.put("k4", "v4");

            // Update k2
            var prev = map.put("k2", "v2_updated");
            assertEquals("v2", prev);
            assertEquals(maxSize, map.size());

            // All keys must still be present
            assertAll(
                () -> assertEquals("v1", map.get("k1")),
                () -> assertEquals("v2_updated", map.get("k2")),
                () -> assertEquals("v3", map.get("k3")),
                () -> assertEquals("v4", map.get("k4"))
            );
        }

        @Test
        @DisplayName("Removing a key from a full map frees a slot so subsequent insert does not evict")
        void removingFreesSlotPreventingEviction() {
            final int maxSize = 4;
            LRUClockMap<String, String> map = createMap(maxSize);

            map.put("k1", "v1");
            map.put("k2", "v2");
            map.put("k3", "v3");
            map.put("k4", "v4");

            map.remove("k2");
            assertEquals(3, map.size());

            // Now insert k5
            map.put("k5", "v5");
            assertEquals(maxSize, map.size());

            // k1, k3, k4 and k5 must all be present
            assertAll(
                () -> assertEquals("v1", map.get("k1")),
                () -> assertNull(map.get("k2")),
                () -> assertEquals("v3", map.get("k3")),
                () -> assertEquals("v4", map.get("k4")),
                () -> assertEquals("v5", map.get("k5"))
            );
        }

        @Test
        @DisplayName("get grants second chance protecting key from immediate eviction compared to getRaw")
        void getProtectsKeyViaSecondChanceWhereasGetRawDoesNot() {
            final int maxSize = 4;

            // Run Scenario A: k1 is touched via get (giving it a second chance)
            LRUClockMap<String, String> mapA = createMap(maxSize);
            mapA.put("k1", "v1");
            mapA.put("k2", "v2");
            mapA.put("k3", "v3");
            mapA.put("k4", "v4");
            // get(k1) sets second chance flag = true
            assertEquals("v1", mapA.get("k1"));

            // Run Scenario B: k1 is touched via getRaw (leaving second chance untouched)
            LRUClockMap<String, String> mapB = createMap(maxSize);
            mapB.put("k1", "v1");
            mapB.put("k2", "v2");
            mapB.put("k3", "v3");
            mapB.put("k4", "v4");
            // getRaw(k1) does NOT set second chance flag
            assertEquals("v1", mapB.getRaw("k1"));

            // Insert 5th key into both maps
            mapA.put("k5", "v5");
            mapB.put("k5", "v5");

            // In Map A, k1 had second-chance set. In standard clock replacement,
            // an entry with second-chance true is spared on the first pass.
            // If k1 was the candidate in map B and was evicted, in map A it must have survived!
            boolean k1InA = mapA.getRaw("k1") != null;
            boolean k1InB = mapB.getRaw("k1") != null;

            // Either k1 survived in A where it was evicted in B, or the evicted key set differs
            // because of k1's second chance.
            if (!k1InB) {
                assertTrue(k1InA, "k1 was evicted in mapB (getRaw) but should have survived in mapA (get second-chance)");
            } else {
                // If k1 was not the first candidate, test subsequent inserts to see second-chance effect
                mapA.put("k6", "v6");
                mapB.put("k6", "v6");
                boolean k1InAAfter6 = mapA.getRaw("k1") != null;
                boolean k1InBAfter6 = mapB.getRaw("k1") != null;
                if (!k1InBAfter6) {
                    assertTrue(k1InAAfter6, "k1 should survive longer with get second chance than with getRaw");
                }
            }
        }

        @Test
        @DisplayName("Clock eviction successfully evicts when all entries have second chance set")
        void clockEvictionSucceedsWhenAllEntriesHaveSecondChance() {
            final int maxSize = 4;
            LRUClockMap<String, String> map = createMap(maxSize);

            map.put("k1", "v1");
            map.put("k2", "v2");
            map.put("k3", "v3");
            map.put("k4", "v4");

            // Touch ALL keys with get() so all second chance flags are true
            assertEquals("v1", map.get("k1"));
            assertEquals("v2", map.get("k2"));
            assertEquals("v3", map.get("k3"));
            assertEquals("v4", map.get("k4"));

            // Clock hand must complete a full cycle, reset flags to false, and evict one entry
            assertDoesNotThrow(() -> map.put("k5", "v5"));

            assertEquals(maxSize, map.size());
            assertEquals("v5", map.get("k5"));
            assertEquals(maxSize, map.keys().size());
        }

        @Test
        @DisplayName("Calling keys() does not grant second chance flags")
        void keysCallDoesNotAffectClockFlags() {
            final int maxSize = 4;
            LRUClockMap<String, String> mapWithoutKeysCall = createMap(maxSize);
            LRUClockMap<String, String> mapWithKeysCall = createMap(maxSize);

            for (var i = 1; i <= 4; i++) {
                mapWithoutKeysCall.put("k" + i, "v" + i);
                mapWithKeysCall.put("k" + i, "v" + i);
            }

            // Call keys() multiple times on second map
            mapWithKeysCall.keys();
            mapWithKeysCall.keys();

            // Insert 5th key into both maps
            mapWithoutKeysCall.put("k5", "v5");
            mapWithKeysCall.put("k5", "v5");

            // Eviction behavior should be identical between both maps
            assertEquals(new HashSet<>(mapWithoutKeysCall.keys()), new HashSet<>(mapWithKeysCall.keys()),
                "Calling keys() should not alter the second-chance eviction outcome");
        }
    }

    @Nested
    @DisplayName("6. Open Addressing and Collision Tests")
    class CollisionTests {

        @Test
        @DisplayName("Multiple keys with identical hash codes are stored and retrieved correctly")
        void collidingKeysAreRetrievedCorrectly() {
            final int maxSize = 4;
            LRUClockMap<CollidingKey, String> map = createMap(maxSize);

            var k1 = new CollidingKey("key_1", 42);
            var k2 = new CollidingKey("key_2", 42);
            var k3 = new CollidingKey("key_3", 42);

            map.put(k1, "val1");
            map.put(k2, "val2");
            map.put(k3, "val3");

            assertEquals(3, map.size());
            assertEquals("val1", map.get(k1));
            assertEquals("val2", map.get(k2));
            assertEquals("val3", map.get(k3));
            assertEquals("val1", map.getRaw(k1));
            assertEquals("val2", map.getRaw(k2));
            assertEquals("val3", map.getRaw(k3));
        }

        @Test
        @DisplayName("Updating a colliding key updates correct entry without changing size")
        void updatingCollidingKeyAffectsOnlyTarget() {
            final int maxSize = 4;
            LRUClockMap<CollidingKey, String> map = createMap(maxSize);

            var k1 = new CollidingKey("key_1", 99);
            var k2 = new CollidingKey("key_2", 99);

            map.put(k1, "v1");
            map.put(k2, "v2");

            var prev = map.put(k1, "v1_updated");
            assertEquals("v1", prev);
            assertEquals(2, map.size());
            assertEquals("v1_updated", map.get(k1));
            assertEquals("v2", map.get(k2));
        }

        @Test
        @DisplayName("Removing from middle of probe collision chain maintains probe integrity")
        void removalInCollisionChainPreservesSubsequentKeys() {
            final int maxSize = 4;
            LRUClockMap<CollidingKey, String> map = createMap(maxSize);

            var k1 = new CollidingKey("first", 100);
            var k2 = new CollidingKey("second", 100);
            var k3 = new CollidingKey("third", 100);

            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            // Remove middle item
            var removed = map.remove(k2);
            assertEquals("v2", removed);
            assertEquals(2, map.size());

            // k1 and k3 must still be found (tests tombstone or shift rehash)
            assertEquals("v1", map.get(k1));
            assertEquals("v3", map.get(k3));
            assertEquals("v1", map.getRaw(k1));
            assertEquals("v3", map.getRaw(k3));
            assertNull(map.get(k2));
        }

        @Test
        @DisplayName("Removing first item of probe collision chain preserves subsequent keys")
        void removalOfFirstItemInCollisionChainPreservesSubsequentKeys() {
            final int maxSize = 4;
            LRUClockMap<CollidingKey, String> map = createMap(maxSize);

            var k1 = new CollidingKey("head", 777);
            var k2 = new CollidingKey("middle", 777);
            var k3 = new CollidingKey("tail", 777);

            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            // Remove head of collision probe chain
            assertEquals("v1", map.remove(k1));
            assertEquals(2, map.size());

            assertEquals("v2", map.get(k2));
            assertEquals("v3", map.get(k3));
            assertNull(map.get(k1));
        }

        @Test
        @DisplayName("Reinserting removed key into collision slot functions correctly")
        void reinsertIntoCollisionSlot() {
            final int maxSize = 4;
            LRUClockMap<CollidingKey, String> map = createMap(maxSize);

            var k1 = new CollidingKey("c1", 55);
            var k2 = new CollidingKey("c2", 55);

            map.put(k1, "v1");
            map.put(k2, "v2");
            map.remove(k1);

            // Re-insert k1 with new value
            assertNull(map.put(k1, "v1_new"));
            assertEquals(2, map.size());
            assertEquals("v1_new", map.get(k1));
            assertEquals("v2", map.get(k2));
        }
    }

    @Nested
    @DisplayName("7. Stress, Churn, and Pattern Matching Tests")
    class StressAndPatternMatchingTests {

        private void executeOperation(LRUClockMap<String, String> map, MapOp op) {
            switch (op) {
                case MapOp.Put(var k, var v) -> map.put(k, v);
                case MapOp.Get(var k) -> map.get(k);
                case MapOp.GetRaw(var k) -> map.getRaw(k);
                case MapOp.Remove(var k) -> map.remove(k);
            }
        }

        @Test
        @DisplayName("Heavy churn preserves size invariant and avoids infinite loops")
        void heavyChurnPreservesSizeInvariant() {
            final int maxSize = 16;
            LRUClockMap<String, String> map = createMap(maxSize);
            var rng = new Random(42);

            var keyPool = new ArrayList<String>();
            for (var i = 0; i < 50; i++) {
                keyPool.add("key_" + i);
            }

            for (var step = 0; step < 2_000; step++) {
                var key = keyPool.get(rng.nextInt(keyPool.size()));
                var opChoice = rng.nextInt(4);

                MapOp op = switch (opChoice) {
                    case 0 -> new MapOp.Put(key, "val_" + step);
                    case 1 -> new MapOp.Get(key);
                    case 2 -> new MapOp.GetRaw(key);
                    default -> new MapOp.Remove(key);
                };

                executeOperation(map, op);

                assertTrue(map.size() <= maxSize, "Size must never exceed maxSize: " + map.size());
                assertEquals(map.size(), map.keys().size(), "keys().size() must match size()");
            }
        }

        @Test
        @DisplayName("Large sequential put stream always keeps size at maxSize")
        void sequentialPutStreamMaintainsMaxSize() {
            final int maxSize = 8;
            LRUClockMap<String, String> map = createMap(maxSize);

            for (var i = 0; i < 500; i++) {
                map.put("stream_key_" + i, "stream_val_" + i);
                if (i < maxSize) {
                    assertEquals(i + 1, map.size());
                } else {
                    assertEquals(maxSize, map.size());
                }
                assertEquals("stream_val_" + i, map.get("stream_key_" + i));
            }

            assertEquals(maxSize, map.size());
            assertEquals(maxSize, map.keys().size());
        }
    }
}
