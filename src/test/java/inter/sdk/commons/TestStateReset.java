package inter.sdk.commons;

import inter.sdk.commons.utils.HttpUtils;
import inter.sdk.commons.utils.TokenUtils;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Resets process-wide static state shared by {@link TokenUtils} and {@link HttpUtils}
 * so tests stay independent of execution order. Call from {@code @After}.
 */
public final class TestStateReset {

    private TestStateReset() {
    }

    public static void resetAll() {
        clearTokenCache();
        clearHttpState();
    }

    public static void clearTokenCache() {
        tokenMap().clear();
    }

    public static void clearHttpState() {
        setStatic(HttpUtils.class, "lastUrl", null);
        setStatic(HttpUtils.class, "lastRequest", null);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> tokenMap() {
        try {
            Field field = TokenUtils.class.getDeclaredField("TOKEN_MAP");
            field.setAccessible(true);
            return (Map<String, Object>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot access TokenUtils.TOKEN_MAP", e);
        }
    }

    private static void setStatic(Class<?> type, String name, Object value) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot reset " + type.getSimpleName() + "." + name, e);
        }
    }
}
