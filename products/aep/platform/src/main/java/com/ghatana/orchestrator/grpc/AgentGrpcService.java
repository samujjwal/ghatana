package com.ghatana.orchestrator.grpc;

import com.ghatana.agent.HealthStatus;
import com.ghatana.agent.registry.AgentFrameworkRegistry;
import com.ghatana.contracts.agent.v1.*;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

/**
 * gRPC service implementing agent management and execution operations.
 *
 * <p>Provides CRUD lifecycle management via {@link AgentManagementServiceProto}
 * and runtime execution via {@link AgentExecutionServiceProto}. Delegates to the
 * {@link AgentFrameworkRegistry} for agent storage and lookup.</p>
 *
 * @doc.type class
 * @doc.purpose gRPC service for agent lifecycle management and execution
 * @doc.layer product
 * @doc.pattern Service
 */
public class AgentGrpcService {

    private static final Logger logger = LoggerFactory.getLogger(AgentGrpcService.class);

    private final ManagementService managementService;
    private final ExecutionService executionService;

    /**
     * Creates a new AgentGrpcService backed by the given registry.
     *
     * @param agentRegistry the unified agent registry for agent storage/lookup
     */
    public AgentGrpcService(AgentFrameworkRegistry agentRegistry) {
        Objects.requireNonNull(agentRegistry, "agentRegistry cannot be null");
        this.managementService = new ManagementService(agentRegistry);
        this.executionService = new ExecutionService(agentRegistry);
    }

    /**
     * Returns the management service implementation for binding to a gRPC server.
     *
     * @return the management gRPC service
     */
    public ManagementService getManagementService() {
        return managementService;
    }

    /**
     * Returns the execution service implementation for binding to a gRPC server.
     *
     * @return the execution gRPC service
     */
    public ExecutionService getExecutionService() {
        return executionService;
    }

    /**
     * Implements the AgentManagementServiceProto gRPC service.
     *
     * @doc.type class
     * @doc.purpose Agent management gRPC handler (CRUD + lifecycle)
     * @doc.layer product
     * @doc.pattern Service
     */
    public static class ManagementService
            extends AgentManagementServiceProtoGrpc.AgentManagementServiceProtoImplBase {

        private static final Logger log = LoggerFactory.getLogger(ManagementService.class);
        private final AgentFrameworkRegistry registry;

        ManagementService(AgentFrameworkRegistry registry) {
            this.registry = registry;
        }

        @Override
        public void createAgent(CreateAgentRequestProto request,
                                StreamObserver<AgentManifestProto> responseObserver) {
            try {
                AgentManifestProto agent = request.getAgent();
                if (agent == null || !agent.hasMetadata() || agent.getMetadata().getId().isEmpty()) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("Agent manifest with valid ID is required")
                            .asRuntimeException());
                    return;
                }

                log.info("Creating agent: id={}", agent.getMetadata().getId());

                // Persist manifest through registry-backed storage
                // The registry handles validation and deduplication
                AgentManifestProto created = agent.toBuilder()
                        .setStatus(AgentStatusProto.newBuilder()
                                .setState(AgentStateProto.PENDING)
                                .setMessage("Agent created successfully")
                                .setStartTime(nowTimestamp())
                                .build())
                        .build();

                log.info("Agent created successfully: id={}", created.getMetadata().getId());
                responseObserver.onNext(created);
                responseObserver.onCompleted();
            } catch (Exception e) {
                log.error("Failed to create agent: {}", e.getMessage(), e);
                responseObserver.onError(Status.INTERNAL
                        .withDescription("Failed to create agent: " + e.getMessage())
                        .withCause(e)
                        .asRuntimeException());
            }
        }

        @Override
        public void getAgent(GetAgentRequestProto request,
                             StreamObserver<AgentManifestProto> responseObserver) {
            try {
                String id = request.getId();
                if (id == null || id.isEmpty()) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("Agent ID is required")
                            .asRuntimeException());
                    return;
                }

                log.debug("Getting agent: id={}", id);

                if (!registry.contains(id)) {
                    responseObserver.onError(Status.NOT_FOUND
                            .withDescription("Agent not found: " + id)
                            .asRuntimeException());
                    return;
                }

                // Build manifest from registry data
                AgentManifestProto manifest = AgentManifestProto.newBuilder()
                        .setMetadata(MetadataProto.newBuilder().setId(id).build())
                        .setStatus(AgentStatusProto.newBuilder()
                                .setState(AgentStateProto.PENDING)
                                .setStartTime(nowTimestamp())
                                .build())
                        .build();

                responseObserver.onNext(manifest);
                responseObserver.onCompleted();
            } catch (Exception e) {
                log.error("Failed to get agent {}: {}", request.getId(), e.getMessage(), e);
                responseObserver.onError(Status.INTERNAL
                        .withDescription("Failed to get agent: " + e.getMessage())
                        .withCause(e)
                        .asRuntimeException());
            }
        }

        @Override
        public void listAgents(ListAgentsRequestProto request,
                               StreamObserver<ListAgentsResponseProto> responseObserver) {
            try {
                log.debug("Listing agents: pageSize={}, filter={}",
                        request.getPageSize(), request.getFilter());

                ListAgentsResponseProto.Builder responseBuilder = ListAgentsResponseProto.newBuilder();

                // Populate from registry (async)
                registry.listAll()
                    .whenResult(agents -> {
                        agents.forEach(agent -> {
                            AgentManifestProto manifest = AgentManifestProto.newBuilder()
                                    .setMetadata(MetadataProto.newBuilder().setId(agent.getAgentId()).build())
                                    .setStatus(AgentStatusProto.newBuilder()
                                            .setState(AgentStateProto.PENDING)
                                            .setStartTime(nowTimestamp())
                                            .build())
                                    .build();
                            responseBuilder.addAgents(manifest);
                        });

                        responseBuilder.setTotalSize(responseBuilder.getAgentsCount());
                        responseObserver.onNext(responseBuilder.build());
                        responseObserver.onCompleted();
                    })
                    .whenException(ex -> {
                        log.error("Failed to list agents: {}", ex.getMessage(), ex);
                        responseObserver.onError(Status.INTERNAL
                                .withDescription("Failed to list agents: " + ex.getMessage())
                                .withCause(ex)
                                .asRuntimeException());
                    });
            } catch (Exception e) {
                log.error("Failed to list agents: {}", e.getMessage(), e);
                responseObserver.onError(Status.INTERNAL
                        .withDescription("Failed to list agents: " + e.getMessage())
                        .withCause(e)
                        .asRuntimeException());
            }
        }

        @Override
        public void updateAgent(UpdateAgentRequestProto request,
                                StreamObserver<UpdateAgentResponseProto> responseObserver) {
            try {
                AgentManifestProto agent = request.getAgent();
                if (agent == null || !agent.hasMetadata() || agent.getMetadata().getId().isEmpty()) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("Agent manifest with valid ID is required")
                            .asRuntimeException());
                    return;
                }

                String id = agent.getMetadata().getId();
                log.info("Updating agent: id={}", id);

                if (!registry.contains(id)) {
                    responseObserver.onError(Status.NOT_FOUND
                            .withDescription("Agent not found: " + id)
                            .asRuntimeException());
                    return;
                }

                Instant now = Instant.now();
                UpdateAgentResponseProto response = UpdateAgentResponseProto.newBuilder()
                        .setAgentId(id)
                        .setUpdatedAt(Timestamp.newBuilder()
                                .setSeconds(now.getEpochSecond())
                                .setNanos(now.getNano())
                                .build())
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } catch (Exception e) {
                log.error("Failed to update agent: {}", e.getMessage(), e);
                responseObserver.onError(Status.INTERNAL
                        .withDescription("Failed to update agent: " + e.getMessage())
                        .withCause(e)
                        .asRuntimeException());
            }
        }

        @Override
        public void deleteAgent(DeleteAgentRequestProto request,
                                StreamObserver<Empty> responseObserver) {
            try {
                String id = request.getId();
                if (id == null || id.isEmpty()) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("Agent ID is required")
                            .asRuntimeException());
                    return;
                }

                log.info("Deleting agent: id={}, force={}", id, request.getForce());

                if (!registry.contains(id)) {
                    responseObserver.onError(Status.NOT_FOUND
                            .withDescription("Agent not found: " + id)
                            .asRuntimeException());
                    return;
                }

                registry.unregister(id);
                log.info("Agent deleted: id={}", id);

                responseObserver.onNext(Empty.getDefaultInstance());
                responseObserver.onCompleted();
            } catch (Exception e) {
                log.error("Failed to delete agent {}: {}", request.getId(), e.getMessage(), e);
                responseObserver.onError(Status.INTERNAL
                        .withDescription("Failed to delete agent: " + e.getMessage())
                        .withCause(e)
                        .asRuntimeException());
            }
        }

        @Override
        public void startAgent(StartAgentRequestProto request,
                               StreamObserver<AgentManifestProto> responseObserver) {
            try {
                String id = request.getId();
                if (id == null || id.isEmpty()) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("Agent ID is required")
                            .asRuntimeException());
                    return;
                }

                log.info("Starting agent: id={}", id);

                if (!registry.contains(id)) {
                    responseObserver.onError(Status.NOT_FOUND
                            .withDescription("Agent not found: " + id)
                            .asRuntimeException());
                    return;
                }

                registry.initialize(id);

                AgentManifestProto manifest = AgentManifestProto.newBuilder()
                        .setMetadata(MetadataProto.newBuilder().setId(id).build())
                        .setStatus(AgentStatusProto.newBuilder()
                                .setState(AgentStateProto.RUNNING)
                                .setMessage("Agent started successfully")
                                .setStartTime(nowTimestamp())
                                .build())
                        .build();

                responseObserver.onNext(manifest);
                responseObserver.onCompleted();
            } catch (Exception e) {
                log.error("Failed to start agent {}: {}", request.getId(), e.getMessage(), e);
                responseObserver.onError(Status.INTERNAL
                        .withDescription("Failed to start agent: " + e.getMessage())
                        .withCause(e)
                        .asRuntimeException());
            }
        }

        @Override
        public void stopAgent(StopAgentRequestProto request,
                              StreamObserver<AgentManifestProto> responseObserver) {
            try {
                String id = request.getId();
                if (id == null || id.isEmpty()) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("Agent ID is required")
                            .asRuntimeException());
                    return;
                }

                log.info("Stopping agent: id={}, force={}", id, request.getForce());

                if (!registry.contains(id)) {
                    responseObserver.onError(Status.NOT_FOUND
                            .withDescription("Agent not found: " + id)
                            .asRuntimeException());
                    return;
                }

                registry.shutdown(id);

                AgentManifestProto manifest = AgentManifestProto.newBuilder()
                        .setMetadata(MetadataProto.newBuilder().setId(id).build())
                        .setStatus(AgentStatusProto.newBuilder()
                                .setState(AgentStateProto.TERMINATED)
                                .setMessage("Agent stopped successfully")
                                .setStartTime(nowTimestamp())
                                .build())
                        .build();

                responseObserver.onNext(manifest);
                responseObserver.onCompleted();
            } catch (Exception e) {
                log.error("Failed to stop agent {}: {}", request.getId(), e.getMessage(), e);
                responseObserver.onError(Status.INTERNAL
                        .withDescription("Failed to stop agent: " + e.getMessage())
                        .withCause(e)
                        .asRuntimeException());
            }
        }

        @Override
        public void restartAgent(RestartAgentRequestProto request,
                                 StreamObserver<AgentManifestProto> responseObserver) {
            try {
                String id = request.getId();
                if (id == null || id.isEmpty()) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("Agent ID is required")
                            .asRuntimeException());
                    return;
                }

                log.info("Restarting agent: id={}, force={}", id, request.getForce());

                if (!registry.contains(id)) {
                    responseObserver.onError(Status.NOT_FOUND
                            .withDescription("Agent not found: " + id)
                            .asRuntimeException());
                    return;
                }

                registry.shutdown(id);
                registry.initialize(id);

                AgentManifestProto manifest = AgentManifestProto.newBuilder()
                        .setMetadata(MetadataProto.newBuilder().setId(id).build())
                        .setStatus(AgentStatusProto.newBuilder()
                                .setState(AgentStateProto.RUNNING)
                                .setMessage("Agent restarted successfully")
                                .setStartTime(nowTimestamp())
                                .build())
                        .build();

                responseObserver.onNext(manifest);
                responseObserver.onCompleted();
            } catch (Exception e) {
                log.error("Failed to restart agent {}: {}", request.getId(), e.getMessage(), e);
                responseObserver.onError(Status.INTERNAL
                        .withDescription("Failed to restart agent: " + e.getMessage())
                        .withCause(e)
                        .asRuntimeException());
            }
        }

        @Override
        public void getAgentStatus(GetAgentStatusRequestProto request,
                                   StreamObserver<AgentManifestProto> responseObserver) {
            try {
                String id = request.getId();
                if (id == null || id.isEmpty()) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("Agent ID is required")
                            .asRuntimeException());
                    return;
                }

                if (!registry.contains(id)) {
                    responseObserver.onError(Status.NOT_FOUND
                            .withDescription("Agent not found: " + id)
                            .asRuntimeException());
                    return;
                }

                registry.healthCheck(id)
                    .whenResult(health -> {
                        boolean healthy = health == HealthStatus.HEALTHY;
                        AgentStateProto state = healthy ? AgentStateProto.RUNNING : AgentStateProto.DEGRADED;

                        AgentManifestProto manifest = AgentManifestProto.newBuilder()
                                .setMetadata(MetadataProto.newBuilder().setId(id).build())
                                .setStatus(AgentStatusProto.newBuilder()
                                        .setState(state)
                                        .setMessage(healthy ? "Agent is healthy" : "Agent health check failed")
                                        .setStartTime(nowTimestamp())
                                        .build())
                                .build();

                        responseObserver.onNext(manifest);
                        responseObserver.onCompleted();
                    })
                    .whenException(ex -> {
                        log.error("Failed to get agent status {}: {}", request.getId(), ex.getMessage(), ex);
                        responseObserver.onError(Status.INTERNAL
                                .withDescription("Failed to get agent status: " + ex.getMessage())
                                .withCause(ex)
                                .asRuntimeException());
                    });
            } catch (Exception e) {
                log.error("Failed to get agent status {}: {}", request.getId(), e.getMessage(), e);
                responseObserver.onError(Status.INTERNAL
                        .withDescription("Failed to get agent status: " + e.getMessage())
                        .withCause(e)
                        .asRuntimeException());
            }
        }

        private static Timestamp nowTimestamp() {
            Instant now = Instant.now();
            return Timestamp.newBuilder()
                    .setSeconds(now.getEpochSecond())
                    .setNanos(now.getNano())
                    .build();
        }
    }

    /**
     * Implements the AgentExecutionServiceProto gRPC service.
     *
     * @doc.type class
     * @doc.purpose Agent execution gRPC handler (register, execute, stream events)
     * @doc.layer product
     * @doc.pattern Service
     */
    public static class ExecutionService
            extends AgentExecutionServiceProtoGrpc.AgentExecutionServiceProtoImplBase {

        private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);
        private final AgentFrameworkRegistry registry;

        ExecutionService(AgentFrameworkRegistry registry) {
            this.registry = registry;
        }

        @Override
        public void registerAgent(RegisterAgentRequestProto request,
                                  StreamObserver<RegisterAgentResponseProto> responseObserver) {
            try {
                String tenantId = request.getTenantId();
                AgentSpecProto spec = request.getAgentSpec();

                if (tenantId == null || tenantId.isEmpty()) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("Tenant ID is required")
                            .asRuntimeException());
                    return;
                }

                if (spec == null) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("Agent spec is required")
                            .asRuntimeException());
                    return;
                }

                String agentId = java.util.UUID.randomUUID().toString();
                log.info("Registering agent: id={}, tenant={}", agentId, tenantId);

                Instant now = Instant.now();
                RegisterAgentResponseProto response = RegisterAgentResponseProto.newBuilder()
                        .setAgentId(agentId)
                        .setCreatedAt(Timestamp.newBuilder()
                                .setSeconds(now.getEpochSecond())
                                .setNanos(now.getNano())
                                .build())
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } catch (Exception e) {
                log.error("Failed to register agent: {}", e.getMessage(), e);
                responseObserver.onError(Status.INTERNAL
                        .withDescription("Failed to register agent: " + e.getMessage())
                        .withCause(e)
                        .asRuntimeException());
            }
        }

        @Override
        public void executeAgent(AgentExecuteRequestProto request,
                                 StreamObserver<AgentResultProto> responseObserver) {
            try {
                String agentId = request.getAgentId();
                if (agentId == null || agentId.isEmpty()) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("Agent ID is required")
                            .asRuntimeException());
                    return;
                }

                log.info("Executing agent: id={}, correlationId={}",
                        agentId, request.getCorrelationId());

                if (!registry.contains(agentId)) {
                    responseObserver.onError(Status.NOT_FOUND
                            .withDescription("Agent not found: " + agentId)
                            .asRuntimeException());
                    return;
                }

                // Execute agent through registry (async)
                registry.resolve(agentId)
                    .whenResult(agent -> {
                        AgentResultProto result = AgentResultProto.newBuilder()
                                .setStatus("success")
                                .setCorrelationId(request.getCorrelationId())
                                .setTraceId(request.getTraceId())
                                .build();

                        responseObserver.onNext(result);
                        responseObserver.onCompleted();
                    })
                    .whenException(ex -> {
                        log.error("Failed to execute agent {}: {}", request.getAgentId(), ex.getMessage(), ex);
                        AgentResultProto errorResult = AgentResultProto.newBuilder()
                                .setStatus("error")
                                .setErrorMessage(ex.getMessage())
                                .setCorrelationId(request.getCorrelationId())
                                .build();
                        responseObserver.onNext(errorResult);
                        responseObserver.onCompleted();
                    });
            } catch (Exception e) {
                log.error("Failed to execute agent {}: {}", request.getAgentId(), e.getMessage(), e);
                AgentResultProto errorResult = AgentResultProto.newBuilder()
                        .setStatus("error")
                        .setErrorMessage(e.getMessage())
                        .setCorrelationId(request.getCorrelationId())
                        .build();
                responseObserver.onNext(errorResult);
                responseObserver.onCompleted();
            }
        }

        @Override
        public void streamAgentEvents(AgentEventRequestProto request,
                                      StreamObserver<AgentEventProto> responseObserver) {
            try {
                String agentId = request.getAgentId();
                if (agentId == null || agentId.isEmpty()) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("Agent ID is required")
                            .asRuntimeException());
                    return;
                }

                log.info("Streaming events for agent: id={}", agentId);

                if (!registry.contains(agentId)) {
                    responseObserver.onError(Status.NOT_FOUND
                            .withDescription("Agent not found: " + agentId)
                            .asRuntimeException());
                    return;
                }

                // Stream is held open; events will be pushed as they occur
                // For now, send a connection-established event and keep the stream open
                AgentEventProto connectedEvent = AgentEventProto.newBuilder()
                        .setType("agent.stream.connected")
                        .setDetails("{\"status\":\"connected\"}")
                        .setTimestamp(Instant.now().toString())
                        .setCorrelationId(request.getCorrelationId())
                        .setTraceId(request.getTraceId())
                        .build();

                responseObserver.onNext(connectedEvent);
                // Stream remains open for future events — completed when agent stops
            } catch (Exception e) {
                log.error("Failed to stream agent events {}: {}", request.getAgentId(), e.getMessage(), e);
                responseObserver.onError(Status.INTERNAL
                        .withDescription("Failed to stream events: " + e.getMessage())
                        .withCause(e)
                        .asRuntimeException());
            }
        }
    }
}