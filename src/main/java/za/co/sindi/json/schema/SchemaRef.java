/**
 * 
 */
package za.co.sindi.json.schema;

import java.net.URI;

/**
 * A lazily resolved {@code $ref}.
 *
 * <p>Resolution is deferred until validation time. That is what makes recursive schemas work: a
 * schema may reference itself (directly or through a cycle) and the reference is only followed when
 * an instance actually needs to be checked against it.
 *
 * <p>Resolution is thread safe (double checked locking on a {@code volatile} field) and cached.
 */
public final class SchemaRef {

    private final URI target;
    private final SchemaRegistry registry;

    private volatile JsonSchema resolved;

    SchemaRef(URI target, SchemaRegistry registry) {
        this.target = target;
        this.registry = registry;
    }

    /** @return the absolute URI this reference points at. */
    public URI target() {
        return target;
    }

    /** @return the referenced schema, loading and building it on first use. */
    public JsonSchema resolve() {
        JsonSchema result = resolved;
        if (result == null) {
            synchronized (this) {
                result = resolved;
                if (result == null) {
                    result = registry.load(target);
                    resolved = result;
                }
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "$ref -> " + target;
    }
}
