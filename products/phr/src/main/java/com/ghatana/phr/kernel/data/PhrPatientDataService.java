package com.ghatana.phr.kernel.data;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.SchemaCreateRequest;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Initializes PHR data stores in Data-Cloud with healthcare governance.
 *
 * <p>All Data-Cloud calls return CompletableFuture; we wrap them with
 * {@code Promise.ofFuture(cf)} at the adapter boundary.</p>
 *
 * <p>Retention policies follow Nepal Directive 2081:
 * <ul>
 *   <li>Patient records: 25 years (healthcare requirement)</li>
 *   <li>Consent records: 10 years</li>
 *   <li>Clinical documents: 7 years</li>
 *   <li>Audit logs: 10 years</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose PHR data store initialization with healthcare retention/governance policies
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class PhrPatientDataService {

    private static final Logger LOG = LoggerFactory.getLogger(PhrPatientDataService.class);

    private final DataCloudKernelAdapter dataCloud;
    private final String tenantId;

    public PhrPatientDataService(DataCloudKernelAdapter dataCloud, String tenantId) {
        this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
    }

    /**
     * Initializes all PHR data schemas in Data-Cloud with healthcare governance.
     *
     * <p>Each schema maps to a logical store with retention, governance, encryption,
     * and audit options encoded into the {@link SchemaCreateRequest} options map.
     *
     * @return Promise that completes when all schemas are initialized
     */
    public Promise<Void> initializeStores() {
        LOG.info("Initializing PHR data schemas in Data-Cloud for tenant={}", tenantId);
        return Promises.all(Stream.of(
            createSchema("patient.records",    "patient-schema-v1",    25, "HEALTHCARE", "STRONG", "DETAILED", false),
            createSchema("patient.consents",   "consent-schema-v1",    10, "HEALTHCARE", "STRONG", "DETAILED", true),
            createSchema("clinical.documents", "document-schema-v1",    7, "HEALTHCARE", "STRONG", "DETAILED", false),
            createSchema("phr.audit",          "audit-schema-v1",      10, "HEALTHCARE", "STRONG", "FULL",     true),
            createSchema("medical.imaging",    "imaging-schema-v1",    25, "HEALTHCARE", "STRONG", "DETAILED", false),
            createSchema("medication.records", "medication-schema-v1",  7, "HEALTHCARE", "STRONG", "DETAILED", false)
        ));
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private Promise<Void> createSchema(
            String name, String schema, int retentionYears,
            String governance, String encryption, String auditLevel, boolean immutable) {
        String datasetId = tenantId + "." + name;
        Map<String, String> options = Map.of(
                "retention.years", String.valueOf(retentionYears),
                "governance", governance,
                "encryption", encryption,
                "audit_level", auditLevel,
                "immutable", String.valueOf(immutable));
        return dataCloud.createSchema(new SchemaCreateRequest(datasetId, Map.of("schema", schema), options));
    }
}
