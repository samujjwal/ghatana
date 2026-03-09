package com.ghatana.catalog.adapters.memory;

import com.ghatana.catalog.ports.EventTypeRepository;
import com.ghatana.platform.domain.domain.event.EventType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory adapter implementation of {@link EventTypeRepository} using concurrent maps.
 * 
 * <p>This adapter provides a thread-safe in-memory storage for EventType entities,
 * primarily intended for testing and development scenarios. Uses composite keys:
 * <ul>
 *   <li>fullId: tenant/namespace/name:version</li>
 *   <li>baseKey: tenant/namespace/name (for version lookups)</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Provides thread-safe in-memory persistence for EventType entities
 * @doc.layer product
 * @doc.pattern Repository
 * @since 2.0.0
 */
public class InMemoryEventTypeRepository implements EventTypeRepository {

    private final Map<String, EventType> byId = new ConcurrentHashMap<>();
    private final Map<String, Map<String, EventType>> byBaseKey = new ConcurrentHashMap<>();

    @Override
    public EventType save(EventType eventType) {
        String fullId = eventType.getId();
        String baseKey = baseKey(eventType.getTenantId(), eventType.getNamespace(), eventType.getName());
        String version = eventType.getVersion();

        byId.put(fullId, eventType);
        byBaseKey.computeIfAbsent(baseKey, k -> new ConcurrentHashMap<>()).put(version, eventType);
        return eventType;
    }

    @Override
    public Optional<EventType> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<EventType> findByNameAndVersion(String tenantId, String namespace, String name, String version) {
        String fullId = buildFullId(tenantId, namespace, name, version);
        return findById(fullId);
    }

    @Override
    public Optional<EventType> findLatest(String tenantId, String namespace, String name) {
        String baseKey = baseKey(tenantId, namespace, name);
        Map<String, EventType> versions = byBaseKey.get(baseKey);
        if (versions == null || versions.isEmpty()) {
            return Optional.empty();
        }
        return versions.values().stream()
                .max(Comparator.comparing(EventType::getVersion, InMemoryEventTypeRepository::compareSemVer));
    }

    @Override
    public List<EventType> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    @Override
    public long count() {
        return byId.size();
    }

    @Override
    public EventType update(EventType eventType) {
        String fullId = eventType.getId();
        if (!byId.containsKey(fullId)) {
            throw new IllegalArgumentException("EventType not found: " + fullId);
        }
        String baseKey = baseKey(eventType.getTenantId(), eventType.getNamespace(), eventType.getName());
        String version = eventType.getVersion();

        byId.put(fullId, eventType);
        byBaseKey.computeIfAbsent(baseKey, k -> new ConcurrentHashMap<>()).put(version, eventType);
        return eventType;
    }

    @Override
    public boolean delete(String id) {
        EventType removed = byId.remove(id);
        if (removed != null) {
            String baseKey = baseKey(removed.getTenantId(), removed.getNamespace(), removed.getName());
            Map<String, EventType> versions = byBaseKey.get(baseKey);
            if (versions != null) {
                versions.remove(removed.getVersion());
                if (versions.isEmpty()) {
                    byBaseKey.remove(baseKey);
                }
            }
            return true;
        }
        return false;
    }

    private static String baseKey(String tenantId, String namespace, String name) {
        return tenantId + "/" + namespace + "/" + name;
    }

    private static String buildFullId(String tenantId, String namespace, String name, String version) {
        return baseKey(tenantId, namespace, name) + ":" + version;
    }

    /**
     * Very small semantic version comparator using dot-separated integers (pre-release ignored).
     */
    private static int compareSemVer(String a, String b) {
        String aCore = a.split("-", 2)[0];
        String bCore = b.split("-", 2)[0];
        String[] as = aCore.split("\\.");
        String[] bs = bCore.split("\\.");
        int len = Math.max(as.length, bs.length);
        for (int i = 0; i < len; i++) {
            int ai = i < as.length ? parseIntSafe(as[i]) : 0;
            int bi = i < bs.length ? parseIntSafe(bs[i]) : 0;
            if (ai != bi) {
                return Integer.compare(ai, bi);
            }
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
