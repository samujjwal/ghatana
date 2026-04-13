package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP handler for the Data-Cloud agent catalog runtime API (B3).
 *
 * <p>Loads YAML agent definitions from {@code agent-catalog/definitions/**} on the classpath
 * and exposes them as a JSON REST API. No new abstraction — uses Jackson {@link YAMLFactory}
 * directly since the repo already has it.
 *
 * <p>Routes wired in {@code DataCloudHttpServer}:
 * <ul>
 *   <li>{@code GET /api/v1/agents/catalog}        — list all agents</li>
 *   <li>{@code GET /api/v1/agents/catalog/:id}    — get single agent by ID</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Serves agent catalog YAML definitions as a JSON REST API (B3)
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class AgentCatalogHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentCatalogHandler.class);
    private static final String CATALOG_CLASSPATH_ROOT = "agent-catalog/definitions";

    private final HttpHandlerSupport http;
    private final MetricsCollector metrics;

    // Loaded eagerly on first access; immutable after load.
    private volatile List<Map<String, Object>> catalogCache;

    /**
     * @param http    shared HTTP support (JSON responses, tenant context)
     * @param metrics observability metrics
     */
    public AgentCatalogHandler(HttpHandlerSupport http, MetricsCollector metrics) {
        this.http = Objects.requireNonNull(http, "http");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    // ─── Routes ───────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/agents/catalog
     * Returns the full list of agent definitions.
     */
    public Promise<HttpResponse> handleListCatalog(HttpRequest request) {
        return Promise.ofBlocking(
                http.blockingExecutor(),
                this::loadCatalog
        ).then(catalog -> {
            String tenantId = http.resolveTenantId(request);
            metrics.incrementCounter("agent.catalog.list", "tenant", tenantId);
            Map<String, Object> response = new HashMap<>();
            response.put("agents", catalog);
            response.put("total", catalog.size());
            return http.jsonResponse(200, response);
        }).mapException(e -> {
            log.error("Failed to load agent catalog", e);
            return e;
        });
    }

    /**
     * GET /api/v1/agents/catalog/:id
     * Returns a single agent definition by ID.
     */
    public Promise<HttpResponse> handleGetAgent(HttpRequest request) {
        String rawId = request.getPathParameter("id");
        String agentId = URLDecoder.decode(rawId, StandardCharsets.UTF_8);

        return Promise.ofBlocking(
                http.blockingExecutor(),
                this::loadCatalog
        ).then(catalog -> {
            String tenantId = http.resolveTenantId(request);
            metrics.incrementCounter("agent.catalog.get", "tenant", tenantId, "agentId", agentId);

            return catalog.stream()
                    .filter(a -> agentId.equals(a.get("id")))
                    .findFirst()
                    .<Promise<HttpResponse>>map(agent -> http.jsonResponse(200, agent))
                    .orElseGet(() -> http.errorResponse(404, "Agent not found: " + agentId));
        });
    }

    // ─── YAML loading ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadCatalog() throws Exception {
        if (catalogCache != null) {
            return catalogCache;
        }

        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        List<Map<String, Object>> agents = new ArrayList<>();

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = cl.getResources(CATALOG_CLASSPATH_ROOT);
        while (resources.hasMoreElements()) {
            URL dirUrl = resources.nextElement();
            // Walk all YAML files under the directory URL
            loadYamlFrom(dirUrl, yamlMapper, agents);
        }

        if (agents.isEmpty()) {
            // Fallback: try loading from file system relative to working directory
            log.warn("Agent catalog classpath resource not found; catalog will be empty");
        } else {
            log.info("Agent catalog loaded: {} agents", agents.size());
        }

        catalogCache = List.copyOf(agents);
        return catalogCache;
    }

    @SuppressWarnings("unchecked")
    private void loadYamlFrom(URL dirUrl, ObjectMapper yamlMapper, List<Map<String, Object>> out) {
        try {
            if ("file".equals(dirUrl.getProtocol())) {
                java.io.File dir = new java.io.File(dirUrl.toURI());
                loadYamlFilesRecursively(dir, yamlMapper, out);
            } else if ("jar".equals(dirUrl.getProtocol())) {
                // Walk JAR entries matching the prefix
                String jarPath = dirUrl.getPath();
                String prefix = jarPath.substring(jarPath.indexOf('!') + 2); // strip "!/"
                java.util.jar.JarFile jar = new java.util.jar.JarFile(
                        jarPath.substring(5, jarPath.indexOf('!')));
                try (jar) {
                    jar.stream()
                            .filter(e -> e.getName().startsWith(prefix) && e.getName().endsWith(".yaml"))
                            .forEach(entry -> {
                                try (InputStream in = jar.getInputStream(entry)) {
                                    Map<String, Object> agent = yamlMapper.readValue(in, Map.class);
                                    if (agent.containsKey("id")) {
                                        out.add(agent);
                                    }
                                } catch (Exception ex) {
                                    log.warn("Failed to parse agent YAML from JAR entry {}: {}", entry.getName(), ex.getMessage());
                                }
                            });
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load agent catalog from {}: {}", dirUrl, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadYamlFilesRecursively(java.io.File dir, ObjectMapper yamlMapper, List<Map<String, Object>> out) {
        if (!dir.isDirectory()) {
            return;
        }
        java.io.File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (java.io.File f : children) {
            if (f.isDirectory()) {
                loadYamlFilesRecursively(f, yamlMapper, out);
            } else if (f.getName().endsWith(".yaml") || f.getName().endsWith(".yml")) {
                try (InputStream in = new java.io.FileInputStream(f)) {
                    Map<String, Object> agent = yamlMapper.readValue(in, Map.class);
                    if (agent.containsKey("id")) {
                        out.add(agent);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse agent YAML {}: {}", f.getAbsolutePath(), e.getMessage());
                }
            }
        }
    }
}
