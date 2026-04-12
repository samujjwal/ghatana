/**
 * @fileoverview Preview host protocol for UI Builder.
 */

export type {
  SandboxProfile,
  Viewport,
  PresetViewportKey,
  HostToPreviewMessage,
  PreviewToHostMessage,
  MountDocumentMessage,
  UpdateDocumentMessage,
  TeardownMessage,
  SetViewportMessage,
  SetThemeMessage,
  SetLocaleMessage,
  PingMessage,
  ReadyMessage,
  MountedMessage,
  UpdatedMessage,
  ErrorMessage,
  ClickMessage,
  HoverMessage,
  PongMessage,
  PreviewHostService,
} from './protocol.js';

export { PRESET_VIEWPORTS, createSandboxProfile } from './protocol.js';

export type {
  DeviceType,
  ThemeMode,
  Orientation,
  DeviceControlState,
  PreviewFallbackKind,
  PreviewFallbackState,
  PreviewTelemetryEventKind,
  PreviewTelemetryEvent,
  PreviewTelemetrySink,
  PreviewCSPDirectives,
} from './trust.js';

export {
  isTrustedOrigin,
  isPreviewProtocolMessage,
  createSafeMessageHandler,
  buildPreviewCSP,
  createDeviceControlState,
  applyOrientation,
  createFallbackState,
  noopPreviewTelemetrySink,
  withPreviewTelemetry,
} from './trust.js';
