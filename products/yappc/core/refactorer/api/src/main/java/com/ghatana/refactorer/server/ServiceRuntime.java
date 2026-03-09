package com.ghatana.refactorer.server;

import com.ghatana.refactorer.server.auth.AccessPolicy;
import com.ghatana.refactorer.server.config.ServerConfig;
import com.ghatana.refactorer.server.jobs.InMemoryJobQueue;
import com.ghatana.refactorer.server.jobs.InMemoryJobStore;
import com.ghatana.refactorer.server.jobs.JobQueue;
import com.ghatana.refactorer.server.jobs.JobService;
import com.ghatana.yappc.refactorer.event.EventBus;
import com.ghatana.yappc.refactorer.event.InMemoryEventBus;
import com.ghatana.refactorer.server.jobs.JobStore;

/**
 * Aggregates shared service-mode components so that server and tests construct
 * consistent wiring.
 *
 *
 *
 * @doc.type record
 *
 * @doc.purpose Act as the composition root wiring controllers, stores,
 * telemetry, and transports.
 *
 * @doc.layer product
 *
 * @doc.pattern Composition Root
 *
 */
public record ServiceRuntime(
        ServerConfig config,
        AccessPolicy accessPolicy,
        JobQueue jobQueue,
        JobStore jobStore,
        JobService jobService) {

    public static Builder builder(ServerConfig config) {
        return new Builder(config);
    }

    public static ServiceRuntime create(ServerConfig config) {
        return builder(config).build();
    }

    public static final class Builder {

        private final ServerConfig config;
        private AccessPolicy accessPolicy;
        private JobQueue jobQueue;
        private JobStore jobStore;

        private Builder(ServerConfig config) {
            this.config = config;
        }

        public Builder withAccessPolicy(AccessPolicy accessPolicy) {
            this.accessPolicy = accessPolicy;
            return this;
        }

        public Builder withJobQueue(JobQueue jobQueue) {
            this.jobQueue = jobQueue;
            return this;
        }

        public Builder withJobStore(JobStore jobStore) {
            this.jobStore = jobStore;
            return this;
        }

        public ServiceRuntime build() {
            AccessPolicy resolvedAccessPolicy
                    = accessPolicy != null ? accessPolicy : new AccessPolicy(config.tenancy());
            JobQueue resolvedJobQueue = jobQueue != null ? jobQueue : new InMemoryJobQueue();
            JobStore resolvedJobStore = jobStore != null ? jobStore : new InMemoryJobStore();
            EventBus eventBus = new InMemoryEventBus();
            JobService jobService
                    = new JobService(resolvedJobQueue, resolvedJobStore, resolvedAccessPolicy, eventBus);
            return new ServiceRuntime(
                    config, resolvedAccessPolicy, resolvedJobQueue, resolvedJobStore, jobService);
        }
    }
}
