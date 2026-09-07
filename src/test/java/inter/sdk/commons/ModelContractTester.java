package inter.sdk.commons;

import inter.sdk.commons.models.AbstractModel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Reflection-driven contract checks for Lombok-generated model classes:
 * constructors, builders, getters/setters, equals/hashCode (every field null/non-null/different
 * on both sides) and toString. Nothing here touches the network.
 */
public final class ModelContractTester {

    private static final int MAX_DEPTH = 2;

    private ModelContractTester() {
    }

    public static void verify(Class<?> type) throws Exception {
        verifyConstructors(type);
        verifyBuilder(type);
        verifyAccessors(type);
        verifyEqualsHashCodeToString(type);
    }

    static void verifyConstructors(Class<?> type) throws Exception {
        if (Modifier.isAbstract(type.getModifiers())) {
            return;
        }
        for (Constructor<?> ctor : type.getDeclaredConstructors()) {
            if (!Modifier.isPublic(ctor.getModifiers())) {
                continue;
            }
            Type[] params = ctor.getGenericParameterTypes();
            Object[] args = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                args[i] = value(params[i], 1, 0);
            }
            Object built = ctor.newInstance(args);
            assertNotNull(built);
            assertNotNull(built.toString());
            assertEquals(built, built);
        }
    }

    static void verifyBuilder(Class<?> type) throws Exception {
        Method builderMethod;
        try {
            builderMethod = type.getMethod("builder");
        } catch (NoSuchMethodException e) {
            return;
        }
        Object builder = builderMethod.invoke(null);
        Object expected = newInstance(type);
        if (expected == null) {
            assertNotNull(build(builder));
            return;
        }
        fill(expected, 1);
        Set<String> seen = new HashSet<>();
        for (Field field : fields(type)) {
            if (!seen.add(field.getName())) {
                // shadowed superclass field: the builder only reaches the most-derived one
                field.set(expected, null);
                continue;
            }
            Object value = field.get(expected);
            Method setter = findMethod(builder.getClass(), field.getName(), field.getType());
            if (setter != null) {
                setter.invoke(builder, value);
            }
        }
        assertNotNull(builder.toString());
        Object built = build(builder);
        assertNotNull(built);
        if (hasLombokEquals(type)) {
            assertEquals(expected, built);
            assertEquals(expected.hashCode(), built.hashCode());
        }
        for (Field field : fields(type)) {
            assertEquals(field.getName(), field.get(expected), field.get(built));
        }
    }

    static void verifyAccessors(Class<?> type) throws Exception {
        Object instance = newInstance(type);
        if (instance == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (Field field : fields(type)) {
            if (Modifier.isFinal(field.getModifiers()) || !seen.add(field.getName())) {
                continue;
            }
            Object value = value(field.getGenericType(), 2, 0);
            Method setter = findMethod(type, "set" + capitalize(field.getName()), field.getType());
            if (setter != null) {
                setter.invoke(instance, value);
            } else {
                field.set(instance, value);
            }
            Method getter = findGetter(type, field);
            if (getter != null) {
                assertEquals(field.getName(), value, getter.invoke(instance));
            }
            assertEquals(field.getName(), value, field.get(instance));
        }
    }

    static void verifyEqualsHashCodeToString(Class<?> type) throws Exception {
        Object a = newInstance(type);
        if (a == null) {
            return;
        }
        Object b = newInstance(type);
        fill(a, 1);
        fill(b, 1);
        assertNotNull(a.toString());
        assertTrue(a.toString().contains(type.getSimpleName()) || !hasLombokEquals(type));
        assertEquals(a, a);
        assertNotEquals(a, null);
        assertNotEquals(a, "not a model");
        if (!hasLombokEquals(type)) {
            return;
        }
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a.toString(), b.toString());

        Object emptyA = newInstance(type);
        Object emptyB = newInstance(type);
        assertEquals(emptyA, emptyB);
        assertEquals(emptyA.hashCode(), emptyB.hashCode());
        assertNotEquals(a, emptyA);
        assertNotEquals(emptyA, a);
        assertNotNull(emptyA.toString());

        Set<String> seen = new HashSet<>();
        for (Field field : fields(type)) {
            if (Modifier.isFinal(field.getModifiers()) || !seen.add(field.getName())) {
                continue;
            }
            Object other = newInstance(type);
            fill(other, 1);
            if (!field.getType().isPrimitive() && field.get(a) != null) {
                field.set(other, null);
                assertNotEquals(field.getName(), a, other);
                assertNotEquals(field.getName(), other, a);
                other.hashCode();
            }
            Object different = value(field.getGenericType(), 2, 0);
            if (different != null && !different.equals(field.get(a))) {
                field.set(other, different);
                assertNotEquals(field.getName(), a, other);
                assertNotEquals(field.getName(), other, a);
            }
        }

        if (a instanceof AbstractModel) {
            AbstractModel extra = (AbstractModel) newInstance(type);
            fill(extra, 1);
            extra.setAdditionalField("extra", "field");
            assertNotEquals(a, extra);
            assertNotEquals(extra, a);
            assertNotEquals(a.hashCode(), extra.hashCode());
            assertTrue(extra.toString().contains("extra=field"));
        }
    }

    private static Object build(Object builder) throws Exception {
        Method build = builder.getClass().getMethod("build");
        build.setAccessible(true);
        return build.invoke(builder);
    }

    private static boolean hasLombokEquals(Class<?> type) {
        try {
            return type.getDeclaredMethod("equals", Object.class) != null;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** All instance fields declared by {@code type} and its superclasses below {@link AbstractModel}/{@link Object}. */
    static List<Field> fields(Class<?> type) {
        List<Field> result = new ArrayList<>();
        for (Class<?> c = type; c != null && c != AbstractModel.class && c.getName().startsWith("inter."); c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
                    continue;
                }
                f.setAccessible(true);
                result.add(f);
            }
        }
        return result;
    }

    static Object newInstance(Class<?> type) throws Exception {
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            return null;
        }
        try {
            Constructor<?> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    static void fill(Object instance, int seed) throws Exception {
        fill(instance, seed, 0);
    }

    private static void fill(Object instance, int seed, int depth) throws Exception {
        for (Field field : fields(instance.getClass())) {
            if (Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            field.set(instance, value(field.getGenericType(), seed, depth));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object value(Type generic, int seed, int depth) throws Exception {
        Class<?> raw = generic instanceof ParameterizedType
                ? (Class<?>) ((ParameterizedType) generic).getRawType()
                : (Class<?>) generic;
        if (raw == String.class) {
            return "value" + seed;
        }
        if (raw == Integer.class || raw == int.class) {
            return seed;
        }
        if (raw == Long.class || raw == long.class) {
            return (long) seed;
        }
        if (raw == Boolean.class || raw == boolean.class) {
            return seed % 2 == 1;
        }
        if (raw == BigDecimal.class) {
            return BigDecimal.valueOf(seed);
        }
        if (raw == Date.class) {
            return new Date(seed * 1000L);
        }
        if (raw.isEnum()) {
            Object[] constants = raw.getEnumConstants();
            return constants[(seed - 1) % constants.length];
        }
        if (List.class.isAssignableFrom(raw)) {
            if (raw != List.class) {
                List list = (List) raw.newInstance();
                return list;
            }
            Type elem = ((ParameterizedType) generic).getActualTypeArguments()[0];
            Object element = value(elem, seed, depth + 1);
            return element == null ? new ArrayList<>() : Collections.singletonList(element);
        }
        if (Map.class.isAssignableFrom(raw)) {
            Map<String, String> map = new HashMap<>();
            map.put("k" + seed, "v" + seed);
            return map;
        }
        if (depth >= MAX_DEPTH) {
            return null;
        }
        Object nested = newInstance(raw);
        if (nested == null) {
            return null;
        }
        fill(nested, seed, depth + 1);
        return nested;
    }

    private static Method findMethod(Class<?> owner, String name, Class<?> param) {
        for (Class<?> c = owner; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == param) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    private static Method findGetter(Class<?> type, Field field) {
        String cap = capitalize(field.getName());
        String prefix = field.getType() == boolean.class ? "is" : "get";
        try {
            return type.getMethod(prefix + cap);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

}
