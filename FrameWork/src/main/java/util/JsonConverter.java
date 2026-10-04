package util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

public final class JsonConverter {

    private JsonConverter() {
    }

    public static String toJson(Object object) {
        return writeValue(object, new IdentityHashMap<Object, Boolean>());
    }

    private static String writeValue(Object value, IdentityHashMap<Object, Boolean> visited) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String || value instanceof Character || value instanceof Enum<?>) {
            return quote(String.valueOf(value));
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }

        if (visited.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Impossible de convertir une reference circulaire en JSON");
        }

        try {
            if (value.getClass().isArray()) {
                StringBuilder json = new StringBuilder("[");
                for (int index = 0; index < Array.getLength(value); index++) {
                    appendValue(json, writeValue(Array.get(value, index), visited), index > 0);
                }
                return json.append(']').toString();
            }
            if (value instanceof Collection<?>) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                for (Object item : (Collection<?>) value) {
                    appendValue(json, writeValue(item, visited), !first);
                    first = false;
                }
                return json.append(']').toString();
            }
            if (value instanceof Map<?, ?>) {
                StringBuilder json = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (!(entry.getKey() instanceof String)) {
                        throw new IllegalArgumentException("Les cles d'une Map doivent etre des String");
                    }
                    if (!first) {
                        json.append(',');
                    }
                    json.append(quote((String) entry.getKey())).append(':');
                    json.append(writeValue(entry.getValue(), visited));
                    first = false;
                }
                return json.append('}').toString();
            }

            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    int modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                        continue;
                    }
                    if (!first) {
                        json.append(',');
                    }
                    field.setAccessible(true);
                    json.append(quote(field.getName())).append(':');
                    json.append(writeValue(field.get(value), visited));
                    first = false;
                }
            }
            return json.append('}').toString();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Impossible de lire l'objet " + value.getClass().getName(), exception);
        } finally {
            visited.remove(value);
        }
    }

    private static void appendValue(StringBuilder json, String value, boolean prependComma) {
        if (prependComma) {
            json.append(',');
        }
        json.append(value);
    }

    private static String quote(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"': escaped.append("\\\""); break;
                case '\\': escaped.append("\\\\"); break;
                case '\b': escaped.append("\\b"); break;
                case '\f': escaped.append("\\f"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default:
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
            }
        }
        return escaped.append('"').toString();
    }
}