/**
 * Configuration describing how values should be redacted or sanitised before
 * they leave the extension. The defaults are intentionally conservative and
 * align with the values referenced in the unified architecture guide.
 */
export interface RedactionConfig {
  sanitizeUrls: boolean;
  sanitizeQueries: boolean;
  redactPII: boolean;
  samplingRate: number;
  piiTokens: string[];
  sensitiveParams: string[];
  allowedDomains: string[];
}

export interface RedactionFlags {
  /** Indicates whether the input was ultimately retained after sampling rules */
  sampled: boolean;
  /** True when any PII pattern was detected */
  piiDetected: boolean;
  /** Set of property paths that were modified */
  sanitisedFields: string[];
  /** True when the input was mutated in any way */
  modified: boolean;
}

export interface RedactionResult<T> {
  redacted: T;
  flags: RedactionFlags;
}

// The default configuration mirrors the privacy defaults in DEFAULT_CONFIG but
// lives here to avoid circular dependencies between config and core modules.
export const DEFAULT_REDACTION_CONFIG: Readonly<RedactionConfig> = deepFreeze({
  sanitizeUrls: true,
  sanitizeQueries: true,
  redactPII: true,
  samplingRate: 0.1,
  piiTokens: [],
  sensitiveParams: [
    'password',
    'passwd',
    'pwd',
    'secret',
    'token',
    'auth',
    'key',
    'session',
    'sid',
    'cookie',
    'csrf',
    'api_key',
    'access_token',
    'refresh_token',
    'bearer',
    'authorization',
    'signature',
    'hash',
    'salt',
    'nonce',
    'email',
    'username',
    'user',
    'userid',
    'uid',
    'ssn',
    'social',
    'phone',
    'mobile',
    'address',
    'zip',
    'postal',
  ],
  allowedDomains: [],
});

const EMAIL_REGEX = /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi;
const SSN_REGEX = /\b\d{3}-?\d{2}-?\d{4}\b/g;
const CREDIT_CARD_REGEX = /\b(?:\d[ -]*?){13,16}\b/g;
const PHONE_REGEX = /\b(?:\+?1[-.\s]?)?(?:\(?\d{3}\)?[-.\s]?){2}\d{4}\b/g;

type RedactionContext = {
  piiDetected: boolean;
  modified: boolean;
  sanitisedPaths: Set<string>;
};

/**
 * PrivacyRedactor performs recursive sanitisation and redaction of payloads.
 * It never mutates the input value and instead returns a deeply cloned copy.
 */
export class PrivacyRedactor {
  private readonly config: RedactionConfig;

  constructor(config: RedactionConfig = DEFAULT_REDACTION_CONFIG) {
    this.config = { ...config };
  }

  redact<T>(input: T): RedactionResult<T> {
    const context: RedactionContext = {
      piiDetected: false,
      modified: false,
      sanitisedPaths: new Set<string>(),
    };

    const redacted = this.processValue(input, [], context);

    return {
      redacted,
      flags: {
        sampled: true,
        piiDetected: context.piiDetected,
        modified: context.modified,
        sanitisedFields: Array.from(context.sanitisedPaths),
      },
    };
  }

  private processValue<T>(value: T, path: string[], ctx: RedactionContext): T {
    if (value === null || value === undefined) {
      return value;
    }

    if (Array.isArray(value)) {
      let mutated = false;
      const cloned = value.map((item, index) => {
        const next = this.processValue(item, [...path, String(index)], ctx);
        mutated = mutated || next !== item;
        return next;
      });
      if (mutated) {
        ctx.modified = true;
        ctx.sanitisedPaths.add(path.join('.') || '[root]');
      }
      return (mutated ? cloned : value) as T;
    }

    if (typeof value === 'object') {
      const source = value as Record<string, unknown>;
      let mutated = false;
      const clone: Record<string, unknown> | unknown[] = Array.isArray(value) ? [] : {};

      for (const [key, original] of Object.entries(source)) {
        const lowerKey = key.toLowerCase();
        const currentPath = [...path, key];

        if (this.config.sensitiveParams.some((param) => param.toLowerCase() === lowerKey)) {
          ctx.modified = true;
          ctx.piiDetected = true;
          ctx.sanitisedPaths.add(currentPath.join('.'));
          (clone as Record<string, unknown>)[key] = '[REDACTED]';
          mutated = true;
          continue;
        }

        const processed = this.processValue(original, currentPath, ctx);
        (clone as Record<string, unknown>)[key] = processed;
        mutated = mutated || processed !== original;
      }

      if (!mutated) {
        return value;
      }

      return clone as T;
    }

    if (typeof value === 'string') {
      const [sanitisedValue, metadata] = this.sanitiseString(value);
      if (metadata.modified) {
        ctx.modified = true;
        metadata.paths.forEach((p) => ctx.sanitisedPaths.add(path.concat(p).join('.')));
      }
      ctx.piiDetected = ctx.piiDetected || metadata.piiDetected;
      return sanitisedValue as unknown as T;
    }

    return value;
  }

  private sanitiseString(
    value: string
  ): [string, { piiDetected: boolean; modified: boolean; paths: string[] }] {
    let output = value;
    let modified = false;
    let piiDetected = false;
    const paths: string[] = ['[value]'];

    if (this.config.redactPII) {
      const result = this.replaceIfMatch(output, EMAIL_REGEX, '[REDACTED_EMAIL]');
      output = result.value;
      piiDetected = piiDetected || result.modified;
      modified = modified || result.modified;

      const ssn = this.replaceIfMatch(output, SSN_REGEX, '[REDACTED_SSN]');
      output = ssn.value;
      piiDetected = piiDetected || ssn.modified;
      modified = modified || ssn.modified;

      const cc = this.replaceIfMatch(output, CREDIT_CARD_REGEX, '[REDACTED_CC]');
      output = cc.value;
      piiDetected = piiDetected || cc.modified;
      modified = modified || cc.modified;

      const phone = this.replaceIfMatch(output, PHONE_REGEX, '[REDACTED_PHONE]');
      output = phone.value;
      piiDetected = piiDetected || phone.modified;
      modified = modified || phone.modified;
    }

    if (this.config.piiTokens.length > 0) {
      for (const token of this.config.piiTokens) {
        if (!token) continue;
        const pattern = new RegExp(token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'gi');
        const masked = this.replaceIfMatch(output, pattern, '[REDACTED_TOKEN]');
        output = masked.value;
        modified = modified || masked.modified;
        piiDetected = piiDetected || masked.modified;
      }
    }

    if (this.config.sanitizeUrls) {
      const urlResult = this.sanitiseUrl(output);
      if (urlResult.modified) {
        output = urlResult.value;
        modified = true;
        if (urlResult.piiDetected) {
          piiDetected = true;
        }
      }
    }

    return [output, { piiDetected, modified, paths }];
  }

  private sanitiseUrl(value: string): { value: string; modified: boolean; piiDetected: boolean } {
    try {
      const url = new URL(value);
      const allowed = this.config.allowedDomains.map((domain) => domain.toLowerCase());
      const hostAllowed = allowed.length === 0 || allowed.includes(url.hostname.toLowerCase());

      let modified = false;
      const cloned = new URL(url.toString());
      const piiDetected = false;

      if (!hostAllowed) {
        // Strip query parameters and fragments for non-allowed hosts
        cloned.search = '';
        cloned.hash = '';
        modified = true;
      } else if (this.config.sanitizeQueries && cloned.searchParams.size > 0) {
        for (const key of cloned.searchParams.keys()) {
          cloned.searchParams.set(key, '[REDACTED]');
        }
        modified = true;
      }

      return {
        value: modified ? cloned.toString() : value,
        modified,
        piiDetected,
      };
    } catch {
      return { value, modified: false, piiDetected: false };
    }
  }

  private replaceIfMatch(
    value: string,
    pattern: RegExp,
    replacement: string
  ): { value: string; modified: boolean } {
    if (!pattern.test(value)) {
      pattern.lastIndex = 0; // reset last index for global regex
      return { value, modified: false };
    }
    pattern.lastIndex = 0;
    return { value: value.replace(pattern, replacement), modified: true };
  }
}

export function createPrivacyRedactor(config?: Partial<RedactionConfig>): PrivacyRedactor {
  if (!config) {
    return new PrivacyRedactor(DEFAULT_REDACTION_CONFIG);
  }

  return new PrivacyRedactor({
    ...DEFAULT_REDACTION_CONFIG,
    ...config,
    piiTokens: config.piiTokens ?? DEFAULT_REDACTION_CONFIG.piiTokens,
    sensitiveParams: config.sensitiveParams ?? DEFAULT_REDACTION_CONFIG.sensitiveParams,
    allowedDomains: config.allowedDomains ?? DEFAULT_REDACTION_CONFIG.allowedDomains,
  });
}

export const defaultRedactor = new PrivacyRedactor(DEFAULT_REDACTION_CONFIG);

function deepFreeze<T>(value: T): T {
  if (value && typeof value === 'object' && !Object.isFrozen(value)) {
    Object.freeze(value);
    for (const key of Object.keys(value as Record<string, unknown>)) {
      const nested = (value as Record<string, unknown>)[key];
      if (nested && typeof nested === 'object' && !Object.isFrozen(nested)) {
        deepFreeze(nested);
      }
    }
  }
  return value;
}
