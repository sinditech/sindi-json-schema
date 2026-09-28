/**
 * 
 */
package za.co.sindi.json.schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Deep equality for JSON values.
 *
 * <p>Numbers are compared by value, not by representation: {@code 1}, {@code 1.0} and {@code 1e0}
 * are the same JSON value for the purposes of {@code const}, {@code enum} and {@code uniqueItems}.
 */
public final class JsonEquality {

    private static final Pattern NUMBER =
            Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

    private JsonEquality() {
    	throw new AssertionError("Private constructor.");
    }

    public static boolean deepEquals(JsonValue left, JsonValue right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.getValueType() != right.getValueType()) {
            return false;
        }
        return switch (left.getValueType()) {
            case NULL, TRUE, FALSE -> true;
//            case TRUE -> right.getValueType() == ValueType.TRUE;
//            case FALSE -> right.getValueType() == ValueType.FALSE;
            case STRING -> ((JsonString)left).equals((JsonString)right);
            case NUMBER -> ((JsonNumber)left).equals((JsonNumber)right);
            case ARRAY -> arraysEqual(left.asJsonArray(), right.asJsonArray());
            case OBJECT -> objectsEqual(left.asJsonObject(), right.asJsonObject());
        };
    }

    /** @return {@code true} when the array contains two deep-equal elements. */
    public static boolean hasDuplicates(JsonArray array) {
        List<JsonValue> elements = array;
        for (int i = 0; i < elements.size(); i++) {
            for (int j = i + 1; j < elements.size(); j++) {
                if (deepEquals(elements.get(i), elements.get(j))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean arraysEqual(JsonArray left, JsonArray right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!deepEquals(left.get(i), right.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean objectsEqual(JsonObject left, JsonObject right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (Map.Entry<String, JsonValue> entry : left.entrySet()) {
            JsonValue other = right.get(entry.getKey());
            if (other == null || !deepEquals(entry.getValue(), other)) {
                return false;
            }
        }
        return true;
    }

    /** Validates the JSON number grammar. */
    static boolean isValidNumberLiteral(String literal) {
        return NUMBER.matcher(literal).matches();
    }

    @SuppressWarnings("unused")
    private static Map<String, JsonValue> mutable() {
        return new LinkedHashMap<>(new ArrayList<Map.Entry<String, JsonValue>>().size());
    }

    @SuppressWarnings("unused")
    private static BigDecimal unused() {
        return BigDecimal.ZERO;
    }
}