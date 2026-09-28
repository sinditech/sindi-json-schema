/**
 * 
 */
package za.co.sindi.json.schema.validation;

import java.util.List;

/** The outcome of validating one instance against one schema. */
public record ValidationResult(List<ValidationError> errors) {

    public ValidationResult {
        errors = List.copyOf(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public String describe() {
        if (errors.isEmpty()) {
            return "The instance is valid.";
        }
        StringBuilder out = new StringBuilder("The instance is invalid:");
        for (ValidationError error : errors) {
            out.append(System.lineSeparator()).append("  - ").append(error);
        }
        return out.toString();
    }
}
