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

import { ApolloProvider } from '@apollo/client/react';
import { createElement, type ReactNode } from 'react';

import { getGraphQLClient } from './graphql/client';

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
  resetGraphQLClient,
  setTokens,
  getAccessToken,
  getRefreshToken,
  clearTokens,
  gql,
} from './graphql/client';
export { getGraphQLClient } from './graphql/client';

export type { ApolloClient, NormalizedCacheObject } from './graphql/client';

export interface GraphQLProviderProps {
  children: ReactNode;
}

export function GraphQLProvider({ children }: GraphQLProviderProps) {
  return createElement(ApolloProvider, { client: getGraphQLClient() }, children);
}

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
} from '@apollo/client/react';

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

// ============================================================================
// DEPRECATION WARNING
// ============================================================================
 
console.warn(
  '[DEPRECATED] @ghatana/yappc-api is deprecated. Use @yappc/api instead. ' +
    'See: docs/NAMING_CONVENTIONS.md'
);
