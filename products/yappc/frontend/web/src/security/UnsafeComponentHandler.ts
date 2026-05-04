/**
 * Unsafe Custom Component Handler
 *
 * Handles unsafe custom components with:
 * - Fallback by default
 * - Review for interactive components
 * - Block browser APIs
 *
 * @doc.type security
 * @doc.purpose Unsafe component handling
 * @doc.layer product
 */

export interface ComponentSafetyAssessment {
  /** Component ID */
  componentId: string;
  /** Whether component is safe */
  isSafe: boolean;
  /** Safety level */
  safetyLevel: 'safe' | 'risky' | 'unsafe';
  /** Risk factors */
  riskFactors: string[];
  /** Recommended action */
  recommendedAction: 'allow' | 'fallback' | 'block';
  /** Review required */
  reviewRequired: boolean;
}

export interface ComponentPolicy {
  /** Allow browser APIs */
  allowBrowserAPIs: boolean;
  /** Allowed APIs list */
  allowedAPIs?: string[];
  /** Allow external network requests */
  allowNetworkRequests: boolean;
  /** Allow local storage access */
  allowLocalStorage: boolean;
  /** Allow session storage access */
  allowSessionStorage: boolean;
  /** Allow cookies access */
  allowCookies: boolean;
  /** Allow eval */
  allowEval: boolean;
  /** Allow inline scripts */
  allowInlineScripts: boolean;
}

/**
 * Risk patterns for component assessment
 */
const RISK_PATTERNS = {
  // Browser APIs
  browserAPIs: [
    'window.fetch',
    'window.XMLHttpRequest',
    'window.WebSocket',
    'window.postMessage',
    'navigator.geolocation',
    'navigator.mediaDevices',
    'navigator.clipboard',
    'localStorage',
    'sessionStorage',
    'document.cookie',
  ],
  // Dangerous functions
  dangerousFunctions: ['eval', 'Function', 'setTimeout', 'setInterval'],
  // Network patterns
  networkPatterns: ['fetch(', 'XMLHttpRequest', 'WebSocket', 'http://', 'https://'],
  // Storage patterns
  storagePatterns: ['localStorage', 'sessionStorage', 'indexedDB'],
};

/**
 * Assess component safety
 */
export function assessComponentSafety(
  componentCode: string,
  componentId: string
): ComponentSafetyAssessment {
  const riskFactors: string[] = [];
  let safetyLevel: 'safe' | 'risky' | 'unsafe' = 'safe';

  // Check for browser APIs
  for (const api of RISK_PATTERNS.browserAPIs) {
    if (componentCode.includes(api)) {
      riskFactors.push(`Uses browser API: ${api}`);
      safetyLevel = 'risky';
    }
  }

  // Check for dangerous functions
  for (const fn of RISK_PATTERNS.dangerousFunctions) {
    if (componentCode.includes(fn)) {
      riskFactors.push(`Uses dangerous function: ${fn}`);
      safetyLevel = 'unsafe';
    }
  }

  // Check for network patterns
  for (const pattern of RISK_PATTERNS.networkPatterns) {
    if (componentCode.includes(pattern)) {
      riskFactors.push(`Network access detected: ${pattern}`);
      safetyLevel = 'risky';
    }
  }

  // Check for storage patterns
  for (const pattern of RISK_PATTERNS.storagePatterns) {
    if (componentCode.includes(pattern)) {
      riskFactors.push(`Storage access detected: ${pattern}`);
      safetyLevel = 'risky';
    }
  }

  // Determine recommended action
  let recommendedAction: 'allow' | 'fallback' | 'block';
  let reviewRequired = false;

  if (safetyLevel === 'unsafe') {
    recommendedAction = 'block';
    reviewRequired = true;
  } else if (safetyLevel === 'risky') {
    recommendedAction = 'fallback';
    reviewRequired = true;
  } else {
    recommendedAction = 'allow';
    reviewRequired = false;
  }

  return {
    componentId,
    isSafe: safetyLevel === 'safe',
    safetyLevel,
    riskFactors,
    recommendedAction,
    reviewRequired,
  };
}

/**
 * Get default component policy
 */
export function getDefaultComponentPolicy(): ComponentPolicy {
  return {
    allowBrowserAPIs: false,
    allowedAPIs: [],
    allowNetworkRequests: false,
    allowLocalStorage: false,
    allowSessionStorage: false,
    allowCookies: false,
    allowEval: false,
    allowInlineScripts: false,
  };
}

/**
 * Get policy for trusted components
 */
export function getTrustedComponentPolicy(): ComponentPolicy {
  return {
    allowBrowserAPIs: true,
    allowedAPIs: ['window.fetch', 'window.XMLHttpRequest'],
    allowNetworkRequests: true,
    allowLocalStorage: true,
    allowSessionStorage: true,
    allowCookies: false, // Still block cookies
    allowEval: false, // Still block eval
    allowInlineScripts: false, // Still block inline scripts
  };
}

/**
 * Apply component policy to code
 */
export function applyComponentPolicy(
  code: string,
  policy: ComponentPolicy
): string {
  let modifiedCode = code;

  // Block eval if not allowed
  if (!policy.allowEval) {
    modifiedCode = modifiedCode.replace(/\beval\s*\(/g, '/* eval blocked */');
  }

  // Block Function constructor if not allowed
  if (!policy.allowEval) {
    modifiedCode = modifiedCode.replace(/\bnew\s+Function\s*\(/g, '/* Function blocked */');
  }

  // Block localStorage if not allowed
  if (!policy.allowLocalStorage) {
    modifiedCode = modifiedCode.replace(/\blocalStorage\./g, '/* localStorage blocked */');
  }

  // Block sessionStorage if not allowed
  if (!policy.allowSessionStorage) {
    modifiedCode = modifiedCode.replace(/\bsessionStorage\./g, '/* sessionStorage blocked */');
  }

  // Block cookies if not allowed
  if (!policy.allowCookies) {
    modifiedCode = modifiedCode.replace(/\bdocument\.cookie\b/g, '/* cookie access blocked */');
  }

  // Filter browser APIs
  if (!policy.allowBrowserAPIs && policy.allowedAPIs) {
    RISK_PATTERNS.browserAPIs.forEach(api => {
      if (!policy.allowedAPIs!.includes(api)) {
        modifiedCode = modifiedCode.replace(new RegExp(api.replace('.', '\\.'), 'g'), `/* ${api} blocked */`);
      }
    });
  }

  return modifiedCode;
}

export default {
  assessComponentSafety,
  getDefaultComponentPolicy,
  getTrustedComponentPolicy,
  applyComponentPolicy,
};
