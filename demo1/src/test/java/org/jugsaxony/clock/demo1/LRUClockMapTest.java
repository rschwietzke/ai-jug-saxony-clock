package org.jugsaxony.clock.demo1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive JUnit 5 unit test suite for {@link LRUClockMap}.
 * Tested strictly according to the interface contract and specification using Java 21 features.
 */
@DisplayName("LRUClockMap Test Suite")
class LRUClockMapTest
{
    /**
     * Factory helper to instantiate a map under test.
     */
    private <K, V> LRUClockMap<K, V> createMap(final int maxSize)
    {
        return LRUClockMap.of(maxSize);
    }

    // =========================================================================
    // 1. Instantiation and Size Constraint Tests
    // =========================================================================
    @Nested
    @DisplayName("Instantiation and Size Constraints")
    class InstantiationTests
    {
        @ParameterizedTest(name = "Valid maxSize = {0}")
        @ValueSource(ints = {4, 5, 8, 16, 50, 100, 1024})
        @DisplayName("Creating map with maxSize >= 4 succeeds")
        void testValidMaxSize(final int maxSize)
        {
            var map = createMap(maxSize);
            assertNotNull(map, "Map instance must not be null");
            assertEquals(0, map.size(), "Initial map size must be 0");
            assertNotNull(map.keys(), "Initial keys list must not be null");
            assertTrue(map.keys().isEmpty(), "Initial keys list must be empty");
        }

        @ParameterizedTest(name = "Invalid maxSize = {0}")
        @ValueSource(ints = {3, 2, 1, 0, -1, -10, -100, Integer.MIN_VALUE})
        @DisplayName("Creating map with maxSize < 4 throws IllegalArgumentException")
        void testInvalidMaxSizeThrowsException(final int invalidSize)
        {
            var exception = assertThrows(IllegalArgumentException.class, () -> createMap(invalidSize),
                () -> "Expected IllegalArgumentException for maxSize: " + invalidSize);
            assertNotNull(exception);
        }
    }

    // =========================================================================
    // 2. Null Contract Tests
    // =========================================================================
    @Nested
    @DisplayName("Null Contract (No null keys or values supported)")
    class NullContractTests
    {
        private LRUClockMap<String, String> map;

        @BeforeEach
        void setUp()
        {
            map = createMap(4);
        }

        @Test
        @DisplayName("put(null, value) throws NullPointerException")
        void testPutNullKeyThrowsNPE()
        {
            assertThrows(NullPointerException.class, () -> map.put(null, "value"));
        }

        @Test
        @DisplayName("put(key, null) throws NullPointerException")
        void testPutNullValueThrowsNPE()
        {
            assertThrows(NullPointerException.class, () -> map.put("key", null));
        }

        @Test
        @DisplayName("put(null, null) throws NullPointerException")
        void testPutNullKeyAndNullValueThrowsNPE()
        {
            assertThrows(NullPointerException.class, () -> map.put(null, null));
        }

        @Test
        @DisplayName("get(null) throws NullPointerException")
        void testGetNullKeyThrowsNPE()
        {
            assertThrows(NullPointerException.class, () -> map.get(null));
        }

        @Test
        @DisplayName("getRaw(null) throws NullPointerException")
        void testGetRawNullKeyThrowsNPE()
        {
            assertThrows(NullPointerException.class, () -> map.getRaw(null));
        }

        @Test
        @DisplayName("remove(null) throws NullPointerException")
        void testRemoveNullKeyThrowsNPE()
        {
            assertThrows(NullPointerException.class, () -> map.remove(null));
        }
    }

    // =========================================================================
    // 3. Basic Map Operations Tests
    // =========================================================================
    @Nested
    @DisplayName("Basic Map Operations (put, get, getRaw, remove, size, clear, keys)")
    class BasicOperationsTests
    {
        private LRUClockMap<String, String> map;

        @BeforeEach
        void setUp()
        {
            map = createMap(4);
        }

        @Test
        @DisplayName("Initial empty map state")
        void testInitialEmptyState()
        {
            assertAll("Initial empty map invariants",
                () -> assertEquals(0, map.size()),
                () -> assertNotNull(map.keys()),
                () -> assertTrue(map.keys().isEmpty()),
                () -> assertNull(map.get("absent")),
                () -> assertNull(map.getRaw("absent")),
                () -> assertNull(map.remove("absent"))
            );
        }

        @Test
        @DisplayName("put returns null for new key and stores value")
        void testPutNewKey()
        {
            var prev = map.put("k1", "v1");
            assertNull(prev, "Previous value for new key must be null");
            assertEquals(1, map.size());
            assertEquals("v1", map.get("k1"));
            assertEquals("v1", map.getRaw("k1"));
        }

        @Test
        @DisplayName("put returns previous value when replacing existing key")
        void testPutOverwriteExistingKey()
        {
            map.put("k1", "v1");
            var prev = map.put("k1", "v2");

            assertEquals("v1", prev, "Previous value must be returned upon replacement");
            assertEquals(1, map.size(), "Size must remain unchanged when replacing an existing key");
            assertEquals("v2", map.get("k1"));
            assertEquals("v2", map.getRaw("k1"));

            var prev3 = map.put("k1", "v3");
            assertEquals("v2", prev3);
            assertEquals(1, map.size());
            assertEquals("v3", map.get("k1"));
        }

        @Test
        @DisplayName("put multiple entries up to maxSize")
        void testPutMultipleEntries()
        {
            assertNull(map.put("k1", "v1"));
            assertNull(map.put("k2", "v2"));
            assertNull(map.put("k3", "v3"));
            assertNull(map.put("k4", "v4"));

            assertEquals(4, map.size());
            assertEquals("v1", map.get("k1"));
            assertEquals("v2", map.get("k2"));
            assertEquals("v3", map.get("k3"));
            assertEquals("v4", map.get("k4"));

            assertEquals("v1", map.getRaw("k1"));
            assertEquals("v2", map.getRaw("k2"));
            assertEquals("v3", map.getRaw("k3"));
            assertEquals("v4", map.getRaw("k4"));
        }

        @Test
        @DisplayName("get and getRaw return null for absent keys")
        void testGetAbsentKeyReturnsNull()
        {
            map.put("k1", "v1");
            assertNull(map.get("k2"));
            assertNull(map.getRaw("k2"));
            assertNull(map.get("non-existent"));
            assertNull(map.getRaw("non-existent"));
        }

        @Test
        @DisplayName("remove existing key returns previous value and decrements size")
        void testRemoveExistingKey()
        {
            map.put("k1", "v1");
            map.put("k2", "v2");
            assertEquals(2, map.size());

            var removed = map.remove("k1");
            assertEquals("v1", removed);
            assertEquals(1, map.size());

            assertNull(map.get("k1"));
            assertNull(map.getRaw("k1"));
            assertEquals("v2", map.get("k2"));
            assertEquals(List.of("k2"), map.keys());
        }

        @Test
        @DisplayName("remove absent key returns null and keeps size unchanged")
        void testRemoveAbsentKey()
        {
            map.put("k1", "v1");
            var removed = map.remove("k2");

            assertNull(removed);
            assertEquals(1, map.size());
            assertEquals("v1", map.get("k1"));
        }

        @Test
        @DisplayName("remove already removed key returns null")
        void testRemoveAlreadyRemovedKey()
        {
            map.put("k1", "v1");
            assertEquals("v1", map.remove("k1"));
            assertNull(map.remove("k1"));
            assertEquals(0, map.size());
        }

        @Test
        @DisplayName("re-inserting a removed key works as a new key")
        void testReinsertRemovedKey()
        {
            map.put("k1", "v1");
            map.remove("k1");
            assertEquals(0, map.size());

            var prev = map.put("k1", "v1_new");
            assertNull(prev, "Re-inserting a previously removed key must return null as previous value");
            assertEquals(1, map.size());
            assertEquals("v1_new", map.get("k1"));
            assertEquals("v1_new", map.getRaw("k1"));
        }

        @Test
        @DisplayName("clear resets size and removes all key-value mappings")
        void testClear()
        {
            map.put("k1", "v1");
            map.put("k2", "v2");
            map.put("k3", "v3");
            map.put("k4", "v4");
            assertEquals(4, map.size());

            map.clear();

            assertEquals(0, map.size());
            assertTrue(map.keys().isEmpty());
            assertNull(map.get("k1"));
            assertNull(map.get("k2"));
            assertNull(map.get("k3"));
            assertNull(map.get("k4"));
            assertNull(map.getRaw("k1"));
            assertNull(map.getRaw("k2"));
            assertNull(map.getRaw("k3"));
            assertNull(map.getRaw("k4"));
        }

        @Test
        @DisplayName("map is reusable after clear")
        void testMapReusableAfterClear()
        {
            map.put("k1", "v1");
            map.put("k2", "v2");
            map.clear();

            map.put("k3", "v3");
            map.put("k4", "v4");
            assertEquals(2, map.size());
            assertEquals("v3", map.get("k3"));
            assertEquals("v4", map.get("k4"));
            assertNull(map.get("k1"));
            assertNull(map.get("k2"));
        }
    }

    // =========================================================================
    // 4. keys() Method Contract Tests
    // =========================================================================
    @Nested
    @DisplayName("keys() Method Contract")
    class KeysMethodContractTests
    {
        private LRUClockMap<String, Integer> map;

        @BeforeEach
        void setUp()
        {
            map = createMap(5);
        }

        @Test
        @DisplayName("keys() returns empty list when map is empty")
        void testKeysWhenEmpty()
        {
            var keys = map.keys();
            assertNotNull(keys);
            assertTrue(keys.isEmpty());
        }

        @Test
        @DisplayName("keys() returns all active keys without nulls or duplicates")
        void testKeysContainsAllActiveKeys()
        {
            map.put("A", 1);
            map.put("B", 2);
            map.put("C", 3);

            var keys = map.keys();
            assertEquals(3, keys.size());
            assertFalse(keys.contains(null));
            assertTrue(keys.containsAll(List.of("A", "B", "C")));
            assertEquals(Set.of("A", "B", "C"), new HashSet<>(keys));
        }

        @Test
        @DisplayName("keys() reflects removals accurately")
        void testKeysAfterRemoval()
        {
            map.put("A", 1);
            map.put("B", 2);
            map.put("C", 3);
            map.remove("B");

            var keys = map.keys();
            assertEquals(2, keys.size());
            assertTrue(keys.contains("A"));
            assertFalse(keys.contains("B"));
            assertTrue(keys.contains("C"));
        }

        @Test
        @DisplayName("Modifying the list returned by keys() does not alter internal map state")
        void testKeysDefensiveCopy()
        {
            map.put("A", 1);
            map.put("B", 2);

            var keys = map.keys();
            try
            {
                keys.clear();
            }
            catch (UnsupportedOperationException ignored)
            {
                // In case keys() returns an unmodifiable list, that also satisfies immutability
            }

            assertEquals(2, map.size(), "Map size must be unaffected by modifying returned keys list");
            assertEquals(1, map.get("A"));
            assertEquals(2, map.get("B"));
            assertEquals(2, map.keys().size(), "New call to keys() must still return all keys");
        }
    }

    // =========================================================================
    // 5. Clock (Second-Chance) LRU Eviction Policy Tests
    // =========================================================================
    @Nested
    @DisplayName("Clock LRU Eviction & Second-Chance Policy")
    class ClockEvictionPolicyTests
    {
        @Test
        @DisplayName("Map never exceeds maxSize when inserting more keys")
        void testCapacityInvariant()
        {
            final int maxSize = 4;
            var map = LRUClockMap.<String, Integer>of(maxSize);

            for (int i = 1; i <= 20; i++)
            {
                map.put("key_" + i, i);
                assertTrue(map.size() <= maxSize, "Map size must never exceed maxSize: " + map.size());
                assertEquals(map.size(), map.keys().size(), "keys().size() must match map.size()");
            }

            assertEquals(maxSize, map.size());
        }

        @Test
        @DisplayName("Eviction occurs on inserting a new key beyond maxSize")
        void testEvictionOccursWhenFull()
        {
            final int maxSize = 4;
            var map = LRUClockMap.<String, String>of(maxSize);

            map.put("k1", "v1");
            map.put("k2", "v2");
            map.put("k3", "v3");
            map.put("k4", "v4");
            assertEquals(4, map.size());

            // Adding 5th key triggers eviction of one entry
            map.put("k5", "v5");

            assertEquals(maxSize, map.size());
            assertEquals("v5", map.get("k5"));
            assertTrue(map.keys().contains("k5"));

            // Exactly 3 of the original 4 keys must remain
            int remainingOriginalKeys = 0;
            for (var key : List.of("k1", "k2", "k3", "k4"))
            {
                if (map.getRaw(key) != null)
                {
                    remainingOriginalKeys++;
                }
            }
            assertEquals(3, remainingOriginalKeys, "Exactly 3 original keys should remain after 1 eviction");
        }

        @Test
        @DisplayName("Updating an existing key at capacity does NOT trigger eviction")
        void testUpdateAtCapacityDoesNotEvict()
        {
            final int maxSize = 4;
            var map = LRUClockMap.<String, String>of(maxSize);

            map.put("k1", "v1");
            map.put("k2", "v2");
            map.put("k3", "v3");
            map.put("k4", "v4");
            assertEquals(4, map.size());

            // Overwrite existing key
            var prev = map.put("k2", "v2_updated");
            assertEquals("v2", prev);
            assertEquals(4, map.size());

            // All 4 keys must still be present
            assertAll("All 4 original keys must be present",
                () -> assertEquals("v1", map.get("k1")),
                () -> assertEquals("v2_updated", map.get("k2")),
                () -> assertEquals("v3", map.get("k3")),
                () -> assertEquals("v4", map.get("k4"))
            );
            assertEquals(Set.of("k1", "k2", "k3", "k4"), new HashSet<>(map.keys()));
        }

        @Test
        @DisplayName("get() sets second-chance flag to protect entries from immediate eviction")
        void testGetSetsSecondChanceFlag()
        {
            final int maxSize = 4;
            var map = LRUClockMap.<String, String>of(maxSize);

            map.put("k1", "v1");
            map.put("k2", "v2");
            map.put("k3", "v3");
            map.put("k4", "v4");

            // Touch k1 and k2 with get() to grant second chance
            assertEquals("v1", map.get("k1"));
            assertEquals("v2", map.get("k2"));

            // Insert k5 and k6 to trigger 2 evictions
            map.put("k5", "v5");
            map.put("k6", "v6");

            assertEquals(maxSize, map.size());
            assertEquals("v5", map.get("k5"));
            assertEquals("v6", map.get("k6"));

            // k1 and k2 should have survived eviction due to second chance
            assertTrue(map.get("k1") != null || map.get("k2") != null,
                "At least one of the second-chance touched entries (k1, k2) should survive");
        }

        @Test
        @DisplayName("getRaw() does not touch clock flag and does not provide second chance")
        void testGetRawDoesNotSetSecondChanceFlag()
        {
            final int maxSize = 4;

            // Test map using getRaw (no second chance flag set)
            var map = LRUClockMap.<String, String>of(maxSize);
            map.put("k1", "v1");
            map.put("k2", "v2");
            map.put("k3", "v3");
            map.put("k4", "v4");

            // Read with getRaw - should NOT set second chance flag
            assertEquals("v1", map.getRaw("k1"));
            assertEquals("v2", map.getRaw("k2"));
            assertEquals("v3", map.getRaw("k3"));
            assertEquals("v4", map.getRaw("k4"));

            // Adding k5, k6, k7, k8 should smoothly evict old entries without extra chances
            map.put("k5", "v5");
            map.put("k6", "v6");
            map.put("k7", "v7");
            map.put("k8", "v8");

            assertEquals(maxSize, map.size());
            assertEquals("v5", map.get("k5"));
            assertEquals("v6", map.get("k6"));
            assertEquals("v7", map.get("k7"));
            assertEquals("v8", map.get("k8"));
            assertEquals(Set.of("k5", "k6", "k7", "k8"), new HashSet<>(map.keys()));
        }

        @Test
        @DisplayName("Re-inserting an evicted key functions as a fresh entry")
        void testReinsertEvictedKey()
        {
            final int maxSize = 4;
            var map = LRUClockMap.<String, String>of(maxSize);

            map.put("k1", "v1");
            map.put("k2", "v2");
            map.put("k3", "v3");
            map.put("k4", "v4");

            // Force eviction of k1 by adding multiple new keys
            for (int i = 5; i <= 10; i++)
            {
                map.put("k" + i, "v" + i);
            }

            assertNull(map.get("k1"), "k1 should have been evicted");
            assertNull(map.getRaw("k1"), "k1 should have been evicted");

            // Re-insert k1
            var prev = map.put("k1", "v1_resurrected");
            assertNull(prev, "Re-inserting evicted key should return null as previous value");
            assertEquals("v1_resurrected", map.get("k1"));
            assertEquals("v1_resurrected", map.getRaw("k1"));
            assertTrue(map.keys().contains("k1"));
            assertEquals(maxSize, map.size());
        }

        @Test
        @DisplayName("Continuous sequential puts maintain capacity and consistency")
        void testContinuousSequentialPuts()
        {
            final int maxSize = 10;
            var map = LRUClockMap.<Integer, String>of(maxSize);

            for (int i = 0; i < 200; i++)
            {
                map.put(i, "val_" + i);
                int expectedSize = Math.min(i + 1, maxSize);
                assertEquals(expectedSize, map.size());
                assertEquals(expectedSize, map.keys().size());
                assertEquals("val_" + i, map.get(i), "Most recently added item must be retrievable");
            }
        }
    }

    // =========================================================================
    // 6. Open Addressing & Hash Collision Tests
    // =========================================================================
    @Nested
    @DisplayName("Open Addressing Probing and Hash Collisions")
    class OpenAddressingAndCollisionTests
    {
        /**
         * Custom Java 21 record with controllable hash code to simulate open addressing collisions.
         */
        record CollidingKey(int id, int forcedHash)
        {
            @Override
            public int hashCode()
            {
                return forcedHash;
            }

            @Override
            public boolean equals(final Object obj)
            {
                if (this == obj) return true;
                if (obj instanceof CollidingKey other)
                {
                    return this.id == other.id;
                }
                return false;
            }

            @Override
            public String toString()
            {
                return "CollidingKey[id=" + id + ", hash=" + forcedHash + "]";
            }
        }

        @Test
        @DisplayName("Inserting and retrieving multiple keys with identical hashCode")
        void testCollidingKeysPutAndGet()
        {
            var map = LRUClockMap.<CollidingKey, String>of(8);
            var k1 = new CollidingKey(1, 42);
            var k2 = new CollidingKey(2, 42);
            var k3 = new CollidingKey(3, 42);
            var k4 = new CollidingKey(4, 42);

            assertNull(map.put(k1, "value_1"));
            assertNull(map.put(k2, "value_2"));
            assertNull(map.put(k3, "value_3"));
            assertNull(map.put(k4, "value_4"));

            assertEquals(4, map.size());
            assertEquals("value_1", map.get(k1));
            assertEquals("value_2", map.get(k2));
            assertEquals("value_3", map.get(k3));
            assertEquals("value_4", map.get(k4));

            assertEquals("value_1", map.getRaw(k1));
            assertEquals("value_2", map.getRaw(k2));
            assertEquals("value_3", map.getRaw(k3));
            assertEquals("value_4", map.getRaw(k4));
        }

        @Test
        @DisplayName("Updating a colliding key modifies only that key's value")
        void testCollidingKeysUpdate()
        {
            var map = LRUClockMap.<CollidingKey, String>of(8);
            var k1 = new CollidingKey(1, 100);
            var k2 = new CollidingKey(2, 100);
            var k3 = new CollidingKey(3, 100);

            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            var prev = map.put(k2, "v2_updated");
            assertEquals("v2", prev);
            assertEquals(3, map.size());

            assertEquals("v1", map.get(k1));
            assertEquals("v2_updated", map.get(k2));
            assertEquals("v3", map.get(k3));
        }

        @Test
        @DisplayName("Removing an intermediate colliding key preserves probing chain for other colliding keys")
        void testCollidingKeysRemoveIntermediate()
        {
            var map = LRUClockMap.<CollidingKey, String>of(8);
            var k1 = new CollidingKey(1, 77);
            var k2 = new CollidingKey(2, 77);
            var k3 = new CollidingKey(3, 77);
            var k4 = new CollidingKey(4, 77);

            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");
            map.put(k4, "v4");
            assertEquals(4, map.size());

            // Remove k2 (intermediate element in the probe chain)
            var removed = map.remove(k2);
            assertEquals("v2", removed);
            assertEquals(3, map.size());

            // Probing for k1, k3, k4 must still succeed (tombstone / backward shift verification)
            assertNull(map.get(k2));
            assertNull(map.getRaw(k2));
            assertEquals("v1", map.get(k1));
            assertEquals("v3", map.get(k3));
            assertEquals("v4", map.get(k4));

            // Remove k3 as well
            assertEquals("v3", map.remove(k3));
            assertEquals(2, map.size());
            assertEquals("v1", map.get(k1));
            assertEquals("v4", map.get(k4));
        }

        @Test
        @DisplayName("Re-inserting a removed colliding key reuses the slot correctly")
        void testCollidingKeysReinsertion()
        {
            var map = LRUClockMap.<CollidingKey, String>of(8);
            var k1 = new CollidingKey(1, 99);
            var k2 = new CollidingKey(2, 99);
            var k3 = new CollidingKey(3, 99);

            map.put(k1, "v1");
            map.put(k2, "v2");
            map.put(k3, "v3");

            map.remove(k2);
            assertNull(map.put(k2, "v2_reinserted"));

            assertEquals(3, map.size());
            assertEquals("v1", map.get(k1));
            assertEquals("v2_reinserted", map.get(k2));
            assertEquals("v3", map.get(k3));
        }

        @Test
        @DisplayName("Eviction works correctly when map contains hash collisions")
        void testCollidingKeysWithEviction()
        {
            final int maxSize = 4;
            var map = LRUClockMap.<CollidingKey, String>of(maxSize);

            for (int i = 1; i <= 4; i++)
            {
                map.put(new CollidingKey(i, 55), "val_" + i);
            }
            assertEquals(4, map.size());

            // Add 5th and 6th colliding keys
            map.put(new CollidingKey(5, 55), "val_5");
            map.put(new CollidingKey(6, 55), "val_6");

            assertEquals(maxSize, map.size());
            assertEquals("val_5", map.get(new CollidingKey(5, 55)));
            assertEquals("val_6", map.get(new CollidingKey(6, 55)));
        }
    }

    // =========================================================================
    // 7. Java 21 Features and Diverse Types Tests
    // =========================================================================
    @Nested
    @DisplayName("Java 21 Features and Diverse Types")
    class Java21AndDiverseTypesTests
    {
        record Account(int accountId, String owner) {}
        record AccountBalance(double amount, String currency) {}

        @Test
        @DisplayName("Record keys and values work seamlessly")
        void testRecordKeysAndValues()
        {
            var map = LRUClockMap.<Account, AccountBalance>of(5);

            var acc1 = new Account(101, "Alice");
            var acc2 = new Account(102, "Bob");
            var bal1 = new AccountBalance(1500.50, "EUR");
            var bal2 = new AccountBalance(2750.00, "USD");

            assertNull(map.put(acc1, bal1));
            assertNull(map.put(acc2, bal2));

            assertEquals(2, map.size());
            assertEquals(bal1, map.get(acc1));
            assertEquals(bal2, map.get(acc2));

            // Record pattern matching with switch
            var retrieved = map.get(acc1);
            String formatted = switch (retrieved)
            {
                case null -> "No balance";
                case AccountBalance(double amount, String currency) -> amount + " " + currency;
            };
            assertEquals("1500.5 EUR", formatted);
        }

        @Test
        @DisplayName("Pattern matching and null-handling switch on get results")
        void testPatternMatchingSwitch()
        {
            var map = LRUClockMap.<String, Object>of(4);
            map.put("text", "hello");
            map.put("number", 42);

            for (var key : List.of("text", "number", "missing"))
            {
                var val = map.get(key);
                String result = switch (val)
                {
                    case null -> "missing";
                    case String s -> "string:" + s.toUpperCase();
                    case Integer i -> "int:" + (i * 2);
                    default -> "other";
                };

                switch (key)
                {
                    case "text" -> assertEquals("string:HELLO", result);
                    case "number" -> assertEquals("int:84", result);
                    case "missing" -> assertEquals("missing", result);
                    default -> throw new IllegalStateException("Unexpected key: " + key);
                }
            }
        }
    }

    // =========================================================================
    // 8. Lifecycle, Stress and Churn Tests
    // =========================================================================
    @Nested
    @DisplayName("Lifecycle, Stress and Churn Operations")
    class StressAndLifecycleTests
    {
        @Test
        @DisplayName("Clear and refill cycles maintain integrity")
        void testMultipleClearAndRefillCycles()
        {
            final int maxSize = 6;
            var map = LRUClockMap.<String, Integer>of(maxSize);

            for (int cycle = 0; cycle < 5; cycle++)
            {
                assertEquals(0, map.size(), "Cycle " + cycle + " start size must be 0");

                for (int i = 0; i < maxSize; i++)
                {
                    assertNull(map.put("k_" + cycle + "_" + i, i));
                }
                assertEquals(maxSize, map.size());

                // Overfill to cause evictions
                for (int i = maxSize; i < maxSize * 2; i++)
                {
                    map.put("k_" + cycle + "_" + i, i);
                    assertEquals(maxSize, map.size());
                }

                // Remove some elements
                for (int i = maxSize; i < maxSize + 3; i++)
                {
                    var removed = map.remove("k_" + cycle + "_" + i);
                    assertNotNull(removed);
                }
                assertEquals(maxSize - 3, map.size());

                map.clear();
                assertEquals(0, map.size());
                assertTrue(map.keys().isEmpty());
            }
        }

        @Test
        @DisplayName("Random churn stress test with invariant validation")
        void testRandomChurnStress()
        {
            final int maxSize = 16;
            var map = LRUClockMap.<Integer, String>of(maxSize);
            var random = new Random(12345L);
            final int totalOperations = 2000;
            final int keySpace = 50;

            for (int op = 0; op < totalOperations; op++)
            {
                int key = random.nextInt(keySpace);
                int action = random.nextInt(4);

                switch (action)
                {
                    case 0 -> { // Put
                        map.put(key, "val_" + op);
                    }
                    case 1 -> { // Get
                        map.get(key);
                    }
                    case 2 -> { // GetRaw
                        map.getRaw(key);
                    }
                    case 3 -> { // Remove
                        map.remove(key);
                    }
                }

                // Verify invariants after every operation
                int size = map.size();
                assertTrue(size <= maxSize, "Size must never exceed maxSize: " + size);
                assertTrue(size >= 0, "Size must not be negative: " + size);

                var keys = map.keys();
                assertEquals(size, keys.size(), "keys().size() must match map.size()");
                assertFalse(keys.contains(null), "keys() must never contain null");

                // Each key reported by keys() must be retrievable
                for (var k : keys)
                {
                    assertNotNull(map.getRaw(k), "Key reported in keys() must have a value");
                }
            }
        }
    }
}
