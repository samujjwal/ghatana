package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.brain.DataCloudBrain;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.entity.storage.StorageConnector;
import com.ghatana.datacloud.infrastructure.storage.OpenSearchConnector;
import com.ghatana.datacloud.launcher.http.ApiInputValidator;
import com.ghatana.datacloud.launcher.learning.DataCloudLearningBridge;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.datacloud.workspace.GlobalWorkspace;
import com.ghatana.datacloud.workspace.SpotlightItem;
import com.ghatana.platform.types.identity.Offset;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.bytebuf.ByteBuf;
import io.activej.csp.supplier.ChannelSupplier;
import io.activej.csp.supplier.ChannelSuppliers;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Handles all SSE (Server-Sent Events) and WebSocket streaming endpoints for Data-Cloud.
 *
 * <p>Extracted from {@code DataCloudHttpServer} (DCHTTP-1, 2026-01) to eliminate
 * the god-class pattern. Covers:
 * <ul>
 *   <li>Entity CDC stream ({@code GET /api/v1/entities/:collection/stream})</li>
 *   <li>General event-log SSE ({@code GET /events/stream})</li>
 *   <li>Brain workspace SSE ({@code GET /api/v1/brain/workspace/stream})</li>
 *   <li>Learning status SSE ({@code GET /api/v1/learning/stream})</li>
 *   <li>Streaming query SSE ({@code GET /api/v1/entities/:collection/query/stream})</li>
 *   <li>WebSocket real-time push ({@code /ws})</li>
 * </ul>
 *
 * <p>This class owns the shared subscription and connection registries ({@code sseSubscriptions},
 * {@code wsConnections}). Call {@link #shutdown()} during server teardown to clean up all
 * active streams and connections.
 *
 * @doc.type class
 * @doc.purpose SSE and WebSocket streaming handler (DCHTTP-1 extraction)
 * @doc.layer product
 * @doc.pattern Handler, Publish-Subscribe
 */
public class SseStreamingHandler {

    private static final Logger log = LoggerFactory.getLogger(SseStreamingHandler.class);

    /** SSE queue capacity — 512 frames before back-pressure kicks in. */
    private static final int SSE_QUEUE_CAPACITY = 512;
    /** Heartbeat interval: block this long waiting for the next SSE frame before sending keep-alive. */
    private static final long SSE_HEARTBEAT_TIMEOUT_SEC = 30L;

    private final DataCloudClient client;
    private final DataCloudBrain brain;
    private final DataCloudLearningBridge learningBridge;
    private final ObjectMapper objectMapper;
    private final HttpHandlerSupport http;

    /** Virtual-thread executor for blocking tail/poll operations. */
    private final Executor blockingExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /** Active SSE subscriptions — cancelled on {@link #shutdown()}. */
    private final CopyOnWriteArrayList<EventLogStore.Subscription> sseSubscriptions =
            new CopyOnWriteArrayList<>();

    /** Active WebSocket connections — closed on {@link #shutdown()}. */
    private final CopyOnWriteArrayList<IWebSocket> wsConnections = new CopyOnWriteArrayList<>();

    /** Optional OpenSearch connector for streaming query SSE. */
    private OpenSearchConnector openSearchConnector;

    /**
     * Creates the SSE/WebSocket streaming handler.
     *
     * @param client        Data-Cloud client (event log access)
     * @param brain         optional brain facade; may be {@code null} to disable brain streams
     * @param learningBridge optional learning bridge; may be {@code null} to disable learning streams
     * @param objectMapper  Jackson serializer
     * @param http          shared HTTP helpers (CORS, error responses, tenant resolution)
     */
    public SseStreamingHandler(DataCloudClient client,
                               DataCloudBrain brain,
                               DataCloudLearningBridge learningBridge,
                               ObjectMapper objectMapper,
                               HttpHandlerSupport http) {
        this.client = client;
        this.brain = brain;
        this.learningBridge = learningBridge;
        this.objectMapper = objectMapper;
        this.http = http;
    }

    /**
     * Configures the optional OpenSearch connector for {@code /entities/:collection/query/stream}.
     *
     * @param connector optional OpenSearch connector; {@code null} returns HTTP 501
     * @return this (fluent)
     */
    public SseStreamingHandler withOpenSearchConnector(OpenSearchConnector connector) {
        this.openSearchConnector = connector;
        return this;
    }

    /**
     * Provides the WebSocket broadcast function for use by {@link EntityCrudHandler}.
     * When an entity is saved or deleted the CRUD handler calls this to notify WS clients.
     *
     * @return a {@code BiConsumer<eventType, data>} that broadcasts to all connected WS clients
     */
    public BiConsumer<String, Map<String, Object>> broadcastFunction() {
        return this::broadcastWsEvent;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entity CDC Stream — GET /api/v1/entities/:collection/stream
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Entity CDC (Change Data Capture) Server-Sent Events stream.
     *
     * @param request the incoming HTTP request
     * @return a streaming {@code text/event-stream} response
     *
     * @doc.type method
     * @doc.purpose Entity CDC real-time push stream filtered by collection (DC-3)
     * @doc.layer product
     * @doc.pattern SSE Adapter, CDC, Event Tailing
     */
    public Promise<HttpResponse> handleEntityCdcStream(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        String collection = request.getPathParameter("collection");

        if (collection == null || collection.isBlank()) {
            return Promise.of(http.errorResponse(400, "collection path parameter is required"));
        }

        long fromOffsetVal = HttpHandlerSupport.parseLongParam(request.getQueryParameter("fromOffset"), 0L);

        Set<String> entityEventTypes = Set.of(
            "entity.saved", "entity.deleted",
            "entity.batch-saved", "entity.batch-deleted"
        );

        LinkedBlockingQueue<Optional<byte[]>> queue = new LinkedBlockingQueue<>(SSE_QUEUE_CAPACITY);
        queue.offer(Optional.of(buildSseFrame("connected", Map.of(
                "service", "data-cloud",
                "tenantId", tenantId,
                "collection", collection,
                "fromOffset", String.valueOf(fromOffsetVal),
                "timestamp", Instant.now().toString()
        ))));

        EventLogStore eventLogStore = client.eventLogStore();
        return eventLogStore.tail(TenantContext.of(tenantId), Offset.of(fromOffsetVal), entry -> {
            if (!entityEventTypes.contains(entry.eventType())) return;
            try {
                byte[] payloadBytes = new byte[entry.payload().remaining()];
                entry.payload().duplicate().get(payloadBytes);
                String payloadStr = new String(payloadBytes, StandardCharsets.UTF_8);
                @SuppressWarnings("unchecked")
                Map<String, Object> payloadMap = objectMapper.readValue(payloadStr, Map.class);

                if (!collection.equals(payloadMap.get("collection"))) return;

                Map<String, Object> frame = new LinkedHashMap<>();
                frame.put("collection", collection);
                frame.put("operation", payloadMap.getOrDefault("operation", "unknown"));
                frame.put("eventType", entry.eventType());
                frame.put("tenantId", tenantId);
                frame.put("timestamp", entry.timestamp().toString());
                if (payloadMap.containsKey("id"))      frame.put("id",      payloadMap.get("id"));
                if (payloadMap.containsKey("ids"))     frame.put("ids",     payloadMap.get("ids"));
                if (payloadMap.containsKey("version")) frame.put("version", payloadMap.get("version"));
                if (payloadMap.containsKey("count"))   frame.put("count",   payloadMap.get("count"));

                byte[] sseFrame = buildSseFrame("entity-change", frame);
                if (!queue.offer(Optional.of(sseFrame), 100L, TimeUnit.MILLISECONDS)) {
                    log.warn("[CDC] queue full for tenant={} collection={}, dropping change event", tenantId, collection);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                log.warn("[CDC] frame build error for tenant={} collection={}: {}", tenantId, collection, ex.getMessage());
            }
        }).map(subscription -> {
            sseSubscriptions.add(subscription);
            ChannelSupplier<ByteBuf> bodyStream = ChannelSuppliers.ofAsyncSupplier(() -> {
                if (subscription.isCancelled()) return Promise.of(null);
                try {
                    if (subscription.isCancelled()) return Promise.of(null);
                    Optional<byte[]> item = queue.poll(SSE_HEARTBEAT_TIMEOUT_SEC, TimeUnit.SECONDS);
                    if (item == null) {
                        return Promise.of(ByteBuf.wrapForReading(buildSseFrame("heartbeat",
                                Map.of("ts", Instant.now().toString()))));
                    }
                    return Promise.of(item.isPresent() ? ByteBuf.wrapForReading(item.get()) : null);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return Promise.of(null);
                }
            });
            log.info("[CDC] stream opened for tenant={} collection={} fromOffset={}", tenantId, collection, fromOffsetVal);
            return buildSseResponse(bodyStream);
        }).mapException(e -> {
            log.error("[CDC] failed to open tail subscription for tenant={} collection={}: {}", tenantId, collection, e.getMessage(), e);
            return new HttpException("CDC subscription failed: " + e.getMessage(), e);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // General Event Log SSE — GET /events/stream
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * General event-log Server-Sent Events stream.
     *
     * @param request the incoming HTTP request
     * @return a streaming {@code text/event-stream} response
     *
     * @doc.type method
     * @doc.purpose Real-time SSE push for Data-Cloud event-log consumers (DC-4)
     * @doc.layer product
     * @doc.pattern SSE Adapter, Event Tailing
     */
    public Promise<HttpResponse> handleSseStream(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        long fromOffsetVal = HttpHandlerSupport.parseLongParam(request.getQueryParameter("fromOffset"), 0L);
        List<String> eventTypesFilter = parseEventTypeFilter(request.getQueryParameter("eventType"));

        LinkedBlockingQueue<Optional<byte[]>> queue = new LinkedBlockingQueue<>(SSE_QUEUE_CAPACITY);
        queue.offer(Optional.of(buildSseFrame("connected", Map.of(
                "service", "data-cloud",
                "tenantId", tenantId,
                "fromOffset", String.valueOf(fromOffsetVal),
                "timestamp", Instant.now().toString()
        ))));

        TenantContext tenant = TenantContext.of(tenantId);
        EventLogStore eventLogStore = client.eventLogStore();
        return eventLogStore.tail(tenant, Offset.of(fromOffsetVal), entry -> {
            if (!eventTypesFilter.isEmpty() && !eventTypesFilter.contains(entry.eventType())) return;
            try {
                byte[] frame = buildEventSseFrame(entry);
                if (!queue.offer(Optional.of(frame), 100L, TimeUnit.MILLISECONDS)) {
                    log.warn("[SSE] queue full for tenant={}, dropping event type={}", tenantId, entry.eventType());
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                log.warn("[SSE] serialization error for tenant={}: {}", tenantId, ex.getMessage());
            }
        }).map(subscription -> {
            sseSubscriptions.add(subscription);
            ChannelSupplier<ByteBuf> bodyStream = ChannelSuppliers.ofAsyncSupplier(() -> {
                if (subscription.isCancelled()) return Promise.of(null);
                try {
                    if (subscription.isCancelled()) return Promise.of(null);
                    Optional<byte[]> item = queue.poll(SSE_HEARTBEAT_TIMEOUT_SEC, TimeUnit.SECONDS);
                    if (item == null) {
                        return Promise.of(ByteBuf.wrapForReading(buildSseFrame("heartbeat",
                                Map.of("ts", Instant.now().toString()))));
                    }
                    return Promise.of(item.isPresent() ? ByteBuf.wrapForReading(item.get()) : null);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return Promise.of(null);
                }
            });
            log.info("[SSE] stream opened for tenant={} fromOffset={} filter={}", tenantId, fromOffsetVal,
                    eventTypesFilter.isEmpty() ? "*" : eventTypesFilter);
            return buildSseResponse(bodyStream);
        }).mapException(e -> {
            log.error("[SSE] failed to open tail subscription for tenant={}: {}", tenantId, e.getMessage(), e);
            return new HttpException("SSE subscription failed: " + e.getMessage(), e);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Brain Workspace SSE — GET /api/v1/brain/workspace/stream
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * SSE stream of spotlight updates from the global brain workspace.
     *
     * @param request the incoming HTTP request
     * @return SSE response, or 503 if brain unavailable
     *
     * @doc.type method
     * @doc.purpose Brain workspace SSE stream (DC-6)
     * @doc.layer product
     * @doc.pattern Publish-Subscribe, SSE
     */
    public Promise<HttpResponse> handleBrainWorkspaceStream(HttpRequest request) {
        if (brain == null) {
            return Promise.of(http.errorResponse(503, "Brain not available in this deployment"));
        }
        Optional<GlobalWorkspace> wsOpt = brain.getWorkspace();
        if (wsOpt.isEmpty()) {
            return Promise.of(http.errorResponse(503, "Workspace stream not available for this brain implementation"));
        }
        GlobalWorkspace workspace = wsOpt.get();
        String tenantId = http.resolveTenantId(request);

        LinkedBlockingQueue<Optional<byte[]>> queue = new LinkedBlockingQueue<>(SSE_QUEUE_CAPACITY);
        workspace.getByTenant(tenantId).forEach(item -> queue.offer(Optional.of(buildWorkspaceSseFrame(item))));
        queue.offer(Optional.of(buildSseFrame("connected", Map.of(
            "service",   "data-cloud-brain",
            "tenantId",  tenantId,
            "timestamp", Instant.now().toString()
        ))));

        GlobalWorkspace.Subscription subscription = workspace.subscribe(item -> {
            if (!"default".equals(tenantId) && !tenantId.equals(item.getTenantId())) return;
            try {
                if (!queue.offer(Optional.of(buildWorkspaceSseFrame(item)), 100L, TimeUnit.MILLISECONDS)) {
                    log.warn("[SSE-WS] queue full for tenant={}, dropping item", tenantId);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        });

        ChannelSupplier<ByteBuf> bodyStream = ChannelSuppliers.ofAsyncSupplier(() -> {
            if (!subscription.isActive()) return Promise.of(null);
            try {
                if (!subscription.isActive()) return Promise.of(null);
                Optional<byte[]> item = queue.poll(SSE_HEARTBEAT_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (item == null) {
                    return Promise.of(ByteBuf.wrapForReading(buildSseFrame("heartbeat",
                        Map.of("ts", Instant.now().toString()))));
                }
                return Promise.of(item.isPresent() ? ByteBuf.wrapForReading(item.get()) : null);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return Promise.of(null);
            }
        });

        log.info("[SSE-WS] brain workspace stream opened for tenant={}", tenantId);
        return Promise.of(buildSseResponse(bodyStream));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Learning Status SSE — GET /api/v1/learning/stream
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * SSE stream of learning-bridge status updates.
     *
     * @param request the incoming HTTP request
     * @return SSE response, or 503 if learning bridge unavailable
     *
     * @doc.type method
     * @doc.purpose Learning status SSE stream (DC-9)
     * @doc.layer product
     * @doc.pattern Publish-Subscribe, SSE
     */
    public Promise<HttpResponse> handleLearningStream(HttpRequest request) {
        if (learningBridge == null) {
            return Promise.of(http.errorResponse(503, "Learning bridge not available in this deployment"));
        }
        String tenantId = http.resolveTenantId(request);
        LinkedBlockingQueue<Optional<byte[]>> queue = new LinkedBlockingQueue<>(SSE_QUEUE_CAPACITY);
        queue.offer(Optional.of(buildSseFrame("connected", Map.of(
            "service",   "data-cloud-learning",
            "tenantId",  tenantId,
            "timestamp", Instant.now().toString()
        ))));

        Thread.ofVirtual().name("learning-sse-" + tenantId).start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(java.time.Duration.ofSeconds(30));
                    Map<String, Object> status = learningBridge.getStatus();
                    Map<String, Object> payload = new LinkedHashMap<>(status);
                    payload.put("tenantId",  tenantId);
                    payload.put("timestamp", Instant.now().toString());
                    byte[] frame = buildSseFrame("learning-status", Map.copyOf(payload));
                    if (!queue.offer(Optional.of(frame), 100L, TimeUnit.MILLISECONDS)) {
                        log.warn("[SSE-LEARN] queue full for tenant={}", tenantId);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("[SSE-LEARN] error pushing status for tenant={}: {}", tenantId, e.getMessage());
                }
            }
            queue.offer(Optional.empty());
        });

        ChannelSupplier<ByteBuf> bodyStream = ChannelSuppliers.ofAsyncSupplier(() -> {
            try {
                Optional<byte[]> item = queue.poll(SSE_HEARTBEAT_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (item == null) {
                    return Promise.of(ByteBuf.wrapForReading(buildSseFrame("heartbeat",
                        Map.of("ts", Instant.now().toString()))));
                }
                return Promise.of(item.isPresent() ? ByteBuf.wrapForReading(item.get()) : null);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return Promise.of(null);
            }
        });

        log.info("[SSE-LEARN] learning stream opened for tenant={}", tenantId);
        return Promise.of(buildSseResponse(bodyStream));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Streaming Query SSE — GET /api/v1/entities/:collection/query/stream
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Streaming query SSE: snapshot + optional live-tail of entity mutations.
     *
     * @param request the incoming HTTP request
     * @return SSE response, or 501 if OpenSearch is not configured
     *
     * @doc.type method
     * @doc.purpose Snapshot-and-tail streaming query over entity collection (DC-9)
     * @doc.layer product
     * @doc.pattern SSE Adapter, CQRS
     */
    public Promise<HttpResponse> handleStreamingQuerySse(HttpRequest request) {
        if (openSearchConnector == null) {
            return Promise.of(http.errorResponse(501, "Streaming query is not enabled; configure an OpenSearchConnector"));
        }

        String collection = request.getPathParameter("collection");
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        String q = request.getQueryParameter("q");
        Optional<String> qErr = ApiInputValidator.validateSearchQuery(q);
        if (qErr.isPresent()) return Promise.of(http.errorResponse(400, qErr.get()));

        String tenantId = http.resolveTenantId(request);
        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));

        boolean follow     = "true".equalsIgnoreCase(request.getQueryParameter("follow"));
        long fromOffsetVal = HttpHandlerSupport.parseLongParam(request.getQueryParameter("fromOffset"), 0L);
        ApiInputValidator.LimitResult limitResult = ApiInputValidator.validateLimit(request.getQueryParameter("limit"), 100);
        if (!limitResult.isValid()) return Promise.of(http.errorResponse(400, limitResult.getError().orElseThrow()));
        int snapshotLimit  = limitResult.getValue();

        LinkedBlockingQueue<Optional<byte[]>> queue = new LinkedBlockingQueue<>(SSE_QUEUE_CAPACITY);
        queue.offer(Optional.of(buildSseFrame("connected", Map.of(
            "service",    "data-cloud",
            "tenantId",   tenantId,
            "collection", collection,
            "q",          q,
            "follow",     String.valueOf(follow),
            "fromOffset", String.valueOf(fromOffsetVal),
            "timestamp",  Instant.now().toString()
        ))));

        QuerySpec snapshotSpec = QuerySpec.builder()
            .filter(q).limit(snapshotLimit).offset(0).build();

        Promise<StorageConnector.QueryResult> snapshotPromise =
            openSearchConnector.query((java.util.UUID) null, tenantId, snapshotSpec)
                .mapException(ex -> {
                    log.warn("[query-stream] snapshot failed tenant={} collection={} q='{}': {}",
                        tenantId, collection, q, ex.getMessage(), ex);
                    return null;
                })
                .then((result, ex) -> Promise.of(ex == null ? result : StorageConnector.QueryResult.empty()));

        Set<String> entityEventTypes = Set.of(
            "entity.saved", "entity.deleted",
            "entity.batch-saved", "entity.batch-deleted");

        if (follow) {
            return client.eventLogStore()
                .tail(TenantContext.of(tenantId), Offset.of(fromOffsetVal), entry -> {
                    if (!entityEventTypes.contains(entry.eventType())) return;
                    try {
                        byte[] payloadBytes = new byte[entry.payload().remaining()];
                        entry.payload().duplicate().get(payloadBytes);
                        String payloadStr = new String(payloadBytes, StandardCharsets.UTF_8);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payloadMap = objectMapper.readValue(payloadStr, Map.class);
                        if (!collection.equals(payloadMap.get("collection"))) return;

                        Map<String, Object> frame = new LinkedHashMap<>();
                        frame.put("collection",  collection);
                        frame.put("operation",   payloadMap.getOrDefault("operation", "unknown"));
                        frame.put("eventType",   entry.eventType());
                        frame.put("tenantId",    tenantId);
                        frame.put("timestamp",   entry.timestamp().toString());
                        if (payloadMap.containsKey("id"))      frame.put("id",      payloadMap.get("id"));
                        if (payloadMap.containsKey("ids"))     frame.put("ids",     payloadMap.get("ids"));
                        if (payloadMap.containsKey("version")) frame.put("version", payloadMap.get("version"));
                        if (payloadMap.containsKey("count"))   frame.put("count",   payloadMap.get("count"));

                        byte[] sseFrame = buildSseFrame("query-update", frame);
                        if (!queue.offer(Optional.of(sseFrame), 100L, TimeUnit.MILLISECONDS)) {
                            log.warn("[query-stream] queue full for tenant={} collection={}, dropping update", tenantId, collection);
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    } catch (Exception ex) {
                        log.warn("[query-stream] frame error tenant={} collection={}: {}", tenantId, collection, ex.getMessage());
                    }
                })
                .then(subscription -> snapshotPromise.map(qr -> {
                    sseSubscriptions.add(subscription);
                    enqueueSnapshot(queue, qr, collection, tenantId, q);
                    return buildSseChannelResponse(queue, subscription, tenantId, collection);
                }))
                .mapException(e -> {
                    log.error("[query-stream] tail failed tenant={} collection={}: {}", tenantId, collection, e.getMessage(), e);
                    return new HttpException("Streaming query failed: " + e.getMessage(), e);
                });
        } else {
            return snapshotPromise.map(qr -> {
                enqueueSnapshot(queue, qr, collection, tenantId, q);
                queue.offer(Optional.empty());
                return buildSseChannelResponse(queue, null, tenantId, collection);
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WebSocket — /ws
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles a new WebSocket connection on {@code /ws}.
     *
     * @param ws the newly established WebSocket connection
     *
     * @doc.type method
     * @doc.purpose Register WebSocket client and begin push-only event stream
     * @doc.layer product
     * @doc.pattern EventBroadcast
     */
    public void handleWebSocketConnection(IWebSocket ws) {
        wsConnections.add(ws);
        log.debug("WebSocket client connected; total active={}", wsConnections.size());
        sendWsFrame(ws, "system.notification", Map.of(
            "message",    "Connected to Data-Cloud real-time stream",
            "serverTime", Instant.now().toString()
        ));
        readWsLoop(ws);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cancels all SSE subscriptions and closes all WebSocket connections.
     * Must be called from {@code DataCloudHttpServer.stop()}.
     */
    public void shutdown() {
        if (!sseSubscriptions.isEmpty()) {
            log.info("Cancelling {} active SSE subscriptions", sseSubscriptions.size());
            sseSubscriptions.forEach(EventLogStore.Subscription::cancel);
            sseSubscriptions.clear();
        }
        if (!wsConnections.isEmpty()) {
            log.info("Closing {} active WebSocket connections", wsConnections.size());
            for (IWebSocket ws : new ArrayList<>(wsConnections)) {
                try { ws.close(); } catch (Exception ignored) { /* best-effort */ }
            }
            wsConnections.clear();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void readWsLoop(IWebSocket ws) {
        ws.readMessage().whenComplete((msg, e) -> {
            if (e != null || msg == null) {
                wsConnections.remove(ws);
                log.debug("WebSocket client disconnected; total active={}", wsConnections.size());
            } else {
                readWsLoop(ws);
            }
        });
    }

    /** Broadcasts a typed event to all connected WebSocket clients. */
    private void broadcastWsEvent(String type, Map<String, Object> data) {
        if (wsConnections.isEmpty()) return;
        String json = buildWsFrame(type, data);
        IWebSocket.Message msg = IWebSocket.Message.text(json);
        for (IWebSocket ws : new ArrayList<>(wsConnections)) {
            ws.writeMessage(msg).whenException(e -> wsConnections.remove(ws));
        }
    }

    private void sendWsFrame(IWebSocket ws, String type, Map<String, Object> data) {
        String json = buildWsFrame(type, data);
        ws.writeMessage(IWebSocket.Message.text(json))
            .whenException(e -> wsConnections.remove(ws));
    }

    private String buildWsFrame(String type, Map<String, Object> data) {
        try {
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("type", type);
            frame.put("data", data);
            frame.put("timestamp", Instant.now().toString());
            return objectMapper.writeValueAsString(frame);
        } catch (Exception e) {
            log.warn("Failed to serialize WebSocket frame for type={}", type, e);
            return "{\"type\":\"error\",\"data\":{\"message\":\"serialization error\"}}";
        }
    }

    private byte[] buildSseFrame(String eventType, Map<String, ?> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            String frame = "event: " + eventType + "\n" + "data: " + json + "\n\n";
            return frame.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[SSE] frame build error for event={}: {}", eventType, e.getMessage());
            return ("event: " + eventType + "\ndata: {}\n\n").getBytes(StandardCharsets.UTF_8);
        }
    }

    private byte[] buildEventSseFrame(EventLogStore.EventEntry entry) {
        try {
            byte[] payloadBytes = new byte[entry.payload().remaining()];
            entry.payload().duplicate().get(payloadBytes);
            String payloadStr = new String(payloadBytes, StandardCharsets.UTF_8);
            Map<String, Object> data = Map.of(
                    "eventId",       entry.eventId().toString(),
                    "eventType",     entry.eventType(),
                    "eventVersion",  entry.eventVersion(),
                    "timestamp",     entry.timestamp().toString(),
                    "contentType",   entry.contentType(),
                    "payload",       payloadStr
            );
            String json = objectMapper.writeValueAsString(data);
            return ("event: event\n" + "data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[SSE] buildEventSseFrame error: {}", e.getMessage());
            return "event: event\ndata: {\"error\":\"serialization failure\"}\n\n".getBytes(StandardCharsets.UTF_8);
        }
    }

    private byte[] buildWorkspaceSseFrame(SpotlightItem item) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id",            item.getId());
            data.put("tenantId",      item.getTenantId());
            data.put("summary",       item.getSummary() != null ? item.getSummary() : "");
            data.put("salienceScore", item.getSalienceScore().getScore());
            data.put("isHigh",        item.getSalienceScore().isHigh());
            data.put("isEmergency",   item.isEmergency());
            data.put("priority",      item.getPriority());
            data.put("spotlightedAt", item.getSpotlightedAt().toString());
            String json = objectMapper.writeValueAsString(Map.copyOf(data));
            return ("event: spotlight\ndata: " + json + "\n\n").getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[SSE-WS] frame build error: {}", e.getMessage());
            return "event: spotlight\ndata: {\"error\":\"serialization failure\"}\n\n".getBytes(StandardCharsets.UTF_8);
        }
    }

    private HttpResponse buildSseResponse(ChannelSupplier<ByteBuf> bodyStream) {
        return HttpResponse.ok200()
                .withHeader(HttpHeaders.CONTENT_TYPE,             HttpHeaderValue.of("text/event-stream"))
                .withHeader(HttpHeaders.of("Cache-Control"),      HttpHeaderValue.of("no-cache"))
                .withHeader(HttpHeaders.of("X-Accel-Buffering"),  HttpHeaderValue.of("no"))
                .withHeader(HttpHeaders.of("Connection"),         HttpHeaderValue.of("keep-alive"))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(http.corsAllowOrigin()))
                .withBodyStream(bodyStream)
                .build();
    }

    private HttpResponse buildSseChannelResponse(
            LinkedBlockingQueue<Optional<byte[]>> queue,
            EventLogStore.Subscription subscription,
            String tenantId,
            String collection) {

        ChannelSupplier<ByteBuf> bodyStream = ChannelSuppliers.ofAsyncSupplier(() -> {
            boolean cancelled = subscription != null && subscription.isCancelled();
            if (cancelled) return Promise.of(null);
            try {
                if (subscription != null && subscription.isCancelled()) return Promise.of(null);
                Optional<byte[]> item = queue.poll(SSE_HEARTBEAT_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (item == null) {
                    return Promise.of(ByteBuf.wrapForReading(buildSseFrame("heartbeat",
                        Map.of("ts", Instant.now().toString()))));
                }
                return Promise.of(item.isPresent() ? ByteBuf.wrapForReading(item.get()) : null);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return Promise.of(null);
            }
        });
        log.info("[query-stream] stream opened tenant={} collection={}", tenantId, collection);
        return buildSseResponse(bodyStream);
    }

    private void enqueueSnapshot(
            LinkedBlockingQueue<Optional<byte[]>> queue,
            StorageConnector.QueryResult qr,
            String collection,
            String tenantId,
            String q) {
        try {
            List<Map<String, Object>> results = qr.entities().stream()
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id",             e.getId() != null ? e.getId().toString() : null);
                    item.put("collectionName", e.getCollectionName());
                    item.put("data",           e.getData());
                    item.put("version",        e.getVersion());
                    item.put("createdAt",      e.getCreatedAt()  != null ? e.getCreatedAt().toString()  : null);
                    item.put("updatedAt",      e.getUpdatedAt()  != null ? e.getUpdatedAt().toString()  : null);
                    return item;
                }).toList();

            Map<String, Object> snapshotFrame = new LinkedHashMap<>();
            snapshotFrame.put("q",           q);
            snapshotFrame.put("collection",  collection);
            snapshotFrame.put("tenantId",    tenantId);
            snapshotFrame.put("total",       qr.total());
            snapshotFrame.put("limit",       qr.limit());
            snapshotFrame.put("offset",      qr.offset());
            snapshotFrame.put("hasMore",     qr.hasMore());
            snapshotFrame.put("executionMs", qr.executionTimeMs());
            snapshotFrame.put("results",     results);
            snapshotFrame.put("timestamp",   Instant.now().toString());

            byte[] frame = buildSseFrame("query-snapshot", snapshotFrame);
            if (!queue.offer(Optional.of(frame), 100L, TimeUnit.MILLISECONDS)) {
                log.warn("[query-stream] queue full when enqueuing snapshot tenant={} collection={}", tenantId, collection);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.warn("[query-stream] snapshot serialization error tenant={} collection={}: {}", tenantId, collection, ex.getMessage());
        }
    }

    private static List<String> parseEventTypeFilter(String param) {
        if (param == null || param.isBlank()) return List.of();
        return Arrays.asList(param.trim().split("\\s*,\\s*"));
    }
}
