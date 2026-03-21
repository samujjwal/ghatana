import { describe, it, expect } from 'vitest';
import { lazyRoutes, prefetchConfig, LazyLoadingFallback, LazyLoadingError } from './lazy';

describe('lazyRoutes', () => {
  it('should have routes defined', () => {
    expect(lazyRoutes).toBeDefined();
    expect(lazyRoutes.length).toBeGreaterThan(0);
  });

  it('should have root route', () => {
    const rootRoute = lazyRoutes.find(r => r.path === '/');
    expect(rootRoute).toBeDefined();
  });

  it('should have animation routes', () => {
    const animationRoute = lazyRoutes.find(r => r.path === '/animations');
    expect(animationRoute).toBeDefined();
  });

  it('should have simulation routes', () => {
    const simulationRoute = lazyRoutes.find(r => r.path === '/simulations');
    expect(simulationRoute).toBeDefined();
  });

  it('should have lazy loading for heavy routes', () => {
    const animationEditorRoute = lazyRoutes.find(r => r.path === '/animations/editor/:id?');
    expect(animationEditorRoute).toBeDefined();
  });
});

describe('prefetchConfig', () => {
  it('should have idle prefetch routes', () => {
    expect(prefetchConfig.idlePrefetch).toBeDefined();
    expect(prefetchConfig.idlePrefetch.length).toBeGreaterThan(0);
  });

  it('should have hover prefetch routes', () => {
    expect(prefetchConfig.hoverPrefetch).toBeDefined();
    expect(prefetchConfig.hoverPrefetch.routes).toBeDefined();
    expect(prefetchConfig.hoverPrefetch.delay).toBe(100);
  });
});

describe('LazyLoadingFallback', () => {
  it('should render fallback component', () => {
    const result = LazyLoadingFallback();
    expect(result).toBeDefined();
    expect(result?.type).toBe('div');
  });
});

describe('LazyLoadingError', () => {
  it('should render error component', () => {
    const error = new Error('Test error');
    const result = LazyLoadingError({ error });
    expect(result).toBeDefined();
    expect(result?.type).toBe('div');
  });
});
