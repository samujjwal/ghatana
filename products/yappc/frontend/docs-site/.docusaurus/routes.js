import React from 'react';
import ComponentCreator from '@docusaurus/ComponentCreator';

export default [
  {
    path: '/docs/__docusaurus/debug',
    component: ComponentCreator('/docs/__docusaurus/debug', 'e58'),
    exact: true
  },
  {
    path: '/docs/__docusaurus/debug/config',
    component: ComponentCreator('/docs/__docusaurus/debug/config', '2ce'),
    exact: true
  },
  {
    path: '/docs/__docusaurus/debug/content',
    component: ComponentCreator('/docs/__docusaurus/debug/content', '11b'),
    exact: true
  },
  {
    path: '/docs/__docusaurus/debug/globalData',
    component: ComponentCreator('/docs/__docusaurus/debug/globalData', 'f13'),
    exact: true
  },
  {
    path: '/docs/__docusaurus/debug/metadata',
    component: ComponentCreator('/docs/__docusaurus/debug/metadata', 'bff'),
    exact: true
  },
  {
    path: '/docs/__docusaurus/debug/registry',
    component: ComponentCreator('/docs/__docusaurus/debug/registry', '830'),
    exact: true
  },
  {
    path: '/docs/__docusaurus/debug/routes',
    component: ComponentCreator('/docs/__docusaurus/debug/routes', '13e'),
    exact: true
  },
  {
    path: '/docs/search',
    component: ComponentCreator('/docs/search', '320'),
    exact: true
  },
  {
    path: '/docs/',
    component: ComponentCreator('/docs/', 'c0a'),
    routes: [
      {
        path: '/docs/',
        component: ComponentCreator('/docs/', 'ea2'),
        routes: [
          {
            path: '/docs/',
            component: ComponentCreator('/docs/', 'e08'),
            routes: [
              {
                path: '/docs/api/canvas-api',
                component: ComponentCreator('/docs/api/canvas-api', 'b14'),
                exact: true,
                sidebar: "guidesSidebar"
              },
              {
                path: '/docs/api/deployment-api',
                component: ComponentCreator('/docs/api/deployment-api', 'e52'),
                exact: true,
                sidebar: "guidesSidebar"
              },
              {
                path: '/docs/api/feature-flags-api',
                component: ComponentCreator('/docs/api/feature-flags-api', '102'),
                exact: true,
                sidebar: "guidesSidebar"
              },
              {
                path: '/docs/api/overview',
                component: ComponentCreator('/docs/api/overview', 'd8a'),
                exact: true,
                sidebar: "guidesSidebar"
              },
              {
                path: '/docs/canvas/architecture/component-hierarchy',
                component: ComponentCreator('/docs/canvas/architecture/component-hierarchy', 'bdc'),
                exact: true,
                sidebar: "canvasSidebar"
              },
              {
                path: '/docs/canvas/architecture/overview',
                component: ComponentCreator('/docs/canvas/architecture/overview', '15b'),
                exact: true,
                sidebar: "canvasSidebar"
              },
              {
                path: '/docs/canvas/architecture/rendering-pipeline',
                component: ComponentCreator('/docs/canvas/architecture/rendering-pipeline', '851'),
                exact: true,
                sidebar: "canvasSidebar"
              },
              {
                path: '/docs/canvas/architecture/state-management',
                component: ComponentCreator('/docs/canvas/architecture/state-management', 'f31'),
                exact: true,
                sidebar: "canvasSidebar"
              },
              {
                path: '/docs/canvas/customization',
                component: ComponentCreator('/docs/canvas/customization', 'a84'),
                exact: true,
                sidebar: "canvasSidebar"
              },
              {
                path: '/docs/canvas/export-sharing',
                component: ComponentCreator('/docs/canvas/export-sharing', 'cbc'),
                exact: true,
                sidebar: "canvasSidebar"
              },
              {
                path: '/docs/canvas/extensibility',
                component: ComponentCreator('/docs/canvas/extensibility', 'af6'),
                exact: true,
                sidebar: "canvasSidebar"
              },
              {
                path: '/docs/canvas/monitoring',
                component: ComponentCreator('/docs/canvas/monitoring', 'ebd'),
                exact: true,
                sidebar: "canvasSidebar"
              },
              {
                path: '/docs/canvas/overview',
                component: ComponentCreator('/docs/canvas/overview', '674'),
                exact: true,
                sidebar: "canvasSidebar"
              },
              {
                path: '/docs/canvas/performance',
                component: ComponentCreator('/docs/canvas/performance', '6ce'),
                exact: true,
                sidebar: "canvasSidebar"
              },
              {
                path: '/docs/canvas/quick-start',
                component: ComponentCreator('/docs/canvas/quick-start', 'b4d'),
                exact: true,
                sidebar: "canvasSidebar"
              },
              {
                path: '/docs/canvas/real-time-collaboration',
                component: ComponentCreator('/docs/canvas/real-time-collaboration', 'd1c'),
                exact: true,
                sidebar: "canvasSidebar"
              },
              {
                path: '/docs/canvas/security',
                component: ComponentCreator('/docs/canvas/security', '4f9'),
                exact: true,
                sidebar: "canvasSidebar"
              },
              {
                path: '/docs/deployment/blue-green',
                component: ComponentCreator('/docs/deployment/blue-green', 'ba0'),
                exact: true,
                sidebar: "deploymentSidebar"
              },
              {
                path: '/docs/deployment/feature-flags',
                component: ComponentCreator('/docs/deployment/feature-flags', 'f28'),
                exact: true,
                sidebar: "deploymentSidebar"
              },
              {
                path: '/docs/deployment/game-day-drills',
                component: ComponentCreator('/docs/deployment/game-day-drills', 'fe9'),
                exact: true
              },
              {
                path: '/docs/deployment/health-checks',
                component: ComponentCreator('/docs/deployment/health-checks', '348'),
                exact: true,
                sidebar: "deploymentSidebar"
              },
              {
                path: '/docs/deployment/incident-response',
                component: ComponentCreator('/docs/deployment/incident-response', 'a3d'),
                exact: true,
                sidebar: "deploymentSidebar"
              },
              {
                path: '/docs/deployment/monitoring',
                component: ComponentCreator('/docs/deployment/monitoring', '632'),
                exact: true,
                sidebar: "deploymentSidebar"
              },
              {
                path: '/docs/deployment/overview',
                component: ComponentCreator('/docs/deployment/overview', '8d5'),
                exact: true,
                sidebar: "deploymentSidebar"
              },
              {
                path: '/docs/deployment/pagerduty-integration',
                component: ComponentCreator('/docs/deployment/pagerduty-integration', '826'),
                exact: true
              },
              {
                path: '/docs/deployment/postmortems',
                component: ComponentCreator('/docs/deployment/postmortems', 'a5b'),
                exact: true
              },
              {
                path: '/docs/deployment/rollback',
                component: ComponentCreator('/docs/deployment/rollback', 'c06'),
                exact: true,
                sidebar: "deploymentSidebar"
              },
              {
                path: '/docs/deployment/runbooks',
                component: ComponentCreator('/docs/deployment/runbooks', '278'),
                exact: true,
                sidebar: "deploymentSidebar"
              },
              {
                path: '/docs/examples/basic-canvas',
                component: ComponentCreator('/docs/examples/basic-canvas', '71d'),
                exact: true,
                sidebar: "guidesSidebar"
              },
              {
                path: '/docs/examples/collaborative-editing',
                component: ComponentCreator('/docs/examples/collaborative-editing', '8a3'),
                exact: true,
                sidebar: "guidesSidebar"
              },
              {
                path: '/docs/examples/performance-optimization',
                component: ComponentCreator('/docs/examples/performance-optimization', '570'),
                exact: true,
                sidebar: "guidesSidebar"
              },
              {
                path: '/docs/guides/building-plugins',
                component: ComponentCreator('/docs/guides/building-plugins', '2d8'),
                exact: true,
                sidebar: "guidesSidebar"
              },
              {
                path: '/docs/guides/custom-components',
                component: ComponentCreator('/docs/guides/custom-components', '124'),
                exact: true,
                sidebar: "guidesSidebar"
              },
              {
                path: '/docs/guides/theming',
                component: ComponentCreator('/docs/guides/theming', 'f4b'),
                exact: true,
                sidebar: "guidesSidebar"
              },
              {
                path: '/docs/guides/tutorials-overview',
                component: ComponentCreator('/docs/guides/tutorials-overview', '571'),
                exact: true,
                sidebar: "guidesSidebar"
              },
              {
                path: '/docs/guides/video-tutorials',
                component: ComponentCreator('/docs/guides/video-tutorials', '670'),
                exact: true
              },
              {
                path: '/docs/',
                component: ComponentCreator('/docs/', '4fe'),
                exact: true,
                sidebar: "canvasSidebar"
              }
            ]
          }
        ]
      }
    ]
  },
  {
    path: '*',
    component: ComponentCreator('*'),
  },
];
