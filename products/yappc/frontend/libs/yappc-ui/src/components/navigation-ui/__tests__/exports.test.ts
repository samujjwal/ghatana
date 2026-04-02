/**
 * @doc.type test
 * @doc.purpose Verify that navigation-ui components are properly exported
 * @doc.layer package-verification
 */
import { describe, it, expect } from 'vitest';

describe('navigation-ui exports', () => {
  it('should export navigation components', async () => {
    const exports = await import('../index');
    expect(exports).toBeDefined();
    expect(typeof exports).toBe('object');
  });

  it('should have Breadcrumb component', async () => {
    const { Breadcrumb } = await import('../index');
    expect(Breadcrumb).toBeDefined();
  });

  it('should have LifecycleStage component', async () => {
    const { LifecycleStage } = await import('../index');
    expect(LifecycleStage).toBeDefined();
  });

  it('should have StageNavigation component', async () => {
    const { StageNavigation } = await import('../index');
    expect(StageNavigation).toBeDefined();
  });

  it('should have Tabs component', async () => {
    const { Tabs } = await import('../index');
    expect(Tabs).toBeDefined();
  });

  it('should have TabNavigation component', async () => {
    const { TabNavigation } = await import('../index');
    expect(TabNavigation).toBeDefined();
  });
});
