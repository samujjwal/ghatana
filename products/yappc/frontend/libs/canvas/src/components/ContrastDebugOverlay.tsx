/**
 * Contrast Debug Overlay
 *
 * Dev-mode visual indicator for contrast ratio issues.
 * Overlays elements with contrast warnings when enabled.
 *
 * @doc.type component
 * @doc.purpose Visual debugging for accessibility
 * @doc.layer core
 * @doc.pattern DevTool
 */

import React, { useEffect, useState } from 'react';
import {
  calculateContrastRatio,
  formatRatio,
  WCAG_LEVELS,
} from '../utils/contrast-checker';

interface ContrastIssue {
  element: Element;
  foreground: string;
  background: string;
  ratio: number;
  passes: boolean;
}

interface ContrastDebugOverlayProps {
  /** Whether to show the overlay */
  enabled?: boolean;
  /** Minimum ratio to enforce */
  minimumRatio?: number;
}

/**
 * Get computed color from element
 */
function getComputedColor(
  element: Element,
  property: 'color' | 'backgroundColor'
): string {
  const computed = window.getComputedStyle(element);
  const value = computed[property];

  // Convert rgb(a) to hex
  if (value.startsWith('rgb')) {
    const match = value.match(/\d+/g);
    if (match) {
      const [r, g, b] = match.map(Number);
      return `#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}`;
    }
  }

  return value;
}

/**
 * Find contrast issues in the document
 */
function findContrastIssues(minimumRatio: number): ContrastIssue[] {
  const issues: ContrastIssue[] = [];

  // Find all text elements
  const elements = document.querySelectorAll(
    'p, span, button, a, h1, h2, h3, h4, h5, h6, label, div'
  );

  elements.forEach((element) => {
    // Skip if no text content
    if (!element.textContent?.trim()) return;

    const foreground = getComputedColor(element, 'color');
    const background = getComputedColor(element, 'backgroundColor');

    // Skip if transparent background
    if (background === 'transparent' || background === 'rgba(0, 0, 0, 0)')
      return;

    const ratio = calculateContrastRatio(foreground, background);
    const passes = ratio >= minimumRatio;

    if (!passes) {
      issues.push({
        element,
        foreground,
        background,
        ratio,
        passes,
      });
    }
  });

  return issues;
}

/**
 * Contrast debug overlay component
 */
export const ContrastDebugOverlay: React.FC<ContrastDebugOverlayProps> = ({
  enabled = false,
  minimumRatio = WCAG_LEVELS.AA,
}) => {
  const [issues, setIssues] = useState<ContrastIssue[]>([]);
  const [overlays, setOverlays] = useState<Map<Element, HTMLDivElement>>(
    new Map()
  );

  useEffect(() => {
    if (!enabled) {
      // Clean up overlays
      overlays.forEach((overlay) => overlay.remove());
      setOverlays(new Map());
      setIssues([]);
      return;
    }

    // Find issues
    const foundIssues = findContrastIssues(minimumRatio);
    setIssues(foundIssues);

    // Create overlays
    const newOverlays = new Map<Element, HTMLDivElement>();

    foundIssues.forEach((issue) => {
      const rect = issue.element.getBoundingClientRect();

      const overlay = document.createElement('div');
      overlay.style.position = 'fixed';
      overlay.style.left = `${rect.left}px`;
      overlay.style.top = `${rect.top}px`;
      overlay.style.width = `${rect.width}px`;
      overlay.style.height = `${rect.height}px`;
      overlay.style.border = '2px solid red';
      overlay.style.backgroundColor = 'rgba(255, 0, 0, 0.1)';
      overlay.style.pointerEvents = 'none';
      overlay.style.zIndex = '999999';
      overlay.style.display = 'flex';
      overlay.style.alignItems = 'center';
      overlay.style.justifyContent = 'center';
      overlay.style.fontSize = '10px';
      overlay.style.fontWeight = 'bold';
      overlay.style.color = 'red';
      overlay.style.textShadow = '0 0 2px white';
      overlay.textContent = formatRatio(issue.ratio);

      document.body.appendChild(overlay);
      newOverlays.set(issue.element, overlay);
    });

    setOverlays(newOverlays);

    // Cleanup
    return () => {
      newOverlays.forEach((overlay) => overlay.remove());
    };
  }, [enabled, minimumRatio]);

  // Re-scan on window resize
  useEffect(() => {
    if (!enabled) return;

    const handleResize = () => {
      const foundIssues = findContrastIssues(minimumRatio);
      setIssues(foundIssues);
    };

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [enabled, minimumRatio]);

  if (!enabled || issues.length === 0) {
    return null;
  }

  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        right: 0,
        background: 'rgba(255, 0, 0, 0.9)',
        color: 'white',
        padding: '8px 12px',
        zIndex: 1000000,
        fontSize: '12px',
        fontFamily: 'monospace',
        borderBottomLeftRadius: '4px',
        boxShadow: '0 2px 8px rgba(0,0,0,0.3)',
      }}
    >
      ⚠️ {issues.length} contrast issue{issues.length !== 1 ? 's' : ''} found
    </div>
  );
};

/**
 * Hook to enable contrast debugging
 */
export function useContrastDebug(enabled = false) {
  useEffect(() => {
    if (!enabled) return;

    console.log('🎨 Contrast Debug Mode Enabled');
    console.log('Press Ctrl+Shift+C to toggle overlay');

    const handleKeyPress = (e: KeyboardEvent) => {
      if (e.ctrlKey && e.shiftKey && e.key === 'C') {
        // Toggle via custom event
        window.dispatchEvent(new CustomEvent('toggle-contrast-debug'));
      }
    };

    window.addEventListener('keydown', handleKeyPress);
    return () => window.removeEventListener('keydown', handleKeyPress);
  }, [enabled]);
}

/**
 * Log contrast issues to console
 */
export function logContrastIssues() {
  const issues = findContrastIssues(WCAG_LEVELS.AA);

  if (issues.length === 0) {
    console.log('✅ No contrast issues found');
    return;
  }

  console.group(`⚠️ Found ${issues.length} contrast issue(s)`);

  issues.forEach((issue, index) => {
    console.group(`Issue ${index + 1}: ${formatRatio(issue.ratio)}`);
    console.log('Element:', issue.element);
    console.log('Foreground:', issue.foreground);
    console.log('Background:', issue.background);
    console.log('Text:', issue.element.textContent?.trim().substring(0, 50));
    console.groupEnd();
  });

  console.groupEnd();
}

// Expose to window for dev tools
if (typeof window !== 'undefined') {
  (window as unknown).__contrastDebug = {
    findIssues: findContrastIssues,
    logIssues: logContrastIssues,
  };
}
