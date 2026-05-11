/**
 * Authentication API Routes
 *
 * Proxies all authentication to the canonical Java lifecycle service.
 * The Node.js API is no longer the auth authority - it delegates to Java.
 *
 * @doc.type route
 * @doc.purpose Authentication API endpoints (proxy to Java service)
 * @doc.layer api
 * @doc.pattern REST API / Proxy
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { proxyAuthService } from '../services/auth/proxy-auth.service';
import type { AuthUser } from '../services/auth/auth.service';
import type { JWTUserPayload } from '../middleware/auth.middleware';
import { getErrorMessage, isRecord } from '../utils/type-guards';

// Use proxy auth service that delegates to Java lifecycle service
const authService = proxyAuthService;

type LoginRequestBody = {
  email: string;
  password: string;
};

type RefreshRequestBody = {
  refreshToken: string;
};

type AuthenticatedRequest = FastifyRequest & {
  user?: JWTUserPayload | AuthUser;
};

function toAuthenticatedUser(user: AuthUser): JWTUserPayload {
  return {
    userId: user.id,
    email: user.email,
    role: user.role,
  };
}

// ============================================================================
// Authentication Routes
// ============================================================================

export async function authRoutes(fastify: FastifyInstance) {
  // Cookie plugin is registered in index.ts at startup
  // This ensures httpOnly cookies are always available

  // Login endpoint
  fastify.post(
    '/auth/login',
    {
      schema: {
        body: {
          type: 'object',
          required: ['email', 'password'],
          properties: {
            email: { type: 'string', format: 'email' },
            password: { type: 'string' },
          },
        },
        response: {
          200: {
            type: 'object',
            properties: {
              user: {
                type: 'object',
                properties: {
                  id: { type: 'string' },
                  email: { type: 'string' },
                  name: { type: 'string' },
                  role: { type: 'string' },
                },
              },
            },
            required: ['user'],
          },
        },
      },
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      try {
        const { email, password } = request.body as LoginRequestBody;

        const result = await authService.login({
          email,
          password,
        });

        // Set httpOnly cookies - cookie plugin is always available
        const isProduction = process.env.NODE_ENV === 'production';
        
        reply.setCookie('accessToken', result.tokens.accessToken, {
          httpOnly: true,
          secure: isProduction,
          sameSite: 'strict',
          path: '/',
          maxAge: result.tokens.expiresIn * 1000,
        });
        
        reply.setCookie('refreshToken', result.tokens.refreshToken, {
          httpOnly: true,
          secure: isProduction,
          sameSite: 'strict',
          path: '/api/auth/refresh',
          maxAge: 30 * 24 * 60 * 60 * 1000, // 30 days
        });
        
        // Return user info only - tokens are in httpOnly cookies
        reply.send({ user: result.user });
      } catch (error: unknown) {
        reply.code(401).send({
          error: 'Authentication failed',
          message:
            error instanceof Error ? error.message : 'Authentication failed',
        });
      }
    }
  );

  // Refresh token endpoint
  fastify.post(
    '/auth/refresh',
    {
      schema: {
        body: {
          type: 'object',
          properties: {
            refreshToken: { type: 'string' },
          },
        },
      },
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      try {
        // Try to get refresh token from cookie first
        let refreshToken: string | undefined;
        if (request.cookies && typeof request.cookies === 'object') {
          refreshToken = (request.cookies as Record<string, string>).refreshToken;
        }
        
        // Fall back to body if cookie not available
        if (!refreshToken) {
          const body = request.body as RefreshRequestBody;
          refreshToken = body.refreshToken;
        }
        
        if (!refreshToken) {
          reply.code(401).send({
            error: 'Token refresh failed',
            message: 'No refresh token provided',
          });
          return;
        }

        const result = await authService.refreshTokens(refreshToken);

        // Set new cookies - cookie plugin is always available
        const isProduction = process.env.NODE_ENV === 'production';
        
        reply.setCookie('accessToken', result.accessToken, {
          httpOnly: true,
          secure: isProduction,
          sameSite: 'strict',
          path: '/',
          maxAge: result.expiresIn * 1000,
        });
        
        reply.setCookie('refreshToken', result.refreshToken, {
          httpOnly: true,
          secure: isProduction,
          sameSite: 'strict',
          path: '/api/auth/refresh',
          maxAge: 30 * 24 * 60 * 60 * 1000,
        });
        
        // Return only non-sensitive session metadata - tokens are in httpOnly cookies
        reply.send({ 
          expiresAt: new Date(Date.now() + result.expiresIn * 1000).toISOString(),
          authMode: 'COOKIE',
        });
      } catch (error: unknown) {
        reply.code(401).send({
          error: 'Token refresh failed',
          message:
            error instanceof Error ? error.message : 'Token refresh failed',
        });
      }
    }
  );

  // Logout endpoint
  fastify.post(
    '/auth/logout',
    {
      schema: {
        body: {
          type: 'object',
          properties: {
            refreshToken: { type: 'string' },
          },
        },
      },
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      try {
        // Try to get refresh token from cookie first
        let refreshToken: string | undefined;
        if (request.cookies && typeof request.cookies === 'object') {
          refreshToken = (request.cookies as Record<string, string>).refreshToken;
        }
        
        // Fall back to body if cookie not available
        if (!refreshToken) {
          const body = request.body as RefreshRequestBody;
          refreshToken = body.refreshToken;
        }

        if (refreshToken) {
          await authService.logout(refreshToken);
        }

        // Clear cookies - cookie plugin is always available
        reply.clearCookie('accessToken', { path: '/' });
        reply.clearCookie('refreshToken', { path: '/api/auth/refresh' });

        reply.send({ message: 'Logged out successfully' });
      } catch (error: unknown) {
        // Always clear cookies even if logout fails
        reply.clearCookie('accessToken', { path: '/' });
        reply.clearCookie('refreshToken', { path: '/api/auth/refresh' });
        
        reply.code(400).send({
          error: 'Logout failed',
          message: getErrorMessage(error),
        });
      }
    }
  );

  // Get current user
  fastify.get(
    '/auth/me',
    {
      preHandler: [authenticateToken],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      try {
        const user = await authService.getCurrentUser(
          (request as AuthenticatedRequest).user?.userId ?? ''
        );

        reply.send(user);
      } catch (error: unknown) {
        reply.code(404).send({
          error: 'User not found',
          message: getErrorMessage(error),
        });
      }
    }
  );

  // P2-6: Onboarding Status Endpoint
  fastify.get(
    '/auth/onboarding-status',
    {
      preHandler: [authenticateToken],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      try {
        const userId = (request as AuthenticatedRequest).user?.userId;
        if (!userId) {
          return reply.code(401).send({
            error: 'Unauthorized',
            message: 'User not authenticated',
          });
        }

        // Get user profile
        const user = await authService.getCurrentUser(userId);

        // Get user's workspaces
        const workspaces = user.workspaces || [];

        // Determine onboarding status based on user actions
        const hasCompletedProfile = !!(
          user.name &&
          user.name.trim().length > 0 &&
          user.email &&
          user.email.length > 0
        );

        const hasCreatedWorkspace = workspaces.length > 0;

        const projectCheck = await checkUserHasProjects(userId);
        const hasCreatedProject = projectCheck.hasProjects;

        const memberCheck = await checkUserHasInvitedMembers(
          userId,
          workspaces.map((w) => w.id)
        );
        const hasInvitedTeamMember = memberCheck.hasInvited;

        // Track degraded status for observability
        const degraded = projectCheck.degraded || memberCheck.degraded;
        if (degraded) {
          console.error(JSON.stringify({
            event: 'onboarding_check_degraded',
            userId,
            projectCheck: projectCheck.degraded ? projectCheck.degradedReason : null,
            memberCheck: memberCheck.degraded ? memberCheck.degradedReason : null,
          }));
        }

        const onboardingStatus = {
          userId,
          completed: hasCompletedProfile && hasCreatedWorkspace && hasCreatedProject,
          degraded,
          degradedReason: degraded ? 'dependency_lookup_failed' : undefined,
          steps: {
            profile: {
              completed: hasCompletedProfile,
              label: 'Complete your profile',
            },
            workspace: {
              completed: hasCreatedWorkspace,
              label: 'Create a workspace',
            },
            project: {
              completed: hasCreatedProject,
              label: 'Create your first project',
            },
            invite: {
              completed: hasInvitedTeamMember,
              label: 'Invite a team member (optional)',
              optional: true,
            },
          },
          progress: {
            total: 4,
            completed: [
              hasCompletedProfile,
              hasCreatedWorkspace,
              hasCreatedProject,
              hasInvitedTeamMember,
            ].filter(Boolean).length,
            percentage: Math.round(
              ([
                hasCompletedProfile,
                hasCreatedWorkspace,
                hasCreatedProject,
                hasInvitedTeamMember,
              ].filter(Boolean).length /
                4) *
                100
            ),
          },
          workspaces: workspaces.map((w) => ({
            id: w.id,
            name: w.name,
            role: w.role,
          })),
          canSkip: true, // Users can skip onboarding if they want
          lastUpdated: new Date().toISOString(),
        };

        reply.send(onboardingStatus);
      } catch (error: unknown) {
        reply.code(500).send({
          error: 'Failed to get onboarding status',
          message: getErrorMessage(error),
        });
      }
    }
  );

}

// ============================================================================
// Onboarding Helper Functions
// ============================================================================

async function checkUserHasProjects(userId: string): Promise<{ hasProjects: boolean; degraded?: boolean; degradedReason?: string }> {
  try {
    const prisma = await import('../database/client.js').then(m => m.getPrismaClient());
    
    // Check if user has created any projects
    const projectCount = await prisma.project.count({
      where: {
        createdById: userId,
      },
    });
    
    return { hasProjects: projectCount > 0 };
  } catch (error) {
    // Use structured logging - log error with context
    const errorMessage = error instanceof Error ? error.message : String(error);
    console.error(JSON.stringify({ 
      error: errorMessage, 
      userId, 
      event: 'check_user_projects_failed' 
    }));
    
    // Return degraded status instead of silently returning false
    return { 
      hasProjects: false, 
      degraded: true, 
      degradedReason: 'project_lookup_failed' 
    };
  }
}

async function checkUserHasInvitedMembers(
  userId: string,
  workspaceIds: string[]
): Promise<{ hasInvited: boolean; degraded?: boolean; degradedReason?: string }> {
  try {
    if (workspaceIds.length === 0) {
      return { hasInvited: false };
    }
    
    const prisma = await import('../database/client.js').then(m => m.getPrismaClient());
    
    // Check if user has added any members to their workspaces
    // Count members in the user's workspaces (excluding the user themselves)
    const memberCount = await prisma.workspaceMember.count({
      where: {
        workspaceId: {
          in: workspaceIds,
        },
        userId: {
          not: userId, // Exclude the user themselves
        },
      },
    });
    
    return { hasInvited: memberCount > 0 };
  } catch (error) {
    // Use structured logging - log error with context
    const errorMessage = error instanceof Error ? error.message : String(error);
    console.error(JSON.stringify({ 
      error: errorMessage, 
      userId, 
      workspaceIds, 
      event: 'check_invited_members_failed' 
    }));
    
    // Return degraded status instead of silently returning false
    return { 
      hasInvited: false, 
      degraded: true, 
      degradedReason: 'member_lookup_failed' 
    };
  }
}

// ============================================================================
// Authentication Middleware
// ============================================================================

export async function authenticateToken(
  request: FastifyRequest,
  reply: FastifyReply
) {
  try {
    let token: string | undefined;

    // Try to get token from cookie first
    if (request.cookies && typeof request.cookies === 'object') {
      token = (request.cookies as Record<string, string>).accessToken;
    }

    // Fall back to Authorization header
    if (!token) {
      const authHeader = request.headers.authorization;
      if (authHeader && authHeader.startsWith('Bearer ')) {
        token = authHeader.substring(7);
      }
    }

    if (!token) {
      return reply.code(401).send({
        error: 'Authentication required',
        message: 'No token provided',
      });
    }

    const user = await authService.validateAccessToken(token);

    (request as AuthenticatedRequest).user = toAuthenticatedUser(user);
  } catch (error: unknown) {
    return reply.code(401).send({
      error: 'Invalid token',
      message: error instanceof Error ? error.message : 'Invalid token',
    });
  }
}

export async function requireRole(role: string) {
  return async (request: FastifyRequest, reply: FastifyReply) => {
    try {
      const user = (request as AuthenticatedRequest).user;

      if (!user || !hasRole(user.role, role)) {
        return reply.code(403).send({
          error: 'Insufficient permissions',
          message: `Role ${role} required`,
        });
      }
    } catch (error: unknown) {
      return reply.code(403).send({
        error: 'Authorization failed',
        message: getErrorMessage(error),
      });
    }
  };
}

// ============================================================================
// Helper Functions
// ============================================================================

function hasRole(userRole: string, requiredRole: string): boolean {
  const roleHierarchy = {
    VIEWER: 1,
    EDITOR: 2,
    ADMIN: 3,
    OWNER: 4,
  };

  const userLevel = roleHierarchy[userRole as keyof typeof roleHierarchy] || 0;
  const requiredLevel =
    roleHierarchy[requiredRole as keyof typeof roleHierarchy] || 0;

  return userLevel >= requiredLevel;
}

// ============================================================================
// Schema Definitions
// ============================================================================

export const authSchemas = {
  User: {
    type: 'object',
    properties: {
      id: { type: 'string' },
      email: { type: 'string' },
      name: { type: 'string' },
      role: { type: 'string', enum: ['VIEWER', 'EDITOR', 'ADMIN', 'OWNER'] },
      avatar: { type: 'string' },
      workspaces: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            id: { type: 'string' },
            name: { type: 'string' },
            role: { type: 'string' },
          },
        },
      },
    },
  },
  AuthTokens: {
    type: 'object',
    properties: {
      accessToken: { type: 'string' },
      refreshToken: { type: 'string' },
      expiresIn: { type: 'number' },
    },
  },
};
