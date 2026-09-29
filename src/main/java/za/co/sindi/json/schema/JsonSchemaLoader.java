/**
 * 
 */
package za.co.sindi.json.schema;

import java.net.URI;

import za.co.sindi.json.schema.dialect.Dialect;
import za.co.sindi.json.schema.dialect.DialectRegistry;

/**
 * Facade that most applications interact with.
 *
 * <pre>{@code
 * JsonSchemaLoader loader = new JsonSchemaLoader();
 * JsonSchema schema = loader.load(URI.create("https://example.com/schemas/person.json"));
 * }</pre>
 */
public final class JsonSchemaLoader {

    private final SchemaRegistry registry;

    public JsonSchemaLoader() {
        this(SchemaRegistry.withDefaultSource());
    }

    public JsonSchemaLoader(SchemaRegistry registry) {
        this.registry = registry;
    }
    
    public JsonSchemaLoader(Dialect defaultDialect) {
        this(new SchemaRegistry(SchemaSource.defaults(), DialectRegistry.defaults()));
    }

    public SchemaRegistry registry() {
        return registry;
    }

//    /** Loads a schema from an absolute URL, fetching it if necessary. */
//    public JsonSchema load(String url) {
//        return registry.load(url);
//    }

    public JsonSchema load(URI uri) {
        return registry.load(uri);
    }

    /** Builds a schema from an in-memory document using a synthetic base URI. */
    public JsonSchema parse(String json) {
        return registry.registerDocument(
                URI.create("urn:jsonschema:inline:" + Integer.toHexString(json.hashCode())), json);
    }

    /** Builds a schema from an in-memory document using {@code baseUri} for relative references. */
    public JsonSchema parse(String json, URI baseUri) {
        return registry.registerDocument(baseUri, json);
    }
}
