/**
 * 
 */
package za.co.sindi.json.schema.dialect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.JsonArray;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import za.co.sindi.json.schema.JsonSchema;
import za.co.sindi.json.schema.ObjectSchema;
import za.co.sindi.json.schema.SchemaException;

/**
 * Shared behaviour for every draft from 06 onward, plus the transitional handling of the tuple
 * form of {@code items}, which 2019-09 still supports.
 */
public abstract class AbstractDialect implements Dialect {

    @Override
    public ObjectSchema.NumericConstraints readNumericConstraints(KeywordContext ctx) {
        return new ObjectSchema.NumericConstraints(
                ctx.number("multipleOf"),
                ctx.number("minimum"),
                ctx.number("maximum"),
                ctx.number("exclusiveMinimum"),
                ctx.number("exclusiveMaximum"));
    }

    /**
     * Handles both the modern form ({@code prefixItems} + {@code items} schema) and the legacy
     * tuple form ({@code items} array + {@code additionalItems}). Draft 2020-12 overrides this to
     * drop the tuple form entirely.
     */
    @Override
    public ObjectSchema.ArrayConstraints readArrayConstraints(KeywordContext ctx) {
        List<JsonSchema> prefixItems = new ArrayList<>(ctx.subschemaList("prefixItems"));
        JsonSchema items = null;

        if (ctx.has("items")) {
            JsonValue itemsNode = ctx.node("items");
            if (itemsNode instanceof JsonArray tuple) {
                for (int i = 0; i < tuple.size(); i++) {
                    prefixItems.add(ctx.subschemaAt("items", i));
                }
                items = ctx.subschema("additionalItems");
            } else {
                items = ctx.subschema("items");
            }
        }

        return new ObjectSchema.ArrayConstraints(
                ctx.integer("minItems"),
                ctx.integer("maxItems"),
                ctx.bool("uniqueItems"),
                items,
                prefixItems,
                ctx.subschema("contains"),
                ctx.integer("minContains"),
                ctx.integer("maxContains"));
    }

    @Override
    public ObjectSchema.ObjectConstraints readObjectConstraints(KeywordContext ctx) {
        return new ObjectSchema.ObjectConstraints(
                ctx.integer("minProperties"),
                ctx.integer("maxProperties"),
                ctx.stringList("required"),
                ctx.subschemaMap("properties"),
                ctx.patternSubschemaMap("patternProperties"),
                ctx.subschema("additionalProperties"),
                ctx.subschema("propertyNames"),
                readDependentRequired(ctx),
                ctx.subschemaMap("dependentSchemas"));
    }

    private Map<String, List<String>> readDependentRequired(KeywordContext ctx) {
        JsonValue node = ctx.node("dependentRequired");
        if (node == null) {
            return Map.of();
        }
        if (!(node instanceof jakarta.json.JsonObject object)) {
            throw new SchemaException("'dependentRequired' must be an object");
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonValue> entry : object.entrySet()) {
            if (!(entry.getValue() instanceof JsonArray array)) {
                throw new SchemaException("'dependentRequired' values must be arrays of strings");
            }
            List<String> names = new ArrayList<>(array.size());
            for (JsonValue value : array) {
                if (!(value instanceof JsonString string)) {
                    throw new SchemaException("'dependentRequired' values must be arrays of strings");
                }
                names.add(string.getString());
            }
            result.put(entry.getKey(), List.copyOf(names));
        }
        return result;
    }

    // ---- shared defaults ---------------------------------------------------------

    @Override
    public String idKeyword() {
        return "$id";
    }

    @Override
    public boolean appliesRefSiblings() {
        return false;
    }

    @Override
    public boolean formatIsAssertion() {
        return true;
    }
}
