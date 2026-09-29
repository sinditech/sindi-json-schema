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
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonPointer;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import za.co.sindi.json.schema.dialect.Dialect;
import za.co.sindi.json.schema.dialect.DialectRegistry;
import za.co.sindi.json.schema.dialect.KeywordContext;

/**
 * Builds the {@link JsonSchema} object graph from a JSON-P document.
 *
 * <p>This is the <em>builder</em> of the library. It walks the raw document once, resolves
 * {@code $schema} and {@code $id} / {@code id}, converts every keyword into its typed
 * representation and registers each subschema in the {@link SchemaRegistry}. References stay
 * unresolved at this point.
 *
 * <p>The same instance also implements {@link KeywordContext}, which is how a {@link Dialect}
 * inspects the node that is currently being read and asks for its subschemas to be parsed. The
 * per-node state (current schema object, base URI, base pointer, document pointer, active dialect)
 * is stored in instance fields and saved / restored around every recursive {@link #read} call, so
 * the callbacks always see the correct node.
 *
 * <p>The reader is not thread safe; the registry instantiates a fresh one per loaded document.
 */
final class SchemaReader implements KeywordContext {

    private final SchemaRegistry registry;
    private final DialectRegistry dialects;
    private final URI documentUri;

    // ---- per-node state, installed by read() and consumed by KeywordContext ----

    private JsonObject currentSchema;
    private URI currentBaseUri;
    private Dialect currentDialect;
    private JsonPointer currentBasePointer;
    private JsonPointer currentDocumentPointer;

    SchemaReader(SchemaRegistry registry, DialectRegistry dialects, URI documentUri) {
        this.registry = registry;
        this.dialects = dialects;
        this.documentUri = documentUri;
    }

    // ================================================================== entry points

    /**
     * Reads a document root using the registry's default dialect.
     *
     * <p>Convenience for {@link SchemaRegistry#load(URI)}; equivalent to calling the five argument
     * {@link #read(JsonValue, URI, Dialect, JsonPointer, JsonPointer)} with the default dialect and
     * the root pointer.
     */
    JsonSchema read(JsonValue document) {
        return read(document, documentUri, dialects.defaultDialect(),
                JsonPointers.ROOT, JsonPointers.ROOT);
    }

    /**
     * Reads one schema node.
     *
     * @param node             raw JSON node, must be an object or a boolean
     * @param baseUri          base URI in effect for this node, before {@code $id} resolution
     * @param dialect          dialect in effect for this node, before {@code $schema} resolution
     * @param basePointer      JSON Pointer of this node relative to {@code baseUri}
     * @param documentPointer  JSON Pointer of this node relative to the containing document
     */
    JsonSchema read(JsonValue node, URI baseUri, Dialect dialect,
                    JsonPointer basePointer, JsonPointer documentPointer) {

        if (node == null) {
            throw new SchemaException("Missing schema node");
        }

        // --- boolean schemas --------------------------------------------------------
        if (JsonValues.isBoolean(node)) {
            URI location = locationOf(baseUri, basePointer);
            BooleanSchema schema = new BooleanSchema(node == JsonValue.TRUE, location);
            register(schema, location, documentPointer);
            return schema;
        }

        if (!(node instanceof JsonObject object)) {
            throw new SchemaException(
                    "A JSON Schema must be an object or a boolean but was "
                            + JsonValues.typeName(node));
        }

        // --- $schema may switch the dialect for this subtree ------------------------
        // The keyword is only meaningful at a resource root: the document root, or a node that
        // declares its own identifier ($id / id). Anywhere else it is ignored (some schemas in the
        // wild put it there by accident).
        Dialect nodeDialect = dialect;
        String metaSchemaUri = JsonValues.getString(object, "$schema");
        if (metaSchemaUri != null
                && (documentPointer.toString().isEmpty()
                        || object.containsKey("$id")
                        || object.containsKey("id"))) {
            nodeDialect = dialects.resolve(URI.create(metaSchemaUri));
        }

        // --- $id / id ---------------------------------------------------------------
        URI declaredId = readId(object, baseUri, nodeDialect);
        URI nodeBaseUri = declaredId != null ? declaredId : baseUri;
        JsonPointer nodeBasePointer = declaredId != null ? JsonPointers.ROOT : basePointer;
        URI location = locationOf(nodeBaseUri, nodeBasePointer);

        // --- $ref ------------------------------------------------------------------
        SchemaRef ref = null;
        String refTarget = JsonValues.getString(object, "$ref");
        if (refTarget != null) {
            ref = new SchemaRef(nodeBaseUri.resolve(refTarget), registry);
        }

        // --- install state so the dialect callbacks can see this node ---------------
        JsonObject previousSchema = currentSchema;
        URI previousBaseUri = currentBaseUri;
        Dialect previousDialect = currentDialect;
        JsonPointer previousBasePointer = currentBasePointer;
        JsonPointer previousDocumentPointer = currentDocumentPointer;

        currentSchema = object;
        currentBaseUri = nodeBaseUri;
        currentDialect = nodeDialect;
        currentBasePointer = nodeBasePointer;
        currentDocumentPointer = documentPointer;

        try {
            // --- annotation keywords ------------------------------------------------
            ObjectSchema.Metadata metadata = readMetadata(object);

            // --- generic keywords ---------------------------------------------------
            ObjectSchema.GenericConstraints generic = new ObjectSchema.GenericConstraints(
                    readTypes(object.get("type")),
                    object.get("const"),
                    readValueList(object, "enum"));

            // --- string keywords ----------------------------------------------------
            ObjectSchema.StringConstraints stringConstraints = new ObjectSchema.StringConstraints(
                    readNonNegativeInt(object, "minLength"),
                    readNonNegativeInt(object, "maxLength"),
                    readPattern(object, "pattern"),
                    JsonValues.getString(object, "format"));

            // --- version specific keyword groups, delegated to the dialect ----------
            ObjectSchema.NumericConstraints numeric = nodeDialect.readNumericConstraints(this);
            ObjectSchema.ArrayConstraints array = nodeDialect.readArrayConstraints(this);
            ObjectSchema.ObjectConstraints objectConstraints = nodeDialect.readObjectConstraints(this);

            // --- applicators --------------------------------------------------------
            ObjectSchema.Applicators applicators = new ObjectSchema.Applicators(
                    subschemaList("allOf"),
                    subschemaList("anyOf"),
                    subschemaList("oneOf"),
                    subschema("not"),
                    subschema("if"),
                    subschema("then"),
                    subschema("else"));

            ObjectSchema schema = new ObjectSchema(location, nodeDialect, object, ref, metadata,
                    generic, numeric, stringConstraints, array, objectConstraints, applicators);
            register(schema, location, documentPointer);

            // --- definitions --------------------------------------------------------
            // Walked eagerly so that a lazily resolved $ref that points inside one of them finds
            // an already registered schema. Each dialect tells us which keywords to look for
            // ("definitions", "$defs", or both). The maps themselves are discarded; only the
            // registration side effects matter.
            for (String defsKeyword : nodeDialect.definitionsKeywords()) {
                subschemaMap(defsKeyword);
            }

            return schema;
        } finally {
            currentSchema = previousSchema;
            currentBaseUri = previousBaseUri;
            currentDialect = previousDialect;
            currentBasePointer = previousBasePointer;
            currentDocumentPointer = previousDocumentPointer;
        }
    }

    // ================================================================== KeywordContext

    @Override
    public boolean has(String keyword) {
        return currentSchema.containsKey(keyword);
    }

    @Override
    public JsonValue node(String keyword) {
        return currentSchema.get(keyword);
    }

    @Override
    public String string(String keyword) {
        if (!currentSchema.containsKey(keyword)) {
            return null;
        }
        JsonValue value = currentSchema.get(keyword);
        if (!(value instanceof JsonString string)) {
            throw new SchemaException("'" + keyword + "' must be a string");
        }
        return string.getString();
    }

    @Override
    public Boolean bool(String keyword) {
        if (!currentSchema.containsKey(keyword)) {
            return null;
        }
        JsonValue value = currentSchema.get(keyword);
        return switch (value.getValueType()) {
            case TRUE -> Boolean.TRUE;
            case FALSE -> Boolean.FALSE;
            default -> throw new SchemaException("'" + keyword + "' must be a boolean");
        };
    }

    @Override
    public BigDecimal number(String keyword) {
        if (!currentSchema.containsKey(keyword)) {
            return null;
        }
        JsonValue value = currentSchema.get(keyword);
        if (!(value instanceof JsonNumber number)) {
            throw new SchemaException("'" + keyword + "' must be a number");
        }
        return number.bigDecimalValue();
    }

    @Override
    public Integer integer(String keyword) {
        return readNonNegativeInt(currentSchema, keyword);
    }

    @Override
    public Pattern pattern(String keyword) {
        return readPattern(currentSchema, keyword);
    }

    @Override
    public List<String> stringList(String keyword) {
        return readStringArray(currentSchema, keyword);
    }

    @Override
    public JsonSchema subschema(String keyword) {
        JsonValue value = currentSchema.get(keyword);
        if (value == null) {
            return null;
        }
        return read(value, currentBaseUri, currentDialect,
                JsonPointers.append(currentBasePointer, keyword),
                JsonPointers.append(currentDocumentPointer, keyword));
    }

    @Override
    public JsonSchema subschemaAt(String keyword, int index) {
        JsonValue value = currentSchema.get(keyword);
        if (!(value instanceof JsonArray array)) {
            throw new SchemaException("'" + keyword + "' must be an array");
        }
        JsonPointer baseChild = JsonPointers.append(currentBasePointer, keyword);
        JsonPointer documentChild = JsonPointers.append(currentDocumentPointer, keyword);
        return read(array.get(index), currentBaseUri, currentDialect,
                JsonPointers.append(baseChild, index),
                JsonPointers.append(documentChild, index));
    }

    @Override
    public List<JsonSchema> subschemaList(String keyword) {
        JsonValue value = currentSchema.get(keyword);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof JsonArray array)) {
            throw new SchemaException("'" + keyword + "' must be an array");
        }
        JsonPointer baseChild = JsonPointers.append(currentBasePointer, keyword);
        JsonPointer documentChild = JsonPointers.append(currentDocumentPointer, keyword);
        List<JsonSchema> schemas = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            schemas.add(read(array.get(i), currentBaseUri, currentDialect,
                    JsonPointers.append(baseChild, i),
                    JsonPointers.append(documentChild, i)));
        }
        return schemas;
    }

    @Override
    public Map<String, JsonSchema> subschemaMap(String keyword) {
        JsonValue value = currentSchema.get(keyword);
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof JsonObject map)) {
            throw new SchemaException("'" + keyword + "' must be an object");
        }
        JsonPointer baseChild = JsonPointers.append(currentBasePointer, keyword);
        JsonPointer documentChild = JsonPointers.append(currentDocumentPointer, keyword);
        Map<String, JsonSchema> schemas = new LinkedHashMap<>();
        for (Map.Entry<String, JsonValue> entry : map.entrySet()) {
            schemas.put(entry.getKey(), read(entry.getValue(), currentBaseUri, currentDialect,
                    JsonPointers.append(baseChild, entry.getKey()),
                    JsonPointers.append(documentChild, entry.getKey())));
        }
        return schemas;
    }

    @Override
    public Map<Pattern, JsonSchema> patternSubschemaMap(String keyword) {
        JsonValue value = currentSchema.get(keyword);
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof JsonObject map)) {
            throw new SchemaException("'" + keyword + "' must be an object");
        }
        JsonPointer baseChild = JsonPointers.append(currentBasePointer, keyword);
        JsonPointer documentChild = JsonPointers.append(currentDocumentPointer, keyword);
        Map<Pattern, JsonSchema> schemas = new LinkedHashMap<>();
        for (Map.Entry<String, JsonValue> entry : map.entrySet()) {
            Pattern pattern;
            try {
                pattern = Pattern.compile(entry.getKey());
            } catch (PatternSyntaxException e) {
                throw new SchemaException("Invalid '" + keyword + "' key: " + entry.getKey(), e);
            }
            schemas.put(pattern, read(entry.getValue(), currentBaseUri, currentDialect,
                    JsonPointers.append(baseChild, entry.getKey()),
                    JsonPointers.append(documentChild, entry.getKey())));
        }
        return schemas;
    }

    // ================================================================== keyword readers

    /**
     * Reads the resource identifier, using the dialect's keyword ({@code id} for draft-04,
     * {@code $id} elsewhere). The URI must not contain a fragment; anything the identifier
     * resolves to becomes the base URI for descendants.
     */
    private static URI readId(JsonObject object, URI baseUri, Dialect dialect) {
        String idKeyword = dialect.idKeyword();
        String id = JsonValues.getString(object, idKeyword);
        if (id == null || id.isBlank()) {
            return null;
        }
        URI resolved = baseUri.resolve(id);
        if (resolved.getFragment() != null && !resolved.getFragment().isEmpty()) {
            throw new SchemaException(
                    "'" + idKeyword + "' must not contain a fragment: '" + id + "'");
        }
        return resolved;
    }

    private static ObjectSchema.Metadata readMetadata(JsonObject object) {
        return new ObjectSchema.Metadata(
                JsonValues.getString(object, "title"),
                JsonValues.getString(object, "description"),
                object.get("default"),
                readValueList(object, "examples"),
                JsonValues.getBoolean(object, "deprecated"),
                JsonValues.getBoolean(object, "readOnly"),
                JsonValues.getBoolean(object, "writeOnly"));
    }

    /**
     * Reads the {@code type} keyword. Accepts a single name or an array of names. Unknown names
     * are rejected, so a typo like {@code "int"} is caught at build time rather than silently
     * accepting every instance.
     */
    private static Set<SchemaType> readTypes(JsonValue node) {
        if (node == null) {
            return Set.of();
        }
        if (node instanceof JsonString string) {
            return Set.of(SchemaType.fromJsonName(string.getString()));
        }
        if (node instanceof JsonArray array) {
            List<SchemaType> types = new ArrayList<>(array.size());
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

    /** Reads an array of arbitrary JSON values ({@code enum}, {@code examples}). */
    private static List<JsonValue> readValueList(JsonObject owner, String keyword) {
        JsonArray array = JsonValues.getArray(owner, keyword);
        if (array == null) {
            if (owner.containsKey(keyword)) {
                throw new SchemaException("'" + keyword + "' must be an array");
            }
            return List.of();
        }
        return List.copyOf(array);
    }

    /**
     * Reads a non negative integer keyword. A missing keyword yields {@code null}; a present but
     * ill formed value throws.
     */
    private static Integer readNonNegativeInt(JsonObject owner, String keyword) {
        if (!owner.containsKey(keyword)) {
            return null;
        }
        JsonValue value = owner.get(keyword);
        if (!(value instanceof JsonNumber number)) {
            throw new SchemaException("'" + keyword + "' must be a non-negative integer");
        }
        BigDecimal decimal = number.bigDecimalValue();
        if (decimal.signum() < 0 || decimal.stripTrailingZeros().scale() > 0) {
            throw new SchemaException("'" + keyword + "' must be a non-negative integer");
        }
        try {
            return decimal.intValueExact();
        } catch (ArithmeticException e) {
            throw new SchemaException("'" + keyword + "' is out of range: " + decimal, e);
        }
    }

    /** Compiles a regex keyword. Regexes are compiled once at build time, not per instance. */
    private static Pattern readPattern(JsonObject owner, String keyword) {
        if (!owner.containsKey(keyword)) {
            return null;
        }
        String regex = JsonValues.getString(owner, keyword);
        if (regex == null) {
            throw new SchemaException("'" + keyword + "' must be a string");
        }
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new SchemaException(
                    "Invalid regular expression in '" + keyword + "': " + regex, e);
        }
    }

    /** Reads an array of strings such as {@code required} or {@code dependentRequired} values. */
    private static List<String> readStringArray(JsonObject owner, String keyword) {
        JsonArray array = JsonValues.getArray(owner, keyword);
        if (array == null) {
            if (owner.containsKey(keyword)) {
                throw new SchemaException("'" + keyword + "' must be an array of strings");
            }
            return List.of();
        }
        List<String> result = new ArrayList<>(array.size());
        for (JsonValue element : array) {
            if (!(element instanceof JsonString string)) {
                throw new SchemaException("'" + keyword + "' must be an array of strings");
            }
            result.add(string.getString());
        }
        return List.copyOf(result);
    }

    // ================================================================== registration

    /**
     * Registers a freshly built schema under both its canonical {@code $id} / location URI and its
     * JSON Pointer location inside the containing document. A {@code $ref} may target either, so
     * both aliases are needed for the two styles of reference to resolve.
     */
    private void register(JsonSchema schema, URI location, JsonPointer documentPointer) {
        registry.register(location, schema);
        URI alias = locationOf(documentUri, documentPointer);
        if (!alias.equals(location)) {
            registry.register(alias, schema);
        }
    }

    /**
     * Builds the canonical URI of a node. When the pointer is empty the base URI is the location
     * of the resource root; otherwise the pointer becomes the URI fragment, percent-encoded where
     * necessary by the five argument {@link URI} constructor so the result is comparable with
     * URIs produced by {@link URI#resolve(String)}.
     */
    private static URI locationOf(URI base, JsonPointer pointer) {
        String fragment = pointer.toString();
        if (fragment.isEmpty()) {
            return base;
        }
        try {
            return new URI(base.getScheme(), base.getAuthority(),
                    base.getPath(), base.getQuery(), fragment);
        } catch (URISyntaxException e) {
            throw new SchemaException(
                    "Cannot build a schema location for " + base + "#" + fragment, e);
        }
    }
}
