/**
 * Lazy Loading Module Configuration
 * Part of Execution Plan item #11: Performance Optimization
 * 
 * Implements code splitting and lazy loading for React Router v7 routes
 * to reduce initial bundle size and improve performance.
 */

import { lazy } from 'react';
import type { RouteObject } from 'react-router-dom';

const AnimationEditor = lazy(() => import('../pages/AnimationEditor.tsx'));
const SimulationStudio = lazy(() => import('../pages/SimulationStudio.tsx'));
const AssessmentBuilder = lazy(() => import('../pages/AssessmentBuilder.tsx'));
const AnalyticsDashboard = lazy(() => import('../pages/AnalyticsDashboard.tsx'));
const LearningPathDesigner = lazy(() => import('../pages/LearningPathDesigner.tsx'));

/**
 * Route configuration with lazy loading
 */
export const lazyRoutes: RouteObject[] = [
  {
    path: '/',
    lazy: () => import('../pages/Home.tsx'),
  },
  {
    path: '/animations',
    lazy: () => import('../pages/AnimationList.tsx'),
  },
  {
    path: '/animations/editor/:id?',
    Component: AnimationEditor,
  },
  {
    path: '/simulations',
    lazy: () => import('../pages/SimulationList.tsx'),
  },
  {
    path: '/simulations/studio/:id?',
    Component: SimulationStudio,
  },
  {
    path: '/assessments',
    lazy: () => import('../pages/AssessmentList.tsx'),
  },
  {
    path: '/assessments/builder/:id?',
    Component: AssessmentBuilder,
  },
  {
    path: '/analytics',
    Component: AnalyticsDashboard,
  },
  {
    path: '/learning-paths',
    lazy: () => import('../pages/LearningPathList.tsx'),
  },
  {
    path: '/learning-paths/designer/:id?',
    Component: LearningPathDesigner,
  },
  {
    path: '/settings',
    lazy: () => import('../pages/Settings.tsx'),
  },
  {
    path: '/profile',
    lazy: () => import('../pages/Profile.tsx'),
  },
];

/**
 * Prefetch configuration for common routes
 */
export const prefetchConfig = {
  // Routes to prefetch on idle
  idlePrefetch: [
    '/animations',
    '/simulations',
    '/search',
  ],
  // Routes to prefetch on hover (with delay)
  hoverPrefetch: {
    delay: 100,
    routes: [
      '/animations/editor',
      '/simulations/studio',
      '/assessments/builder',
    ],
  },
};

/**
 * Loading fallback component
 */
export function LazyLoadingFallback() {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100vh',
        flexDirection: 'column',
        gap: '16px',
      }}
    >
      <div
        style={{
          width: '48px',
          height: '48px',
          border: '3px solid #e0e0e0',
          borderTopColor: '#4ecdc4',
          borderRadius: '50%',
          animation: 'spin 1s linear infinite',
        }}
      />
      <p style={{ color: '#666', fontSize: '14px' }}>Loading...</p>
      <style>{`
        @keyframes spin {
          to { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
}

/**
 * Error boundary for lazy loaded components
 */
export function LazyLoadingError({ error }: { error: Error }) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100vh',
        flexDirection: 'column',
        gap: '16px',
        padding: '24px',
      }}
    >
      <div style={{ fontSize: '48px' }}>⚠️</div>
      <h2 style={{ margin: 0, color: '#333' }}>Failed to Load</h2>
      <p style={{ color: '#666', textAlign: 'center', maxWidth: '400px' }}>
        There was an error loading this module. Please try refreshing the page.
      </p>
      <button
        onClick={() => window.location.reload()}
        style={{
          padding: '12px 24px',
          backgroundColor: '#4ecdc4',
          color: 'white',
          border: 'none',
          borderRadius: '6px',
          cursor: 'pointer',
          fontSize: '14px',
        }}
      >
        Refresh Page
      </button>
      {process.env.NODE_ENV === 'development' && (
        <pre
          style={{
            marginTop: '16px',
            padding: '16px',
            backgroundColor: '#f5f5f5',
            borderRadius: '6px',
            fontSize: '12px',
            color: '#e74c3c',
            maxWidth: '100%',
            overflow: 'auto',
          }}
        >
          {error.message}
        </pre>
      )}
    </div>
  );
}
