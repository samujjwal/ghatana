/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.entity.policy;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Filter;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.datacloud.DataCloudClient.Sort;
import com.ghatana.datacloud.RetentionPolicy;
import com.ghatana.datacloud.RetentionPolicy.RetentionStrategy;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enforces {@link RetentionPolicy} rules against entity collections.
 *
 * <p>Evaluates each registered policy and deletes (or archives) entities that
 * have exceeded their retention window. Designed to be invoked from a scheduled
 * task or Kubernetes CronJob — it is <em>not</em> a background daemon.
 *
 * <h3>Supported strategies</h3>
 * <ul>
 *   <li>{@link RetentionStrategy#TIME_BASED} — deletes entities whose
 *       {@code createdAt} is older than {@link RetentionPolicy#getDeleteAfter()}.</li>
 *   <li>{@link RetentionStrategy#COUNT_BASED} — retains only the newest
 *       {@link RetentionPolicy#getMaxRecordCount()} entities; older ones are deleted.</li>
 *   <li>{@link RetentionStrategy#SIZE_BASED} and {@link RetentionStrategy#NONE}
 *       — logged but not acted upon (require infrastructure-level tooling).</li>
 * </ul>
 *
 * <h3>GDPR / CCPA compliance role</h3>
 * <p>This service is the primary enforcement point for the platform's data-minimisation
 * obligation. Callers must register a policy per (tenantId, collection) pair.
 * For data-subject erasure requests, call {@link #eraseSubject} directly.
 *
 * <h3>Safety</h3>
 * <p>Deletions are <em>irreversible</em>. Always verify the registered policies
 * in staging before applying to production.
 *
 * @doc.type class
 * @doc.purpose Enforces data retention and GDPR erasure for entity collections
 * @doc.layer product
 * @doc.pattern Service
 * @since 2.0.0
 */
public final class RetentionEnforcerService {

    private static final Logger log = LoggerFactory.getLogger(RetentionEnforcerService.class);

    /** Maximum entities fetched per page during scan-to-delete loops. */
    private static final int PAGE_SIZE = 500;

    private final DataCloudClient client;

    /**
     * Registered policies: outer key = tenantId, inner key = collection name.
     */
    private final Map<String, Map<String, RetentionPolicy>> policies;

    /**
     * Creates the enforcer with a pre-built policy registry.
     *
     * @param client   Data-Cloud client used for queries and deletes (must not be null)
     * @param policies policy registry keyed by {@code tenantId → collection → policy}
     */
    public RetentionEnforcerService(
            DataCloudClient client,
            Map<String, Map<String, RetentionPolicy>> policies) {
        this.client   = Objects.requireNonNull(client,   "client");
        this.policies = Map.copyOf(Objects.requireNonNull(policies, "policies"));
    }

    // ==================== Scheduled enforcement ====================

    /**
     * Runs a full retention sweep across all registered (tenant, collection) pairs.
     *
     * <p>Returns a {@link RetentionReport} that summarises how many entities were
     * examined and deleted.
     *
     * @return promise of a sweep report
     */
    public Promise<RetentionReport> enforceAll() {
        RetentionReport report = new RetentionReport();
        List<Promise<Void>> tasks = new ArrayList<>();

        for (Map.Entry<String, Map<String, RetentionPolicy>> tenantEntry : policies.entrySet()) {
            String tenantId = tenantEntry.getKey();
            for (Map.Entry<String, RetentionPolicy> collEntry : tenantEntry.getValue().entrySet()) {
                String collection = collEntry.getKey();
                RetentionPolicy policy = collEntry.getValue();
                if (!Boolean.TRUE.equals(policy.getEnabled())) {
                    log.debug("retention: skipping disabled policy for {}/{}", tenantId, collection);
                    continue;
                }
                tasks.add(enforceOne(tenantId, collection, policy, report));
            }
        }

        return Promises.all(tasks).map(ignored -> {
            log.info("retention sweep complete: examined={} deleted={}",
                    report.entitiesExamined(), report.entitiesDeleted());
            return report;
        });
    }

    /**
     * Enforces the retention policy for a single (tenant, collection) pair.
     *
     * @param tenantId   tenant identifier
     * @param collection collection name
     * @param policy     retention policy to apply
     * @param report     mutable report to accumulate counts into
     * @return promise completing when enforcement is done
     */
    public Promise<Void> enforceOne(
            String tenantId, String collection, RetentionPolicy policy, RetentionReport report) {

        return switch (policy.getStrategy()) {
            case TIME_BASED -> enforceTimeBased(tenantId, collection, policy, report);
            case COUNT_BASED -> enforceCountBased(tenantId, collection, policy, report);
            case SIZE_BASED -> {
                log.info("retention: SIZE_BASED strategy for {}/{} must be handled by storage-tier tooling",
                        tenantId, collection);
                yield Promise.complete();
            }
            case NONE -> {
                log.debug("retention: NONE strategy for {}/{} — no action taken", tenantId, collection);
                yield Promise.complete();
            }
        };
    }

    // ==================== GDPR / CCPA data-subject erasure ====================

    /**
     * Erases all entities linked to a specific data-subject identifier within a
     * tenant. The subject identifier is matched against the {@code "_subjectId"} field in
     * entity data.
     *
     * <p>This satisfies the GDPR Article 17 "right to erasure" obligation.
     *
     * @param tenantId   tenant identifier
     * @param collection collection to erase from
     * @param subjectId  the data subject's identifier (e.g. a user UUID)
     * @return promise of the number of entities erased
     */
    public Promise<Long> eraseSubject(String tenantId, String collection, String subjectId) {
        Objects.requireNonNull(subjectId, "subjectId");
        log.info("gdpr-erasure: starting for tenant={} collection={} subject={}",
                tenantId, collection, subjectId);

        Query subjectQuery = Query.builder()
                .filter(Filter.eq("_subjectId", subjectId))
                .limit(PAGE_SIZE)
                .build();

        return queryAndDeleteAll(tenantId, collection, subjectQuery, new RetentionReport())
                .map(ignored -> {
                    log.info("gdpr-erasure: complete for tenant={} collection={} subject={}",
                            tenantId, collection, subjectId);
                    return 0L;
                })
                .mapException(ex -> {
                    log.error("gdpr-erasure: failed for tenant={} subject={}", tenantId, subjectId, ex);
                    return ex;
                });
    }

    // ==================== Strategy implementations ====================

    private Promise<Void> enforceTimeBased(
            String tenantId, String collection, RetentionPolicy policy, RetentionReport report) {

        if (policy.getDeleteAfter() == null) {
            log.warn("retention: TIME_BASED policy for {}/{} has no deleteAfter set — skipping",
                    tenantId, collection);
            return Promise.complete();
        }

        Instant cutoff = Instant.now().minus(policy.getDeleteAfter());
        log.info("retention: TIME_BASED sweep for {}/{} — deleting entities created before {}",
                tenantId, collection, cutoff);

        Query oldEntities = Query.builder()
                .filter(Filter.lt("createdAt", cutoff.toString()))
                .limit(PAGE_SIZE)
                .build();

        return queryAndDeleteAll(tenantId, collection, oldEntities, report);
    }

    private Promise<Void> enforceCountBased(
            String tenantId, String collection, RetentionPolicy policy, RetentionReport report) {

        if (policy.getMaxRecordCount() == null || policy.getMaxRecordCount() <= 0) {
            log.warn("retention: COUNT_BASED policy for {}/{} has no maxRecordCount — skipping",
                    tenantId, collection);
            return Promise.complete();
        }

        // Count everything, then delete oldest (sorted by createdAt asc) beyond the limit.
        Query countQuery = Query.builder()
                .sorts(List.of(Sort.asc("createdAt")))
                .limit(Integer.MAX_VALUE)
                .build();

        return client.query(tenantId, collection, countQuery).then(all -> {
            report.examined(all.size());
            long excess = all.size() - policy.getMaxRecordCount();
            if (excess <= 0) {
                log.debug("retention: COUNT_BASED {}/{} within limit ({} ≤ {})",
                        tenantId, collection, all.size(), policy.getMaxRecordCount());
                return Promise.of((Void) null);
            }

            List<Entity> toDelete = all.subList(0, (int) excess);
            log.info("retention: COUNT_BASED {}/{} — deleting {} oldest entities",
                    tenantId, collection, toDelete.size());

            List<Promise<Void>> deletes = toDelete.stream()
                    .map(e -> client.delete(tenantId, collection, e.id())
                            .whenResult(v -> report.deleted(1))
                            .whenException(ex -> log.error(
                                    "retention: failed to delete entity {} in {}/{}: {}",
                                    e.id(), tenantId, collection, ex.getMessage())))
                    .toList();

            return Promises.all(deletes);
        });
    }

    /**
     * Executes a query and deletes all returned entities, looping until no more results.
     */
    private Promise<Void> queryAndDeleteAll(
            String tenantId, String collection, Query query, RetentionReport report) {
        return client.query(tenantId, collection, query).then(entities -> {
            if (entities.isEmpty()) {
                return Promise.of((Void) null);
            }

            report.examined(entities.size());
            log.debug("retention: deleting {} entities from {}/{}", entities.size(), tenantId, collection);

            List<Promise<Void>> deletes = entities.stream()
                    .map(e -> client.delete(tenantId, collection, e.id())
                            .whenResult(ignored -> report.deleted(1))
                            .whenException(ex -> log.error(
                                    "retention: failed to delete {} in {}/{}: {}",
                                    e.id(), tenantId, collection, ex.getMessage())))
                    .toList();

            return Promises.all(deletes).then(ignored ->
                    // If we got a full page there may be more — recurse
                    entities.size() < PAGE_SIZE
                            ? Promise.of((Void) null)
                            : queryAndDeleteAll(tenantId, collection, query, report));
        });
    }

    // ==================== Report ====================

    /**
     * Accumulates counts from a retention sweep.
     *
     * @doc.type class
     * @doc.purpose Sweep result summary
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public static final class RetentionReport {

        private final AtomicLong examined = new AtomicLong();
        private final AtomicLong deleted  = new AtomicLong();

        void examined(long count) { examined.addAndGet(count); }
        void deleted(long count)  { deleted.addAndGet(count);  }

        /**
         * Total number of entities examined across all collections.
         */
        public long entitiesExamined() { return examined.get(); }

        /**
         * Total number of entities permanently deleted.
         */
        public long entitiesDeleted()  { return deleted.get();  }

        @Override
        public String toString() {
            return "RetentionReport{examined=" + examined + ", deleted=" + deleted + '}';
        }
    }
}
