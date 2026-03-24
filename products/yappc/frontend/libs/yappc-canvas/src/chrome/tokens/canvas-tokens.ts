export type LifecyclePhase =
  | 'intent'
  | 'shape'
  | 'validate'
  | 'generate'
  | 'run'
  | 'observe'
  | 'improve';

const lifecyclePhaseMap = {
  intent: {
    id: 'intent',
    title: 'Intent',
    description: 'Define problem and outcomes',
    emoji: '🎯',
    color: '#3b82f6',
  },
  shape: {
    id: 'shape',
    title: 'Shape',
    description: 'Design and model the solution',
    emoji: '🧩',
    color: '#6366f1',
  },
  validate: {
    id: 'validate',
    title: 'Validate',
    description: 'Verify assumptions and quality',
    emoji: '✅',
    color: '#14b8a6',
  },
  generate: {
    id: 'generate',
    title: 'Generate',
    description: 'Produce implementation artifacts',
    emoji: '⚙️',
    color: '#f59e0b',
  },
  run: {
    id: 'run',
    title: 'Run',
    description: 'Execute and operate workflows',
    emoji: '▶️',
    color: '#22c55e',
  },
  observe: {
    id: 'observe',
    title: 'Observe',
    description: 'Measure and monitor outcomes',
    emoji: '📈',
    color: '#06b6d4',
  },
  improve: {
    id: 'improve',
    title: 'Improve',
    description: 'Learn and iterate continuously',
    emoji: '🛠️',
    color: '#a855f7',
  },
} as const;

export const LIFECYCLE_PHASES = lifecyclePhaseMap;

const lifecyclePhaseList = Object.values(lifecyclePhaseMap);

export const CANVAS_TOKENS = {
  SPACING: {
    XXS: 2,
    XS: 4,
    SM: 8,
    MD: 12,
    LG: 16,
    XL: 24,
  },
  COLORS: {
    PRIMARY: '#3b82f6',
    INFO: '#0ea5e9',
    SUCCESS: '#22c55e',
    WARNING: '#f59e0b',
    DANGER: '#ef4444',
    PANEL_BG_LIGHT: '#ffffff',
    BORDER: '#d1d5db',
    BORDER_LIGHT: '#e5e7eb',
    SELECTION_BG: 'rgba(59, 130, 246, 0.12)',
    TEXT_PRIMARY: '#111827',
    TEXT_SECONDARY: '#6b7280',
    NEUTRAL_100: '#f3f4f6',
    NEUTRAL_200: '#e5e7eb',
    NEUTRAL_400: '#9ca3af',
    STICKY_YELLOW: '#fef08a',
    STICKY_PINK: '#fbcfe8',
    STICKY_BLUE: '#bfdbfe',
    STICKY_GREEN: '#bbf7d0',
    STICKY_ORANGE: '#fed7aa',
    DRAWING_BLACK: '#111827',
    DRAWING_RED: '#ef4444',
    DRAWING_BLUE: '#3b82f6',
    DRAWING_GREEN: '#22c55e',
    DRAWING_YELLOW: '#eab308',
    DRAWING_ORANGE: '#f97316',
    DRAWING_PURPLE: '#a855f7',
    DRAWING_PINK: '#ec4899',
  },
  TYPOGRAPHY: {
    XS: 12,
    SM: 14,
    BASE: 16,
    LG: 20,
    XL: 24,
    XXL: 32,
    SIZE: {
      MD: 14,
    },
  },
  FONT_WEIGHT: {
    MEDIUM: 500,
    SEMIBOLD: 600,
    BOLD: 700,
  },
  RADIUS: {
    SM: 6,
    MD: 8,
    LG: 12,
    FULL: 9999,
  },
  SHADOWS: {
    LG: '0 10px 20px rgba(0, 0, 0, 0.12)',
    XL: '0 20px 40px rgba(0, 0, 0, 0.18)',
  },
  Z_INDEX: {
    BACKGROUND: 0,
    CONTROLS: 100,
    SMART_GUIDES: 250,
    MODAL: 1400,
    TOAST: 1500,
  },
  TRANSITIONS: {
    FAST: '150ms ease',
    NORMAL: '250ms ease',
  },
  CANVAS: {
    PHASE_LANE_WIDTH: 2000,
    PHASE_SEPARATOR_WIDTH: 100,
  },
  DRAWING_PRESETS: {
    pen: { color: '#111827', width: 2, opacity: 1 },
    marker: { color: '#3b82f6', width: 4, opacity: 1 },
    highlighter: { color: '#eab308', width: 10, opacity: 0.35 },
    eraser: { color: '#ffffff', width: 14, opacity: 1 },
  },
  LIFECYCLE_PHASES: lifecyclePhaseList,
};
