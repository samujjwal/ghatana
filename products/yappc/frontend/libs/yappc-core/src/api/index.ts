/**
 * @yappc/core — API sub-module
 *
 * YAPPC-specific API layer migrated from `@yappc/api`:
 * Apollo/GraphQL client, GraphQL operations, AI client, AuthService, and hooks.
 *
 * @doc.type module
 * @doc.purpose YAPPC API layer — GraphQL, AI, and Auth service exports
 * @doc.layer product
 * @doc.pattern Barrel Export
 */

import { ApolloProvider } from '@apollo/client/react';
import { createElement, type ReactNode } from 'react';

import { getGraphQLClient } from './graphql/client';

// ─── Authentication Service ─────────────────────────────────────────────────
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

// ─── GraphQL Client ──────────────────────────────────────────────────────────
export {
  createGraphQLClient,
  resetGraphQLClient,
  setTokens,
  getAccessToken,
  getRefreshToken,
  clearTokens,
  gql,
  getGraphQLClient,
} from './graphql/client';
export type { ApolloClient, NormalizedCacheObject } from './graphql/client';

export interface GraphQLProviderProps {
  children: ReactNode;
}

export function GraphQLProvider({ children }: GraphQLProviderProps) {
  return createElement(
    ApolloProvider,
    { client: getGraphQLClient() },
    children
  );
}

// ─── GraphQL Operations ──────────────────────────────────────────────────────
export * from './graphql/operations/bootstrapping.operations';
export * from './graphql/operations/initialization.operations';
export * from './graphql/operations/development.operations';
export * from './graphql/operations/operations.operations';
export * from './graphql/operations/collaboration.operations';
export * from './graphql/operations/security.operations';
// Aliases removed - using direct operation exports

// ─── Apollo Client Hooks (re-exported for convenience) ───────────────────────
export {
  useQuery,
  useLazyQuery,
  useMutation,
  useSubscription,
  useApolloClient,
  ApolloProvider,
} from '@apollo/client/react';

// ─── AI Client ───────────────────────────────────────────────────────────────
export * from './ai';
export { aiClient } from './ai';

// ─── Apollo Hooks ────────────────────────────────────────────────────────────
export * from './hooks';
