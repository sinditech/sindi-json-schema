/**
 * 
 */
package za.co.sindi.json.schema.validation;

import java.net.URI;

import jakarta.json.JsonPointer;
import za.co.sindi.json.schema.JsonPointers;

/**
 * A single validation failure.
 *
 * @param instancePath   where in the validated instance the failure occurred
 * @param schemaLocation the schema that produced the failure, may be {@code null}
 * @param message        human readable description
 */
public record ValidationError(JsonPointer instancePath, URI schemaLocation, String message) {

    @Override
    public String toString() {
        String path = JsonPointers.display(instancePath);
        return schemaLocation == null
                ? path + ": " + message
                : path + ": " + message + " [" + schemaLocation + "]";
    }
}
