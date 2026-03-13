/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ghatana.aep.event.EventCloud;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.core.template.TemplateContext;
import com.ghatana.core.template.TemplateContextBuilder;
import com.ghatana.core.template.YamlTemplateEngine;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ActiveJ DI module that wires AEP to subscribe to all YAPPC event topics
 * declared in {@code products/yappc/config/agents/event-routing.yaml} and
 * routes each received event to the {@link AgentDispatcher}.
 *
 * <h2>Binding Strategy</h2>
 * <ol>
 *   <li>Loads {@code event-routing.yaml} from the classpath via
 *       {@link YamlTemplateEngine} (template variables resolved from env vars).</li>
 *   <li>Parses {@code event_routing[].topic} → {@code agent_id} mappings.</li>
 *   <li>Calls {@link EventCloud#subscribe(String, String, EventCloud.EventHandler)}
 *       for every declared topic, using the AEP consumer group
 *       {@code aep-yappc-integration}.</li>
 *   <li>On every received payload, builds an {@link AgentContext} carrying the
 *       {@code tenantId} extracted from event headers and delegates to
 *       {@link AgentDispatcher#dispatch(String, Object, AgentContext)}.</li>
 * </ol>
 *
 * <h2>Failure Model</h2>
 * An {@link IllegalStateException} is thrown at startup if the YAML file is
 * absent or contains no routing entries — ensuring fail-fast behaviour
 * consistent with the platform's design principles.
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module: subscribes AEP to all YAPPC event topics
 * @doc.layer product
 * @doc.pattern Module, Observer
 * @doc.gaa.lifecycle perceive
 * @see AgentDispatcher
 * @see EventCloud
 */
public class YappcIntegrationModule extends AbstractModule {

    private static final Logger log = LoggerFactory.getLogger(YappcIntegrationModule.class);

    /** Classpath location of YAPPC's event routing table. */
    private static final String ROUTING_YAML_PATH = "config/agents/event-routing.yaml";

    /** Consumer group identifier used for all YAPPC topic subscriptions. */
    private static final String CONSUMER_GROUP = "aep-yappc-integration";

    /**
     * Tenant ID used when no tenant header is present in the event payload.
     * <p>Override via {@code AEP_DEFAULT_TENANT_ID} environment variable.
     * Default: {@code "platform"} (platform-managed events).
     */
    private static final String DEFAULT_TENANT_ID =
            System.getenv().getOrDefault("AEP_DEFAULT_TENANT_ID", "platform");

    /**
     * Loads and wires all YAPPC event routing subscriptions.
     *
     * <p>Scans the classpath for {@value #ROUTING_YAML_PATH}, runs it through
     * the {@link YamlTemplateEngine} so that any {@code {{ param }}} placeholders
     * are resolved from environment variables, then subscribes to every declared
     * topic via the active {@link EventCloud} connector.
     *
     * @param eventCloud       active EventCloud connector (gRPC / HTTP / EventLog)
     * @param agentDispatcher  three-tier agent dispatcher for routing events to agents
     * @param templateEngine   YAML template engine for resolving env placeholders
     * @return an {@link YappcEventSubscriptionManager} handle for lifecycle management
     * @throws IllegalStateException if routing YAML is missing or unreadable
     */
    @Provides
    YappcEventSubscriptionManager yappcEventSubscriptions(
            EventCloud eventCloud,
            AgentDispatcher agentDispatcher,
            YamlTemplateEngine templateEngine) {

        List<EventRoute> routes = loadRoutes(templateEngine);
        if (routes.isEmpty()) {
            throw new IllegalStateException(
                    "YappcIntegrationModule: no event routes found in "
                    + ROUTING_YAML_PATH + " — cannot start without at least one topic subscription.");
        }

        log.info("YappcIntegrationModule: subscribing to {} YAPPC event topics", routes.size());

        for (EventRoute route : routes) {
            subscribeRoute(eventCloud, agentDispatcher, route);
        }

        log.info("YappcIntegrationModule: all {} subscriptions active", routes.size());
        return new YappcEventSubscriptionManager(routes.size());
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private List<EventRoute> loadRoutes(YamlTemplateEngine templateEngine) {
        URL resource = Thread.currentThread().getContextClassLoader()
                .getResource(ROUTING_YAML_PATH);
        if (resource == null) {
            throw new IllegalStateException(
                    "YappcIntegrationModule: classpath resource not found: " + ROUTING_YAML_PATH
                    + ". Ensure the yappc config directory is on the classpath.");
        }

        try (InputStream is = resource.openStream()) {
            String rawYaml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            TemplateContext ctx = TemplateContextBuilder.fromEnvironment();
            String resolvedYaml = templateEngine.render(rawYaml, ctx);
            return parseRoutes(resolvedYaml);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "YappcIntegrationModule: failed to read " + ROUTING_YAML_PATH, e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<EventRoute> parseRoutes(String yaml) {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> doc = mapper.readValue(yaml, new TypeReference<>() {});
            Object rawRoutes = doc.get("event_routing");
            if (!(rawRoutes instanceof List<?> list)) {
                return List.of();
            }
            return list.stream()
                    .filter(entry -> entry instanceof Map)
                    .map(entry -> (Map<String, Object>) entry)
                    .filter(m -> m.containsKey("topic") && m.containsKey("agent_id"))
                    .map(m -> new EventRoute(
                            String.valueOf(m.get("topic")),
                            String.valueOf(m.get("agent_id")),
                            m.containsKey("description") ? String.valueOf(m.get("description")) : ""))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "YappcIntegrationModule: failed to parse " + ROUTING_YAML_PATH, e);
        }
    }

    private void subscribeRoute(EventCloud eventCloud, AgentDispatcher agentDispatcher, EventRoute route) {
        try {
            eventCloud.subscribe(DEFAULT_TENANT_ID, route.topic(), (eventId, eventType, payload) -> {
                String tenantId = extractTenantId(payload);
                String turnId = UUID.randomUUID().toString();

                AgentContext ctx = AgentContext.builder()
                        .agentId(route.agentId())
                        .turnId(turnId)
                        .tenantId(tenantId)
                        .memoryStore(MemoryStore.noOp())
                        .build();

                log.debug("Routing event topic={} agentId={} turnId={}", route.topic(), route.agentId(), turnId);

                agentDispatcher.dispatch(route.agentId(), payload, ctx).whenException(ex ->
                        log.error("Dispatch failed for topic={} agentId={} turnId={}: {}",
                                route.topic(), route.agentId(), turnId, ex.getMessage(), ex)
                );
            });
            log.info("  subscribed: topic={} → agentId={}", route.topic(), route.agentId());
        } catch (Exception e) {
            log.error("YappcIntegrationModule: failed to subscribe topic={}: {}", route.topic(), e.getMessage(), e);
            throw new IllegalStateException(
                    "YappcIntegrationModule: subscription failed for topic=" + route.topic(), e);
        }
    }

    /**
     * Attempts to extract {@code tenantId} from the JSON event payload.
     * Falls back to {@link #DEFAULT_TENANT_ID} if absent or unparseable.
     */
    private String extractTenantId(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return DEFAULT_TENANT_ID;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<?, ?> map = mapper.readValue(payload, Map.class);
            Object tid = map.get("tenantId");
            if (tid == null) {
                tid = map.get("tenant_id");
            }
            if (tid instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (Exception ignored) {
            // payload is not JSON or missing the tenantId field — use default
        }
        return DEFAULT_TENANT_ID;
    }

    // -----------------------------------------------------------------------
    // Value types
    // -----------------------------------------------------------------------

    /**
     * Immutable route descriptor parsed from {@code event-routing.yaml}.
     *
     * @param topic       event topic (e.g. {@code "test.failed"})
     * @param agentId     target agent ID (e.g. {@code "debug-orchestrator"})
     * @param description human-readable route description
     */
    record EventRoute(String topic, String agentId, String description) {
        EventRoute {
            Objects.requireNonNull(topic, "topic");
            Objects.requireNonNull(agentId, "agentId");
        }
    }

    /**
     * Lifecycle handle for YAPPC event subscriptions.
     *
     * <p>Holds the count of active subscriptions for health reporting.
     *
     * @doc.type class
     * @doc.purpose Subscription lifecycle handle for YAPPC event routing
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public static final class YappcEventSubscriptionManager {

        private final int subscriptionCount;

        YappcEventSubscriptionManager(int subscriptionCount) {
            this.subscriptionCount = subscriptionCount;
        }

        /**
         * @return number of active topic subscriptions
         */
        public int getSubscriptionCount() {
            return subscriptionCount;
        }

        @Override
        public String toString() {
            return "YappcEventSubscriptionManager{subscriptions=" + subscriptionCount + "}";
        }
    }
}
