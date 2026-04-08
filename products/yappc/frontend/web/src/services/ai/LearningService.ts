/**
 * Learning Service
 *
 * Tracks user behaviour patterns and provides adaptive personalisation.
 * Stores usage data locally and derives preferences for UI adaptation.
 *
 * @doc.type service
 * @doc.purpose User behaviour learning and personalisation
 * @doc.layer product
 * @doc.pattern Service Layer
 */

// ============================================================================
// Types
// ============================================================================

export type ActionCategory =
  | 'navigation'
  | 'creation'
  | 'editing'
  | 'search'
  | 'ai-interaction'
  | 'collaboration'
  | 'settings';

export interface UserAction {
  id: string;
  category: ActionCategory;
  action: string;
  context: string;
  timestamp: number;
  metadata?: Record<string, unknown>;
}

export interface UsagePattern {
  category: ActionCategory;
  frequency: number;
  lastUsed: number;
  avgSessionCount: number;
}

export interface UserPreferences {
  frequentActions: string[];
  preferredLayout: 'compact' | 'comfortable' | 'spacious';
  navigationStyle: 'sidebar' | 'topbar' | 'command';
  aiAssistanceLevel: 'minimal' | 'moderate' | 'proactive';
  recentProjects: string[];
  pinnedFeatures: string[];
}

export interface LearningState {
  actions: UserAction[];
  patterns: UsagePattern[];
  preferences: UserPreferences;
  lastUpdated: number;
}

// ============================================================================
// Constants
// ============================================================================

const STORAGE_KEY = 'yappc-learning-state';
const MAX_ACTIONS = 500;
const PATTERN_WINDOW_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

// ============================================================================
// Persistence
// ============================================================================

function loadState(): LearningState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) return JSON.parse(raw) as LearningState;
  } catch {
    // ignore corrupt data
  }
  return createDefaultState();
}

function saveState(state: LearningState): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // storage full — silently degrade
  }
}

function createDefaultState(): LearningState {
  return {
    actions: [],
    patterns: [],
    preferences: {
      frequentActions: [],
      preferredLayout: 'comfortable',
      navigationStyle: 'sidebar',
      aiAssistanceLevel: 'moderate',
      recentProjects: [],
      pinnedFeatures: [],
    },
    lastUpdated: Date.now(),
  };
}

// ============================================================================
// Pattern Analysis
// ============================================================================

function analysePatterns(actions: UserAction[]): UsagePattern[] {
  const cutoff = Date.now() - PATTERN_WINDOW_MS;
  const recent = actions.filter((a) => a.timestamp > cutoff);

  const categoryMap = new Map<ActionCategory, UserAction[]>();
  for (const action of recent) {
    const group = categoryMap.get(action.category) ?? [];
    group.push(action);
    categoryMap.set(action.category, group);
  }

  const patterns: UsagePattern[] = [];
  for (const [category, group] of categoryMap.entries()) {
    const days = new Set(group.map((a) => new Date(a.timestamp).toDateString())).size;
    patterns.push({
      category,
      frequency: group.length,
      lastUsed: Math.max(...group.map((a) => a.timestamp)),
      avgSessionCount: days > 0 ? group.length / days : 0,
    });
  }

  return patterns.sort((a, b) => b.frequency - a.frequency);
}

function derivePreferences(
  patterns: UsagePattern[],
  actions: UserAction[],
): UserPreferences {
  // Frequent actions — top 5 by frequency
  const actionFreq = new Map<string, number>();
  for (const a of actions) {
    actionFreq.set(a.action, (actionFreq.get(a.action) ?? 0) + 1);
  }
  const frequentActions = [...actionFreq.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
    .map(([action]) => action);

  // Preferred layout — derive from action density
  const totalActions = actions.length;
  let preferredLayout: UserPreferences['preferredLayout'] = 'comfortable';
  if (totalActions > 200) preferredLayout = 'compact';
  else if (totalActions < 50) preferredLayout = 'spacious';

  // Navigation style — derive from navigation pattern frequency
  const navPattern = patterns.find((p) => p.category === 'navigation');
  const searchPattern = patterns.find((p) => p.category === 'search');
  let navigationStyle: UserPreferences['navigationStyle'] = 'sidebar';
  if (searchPattern && navPattern && searchPattern.frequency > navPattern.frequency) {
    navigationStyle = 'command';
  }

  // AI assistance level
  const aiPattern = patterns.find((p) => p.category === 'ai-interaction');
  let aiAssistanceLevel: UserPreferences['aiAssistanceLevel'] = 'moderate';
  if (aiPattern && aiPattern.avgSessionCount > 10) aiAssistanceLevel = 'proactive';
  else if (!aiPattern || aiPattern.avgSessionCount < 2) aiAssistanceLevel = 'minimal';

  // Recent projects
  const projectActions = actions.filter((a) => a.context.startsWith('project:'));
  const recentProjects = [...new Set(projectActions.map((a) => a.context.replace('project:', '')))]
    .slice(0, 5);

  return {
    frequentActions,
    preferredLayout,
    navigationStyle,
    aiAssistanceLevel,
    recentProjects,
    pinnedFeatures: [],
  };
}

// ============================================================================
// Public API
// ============================================================================

export function recordAction(action: Omit<UserAction, 'id' | 'timestamp'>): LearningState {
  const state = loadState();

  const newAction: UserAction = {
    ...action,
    id: `act-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    timestamp: Date.now(),
  };

  state.actions = [newAction, ...state.actions].slice(0, MAX_ACTIONS);
  state.patterns = analysePatterns(state.actions);
  state.preferences = derivePreferences(state.patterns, state.actions);
  state.lastUpdated = Date.now();

  saveState(state);
  return state;
}

export function getState(): LearningState {
  return loadState();
}

export function getPreferences(): UserPreferences {
  return loadState().preferences;
}

export function getPatterns(): UsagePattern[] {
  return loadState().patterns;
}

export function resetState(): void {
  saveState(createDefaultState());
}
