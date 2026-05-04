/**
 * Preview Session Management
 *
 * Handles signed preview sessions with expiration and scoping.
 * Ensures preview URLs are time-limited and scoped to specific resources.
 *
 * @doc.type security
 * @doc.purpose Signed preview session management
 * @doc.layer product
 */

export interface PreviewSession {
  /** Session ID */
  sessionId: string;
  /** Project ID */
  projectId: string;
  /** Artifact ID */
  artifactId: string;
  /** User ID who created the session */
  userId: string;
  /** Session creation timestamp */
  createdAt: string;
  /** Session expiration timestamp */
  expiresAt: string;
  /** Session scope */
  scope: PreviewSessionScope;
  /** Session signature */
  signature: string;
}

export interface PreviewSessionScope {
  /** Allowed artifact IDs */
  allowedArtifactIds?: string[];
  /** Allowed project IDs */
  allowedProjectIds?: string[];
  /** Read-only flag */
  readOnly?: boolean;
  /** Allow download flag */
  allowDownload?: boolean;
  /** Allow clipboard flag */
  allowClipboard?: boolean;
  /** Maximum session duration in seconds */
  maxDuration?: number;
}

export interface PreviewSessionOptions {
  /** Project ID */
  projectId: string;
  /** Artifact ID */
  artifactId: string;
  /** User ID */
  userId: string;
  /** Session scope */
  scope?: PreviewSessionScope;
  /** Session duration in seconds (default: 1 hour) */
  duration?: number;
}

/**
 * Default session duration: 1 hour
 */
const DEFAULT_SESSION_DURATION = 3600; // 1 hour in seconds

/**
 * Maximum session duration: 24 hours
 */
const MAX_SESSION_DURATION = 86400; // 24 hours in seconds

/**
 * Generate a session ID
 */
function generateSessionId(): string {
  return `preview_${Date.now()}_${Math.random().toString(36).substring(2, 15)}`;
}

/**
 * Create a preview session
 */
export function createPreviewSession(options: PreviewSessionOptions): PreviewSession {
  const { projectId, artifactId, userId, scope = {}, duration = DEFAULT_SESSION_DURATION } = options;

  // Enforce maximum duration
  const actualDuration = Math.min(duration, MAX_SESSION_DURATION);
  
  const now = new Date();
  const expiresAt = new Date(now.getTime() + actualDuration * 1000);

  const sessionId = generateSessionId();
  const signature = generateSignature(sessionId, projectId, artifactId, userId, expiresAt.toISOString());

  return {
    sessionId,
    projectId,
    artifactId,
    userId,
    createdAt: now.toISOString(),
    expiresAt: expiresAt.toISOString(),
    scope: {
      readOnly: true,
      allowDownload: false,
      allowClipboard: false,
      maxDuration: actualDuration,
      ...scope,
    },
    signature,
  };
}

/**
 * Validate a preview session
 */
export function validatePreviewSession(session: PreviewSession): { valid: boolean; reason?: string } {
  const now = new Date();
  const expiresAt = new Date(session.expiresAt);

  // Check expiration
  if (now > expiresAt) {
    return { valid: false, reason: 'Session expired' };
  }

  // Verify signature
  const expectedSignature = generateSignature(
    session.sessionId,
    session.projectId,
    session.artifactId,
    session.userId,
    session.expiresAt
  );

  if (session.signature !== expectedSignature) {
    return { valid: false, reason: 'Invalid signature' };
  }

  return { valid: true };
}

/**
 * Check if a resource is within session scope
 */
export function isResourceInScope(
  session: PreviewSession,
  projectId: string,
  artifactId: string
): boolean {
  const { scope } = session;

  // Check project scope
  if (scope.allowedProjectIds && !scope.allowedProjectIds.includes(projectId)) {
    return false;
  }

  // Check artifact scope
  if (scope.allowedArtifactIds && !scope.allowedArtifactIds.includes(artifactId)) {
    return false;
  }

  return true;
}

/**
 * Generate a session signature
 * Note: In production, this should use a proper HMAC with a secret key
 */
function generateSignature(
  sessionId: string,
  projectId: string,
  artifactId: string,
  userId: string,
  expiresAt: string | Date
): string {
  const expiresAtStr = typeof expiresAt === 'string' ? expiresAt : expiresAt.toISOString();
  
  // Simple hash for demonstration - use HMAC in production
  const data = `${sessionId}:${projectId}:${artifactId}:${userId}:${expiresAtStr}`;
  
  // Simple hash function (replace with proper HMAC in production)
  let hash = 0;
  for (let i = 0; i < data.length; i++) {
    const char = data.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash = hash & hash; // Convert to 32-bit integer
  }
  
  return `sig_${Math.abs(hash).toString(36)}`;
}

/**
 * Get session expiration time
 */
export function getSessionExpirationTime(session: PreviewSession): Date {
  return new Date(session.expiresAt);
}

/**
 * Check if session is expired
 */
export function isSessionExpired(session: PreviewSession): boolean {
  const now = new Date();
  const expiresAt = getSessionExpirationTime(session);
  return now > expiresAt;
}

/**
 * Get remaining session time in seconds
 */
export function getRemainingSessionTime(session: PreviewSession): number {
  const now = new Date();
  const expiresAt = getSessionExpirationTime(session);
  const remainingMs = expiresAt.getTime() - now.getTime();
  return Math.max(0, Math.floor(remainingMs / 1000));
}

/**
 * Extend session expiration
 */
export function extendSession(
  session: PreviewSession,
  additionalDuration: number
): PreviewSession {
  const currentExpiresAt = getSessionExpirationTime(session);
  const newExpiresAt = new Date(currentExpiresAt.getTime() + additionalDuration * 1000);
  
  // Enforce maximum duration from creation
  const createdAt = new Date(session.createdAt);
  const maxExpiresAt = new Date(createdAt.getTime() + MAX_SESSION_DURATION * 1000);
  
  const finalExpiresAt = newExpiresAt > maxExpiresAt ? maxExpiresAt : newExpiresAt;

  const newSignature = generateSignature(
    session.sessionId,
    session.projectId,
    session.artifactId,
    session.userId,
    finalExpiresAt.toISOString()
  );

  return {
    ...session,
    expiresAt: finalExpiresAt.toISOString(),
    signature: newSignature,
  };
}

export default {
  createPreviewSession,
  validatePreviewSession,
  isResourceInScope,
  getSessionExpirationTime,
  isSessionExpired,
  getRemainingSessionTime,
  extendSession,
};
