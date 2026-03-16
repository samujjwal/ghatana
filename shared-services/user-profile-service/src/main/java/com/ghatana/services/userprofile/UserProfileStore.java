package com.ghatana.services.userprofile;

import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Storage abstraction for {@link UserProfile} records.
 *
 * <p>All operations are tenant-scoped: the combination of
 * {@code (tenantId, userId)} forms the natural key.</p>
 *
 * <p>Implementations MUST be safe to call from within the ActiveJ event loop
 * (i.e. they must return {@link Promise} and run any blocking work off-loop
 * via {@code Promise.ofBlocking}).</p>
 *
 * @doc.type interface
 * @doc.purpose Storage contract for user profiles (multi-tenant)
 * @doc.layer platform
 * @doc.pattern Repository
 */
public interface UserProfileStore {

    /**
     * Retrieves a profile by tenant + user.
     *
     * @param tenantId tenant scope
     * @param userId   unique user identifier
     * @return Promise of an Optional profile
     */
    Promise<Optional<UserProfile>> findByTenantAndUser(String tenantId, String userId);

    /**
     * Creates or fully replaces a user profile.
     *
     * @param profile the profile to persist
     * @return Promise of the persisted profile (with timestamps set)
     */
    Promise<UserProfile> upsert(UserProfile profile);

    /**
     * Deletes a profile. No-op if the profile does not exist.
     *
     * @param tenantId tenant scope
     * @param userId   unique user identifier
     * @return Promise that completes when deletion is done
     */
    Promise<Void> delete(String tenantId, String userId);
}
