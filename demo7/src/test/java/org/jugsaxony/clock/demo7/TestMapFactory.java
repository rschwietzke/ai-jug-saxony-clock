package org.jugsaxony.clock.demo7;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Locates and instantiates the {@link LRUClockMap} implementation under test.
 *
 * <p>Because the tests are written against the interface only and the concrete
 * implementation is supplied later, this factory discovers the implementation
 * at runtime using reflection. The lookup order is:
 *
 * <ol>
 *   <li>the class named by the system property {@code lruclockmap.impl}</li>
 *   <li>a {@code public static of(int)} factory method on {@link LRUClockMap} itself
 *       (the interface publishes this as implementation guidance)</li>
 *   <li>the conventional name {@code org.jugsaxony.clock.demo7.LRUClockMapImpl}</li>
 *   <li>a best-effort scan of the {@code org.jugsaxony.clock.demo7} package on the
 *       classpath for a concrete class implementing {@link LRUClockMap} that offers
 *       either a {@code static of(int)} method or an {@code (int)} constructor</li>
 * </ol>
 *
 * <p>Exceptions thrown by the implementation factory (for example the
 * {@link IllegalArgumentException} for a maxSize below 4) are propagated unchanged.
 */
final class TestMapFactory
{
    /** System property to explicitly name the implementation class. */
    static final String IMPL_PROPERTY = "lruclockmap.impl";

    private static final String PACKAGE_NAME = "org.jugsaxony.clock.demo7";
    private static final String PACKAGE_PATH = PACKAGE_NAME.replace('.', '/');

    private static volatile Creator cachedCreator;

    private TestMapFactory()
    {
        // utility class
    }

    @FunctionalInterface
    private interface Creator
    {
        Object create(int maxSize) throws Exception;
    }

    /**
     * Creates a new map instance from the discovered implementation.
     *
     * @param maxSize the maximum size of the map
     * @return a new LRUClockMap
     * @throws IllegalArgumentException if the implementation rejects the maxSize
     * @throws IllegalStateException if no implementation can be found
     */
    @SuppressWarnings("unchecked")
    static <K, V> LRUClockMap<K, V> create(final int maxSize)
    {
        var creator = cachedCreator;
        if (creator == null)
        {
            creator = resolve();
            cachedCreator = creator;
        }

        try
        {
            return (LRUClockMap<K, V>) creator.create(maxSize);
        }
        catch (final InvocationTargetException e)
        {
            // propagate what the implementation threw, e.g. IllegalArgumentException
            switch (e.getCause())
            {
                case RuntimeException re  -> throw re;
                case Error err            -> throw err;
                case Throwable t          -> throw new IllegalStateException(
                        "LRUClockMap factory failed for maxSize " + maxSize, t);
                case null                 -> throw new IllegalStateException(
                        "LRUClockMap factory failed for maxSize " + maxSize, e);
            }
        }
        catch (final RuntimeException e)
        {
            throw e;
        }
        catch (final Exception e)
        {
            throw new IllegalStateException(
                    "Could not create LRUClockMap for maxSize " + maxSize, e);
        }
    }

    private static Creator resolve()
    {
        var candidates = new ArrayList<String>();

        var fromProperty = System.getProperty(IMPL_PROPERTY);
        if (fromProperty != null && !fromProperty.isBlank())
        {
            candidates.add(fromProperty.trim());
        }
        candidates.add(LRUClockMap.class.getName());
        candidates.add(PACKAGE_NAME + ".LRUClockMapImpl");
        candidates.addAll(scanPackage());

        var problems = new ArrayList<String>();
        for (final var candidate : candidates)
        {
            try
            {
                var type = Class.forName(candidate);
                var creator = creatorFor(type);
                if (creator != null)
                {
                    return creator;
                }
                problems.add(candidate + ": no usable static of(int) or (int) constructor");
            }
            catch (final ClassNotFoundException | NoClassDefFoundError e)
            {
                problems.add(candidate + ": class not found");
            }
        }

        throw new IllegalStateException("""
                No LRUClockMap implementation found. Tried:
                  %s
                Provide one via the system property '%s', a static of(int) on the \
                interface, or a concrete implementation class in package %s."""
                .formatted(String.join(System.lineSeparator() + "  ", problems),
                        IMPL_PROPERTY, PACKAGE_NAME));
    }

    /**
     * Builds a creator for a candidate type or returns null if the type
     * cannot serve as an implementation.
     */
    private static Creator creatorFor(final Class<?> type)
    {
        // preferred: public static of(int)
        try
        {
            final Method of = type.getMethod("of", int.class);
            final boolean isStatic = Modifier.isStatic(of.getModifiers());
            final boolean returnsMap = LRUClockMap.class.isAssignableFrom(of.getReturnType());

            if (isStatic && returnsMap)
            {
                of.setAccessible(true);
                return maxSize -> of.invoke(null, maxSize);
            }
        }
        catch (final NoSuchMethodException e)
        {
            // fall through to constructor lookup
        }

        // fallback: public (int) constructor on a concrete implementation class
        final boolean isCandidateImpl = LRUClockMap.class.isAssignableFrom(type)
                && !type.isInterface()
                && !Modifier.isAbstract(type.getModifiers());

        if (isCandidateImpl)
        {
            try
            {
                final var ctor = type.getConstructor(int.class);
                return maxSize -> ctor.newInstance(maxSize);
            }
            catch (final NoSuchMethodException e)
            {
                // not usable
            }
        }

        return null;
    }

    /**
     * Best-effort scan of the package directory on the classpath to discover
     * implementation class names. Works for directory-based classpaths, which
     * is the normal case for locally built Maven modules.
     */
    private static List<String> scanPackage()
    {
        final var result = new ArrayList<String>();

        try
        {
            final var loader = TestMapFactory.class.getClassLoader();
            final Enumeration<URL> urls = loader.getResources(PACKAGE_PATH);

            while (urls.hasMoreElements())
            {
                final var url = urls.nextElement();
                if (!"file".equals(url.getProtocol()))
                {
                    continue;
                }

                final var dir = new File(url.toURI());
                final var files = dir.listFiles((d, name) ->
                        name.endsWith(".class") && !name.contains("$"));
                if (files == null)
                {
                    continue;
                }

                for (final var file : files)
                {
                    final var className = PACKAGE_NAME + "."
                            + file.getName().substring(0, file.getName().length() - ".class".length());
                    if (!className.equals(TestMapFactory.class.getName())
                            && !className.equals(LRUClockMap.class.getName()))
                    {
                        result.add(className);
                    }
                }
            }
        }
        catch (final Exception e)
        {
            // best effort only, the explicit candidates still apply
        }

        return result;
    }
}
