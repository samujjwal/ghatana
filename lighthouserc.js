module.exports = {
  ci: {
    collect: {
      // URLs to audit (adjust per product)
      url: [
        'http://localhost:3000/',
        'http://localhost:3000/login',
      ],
      startServerCommand: 'pnpm --filter @ghatana/data-cloud-ui dev',
      startServerReadyPattern: 'ready in',
      startServerReadyTimeout: 30000,
      numberOfRuns: 3,
      settings: {
        preset: 'desktop',
        chromeFlags: '--no-sandbox --headless',
      },
    },
    assert: {
      assertions: {
        // Performance budgets
        'categories:performance': ['error', { minScore: 0.8 }],
        'categories:accessibility': ['error', { minScore: 0.9 }],
        'categories:best-practices': ['error', { minScore: 0.85 }],
        'categories:seo': ['warn', { minScore: 0.8 }],

        // Bundle size budgets (in bytes)
        'resource-summary:script:size': ['error', { maxNumericValue: 512000 }], // 500KB JS
        'resource-summary:stylesheet:size': ['warn', { maxNumericValue: 102400 }], // 100KB CSS
        'resource-summary:total:size': ['error', { maxNumericValue: 1048576 }], // 1MB total

        // Core Web Vitals
        'first-contentful-paint': ['error', { maxNumericValue: 2000 }],
        'largest-contentful-paint': ['error', { maxNumericValue: 2500 }],
        'cumulative-layout-shift': ['error', { maxNumericValue: 0.1 }],
        'total-blocking-time': ['error', { maxNumericValue: 300 }],
        'interactive': ['error', { maxNumericValue: 3500 }],
      },
    },
    upload: {
      target: 'filesystem',
      outputDir: './build/reports/lighthouse',
    },
  },
};
