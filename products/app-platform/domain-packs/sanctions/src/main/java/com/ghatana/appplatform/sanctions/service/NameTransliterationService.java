package com.ghatana.appplatform.sanctions.service;

import java.util.Map;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Transliterates Devanagari (Nepali) names to Latin script for sanctions
 *              name matching (D14-006). Also expands common nicknames.
 * @doc.layer   Application Service
 * @doc.pattern Algorithm, Adapter
 */
public class NameTransliterationService {

    /**
     * Simplified Devanagari→Latin transliteration table for common Nepali characters.
     * Full production implementation would use ICU4J library.
     */
    private static final Map<String, String> DEVANAGARI_TO_LATIN = Map.ofEntries(
            Map.entry("अ", "a"), Map.entry("आ", "aa"), Map.entry("इ", "i"),
            Map.entry("ई", "ee"), Map.entry("उ", "u"), Map.entry("ऊ", "oo"),
            Map.entry("क", "k"), Map.entry("ख", "kh"), Map.entry("ग", "g"),
            Map.entry("घ", "gh"), Map.entry("च", "ch"), Map.entry("छ", "chh"),
            Map.entry("ज", "j"), Map.entry("झ", "jh"), Map.entry("त", "t"),
            Map.entry("थ", "th"), Map.entry("द", "d"), Map.entry("ध", "dh"),
            Map.entry("न", "n"), Map.entry("प", "p"), Map.entry("फ", "ph"),
            Map.entry("ब", "b"), Map.entry("भ", "bh"), Map.entry("म", "m"),
            Map.entry("य", "y"), Map.entry("र", "r"), Map.entry("ल", "l"),
            Map.entry("व", "v"), Map.entry("श", "sh"), Map.entry("ष", "sh"),
            Map.entry("स", "s"), Map.entry("ह", "h"), Map.entry("क्ष", "ksh"),
            Map.entry("त्र", "tr"), Map.entry("ज्ञ", "gya")
    );

    /** Common Nepali nickname expansions for alias-matching (D14-006). */
    private static final Map<String, String[]> NICKNAME_EXPANSIONS = Map.of(
            "ram", new String[]{"rama", "ramesh"},
            "shyam", new String[]{"shyama", "shyamesh"},
            "krishna", new String[]{"krishan", "krisna"},
            "sita", new String[]{"seeta", "seetha"},
            "laxmi", new String[]{"lakshmi", "laxmi"}
    );

    /**
     * Transliterate Devanagari text to Latin script.
     * Returns original string unchanged if it contains no Devanagari characters.
     */
    public String transliterate(String name) {
        if (name == null) return null;
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < name.length()) {
            // Try two-character sequence first (conjuncts like क्ष)
            boolean matched = false;
            if (i + 1 < name.length()) {
                String twoChar = name.substring(i, i + 2);
                String latin = DEVANAGARI_TO_LATIN.get(twoChar);
                if (latin != null) {
                    result.append(latin);
                    i += 2;
                    matched = true;
                }
            }
            if (!matched) {
                String oneChar = name.substring(i, i + 1);
                String latin = DEVANAGARI_TO_LATIN.get(oneChar);
                result.append(latin != null ? latin : oneChar);
                i++;
            }
        }
        return result.toString();
    }

    /**
     * Expand a normalized name to include known nickname/alias variants.
     * Returns the original name plus any expansions.
     */
    public java.util.List<String> expandNicknames(String normalizedName) {
        var variants = new java.util.ArrayList<String>();
        variants.add(normalizedName);
        String[] expansions = NICKNAME_EXPANSIONS.get(normalizedName.toLowerCase());
        if (expansions != null) {
            for (String exp : expansions) variants.add(exp);
        }
        return variants;
    }
}
