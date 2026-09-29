/**
 * 
 */
package za.co.sindi.json.schema.dialect;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Set;

import za.co.sindi.json.schema.ObjectSchema;
import za.co.sindi.json.schema.SchemaException;

/**
 * JSON Schema draft-04.
 *
 * <p>Three things set it apart: identifiers use {@code id}, {@code exclusiveMinimum} and
 * {@code exclusiveMaximum} are boolean modifiers of {@code minimum} and {@code maximum}, and
 * {@code required} is a boolean on each property rather than an array on the parent. The last one
 * is not modelled here; see the note at the end.
 */
public final class Draft04Dialect extends AbstractDialect {

    public static final Draft04Dialect INSTANCE = new Draft04Dialect();

    private static final Set<URI> META_SCHEMAS = Set.of(
            URI.create("http://json-schema.org/draft-04/schema#"),
            URI.create("http://json-schema.org/draft-04/schema"),
            URI.create("https://json-schema.org/draft-04/schema#"),
            URI.create("https://json-schema.org/draft-04/schema"));

    @Override
    public String name() {
        return "JSON Schema draft-04";
    }

    @Override
    public Set<URI> metaSchemaUris() {
        return META_SCHEMAS;
    }

    @Override
    public String idKeyword() {
        return "id";
    }

    @Override
    public Set<String> definitionsKeywords() {
        return Set.of("definitions");
    }

    @Override
    public ObjectSchema.NumericConstraints readNumericConstraints(KeywordContext ctx) {
        BigDecimal minimum = ctx.number("minimum");
        BigDecimal maximum = ctx.number("maximum");
        BigDecimal exclusiveMinimum = null;
        BigDecimal exclusiveMaximum = null;

        if (Boolean.TRUE.equals(ctx.bool("exclusiveMinimum"))) {
            if (minimum == null) {
                throw new SchemaException("'exclusiveMinimum' requires 'minimum' in draft-04");
            }
            exclusiveMinimum = minimum;
            minimum = null;
        }
        if (Boolean.TRUE.equals(ctx.bool("exclusiveMaximum"))) {
            if (maximum == null) {
                throw new SchemaException("'exclusiveMaximum' requires 'maximum' in draft-04");
            }
            exclusiveMaximum = maximum;
            maximum = null;
        }

        return new ObjectSchema.NumericConstraints(
                ctx.number("multipleOf"), minimum, maximum, exclusiveMinimum, exclusiveMaximum);
    }
}

