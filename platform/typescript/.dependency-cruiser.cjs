/**
 * dependency-cruiser configuration for @ghatana/platform TypeScript packages.
 *
 * Run: npx depcruise --config .dependency-cruiser.cjs platform/typescript/*/src
 *
 * Rules enforced:
 * 1. Deprecated canvas sub-libs must not import from each other (only from @ghatana/canvas)
 * 2. Platform packages must not import from product packages
 * 3. @ghatana/sso-client main barrel must not import server-only deps (fastify, fastify-plugin)
 * 4. No circular dependencies within a package's src
 *
 * @doc.type config
 * @doc.purpose Architecture boundary enforcement for platform TypeScript packages
 * @doc.layer platform
 * @doc.pattern Governance
 */

/** @type {import('dependency-cruiser').IConfiguration} */
module.exports = {
  forbidden: [
    // ── Rule 1: Deprecated canvas sub-libs must not cross-import each other ──
    // canvas-react must not import from canvas-core (both are facades over @ghatana/canvas)
    {
      name: 'no-canvas-sub-lib-cross-imports',
      severity: 'error',
      comment:
        'Deprecated canvas sub-libs are independent facades. They must not cross-import each other.',
      from: {
        path: '^platform/typescript/canvas-(core|react|plugins|tools|chrome)/src',
      },
      to: {
        path: '^platform/typescript/canvas-(core|react|plugins|tools|chrome)/src',
        pathNot: '^platform/typescript/\\1/src', // not importing from itself
      },
    },

    // ── Rule 2: Platform packages must not import from products ──
    {
      name: 'no-platform-imports-from-product',
      severity: 'error',
      comment:
        'Platform libraries must not depend on product-specific packages. Product logic must not silently leak into the platform.',
      from: {
        path: '^platform/typescript/',
      },
      to: {
        path: '^products/',
      },
    },

    // ── Rule 3: sso-client main barrel must not import server frameworks ──
    {
      name: 'no-sso-client-server-deps-in-main-barrel',
      severity: 'error',
      comment:
        'The @ghatana/sso-client main barrel must not import fastify or fastify-plugin. ' +
        'Server-specific code lives in src/security/csp-fastify.ts and is exported via the ./security/fastify subpath.',
      from: {
        path: '^platform/typescript/sso-client/src/(?!security/csp-fastify)',
      },
      to: {
        path: '^(fastify|fastify-plugin)(/|$)',
      },
    },

    // ── Rule 4: No circular dependencies ──
    {
      name: 'no-circular',
      severity: 'error',
      comment: 'Circular dependencies make code hard to test and reason about.',
      from: {},
      to: {
        circular: true,
      },
    },

    // ── Rule 5: @ghatana/realtime must not re-define PlatformEvent types ──
    // It must import from @ghatana/events instead.
    {
      name: 'no-realtime-local-platform-event',
      severity: 'warn',
      comment:
        'PlatformEvent must be defined in @ghatana/events, not in @ghatana/realtime. ' +
        'Realtime should import and re-export from @ghatana/events.',
      from: {
        path: '^platform/typescript/realtime/src',
      },
      to: {
        path: '^platform/typescript/realtime/src/events/types',
        // This warns if realtime/src/events/types re-exports local (non-events) definitions
        // When the file only re-exports from @ghatana/events, this path won't exist as a
        // circular dep. It fires if types.ts defines its own PlatformEvent class/interface.
      },
    },

    // ── Rule 6: No orphan platform packages (every package must be imported somewhere) ──
    // Disabled by default — enable in CI when a full audit is run.
    // {
    //   name: 'no-orphans',
    //   severity: 'warn',
    //   from: { orphan: true, pathNot: ['.*\\.d\\.ts$', '(^|/)index\\.ts$'] },
    //   to: {},
    // },
  ],

  options: {
    // Use TypeScript's module resolution with bundler mode (matching tsconfig)
    tsConfig: {
      fileName: './tsconfig.base.json',
    },
    tsPreCompilationDeps: true,
    moduleSystems: ['es6'],

    // Exclude test files and dist from analysis
    exclude: {
      path: '(^|/)(__tests__|dist|node_modules)/',
    },

    // Report dependency-cruiser errors in text format for CI
    reporterOptions: {
      text: {
        highlightFocused: true,
      },
    },
  },
};
