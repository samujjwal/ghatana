package com.ghatana.phr.healthcare.port.impl;

import com.ghatana.phr.healthcare.domain.DataClassification;
import com.ghatana.phr.healthcare.port.PhrAuditStore;
import com.ghatana.platform.audit.AuditEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory implementation of PHR audit store for testing and development.
 *
 * <p>This implementation stores audit events in memory. For production, use a
 * persistent implementation backed by a database with proper immutability guarantees.</p>
 *
 * @doc.type class
 * @doc.purpose In-memory PHR audit store implementation for testing
 * @doc.layer domain-pack
 * @doc.pattern PortAdapter
 */
public final class InMemoryPhrAuditStore implements PhrAuditStore {

    private final List<AuditEvent> accessEvents = new CopyOnWriteArrayList<>();
    private final List<AuditEvent> consentEvents = new CopyOnWriteArrayList<>();
    private final Map<String, List<AuditEvent>> patientAccessIndex = new ConcurrentHashMap<>();
    private final Map<String, List<AuditEvent>> actorAccessIndex = new ConcurrentHashMap<>();
    private final Map<String, List<AuditEvent>> classificationAccessIndex = new ConcurrentHashMap<>();

    @Override
    public void recordAccess(AuditEvent event) {
        accessEvents.add(event);
        
        // Index by patient
        if (event.resourceId() != null) {
            patientAccessIndex.computeIfAbsent(event.resourceId(), k -> new CopyOnWriteArrayList<>()).add(event);
        }
        
        // Index by actor
        if (event.principal() != null) {
            actorAccessIndex.computeIfAbsent(event.principal(), k -> new CopyOnWriteArrayList<>()).add(event);
        }
        
        // Index by classification (if available in details)
        String classification = event.details() != null ? (String) event.details().get("classification") : null;
        if (classification != null) {
            classificationAccessIndex.computeIfAbsent(classification, k -> new CopyOnWriteArrayList<>()).add(event);
        }
    }

    @Override
    public void recordConsentGrant(AuditEvent event) {
        consentEvents.add(event);
    }

    @Override
    public void recordConsentRevocation(AuditEvent event) {
        consentEvents.add(event);
    }

    @Override
    public List<AuditEvent> findAccessByPatient(String tenantId, UUID patientId) {
        String patientKey = patientId.toString();
        return patientAccessIndex.getOrDefault(patientKey, List.of()).stream()
            .filter(e -> e.tenantId().equals(tenantId))
            .collect(Collectors.toList());
    }

    @Override
    public List<AuditEvent> findAccessByActor(String tenantId, String actorId) {
        return actorAccessIndex.getOrDefault(actorId, List.of()).stream()
            .filter(e -> e.tenantId().equals(tenantId))
            .collect(Collectors.toList());
    }

    @Override
    public List<AuditEvent> findAccessByClassification(String tenantId, DataClassification classification) {
        String classificationKey = classification.name();
        return classificationAccessIndex.getOrDefault(classificationKey, List.of()).stream()
            .filter(e -> e.tenantId().equals(tenantId))
            .collect(Collectors.toList());
    }

    @Override
    public List<AuditEvent> findAccessByTimeRange(String tenantId, Instant startDate, Instant endDate) {
        return accessEvents.stream()
            .filter(e -> e.tenantId().equals(tenantId))
            .filter(e -> !e.timestamp().isBefore(startDate))
            .filter(e -> !e.timestamp().isAfter(endDate))
            .collect(Collectors.toList());
    }

    @Override
    public List<AuditEvent> findConsentByPatient(String tenantId, UUID patientId) {
        String patientKey = patientId.toString();
        return consentEvents.stream()
            .filter(e -> e.tenantId().equals(tenantId))
            .filter(e -> patientKey.equals(e.resourceId()))
            .collect(Collectors.toList());
    }

    @Override
    public long countAccessByPatient(String tenantId, UUID patientId) {
        return findAccessByPatient(tenantId, patientId).size();
    }

    /**
     * Clears all stored events (for testing purposes only).
     */
    public void clear() {
        accessEvents.clear();
        consentEvents.clear();
        patientAccessIndex.clear();
        actorAccessIndex.clear();
        classificationAccessIndex.clear();
    }
}
