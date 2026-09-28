/**
 * 
 */
package za.co.sindi.json.schema;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.json.JsonObject;
import jakarta.json.JsonPointer;
import jakarta.json.JsonValue;

/**
 * An object schema: a bag of optional keywords.
 *
 * <p>Keywords are grouped into small value records so the type stays readable and each group can be
 * reasoned about (and unit tested) on its own. Every group has an {@code EMPTY} instance, so callers
 * never need null checks.
 */
public record ObjectSchema(
        URI location,
        JsonObject raw,
        SchemaRef ref,
        Metadata metadata,
        GenericConstraints generic,
        NumericConstraints numeric,
        StringConstraints string,
        ArrayConstraints array,
        ObjectConstraints object,
        Applicators applicators
) implements JsonSchema {

    public ObjectSchema {
        metadata = metadata == null ? Metadata.EMPTY : metadata;
        generic = generic == null ? GenericConstraints.EMPTY : generic;
        numeric = numeric == null ? NumericConstraints.EMPTY : numeric;
        string = string == null ? StringConstraints.EMPTY : string;
        array = array == null ? ArrayConstraints.EMPTY : array;
        object = object == null ? ObjectConstraints.EMPTY : object;
        applicators = applicators == null ? Applicators.EMPTY : applicators;
    }

    @Override
    public <R> R accept(SchemaVisitor<R> visitor, JsonValue instance, JsonPointer instancePath) {
        return visitor.visit(this, instance, instancePath);
    }

    /** @return {@code true} when the schema declares no assertion whatsoever. */
    public boolean isTrivial() {
        return ref == null
                && generic.equals(GenericConstraints.EMPTY)
                && numeric.equals(NumericConstraints.EMPTY)
                && string.equals(StringConstraints.EMPTY)
                && array.equals(ArrayConstraints.EMPTY)
                && object.equals(ObjectConstraints.EMPTY)
                && applicators.equals(Applicators.EMPTY);
    }

    // ------------------------------------------------------------------ keyword groups

    /** Annotation keywords. They never affect validation outcome. */
    public record Metadata(
            String title,
            String description,
            JsonValue defaultValue,
            List<JsonValue> examples,
            Boolean deprecated,
            Boolean readOnly,
            Boolean writeOnly
    ) {
        public Metadata {
            examples = examples == null ? List.of() : List.copyOf(examples);
        }

        public static final Metadata EMPTY =
                new Metadata(null, null, null, List.of(), null, null, null);
    }

    /** {@code type}, {@code const} and {@code enum}. */
    public record GenericConstraints(Set<SchemaType> types, JsonValue constValue, List<JsonValue> enumValues) {

        public GenericConstraints {
            types = types == null
                    ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(types));
            enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        }

        public static final GenericConstraints EMPTY =
                new GenericConstraints(Set.of(), null, List.of());
    }

    /** Numeric assertion keywords. */
    public record NumericConstraints(
            BigDecimal multipleOf,
            BigDecimal minimum,
            BigDecimal maximum,
            BigDecimal exclusiveMinimum,
            BigDecimal exclusiveMaximum
    ) {
        public static final NumericConstraints EMPTY =
                new NumericConstraints(null, null, null, null, null);
    }

    /** String assertion keywords. */
    public record StringConstraints(Integer minLength, Integer maxLength, Pattern pattern, String format) {
        public static final StringConstraints EMPTY =
                new StringConstraints(null, null, null, null);
    }

    /** Array assertion keywords. */
    public record ArrayConstraints(
            Integer minItems,
            Integer maxItems,
            Boolean uniqueItems,
            JsonSchema items,
            List<JsonSchema> prefixItems,
            JsonSchema contains,
            Integer minContains,
            Integer maxContains
    ) {
        public ArrayConstraints {
            prefixItems = prefixItems == null ? List.of() : List.copyOf(prefixItems);
        }

        public static final ArrayConstraints EMPTY =
                new ArrayConstraints(null, null, null, null, List.of(), null, null, null);
    }

    /** Object assertion keywords. */
    public record ObjectConstraints(
            Integer minProperties,
            Integer maxProperties,
            List<String> required,
            Map<String, JsonSchema> properties,
            Map<Pattern, JsonSchema> patternProperties,
            JsonSchema additionalProperties,
            JsonSchema propertyNames,
            Map<String, List<String>> dependentRequired,
            Map<String, JsonSchema> dependentSchemas
    ) {
        public ObjectConstraints {
            required = required == null ? List.of() : List.copyOf(required);
            properties = properties == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
            patternProperties = patternProperties == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(patternProperties));
            dependentRequired = dependentRequired == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(dependentRequired));
            dependentSchemas = dependentSchemas == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(dependentSchemas));
        }

        public static final ObjectConstraints EMPTY = new ObjectConstraints(
                null, null, List.of(), Map.of(), Map.of(), null, null, Map.of(), Map.of());
    }

    /** Combining / conditional applicators. */
    public record Applicators(
            List<JsonSchema> allOf,
            List<JsonSchema> anyOf,
            List<JsonSchema> oneOf,
            JsonSchema not,
            JsonSchema ifSchema,
            JsonSchema thenSchema,
            JsonSchema elseSchema
    ) {
        public Applicators {
            allOf = allOf == null ? List.of() : List.copyOf(allOf);
            anyOf = anyOf == null ? List.of() : List.copyOf(anyOf);
            oneOf = oneOf == null ? List.of() : List.copyOf(oneOf);
        }

        public static final Applicators EMPTY =
                new Applicators(List.of(), List.of(), List.of(), null, null, null, null);
    }
}
