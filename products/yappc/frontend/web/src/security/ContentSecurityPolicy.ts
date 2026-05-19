/**
 * Content Security Policy Configuration
 *
 * Defines CSP headers and iframe sandbox policies for YAPPC preview functionality.
 * These policies prevent XSS and ensure safe iframe isolation.
 *
 * @doc.type security
 * @doc.purpose CSP and sandbox policy configuration
 * @doc.layer product
 */

export interface CSPConfig {
  /** Default source for content */
  defaultSrc?: string[];
  /** Script sources */
  scriptSrc?: string[];
  /** Style sources */
  styleSrc?: string[];
  /** Image sources */
  imgSrc?: string[];
  /** Connect sources (API calls) */
  connectSrc?: string[];
  /** Font sources */
  fontSrc?: string[];
  /** Object sources */
  objectSrc?: string[];
  /** Media sources */
  mediaSrc?: string[];
  /** Frame sources */
  frameSrc?: string[];
  /** Base URI */
  baseUri?: string[];
  /** Form action */
  formAction?: string[];
  /** Frame ancestors */
  frameAncestors?: string[];
  /** Iframe/page sandbox restrictions */
  sandbox?: string[];
}

export interface SandboxConfig {
  /** Allow same-origin navigation */
  allowSameOrigin?: boolean;
  /** Allow scripts */
  allowScripts?: boolean;
  /** Allow forms */
  allowForms?: boolean;
  /** Allow popups */
  allowPopups?: boolean;
  /** Allow modals */
  allowModals?: boolean;
  /** Allow top navigation */
  allowTopNavigation?: boolean;
  /** Allow top navigation by user activation */
  allowTopNavigationByUserActivation?: boolean;
}

/**
 * Default CSP configuration for YAPPC
 */
export const DEFAULT_CSP: CSPConfig = {
  defaultSrc: ["'self'"],
  scriptSrc: ["'self'", "'unsafe-inline'", "'unsafe-eval'"], // Allow inline scripts for development
  styleSrc: ["'self'", "'unsafe-inline'"],
  imgSrc: ["'self'", "data:", "https:"],
  connectSrc: ["'self'", "https:"],
  fontSrc: ["'self'", "data:"],
  objectSrc: ["'none'"],
  mediaSrc: ["'self'"],
  frameSrc: ["'self'"],
  baseUri: ["'self'"],
  formAction: ["'self'"],
  frameAncestors: ["'none'"], // Prevent clickjacking
};

/**
 * Strict CSP for production
 */
export const STRICT_CSP: CSPConfig = {
  defaultSrc: ["'self'"],
  scriptSrc: ["'self'"], // No inline scripts in production
  styleSrc: ["'self'"], // No inline styles in production
  imgSrc: ["'self'", "data:"],
  connectSrc: ["'self'"],
  fontSrc: ["'self'", "data:"],
  objectSrc: ["'none'"],
  mediaSrc: ["'self'"],
  frameSrc: ["'self'"],
  baseUri: ["'self'"],
  formAction: ["'self'"],
  frameAncestors: ["'none'"],
};

/**
 * Sandbox configuration for preview iframe
 */
export const PREVIEW_SANDBOX: SandboxConfig = {
  allowSameOrigin: true,
  allowScripts: true, // Required for preview functionality
  allowForms: true, // Required for form testing
  allowPopups: false, // Prevent popups
  allowModals: false, // Prevent modals
  allowTopNavigation: false, // Prevent navigation away
  allowTopNavigationByUserActivation: false,
};

export const EXTERNAL_PREVIEW_SANDBOX: SandboxConfig = {
  allowSameOrigin: false,
  allowScripts: true,
  allowForms: true,
  allowPopups: false,
  allowModals: false,
  allowTopNavigation: false,
  allowTopNavigationByUserActivation: false,
};

/**
 * Sandbox configuration for trusted content
 */
export const TRUSTED_SANDBOX: SandboxConfig = {
  allowSameOrigin: true,
  allowScripts: true,
  allowForms: true,
  allowPopups: true,
  allowModals: true,
  allowTopNavigation: false,
  allowTopNavigationByUserActivation: true,
};

/**
 * Convert CSP config to CSP header string
 */
export function cspConfigToHeader(config: CSPConfig): string {
  const directives: string[] = [];

  if (config.defaultSrc) directives.push(`default-src ${config.defaultSrc.join(' ')}`);
  if (config.scriptSrc) directives.push(`script-src ${config.scriptSrc.join(' ')}`);
  if (config.styleSrc) directives.push(`style-src ${config.styleSrc.join(' ')}`);
  if (config.imgSrc) directives.push(`img-src ${config.imgSrc.join(' ')}`);
  if (config.connectSrc) directives.push(`connect-src ${config.connectSrc.join(' ')}`);
  if (config.fontSrc) directives.push(`font-src ${config.fontSrc.join(' ')}`);
  if (config.objectSrc) directives.push(`object-src ${config.objectSrc.join(' ')}`);
  if (config.mediaSrc) directives.push(`media-src ${config.mediaSrc.join(' ')}`);
  if (config.frameSrc) directives.push(`frame-src ${config.frameSrc.join(' ')}`);
  if (config.baseUri) directives.push(`base-uri ${config.baseUri.join(' ')}`);
  if (config.formAction) directives.push(`form-action ${config.formAction.join(' ')}`);
  if (config.frameAncestors) directives.push(`frame-ancestors ${config.frameAncestors.join(' ')}`);
  if (config.sandbox) directives.push(`sandbox ${config.sandbox.join(' ')}`);

  return directives.join('; ');
}

/**
 * Convert sandbox config to sandbox attribute string
 */
export function sandboxConfigToAttribute(config: SandboxConfig): string {
  const values: string[] = [];

  if (config.allowSameOrigin) values.push('allow-same-origin');
  if (config.allowScripts) values.push('allow-scripts');
  if (config.allowForms) values.push('allow-forms');
  if (config.allowPopups) values.push('allow-popups');
  if (config.allowModals) values.push('allow-modals');
  if (config.allowTopNavigation) values.push('allow-top-navigation');
  if (config.allowTopNavigationByUserActivation) values.push('allow-top-navigation-by-user-activation');

  return values.join(' ');
}

/**
 * Get CSP header for current environment
 */
export function getCSPHeader(isProduction: boolean): string {
  const config = isProduction ? STRICT_CSP : DEFAULT_CSP;
  return cspConfigToHeader(config);
}

/**
 * Get sandbox attribute for preview
 */
export function getPreviewSandbox(): string {
  return sandboxConfigToAttribute(PREVIEW_SANDBOX);
}

export function getExternalPreviewSandbox(): string {
  return sandboxConfigToAttribute(EXTERNAL_PREVIEW_SANDBOX);
}

/**
 * Get sandbox attribute for trusted content
 */
export function getTrustedSandbox(): string {
  return sandboxConfigToAttribute(TRUSTED_SANDBOX);
}

export default {
  DEFAULT_CSP,
  STRICT_CSP,
  PREVIEW_SANDBOX,
  EXTERNAL_PREVIEW_SANDBOX,
  TRUSTED_SANDBOX,
  cspConfigToHeader,
  sandboxConfigToAttribute,
  getCSPHeader,
  getPreviewSandbox,
  getExternalPreviewSandbox,
  getTrustedSandbox,
};
