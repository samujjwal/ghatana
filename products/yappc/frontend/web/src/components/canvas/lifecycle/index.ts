/**
 * Canvas Lifecycle Panels Index
 *
 * Components for lifecycle artifact editing within the canvas.
 * These panels are URL-controlled via ?panel= query param.
 *
 * @doc.type module
 * @doc.purpose Canvas lifecycle panel exports
 * @doc.layer product
 * @doc.pattern Barrel Export
 */

export { ArtifactsPanel } from './ArtifactsPanel';
export { RequirementsPanel } from './RequirementsPanel';
export { AdrPanel } from './AdrPanel';
export { AdrLifecycle } from './AdrLifecycle';
export type { AdrLifecycleProps, AdrLifecycleRecord, AdrLifecycleStatus, AdrAuditEntry } from './AdrLifecycle';
export { ThreatModelPanel } from './ThreatModelPanel';
export { ThreatLifecycle } from './ThreatLifecycle';
export type { ThreatLifecycleProps, ThreatRecord, ThreatDispositionStatus, ThreatAuditEntry } from './ThreatLifecycle';
export { UxSpecPanel } from './UxSpecPanel';
export { ImprovePanel } from './ImprovePanel';
export { TraceabilityPanel } from './TraceabilityPanel';
export { CanvasRightPanelHost } from './CanvasRightPanelHost';
