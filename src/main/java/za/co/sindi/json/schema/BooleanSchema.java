/**
 * 
 */
package za.co.sindi.json.schema;

import java.net.URI;

import jakarta.json.JsonPointer;
import jakarta.json.JsonValue;

/**
 * A boolean schema: {@code true} accepts every instance, {@code false} rejects every instance.
 *
 * @param value    the boolean value
 * @param location canonical URI, may be {@code null}
 */
public record BooleanSchema(boolean value, URI location) implements JsonSchema {

    public static final BooleanSchema TRUE = new BooleanSchema(true, null);
    public static final BooleanSchema FALSE = new BooleanSchema(false, null);

    @Override
    public <R> R accept(SchemaVisitor<R> visitor, JsonValue instance, JsonPointer instancePath) {
        return visitor.visit(this, instance, instancePath);
    }

    @Override
    public String toString() {
        return Boolean.toString(value);
    }
}
