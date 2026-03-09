/**
 * API Client Library
 * 
 * Central export for all API clients including GraphQL, DevSecOps, AI, Auth, etc.
 * 
 * @doc.type module
 * @doc.purpose API client library exports
 * @doc.layer api
 * @doc.pattern Barrel
 */

// =============================================================================
// Authentication Service
// =============================================================================

export { AuthService, authService } from './auth/authService';
export type {
  User,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
  RefreshTokenRequest,
  RefreshTokenResponse,
  LogoutRequest,
  PasswordResetRequest,
  PasswordResetConfirmRequest,
  ApiError,
  AuthServiceConfig,
} from './auth/authService';

// =============================================================================
// GraphQL Client
// =============================================================================

export {
  createGraphQLClient,
  getGraphQLClient,
  resetGraphQLClient,
  setTokens,
  getAccessToken,
  getRefreshToken,
  clearTokens,
  gql,
} from './graphql/client';

export type { ApolloClient, NormalizedCacheObject } from './graphql/client';

// =============================================================================
// Bootstrapping Operations
// =============================================================================

export * from './graphql/operations/bootstrapping.operations';

// =============================================================================
// Initialization Operations
// =============================================================================

export * from './graphql/operations/initialization.operations';

// =============================================================================
// Development Operations
// =============================================================================

export * from './graphql/operations/development.operations';

// =============================================================================
// Operations Phase Operations
// =============================================================================

export * from './graphql/operations/operations.operations';

// =============================================================================
// Collaboration Operations
// =============================================================================

export * from './graphql/operations/collaboration.operations';

// =============================================================================
// Security Operations
// =============================================================================

export * from './graphql/operations/security.operations';

// =============================================================================
// Apollo Client Hooks
// =============================================================================

export {
  useQuery,
  useLazyQuery,
  useMutation,
  useSubscription,
  useApolloClient,
  ApolloProvider,
} from '@apollo/client';

// =============================================================================
// DevSecOps API Client (Legacy)
// =============================================================================

export * from './devsecops/client';
export { devsecopsClient } from './devsecops/client';

// =============================================================================
// AI Service API Client
// =============================================================================

export * from './ai';
export { aiClient } from './ai';

// =============================================================================
// GraphQL (consolidated from @ghatana/yappc-api)
// Re-exported here as the canonical API client entry point.
// Direct imports from '@ghatana/yappc-api' still work but are deprecated.
// =============================================================================

export * from '../../graphql/src';
