package com.ghatana.digitalmarketing.application.lead;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.lead.DmLeadCapture;
import com.ghatana.digitalmarketing.domain.lead.DmLeadStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for lead capture forms and CRM-lite state progression.
 *
 * @doc.type class
 * @doc.purpose Captures leads from landing pages and manages CRM-lite status lifecycle (DMOS-F2-011)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmLeadCaptureService {

    Promise<DmLeadCapture> capture(DmOperationContext ctx, CaptureLeadFormCommand command);

    Promise<DmLeadCapture> qualify(DmOperationContext ctx, String leadCaptureId);

    Promise<DmLeadCapture> markContacted(DmOperationContext ctx, String leadCaptureId);

    Promise<DmLeadCapture> convert(DmOperationContext ctx, String leadCaptureId);

    Promise<DmLeadCapture> disqualify(DmOperationContext ctx, String leadCaptureId);

    Promise<Optional<DmLeadCapture>> findById(DmOperationContext ctx, String leadCaptureId);

    Promise<List<DmLeadCapture>> listByStatus(DmOperationContext ctx, DmLeadStatus status, int limit);

    /**
     * Lead form capture command.
     */
    record CaptureLeadFormCommand(
        String landingPageId,
        String email,
        String name,
        String phone,
        Map<String, String> customFields,
        String utmSource,
        String utmMedium,
        String utmCampaign
    ) {
        public CaptureLeadFormCommand {
            Objects.requireNonNull(landingPageId, "landingPageId must not be null");
            Objects.requireNonNull(email, "email must not be null");
            Objects.requireNonNull(customFields, "customFields must not be null");
            if (landingPageId.isBlank()) {
                throw new IllegalArgumentException("landingPageId must not be blank");
            }
            if (email.isBlank()) {
                throw new IllegalArgumentException("email must not be blank");
            }
            customFields = Map.copyOf(customFields);
        }
    }
}
