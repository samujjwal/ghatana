/**
 * Sandboxed Preview System
 * 
 * Provides secure preview rendering for untrusted HTML/markup with:
 * - Restrictive iframe sandbox attributes
 * - Content Security Policy (CSP) enforcement
 * - CSP violation logging and monitoring
 * - Optional proxy for external HTML sanitization
 * - Preview lifecycle management
 * 
 * Security Features:
 * - Isolated iframe environment with no same-origin access
 * - Disabled scripts, forms, popups, and plugins by default
 * - CSP headers: default-src 'none', img-src data:, style-src 'unsafe-inline'
 * - Violation reporting with detailed security event tracking
 * 
 * @module security/sandboxedPreview
 */

/**
 * CSP directive configuration
 */
export interface CSPDirectives {
  /** Default source for all content types */
  defaultSrc?: string[];
  /** Allowed script sources */
  scriptSrc?: string[];
  /** Allowed style sources */
  styleSrc?: string[];
  /** Allowed image sources */
  imgSrc?: string[];
  /** Allowed font sources */
  fontSrc?: string[];
  /** Allowed connection (fetch/XHR) sources */
  connectSrc?: string[];
  /** Allowed frame sources */
  frameSrc?: string[];
  /** Allowed object/embed sources */
  objectSrc?: string[];
  /** Allowed media (audio/video) sources */
  mediaSrc?: string[];
  /** Allowed manifest sources */
  manifestSrc?: string[];
  /** Report violations to this URI */
  reportUri?: string;
}

/**
 * Sandbox attribute flags for iframe
 */
export type SandboxAttribute = 
  | 'allow-forms'
  | 'allow-modals'
  | 'allow-orientation-lock'
  | 'allow-pointer-lock'
  | 'allow-popups'
  | 'allow-popups-to-escape-sandbox'
  | 'allow-presentation'
  | 'allow-same-origin'
  | 'allow-scripts'
  | 'allow-top-navigation'
  | 'allow-top-navigation-by-user-activation';

/**
 * Preview configuration options
 */
export interface PreviewConfig {
  /** Sandbox attributes to apply (restrictive by default) */
  sandbox?: SandboxAttribute[];
  /** CSP directives */
  csp?: CSPDirectives;
  /** Enable proxy for external HTML (sanitizes before rendering) */
  useProxy?: boolean;
  /** Proxy endpoint URL */
  proxyUrl?: string;
  /** Enable CSP violation logging */
  logViolations?: boolean;
  /** Maximum content size in bytes (default: 1MB) */
  maxContentSize?: number;
  /** Preview timeout in milliseconds (default: 30s) */
  timeout?: number;
  /** Custom iframe title for accessibility */
  iframeTitle?: string;
}

/**
 * CSP violation details
 */
export interface CSPViolation {
  /** Violation ID */
  id: string;
  /** Violated directive */
  violatedDirective: string;
  /** Blocked URI */
  blockedUri: string;
  /** Document URI where violation occurred */
  documentUri: string;
  /** Original CSP policy */
  originalPolicy: string;
  /** Line number in source */
  lineNumber?: number;
  /** Column number in source */
  columnNumber?: number;
  /** Source file */
  sourceFile?: string;
  /** Timestamp */
  timestamp: number;
  /** Severity level */
  severity: 'low' | 'medium' | 'high';
}

/**
 * Preview render result
 */
export interface PreviewResult {
  /** Success status */
  success: boolean;
  /** Preview ID */
  previewId: string;
  /** Iframe element reference */
  iframe?: HTMLIFrameElement;
  /** Error message if failed */
  error?: string;
  /** Content size in bytes */
  contentSize?: number;
  /** Render time in milliseconds */
  renderTime?: number;
}

/**
 * Preview state
 */
interface PreviewState {
  /** Preview ID */
  id: string;
  /** Iframe element */
  iframe: HTMLIFrameElement;
  /** Configuration */
  config: Required<PreviewConfig>;
  /** Creation timestamp */
  createdAt: number;
  /** Violation count */
  violations: number;
  /** Cleanup function */
  cleanup: () => void;
}

/**
 * Security event types
 */
export type SecurityEventType = 
  | 'preview_created'
  | 'preview_destroyed'
  | 'csp_violation'
  | 'content_blocked'
  | 'timeout'
  | 'size_exceeded';

/**
 * Security event
 */
export interface SecurityEvent {
  /** Event type */
  type: SecurityEventType;
  /** Preview ID */
  previewId: string;
  /** Timestamp */
  timestamp: number;
  /** Event details */
  details: Record<string, unknown>;
  /** Severity */
  severity: 'low' | 'medium' | 'high' | 'critical';
}

/**
 * Event listener callback
 */
type SecurityEventListener = (event: SecurityEvent) => void;

/**
 * Default CSP directives (most restrictive)
 */
const DEFAULT_CSP: CSPDirectives = {
  defaultSrc: ["'none'"],
  imgSrc: ['data:', 'blob:'],
  styleSrc: ["'unsafe-inline'"],
  scriptSrc: ["'none'"],
  objectSrc: ["'none'"],
  frameSrc: ["'none'"],
};

/**
 * Default sandbox attributes (most restrictive)
 */
const DEFAULT_SANDBOX: SandboxAttribute[] = [
  // No scripts, forms, popups, or same-origin access by default
];

/**
 * SandboxedPreviewManager
 * 
 * Manages sandboxed HTML preview rendering with security controls
 */
export class SandboxedPreviewManager {
  private previews = new Map<string, PreviewState>();
  private violations = new Map<string, CSPViolation[]>();
  private securityEvents: SecurityEvent[] = [];
  private eventListeners: Set<SecurityEventListener> = new Set();
  private violationCounter = 0;

  /**
   * Create preview from HTML content
   */
  createPreview(
    container: HTMLElement,
    content: string,
    config: PreviewConfig = {}
  ): PreviewResult {
    const startTime = Date.now();
    const previewId = this.generatePreviewId();

    try {
      // Validate content size
      const contentSize = new Blob([content]).size;
      const maxSize = config.maxContentSize ?? 1024 * 1024; // 1MB default

      if (contentSize > maxSize) {
        this.logSecurityEvent({
          type: 'size_exceeded',
          previewId,
          timestamp: Date.now(),
          details: { contentSize, maxSize },
          severity: 'medium',
        });

        return {
          success: false,
          previewId,
          error: `Content size ${contentSize} exceeds maximum ${maxSize} bytes`,
          contentSize,
        };
      }

      // Merge with defaults
      const fullConfig: Required<PreviewConfig> = {
        sandbox: config.sandbox ?? DEFAULT_SANDBOX,
        csp: { ...DEFAULT_CSP, ...config.csp },
        useProxy: config.useProxy ?? false,
        proxyUrl: config.proxyUrl ?? '/api/preview-proxy',
        logViolations: config.logViolations ?? true,
        maxContentSize: maxSize,
        timeout: config.timeout ?? 30000,
        iframeTitle: config.iframeTitle ?? 'Sandboxed Preview',
      };

      // Create iframe
      const iframe = this.createIframe(fullConfig);
      container.appendChild(iframe);

      // Set up CSP violation monitoring
      if (fullConfig.logViolations) {
        this.setupViolationMonitoring(previewId, iframe);
      }

      // Set timeout
      const timeoutId = setTimeout(() => {
        this.logSecurityEvent({
          type: 'timeout',
          previewId,
          timestamp: Date.now(),
          details: { timeout: fullConfig.timeout },
          severity: 'low',
        });
        this.destroyPreview(previewId);
      }, fullConfig.timeout);

      // Store preview state
      const state: PreviewState = {
        id: previewId,
        iframe,
        config: fullConfig,
        createdAt: Date.now(),
        violations: 0,
        cleanup: () => {
          clearTimeout(timeoutId);
          iframe.remove();
        },
      };

      this.previews.set(previewId, state);

      // Load content
      this.loadContent(iframe, content, fullConfig);

      // Log creation
      this.logSecurityEvent({
        type: 'preview_created',
        previewId,
        timestamp: Date.now(),
        details: { contentSize, config: fullConfig },
        severity: 'low',
      });

      const renderTime = Date.now() - startTime;

      return {
        success: true,
        previewId,
        iframe,
        contentSize,
        renderTime,
      };
    } catch (error) {
      return {
        success: false,
        previewId,
        error: error instanceof Error ? error.message : 'Unknown error',
      };
    }
  }

  /**
   * Create preview from URL (with optional proxy)
   */
  async createPreviewFromUrl(
    container: HTMLElement,
    url: string,
    config: PreviewConfig = {}
  ): Promise<PreviewResult> {
    try {
      if (config.useProxy) {
        // Fetch through proxy
        const proxyUrl = config.proxyUrl ?? '/api/preview-proxy';
        const response = await fetch(`${proxyUrl}?url=${encodeURIComponent(url)}`);
        
        if (!response.ok) {
          throw new Error(`Proxy failed: ${response.statusText}`);
        }

        const content = await response.text();
        return this.createPreview(container, content, config);
      } else {
        // Direct iframe src (less secure)
        const previewId = this.generatePreviewId();
        const fullConfig: Required<PreviewConfig> = {
          sandbox: config.sandbox ?? DEFAULT_SANDBOX,
          csp: { ...DEFAULT_CSP, ...config.csp },
          useProxy: false,
          proxyUrl: config.proxyUrl ?? '/api/preview-proxy',
          logViolations: config.logViolations ?? true,
          maxContentSize: config.maxContentSize ?? 1024 * 1024,
          timeout: config.timeout ?? 30000,
          iframeTitle: config.iframeTitle ?? 'Sandboxed Preview',
        };

        const iframe = this.createIframe(fullConfig);
        iframe.src = url;
        container.appendChild(iframe);

        if (fullConfig.logViolations) {
          this.setupViolationMonitoring(previewId, iframe);
        }

        const state: PreviewState = {
          id: previewId,
          iframe,
          config: fullConfig,
          createdAt: Date.now(),
          violations: 0,
          cleanup: () => iframe.remove(),
        };

        this.previews.set(previewId, state);

        return {
          success: true,
          previewId,
          iframe,
        };
      }
    } catch (error) {
      return {
        success: false,
        previewId: this.generatePreviewId(),
        error: error instanceof Error ? error.message : 'Unknown error',
      };
    }
  }

  /**
   * Destroy preview and cleanup resources
   */
  destroyPreview(previewId: string): boolean {
    const state = this.previews.get(previewId);
    
    if (!state) {
      return false;
    }

    state.cleanup();
    this.previews.delete(previewId);

    this.logSecurityEvent({
      type: 'preview_destroyed',
      previewId,
      timestamp: Date.now(),
      details: { 
        lifetime: Date.now() - state.createdAt,
        violations: state.violations,
      },
      severity: 'low',
    });

    return true;
  }

  /**
   * Get CSP violations for a preview
   */
  getViolations(previewId?: string): CSPViolation[] {
    if (previewId) {
      return this.violations.get(previewId) ?? [];
    }

    // Return all violations
    return Array.from(this.violations.values()).flat();
  }

  /**
   * Get security events
   */
  getSecurityEvents(filter?: {
    previewId?: string;
    type?: SecurityEventType;
    severity?: SecurityEvent['severity'];
    since?: number;
  }): SecurityEvent[] {
    let events = this.securityEvents;

    if (filter?.previewId) {
      events = events.filter(e => e.previewId === filter.previewId);
    }

    if (filter?.type) {
      events = events.filter(e => e.type === filter.type);
    }

    if (filter?.severity) {
      events = events.filter(e => e.severity === filter.severity);
    }

    if (filter?.since !== undefined) {
      events = events.filter(e => e.timestamp >= filter.since!);
    }

    return events;
  }

  /**
   * Clear violations
   */
  clearViolations(previewId?: string): void {
    if (previewId) {
      this.violations.delete(previewId);
    } else {
      this.violations.clear();
    }
  }

  /**
   * Clear security events
   */
  clearSecurityEvents(): void {
    this.securityEvents = [];
  }

  /**
   * Subscribe to security events
   */
  subscribe(listener: SecurityEventListener): () => void {
    this.eventListeners.add(listener);
    return () => this.eventListeners.delete(listener);
  }

  /**
   * Get active previews count
   */
  getActivePreviewsCount(): number {
    return this.previews.size;
  }

  /**
   * Get preview state
   */
  getPreview(previewId: string): PreviewState | undefined {
    return this.previews.get(previewId);
  }

  /**
   * Build CSP header string
   */
  buildCSPHeader(directives: CSPDirectives): string {
    const parts: string[] = [];

    for (const [key, value] of Object.entries(directives)) {
      if (value && Array.isArray(value) && value.length > 0) {
        const directive = key.replace(/([A-Z])/g, '-$1').toLowerCase();
        parts.push(`${directive} ${value.join(' ')}`);
      } else if (value && typeof value === 'string') {
        const directive = key.replace(/([A-Z])/g, '-$1').toLowerCase();
        parts.push(`${directive} ${value}`);
      }
    }

    return parts.join('; ');
  }

  /**
   * Destroy all previews
   */
  destroyAll(): void {
    for (const previewId of this.previews.keys()) {
      this.destroyPreview(previewId);
    }
  }

  // Private methods

  /**
   *
   */
  private createIframe(config: Required<PreviewConfig>): HTMLIFrameElement {
    const iframe = document.createElement('iframe');
    
    // Set sandbox attribute
    if (config.sandbox.length > 0) {
      iframe.setAttribute('sandbox', config.sandbox.join(' '));
    } else {
      iframe.setAttribute('sandbox', ''); // Empty = most restrictive
    }

    // Set CSP via meta tag (will be injected into content)
    iframe.setAttribute('csp', this.buildCSPHeader(config.csp));
    
    // Accessibility
    iframe.title = config.iframeTitle;
    
    // Security attributes
    iframe.setAttribute('referrerpolicy', 'no-referrer');
    iframe.setAttribute('loading', 'lazy');
    
    // Styling
    iframe.style.border = 'none';
    iframe.style.width = '100%';
    iframe.style.height = '100%';

    return iframe;
  }

  /**
   *
   */
  private loadContent(
    iframe: HTMLIFrameElement,
    content: string,
    config: Required<PreviewConfig>
  ): void {
    const cspHeader = this.buildCSPHeader(config.csp);
    
    // Inject CSP meta tag
    const wrappedContent = `
      <!DOCTYPE html>
      <html>
        <head>
          <meta http-equiv="Content-Security-Policy" content="${cspHeader}">
          <meta charset="UTF-8">
        </head>
        <body>
          ${content}
        </body>
      </html>
    `;

    // Use srcdoc for content
    iframe.srcdoc = wrappedContent;
  }

  /**
   *
   */
  private setupViolationMonitoring(previewId: string, iframe: HTMLIFrameElement): void {
    // Listen for CSP violation events
    const handleViolation = (event: SecurityPolicyViolationEvent) => {
      const violation: CSPViolation = {
        id: `${previewId}-${++this.violationCounter}`,
        violatedDirective: event.violatedDirective,
        blockedUri: event.blockedURI,
        documentUri: event.documentURI,
        originalPolicy: event.originalPolicy,
        lineNumber: event.lineNumber,
        columnNumber: event.columnNumber,
        sourceFile: event.sourceFile,
        timestamp: Date.now(),
        severity: this.calculateViolationSeverity(event.violatedDirective),
      };

      // Store violation
      const violations = this.violations.get(previewId) ?? [];
      violations.push(violation);
      this.violations.set(previewId, violations);

      // Update preview state
      const state = this.previews.get(previewId);
      if (state) {
        state.violations++;
      }

      // Log security event
      this.logSecurityEvent({
        type: 'csp_violation',
        previewId,
        timestamp: Date.now(),
        details: { violation },
        severity: violation.severity,
      });
    };

    // Add listener to iframe's content window (when loaded)
    iframe.addEventListener('load', () => {
      try {
        iframe.contentWindow?.addEventListener('securitypolicyviolation', handleViolation as EventListener);
      } catch (error) {
        // Cross-origin restrictions may prevent this
        console.warn('Could not attach violation listener:', error);
      }
    });
  }

  /**
   *
   */
  private calculateViolationSeverity(directive: string): CSPViolation['severity'] {
    // Script violations are most severe
    if (directive.includes('script')) {
      return 'high';
    }
    
    // Object/frame violations are medium
    if (directive.includes('object') || directive.includes('frame')) {
      return 'medium';
    }

    // Others are low
    return 'low';
  }

  /**
   *
   */
  private logSecurityEvent(event: SecurityEvent): void {
    this.securityEvents.push(event);
    
    // Notify listeners
    for (const listener of this.eventListeners) {
      try {
        listener(event);
      } catch (error) {
        console.error('Event listener error:', error);
      }
    }
  }

  /**
   *
   */
  private generatePreviewId(): string {
    return `preview-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }
}

/**
 * Create a sandboxed preview manager
 */
export function createSandboxedPreviewManager(): SandboxedPreviewManager {
  return new SandboxedPreviewManager();
}

/**
 * Validate preview configuration
 */
export function validatePreviewConfig(config: PreviewConfig): { valid: boolean; errors: string[] } {
  const errors: string[] = [];

  if (config.maxContentSize !== undefined && config.maxContentSize <= 0) {
    errors.push('maxContentSize must be positive');
  }

  if (config.timeout !== undefined && config.timeout <= 0) {
    errors.push('timeout must be positive');
  }

  if (config.useProxy && !config.proxyUrl) {
    errors.push('proxyUrl required when useProxy is true');
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}
