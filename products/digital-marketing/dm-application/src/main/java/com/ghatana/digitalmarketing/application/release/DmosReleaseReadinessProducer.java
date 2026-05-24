package com.ghatana.digitalmarketing.application.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * YAPPC-002: Release readiness producer for DMOS (Digital Marketing Operations System).
 *
 * <p>Reads DMOS release evidence from .kernel/evidence/product-release-evidence-pack.digital-marketing.json
 * and converts it to a Data Cloud release-readiness payload.</p>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * DmosReleaseReadinessProducer producer = new DmosReleaseReadinessProducer(objectMapper);
 * Promise<DmosReleaseEvidence> promise = producer.readEvidence();
 * DmosReleaseEvidence evidence = runPromise(() -> promise);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose DMOS release readiness evidence producer
 * @doc.layer product
 * @doc.pattern Producer
 */
public class DmosReleaseReadinessProducer {

    private static final Logger LOG = LoggerFactory.getLogger(DmosReleaseReadinessProducer.class);
    private static final String EVIDENCE_FILE_PATH = ".kernel/evidence/product-release-evidence-pack.digital-marketing.json";

    private final ObjectMapper objectMapper;

    public DmosReleaseReadinessProducer(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
    }

    /**
     * Reads DMOS release evidence from the evidence file.
     *
     * @return Promise that completes with the DMOS release evidence
     */
    public Promise<DmosReleaseEvidence> readEvidence() {
        try {
            Path evidencePath = Path.of(EVIDENCE_FILE_PATH);
            if (!Files.exists(evidencePath)) {
                LOG.warn("DMOS evidence file not found at: {}", EVIDENCE_FILE_PATH);
                return Promise.ofException(new IOException("Evidence file not found: " + EVIDENCE_FILE_PATH));
            }

            String content = Files.readString(evidencePath);
            DmosReleaseEvidence evidence = objectMapper.readValue(content, DmosReleaseEvidence.class);

            LOG.info("Read DMOS release evidence: productId={}, status={}", 
                evidence.productId(), evidence.status());

            return Promise.of(evidence);
        } catch (IOException e) {
            LOG.error("Failed to read DMOS release evidence", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Converts DMOS release evidence to a Data Cloud release-readiness payload.
     *
     * @param evidence the DMOS release evidence
     * @param tenantId the tenant ID
     * @return release readiness payload in Data Cloud wire format
     */
    public ProductReleaseReadinessPayload
            toDataCloudFormat(DmosReleaseEvidence evidence, String tenantId) {
        
        Map<String, Object> evidenceMap = new HashMap<>();
        evidenceMap.put("categories", evidence.categories());
        evidenceMap.put("generatedAt", evidence.generatedAt());

        List<Map<String, Object>> blockingGaps = new ArrayList<>();
        List<Map<String, Object>> belowTargetDimensions = new ArrayList<>();

        // Extract blocking gaps and below-target dimensions from categories
        for (Map.Entry<String, CategoryEvidence> entry : evidence.categories().entrySet()) {
            CategoryEvidence category = entry.getValue();
            if ("failed".equals(category.status())) {
                blockingGaps.add(Map.of(
                    "category", entry.getKey(),
                    "refs", category.refs(),
                    "missing", category.missing()
                ));
            }
        }

        double averageScore = "passed".equals(evidence.status()) ? 1.0 : 0.0;
        double releaseTargetScore = "passed".equals(evidence.status()) ? 0.90 : 0.0;

        return new ProductReleaseReadinessPayload(
            evidence.productId(),
            getDmosVersion(),
            "production",
            "passed".equals(evidence.status()) ? "pass" : "fail",
            averageScore,
            releaseTargetScore,
            Instant.parse(evidence.generatedAt()),
            Map.copyOf(evidenceMap),
            List.copyOf(blockingGaps),
            List.copyOf(belowTargetDimensions),
            tenantId);
    }

    private String getDmosVersion() {
        // In production, this would read from build.gradle.kts or a version file
        return "1.0.0";
    }

    /**
     * Product-local DTO matching the Data Cloud release-readiness wire shape.
     */
    public record ProductReleaseReadinessPayload(
            String productId,
            String productVersion,
            String releaseTarget,
            String releaseVerdict,
            double averageScore,
            double releaseTargetScore,
            Instant generatedAt,
            Map<String, Object> evidence,
            List<Map<String, Object>> blockingGaps,
            List<Map<String, Object>> belowTargetDimensions,
            String tenantId) {
    }

    /**
     * DMOS release evidence domain object.
     */
    public static class DmosReleaseEvidence {
        private final String generatedAt;
        private final String productId;
        private final String status;
        private final Map<String, CategoryEvidence> categories;

        public DmosReleaseEvidence(
                String generatedAt,
                String productId,
                String status,
                Map<String, CategoryEvidence> categories) {
            this.generatedAt = generatedAt;
            this.productId = productId;
            this.status = status;
            this.categories = categories;
        }

        public String generatedAt() { return generatedAt; }
        public String productId() { return productId; }
        public String status() { return status; }
        public Map<String, CategoryEvidence> categories() { return categories; }
    }

    /**
     * Category evidence domain object.
     */
    public static class CategoryEvidence {
        private final List<String> refs;
        private final List<String> missing;
        private final String status;

        public CategoryEvidence(List<String> refs, List<String> missing, String status) {
            this.refs = refs;
            this.missing = missing;
            this.status = status;
        }

        public List<String> refs() { return refs; }
        public List<String> missing() { return missing; }
        public String status() { return status; }
    }
}
