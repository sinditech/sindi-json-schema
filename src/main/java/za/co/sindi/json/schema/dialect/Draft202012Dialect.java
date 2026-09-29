/**
 * 
 */
package za.co.sindi.json.schema.dialect;

import java.net.URI;
import java.util.Set;

import za.co.sindi.json.schema.ObjectSchema;

/** JSON Schema 2020-12. */
public final class Draft202012Dialect extends AbstractDialect {

    public static final Draft202012Dialect INSTANCE = new Draft202012Dialect();

    private static final Set<URI> META_SCHEMAS = Set.of(
            URI.create("https://json-schema.org/draft/2020-12/schema"),
            URI.create("https://json-schema.org/draft/2020-12/schema#"));

    @Override
    public String name() {
        return "JSON Schema 2020-12";
    }

    @Override
    public Set<URI> metaSchemaUris() {
        return META_SCHEMAS;
    }

    @Override
    public Set<String> definitionsKeywords() {
        // Accept the legacy keyword as well, which most implementations do in practice.
        return Set.of("$defs", "definitions");
    }

    @Override
    public boolean appliesRefSiblings() {
        return true;
    }

    /** 2020-12 replaced the tuple form of {@code items} with {@code prefixItems}. */
    @Override
    public ObjectSchema.ArrayConstraints readArrayConstraints(KeywordContext ctx) {
        return new ObjectSchema.ArrayConstraints(
                ctx.integer("minItems"),
                ctx.integer("maxItems"),
                ctx.bool("uniqueItems"),
                ctx.subschema("items"),
                ctx.subschemaList("prefixItems"),
                ctx.subschema("contains"),
                ctx.integer("minContains"),
                ctx.integer("maxContains"));
    }

    /** 2020-12 dropped {@code dependencies} in favour of the two split keywords. */
    @Override
    public ObjectSchema.ObjectConstraints readObjectConstraints(KeywordContext ctx) {
        return super.readObjectConstraints(ctx);
    }
}
