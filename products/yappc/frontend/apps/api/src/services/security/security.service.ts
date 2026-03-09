/**
 * Security Hardening Service
 * 
 * Comprehensive security implementation including:
 * - Data encryption at rest and in transit
 * - Role-based access control (RBAC)
 * - Audit logging and compliance
 * - PII detection and redaction
 * - Security monitoring and alerts
 * 
 * @doc.type service
 * @doc.purpose Production security implementation
 * @doc.layer product
 * @doc.pattern Service Layer
 */

import crypto from 'crypto';
import { PrismaClient } from '../../generated/prisma';
import type { User, Role, Workspace } from '../../generated/prisma';

// ============================================================================
// Configuration
// ============================================================================

const SECURITY_CONFIG = {
  encryption: {
    algorithm: 'aes-256-gcm',
    keyLength: 32,
    ivLength: 16,
    tagLength: 16,
    saltLength: 32,
  },
  audit: {
    logRetention: '90 days',
    sensitiveFields: ['password', 'token', 'key', 'secret'],
    piiFields: ['email', 'phone', 'ssn', 'creditCard'],
  },
  rbac: {
    defaultRole: 'VIEWER' as Role,
    adminRoles: ['ADMIN', 'OWNER'] as Role[],
    permissions: {
      workspace: {
        VIEWER: ['read'],
        EDITOR: ['read', 'write', 'create'],
        ADMIN: ['read', 'write', 'create', 'delete', 'manage_users'],
        OWNER: ['read', 'write', 'create', 'delete', 'manage_users', 'manage_workspace'],
      },
      system: {
        VIEWER: [],
        EDITOR: [],
        ADMIN: ['system_config', 'user_management', 'audit_logs'],
        OWNER: ['system_config', 'user_management', 'audit_logs', 'billing'],
      },
    },
  },
} as const;

// ============================================================================
// Types
// ============================================================================

export interface SecurityContext {
  userId: string;
  workspaceId?: string;
  role: Role;
  permissions: string[];
  sessionId: string;
  ipAddress: string;
  userAgent: string;
}

export interface AuditEvent {
  id: string;
  userId: string;
  workspaceId?: string;
  action: string;
  resource: string;
  resourceId?: string;
  outcome: 'success' | 'failure';
  details?: Record<string, unknown>;
  ipAddress: string;
  userAgent: string;
  timestamp: Date;
  riskScore: number;
}

export interface EncryptionResult {
  encrypted: string;
  iv: string;
  tag: string;
  salt: string;
}

export interface PIIResult {
  hasPII: boolean;
  redacted: string;
  detectedPII: Array<{
    type: string;
    value: string;
    position: number;
    confidence: number;
  }>;
  riskScore: number;
}

export interface SecurityAlert {
  id: string;
  type: 'brute_force' | 'suspicious_access' | 'data_breach' | 'privilege_escalation';
  severity: 'low' | 'medium' | 'high' | 'critical';
  userId?: string;
  workspaceId?: string;
  description: string;
  details: Record<string, unknown>;
  timestamp: Date;
  resolved: boolean;
}

// ============================================================================
// Security Service Class
// ============================================================================

export class SecurityService {
  private prisma: PrismaClient;
  private encryptionKey: Buffer;
  private auditEvents: AuditEvent[] = [];
  private securityAlerts: SecurityAlert[] = [];

  constructor(prisma: PrismaClient) {
    this.prisma = prisma;
    this.encryptionKey = this.getEncryptionKey();
  }

  // -------------------------------------------------------------------------
  // Encryption Services
  // -------------------------------------------------------------------------

  /**
   * Encrypt sensitive data at rest
   */
  encrypt(data: string): EncryptionResult {
    const salt = crypto.randomBytes(SECURITY_CONFIG.encryption.saltLength);
    const key = crypto.pbkdf2Sync(this.encryptionKey, salt, 100000, SECURITY_CONFIG.encryption.keyLength, 'sha256');
    const iv = crypto.randomBytes(SECURITY_CONFIG.encryption.ivLength);
    
    const cipher = crypto.createCipher(SECURITY_CONFIG.encryption.algorithm, key);
    cipher.setAAD(Buffer.from('additional-data'));
    
    let encrypted = cipher.update(data, 'utf8', 'hex');
    encrypted += cipher.final('hex');
    
    const tag = cipher.getAuthTag();
    
    return {
      encrypted,
      iv: iv.toString('hex'),
      tag: tag.toString('hex'),
      salt: salt.toString('hex'),
    };
  }

  /**
   * Decrypt sensitive data at rest
   */
  decrypt(encryptedData: EncryptionResult): string {
    const salt = Buffer.from(encryptedData.salt, 'hex');
    const key = crypto.pbkdf2Sync(this.encryptionKey, salt, 100000, SECURITY_CONFIG.encryption.keyLength, 'sha256');
    const iv = Buffer.from(encryptedData.iv, 'hex');
    const tag = Buffer.from(encryptedData.tag, 'hex');
    
    const decipher = crypto.createDecipher(SECURITY_CONFIG.encryption.algorithm, key);
    decipher.setAuthTag(tag);
    decipher.setAAD(Buffer.from('additional-data'));
    
    let decrypted = decipher.update(encryptedData.encrypted, 'hex', 'utf8');
    decrypted += decipher.final('utf8');
    
    return decrypted;
  }

  /**
   * Hash passwords securely
   */
  hashPassword(password: string, salt?: string): { hash: string; salt: string } {
    const passwordSalt = salt ? Buffer.from(salt, 'hex') : crypto.randomBytes(32);
    const hash = crypto.pbkdf2Sync(password, passwordSalt, 100000, 64, 'sha512');
    
    return {
      hash: hash.toString('hex'),
      salt: passwordSalt.toString('hex'),
    };
  }

  /**
   * Verify password hash
   */
  verifyPassword(password: string, hash: string, salt: string): boolean {
    const { hash: computedHash } = this.hashPassword(password, salt);
    return crypto.timingSafeEqual(Buffer.from(hash, 'hex'), Buffer.from(computedHash, 'hex'));
  }

  // -------------------------------------------------------------------------
  // PII Detection and Redaction
  // -------------------------------------------------------------------------

  /**
   * Detect and redact PII in text
   */
  detectAndRedactPII(text: string): PIIResult {
    const piiPatterns = [
      { type: 'email', pattern: /\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b/g, confidence: 0.9 },
      { type: 'phone', pattern: /\b\d{3}[-.]?\d{3}[-.]?\d{4}\b/g, confidence: 0.8 },
      { type: 'ssn', pattern: /\b\d{3}-\d{2}-\d{4}\b/g, confidence: 0.95 },
      { type: 'creditcard', pattern: /\b\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}\b/g, confidence: 0.9 },
      { type: 'ip', pattern: /\b(?:\d{1,3}\.){3}\d{1,3}\b/g, confidence: 0.7 },
      { type: 'apikey', pattern: /\b[A-Za-z0-9]{32,}\b/g, confidence: 0.6 },
    ];

    const detectedPII: PIIResult['detectedPII'] = [];
    let redactedText = text;
    let totalRiskScore = 0;

    for (const { type, pattern, confidence } of piiPatterns) {
      let match;
      while ((match = pattern.exec(text)) !== null) {
        detectedPII.push({
          type,
          value: match[0],
          position: match.index,
          confidence,
        });
        
        // Redact the PII
        const redaction = `[${type.toUpperCase()}_REDACTED]`;
        redactedText = redactedText.replace(match[0], redaction);
        
        totalRiskScore += confidence * 10;
      }
    }

    return {
      hasPII: detectedPII.length > 0,
      redacted: redactedText,
      detectedPII,
      riskScore: Math.min(totalRiskScore, 100),
    };
  }

  // -------------------------------------------------------------------------
  // Role-Based Access Control (RBAC)
  // -------------------------------------------------------------------------

  /**
   * Check if user has permission for specific action
   */
  hasPermission(
    context: SecurityContext,
    action: string,
    resource: string,
    resourceId?: string
  ): boolean {
    const permissions = SECURITY_CONFIG.rbac.permissions[resource]?.[context.role] || [];
    return permissions.includes(action);
  }

  /**
   * Get user permissions for workspace
   */
  async getUserPermissions(userId: string, workspaceId?: string): Promise<string[]> {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      include: {
        workspaces: workspaceId ? {
          where: { workspaceId },
          include: { workspace: true },
        } : {
          include: { workspace: true },
        },
      },
    });

    if (!user) {
      return [];
    }

    const workspaceRole = workspaceId
      ? user.workspaces.find(wm => wm.workspaceId === workspaceId)?.role
      : user.role;

    const permissions = SECURITY_CONFIG.rbac.permissions.workspace[workspaceRole || user.role] || [];
    return permissions;
  }

  /**
   * Create security context for user
   */
  async createSecurityContext(
    userId: string,
    workspaceId?: string,
    sessionId?: string,
    ipAddress?: string,
    userAgent?: string
  ): Promise<SecurityContext> {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: { role: true },
    });

    if (!user) {
      throw new Error('User not found');
    }

    const permissions = await this.getUserPermissions(userId, workspaceId);

    return {
      userId,
      workspaceId,
      role: user.role,
      permissions,
      sessionId: sessionId || crypto.randomUUID(),
      ipAddress: ipAddress || 'unknown',
      userAgent: userAgent || 'unknown',
    };
  }

  // -------------------------------------------------------------------------
  // Audit Logging
  // -------------------------------------------------------------------------

  /**
   * Log security event
   */
  async logEvent(
    context: SecurityContext,
    action: string,
    resource: string,
    resourceId?: string,
    outcome: 'success' | 'failure' = 'success',
    details?: Record<string, unknown>
  ): Promise<void> {
    const event: AuditEvent = {
      id: crypto.randomUUID(),
      userId: context.userId,
      workspaceId: context.workspaceId,
      action,
      resource,
      resourceId,
      outcome,
      details: this.sanitizeDetails(details),
      ipAddress: context.ipAddress,
      userAgent: context.userAgent,
      timestamp: new Date(),
      riskScore: this.calculateRiskScore(action, resource, outcome),
    };

    // Store in memory for immediate access
    this.auditEvents.push(event);

    // Store in database for persistence
    try {
      await this.prisma.auditLog.create({
        data: {
          id: event.id,
          userId: event.userId,
          workspaceId: event.workspaceId,
          action: event.action,
          resource: event.resource,
          resourceId: event.resourceId,
          outcome: event.outcome,
          details: event.details as unknown,
          ipAddress: event.ipAddress,
          userAgent: event.userAgent,
          riskScore: event.riskScore,
        },
      });
    } catch (error) {
      console.error('Failed to log audit event:', error);
    }

    // Check for security alerts
    await this.checkForSecurityAlerts(event);
  }

  /**
   * Get audit events for user or workspace
   */
  async getAuditEvents(
    userId?: string,
    workspaceId?: string,
    limit: number = 100,
    offset: number = 0
  ): Promise<AuditEvent[]> {
    const events = await this.prisma.auditLog.findMany({
      where: {
        ...(userId && { userId }),
        ...(workspaceId && { workspaceId }),
      },
      orderBy: { timestamp: 'desc' },
      take: limit,
      skip: offset,
    });

    return events.map(event => ({
      ...event,
      details: event.details as Record<string, unknown>,
    }));
  }

  // -------------------------------------------------------------------------
  // Security Monitoring and Alerts
  // -------------------------------------------------------------------------

  /**
   * Check for security alerts based on audit events
   */
  private async checkForSecurityAlerts(event: AuditEvent): Promise<void> {
    // Check for brute force attacks
    if (event.action === 'login' && event.outcome === 'failure') {
      const recentFailures = this.auditEvents.filter(
        e => e.action === 'login' && 
             e.outcome === 'failure' && 
             e.userId === event.userId &&
             e.timestamp > new Date(Date.now() - 15 * 60 * 1000) // Last 15 minutes
      );

      if (recentFailures.length >= 5) {
        await this.createSecurityAlert({
          type: 'brute_force',
          severity: 'high',
          userId: event.userId,
          description: `Multiple failed login attempts detected for user ${event.userId}`,
          details: {
            failureCount: recentFailures.length,
            timeWindow: '15 minutes',
            ipAddress: event.ipAddress,
          },
        });
      }
    }

    // Check for suspicious access patterns
    if (event.riskScore > 70) {
      await this.createSecurityAlert({
        type: 'suspicious_access',
        severity: 'medium',
        userId: event.userId,
        workspaceId: event.workspaceId,
        description: `High-risk activity detected: ${event.action} on ${event.resource}`,
        details: {
          riskScore: event.riskScore,
          action: event.action,
          resource: event.resource,
        },
      });
    }

    // Check for privilege escalation attempts
    if (event.action === 'role_change' && event.outcome === 'success') {
      await this.createSecurityAlert({
        type: 'privilege_escalation',
        severity: 'high',
        userId: event.userId,
        workspaceId: event.workspaceId,
        description: `Role change detected for user ${event.userId}`,
        details: event.details || {},
      });
    }
  }

  /**
   * Create security alert
   */
  private async createSecurityAlert(alertData: Omit<SecurityAlert, 'id' | 'timestamp' | 'resolved'>): Promise<void> {
    const alert: SecurityAlert = {
      id: crypto.randomUUID(),
      timestamp: new Date(),
      resolved: false,
      ...alertData,
    };

    this.securityAlerts.push(alert);

    // Store in database
    try {
      await this.prisma.securityAlert.create({
        data: alert,
      });
    } catch (error) {
      console.error('Failed to create security alert:', error);
    }

    // Send notification for critical alerts
    if (alert.severity === 'critical') {
      await this.sendSecurityNotification(alert);
    }
  }

  /**
   * Get active security alerts
   */
  async getSecurityAlerts(
    workspaceId?: string,
    resolved: boolean = false,
    limit: number = 50
  ): Promise<SecurityAlert[]> {
    const alerts = await this.prisma.securityAlert.findMany({
      where: {
        ...(workspaceId && { workspaceId }),
        resolved,
      },
      orderBy: { timestamp: 'desc' },
      take: limit,
    });

    return alerts;
  }

  /**
   * Resolve security alert
   */
  async resolveSecurityAlert(alertId: string, resolvedBy: string): Promise<void> {
    await this.prisma.securityAlert.update({
      where: { id: alertId },
      data: { 
        resolved: true,
        resolvedBy,
        resolvedAt: new Date(),
      },
    });

    // Update in-memory alerts
    const alert = this.securityAlerts.find(a => a.id === alertId);
    if (alert) {
      alert.resolved = true;
    }
  }

  // -------------------------------------------------------------------------
  // Helper Methods
  // -------------------------------------------------------------------------

  private getEncryptionKey(): Buffer {
    const key = process.env.ENCRYPTION_KEY;
    if (!key) {
      throw new Error('ENCRYPTION_KEY environment variable is required');
    }
    return Buffer.from(key, 'hex');
  }

  private sanitizeDetails(details?: Record<string, unknown>): Record<string, unknown> {
    if (!details) return {};

    const sanitized: Record<string, unknown> = {};
    
    for (const [key, value] of Object.entries(details)) {
      // Redact sensitive fields
      if (SECURITY_CONFIG.audit.sensitiveFields.some(field => 
        key.toLowerCase().includes(field.toLowerCase())
      )) {
        sanitized[key] = '[REDACTED]';
      } else if (typeof value === 'string') {
        // Check for PII in string values
        const piiResult = this.detectAndRedactPII(value);
        sanitized[key] = piiResult.redacted;
      } else {
        sanitized[key] = value;
      }
    }

    return sanitized;
  }

  private calculateRiskScore(action: string, resource: string, outcome: 'success' | 'failure'): number {
    let score = 0;

    // Base score for action
    const actionScores: Record<string, number> = {
      'login': 10,
      'logout': 5,
      'create': 15,
      'update': 20,
      'delete': 30,
      'role_change': 40,
      'export': 25,
      'import': 20,
    };

    score += actionScores[action] || 10;

    // Resource sensitivity
    const resourceScores: Record<string, number> = {
      'user': 25,
      'workspace': 20,
      'project': 15,
      'audit_log': 35,
      'security': 40,
    };

    score += resourceScores[resource] || 10;

    // Outcome modifier
    if (outcome === 'failure') {
      score *= 1.5;
    }

    return Math.min(score, 100);
  }

  private async sendSecurityNotification(alert: SecurityAlert): Promise<void> {
    // In production, send to Slack, email, or other notification systems
    console.error('CRITICAL SECURITY ALERT:', alert);
    
    // Store notification for admin dashboard
    try {
      await this.prisma.notification.create({
        data: {
          id: crypto.randomUUID(),
          userId: alert.userId,
          type: 'security_alert',
          title: `Security Alert: ${alert.type}`,
          message: alert.description,
          data: alert,
          priority: 'high',
          read: false,
        },
      });
    } catch (error) {
      console.error('Failed to send security notification:', error);
    }
  }
}

// ============================================================================
// Middleware Factory
// ============================================================================

export function createSecurityMiddleware(securityService: SecurityService) {
  return async (request: unknown, reply: unknown, done: unknown) => {
    try {
      // Create security context
      const context = await securityService.createSecurityContext(
        request.user?.userId,
        request.workspaceId,
        request.sessionId,
        request.ip,
        request.headers['user-agent']
      );

      // Add security context to request
      request.securityContext = context;

      // Log access
      await securityService.logEvent(
        context,
        `${request.method} ${request.url}`,
        'api_endpoint',
        undefined,
        'success'
      );
    } catch (error) {
      console.error('Security middleware error:', error);
    }

    done();
  };
}

// ============================================================================
// Authorization Decorators
// ============================================================================

export function requirePermission(action: string, resource: string) {
  return async (request: unknown, reply: unknown) => {
    if (!request.securityContext) {
      reply.code(401).send({ error: 'Unauthorized' });
      return;
    }

    const hasPermission = request.securityContext.permissions.includes(action);
    
    if (!hasPermission) {
      await request.securityContext.logEvent(
        request.securityContext,
        action,
        resource,
        undefined,
        'failure',
        { reason: 'insufficient_permissions' }
      );
      
      reply.code(403).send({ error: 'Forbidden', message: 'Insufficient permissions' });
      return;
    }
  };
}

export function requireRole(requiredRole: Role) {
  return async (request: unknown, reply: unknown) => {
    if (!request.securityContext) {
      reply.code(401).send({ error: 'Unauthorized' });
      return;
    }

    const userRole = request.securityContext.role;
    const roleHierarchy: Record<Role, number> = {
      VIEWER: 1,
      EDITOR: 2,
      ADMIN: 3,
      OWNER: 4,
    };

    if (roleHierarchy[userRole] < roleHierarchy[requiredRole]) {
      reply.code(403).send({ error: 'Forbidden', message: 'Insufficient role permissions' });
      return;
    }
  };
}
