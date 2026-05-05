/**
 * Plugin Runtime Policy Enforcement
 *
 * Enforces runtime policies for plugins including:
 * - PluginRuntimePolicy
 * - PluginSandboxBoundary
 * - network/storage/browser API/telemetry policies
 *
 * @doc.type service
 * @doc.purpose Plugin runtime policy enforcement
 * @doc.layer product
 */

export interface NetworkPolicy {
  /** Allow network requests */
  allowNetworkRequests: boolean;
  /** Allowed domains */
  allowedDomains?: string[];
  /** Blocked domains */
  blockedDomains?: string[];
  /** Max request size */
  maxRequestSize?: number;
  /** Timeout in milliseconds */
  timeout?: number;
}

export interface StoragePolicy {
  /** Allow localStorage */
  allowLocalStorage: boolean;
  /** Allow sessionStorage */
  allowSessionStorage: boolean;
  /** Allow IndexedDB */
  allowIndexedDB: boolean;
  /** Allow cookies */
  allowCookies: boolean;
  /** Storage quota in bytes */
  storageQuota?: number;
}

export interface BrowserAPIPolicy {
  /** Allow geolocation */
  allowGeolocation: boolean;
  /** Allow media devices (camera/mic) */
  allowMediaDevices: boolean;
  /** Allow clipboard access */
  allowClipboard: boolean;
  /** Allow notifications */
  allowNotifications: boolean;
  /** Allow fullscreen */
  allowFullscreen: boolean;
  /** Allow websockets */
  allowWebSockets: boolean;
  /** Allowed APIs */
  allowedAPIs?: string[];
}

export interface TelemetryPolicy {
  /** Allow telemetry */
  allowTelemetry: boolean;
  /** Event allowlist */
  eventAllowlist?: string[];
  /** Event blocklist */
  eventBlocklist?: string[];
  /** Sampling rate */
  samplingRate?: number;
  /** Require consent */
  requireConsent?: boolean;
}

export interface PluginRuntimePolicy {
  /** Plugin ID */
  pluginId: string;
  /** Plugin version */
  version: string;
  /** Network policy */
  network: NetworkPolicy;
  /** Storage policy */
  storage: StoragePolicy;
  /** Browser API policy */
  browserAPI: BrowserAPIPolicy;
  /** Telemetry policy */
  telemetry: TelemetryPolicy;
  /** Is plugin trusted */
  trusted: boolean;
  /** Sandbox required */
  sandboxRequired: boolean;
  /** Max memory usage in bytes */
  maxMemoryUsage?: number;
  /** Max CPU usage percentage */
  maxCPUUsage?: number;
}

export interface PluginSandboxBoundary {
  /** Boundary ID */
  boundaryId: string;
  /** Plugin ID */
  pluginId: string;
  /** Isolation level */
  isolationLevel: 'none' | 'basic' | 'strict' | 'custom';
  /** Allowed origins */
  allowedOrigins: string[];
  /** Content security policy */
  csp?: string;
  /** Sandbox attributes */
  sandboxAttributes?: string[];
  /** Resource limits */
  resourceLimits: {
    maxMemory?: number;
    maxCPU?: number;
    maxNetworkRequests?: number;
    maxStorage?: number;
  };
}

export interface PluginRuntimeEnvironment {
  readonly policy: PluginRuntimePolicy;
  readonly boundary: PluginSandboxBoundary;
  readonly sandboxAttribute: string;
  fetch: (input: string, init?: RequestInit) => Promise<Response>;
  useStorage: (storageType: 'localStorage' | 'sessionStorage' | 'indexedDB' | 'cookie') => void;
  useBrowserAPI: (api: string) => void;
  emitTelemetry: (eventName: string, payload?: unknown) => boolean;
}

/**
 * Default network policy (restrictive)
 */
export const DEFAULT_NETWORK_POLICY: NetworkPolicy = {
  allowNetworkRequests: false,
  allowedDomains: [],
  blockedDomains: [],
  maxRequestSize: 1024 * 1024, // 1MB
  timeout: 10000, // 10 seconds
};

/**
 * Default storage policy (restrictive)
 */
export const DEFAULT_STORAGE_POLICY: StoragePolicy = {
  allowLocalStorage: false,
  allowSessionStorage: false,
  allowIndexedDB: false,
  allowCookies: false,
  storageQuota: 1024 * 1024, // 1MB
};

/**
 * Default browser API policy (restrictive)
 */
export const DEFAULT_BROWSER_API_POLICY: BrowserAPIPolicy = {
  allowGeolocation: false,
  allowMediaDevices: false,
  allowClipboard: false,
  allowNotifications: false,
  allowFullscreen: false,
  allowWebSockets: false,
  allowedAPIs: [],
};

/**
 * Default telemetry policy (restrictive)
 */
export const DEFAULT_TELEMETRY_POLICY: TelemetryPolicy = {
  allowTelemetry: false,
  eventAllowlist: [],
  eventBlocklist: [],
  samplingRate: 0.1,
  requireConsent: true,
};

/**
 * Create default plugin runtime policy
 */
export function createDefaultPluginRuntimePolicy(pluginId: string, version: string): PluginRuntimePolicy {
  return {
    pluginId,
    version,
    network: DEFAULT_NETWORK_POLICY,
    storage: DEFAULT_STORAGE_POLICY,
    browserAPI: DEFAULT_BROWSER_API_POLICY,
    telemetry: DEFAULT_TELEMETRY_POLICY,
    trusted: false,
    sandboxRequired: true,
    maxMemoryUsage: 50 * 1024 * 1024, // 50MB
    maxCPUUsage: 10, // 10%
  };
}

/**
 * Create trusted plugin runtime policy
 */
export function createTrustedPluginRuntimePolicy(pluginId: string, version: string): PluginRuntimePolicy {
  const policy = createDefaultPluginRuntimePolicy(pluginId, version);
  policy.trusted = true;
  policy.sandboxRequired = false;
  policy.network.allowNetworkRequests = true;
  policy.network.allowedDomains = ['api.yappc.local', 'api.ghatana.com'];
  policy.storage.allowLocalStorage = true;
  policy.storage.allowSessionStorage = true;
  policy.telemetry.allowTelemetry = true;
  policy.telemetry.requireConsent = false;
  return policy;
}

/**
 * Create plugin sandbox boundary
 */
export function createPluginSandboxBoundary(
  pluginId: string,
  isolationLevel: PluginSandboxBoundary['isolationLevel'] = 'basic'
): PluginSandboxBoundary {
  const sandboxAttributes: string[] = ['allow-same-origin'];

  if (isolationLevel === 'basic') {
    sandboxAttributes.push('allow-scripts', 'allow-forms');
  } else if (isolationLevel === 'strict') {
    // No additional attributes - most restrictive
  } else if (isolationLevel === 'custom') {
    sandboxAttributes.push('allow-scripts', 'allow-forms', 'allow-popups');
  }

  return {
    boundaryId: `boundary_${pluginId}`,
    pluginId,
    isolationLevel,
    allowedOrigins: ['self'],
    sandboxAttributes,
    resourceLimits: {
      maxMemory: 50 * 1024 * 1024,
      maxCPU: 10,
      maxNetworkRequests: 100,
      maxStorage: 1024 * 1024,
    },
  };
}

/**
 * Validate plugin runtime policy
 */
export function validatePluginRuntimePolicy(policy: PluginRuntimePolicy): { valid: boolean; errors: string[] } {
  const errors: string[] = [];

  if (!policy.pluginId) {
    errors.push('Plugin ID is required');
  }

  if (!policy.version) {
    errors.push('Plugin version is required');
  }

  if (policy.network.allowNetworkRequests && !policy.network.allowedDomains?.length) {
    errors.push('Network requests allowed but no domains specified');
  }

  if (policy.storage.allowCookies && !policy.storage.allowLocalStorage) {
    errors.push('Cookies require localStorage access');
  }

  if (policy.maxMemoryUsage && policy.maxMemoryUsage < 1024 * 1024) {
    errors.push('Max memory usage must be at least 1MB');
  }

  if (policy.maxCPUUsage && (policy.maxCPUUsage < 1 || policy.maxCPUUsage > 100)) {
    errors.push('Max CPU usage must be between 1% and 100%');
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}

/**
 * Enforce network policy on request
 */
export function enforceNetworkPolicy(
  url: string,
  policy: NetworkPolicy
): { allowed: boolean; reason?: string } {
  if (!policy.allowNetworkRequests) {
    return { allowed: false, reason: 'Network requests not allowed' };
  }

  const parsedUrl = parseNetworkUrl(url);
  if (!parsedUrl) {
    return { allowed: false, reason: 'Invalid URL' };
  }

  if (policy.blockedDomains?.some(domain => matchesAllowedDomain(parsedUrl, domain))) {
    return { allowed: false, reason: 'Domain is blocked' };
  }

  if (policy.allowedDomains?.length > 0) {
    const allowed = policy.allowedDomains.some(domain => matchesAllowedDomain(parsedUrl, domain));
    if (!allowed) {
      return { allowed: false, reason: 'Domain not in allowlist' };
    }
  }

  return { allowed: true };
}

function parseNetworkUrl(url: string): URL | null {
  try {
    return new URL(url);
  } catch {
    return null;
  }
}

function matchesAllowedDomain(url: URL, rule: string): boolean {
  const normalizedRule = rule.trim().toLowerCase();
  if (!normalizedRule) {
    return false;
  }

  if (normalizedRule.includes('://')) {
    try {
      const allowedUrl = new URL(normalizedRule);
      return url.origin === allowedUrl.origin;
    } catch {
      return false;
    }
  }

  // Explicit origin rule: origin:https://example.com
  if (normalizedRule.startsWith('origin:')) {
    const originRule = normalizedRule.slice('origin:'.length).trim();
    if (!originRule) {
      return false;
    }

    try {
      const allowedOrigin = new URL(originRule);
      return url.origin === allowedOrigin.origin;
    } catch {
      return false;
    }
  }

  // Explicit host rule: host:api.example.com
  if (normalizedRule.startsWith('host:')) {
    const hostRule = normalizedRule.slice('host:'.length).trim();
    return hostRule.length > 0 && url.hostname.toLowerCase() === hostRule;
  }

  // Wildcard host rule: *.example.com
  if (normalizedRule.startsWith('*.')) {
    const suffix = normalizedRule.slice(2);
    if (!suffix) {
      return false;
    }
    const hostname = url.hostname.toLowerCase();
    return hostname.endsWith(`.${suffix}`);
  }

  const hostname = url.hostname.toLowerCase();
  // Default rule is exact host match only.
  return hostname === normalizedRule;
}

/**
 * Enforce storage policy
 */
export function enforceStoragePolicy(
  storageType: 'localStorage' | 'sessionStorage' | 'indexedDB' | 'cookie',
  policy: StoragePolicy
): { allowed: boolean; reason?: string } {
  switch (storageType) {
    case 'localStorage':
      return policy.allowLocalStorage
        ? { allowed: true }
        : { allowed: false, reason: 'localStorage not allowed' };
    case 'sessionStorage':
      return policy.allowSessionStorage
        ? { allowed: true }
        : { allowed: false, reason: 'sessionStorage not allowed' };
    case 'indexedDB':
      return policy.allowIndexedDB
        ? { allowed: true }
        : { allowed: false, reason: 'IndexedDB not allowed' };
    case 'cookie':
      return policy.allowCookies
        ? { allowed: true }
        : { allowed: false, reason: 'Cookies not allowed' };
    default:
      return { allowed: false, reason: 'Unknown storage type' };
  }
}

/**
 * Enforce browser API policy
 */
export function enforceBrowserAPIPolicy(
  api: string,
  policy: BrowserAPIPolicy
): { allowed: boolean; reason?: string } {
  if (policy.allowedAPIs?.length > 0 && !policy.allowedAPIs.includes(api)) {
    return { allowed: false, reason: 'API not in allowlist' };
  }

  switch (api) {
    case 'geolocation':
      return policy.allowGeolocation
        ? { allowed: true }
        : { allowed: false, reason: 'Geolocation not allowed' };
    case 'mediaDevices':
      return policy.allowMediaDevices
        ? { allowed: true }
        : { allowed: false, reason: 'Media devices not allowed' };
    case 'clipboard':
      return policy.allowClipboard
        ? { allowed: true }
        : { allowed: false, reason: 'Clipboard not allowed' };
    case 'notifications':
      return policy.allowNotifications
        ? { allowed: true }
        : { allowed: false, reason: 'Notifications not allowed' };
    case 'fullscreen':
      return policy.allowFullscreen
        ? { allowed: true }
        : { allowed: false, reason: 'Fullscreen not allowed' };
    case 'websockets':
      return policy.allowWebSockets
        ? { allowed: true }
        : { allowed: false, reason: 'WebSockets not allowed' };
    default:
      // Unknown APIs are denied by default. Plugins must explicitly declare
      // every browser API they need in the allowedAPIs allowlist.
      return { allowed: false, reason: `Unknown browser API '${api}' is not permitted. Add it to the allowedAPIs allowlist to request access.` };
  }
}

/**
 * Enforce telemetry policy
 */
export function enforceTelemetryPolicy(
  eventName: string,
  policy: TelemetryPolicy
): { allowed: boolean; reason?: string } {
  if (!policy.allowTelemetry) {
    return { allowed: false, reason: 'Telemetry not allowed' };
  }

  if (policy.eventBlocklist?.includes(eventName)) {
    return { allowed: false, reason: 'Event is blocked' };
  }

  if (policy.eventAllowlist?.length > 0 && !policy.eventAllowlist.includes(eventName)) {
    return { allowed: false, reason: 'Event not in allowlist' };
  }

  if (policy.samplingRate !== undefined && Math.random() > policy.samplingRate) {
    return { allowed: false, reason: 'Event not sampled' };
  }

  return { allowed: true };
}

/**
 * Generate sandbox attribute string
 */
export function generateSandboxAttribute(boundary: PluginSandboxBoundary): string {
  return boundary.sandboxAttributes?.join(' ') || '';
}

export function createPluginRuntimeEnvironment(
  policy: PluginRuntimePolicy,
  options?: {
    readonly boundary?: PluginSandboxBoundary;
    readonly fetchImpl?: typeof fetch;
    readonly telemetryEmitter?: (eventName: string, payload?: unknown) => void;
  }
): PluginRuntimeEnvironment {
  const boundary =
    options?.boundary ??
    createPluginSandboxBoundary(policy.pluginId, policy.sandboxRequired ? 'basic' : 'none');
  const fetchImpl = options?.fetchImpl ?? fetch;

  return {
    policy,
    boundary,
    sandboxAttribute: generateSandboxAttribute(boundary),
    async fetch(input: string, init?: RequestInit) {
      const decision = enforceNetworkPolicy(input, policy.network);
      if (!decision.allowed) {
        throw new Error(`Plugin network request blocked: ${decision.reason}`);
      }
      return fetchImpl(input, init);
    },
    useStorage(storageType) {
      const decision = enforceStoragePolicy(storageType, policy.storage);
      if (!decision.allowed) {
        throw new Error(`Plugin storage access blocked: ${decision.reason}`);
      }
    },
    useBrowserAPI(api) {
      const decision = enforceBrowserAPIPolicy(api, policy.browserAPI);
      if (!decision.allowed) {
        throw new Error(`Plugin browser API blocked: ${decision.reason}`);
      }
    },
    emitTelemetry(eventName, payload) {
      const decision = enforceTelemetryPolicy(eventName, policy.telemetry);
      if (!decision.allowed) {
        return false;
      }
      options?.telemetryEmitter?.(eventName, payload);
      return true;
    },
  };
}

export default {
  DEFAULT_NETWORK_POLICY,
  DEFAULT_STORAGE_POLICY,
  DEFAULT_BROWSER_API_POLICY,
  DEFAULT_TELEMETRY_POLICY,
  createDefaultPluginRuntimePolicy,
  createTrustedPluginRuntimePolicy,
  createPluginSandboxBoundary,
  validatePluginRuntimePolicy,
  enforceNetworkPolicy,
  enforceStoragePolicy,
  enforceBrowserAPIPolicy,
  enforceTelemetryPolicy,
  generateSandboxAttribute,
  createPluginRuntimeEnvironment,
};
