/**
 * Preview Session Service
 *
 * @doc.type service
 * @doc.purpose Manage scoped preview sessions bound to tenant, workspace, project, artifact, user, and expiration
 * @doc.layer product
 * @doc.pattern Service
 */

export interface PreviewSessionContext {
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
  readonly artifactId: string;
  readonly userId: string;
  readonly expiresAt: string;
}

export interface PreviewSession {
  readonly sessionId: string;
  readonly context: PreviewSessionContext;
  readonly token: string;
  readonly createdAt: string;
}

export interface PreviewSessionRequest {
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
  readonly artifactId: string;
  readonly userId: string;
  readonly ttlSeconds?: number;
}

export interface PreviewSessionResponse {
  readonly sessionId: string;
  readonly token: string;
  readonly expiresAt: string;
}

/**
 * Check if a preview session is valid and not expired
 */
export function isPreviewSessionValid(session: PreviewSession): boolean {
  const now = new Date();
  const expiresAt = new Date(session.context.expiresAt);
  return now < expiresAt;
}

/**
 * Check if a preview session is expired
 */
export function isPreviewSessionExpired(session: PreviewSession): boolean {
  return !isPreviewSessionValid(session);
}

/**
 * Get time remaining until session expiration
 */
export function getTimeUntilExpiration(session: PreviewSession): number {
  const now = new Date();
  const expiresAt = new Date(session.context.expiresAt);
  return expiresAt.getTime() - now.getTime();
}

/**
 * Validate that preview session context matches expected scope
 */
export function validatePreviewSessionScope(
  session: PreviewSession,
  expectedTenantId: string,
  expectedWorkspaceId: string,
  expectedProjectId: string,
  expectedArtifactId: string,
  expectedUserId: string
): boolean {
  return (
    session.context.tenantId === expectedTenantId &&
    session.context.workspaceId === expectedWorkspaceId &&
    session.context.projectId === expectedProjectId &&
    session.context.artifactId === expectedArtifactId &&
    session.context.userId === expectedUserId
  );
}

/**
 * Create a preview session request with default TTL
 */
export function createPreviewSessionRequest(
  tenantId: string,
  workspaceId: string,
  projectId: string,
  artifactId: string,
  userId: string,
  ttlSeconds: number = 3600 // Default 1 hour
): PreviewSessionRequest {
  return {
    tenantId,
    workspaceId,
    projectId,
    artifactId,
    userId,
    ttlSeconds,
  };
}

/**
 * Default TTL for preview sessions (1 hour)
 */
export const DEFAULT_PREVIEW_SESSION_TTL = 3600;

/**
 * Maximum TTL for preview sessions (24 hours)
 */
export const MAX_PREVIEW_SESSION_TTL = 86400;
