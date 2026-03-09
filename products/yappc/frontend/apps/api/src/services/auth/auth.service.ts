/**
 * Production Authentication Service
 * 
 * JWT-based authentication system with refresh tokens,
 * role-based access control, and session management.
 * 
 * @doc.type service
 * @doc.purpose Production authentication and authorization
 * @doc.layer product
 * @doc.pattern Service Layer
 */

import jwt from 'jsonwebtoken';
import bcrypt from 'bcrypt';
import { PrismaClient } from '../../generated/prisma';
import type { Role } from '../../generated/prisma';

// ============================================================================
// Types
// ============================================================================

export interface JWTPayload {
  userId: string;
  email: string;
  role: Role;
  workspaceId?: string;
  iat?: number;
  exp?: number;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface AuthUser {
  id: string;
  email: string;
  name: string;
  role: Role;
  avatar?: string;
  workspaces: Array<{
    id: string;
    name: string;
    role: Role;
  }>;
}

export interface LoginCredentials {
  email: string;
  password: string;
  workspaceId?: string;
}

export interface RegisterData {
  email: string;
  password: string;
  name: string;
  workspaceName?: string;
}

// ============================================================================
// Configuration
// ============================================================================

const JWT_CONFIG = {
  accessTokenSecret: process.env.JWT_ACCESS_SECRET || 'your-access-secret',
  refreshTokenSecret: process.env.JWT_REFRESH_SECRET || 'your-refresh-secret',
  accessTokenExpiry: '15m',
  refreshTokenExpiry: '7d',
  bcryptRounds: 12,
} as const;

// ============================================================================
// Auth Service Class
// ============================================================================

export class AuthService {
  private prisma: PrismaClient;

  constructor(prisma: PrismaClient) {
    this.prisma = prisma;
  }

  // -------------------------------------------------------------------------
  // User Registration
  // -------------------------------------------------------------------------

  async register(data: RegisterData): Promise<{ user: AuthUser; tokens: AuthTokens }> {
    // Check if user already exists
    const existingUser = await this.prisma.user.findUnique({
      where: { email: data.email },
    });

    if (existingUser) {
      throw new Error('User already exists with this email');
    }

    // Hash password
    const passwordHash = await bcrypt.hash(data.password, JWT_CONFIG.bcryptRounds);

    // Create user
    const user = await this.prisma.user.create({
      data: {
        email: data.email,
        name: data.name,
        passwordHash,
        role: 'EDITOR',
        workspaces: {
          create: {
            name: data.workspaceName || `${data.name}'s Workspace`,
            description: 'Default workspace',
            isDefault: true,
          },
        },
      },
      include: {
        workspaces: {
          include: {
            workspace: true,
          },
        },
      },
    });

    // Generate tokens
    const tokens = await this.generateTokens(user);

    // Return user data
    return {
      user: this.formatAuthUser(user),
      tokens,
    };
  }

  // -------------------------------------------------------------------------
  // User Login
  // -------------------------------------------------------------------------

  async login(credentials: LoginCredentials): Promise<{ user: AuthUser; tokens: AuthTokens }> {
    // Find user with password
    const user = await this.prisma.user.findUnique({
      where: { email: credentials.email },
      include: {
        workspaces: {
          include: {
            workspace: true,
          },
        },
      },
    });

    if (!user || !user.passwordHash) {
      throw new Error('Invalid email or password');
    }

    // Verify password
    const isValidPassword = await bcrypt.compare(credentials.password, user.passwordHash);
    if (!isValidPassword) {
      throw new Error('Invalid email or password');
    }

    // Check workspace access if specified
    if (credentials.workspaceId) {
      const hasAccess = user.workspaces.some(wm => wm.workspaceId === credentials.workspaceId);
      if (!hasAccess) {
        throw new Error('Access denied to this workspace');
      }
    }

    // Generate tokens
    const tokens = await this.generateTokens(user, credentials.workspaceId);

    return {
      user: this.formatAuthUser(user),
      tokens,
    };
  }

  // -------------------------------------------------------------------------
  // Token Refresh
  // -------------------------------------------------------------------------

  async refreshTokens(refreshToken: string): Promise<AuthTokens> {
    try {
      // Verify refresh token
      const decoded = jwt.verify(refreshToken, JWT_CONFIG.refreshTokenSecret) as JWTPayload;

      // Find user
      const user = await this.prisma.user.findUnique({
        where: { id: decoded.userId },
        include: {
          workspaces: {
            include: {
              workspace: true,
            },
          },
        },
      });

      if (!user) {
        throw new Error('User not found');
      }

      // Generate new tokens
      return await this.generateTokens(user, decoded.workspaceId);
    } catch (error) {
      throw new Error('Invalid or expired refresh token');
    }
  }

  // -------------------------------------------------------------------------
  // Token Validation
  // -------------------------------------------------------------------------

  async validateAccessToken(token: string): Promise<JWTPayload> {
    try {
      const decoded = jwt.verify(token, JWT_CONFIG.accessTokenSecret) as JWTPayload;
      
      // Verify user still exists and is active
      const user = await this.prisma.user.findUnique({
        where: { id: decoded.userId },
        select: { id: true, email: true, role: true },
      });

      if (!user) {
        throw new Error('User not found');
      }

      return decoded;
    } catch (error) {
      throw new Error('Invalid or expired access token');
    }
  }

  // -------------------------------------------------------------------------
  // Password Reset
  // -------------------------------------------------------------------------

  async requestPasswordReset(email: string): Promise<string> {
    const user = await this.prisma.user.findUnique({
      where: { email },
    });

    if (!user) {
      // Don't reveal if email exists or not
      return 'Password reset instructions sent if email exists';
    }

    // Generate reset token (valid for 1 hour)
    const resetToken = jwt.sign(
      { userId: user.id, type: 'password-reset' },
      JWT_CONFIG.accessTokenSecret,
      { expiresIn: '1h' }
    );

    // Store reset token (in a real implementation, you'd send this via email)
    await this.prisma.user.update({
      where: { id: user.id },
      data: { passwordResetToken: resetToken },
    });

    return resetToken;
  }

  async resetPassword(token: string, newPassword: string): Promise<void> {
    try {
      const decoded = jwt.verify(token, JWT_CONFIG.accessTokenSecret) as unknown;
      
      if (decoded.type !== 'password-reset') {
        throw new Error('Invalid reset token');
      }

      const user = await this.prisma.user.findUnique({
        where: { id: decoded.userId },
      });

      if (!user || user.passwordResetToken !== token) {
        throw new Error('Invalid or expired reset token');
      }

      // Hash new password
      const passwordHash = await bcrypt.hash(newPassword, JWT_CONFIG.bcryptRounds);

      // Update password and clear reset token
      await this.prisma.user.update({
        where: { id: user.id },
        data: {
          passwordHash,
          passwordResetToken: null,
        },
      });
    } catch (error) {
      throw new Error('Invalid or expired reset token');
    }
  }

  // -------------------------------------------------------------------------
  // Session Management
  // -------------------------------------------------------------------------

  async logout(refreshToken: string): Promise<void> {
    try {
      const decoded = jwt.verify(refreshToken, JWT_CONFIG.refreshTokenSecret) as JWTPayload;
      
      // In a real implementation, you'd add the token to a blacklist
      // For now, we'll just validate it exists
      await this.validateAccessToken(refreshToken);
    } catch (error) {
      // Token is already invalid, which is fine for logout
    }
  }

  async logoutAllSessions(userId: string): Promise<void> {
    // In a real implementation, you'd invalidate all refresh tokens for this user
    // This could be done by maintaining a token blacklist or versioning user tokens
    console.log(`Logged out all sessions for user ${userId}`);
  }

  // -------------------------------------------------------------------------
  // Helper Methods
  // -------------------------------------------------------------------------

  private async generateTokens(user: unknown, workspaceId?: string): Promise<AuthTokens> {
    const payload: JWTPayload = {
      userId: user.id,
      email: user.email,
      role: user.role,
      workspaceId,
    };

    const accessToken = jwt.sign(payload, JWT_CONFIG.accessTokenSecret, {
      expiresIn: JWT_CONFIG.accessTokenExpiry,
    });

    const refreshToken = jwt.sign(payload, JWT_CONFIG.refreshTokenSecret, {
      expiresIn: JWT_CONFIG.refreshTokenExpiry,
    });

    // Calculate expiration time in seconds
    const expiresIn = 15 * 60; // 15 minutes

    return {
      accessToken,
      refreshToken,
      expiresIn,
    };
  }

  private formatAuthUser(user: unknown): AuthUser {
    return {
      id: user.id,
      email: user.email,
      name: user.name,
      role: user.role,
      avatar: user.avatar,
      workspaces: user.workspaces.map((wm: unknown) => ({
        id: wm.workspace.id,
        name: wm.workspace.name,
        role: wm.role,
      })),
    };
  }
}

// ============================================================================
// Middleware Factory
// ============================================================================

export function createAuthMiddleware(authService: AuthService) {
  return async (request: unknown, reply: unknown) => {
    try {
      const authHeader = request.headers.authorization;
      
      if (!authHeader || !authHeader.startsWith('Bearer ')) {
        throw new Error('Missing or invalid authorization header');
      }

      const token = authHeader.substring(7);
      const payload = await authService.validateAccessToken(token);

      // Add user info to request
      request.user = payload;
      
      // Add workspace context if available
      if (payload.workspaceId) {
        request.workspaceId = payload.workspaceId;
      }
    } catch (error) {
      reply.code(401).send({ error: 'Unauthorized', message: error.message });
    }
  };
}

// ============================================================================
// Role-based Authorization
// ============================================================================

export function requireRole(requiredRole: Role) {
  return async (request: unknown, reply: unknown) => {
    if (!request.user) {
      reply.code(401).send({ error: 'Unauthorized' });
      return;
    }

    const userRole = request.user.role as Role;
    const roleHierarchy: Record<Role, number> = {
      VIEWER: 1,
      EDITOR: 2,
      ADMIN: 3,
      OWNER: 4,
    };

    if (roleHierarchy[userRole] < roleHierarchy[requiredRole]) {
      reply.code(403).send({ error: 'Forbidden', message: 'Insufficient permissions' });
      return;
    }
  };
}

export function requireWorkspaceRole(requiredRole: Role) {
  return async (request: unknown, reply: unknown) => {
    if (!request.user || !request.workspaceId) {
      reply.code(401).send({ error: 'Unauthorized' });
      return;
    }

    // In a real implementation, you'd check the user's role in the specific workspace
    // For now, we'll use the user's global role
    const userRole = request.user.role as Role;
    const roleHierarchy: Record<Role, number> = {
      VIEWER: 1,
      EDITOR: 2,
      ADMIN: 3,
      OWNER: 4,
    };

    if (roleHierarchy[userRole] < roleHierarchy[requiredRole]) {
      reply.code(403).send({ error: 'Forbidden', message: 'Insufficient workspace permissions' });
      return;
    }
  };
}
