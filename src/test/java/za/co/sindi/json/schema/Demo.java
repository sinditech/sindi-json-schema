/**
 * 
 */
package za.co.sindi.json.schema;

import java.io.StringReader;
import java.net.URI;

import jakarta.json.Json;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import za.co.sindi.json.schema.validation.JsonSchemaValidator;
import za.co.sindi.json.schema.validation.ValidationResult;

/**
 * End to end demonstration.
 *
 * <pre>{@code
 * java com.example.jsonschema.Demo
 * }</pre>
 */
public final class Demo {

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
                "tags":  { "type": "array", "items": { "type": "string" }, "uniqueItems": true },
                "address": { "$ref": "#/$defs/address" }
              },
              "additionalProperties": false,
              "$defs": {
                "address": {
                  "type": "object",
                  "required": ["city"],
                  "properties": { "city": { "type": "string" }, "zip": { "type": "string" } }
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

    private Demo() {
    }

    public static void main(String[] args) {
        JsonSchemaLoader loader = new JsonSchemaLoader();
        JsonSchema schema = loader.parse(SCHEMA, URI.create("https://example.com/schemas/person.json"));

        JsonSchemaValidator validator = new JsonSchemaValidator();

        try (JsonReader reader = Json.createReader(new StringReader(VALID))) {
            JsonValue value = reader.readValue();
            ValidationResult result = validator.validate(schema, value);
            System.out.println("valid instance   -> " + result.describe());
        }

        try (JsonReader reader = Json.createReader(new StringReader(INVALID))) {
            JsonValue value = reader.readValue();
            ValidationResult result = validator.validate(schema, value);
            System.out.println("invalid instance -> " + result.describe());
        }
    }
}

