/**
 * Table Template
 *
 * Pre-built table page template.
 *
 * @packageDocumentation
 */

import type { PageConfig } from 'yappc-config-schema';

export const tableTemplate: PageConfig = {
  id: 'template-table',
  title: 'Table',
  route: '/table',
  layout: 'canvas',
  components: [
    {
      id: 'header-1',
      type: 'Container',
      props: {
        padding: '24px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
      },
      children: ['title-1', 'add-button'],
      position: { x: 0, y: 0, width: 1200, height: 80 },
    },
    {
      id: 'title-1',
      type: 'Typography',
      props: {
        variant: 'h4',
        children: 'Data Table',
      },
      position: { x: 24, y: 24, width: 400, height: 40 },
    },
    {
      id: 'add-button',
      type: 'Button',
      props: {
        variant: 'contained',
        children: 'Add New',
        startIcon: 'Plus',
      },
      position: { x: 1050, y: 24, width: 120, height: 40 },
    },
    {
      id: 'table-container',
      type: 'Container',
      props: {
        padding: '0 24px 24px',
      },
      children: ['data-table'],
      position: { x: 0, y: 80, width: 1200, height: 400 },
    },
    {
      id: 'data-table',
      type: 'Table',
      props: {
        columns: [
          { id: 'id', label: 'ID', width: 80 },
          { id: 'name', label: 'Name', width: 200 },
          { id: 'email', label: 'Email', width: 250 },
          { id: 'status', label: 'Status', width: 120 },
          { id: 'actions', label: 'Actions', width: 150 },
        ],
        rows: [],
      },
      position: { x: 24, y: 0, width: 1150, height: 350 },
    },
  ],
  connections: {
    events: [
      {
        id: 'event-add',
        sourceComponentId: 'add-button',
        eventType: 'onClick',
        targetComponentId: '',
        action: 'navigate',
      },
    ],
    data: [
      {
        id: 'data-binding-table',
        sourceComponentId: '',
        targetComponentId: 'data-table',
        bindingType: 'tableData',
      },
    ],
    navigation: [],
  },
  metadata: {
    template: 'table',
    version: '1.0.0',
  },
};
