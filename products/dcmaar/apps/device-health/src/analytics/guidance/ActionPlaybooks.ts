/**
 * @fileoverview Action playbooks for key performance metrics.
 *
 * Provides guided remediation steps that surface across the dashboard
 * in alerts, metric cards, and insight panels.
 *
 * @module analytics/guidance
 */

export type PlaybookPriority = 'high' | 'medium' | 'low';
export type PlaybookDifficulty = 'easy' | 'moderate' | 'advanced';

export interface CodeExample {
  title?: string;
  language: string;
  code: string;
}

export interface Resource {
  title: string;
  url: string;
  type?: 'article' | 'video' | 'case-study' | 'documentation';
}

export interface PlaybookAction {
  id: string;
  priority: PlaybookPriority;
  title: string;
  description: string;
  expectedImprovement: string;
  timeToImplement: string;
  difficulty: PlaybookDifficulty;
  steps: string[];
  codeExamples?: CodeExample[];
  resources: Resource[];
  tools?: string[];
}

export interface ActionPlaybook {
  metric: string;
  severity: 'warning' | 'critical';
  title: string;
  summary: string;
  actions: PlaybookAction[];
}

const priorityOrder: Record<PlaybookPriority, number> = {
  high: 0,
  medium: 1,
  low: 2,
};

const createActionId = (metric: string, slug: string): string =>
  `${metric}:${slug}`;

export const ACTION_PLAYBOOKS: Record<string, ActionPlaybook[]> = {
  lcp: [
    {
      metric: 'lcp',
      severity: 'critical',
      title: 'Critical LCP Remediation',
      summary:
        'Hero content is loading far too slowly. Prioritize server response, hero media optimization, and render-blocking eliminations.',
      actions: [
        {
          id: createActionId('lcp', 'edge-cache-html'),
          priority: 'high',
          title: 'Serve HTML Through Edge Cache',
          description:
            'Move server-rendered HTML behind a CDN edge with aggressive caching and stale-while-revalidate.',
          expectedImprovement: '-400 to -800ms',
          timeToImplement: '2 hours',
          difficulty: 'moderate',
          steps: [
            'Enable CDN caching for HTML with cache key on device type and localization.',
            'Add `cache-control: max-age=60, stale-while-revalidate=300` headers.',
            'Warm the cache using the top landing pages.',
          ],
          codeExamples: [
            {
              title: 'Fastly VCL snippet',
              language: 'vcl',
              code: `sub vcl_backend_response {
  if (bereq.method == "GET" && bereq.url.path ~ "^/") {
    set beresp.ttl = 60s;
    set beresp.grace = 300s;
  }
}`,
            },
          ],
          resources: [
            {
              title: 'Fastly stale-while-revalidate guide',
              url: 'https://engineering.fastly.com/blog/stale-while-revalidate/',
              type: 'article',
            },
            {
              title: 'Improve LCP by caching HTML',
              url: 'https://web.dev/articles/improve-lcp',
              type: 'documentation',
            },
          ],
          tools: ['CDN (Fastly, Cloudflare, Akamai)'],
        },
        {
          id: createActionId('lcp', 'preload-hero-image'),
          priority: 'high',
          title: 'Preload Hero Media',
          description:
            'Explicitly preload the hero image or video to avoid late discovery by the browser.',
          expectedImprovement: '-200 to -450ms',
          timeToImplement: '30 minutes',
          difficulty: 'easy',
          steps: [
            'Identify the element contributing to LCP in Chrome DevTools performance panel.',
            'Add a `<link rel="preload">` tag in the `<head>` for the hero resource.',
            'Ensure the preloaded asset matches the responsive source actually rendered.',
          ],
          codeExamples: [
            {
              language: 'html',
              code: `<link
  rel="preload"
  as="image"
  href="/images/hero@2x.webp"
  imagesrcset="/images/hero.webp 1x, /images/hero@2x.webp 2x"
  imagesizes="(max-width: 768px) 100vw, 1200px"
/>`,
            },
          ],
          resources: [
            {
              title: 'Preload critical assets',
              url: 'https://web.dev/articles/preload-critical-assets',
            },
          ],
          tools: ['Chrome DevTools', 'Lighthouse'],
        },
        {
          id: createActionId('lcp', 'optimize-hero-image'),
          priority: 'medium',
          title: 'Serve Optimized Hero Media',
          description:
            'Compress hero imagery using modern codecs and resize to the rendered dimensions.',
          expectedImprovement: '-150 to -300ms',
          timeToImplement: '1 hour',
          difficulty: 'moderate',
          steps: [
            'Export hero imagery at no larger than the maximum rendered size.',
            'Encode using AVIF/WebP with quality targeting < 150 KB file size.',
            'Deliver through responsive `srcset` so mobile devices get smaller payloads.',
          ],
          resources: [
            {
              title: 'Serve images in next-gen formats',
              url: 'https://web.dev/articles/serve-images-webp-avif',
            },
            {
              title: 'Image optimization checklist',
              url: 'https://calibreapp.com/blog/image-optimisation',
              type: 'article',
            },
          ],
          tools: ['Squoosh', 'ImageOptim', 'sharp'],
        },
        {
          id: createActionId('lcp', 'eliminate-render-blockers'),
          priority: 'medium',
          title: 'Eliminate Render-Blocking Resources',
          description:
            'Inline critical CSS and defer non-critical JS to keep the render pipeline unblocked.',
          expectedImprovement: '-180 to -350ms',
          timeToImplement: '3 hours',
          difficulty: 'advanced',
          steps: [
            'Audit render-blocking CSS/JS using Chrome DevTools coverage panel.',
            'Inline above-the-fold CSS (first ~14 KB) and defer the rest with media queries or dynamic import.',
            'Move non-critical scripts to the end of body with `defer`.',
          ],
          resources: [
            {
              title: 'Eliminate render-blocking resources',
              url: 'https://web.dev/articles/render-blocking-resources',
            },
          ],
          tools: ['Chrome DevTools Coverage', 'Critters', 'Webpack SplitChunks'],
        },
      ],
    },
    {
      metric: 'lcp',
      severity: 'warning',
      title: 'LCP Optimization Opportunities',
      summary:
        'Largest Contentful Paint is trending slow. Tackle the highest leverage optimizations to stay under 2.5s.',
      actions: [
        {
          id: createActionId('lcp', 'priority-hints'),
          priority: 'high',
          title: 'Apply Priority Hints',
          description:
            'Use `<link rel="preload">` and `<img fetchpriority="high">` to hint criticality.',
          expectedImprovement: '-120 to -180ms',
          timeToImplement: '30 minutes',
          difficulty: 'easy',
          steps: [
            'Add `fetchpriority="high"` to the LCP `<img>` element.',
            'Audit for conflicting resource priorities (e.g., carousels, background hero).',
          ],
          codeExamples: [
            {
              language: 'html',
              code: `<img
  src="/images/hero.webp"
  alt="Platform dashboard"
  width="1200"
  height="768"
  fetchpriority="high"
/>`,
            },
          ],
          resources: [
            {
              title: 'Optimize LCP with Priority Hints',
              url: 'https://web.dev/articles/priority-hints',
            },
          ],
        },
        {
          id: createActionId('lcp', 'server-timing'),
          priority: 'medium',
          title: 'Instrument Server Timing',
          description:
            'Add `Server-Timing` headers to identify backend bottlenecks impacting LCP.',
          expectedImprovement: 'Faster debugging',
          timeToImplement: '1 hour',
          difficulty: 'moderate',
          steps: [
            'Instrument render pipeline stages (DB, template, personalization).',
            'Visualize timings in Chrome DevTools > Network tab.',
            'Address the slowest leg first (e.g., database queries, third-party APIs).',
          ],
          codeExamples: [
            {
              language: 'js',
              code: `res.setHeader(
  'Server-Timing',
  ['app;dur=120', 'db;dur=80', 'edge;dur=20'].join(', ')
);`,
            },
          ],
          resources: [
            {
              title: 'Diagnose LCP with Server Timing',
              url: 'https://web.dev/articles/custom-metrics#server-timing',
            },
          ],
        },
        {
          id: createActionId('lcp', 'critical-css'),
          priority: 'medium',
          title: 'Generate Critical CSS',
          description:
            'Inline the minimum CSS required for above-the-fold content and lazy load the rest.',
          expectedImprovement: '-100 to -200ms',
          timeToImplement: '2 hours',
          difficulty: 'advanced',
          steps: [
            'Run a critical CSS tool (Critters, Penthouse) against key templates.',
            'Inline extracted CSS in the document head.',
            'Load the remaining CSS asynchronously using `rel="preload"` swap pattern.',
          ],
          resources: [
            {
              title: 'Extract critical CSS',
              url: 'https://web.dev/articles/extract-critical-css',
            },
          ],
        },
      ],
    },
  ],
  inp: [
    {
      metric: 'inp',
      severity: 'critical',
      title: 'Critical INP Remediation',
      summary:
        'User interactions are stalling. Focus on main thread relief and input handler optimization.',
      actions: [
        {
          id: createActionId('inp', 'break-long-tasks'),
          priority: 'high',
          title: 'Break Up Long Tasks',
          description:
            'Identify long JavaScript tasks (>50ms) and split them using scheduling primitives.',
          expectedImprovement: '-80 to -150ms',
          timeToImplement: '2 hours',
          difficulty: 'advanced',
          steps: [
            'Open Chrome DevTools performance panel and record an interaction.',
            'Locate long tasks and identify the functions responsible.',
            'Refactor heavy loops with `requestIdleCallback` or chunk processing with `setTimeout`.',
          ],
          codeExamples: [
            {
              language: 'ts',
              code: `const processItems = (items: Item[], index = 0) => {
  const chunk = items.slice(index, index + 50);
  chunk.forEach(expensiveWork);

  if (index + 50 < items.length) {
    requestIdleCallback(() => processItems(items, index + 50));
  }
};`,
            },
          ],
          resources: [
            {
              title: 'Optimize long tasks',
              url: 'https://web.dev/articles/optimize-long-tasks',
            },
            {
              title: 'Break up long tasks in React',
              url: 'https://react.dev/reference/react/useTransition',
            },
          ],
          tools: ['Chrome DevTools performance', 'Web Vitals extension'],
        },
        {
          id: createActionId('inp', 'move-work-off-main'),
          priority: 'high',
          title: 'Move Heavy Work Off the Main Thread',
          description:
            'Use web workers or Worklets to move CPU-heavy tasks away from the UI thread.',
          expectedImprovement: '-60 to -120ms',
          timeToImplement: '3 hours',
          difficulty: 'advanced',
          steps: [
            'Audit which interactions trigger heavy computation (filters, analytics).',
            'Create a dedicated worker that handles the heavy processing.',
            'Communicate results back via `postMessage` and update UI when ready.',
          ],
          codeExamples: [
            {
              language: 'ts',
              code: `// worker.ts
self.onmessage = ({ data }) => {
  const result = expensiveComputation(data.payload);
  self.postMessage(result);
};`,
            },
          ],
          resources: [
            {
              title: 'Use web workers',
              url: 'https://web.dev/articles/off-main-thread',
            },
            {
              title: 'Workerize guide',
              url: 'https://github.com/developit/workerize',
            },
          ],
          tools: ['workerize', 'Comlink'],
        },
        {
          id: createActionId('inp', 'optimize-event-handlers'),
          priority: 'medium',
          title: 'Optimize Event Handlers',
          description:
            'Reduce handler complexity and debounce low priority updates (analytics, logs).',
          expectedImprovement: '-40 to -80ms',
          timeToImplement: '1 hour',
          difficulty: 'moderate',
          steps: [
            'Profile the slowest interaction handlers via the Performance panel.',
            'Remove synchronous analytics/event logging and defer via `queueMicrotask`.',
            'Memoize handlers in React to avoid re-binding work.',
          ],
          codeExamples: [
            {
              language: 'ts',
              code: `const handleSearch = useCallback(
  (query: string) => {
    startTransition(() => executeSearch(query));
  },
  [executeSearch]
);`,
            },
          ],
          resources: [
            {
              title: 'Optimize input delay',
              url: 'https://web.dev/articles/optimize-inp',
            },
          ],
        },
      ],
    },
    {
      metric: 'inp',
      severity: 'warning',
      title: 'INP Optimization Opportunities',
      summary:
        'Responsiveness is drifting into the warning zone. Smooth out interaction spikes.',
      actions: [
        {
          id: createActionId('inp', 'lazy-load-noncritical-js'),
          priority: 'high',
          title: 'Lazy Load Non-critical JavaScript',
          description:
            'Split bundles so route-level JS loads only when needed, avoiding runtime contention.',
          expectedImprovement: '-40 to -90ms',
          timeToImplement: '2 hours',
          difficulty: 'moderate',
          steps: [
            'Enable code-splitting (dynamic import) for rarely accessed panels or visualizations.',
            'Defer hydration of below-the-fold widgets using intersection observers.',
          ],
          resources: [
            {
              title: 'Optimizing third-party script loading',
              url: 'https://web.dev/articles/optimize-third-party-scripts',
            },
          ],
        },
        {
          id: createActionId('inp', 'reduce-react-re-render'),
          priority: 'medium',
          title: 'Reduce React Re-render Storms',
          description:
            'Audit components that re-render on every keystroke and memoize expensive subtrees.',
          expectedImprovement: '-30 to -60ms',
          timeToImplement: '1.5 hours',
          difficulty: 'moderate',
          steps: [
            'Using React Profiler, capture a slow interaction and inspect component render counts.',
            'Wrap static child trees in `React.memo` and move state closer to leaf components.',
            'Adopt `useDeferredValue` for large list filters.',
          ],
          resources: [
            {
              title: 'Optimize React rendering',
              url: 'https://react.dev/learn/keeping-components-pure',
            },
          ],
        },
        {
          id: createActionId('inp', 'analytics-debounce'),
          priority: 'low',
          title: 'Debounce Low Priority Analytics',
          description:
            'Throttle analytics and logging calls triggered by interactions to keep handlers fast.',
          expectedImprovement: '-15 to -30ms',
          timeToImplement: '20 minutes',
          difficulty: 'easy',
          steps: [
            'Identify analytics calls fired in event handlers.',
            'Wrap in `requestIdleCallback` or debounce with a 250ms delay.',
            'Batch payloads before sending to the network.',
          ],
          resources: [
            {
              title: 'Queue analytics work',
              url: 'https://web.dev/articles/optimize-long-tasks#move_off_main_thread',
            },
          ],
        },
      ],
    },
  ],
  cls: [
    {
      metric: 'cls',
      severity: 'critical',
      title: 'Critical CLS Remediation',
      summary:
        'Unexpected layout shifts are disrupting users. Fix deterministic layout sizing and delayed content injection.',
      actions: [
        {
          id: createActionId('cls', 'reserve-media-space'),
          priority: 'high',
          title: 'Reserve Space for Media & Ads',
          description:
            'Assign explicit width and height to images, videos, and ad slots to prevent shifts.',
          expectedImprovement: '-0.15 to -0.25 CLS',
          timeToImplement: '1 hour',
          difficulty: 'easy',
          steps: [
            'Audit DOM for images/videos missing width and height attributes.',
            'Set CSS aspect-ratio or fixed dimensions for ad placeholders.',
            'Verify in Chrome DevTools Layout Shift Regions overlay.',
          ],
          codeExamples: [
            {
              language: 'css',
              code: `.ad-slot {
  width: 300px;
  height: 250px;
  background: #f8fafc;
}`,
            },
          ],
          resources: [
            {
              title: 'Avoid layout shifts',
              url: 'https://web.dev/articles/cls',
            },
          ],
        },
        {
          id: createActionId('cls', 'defer-nonlazy-content'),
          priority: 'medium',
          title: 'Defer Injected Content Below Viewport',
          description:
            'Use placeholders and load dynamic content below the fold to avoid mid-viewport injections.',
          expectedImprovement: '-0.05 to -0.1 CLS',
          timeToImplement: '45 minutes',
          difficulty: 'moderate',
          steps: [
            'Wrap dynamically inserted banners in placeholder containers sized to final content.',
            'Use `content-visibility: auto` for below-the-fold blocks.',
          ],
          resources: [
            {
              title: 'Stabilize layout with placeholders',
              url: 'https://web.dev/articles/optimize-cls',
            },
          ],
        },
        {
          id: createActionId('cls', 'font-loading'),
          priority: 'medium',
          title: 'Stabilize Font Loading',
          description:
            'Avoid FOIT/FOUT by preloading fonts and using `font-display: swap` with matching fallback metrics.',
          expectedImprovement: '-0.03 to -0.06 CLS',
          timeToImplement: '1 hour',
          difficulty: 'moderate',
          steps: [
            'Preload critical fonts and define fallback stacks with similar metrics.',
            'Leverage `font-size-adjust` to minimize reflow between fallback and final fonts.',
          ],
          codeExamples: [
            {
              language: 'css',
              code: `@font-face {
  font-family: 'Inter';
  src: url('/fonts/inter-var.woff2') format('woff2');
  font-display: swap;
}`,
            },
          ],
          resources: [
            {
              title: 'Font loading strategies to improve CLS',
              url: 'https://web.dev/articles/font-best-practices',
            },
          ],
        },
      ],
    },
    {
      metric: 'cls',
      severity: 'warning',
      title: 'CLS Optimization Opportunities',
      summary:
        'Layout shifts are creeping up. Address remaining causes to stay well below 0.1.',
      actions: [
        {
          id: createActionId('cls', 'animation-best-practices'),
          priority: 'medium',
          title: 'Use Transform-based Animations',
          description:
            'Ensure UI animations rely on `transform` and `opacity`, not properties that trigger layout.',
          expectedImprovement: '-0.02 to -0.04 CLS',
          timeToImplement: '45 minutes',
          difficulty: 'moderate',
          steps: [
            'Audit CSS for animations on height, width, margin, or top/left properties.',
            'Refactor to use translate/scale transforms with `will-change: transform`.',
          ],
          resources: [
            {
              title: 'High-performance animations',
              url: 'https://web.dev/articles/high-performance-animations',
            },
          ],
        },
        {
          id: createActionId('cls', 'schedule-ui-updates'),
          priority: 'low',
          title: 'Schedule UI Updates After Paint',
          description:
            'Wrap state updates that add DOM nodes in `requestAnimationFrame` to avoid mid-frame shifts.',
          expectedImprovement: '-0.01 CLS',
          timeToImplement: '30 minutes',
          difficulty: 'easy',
          steps: [
            'Identify React effects that synchronously insert DOM nodes on mount.',
            'Wrap non-critical DOM injections in `requestAnimationFrame` to schedule after paint.',
          ],
          resources: [
            {
              title: 'Avoid unexpected layout shifts',
              url: 'https://web.dev/articles/cls/#avoid-content-shifts',
            },
          ],
        },
      ],
    },
  ],
  tbt: [
    {
      metric: 'tbt',
      severity: 'critical',
      title: 'Critical TBT Remediation',
      summary:
        'Main thread blocking is severe. Focus on cutting bundle size and isolating heavy work.',
      actions: [
        {
          id: createActionId('tbt', 'analyze-bundle'),
          priority: 'high',
          title: 'Analyze and Trim Bundle Size',
          description:
            'Use bundle analyzers to identify largest synchronous chunks and tree-shake unused code.',
          expectedImprovement: '-120 to -200ms',
          timeToImplement: '3 hours',
          difficulty: 'advanced',
          steps: [
            'Generate a bundle report (Webpack Bundle Analyzer, Source Map Explorer).',
            'Split vendor chunks and lazy load route-specific dependencies.',
            'Remove unused polyfills or adopt differential serving.',
          ],
          resources: [
            {
              title: 'Reduce JavaScript payloads',
              url: 'https://web.dev/articles/reduce-javascript-payloads',
            },
          ],
          tools: ['webpack-bundle-analyzer', 'source-map-explorer'],
        },
        {
          id: createActionId('tbt', 'third-party-governance'),
          priority: 'high',
          title: 'Govern Third-party Scripts',
          description:
            'Audit third-party tags and asynchronously load or remove those that block the main thread.',
          expectedImprovement: '-80 to -140ms',
          timeToImplement: '2 hours',
          difficulty: 'moderate',
          steps: [
            'Capture a performance trace and filter for third-party long tasks.',
            'Self-host critical scripts to enable caching and control execution order.',
            'Lazy load marketing scripts behind user interaction when possible.',
          ],
          resources: [
            {
              title: 'Optimizing third-party scripts',
              url: 'https://web.dev/articles/optimize-third-party-scripts',
            },
          ],
        },
        {
          id: createActionId('tbt', 'stream-hydration'),
          priority: 'medium',
          title: 'Stream or Defer Hydration',
          description:
            'Use server components, partial/streaming hydration, or islands architecture to reduce synchronous work.',
          expectedImprovement: '-70 to -120ms',
          timeToImplement: '1 day',
          difficulty: 'advanced',
          steps: [
            'Identify components causing hydration bottlenecks (React Profiler).',
            'Adopt server components or islands for static sections.',
            'Hydrate complex widgets on intersection or after idle callbacks.',
          ],
          resources: [
            {
              title: 'Hydration strategies',
              url: 'https://nextjs.org/docs/app/building-your-application/routing/loading-ui',
              type: 'documentation',
            },
          ],
        },
      ],
    },
    {
      metric: 'tbt',
      severity: 'warning',
      title: 'TBT Optimization Opportunities',
      summary:
        'Main thread blocking is creeping up. Address medium complexity tasks before they regress.',
      actions: [
        {
          id: createActionId('tbt', 'idle-unimportant-work'),
          priority: 'medium',
          title: 'Schedule Non-critical Work During Idle',
          description:
            'Use `requestIdleCallback` for analytics, logging, and DOM mutations that can wait.',
          expectedImprovement: '-40 to -70ms',
          timeToImplement: '45 minutes',
          difficulty: 'easy',
          steps: [
            'Identify synchronous work after page load (analytics, DOM decoration).',
            'Wrap these in `requestIdleCallback` with appropriate timeout.',
          ],
          codeExamples: [
            {
              language: 'ts',
              code: `requestIdleCallback(
  () => hydrateNonCriticalWidgets(),
  { timeout: 2000 }
);`,
            },
          ],
          resources: [
            {
              title: 'Using requestIdleCallback',
              url: 'https://developer.mozilla.org/docs/Web/API/Window/requestIdleCallback',
            },
          ],
        },
        {
          id: createActionId('tbt', 'optimize-charts'),
          priority: 'medium',
          title: 'Virtualize Heavy UI Widgets',
          description:
            'Virtualize lists/tables and move heavy chart rendering off the critical path.',
          expectedImprovement: '-30 to -60ms',
          timeToImplement: '2 hours',
          difficulty: 'moderate',
          steps: [
            'Use React Window/Virtual to render above-the-fold rows.',
            'Defer chart rendering until container is visible using Intersection Observer.',
          ],
          resources: [
            {
              title: 'Virtualize long lists',
              url: 'https://react-window.now.sh/#/examples/list/fixed-size',
            },
          ],
        },
        {
          id: createActionId('tbt', 'optimize-build-pipeline'),
          priority: 'low',
          title: 'Enable Modern Build Optimizations',
          description:
            'Turn on tree-shaking, minification, and module/nomodule builds to reduce shipped JS.',
          expectedImprovement: '-20 to -40ms',
          timeToImplement: '1.5 hours',
          difficulty: 'easy',
          steps: [
            'Verify bundler is configured for production mode with minification.',
            'Ship modern syntax (ES2017+) to evergreen browsers via module/nomodule.',
          ],
          resources: [
            {
              title: 'JavaScript building best practices',
              url: 'https://web.dev/articles/publish-modern-javascript',
            },
          ],
        },
      ],
    },
  ],
};

export const hasPlaybook = (metric: string, severity?: 'warning' | 'critical'): boolean => {
  if (!ACTION_PLAYBOOKS[metric]) {
    return false;
  }

  return severity
    ? ACTION_PLAYBOOKS[metric].some((playbook) => playbook.severity === severity)
    : ACTION_PLAYBOOKS[metric].length > 0;
};

export const getPlaybook = (
  metric: string,
  severity: 'warning' | 'critical'
): ActionPlaybook | undefined => {
  return ACTION_PLAYBOOKS[metric]?.find((playbook) => playbook.severity === severity);
};

export const getPlaybookActions = (
  metric: string,
  severity: 'warning' | 'critical'
): PlaybookAction[] => {
  const playbook = getPlaybook(metric, severity);
  if (!playbook) {
    return [];
  }

  return [...playbook.actions].sort(
    (a, b) => priorityOrder[a.priority] - priorityOrder[b.priority]
  );
};

export const getTopActions = (
  metric: string,
  severity: 'warning' | 'critical',
  max = 2
): PlaybookAction[] => {
  return getPlaybookActions(metric, severity).slice(0, max);
};
