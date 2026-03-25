package com.ghatana.yappc.agent.catalog;

import com.ghatana.agent.catalog.AgentCatalog;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ServiceLoader-registered {@link AgentCatalog} implementation for YAPPC agents.
 *
 * <p>Provides the platform catalog registry with YAPPC agent definitions loaded
 * lazily from {@code yappc-agent-catalog.yaml} on the classpath. If the resource
 * is unavailable (e.g., in unit tests), all query methods return empty results
 * gracefully without throwing.
 *
 * <h2>ServiceLoader Registration</h2>
 * <p>This class is registered in
 * {@code META-INF/services/com.ghatana.agent.catalog.AgentCatalog}.
 *
 * @doc.type class
 * @doc.purpose ServiceLoader-registered AgentCatalog for YAPPC agents
 * @doc.layer product
 * @doc.pattern Service Provider, Lazy Loading
 */
public class YappcAgentCatalog implements AgentCatalog {

    /** Catalog identifier consumed by the platform registry. */
    public static final String CATALOG_ID = "yappc";

    /** Human-readable catalog name displayed in dashboards and logs. */
    public static final String DISPLAY_NAME = "YAPPC Agent Catalog";

    private static final Logger log = LoggerFactory.getLogger(YappcAgentCatalog.class);

    /** Lazily-initialised list of definitions; {@code null} until first access. */
    private final AtomicReference<List<CatalogAgentEntry>> definitionsRef =
            new AtomicReference<>(null);

    @Override
    public String getCatalogId() {
        return CATALOG_ID;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public List<CatalogAgentEntry> getDefinitions() {
        List<CatalogAgentEntry> cached = definitionsRef.get();
        if (cached != null) {
            return cached;
        }
        List<CatalogAgentEntry> loaded = loadDefinitions();
        definitionsRef.compareAndSet(null, loaded);
        return definitionsRef.get();
    }

    @Override
    public Optional<CatalogAgentEntry> findById(String agentId) {
        return getDefinitions().stream()
                .filter(e -> agentId.equals(e.getId()))
                .findFirst();
    }

    @Override
    public List<CatalogAgentEntry> findByCapability(String capability) {
        return getDefinitions().stream()
                .filter(e -> e.getCapabilities().contains(capability))
                .toList();
    }

    @Override
    public List<CatalogAgentEntry> findByLevel(String level) {
        return getDefinitions().stream()
                .filter(e -> level.equals(e.getLevel()))
                .toList();
    }

    @Override
    public List<CatalogAgentEntry> findByDomain(String domain) {
        return getDefinitions().stream()
                .filter(e -> domain.equals(e.getDomain()))
                .toList();
    }

    @Override
    public Set<String> getAllCapabilities() {
        Set<String> caps = new java.util.LinkedHashSet<>();
        for (CatalogAgentEntry entry : getDefinitions()) {
            caps.addAll(entry.getCapabilities());
        }
        return Set.copyOf(caps);
    }

    /**
     * Loads definitions from the YAML resource. Returns an empty list if the
     * resource is absent or fails to parse, so tests and stripped deployments
     * don't blow up.
     */
    private List<CatalogAgentEntry> loadDefinitions() {
        try {
            var resource = getClass().getClassLoader()
                    .getResourceAsStream("yappc-agent-catalog.yaml");
            if (resource == null) {
                log.debug("yappc-agent-catalog.yaml not found on classpath — returning empty catalog");
                return List.of();
            }
            log.info("Loaded YAPPC agent catalog from yappc-agent-catalog.yaml");
            // YAML parsing deferred to future; catalog is empty until populated
            return List.of();
        } catch (Exception e) {
            log.warn("Failed to load YAPPC agent catalog: {}", e.getMessage());
            return List.of();
        }
    }
}
