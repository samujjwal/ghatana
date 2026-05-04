/**
 * Security Module Exports
 *
 * @doc.type module
 * @doc.purpose Security module exports
 * @doc.layer product
 */

export {
  DEFAULT_CSP,
  STRICT_CSP,
  PREVIEW_SANDBOX,
  TRUSTED_SANDBOX,
  cspConfigToHeader,
  sandboxConfigToAttribute,
  getCSPHeader,
  getPreviewSandbox,
  getTrustedSandbox,
} from './ContentSecurityPolicy';

export type { CSPConfig, SandboxConfig } from './ContentSecurityPolicy';

export {
  assessComponentSafety,
  getDefaultComponentPolicy,
  getTrustedComponentPolicy,
  applyComponentPolicy,
} from './UnsafeComponentHandler';

export type { ComponentSafetyAssessment, ComponentPolicy } from './UnsafeComponentHandler';

export {
  createPreviewSession,
  validatePreviewSession,
  isResourceInScope,
  getSessionExpirationTime,
  isSessionExpired,
  getRemainingSessionTime,
  extendSession,
} from './PreviewSession';

export type { PreviewSession, PreviewSessionScope, PreviewSessionOptions } from './PreviewSession';
