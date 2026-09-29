/**
 * 
 */
package za.co.sindi.json.schema.dialect;

import java.net.URI;
import java.util.Set;

/** JSON Schema draft-07. */
public final class Draft07Dialect extends AbstractDialect {

    public static final Draft07Dialect INSTANCE = new Draft07Dialect();

    private static final Set<URI> META_SCHEMAS = Set.of(
            URI.create("http://json-schema.org/draft-07/schema#"),
            URI.create("http://json-schema.org/draft-07/schema"),
            URI.create("https://json-schema.org/draft-07/schema#"),
            URI.create("https://json-schema.org/draft-07/schema"));

    @Override
    public String name() {
        return "JSON Schema draft-07";
    }

    @Override
    public Set<URI> metaSchemaUris() {
        return META_SCHEMAS;
    }

    @Override
    public Set<String> definitionsKeywords() {
        return Set.of("definitions");
    }
}
