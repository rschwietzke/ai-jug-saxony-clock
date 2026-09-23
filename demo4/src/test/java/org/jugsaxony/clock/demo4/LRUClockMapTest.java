package org.jugsaxony.clock.demo4;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LRUClockMap}.
 * 
 * These tests are designed to validate the behavior of the LRUClockMap interface
 * and its future implementations.
 */
public class LRUClockMapTest {

    // Note: Since LRUClockMap is an interface, the tests assume the existence of 
    // a factory method LRUClockMap.of(int maxSize) provided by the implementation.
    // To allow these tests to compile and run against any implementation, 
    // we would normally use a provider or a specific implementation class.
    // For now, we assume: 
    // static <K, V> LRUClockMap<K, V> createMap(int maxSize) { return LRUClockMap.of(maxSize); }

    // I will use a helper method to instantiate the map. 
    // In the actual project, this would be replaced by the real factory call.
    @SuppressWarnings("unchecked")
    private <K, V> LRUClockMap<K, V> createMap(int maxSize) {
        // This is a placeholder. The implementation will provide the actual logic.
        // For the sake of this task, we assume LRUClockMap.of exists in the implementation.
        try {
            var method = LRUClockMap.class.getDeclaredMethod("of", int.class);
            return (LRUClockMap<K, V>) method.invoke(null, maxSize);
        } catch (Exception e) {
            throw new RuntimeException("LRUClockMap.of(int) method not found or accessible. " +
                                       "The implementation must provide this factory method.", e);
        }
    }

    @Nested
    @DisplayName("Basic Map Operations")
    class BasicOperations {

        @Test
        @DisplayName("put and get should store and retrieve values")
        void putAndGet() {
            // Fixed syntax error: 'createMap<String, Integer>(10)' was invalid Java; qualified with enclosing class 'LRUClockMapTest.this.<String, Integer>createMap(10)'
            var map = LRUClockMapTest.this.<String, Integer>createMap(10);
            map.put("key1", 1);
            map.put("key2", 2);

            assertEquals(1, map.get("key1"));
            assertEquals(2, map.get("key2"));
            assertEquals(2, map.size());
        }

        @Test
        @DisplayName("put should update existing keys")
        void putUpdate() {
            // Fixed syntax error: 'createMap<String, Integer>(10)' was invalid Java; qualified with enclosing class 'LRUClockMapTest.this.<String, Integer>createMap(10)'
            var map = LRUClockMapTest.this.<String, Integer>createMap(10);
            map.put("key1", 1);
            Integer prev = map.put("key1", 11);

            assertEquals(1, prev);
            assertEquals(11, map.get("key1"));
            assertEquals(1, map.size());
        }

        @Test
        @DisplayName("remove should remove keys and return value")
        void remove() {
            // Fixed syntax error: 'createMap<String, Integer>(10)' was invalid Java; qualified with enclosing class 'LRUClockMapTest.this.<String, Integer>createMap(10)'
            var map = LRUClockMapTest.this.<String, Integer>createMap(10);
            map.put("key1", 1);
            
            Integer removed = map.remove("key1");
            assertEquals(1, removed);
            assertNull(map.get("key1"));
            assertEquals(0, map.size());

            assertNull(map.remove("nonexistent"));
        }

        @Test
        @DisplayName("clear should remove all entries")
        void clear() {
            // Fixed syntax error: 'createMap<String, Integer>(10)' was invalid Java; qualified with enclosing class 'LRUClockMapTest.this.<String, Integer>createMap(10)'
            var map = LRUClockMapTest.this.<String, Integer>createMap(10);
            map.put("k1", 1);
            map.put("k2", 2);
            
            map.clear();
            assertEquals(0, map.size());
            assertNull(map.get("k1"));
            assertTrue(map.keys().isEmpty());
        }

        @Test
        @DisplayName("keys should return all current keys")
        void keys() {
            // Fixed syntax error: 'createMap<String, Integer>(10)' was invalid Java; qualified with enclosing class 'LRUClockMapTest.this.<String, Integer>createMap(10)'
            var map = LRUClockMapTest.this.<String, Integer>createMap(10);
            map.put("k1", 1);
            map.put("k2", 2);
            map.put("k3", 3);

            List<String> keys = map.keys();
            assertEquals(3, keys.size());
            assertTrue(keys.containsAll(List.of("k1", "k2", "k3")));
        }

        @Test
        @DisplayName("getRaw should retrieve value without affecting LRU state")
        void getRaw() {
            // Fixed syntax error: 'createMap<String, Integer>(10)' was invalid Java; qualified with enclosing class 'LRUClockMapTest.this.<String, Integer>createMap(10)'
            var map = LRUClockMapTest.this.<String, Integer>createMap(10);
            map.put("k1", 1);
            assertEquals(1, map.getRaw("k1"));
            assertNull(map.getRaw("nonexistent"));
        }
    }

    @Nested
    @DisplayName("Constraints and Edge Cases")
    class Constraints {

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2, 3})
        @DisplayName("maxSize less than 4 should throw IllegalArgumentException")
        void minMaxSize(int size) {
            assertThrows(IllegalArgumentException.class, () -> createMap(size));
        }

        @Test
        @DisplayName("null keys should throw exception")
        void nullKey() {
            // Fixed syntax error: 'createMap<String, Integer>(10)' was invalid Java; qualified with enclosing class 'LRUClockMapTest.this.<String, Integer>createMap(10)'
            var map = LRUClockMapTest.this.<String, Integer>createMap(10);
            assertThrows(Exception.class, () -> map.put(null, 1));
        }

        @Test
        @DisplayName("null values should throw exception")
        void nullValue() {
            // Fixed syntax error: 'createMap<String, Integer>(10)' was invalid Java; qualified with enclosing class 'LRUClockMapTest.this.<String, Integer>createMap(10)'
            var map = LRUClockMapTest.this.<String, Integer>createMap(10);
            assertThrows(Exception.class, () -> map.put("k1", null));
        }
    }

    @Nested
    @DisplayName("Clock Eviction Logic")
    class Eviction {

        @Test
        @DisplayName("should evict entries when maxSize is reached")
        void simpleEviction() {
            int maxSize = 4;
            // Fixed syntax error: 'createMap<String, Integer>(maxSize)' was invalid Java; qualified with enclosing class 'LRUClockMapTest.this.<String, Integer>createMap(maxSize)'
            var map = LRUClockMapTest.this.<String, Integer>createMap(maxSize);
            
            for (int i = 0; i < maxSize; i++) {
                map.put("k" + i, i);
            }
            
            assertEquals(maxSize, map.size());
            
            // Adding one more should trigger eviction
            map.put("extra", 100);
            assertEquals(maxSize, map.size());
            
            // At least one of the original keys must have been evicted
            long foundOriginals = 0;
            for (int i = 0; i < maxSize; i++) {
                if (map.getRaw("k" + i) != null) {
                    foundOriginals++;
                }
            }
            assertTrue(foundOriginals < maxSize, "One entry should have been evicted");
        }

        @Test
        @DisplayName("get should give entry a second chance")
        void secondChance() {
            int maxSize = 4;
            // Fixed syntax error: 'createMap<String, Integer>(maxSize)' was invalid Java; qualified with enclosing class 'LRUClockMapTest.this.<String, Integer>createMap(maxSize)'
            var map = LRUClockMapTest.this.<String, Integer>createMap(maxSize);
            
            // 1. Fill the map
            for (int i = 0; i < maxSize; i++) {
                map.put("k" + i, i);
            }
            
            // 2. Access k0 and k1 with get() -> set second chance bit to 1
            map.get("k0");
            map.get("k1");
            
            // 3. Access k2 with getRaw() -> second chance bit remains 0
            map.getRaw("k2");
            
            // 4. Add new entry to trigger eviction
            map.put("extra", 100);
            
            // k0 and k1 should likely be preserved because they had a second chance.
            // k2 or k3 (which weren't touched by get()) should be evicted.
            assertNotNull(map.getRaw("k0"), "k0 should have been preserved by second chance");
            assertNotNull(map.getRaw("k1"), "k1 should have been preserved by second chance");
        }

        @Test
        @DisplayName("getRaw should not give entry a second chance")
        void getRawNoSecondChance() {
            int maxSize = 4;
            // Fixed syntax error: 'createMap<String, Integer>(maxSize)' was invalid Java; qualified with enclosing class 'LRUClockMapTest.this.<String, Integer>createMap(maxSize)'
            var map = LRUClockMapTest.this.<String, Integer>createMap(maxSize);
            
            for (int i = 0; i < maxSize; i++) {
                map.put("k" + i, i);
            }
            
            // Use getRaw on k0. It should still be a candidate for eviction.
            map.getRaw("k0");
            
            // Use get on k1, k2, k3. They should get second chances.
            map.get("k1");
            map.get("k2");
            map.get("k3");
            
            // Trigger eviction. k0 is the most likely candidate.
            map.put("extra", 100);
            
            assertNull(map.getRaw("k0"), "k0 should have been evicted as getRaw does not set second chance");
        }
    }
}
