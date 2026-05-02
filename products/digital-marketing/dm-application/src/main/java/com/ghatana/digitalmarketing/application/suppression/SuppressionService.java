package com.ghatana.digitalmarketing.application.suppression;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.suppression.SuppressionEntry;
import io.activej.promise.Promise;

/**
 * Application service for suppression and do-not-contact management.
 *
 * @doc.type interface
 * @doc.purpose Manage suppression and DNC lifecycle for DMOS contacts and intake
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public interface SuppressionService {

    Promise<SuppressionEntry> addSuppression(DmOperationContext ctx, AddSuppressionCommand command);

    Promise<SuppressionEntry> removeSuppression(DmOperationContext ctx, String email);

    Promise<Boolean> isSuppressed(DmOperationContext ctx, String email);

    record AddSuppressionCommand(String email, String reason) {
        public AddSuppressionCommand {
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("email must not be blank");
            }
        }
    }
}
