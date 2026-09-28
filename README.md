# sindi-json-schema
A Jakarta EE utility that loads a JSON Schema and can validate the JSON value against the schema.

It targets **JSON Schema Draft 2020-12** semantics (with familiar keywords from earlier drafts), is built on standard Jakarta JSON Processing (`jakarta.json`), and is designed for use in modern Java applications (Java 25+).

| | |
|---|---|
| **Group ID** | `za.co.sindi.json` |
| **Artifact ID** | `sindi-json-schema` |
| **Version** | `0.0.1` |
| **License** | [Apache License 2.0](LICENSE) |
| **Author** | Buhake Sindi — [Sindi Technologies (Pty) Ltd](https://www.sindi.co.za) |
| **Repository** | https://github.com/sinditech/sindi-json-schema |

---

## Features

- **Load schemas** from:
  - In-memory JSON strings (`parse`)
  - Absolute URIs (`http`, `https`, `file`) via a pluggable `SchemaSource`
- **Central `SchemaRegistry`** (flyweight):
  - Documents are fetched, parsed, and built at most once
  - Subschemas are registered under canonical `$id` URIs and JSON Pointer locations
  - `$ref` resolution is a simple map lookup
  - Cyclic schemas are supported (documents are registered before references are followed)
- **Validation** via `JsonSchemaValidator`:
  - Exhaustive (not fail-fast) — returns the full list of problems in one pass
  - Thread-safe: a single validator instance can be shared across threads
  - Configurable max depth (default 256) to guard against pathological cyclic `$ref` usage
- **Supported assertion / applicator keywords** (grouped on `ObjectSchema`):
  - **Generic**: `type`, `const`, `enum`
  - **Numeric**: `multipleOf`, `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`
  - **String**: `minLength`, `maxLength`, `pattern`, `format`
  - **Array**: `minItems`, `maxItems`, `uniqueItems`, `items`, `prefixItems`, `contains`, `minContains`, `maxContains`
  - **Object**: `minProperties`, `maxProperties`, `required`, `properties`, `patternProperties`, `additionalProperties`, `propertyNames`, `dependentRequired`, `dependentSchemas`
  - **Applicators**: `allOf`, `anyOf`, `oneOf`, `not`, `if` / `then` / `else`
  - **References**: `$ref` (2020-12 style: applies alongside siblings)
  - **Boolean schemas**: `true` / `false`
- **Built-in `format` validators**: `email`, `hostname`, `ipv4`, `ipv6`, `uri`, `uri-reference`, `uuid`, `date`, `time`, `date-time`, `regex`, `json-pointer`
- Sealed `JsonSchema` hierarchy: only `BooleanSchema` and `ObjectSchema`
- Visitor pattern (`SchemaVisitor`) for extensible traversal

---

## Requirements

- **Java 25** (compiler source/target/release set to 25)
- **Jakarta JSON Processing API** (`jakarta.json-api`) — provided scope in the library
- **Jakarta JSON Binding API** (`jakarta.json.bind-api`) — provided scope
- A JSON-P implementation at runtime (e.g. Eclipse Parsson) and optionally Yasson for JSON-B tests

---

## Installation

### Maven

```xml
<dependency>
    <groupId>za.co.sindi.json</groupId>
    <artifactId>sindi-json-schema</artifactId>
    <version>0.0.1</version>
</dependency>
```

Ensure a JSON-P implementation is on the classpath, for example:

```xml
<dependency>
    <groupId>org.eclipse.parsson</groupId>
    <artifactId>parsson</artifactId>
    <version>1.1.9</version>
</dependency>
```

---

## Architecture overview

| Type | Role |
|------|------|
| `JsonSchemaLoader` | Main facade: load by URI or parse in-memory JSON |
| `SchemaRegistry` | Caches documents and built schemas by canonical URI |
| `SchemaSource` | Strategy for fetching raw schema text (default: HTTP(S) + file) |
| `JsonSchema` | Sealed interface (`BooleanSchema` \| `ObjectSchema`) |
| `ObjectSchema` | Object schema with keyword groups (metadata, type, numeric, string, array, object, applicators) |
| `JsonSchemaValidator` | Validates instances; returns `ValidationResult` |
| `ValidationResult` / `ValidationError` | Outcome and individual failures (instance path, schema location, message) |

Typical flow:

1. Create a `JsonSchemaLoader` (optionally with a custom `SchemaRegistry` / `SchemaSource`).
2. Obtain a `JsonSchema` via `load(URI)` or `parse(String)` / `parse(String, URI)`.
3. Create a `JsonSchemaValidator` and call `validate(schema, instance)` or `validate(schema, jsonString)`.
4. Inspect `ValidationResult.isValid()`, `errors()`, or `describe()`.

---

## Usage example

The following example mirrors the project’s `Demo` class: load a person schema (with `$defs` and `$ref`), then validate a valid and an invalid instance.

```java
import java.io.StringReader;
import java.net.URI;

import jakarta.json.Json;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import za.co.sindi.json.schema.JsonSchema;
import za.co.sindi.json.schema.JsonSchemaLoader;
import za.co.sindi.json.schema.validation.JsonSchemaValidator;
import za.co.sindi.json.schema.validation.ValidationResult;

public class Example {

    private static final String SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "https://example.com/schemas/person.json",
              "title": "Person",
              "type": "object",
              "required": ["name", "age"],
              "properties": {
                "name":  { "type": "string", "minLength": 1 },
                "age":   { "type": "integer", "minimum": 0, "maximum": 150 },
                "email": { "type": "string", "format": "email" },
                "tags":  {
                  "type": "array",
                  "items": { "type": "string" },
                  "uniqueItems": true
                },
                "address": { "$ref": "#/$defs/address" }
              },
              "additionalProperties": false,
              "$defs": {
                "address": {
                  "type": "object",
                  "required": ["city"],
                  "properties": {
                    "city": { "type": "string" },
                    "zip":  { "type": "string" }
                  }
                }
              }
            }
            """;

    private static final String VALID = """
            {
              "name": "Ada",
              "age": 36,
              "email": "ada@example.com",
              "tags": ["math", "engineer"],
              "address": { "city": "London" }
            }
            """;

    private static final String INVALID = """
            {
              "name": "",
              "age": -3,
              "email": "not-an-email",
              "tags": ["math", "math"],
              "address": { "zip": "SW1" },
              "extra": true
            }
            """;

    public static void main(String[] args) {
        // 1. Load / parse the schema
        JsonSchemaLoader loader = new JsonSchemaLoader();
        JsonSchema schema = loader.parse(
                SCHEMA,
                URI.create("https://example.com/schemas/person.json"));

        // 2. Create a reusable, thread-safe validator
        JsonSchemaValidator validator = new JsonSchemaValidator();

        // 3. Validate a valid instance
        try (JsonReader reader = Json.createReader(new StringReader(VALID))) {
            JsonValue value = reader.readValue();
            ValidationResult result = validator.validate(schema, value);
            System.out.println("valid instance   -> " + result.describe());
            // Output: The instance is valid.
        }

        // 4. Validate an invalid instance (exhaustive error reporting)
        try (JsonReader reader = Json.createReader(new StringReader(INVALID))) {
            JsonValue value = reader.readValue();
            ValidationResult result = validator.validate(schema, value);
            System.out.println("invalid instance -> " + result.describe());
            // Reports issues such as:
            //   - empty name (minLength)
            //   - negative age (minimum)
            //   - bad email (format)
            //   - duplicate tags (uniqueItems)
            //   - missing required city under address
            //   - unexpected "extra" property (additionalProperties: false)
        }

        // Convenience overload: validate from a JSON string directly
        ValidationResult fromString = validator.validate(schema, VALID);
        System.out.println(fromString.isValid()); // true
    }
}
```

### Loading a schema from a URI

```java
JsonSchemaLoader loader = new JsonSchemaLoader();
JsonSchema schema = loader.load(URI.create("https://example.com/schemas/person.json"));
// or from the local filesystem:
JsonSchema fileSchema = loader.load(URI.create("file:///path/to/person.json"));
```

The default `SchemaSource` supports `http`, `https`, and `file`. For offline/classpath/custom sources, implement `SchemaSource` and pass a `SchemaRegistry`:

```java
SchemaSource customSource = uri -> { /* return schema text */ };
SchemaRegistry registry = new SchemaRegistry(customSource);
JsonSchemaLoader loader = new JsonSchemaLoader(registry);
```

### Working with validation results

```java
ValidationResult result = validator.validate(schema, instance);

if (result.isValid()) {
    // OK
} else {
    for (ValidationError error : result.errors()) {
        // error.instancePath()  — JSON Pointer into the instance
        // error.schemaLocation() — URI of the schema that failed
        // error.message()       — human-readable description
        System.out.println(error);
    }
    System.out.println(result.describe());
}
```

---

## Project layout

```
sindi-json-schema/
├── pom.xml
├── LICENSE                 # Apache-2.0
├── CHANGELOG.md
├── README.md
├── .github/workflows/      # build, release, snapshot
└── src/
    ├── main/java/za/co/sindi/json/schema/
    │   ├── JsonSchema.java              # sealed interface
    │   ├── BooleanSchema.java
    │   ├── ObjectSchema.java            # keyword groups
    │   ├── JsonSchemaLoader.java        # facade
    │   ├── SchemaRegistry.java
    │   ├── SchemaSource.java
    │   ├── DefaultSchemaSource.java
    │   ├── SchemaReader.java
    │   ├── SchemaRef.java
    │   ├── SchemaType.java
    │   ├── SchemaVisitor.java
    │   ├── SchemaException.java
    │   ├── JsonPointers.java
    │   ├── JsonEquality.java
    │   ├── JsonValues.java
    │   └── validation/
    │       ├── JsonSchemaValidator.java
    │       ├── ValidationResult.java
    │       ├── ValidationError.java
    │       ├── FormatValidator.java
    │       └── Formats.java
    └── test/java/za/co/sindi/json/schema/
        └── Demo.java                    # end-to-end demo
```

---

## Building from source

```bash
git clone https://github.com/sinditech/sindi-json-schema.git
cd sindi-json-schema
mvn clean install
```

Requires JDK 25+.

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

- **0.0.1** (2026-09-28) — Initial packaging aligned for Maven Central publishing.

---

## License

Copyright © Sindi Technologies (Pty) Ltd  
Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

