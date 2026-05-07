package com.ghatana.digitalmarketing.application.funnel;

import com.ghatana.digitalmarketing.domain.funnel.TrialOnboarding;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for TrialOnboarding persistence.
 *
 * @doc.type interface
 * @doc.purpose Persistence operations for trial onboardings (P3-001)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface TrialOnboardingRepository {

    /**
     * Saves a trial onboarding.
     *
     * @param onboarding the onboarding to save
     * @return the saved onboarding
     */
    Promise<TrialOnboarding> save(TrialOnboarding onboarding);

    /**
     * Finds a trial onboarding by ID.
     *
     * @param id the onboarding ID
     * @return the onboarding if found
     */
    Promise<Optional<TrialOnboarding>> findById(String id);

    /**
     * Finds trial onboarding by lead ID.
     *
     * @param leadId the lead ID
     * @return the onboarding if found
     */
    Promise<Optional<TrialOnboarding>> findByLeadId(String leadId);

    /**
     * Finds trial onboardings by demo workspace ID.
     *
     * @param demoWorkspaceId the demo workspace ID
     * @return list of trial onboardings for the demo workspace
     */
    Promise<List<TrialOnboarding>> findByDemoWorkspaceId(String demoWorkspaceId);

    /**
     * Finds trial onboardings by tenant ID.
     *
     * @param tenantId the tenant ID
     * @return list of trial onboardings for the tenant
     */
    Promise<List<TrialOnboarding>> findByTenantId(String tenantId);

    /**
     * Lists all trial onboardings for a tenant.
     *
     * @param tenantId the tenant ID
     * @return list of trial onboardings
     */
    Promise<List<TrialOnboarding>> listByTenant(String tenantId);

    /**
     * Deletes a trial onboarding.
     *
     * @param id the onboarding ID
     * @return void
     */
    Promise<Void> delete(String id);
}
