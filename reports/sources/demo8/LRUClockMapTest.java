package org.jugsaxony.clock.demo8;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive unit test suite for LRUClockMap implementations.
 * Uses JUnit 5 exclusively and leverages Java 21 features.
 */
@DisplayName("LRUClockMap Unit Tests")
class LRUClockMapTest {

    private <K, V> LRUClockMap<K, V> createMap(int maxSize) {
        return LRUClockMapFactory.create(maxSize);
    }

    /**
     * Record representing a custom key with controllable hash code for testing open addressing collisions.
     */
    record CollidingKey(String id, int forcedHash) {
        @Override
        public int hashCode() {
            return forcedHash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CollidingKey other)) return false;
            return Objects.equals(this.id, other.id);
        }

        @Override
        public String toString() {
            return "CollidingKey[id=" + id + ", hash=" + forcedHash + "]";
        }
    }

    /**
     * Record for tracking operations in parameterized tests.
     */
    record MapEntryRecord<K, V>(K key, V value) {}

    @Nested
    @DisplayName("1. Initialization and Validation")
    class InitializationTests {

        @ParameterizedTest(name = "maxSize = {0} should throw IllegalArgumentException")
        @ValueSource(ints = {Integer.MIN_VALUE, -100, -1, 0, 1, 2, 3})
        @DisplayName("Should reject maxSize less than 4")
        void shouldRejectInvalidMaxSize(int invalidSize) {
            assertThrows(IllegalArgumentException.class, () -> createMap(invalidSize),
                    "Expected IllegalArgumentException for maxSize < 4, but got none for size: " + invalidSize);
        }

        @ParameterizedTest(name = "maxSize = {0} should initialize successfully")
        @ValueSource(ints = {4, 5, 8, 16, 32, 64, 100, 1024})
        @DisplayName("Should accept valid maxSize (>= 4)")
        void shouldAcceptValidMaxSize(int validSize) {
            var map = createMap(validSize);
            assertNotNull(map);
            assertEquals(0, map.size());
            assertNotNull(map.keys());
            assertTrue(map.keys().isEmpty());
        }
    }

    @Nested
    @DisplayName("2. Basic Map Operations (Put, Get, GetRaw, Remove, Size, Clear)")
    class BasicOperationsTests {

        @Test
        @DisplayName("put() new entries should increment size and return null")
        void putNewEntriesIncrementsSizeAndReturnsNull() {
            var map = createMap(4);

            assertNull(map.put("A", 1));
            assertEquals(1, map.size());
            assertEquals(1, map.get("A"));
            assertEquals(1, map.getRaw("A"));

            assertNull(map.put("B", 2));
            assertEquals(2, map.size());
            assertEquals(2, map.get("B"));
            assertEquals(2, map.getRaw("B"));
        }

        @Test
        @DisplayName("put() existing key should update value and return previous value without changing size")
        void putExistingKeyUpdatesValueAndKeepsSize() {
            var map = createMap(4);

            assertNull(map.put("key1", "val1"));
            assertEquals(1, map.size());

            var previous = map.put("key1", "val2");
            assertEquals("val1", previous);
            assertEquals(1, map.size());
            assertEquals("val2", map.get("key1"));
            assertEquals("val2", map.getRaw("key1"));

            var previous2 = map.put("key1", "val3");
            assertEquals("val2", previous2);
            assertEquals(1, map.size());
            assertEquals("val3", map.get("key1"));
        }

        @Test
        @DisplayName("get() and getRaw() on non-existent keys should return null")
        void getNonExistentKeyReturnsNull() {
            var map = createMap(4);

            assertNull(map.get("missing"));
            assertNull(map.getRaw("missing"));

            map.put("present", 100);
            assertNull(map.get("other"));
            assertNull(map.getRaw("other"));
        }

        @Test
        @DisplayName("remove() existing key should return previous value and decrement size")
        void removeExistingKeyDecrementsSizeAndReturnsValue() {
            var map = createMap(4);

            map.put("k1", 10);
            map.put("k2", 20);
            map.put("k3", 30);
            assertEquals(3, map.size());

            var removed = map.remove("k2");
            assertEquals(20, removed);
            assertEquals(2, map.size());
            assertNull(map.get("k2"));
            assertNull(map.getRaw("k2"));
            assertFalse(map.keys().contains("k2"));

            // Other keys should still exist
            assertEquals(10, map.get("k1"));
            assertEquals(30, map.get("k3"));
        }

        @Test
        @DisplayName("remove() non-existent key should return null and not change size")
        void removeNonExistentKeyReturnsNullAndLeavesSizeUnchanged() {
            var map = createMap(4);

            assertNull(map.remove("absent"));
            assertEquals(0, map.size());

            map.put("k1", 1);
            assertEquals(1, map.size());
            assertNull(map.remove("k2"));
            assertEquals(1, map.size());
            assertEquals(1, map.get("k1"));
        }

        @Test
        @DisplayName("remove() and re-inserting same key should work properly")
        void removeAndReinsertKey() {
            var map = createMap(4);

            map.put("X", 100);
            assertEquals(100, map.remove("X"));
            assertNull(map.get("X"));
            assertEquals(0, map.size());

            assertNull(map.put("X", 200));
            assertEquals(1, map.size());
            assertEquals(200, map.get("X"));
            assertEquals(200, map.getRaw("X"));
        }

        @Test
        @DisplayName("clear() should remove all entries and reset size to 0")
        void clearRemovesAllEntriesAndResetsSize() {
            var map = createMap(4);

            map.put("k1", "v1");
            map.put("k2", "v2");
            map.put("k3", "v3");
            assertEquals(3, map.size());

            map.clear();
            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
            assertNull(map.get("k1"));
            assertNull(map.getRaw("k1"));
            assertNull(map.get("k2"));
            assertNull(map.get("k3"));

            // Reusing map after clear
            assertNull(map.put("newKey", "newValue"));
            assertEquals(1, map.size());
            assertEquals("newValue", map.get("newKey"));
        }

        @Test
        @DisplayName("clear() on empty map should be a safe no-op")
        void clearOnEmptyMap() {
            var map = createMap(4);
            assertDoesNotThrow(map::clear);
            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
        }
    }

    @Nested
    @DisplayName("3. keys() Method Invariants")
    class KeysMethodTests {

        @Test
        @DisplayName("keys() on empty map should return empty list")
        void keysOnEmptyMapReturnsEmptyList() {
            var map = createMap(4);
            var keys = map.keys();
            assertNotNull(keys);
            assertTrue(keys.isEmpty());
        }

        @Test
        @DisplayName("keys() should return exactly size() elements matching active keys")
        void keysReturnsAllActiveKeys() {
            LRUClockMap<String, Integer> map = createMap(5);
            var expectedKeys = List.of("A", "B", "C", "D");

            for (var k : expectedKeys) {
                map.put(k, k.hashCode());
            }

            var actualKeys = map.keys();
            assertEquals(4, actualKeys.size());
            assertEquals(expectedKeys.size(), actualKeys.size());

            Set<String> actualSet = new HashSet<>(actualKeys);
            assertEquals(new HashSet<>(expectedKeys), actualSet);
        }

        @Test
        @DisplayName("Modifying returned keys list should not affect internal map state")
        void modifyingKeysListDoesNotMutateMap() {
            var map = createMap(4);
            map.put("A", 1);
            map.put("B", 2);

            var keys = map.keys();
            try {
                keys.clear();
            } catch (UnsupportedOperationException ignored) {
                // If keys() returns an unmodifiable list, that also satisfies encapsulation
            }

            // Map must be intact regardless
            assertEquals(2, map.size());
            assertEquals(1, map.get("A"));
            assertEquals(2, map.get("B"));
        }
    }

    @Nested
    @DisplayName("4. Null Handling and Restrictions")
    class NullHandlingTests {

        @Test
        @DisplayName("put() with null key should be rejected or throw exception")
        void putWithNullKeyThrowsException() {
            var map = createMap(4);
            assertThrows(Exception.class, () -> map.put(null, "value"),
                    "Map specifies no support for null keys; put(null, value) must throw an exception");
        }

        @Test
        @DisplayName("put() with null value should be rejected or throw exception")
        void putWithNullValueThrowsException() {
            var map = createMap(4);
            assertThrows(Exception.class, () -> map.put("key", null),
                    "Map specifies no support for null values; put(key, null) must throw an exception");
        }

        @Test
        @DisplayName("get() and getRaw() with null key should safely handle or reject null")
        void getWithNullKey() {
            var map = createMap(4);
            map.put("key", 1);
            try {
                var res = map.get(null);
                assertNull(res);
            } catch (Exception e) {
                // Throwing NPE or IAE on null key lookup is also acceptable for null-disallowing maps
                assertTrue(e instanceof NullPointerException || e instanceof IllegalArgumentException);
            }

            try {
                var resRaw = map.getRaw(null);
                assertNull(resRaw);
            } catch (Exception e) {
                assertTrue(e instanceof NullPointerException || e instanceof IllegalArgumentException);
            }
        }

        @Test
        @DisplayName("remove() with null key should safely handle or reject null")
        void removeWithNullKey() {
            var map = createMap(4);
            map.put("key", 1);
            try {
                var res = map.remove(null);
                assertNull(res);
            } catch (Exception e) {
                assertTrue(e instanceof NullPointerException || e instanceof IllegalArgumentException);
            }
        }
    }

    @Nested
    @DisplayName("5. Open Addressing & Hash Collision Handling")
    class CollisionAndOpenAddressingTests {

        @Test
        @DisplayName("Should handle multiple colliding keys with same hashCode")
        void handleCollidingKeys() {
            var map = createMap(8);
            int fixedHash = 42;

            var k1 = new CollidingKey("c1", fixedHash);
            var k2 = new CollidingKey("c2", fixedHash);
            var k3 = new CollidingKey("c3", fixedHash);
            var k4 = new CollidingKey("c4", fixedHash);

            assertNull(map.put(k1, "v1"));
            assertNull(map.put(k2, "v2"));
            assertNull(map.put(k3, "v3"));
            assertNull(map.put(k4, "v4"));

            assertEquals(4, map.size());

            assertEquals("v1", map.get(k1));
            assertEquals("v2", map.get(k2));
            assertEquals("v3", map.get(k3));
            assertEquals("v4", map.get(k4));

            assertEquals("v1", map.getRaw(k1));
            assertEquals("v2", map.getRaw(k2));
            assertEquals("v3", map.getRaw(k3));
            assertEquals("v4", map.getRaw(k4));
        }

        @Test
        @DisplayName("Open addressing lookup must continue after middle colliding key is removed")
        void removeMiddleCollidingKeyPreservesRemainingLookup() {
            var map = createMap(8);
            int fixedHash = 100;

            var k1 = new CollidingKey("key-1", fixedHash);
            var k2 = new CollidingKey("key-2", fixedHash);
            var k3 = new CollidingKey("key-3", fixedHash);

            map.put(k1, "first");
            map.put(k2, "second");
            map.put(k3, "third");

            // Remove middle item
            assertEquals("second", map.remove(k2));
            assertEquals(2, map.size());

            // First and third must still be reachable
            assertEquals("first", map.get(k1));
            assertEquals("third", map.get(k3));
            assertEquals("first", map.getRaw(k1));
            assertEquals("third", map.getRaw(k3));
            assertNull(map.get(k2));
            assertNull(map.getRaw(k2));
        }

        @Test
        @DisplayName("Re-inserting into removed collision slot should reuse slot and preserve chain")
        void reinsertIntoRemovedSlotPreservesChain() {
            var map = createMap(8);
            int fixedHash = 77;

            var k1 = new CollidingKey("k1", fixedHash);
            var k2 = new CollidingKey("k2", fixedHash);
            var k3 = new CollidingKey("k3", fixedHash);

            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            map.remove(k2);

            var k4 = new CollidingKey("k4", fixedHash);
            map.put(k4, "v4");

            assertEquals(3, map.size());
            assertEquals("v1", map.get(k1));
            assertNull(map.get(k2));
            assertEquals("v3", map.get(k3));
            assertEquals("v4", map.get(k4));
        }
    }

    @Nested
    @DisplayName("6. Clock (Second-Chance) LRU Eviction Logic")
    class ClockLRUEvictionTests {

        @Test
        @DisplayName("Inserting up to maxSize elements must not evict any element")
        void upToMaxSizeNoEviction() {
            int maxSize = 4;
            var map = createMap(maxSize);

            for (int i = 1; i <= maxSize; i++) {
                assertNull(map.put("key" + i, "val" + i));
                assertEquals(i, map.size());
            }

            for (int i = 1; i <= maxSize; i++) {
                assertEquals("val" + i, map.getRaw("key" + i));
            }
        }

        @Test
        @DisplayName("Inserting (maxSize + 1)-th distinct key must trigger eviction and keep size == maxSize")
        void insertingBeyondMaxSizeEvictsAndMaintainsMaxSize() {
            int maxSize = 4;
            var map = createMap(maxSize);

            for (int i = 1; i <= maxSize; i++) {
                map.put("k" + i, i);
            }
            assertEquals(maxSize, map.size());

            // 5th key should trigger eviction
            map.put("k5", 5);
            assertEquals(maxSize, map.size(), "Size must not exceed maxSize after eviction");

            // Exactly 4 keys should be present in map
            int presentCount = 0;
            for (int i = 1; i <= 5; i++) {
                if (map.getRaw("k" + i) != null) {
                    presentCount++;
                }
            }
            assertEquals(maxSize, presentCount);
            assertEquals(maxSize, map.keys().size());
            assertEquals(5, map.get("k5"), "The newly inserted key must be present in the map");
        }

        @Test
        @DisplayName("Updating an existing key at capacity must NOT trigger eviction")
        void updatingExistingKeyAtCapacityDoesNotEvict() {
            int maxSize = 4;
            var map = createMap(maxSize);

            for (int i = 1; i <= maxSize; i++) {
                map.put("k" + i, i * 10);
            }
            assertEquals(maxSize, map.size());

            // Update existing key
            var old = map.put("k2", 999);
            assertEquals(20, old);
            assertEquals(maxSize, map.size());

            // All keys k1..k4 must still exist
            for (int i = 1; i <= maxSize; i++) {
                assertNotNull(map.getRaw("k" + i), "Key k" + i + " must not have been evicted");
            }
            assertEquals(999, map.get("k2"));
        }

        @Test
        @DisplayName("get() sets second-chance flag whereas getRaw() does not")
        void getSetsSecondChanceWhereasGetRawDoesNot() {
            int maxSize = 4;
            var map = createMap(maxSize);

            // Fill map with 4 entries
            map.put("A", 1);
            map.put("B", 2);
            map.put("C", 3);
            map.put("D", 4);

            // Access B and D with get() to grant/refresh second chance
            assertEquals(2, map.get("B"));
            assertEquals(4, map.get("D"));

            // Access A and C only with getRaw() so their second chance is NOT refreshed
            assertEquals(1, map.getRaw("A"));
            assertEquals(3, map.getRaw("C"));

            // Insert new key E; clock should evict an entry that lacks second chance
            map.put("E", 5);
            assertEquals(maxSize, map.size());
            assertEquals(5, map.get("E"));

            // B and D were accessed via get() so they should be more protected than A or C
            // Either A or C must have been evicted, but B and D should likely survive the single sweep
            boolean bPresent = map.getRaw("B") != null;
            boolean dPresent = map.getRaw("D") != null;
            assertTrue(bPresent || dPresent, "Keys accessed via get() should have second chance protection");
        }

        @Test
        @DisplayName("Clock eviction sweeps continuously and evicts when all entries initially have second chance")
        void clockEvictionWhenAllHaveSecondChance() {
            int maxSize = 4;
            var map = createMap(maxSize);

            map.put("A", 1);
            map.put("B", 2);
            map.put("C", 3);
            map.put("D", 4);

            // Give all keys second chance explicitly
            map.get("A");
            map.get("B");
            map.get("C");
            map.get("D");

            // Adding 5th key must succeed and evict exactly 1 key
            map.put("E", 5);
            assertEquals(4, map.size());
            assertEquals(5, map.get("E"));

            // Adding 6th key
            map.put("F", 6);
            assertEquals(4, map.size());
            assertEquals(6, map.get("F"));
        }

        @Test
        @DisplayName("Repeated evictions over heavy churn maintain consistent state")
        void heavyChurnEvictionMaintainsIntegrity() {
            int maxSize = 8;
            var map = createMap(maxSize);

            int totalInsertions = 200;
            for (int i = 0; i < totalInsertions; i++) {
                map.put("key_" + i, i);
                assertTrue(map.size() <= maxSize, "Map size must never exceed maxSize: " + maxSize);

                // Occasionally read with get or getRaw
                if (i % 3 == 0) {
                    map.get("key_" + i);
                } else if (i % 3 == 1) {
                    map.getRaw("key_" + i);
                }
            }

            assertEquals(maxSize, map.size());
            var keys = map.keys();
            assertEquals(maxSize, keys.size());

            // Every key returned by keys() must return valid non-null value
            for (var key : keys) {
                var val = map.getRaw(key);
                assertNotNull(val, "Key " + key + " from keys() must exist in map");
            }
        }
    }

    @Nested
    @DisplayName("7. Complex Lifecycle and Invariant Stress Tests")
    class LifecycleAndStressTests {

        @Test
        @DisplayName("Mixed operations (put, get, getRaw, remove, clear) maintain consistent map invariants")
        void mixedOperationsConsistency() {
            int maxSize = 16;
            var map = createMap(maxSize);
            var random = new Random(42);

            int iterations = 1000;
            for (int i = 0; i < iterations; i++) {
                int op = random.nextInt(6);
                int keyNum = random.nextInt(50);
                String key = "k" + keyNum;
                int val = keyNum * 10;

                switch (op) {
                    case 0, 1 -> { // Put
                        map.put(key, val);
                        assertTrue(map.size() <= maxSize);
                    }
                    case 2 -> { // Get
                        var v = map.get(key);
                        if (v != null) {
                            assertEquals(val, v);
                        }
                    }
                    case 3 -> { // GetRaw
                        var v = map.getRaw(key);
                        if (v != null) {
                            assertEquals(val, v);
                        }
                    }
                    case 4 -> { // Remove
                        map.remove(key);
                        assertNull(map.getRaw(key));
                    }
                    case 5 -> { // Verify keys
                        var keys = map.keys();
                        assertEquals(map.size(), keys.size());
                        for (var k : keys) {
                            assertNotNull(map.getRaw(k));
                        }
                    }
                }
            }

            // Verify final state invariants
            assertTrue(map.size() <= maxSize);
            assertEquals(map.size(), map.keys().size());
            for (var k : map.keys()) {
                assertNotNull(map.getRaw(k));
            }
        }

        @Test
        @DisplayName("Map of minimal capacity (maxSize = 4) behaves correctly under frequent eviction")
        void minimalCapacityFourBehavior() {
            int maxSize = 4;
            var map = createMap(maxSize);

            // Put 4 elements
            map.put("1", 1);
            map.put("2", 2);
            map.put("3", 3);
            map.put("4", 4);
            assertEquals(4, map.size());

            // Remove 1 element
            assertEquals(2, map.remove("2"));
            assertEquals(3, map.size());

            // Put 2 new elements (1 fill + 1 eviction)
            map.put("5", 5);
            assertEquals(4, map.size());
            map.put("6", 6);
            assertEquals(4, map.size());

            assertEquals(6, map.get("6"));
            assertEquals(5, map.get("5"));
        }

        @Test
        @DisplayName("Reusing map after clear with full capacity refills")
        void clearAndRefillMultipleTimes() {
            int maxSize = 6;
            var map = createMap(maxSize);

            for (int cycle = 0; cycle < 5; cycle++) {
                for (int i = 0; i < maxSize; i++) {
                    map.put("c" + cycle + "_k" + i, i);
                }
                assertEquals(maxSize, map.size());

                for (int i = 0; i < maxSize; i++) {
                    assertEquals(i, map.getRaw("c" + cycle + "_k" + i));
                }

                map.clear();
                assertEquals(0, map.size());
                assertTrue(map.keys().isEmpty());
            }
        }
    }
}
