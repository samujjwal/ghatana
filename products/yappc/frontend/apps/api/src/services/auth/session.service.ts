/**
 * Session Management Service
 *
 * Provides durable, database-backed session tracking for JWT refresh-token
 * rotation. Each active browser/device session has exactly one row in the
 * `UserSession` table.  When a refresh token is consumed, its session is
 * revoked and a new one is created atomically — preventing refresh-token
 * reuse attacks.
 *
 * @doc.type service
 * @doc.purpose Persistent refresh-token session management for JWT rotation
 * @doc.layer product
 * @doc.pattern Service Layer
 */

import crypto from 'crypto';
import { PrismaClient } from '../../generated/prisma';

// ============================================================================
// Types
// ============================================================================

export interface CreateSessionParams {
  userId: string;
  workspaceId?: string;
  expiresAt: Date;
}

export interface SessionRecord {
  id: string;
  userId: string;
  workspaceId: string | null;
  sessionToken: string;
  expiresAt: Date;
  revokedAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
}

// ============================================================================
// SessionService
// ============================================================================

export class SessionService {
  private readonly prisma: PrismaClient;

  constructor(prisma: PrismaClient) {
    this.prisma = prisma;
  }

  /**
   * Creates a new session and returns its opaque {@code sessionToken}.
   *
   * The returned token is 32 bytes of cryptographically-random data, encoded
   * as hex.  It is intended to be embedded as a claim in a signed refresh JWT
   * so that callers never need to store the raw token.
   *
   * @param params session creation parameters
   * @returns the opaque session token to embed in the refresh JWT
   */
  async create(params: CreateSessionParams): Promise<string> {
    const sessionToken = crypto.randomBytes(32).toString('hex');

    await this.prisma.userSession.create({
      data: {
        userId: params.userId,
        workspaceId: params.workspaceId ?? null,
        sessionToken,
        expiresAt: params.expiresAt,
      },
    });

    return sessionToken;
  }

  /**
   * Looks up a session by its opaque token.
   *
   * @param sessionToken the token embedded in the refresh JWT
   * @returns the session row, or {@code null} if not found
   */
  async findByToken(sessionToken: string): Promise<SessionRecord | null> {
    return this.prisma.userSession.findUnique({
      where: { sessionToken },
    });
  }

  /**
   * Validates that a session:
   * 1. exists,
   * 2. has not been revoked, and
   * 3. has not expired.
   *
   * @param sessionToken the token embedded in the refresh JWT
   * @returns the session row when valid
   * @throws {@link Error} with a descriptive message when invalid
   */
  async validateSession(sessionToken: string): Promise<SessionRecord> {
    const session = await this.findByToken(sessionToken);

    if (!session) {
      throw new Error('Session not found — refresh token is invalid');
    }
    if (session.revokedAt !== null) {
      throw new Error('Session has been revoked — please log in again');
    }
    if (session.expiresAt <= new Date()) {
      throw new Error('Session has expired — please log in again');
    }

    return session;
  }

  /**
   * Revokes a specific session, preventing the associated refresh token from
   * being used again.
   *
   * @param sessionToken the token embedded in the refresh JWT to revoke
   */
  async revoke(sessionToken: string): Promise<void> {
    await this.prisma.userSession.updateMany({
      where: { sessionToken, revokedAt: null },
      data: { revokedAt: new Date() },
    });
  }

  /**
   * Revokes ALL active sessions for a user (e.g., on security breach or
   * explicit "log out everywhere" action).
   *
   * @param userId the user whose sessions should all be revoked
   * @returns the number of sessions revoked
   */
  async revokeAll(userId: string): Promise<number> {
    const result = await this.prisma.userSession.updateMany({
      where: { userId, revokedAt: null },
      data: { revokedAt: new Date() },
    });
    return result.count;
  }

  /**
   * Deletes expired and revoked sessions older than 30 days.
   * Intended to be called from a background cleanup job.
   *
   * @returns the number of rows deleted
   */
  async cleanup(): Promise<number> {
    const cutoff = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);
    const result = await this.prisma.userSession.deleteMany({
      where: {
        OR: [
          { revokedAt: { lte: cutoff } },
          { expiresAt: { lte: cutoff } },
        ],
      },
    });
    return result.count;
  }
}
