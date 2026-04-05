import {
  type RouteConfig,
  index,
  layout,
  route,
} from '@react-router/dev/routes';

/**
 * Route configuration for YAPPC Web Application
 *
 * Explicit route definitions for all application pages.
 * Structure mirrors the file organization in src/routes/*
 *
 * @doc.type routes
 * @doc.purpose Application route definitions
 * @doc.layer routing
 */

export default [
  // Root layout
  layout('routes/_root.tsx', [
    // Landing / home
    index('routes/dashboard.tsx'),

    // Auth routes
    route('login', 'routes/login.tsx'),
    route('register', 'routes/register.tsx'),
    route('forgot-password', 'routes/forgot-password.tsx'),
    route('onboarding', 'routes/onboarding.tsx'),

    // App routes with shell layout
    layout('routes/_shell.tsx', [
      // Workspace and project management
      route('workspaces', 'routes/app/workspaces.tsx'),
      route('projects', 'routes/app/projects.tsx'),

      // User profile and workspace settings
      route('profile', 'routes/profile.tsx'),
      route('settings', 'routes/settings.tsx'),

      // Project-specific route: /p/:projectId
      route('p/:projectId', 'routes/app/project/_shell.tsx', [
        // Project tabs
        index('routes/app/project/index.tsx'),
        route('canvas', 'routes/app/project/canvas.tsx'), // Unified canvas with all Epic 1-10 features
        route('canvas-workspace', 'routes/app/project/canvas-workspace.tsx'), // Production workspace canvas
        route('preview', 'routes/app/project/preview.tsx'),
        route('deploy', 'routes/app/project/deploy.tsx'),
        route('settings', 'routes/app/project/settings.tsx'),
        route('lifecycle', 'routes/app/project/lifecycle.tsx'),
      ]),
    ]),

    // Catch-all route for 404
    route('*', 'routes/not-found.tsx'),
  ]),
] satisfies RouteConfig;
