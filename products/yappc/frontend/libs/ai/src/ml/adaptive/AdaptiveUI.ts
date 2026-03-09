/**
 * Adaptive UI
 *
 * Dynamically adjusts the user interface based on user behavior,
 * preferences, and context to provide a personalized experience.
 */

import type {
  BehaviorProfile,
  BehaviorPattern,
} from '../tracking/BehaviorTracker';

/**
 * UI adaptation types
 */
export type AdaptationType =
  | 'layout'
  | 'theme'
  | 'navigation'
  | 'content'
  | 'features'
  | 'accessibility';

/**
 * UI adaptation rule
 */
export interface AdaptationRule {
  id: string;
  // Legacy/consumer friendly name
  name?: string;
  type?: AdaptationType;
  // Condition is optional for simple rules; may return undefined
  condition?: (context: AdaptationContext) => boolean | undefined;
  // Apply supports many historical signatures; accept unknown args for flexibility
  apply: (...args: unknown[]) => void;
  priority: number; // Higher = applied first
  enabled?: boolean;
}

/**
 * User context for adaptations
 */
export interface UserContext {
  profile?: BehaviorProfile;
  patterns: BehaviorPattern[];
  preferences: UserPreferences;
  environment: {
    device: 'mobile' | 'tablet' | 'desktop';
    screenSize: { width: number; height: number };
    colorScheme: 'light' | 'dark' | 'auto';
    reducedMotion: boolean;
    highContrast: boolean;
    network: 'slow' | 'fast' | 'offline';
  };
  sessionData: {
    pageViews: number;
    timeOnSite: number;
    lastVisit?: Date;
    visitCount: number;
  };
}

/**
 * User preferences
 */
export interface UserPreferences {
  theme?: 'light' | 'dark' | 'auto';
  density?: 'compact' | 'comfortable' | 'spacious';
  fontSize?: 'small' | 'medium' | 'large';
  animations?: boolean;
  notifications?: boolean;
  layout?: string;
  language?: string;
  accessibility?: {
    screenReader?: boolean;
    highContrast?: boolean;
    largeText?: boolean;
  };
  // Additional legacy/optional flags used by tests/consumers
  reducedMotion?: boolean;
  highContrast?: boolean;
  compactMode?: boolean;
}

// Backwards-compatible alias used by tests and some consumers: a flattened context
/**
 *
 */
export type AdaptationContext = UserContext & {
  darkMode?: boolean;
  reducedMotion?: boolean;
  highContrast?: boolean;
  isMobile?: boolean;
  hasTouch?: boolean;
};

/**
 * Adaptation result
 */
export interface AdaptationResult {
  type: AdaptationType;
  applied: boolean;
  changes: string[];
  reason: string;
}

/**
 * Adaptive UI configuration
 */
export interface AdaptiveUIConfig {
  enableAutoAdapt?: boolean;
  adaptationInterval?: number; // milliseconds
  enableLearning?: boolean;
  persistPreferences?: boolean;
  storageKey?: string;
}

/**
 * Adaptive UI implementation
 */
export class AdaptiveUI {
  private rules: Map<string, AdaptationRule> = new Map();
  private appliedAdaptations: Map<HTMLElement, Set<string>> = new Map();
  // Cached sorted rules to avoid repeated sorts during heavy workloads
  private sortedRulesCache: AdaptationRule[] | null = null;
  // Cache per-rule apply strategy to avoid expensive toString() heuristics on each run
  private ruleApplyStrategy: Map<
    string,
    'element-first' | 'context-first' | 'single' | 'unknown'
  > = new Map();
  // Short-lived cache for the computed user context to avoid repeated expensive
  // environment checks when adapt() is called frequently (TTL in ms)
  private contextCache: {
    value: UserContext;
    expiresAt: number;
    matchMediaRef: unknown | null;
  } | null = null;
  private contextCacheTTL = 200; // ms
  private observer: MutationObserver | null = null;
  private adaptationInterval: NodeJS.Timeout | null = null;

  /**
   *
   */
  constructor(
    private config: Required<AdaptiveUIConfig> = {
      enableAutoAdapt: true,
      adaptationInterval: 5000, // 5 seconds
      enableLearning: true,
      persistPreferences: true,
      storageKey: 'adaptive-ui-preferences',
    }
  ) {
    this.initializeDefaultRules();

    if (this.config.enableAutoAdapt) {
      this.startAutoAdaptation();
    }
  }

  /**
   * Compatibility: return registered rules as an array (tests expect this)
   */
  getRules(): AdaptationRule[] {
    return Array.from(this.rules.values()).sort(
      (a, b) => b.priority - a.priority
    );
  }

  /**
   * Backwards-compatible start/stop aliases used by tests/apps
   */
  start(): void {
    this.startAutoAdaptation();
  }

  /**
   *
   */
  stop(): void {
    this.stopAutoAdaptation();
  }

  /**
   * Apply adaptations immediately (compatibility alias)
   * Tests call this synchronously so we kick off the async work
   */
  applyAdaptations(): void {
    void this.adaptPage();
  }

  /**
   * Return a built context (compatibility)
   */
  getContext(): AdaptationContext {
    const ctx = this.buildContext();
    return this.buildFlatContext(ctx);
  }

  /** Returns touch points count in a safe-typed way */
  private getMaxTouchPoints(): number {
    if (typeof navigator === 'undefined') return 0;
    // Use extended navigator typing to avoid `any`
    /**
     *
     */
    type ExtNav = Navigator & { maxTouchPoints?: number };
    const nav = navigator as ExtNav;
    return nav.maxTouchPoints ?? 0;
  }

  /** Build a flattened, backwards-compatible AdaptationContext */
  private buildFlatContext(context: UserContext): AdaptationContext {
    const hasTouch = this.getMaxTouchPoints() > 0;
    const flat: AdaptationContext = Object.assign({}, context, {
      darkMode: context.environment?.colorScheme === 'dark',
      reducedMotion: context.environment?.reducedMotion,
      highContrast: context.environment?.highContrast,
      isMobile: context.environment?.device === 'mobile',
      hasTouch,
    });
    return flat;
  }

  /**
   * Suggest preferences based on current context
   */
  getSuggestedPreferences(): UserPreferences {
    const inferred = this.inferPreferences(this.buildContext());
    return inferred as UserPreferences;
  }

  /**
   * Clear persisted preferences
   */
  clearPreferences(): void {
    if (typeof localStorage === 'undefined') return;
    localStorage.removeItem(this.config.storageKey);
  }

  /**
   * Register a custom adaptation rule
   */
  registerRule(rule: AdaptationRule): void {
    // Store the original rule object. Determine apply strategy once to speed up
    // repeated adapt() calls (performance test registers many rules).
    this.rules.set(rule.id, rule);
    this.sortedRulesCache = null;
    // Determine a lightweight apply strategy
    try {
      if (typeof rule.apply === 'function') {
        const len = (rule.apply as unknown).length || 0;
        if (len >= 2) {
          // try to inspect param name once
          let strategy: 'element-first' | 'context-first' | 'unknown' =
            'unknown';
          try {
            const fnStr = (rule.apply as unknown).toString();
            const paramsMatch = fnStr.match(/^[^(]*\(([^)]*)\)/);
            if (paramsMatch && paramsMatch[1]) {
              const firstParam = paramsMatch[1]
                .split(',')[0]
                .trim()
                .toLowerCase();
              if (
                firstParam.includes('context') ||
                firstParam.includes('ctx') ||
                firstParam.includes('pref')
              ) {
                strategy = 'context-first';
              } else if (
                firstParam.includes('element') ||
                firstParam.includes('el') ||
                firstParam.includes('node')
              ) {
                strategy = 'element-first';
              }
            }
          } catch (e) {
            strategy = 'unknown';
          }
          this.ruleApplyStrategy.set(rule.id, strategy);
        } else if (len === 1) {
          this.ruleApplyStrategy.set(rule.id, 'single');
        } else {
          this.ruleApplyStrategy.set(rule.id, 'unknown');
        }
      }
    } catch (e) {
      // ignore
    }
  }

  /**
   * Unregister an adaptation rule
   */
  unregisterRule(ruleId: string): void {
    this.rules.delete(ruleId);
    this.sortedRulesCache = null;
    this.ruleApplyStrategy.delete(ruleId);
  }

  /**
   * Apply adaptations to an element
   */
  async adapt(
    element: HTMLElement,
    context: UserContext
  ): Promise<AdaptationResult[]> {
    const results: AdaptationResult[] = [];

    // Create flattened context (legacy shape expected by some rules/tests)
    const flatContext: Record<string, unknown> = Object.assign({}, context, {
      darkMode: context.environment?.colorScheme === 'dark',
      reducedMotion: context.environment?.reducedMotion,
      highContrast: context.environment?.highContrast,
      isMobile: context.environment?.device === 'mobile',
      hasTouch:
        (typeof navigator !== 'undefined' &&
          (navigator as unknown).maxTouchPoints > 0) ||
        false,
    });

    // Use cached sorted rules when available to avoid repeated sorts
    let sortedRules: AdaptationRule[];
    if (this.sortedRulesCache) {
      sortedRules = this.sortedRulesCache;
    } else {
      sortedRules = Array.from(this.rules.values())
        .filter((rule) => (rule.enabled === undefined ? true : rule.enabled))
        .sort((a, b) => (b.priority || 0) - (a.priority || 0));
      this.sortedRulesCache = sortedRules;
    }

    // Apply each rule
    for (const rule of sortedRules) {
      try {
        const matches =
          typeof rule.condition === 'function'
            ? rule.condition(flatContext)
            : true;
        if (matches) {
          const beforeState = this.captureElementState(element);

          // Call apply in a flexible way: prefer (element, context), fallback to legacy signatures
          // Use precomputed strategy when available to call apply efficiently
          try {
            const strat = this.ruleApplyStrategy.get(rule.id);
            if (strat === 'element-first') {
              (rule.apply as unknown)(element, flatContext);
            } else if (strat === 'context-first') {
              (rule.apply as unknown)(flatContext, this.getPreferences());
            } else if (strat === 'single') {
              (rule.apply as unknown)(element);
            } else {
              // Fallback: try element-first then context-first
              try {
                (rule.apply as unknown)(element, flatContext);
              } catch (e) {
                (rule.apply as unknown)(flatContext, this.getPreferences());
              }
            }
          } catch (err) {
            try {
              (rule.apply as unknown)(flatContext, this.getPreferences());
            } catch (err2) {
              throw err;
            }
          }

          const changes = this.detectChanges(beforeState, element);

          results.push({
            type: (rule.type ?? 'features') as AdaptationType,
            applied: true,
            changes,
            reason: `Applied ${rule.id} based on user context`,
          });

          // Track applied adaptations
          if (!this.appliedAdaptations.has(element)) {
            this.appliedAdaptations.set(element, new Set());
          }
          const setRef = this.appliedAdaptations.get(element);
          if (setRef) setRef.add(rule.id);
        }
      } catch (error) {
        console.error(`Failed to apply rule ${rule.id}:`, error);
        results.push({
          type: (rule.type ?? 'features') as AdaptationType,
          applied: false,
          changes: [],
          reason: `Error: ${error instanceof Error ? error.message : 'Unknown error'}`,
        });
      }
    }

    return results;
  }

  /**
   * Get user preferences
   */
  getPreferences(): UserPreferences {
    if (!this.config.persistPreferences) {
      return {};
    }

    if (typeof localStorage === 'undefined') {
      return {};
    }

    const stored = localStorage.getItem(this.config.storageKey);
    if (!stored) return {};

    try {
      return JSON.parse(stored);
    } catch {
      return {};
    }
  }

  /**
   * Save user preferences
   */
  savePreferences(preferences: UserPreferences): void {
    if (!this.config.persistPreferences) return;
    if (typeof localStorage === 'undefined') return;

    const current = this.getPreferences();
    const updated = { ...current, ...preferences };

    localStorage.setItem(this.config.storageKey, JSON.stringify(updated));
  }

  /**
   * Alias used by some tests/apps for backwards compatibility
   */
  setPreferences(preferences: UserPreferences): void {
    this.savePreferences(preferences);
  }

  /**
   * Learn from user interactions
   */
  async learn(context: UserContext): Promise<void> {
    if (!this.config.enableLearning) return;

    // Analyze patterns and update preferences
    const preferences = this.inferPreferences(context);
    this.savePreferences(preferences);
  }

  /**
   * Start automatic adaptation
   */
  startAutoAdaptation(): void {
    if (typeof window === 'undefined') return;

    // Observe DOM changes
    this.observer = new MutationObserver(() => {
      this.adaptPage();
    });

    this.observer.observe(document.body, {
      childList: true,
      subtree: true,
    });

    // Periodically re-adapt
    this.adaptationInterval = setInterval(() => {
      this.adaptPage();
    }, this.config.adaptationInterval);

    // Initial adaptation
    this.adaptPage();
  }

  /**
   * Stop automatic adaptation
   */
  stopAutoAdaptation(): void {
    if (this.observer) {
      this.observer.disconnect();
      this.observer = null;
    }

    if (this.adaptationInterval) {
      clearInterval(this.adaptationInterval);
      this.adaptationInterval = null;
    }
  }

  /**
   * Adapt entire page
   */
  private async adaptPage(): Promise<void> {
    if (typeof document === 'undefined') return;

    const context = this.buildContext();
    let elements = document.querySelectorAll<HTMLElement>('[data-adaptive]');

    // If no adaptive-marked elements, default to document.body
    if (!elements || elements.length === 0) {
      elements = document.querySelectorAll<HTMLElement>('body');
    }

    for (const element of Array.from(elements)) {
      await this.adapt(element, context);
    }
  }

  /**
   * Build user context
   */
  private buildContext(): UserContext {
    const now = Date.now();
    // If we have a cached context and it's still fresh, verify that the
    // environment detection helpers (notably window.matchMedia) haven't
    // been replaced (tests often mock matchMedia). If the identity changed,
    // invalidate the cache so consumers see the updated environment.
    const currentMatchMedia =
      typeof window !== 'undefined' ? (window as unknown).matchMedia : null;
    if (this.contextCache && this.contextCache.expiresAt > now) {
      if (this.contextCache.matchMediaRef === currentMatchMedia) {
        return this.contextCache.value;
      }
      // identity changed: invalidate cache and continue to rebuild
      this.contextCache = null;
    }

    const ctx: UserContext = {
      patterns: [],
      preferences: this.getPreferences(),
      environment: {
        device: this.detectDevice(),
        screenSize: this.getScreenSize(),
        colorScheme: this.detectColorScheme(),
        reducedMotion: this.detectReducedMotion(),
        highContrast: this.detectHighContrast(),
        network: this.detectNetworkSpeed(),
      },
      sessionData: {
        pageViews: this.getPageViews(),
        timeOnSite: this.getTimeOnSite(),
        visitCount: this.getVisitCount(),
      },
    };

    this.contextCache = {
      value: ctx,
      expiresAt: now + this.contextCacheTTL,
      matchMediaRef: currentMatchMedia,
    };
    return ctx;
  }

  /**
   * Initialize default adaptation rules
   */
  private initializeDefaultRules(): void {
    // Default rules expected by tests (7 total)
    // 1) Dark mode
    this.registerRule({
      id: 'dark-mode',
      type: 'theme',
      priority: 100,
      enabled: true,
      condition: (context) =>
        !!context?.preferences?.theme ||
        context.environment?.colorScheme === 'dark',
      apply: (element: HTMLElement, context?: AdaptationContext) => {
        const theme =
          context?.preferences?.theme ||
          context?.environment?.colorScheme ||
          'light';
        element.setAttribute('data-theme', theme as string);
      },
    });

    // 2) Font size
    this.registerRule({
      id: 'font-size',
      type: 'accessibility',
      priority: 95,
      enabled: true,
      condition: (context) => !!context?.preferences?.fontSize,
      apply: (element: HTMLElement, context?: AdaptationContext) => {
        if (context?.preferences?.fontSize === 'large')
          element.style.fontSize = '1.25em';
        if (context?.preferences?.fontSize === 'small')
          element.style.fontSize = '0.875em';
      },
    });

    // 3) Reduced motion
    this.registerRule({
      id: 'reduced-motion',
      type: 'accessibility',
      priority: 94,
      enabled: true,
      condition: (context) => !!context?.environment?.reducedMotion,
      apply: (element: HTMLElement) => {
        element.style.transition = 'none';
        element.style.animation = 'none';
      },
    });

    // 4) High contrast
    this.registerRule({
      id: 'high-contrast',
      type: 'accessibility',
      priority: 93,
      enabled: true,
      condition: (context) => !!context?.environment?.highContrast,
      apply: (element: HTMLElement) => {
        element.classList.add('high-contrast');
      },
    });

    // 5) Mobile layout
    this.registerRule({
      id: 'mobile-layout',
      type: 'layout',
      priority: 90,
      enabled: true,
      condition: (context) => context?.environment?.device === 'mobile',
      apply: (element: HTMLElement) => {
        element.classList.add('mobile-optimized');
        element.style.padding = '0.5rem';
      },
    });

    // 6) Touch optimization
    this.registerRule({
      id: 'touch-optimization',
      type: 'features',
      priority: 89,
      enabled: true,
      condition: () =>
        (typeof navigator !== 'undefined' &&
          (navigator as unknown).maxTouchPoints > 0) ||
        false,
      apply: (element: HTMLElement) => {
        element.setAttribute('data-touch', 'true');
      },
    });

    // 7) Keyboard navigation
    this.registerRule({
      id: 'keyboard-navigation',
      type: 'navigation',
      priority: 88,
      enabled: true,
      condition: () => true,
      apply: (element: HTMLElement) => {
        element.setAttribute('data-keyboard-nav', 'enabled');
      },
    });
  }

  /**
   * Infer preferences from user context
   */
  private inferPreferences(context: UserContext): Partial<UserPreferences> {
    const inferred: Partial<UserPreferences> = {};

    // Infer theme preference from time of day
    if (!context.preferences.theme) {
      const hour = new Date().getHours();
      if (hour >= 20 || hour < 6) {
        inferred.theme = 'dark';
      } else {
        inferred.theme = 'light';
      }
    }

    // Infer density based on device and session data
    if (!context.preferences.density) {
      if (context.environment.device === 'mobile') {
        inferred.density = 'compact';
      } else if (context.sessionData.visitCount > 10) {
        // Experienced users might prefer compact
        inferred.density = 'compact';
      } else {
        inferred.density = 'comfortable';
      }
    }

    // Infer animations preference
    if (context.preferences.animations === undefined) {
      inferred.animations = !context.environment.reducedMotion;
    }

    return inferred;
  }

  /**
   * Capture element state for change detection
   */
  private captureElementState(element: HTMLElement): Record<string, unknown> {
    return {
      classes: Array.from(element.classList),
      attributes: Array.from(element.attributes).map((attr) => ({
        name: attr.name,
        value: attr.value,
      })),
      styles: element.getAttribute('style'),
    };
  }

  /**
   * Detect changes made to an element
   */
  private detectChanges(
    before: Record<string, unknown>,
    element: HTMLElement
  ): string[] {
    const changes: string[] = [];
    const after = this.captureElementState(element);

    // Compare classes
    const beforeClasses = new Set(before.classes as string[]);
    const afterClasses = new Set(after.classes as string[]);

    afterClasses.forEach((cls) => {
      if (!beforeClasses.has(cls)) {
        changes.push(`Added class: ${cls}`);
      }
    });

    // Compare attributes
    const beforeAttrs = new Map(
      (before.attributes as Array<{ name: string; value: string }>).map((a) => [
        a.name,
        a.value,
      ])
    );
    const afterAttrs = new Map(
      (after.attributes as Array<{ name: string; value: string }>).map((a) => [
        a.name,
        a.value,
      ])
    );

    afterAttrs.forEach((value, name) => {
      if (beforeAttrs.get(name) !== value) {
        changes.push(`Changed attribute ${name}: ${value}`);
      }
    });

    return changes;
  }

  /**
   * Detect device type
   */
  private detectDevice(): 'mobile' | 'tablet' | 'desktop' {
    if (typeof window === 'undefined') return 'desktop';

    const width = window.innerWidth;
    if (width < 768) return 'mobile';
    if (width < 1024) return 'tablet';
    return 'desktop';
  }

  /**
   * Get screen size
   */
  private getScreenSize(): { width: number; height: number } {
    if (typeof window === 'undefined') {
      return { width: 1920, height: 1080 };
    }

    return {
      width: window.innerWidth,
      height: window.innerHeight,
    };
  }

  /**
   * Detect color scheme preference
   */
  private detectColorScheme(): 'light' | 'dark' | 'auto' {
    if (typeof window === 'undefined') return 'light';

    // Some test environments or older browsers may not provide matchMedia.
    // Guard against that and return a sensible default.
    if (typeof (window as unknown).matchMedia !== 'function') return 'light';

    const prefersDark = window.matchMedia(
      '(prefers-color-scheme: dark)'
    ).matches;
    return prefersDark ? 'dark' : 'light';
  }

  /**
   * Detect reduced motion preference
   */
  private detectReducedMotion(): boolean {
    if (typeof window === 'undefined') return false;
    if (typeof (window as unknown).matchMedia !== 'function') return false;

    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  /**
   * Detect high contrast preference
   */
  private detectHighContrast(): boolean {
    if (typeof window === 'undefined') return false;
    if (typeof (window as unknown).matchMedia !== 'function') return false;

    return window.matchMedia('(prefers-contrast: high)').matches;
  }

  /**
   * Detect network speed
   */
  private detectNetworkSpeed(): 'slow' | 'fast' | 'offline' {
    if (typeof navigator === 'undefined') return 'fast';

    if (!navigator.onLine) return 'offline';

    const connection =
      (navigator as unknown).connection ||
      (navigator as unknown).mozConnection ||
      (navigator as unknown).webkitConnection;

    if (!connection) return 'fast';

    const effectiveType = connection.effectiveType;
    if (effectiveType === 'slow-2g' || effectiveType === '2g') {
      return 'slow';
    }

    return 'fast';
  }

  /**
   * Get page views count
   */
  private getPageViews(): number {
    if (typeof sessionStorage === 'undefined') return 0;

    const count = sessionStorage.getItem('yappc_page_views');
    return count ? parseInt(count, 10) : 0;
  }

  /**
   * Get time on site
   */
  private getTimeOnSite(): number {
    if (typeof sessionStorage === 'undefined') return 0;

    const startTime = sessionStorage.getItem('yappc_session_start');
    if (!startTime) return 0;

    return Date.now() - parseInt(startTime, 10);
  }

  /**
   * Get visit count
   */
  private getVisitCount(): number {
    if (typeof localStorage === 'undefined') return 0;

    const count = localStorage.getItem('yappc_visit_count');
    return count ? parseInt(count, 10) : 0;
  }
}
