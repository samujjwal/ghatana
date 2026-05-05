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
  sessionId: string;
  projectId: string;
  artifactId: string;
  userId: string;
  createdAt: string;
  expiresAt: string;
  scope: PreviewSessionScope;
  signature: string;
}

export interface PreviewSessionScope {
  allowedArtifactIds?: string[];
  allowedProjectIds?: string[];
  readOnly?: boolean;
  allowDownload?: boolean;
  allowClipboard?: boolean;
  maxDuration?: number;
}

export interface PreviewSessionFixtureOptions {
  projectId: string;
  artifactId: string;
  userId: string;
  scope?: PreviewSessionScope;
  duration?: number;
  signature?: string;
}

const DEFAULT_SESSION_DURATION = 3600;
const MAX_SESSION_DURATION = 86400;

function generateSessionId(): string {
  return `preview_${Date.now()}_${Math.random().toString(36).slice(2, 15)}`;
}

function normalizeScope(
  projectId: string,
  artifactId: string,
  scope: PreviewSessionScope = {},
  maxDuration: number
): PreviewSessionScope {
  return {
    readOnly: scope.readOnly ?? true,
    allowDownload: scope.allowDownload ?? false,
    allowClipboard: scope.allowClipboard ?? false,
    maxDuration,
    allowedProjectIds: sortUnique(scope.allowedProjectIds ?? [projectId]),
    allowedArtifactIds: sortUnique(scope.allowedArtifactIds ?? [artifactId]),
  };
}

function sortUnique(values: string[]): string[] {
  return [...new Set(values)].sort();
}

/**
 * Builds a local preview-session fixture for tests.
 * Production code must obtain preview sessions from the server API.
 */
export function createPreviewSessionFixture(
  options: PreviewSessionFixtureOptions,
): PreviewSession {
  const {
    projectId,
    artifactId,
    userId,
    scope = {},
    duration = DEFAULT_SESSION_DURATION,
    signature = '',
  } = options;
  const actualDuration = Math.min(duration, MAX_SESSION_DURATION);
  const now = new Date();
  const createdAt = now.toISOString();
  const expiresAt = new Date(now.getTime() + actualDuration * 1000).toISOString();
  const normalizedScope = normalizeScope(projectId, artifactId, scope, actualDuration);

  const sessionWithoutSignature = {
    sessionId: generateSessionId(),
    projectId,
    artifactId,
    userId,
    createdAt,
    expiresAt,
    scope: normalizedScope,
  };

  return {
    ...sessionWithoutSignature,
    signature,
  };
}

export function validatePreviewSession(
  session: PreviewSession
): { valid: boolean; reason?: string } {
  const createdAt = new Date(session.createdAt);
  const expiresAt = new Date(session.expiresAt);

  if (Number.isNaN(createdAt.getTime()) || Number.isNaN(expiresAt.getTime())) {
    return { valid: false, reason: 'Invalid session timestamps' };
  }

  if (expiresAt <= createdAt) {
    return { valid: false, reason: 'Session expiration must be after creation' };
  }

  const actualDurationSeconds = Math.floor((expiresAt.getTime() - createdAt.getTime()) / 1000);
  if (actualDurationSeconds > MAX_SESSION_DURATION) {
    return { valid: false, reason: 'Session exceeds maximum duration' };
  }

  if (new Date() > expiresAt) {
    return { valid: false, reason: 'Session expired' };
  }

  if (!session.signature || session.signature.trim().length === 0) {
    return { valid: false, reason: 'Missing server-issued signature' };
  }

  normalizeScope(
    session.projectId,
    session.artifactId,
    session.scope,
    session.scope.maxDuration ?? actualDurationSeconds
  );

  return { valid: true };
}

export function isResourceInScope(
  session: PreviewSession,
  projectId: string,
  artifactId: string
): boolean {
  if (session.projectId !== projectId || session.artifactId !== artifactId) {
    return false;
  }

  const { scope } = session;
  if (scope.allowedProjectIds && !scope.allowedProjectIds.includes(projectId)) {
    return false;
  }
  if (scope.allowedArtifactIds && !scope.allowedArtifactIds.includes(artifactId)) {
    return false;
  }
  return true;
}

export function getSessionExpirationTime(session: PreviewSession): Date {
  return new Date(session.expiresAt);
}

export function isSessionExpired(session: PreviewSession): boolean {
  return new Date() > getSessionExpirationTime(session);
}

export function getRemainingSessionTime(session: PreviewSession): number {
  const remainingMs = getSessionExpirationTime(session).getTime() - Date.now();
  return Math.max(0, Math.floor(remainingMs / 1000));
}

export function extendSession(
  session: PreviewSession,
  additionalDuration: number
): PreviewSession {
  const createdAt = new Date(session.createdAt);
  const currentExpiresAt = getSessionExpirationTime(session);
  const maxExpiresAt = new Date(createdAt.getTime() + MAX_SESSION_DURATION * 1000);
  const requestedExpiresAt = new Date(currentExpiresAt.getTime() + additionalDuration * 1000);
  const finalExpiresAt = requestedExpiresAt > maxExpiresAt ? maxExpiresAt : requestedExpiresAt;
  const updatedScope = normalizeScope(
    session.projectId,
    session.artifactId,
    session.scope,
    Math.floor((finalExpiresAt.getTime() - createdAt.getTime()) / 1000)
  );

  const updatedSession = {
    ...session,
    expiresAt: finalExpiresAt.toISOString(),
    scope: updatedScope,
  };

  return {
    ...updatedSession,
    signature: session.signature,
  };
}

export default {
  validatePreviewSession,
  isResourceInScope,
  getSessionExpirationTime,
  isSessionExpired,
  getRemainingSessionTime,
  extendSession,
};
