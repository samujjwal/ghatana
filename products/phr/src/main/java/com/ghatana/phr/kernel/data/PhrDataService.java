package com.ghatana.phr.kernel.data;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.SchemaCreateRequest;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Initializes PHR data stores in Data-Cloud with healthcare governance.
 *
 * <p>All Data-Cloud calls return CompletableFuture; we wrap them with
 * {@code Promise.ofFuture(cf)} at the adapter boundary.</p>
 *
 * <p>Healthcare retention requirements:
 * <ul>
 *   <li>Patient records: 25 years (healthcare requirement)</li>
 *   <li>Consent records: 10 years</li>
 *   <li>Document records: 25 years</li>
 *   <li>Audit logs: 7 years (Nepal Directive 2081)</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose PHR data store initialization with healthcare retention/governance policies
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class PhrDataService {

    private static final Logger LOG = LoggerFactory.getLogger(PhrDataService.class);

    private final DataCloudKernelAdapter dataCloud;

    public PhrDataService(DataCloudKernelAdapter dataCloud) {
        this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud cannot be null");
    }

    /**
     * Initializes all PHR data schemas in Data-Cloud with healthcare governance.
     *
     * @return Promise completing when all schemas are initialized
     */
    public Promise<Void> initializeStores() {
        LOG.info("Initializing PHR data schemas in Data-Cloud");
        return Promises.all(List.of(
            createSchema("patient.records",    "patient-schema-v1",   25, "HEALTHCARE", "STRONG", "DETAILED", false),
            createSchema("patient.consents",   "consent-schema-v1",   10, "HEALTHCARE", "STRONG", "DETAILED", true),
            createSchema("patient.documents",  "document-schema-v1",  25, "HEALTHCARE", "STRONG", "DETAILED", false),
            createSchema("patient.appointments","appointment-schema-v1", 7, "HEALTHCARE", "STANDARD", "BASIC", false),
            createSchema("phr.audit",          "audit-schema-v1",      7, "HEALTHCARE", "STRONG", "FULL",     true)
        ));
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private Promise<Void> createSchema(
            String name, String schema, int retentionYears,
            String governance, String encryption, String auditLevel, boolean immutable) {
        Map<String, String> options = Map.of(
                "retention.years", String.valueOf(retentionYears),
                "governance", governance,
                "encryption", encryption,
                "audit_level", auditLevel,
                "immutable", String.valueOf(immutable));
        return dataCloud.createSchema(new SchemaCreateRequest(name, Map.of("schema", schema), options));
    }
}
