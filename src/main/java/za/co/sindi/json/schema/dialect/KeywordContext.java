/**
 * 
 */
package za.co.sindi.json.schema.dialect;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.json.JsonValue;
import za.co.sindi.json.schema.JsonSchema;

/**
 * The reader's view of the schema node currently being interpreted.
 *
 * <p>Every accessor is null safe: absent keywords return {@code null} (or an empty collection),
 * and shape mismatches raise {@link com.example.jsonschema.schema.SchemaException}. Recursion into
 * subschemas stays in the reader, so a dialect never has to think about pointers or base URIs.
 */
public interface KeywordContext {

    boolean has(String keyword);

    JsonValue node(String keyword);

    String string(String keyword);

    Boolean bool(String keyword);

    BigDecimal number(String keyword);

    Integer integer(String keyword);

    Pattern pattern(String keyword);

    List<String> stringList(String keyword);

    /** Reads the subschema under {@code keyword}, or {@code null} when absent. */
    JsonSchema subschema(String keyword);

    /** Reads index {@code index} of an array valued keyword, e.g. the tuple form of {@code items}. */
    JsonSchema subschemaAt(String keyword, int index);

    List<JsonSchema> subschemaList(String keyword);

    Map<String, JsonSchema> subschemaMap(String keyword);

    Map<Pattern, JsonSchema> patternSubschemaMap(String keyword);
}
