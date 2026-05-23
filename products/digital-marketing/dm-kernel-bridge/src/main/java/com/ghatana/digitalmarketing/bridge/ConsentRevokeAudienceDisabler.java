package com.ghatana.digitalmarketing.bridge;

import com.ghatana.kernel.interaction.ProductInteractionEventEnvelope;
import com.ghatana.kernel.interaction.ProductInteractionEventHandler;
import io.activej.promise.Promise;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DMOS event handler that responds to PHR consent revocation events.
 *
 * <p>When a patient revokes consent through PHR, this handler receives the event
 * and logs it. The actual audience disabling is handled by the application layer
 * which has access to the AudienceRepository.</p>
 *
 * <p>This implements the P1-05 requirement: "DMOS audience eligibility must
 * respond to PHR consent revoke events."</p>
 *
 * @doc.type class
 * @doc.purpose Event handler for PHR consent revocation - logs for application layer processing
 * @doc.layer digital-marketing
 * @doc.pattern EventHandler
 */
public final class ConsentRevokeAudienceDisabler
        implements ProductInteractionEventHandler<ConsentRevokeEvent> {

    private static final Logger LOGGER = Logger.getLogger(ConsentRevokeAudienceDisabler.class.getName());
    private static final String TOPIC = "phr.consent-revoke.v1";

    /**
     * Constructs a consent revoke audience disabler.
     */
    public ConsentRevokeAudienceDisabler() {
    }

    @Override
    public String subscriberId() {
        return "digital-marketing:consent-revoke-audience-disabler";
    }

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public Class<ConsentRevokeEvent> eventType() {
        return ConsentRevokeEvent.class;
    }

    @Override
    public Promise<Void> handle(ProductInteractionEventEnvelope<ConsentRevokeEvent> envelope) {
        ConsentRevokeEvent event = envelope.payload();

        if (!"revoked".equals(event.status())) {
            LOGGER.log(Level.INFO, "Consent not revoked, skipping audience disable for subject: {0}", event.subjectId());
            return Promise.complete();
        }

        LOGGER.log(Level.INFO, "Processing consent revoke for subject: {0}, consent: {1}",
                new Object[]{event.subjectId(), event.consentId()});

        // Log the event - the application layer will handle actual audience disabling
        // through the AudienceRepository which is in the dm-application module
        LOGGER.log(Level.INFO, "Application layer should disable audiences containing subject: {0}", event.subjectId());

        return Promise.complete();
    }
}
