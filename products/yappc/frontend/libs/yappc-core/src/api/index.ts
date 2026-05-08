/**
 * yappc-core — API sub-module
 *
 * YAPPC-specific API layer migrated from `yappc-api`:
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
export type { ApolloClient } from './graphql/client';

export interface GraphQLProviderProps {
  children: ReactNode;
}

export function GraphQLProvider({ children }: GraphQLProviderProps) {
  return createElement(
    ApolloProvider,
    { client: getGraphQLClient(), children }
  );
}

// ─── GraphQL Operations ──────────────────────────────────────────────────────
export * as BootstrappingOperations from './graphql/operations/bootstrapping.operations';
export * as InitializationOperations from './graphql/operations/initialization.operations';
export * as DevelopmentOperations from './graphql/operations/development.operations';
export * as OperationsOperations from './graphql/operations/operations.operations';
export * as CollaborationOperations from './graphql/operations/collaboration.operations';
export * as SecurityOperations from './graphql/operations/security.operations';

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

// Legacy Apollo hook modules remain available as source files, but are not
// re-exported from the runtime API barrel until their contracts are migrated to
// Apollo Client v4. Pulling them into every consumer makes mounted app typecheck
// depend on unrelated initialization/development/operations/security surfaces.
