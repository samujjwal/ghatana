package com.ghatana.digitalmarketing.domain.research;

/**
 * Search intent classification for keywords in competitor and keyword research.
 *
 * @doc.type class
 * @doc.purpose DMOS keyword intent classification for F1-011 research workflow
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum KeywordIntent {
    /** User is looking to take immediate action (buy, hire, contact). */
    TRANSACTIONAL,
    /** User is researching options before deciding. */
    INFORMATIONAL,
    /** User is navigating to a specific brand or site. */
    NAVIGATIONAL,
    /** User is comparing options or investigating commercial choices. */
    COMMERCIAL_INVESTIGATION
}
