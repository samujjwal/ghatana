import type { RouteLifecycle } from '@ghatana/product-shell/types.js';

export type FlashItRole = 'member' | 'premium' | 'admin';

export interface FlashItRouteContract {
  readonly path: string;
  readonly label: string;
  readonly description: string;
  readonly group: string;
  readonly minimumRole: FlashItRole;
  readonly personas: readonly string[];
  readonly tiers: readonly string[];
  readonly actions: readonly string[];
  readonly cards: readonly string[];
  readonly iconName: string;
  readonly lifecycle: RouteLifecycle;
  readonly discoverable?: boolean;
}

export const flashItRouteContracts = [
  route('/', 'Dashboard', 'Overview of memories, activity, and recommended next steps.', 'Capture', 'member', ['view-dashboard'], ['weekly-activity', 'recent-moments', 'capture-shortcuts'], 'home', ['reflector', 'creator', 'caregiver'], ['core'], 'stable'),
  route('/capture', 'Capture', 'Capture a new moment with media and metadata.', 'Capture', 'member', ['capture-moment'], ['capture-prompt', 'media-uploader'], 'plus-circle', ['reflector', 'creator', 'caregiver'], ['core'], 'stable'),
  route('/moments', 'Moments', 'Browse captured memories and reflections.', 'Capture', 'member', ['view-moments'], ['moment-list', 'moment-filters'], 'file-text', ['reflector', 'creator', 'caregiver'], ['core'], 'stable'),
  route('/spheres', 'Spheres', 'Organize memory collections by sphere and context.', 'Capture', 'member', ['manage-spheres'], ['sphere-overview', 'sphere-health'], 'layers', ['reflector', 'creator', 'caregiver'], ['core'], 'stable'),
  route('/search', 'Search', 'Search across memories, tags, and summaries.', 'Discover', 'member', ['search-memories'], ['saved-searches', 'search-results'], 'search', ['reflector', 'creator', 'caregiver'], ['core'], 'stable'),
  route('/analytics', 'Analytics', 'Track trends, activity, and memory patterns.', 'Discover', 'premium', ['view-analytics'], ['meaning-metrics', 'trend-breakdown'], 'bar-chart-3', ['reflector', 'caregiver'], ['premium'], 'stable'),
  route('/reflection', 'Reflection', 'Review summaries and guided reflection insights.', 'Discover', 'member', ['review-reflection'], ['reflection-prompts', 'summary-panel'], 'brain', ['reflector'], ['core'], 'stable'),
  route('/collaboration', 'Collaboration', 'Shared review and collaboration workflows.', 'Governance', 'premium', ['share-memory'], ['shared-reviews', 'collaborator-activity'], 'users', ['caregiver', 'partner'], ['premium'], 'stable'),
  route('/memory-expansion', 'Memory Expansion', 'Enrich moments with additional context and prompts.', 'Governance', 'premium', ['expand-memory'], ['expansion-suggestions'], 'sparkles', ['reflector', 'creator'], ['premium'], 'preview', false),
  route('/language-insights', 'Language Insights', 'Analyze language evolution and communication trends.', 'Discover', 'premium', ['view-language-insights'], ['language-trends', 'return-to-meaning-rate'], 'languages', ['reflector'], ['premium'], 'preview', false),
  route('/settings', 'Settings', 'Profile, preferences, privacy, and account controls.', 'Governance', 'member', ['manage-settings'], ['privacy-controls', 'account-preferences'], 'settings', ['reflector', 'creator', 'caregiver'], ['core'], 'stable'),
] as const satisfies readonly FlashItRouteContract[];

function route(
  path: string,
  label: string,
  description: string,
  group: string,
  minimumRole: FlashItRole,
  actions: readonly string[],
  cards: readonly string[],
  iconName: string,
  personas: readonly string[] = ['reflector', 'creator', 'caregiver'],
  tiers: readonly string[] = ['core'],
  lifecycle: RouteLifecycle = 'stable',
  discoverable = true,
): FlashItRouteContract {
  return {
    path,
    label,
    description,
    group,
    minimumRole,
    personas,
    tiers,
    actions,
    cards,
    iconName,
    lifecycle,
    ...(discoverable ? {} : { discoverable: false }),
  };
}
