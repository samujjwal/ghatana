package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.domain.agency.DmAgencyProfile;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for agency profile persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for agency profile storage (DMOS-F4-003)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmAgencyProfileRepository {

    Promise<DmAgencyProfile> save(DmAgencyProfile profile);

    Promise<DmAgencyProfile> update(DmAgencyProfile profile);

    Promise<Optional<DmAgencyProfile>> findById(String id);

    Promise<Optional<DmAgencyProfile>> findByAgencyTenantId(String agencyTenantId);

    Promise<List<DmAgencyProfile>> listAll();
}
