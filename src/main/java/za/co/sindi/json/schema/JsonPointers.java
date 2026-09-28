/**
 * 
 */
package za.co.sindi.json.schema;

import jakarta.json.Json;
import jakarta.json.JsonPointer;

/**
 * Helpers around {@link jakarta.json.JsonPointer}.
 *
 * <p>JSON-P exposes pointers as immutable value objects created through {@link Json#createPointer}.
 * This utility provides RFC 6901 escaping and an append operation, so the validator can build the
 * location of a failing instance while it descends into the document.
 */
public final class JsonPointers {

    /** The pointer to the root of a document. */
    public static final JsonPointer ROOT = Json.createPointer("");

    private JsonPointers() {
    	throw new AssertionError("Private constructor.");
    }

    /** Appends an object member name. */
    public static JsonPointer append(JsonPointer pointer, String token) {
        return Json.createPointer(pointer.toString() + "/" + escape(token));
    }

    /** Appends an array index. */
    public static JsonPointer append(JsonPointer pointer, int index) {
        return append(pointer, Integer.toString(index));
    }

    /** RFC 6901: {@code ~} becomes {@code ~0} and {@code /} becomes {@code ~1}. */
    public static String escape(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }

    /** @return {@code "$"} for the root, otherwise the pointer with a {@code $} prefix. */
    public static String display(JsonPointer pointer) {
        String path = pointer.toString();
        return path.isEmpty() ? "$" : "$" + path;
    }
}
