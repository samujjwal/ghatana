/**
 * Input Sanitizer Middleware
 *
 * Protects AI-facing endpoints from prompt injection, XSS, and
 * malicious payloads. Applied as a Fastify preHandler hook.
 *
 * @doc.type middleware
 * @doc.purpose Sanitize user inputs before they reach AI services
 * @doc.layer security
 * @doc.pattern Middleware
 */

import { type FastifyInstance, type FastifyRequest, type FastifyReply } from 'fastify';

// ============================================================================
// Configuration
// ============================================================================

export interface SanitizerConfig {
    /** Max input string length before rejection */
    maxInputLength: number;
    /** Enable prompt injection detection */
    detectPromptInjection: boolean;
    /** Enable HTML/script tag stripping */
    stripHtml: boolean;
    /** Paths to protect (glob-style prefixes). Empty = all paths. */
    protectedPaths: string[];
    /** Paths to skip sanitization */
    excludedPaths: string[];
    /** Action on detection: 'reject' returns 400, 'strip' removes dangerous content */
    onDetection: 'reject' | 'strip';
}

const DEFAULT_CONFIG: SanitizerConfig = {
    maxInputLength: 10_000,
    detectPromptInjection: true,
    stripHtml: true,
    protectedPaths: ['/api/v1/ai/', '/api/v1/content/', '/api/v1/experiences/'],
    excludedPaths: ['/health', '/metrics', '/api/v1/auth/'],
    onDetection: 'reject',
};

// ============================================================================
// Prompt injection patterns
// ============================================================================

/**
 * Known prompt injection patterns.
 * These detect attempts to override system prompts or extract system instructions.
 */
const INJECTION_PATTERNS: Array<{ pattern: RegExp; label: string; severity: 'high' | 'medium' }> = [
    // Direct system prompt overrides
    { pattern: /ignore\s+(all\s+)?(previous|prior|above)\s+(instructions|prompts|rules)/i, label: 'system_override', severity: 'high' },
    { pattern: /disregard\s+(all\s+)?(previous|prior|above)\s+(instructions|prompts|rules)/i, label: 'system_override', severity: 'high' },
    { pattern: /forget\s+(all\s+)?(previous|prior|above)\s+(instructions|prompts|rules)/i, label: 'system_override', severity: 'high' },
    { pattern: /you\s+are\s+now\s+(a|an)\s+/i, label: 'role_hijack', severity: 'high' },
    { pattern: /act\s+as\s+(if\s+)?(you\s+are|a|an)\s+/i, label: 'role_hijack', severity: 'medium' },
    { pattern: /pretend\s+(to\s+be|you\s+are)/i, label: 'role_hijack', severity: 'medium' },

    // System prompt extraction
    { pattern: /repeat\s+(your|the)\s+(system\s+)?(prompt|instructions|rules)/i, label: 'prompt_extraction', severity: 'high' },
    { pattern: /what\s+(are|is)\s+your\s+(system\s+)?(prompt|instructions|rules)/i, label: 'prompt_extraction', severity: 'high' },
    { pattern: /show\s+(me\s+)?(your|the)\s+(system\s+)?(prompt|instructions)/i, label: 'prompt_extraction', severity: 'high' },
    { pattern: /print\s+(your|the)\s+(system\s+)?(prompt|instructions)/i, label: 'prompt_extraction', severity: 'high' },

    // Delimiter injection (trying to break out of user content)
    { pattern: /```\s*system/i, label: 'delimiter_injection', severity: 'high' },
    { pattern: /<\|system\|>/i, label: 'delimiter_injection', severity: 'high' },
    { pattern: /\[SYSTEM\]/i, label: 'delimiter_injection', severity: 'medium' },
    { pattern: /\[INST\]/i, label: 'delimiter_injection', severity: 'medium' },
    { pattern: /<<SYS>>/i, label: 'delimiter_injection', severity: 'high' },

    // Encoding-based evasion
    { pattern: /&#x[0-9a-fA-F]+;/g, label: 'hex_encoding', severity: 'medium' },
    { pattern: /\\u[0-9a-fA-F]{4}/g, label: 'unicode_escape', severity: 'medium' },
];

/**
 * HTML/script patterns to strip.
 */
const HTML_PATTERNS: RegExp[] = [
    /<script[\s\S]*?<\/script>/gi,
    /<iframe[\s\S]*?<\/iframe>/gi,
    /<object[\s\S]*?<\/object>/gi,
    /<embed[\s\S]*?>/gi,
    /on\w+\s*=\s*["'][^"']*["']/gi,
    /javascript:/gi,
    /data:text\/html/gi,
    /<style[\s\S]*?<\/style>/gi,
];

// ============================================================================
// Detection result
// ============================================================================

export interface SanitizationResult {
    clean: boolean;
    threats: Array<{
        label: string;
        severity: 'high' | 'medium';
        field: string;
        snippet: string;
    }>;
    sanitizedValue?: string;
}

// ============================================================================
// Core sanitization functions
// ============================================================================

/**
 * Check a single string value for prompt injection patterns.
 */
export function detectInjection(value: string, fieldPath: string): SanitizationResult {
    const threats: SanitizationResult['threats'] = [];

    for (const { pattern, label, severity } of INJECTION_PATTERNS) {
        // Reset lastIndex for global patterns
        pattern.lastIndex = 0;
        const match = pattern.exec(value);
        if (match) {
            const start = Math.max(0, match.index - 20);
            const end = Math.min(value.length, match.index + match[0].length + 20);
            threats.push({
                label,
                severity,
                field: fieldPath,
                snippet: value.slice(start, end),
            });
        }
    }

    return {
        clean: threats.length === 0,
        threats,
    };
}

/**
 * Strip HTML and dangerous tags from a string.
 */
export function stripHtmlContent(value: string): string {
    let result = value;
    for (const pattern of HTML_PATTERNS) {
        result = result.replace(pattern, '');
    }
    // Strip remaining HTML tags but keep content
    result = result.replace(/<[^>]*>/g, '');
    return result;
}

/**
 * Recursively scan an object for injection in all string fields.
 */
function scanObject(
    obj: unknown,
    config: SanitizerConfig,
    path: string = '',
): SanitizationResult {
    const allThreats: SanitizationResult['threats'] = [];

    if (typeof obj === 'string') {
        // Length check
        if (obj.length > config.maxInputLength) {
            allThreats.push({
                label: 'input_too_long',
                severity: 'medium',
                field: path || 'value',
                snippet: `length=${obj.length}, max=${config.maxInputLength}`,
            });
        }

        // Prompt injection detection
        if (config.detectPromptInjection) {
            const result = detectInjection(obj, path || 'value');
            allThreats.push(...result.threats);
        }

        return { clean: allThreats.length === 0, threats: allThreats };
    }

    if (Array.isArray(obj)) {
        for (let i = 0; i < obj.length; i++) {
            const child = scanObject(obj[i], config, `${path}[${i}]`);
            allThreats.push(...child.threats);
        }
        return { clean: allThreats.length === 0, threats: allThreats };
    }

    if (obj && typeof obj === 'object') {
        for (const [key, val] of Object.entries(obj)) {
            const child = scanObject(val, config, path ? `${path}.${key}` : key);
            allThreats.push(...child.threats);
        }
        return { clean: allThreats.length === 0, threats: allThreats };
    }

    return { clean: true, threats: [] };
}

/**
 * Recursively strip HTML from all string fields in an object.
 */
function stripObjectHtml(obj: any): any {
    if (typeof obj === 'string') {
        return stripHtmlContent(obj);
    }
    if (Array.isArray(obj)) {
        return obj.map(stripObjectHtml);
    }
    if (obj && typeof obj === 'object') {
        const result: any = {};
        for (const [key, val] of Object.entries(obj)) {
            result[key] = stripObjectHtml(val);
        }
        return result;
    }
    return obj;
}

// ============================================================================
// Fastify middleware
// ============================================================================

/**
 * Register the input sanitizer as a Fastify preHandler hook.
 */
export async function setupInputSanitizer(
    app: FastifyInstance,
    userConfig?: Partial<SanitizerConfig>,
): Promise<void> {
    const config: SanitizerConfig = { ...DEFAULT_CONFIG, ...userConfig };

    app.addHook('preHandler', async (request: FastifyRequest, reply: FastifyReply) => {
        // Check if path should be processed
        const url = request.url;

        // Skip excluded paths
        if (config.excludedPaths.some((p) => url.startsWith(p))) {
            return;
        }

        // Only process protected paths (if specified)
        if (
            config.protectedPaths.length > 0 &&
            !config.protectedPaths.some((p) => url.startsWith(p))
        ) {
            return;
        }

        // Scan request body
        if (request.body && typeof request.body === 'object') {
            const scanResult = scanObject(request.body, config, 'body');

            if (!scanResult.clean) {
                const highSeverity = scanResult.threats.filter((t) => t.severity === 'high');

                // Always reject high-severity threats
                if (highSeverity.length > 0 || config.onDetection === 'reject') {
                    request.log.warn(
                        {
                            url,
                            threats: scanResult.threats.map((t) => ({
                                label: t.label,
                                severity: t.severity,
                                field: t.field,
                            })),
                        },
                        'Input sanitizer: malicious content detected',
                    );

                    return reply.status(400).send({
                        error: 'Bad Request',
                        message: 'Input contains potentially harmful content',
                        code: 'INPUT_SANITIZATION_FAILED',
                    });
                }

                // Strip mode: sanitize and continue
                if (config.onDetection === 'strip') {
                    request.log.info(
                        { url, threatCount: scanResult.threats.length },
                        'Input sanitizer: stripping detected threats',
                    );
                }
            }

            // Strip HTML if enabled (even if no injection detected)
            if (config.stripHtml) {
                (request as any).body = stripObjectHtml(request.body);
            }
        }

        // Scan query string parameters
        if (request.query && typeof request.query === 'object') {
            const queryScan = scanObject(request.query, config, 'query');
            if (!queryScan.clean) {
                const highSeverity = queryScan.threats.filter((t) => t.severity === 'high');
                if (highSeverity.length > 0) {
                    request.log.warn(
                        { url, threats: queryScan.threats },
                        'Input sanitizer: malicious query params detected',
                    );
                    return reply.status(400).send({
                        error: 'Bad Request',
                        message: 'Query parameters contain potentially harmful content',
                        code: 'INPUT_SANITIZATION_FAILED',
                    });
                }
            }
        }
    });
}
