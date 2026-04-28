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
      // 8-phase IA navigation — each phase is a first-class route
      route('p/:projectId', 'routes/app/project/_shell.tsx', [
        // Default: redirect to intent phase
        index('routes/app/project/index.tsx'),
        // Phase 1: Intent — capture goals and problems
        route('intent', 'routes/app/project/intent.tsx'),
        // Phase 2: Shape — define solution via canvas
        route('shape', 'routes/app/project/shape.tsx'),
        // Phase 3: Validate — review and gate requirements
        route('validate', 'routes/app/project/validate.tsx'),
        // Phase 4: Generate — AI-powered code and doc generation
        route('generate', 'routes/app/project/generate.tsx'),
        // Phase 5: Run — execute pipelines and deployments
        route('run', 'routes/app/project/run.tsx'),
        // Phase 6: Observe — metrics, incidents, and live signals
        route('observe', 'routes/app/project/observe.tsx'),
        // Phase 7: Learn — retrospectives and AI insights
        route('learn', 'routes/app/project/learn.tsx'),
        // Phase 8: Evolve — plan the next cycle
        route('evolve', 'routes/app/project/evolve.tsx'),
        // Project configuration (not a phase tab — accessible via settings icon)
        route('settings', 'routes/app/project/settings.tsx'),
        // Legacy routes preserved for deep-links (may be removed in a future cycle)
        route('canvas', 'routes/app/project/canvas.tsx'),
        route('preview', 'routes/app/project/preview.tsx'),
        route('deploy', 'routes/app/project/deploy.tsx'),
        route('lifecycle', 'routes/app/project/lifecycle.tsx'),
      ]),

      // Admin routes (OWNER/ADMIN only — capability-gated via useCapabilityGate)
      route('admin/prompt-versions', 'routes/app/admin/prompt-versions.tsx'),
      route('admin/ab-testing', 'routes/app/admin/ab-testing.tsx'),
      route('admin/feature-flags', 'routes/app/admin/feature-flags.tsx'),
    ]),

    // Catch-all route for 404
    route('*', 'routes/not-found.tsx'),
  ]),
] satisfies RouteConfig;
