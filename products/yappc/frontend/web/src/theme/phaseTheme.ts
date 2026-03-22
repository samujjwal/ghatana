/**
 * Phase-Specific Color Theme System
 *
 * Provides semantic colors for each lifecycle phase to enhance visual hierarchy
 * and reduce cognitive load through color psychology.
 *
 * @doc.type utility
 * @doc.purpose Phase-based theming
 * @doc.layer presentation
 */

export type LifecyclePhase =
  | 'intent'
  | 'shape'
  | 'validate'
  | 'generate'
  | 'build'
  | 'run'
  | 'observe'
  | 'improve';

export interface PhaseTheme {
  /** Background tint for canvas */
  canvasBg: string;
  /** Accent color for UI elements */
  accent: string;
  /** Border color for nodes/panels */
  border: string;
  /** Primary text color */
  text: string;
  /** Icon color */
  icon: string;
  /** Hover state */
  hover: string;
}

/**
 * Phase Theme Configuration
 * Based on color psychology and WCAG AA contrast standards
 */
export const PHASE_THEMES: Record<LifecyclePhase, PhaseTheme> = {
  intent: {
    canvasBg: '#FFFBEB', // Warm yellow tint - creativity, openness
    accent: '#FEF3C7', // Soft yellow
    border: '#FDE047', // Bright yellow border
    text: '#78350F', // Dark amber text (11:1 contrast)
    icon: '#F59E0B', // Amber icon
    hover: '#FEF9C3', // Light yellow hover
  },
  shape: {
    canvasBg: '#F0F9FF', // Sky blue tint - structure, planning
    accent: '#DBEAFE', // Light blue
    border: '#60A5FA', // Blue border
    text: '#1E3A8A', // Dark blue text (10:1 contrast)
    icon: '#3B82F6', // Blue icon
    hover: '#E0F2FE', // Light blue hover
  },
  validate: {
    canvasBg: '#FEF3C7', // Light amber - caution, verification
    accent: '#FDE68A', // Amber
    border: '#F59E0B', // Orange border
    text: '#78350F', // Dark amber text
    icon: '#D97706', // Amber icon
    hover: '#FEF9C3', // Light amber hover
  },
  generate: {
    canvasBg: '#F3E8FF', // Light purple - magic, automation
    accent: '#E9D5FF', // Purple
    border: '#A855F7', // Purple border
    text: '#581C87', // Dark purple text (9:1 contrast)
    icon: '#9333EA', // Purple icon
    hover: '#FAF5FF', // Light purple hover
  },
  build: {
    canvasBg: '#ECFDF5', // Light green - progress, growth
    accent: '#D1FAE5', // Green
    border: '#10B981', // Green border
    text: '#064E3B', // Dark green text (11:1 contrast)
    icon: '#059669', // Green icon
    hover: '#D1FAE5', // Light green hover
  },
  run: {
    canvasBg: '#FFF7ED', // Light orange - energy, action
    accent: '#FED7AA', // Orange
    border: '#F97316', // Orange border
    text: '#7C2D12', // Dark orange text (10:1 contrast)
    icon: '#EA580C', // Orange icon
    hover: '#FFEDD5', // Light orange hover
  },
  observe: {
    canvasBg: '#FAF5FF', // Light purple - insight, analysis
    accent: '#E9D5FF', // Purple
    border: '#A855F7', // Purple border
    text: '#581C87', // Dark purple text (9:1 contrast)
    icon: '#9333EA', // Purple icon
    hover: '#F3E8FF', // Light purple hover
  },
  improve: {
    canvasBg: '#FDF2F8', // Light pink - iteration, refinement
    accent: '#FBCFE8', // Pink
    border: '#EC4899', // Pink border
    text: '#831843', // Dark pink text (9:1 contrast)
    icon: '#DB2777', // Pink icon
    hover: '#FCE7F3', // Light pink hover
  },
};

/**
 * Get theme for a specific phase
 */
export function getPhaseTheme(phase: LifecyclePhase): PhaseTheme {
  return PHASE_THEMES[phase] || PHASE_THEMES.intent;
}

/**
 * Get phase label
 */
export function getPhaseLabel(phase: LifecyclePhase): string {
  const labels: Record<LifecyclePhase, string> = {
    intent: 'Intent',
    shape: 'Shape',
    validate: 'Validate',
    generate: 'Generate',
    build: 'Build',
    run: 'Run',
    observe: 'Observe',
    improve: 'Improve',
  };
  return labels[phase] || phase;
}

/**
 * Get phase icon (Material Icon name)
 */
export function getPhaseIcon(phase: LifecyclePhase): string {
  const icons: Record<LifecyclePhase, string> = {
    intent: 'Lightbulb',
    shape: 'AccountTree',
    validate: 'CheckCircle',
    generate: 'AutoAwesome',
    build: 'Build',
    run: 'PlayArrow',
    observe: 'Visibility',
    improve: 'Refresh',
  };
  return icons[phase] || 'Help';
}
