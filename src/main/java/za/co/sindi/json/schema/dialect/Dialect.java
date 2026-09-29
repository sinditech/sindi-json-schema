/**
 * 
 */
package za.co.sindi.json.schema.dialect;

import java.net.URI;
import java.util.Set;

import za.co.sindi.json.schema.ObjectSchema;

/**
 * Version specific interpretation of JSON Schema keywords.
 *
 * <p>A dialect answers three kinds of question: what a keyword is called, what shape it takes,
 * and what it means at validation time. Everything that differs between drafts lives behind this
 * interface so the reader and the validator stay version agnostic.
 */
public interface Dialect {

    /** Human readable name, e.g. {@code "JSON Schema draft-07"}. */
    String name();

    /** The {@code $schema} URIs this dialect is responsible for. */
    Set<URI> metaSchemaUris();

    // ---- keyword names -----------------------------------------------------------

    /** The keyword that declares a resource identifier: {@code "id"} or {@code "$id"}. */
    String idKeyword();

    /** The keyword(s) holding reusable subschemas: {@code "definitions"}, {@code "$defs"}. */
    Set<String> definitionsKeywords();

    // ---- validation time semantics ----------------------------------------------

    /** True for 2019-09 and later, where {@code $ref} applies alongside its siblings. */
    boolean appliesRefSiblings();

    /** True when {@code format} produces assertion failures rather than annotations. */
    boolean formatIsAssertion();

    // ---- parse time keyword groups ----------------------------------------------

    ObjectSchema.NumericConstraints readNumericConstraints(KeywordContext ctx);

    ObjectSchema.ArrayConstraints readArrayConstraints(KeywordContext ctx);

    ObjectSchema.ObjectConstraints readObjectConstraints(KeywordContext ctx);
}
