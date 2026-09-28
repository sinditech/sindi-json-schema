/**
 * 
 */
package za.co.sindi.json.schema;

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import java.math.BigDecimal;

/**
 * Read-only helpers around the {@link jakarta.json} model.
 *
 * <p>JSON-P deliberately provides no null-safe accessors, so these methods keep the schema reader
 * free of repetitive casts and null checks.
 */
public final class JsonValues {

    private JsonValues() {
    	throw new AssertionError("Private constructor.");
    }

    /** Lower case JSON type name, e.g. {@code "object"}. */
    public static String typeName(JsonValue value) {
        return switch (value.getValueType()) {
            case OBJECT -> "object";
            case ARRAY -> "array";
            case STRING -> "string";
            case NUMBER -> "number";
            case TRUE, FALSE -> "boolean";
            case NULL -> "null";
        };
    }

    public static boolean isBoolean(JsonValue value) {
        JsonValue.ValueType type = value.getValueType();
        return type == JsonValue.ValueType.TRUE || type == JsonValue.ValueType.FALSE;
    }

    public static String getString(JsonObject owner, String keyword) {
        return owner.get(keyword) instanceof JsonString string ? string.getString() : null;
    }

    public static BigDecimal getNumber(JsonObject owner, String keyword) {
        return owner.get(keyword) instanceof JsonNumber number ? number.bigDecimalValue() : null;
    }

    public static Boolean getBoolean(JsonObject owner, String keyword) {
        JsonValue value = owner.get(keyword);
        if (value == null) {
            return null;
        }
        return switch (value.getValueType()) {
            case TRUE -> Boolean.TRUE;
            case FALSE -> Boolean.FALSE;
            default -> null;
        };
    }

    public static JsonObject getObject(JsonObject owner, String keyword) {
        return owner.get(keyword) instanceof JsonObject object ? object : null;
    }

    public static JsonArray getArray(JsonObject owner, String keyword) {
        return owner.get(keyword) instanceof JsonArray array ? array : null;
    }
}
