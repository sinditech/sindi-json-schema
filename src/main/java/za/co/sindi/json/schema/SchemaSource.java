/**
 * 
 */
package za.co.sindi.json.schema;

import java.io.IOException;
import java.net.URI;

/**
 * Strategy for retrieving the raw text of a schema document.
 *
 * <p>Replacing the source is the main extension point for offline operation, caching, or embedding
 * schemas in a classpath / database.
 */
@FunctionalInterface
public interface SchemaSource {

    String fetch(URI uri) throws IOException;

    /** @return a source that understands {@code http}, {@code https} and {@code file} URIs. */
    static SchemaSource defaults() {
        return DefaultSchemaSource.INSTANCE;
    }
}