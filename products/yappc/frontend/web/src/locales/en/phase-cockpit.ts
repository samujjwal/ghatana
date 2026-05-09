/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

/**
 * P2-11: Phase cockpit i18n resource file for English locale.
 *
 * This file externalizes all user-facing text for the phase cockpit,
 * enabling proper internationalization and accessibility.
 *
 * @doc.type module
 * @doc.purpose Phase cockpit i18n resources (P2-11)
 * @doc.layer frontend
 * @doc.pattern i18n
 */
export const phaseCockpit = {
  // Phase names
  intent: {
    label: 'Intent',
    description: 'Capture and analyze project intent',
  },
  shape: {
    label: 'Shape',
    description: 'Derive system architecture and model',
  },
  validate: {
    label: 'Validate',
    description: 'Validate system against requirements',
  },
  generate: {
    label: 'Generate',
    description: 'Generate code artifacts',
  },
  run: {
    label: 'Run',
    description: 'Execute and test the system',
  },
  observe: {
    label: 'Observe',
    description: 'Observe system behavior and metrics',
  },
  learn: {
    label: 'Learn',
    description: 'Learn from execution results',
  },
  evolve: {
    label: 'Evolve',
    description: 'Evolve the system based on insights',
  },

  // Phase cockpit UI
  cockpit: {
    title: 'Phase Cockpit',
    subtitle: 'Project lifecycle management',
    loading: 'Loading phase data...',
    error: 'Failed to load phase data',
    retry: 'Retry',
  },

  // Blockers
  blockers: {
    title: 'Phase Blockers',
    subtitle: 'Issues preventing phase advancement',
    none: 'No blockers detected',
    resolve: 'Resolve Blockers',
  },

  // Evidence
  evidence: {
    title: 'Phase Evidence',
    subtitle: 'Supporting evidence for phase completion',
    none: 'No evidence available',
  },

  // Governance
  governance: {
    title: 'Governance Records',
    subtitle: 'Audit and compliance records',
    none: 'No governance records available',
  },

  // Suggestions
  suggestions: {
    title: 'Suggested Actions',
    subtitle: 'Recommended actions to advance the phase',
    none: 'No suggestions available',
    execute: 'Execute Action',
  },

  // Actions
  actions: {
    advance: 'Advance to Next Phase',
    cannotAdvance: 'Cannot advance phase',
    viewDetails: 'View Details',
    dismiss: 'Dismiss',
  },

  // Accessibility labels
  aria: {
    phaseStatus: 'Phase status',
    phaseProgress: 'Phase progress',
    blockerList: 'List of phase blockers',
    evidenceList: 'List of phase evidence',
    governanceList: 'List of governance records',
    suggestionList: 'List of suggested actions',
    actionButton: 'Execute action',
    dismissButton: 'Dismiss suggestion',
  },
} as const;

export type PhaseCockpitTranslations = typeof phaseCockpit;
