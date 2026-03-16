package com.ghatana.appplatform.sanctions.domain;

/**
 * @doc.type    Enum
 * @doc.purpose Algorithm that produced a sanctions name match.
 * @doc.layer   Domain
 */
public enum MatchType {
    EXACT,
    LEVENSHTEIN,
    JARO_WINKLER,
    PHONETIC,
    ALIAS
}
