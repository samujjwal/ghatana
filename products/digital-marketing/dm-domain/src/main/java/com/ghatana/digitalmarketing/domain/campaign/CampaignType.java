package com.ghatana.digitalmarketing.domain.campaign;

/**
 * The channel or method type of a DMOS campaign.
 *
 * @doc.type class
 * @doc.purpose DMOS campaign type enum classifying the delivery channel
 * @doc.layer product
 * @doc.pattern Enum
 */
public enum CampaignType {
    /** Email marketing campaign. */
    EMAIL,
    /** Social media advertising campaign. */
    SOCIAL,
    /** Search/display advertising campaign. */
    PAID_SEARCH,
    /** Push notification campaign. */
    PUSH,
    /** SMS / text message campaign. */
    SMS,
    /** Multi-channel campaign spanning more than one delivery type. */
    OMNICHANNEL
}
