/**
 * 
 */
package za.co.sindi.json.schema;


import java.net.URI;

import jakarta.json.JsonPointer;
import jakarta.json.JsonValue;

/**
 * A JSON Schema node.
 *
 * <p>The hierarchy is sealed: JSON Schema permits exactly two node shapes, a boolean schema and an
 * object schema.
 */
public sealed interface JsonSchema permits BooleanSchema, ObjectSchema {

    /** Canonical URI of this schema inside the registry, or {@code null} for ad hoc schemas. */
    URI location();

    <R> R accept(SchemaVisitor<R> visitor, JsonValue instance, JsonPointer instancePath);
}
