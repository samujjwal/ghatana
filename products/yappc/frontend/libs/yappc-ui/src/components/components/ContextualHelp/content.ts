/**
 * Default help content for the Contextual Help system
 * @module components/ContextualHelp/content
 */

import type { HelpContent } from './types';

/**
 * Default help topics provided by the system
 *
 * These topics cover:
 * - Getting started with the application
 * - Canvas and component interaction
 * - Collaboration features
 * - Keyboard shortcuts
 * - Troubleshooting and performance
 */
export const DEFAULT_HELP_CONTENT: HelpContent[] = [
  {
    id: 'canvas-basics',
    title: 'Canvas Basics',
    content:
      'Learn how to create and manage elements on the canvas. Drag components from the palette, connect nodes, and customize properties.',
    category: 'canvas',
    keywords: ['canvas', 'nodes', 'components', 'drag', 'connect', 'basics'],
    relatedTopics: ['component-palette', 'node-connections'],
    priority: 100,
  },
  {
    id: 'component-palette',
    title: 'Using the Component Palette',
    content:
      'The component palette contains reusable elements you can add to your canvas. Simply drag any component onto the canvas to create a new node.',
    category: 'canvas',
    keywords: ['palette', 'components', 'drag', 'elements', 'library'],
    relatedTopics: ['canvas-basics', 'node-properties'],
    priority: 90,
  },
  {
    id: 'node-connections',
    title: 'Connecting Nodes',
    content:
      "Create relationships between nodes by dragging from one node's output handle to another node's input handle. This creates a visual connection showing data flow.",
    category: 'canvas',
    keywords: ['connections', 'nodes', 'handles', 'flow', 'relationships'],
    relatedTopics: ['canvas-basics', 'data-flow'],
    priority: 85,
  },
  {
    id: 'keyboard-shortcuts',
    title: 'Keyboard Shortcuts',
    content:
      'Speed up your workflow with keyboard shortcuts. Press Cmd+K for command palette, Cmd+C/V for copy/paste, and Cmd+Z for undo.',
    category: 'shortcuts',
    keywords: ['keyboard', 'shortcuts', 'hotkeys', 'commands', 'productivity'],
    relatedTopics: ['command-palette', 'productivity-tips'],
    priority: 80,
  },
  {
    id: 'sharing-canvas',
    title: 'Sharing Your Canvas',
    content:
      'Collaborate with others by sharing your canvas. Click the share button to generate a link or invite team members by email.',
    category: 'collaboration',
    keywords: ['share', 'collaborate', 'team', 'invite', 'link'],
    relatedTopics: ['real-time-collaboration', 'permissions'],
    priority: 75,
  },
  {
    id: 'real-time-collaboration',
    title: 'Real-time Collaboration',
    content:
      'See who else is working on the canvas with live cursors and instant updates. Changes are synchronized automatically across all users.',
    category: 'collaboration',
    keywords: ['real-time', 'collaboration', 'cursors', 'sync', 'live'],
    relatedTopics: ['sharing-canvas', 'comments'],
    priority: 70,
  },
  {
    id: 'saving-work',
    title: 'Saving Your Work',
    content:
      'Your canvas is automatically saved as you work. You can also manually save by pressing Cmd+S or using the save button in the toolbar.',
    category: 'getting-started',
    keywords: ['save', 'autosave', 'backup', 'preserve', 'work'],
    relatedTopics: ['canvas-basics', 'export-import'],
    priority: 95,
  },
  {
    id: 'export-import',
    title: 'Export and Import',
    content:
      'Export your canvas as JSON, PNG, or SVG. Import existing canvases or templates to get started quickly.',
    category: 'getting-started',
    keywords: ['export', 'import', 'json', 'png', 'svg', 'templates'],
    relatedTopics: ['saving-work', 'file-formats'],
    priority: 65,
  },
  {
    id: 'common-issues',
    title: 'Common Issues',
    content:
      'Troubleshoot common problems like connection issues, slow performance, or missing features. Check your browser compatibility and clear cache if needed.',
    category: 'troubleshooting',
    keywords: ['issues', 'problems', 'troubleshoot', 'performance', 'browser'],
    relatedTopics: ['performance-tips', 'browser-support'],
    priority: 60,
  },
  {
    id: 'performance-tips',
    title: 'Performance Tips',
    content:
      'Optimize your canvas performance by limiting the number of nodes, using appropriate zoom levels, and closing unused browser tabs.',
    category: 'troubleshooting',
    keywords: ['performance', 'optimization', 'speed', 'memory', 'tips'],
    relatedTopics: ['common-issues', 'best-practices'],
    priority: 55,
  },
];
