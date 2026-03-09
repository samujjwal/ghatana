/**
 * Collaboration Diagnostics & Monitoring
 *
 * Provides comprehensive diagnostics, monitoring, and troubleshooting tools
 * for real-time collaboration infrastructure.
 *
 * Features:
 * - Session monitoring with active connection tracking
 * - Error rate tracking and alerting
 * - Performance metrics collection
 * - Health checks for collaboration services
 * - Connection quality metrics
 * - Troubleshooting runbook automation
 *
 * @module libs/canvas/src/collab/collabDiagnostics
 */

/**
 * Session status
 */
export type SessionStatus = 'connected' | 'disconnected' | 'reconnecting' | 'error';

/**
 * Error severity level
 */
export type ErrorSeverity = 'low' | 'medium' | 'high' | 'critical';

/**
 * Health status
 */
export type HealthStatus = 'healthy' | 'degraded' | 'unhealthy';

/**
 * Connection quality level
 */
export type ConnectionQuality = 'excellent' | 'good' | 'fair' | 'poor';

/**
 * Collaboration session information
 */
export interface SessionInfo {
  /** Session ID */
  sessionId: string;
  /** User ID */
  userId: string;
  /** Username */
  username: string;
  /** Session status */
  status: SessionStatus;
  /** Connection start time */
  connectedAt: number;
  /** Last activity timestamp */
  lastActivityAt: number;
  /** Provider type (websocket/webrtc) */
  provider: 'websocket' | 'webrtc';
  /** Room ID */
  roomId: string;
  /** Additional metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Error tracking entry
 */
export interface ErrorEntry {
  /** Error ID */
  id: string;
  /** Error type/code */
  type: string;
  /** Error message */
  message: string;
  /** Error severity */
  severity: ErrorSeverity;
  /** Timestamp */
  timestamp: number;
  /** User ID if applicable */
  userId?: string;
  /** Session ID if applicable */
  sessionId?: string;
  /** Stack trace */
  stack?: string;
  /** Additional context */
  context?: Record<string, unknown>;
}

/**
 * Performance metric
 */
export interface PerformanceMetric {
  /** Metric name */
  name: string;
  /** Metric value */
  value: number;
  /** Metric unit */
  unit: string;
  /** Timestamp */
  timestamp: number;
  /** Session ID if applicable */
  sessionId?: string;
  /** Tags for grouping */
  tags?: Record<string, string>;
}

/**
 * Health check result
 */
export interface HealthCheckResult {
  /** Service name */
  service: string;
  /** Health status */
  status: HealthStatus;
  /** Check timestamp */
  timestamp: number;
  /** Response time in ms */
  responseTime: number;
  /** Additional details */
  details?: Record<string, unknown>;
  /** Error message if unhealthy */
  error?: string;
}

/**
 * Connection quality metrics
 */
export interface ConnectionQualityMetrics {
  /** Session ID */
  sessionId: string;
  /** Quality rating */
  quality: ConnectionQuality;
  /** Latency in ms */
  latency: number;
  /** Packet loss percentage */
  packetLoss: number;
  /** Jitter in ms */
  jitter: number;
  /** Bandwidth in kbps */
  bandwidth: number;
  /** Measurement timestamp */
  timestamp: number;
}

/**
 * Diagnostic statistics
 */
export interface DiagnosticStatistics {
  /** Total active sessions */
  activeSessions: number;
  /** Total errors in window */
  totalErrors: number;
  /** Errors by severity */
  errorsBySeverity: Record<ErrorSeverity, number>;
  /** Average latency in ms */
  averageLatency: number;
  /** Average packet loss % */
  averagePacketLoss: number;
  /** Reconnection count */
  reconnectionCount: number;
  /** Service health summary */
  healthSummary: Record<string, HealthStatus>;
}

/**
 * Collaboration diagnostics configuration
 */
export interface CollabDiagnosticsConfig {
  /** Enable diagnostics collection */
  enabled: boolean;
  /** Maximum error history entries */
  maxErrorHistory: number;
  /** Maximum metric history entries */
  maxMetricHistory: number;
  /** Error rate threshold per minute */
  errorRateThreshold: number;
  /** Latency threshold in ms */
  latencyThreshold: number;
  /** Packet loss threshold % */
  packetLossThreshold: number;
  /** Health check interval in ms */
  healthCheckInterval: number;
}

/**
 * Collaboration diagnostics state
 */
interface CollabDiagnosticsState {
  /** Active sessions by ID */
  sessions: Map<string, SessionInfo>;
  /** Error history */
  errors: ErrorEntry[];
  /** Performance metrics */
  metrics: PerformanceMetric[];
  /** Connection quality metrics */
  qualityMetrics: Map<string, ConnectionQualityMetrics>;
  /** Health check results */
  healthChecks: Map<string, HealthCheckResult>;
  /** Reconnection attempts counter */
  reconnectionCount: number;
  /** Configuration */
  config: CollabDiagnosticsConfig;
}

/**
 * Default configuration
 */
const DEFAULT_CONFIG: CollabDiagnosticsConfig = {
  enabled: true,
  maxErrorHistory: 1000,
  maxMetricHistory: 5000,
  errorRateThreshold: 10, // 10 errors per minute
  latencyThreshold: 200, // 200ms
  packetLossThreshold: 5, // 5%
  healthCheckInterval: 30000, // 30 seconds
};

/**
 * Collaboration Diagnostics Manager
 *
 * Provides monitoring, diagnostics, and troubleshooting capabilities
 * for collaboration infrastructure.
 */
export class CollabDiagnosticsManager {
  private state: CollabDiagnosticsState;

  /**
   *
   */
  constructor(config: Partial<CollabDiagnosticsConfig> = {}) {
    this.state = {
      sessions: new Map(),
      errors: [],
      metrics: [],
      qualityMetrics: new Map(),
      healthChecks: new Map(),
      reconnectionCount: 0,
      config: {
        ...DEFAULT_CONFIG,
        ...config,
      },
    };
  }

  // ==================== Session Management ====================

  /**
   * Register new collaboration session
   */
  registerSession(
    sessionId: string,
    userId: string,
    username: string,
    provider: 'websocket' | 'webrtc',
    roomId: string,
    metadata?: Record<string, unknown>
  ): SessionInfo {
    if (!this.state.config.enabled) {
      throw new Error('Diagnostics collection is disabled');
    }

    const now = Date.now();
    const session: SessionInfo = {
      sessionId,
      userId,
      username,
      status: 'connected',
      connectedAt: now,
      lastActivityAt: now,
      provider,
      roomId,
      metadata,
    };

    this.state.sessions.set(sessionId, session);
    return session;
  }

  /**
   * Update session status
   */
  updateSessionStatus(sessionId: string, status: SessionStatus): void {
    const session = this.state.sessions.get(sessionId);
    if (!session) {
      throw new Error(`Session not found: ${sessionId}`);
    }

    session.status = status;
    session.lastActivityAt = Date.now();
    this.state.sessions.set(sessionId, session);

    // Track reconnection attempts
    if (status === 'reconnecting') {
      this.state.reconnectionCount++;
    }
  }

  /**
   * Update session activity timestamp
   */
  updateSessionActivity(sessionId: string): void {
    const session = this.state.sessions.get(sessionId);
    if (!session) {
      throw new Error(`Session not found: ${sessionId}`);
    }

    session.lastActivityAt = Date.now();
    this.state.sessions.set(sessionId, session);
  }

  /**
   * Unregister session
   */
  unregisterSession(sessionId: string): boolean {
    const session = this.state.sessions.get(sessionId);
    if (!session) {
      return false;
    }

    session.status = 'disconnected';
    session.lastActivityAt = Date.now();
    this.state.sessions.set(sessionId, session);

    // Keep session in history briefly, then remove
    // In production, you'd move to archive storage
    return true;
  }

  /**
   * Get session information
   */
  getSession(sessionId: string): SessionInfo | undefined {
    return this.state.sessions.get(sessionId);
  }

  /**
   * Get all active sessions
   */
  getActiveSessions(): SessionInfo[] {
    return Array.from(this.state.sessions.values()).filter(
      (s) => s.status === 'connected' || s.status === 'reconnecting'
    );
  }

  /**
   * Get sessions by user
   */
  getSessionsByUser(userId: string): SessionInfo[] {
    return Array.from(this.state.sessions.values()).filter((s) => s.userId === userId);
  }

  /**
   * Get sessions by room
   */
  getSessionsByRoom(roomId: string): SessionInfo[] {
    return Array.from(this.state.sessions.values()).filter((s) => s.roomId === roomId);
  }

  // ==================== Error Tracking ====================

  /**
   * Log error
   */
  logError(
    type: string,
    message: string,
    severity: ErrorSeverity,
    userId?: string,
    sessionId?: string,
    stack?: string,
    context?: Record<string, unknown>
  ): ErrorEntry {
    if (!this.state.config.enabled) {
      throw new Error('Diagnostics collection is disabled');
    }

    const id = `error-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
    const entry: ErrorEntry = {
      id,
      type,
      message,
      severity,
      timestamp: Date.now(),
      userId,
      sessionId,
      stack,
      context,
    };

    this.state.errors.push(entry);

    // Enforce max history
    if (this.state.errors.length > this.state.config.maxErrorHistory) {
      this.state.errors.shift();
    }

    return entry;
  }

  /**
   * Get error history
   */
  getErrors(): ErrorEntry[] {
    return [...this.state.errors];
  }

  /**
   * Get errors by severity
   */
  getErrorsBySeverity(severity: ErrorSeverity): ErrorEntry[] {
    return this.state.errors.filter((e) => e.severity === severity);
  }

  /**
   * Get errors by session
   */
  getErrorsBySession(sessionId: string): ErrorEntry[] {
    return this.state.errors.filter((e) => e.sessionId === sessionId);
  }

  /**
   * Get errors in time range
   */
  getErrorsByTimeRange(startTime: number, endTime: number): ErrorEntry[] {
    return this.state.errors.filter(
      (e) => e.timestamp >= startTime && e.timestamp <= endTime
    );
  }

  /**
   * Clear error history
   */
  clearErrors(): number {
    const count = this.state.errors.length;
    this.state.errors = [];
    return count;
  }

  /**
   * Check if error rate exceeds threshold
   */
  isErrorRateExceeded(): boolean {
    const oneMinuteAgo = Date.now() - 60000;
    const recentErrors = this.state.errors.filter((e) => e.timestamp >= oneMinuteAgo);
    return recentErrors.length > this.state.config.errorRateThreshold;
  }

  // ==================== Performance Metrics ====================

  /**
   * Record performance metric
   */
  recordMetric(
    name: string,
    value: number,
    unit: string,
    sessionId?: string,
    tags?: Record<string, string>
  ): PerformanceMetric {
    if (!this.state.config.enabled) {
      throw new Error('Diagnostics collection is disabled');
    }

    const metric: PerformanceMetric = {
      name,
      value,
      unit,
      timestamp: Date.now(),
      sessionId,
      tags,
    };

    this.state.metrics.push(metric);

    // Enforce max history
    if (this.state.metrics.length > this.state.config.maxMetricHistory) {
      this.state.metrics.shift();
    }

    return metric;
  }

  /**
   * Get metrics by name
   */
  getMetricsByName(name: string): PerformanceMetric[] {
    return this.state.metrics.filter((m) => m.name === name);
  }

  /**
   * Get metrics by session
   */
  getMetricsBySession(sessionId: string): PerformanceMetric[] {
    return this.state.metrics.filter((m) => m.sessionId === sessionId);
  }

  /**
   * Get average metric value
   */
  getAverageMetric(name: string): number {
    const metrics = this.getMetricsByName(name);
    if (metrics.length === 0) {
      return 0;
    }

    const sum = metrics.reduce((acc, m) => acc + m.value, 0);
    return sum / metrics.length;
  }

  /**
   * Clear metrics history
   */
  clearMetrics(): number {
    const count = this.state.metrics.length;
    this.state.metrics = [];
    return count;
  }

  // ==================== Connection Quality ====================

  /**
   * Update connection quality metrics
   */
  updateConnectionQuality(
    sessionId: string,
    latency: number,
    packetLoss: number,
    jitter: number,
    bandwidth: number
  ): ConnectionQualityMetrics {
    // Calculate quality rating
    let quality: ConnectionQuality;
    if (latency < 50 && packetLoss < 1) {
      quality = 'excellent';
    } else if (latency < 100 && packetLoss < 3) {
      quality = 'good';
    } else if (latency < 200 && packetLoss < 5) {
      quality = 'fair';
    } else {
      quality = 'poor';
    }

    const metrics: ConnectionQualityMetrics = {
      sessionId,
      quality,
      latency,
      packetLoss,
      jitter,
      bandwidth,
      timestamp: Date.now(),
    };

    this.state.qualityMetrics.set(sessionId, metrics);
    return metrics;
  }

  /**
   * Get connection quality for session
   */
  getConnectionQuality(sessionId: string): ConnectionQualityMetrics | undefined {
    return this.state.qualityMetrics.get(sessionId);
  }

  /**
   * Get all connection quality metrics
   */
  getAllConnectionQuality(): ConnectionQualityMetrics[] {
    return Array.from(this.state.qualityMetrics.values());
  }

  /**
   * Get sessions with poor connection quality
   */
  getPoorQualityConnections(): ConnectionQualityMetrics[] {
    return Array.from(this.state.qualityMetrics.values()).filter(
      (m) => m.quality === 'poor' || m.quality === 'fair'
    );
  }

  // ==================== Health Checks ====================

  /**
   * Record health check result
   */
  recordHealthCheck(
    service: string,
    status: HealthStatus,
    responseTime: number,
    details?: Record<string, unknown>,
    error?: string
  ): HealthCheckResult {
    const result: HealthCheckResult = {
      service,
      status,
      timestamp: Date.now(),
      responseTime,
      details,
      error,
    };

    this.state.healthChecks.set(service, result);
    return result;
  }

  /**
   * Get health check result for service
   */
  getHealthCheck(service: string): HealthCheckResult | undefined {
    return this.state.healthChecks.get(service);
  }

  /**
   * Get all health check results
   */
  getAllHealthChecks(): HealthCheckResult[] {
    return Array.from(this.state.healthChecks.values());
  }

  /**
   * Get unhealthy services
   */
  getUnhealthyServices(): HealthCheckResult[] {
    return Array.from(this.state.healthChecks.values()).filter(
      (h) => h.status === 'unhealthy' || h.status === 'degraded'
    );
  }

  // ==================== Statistics ====================

  /**
   * Get diagnostic statistics
   */
  getStatistics(): DiagnosticStatistics {
    const activeSessions = this.getActiveSessions();
    const qualityMetrics = this.getAllConnectionQuality();

    // Calculate averages
    const avgLatency =
      qualityMetrics.length > 0
        ? qualityMetrics.reduce((sum, m) => sum + m.latency, 0) / qualityMetrics.length
        : 0;

    const avgPacketLoss =
      qualityMetrics.length > 0
        ? qualityMetrics.reduce((sum, m) => sum + m.packetLoss, 0) /
          qualityMetrics.length
        : 0;

    // Errors by severity
    const errorsBySeverity: Record<ErrorSeverity, number> = {
      low: 0,
      medium: 0,
      high: 0,
      critical: 0,
    };

    for (const error of this.state.errors) {
      errorsBySeverity[error.severity]++;
    }

    // Health summary
    const healthSummary: Record<string, HealthStatus> = {};
    for (const [service, check] of this.state.healthChecks.entries()) {
      healthSummary[service] = check.status;
    }

    return {
      activeSessions: activeSessions.length,
      totalErrors: this.state.errors.length,
      errorsBySeverity,
      averageLatency: avgLatency,
      averagePacketLoss: avgPacketLoss,
      reconnectionCount: this.state.reconnectionCount,
      healthSummary,
    };
  }

  // ==================== Configuration ====================

  /**
   * Get current configuration
   */
  getConfig(): CollabDiagnosticsConfig {
    return { ...this.state.config };
  }

  /**
   * Update configuration
   */
  updateConfig(updates: Partial<CollabDiagnosticsConfig>): void {
    this.state.config = {
      ...this.state.config,
      ...updates,
    };
  }

  /**
   * Reset all diagnostics data
   */
  reset(): void {
    this.state.sessions.clear();
    this.state.errors = [];
    this.state.metrics = [];
    this.state.qualityMetrics.clear();
    this.state.healthChecks.clear();
    this.state.reconnectionCount = 0;
  }
}
