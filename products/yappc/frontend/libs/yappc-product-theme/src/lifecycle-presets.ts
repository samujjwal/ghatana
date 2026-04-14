/**
 * YAPPC lifecycle phase color presets.
 *
 * These are product-specific — they encode the 8-phase YAPPC lifecycle
 * visual semantics and must live here, not in shared @ghatana/tokens.
 *
 * @doc.type module
 * @doc.purpose YAPPC lifecycle phase visual presets
 * @doc.layer product
 * @doc.pattern Tokens
 */

/** YAPPC 8-phase lifecycle identifiers */
export type LifecyclePhase =
  | 'intent'
  | 'shape'
  | 'validate'
  | 'generate'
  | 'build'
  | 'run'
  | 'observe'
  | 'improve';

/**
 * Visual theme for a single lifecycle phase.
 * All colors are WCAG AA compliant (text contrast ≥ 4.5:1).
 */
export interface PhaseTheme {
  /** Background tint for the canvas surface */
  canvasBg: string;
  /** Subtle accent fill for UI panels */
  accent: string;
  /** Border color for nodes and panel edges */
  border: string;
  /** Primary text color — dark enough for AA contrast on canvasBg */
  text: string;
  /** Icon color */
  icon: string;
  /** Hover state background */
  hover: string;
}

/**
 * Per-phase visual themes.
 * Colors are chosen via color psychology and verified against WCAG AA.
 */
export const PHASE_THEMES: Record<LifecyclePhase, PhaseTheme> = {
  intent: {
    canvasBg: '#FFFBEB',
    accent: '#FEF3C7',
    border: '#FDE047',
    text: '#78350F',
    icon: '#F59E0B',
    hover: '#FEF9C3',
  },
  shape: {
    canvasBg: '#F0F9FF',
    accent: '#DBEAFE',
    border: '#60A5FA',
    text: '#1E3A8A',
    icon: '#3B82F6',
    hover: '#E0F2FE',
  },
  validate: {
    canvasBg: '#FEF3C7',
    accent: '#FDE68A',
    border: '#F59E0B',
    text: '#78350F',
    icon: '#D97706',
    hover: '#FEF9C3',
  },
  generate: {
    canvasBg: '#F3E8FF',
    accent: '#E9D5FF',
    border: '#A855F7',
    text: '#581C87',
    icon: '#9333EA',
    hover: '#FAF5FF',
  },
  build: {
    canvasBg: '#ECFDF5',
    accent: '#D1FAE5',
    border: '#10B981',
    text: '#064E3B',
    icon: '#059669',
    hover: '#D1FAE5',
  },
  run: {
    canvasBg: '#FFF7ED',
    accent: '#FED7AA',
    border: '#F97316',
    text: '#7C2D12',
    icon: '#EA580C',
    hover: '#FFEDD5',
  },
  observe: {
    canvasBg: '#FAF5FF',
    accent: '#E9D5FF',
    border: '#A855F7',
    text: '#581C87',
    icon: '#9333EA',
    hover: '#F3E8FF',
  },
  improve: {
    canvasBg: '#FDF2F8',
    accent: '#FBCFE8',
    border: '#EC4899',
    text: '#831843',
    icon: '#DB2777',
    hover: '#FCE7F3',
  },
};

/** Return the visual theme for a lifecycle phase, defaulting to intent. */
export function getPhaseTheme(phase: LifecyclePhase): PhaseTheme {
  return PHASE_THEMES[phase] ?? PHASE_THEMES.intent;
}

/** Human-readable label for each phase. */
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
  return labels[phase] ?? phase;
}

/** Lucide icon name for each phase. */
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
  return icons[phase] ?? 'Help';
}
