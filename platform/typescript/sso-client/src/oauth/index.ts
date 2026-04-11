/**
 * @ghatana/sso-client — OAuth sub-module
 *
 * Consolidated OAuth 2.0 utilities migrated from `@yappc/auth`.
 *
 * @doc.type module
 * @doc.purpose OAuth provider configuration, utilities, and React hooks
 * @doc.layer platform
 * @doc.pattern Barrel Export
 */

export * from './types';
export * from './utils';
export * from './providers';
export { useOAuth } from './useOAuth';
