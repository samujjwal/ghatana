package com.ghatana.digitalmarketing.domain.content;

/**
 * Ordered sections of a conversion-focused landing page draft.
 *
 * <p>Each section corresponds to a {@link ContentBlock} within a
 * {@link ContentVersion} of type {@link ContentItemType#LANDING_PAGE}.
 * Sections must all be present in a well-formed draft; claim-backed sections
 * ({@link #PROOF}, {@link #OFFER}) are flagged for evidence review when
 * no approved claims are provided.</p>
 *
 * @doc.type class
 * @doc.purpose Defines required conversion sections for DMOS landing page content versions
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum LandingPageSection {

    /**
     * Hero section: headline, sub-headline, primary call-to-action (above-the-fold).
     */
    HERO,

    /**
     * Problem section: surfaces the customer pain point the offer addresses.
     */
    PROBLEM,

    /**
     * Offer section: describes the service, pricing intent, and key differentiators.
     * Claim-backed; triggers evidence review if no approved claim is supplied.
     */
    OFFER,

    /**
     * Social proof section: testimonials, case study summaries, or review snippets.
     * Claim-backed; the generator must never fabricate testimonials — only reference
     * approved claim entries or emit a missing-proof warning instead.
     */
    PROOF,

    /**
     * Call-to-action section: primary form anchor, phone number, or conversion trigger.
     */
    CTA,

    /**
     * FAQ section: up to five common objections answered in the client voice tone.
     */
    FAQ,

    /**
     * Disclaimer and legal disclosure section.
     * Populated from approved {@link DisclosureReference} entries; left as a
     * placeholder when none are configured.
     */
    DISCLAIMER
}
