import { AccessibilityAuditor } from '../AccessibilityAuditor';

import type { AccessibilityReport, WCAGLevel, OutputFormat } from '../types';

/**
 * Audit a target which can be:
 * - a DOM Element / Document (browser or jsdom)
 * - an HTML string
 * - a filesystem path to an HTML file
 *
 * When running in Node and an HTML string/path is provided, this helper
 * will dynamically create a jsdom environment, run the audit, and then
 * clean up the globals it injected.
 */
export async function auditTarget(
  target: Element | Document | string | undefined,
  wcagLevel: WCAGLevel = 'AA'
): Promise<AccessibilityReport> {
  const auditor = new AccessibilityAuditor({ wcagLevel });

  // If target is undefined or a Document/Element, just run normally
  if (typeof target === 'undefined' || (typeof target === 'object' && 'nodeType' in target)) {
    if (typeof (globalThis as any).window === 'undefined' && typeof document === 'undefined') {
      // No global document; still okay if a Document was passed explicitly
    }
    await auditor.initialize();
    return auditor.audit(target as Element | Document | undefined);
  }

  // If target is a string, decide whether it's HTML or a file path
  const asString = target as string;
  const looksLikeHtml = asString.trim().startsWith('<');

  // For file paths, try to read file
  let html: string | null = null;
  if (!looksLikeHtml) {
    try {
      // Use dynamic import to avoid requiring fs in browser environments
       
      const fs = await import('fs');
      if (fs && typeof (fs as any).readFileSync === 'function') {
        try {
          html = (fs as any).readFileSync(asString, 'utf-8');
        } catch {
          // not a file path — treat as raw HTML string
          html = asString;
        }
      }
    } catch {
      html = asString;
    }
  } else {
    html = asString;
  }

  // At this point we have HTML; create a jsdom environment
  // Dynamically import jsdom to avoid adding a hard dependency for browser
  const JSDOM = (await import('jsdom')).JSDOM;
  const dom = new JSDOM(html || '<!doctype html><html><body></body></html>');
  const { window } = dom;

  // Inject globals temporarily so axe-core and code that expects window/document works
  const oldWindow = (globalThis as any).window;
  const oldDocument = (globalThis as any).document;
  try {
    (globalThis as any).window = window;
    (globalThis as any).document = window.document;
    // Don't try to override navigator; jsdom's navigator is already available on window
    // and trying to set it on globalThis causes issues

    await auditor.initialize();
    const report = await auditor.audit(window.document);
    return report;
  } finally {
    // Clean up injected globals
    if (typeof oldWindow === 'undefined') delete (globalThis as any).window; else (globalThis as any).window = oldWindow;
    if (typeof oldDocument === 'undefined') delete (globalThis as any).document; else (globalThis as any).document = oldDocument;
  }
}

/**
 * Convenience: run auditTarget and return formatted output
 */
export async function auditTargetToFormat(
  target: Element | Document | string | undefined,
  format: OutputFormat = 'json',
  wcagLevel: WCAGLevel = 'AA'
): Promise<string> {
  const report = await auditTarget(target, wcagLevel);
  const auditor = new AccessibilityAuditor({ wcagLevel });
  return auditor.exportReport(report, format);
}
