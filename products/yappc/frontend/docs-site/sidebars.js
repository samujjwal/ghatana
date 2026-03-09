/**
 * Creating a sidebar enables you to:
 - create an ordered group of docs
 - render a sidebar for each doc of that group
 - provide next/previous navigation

 The sidebars can be generated from the filesystem, or explicitly defined here.

 Create as many sidebars as you want.
 */

// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  // Canvas features sidebar
  canvasSidebar: [
    {
      type: 'category',
      label: 'Getting Started',
      items: [
        'getting-started',
        'canvas/overview',
        'canvas/quick-start',
      ],
    },
    {
      type: 'category',
      label: 'Core Features',
      items: [
        'canvas/real-time-collaboration',
        'canvas/export-sharing',
        'canvas/performance',
        'canvas/security',
      ],
    },
    {
      type: 'category',
      label: 'Advanced Features',
      items: [
        'canvas/monitoring',
        'canvas/extensibility',
        'canvas/customization',
      ],
    },
    {
      type: 'category',
      label: 'Architecture',
      items: [
        'canvas/architecture/overview',
        'canvas/architecture/component-hierarchy',
        'canvas/architecture/state-management',
        'canvas/architecture/rendering-pipeline',
      ],
    },
  ],

  // Deployment sidebar
  deploymentSidebar: [
    {
      type: 'category',
      label: 'Deployment',
      items: [
        'deployment/overview',
        'deployment/blue-green',
        'deployment/feature-flags',
        'deployment/health-checks',
        'deployment/rollback',
      ],
    },
    {
      type: 'category',
      label: 'Operations',
      items: [
        'deployment/monitoring',
        'deployment/incident-response',
        'deployment/runbooks',
      ],
    },
  ],

  // Guides and tutorials sidebar
  guidesSidebar: [
    {
      type: 'category',
      label: 'Guides',
      items: [
        'guides/tutorials-overview',
        'guides/building-plugins',
        'guides/custom-components',
        'guides/theming',
      ],
    },
    {
      type: 'category',
      label: 'Examples',
      items: [
        'examples/basic-canvas',
        'examples/collaborative-editing',
        'examples/performance-optimization',
      ],
    },
    {
      type: 'category',
      label: 'API Reference',
      items: [
        'api/overview',
        'api/canvas-api',
        'api/deployment-api',
        'api/feature-flags-api',
      ],
    },
  ],
};

module.exports = sidebars;
