/**
 * 
 */
package za.co.sindi.json.schema.validation;

import java.util.HashMap;
import java.util.Map;

/**
 * Strategy for the {@code format} keyword.
 *
 * <p>Formats are assertions in this library (matching the default behaviour of the 2020-12 test
 * suite). Register a no-op validator for a format name to downgrade it to an annotation.
 */
@FunctionalInterface
public interface FormatValidator {

    boolean isValid(String value);

    /** @return an immutable map of the built-in format validators. */
    static Map<String, FormatValidator> defaults() {
        Map<String, FormatValidator> formats = new HashMap<>();
        formats.put("date-time", Formats::isDateTime);
        formats.put("date", Formats::isDate);
        formats.put("time", Formats::isTime);
        formats.put("email", Formats::isEmail);
        formats.put("hostname", Formats::isHostname);
        formats.put("ipv4", Formats::isIpv4);
        formats.put("ipv6", Formats::isIpv6);
        formats.put("uri", Formats::isUri);
        formats.put("uri-reference", Formats::isUriReference);
        formats.put("uuid", Formats::isUuid);
        formats.put("regex", Formats::isRegex);
        formats.put("json-pointer", Formats::isJsonPointer);
        return Map.copyOf(formats);
    }
}
