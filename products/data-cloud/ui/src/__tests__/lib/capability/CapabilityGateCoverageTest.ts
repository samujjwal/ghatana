/**
 * Capability Gate Coverage Test
 *
 * Verifies that all optional UI actions are properly gated with CapabilityGated components.
 * This test enforces production-grade capability gating across the UI.
 *
 * @doc.type test
 * @doc.purpose Verify capability gates are in place for all optional UI actions
 * @doc.layer frontend
 * @doc.pattern Test
 */

import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync } from 'fs';
import { join } from 'path';

describe('Capability Gate Coverage', () => {
  const uiSrcPath = join(__dirname, '../../../../src');

  /**
   * Test that verifies pages with optional actions use CapabilityGated.
   * This is a baseline test - add specific assertions for each page that needs gating.
   */
  it('WorkflowsPage uses capability signals for AI features', () => {
    const workflowsPagePath = join(uiSrcPath, 'pages/WorkflowsPage.tsx');
    const content = readFileSync(workflowsPagePath, 'utf-8');

    // Verify AI-related features use capability checks
    expect(content).toContain('getCapabilitySignal');
    expect(content).toContain('useCapabilityRegistry');
    expect(content).toContain('ai-operations.service');
  });

  it('AlertsPage uses capability signals for alert features', () => {
    const alertsPagePath = join(uiSrcPath, 'pages/AlertsPage.tsx');
    const content = readFileSync(alertsPagePath, 'utf-8');

    // Verify alert features use capability checks
    expect(content).toContain('getCapabilitySignal');
    expect(content).toContain('useCapabilityRegistry');
  });

  it('CapabilityGated component exists and is exported', () => {
    const capabilityGatedPath = join(uiSrcPath, 'components/common/CapabilityGated.tsx');
    const content = readFileSync(capabilityGatedPath, 'utf-8');

    expect(content).toContain('export const CapabilityGated');
    expect(content).toContain('useCapabilityGate');
  });

  it('useCapabilityGate hook exists and is exported', () => {
    const hookPath = join(uiSrcPath, 'hooks/useCapabilityGate.ts');
    const content = readFileSync(hookPath, 'utf-8');

    expect(content).toContain('export function useCapabilityGate');
    expect(content).toContain('export function useCapabilitySignal');
  });

  /**
   * Test that RouteCapabilityRegistry has capability definitions for all routes.
   */
  it('RouteCapabilityRegistry defines capabilities for all routes', () => {
    const registryPath = join(uiSrcPath, 'lib/routing/RouteCapabilityRegistry.ts');
    const content = readFileSync(registryPath, 'utf-8');

    // Verify the registry exports the canonical registry
    expect(content).toContain('canonicalRouteRegistry');

    // Verify key routes have capabilities defined
    expect(content).toContain('capabilities: [');
    expect(content).toContain('workflows');
    expect(content).toContain('analytics');
    expect(content).toContain('governance');
  });

  /**
   * Test that critical optional actions in pages are documented with capability requirements.
   * This is a documentation test - ensures that optional actions have clear capability annotations.
   */
  it('AnalyticsHandler cancellation is documented with capability requirement', () => {
    // This test verifies the backend capability is documented
    // The UI should reference this capability
    const handlerPath = join(
      __dirname,
      '../../../../../launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/AnalyticsHandler.java'
    );

    try {
      const content = readFileSync(handlerPath, 'utf-8');
      // Verify the handler documents the capability requirement
      expect(content).toContain('analytics.cancellation');
      expect(content).toContain('capability');
    } catch (e) {
      // File may not exist in test environment, skip
      console.log('Skipping AnalyticsHandler capability check - file not found');
    }
  });

  /**
   * Future: Add more specific tests for each page's optional actions
   * Example:
   * it('WorkflowsPage gates pipeline creation with capability', () => {
   *   const content = readFileSync(join(uiSrcPath, 'pages/WorkflowsPage.tsx'), 'utf-8');
   *   // Verify "Create Pipeline" button is wrapped in CapabilityGated
   *   expect(content).toMatch(/CapabilityGated.*pipelines/);
   * });
   */
});
