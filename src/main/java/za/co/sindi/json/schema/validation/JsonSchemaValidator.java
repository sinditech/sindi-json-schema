/**
 * 
 */
package za.co.sindi.json.schema.validation;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonPointer;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import za.co.sindi.json.schema.BooleanSchema;
import za.co.sindi.json.schema.JsonPointers;
import za.co.sindi.json.schema.JsonSchema;
import za.co.sindi.json.schema.ObjectSchema;
import za.co.sindi.json.schema.SchemaType;
import za.co.sindi.json.schema.SchemaVisitor;
import za.co.sindi.json.schema.dialect.Dialect;

/**
 * Validates JSON instances against {@link JsonSchema} trees.
 *
 * <p>Traversal is implemented with the {@link SchemaVisitor} pattern. Each validation run creates a
 * private {@code Session}, so a single validator instance is immutable and safe to share across
 * threads.
 *
 * <p>Validation is exhaustive rather than fail fast: every assertion is evaluated so the caller gets
 * the complete list of problems in one pass.
 */
public final class JsonSchemaValidator {

    private final Map<String, FormatValidator> formats;
    private final int maxDepth;

    public JsonSchemaValidator() {
        this(FormatValidator.defaults(), 256);
    }

    public JsonSchemaValidator(Map<String, FormatValidator> formats, int maxDepth) {
        this.formats = Map.copyOf(formats);
        this.maxDepth = maxDepth;
    }

    public ValidationResult validate(JsonSchema schema, JsonValue instance) {
        Session session = new Session(this, 0);
        session.evaluate(schema, instance, JsonPointers.ROOT);
        return new ValidationResult(session.errors);
    }

    /** Parses {@code json} with JSON-P and validates the resulting value. */
    public ValidationResult validate(JsonSchema schema, String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return validate(schema, reader.readValue());
        }
    }

    // ================================================================== session

    private static final class Session implements SchemaVisitor<Boolean> {

        private final JsonSchemaValidator validator;
        private final int depth;
        private final List<ValidationError> errors = new ArrayList<>();

        Session(JsonSchemaValidator validator, int depth) {
            this.validator = validator;
            this.depth = depth;
        }

        boolean evaluate(JsonSchema schema, JsonValue instance, JsonPointer path) {
            if (depth > validator.maxDepth) {
                errors.add(new ValidationError(path, schema.location(),
                        "Maximum validation depth exceeded; a cyclic $ref was probably applied "
                                + "without consuming any of the instance"));
                return false;
            }
            return Boolean.TRUE.equals(schema.accept(this, instance, path));
        }

        boolean matches(JsonSchema schema, JsonValue instance, JsonPointer path) {
            Session sub = new Session(validator, depth + 1);
            sub.evaluate(schema, instance, path);
            return sub.errors.isEmpty();
        }

        void error(JsonPointer path, JsonSchema schema, String message) {
            errors.add(new ValidationError(path, schema.location(), message));
        }

        // -------------------------------------------------------------- visitor

        @Override
        public Boolean visit(BooleanSchema schema, JsonValue instance, JsonPointer path) {
            if (!schema.value()) {
                error(path, schema, "Schema 'false' rejects every instance");
                return false;
            }
            return true;
        }

        @Override
        public Boolean visit(ObjectSchema schema, JsonValue instance, JsonPointer path) {
            Dialect dialect = schema.dialect();
            boolean valid = true;

            if (schema.ref() != null) {
                valid = evaluate(schema.ref().resolve(), instance, path);
                if (!dialect.appliesRefSiblings()) {
                    // draft-07 and earlier: presence of $ref means every sibling is ignored.
                    return valid;
                }
            }

            ObjectSchema.GenericConstraints generic = schema.generic();
            if (!generic.types().isEmpty()
                    && generic.types().stream().noneMatch(type -> matchesType(type, instance))) {
                error(path, schema, "Expected " + describe(generic.types())
                        + " but found " + typeName(instance));
                // Type specific keywords do not apply, so stop here for this branch.
                return false;
            }

            if (generic.constValue() != null && !deepEquals(generic.constValue(), instance)) {
                error(path, schema, "Value does not match 'const'");
                valid = false;
            }
            if (!generic.enumValues().isEmpty()
                    && generic.enumValues().stream().noneMatch(candidate -> deepEquals(candidate, instance))) {
                error(path, schema, "Value is not one of the values in 'enum'");
                valid = false;
            }

            switch (instance.getValueType()) {
                case NUMBER -> valid &= checkNumeric(schema, (JsonNumber) instance, path);
                case STRING -> valid &= checkString(schema, ((JsonString) instance).getString(), path);
                case ARRAY -> valid &= checkArray(schema, instance.asJsonArray(), path);
                case OBJECT -> valid &= checkObject(schema, instance.asJsonObject(), path);
                default -> {
                    // No type specific assertions apply to booleans or null.
                }
            }

            valid &= checkApplicators(schema, instance, path);
            return valid;
        }

        // -------------------------------------------------------------- numeric

        private boolean checkNumeric(ObjectSchema schema, JsonNumber number, JsonPointer path) {
            ObjectSchema.NumericConstraints c = schema.numeric();
            BigDecimal value = number.bigDecimalValue();
            boolean valid = true;

            if (c.multipleOf() != null) {
                if (c.multipleOf().signum() <= 0) {
                    throw new IllegalStateException("'multipleOf' must be greater than zero");
                }
                if (value.remainder(c.multipleOf()).compareTo(BigDecimal.ZERO) != 0) {
                    error(path, schema, "Value " + value + " is not a multiple of " + c.multipleOf());
                    valid = false;
                }
            }
            if (c.minimum() != null && value.compareTo(c.minimum()) < 0) {
                error(path, schema, "Value " + value + " is less than the minimum " + c.minimum());
                valid = false;
            }
            if (c.maximum() != null && value.compareTo(c.maximum()) > 0) {
                error(path, schema, "Value " + value + " is greater than the maximum " + c.maximum());
                valid = false;
            }
            if (c.exclusiveMinimum() != null && value.compareTo(c.exclusiveMinimum()) <= 0) {
                error(path, schema, "Value " + value + " must be greater than " + c.exclusiveMinimum());
                valid = false;
            }
            if (c.exclusiveMaximum() != null && value.compareTo(c.exclusiveMaximum()) >= 0) {
                error(path, schema, "Value " + value + " must be less than " + c.exclusiveMaximum());
                valid = false;
            }
            return valid;
        }

        // -------------------------------------------------------------- string

        private boolean checkString(ObjectSchema schema, String value, JsonPointer path) {
            ObjectSchema.StringConstraints c = schema.string();
            boolean valid = true;

            int length = value.codePointCount(0, value.length());
            if (c.minLength() != null && length < c.minLength()) {
                error(path, schema, "String is shorter than minLength " + c.minLength());
                valid = false;
            }
            if (c.maxLength() != null && length > c.maxLength()) {
                error(path, schema, "String is longer than maxLength " + c.maxLength());
                valid = false;
            }
            if (c.pattern() != null && !c.pattern().matcher(value).find()) {
                error(path, schema, "String does not match pattern '" + c.pattern().pattern() + "'");
                valid = false;
            }
            if (c.format() != null && schema.dialect().formatIsAssertion()) {
                FormatValidator formatValidator = validator.formats.get(c.format());
                if (formatValidator != null && !formatValidator.isValid(value)) {
                    error(path, schema, "String is not a valid '" + c.format() + "'");
                    valid = false;
                }
            }
            return valid;
        }

        // -------------------------------------------------------------- array

        private boolean checkArray(ObjectSchema schema, JsonArray array, JsonPointer path) {
            ObjectSchema.ArrayConstraints c = schema.array();
            boolean valid = true;

            if (c.minItems() != null && array.size() < c.minItems()) {
                error(path, schema, "Array has fewer than minItems " + c.minItems());
                valid = false;
            }
            if (c.maxItems() != null && array.size() > c.maxItems()) {
                error(path, schema, "Array has more than maxItems " + c.maxItems());
                valid = false;
            }
            if (Boolean.TRUE.equals(c.uniqueItems()) && hasDuplicates(array)) {
                error(path, schema, "Array items are not unique");
                valid = false;
            }

            List<JsonSchema> prefixItems = c.prefixItems();
            for (int i = 0; i < array.size(); i++) {
                JsonValue element = array.get(i);
                JsonPointer elementPath = JsonPointers.append(path, i);
                if (i < prefixItems.size()) {
                    valid &= evaluate(prefixItems.get(i), element, elementPath);
                } else if (c.items() != null) {
                    valid &= evaluate(c.items(), element, elementPath);
                }
            }

            if (c.contains() != null) {
                int matched = 0;
                for (JsonValue element : array) {
                    if (matches(c.contains(), element, path)) {
                        matched++;
                    }
                }
                int min = c.minContains() == null ? 1 : c.minContains();
                int max = c.maxContains() == null ? Integer.MAX_VALUE : c.maxContains();
                if (matched < min || matched > max) {
                    error(path, schema, "Array contains " + matched
                            + " matching item(s), expected between " + min + " and " + max);
                    valid = false;
                }
            }
            return valid;
        }

        // -------------------------------------------------------------- object

        private boolean checkObject(ObjectSchema schema, JsonObject object, JsonPointer path) {
            ObjectSchema.ObjectConstraints c = schema.object();
            boolean valid = true;

            if (c.minProperties() != null && object.size() < c.minProperties()) {
                error(path, schema, "Object has fewer than minProperties " + c.minProperties());
                valid = false;
            }
            if (c.maxProperties() != null && object.size() > c.maxProperties()) {
                error(path, schema, "Object has more than maxProperties " + c.maxProperties());
                valid = false;
            }
            for (String name : c.required()) {
                if (!object.containsKey(name)) {
                    error(path, schema, "Missing required property '" + name + "'");
                    valid = false;
                }
            }

            Set<String> evaluated = new HashSet<>();
            for (Map.Entry<String, JsonValue> entry : object.entrySet()) {
                String name = entry.getKey();
                JsonPointer childPath = JsonPointers.append(path, name);

                JsonSchema propertySchema = c.properties().get(name);
                if (propertySchema != null) {
                    evaluated.add(name);
                    valid &= evaluate(propertySchema, entry.getValue(), childPath);
                }
                for (Map.Entry<Pattern, JsonSchema> patternEntry : c.patternProperties().entrySet()) {
                    if (patternEntry.getKey().matcher(name).find()) {
                        evaluated.add(name);
                        valid &= evaluate(patternEntry.getValue(), entry.getValue(), childPath);
                    }
                }
            }

            if (c.propertyNames() != null) {
                for (String name : object.keySet()) {
                    valid &= evaluate(c.propertyNames(), Json.createValue(name),
                            JsonPointers.append(path, name));
                }
            }

            if (c.additionalProperties() != null) {
                for (Map.Entry<String, JsonValue> entry : object.entrySet()) {
                    if (!evaluated.contains(entry.getKey())) {
                        valid &= evaluate(c.additionalProperties(), entry.getValue(),
                                JsonPointers.append(path, entry.getKey()));
                    }
                }
            }

            for (Map.Entry<String, List<String>> dependency : c.dependentRequired().entrySet()) {
                if (object.containsKey(dependency.getKey())) {
                    for (String required : dependency.getValue()) {
                        if (!object.containsKey(required)) {
                            error(path, schema, "Property '" + required + "' is required when '"
                                    + dependency.getKey() + "' is present");
                            valid = false;
                        }
                    }
                }
            }

            for (Map.Entry<String, JsonSchema> dependency : c.dependentSchemas().entrySet()) {
                if (object.containsKey(dependency.getKey())) {
                    valid &= evaluate(dependency.getValue(), object, path);
                }
            }
            return valid;
        }

        // -------------------------------------------------------------- applicators

        private boolean checkApplicators(ObjectSchema schema, JsonValue instance, JsonPointer path) {
            ObjectSchema.Applicators a = schema.applicators();
            boolean valid = true;

            for (JsonSchema sub : a.allOf()) {
                valid &= evaluate(sub, instance, path);
            }

            if (!a.anyOf().isEmpty()) {
                boolean matched = false;
                for (JsonSchema sub : a.anyOf()) {
                    if (matches(sub, instance, path)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    error(path, schema, "Instance does not match any schema in 'anyOf'");
                    valid = false;
                }
            }

            if (!a.oneOf().isEmpty()) {
                int matched = 0;
                for (JsonSchema sub : a.oneOf()) {
                    if (matches(sub, instance, path)) {
                        matched++;
                    }
                }
                if (matched != 1) {
                    error(path, schema, "Instance matches " + matched
                            + " schema(s) in 'oneOf', exactly one is required");
                    valid = false;
                }
            }

            if (a.not() != null && matches(a.not(), instance, path)) {
                error(path, schema, "Instance matches the schema in 'not'");
                valid = false;
            }

            if (a.ifSchema() != null) {
                if (matches(a.ifSchema(), instance, path)) {
                    if (a.thenSchema() != null) {
                        valid &= evaluate(a.thenSchema(), instance, path);
                    }
                } else if (a.elseSchema() != null) {
                    valid &= evaluate(a.elseSchema(), instance, path);
                }
            }
            return valid;
        }

        // -------------------------------------------------------------- helpers

        private static boolean matchesType(SchemaType type, JsonValue instance) {
            return switch (type) {
                case NULL -> instance.getValueType() == JsonValue.ValueType.NULL;
                case BOOLEAN -> instance.getValueType() == JsonValue.ValueType.TRUE
                        || instance.getValueType() == JsonValue.ValueType.FALSE;
                case OBJECT -> instance instanceof JsonObject;
                case ARRAY -> instance instanceof JsonArray;
                case STRING -> instance instanceof JsonString;
                case NUMBER -> instance instanceof JsonNumber;
                case INTEGER -> instance instanceof JsonNumber number && isIntegral(number);
            };
        }

        private static boolean isIntegral(JsonNumber number) {
            return number.bigDecimalValue().stripTrailingZeros().scale() <= 0;
        }

        private static String describe(Set<SchemaType> types) {
            List<String> names = types.stream().map(SchemaType::jsonName).sorted().toList();
            return names.size() == 1 ? "'" + names.get(0) + "'" : "one of " + names;
        }

        private static String typeName(JsonValue value) {
            return switch (value.getValueType()) {
                case OBJECT -> "object";
                case ARRAY -> "array";
                case STRING -> "string";
                case NUMBER -> "number";
                case TRUE, FALSE -> "boolean";
                case NULL -> "null";
            };
        }

        private static boolean hasDuplicates(JsonArray array) {
            for (int i = 0; i < array.size(); i++) {
                for (int j = i + 1; j < array.size(); j++) {
                    if (deepEquals(array.get(i), array.get(j))) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Deep structural equality where numbers compare by value, not representation, so that
         * {@code 1}, {@code 1.0} and {@code 1e0} are all the same JSON value for {@code const},
         * {@code enum} and {@code uniqueItems}.
         */
        private static boolean deepEquals(JsonValue left, JsonValue right) {
            if (left == right) {
                return true;
            }
            if (left == null || right == null || left.getValueType() != right.getValueType()) {
                return false;
            }
            return switch (left.getValueType()) {
                case NULL -> true;
                case TRUE, FALSE -> true;
                case STRING -> ((JsonString) left).getString().equals(((JsonString) right).getString());
                case NUMBER -> ((JsonNumber) left).bigDecimalValue()
                        .compareTo(((JsonNumber) right).bigDecimalValue()) == 0;
                case ARRAY -> arraysEqual(left.asJsonArray(), right.asJsonArray());
                case OBJECT -> objectsEqual(left.asJsonObject(), right.asJsonObject());
            };
        }

        private static boolean arraysEqual(JsonArray left, JsonArray right) {
            if (left.size() != right.size()) {
                return false;
            }
            for (int i = 0; i < left.size(); i++) {
                if (!deepEquals(left.get(i), right.get(i))) {
                    return false;
                }
            }
            return true;
        }

        private static boolean objectsEqual(JsonObject left, JsonObject right) {
            if (left.size() != right.size()) {
                return false;
            }
            for (Map.Entry<String, JsonValue> entry : left.entrySet()) {
                JsonValue other = right.get(entry.getKey());
                if (other == null || !deepEquals(entry.getValue(), other)) {
                    return false;
                }
            }
            return true;
        }
    }
}
