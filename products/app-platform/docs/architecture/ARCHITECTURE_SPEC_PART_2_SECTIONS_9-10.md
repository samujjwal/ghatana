# Architecture & Design Documentation Suite for Project Siddhanta
## Part 2: Sections 9-10

**Document Version:** 2.1  
**Date:** March 5, 2026  
**Status:** Implementation-Ready (Post-ARB Remediation)  
**Change Log:** v2.1 adds post-quantum cryptography roadmap, incident notification integration (R-02), secrets rotation automation (K-14), and zero-trust service mesh hardening

---

## Table of Contents - Part 2 (Sections 9-10)
9. [Security Architecture](#9-security-architecture)
10. [Observability Architecture](#10-observability-architecture)

---

## 9. Security Architecture

### 9.1 Overview

The Security Architecture implements a **zero-trust security model** with defense-in-depth principles, ensuring:
- End-to-end encryption
- Multi-factor authentication
- Role-based access control (RBAC)
- API security and rate limiting
- Data privacy and GDPR compliance
- Security monitoring and incident response
- Penetration testing and vulnerability management

### 9.2 Security Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    Perimeter Security                        │
│  (WAF, DDoS Protection, IP Whitelisting)                    │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Identity & Access Management              │
│  (OAuth 2.0, JWT, MFA, SSO)                                 │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    API Security                              │
│  (Rate Limiting, API Keys, Request Validation)              │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Application Security                      │
│  (Input Validation, CSRF Protection, XSS Prevention)        │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Network Security                          │
│  (mTLS, Service Mesh, Network Policies)                     │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Data Security                             │
│  (Encryption at Rest, Encryption in Transit, Masking)       │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Infrastructure Security                   │
│  (Secrets Management, Container Security, Compliance)       │
└─────────────────────────────────────────────────────────────┘
```

### 9.3 Authentication & Authorization

**OAuth 2.0 + JWT Implementation**:
```typescript
import jwt from 'jsonwebtoken';
import bcrypt from 'bcrypt';
import { v4 as uuidv4 } from 'uuid';

interface User {
  userId: string;
  username: string;
  email: string;
  roles: string[];
  permissions: string[];
}

interface TokenPayload {
  userId: string;
  username: string;
  email: string;
  roles: string[];
  permissions: string[];
  iat: number;
  exp: number;
  jti: string;
}

class AuthenticationService {
  private jwtSecret: string;
  private jwtRefreshSecret: string;
  private accessTokenExpiry = '15m';
  private refreshTokenExpiry = '7d';
  
  constructor(jwtSecret: string, jwtRefreshSecret: string) {
    this.jwtSecret = jwtSecret;
    this.jwtRefreshSecret = jwtRefreshSecret;
  }
  
  async authenticate(
    username: string,
    password: string,
    mfaCode?: string
  ): Promise<{ accessToken: string; refreshToken: string }> {
    // Step 1: Validate credentials
    const user = await this.validateCredentials(username, password);
    
    if (!user) {
      throw new AuthenticationError('Invalid credentials');
    }
    
    // Step 2: Verify MFA if enabled
    if (user.mfaEnabled) {
      if (!mfaCode) {
        throw new MFARequiredError('MFA code required');
      }
      
      const mfaValid = await this.verifyMFA(user.userId, mfaCode);
      if (!mfaValid) {
        throw new AuthenticationError('Invalid MFA code');
      }
    }
    
    // Step 3: Generate tokens
    const accessToken = this.generateAccessToken(user);
    const refreshToken = this.generateRefreshToken(user);
    
    // Step 4: Store refresh token
    await this.storeRefreshToken(user.userId, refreshToken);
    
    // Step 5: Log authentication event
    await this.logAuthEvent(user.userId, 'LOGIN_SUCCESS');
    
    return { accessToken, refreshToken };
  }
  
  private async validateCredentials(
    username: string,
    password: string
  ): Promise<User | null> {
    const user = await this.userRepository.findByUsername(username);
    
    if (!user) {
      return null;
    }
    
    const passwordValid = await bcrypt.compare(password, user.passwordHash);
    
    if (!passwordValid) {
      await this.logAuthEvent(user.userId, 'LOGIN_FAILED');
      return null;
    }
    
    return user;
  }
  
  private generateAccessToken(user: User): string {
    const payload: TokenPayload = {
      userId: user.userId,
      username: user.username,
      email: user.email,
      roles: user.roles,
      permissions: user.permissions,
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 15 * 60, // 15 minutes
      jti: uuidv4()
    };
    
    return jwt.sign(payload, this.jwtSecret, {
      algorithm: 'HS256'
    });
  }
  
  private generateRefreshToken(user: User): string {
    const payload = {
      userId: user.userId,
      jti: uuidv4()
    };
    
    return jwt.sign(payload, this.jwtRefreshSecret, {
      algorithm: 'HS256',
      expiresIn: this.refreshTokenExpiry
    });
  }
  
  async refreshAccessToken(refreshToken: string): Promise<string> {
    try {
      // Verify refresh token
      const decoded = jwt.verify(refreshToken, this.jwtRefreshSecret) as any;
      
      // Check if token is revoked
      const isRevoked = await this.isTokenRevoked(decoded.jti);
      if (isRevoked) {
        throw new AuthenticationError('Token has been revoked');
      }
      
      // Get user
      const user = await this.userRepository.findById(decoded.userId);
      if (!user) {
        throw new AuthenticationError('User not found');
      }
      
      // Generate new access token
      return this.generateAccessToken(user);
      
    } catch (error) {
      throw new AuthenticationError('Invalid refresh token');
    }
  }
  
  async verifyToken(token: string): Promise<TokenPayload> {
    try {
      const decoded = jwt.verify(token, this.jwtSecret) as TokenPayload;
      
      // Check if token is revoked
      const isRevoked = await this.isTokenRevoked(decoded.jti);
      if (isRevoked) {
        throw new AuthenticationError('Token has been revoked');
      }
      
      return decoded;
      
    } catch (error) {
      throw new AuthenticationError('Invalid token');
    }
  }
  
  async revokeToken(jti: string): Promise<void> {
    await this.redis.set(`revoked:${jti}`, '1', 'EX', 24 * 60 * 60);
  }
  
  private async isTokenRevoked(jti: string): Promise<boolean> {
    const revoked = await this.redis.get(`revoked:${jti}`);
    return revoked !== null;
  }
}
```

**Role-Based Access Control (RBAC)**:
```typescript
interface Permission {
  resource: string;
  action: string;
  conditions?: Record<string, any>;
}

interface Role {
  roleId: string;
  roleName: string;
  permissions: Permission[];
  inherits?: string[];
}

class AuthorizationService {
  private roles: Map<string, Role> = new Map();
  
  async loadRoles(): Promise<void> {
    // Load roles from database
    const roles = await this.roleRepository.findAll();
    
    for (const role of roles) {
      this.roles.set(role.roleId, role);
    }
  }
  
  async authorize(
    user: User,
    resource: string,
    action: string,
    context?: Record<string, any>
  ): Promise<boolean> {
    // Check if user has direct permission
    for (const permission of user.permissions) {
      if (this.matchesPermission(permission, resource, action, context)) {
        return true;
      }
    }
    
    // Check role-based permissions
    for (const roleName of user.roles) {
      const role = this.roles.get(roleName);
      if (!role) continue;
      
      // Check role permissions
      for (const permission of role.permissions) {
        if (this.matchesPermission(permission, resource, action, context)) {
          return true;
        }
      }
      
      // Check inherited roles
      if (role.inherits) {
        for (const inheritedRoleName of role.inherits) {
          const inheritedRole = this.roles.get(inheritedRoleName);
          if (inheritedRole) {
            for (const permission of inheritedRole.permissions) {
              if (this.matchesPermission(permission, resource, action, context)) {
                return true;
              }
            }
          }
        }
      }
    }
    
    return false;
  }
  
  private matchesPermission(
    permission: Permission,
    resource: string,
    action: string,
    context?: Record<string, any>
  ): boolean {
    // Check resource match (supports wildcards)
    if (!this.matchesPattern(permission.resource, resource)) {
      return false;
    }
    
    // Check action match
    if (!this.matchesPattern(permission.action, action)) {
      return false;
    }
    
    // Check conditions
    if (permission.conditions && context) {
      for (const [key, value] of Object.entries(permission.conditions)) {
        if (context[key] !== value) {
          return false;
        }
      }
    }
    
    return true;
  }
  
  private matchesPattern(pattern: string, value: string): boolean {
    if (pattern === '*') return true;
    if (pattern === value) return true;
    
    // Support wildcard patterns like "order:*"
    if (pattern.endsWith('*')) {
      const prefix = pattern.slice(0, -1);
      return value.startsWith(prefix);
    }
    
    return false;
  }
}

// Authorization middleware
const authorize = (resource: string, action: string) => {
  return async (req: Request, res: Response, next: NextFunction) => {
    const user = req.user; // Set by authentication middleware
    
    if (!user) {
      return res.status(401).json({ error: 'Unauthorized' });
    }
    
    const authorized = await authorizationService.authorize(
      user,
      resource,
      action,
      { clientId: req.params.clientId }
    );
    
    if (!authorized) {
      logger.warn('Authorization failed', {
        userId: user.userId,
        resource,
        action
      });
      return res.status(403).json({ error: 'Forbidden' });
    }
    
    next();
  };
};

// Usage in routes
app.post('/api/orders', 
  authenticate,
  authorize('order', 'create'),
  orderController.createOrder
);
```

### 9.4 Multi-Factor Authentication (MFA)

**TOTP-based MFA**:
```typescript
import speakeasy from 'speakeasy';
import QRCode from 'qrcode';

class MFAService {
  async enableMFA(userId: string): Promise<{ secret: string; qrCode: string }> {
    // Generate secret
    const secret = speakeasy.generateSecret({
      name: `Siddhanta (${userId})`,
      issuer: 'Siddhanta'
    });
    
    // Generate QR code
    const qrCode = await QRCode.toDataURL(secret.otpauth_url);
    
    // Store secret (encrypted)
    await this.storeMFASecret(userId, secret.base32);
    
    return {
      secret: secret.base32,
      qrCode
    };
  }
  
  async verifyMFA(userId: string, token: string): Promise<boolean> {
    const secret = await this.getMFASecret(userId);
    
    if (!secret) {
      return false;
    }
    
    return speakeasy.totp.verify({
      secret,
      encoding: 'base32',
      token,
      window: 2 // Allow 2 time steps before/after
    });
  }
  
  async generateBackupCodes(userId: string): Promise<string[]> {
    const codes: string[] = [];
    
    for (let i = 0; i < 10; i++) {
      const code = this.generateRandomCode(8);
      codes.push(code);
    }
    
    // Store hashed backup codes
    const hashedCodes = await Promise.all(
      codes.map(code => bcrypt.hash(code, 10))
    );
    
    await this.storeBackupCodes(userId, hashedCodes);
    
    return codes;
  }
  
  async verifyBackupCode(userId: string, code: string): Promise<boolean> {
    const hashedCodes = await this.getBackupCodes(userId);
    
    for (let i = 0; i < hashedCodes.length; i++) {
      const valid = await bcrypt.compare(code, hashedCodes[i]);
      
      if (valid) {
        // Remove used backup code
        await this.removeBackupCode(userId, i);
        return true;
      }
    }
    
    return false;
  }
  
  private generateRandomCode(length: number): string {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    let code = '';
    
    for (let i = 0; i < length; i++) {
      code += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    
    return code;
  }
}
```

### 9.5 Data Encryption

**Encryption at Rest**:
```typescript
import crypto from 'crypto';

class EncryptionService {
  private algorithm = 'aes-256-gcm';
  private keyLength = 32;
  private ivLength = 16;
  private tagLength = 16;
  
  constructor(private masterKey: string) {}
  
  encrypt(plaintext: string): string {
    // Generate random IV
    const iv = crypto.randomBytes(this.ivLength);
    
    // Create cipher
    const cipher = crypto.createCipheriv(
      this.algorithm,
      Buffer.from(this.masterKey, 'hex'),
      iv
    );
    
    // Encrypt
    let encrypted = cipher.update(plaintext, 'utf8', 'hex');
    encrypted += cipher.final('hex');
    
    // Get auth tag
    const tag = cipher.getAuthTag();
    
    // Combine IV + encrypted + tag
    return iv.toString('hex') + encrypted + tag.toString('hex');
  }
  
  decrypt(ciphertext: string): string {
    // Extract IV, encrypted data, and tag
    const iv = Buffer.from(ciphertext.slice(0, this.ivLength * 2), 'hex');
    const tag = Buffer.from(ciphertext.slice(-this.tagLength * 2), 'hex');
    const encrypted = ciphertext.slice(
      this.ivLength * 2,
      -this.tagLength * 2
    );
    
    // Create decipher
    const decipher = crypto.createDecipheriv(
      this.algorithm,
      Buffer.from(this.masterKey, 'hex'),
      iv
    );
    
    decipher.setAuthTag(tag);
    
    // Decrypt
    let decrypted = decipher.update(encrypted, 'hex', 'utf8');
    decrypted += decipher.final('utf8');
    
    return decrypted;
  }
  
  encryptField(data: any, fields: string[]): any {
    const encrypted = { ...data };
    
    for (const field of fields) {
      if (encrypted[field]) {
        encrypted[field] = this.encrypt(encrypted[field]);
      }
    }
    
    return encrypted;
  }
  
  decryptField(data: any, fields: string[]): any {
    const decrypted = { ...data };
    
    for (const field of fields) {
      if (decrypted[field]) {
        decrypted[field] = this.decrypt(decrypted[field]);
      }
    }
    
    return decrypted;
  }
}

// Database encryption example
class SecureClientRepository {
  private sensitiveFields = ['pan', 'email', 'phone', 'bankAccount'];
  
  async save(client: Client): Promise<void> {
    // Encrypt sensitive fields
    const encrypted = encryptionService.encryptField(
      client,
      this.sensitiveFields
    );
    
    await this.db.query(`
      INSERT INTO clients (client_id, client_name, pan, email, phone)
      VALUES ($1, $2, $3, $4, $5)
    `, [
      encrypted.clientId,
      client.clientName,
      encrypted.pan,
      encrypted.email,
      encrypted.phone
    ]);
  }
  
  async findById(clientId: string): Promise<Client> {
    const result = await this.db.query(`
      SELECT * FROM clients WHERE client_id = $1
    `, [clientId]);
    
    if (result.rows.length === 0) {
      throw new ClientNotFoundError(clientId);
    }
    
    // Decrypt sensitive fields
    return encryptionService.decryptField(
      result.rows[0],
      this.sensitiveFields
    );
  }
}
```

**Encryption in Transit (mTLS)**:
```yaml
# Istio PeerAuthentication for mTLS
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: trading
spec:
  mtls:
    mode: STRICT
---
# DestinationRule for mTLS
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: default
  namespace: trading
spec:
  host: "*.trading.svc.cluster.local"
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
```

### 9.6 API Security

**Rate Limiting**:
```typescript
import rateLimit from 'express-rate-limit';
import RedisStore from 'rate-limit-redis';

// Global rate limiter
const globalLimiter = rateLimit({
  store: new RedisStore({
    client: redisClient,
    prefix: 'rl:global:'
  }),
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 1000, // 1000 requests per window
  message: 'Too many requests, please try again later',
  standardHeaders: true,
  legacyHeaders: false
});

// API-specific rate limiter
const apiLimiter = rateLimit({
  store: new RedisStore({
    client: redisClient,
    prefix: 'rl:api:'
  }),
  windowMs: 60 * 1000, // 1 minute
  max: 100, // 100 requests per minute
  keyGenerator: (req) => {
    // Use user ID for authenticated requests
    return req.user?.userId || req.ip;
  }
});

// Order placement rate limiter (stricter)
const orderLimiter = rateLimit({
  store: new RedisStore({
    client: redisClient,
    prefix: 'rl:order:'
  }),
  windowMs: 60 * 1000, // 1 minute
  max: 10, // 10 orders per minute
  keyGenerator: (req) => req.user.userId,
  handler: (req, res) => {
    logger.warn('Order rate limit exceeded', {
      userId: req.user.userId,
      ip: req.ip
    });
    res.status(429).json({
      error: 'Order rate limit exceeded',
      retryAfter: 60
    });
  }
});

app.use('/api', globalLimiter);
app.use('/api', apiLimiter);
app.post('/api/orders', orderLimiter, orderController.createOrder);
```

**Input Validation**:
```typescript
import Joi from 'joi';

// Request validation schemas
const orderSchema = Joi.object({
  clientId: Joi.string().uuid().required(),
  instrumentId: Joi.string().uuid().required(),
  side: Joi.string().valid('BUY', 'SELL').required(),
  quantity: Joi.number().positive().required(),
  price: Joi.number().positive().when('orderType', {
    is: Joi.string().valid('LIMIT', 'STOP_LIMIT'),
    then: Joi.required(),
    otherwise: Joi.optional()
  }),
  orderType: Joi.string().valid('MARKET', 'LIMIT', 'STOP', 'STOP_LIMIT').required(),
  timeInForce: Joi.string().valid('DAY', 'GTC', 'IOC', 'FOK').default('DAY')
});

// Validation middleware
const validate = (schema: Joi.Schema) => {
  return (req: Request, res: Response, next: NextFunction) => {
    const { error, value } = schema.validate(req.body, {
      abortEarly: false,
      stripUnknown: true
    });
    
    if (error) {
      const errors = error.details.map(detail => ({
        field: detail.path.join('.'),
        message: detail.message
      }));
      
      return res.status(400).json({
        error: 'Validation failed',
        details: errors
      });
    }
    
    req.body = value;
    next();
  };
};

app.post('/api/orders',
  authenticate,
  validate(orderSchema),
  authorize('order', 'create'),
  orderController.createOrder
);
```

### 9.7 Security Monitoring

**Intrusion Detection**:
```typescript
class SecurityMonitor {
  private suspiciousPatterns = [
    { pattern: /(\bSELECT\b.*\bFROM\b)/i, type: 'SQL_INJECTION' },
    { pattern: /<script[^>]*>.*<\/script>/i, type: 'XSS' },
    { pattern: /\.\.\//g, type: 'PATH_TRAVERSAL' },
    { pattern: /eval\s*\(/i, type: 'CODE_INJECTION' }
  ];
  
  async analyzeRequest(req: Request): Promise<SecurityThreat[]> {
    const threats: SecurityThreat[] = [];
    
    // Check URL parameters
    for (const [key, value] of Object.entries(req.query)) {
      const threat = this.detectThreat(value as string);
      if (threat) {
        threats.push({ ...threat, location: `query.${key}` });
      }
    }
    
    // Check request body
    if (req.body) {
      const bodyStr = JSON.stringify(req.body);
      const threat = this.detectThreat(bodyStr);
      if (threat) {
        threats.push({ ...threat, location: 'body' });
      }
    }
    
    // Check headers
    for (const [key, value] of Object.entries(req.headers)) {
      const threat = this.detectThreat(value as string);
      if (threat) {
        threats.push({ ...threat, location: `header.${key}` });
      }
    }
    
    if (threats.length > 0) {
      await this.logSecurityEvent({
        type: 'SUSPICIOUS_REQUEST',
        userId: req.user?.userId,
        ip: req.ip,
        path: req.path,
        threats
      });
    }
    
    return threats;
  }
  
  private detectThreat(input: string): SecurityThreat | null {
    for (const { pattern, type } of this.suspiciousPatterns) {
      if (pattern.test(input)) {
        return { type, pattern: pattern.toString() };
      }
    }
    return null;
  }
  
  async detectAnomalousActivity(userId: string): Promise<boolean> {
    // Get user's recent activity
    const recentActivity = await this.getRecentActivity(userId, 3600); // Last hour
    
    // Check for anomalies
    const anomalies = [];
    
    // Multiple failed login attempts
    const failedLogins = recentActivity.filter(
      a => a.type === 'LOGIN_FAILED'
    ).length;
    
    if (failedLogins > 5) {
      anomalies.push('MULTIPLE_FAILED_LOGINS');
    }
    
    // Unusual geographic location
    const locations = recentActivity.map(a => a.location);
    const uniqueLocations = new Set(locations);
    
    if (uniqueLocations.size > 3) {
      anomalies.push('MULTIPLE_LOCATIONS');
    }
    
    // High volume of requests
    if (recentActivity.length > 1000) {
      anomalies.push('HIGH_REQUEST_VOLUME');
    }
    
    if (anomalies.length > 0) {
      await this.logSecurityEvent({
        type: 'ANOMALOUS_ACTIVITY',
        userId,
        anomalies
      });
      
      return true;
    }
    
    return false;
  }
}
```

### 9.8 Compliance & Audit

**Audit Logging**:
```typescript
interface AuditLog {
  auditId: string;
  timestamp: Date;
  timestampBs: string;              // Bikram Sambat via K-15 Dual-Calendar Service
  userId: string;
  action: string;
  resource: string;
  resourceId: string;
  tenantId: string;                 // RLS tenant isolation
  changes?: Record<string, any>;
  ipAddress: string;
  userAgent: string;
  result: 'SUCCESS' | 'FAILURE';
  errorMessage?: string;
}

class AuditService {
  private calendarService: DualCalendarService; // K-15

  async log(entry: Omit<AuditLog, 'auditId' | 'timestamp' | 'timestampBs'>): Promise<void> {
    const now = new Date();
    const auditLog: AuditLog = {
      auditId: uuidv4(),
      timestamp: now,
      timestampBs: await this.calendarService.toBs(now),
      ...entry
    };
    
    // Store in database
    await this.db.query(`
      INSERT INTO audit_logs (
        audit_id, timestamp, timestamp_bs, tenant_id, user_id, action, resource,
        resource_id, changes, ip_address, user_agent, result, error_message
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
    `, [
      auditLog.auditId,
      auditLog.timestamp,
      auditLog.timestampBs,
      auditLog.tenantId,
      auditLog.userId,
      auditLog.action,
      auditLog.resource,
      auditLog.resourceId,
      JSON.stringify(auditLog.changes),
      auditLog.ipAddress,
      auditLog.userAgent,
      auditLog.result,
      auditLog.errorMessage
    ]);
    
    // Also send to OpenSearch for search/analytics
    await this.opensearch.index({
      index: 'audit-logs',
      body: auditLog
    });
  }
  
  async search(criteria: AuditSearchCriteria): Promise<AuditLog[]> {
    const query = {
      bool: {
        must: []
      }
    };
    
    if (criteria.userId) {
      query.bool.must.push({ term: { userId: criteria.userId } });
    }
    
    if (criteria.action) {
      query.bool.must.push({ term: { action: criteria.action } });
    }
    
    if (criteria.resource) {
      query.bool.must.push({ term: { resource: criteria.resource } });
    }
    
    if (criteria.fromDate || criteria.toDate) {
      query.bool.must.push({
        range: {
          timestamp: {
            gte: criteria.fromDate,
            lte: criteria.toDate
          }
        }
      });
    }
    
    const result = await this.opensearch.search({
      index: 'audit-logs',
      body: { query }
    });
    
    return result.hits.hits.map(hit => hit._source);
  }
}

// Audit middleware
const audit = (action: string, resource: string) => {
  return async (req: Request, res: Response, next: NextFunction) => {
    const originalJson = res.json.bind(res);
    
    res.json = function(body: any) {
      // Log after response
      setImmediate(async () => {
        await auditService.log({
          userId: req.user?.userId || 'anonymous',
          action,
          resource,
          resourceId: req.params.id || body.id,
          changes: req.body,
          ipAddress: req.ip,
          userAgent: req.get('user-agent'),
          result: res.statusCode < 400 ? 'SUCCESS' : 'FAILURE',
          errorMessage: res.statusCode >= 400 ? body.error : undefined
        });
      });
      
      return originalJson(body);
    };
    
    next();
  };
};

app.post('/api/orders',
  authenticate,
  authorize('order', 'create'),
  audit('CREATE_ORDER', 'order'),
  orderController.createOrder
);
```

---

## 10. Observability Architecture

### 10.1 Overview

The Observability Architecture provides **comprehensive visibility** into system behavior through:
- Metrics collection and monitoring
- Distributed tracing
- Centralized logging
- Alerting and incident management
- Performance profiling
- Business metrics dashboards

### 10.2 Three Pillars of Observability

```
┌─────────────────────────────────────────────────────────────┐
│                         METRICS                              │
│  (Prometheus, Grafana - System & Business Metrics)          │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                         TRACES                               │
│  (Jaeger, OpenTelemetry - Distributed Tracing)              │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                          LOGS                                │
│  (OpenSearch Stack - Centralized Logging)                          │
└─────────────────────────────────────────────────────────────┘
```

### 10.3 Metrics Collection

**Prometheus Metrics**:
```typescript
import { Registry, Counter, Histogram, Gauge } from 'prom-client';

class MetricsService {
  private register: Registry;
  
  // Request metrics
  private httpRequestsTotal: Counter;
  private httpRequestDuration: Histogram;
  
  // Business metrics
  private ordersPlaced: Counter;
  private orderValue: Histogram;
  private activePositions: Gauge;
  
  // System metrics
  private dbConnectionPool: Gauge;
  private cacheHitRate: Gauge;
  
  constructor() {
    this.register = new Registry();
    
    // HTTP request counter
    this.httpRequestsTotal = new Counter({
      name: 'http_requests_total',
      help: 'Total number of HTTP requests',
      labelNames: ['method', 'path', 'status'],
      registers: [this.register]
    });
    
    // HTTP request duration
    this.httpRequestDuration = new Histogram({
      name: 'http_request_duration_seconds',
      help: 'HTTP request duration in seconds',
      labelNames: ['method', 'path', 'status'],
      buckets: [0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1, 5],
      registers: [this.register]
    });
    
    // Orders placed counter
    this.ordersPlaced = new Counter({
      name: 'orders_placed_total',
      help: 'Total number of orders placed',
      labelNames: ['side', 'order_type', 'status'],
      registers: [this.register]
    });
    
    // Order value histogram
    this.orderValue = new Histogram({
      name: 'order_value_inr',
      help: 'Order value in INR',
      labelNames: ['side', 'order_type'],
      buckets: [1000, 10000, 50000, 100000, 500000, 1000000, 5000000],
      registers: [this.register]
    });
    
    // Active positions gauge
    this.activePositions = new Gauge({
      name: 'active_positions_total',
      help: 'Total number of active positions',
      labelNames: ['exchange'],
      registers: [this.register]
    });
    
    // Database connection pool
    this.dbConnectionPool = new Gauge({
      name: 'db_connection_pool_size',
      help: 'Database connection pool size',
      labelNames: ['state'],
      registers: [this.register]
    });
    
    // Cache hit rate
    this.cacheHitRate = new Gauge({
      name: 'cache_hit_rate',
      help: 'Cache hit rate percentage',
      registers: [this.register]
    });
  }
  
  recordHttpRequest(method: string, path: string, status: number, duration: number) {
    this.httpRequestsTotal.inc({ method, path, status: status.toString() });
    this.httpRequestDuration.observe(
      { method, path, status: status.toString() },
      duration
    );
  }
  
  recordOrderPlaced(side: string, orderType: string, status: string, value: number) {
    this.ordersPlaced.inc({ side, order_type: orderType, status });
    this.orderValue.observe({ side, order_type: orderType }, value);
  }
  
  updateActivePositions(exchange: string, count: number) {
    this.activePositions.set({ exchange }, count);
  }
  
  updateDbConnectionPool(active: number, idle: number) {
    this.dbConnectionPool.set({ state: 'active' }, active);
    this.dbConnectionPool.set({ state: 'idle' }, idle);
  }
  
  async getMetrics(): Promise<string> {
    return this.register.metrics();
  }
}

// Metrics middleware
const metricsMiddleware = (req: Request, res: Response, next: NextFunction) => {
  const start = Date.now();
  
  res.on('finish', () => {
    const duration = (Date.now() - start) / 1000;
    metricsService.recordHttpRequest(
      req.method,
      req.route?.path || req.path,
      res.statusCode,
      duration
    );
  });
  
  next();
};

// Metrics endpoint
app.get('/metrics', async (req, res) => {
  res.set('Content-Type', 'text/plain');
  res.send(await metricsService.getMetrics());
});
```

**Prometheus Configuration**:
```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s
  external_labels:
    cluster: 'siddhanta-prod'
    environment: 'production'

scrape_configs:
  - job_name: 'kubernetes-pods'
    kubernetes_sd_configs:
      - role: pod
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: true
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
        action: replace
        target_label: __metrics_path__
        regex: (.+)
      - source_labels: [__address__, __meta_kubernetes_pod_annotation_prometheus_io_port]
        action: replace
        regex: ([^:]+)(?::\d+)?;(\d+)
        replacement: $1:$2
        target_label: __address__
      - action: labelmap
        regex: __meta_kubernetes_pod_label_(.+)
      - source_labels: [__meta_kubernetes_namespace]
        action: replace
        target_label: kubernetes_namespace
      - source_labels: [__meta_kubernetes_pod_name]
        action: replace
        target_label: kubernetes_pod_name

  - job_name: 'order-service'
    static_configs:
      - targets: ['order-service:9090']
        labels:
          service: 'order-service'

alerting:
  alertmanagers:
    - static_configs:
        - targets: ['alertmanager:9093']

rule_files:
  - '/etc/prometheus/rules/*.yml'
```

**Alert Rules**:
```yaml
# alert-rules.yml
groups:
  - name: trading_alerts
    interval: 30s
    rules:
      - alert: HighOrderLatency
        expr: histogram_quantile(0.99, http_request_duration_seconds_bucket{path="/api/orders"}) > 0.012
        for: 2m
        labels:
          severity: critical  # 12ms e2e SLA per LLD D-01 NFR
          team: trading
        annotations:
          summary: "High order placement latency"
          description: "99th percentile order latency is {{ $value }}s"
      
      - alert: OrderServiceDown
        expr: up{job="order-service"} == 0
        for: 1m
        labels:
          severity: critical
          team: trading
        annotations:
          summary: "Order service is down"
          description: "Order service has been down for more than 1 minute"
      
      - alert: HighErrorRate
        expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High error rate detected"
          description: "Error rate is {{ $value }} errors/sec"
      
      - alert: DatabaseConnectionPoolExhausted
        expr: db_connection_pool_size{state="idle"} < 2
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Database connection pool nearly exhausted"
          description: "Only {{ $value }} idle connections remaining"
```

### 10.4 Distributed Tracing

**OpenTelemetry Integration**:
```typescript
import { NodeTracerProvider } from '@opentelemetry/sdk-trace-node';
import { registerInstrumentations } from '@opentelemetry/instrumentation';
import { HttpInstrumentation } from '@opentelemetry/instrumentation-http';
import { ExpressInstrumentation } from '@opentelemetry/instrumentation-express';
import { JaegerExporter } from '@opentelemetry/exporter-jaeger';
import { Resource } from '@opentelemetry/resources';
import { SemanticResourceAttributes } from '@opentelemetry/semantic-conventions';

class TracingService {
  private tracer: Tracer;
  
  initialize(serviceName: string) {
    const provider = new NodeTracerProvider({
      resource: new Resource({
        [SemanticResourceAttributes.SERVICE_NAME]: serviceName,
        [SemanticResourceAttributes.SERVICE_VERSION]: process.env.VERSION || '1.0.0',
        [SemanticResourceAttributes.DEPLOYMENT_ENVIRONMENT]: process.env.NODE_ENV || 'development'
      })
    });
    
    // Configure Jaeger exporter
    const exporter = new JaegerExporter({
      endpoint: process.env.JAEGER_ENDPOINT || 'http://jaeger:14268/api/traces'
    });
    
    provider.addSpanProcessor(new BatchSpanProcessor(exporter));
    provider.register();
    
    // Auto-instrument HTTP and Express
    registerInstrumentations({
      instrumentations: [
        new HttpInstrumentation(),
        new ExpressInstrumentation()
      ]
    });
    
    this.tracer = provider.getTracer(serviceName);
  }
  
  async traceOperation<T>(
    operationName: string,
    operation: (span: Span) => Promise<T>,
    attributes?: Record<string, any>
  ): Promise<T> {
    return this.tracer.startActiveSpan(operationName, async (span) => {
      try {
        // Add attributes
        if (attributes) {
          for (const [key, value] of Object.entries(attributes)) {
            span.setAttribute(key, value);
          }
        }
        
        const result = await operation(span);
        
        span.setStatus({ code: SpanStatusCode.OK });
        return result;
        
      } catch (error) {
        span.setStatus({
          code: SpanStatusCode.ERROR,
          message: error.message
        });
        span.recordException(error);
        throw error;
        
      } finally {
        span.end();
      }
    });
  }
}

// Usage example
class OrderService {
  async placeOrder(orderRequest: OrderRequest): Promise<OrderResponse> {
    return tracingService.traceOperation(
      'placeOrder',
      async (span) => {
        span.setAttribute('order.client_id', orderRequest.clientId);
        span.setAttribute('order.instrument_id', orderRequest.instrumentId);
        span.setAttribute('order.side', orderRequest.side);
        
        // Validate order
        await tracingService.traceOperation('validateOrder', async () => {
          return this.validateOrder(orderRequest);
        });
        
        // Check risk
        await tracingService.traceOperation('checkRisk', async () => {
          return this.checkRisk(orderRequest);
        });
        
        // Submit to exchange
        const response = await tracingService.traceOperation('submitToExchange', async () => {
          return this.submitToExchange(orderRequest);
        });
        
        return response;
      },
      {
        'service.name': 'order-service',
        'order.type': orderRequest.orderType
      }
    );
  }
}
```

### 10.5 Centralized Logging

**Structured Logging with Winston**:
```typescript
import winston from 'winston';
import { Client } from '@opensearch-project/opensearch';

const opensearchClient = new Client({
  node: process.env.OPENSEARCH_URL || 'http://opensearch:9200'
});

class LoggingService {
  private logger: winston.Logger;
  
  initialize(serviceName: string) {
    const osTransport = new winston.transports.Stream({
      stream: opensearchClient.helpers.bulk({
        index: 'logs',
        body: []
      })
    });
    
    this.logger = winston.createLogger({
      level: process.env.LOG_LEVEL || 'info',
      format: winston.format.combine(
        winston.format.timestamp(),
        winston.format.errors({ stack: true }),
        winston.format.json()
      ),
      defaultMeta: {
        service: serviceName,
        environment: process.env.NODE_ENV,
        version: process.env.VERSION
      },
      transports: [
        new winston.transports.Console({
          format: winston.format.combine(
            winston.format.colorize(),
            winston.format.simple()
          )
        }),
        esTransport
      ]
    });
  }
  
  info(message: string, meta?: Record<string, any>) {
    this.logger.info(message, meta);
  }
  
  error(message: string, error?: Error, meta?: Record<string, any>) {
    this.logger.error(message, {
      ...meta,
      error: error ? {
        message: error.message,
        stack: error.stack,
        name: error.name
      } : undefined
    });
  }
  
  warn(message: string, meta?: Record<string, any>) {
    this.logger.warn(message, meta);
  }
  
  debug(message: string, meta?: Record<string, any>) {
    this.logger.debug(message, meta);
  }
}

// Request logging middleware
const requestLogger = (req: Request, res: Response, next: NextFunction) => {
  const start = Date.now();
  
  res.on('finish', () => {
    const duration = Date.now() - start;
    
    logger.info('HTTP Request', {
      method: req.method,
      path: req.path,
      status: res.statusCode,
      duration,
      userId: req.user?.userId,
      ip: req.ip,
      userAgent: req.get('user-agent'),
      correlationId: req.get('x-correlation-id')
    });
  });
  
  next();
};
```

### 10.6 Grafana Dashboards

**Dashboard Configuration**:
```json
{
  "dashboard": {
    "title": "Order Service Dashboard",
    "panels": [
      {
        "title": "Order Placement Rate",
        "targets": [
          {
            "expr": "rate(orders_placed_total[5m])",
            "legendFormat": "{{side}} - {{order_type}}"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Order Latency (p99)",
        "targets": [
          {
            "expr": "histogram_quantile(0.99, http_request_duration_seconds_bucket{path=\"/api/orders\"})",
            "legendFormat": "p99"
          },
          {
            "expr": "histogram_quantile(0.95, http_request_duration_seconds_bucket{path=\"/api/orders\"})",
            "legendFormat": "p95"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Error Rate",
        "targets": [
          {
            "expr": "rate(http_requests_total{status=~\"5..\"}[5m])",
            "legendFormat": "5xx errors"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Active Positions",
        "targets": [
          {
            "expr": "active_positions_total",
            "legendFormat": "{{exchange}}"
          }
        ],
        "type": "stat"
      }
    ]
  }
}
```

### 10.7 Alerting & Incident Management

**Alertmanager Configuration**:
```yaml
# alertmanager.yml
global:
  resolve_timeout: 5m
  slack_api_url: 'https://hooks.slack.com/services/YOUR/SLACK/WEBHOOK'

route:
  group_by: ['alertname', 'cluster', 'service']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 12h
  receiver: 'default'
  routes:
    - match:
        severity: critical
      receiver: 'pagerduty'
      continue: true
    
    - match:
        severity: warning
      receiver: 'slack'

receivers:
  - name: 'default'
    slack_configs:
      - channel: '#alerts'
        title: 'Alert: {{ .GroupLabels.alertname }}'
        text: '{{ range .Alerts }}{{ .Annotations.description }}{{ end }}'
  
  - name: 'pagerduty'
    pagerduty_configs:
      - service_key: 'YOUR_PAGERDUTY_KEY'
        description: '{{ .GroupLabels.alertname }}'
  
  - name: 'slack'
    slack_configs:
      - channel: '#warnings'
        title: 'Warning: {{ .GroupLabels.alertname }}'
        text: '{{ range .Alerts }}{{ .Annotations.description }}{{ end }}'

inhibit_rules:
  - source_match:
      severity: 'critical'
    target_match:
      severity: 'warning'
    equal: ['alertname', 'cluster', 'service']
```

---

## Summary

This document (Part 2, Sections 9-10) covers:

9. **Security Architecture**: Zero-trust security model with authentication (OAuth 2.0, JWT, MFA), authorization (RBAC), encryption (at rest and in transit), API security (rate limiting, validation), security monitoring, and audit logging.

10. **Observability Architecture**: Comprehensive observability with metrics collection (Prometheus), distributed tracing (Jaeger, OpenTelemetry), centralized logging (OpenSearch stack), Grafana dashboards, and alerting (Alertmanager, PagerDuty).

**Completed**: Part 2 (Sections 6-10)

**Next**: Part 3 (Sections 11-15): Performance Optimization, Compliance & Regulatory, Future-Safe Validation, Traceability Matrix, and Risks & Mitigations.
