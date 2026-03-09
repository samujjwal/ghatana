/**
 * Centralized Authentication System
 * 
 * Provides secure authentication with:
 * - JWT token validation and refresh
 * - Role-based access control
 * - Session management
 * - Multi-tenant support
 * - Security best practices
 */

import jwt from 'jsonwebtoken';
import crypto from 'crypto';
import { FastifyRequest, FastifyReply } from 'fastify';
import { getConfig } from '../config/config.js';
import { secretManager } from '../config/secrets.js';
import './types.js';

export interface User {
  id: string;
  email: string;
  tenantId: string;
  roles: Role[];
  permissions: Permission[];
  lastLogin?: Date;
  isActive: boolean;
}

export interface Role {
  id: string;
  name: string;
  permissions: Permission[];
}

export interface Permission {
  id: string;
  resource: string;
  action: string;
  conditions?: Record<string, any>;
}

export interface AuthToken {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  scope: string;
}

export interface AuthContext {
  user: User;
  token: string;
  permissions: Permission[];
  tenantId: string;
}

/**
 * JWT Token Manager
 */
export class JWTManager {
  private readonly config = getConfig();
  private readonly jwtSecret: string;

  constructor() {
    this.jwtSecret = this.config.JWT_SECRET;
  }

  /**
   * Generate access token
   */
  async generateAccessToken(user: User): Promise<string> {
    const payload = {
      sub: user.id,
      email: user.email,
      tenantId: user.tenantId,
      roles: user.roles.map(role => role.id),
      permissions: user.permissions.map(perm => ({
        resource: perm.resource,
        action: perm.action,
      })),
      type: 'access',
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + (15 * 60), // 15 minutes
      jti: crypto.randomUUID(),
    };

    return jwt.sign(payload, this.jwtSecret, {
      algorithm: 'HS256',
      issuer: 'tutorputor',
      audience: 'tutorputor-api',
    });
  }

  /**
   * Generate refresh token
   */
  async generateRefreshToken(user: User): Promise<string> {
    const payload = {
      sub: user.id,
      tenantId: user.tenantId,
      type: 'refresh',
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + (7 * 24 * 60 * 60), // 7 days
      jti: crypto.randomUUID(),
    };

    return jwt.sign(payload, this.jwtSecret, {
      algorithm: 'HS256',
      issuer: 'tutorputor',
      audience: 'tutorputor-api',
    });
  }

  /**
   * Validate and decode token
   */
  async validateToken(token: string): Promise<any> {
    try {
      const decoded = jwt.verify(token, this.jwtSecret, {
        algorithms: ['HS256'],
        issuer: 'tutorputor',
        audience: 'tutorputor-api',
      });

      return decoded;
    } catch (error) {
      if (error instanceof jwt.TokenExpiredError) {
        throw new Error('Token expired');
      } else if (error instanceof jwt.JsonWebTokenError) {
        throw new Error('Invalid token');
      } else {
        throw new Error('Token validation failed');
      }
    }
  }

  /**
   * Refresh access token
   */
  async refreshToken(refreshToken: string): Promise<AuthToken> {
    const decoded = await this.validateToken(refreshToken);
    
    if (decoded.type !== 'refresh') {
      throw new Error('Invalid token type for refresh');
    }

    // In a real implementation, fetch user from database
    const user = await this.getUserById(decoded.sub);
    
    if (!user || !user.isActive) {
      throw new Error('User not found or inactive');
    }

    const accessToken = await this.generateAccessToken(user);
    const newRefreshToken = await this.generateRefreshToken(user);

    return {
      accessToken,
      refreshToken: newRefreshToken,
      tokenType: 'Bearer',
      expiresIn: 15 * 60, // 15 minutes
      scope: 'read write',
    };
  }

  /**
   * Get user by ID (placeholder - would integrate with database)
   */
  private async getUserById(userId: string): Promise<User | null> {
    // This would integrate with your user service/database
    // For now, return a mock user
    return {
      id: userId,
      email: 'user@example.com',
      tenantId: 'default',
      roles: [],
      permissions: [],
      isActive: true,
    };
  }
}

/**
 * Role-Based Access Control (RBAC)
 */
export class RBACManager {
  private permissions = new Map<string, Permission>();
  private roles = new Map<string, Role>();

  constructor() {
    this.initializeDefaultPermissions();
    this.initializeDefaultRoles();
  }

  /**
   * Initialize default permissions
   */
  private initializeDefaultPermissions(): void {
    const defaultPermissions: Permission[] = [
      { id: 'user.read', resource: 'user', action: 'read' },
      { id: 'user.write', resource: 'user', action: 'write' },
      { id: 'user.delete', resource: 'user', action: 'delete' },
      { id: 'module.read', resource: 'module', action: 'read' },
      { id: 'module.write', resource: 'module', action: 'write' },
      { id: 'module.delete', resource: 'module', action: 'delete' },
      { id: 'assessment.read', resource: 'assessment', action: 'read' },
      { id: 'assessment.write', resource: 'assessment', action: 'write' },
      { id: 'simulation.read', resource: 'simulation', action: 'read' },
      { id: 'simulation.write', resource: 'simulation', action: 'write' },
      { id: 'admin.system', resource: 'system', action: 'admin' },
    ];

    defaultPermissions.forEach(perm => {
      this.permissions.set(perm.id, perm);
    });
  }

  /**
   * Initialize default roles
   */
  private initializeDefaultRoles(): void {
    const studentRole: Role = {
      id: 'student',
      name: 'Student',
      permissions: [
        this.permissions.get('module.read')!,
        this.permissions.get('assessment.read')!,
        this.permissions.get('simulation.read')!,
      ],
    };

    const instructorRole: Role = {
      id: 'instructor',
      name: 'Instructor',
      permissions: [
        this.permissions.get('module.read')!,
        this.permissions.get('module.write')!,
        this.permissions.get('assessment.read')!,
        this.permissions.get('assessment.write')!,
        this.permissions.get('simulation.read')!,
        this.permissions.get('simulation.write')!,
      ],
    };

    const adminRole: Role = {
      id: 'admin',
      name: 'Administrator',
      permissions: Array.from(this.permissions.values()),
    };

    this.roles.set('student', studentRole);
    this.roles.set('instructor', instructorRole);
    this.roles.set('admin', adminRole);
  }

  /**
   * Check if user has permission
   */
  hasPermission(user: User, resource: string, action: string, context?: Record<string, any>): boolean {
    const userPermissions = user.roles.flatMap(role => role.permissions).concat(user.permissions);
    
    return userPermissions.some(permission => {
      const matchesResource = permission.resource === resource || permission.resource === '*';
      const matchesAction = permission.action === action || permission.action === '*';
      
      if (!matchesResource || !matchesAction) {
        return false;
      }

      // Check conditions if present
      if (permission.conditions) {
        return this.evaluateConditions(permission.conditions, context);
      }

      return true;
    });
  }

  /**
   * Evaluate permission conditions
   */
  private evaluateConditions(conditions: Record<string, any>, context?: Record<string, any>): boolean {
    if (!context) return false;

    return Object.entries(conditions).every(([key, value]) => {
      return context[key] === value;
    });
  }

  /**
   * Get role by ID
   */
  getRole(roleId: string): Role | undefined {
    return this.roles.get(roleId);
  }

  /**
   * Get all permissions
   */
  getAllPermissions(): Permission[] {
    return Array.from(this.permissions.values());
  }
}

/**
 * Authentication Middleware
 */
export class AuthMiddleware {
  private readonly jwtManager = new JWTManager();
  private readonly rbacManager = new RBACManager();

  /**
   * Authentication middleware for Fastify
   */
  async authenticate(request: FastifyRequest, reply: FastifyReply): Promise<AuthContext> {
    const authHeader = request.headers.authorization;
    
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      reply.code(401).send({ error: 'Missing or invalid authorization header' });
      throw new Error('Authentication required');
    }

    const token = authHeader.substring(7);
    
    try {
      const decoded = await this.jwtManager.validateToken(token);
      
      if (decoded.type !== 'access') {
        reply.code(401).send({ error: 'Invalid token type' });
        throw new Error('Invalid token type');
      }

      // In a real implementation, fetch user from database
      const user = await this.getUserFromToken(decoded);
      
      if (!user || !user.isActive) {
        reply.code(401).send({ error: 'User not found or inactive' });
        throw new Error('User not found or inactive');
      }

      const authContext: AuthContext = {
        user,
        token,
        permissions: user.permissions,
        tenantId: user.tenantId,
      };

      // Add auth context to request
      request.authContext = authContext;
      
      return authContext;
    } catch (error) {
      reply.code(401).send({ error: 'Invalid or expired token' });
      throw error;
    }
  }

  /**
   * Authorization middleware
   */
  authorize(resource: string, action: string) {
    return async (request: FastifyRequest, reply: FastifyReply) => {
      const authContext = request.authContext;
      
      if (!authContext) {
        reply.code(401).send({ error: 'Authentication required' });
        throw new Error('Authentication required');
      }

      const hasPermission = this.rbacManager.hasPermission(
        authContext.user,
        resource,
        action,
        { tenantId: authContext.tenantId, request }
      );

      if (!hasPermission) {
        reply.code(403).send({ error: 'Insufficient permissions' });
        throw new Error('Insufficient permissions');
      }
    };
  }

  /**
   * Tenant isolation middleware
   */
  requireTenantAccess() {
    return async (request: FastifyRequest, reply: FastifyReply) => {
      const authContext = request.authContext;
      
      if (!authContext) {
        reply.code(401).send({ error: 'Authentication required' });
        throw new Error('Authentication required');
      }

      // Ensure user can only access their tenant data
      const requestParams = request.params as Record<string, any>;
      const requestTenantId = requestParams?.tenantId || request.headers['x-tenant-id'];
      
      if (requestTenantId && requestTenantId !== authContext.tenantId) {
        reply.code(403).send({ error: 'Tenant access denied' });
        throw new Error('Tenant access denied');
      }
    };
  }

  /**
   * Get user from token (placeholder)
   */
  private async getUserFromToken(decoded: any): Promise<User> {
    // This would integrate with your user service/database
    // For now, return a mock user based on token
    return {
      id: decoded.sub,
      email: decoded.email,
      tenantId: decoded.tenantId,
      roles: decoded.roles.map((roleId: string) => this.rbacManager.getRole(roleId)!).filter(Boolean),
      permissions: decoded.permissions.map((perm: any) => ({
        id: `${perm.resource}.${perm.action}`,
        resource: perm.resource,
        action: perm.action,
      })),
      isActive: true,
    };
  }
}

/**
 * Authentication Service
 */
export class AuthService {
  private readonly jwtManager = new JWTManager();
  private readonly rbacManager = new RBACManager();

  /**
   * User login
   */
  async login(email: string, password: string, tenantId: string): Promise<AuthToken> {
    // In a real implementation, validate credentials against database
    const user = await this.validateCredentials(email, password, tenantId);
    
    if (!user || !user.isActive) {
      throw new Error('Invalid credentials or user inactive');
    }

    const accessToken = await this.jwtManager.generateAccessToken(user);
    const refreshToken = await this.jwtManager.generateRefreshToken(user);

    // Update last login
    user.lastLogin = new Date();
    await this.updateUserLastLogin(user.id);

    return {
      accessToken,
      refreshToken,
      tokenType: 'Bearer',
      expiresIn: 15 * 60, // 15 minutes
      scope: 'read write',
    };
  }

  /**
   * User logout
   */
  async logout(refreshToken: string): Promise<void> {
    // In a real implementation, invalidate refresh token
    // This could involve adding it to a blacklist or removing it from database
  }

  /**
   * Refresh token
   */
  async refreshToken(refreshToken: string): Promise<AuthToken> {
    return this.jwtManager.refreshToken(refreshToken);
  }

  /**
   * Validate credentials (placeholder)
   */
  private async validateCredentials(email: string, password: string, tenantId: string): Promise<User | null> {
    // This would integrate with your user service/database
    // For now, return a mock user
    return {
      id: 'user-123',
      email,
      tenantId,
      roles: [this.rbacManager.getRole('student')!],
      permissions: [],
      isActive: true,
    };
  }

  /**
   * Update user last login (placeholder)
   */
  private async updateUserLastLogin(userId: string): Promise<void> {
    // This would update the user in the database
  }
}

// Export singleton instances
export const authMiddleware = new AuthMiddleware();
export const authService = new AuthService();
