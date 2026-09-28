/**
 * 
 */
package za.co.sindi.json.schema;

/**
 * Unchecked exception raised for malformed schemas or unresolvable references.
 */
public class SchemaException extends RuntimeException {

	/**
	 * @param message
	 * @param cause
	 */
	public SchemaException(String message, Throwable cause) {
		super(message, cause);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param message
	 */
	public SchemaException(String message) {
		super(message);
		// TODO Auto-generated constructor stub
	}
}
