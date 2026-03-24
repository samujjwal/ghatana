/**
 * User Enablement Management
 *
 * Provides comprehensive user onboarding, help center, tutorial system,
 * and documentation generation capabilities for canvas applications.
 *
 * Features:
 * - Onboarding flow management with step tracking
 * - Interactive tutorial system with progress tracking
 * - Help center with search and categories
 * - Documentation generation from code and metadata
 * - User progress analytics
 * - Contextual help and tooltips
 *
 * @module operations/userEnablement
 */

// ============================================================================
// Types and Interfaces
// ============================================================================

/**
 * User onboarding flow configuration
 */
export interface OnboardingFlow {
  id: string;
  name: string;
  description: string;
  targetAudience: 'beginner' | 'intermediate' | 'advanced' | 'all';
  estimatedDuration: number; // minutes
  steps: OnboardingStep[];
  prerequisites: string[]; // Flow IDs
  createdAt: Date;
  updatedAt: Date;
  published: boolean;
  completionRate: number; // 0-100
  averageRating: number; // 0-5
}

/**
 * Individual step in onboarding flow
 */
export interface OnboardingStep {
  id: string;
  order: number;
  title: string;
  content: string; // Markdown
  type: 'text' | 'video' | 'interactive' | 'quiz' | 'task';
  mediaUrl?: string;
  duration: number; // seconds
  required: boolean;
  interactiveElements?: InteractiveElement[];
  quiz?: QuizQuestion[];
  taskRequirements?: TaskRequirement[];
}

/**
 * Interactive element in onboarding
 */
export interface InteractiveElement {
  id: string;
  type: 'button' | 'input' | 'canvas' | 'demo';
  label: string;
  action: string;
  target?: string;
  validation?: ValidationRule;
}

/**
 * Quiz question for knowledge validation
 */
export interface QuizQuestion {
  id: string;
  question: string;
  type: 'multiple-choice' | 'true-false' | 'fill-blank';
  options?: string[];
  correctAnswer: string | string[];
  explanation: string;
  points: number;
}

/**
 * Task requirement for hands-on learning
 */
export interface TaskRequirement {
  id: string;
  description: string;
  verificationCriteria: string[];
  hints: string[];
  completed: boolean;
}

/**
 * Validation rule for interactive elements
 */
export interface ValidationRule {
  type: 'required' | 'pattern' | 'custom';
  pattern?: string;
  message: string;
  validator?: (value: unknown) => boolean;
}

/**
 * User progress in onboarding flow
 */
export interface OnboardingProgress {
  id: string;
  userId: string;
  flowId: string;
  currentStepId: string;
  completedSteps: string[];
  startedAt: Date;
  lastActivityAt: Date;
  completedAt?: Date;
  score?: number;
  feedback?: string;
  rating?: number;
}

/**
 * Tutorial configuration
 */
export interface Tutorial {
  id: string;
  title: string;
  description: string;
  category: string;
  difficulty: 'beginner' | 'intermediate' | 'advanced';
  tags: string[];
  duration: number; // minutes
  lessons: Lesson[];
  prerequisites: string[]; // Tutorial IDs
  createdAt: Date;
  updatedAt: Date;
  published: boolean;
  author: string;
  views: number;
  completions: number;
}

/**
 * Lesson in tutorial
 */
export interface Lesson {
  id: string;
  order: number;
  title: string;
  content: string; // Markdown
  videoUrl?: string;
  codeExamples: CodeExample[];
  exercises: Exercise[];
  resources: Resource[];
  duration: number; // minutes
}

/**
 * Code example in lesson
 */
export interface CodeExample {
  id: string;
  language: string;
  code: string;
  description: string;
  runnable: boolean;
  expectedOutput?: string;
}

/**
 * Exercise for practice
 */
export interface Exercise {
  id: string;
  title: string;
  description: string;
  difficulty: 'easy' | 'medium' | 'hard';
  starterCode?: string;
  solution?: string;
  tests: ExerciseTest[];
  hints: string[];
}

/**
 * Test for exercise validation
 */
export interface ExerciseTest {
  id: string;
  description: string;
  input: unknown;
  expectedOutput: unknown;
}

/**
 * Learning resource
 */
export interface Resource {
  id: string;
  type: 'documentation' | 'article' | 'video' | 'tool' | 'api';
  title: string;
  url: string;
  description: string;
}

/**
 * User tutorial progress
 */
export interface TutorialProgress {
  id: string;
  userId: string;
  tutorialId: string;
  currentLessonId: string;
  completedLessons: string[];
  exerciseScores: Map<string, number>;
  startedAt: Date;
  lastActivityAt: Date;
  completedAt?: Date;
  totalScore: number;
}

/**
 * Help article
 */
export interface HelpArticle {
  id: string;
  title: string;
  content: string; // Markdown
  category: string;
  tags: string[];
  relatedArticles: string[]; // Article IDs
  author: string;
  createdAt: Date;
  updatedAt: Date;
  published: boolean;
  views: number;
  helpful: number;
  notHelpful: number;
  searchTerms: string[]; // For search optimization
}

/**
 * Help category
 */
export interface HelpCategory {
  id: string;
  name: string;
  description: string;
  icon: string;
  parentId?: string;
  order: number;
  articleCount: number;
}

/**
 * Search result
 */
export interface SearchResult {
  article: HelpArticle;
  score: number; // Relevance score 0-1
  matchedTerms: string[];
  excerpt: string; // Highlighted excerpt
}

/**
 * User feedback on help content
 */
export interface HelpFeedback {
  id: string;
  userId: string;
  articleId: string;
  helpful: boolean;
  comment?: string;
  timestamp: Date;
}

/**
 * Documentation configuration
 */
export interface Documentation {
  id: string;
  title: string;
  version: string;
  description: string;
  sections: DocSection[];
  generatedAt: Date;
  format: 'markdown' | 'html' | 'pdf';
  metadata: DocMetadata;
}

/**
 * Documentation section
 */
export interface DocSection {
  id: string;
  title: string;
  order: number;
  content: string;
  subsections: DocSection[];
  codeExamples: CodeExample[];
  apiReferences: APIReference[];
}

/**
 * API reference documentation
 */
export interface APIReference {
  id: string;
  name: string;
  type: 'function' | 'class' | 'interface' | 'type' | 'constant';
  signature: string;
  description: string;
  parameters: Parameter[];
  returnType?: string;
  examples: CodeExample[];
  deprecated?: boolean;
  since?: string;
}

/**
 * Parameter documentation
 */
export interface Parameter {
  name: string;
  type: string;
  description: string;
  optional: boolean;
  defaultValue?: string;
}

/**
 * Documentation metadata
 */
export interface DocMetadata {
  author: string;
  contributors: string[];
  license: string;
  repository: string;
  tags: string[];
  keywords: string[];
}

/**
 * Tooltip configuration
 */
export interface Tooltip {
  id: string;
  target: string; // Element selector or ID
  content: string;
  position: 'top' | 'bottom' | 'left' | 'right';
  trigger: 'hover' | 'click' | 'focus';
  delay: number; // milliseconds
  contextual: boolean; // Show based on user context
  dismissible: boolean;
  priority: number; // Higher priority shows first
}

/**
 * User enablement analytics
 */
export interface EnablementAnalytics {
  totalUsers: number;
  activeUsers: number;
  onboardingCompletionRate: number;
  averageOnboardingTime: number;
  tutorialCompletionRate: number;
  helpArticleViews: number;
  topSearchTerms: string[];
  mostViewedArticles: HelpArticle[];
  userSatisfaction: number; // 0-5
}

/**
 * User enablement configuration
 */
export interface UserEnablementConfig {
  retentionDays: number; // How long to keep progress data
  searchIndexSize: number; // Max articles to index for search
  tooltipDelay: number; // Default tooltip delay in ms
}

// ============================================================================
// User Enablement Manager
// ============================================================================

/**
 * Manages user enablement features including onboarding, tutorials,
 * help center, and documentation.
 */
export class UserEnablementManager {
  private onboardingFlows: Map<string, OnboardingFlow> = new Map();
  private onboardingProgress: Map<string, OnboardingProgress[]> = new Map();
  private tutorials: Map<string, Tutorial> = new Map();
  private tutorialProgress: Map<string, TutorialProgress[]> = new Map();
  private helpArticles: Map<string, HelpArticle> = new Map();
  private helpCategories: Map<string, HelpCategory> = new Map();
  private helpFeedback: Map<string, HelpFeedback[]> = new Map();
  private documentation: Map<string, Documentation> = new Map();
  private tooltips: Map<string, Tooltip> = new Map();
  private config: UserEnablementConfig;

  // Counters for unique IDs
  private flowCounter = 0;
  private progressCounter = 0;
  private tutorialCounter = 0;
  private tutorialProgressCounter = 0;
  private articleCounter = 0;
  private categoryCounter = 0;
  private feedbackCounter = 0;
  private docCounter = 0;
  private tooltipCounter = 0;

  /**
   *
   */
  constructor(config?: Partial<UserEnablementConfig>) {
    this.config = {
      retentionDays: config?.retentionDays ?? 365,
      searchIndexSize: config?.searchIndexSize ?? 1000,
      tooltipDelay: config?.tooltipDelay ?? 500,
    };
  }

  // ========================================================================
  // Onboarding Flow Management
  // ========================================================================

  /**
   * Create a new onboarding flow
   */
  createOnboardingFlow(
    input: Omit<OnboardingFlow, 'id' | 'createdAt' | 'updatedAt' | 'completionRate' | 'averageRating'>
  ): OnboardingFlow {
    const flow: OnboardingFlow = {
      ...input,
      id: `flow-${Date.now()}-${this.flowCounter++}`,
      createdAt: new Date(),
      updatedAt: new Date(),
      completionRate: 0,
      averageRating: 0,
    };

    this.onboardingFlows.set(flow.id, flow);
    return flow;
  }

  /**
   * Get onboarding flow by ID
   */
  getOnboardingFlow(flowId: string): OnboardingFlow | undefined {
    return this.onboardingFlows.get(flowId);
  }

  /**
   * Get all onboarding flows
   */
  getAllOnboardingFlows(): OnboardingFlow[] {
    return Array.from(this.onboardingFlows.values());
  }

  /**
   * Get published onboarding flows
   */
  getPublishedFlows(): OnboardingFlow[] {
    return Array.from(this.onboardingFlows.values()).filter((f) => f.published);
  }

  /**
   * Get flows by target audience
   */
  getFlowsByAudience(audience: OnboardingFlow['targetAudience']): OnboardingFlow[] {
    return Array.from(this.onboardingFlows.values()).filter(
      (f) => f.targetAudience === audience || f.targetAudience === 'all'
    );
  }

  /**
   * Update onboarding flow
   */
  updateOnboardingFlow(
    flowId: string,
    updates: Partial<Omit<OnboardingFlow, 'id' | 'createdAt'>>
  ): OnboardingFlow | undefined {
    const flow = this.onboardingFlows.get(flowId);
    if (!flow) return undefined;

    const updated = {
      ...flow,
      ...updates,
      updatedAt: new Date(),
    };

    this.onboardingFlows.set(flowId, updated);
    return updated;
  }

  /**
   * Start onboarding for user
   */
  startOnboarding(userId: string, flowId: string): OnboardingProgress {
    const flow = this.onboardingFlows.get(flowId);
    if (!flow) {
      throw new Error(`Flow ${flowId} not found`);
    }

    const progress: OnboardingProgress = {
      id: `progress-${Date.now()}-${this.progressCounter++}`,
      userId,
      flowId,
      currentStepId: flow.steps[0]?.id ?? '',
      completedSteps: [],
      startedAt: new Date(),
      lastActivityAt: new Date(),
    };

    const userProgress = this.onboardingProgress.get(userId) ?? [];
    userProgress.push(progress);
    this.onboardingProgress.set(userId, userProgress);

    return progress;
  }

  /**
   * Complete onboarding step
   */
  completeStep(progressId: string, stepId: string, score?: number): OnboardingProgress | undefined {
    for (const [userId, progressList] of this.onboardingProgress.entries()) {
      const progress = progressList.find((p) => p.id === progressId);
      if (progress) {
        if (!progress.completedSteps.includes(stepId)) {
          progress.completedSteps.push(stepId);
        }
        progress.lastActivityAt = new Date();

        const flow = this.onboardingFlows.get(progress.flowId);
        if (flow) {
          const currentIndex = flow.steps.findIndex((s) => s.id === stepId);
          const nextStep = flow.steps[currentIndex + 1];
          if (nextStep) {
            progress.currentStepId = nextStep.id;
          } else {
            // All steps completed
            progress.completedAt = new Date();
            progress.score = score;
          }
        }

        return progress;
      }
    }
    return undefined;
  }

  /**
   * Get user's onboarding progress
   */
  getUserOnboardingProgress(userId: string): OnboardingProgress[] {
    return this.onboardingProgress.get(userId) ?? [];
  }

  /**
   * Rate onboarding flow
   */
  rateOnboarding(progressId: string, rating: number, feedback?: string): void {
    for (const progressList of this.onboardingProgress.values()) {
      const progress = progressList.find((p) => p.id === progressId);
      if (progress) {
        progress.rating = Math.max(0, Math.min(5, rating));
        progress.feedback = feedback;

        // Update flow average rating
        const flow = this.onboardingFlows.get(progress.flowId);
        if (flow) {
          const allProgress = Array.from(this.onboardingProgress.values())
            .flat()
            .filter((p) => p.flowId === flow.id && p.rating !== undefined);

          if (allProgress.length > 0) {
            const sum = allProgress.reduce((acc, p) => acc + (p.rating ?? 0), 0);
            flow.averageRating = sum / allProgress.length;
          }
        }
        return;
      }
    }
  }

  // ========================================================================
  // Tutorial Management
  // ========================================================================

  /**
   * Create a new tutorial
   */
  createTutorial(input: Omit<Tutorial, 'id' | 'createdAt' | 'updatedAt' | 'views' | 'completions'>): Tutorial {
    const tutorial: Tutorial = {
      ...input,
      id: `tutorial-${Date.now()}-${this.tutorialCounter++}`,
      createdAt: new Date(),
      updatedAt: new Date(),
      views: 0,
      completions: 0,
    };

    this.tutorials.set(tutorial.id, tutorial);
    return tutorial;
  }

  /**
   * Get tutorial by ID
   */
  getTutorial(tutorialId: string): Tutorial | undefined {
    const tutorial = this.tutorials.get(tutorialId);
    if (tutorial) {
      tutorial.views++;
    }
    return tutorial;
  }

  /**
   * Get all tutorials
   */
  getAllTutorials(): Tutorial[] {
    return Array.from(this.tutorials.values());
  }

  /**
   * Get tutorials by category
   */
  getTutorialsByCategory(category: string): Tutorial[] {
    return Array.from(this.tutorials.values()).filter((t) => t.category === category);
  }

  /**
   * Get tutorials by difficulty
   */
  getTutorialsByDifficulty(difficulty: Tutorial['difficulty']): Tutorial[] {
    return Array.from(this.tutorials.values()).filter((t) => t.difficulty === difficulty);
  }

  /**
   * Search tutorials by tags
   */
  searchTutorialsByTags(tags: string[]): Tutorial[] {
    return Array.from(this.tutorials.values()).filter((t) => tags.some((tag) => t.tags.includes(tag)));
  }

  /**
   * Start tutorial for user
   */
  startTutorial(userId: string, tutorialId: string): TutorialProgress {
    const tutorial = this.tutorials.get(tutorialId);
    if (!tutorial) {
      throw new Error(`Tutorial ${tutorialId} not found`);
    }

    const progress: TutorialProgress = {
      id: `tutorial-progress-${Date.now()}-${this.tutorialProgressCounter++}`,
      userId,
      tutorialId,
      currentLessonId: tutorial.lessons[0]?.id ?? '',
      completedLessons: [],
      exerciseScores: new Map(),
      startedAt: new Date(),
      lastActivityAt: new Date(),
      totalScore: 0,
    };

    const userProgress = this.tutorialProgress.get(userId) ?? [];
    userProgress.push(progress);
    this.tutorialProgress.set(userId, userProgress);

    return progress;
  }

  /**
   * Complete tutorial lesson
   */
  completeLesson(progressId: string, lessonId: string): TutorialProgress | undefined {
    for (const progressList of this.tutorialProgress.values()) {
      const progress = progressList.find((p) => p.id === progressId);
      if (progress) {
        if (!progress.completedLessons.includes(lessonId)) {
          progress.completedLessons.push(lessonId);
        }
        progress.lastActivityAt = new Date();

        const tutorial = this.tutorials.get(progress.tutorialId);
        if (tutorial) {
          const currentIndex = tutorial.lessons.findIndex((l) => l.id === lessonId);
          const nextLesson = tutorial.lessons[currentIndex + 1];
          if (nextLesson) {
            progress.currentLessonId = nextLesson.id;
          } else {
            // All lessons completed
            progress.completedAt = new Date();
            tutorial.completions++;
          }
        }

        return progress;
      }
    }
    return undefined;
  }

  /**
   * Submit exercise solution
   */
  submitExercise(progressId: string, exerciseId: string, score: number): void {
    for (const progressList of this.tutorialProgress.values()) {
      const progress = progressList.find((p) => p.id === progressId);
      if (progress) {
        progress.exerciseScores.set(exerciseId, score);
        progress.totalScore = Array.from(progress.exerciseScores.values()).reduce((sum, s) => sum + s, 0);
        progress.lastActivityAt = new Date();
        return;
      }
    }
  }

  /**
   * Get user's tutorial progress
   */
  getUserTutorialProgress(userId: string): TutorialProgress[] {
    return this.tutorialProgress.get(userId) ?? [];
  }

  // ========================================================================
  // Help Center Management
  // ========================================================================

  /**
   * Create help category
   */
  createHelpCategory(
    input: Omit<HelpCategory, 'id' | 'articleCount'>
  ): HelpCategory {
    const category: HelpCategory = {
      ...input,
      id: `category-${Date.now()}-${this.categoryCounter++}`,
      articleCount: 0,
    };

    this.helpCategories.set(category.id, category);
    return category;
  }

  /**
   * Get help category
   */
  getHelpCategory(categoryId: string): HelpCategory | undefined {
    return this.helpCategories.get(categoryId);
  }

  /**
   * Get all help categories
   */
  getAllHelpCategories(): HelpCategory[] {
    return Array.from(this.helpCategories.values()).sort((a, b) => a.order - b.order);
  }

  /**
   * Create help article
   */
  createHelpArticle(
    input: Omit<HelpArticle, 'id' | 'createdAt' | 'updatedAt' | 'views' | 'helpful' | 'notHelpful'>
  ): HelpArticle {
    const article: HelpArticle = {
      ...input,
      id: `article-${Date.now()}-${this.articleCounter++}`,
      createdAt: new Date(),
      updatedAt: new Date(),
      views: 0,
      helpful: 0,
      notHelpful: 0,
    };

    this.helpArticles.set(article.id, article);

    // Update category article count
    const category = this.helpCategories.get(article.category);
    if (category) {
      category.articleCount++;
    }

    return article;
  }

  /**
   * Get help article
   */
  getHelpArticle(articleId: string): HelpArticle | undefined {
    const article = this.helpArticles.get(articleId);
    if (article) {
      article.views++;
    }
    return article;
  }

  /**
   * Get articles by category
   */
  getArticlesByCategory(categoryId: string): HelpArticle[] {
    return Array.from(this.helpArticles.values()).filter((a) => a.category === categoryId && a.published);
  }

  /**
   * Search help articles
   */
  searchHelpArticles(query: string): SearchResult[] {
    const lowerQuery = query.toLowerCase();
    const queryTerms = lowerQuery.split(/\s+/).filter((t) => t.length > 2);

    const results: SearchResult[] = [];

    for (const article of this.helpArticles.values()) {
      if (!article.published) continue;

      let score = 0;
      const matchedTerms: string[] = [];

      // Title match (highest weight)
      const titleMatch = queryTerms.filter((term) => article.title.toLowerCase().includes(term));
      score += titleMatch.length * 10;
      matchedTerms.push(...titleMatch);

      // Search terms match
      const searchMatch = queryTerms.filter((term) =>
        article.searchTerms.some((st) => st.toLowerCase().includes(term))
      );
      score += searchMatch.length * 5;
      matchedTerms.push(...searchMatch);

      // Content match
      const contentMatch = queryTerms.filter((term) => article.content.toLowerCase().includes(term));
      score += contentMatch.length * 2;
      matchedTerms.push(...contentMatch);

      // Tags match
      const tagMatch = queryTerms.filter((term) => article.tags.some((tag) => tag.toLowerCase().includes(term)));
      score += tagMatch.length * 3;
      matchedTerms.push(...tagMatch);

      if (score > 0) {
        // Generate excerpt
        const firstMatch = queryTerms.find((term) => article.content.toLowerCase().includes(term));
        let excerpt = '';
        if (firstMatch) {
          const index = article.content.toLowerCase().indexOf(firstMatch);
          const start = Math.max(0, index - 50);
          const end = Math.min(article.content.length, index + 100);
          excerpt = `...${  article.content.slice(start, end)  }...`;
        } else {
          excerpt = `${article.content.slice(0, 150)  }...`;
        }

        results.push({
          article,
          score: score / Math.max(queryTerms.length, 1),
          matchedTerms: Array.from(new Set(matchedTerms)),
          excerpt,
        });
      }
    }

    return results.sort((a, b) => b.score - a.score).slice(0, this.config.searchIndexSize);
  }

  /**
   * Submit help feedback
   */
  submitHelpFeedback(userId: string, articleId: string, helpful: boolean, comment?: string): HelpFeedback {
    const feedback: HelpFeedback = {
      id: `feedback-${Date.now()}-${this.feedbackCounter++}`,
      userId,
      articleId,
      helpful,
      comment,
      timestamp: new Date(),
    };

    const articleFeedback = this.helpFeedback.get(articleId) ?? [];
    articleFeedback.push(feedback);
    this.helpFeedback.set(articleId, articleFeedback);

    // Update article helpful counts
    const article = this.helpArticles.get(articleId);
    if (article) {
      if (helpful) {
        article.helpful++;
      } else {
        article.notHelpful++;
      }
    }

    return feedback;
  }

  /**
   * Get article feedback
   */
  getArticleFeedback(articleId: string): HelpFeedback[] {
    return this.helpFeedback.get(articleId) ?? [];
  }

  // ========================================================================
  // Documentation Generation
  // ========================================================================

  /**
   * Generate documentation
   */
  generateDocumentation(
    input: Omit<Documentation, 'id' | 'generatedAt'>
  ): Documentation {
    const doc: Documentation = {
      ...input,
      id: `doc-${Date.now()}-${this.docCounter++}`,
      generatedAt: new Date(),
    };

    this.documentation.set(doc.id, doc);
    return doc;
  }

  /**
   * Get documentation
   */
  getDocumentation(docId: string): Documentation | undefined {
    return this.documentation.get(docId);
  }

  /**
   * Get all documentation versions
   */
  getAllDocumentation(): Documentation[] {
    return Array.from(this.documentation.values()).sort(
      (a, b) => b.generatedAt.getTime() - a.generatedAt.getTime()
    );
  }

  /**
   * Export documentation as markdown
   */
  exportDocumentationMarkdown(docId: string): string {
    const doc = this.documentation.get(docId);
    if (!doc) return '';

    let markdown = `# ${doc.title}\n\n`;
    markdown += `**Version**: ${doc.version}\n`;
    markdown += `**Generated**: ${doc.generatedAt.toISOString()}\n\n`;
    markdown += `${doc.description}\n\n`;

    if (doc.metadata.author) {
      markdown += `**Author**: ${doc.metadata.author}\n`;
    }
    if (doc.metadata.contributors.length > 0) {
      markdown += `**Contributors**: ${doc.metadata.contributors.join(', ')}\n`;
    }
    markdown += `\n---\n\n`;

    const exportSection = (section: DocSection, level: number): string => {
      let output = '';
      const heading = '#'.repeat(level);
      output += `${heading} ${section.title}\n\n`;
      output += `${section.content}\n\n`;

      // Add code examples
      if (section.codeExamples.length > 0) {
        output += '### Code Examples\n\n';
        for (const example of section.codeExamples) {
          output += `**${example.description}**\n\n`;
          output += `\`\`\`${example.language}\n${example.code}\n\`\`\`\n\n`;
        }
      }

      // Add API references
      if (section.apiReferences.length > 0) {
        output += '### API Reference\n\n';
        for (const api of section.apiReferences) {
          output += `#### \`${api.name}\`\n\n`;
          output += `${api.description}\n\n`;
          output += `**Signature**: \`${api.signature}\`\n\n`;
          if (api.parameters.length > 0) {
            output += '**Parameters**:\n\n';
            for (const param of api.parameters) {
              const optional = param.optional ? ' (optional)' : '';
              const defaultVal = param.defaultValue ? ` = ${param.defaultValue}` : '';
              output += `- \`${param.name}\`: \`${param.type}\`${optional}${defaultVal} - ${param.description}\n`;
            }
            output += '\n';
          }
        }
      }

      // Recursive subsections
      for (const subsection of section.subsections) {
        output += exportSection(subsection, level + 1);
      }

      return output;
    };

    for (const section of doc.sections) {
      markdown += exportSection(section, 2);
    }

    return markdown;
  }

  // ========================================================================
  // Tooltip Management
  // ========================================================================

  /**
   * Create tooltip
   */
  createTooltip(input: Omit<Tooltip, 'id'>): Tooltip {
    const tooltip: Tooltip = {
      ...input,
      id: `tooltip-${Date.now()}-${this.tooltipCounter++}`,
    };

    this.tooltips.set(tooltip.id, tooltip);
    return tooltip;
  }

  /**
   * Get tooltips for target
   */
  getTooltipsForTarget(target: string): Tooltip[] {
    return Array.from(this.tooltips.values())
      .filter((t) => t.target === target)
      .sort((a, b) => b.priority - a.priority);
  }

  /**
   * Get all tooltips
   */
  getAllTooltips(): Tooltip[] {
    return Array.from(this.tooltips.values());
  }

  /**
   * Remove tooltip
   */
  removeTooltip(tooltipId: string): boolean {
    return this.tooltips.delete(tooltipId);
  }

  // ========================================================================
  // Analytics
  // ========================================================================

  /**
   * Get enablement analytics
   */
  getAnalytics(): EnablementAnalytics {
    const allUsers = new Set<string>();
    for (const userId of this.onboardingProgress.keys()) {
      allUsers.add(userId);
    }
    for (const userId of this.tutorialProgress.keys()) {
      allUsers.add(userId);
    }

    const now = new Date();
    const thirtyDaysAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);

    const activeUsers = new Set<string>();
    for (const [userId, progressList] of this.onboardingProgress.entries()) {
      if (progressList.some((p) => p.lastActivityAt > thirtyDaysAgo)) {
        activeUsers.add(userId);
      }
    }
    for (const [userId, progressList] of this.tutorialProgress.entries()) {
      if (progressList.some((p) => p.lastActivityAt > thirtyDaysAgo)) {
        activeUsers.add(userId);
      }
    }

    // Calculate onboarding completion rate
    let totalOnboarding = 0;
    let completedOnboarding = 0;
    let totalOnboardingTime = 0;

    for (const progressList of this.onboardingProgress.values()) {
      for (const progress of progressList) {
        totalOnboarding++;
        if (progress.completedAt) {
          completedOnboarding++;
          totalOnboardingTime += progress.completedAt.getTime() - progress.startedAt.getTime();
        }
      }
    }

    // Calculate tutorial completion rate
    let totalTutorials = 0;
    let completedTutorials = 0;

    for (const progressList of this.tutorialProgress.values()) {
      for (const progress of progressList) {
        totalTutorials++;
        if (progress.completedAt) {
          completedTutorials++;
        }
      }
    }

    // Get most viewed articles
    const mostViewedArticles = Array.from(this.helpArticles.values())
      .sort((a, b) => b.views - a.views)
      .slice(0, 10);

    // Calculate user satisfaction
    let totalRatings = 0;
    let ratingSum = 0;
    for (const progressList of this.onboardingProgress.values()) {
      for (const progress of progressList) {
        if (progress.rating !== undefined) {
          totalRatings++;
          ratingSum += progress.rating;
        }
      }
    }

    return {
      totalUsers: allUsers.size,
      activeUsers: activeUsers.size,
      onboardingCompletionRate: totalOnboarding > 0 ? completedOnboarding / totalOnboarding : 0,
      averageOnboardingTime: completedOnboarding > 0 ? totalOnboardingTime / completedOnboarding / (1000 * 60) : 0, // minutes
      tutorialCompletionRate: totalTutorials > 0 ? completedTutorials / totalTutorials : 0,
      helpArticleViews: Array.from(this.helpArticles.values()).reduce((sum, a) => sum + a.views, 0),
      topSearchTerms: [], // Would need to track search queries
      mostViewedArticles,
      userSatisfaction: totalRatings > 0 ? ratingSum / totalRatings : 0,
    };
  }

  // ========================================================================
  // Cleanup and Utilities
  // ========================================================================

  /**
   * Clean up old progress data
   */
  cleanupOldProgress(): { onboardingRemoved: number; tutorialRemoved: number } {
    const cutoffDate = new Date(Date.now() - this.config.retentionDays * 24 * 60 * 60 * 1000);

    let onboardingRemoved = 0;
    for (const [userId, progressList] of this.onboardingProgress.entries()) {
      const filtered = progressList.filter((p) => {
        const shouldRemove = p.completedAt && p.completedAt < cutoffDate;
        if (shouldRemove) onboardingRemoved++;
        return !shouldRemove;
      });
      if (filtered.length === 0) {
        this.onboardingProgress.delete(userId);
      } else {
        this.onboardingProgress.set(userId, filtered);
      }
    }

    let tutorialRemoved = 0;
    for (const [userId, progressList] of this.tutorialProgress.entries()) {
      const filtered = progressList.filter((p) => {
        const shouldRemove = p.completedAt && p.completedAt < cutoffDate;
        if (shouldRemove) tutorialRemoved++;
        return !shouldRemove;
      });
      if (filtered.length === 0) {
        this.tutorialProgress.delete(userId);
      } else {
        this.tutorialProgress.set(userId, filtered);
      }
    }

    return { onboardingRemoved, tutorialRemoved };
  }

  /**
   * Reset manager (for testing)
   */
  reset(): void {
    this.onboardingFlows.clear();
    this.onboardingProgress.clear();
    this.tutorials.clear();
    this.tutorialProgress.clear();
    this.helpArticles.clear();
    this.helpCategories.clear();
    this.helpFeedback.clear();
    this.documentation.clear();
    this.tooltips.clear();
    this.flowCounter = 0;
    this.progressCounter = 0;
    this.tutorialCounter = 0;
    this.tutorialProgressCounter = 0;
    this.articleCounter = 0;
    this.categoryCounter = 0;
    this.feedbackCounter = 0;
    this.docCounter = 0;
    this.tooltipCounter = 0;
  }
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Calculate onboarding completion percentage
 */
export function calculateOnboardingCompletion(progress: OnboardingProgress, flow: OnboardingFlow): number {
  if (flow.steps.length === 0) return 0;
  return (progress.completedSteps.length / flow.steps.length) * 100;
}

/**
 * Calculate tutorial completion percentage
 */
export function calculateTutorialCompletion(progress: TutorialProgress, tutorial: Tutorial): number {
  if (tutorial.lessons.length === 0) return 0;
  return (progress.completedLessons.length / tutorial.lessons.length) * 100;
}

/**
 * Calculate estimated time remaining for onboarding
 */
export function calculateEstimatedTimeRemaining(progress: OnboardingProgress, flow: OnboardingFlow): number {
  const completedStepIds = new Set(progress.completedSteps);
  const remainingSteps = flow.steps.filter((s) => !completedStepIds.has(s.id));
  return remainingSteps.reduce((sum, step) => sum + step.duration, 0);
}

/**
 * Format duration in human-readable format
 */
export function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  return remainingMinutes > 0 ? `${hours}h ${remainingMinutes}m` : `${hours}h`;
}

/**
 * Validate quiz answer
 */
export function validateQuizAnswer(question: QuizQuestion, userAnswer: string | string[]): boolean {
  if (Array.isArray(question.correctAnswer)) {
    if (!Array.isArray(userAnswer)) return false;
    const correct = new Set(question.correctAnswer);
    const user = new Set(userAnswer);
    return correct.size === user.size && Array.from(correct).every((a) => user.has(a));
  }
  return question.correctAnswer === userAnswer;
}

/**
 * Generate search terms from content
 */
export function generateSearchTerms(content: string, title: string): string[] {
  const terms = new Set<string>();

  // Add title words
  title
    .toLowerCase()
    .split(/\s+/)
    .filter((w) => w.length > 2)
    .forEach((w) => terms.add(w));

  // Add content words (filtered)
  const stopWords = new Set(['the', 'and', 'or', 'but', 'in', 'on', 'at', 'to', 'for', 'of', 'with', 'by']);
  content
    .toLowerCase()
    .replace(/[^\w\s]/g, ' ')
    .split(/\s+/)
    .filter((w) => w.length > 3 && !stopWords.has(w))
    .slice(0, 50) // Limit to 50 terms
    .forEach((w) => terms.add(w));

  return Array.from(terms);
}

/**
 * Calculate help article relevance score
 */
export function calculateRelevanceScore(article: HelpArticle, query: string): number {
  const lowerQuery = query.toLowerCase();
  const queryTerms = lowerQuery.split(/\s+/).filter((t) => t.length > 2);

  let score = 0;

  // Title match (highest weight)
  queryTerms.forEach((term) => {
    if (article.title.toLowerCase().includes(term)) score += 10;
  });

  // Tag match
  queryTerms.forEach((term) => {
    if (article.tags.some((tag) => tag.toLowerCase().includes(term))) score += 5;
  });

  // Content match
  queryTerms.forEach((term) => {
    if (article.content.toLowerCase().includes(term)) score += 2;
  });

  // Boost recent articles
  const daysSinceUpdate = (Date.now() - article.updatedAt.getTime()) / (1000 * 60 * 60 * 24);
  if (daysSinceUpdate < 30) score *= 1.2;

  // Boost helpful articles
  const helpfulnessRatio =
    article.helpful + article.notHelpful > 0 ? article.helpful / (article.helpful + article.notHelpful) : 0.5;
  score *= 0.8 + helpfulnessRatio * 0.4;

  return score;
}
