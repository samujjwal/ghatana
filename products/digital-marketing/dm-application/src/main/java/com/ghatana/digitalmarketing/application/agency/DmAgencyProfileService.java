package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.agency.DmAgencyProfile;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Application service for managing agency mode profiles.
 *
 * @doc.type interface
 * @doc.purpose Use-case boundary for agency profile management (DMOS-F4-003)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmAgencyProfileService {

    Promise<DmAgencyProfile> create(DmOperationContext ctx, CreateAgencyProfileCommand cmd);

    Promise<DmAgencyProfile> addManagedTenant(DmOperationContext ctx, String agencyProfileId, String managedTenantId);

    Promise<DmAgencyProfile> removeManagedTenant(DmOperationContext ctx, String agencyProfileId, String managedTenantId);

    Promise<Optional<DmAgencyProfile>> findById(DmOperationContext ctx, String agencyProfileId);

    Promise<List<DmAgencyProfile>> listAll(DmOperationContext ctx);

    record CreateAgencyProfileCommand(String displayName) {
        public CreateAgencyProfileCommand {
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
        }
    }
}
