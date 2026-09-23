package org.jugsaxony.clock.demo8;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Test factory utility to dynamically instantiate LRUClockMap implementations.
 * This allows the test suite to execute against any implementation class
 * provided in the package or registered at runtime.
 */
public final class LRUClockMapFactory {

    private static final String[] CANDIDATE_CLASS_NAMES = {
        "org.jugsaxony.clock.demo8.LRUClockMapImpl",
        "org.jugsaxony.clock.demo8.ClockLRUMap",
        "org.jugsaxony.clock.demo8.FastLRUClockMap",
        "org.jugsaxony.clock.demo8.LRUClockMap",
        "org.jugsaxony.clock.demo8.LRUClockHashMap"
    };

    private LRUClockMapFactory() {
    }

    @SuppressWarnings("unchecked")
    public static <K, V> LRUClockMap<K, V> create(int maxSize) {
        // 1. Try static factory method on LRUClockMap interface if present
        try {
            Method ofMethod = LRUClockMap.class.getMethod("of", int.class);
            if (Modifier.isStatic(ofMethod.getModifiers())) {
                return (LRUClockMap<K, V>) ofMethod.invoke(null, maxSize);
            }
        } catch (ReflectiveOperationException ignored) {
        }

        // 2. Try configured system property if set
        String customClassName = System.getProperty("lruclockmap.impl");
        if (customClassName != null && !customClassName.isBlank()) {
            LRUClockMap<K, V> instance = tryInstantiate(customClassName.trim(), maxSize);
            if (instance != null) {
                return instance;
            }
        }

        // 3. Try standard candidate class names in package
        for (String className : CANDIDATE_CLASS_NAMES) {
            LRUClockMap<K, V> instance = tryInstantiate(className, maxSize);
            if (instance != null) {
                return instance;
            }
        }

        throw new IllegalStateException(
            "No concrete implementation of LRUClockMap found. " +
            "Ensure an implementation (e.g. LRUClockMapImpl) is available in org.jugsaxony.clock.demo8 " +
            "or specify via system property -Dlruclockmap.impl=<full-class-name>."
        );
    }

    @SuppressWarnings("unchecked")
    private static <K, V> LRUClockMap<K, V> tryInstantiate(String className, int maxSize) {
        try {
            Class<?> clazz = Class.forName(className);
            if (!LRUClockMap.class.isAssignableFrom(clazz)) {
                return null;
            }

            // Try static method: of(int)
            try {
                Method ofMethod = clazz.getMethod("of", int.class);
                if (Modifier.isStatic(ofMethod.getModifiers())) {
                    return (LRUClockMap<K, V>) ofMethod.invoke(null, maxSize);
                }
            } catch (NoSuchMethodException ignored) {
            }

            // Try constructor(int)
            try {
                Constructor<?> ctor = clazz.getConstructor(int.class);
                return (LRUClockMap<K, V>) ctor.newInstance(maxSize);
            } catch (NoSuchMethodException ignored) {
            }

            // Try constructor(Integer)
            try {
                Constructor<?> ctor = clazz.getConstructor(Integer.class);
                return (LRUClockMap<K, V>) ctor.newInstance(maxSize);
            } catch (NoSuchMethodException ignored) {
            }

        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            if (e.getCause() instanceof IllegalArgumentException iae) {
                throw iae;
            }
            if (e instanceof IllegalArgumentException iae) {
                throw iae;
            }
        }
        return null;
    }
}
