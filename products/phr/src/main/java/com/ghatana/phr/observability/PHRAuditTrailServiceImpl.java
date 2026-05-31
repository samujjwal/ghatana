package com.ghatana.phr.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataWriteRequest;
import com.ghatana.kernel.observability.AuditTrailService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * PHR audit trail service implementation using DataCloud persistence.
 *
 * <p>Provides immutable audit trail with hash chaining for tamper-evident logging.
 * Events are persisted to DataCloud with cryptographic hash verification.</p>
 *
 * @doc.type class
 * @doc.purpose PHR DataCloud-backed audit trail service
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class PHRAuditTrailServiceImpl implements AuditTrailService {

    private static final String DATASET_ID = "phr.audit";
    private static final Map<String, List<AuditTrailEvent>> DURABLE_ENTRIES = new ConcurrentHashMap<>();
    
    private final DataCloudKernelAdapter dataCloud;
    private final String storageKey;
    private final ObjectMapper objectMapper;

    public PHRAuditTrailServiceImpl(DataCloudKernelAdapter dataCloud) {
        this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud cannot be null");
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.storageKey = DATASET_ID + "#" + System.identityHashCode(dataCloud);
    }

    @Override
    public void recordAuditEvent(AuditTrailEvent event) {
        List<AuditTrailEvent> entries = DURABLE_ENTRIES.computeIfAbsent(storageKey, ignored -> new CopyOnWriteArrayList<>());
        
        // Check for duplicate events
        boolean exists = entries.stream()
            .anyMatch(existing -> existing.getEventId().equals(event.getEventId()));
        
        if (!exists) {
            // Compute hash for the event
            String eventHash = computeEventHash(event);
            
            // Link to previous event's hash
            String previousHash = entries.isEmpty() ? "0" : computeEventHash(entries.get(entries.size() - 1));
            
            // Create linked event with hash chain
            AuditTrailEvent linkedEvent = AuditTrailEvent.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .entityId(event.getEntityId())
                .userId(event.getUserId())
                .tenantId(event.getTenantId())
                .action(event.getAction())
                .data(event.getData())
                .timestamp(event.getTimestamp())
                .previousHash(previousHash)
                .build();
            
            entries.add(linkedEvent);
            
            // Persist to DataCloud
            byte[] payload = (
                event.getEventId() + "|" +
                event.getEntityId() + "|" +
                event.getEventType() + "|" +
                eventHash
            ).getBytes(StandardCharsets.UTF_8);
            
            dataCloud.writeData(new DataWriteRequest(
                DATASET_ID,
                event.getEventId(),
                payload,
                Map.of(
                    "entityId", event.getEntityId(),
                    "eventType", event.getEventType(),
                    "timestamp", Instant.ofEpochMilli(event.getTimestamp()).toString(),
                    "previousHash", previousHash,
                    "eventHash", eventHash,
                    "retention", "25years"
                )
            ));
        }
    }

    @Override
    public List<AuditTrailEvent> queryAuditEvents(AuditQuery query) {
        List<AuditTrailEvent> entries = DURABLE_ENTRIES.getOrDefault(storageKey, List.of());
        
        return entries.stream()
            .filter(event -> query.getEntityId() == null || event.getEntityId().equals(query.getEntityId()))
            .filter(event -> query.getUserId() == null || event.getUserId().equals(query.getUserId()))
            .filter(event -> query.getTenantId() == null || event.getTenantId().equals(query.getTenantId()))
            .filter(event -> query.getEventType() == null || event.getEventType().equals(query.getEventType()))
            .filter(event -> event.getTimestamp() >= query.getStartTime())
            .filter(event -> event.getTimestamp() <= query.getEndTime())
            .limit(query.getLimit())
            .collect(Collectors.toList());
    }

    @Override
    public ImmutableAuditTrail getImmutableTrail(String entityId) {
        List<AuditTrailEvent> entries = DURABLE_ENTRIES.getOrDefault(storageKey, List.of());
        
        List<AuditTrailEvent> entityEvents = entries.stream()
            .filter(event -> event.getEntityId().equals(entityId))
            .collect(Collectors.toList());
        
        String merkleRoot = computeMerkleRoot(entityEvents);
        boolean isIntact = verifyHashChain(entityEvents);
        
        return new ImmutableAuditTrailImpl(entityId, entityEvents, merkleRoot, isIntact);
    }

    @Override
    public VerificationResult verifyTrailIntegrity(String entityId) {
        List<AuditTrailEvent> entries = DURABLE_ENTRIES.getOrDefault(storageKey, List.of());
        
        List<AuditTrailEvent> entityEvents = entries.stream()
            .filter(event -> event.getEntityId().equals(entityId))
            .collect(Collectors.toList());
        
        List<String> violations = new ArrayList<>();
        boolean isIntact = verifyHashChain(entityEvents);
        
        if (!isIntact) {
            violations.add("Hash chain integrity check failed");
        }
        
        String message = isIntact ? "Audit trail intact" : "Audit trail integrity violations detected";
        return new VerificationResult(isIntact, message, violations);
    }

    private String computeEventHash(AuditTrailEvent event) {
        try {
            String eventString = event.getEventId() + "|" +
                event.getEventType() + "|" +
                event.getEntityId() + "|" +
                event.getUserId() + "|" +
                event.getTenantId() + "|" +
                event.getAction() + "|" +
                event.getTimestamp() + "|" +
                event.getPreviousHash();
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(eventString.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String computeMerkleRoot(List<AuditTrailEvent> events) {
        if (events.isEmpty()) {
            return "0";
        }
        
        List<String> hashes = events.stream()
            .map(this::computeEventHash)
            .collect(Collectors.toList());
        
        while (hashes.size() > 1) {
            List<String> newLevel = new ArrayList<>();
            for (int i = 0; i < hashes.size(); i += 2) {
                String left = hashes.get(i);
                String right = (i + 1 < hashes.size()) ? hashes.get(i + 1) : left;
                String combined = left + right;
                newLevel.add(computeHash(combined));
            }
            hashes = newLevel;
        }
        
        return hashes.get(0);
    }

    private String computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private boolean verifyHashChain(List<AuditTrailEvent> events) {
        if (events.isEmpty()) {
            return true;
        }
        
        for (int i = 0; i < events.size(); i++) {
            AuditTrailEvent event = events.get(i);
            String expectedPreviousHash = (i == 0) ? "0" : computeEventHash(events.get(i - 1));
            
            if (!Objects.equals(event.getPreviousHash(), expectedPreviousHash)) {
                return false;
            }
        }
        
        return true;
    }

    private static class ImmutableAuditTrailImpl implements ImmutableAuditTrail {
        private final String entityId;
        private final List<AuditTrailEvent> events;
        private final String merkleRoot;
        private final boolean isIntact;

        ImmutableAuditTrailImpl(String entityId, List<AuditTrailEvent> events, String merkleRoot, boolean isIntact) {
            this.entityId = entityId;
            this.events = List.copyOf(events);
            this.merkleRoot = merkleRoot;
            this.isIntact = isIntact;
        }

        @Override
        public String getEntityId() {
            return entityId;
        }

        @Override
        public List<AuditTrailEvent> getEvents() {
            return events;
        }

        @Override
        public String getMerkleRoot() {
            return merkleRoot;
        }

        @Override
        public boolean isIntact() {
            return isIntact;
        }
    }
}
