package com.ghatana.phr.observability;

import com.ghatana.kernel.observability.AuditTrailService;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Business logic service for PHRAuditTrailService
 *
 * @doc.type class
 * @doc.purpose Business logic service for PHRAuditTrailService
 * @doc.layer product
 * @doc.pattern Service
 */
public class PHRAuditTrailServiceImpl implements AuditTrailService {
    private final Map<String, List<AuditLogEntry>> auditLogs = new ConcurrentHashMap<>();
    private final MerkleTreeService merkleTreeService = new MerkleTreeService();

    @Override
    public void recordAuditEvent(AuditEvent event) {
        String entityId = event.getEntityId();
        String previousHash = getLastEventHash(entityId);
        
        AuditLogEntry entry = new AuditLogEntry();
        entry.setEventId(event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString());
        entry.setEventType(event.getEventType());
        entry.setEntityId(entityId);
        entry.setUserId(event.getUserId());
        entry.setTenantId(event.getTenantId());
        entry.setAction(event.getAction());
        entry.setData(event.getData());
        entry.setTimestamp(event.getTimestamp());
        entry.setPreviousHash(previousHash);
        entry.setHash(calculateHash(entry, previousHash));
        
        auditLogs.computeIfAbsent(entityId, k -> new ArrayList<>()).add(entry);
        merkleTreeService.addLeaf(entry.getHash());
    }

    @Override
    public List<AuditEvent> queryAuditEvents(AuditQuery query) {
        return auditLogs.values().stream()
            .flatMap(List::stream)
            .filter(entry -> matchesQuery(entry, query))
            .map(this::toAuditEvent)
            .limit(query.getLimit())
            .collect(Collectors.toList());
    }

    @Override
    public ImmutableAuditTrail getImmutableTrail(String entityId) {
        List<AuditEvent> events = auditLogs.getOrDefault(entityId, Collections.emptyList())
            .stream()
            .map(this::toAuditEvent)
            .collect(Collectors.toList());
        
        String merkleRoot = merkleTreeService.getRoot();
        boolean intact = verifyHashChain(entityId);
        
        return new PHRImmutableAuditTrail(entityId, events, merkleRoot, intact);
    }

    @Override
    public VerificationResult verifyTrailIntegrity(String entityId) {
        List<AuditLogEntry> entries = auditLogs.getOrDefault(entityId, Collections.emptyList());
        List<String> violations = new ArrayList<>();
        
        for (int i = 1; i < entries.size(); i++) {
            AuditLogEntry current = entries.get(i);
            AuditLogEntry previous = entries.get(i - 1);
            
            String expectedHash = calculateHash(current, previous.getHash());
            if (!expectedHash.equals(current.getHash())) {
                violations.add("Hash mismatch at event " + current.getEventId());
            }
        }
        
        boolean valid = violations.isEmpty();
        String message = valid ? "Audit trail intact" : "Integrity violations detected";
        
        return new VerificationResult(valid, message, violations);
    }

    private String getLastEventHash(String entityId) {
        List<AuditLogEntry> entries = auditLogs.get(entityId);
        if (entries == null || entries.isEmpty()) {
            return "0";
        }
        return entries.get(entries.size() - 1).getHash();
    }

    private String calculateHash(AuditLogEntry entry, String previousHash) {
        String data = entry.getEventId() + entry.getTimestamp() + 
                     entry.getData() + previousHash;
        return DigestUtils.sha256Hex(data);
    }

    private boolean verifyHashChain(String entityId) {
        List<AuditLogEntry> entries = auditLogs.getOrDefault(entityId, Collections.emptyList());
        
        for (int i = 1; i < entries.size(); i++) {
            AuditLogEntry current = entries.get(i);
            AuditLogEntry previous = entries.get(i - 1);
            
            String expectedHash = calculateHash(current, previous.getHash());
            if (!expectedHash.equals(current.getHash())) {
                return false;
            }
        }
        
        return true;
    }

    private boolean matchesQuery(AuditLogEntry entry, AuditQuery query) {
        if (query.getEntityId() != null && !query.getEntityId().equals(entry.getEntityId())) {
            return false;
        }
        if (query.getUserId() != null && !query.getUserId().equals(entry.getUserId())) {
            return false;
        }
        if (query.getTenantId() != null && !query.getTenantId().equals(entry.getTenantId())) {
            return false;
        }
        if (query.getEventType() != null && !query.getEventType().equals(entry.getEventType())) {
            return false;
        }
        if (entry.getTimestamp() < query.getStartTime() || entry.getTimestamp() > query.getEndTime()) {
            return false;
        }
        return true;
    }

    private AuditEvent toAuditEvent(AuditLogEntry entry) {
        return AuditEvent.builder()
            .eventId(entry.getEventId())
            .eventType(entry.getEventType())
            .entityId(entry.getEntityId())
            .userId(entry.getUserId())
            .tenantId(entry.getTenantId())
            .action(entry.getAction())
            .data(entry.getData())
            .timestamp(entry.getTimestamp())
            .previousHash(entry.getPreviousHash())
            .build();
    }

    private static class AuditLogEntry {
        private String eventId;
        private String eventType;
        private String entityId;
        private String userId;
        private String tenantId;
        private String action;
        private Map<String, Object> data;
        private long timestamp;
        private String previousHash;
        private String hash;

        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getEntityId() { return entityId; }
        public void setEntityId(String entityId) { this.entityId = entityId; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public String getPreviousHash() { return previousHash; }
        public void setPreviousHash(String previousHash) { this.previousHash = previousHash; }
        public String getHash() { return hash; }
        public void setHash(String hash) { this.hash = hash; }
    }

    private static class PHRImmutableAuditTrail implements ImmutableAuditTrail {
        private final String entityId;
        private final List<AuditEvent> events;
        private final String merkleRoot;
        private final boolean intact;

        public PHRImmutableAuditTrail(String entityId, List<AuditEvent> events, 
                                      String merkleRoot, boolean intact) {
            this.entityId = entityId;
            this.events = events;
            this.merkleRoot = merkleRoot;
            this.intact = intact;
        }

        @Override
        public String getEntityId() { return entityId; }
        @Override
        public List<AuditEvent> getEvents() { return events; }
        @Override
        public String getMerkleRoot() { return merkleRoot; }
        @Override
        public boolean isIntact() { return intact; }
    }

    private static class MerkleTreeService {
        private final List<String> leaves = new ArrayList<>();

        public void addLeaf(String hash) {
            leaves.add(hash);
        }

        public String getRoot() {
            if (leaves.isEmpty()) {
                return "empty";
            }
            return DigestUtils.sha256Hex(String.join("", leaves));
        }
    }
}
