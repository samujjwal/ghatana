import { describe, expect, it } from 'vitest';

import {
  alertsSurfaceBoundary,
  dataFabricMetricsBoundary,
  pluginDependencyBoundary,
  settingsSurfaceBoundaries,
  smartWorkflowGenerationBoundary,
} from '@/components/common/unsupportedSurfaceRegistry';

describe('unsupportedSurfaceRegistry', () => {
  it('defines canonical boundary metadata for the primary unsupported surfaces', () => {
    expect(alertsSurfaceBoundary.state).toBe('not-in-deployment');
    expect(dataFabricMetricsBoundary.state).toBe('preview');
    expect(smartWorkflowGenerationBoundary.state).toBe('temporarily-unavailable');
    expect(pluginDependencyBoundary.details).toHaveLength(3);
    expect(alertsSurfaceBoundary.title).toBe('Alerts Surface Not Available');
    expect(dataFabricMetricsBoundary.summary).toContain('preview topology only');
    expect(smartWorkflowGenerationBoundary.summary).toContain('manual pipeline editor');
  });

  it('keeps all settings sections mapped to explicit boundary definitions', () => {
    expect(Object.keys(settingsSurfaceBoundaries)).toEqual([
      'profile',
      'preferences',
      'notifications',
      'api',
    ]);

    Object.values(settingsSurfaceBoundaries).forEach((boundary) => {
      expect(boundary.title.length).toBeGreaterThan(0);
      expect(boundary.summary.length).toBeGreaterThan(0);
      expect(boundary.details.length).toBeGreaterThan(0);
      expect(boundary.state).toBe('not-in-deployment');
    });
  });
});