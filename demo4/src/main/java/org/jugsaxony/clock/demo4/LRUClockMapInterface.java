package org.jugsaxony.clock.demo4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

/**
 * This is an interface for an LRU map implementation based on the clock algorithm.
 * The LRU part is not really predictable, but it is fast and simple. 
 * We don't keep a linked list, rather a marker that give every entry a second chance. 
 * If the second chance is false, we can evict this entry. This can happen
 * rather early. In general, it is still a good enough LRU implementation.
 * https://www.geeksforgeeks.org/operating-systems/second-chance-or-clock-page-replacement-policy/
 *
 * The base hashmap is an open addressing hash table.
 * Not thread-safe! No support for null keys or null values. 
 *
 * @author René Schwietzke
 */
public interface LRUClockMapInterface<K, V>
{
    /**
     * The only way to create a new LRUClockMap. Each map will have
     * a defined load factor of 0.5f which cannot be changed.
     * 
     * Note: Because we cannot define a Constructor in an interface and
     * we want to things simple, we publish this as implementation guidance.
     * 
     * @param maxSize 
     *  the maximum size of the map, putting another new key 
     *  into the map will evict another key based on a good enough LRU concept
     * @return a new LRUClockMap
     * 
     * @throws IllegalArgumentException if maxSize is less than 4
     */
    // public static <K, V> LRUClockMap<K, V> of(final int maxSize);
    
    /**
     * Read data without touching the clock flag. This is mostly for testing purposes
     * or in case you really don't want to affect the LRU state.
     *
     * @param key what key to look for
     * @return the value for the key or null if not found
     */
    public V getRaw(final K key);
    
    /**
     * Get a value from the map. This will set the second chance flag
     * if the entry is found.
     * 
     * @param key what key to look for
     * @return the value for the key or null if not found
     */
    public V get(final K key);

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for the key, the old
     * value is replaced. Starts eviction if the max size is reached.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with key, or null if there was no mapping for key.
     */
    public V put(final K key, final V value);

    /**
     * Removes the mapping for a key from this map if it is present.
     *
     * @param key key whose mapping is to be removed from the map
     * @return the previous value associated with key, or null if there was no mapping for key.
     */
    public V remove(final K key);
    
    /**
     * Returns the number of key-value mappings.
     *
     * @return the number of entries
     */
    public int size();
    
    /**
     * Returns a list of all keys. This does not cause LRU effects.
     * This is in order of the internal array but without the free slots. 
     * Changing the array does not change the internal structure.
     *
     * @return a list of keys or an empty array if size is 0
     */
    public List<K> keys();

    /**
     * Clears the map, reuses the data structure by clearing it out.
     */
    public void clear();
}
