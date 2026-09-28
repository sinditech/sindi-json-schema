/**
 * 
 */
package za.co.sindi.json.schema;

/** The JSON Schema primitive types. */
public enum SchemaType {

    NULL("null"),
    BOOLEAN("boolean"),
    OBJECT("object"),
    ARRAY("array"),
    NUMBER("number"),
    STRING("string"),
    INTEGER("integer");

    private final String jsonName;

    SchemaType(String jsonName) {
        this.jsonName = jsonName;
    }

    public String jsonName() {
        return jsonName;
    }

    public static SchemaType fromJsonName(String name) {
        for (SchemaType type : values()) {
            if (type.jsonName.equals(name)) {
                return type;
            }
        }
        throw new SchemaException("Unknown JSON Schema type: '" + name + "'");
    }
}