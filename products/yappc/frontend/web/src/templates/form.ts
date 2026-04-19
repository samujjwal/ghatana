/**
 * Form Template
 *
 * Pre-built form page template.
 *
 * @packageDocumentation
 */

import type { PageConfig } from '@yappc/config-schema';

export const formTemplate: PageConfig = {
  id: 'template-form',
  title: 'Form',
  route: '/form',
  layout: 'canvas',
  components: [
    {
      id: 'header-1',
      type: 'Container',
      props: {
        padding: '24px',
      },
      children: ['title-1'],
      position: { x: 0, y: 0, width: 800, height: 80 },
    },
    {
      id: 'title-1',
      type: 'Typography',
      props: {
        variant: 'h4',
        children: 'Form Title',
      },
      position: { x: 24, y: 24, width: 400, height: 40 },
    },
    {
      id: 'form-container',
      type: 'Container',
      props: {
        padding: '24px',
        maxWidth: '600px',
      },
      children: ['form-field-1', 'form-field-2', 'submit-button'],
      position: { x: 0, y: 80, width: 800, height: 300 },
    },
    {
      id: 'form-field-1',
      type: 'TextField',
      props: {
        label: 'Name',
        placeholder: 'Enter your name',
        required: true,
      },
      position: { x: 24, y: 24, width: 550, height: 60 },
    },
    {
      id: 'form-field-2',
      type: 'TextField',
      props: {
        label: 'Email',
        placeholder: 'Enter your email',
        type: 'email',
        required: true,
      },
      position: { x: 24, y: 100, width: 550, height: 60 },
    },
    {
      id: 'submit-button',
      type: 'Button',
      props: {
        variant: 'contained',
        children: 'Submit',
      },
      position: { x: 24, y: 180, width: 120, height: 40 },
    },
  ],
  connections: {
    events: [
      {
        id: 'event-submit',
        sourceComponentId: 'submit-button',
        eventType: 'onClick',
        targetComponentId: 'form-container',
        action: 'submit',
      },
    ],
    data: [],
    navigation: [],
  },
  metadata: {
    template: 'form',
    version: '1.0.0',
  },
};
