/**
 * 
 */
package za.co.sindi.json.schema;

import jakarta.json.JsonPointer;
import jakarta.json.JsonValue;

/**
 * Visitor over the JSON Schema composite tree.
 *
 * <p>Keeping traversal outside the schema classes means new operations (printing, dialect
 * conversion, code generation, validation) can be added without touching the model.
 *
 * @param <R> result type of the traversal
 */
public interface SchemaVisitor<R> {

    R visit(BooleanSchema schema, JsonValue instance, JsonPointer instancePath);

    R visit(ObjectSchema schema, JsonValue instance, JsonPointer instancePath);
}
