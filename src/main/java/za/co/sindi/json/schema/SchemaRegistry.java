/**
 * 
 */
package za.co.sindi.json.schema;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

/**
 * Central store of schema documents and their subschemas, keyed by canonical URI.
 *
 * <p>This is the <em>flyweight</em> of the library: a document is fetched, parsed and built at most
 * once, and every subschema is registered under both its canonical {@code $id} URI and its JSON
 * Pointer location inside the containing document. {@code $ref} resolution is then a plain map
 * lookup.
 *
 * <p>Documents are loaded and registered before any reference is followed, so cyclic schemas are
 * supported.
 */
public final class SchemaRegistry {

    private final SchemaSource source;

    private final Map<URI, JsonSchema> schemas = new HashMap<>();
    private final Map<URI, JsonValue> documents = new HashMap<>();
    private final Set<URI> builtDocuments = new HashSet<>();

    public SchemaRegistry(SchemaSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /** @return a registry that can fetch over HTTP(S) and from the file system. */
    public static SchemaRegistry withDefaultSource() {
        return new SchemaRegistry(SchemaSource.defaults());
    }

    public SchemaSource source() {
        return source;
    }

    // ------------------------------------------------------------------ loading

//    public JsonSchema load(String url) {
//        return load(URI.create(url));
//    }

    /**
     * Loads the schema identified by {@code uri}, fetching and building it on first use.
     *
     * @param uri absolute URI, optionally with a JSON Pointer fragment
     */
    public synchronized JsonSchema load(URI uri) {
        URI target = requireAbsolute(uri);
        URI documentUri = withoutFragment(target);

        if (builtDocuments.add(documentUri)) {
            try {
                JsonValue document = documents.get(documentUri);
                if (document == null) {
                    document = parse(source.fetch(documentUri));
                    documents.put(documentUri, document);
                }
                new SchemaReader(this, documentUri).read(document, documentUri, documentUri);
            } catch (RuntimeException | IOException e) {
                builtDocuments.remove(documentUri);
                if (e instanceof IOException ioe) throw new UncheckedIOException(ioe);
                throw (RuntimeException) e;
            }
        }

        JsonSchema schema = schemas.get(target);
        if (schema == null) {
            throw new SchemaException("No JSON Schema found at " + target);
        }
        return schema;
    }

    /** Parses {@code json} and registers it under {@code uri} without hitting the network. */
    public synchronized JsonSchema registerDocument(URI uri, String json) {
        URI documentUri = withoutFragment(requireAbsolute(uri));
        documents.put(documentUri, parse(json));
        builtDocuments.remove(documentUri);
        return load(documentUri);
    }

    /** Registers an already built schema. First registration wins. */
    public synchronized void register(URI location, JsonSchema schema) {
        schemas.putIfAbsent(Objects.requireNonNull(location, "location"), schema);
    }

    /** @return the schema registered at {@code location}, or {@code null}. */
    public synchronized JsonSchema lookup(URI location) {
        return schemas.get(location);
    }

    // ------------------------------------------------------------------ helpers

    private static JsonValue parse(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readValue();
        }
    }

    private static URI requireAbsolute(URI uri) {
        Objects.requireNonNull(uri, "uri");
        if (!uri.isAbsolute()) {
            throw new SchemaException("Schema URI must be absolute: " + uri);
        }
        return uri.normalize();
    }

    private static URI withoutFragment(URI uri) {
        try {
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), null);
        } catch (URISyntaxException e) {
            throw new SchemaException("Invalid schema URI: " + uri, e);
        }
    }
}
