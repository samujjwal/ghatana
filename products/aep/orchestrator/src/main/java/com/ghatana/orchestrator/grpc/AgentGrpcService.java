package com.ghatana.orchestrator.grpc;

// AgentFrameworkRegistry not yet implemented - commented out
// import com.ghatana.agent.registry.AgentFrameworkRegistry;
import com.ghatana.contracts.agent.v1.*;

/**
 * gRPC service implementing agent management and execution operations.
 *
 * <p>Provides CRUD lifecycle management via {@link AgentManagementServiceProto}
 * and runtime execution via {@link AgentExecutionServiceProto}. Delegates to the
 * {@link AgentFrameworkRegistry} for agent storage and lookup.</p>
 *
 * <p>NOTE: This class is commented out because AgentFrameworkRegistry doesn't exist
 * and the API doesn't match the actual AgentRegistry SPI. Needs to be reimplemented
 * to use com.ghatana.agent.spi.AgentRegistry.</p>
 *
 * @doc.type class
 * @doc.purpose gRPC service for agent lifecycle management and execution
 * @doc.layer product
 * @doc.pattern Service
 */
// TODO: Reimplement to use com.ghatana.agent.spi.AgentRegistry
// The current implementation references non-existent AgentFrameworkRegistry
// public class AgentGrpcService {
//
//     private static final Logger logger = LoggerFactory.getLogger(AgentGrpcService.class);
//
//     private final ManagementService managementService;
//     private final ExecutionService executionService;
//
//     /**
//      * Creates a new AgentGrpcService backed by the given registry.
//      *
//      * @param agentRegistry the unified agent registry for agent storage/lookup
//      */
//     public AgentGrpcService(AgentRegistry agentRegistry) {
//         Objects.requireNonNull(agentRegistry, "agentRegistry cannot be null");
//         this.managementService = new ManagementService(agentRegistry);
//         this.executionService = new ExecutionService(agentRegistry);
//     }
//
//     /**
//      * Returns the management service implementation for binding to a gRPC server.
//      *
//      * @return the management gRPC service
//      */
//     public ManagementService getManagementService() {
//         return managementService;
//     }
//
//     /**
//      * Returns the execution service implementation for binding to a gRPC server.
//      *
//      * @return the execution gRPC service
//      */
//     public ExecutionService getExecutionService() {
//         return executionService;
//     }
//
//     /**
//      * Implements the AgentManagementServiceProto gRPC service.
//      *
//      * @doc.type class
//      * @doc.purpose Agent management gRPC handler (CRUD + lifecycle)
//      * @doc.layer product
//      * @doc.pattern Service
//      */
//     public static class ManagementService extends AgentManagementServiceProtoGrpc.AgentManagementServiceProtoImplBase {
//
//         private static final Logger log = LoggerFactory.getLogger(ManagementService.class);
//         private final AgentFrameworkRegistry registry;
//
//         ManagementService(AgentFrameworkRegistry registry) {
//             this.registry = registry;
//         }
//
//         // ... rest of implementation
//     }
//
//     /**
//      * Implements the AgentExecutionServiceProto gRPC service.
//      *
//      * @doc.type class
//      * @doc.purpose Agent execution gRPC handler (register, execute, stream events)
//      * @doc.layer product
//      * @doc.pattern Service
//      */
//     public static class ExecutionService extends AgentExecutionServiceProtoGrpc.AgentExecutionServiceProtoImplBase {
//
//         private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);
//         private final AgentFrameworkRegistry registry;
//
//         ExecutionService(AgentFrameworkRegistry registry) {
//             this.registry = registry;
//         }
//
//         // ... rest of implementation
//     }
// }
