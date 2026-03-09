/**
 * Data Exfiltration Controls
 * 
 * Provides security controls to prevent unauthorized data exfiltration:
 * - External URL validation with allowlist/blocklist
 * - Payload size limits for collaboration and exports
 * - Script injection detection and blocking
 * - Data loss prevention (DLP) rules
 * 
 * Security Features:
 * - Multi-tier URL validation (protocol, domain, path patterns)
 * - Configurable size limits per operation type
 * - Content sanitization with script stripping
 * - Violation logging and alerting
 * - Rate limiting for outbound requests
 * 
 * @module security/exfiltrationControl
 */

/**
 * URL validation result
 */
export interface URLValidationResult {
  /** Validation passed */
  allowed: boolean;
  /** Validation reason */
  reason?: string;
  /** Blocked category if denied */
  category?: 'protocol' | 'domain' | 'pattern' | 'rate_limit';
  /** Sanitized URL if allowed */
  sanitizedUrl?: string;
}

/**
 * Payload size check result
 */
export interface PayloadSizeResult {
  /** Within limits */
  allowed: boolean;
  /** Actual size in bytes */
  size: number;
  /** Maximum allowed size */
  limit: number;
  /** Reason if denied */
  reason?: string;
}

/**
 * Script detection result
 */
export interface ScriptDetectionResult {
  /** No malicious content detected */
  safe: boolean;
  /** Detected threats */
  threats: string[];
  /** Sanitized content */
  sanitized?: string;
  /** Severity level */
  severity: 'low' | 'medium' | 'high' | 'critical';
}

/**
 * Operation types with different size limits
 */
export type OperationType = 
  | 'collaboration'
  | 'export'
  | 'upload'
  | 'share'
  | 'embed';

/**
 * Exfiltration control configuration
 */
export interface ExfiltrationControlConfig {
  /** Allowed URL protocols (default: ['https']) */
  allowedProtocols?: string[];
  /** Domain allowlist (empty = allow all) */
  domainAllowlist?: string[];
  /** Domain blocklist */
  domainBlocklist?: string[];
  /** Blocked URL patterns (regex) */
  blockedPatterns?: RegExp[];
  /** Size limits by operation type (bytes) */
  sizeLimits?: Partial<Record<OperationType, number>>;
  /** Enable script detection */
  detectScripts?: boolean;
  /** Strict mode (block on any suspicion) */
  strictMode?: boolean;
  /** Rate limit: max requests per minute per user */
  rateLimitPerMinute?: number;
  /** Enable violation logging */
  logViolations?: boolean;
}

/**
 * Violation log entry
 */
export interface ViolationLog {
  /** Violation ID */
  id: string;
  /** Timestamp */
  timestamp: number;
  /** User ID if available */
  userId?: string;
  /** Violation type */
  type: 'url' | 'size' | 'script' | 'rate_limit';
  /** Violation details */
  details: Record<string, unknown>;
  /** Severity */
  severity: 'low' | 'medium' | 'high' | 'critical';
  /** Blocked or warned */
  action: 'blocked' | 'warned' | 'sanitized';
}

/**
 * Rate limit state per user
 */
interface RateLimitState {
  /** Request count in current window */
  count: number;
  /** Window start timestamp */
  windowStart: number;
}

/**
 * Default size limits (in bytes)
 */
const DEFAULT_SIZE_LIMITS: Record<OperationType, number> = {
  collaboration: 100 * 1024,        // 100KB for collab payloads
  export: 50 * 1024 * 1024,         // 50MB for exports
  upload: 10 * 1024 * 1024,         // 10MB for uploads
  share: 5 * 1024 * 1024,           // 5MB for shares
  embed: 1 * 1024 * 1024,           // 1MB for embeds
};

/**
 * Common dangerous script patterns
 */
const SCRIPT_PATTERNS = [
  /<script[\s\S]*?>[\s\S]*?<\/script>/gi,
  /on\w+\s*=\s*["'][^"']*["']/gi,        // Event handlers
  /javascript:/gi,                        // JavaScript protocol
  /data:text\/html/gi,                    // Data URI with HTML
  /<iframe[\s\S]*?>/gi,                   // Iframes
  /<object[\s\S]*?>/gi,                   // Objects
  /<embed[\s\S]*?>/gi,                    // Embeds
  /eval\s*\(/gi,                          // eval()
  /Function\s*\(/gi,                      // Function constructor
  /setTimeout\s*\(/gi,                    // setTimeout with string
  /setInterval\s*\(/gi,                   // setInterval with string
];

/**
 * ExfiltrationControl
 * 
 * Manages data exfiltration prevention controls
 */
export class ExfiltrationControl {
  private config: Required<ExfiltrationControlConfig>;
  private violations: ViolationLog[] = [];
  private rateLimits = new Map<string, RateLimitState>();
  private violationCounter = 0;

  /**
   *
   */
  constructor(config: ExfiltrationControlConfig = {}) {
    this.config = {
      allowedProtocols: config.allowedProtocols ?? ['https:', 'http:'],
      domainAllowlist: config.domainAllowlist ?? [],
      domainBlocklist: config.domainBlocklist ?? [],
      blockedPatterns: config.blockedPatterns ?? [],
      sizeLimits: { ...DEFAULT_SIZE_LIMITS, ...config.sizeLimits },
      detectScripts: config.detectScripts ?? true,
      strictMode: config.strictMode ?? false,
      rateLimitPerMinute: config.rateLimitPerMinute ?? 60,
      logViolations: config.logViolations ?? true,
    };
  }

  /**
   * Validate external URL
   */
  validateURL(url: string, userId?: string): URLValidationResult {
    try {
      // Check rate limit first
      if (userId && !this.checkRateLimit(userId)) {
        this.logViolation({
          type: 'rate_limit',
          userId,
          details: { url },
          severity: 'medium',
          action: 'blocked',
        });

        return {
          allowed: false,
          reason: 'Rate limit exceeded',
          category: 'rate_limit',
        };
      }

      const urlObj = new URL(url);

      // Check protocol
      if (!this.config.allowedProtocols.includes(urlObj.protocol)) {
        this.logViolation({
          type: 'url',
          userId,
          details: { url, protocol: urlObj.protocol },
          severity: 'high',
          action: 'blocked',
        });

        return {
          allowed: false,
          reason: `Protocol ${urlObj.protocol} not allowed`,
          category: 'protocol',
        };
      }

      const hostname = urlObj.hostname.toLowerCase();

      // Check blocklist first
      if (this.isBlocked(hostname)) {
        this.logViolation({
          type: 'url',
          userId,
          details: { url, hostname },
          severity: 'high',
          action: 'blocked',
        });

        return {
          allowed: false,
          reason: `Domain ${hostname} is blocked`,
          category: 'domain',
        };
      }

      // Check allowlist if configured
      if (this.config.domainAllowlist.length > 0) {
        if (!this.isAllowed(hostname)) {
          this.logViolation({
            type: 'url',
            userId,
            details: { url, hostname },
            severity: 'medium',
            action: 'blocked',
          });

          return {
            allowed: false,
            reason: `Domain ${hostname} not in allowlist`,
            category: 'domain',
          };
        }
      }

      // Check URL patterns
      for (const pattern of this.config.blockedPatterns) {
        if (pattern.test(url)) {
          this.logViolation({
            type: 'url',
            userId,
            details: { url, pattern: pattern.source },
            severity: 'high',
            action: 'blocked',
          });

          return {
            allowed: false,
            reason: 'URL matches blocked pattern',
            category: 'pattern',
          };
        }
      }

      // Sanitize URL (remove credentials, etc.)
      const sanitized = this.sanitizeURL(urlObj);

      return {
        allowed: true,
        sanitizedUrl: sanitized,
      };
    } catch (error) {
      return {
        allowed: false,
        reason: 'Invalid URL format',
        category: 'protocol',
      };
    }
  }

  /**
   * Check payload size limits
   */
  checkPayloadSize(
    payload: unknown,
    operationType: OperationType,
    userId?: string
  ): PayloadSizeResult {
    const size = this.calculateSize(payload);
    const limit = this.config.sizeLimits[operationType] ?? DEFAULT_SIZE_LIMITS[operationType];

    if (size > limit) {
      this.logViolation({
        type: 'size',
        userId,
        details: { size, limit, operationType },
        severity: size > limit * 2 ? 'high' : 'medium',
        action: 'blocked',
      });

      return {
        allowed: false,
        size,
        limit,
        reason: `Payload size ${size} exceeds limit ${limit} for ${operationType}`,
      };
    }

    return {
      allowed: true,
      size,
      limit,
    };
  }

  /**
   * Detect and block scripts in content
   */
  detectScripts(content: string, userId?: string): ScriptDetectionResult {
    if (!this.config.detectScripts) {
      return {
        safe: true,
        threats: [],
        severity: 'low',
      };
    }

    const threats: string[] = [];
    let sanitized = content;

    for (const pattern of SCRIPT_PATTERNS) {
      const matches = content.match(pattern);
      if (matches) {
        threats.push(...matches);
        sanitized = sanitized.replace(pattern, '');
      }
    }

    if (threats.length > 0) {
      const severity = this.calculateThreatSeverity(threats);

      this.logViolation({
        type: 'script',
        userId,
        details: { threats: threats.slice(0, 10), count: threats.length },
        severity,
        action: this.config.strictMode ? 'blocked' : 'sanitized',
      });

      if (this.config.strictMode) {
        return {
          safe: false,
          threats,
          severity,
        };
      }

      return {
        safe: true,
        threats,
        sanitized,
        severity,
      };
    }

    return {
      safe: true,
      threats: [],
      severity: 'low',
    };
  }

  /**
   * Validate complete operation (URL + size + scripts)
   */
  validateOperation(
    data: {
      url?: string;
      payload?: unknown;
      content?: string;
      operationType: OperationType;
    },
    userId?: string
  ): {
    allowed: boolean;
    violations: string[];
    sanitized?: { url?: string; content?: string };
  } {
    const violations: string[] = [];
    const sanitized: { url?: string; content?: string } = {};

    // Check URL if provided
    if (data.url) {
      const urlResult = this.validateURL(data.url, userId);
      if (!urlResult.allowed) {
        violations.push(urlResult.reason ?? 'URL validation failed');
      } else {
        sanitized.url = urlResult.sanitizedUrl;
      }
    }

    // Check payload size if provided
    if (data.payload !== undefined) {
      const sizeResult = this.checkPayloadSize(data.payload, data.operationType, userId);
      if (!sizeResult.allowed) {
        violations.push(sizeResult.reason ?? 'Size limit exceeded');
      }
    }

    // Check scripts if content provided
    if (data.content) {
      const scriptResult = this.detectScripts(data.content, userId);
      if (!scriptResult.safe) {
        violations.push(`Dangerous scripts detected: ${scriptResult.threats.length} threats`);
      } else if (scriptResult.sanitized) {
        sanitized.content = scriptResult.sanitized;
      }
    }

    return {
      allowed: violations.length === 0,
      violations,
      sanitized: Object.keys(sanitized).length > 0 ? sanitized : undefined,
    };
  }

  /**
   * Get violation logs
   */
  getViolations(filter?: {
    userId?: string;
    type?: ViolationLog['type'];
    severity?: ViolationLog['severity'];
    since?: number;
  }): ViolationLog[] {
    let logs = this.violations;

    if (filter?.userId) {
      logs = logs.filter(v => v.userId === filter.userId);
    }

    if (filter?.type) {
      logs = logs.filter(v => v.type === filter.type);
    }

    if (filter?.severity) {
      logs = logs.filter(v => v.severity === filter.severity);
    }

    if (filter?.since !== undefined) {
      logs = logs.filter(v => v.timestamp >= filter.since!);
    }

    return logs;
  }

  /**
   * Clear violation logs
   */
  clearViolations(): void {
    this.violations = [];
  }

  /**
   * Get statistics
   */
  getStatistics(): {
    totalViolations: number;
    byType: Record<ViolationLog['type'], number>;
    bySeverity: Record<ViolationLog['severity'], number>;
    uniqueUsers: number;
  } {
    const byType: Record<ViolationLog['type'], number> = {
      url: 0,
      size: 0,
      script: 0,
      rate_limit: 0,
    };

    const bySeverity: Record<ViolationLog['severity'], number> = {
      low: 0,
      medium: 0,
      high: 0,
      critical: 0,
    };

    const users = new Set<string>();

    for (const violation of this.violations) {
      byType[violation.type]++;
      bySeverity[violation.severity]++;
      if (violation.userId) {
        users.add(violation.userId);
      }
    }

    return {
      totalViolations: this.violations.length,
      byType,
      bySeverity,
      uniqueUsers: users.size,
    };
  }

  /**
   * Update configuration
   */
  updateConfig(updates: Partial<ExfiltrationControlConfig>): void {
    Object.assign(this.config, updates);
  }

  /**
   * Get current configuration
   */
  getConfig(): Readonly<Required<ExfiltrationControlConfig>> {
    return { ...this.config };
  }

  // Private methods

  /**
   *
   */
  private isAllowed(hostname: string): boolean {
    return this.config.domainAllowlist.some(domain => {
      if (domain.startsWith('*.')) {
        const baseDomain = domain.slice(2);
        return hostname.endsWith(baseDomain) || hostname === baseDomain.replace(/^\./, '');
      }
      return hostname === domain;
    });
  }

  /**
   *
   */
  private isBlocked(hostname: string): boolean {
    return this.config.domainBlocklist.some(domain => {
      if (domain.startsWith('*.')) {
        const baseDomain = domain.slice(2);
        return hostname.endsWith(baseDomain) || hostname === baseDomain.replace(/^\./, '');
      }
      return hostname === domain;
    });
  }

  /**
   *
   */
  private sanitizeURL(url: URL): string {
    // Remove credentials
    const sanitized = new URL(url.toString());
    sanitized.username = '';
    sanitized.password = '';
    return sanitized.toString();
  }

  /**
   *
   */
  private calculateSize(payload: unknown): number {
    if (typeof payload === 'string') {
      return new Blob([payload]).size;
    }
    return new Blob([JSON.stringify(payload)]).size;
  }

  /**
   *
   */
  private checkRateLimit(userId: string): boolean {
    const now = Date.now();
    const state = this.rateLimits.get(userId);

    if (!state || now - state.windowStart >= 60000) {
      // New window
      this.rateLimits.set(userId, { count: 1, windowStart: now });
      return true;
    }

    if (state.count >= this.config.rateLimitPerMinute) {
      return false;
    }

    state.count++;
    return true;
  }

  /**
   *
   */
  private calculateThreatSeverity(threats: string[]): ViolationLog['severity'] {
    const count = threats.length;

    // Check for high-risk patterns
    const hasEval = threats.some(t => /eval|Function/.test(t));
    const hasScriptTag = threats.some(t => /<script/i.test(t));

    if (hasEval || count > 10) return 'critical';
    if (hasScriptTag || count > 5) return 'high';
    if (count > 2) return 'medium';
    return 'low';
  }

  /**
   *
   */
  private logViolation(log: Omit<ViolationLog, 'id' | 'timestamp'>): void {
    if (!this.config.logViolations) return;

    this.violations.push({
      id: `violation-${++this.violationCounter}`,
      timestamp: Date.now(),
      ...log,
    });
  }
}

/**
 * Create exfiltration control instance
 */
export function createExfiltrationControl(
  config?: ExfiltrationControlConfig
): ExfiltrationControl {
  return new ExfiltrationControl(config);
}
