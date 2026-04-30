/**
 * Dashboard Template
 *
 * Pre-built dashboard page template.
 *
 * @packageDocumentation
 */

import type { PageConfig } from 'yappc-config-schema';

export const dashboardTemplate: PageConfig = {
  id: 'template-dashboard',
  title: 'Dashboard',
  route: '/dashboard',
  layout: 'canvas',
  components: [
    {
      id: 'header-1',
      type: 'Container',
      props: {
        style: { padding: '24px', backgroundColor: '#f5f5f5' },
      },
      children: ['title-1'],
      position: { x: 0, y: 0, width: 1200, height: 80 },
    },
    {
      id: 'title-1',
      type: 'Typography',
      props: {
        variant: 'h4',
        children: 'Dashboard',
      },
      position: { x: 24, y: 24, width: 400, height: 40 },
    },
    {
      id: 'stats-grid',
      type: 'Container',
      props: {
        display: 'grid',
        gridTemplateColumns: 'repeat(4, 1fr)',
        gap: '16px',
      },
      children: ['stat-card-1', 'stat-card-2', 'stat-card-3', 'stat-card-4'],
      position: { x: 0, y: 80, width: 1200, height: 120 },
    },
    {
      id: 'stat-card-1',
      type: 'Card',
      props: {
        title: 'Total Users',
        content: '1,234',
      },
      position: { x: 0, y: 0, width: 280, height: 100 },
    },
    {
      id: 'stat-card-2',
      type: 'Card',
      props: {
        title: 'Active Sessions',
        content: '456',
      },
      position: { x: 300, y: 0, width: 280, height: 100 },
    },
    {
      id: 'stat-card-3',
      type: 'Card',
      props: {
        title: 'Revenue',
        content: '$12,345',
      },
      position: { x: 600, y: 0, width: 280, height: 100 },
    },
    {
      id: 'stat-card-4',
      type: 'Card',
      props: {
        title: 'Conversion Rate',
        content: '3.2%',
      },
      position: { x: 900, y: 0, width: 280, height: 100 },
    },
  ],
  connections: {
    events: [],
    data: [],
    navigation: [],
  },
  metadata: {
    template: 'dashboard',
    version: '1.0.0',
  },
};
