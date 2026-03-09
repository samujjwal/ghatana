/**
 * Predefined Templates
 *
 * Collection of ready-to-use canvas templates.
 *
 * @module canvas/templates/predefinedTemplates
 */

import type { TemplateDefinition } from './TemplateDefinition';

// ============================================================================
// Login Form Template
// ============================================================================

export const loginFormTemplate: TemplateDefinition = {
  id: 'login-form',
  name: 'Login Form',
  description: 'Complete login form with email, password, and submit button',
  category: 'authentication',
  tags: ['login', 'authentication', 'form'],
  nodes: [
    {
      id: 'login-container',
      componentType: 'Card',
      schema: {
        type: 'Card',
        props: {
          title: 'Sign In',
          elevation: 2,
        },
      },
      position: { x: 0, y: 0 },
      children: ['login-form'],
    },
    {
      id: 'login-form',
      componentType: 'Form',
      schema: {
        type: 'Form',
        props: {
          onSubmit: 'handleLogin',
        },
      },
      position: { x: 20, y: 60 },
      children: ['email-field', 'password-field', 'submit-button'],
    },
    {
      id: 'email-field',
      componentType: 'TextField',
      schema: {
        type: 'TextField',
        props: {
          label: 'Email',
          type: 'email',
          required: true,
          fullWidth: true,
        },
      },
      position: { x: 0, y: 0 },
    },
    {
      id: 'password-field',
      componentType: 'TextField',
      schema: {
        type: 'TextField',
        props: {
          label: 'Password',
          type: 'password',
          required: true,
          fullWidth: true,
        },
      },
      position: { x: 0, y: 80 },
    },
    {
      id: 'submit-button',
      componentType: 'Button',
      schema: {
        type: 'Button',
        props: {
          label: 'Sign In',
          variant: 'primary',
          type: 'submit',
          fullWidth: true,
        },
      },
      position: { x: 0, y: 160 },
    },
  ],
  events: [
    {
      sourceNodeId: 'login-form',
      sourceEvent: 'onSubmit',
      targetNodeId: 'login-form',
      targetEvent: 'formSubmitted',
      payload: { action: 'login' },
    },
  ],
  metadata: {
    author: 'Canvas Team',
    version: '1.0.0',
    createdAt: new Date('2025-01-01'),
    updatedAt: new Date('2025-01-01'),
  },
};

// ============================================================================
// Dashboard Template
// ============================================================================

export const dashboardTemplate: TemplateDefinition = {
  id: 'dashboard',
  name: 'Dashboard Layout',
  description: 'Dashboard with header, sidebar, and content area',
  category: 'dashboard',
  tags: ['dashboard', 'layout', 'admin'],
  nodes: [
    {
      id: 'dashboard-container',
      componentType: 'Container',
      schema: {
        type: 'Container',
        props: {
          maxWidth: 'xl',
        },
      },
      position: { x: 0, y: 0 },
      children: ['dashboard-grid'],
    },
    {
      id: 'dashboard-grid',
      componentType: 'Grid',
      schema: {
        type: 'Grid',
        props: {
          container: true,
          spacing: 3,
        },
      },
      position: { x: 0, y: 0 },
      children: ['stat-card-1', 'stat-card-2', 'stat-card-3', 'stat-card-4'],
    },
    {
      id: 'stat-card-1',
      componentType: 'Card',
      schema: {
        type: 'Card',
        props: {
          title: 'Total Users',
          content: '1,234',
          elevation: 1,
        },
      },
      position: { x: 0, y: 0 },
    },
    {
      id: 'stat-card-2',
      componentType: 'Card',
      schema: {
        type: 'Card',
        props: {
          title: 'Revenue',
          content: '$12,345',
          elevation: 1,
        },
      },
      position: { x: 300, y: 0 },
    },
    {
      id: 'stat-card-3',
      componentType: 'Card',
      schema: {
        type: 'Card',
        props: {
          title: 'Orders',
          content: '567',
          elevation: 1,
        },
      },
      position: { x: 600, y: 0 },
    },
    {
      id: 'stat-card-4',
      componentType: 'Card',
      schema: {
        type: 'Card',
        props: {
          title: 'Growth',
          content: '+23%',
          elevation: 1,
        },
      },
      position: { x: 900, y: 0 },
    },
  ],
  metadata: {
    author: 'Canvas Team',
    version: '1.0.0',
    createdAt: new Date('2025-01-01'),
    updatedAt: new Date('2025-01-01'),
  },
};

// ============================================================================
// Settings Form Template
// ============================================================================

export const settingsFormTemplate: TemplateDefinition = {
  id: 'settings-form',
  name: 'Settings Form',
  description: 'Settings form with tabs and various input types',
  category: 'settings',
  tags: ['settings', 'form', 'configuration'],
  nodes: [
    {
      id: 'settings-container',
      componentType: 'Container',
      schema: {
        type: 'Container',
        props: {
          maxWidth: 'md',
        },
      },
      position: { x: 0, y: 0 },
      children: ['settings-tabs'],
    },
    {
      id: 'settings-tabs',
      componentType: 'Tabs',
      schema: {
        type: 'Tabs',
        props: {
          tabs: [
            { label: 'General', value: 'general' },
            { label: 'Notifications', value: 'notifications' },
            { label: 'Privacy', value: 'privacy' },
          ],
        },
      },
      position: { x: 0, y: 0 },
      children: ['general-form', 'notifications-form'],
    },
    {
      id: 'general-form',
      componentType: 'Stack',
      schema: {
        type: 'Stack',
        props: {
          spacing: 2,
          direction: 'column',
        },
      },
      position: { x: 0, y: 60 },
      children: ['name-field', 'email-field', 'bio-field'],
    },
    {
      id: 'name-field',
      componentType: 'TextField',
      schema: {
        type: 'TextField',
        props: {
          label: 'Display Name',
          fullWidth: true,
        },
      },
      position: { x: 0, y: 0 },
    },
    {
      id: 'email-field',
      componentType: 'TextField',
      schema: {
        type: 'TextField',
        props: {
          label: 'Email Address',
          type: 'email',
          fullWidth: true,
        },
      },
      position: { x: 0, y: 70 },
    },
    {
      id: 'bio-field',
      componentType: 'TextField',
      schema: {
        type: 'TextField',
        props: {
          label: 'Bio',
          multiline: true,
          rows: 4,
          fullWidth: true,
        },
      },
      position: { x: 0, y: 140 },
    },
    {
      id: 'notifications-form',
      componentType: 'Stack',
      schema: {
        type: 'Stack',
        props: {
          spacing: 1,
          direction: 'column',
        },
      },
      position: { x: 0, y: 60 },
    },
  ],
  metadata: {
    author: 'Canvas Team',
    version: '1.0.0',
    createdAt: new Date('2025-01-01'),
    updatedAt: new Date('2025-01-01'),
  },
};

// ============================================================================
// Data Table Template
// ============================================================================

export const dataTableTemplate: TemplateDefinition = {
  id: 'data-table',
  name: 'Data Table',
  description: 'Data table with search, filters, and pagination',
  category: 'data-display',
  tags: ['table', 'data', 'list'],
  nodes: [
    {
      id: 'table-container',
      componentType: 'Card',
      schema: {
        type: 'Card',
        props: {
          title: 'Data Table',
          elevation: 1,
        },
      },
      position: { x: 0, y: 0 },
      children: ['table-toolbar', 'table-content'],
    },
    {
      id: 'table-toolbar',
      componentType: 'Stack',
      schema: {
        type: 'Stack',
        props: {
          direction: 'row',
          spacing: 2,
          alignItems: 'center',
        },
      },
      position: { x: 20, y: 60 },
      children: ['search-field', 'filter-button'],
    },
    {
      id: 'search-field',
      componentType: 'TextField',
      schema: {
        type: 'TextField',
        props: {
          label: 'Search',
          placeholder: 'Search...',
          size: 'small',
        },
      },
      position: { x: 0, y: 0 },
    },
    {
      id: 'filter-button',
      componentType: 'Button',
      schema: {
        type: 'Button',
        props: {
          label: 'Filters',
          variant: 'outlined',
          size: 'small',
        },
      },
      position: { x: 250, y: 0 },
    },
    {
      id: 'table-content',
      componentType: 'Container',
      schema: {
        type: 'Container',
        props: {
          // Table component placeholder
        },
      },
      position: { x: 20, y: 120 },
    },
  ],
  bindings: [
    {
      sourceNodeId: 'search-field',
      targetNodeId: 'table-content',
      sourcePath: 'value',
      targetProp: 'searchQuery',
      mode: 'one-way',
    },
  ],
  events: [
    {
      sourceNodeId: 'filter-button',
      sourceEvent: 'onClick',
      targetNodeId: 'table-content',
      targetEvent: 'openFilters',
    },
  ],
  metadata: {
    author: 'Canvas Team',
    version: '1.0.0',
    createdAt: new Date('2025-01-01'),
    updatedAt: new Date('2025-01-01'),
  },
};

// ============================================================================
// Wizard Template
// ============================================================================

export const wizardTemplate: TemplateDefinition = {
  id: 'wizard',
  name: 'Multi-Step Wizard',
  description: 'Multi-step wizard with navigation',
  category: 'form',
  tags: ['wizard', 'stepper', 'multi-step'],
  nodes: [
    {
      id: 'wizard-container',
      componentType: 'Card',
      schema: {
        type: 'Card',
        props: {
          title: 'Setup Wizard',
          elevation: 2,
        },
      },
      position: { x: 0, y: 0 },
      children: ['wizard-content', 'wizard-actions'],
    },
    {
      id: 'wizard-content',
      componentType: 'Container',
      schema: {
        type: 'Container',
        props: {
          // Step content
        },
      },
      position: { x: 20, y: 60 },
    },
    {
      id: 'wizard-actions',
      componentType: 'Stack',
      schema: {
        type: 'Stack',
        props: {
          direction: 'row',
          spacing: 2,
          justifyContent: 'flex-end',
        },
      },
      position: { x: 20, y: 300 },
      children: ['back-button', 'next-button'],
    },
    {
      id: 'back-button',
      componentType: 'Button',
      schema: {
        type: 'Button',
        props: {
          label: 'Back',
          variant: 'outlined',
        },
      },
      position: { x: 0, y: 0 },
    },
    {
      id: 'next-button',
      componentType: 'Button',
      schema: {
        type: 'Button',
        props: {
          label: 'Next',
          variant: 'primary',
        },
      },
      position: { x: 100, y: 0 },
    },
  ],
  events: [
    {
      sourceNodeId: 'back-button',
      sourceEvent: 'onClick',
      targetNodeId: 'wizard-content',
      targetEvent: 'previousStep',
    },
    {
      sourceNodeId: 'next-button',
      sourceEvent: 'onClick',
      targetNodeId: 'wizard-content',
      targetEvent: 'nextStep',
    },
  ],
  metadata: {
    author: 'Canvas Team',
    version: '1.0.0',
    createdAt: new Date('2025-01-01'),
    updatedAt: new Date('2025-01-01'),
  },
};

// ============================================================================
// Template Registry
// ============================================================================

export const predefinedTemplates: TemplateDefinition[] = [
  loginFormTemplate,
  dashboardTemplate,
  settingsFormTemplate,
  dataTableTemplate,
  wizardTemplate,
];

/**
 *
 */
export function getTemplateById(id: string): TemplateDefinition | undefined {
  return predefinedTemplates.find((t) => t.id === id);
}

/**
 *
 */
export function getTemplatesByCategory(
  category: TemplateDefinition['category']
): TemplateDefinition[] {
  return predefinedTemplates.filter((t) => t.category === category);
}

/**
 *
 */
export function searchTemplates(query: string): TemplateDefinition[] {
  const lowerQuery = query.toLowerCase();
  return predefinedTemplates.filter(
    (t) =>
      t.name.toLowerCase().includes(lowerQuery) ||
      t.description.toLowerCase().includes(lowerQuery) ||
      t.tags.some((tag) => tag.toLowerCase().includes(lowerQuery))
  );
}
