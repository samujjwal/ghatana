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

import * as ts from 'typescript';

export interface ComponentSafetyAssessment {
  componentId: string;
  isSafe: boolean;
  safetyLevel: 'safe' | 'risky' | 'unsafe';
  riskFactors: string[];
  recommendedAction: 'allow' | 'fallback' | 'block';
  reviewRequired: boolean;
}

export interface ComponentPolicy {
  allowBrowserAPIs: boolean;
  allowedAPIs?: string[];
  allowNetworkRequests: boolean;
  allowLocalStorage: boolean;
  allowSessionStorage: boolean;
  allowCookies: boolean;
  allowEval: boolean;
  allowInlineScripts: boolean;
}

type RiskSeverity = 'risky' | 'unsafe';
type RiskKind =
  | 'browser-api'
  | 'dangerous-call'
  | 'network'
  | 'storage'
  | 'inline-script';

interface RiskMatch {
  readonly kind: RiskKind;
  readonly api: string;
  readonly severity: RiskSeverity;
  readonly message: string;
  readonly start: number;
  readonly end: number;
}

interface Replacement {
  readonly start: number;
  readonly end: number;
  readonly text: string;
}

const BROWSER_API_RISKS = new Map<string, string>([
  ['window.fetch', 'Uses browser API: window.fetch'],
  ['window.XMLHttpRequest', 'Uses browser API: window.XMLHttpRequest'],
  ['window.WebSocket', 'Uses browser API: window.WebSocket'],
  ['window.postMessage', 'Uses browser API: window.postMessage'],
  ['navigator.geolocation', 'Uses browser API: navigator.geolocation'],
  ['navigator.mediaDevices', 'Uses browser API: navigator.mediaDevices'],
  ['navigator.clipboard', 'Uses browser API: navigator.clipboard'],
  ['localStorage', 'Uses browser API: localStorage'],
  ['sessionStorage', 'Uses browser API: sessionStorage'],
  ['indexedDB', 'Uses browser API: indexedDB'],
  ['document.cookie', 'Uses browser API: document.cookie'],
]);

const NETWORK_RISKS = new Map<string, string>([
  ['fetch', 'Network access detected: fetch'],
  ['window.fetch', 'Network access detected: window.fetch'],
  ['XMLHttpRequest', 'Network access detected: XMLHttpRequest'],
  ['window.XMLHttpRequest', 'Network access detected: window.XMLHttpRequest'],
  ['WebSocket', 'Network access detected: WebSocket'],
  ['window.WebSocket', 'Network access detected: window.WebSocket'],
  ['http-url', 'Network access detected: http://'],
  ['https-url', 'Network access detected: https://'],
]);

const STORAGE_RISKS = new Map<string, string>([
  ['localStorage', 'Storage access detected: localStorage'],
  ['sessionStorage', 'Storage access detected: sessionStorage'],
  ['indexedDB', 'Storage access detected: indexedDB'],
  ['document.cookie', 'Storage access detected: document.cookie'],
]);

const DIRECT_BLOCKED_EXPRESSIONS = new Set(['eval', 'Function', 'window.eval', 'globalThis.eval']);

export function assessComponentSafety(
  componentCode: string,
  componentId: string
): ComponentSafetyAssessment {
  const matches = analyzeComponentCode(componentCode);
  const riskFactors = matches.map((match) => match.message);
  const safetyLevel = matches.some((match) => match.severity === 'unsafe')
    ? 'unsafe'
    : matches.length > 0
      ? 'risky'
      : 'safe';

  return {
    componentId,
    isSafe: safetyLevel === 'safe',
    safetyLevel,
    riskFactors,
    recommendedAction:
      safetyLevel === 'unsafe' ? 'block' : safetyLevel === 'risky' ? 'fallback' : 'allow',
    reviewRequired: safetyLevel !== 'safe',
  };
}

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

export function getTrustedComponentPolicy(): ComponentPolicy {
  return {
    allowBrowserAPIs: true,
    allowedAPIs: ['window.fetch', 'window.XMLHttpRequest'],
    allowNetworkRequests: true,
    allowLocalStorage: true,
    allowSessionStorage: true,
    allowCookies: false,
    allowEval: false,
    allowInlineScripts: false,
  };
}

export function applyComponentPolicy(code: string, policy: ComponentPolicy): string {
  const matches = analyzeComponentCode(code);
  const replacements: Replacement[] = [];
  const requiredGuards = new Set<string>();

  for (const match of matches) {
    if (shouldAllowMatch(match, policy)) {
      continue;
    }

    if (match.kind === 'dangerous-call' && !policy.allowEval) {
      replacements.push({
        start: match.start,
        end: match.end,
        text: `(() => { throw new Error(${JSON.stringify(`Blocked unsafe API: ${match.api}`)}); })()`,
      });
      continue;
    }

    if (match.kind === 'inline-script' && !policy.allowInlineScripts) {
      replacements.push({
        start: match.start,
        end: match.end,
        text: '{null}',
      });
      continue;
    }

    const guard = guardNameForApi(match.api);
    if (guard) {
      requiredGuards.add(guard);
    }
  }

  const transformedCode = applyReplacements(code, replacements);
  const prelude = buildPolicyPrelude(policy, requiredGuards);
  return prelude ? `${prelude}\n${transformedCode}` : transformedCode;
}

function analyzeComponentCode(componentCode: string): RiskMatch[] {
  const sourceFile = ts.createSourceFile(
    'unsafe-component.tsx',
    componentCode,
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TSX
  );

  const matches = new Map<string, RiskMatch>();
  const addMatch = (match: RiskMatch) => {
    matches.set(`${match.kind}:${match.api}:${match.start}:${match.end}`, match);
  };

  const visit = (node: ts.Node) => {
    if (ts.isCallExpression(node)) {
      const expressionText = normalizeExpression(node.expression, sourceFile);
      if (DIRECT_BLOCKED_EXPRESSIONS.has(expressionText)) {
        addMatch({
          kind: 'dangerous-call',
          api: expressionText === 'Function' ? 'Function' : 'eval',
          severity: 'unsafe',
          message: `Uses dangerous function: ${expressionText === 'Function' ? 'Function' : 'eval'}`,
          start: node.getStart(sourceFile),
          end: node.getEnd(),
        });
      }

      if (NETWORK_RISKS.has(expressionText)) {
        addMatch({
          kind: 'network',
          api: expressionText,
          severity: 'risky',
          message: NETWORK_RISKS.get(expressionText)!,
          start: node.expression.getStart(sourceFile),
          end: node.expression.getEnd(),
        });
      }

      if ((expressionText === 'setTimeout' || expressionText === 'setInterval') && node.arguments.length > 0) {
        addMatch({
          kind: 'dangerous-call',
          api: expressionText,
          severity: 'unsafe',
          message: `Uses dangerous function: ${expressionText}`,
          start: node.getStart(sourceFile),
          end: node.getEnd(),
        });
      }
    }

    if (ts.isNewExpression(node)) {
      const expressionText = normalizeExpression(node.expression, sourceFile);
      if (expressionText === 'Function') {
        addMatch({
          kind: 'dangerous-call',
          api: 'Function',
          severity: 'unsafe',
          message: 'Uses dangerous function: Function',
          start: node.getStart(sourceFile),
          end: node.getEnd(),
        });
      }

      if (NETWORK_RISKS.has(expressionText)) {
        addMatch({
          kind: 'network',
          api: expressionText,
          severity: 'risky',
          message: NETWORK_RISKS.get(expressionText)!,
          start: node.expression.getStart(sourceFile),
          end: node.expression.getEnd(),
        });
      }
    }

    if (ts.isPropertyAccessExpression(node) || ts.isIdentifier(node)) {
      const expressionText = normalizeExpression(node, sourceFile);

      if (BROWSER_API_RISKS.has(expressionText)) {
        addMatch({
          kind: 'browser-api',
          api: expressionText,
          severity: 'risky',
          message: BROWSER_API_RISKS.get(expressionText)!,
          start: node.getStart(sourceFile),
          end: node.getEnd(),
        });
      }

      if (STORAGE_RISKS.has(expressionText)) {
        addMatch({
          kind: 'storage',
          api: expressionText,
          severity: 'risky',
          message: STORAGE_RISKS.get(expressionText)!,
          start: node.getStart(sourceFile),
          end: node.getEnd(),
        });
      }
    }

    if (ts.isStringLiteralLike(node)) {
      if (node.text.includes('http://')) {
        addMatch({
          kind: 'network',
          api: 'http-url',
          severity: 'risky',
          message: NETWORK_RISKS.get('http-url')!,
          start: node.getStart(sourceFile),
          end: node.getEnd(),
        });
      }
      if (node.text.includes('https://')) {
        addMatch({
          kind: 'network',
          api: 'https-url',
          severity: 'risky',
          message: NETWORK_RISKS.get('https-url')!,
          start: node.getStart(sourceFile),
          end: node.getEnd(),
        });
      }
    }

    if (
      ts.isJsxElement(node) &&
      ts.isIdentifier(node.openingElement.tagName) &&
      node.openingElement.tagName.text.toLowerCase() === 'script'
    ) {
      addMatch({
        kind: 'inline-script',
        api: 'script',
        severity: 'unsafe',
        message: 'Inline script tag detected',
        start: node.getStart(sourceFile),
        end: node.getEnd(),
      });
    }

    if (
      ts.isJsxSelfClosingElement(node) &&
      ts.isIdentifier(node.tagName) &&
      node.tagName.text.toLowerCase() === 'script'
    ) {
      addMatch({
        kind: 'inline-script',
        api: 'script',
        severity: 'unsafe',
        message: 'Inline script tag detected',
        start: node.getStart(sourceFile),
        end: node.getEnd(),
      });
    }

    ts.forEachChild(node, visit);
  };

  visit(sourceFile);

  return [...matches.values()].sort((left, right) => left.start - right.start);
}

function normalizeExpression(node: ts.Node, sourceFile: ts.SourceFile): string {
  if (ts.isPropertyAccessExpression(node)) {
    return `${normalizeExpression(node.expression, sourceFile)}.${node.name.text}`;
  }
  if (ts.isIdentifier(node)) {
    return node.text;
  }
  return node.getText(sourceFile);
}

function shouldAllowMatch(match: RiskMatch, policy: ComponentPolicy): boolean {
  if (match.kind === 'inline-script') {
    return policy.allowInlineScripts;
  }

  if (match.kind === 'dangerous-call') {
    return policy.allowEval;
  }

  if (match.kind === 'storage') {
    if (match.api === 'localStorage') {
      return policy.allowLocalStorage;
    }
    if (match.api === 'sessionStorage') {
      return policy.allowSessionStorage;
    }
    if (match.api === 'document.cookie') {
      return policy.allowCookies;
    }
    return false;
  }

  if (match.kind === 'network') {
    return policy.allowNetworkRequests;
  }

  if (match.kind === 'browser-api') {
    if (match.api === 'localStorage') {
      return policy.allowLocalStorage;
    }
    if (match.api === 'sessionStorage') {
      return policy.allowSessionStorage;
    }
    if (match.api === 'document.cookie') {
      return policy.allowCookies;
    }
    return policy.allowBrowserAPIs || Boolean(policy.allowedAPIs?.includes(match.api));
  }

  return false;
}

function applyReplacements(code: string, replacements: readonly Replacement[]): string {
  return [...replacements]
    .sort((left, right) => right.start - left.start)
    .reduce((result, replacement) => {
      return result.slice(0, replacement.start) + replacement.text + result.slice(replacement.end);
    }, code);
}

function buildPolicyPrelude(policy: ComponentPolicy, guards: ReadonlySet<string>): string {
  if (guards.size === 0) {
    return '';
  }

  const lines = [
    'const __yappcBlockApi = (name: string): never => { throw new Error(`Blocked unsafe API: ${name}`); };',
  ];

  if (guards.has('fetch') && !policy.allowNetworkRequests) {
    lines.push('const fetch = (..._args: unknown[]) => __yappcBlockApi("fetch");');
  }
  if (guards.has('XMLHttpRequest') && !policy.allowNetworkRequests) {
    lines.push('const XMLHttpRequest = class { constructor() { __yappcBlockApi("XMLHttpRequest"); } };');
  }
  if (guards.has('WebSocket') && !policy.allowNetworkRequests) {
    lines.push('const WebSocket = class { constructor() { __yappcBlockApi("WebSocket"); } };');
  }
  if (guards.has('localStorage') && !policy.allowLocalStorage) {
    lines.push('const localStorage = new Proxy({}, { get() { return __yappcBlockApi("localStorage"); }, set() { return __yappcBlockApi("localStorage"); } });');
  }
  if (guards.has('sessionStorage') && !policy.allowSessionStorage) {
    lines.push('const sessionStorage = new Proxy({}, { get() { return __yappcBlockApi("sessionStorage"); }, set() { return __yappcBlockApi("sessionStorage"); } });');
  }
  if (guards.has('indexedDB')) {
    lines.push('const indexedDB = new Proxy({}, { get() { return __yappcBlockApi("indexedDB"); } });');
  }
  if (guards.has('window')) {
    lines.push(
      [
        'const window = new Proxy(globalThis.window ?? {}, {',
        '  get(target, prop, receiver) {',
        '    if (prop === "fetch") return (..._args: unknown[]) => __yappcBlockApi("window.fetch");',
        '    if (prop === "XMLHttpRequest") return class { constructor() { __yappcBlockApi("window.XMLHttpRequest"); } };',
        '    if (prop === "WebSocket") return class { constructor() { __yappcBlockApi("window.WebSocket"); } };',
        '    if (prop === "postMessage") return (..._args: unknown[]) => __yappcBlockApi("window.postMessage");',
        '    return Reflect.get(target, prop, receiver);',
        '  },',
        '});',
      ].join('\n')
    );
  }
  if (guards.has('document') && !policy.allowCookies) {
    lines.push(
      [
        'const document = new Proxy(globalThis.document ?? {}, {',
        '  get(target, prop, receiver) {',
        '    if (prop === "cookie") return __yappcBlockApi("document.cookie");',
        '    return Reflect.get(target, prop, receiver);',
        '  },',
        '  set(target, prop, _value, receiver) {',
        '    if (prop === "cookie") return __yappcBlockApi("document.cookie");',
        '    return Reflect.set(target, prop, _value, receiver);',
        '  },',
        '});',
      ].join('\n')
    );
  }
  if (guards.has('navigator')) {
    lines.push(
      [
        'const navigator = new Proxy(globalThis.navigator ?? {}, {',
        '  get(target, prop, receiver) {',
        '    if (prop === "geolocation") return __yappcBlockApi("navigator.geolocation");',
        '    if (prop === "mediaDevices") return __yappcBlockApi("navigator.mediaDevices");',
        '    if (prop === "clipboard") return __yappcBlockApi("navigator.clipboard");',
        '    return Reflect.get(target, prop, receiver);',
        '  },',
        '});',
      ].join('\n')
    );
  }

  return lines.join('\n');
}

function guardNameForApi(api: string): string | null {
  if (api.startsWith('window.')) {
    return 'window';
  }
  if (api.startsWith('navigator.')) {
    return 'navigator';
  }
  if (api === 'document.cookie') {
    return 'document';
  }
  if (api === 'fetch') {
    return 'fetch';
  }
  if (api === 'XMLHttpRequest') {
    return 'XMLHttpRequest';
  }
  if (api === 'WebSocket') {
    return 'WebSocket';
  }
  if (api === 'localStorage') {
    return 'localStorage';
  }
  if (api === 'sessionStorage') {
    return 'sessionStorage';
  }
  if (api === 'indexedDB') {
    return 'indexedDB';
  }
  return null;
}

export default {
  assessComponentSafety,
  getDefaultComponentPolicy,
  getTrustedComponentPolicy,
  applyComponentPolicy,
};
