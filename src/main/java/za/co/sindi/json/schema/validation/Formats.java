/**
 * 
 */
package za.co.sindi.json.schema.validation;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** Built-in implementations of the standard {@code format} values. */
final class Formats {

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@"
                    + "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$");

    private static final Pattern HOSTNAME = Pattern.compile(
            "^(?=.{1,253}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(?:\\."
                    + "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$");

    private static final Pattern IPV4 = Pattern.compile(
            "^(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

    private static final Pattern IPV6 = Pattern.compile(
            "^(?:"
                    + "(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}"
                    + "|(?:[0-9a-fA-F]{1,4}:){1,7}:"
                    + "|(?:[0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}"
                    + "|(?:[0-9a-fA-F]{1,4}:){1,5}(?::[0-9a-fA-F]{1,4}){1,2}"
                    + "|(?:[0-9a-fA-F]{1,4}:){1,4}(?::[0-9a-fA-F]{1,4}){1,3}"
                    + "|(?:[0-9a-fA-F]{1,4}:){1,3}(?::[0-9a-fA-F]{1,4}){1,4}"
                    + "|(?:[0-9a-fA-F]{1,4}:){1,2}(?::[0-9a-fA-F]{1,4}){1,5}"
                    + "|[0-9a-fA-F]{1,4}:(?:(?::[0-9a-fA-F]{1,4}){1,6})"
                    + "|:(?:(?::[0-9a-fA-F]{1,4}){1,7}|:)"
                    + "|::(?:ffff(?::0{1,4})?:)?"
                    + "(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)"
                    + "|(?:[0-9a-fA-F]{1,4}:){1,4}:"
                    + "(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)"
                    + ")$");

    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private Formats() {
    	throw new AssertionError("Private constructor.");
    }

    static boolean isDateTime(String value) {
        String normalized = normalize(value);
        try {
            OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return true;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    static boolean isDate(String value) {
        if (value.length() != 10) {
            return false;
        }
        try {
            java.time.LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    static boolean isTime(String value) {
        try {
            OffsetTime.parse(normalize(value), DateTimeFormatter.ISO_OFFSET_TIME);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    static boolean isEmail(String value) {
        return EMAIL.matcher(value).matches();
    }

    static boolean isHostname(String value) {
        return HOSTNAME.matcher(value).matches();
    }

    static boolean isIpv4(String value) {
        return IPV4.matcher(value).matches();
    }

    static boolean isIpv6(String value) {
        return IPV6.matcher(value).matches();
    }

    static boolean isUri(String value) {
        try {
            return new URI(value).isAbsolute();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    static boolean isUriReference(String value) {
        try {
            new URI(value);
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    static boolean isUuid(String value) {
        return UUID.matcher(value).matches();
    }

    static boolean isRegex(String value) {
        try {
            Pattern.compile(value);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean isJsonPointer(String value) {
        if (value.isEmpty()) {
            return true;
        }
        if (value.charAt(0) != '/') {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '~') {
                if (i + 1 >= value.length()
                        || (value.charAt(i + 1) != '0' && value.charAt(i + 1) != '1')) {
                    return false;
                }
                i++;
            }
        }
        return true;
    }

    /** RFC 3339 permits lower case {@code t} and {@code z}. */
    private static String normalize(String value) {
        return value.replace('t', 'T').replace('z', 'Z');
    }
}
