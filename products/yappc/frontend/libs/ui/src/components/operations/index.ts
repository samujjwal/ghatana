/**
 * Operations Components Index
 *
 * @description Exports all operations UI components for Phase 4.
 *
 * @doc.type index
 * @doc.purpose Component exports
 * @doc.layer presentation
 * @doc.phase 4
 */

// Incident Management
export { IncidentDashboard } from './IncidentDashboard';
export type { Incident, IncidentSeverity, IncidentStatus } from './IncidentDashboard';

// Health & Metrics
export { HealthStatusCard } from './HealthStatusCard';
export type {
  HealthStatusCardProps,
  HealthStatus,
  ComponentStatus,
  HealthComponent,
} from './HealthStatusCard';

export { MetricCard } from './MetricCard';
export type {
  MetricCardProps,
  TrendDirection,
  MetricFormat,
  SparklinePoint,
} from './MetricCard';

// Alerts
export { AlertCard } from './AlertCard';
export type {
  AlertCardProps,
  Alert,
  AlertRule,
  AlertSeverity,
  AlertStatus,
} from './AlertCard';

// Logging
export { LogViewer } from './LogViewer';
export type {
  LogViewerProps,
  LogEntry,
  LogLevel,
  LogFilter,
} from './LogViewer';

// Incident Response
export { IncidentTimeline } from './IncidentTimeline';
export type {
  IncidentTimelineProps,
  TimelineEvent,
  TimelineEventType,
  TimelineActor,
} from './IncidentTimeline';

export { WarRoomChat } from './WarRoomChat';
export type {
  WarRoomChatProps,
  ChatMessage,
  ChatUser,
  MessageType,
} from './WarRoomChat';

// Runbooks
export { RunbookCard } from './RunbookCard';
export type {
  RunbookCardProps,
  Runbook,
  RunbookStep,
  RunbookExecution,
  RunbookStatus,
  RunbookExecutionStatus,
  StepType,
} from './RunbookCard';

// Custom Dashboards
export { DashboardWidget } from './DashboardWidget';
export type {
  DashboardWidgetProps,
  WidgetConfig,
  WidgetType,
  WidgetSize,
  WidgetDataPoint,
  WidgetSeries,
} from './DashboardWidget';

// Tracing
export { TraceViewer } from './TraceViewer';
export type {
  TraceViewerProps,
  Trace,
  Span,
  SpanStatus,
  SpanTag,
  SpanLog,
} from './TraceViewer';
