/**
 * 
 */
package za.co.sindi.json.schema;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonPointer;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Builds the {@link JsonSchema} object graph from a JSON-P document.
 *
 * <p>This is the <em>builder</em> of the library. It walks the raw document once, resolves
 * {@code $id} base URIs, converts every keyword into its typed representation and registers each
 * subschema in the {@link SchemaRegistry}. References stay unresolved at this point.
 */
final class SchemaReader {

    private final SchemaRegistry registry;
    private final URI documentUri;

    SchemaReader(SchemaRegistry registry, URI documentUri) {
        this.registry = registry;
        this.documentUri = documentUri;
    }

    /** Convenience overload used by the registry, seeding both pointers to the document root. */
    JsonSchema read(JsonValue node, URI baseUri, URI documentUri) {
        return read(node, baseUri, JsonPointers.ROOT, JsonPointers.ROOT);
    }

    /**
     * @param node             raw JSON node
     * @param baseUri          base URI in effect for this node (after {@code $id} resolution)
     * @param basePointer      JSON Pointer of this node relative to {@code baseUri}
     * @param documentPointer  JSON Pointer of this node relative to the containing document
     */
    JsonSchema read(JsonValue node, URI baseUri, JsonPointer basePointer, JsonPointer documentPointer) {
        if (node == null) {
            throw new SchemaException("Missing schema node");
        }
        if (JsonValues.isBoolean(node)) {
            URI location = locationOf(baseUri, basePointer);
            BooleanSchema schema = new BooleanSchema(node == JsonValue.TRUE, location);
            register(schema, location, documentPointer);
            return schema;
        }
        if (!(node instanceof JsonObject object)) {
            throw new SchemaException(
                    "A JSON Schema must be an object or a boolean but was " + JsonValues.typeName(node));
        }

        URI declaredId = readId(object, baseUri);
        URI nodeBase = declaredId != null ? declaredId : baseUri;
        JsonPointer nodePointer = declaredId != null ? JsonPointers.ROOT : basePointer;
        URI location = locationOf(nodeBase, nodePointer);

        SchemaRef ref = null;
        String refTarget = JsonValues.getString(object, "$ref");
        if (refTarget != null) {
            ref = new SchemaRef(nodeBase.resolve(refTarget), registry);
        }

        ObjectSchema.Metadata metadata = new ObjectSchema.Metadata(
                JsonValues.getString(object, "title"),
                JsonValues.getString(object, "description"),
                object.get("default"),
                readArrayElements(object, "examples"),
                JsonValues.getBoolean(object, "deprecated"),
                JsonValues.getBoolean(object, "readOnly"),
                JsonValues.getBoolean(object, "writeOnly"));

        ObjectSchema.GenericConstraints generic = new ObjectSchema.GenericConstraints(
                readTypes(object.get("type")),
                object.get("const"),
                readArrayElements(object, "enum"));

        ObjectSchema.NumericConstraints numeric = new ObjectSchema.NumericConstraints(
                JsonValues.getNumber(object, "multipleOf"),
                JsonValues.getNumber(object, "minimum"),
                JsonValues.getNumber(object, "maximum"),
                JsonValues.getNumber(object, "exclusiveMinimum"),
                JsonValues.getNumber(object, "exclusiveMaximum"));

        ObjectSchema.StringConstraints stringConstraints = new ObjectSchema.StringConstraints(
                readNonNegativeInt(object, "minLength"),
                readNonNegativeInt(object, "maxLength"),
                readPattern(object, "pattern"),
                JsonValues.getString(object, "format"));

        // ---------------------------------------------------------------- arrays
        List<JsonSchema> prefixItems = new ArrayList<>();
        JsonArray prefixNode = JsonValues.getArray(object, "prefixItems");
        if (prefixNode != null) {
            for (int i = 0; i < prefixNode.size(); i++) {
                prefixItems.add(read(prefixNode.get(i), nodeBase,
                        JsonPointers.append(JsonPointers.append(nodePointer, "prefixItems"), i),
                        JsonPointers.append(JsonPointers.append(documentPointer, "prefixItems"), i)));
            }
        }

        JsonSchema items = null;
        JsonValue itemsNode = object.get("items");
        if (itemsNode != null) {
            if (itemsNode instanceof JsonArray tuple) {
                // draft-07 tuple form: items is an array and additionalItems is its tail schema.
                for (int i = 0; i < tuple.size(); i++) {
                    prefixItems.add(read(tuple.get(i), nodeBase,
                            JsonPointers.append(JsonPointers.append(nodePointer, "items"), i),
                            JsonPointers.append(JsonPointers.append(documentPointer, "items"), i)));
                }
                if (object.containsKey("additionalItems")) {
                    items = read(object.get("additionalItems"), nodeBase,
                            JsonPointers.append(nodePointer, "additionalItems"),
                            JsonPointers.append(documentPointer, "additionalItems"));
                }
            } else {
                items = read(itemsNode, nodeBase,
                        JsonPointers.append(nodePointer, "items"),
                        JsonPointers.append(documentPointer, "items"));
            }
        }

        ObjectSchema.ArrayConstraints array = new ObjectSchema.ArrayConstraints(
                readNonNegativeInt(object, "minItems"),
                readNonNegativeInt(object, "maxItems"),
                JsonValues.getBoolean(object, "uniqueItems"),
                items,
                prefixItems,
                readChild(object, "contains", nodeBase, nodePointer, documentPointer),
                readNonNegativeInt(object, "minContains"),
                readNonNegativeInt(object, "maxContains"));

        // ---------------------------------------------------------------- objects
        ObjectSchema.ObjectConstraints objectConstraints = new ObjectSchema.ObjectConstraints(
                readNonNegativeInt(object, "minProperties"),
                readNonNegativeInt(object, "maxProperties"),
                readStringArray(object, "required"),
                readSchemaMap(object.get("properties"), nodeBase, nodePointer, documentPointer, "properties"),
                readPatternSchemaMap(object.get("patternProperties"), nodeBase, nodePointer, documentPointer),
                readChild(object, "additionalProperties", nodeBase, nodePointer, documentPointer),
                readChild(object, "propertyNames", nodeBase, nodePointer, documentPointer),
                readDependentRequired(object.get("dependentRequired")),
                readSchemaMap(object.get("dependentSchemas"), nodeBase, nodePointer, documentPointer, "dependentSchemas"));

        // ---------------------------------------------------------------- applicators
        ObjectSchema.Applicators applicators = new ObjectSchema.Applicators(
                readSchemaList(object.get("allOf"), nodeBase, nodePointer, documentPointer, "allOf"),
                readSchemaList(object.get("anyOf"), nodeBase, nodePointer, documentPointer, "anyOf"),
                readSchemaList(object.get("oneOf"), nodeBase, nodePointer, documentPointer, "oneOf"),
                readChild(object, "not", nodeBase, nodePointer, documentPointer),
                readChild(object, "if", nodeBase, nodePointer, documentPointer),
                readChild(object, "then", nodeBase, nodePointer, documentPointer),
                readChild(object, "else", nodeBase, nodePointer, documentPointer));

        ObjectSchema schema = new ObjectSchema(location, object, ref, metadata, generic, numeric,
                stringConstraints, array, objectConstraints, applicators);
        register(schema, location, documentPointer);

        // Subschema containers must be walked eagerly so a lazily resolved "$ref" that points
        // inside them finds an already registered schema.
        readDefinitions(object.get("$defs"), nodeBase, nodePointer, documentPointer, "$defs");
        readDefinitions(object.get("definitions"), nodeBase, nodePointer, documentPointer, "definitions");

        return schema;
    }

    // ------------------------------------------------------------------ keyword readers

    private URI readId(JsonObject object, URI baseUri) {
        String id = JsonValues.getString(object, "$id");
        if (id == null) {
            id = JsonValues.getString(object, "id");
        }
        if (id == null || id.isBlank()) {
            return null;
        }
        URI resolved = baseUri.resolve(id);
        if (resolved.getFragment() != null && !resolved.getFragment().isEmpty()) {
            throw new SchemaException("'$id' must not contain a fragment: '" + id + "'");
        }
        return resolved;
    }

    private Set<SchemaType> readTypes(JsonValue node) {
        if (node == null) {
            return Set.of();
        }
        if (node instanceof JsonString string) {
            return Set.of(SchemaType.fromJsonName(string.getString()));
        }
        if (node instanceof JsonArray array) {
            List<SchemaType> types = new ArrayList<>();
            for (JsonValue element : array) {
                if (!(element instanceof JsonString string)) {
                    throw new SchemaException("'type' must be a string or an array of strings");
                }
                types.add(SchemaType.fromJsonName(string.getString()));
            }
            return Set.copyOf(types);
        }
        throw new SchemaException("'type' must be a string or an array of strings");
    }

    private List<JsonValue> readArrayElements(JsonObject owner, String keyword) {
        JsonArray array = JsonValues.getArray(owner, keyword);
        if (array == null) {
            if (owner.containsKey(keyword)) {
                throw new SchemaException("'" + keyword + "' must be an array");
            }
            return List.of();
        }
        return List.copyOf(array);
    }

    private List<String> readStringArray(JsonObject owner, String keyword) {
        JsonArray array = JsonValues.getArray(owner, keyword);
        if (array == null) {
            if (owner.containsKey(keyword)) {
                throw new SchemaException("'" + keyword + "' must be an array of strings");
            }
            return List.of();
        }
        List<String> result = new ArrayList<>(array.size());
        for (JsonValue value : array) {
            if (!(value instanceof JsonString string)) {
                throw new SchemaException("'" + keyword + "' must be an array of strings");
            }
            result.add(string.getString());
        }
        return List.copyOf(result);
    }

    private Integer readNonNegativeInt(JsonObject owner, String keyword) {
        BigDecimal value = JsonValues.getNumber(owner, keyword);
        if (value == null) {
            if (owner.containsKey(keyword)) {
                throw new SchemaException("'" + keyword + "' must be a number");
            }
            return null;
        }
        if (value.signum() < 0 || value.stripTrailingZeros().scale() > 0) {
            throw new SchemaException("'" + keyword + "' must be a non-negative integer");
        }
        try {
            return value.intValueExact();
        } catch (ArithmeticException e) {
            throw new SchemaException("'" + keyword + "' is out of range: " + value, e);
        }
    }

    private Pattern readPattern(JsonObject owner, String keyword) {
        String regex = JsonValues.getString(owner, keyword);
        if (regex == null) {
            return null;
        }
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new SchemaException("Invalid regular expression in '" + keyword + "': " + regex, e);
        }
    }

    private JsonSchema readChild(JsonObject owner, String keyword, URI base,
                                 JsonPointer basePointer, JsonPointer documentPointer) {
        if (!owner.containsKey(keyword)) {
            return null;
        }
        return read(owner.get(keyword), base,
                JsonPointers.append(basePointer, keyword),
                JsonPointers.append(documentPointer, keyword));
    }

    private List<JsonSchema> readSchemaList(JsonValue node, URI base,
                                            JsonPointer basePointer, JsonPointer documentPointer,
                                            String keyword) {
        if (node == null) {
            return List.of();
        }
        if (!(node instanceof JsonArray array)) {
            throw new SchemaException("'" + keyword + "' must be an array of schemas");
        }
        List<JsonSchema> schemas = new ArrayList<>(array.size());
        JsonPointer baseChild = JsonPointers.append(basePointer, keyword);
        JsonPointer documentChild = JsonPointers.append(documentPointer, keyword);
        for (int i = 0; i < array.size(); i++) {
            schemas.add(read(array.get(i), base,
                    JsonPointers.append(baseChild, i),
                    JsonPointers.append(documentChild, i)));
        }
        return schemas;
    }

    private Map<String, JsonSchema> readSchemaMap(JsonValue node, URI base,
                                                  JsonPointer basePointer, JsonPointer documentPointer,
                                                  String keyword) {
        if (node == null) {
            return Map.of();
        }
        if (!(node instanceof JsonObject object)) {
            throw new SchemaException("'" + keyword + "' must be an object");
        }
        Map<String, JsonSchema> result = new LinkedHashMap<>();
        JsonPointer baseChild = JsonPointers.append(basePointer, keyword);
        JsonPointer documentChild = JsonPointers.append(documentPointer, keyword);
        for (Map.Entry<String, JsonValue> entry : object.entrySet()) {
            result.put(entry.getKey(), read(entry.getValue(), base,
                    JsonPointers.append(baseChild, entry.getKey()),
                    JsonPointers.append(documentChild, entry.getKey())));
        }
        return result;
    }

    private Map<Pattern, JsonSchema> readPatternSchemaMap(JsonValue node, URI base,
                                                          JsonPointer basePointer, JsonPointer documentPointer) {
        if (node == null) {
            return Map.of();
        }
        if (!(node instanceof JsonObject object)) {
            throw new SchemaException("'patternProperties' must be an object");
        }
        Map<Pattern, JsonSchema> result = new LinkedHashMap<>();
        JsonPointer baseChild = JsonPointers.append(basePointer, "patternProperties");
        JsonPointer documentChild = JsonPointers.append(documentPointer, "patternProperties");
        for (Map.Entry<String, JsonValue> entry : object.entrySet()) {
            Pattern pattern;
            try {
                pattern = Pattern.compile(entry.getKey());
            } catch (PatternSyntaxException e) {
                throw new SchemaException("Invalid 'patternProperties' key: " + entry.getKey(), e);
            }
            result.put(pattern, read(entry.getValue(), base,
                    JsonPointers.append(baseChild, entry.getKey()),
                    JsonPointers.append(documentChild, entry.getKey())));
        }
        return result;
    }

    private Map<String, List<String>> readDependentRequired(JsonValue node) {
        if (node == null) {
            return Map.of();
        }
        if (!(node instanceof JsonObject object)) {
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

    private void readDefinitions(JsonValue node, URI base, JsonPointer basePointer,
                                 JsonPointer documentPointer, String keyword) {
        if (node == null) {
            return;
        }
        if (!(node instanceof JsonObject object)) {
            throw new SchemaException("'" + keyword + "' must be an object");
        }
        for (Map.Entry<String, JsonValue> entry : object.entrySet()) {
            read(entry.getValue(), base,
                    JsonPointers.append(JsonPointers.append(basePointer, keyword), entry.getKey()),
                    JsonPointers.append(JsonPointers.append(documentPointer, keyword), entry.getKey()));
        }
    }

    // ------------------------------------------------------------------ registration

    private void register(JsonSchema schema, URI location, JsonPointer documentPointer) {
        registry.register(location, schema);
        URI alias = locationOf(documentUri, documentPointer);
        if (!alias.equals(location)) {
            registry.register(alias, schema);
        }
    }

    private static URI locationOf(URI base, JsonPointer pointer) {
        String fragment = pointer.toString();
        if (fragment.isEmpty()) {
            return base;
        }
        try {
            // The five argument constructor percent-encodes anything illegal in a fragment, which
            // keeps schema locations comparable with URIs produced by URI.resolve.
            return new URI(base.getScheme(), base.getAuthority(), base.getPath(), base.getQuery(), fragment);
        } catch (URISyntaxException e) {
            throw new SchemaException("Cannot build a schema location for " + base + "#" + fragment, e);
        }
    }
}
