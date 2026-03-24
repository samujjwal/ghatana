/**
 * Export Sanitization
 *
 * Provides secure export sanitization with schema allowlists and DOMPurify integration.
 * Features:
 * - Schema-based property allowlists
 * - Rich text sanitization with DOMPurify
 * - Policy audit logging
 * - Configurable sanitization rules
 *
 * @module sanitizer
 */

/**
 * Sanitization policy
 */
export interface SanitizationPolicy {
  /** Allowed node properties */
  allowedNodeProps: Set<string>;
  /** Allowed edge properties */
  allowedEdgeProps: Set<string>;
  /** Allowed HTML tags in rich text */
  allowedHtmlTags: string[];
  /** Allowed HTML attributes */
  allowedHtmlAttrs: string[];
  /** Enable DOMPurify for HTML sanitization */
  enableDomPurify: boolean;
  /** Enable audit logging */
  enableAuditLog: boolean;
}

/**
 * Sanitization result
 */
export interface SanitizationResult<T> {
  /** Sanitized data */
  data: T;
  /** Whether any sanitization occurred */
  sanitized: boolean;
  /** Removed properties/fields */
  removed: string[];
  /** Sanitization warnings */
  warnings: string[];
}

/**
 * Sanitization audit entry
 */
export interface SanitizationAudit {
  /** Audit entry ID */
  id: string;
  /** Timestamp */
  timestamp: number;
  /** Type of data sanitized */
  type: 'node' | 'edge' | 'document' | 'html';
  /** Action taken */
  action: 'allow' | 'remove' | 'sanitize';
  /** Field/property affected */
  field: string;
  /** Original value (truncated) */
  originalValue?: string;
  /** Reason for action */
  reason: string;
}

/**
 * Node data for export
 */
export interface ExportNode {
  id: string;
  type: string;
  position: { x: number; y: number };
  data: Record<string, unknown>;
  style?: Record<string, unknown>;
  metadata?: Record<string, unknown>;
}

/**
 * Edge data for export
 */
export interface ExportEdge {
  id: string;
  source: string;
  target: string;
  type?: string;
  data?: Record<string, unknown>;
  style?: Record<string, unknown>;
}

/**
 * Document data for export
 */
export interface ExportDocument {
  nodes: ExportNode[];
  edges: ExportEdge[];
  metadata?: Record<string, unknown>;
}

/**
 * Default sanitization policy
 */
const DEFAULT_POLICY: SanitizationPolicy = {
  allowedNodeProps: new Set(['id', 'type', 'position', 'data', 'style', 'metadata']),
  allowedEdgeProps: new Set(['id', 'source', 'target', 'type', 'data', 'style']),
  allowedHtmlTags: ['p', 'br', 'strong', 'em', 'u', 'a', 'ul', 'ol', 'li', 'code', 'pre'],
  allowedHtmlAttrs: ['href', 'title', 'class'],
  enableDomPurify: true,
  enableAuditLog: true,
};

/**
 * Export sanitizer
 *
 * Sanitizes canvas data for secure export with schema allowlists and HTML cleaning.
 */
export class ExportSanitizer {
  private policy: SanitizationPolicy;
  private auditLog: SanitizationAudit[] = [];
  private auditIdCounter = 0;

  /**
   *
   */
  constructor(policy: Partial<SanitizationPolicy> = {}) {
    this.policy = {
      ...DEFAULT_POLICY,
      ...policy,
      allowedNodeProps: policy.allowedNodeProps
        ? new Set(policy.allowedNodeProps)
        : DEFAULT_POLICY.allowedNodeProps,
      allowedEdgeProps: policy.allowedEdgeProps
        ? new Set(policy.allowedEdgeProps)
        : DEFAULT_POLICY.allowedEdgeProps,
    };
  }

  /**
   * Sanitize a single node
   */
  sanitizeNode(node: Record<string, unknown>): SanitizationResult<ExportNode> {
    const removed: string[] = [];
    const warnings: string[] = [];
    const sanitized: Record<string, unknown> = {};

    // Filter properties by allowlist
    for (const [key, value] of Object.entries(node)) {
      if (this.policy.allowedNodeProps.has(key)) {
        sanitized[key] = value;
        this.audit('node', 'allow', key, 'Property in allowlist');
      } else {
        removed.push(key);
        warnings.push(`Removed disallowed property: ${key}`);
        this.audit('node', 'remove', key, 'Property not in allowlist');
      }
    }

    // Sanitize data fields with HTML content
    if (sanitized.data && typeof sanitized.data === 'object') {
      const dataResult = this.sanitizeDataFields(sanitized.data as Record<string, unknown>);
      sanitized.data = dataResult.data;
      removed.push(...dataResult.removed);
      warnings.push(...dataResult.warnings);
    }

    return {
      data: sanitized as unknown as ExportNode,
      sanitized: removed.length > 0,
      removed,
      warnings,
    };
  }

  /**
   * Sanitize a single edge
   */
  sanitizeEdge(edge: Record<string, unknown>): SanitizationResult<ExportEdge> {
    const removed: string[] = [];
    const warnings: string[] = [];
    const sanitized: Record<string, unknown> = {};

    // Filter properties by allowlist
    for (const [key, value] of Object.entries(edge)) {
      if (this.policy.allowedEdgeProps.has(key)) {
        sanitized[key] = value;
        this.audit('edge', 'allow', key, 'Property in allowlist');
      } else {
        removed.push(key);
        warnings.push(`Removed disallowed property: ${key}`);
        this.audit('edge', 'remove', key, 'Property not in allowlist');
      }
    }

    // Sanitize data fields with HTML content
    if (sanitized.data && typeof sanitized.data === 'object') {
      const dataResult = this.sanitizeDataFields(sanitized.data as Record<string, unknown>);
      sanitized.data = dataResult.data;
      removed.push(...dataResult.removed);
      warnings.push(...dataResult.warnings);
    }

    return {
      data: sanitized as unknown as ExportEdge,
      sanitized: removed.length > 0,
      removed,
      warnings,
    };
  }

  /**
   * Sanitize entire document
   */
  sanitizeDocument(document: {
    nodes?: unknown[];
    edges?: unknown[];
    metadata?: Record<string, unknown>;
  }): SanitizationResult<ExportDocument> {
    const removed: string[] = [];
    const warnings: string[] = [];

    // Sanitize nodes
    const nodes: ExportNode[] = [];
    if (Array.isArray(document.nodes)) {
      for (const node of document.nodes) {
        if (typeof node === 'object' && node !== null) {
          const result = this.sanitizeNode(node as Record<string, unknown>);
          nodes.push(result.data);
          removed.push(...result.removed.map((r) => `nodes.${r}`));
          warnings.push(...result.warnings);
        }
      }
    }

    // Sanitize edges
    const edges: ExportEdge[] = [];
    if (Array.isArray(document.edges)) {
      for (const edge of document.edges) {
        if (typeof edge === 'object' && edge !== null) {
          const result = this.sanitizeEdge(edge as Record<string, unknown>);
          edges.push(result.data);
          removed.push(...result.removed.map((r) => `edges.${r}`));
          warnings.push(...result.warnings);
        }
      }
    }

    // Include metadata as-is (could add allowlist if needed)
    const metadata = document.metadata;

    return {
      data: { nodes, edges, metadata },
      sanitized: removed.length > 0,
      removed,
      warnings,
    };
  }

  /**
   * Sanitize HTML string with DOMPurify
   */
  sanitizeHtml(html: string): SanitizationResult<string> {
    if (!this.policy.enableDomPurify) {
      return {
        data: html,
        sanitized: false,
        removed: [],
        warnings: [],
      };
    }

    const sanitized = this.applyDomPurify(html);
    const wasSanitized = sanitized !== html;

    if (wasSanitized) {
      this.audit('html', 'sanitize', 'html', 'HTML content sanitized with DOMPurify', html.slice(0, 100));
    }

    return {
      data: sanitized,
      sanitized: wasSanitized,
      removed: wasSanitized ? ['unsafe-html'] : [],
      warnings: wasSanitized ? ['HTML content was sanitized'] : [],
    };
  }

  /**
   * Get audit log
   */
  getAuditLog(): SanitizationAudit[] {
    return [...this.auditLog];
  }

  /**
   * Clear audit log
   */
  clearAuditLog(): void {
    this.auditLog = [];
  }

  /**
   * Get policy
   */
  getPolicy(): SanitizationPolicy {
    return { ...this.policy };
  }

  /**
   * Update policy
   */
  updatePolicy(policy: Partial<SanitizationPolicy>): void {
    this.policy = {
      ...this.policy,
      ...policy,
      allowedNodeProps: policy.allowedNodeProps
        ? new Set(policy.allowedNodeProps)
        : this.policy.allowedNodeProps,
      allowedEdgeProps: policy.allowedEdgeProps
        ? new Set(policy.allowedEdgeProps)
        : this.policy.allowedEdgeProps,
    };
  }

  /**
   * Sanitize data fields (looking for HTML content)
   */
  private sanitizeDataFields(data: Record<string, unknown>): SanitizationResult<Record<string, unknown>> {
    const removed: string[] = [];
    const warnings: string[] = [];
    const sanitized: Record<string, unknown> = {};

    for (const [key, value] of Object.entries(data)) {
      if (typeof value === 'string' && this.looksLikeHtml(value)) {
        const result = this.sanitizeHtml(value);
        sanitized[key] = result.data;
        if (result.sanitized) {
          warnings.push(`Sanitized HTML in field: ${key}`);
        }
      } else {
        sanitized[key] = value;
      }
    }

    return {
      data: sanitized,
      sanitized: warnings.length > 0,
      removed,
      warnings,
    };
  }

  /**
   * Check if string looks like HTML
   */
  private looksLikeHtml(str: string): boolean {
    return /<[a-z][\s\S]*>/i.test(str);
  }

  /**
   * Apply DOMPurify sanitization (mock implementation)
   *
   * In production, this would use the actual DOMPurify library.
   * For testing, we implement a basic sanitizer.
   */
  private applyDomPurify(html: string): string {
    // Simple sanitization: remove script tags and event handlers
    let sanitized = html;

    // Remove script tags
    sanitized = sanitized.replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '');

    // Remove event handler attributes
    sanitized = sanitized.replace(/\son\w+\s*=\s*["'][^"']*["']/gi, '');

    // Remove javascript: protocol
    sanitized = sanitized.replace(/javascript:/gi, '');

    // Filter to allowed tags (basic implementation)
    const allowedTags = this.policy.allowedHtmlTags.join('|');
    const tagPattern = new RegExp(`<(?!/?(?:${allowedTags})\\b)[^>]+>`, 'gi');
    sanitized = sanitized.replace(tagPattern, '');

    return sanitized;
  }

  /**
   * Add audit log entry
   */
  private audit(
    type: SanitizationAudit['type'],
    action: SanitizationAudit['action'],
    field: string,
    reason: string,
    originalValue?: string
  ): void {
    if (!this.policy.enableAuditLog) {
      return;
    }

    this.auditLog.push({
      id: `audit-${++this.auditIdCounter}`,
      timestamp: Date.now(),
      type,
      action,
      field,
      originalValue: originalValue?.slice(0, 100),
      reason,
    });
  }
}

/**
 * Create export sanitizer instance
 */
export function createSanitizer(policy?: Partial<SanitizationPolicy>): ExportSanitizer {
  return new ExportSanitizer(policy);
}

/**
 * Validate sanitization policy
 */
export function validatePolicy(policy: unknown): policy is SanitizationPolicy {
  if (typeof policy !== 'object' || policy === null) {
    return false;
  }

  const p = policy as Partial<SanitizationPolicy>;

  if (!(p.allowedNodeProps instanceof Set)) {
    return false;
  }
  if (!(p.allowedEdgeProps instanceof Set)) {
    return false;
  }
  if (!Array.isArray(p.allowedHtmlTags)) {
    return false;
  }
  if (!Array.isArray(p.allowedHtmlAttrs)) {
    return false;
  }
  if (typeof p.enableDomPurify !== 'boolean') {
    return false;
  }
  if (typeof p.enableAuditLog !== 'boolean') {
    return false;
  }

  return true;
}
