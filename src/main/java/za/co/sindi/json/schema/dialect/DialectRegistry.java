/**
 * 
 */
package za.co.sindi.json.schema.dialect;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import za.co.sindi.json.schema.SchemaException;

/** Maps {@code $schema} URIs to {@link Dialect} implementations. */
public final class DialectRegistry {

    private final Map<URI, Dialect> byMetaSchema = new HashMap<>();
    private final Dialect fallback;

    public DialectRegistry(Dialect fallback) {
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    /** @return a registry that knows draft-04, draft-07 and 2020-12, defaulting to 2020-12. */
    public static DialectRegistry defaults() {
        return new DialectRegistry(Draft202012Dialect.INSTANCE)
                .register(Draft04Dialect.INSTANCE)
                .register(Draft07Dialect.INSTANCE)
                .register(Draft202012Dialect.INSTANCE);
    }

    public DialectRegistry register(Dialect dialect) {
        for (URI uri : dialect.metaSchemaUris()) {
            byMetaSchema.put(uri, dialect);
        }
        return this;
    }

    public Dialect defaultDialect() {
        return fallback;
    }

    /**
     * @param metaSchemaUri the value of a {@code $schema} keyword
     * @throws SchemaException when the URI is not recognised; silently falling back would hide a
     *                         genuine dialect mismatch
     */
    public Dialect resolve(URI metaSchemaUri) {
        Dialect dialect = byMetaSchema.get(metaSchemaUri);
        if (dialect == null) {
            throw new SchemaException("Unsupported JSON Schema dialect: " + metaSchemaUri);
        }
        return dialect;
    }
}

