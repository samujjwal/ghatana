package com.ghatana.kernel.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @doc.type class
 * @doc.purpose Default tamper-evident audit implementation with pluggable persistence
 * @doc.layer core
 * @doc.pattern Service
 */
public class DefaultAuditTrailService implements AuditTrailService {

    private static final String GENESIS_HASH = "0";

    private final ObjectMapper objectMapper;
    private final AuditTrailPersistence persistence;
    private final Map<String, List<StoredAuditEvent>> auditLogs = new ConcurrentHashMap<>();
    private final Map<String, StoredAuditEvent> auditEventsById = new ConcurrentHashMap<>();
    private final AtomicBoolean hydrated = new AtomicBoolean(false);

    public DefaultAuditTrailService(ObjectMapper objectMapper, AuditTrailPersistence persistence) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.persistence = Objects.requireNonNull(persistence, "persistence cannot be null");
    }

    @Override
    public void recordAuditEvent(AuditTrailEvent event) {
        hydrateIfNeeded();

        AuditTrailEvent canonicalEvent = canonicalize(event);
        if (auditEventsById.containsKey(canonicalEvent.getEventId())) {
            return;
        }

        String entityId = canonicalEvent.getEntityId();
        String previousHash = getLastHash(entityId);
        String eventHash = calculateHash(canonicalEvent, previousHash);

        StoredAuditEvent storedEvent = new StoredAuditEvent(canonicalEvent, eventHash);
        auditLogs.computeIfAbsent(entityId, ignored -> new CopyOnWriteArrayList<>()).add(storedEvent);
        auditEventsById.put(canonicalEvent.getEventId(), storedEvent);
        persistence.persist(storedEvent);
    }

    @Override
    public List<AuditTrailEvent> queryAuditEvents(AuditQuery query) {
        hydrateIfNeeded();

        return auditLogs.values().stream()
            .flatMap(List::stream)
            .map(StoredAuditEvent::event)
            .filter(event -> matches(query, event))
            .sorted(Comparator.comparingLong(AuditTrailEvent::getTimestamp))
            .limit(query.getLimit())
            .toList();
    }

    @Override
    public ImmutableAuditTrail getImmutableTrail(String entityId) {
        hydrateIfNeeded();

        List<StoredAuditEvent> entries = auditLogs.getOrDefault(entityId, List.of());
        List<AuditTrailEvent> events = entries.stream().map(StoredAuditEvent::event).toList();
        return new DefaultImmutableAuditTrail(entityId, events, calculateMerkleRoot(entries), verify(entityId).isValid());
    }

    @Override
    public VerificationResult verifyTrailIntegrity(String entityId) {
        hydrateIfNeeded();
        return verify(entityId);
    }

    protected String calculateMerkleRoot(List<StoredAuditEvent> entries) {
        if (entries.isEmpty()) {
            return "empty";
        }

        List<String> layer = entries.stream().map(StoredAuditEvent::hash).toList();
        while (layer.size() > 1) {
            List<String> nextLayer = new ArrayList<>();
            for (int i = 0; i < layer.size(); i += 2) {
                String left = layer.get(i);
                String right = (i + 1) < layer.size() ? layer.get(i + 1) : left;
                nextLayer.add(DigestUtils.sha256Hex(left + right));
            }
            layer = nextLayer;
        }
        return layer.getFirst();
    }

    protected String calculateHash(AuditTrailEvent event, String previousHash) {
        try {
            return DigestUtils.sha256Hex(objectMapper.writeValueAsString(Map.of(
                "eventId", event.getEventId(),
                "eventType", event.getEventType(),
                "entityId", event.getEntityId(),
                "userId", event.getUserId(),
                "tenantId", event.getTenantId(),
                "action", event.getAction(),
                "data", normalizedData(event.getData()),
                "timestamp", event.getTimestamp(),
                "previousHash", previousHash
            )));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to hash audit event " + event.getEventId(), exception);
        }
    }

    private VerificationResult verify(String entityId) {
        List<StoredAuditEvent> entries = auditLogs.getOrDefault(entityId, List.of());
        List<String> violations = new ArrayList<>();
        String expectedPreviousHash = GENESIS_HASH;

        for (StoredAuditEvent entry : entries) {
            String actualPreviousHash = Objects.requireNonNullElse(entry.event().getPreviousHash(), GENESIS_HASH);
            if (!expectedPreviousHash.equals(actualPreviousHash)) {
                violations.add("Hash chain break at event " + entry.event().getEventId());
            }

            String recalculatedHash = calculateHash(entry.event(), actualPreviousHash);
            if (!recalculatedHash.equals(entry.hash())) {
                violations.add("Tampered event detected: " + entry.event().getEventId());
            }

            expectedPreviousHash = entry.hash();
        }

        return new VerificationResult(
            violations.isEmpty(),
            violations.isEmpty() ? "Audit trail intact" : "Integrity violations detected",
            violations
        );
    }

    private void hydrateIfNeeded() {
        if (!hydrated.compareAndSet(false, true)) {
            return;
        }

        persistence.loadAll().stream()
            .sorted(Comparator.comparingLong(entry -> entry.event().getTimestamp()))
            .forEach(entry -> {
                auditLogs.computeIfAbsent(entry.event().getEntityId(), ignored -> new CopyOnWriteArrayList<>()).add(entry);
                auditEventsById.putIfAbsent(entry.event().getEventId(), entry);
            });
    }

    private AuditTrailEvent canonicalize(AuditTrailEvent event) {
        String entityId = Objects.requireNonNull(event.getEntityId(), "event.entityId cannot be null");
        String eventId = event.getEventId() != null ? event.getEventId() : java.util.UUID.randomUUID().toString();
        Map<String, Object> data = normalizedData(event.getData());
        String previousHash = getLastHash(entityId);

        return AuditTrailEvent.builder()
            .eventId(eventId)
            .eventType(Objects.requireNonNullElse(event.getEventType(), "unknown"))
            .entityId(entityId)
            .userId(event.getUserId())
            .tenantId(event.getTenantId())
            .action(Objects.requireNonNullElse(event.getAction(), "unknown"))
            .data(data)
            .timestamp(event.getTimestamp() > 0 ? event.getTimestamp() : System.currentTimeMillis())
            .previousHash(previousHash)
            .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizedData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> ordered = new LinkedHashMap<>();
        data.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> mapValue) {
                    ordered.put(entry.getKey(), normalizedData((Map<String, Object>) mapValue));
                } else {
                    ordered.put(entry.getKey(), value);
                }
            });
        return Map.copyOf(ordered);
    }

    private boolean matches(AuditQuery query, AuditTrailEvent event) {
        if (query.getEntityId() != null && !query.getEntityId().equals(event.getEntityId())) {
            return false;
        }
        if (query.getUserId() != null && !query.getUserId().equals(event.getUserId())) {
            return false;
        }
        if (query.getTenantId() != null && !query.getTenantId().equals(event.getTenantId())) {
            return false;
        }
        if (query.getEventType() != null && !query.getEventType().equals(event.getEventType())) {
            return false;
        }
        return event.getTimestamp() >= query.getStartTime() && event.getTimestamp() <= query.getEndTime();
    }

    private String getLastHash(String entityId) {
        List<StoredAuditEvent> entries = auditLogs.get(entityId);
        if (entries == null || entries.isEmpty()) {
            return GENESIS_HASH;
        }
        return entries.getLast().hash();
    }

    private record DefaultImmutableAuditTrail(
        String entityId,
        List<AuditTrailEvent> events,
        String merkleRoot,
        boolean intact
    ) implements ImmutableAuditTrail {
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
            return intact;
        }
    }

    public record StoredAuditEvent(AuditTrailEvent event, String hash) {
    }
}
