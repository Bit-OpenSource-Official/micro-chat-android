package ru.e6atb.chat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class MainActivityPreviewTest {
    @Test
    public void missingPreviewErrorIsNormalBeforeAndAfterSuccessfulRetry() throws Exception {
        // Exercise the actual Activity method without creating Android UI or
        // starting its executors in this host-side regression test.
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        MainActivity activity = (MainActivity) unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(unsafe, MainActivity.class);
        Map<String, String> errors = new HashMap<>();
        setField(activity, "imagePreviewErrors", errors);
        setField(activity, "imagePreviewLoading", new HashSet<String>());
        Method lookup = MainActivity.class.getDeclaredMethod("cachedImagePreviewError", String.class);
        lookup.setAccessible(true);

        assertNull(lookup.invoke(activity, "photo"));
        errors.put("photo", "download failed");
        assertEquals("download failed", lookup.invoke(activity, "photo"));
        assertNull(lookup.invoke(activity, "another-photo"));
        errors.remove("photo");
        assertNull(lookup.invoke(activity, "photo"));
    }

    private static void setField(MainActivity activity, String name, Object value) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(activity, value);
    }
}
