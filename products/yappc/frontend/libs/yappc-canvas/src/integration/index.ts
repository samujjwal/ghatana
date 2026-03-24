/**
 * Canvas Data Integration
 *
 * REST/GraphQL/WebSocket synchronization with offline support and conflict resolution.
 * Also includes code generation from canvas nodes.
 */

export * from './types';
export * from './restSync';
export * from './websocketSync';
export * from './yjsSync';
export * from './graphqlSync';
export * from './offlineQueue';

// Re-export main classes
export { RestSyncAdapter, createRestSyncAdapter } from './restSync';
export {
  WebSocketSyncAdapter,
  createWebSocketSyncAdapter,
} from './websocketSync';
export { GraphQLSyncAdapter, createGraphQLSyncAdapter } from './graphqlSync';
export { OfflineQueue, createOfflineQueue } from './offlineQueue';

// Code generation from canvas nodes
export {
  generateCodeFromNode,
  generateCodeFromFlow,
  type CodeGenerationRequest,
  type CodeGenerationOptions,
  type GeneratedFile,
  type CodeGenerationResult,
} from './codeGeneration';

// AI-powered code generation
export {
  AICodeGenerationService,
  createAICodeGenerationService,
  type AICodeGenerationOptions,
} from './aiCodeGeneration';

// DevSecOps workflow integration
export {
  DevSecOpsCanvasIntegration,
  createDevSecOpsCanvasIntegration,
  type DeploymentConfig,
  type DeploymentResult,
  type InfrastructureProvisionResult,
} from './devSecOpsIntegration';
