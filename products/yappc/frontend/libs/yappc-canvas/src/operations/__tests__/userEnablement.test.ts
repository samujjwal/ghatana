/**
 * @vitest-environment jsdom
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  UserEnablementManager,
  type OnboardingFlow,
  type Tutorial,
  type HelpArticle,
  type HelpCategory,
  type Documentation,
  type Tooltip,
  calculateOnboardingCompletion,
  calculateTutorialCompletion,
  calculateEstimatedTimeRemaining,
  formatDuration,
  validateQuizAnswer,
  generateSearchTerms,
  calculateRelevanceScore,
} from '../userEnablement';

describe('UserEnablementManager', () => {
  let manager: UserEnablementManager;

  beforeEach(() => {
    manager = new UserEnablementManager();
  });

  // ==========================================================================
  // Initialization
  // ==========================================================================

  describe('Initialization', () => {
    it('should create manager with default configuration', () => {
      expect(manager).toBeDefined();
      expect(manager.getAllOnboardingFlows()).toHaveLength(0);
      expect(manager.getAllTutorials()).toHaveLength(0);
      expect(manager.getAllHelpCategories()).toHaveLength(0);
    });

    it('should accept custom configuration', () => {
      const customManager = new UserEnablementManager({
        retentionDays: 180,
        searchIndexSize: 500,
        tooltipDelay: 1000,
      });

      expect(customManager).toBeDefined();
    });
  });

  // ==========================================================================
  // Onboarding Flow Management
  // ==========================================================================

  describe('Onboarding Flows', () => {
    it('should create onboarding flow', () => {
      const flow = manager.createOnboardingFlow({
        name: 'Getting Started',
        description: 'Learn the basics',
        targetAudience: 'beginner',
        estimatedDuration: 15,
        steps: [
          {
            id: 'step1',
            order: 1,
            title: 'Welcome',
            content: 'Welcome to our platform',
            type: 'text',
            duration: 60,
            required: true,
          },
        ],
        prerequisites: [],
        published: true,
      });

      expect(flow.id).toBeDefined();
      expect(flow.name).toBe('Getting Started');
      expect(flow.completionRate).toBe(0);
      expect(flow.averageRating).toBe(0);
    });

    it('should get onboarding flow by ID', () => {
      const flow = manager.createOnboardingFlow({
        name: 'Test Flow',
        description: 'Test',
        targetAudience: 'all',
        estimatedDuration: 10,
        steps: [],
        prerequisites: [],
        published: false,
      });

      const retrieved = manager.getOnboardingFlow(flow.id);
      expect(retrieved).toEqual(flow);
    });

    it('should get all onboarding flows', () => {
      manager.createOnboardingFlow({
        name: 'Flow 1',
        description: 'Test',
        targetAudience: 'beginner',
        estimatedDuration: 10,
        steps: [],
        prerequisites: [],
        published: true,
      });

      manager.createOnboardingFlow({
        name: 'Flow 2',
        description: 'Test',
        targetAudience: 'advanced',
        estimatedDuration: 20,
        steps: [],
        prerequisites: [],
        published: true,
      });

      const flows = manager.getAllOnboardingFlows();
      expect(flows).toHaveLength(2);
    });

    it('should get published flows only', () => {
      manager.createOnboardingFlow({
        name: 'Published',
        description: 'Test',
        targetAudience: 'all',
        estimatedDuration: 10,
        steps: [],
        prerequisites: [],
        published: true,
      });

      manager.createOnboardingFlow({
        name: 'Draft',
        description: 'Test',
        targetAudience: 'all',
        estimatedDuration: 10,
        steps: [],
        prerequisites: [],
        published: false,
      });

      const published = manager.getPublishedFlows();
      expect(published).toHaveLength(1);
      expect(published[0]!.name).toBe('Published');
    });

    it('should get flows by audience', () => {
      manager.createOnboardingFlow({
        name: 'Beginner Flow',
        description: 'Test',
        targetAudience: 'beginner',
        estimatedDuration: 10,
        steps: [],
        prerequisites: [],
        published: true,
      });

      manager.createOnboardingFlow({
        name: 'Advanced Flow',
        description: 'Test',
        targetAudience: 'advanced',
        estimatedDuration: 20,
        steps: [],
        prerequisites: [],
        published: true,
      });

      manager.createOnboardingFlow({
        name: 'All Flow',
        description: 'Test',
        targetAudience: 'all',
        estimatedDuration: 15,
        steps: [],
        prerequisites: [],
        published: true,
      });

      const beginnerFlows = manager.getFlowsByAudience('beginner');
      expect(beginnerFlows).toHaveLength(2); // Beginner + All
    });

    it('should update onboarding flow', () => {
      const flow = manager.createOnboardingFlow({
        name: 'Original',
        description: 'Test',
        targetAudience: 'beginner',
        estimatedDuration: 10,
        steps: [],
        prerequisites: [],
        published: false,
      });

      const updated = manager.updateOnboardingFlow(flow.id, {
        name: 'Updated',
        published: true,
      });

      expect(updated?.name).toBe('Updated');
      expect(updated?.published).toBe(true);
    });

    it('should start onboarding for user', () => {
      const flow = manager.createOnboardingFlow({
        name: 'Test Flow',
        description: 'Test',
        targetAudience: 'all',
        estimatedDuration: 10,
        steps: [
          {
            id: 'step1',
            order: 1,
            title: 'Step 1',
            content: 'Content',
            type: 'text',
            duration: 60,
            required: true,
          },
        ],
        prerequisites: [],
        published: true,
      });

      const progress = manager.startOnboarding('user1', flow.id);

      expect(progress.id).toBeDefined();
      expect(progress.userId).toBe('user1');
      expect(progress.flowId).toBe(flow.id);
      expect(progress.currentStepId).toBe('step1');
      expect(progress.completedSteps).toHaveLength(0);
    });

    it('should complete onboarding step', () => {
      const flow = manager.createOnboardingFlow({
        name: 'Test Flow',
        description: 'Test',
        targetAudience: 'all',
        estimatedDuration: 10,
        steps: [
          {
            id: 'step1',
            order: 1,
            title: 'Step 1',
            content: 'Content',
            type: 'text',
            duration: 60,
            required: true,
          },
          {
            id: 'step2',
            order: 2,
            title: 'Step 2',
            content: 'Content',
            type: 'text',
            duration: 60,
            required: true,
          },
        ],
        prerequisites: [],
        published: true,
      });

      const progress = manager.startOnboarding('user1', flow.id);
      const updated = manager.completeStep(progress.id, 'step1');

      expect(updated?.completedSteps).toContain('step1');
      expect(updated?.currentStepId).toBe('step2');
    });

    it('should complete entire onboarding flow', () => {
      const flow = manager.createOnboardingFlow({
        name: 'Test Flow',
        description: 'Test',
        targetAudience: 'all',
        estimatedDuration: 10,
        steps: [
          {
            id: 'step1',
            order: 1,
            title: 'Step 1',
            content: 'Content',
            type: 'text',
            duration: 60,
            required: true,
          },
        ],
        prerequisites: [],
        published: true,
      });

      const progress = manager.startOnboarding('user1', flow.id);
      const completed = manager.completeStep(progress.id, 'step1', 95);

      expect(completed?.completedAt).toBeDefined();
      expect(completed?.score).toBe(95);
    });

    it('should get user onboarding progress', () => {
      const flow = manager.createOnboardingFlow({
        name: 'Test Flow',
        description: 'Test',
        targetAudience: 'all',
        estimatedDuration: 10,
        steps: [],
        prerequisites: [],
        published: true,
      });

      manager.startOnboarding('user1', flow.id);
      manager.startOnboarding('user1', flow.id); // Start same flow again

      const userProgress = manager.getUserOnboardingProgress('user1');
      expect(userProgress).toHaveLength(2);
    });

    it('should rate onboarding flow', () => {
      const flow = manager.createOnboardingFlow({
        name: 'Test Flow',
        description: 'Test',
        targetAudience: 'all',
        estimatedDuration: 10,
        steps: [],
        prerequisites: [],
        published: true,
      });

      const progress = manager.startOnboarding('user1', flow.id);
      manager.rateOnboarding(progress.id, 4.5, 'Great flow!');

      const userProgress = manager.getUserOnboardingProgress('user1');
      expect(userProgress[0]?.rating).toBe(4.5);
      expect(userProgress[0]?.feedback).toBe('Great flow!');

      // Check that flow average rating is updated
      const updatedFlow = manager.getOnboardingFlow(flow.id);
      expect(updatedFlow?.averageRating).toBe(4.5);
    });
  });

  // ==========================================================================
  // Tutorial Management
  // ==========================================================================

  describe('Tutorials', () => {
    it('should create tutorial', () => {
      const tutorial = manager.createTutorial({
        title: 'Canvas Basics',
        description: 'Learn canvas fundamentals',
        category: 'getting-started',
        difficulty: 'beginner',
        tags: ['canvas', 'basics'],
        duration: 30,
        lessons: [],
        prerequisites: [],
        published: true,
        author: 'instructor@example.com',
      });

      expect(tutorial.id).toBeDefined();
      expect(tutorial.title).toBe('Canvas Basics');
      expect(tutorial.views).toBe(0);
      expect(tutorial.completions).toBe(0);
    });

    it('should increment views when getting tutorial', () => {
      const tutorial = manager.createTutorial({
        title: 'Test Tutorial',
        description: 'Test',
        category: 'test',
        difficulty: 'beginner',
        tags: [],
        duration: 10,
        lessons: [],
        prerequisites: [],
        published: true,
        author: 'test@example.com',
      });

      manager.getTutorial(tutorial.id);
      manager.getTutorial(tutorial.id);

      const updated = manager.getTutorial(tutorial.id);
      expect(updated?.views).toBe(3);
    });

    it('should get tutorials by category', () => {
      manager.createTutorial({
        title: 'Tutorial 1',
        description: 'Test',
        category: 'category-a',
        difficulty: 'beginner',
        tags: [],
        duration: 10,
        lessons: [],
        prerequisites: [],
        published: true,
        author: 'test@example.com',
      });

      manager.createTutorial({
        title: 'Tutorial 2',
        description: 'Test',
        category: 'category-b',
        difficulty: 'beginner',
        tags: [],
        duration: 10,
        lessons: [],
        prerequisites: [],
        published: true,
        author: 'test@example.com',
      });

      const categoryATutorials = manager.getTutorialsByCategory('category-a');
      expect(categoryATutorials).toHaveLength(1);
      expect(categoryATutorials[0]!.title).toBe('Tutorial 1');
    });

    it('should get tutorials by difficulty', () => {
      manager.createTutorial({
        title: 'Easy Tutorial',
        description: 'Test',
        category: 'test',
        difficulty: 'beginner',
        tags: [],
        duration: 10,
        lessons: [],
        prerequisites: [],
        published: true,
        author: 'test@example.com',
      });

      manager.createTutorial({
        title: 'Hard Tutorial',
        description: 'Test',
        category: 'test',
        difficulty: 'advanced',
        tags: [],
        duration: 30,
        lessons: [],
        prerequisites: [],
        published: true,
        author: 'test@example.com',
      });

      const beginnerTutorials = manager.getTutorialsByDifficulty('beginner');
      expect(beginnerTutorials).toHaveLength(1);
      expect(beginnerTutorials[0]!.title).toBe('Easy Tutorial');
    });

    it('should search tutorials by tags', () => {
      manager.createTutorial({
        title: 'Canvas Tutorial',
        description: 'Test',
        category: 'test',
        difficulty: 'beginner',
        tags: ['canvas', 'drawing'],
        duration: 10,
        lessons: [],
        prerequisites: [],
        published: true,
        author: 'test@example.com',
      });

      manager.createTutorial({
        title: 'Animation Tutorial',
        description: 'Test',
        category: 'test',
        difficulty: 'beginner',
        tags: ['animation', 'effects'],
        duration: 10,
        lessons: [],
        prerequisites: [],
        published: true,
        author: 'test@example.com',
      });

      const canvasTutorials = manager.searchTutorialsByTags(['canvas']);
      expect(canvasTutorials).toHaveLength(1);
      expect(canvasTutorials[0]!.title).toBe('Canvas Tutorial');
    });

    it('should start tutorial for user', () => {
      const tutorial = manager.createTutorial({
        title: 'Test Tutorial',
        description: 'Test',
        category: 'test',
        difficulty: 'beginner',
        tags: [],
        duration: 10,
        lessons: [
          {
            id: 'lesson1',
            order: 1,
            title: 'Lesson 1',
            content: 'Content',
            codeExamples: [],
            exercises: [],
            resources: [],
            duration: 10,
          },
        ],
        prerequisites: [],
        published: true,
        author: 'test@example.com',
      });

      const progress = manager.startTutorial('user1', tutorial.id);

      expect(progress.id).toBeDefined();
      expect(progress.userId).toBe('user1');
      expect(progress.tutorialId).toBe(tutorial.id);
      expect(progress.currentLessonId).toBe('lesson1');
      expect(progress.completedLessons).toHaveLength(0);
    });

    it('should complete tutorial lesson', () => {
      const tutorial = manager.createTutorial({
        title: 'Test Tutorial',
        description: 'Test',
        category: 'test',
        difficulty: 'beginner',
        tags: [],
        duration: 20,
        lessons: [
          {
            id: 'lesson1',
            order: 1,
            title: 'Lesson 1',
            content: 'Content',
            codeExamples: [],
            exercises: [],
            resources: [],
            duration: 10,
          },
          {
            id: 'lesson2',
            order: 2,
            title: 'Lesson 2',
            content: 'Content',
            codeExamples: [],
            exercises: [],
            resources: [],
            duration: 10,
          },
        ],
        prerequisites: [],
        published: true,
        author: 'test@example.com',
      });

      const progress = manager.startTutorial('user1', tutorial.id);
      const updated = manager.completeLesson(progress.id, 'lesson1');

      expect(updated?.completedLessons).toContain('lesson1');
      expect(updated?.currentLessonId).toBe('lesson2');
    });

    it('should complete entire tutorial', () => {
      const tutorial = manager.createTutorial({
        title: 'Test Tutorial',
        description: 'Test',
        category: 'test',
        difficulty: 'beginner',
        tags: [],
        duration: 10,
        lessons: [
          {
            id: 'lesson1',
            order: 1,
            title: 'Lesson 1',
            content: 'Content',
            codeExamples: [],
            exercises: [],
            resources: [],
            duration: 10,
          },
        ],
        prerequisites: [],
        published: true,
        author: 'test@example.com',
      });

      const progress = manager.startTutorial('user1', tutorial.id);
      const completed = manager.completeLesson(progress.id, 'lesson1');

      expect(completed?.completedAt).toBeDefined();

      const updatedTutorial = manager.getTutorial(tutorial.id);
      expect(updatedTutorial?.completions).toBe(1);
    });

    it('should submit exercise solution', () => {
      const tutorial = manager.createTutorial({
        title: 'Test Tutorial',
        description: 'Test',
        category: 'test',
        difficulty: 'beginner',
        tags: [],
        duration: 10,
        lessons: [],
        prerequisites: [],
        published: true,
        author: 'test@example.com',
      });

      const progress = manager.startTutorial('user1', tutorial.id);
      manager.submitExercise(progress.id, 'exercise1', 85);
      manager.submitExercise(progress.id, 'exercise2', 90);

      const userProgress = manager.getUserTutorialProgress('user1');
      expect(userProgress[0]?.totalScore).toBe(175);
      expect(userProgress[0]?.exerciseScores.get('exercise1')).toBe(85);
    });

    it('should get user tutorial progress', () => {
      const tutorial = manager.createTutorial({
        title: 'Test Tutorial',
        description: 'Test',
        category: 'test',
        difficulty: 'beginner',
        tags: [],
        duration: 10,
        lessons: [],
        prerequisites: [],
        published: true,
        author: 'test@example.com',
      });

      manager.startTutorial('user1', tutorial.id);
      manager.startTutorial('user1', tutorial.id);

      const userProgress = manager.getUserTutorialProgress('user1');
      expect(userProgress).toHaveLength(2);
    });
  });

  // ==========================================================================
  // Help Center Management
  // ==========================================================================

  describe('Help Center', () => {
    it('should create help category', () => {
      const category = manager.createHelpCategory({
        name: 'Getting Started',
        description: 'Introductory articles',
        icon: 'book',
        order: 1,
      });

      expect(category.id).toBeDefined();
      expect(category.name).toBe('Getting Started');
      expect(category.articleCount).toBe(0);
    });

    it('should get help categories sorted by order', () => {
      manager.createHelpCategory({
        name: 'Category C',
        description: 'Test',
        icon: 'icon',
        order: 3,
      });

      manager.createHelpCategory({
        name: 'Category A',
        description: 'Test',
        icon: 'icon',
        order: 1,
      });

      manager.createHelpCategory({
        name: 'Category B',
        description: 'Test',
        icon: 'icon',
        order: 2,
      });

      const categories = manager.getAllHelpCategories();
      expect(categories[0]!.name).toBe('Category A');
      expect(categories[1]!.name).toBe('Category B');
      expect(categories[2]!.name).toBe('Category C');
    });

    it('should create help article', () => {
      const category = manager.createHelpCategory({
        name: 'Getting Started',
        description: 'Test',
        icon: 'icon',
        order: 1,
      });

      const article = manager.createHelpArticle({
        title: 'How to create a canvas',
        content: 'Follow these steps...',
        category: category.id,
        tags: ['canvas', 'basics'],
        relatedArticles: [],
        author: 'support@example.com',
        published: true,
        searchTerms: ['create', 'canvas', 'new'],
      });

      expect(article.id).toBeDefined();
      expect(article.title).toBe('How to create a canvas');
      expect(article.views).toBe(0);
      expect(article.helpful).toBe(0);

      const updatedCategory = manager.getHelpCategory(category.id);
      expect(updatedCategory?.articleCount).toBe(1);
    });

    it('should increment views when getting article', () => {
      const category = manager.createHelpCategory({
        name: 'Test',
        description: 'Test',
        icon: 'icon',
        order: 1,
      });

      const article = manager.createHelpArticle({
        title: 'Test Article',
        content: 'Content',
        category: category.id,
        tags: [],
        relatedArticles: [],
        author: 'test@example.com',
        published: true,
        searchTerms: [],
      });

      manager.getHelpArticle(article.id);
      manager.getHelpArticle(article.id);

      const updated = manager.getHelpArticle(article.id);
      expect(updated?.views).toBe(3);
    });

    it('should get articles by category', () => {
      const category1 = manager.createHelpCategory({
        name: 'Category 1',
        description: 'Test',
        icon: 'icon',
        order: 1,
      });

      const category2 = manager.createHelpCategory({
        name: 'Category 2',
        description: 'Test',
        icon: 'icon',
        order: 2,
      });

      manager.createHelpArticle({
        title: 'Article 1',
        content: 'Content',
        category: category1.id,
        tags: [],
        relatedArticles: [],
        author: 'test@example.com',
        published: true,
        searchTerms: [],
      });

      manager.createHelpArticle({
        title: 'Article 2',
        content: 'Content',
        category: category2.id,
        tags: [],
        relatedArticles: [],
        author: 'test@example.com',
        published: true,
        searchTerms: [],
      });

      manager.createHelpArticle({
        title: 'Article 3',
        content: 'Content',
        category: category1.id,
        tags: [],
        relatedArticles: [],
        author: 'test@example.com',
        published: true,
        searchTerms: [],
      });

      const category1Articles = manager.getArticlesByCategory(category1.id);
      expect(category1Articles).toHaveLength(2);
    });

    it('should search help articles', () => {
      const category = manager.createHelpCategory({
        name: 'Test',
        description: 'Test',
        icon: 'icon',
        order: 1,
      });

      manager.createHelpArticle({
        title: 'How to create a canvas',
        content: 'Click the new button to create a canvas',
        category: category.id,
        tags: ['canvas', 'basics'],
        relatedArticles: [],
        author: 'test@example.com',
        published: true,
        searchTerms: ['create', 'canvas', 'new'],
      });

      manager.createHelpArticle({
        title: 'Drawing shapes',
        content: 'Use the shape tool to draw rectangles',
        category: category.id,
        tags: ['shapes', 'drawing'],
        relatedArticles: [],
        author: 'test@example.com',
        published: true,
        searchTerms: ['drawing', 'shapes', 'rectangles'],
      });

      const results = manager.searchHelpArticles('canvas create');
      expect(results.length).toBeGreaterThan(0);
      expect(results[0]!.article.title).toBe('How to create a canvas');
      expect(results[0]!.matchedTerms).toContain('canvas');
      expect(results[0]!.excerpt).toBeDefined();
    });

    it('should not return unpublished articles in search', () => {
      const category = manager.createHelpCategory({
        name: 'Test',
        description: 'Test',
        icon: 'icon',
        order: 1,
      });

      manager.createHelpArticle({
        title: 'Draft Article',
        content: 'This is a draft',
        category: category.id,
        tags: ['draft'],
        relatedArticles: [],
        author: 'test@example.com',
        published: false,
        searchTerms: ['draft'],
      });

      const results = manager.searchHelpArticles('draft');
      expect(results).toHaveLength(0);
    });

    it('should submit help feedback', () => {
      const category = manager.createHelpCategory({
        name: 'Test',
        description: 'Test',
        icon: 'icon',
        order: 1,
      });

      const article = manager.createHelpArticle({
        title: 'Test Article',
        content: 'Content',
        category: category.id,
        tags: [],
        relatedArticles: [],
        author: 'test@example.com',
        published: true,
        searchTerms: [],
      });

      const feedback = manager.submitHelpFeedback('user1', article.id, true, 'Very helpful!');

      expect(feedback.id).toBeDefined();
      expect(feedback.helpful).toBe(true);
      expect(feedback.comment).toBe('Very helpful!');

      const updatedArticle = manager.getHelpArticle(article.id);
      expect(updatedArticle?.helpful).toBe(1);
      expect(updatedArticle?.notHelpful).toBe(0);
    });

    it('should get article feedback', () => {
      const category = manager.createHelpCategory({
        name: 'Test',
        description: 'Test',
        icon: 'icon',
        order: 1,
      });

      const article = manager.createHelpArticle({
        title: 'Test Article',
        content: 'Content',
        category: category.id,
        tags: [],
        relatedArticles: [],
        author: 'test@example.com',
        published: true,
        searchTerms: [],
      });

      manager.submitHelpFeedback('user1', article.id, true);
      manager.submitHelpFeedback('user2', article.id, false, 'Not helpful');

      const feedback = manager.getArticleFeedback(article.id);
      expect(feedback).toHaveLength(2);
    });
  });

  // ==========================================================================
  // Documentation Generation
  // ==========================================================================

  describe('Documentation', () => {
    it('should generate documentation', () => {
      const doc = manager.generateDocumentation({
        title: 'API Documentation',
        version: '1.0.0',
        description: 'Complete API reference',
        format: 'markdown',
        sections: [
          {
            id: 'intro',
            title: 'Introduction',
            order: 1,
            content: 'Welcome to the API',
            subsections: [],
            codeExamples: [],
            apiReferences: [],
          },
        ],
        metadata: {
          author: 'dev@example.com',
          contributors: [],
          license: 'MIT',
          repository: 'https://github.com/example/repo',
          tags: ['api', 'docs'],
          keywords: ['api', 'documentation'],
        },
      });

      expect(doc.id).toBeDefined();
      expect(doc.title).toBe('API Documentation');
      expect(doc.generatedAt).toBeDefined();
    });

    it('should get documentation by ID', () => {
      const doc = manager.generateDocumentation({
        title: 'Test Docs',
        version: '1.0.0',
        description: 'Test',
        format: 'markdown',
        sections: [],
        metadata: {
          author: 'test@example.com',
          contributors: [],
          license: 'MIT',
          repository: '',
          tags: [],
          keywords: [],
        },
      });

      const retrieved = manager.getDocumentation(doc.id);
      expect(retrieved).toEqual(doc);
    });

    it('should get all documentation sorted by date', async () => {
      manager.generateDocumentation({
        title: 'Docs v1',
        version: '1.0.0',
        description: 'Test',
        format: 'markdown',
        sections: [],
        metadata: {
          author: 'test@example.com',
          contributors: [],
          license: 'MIT',
          repository: '',
          tags: [],
          keywords: [],
        },
      });

      // Wait a bit to ensure different timestamps
      await new Promise((resolve) => setTimeout(resolve, 10));

      manager.generateDocumentation({
        title: 'Docs v2',
        version: '2.0.0',
        description: 'Test',
        format: 'markdown',
        sections: [],
        metadata: {
          author: 'test@example.com',
          contributors: [],
          license: 'MIT',
          repository: '',
          tags: [],
          keywords: [],
        },
      });

      const allDocs = manager.getAllDocumentation();
      expect(allDocs).toHaveLength(2);
      expect(allDocs[0]!.version).toBe('2.0.0'); // Most recent first
    });

    it('should export documentation as markdown', () => {
      const doc = manager.generateDocumentation({
        title: 'API Docs',
        version: '1.0.0',
        description: 'Complete API',
        format: 'markdown',
        sections: [
          {
            id: 'intro',
            title: 'Introduction',
            order: 1,
            content: 'Welcome',
            subsections: [],
            codeExamples: [
              {
                id: 'example1',
                language: 'typescript',
                code: 'const x = 1;',
                description: 'Example code',
                runnable: false,
              },
            ],
            apiReferences: [
              {
                id: 'api1',
                name: 'createCanvas',
                type: 'function',
                signature: 'createCanvas(options: CanvasOptions): Canvas',
                description: 'Creates a new canvas',
                parameters: [
                  {
                    name: 'options',
                    type: 'CanvasOptions',
                    description: 'Canvas configuration',
                    optional: false,
                  },
                ],
                examples: [],
              },
            ],
          },
        ],
        metadata: {
          author: 'dev@example.com',
          contributors: ['contributor@example.com'],
          license: 'MIT',
          repository: 'https://github.com/example/repo',
          tags: [],
          keywords: [],
        },
      });

      const markdown = manager.exportDocumentationMarkdown(doc.id);

      expect(markdown).toContain('# API Docs');
      expect(markdown).toContain('**Version**: 1.0.0');
      expect(markdown).toContain('**Author**: dev@example.com');
      expect(markdown).toContain('## Introduction');
      expect(markdown).toContain('```typescript');
      expect(markdown).toContain('const x = 1;');
      expect(markdown).toContain('#### `createCanvas`');
    });
  });

  // ==========================================================================
  // Tooltip Management
  // ==========================================================================

  describe('Tooltips', () => {
    it('should create tooltip', () => {
      const tooltip = manager.createTooltip({
        target: '#create-button',
        content: 'Click to create new canvas',
        position: 'bottom',
        trigger: 'hover',
        delay: 500,
        contextual: true,
        dismissible: true,
        priority: 1,
      });

      expect(tooltip.id).toBeDefined();
      expect(tooltip.target).toBe('#create-button');
    });

    it('should get tooltips for target', () => {
      manager.createTooltip({
        target: '#button',
        content: 'Tooltip 1',
        position: 'top',
        trigger: 'hover',
        delay: 500,
        contextual: false,
        dismissible: true,
        priority: 1,
      });

      manager.createTooltip({
        target: '#button',
        content: 'Tooltip 2',
        position: 'bottom',
        trigger: 'hover',
        delay: 500,
        contextual: false,
        dismissible: true,
        priority: 2,
      });

      manager.createTooltip({
        target: '#other',
        content: 'Tooltip 3',
        position: 'left',
        trigger: 'hover',
        delay: 500,
        contextual: false,
        dismissible: true,
        priority: 1,
      });

      const tooltips = manager.getTooltipsForTarget('#button');
      expect(tooltips).toHaveLength(2);
      expect(tooltips[0]!.priority).toBe(2); // Higher priority first
    });

    it('should get all tooltips', () => {
      manager.createTooltip({
        target: '#button1',
        content: 'Tooltip 1',
        position: 'top',
        trigger: 'hover',
        delay: 500,
        contextual: false,
        dismissible: true,
        priority: 1,
      });

      manager.createTooltip({
        target: '#button2',
        content: 'Tooltip 2',
        position: 'bottom',
        trigger: 'click',
        delay: 500,
        contextual: false,
        dismissible: true,
        priority: 1,
      });

      const allTooltips = manager.getAllTooltips();
      expect(allTooltips).toHaveLength(2);
    });

    it('should remove tooltip', () => {
      const tooltip = manager.createTooltip({
        target: '#button',
        content: 'Tooltip',
        position: 'top',
        trigger: 'hover',
        delay: 500,
        contextual: false,
        dismissible: true,
        priority: 1,
      });

      const removed = manager.removeTooltip(tooltip.id);
      expect(removed).toBe(true);

      const tooltips = manager.getAllTooltips();
      expect(tooltips).toHaveLength(0);
    });
  });

  // ==========================================================================
  // Analytics
  // ==========================================================================

  describe('Analytics', () => {
    it('should get enablement analytics', () => {
      // Create some flows and tutorials
      const flow = manager.createOnboardingFlow({
        name: 'Test Flow',
        description: 'Test',
        targetAudience: 'all',
        estimatedDuration: 10,
        steps: [],
        prerequisites: [],
        published: true,
      });

      const tutorial = manager.createTutorial({
        title: 'Test Tutorial',
        description: 'Test',
        category: 'test',
        difficulty: 'beginner',
        tags: [],
        duration: 10,
        lessons: [],
        prerequisites: [],
        published: true,
        author: 'test@example.com',
      });

      // Start some progress
      manager.startOnboarding('user1', flow.id);
      manager.startTutorial('user2', tutorial.id);

      const analytics = manager.getAnalytics();

      expect(analytics.totalUsers).toBe(2);
      expect(analytics.activeUsers).toBeGreaterThanOrEqual(0);
      expect(analytics.onboardingCompletionRate).toBeGreaterThanOrEqual(0);
      expect(analytics.tutorialCompletionRate).toBeGreaterThanOrEqual(0);
    });
  });

  // ==========================================================================
  // Cleanup and Utilities
  // ==========================================================================

  describe('Cleanup Operations', () => {
    it('should clean up old progress', () => {
      const flow = manager.createOnboardingFlow({
        name: 'Test Flow',
        description: 'Test',
        targetAudience: 'all',
        estimatedDuration: 10,
        steps: [],
        prerequisites: [],
        published: true,
      });

      const progress = manager.startOnboarding('user1', flow.id);

      // Manually set completion date to past retention period
      const userProgress = manager.getUserOnboardingProgress('user1');
      userProgress[0]!.completedAt = new Date(Date.now() - 400 * 24 * 60 * 60 * 1000); // 400 days ago

      const result = manager.cleanupOldProgress();
      expect(result.onboardingRemoved).toBe(1);
    });
  });

  describe('Reset Operations', () => {
    it('should reset manager state', () => {
      manager.createOnboardingFlow({
        name: 'Test',
        description: 'Test',
        targetAudience: 'all',
        estimatedDuration: 10,
        steps: [],
        prerequisites: [],
        published: true,
      });

      manager.createTutorial({
        title: 'Test',
        description: 'Test',
        category: 'test',
        difficulty: 'beginner',
        tags: [],
        duration: 10,
        lessons: [],
        prerequisites: [],
        published: true,
        author: 'test@example.com',
      });

      manager.reset();

      expect(manager.getAllOnboardingFlows()).toHaveLength(0);
      expect(manager.getAllTutorials()).toHaveLength(0);
      expect(manager.getAllHelpCategories()).toHaveLength(0);
      expect(manager.getAllDocumentation()).toHaveLength(0);
    });
  });
});

// ==============================================================================
// Helper Functions Tests
// ==============================================================================

describe('User Enablement Helper Functions', () => {
  describe('calculateOnboardingCompletion', () => {
    it('should calculate completion percentage', () => {
      const flow: OnboardingFlow = {
        id: 'flow1',
        name: 'Test',
        description: 'Test',
        targetAudience: 'all',
        estimatedDuration: 10,
        steps: [
          { id: 'step1', order: 1, title: 'Step 1', content: '', type: 'text', duration: 60, required: true },
          { id: 'step2', order: 2, title: 'Step 2', content: '', type: 'text', duration: 60, required: true },
          { id: 'step3', order: 3, title: 'Step 3', content: '', type: 'text', duration: 60, required: true },
        ],
        prerequisites: [],
        createdAt: new Date(),
        updatedAt: new Date(),
        published: true,
        completionRate: 0,
        averageRating: 0,
      };

      const progress = {
        id: 'progress1',
        userId: 'user1',
        flowId: 'flow1',
        currentStepId: 'step2',
        completedSteps: ['step1'],
        startedAt: new Date(),
        lastActivityAt: new Date(),
      };

      const completion = calculateOnboardingCompletion(progress, flow);
      expect(completion).toBeCloseTo(33.33, 1);
    });

    it('should return 0 for empty flow', () => {
      const flow: OnboardingFlow = {
        id: 'flow1',
        name: 'Test',
        description: 'Test',
        targetAudience: 'all',
        estimatedDuration: 10,
        steps: [],
        prerequisites: [],
        createdAt: new Date(),
        updatedAt: new Date(),
        published: true,
        completionRate: 0,
        averageRating: 0,
      };

      const progress = {
        id: 'progress1',
        userId: 'user1',
        flowId: 'flow1',
        currentStepId: '',
        completedSteps: [],
        startedAt: new Date(),
        lastActivityAt: new Date(),
      };

      const completion = calculateOnboardingCompletion(progress, flow);
      expect(completion).toBe(0);
    });
  });

  describe('calculateTutorialCompletion', () => {
    it('should calculate completion percentage', () => {
      const tutorial: Tutorial = {
        id: 'tutorial1',
        title: 'Test',
        description: 'Test',
        category: 'test',
        difficulty: 'beginner',
        tags: [],
        duration: 30,
        lessons: [
          { id: 'lesson1', order: 1, title: 'Lesson 1', content: '', codeExamples: [], exercises: [], resources: [], duration: 10 },
          { id: 'lesson2', order: 2, title: 'Lesson 2', content: '', codeExamples: [], exercises: [], resources: [], duration: 10 },
          { id: 'lesson3', order: 3, title: 'Lesson 3', content: '', codeExamples: [], exercises: [], resources: [], duration: 10 },
        ],
        prerequisites: [],
        createdAt: new Date(),
        updatedAt: new Date(),
        published: true,
        author: 'test@example.com',
        views: 0,
        completions: 0,
      };

      const progress = {
        id: 'progress1',
        userId: 'user1',
        tutorialId: 'tutorial1',
        currentLessonId: 'lesson3',
        completedLessons: ['lesson1', 'lesson2'],
        exerciseScores: new Map(),
        startedAt: new Date(),
        lastActivityAt: new Date(),
        totalScore: 0,
      };

      const completion = calculateTutorialCompletion(progress, tutorial);
      expect(completion).toBeCloseTo(66.67, 1);
    });

    it('should return 0 for empty tutorial', () => {
      const tutorial: Tutorial = {
        id: 'tutorial1',
        title: 'Test',
        description: 'Test',
        category: 'test',
        difficulty: 'beginner',
        tags: [],
        duration: 0,
        lessons: [],
        prerequisites: [],
        createdAt: new Date(),
        updatedAt: new Date(),
        published: true,
        author: 'test@example.com',
        views: 0,
        completions: 0,
      };

      const progress = {
        id: 'progress1',
        userId: 'user1',
        tutorialId: 'tutorial1',
        currentLessonId: '',
        completedLessons: [],
        exerciseScores: new Map(),
        startedAt: new Date(),
        lastActivityAt: new Date(),
        totalScore: 0,
      };

      const completion = calculateTutorialCompletion(progress, tutorial);
      expect(completion).toBe(0);
    });
  });

  describe('calculateEstimatedTimeRemaining', () => {
    it('should calculate remaining time', () => {
      const flow: OnboardingFlow = {
        id: 'flow1',
        name: 'Test',
        description: 'Test',
        targetAudience: 'all',
        estimatedDuration: 10,
        steps: [
          { id: 'step1', order: 1, title: 'Step 1', content: '', type: 'text', duration: 60, required: true },
          { id: 'step2', order: 2, title: 'Step 2', content: '', type: 'text', duration: 120, required: true },
          { id: 'step3', order: 3, title: 'Step 3', content: '', type: 'text', duration: 180, required: true },
        ],
        prerequisites: [],
        createdAt: new Date(),
        updatedAt: new Date(),
        published: true,
        completionRate: 0,
        averageRating: 0,
      };

      const progress = {
        id: 'progress1',
        userId: 'user1',
        flowId: 'flow1',
        currentStepId: 'step2',
        completedSteps: ['step1'],
        startedAt: new Date(),
        lastActivityAt: new Date(),
      };

      const remaining = calculateEstimatedTimeRemaining(progress, flow);
      expect(remaining).toBe(300); // 120 + 180
    });
  });

  describe('formatDuration', () => {
    it('should format seconds', () => {
      expect(formatDuration(45)).toBe('45s');
    });

    it('should format minutes only', () => {
      expect(formatDuration(300)).toBe('5m');
    });

    it('should format hours only', () => {
      expect(formatDuration(7200)).toBe('2h');
    });

    it('should format hours and minutes', () => {
      expect(formatDuration(7320)).toBe('2h 2m');
    });
  });

  describe('validateQuizAnswer', () => {
    it('should validate single answer', () => {
      const question = {
        id: 'q1',
        question: 'What is 2+2?',
        type: 'multiple-choice' as const,
        options: ['3', '4', '5'],
        correctAnswer: '4',
        explanation: 'Basic math',
        points: 10,
      };

      expect(validateQuizAnswer(question, '4')).toBe(true);
      expect(validateQuizAnswer(question, '3')).toBe(false);
    });

    it('should validate multiple answers', () => {
      const question = {
        id: 'q1',
        question: 'Select all even numbers',
        type: 'multiple-choice' as const,
        options: ['1', '2', '3', '4'],
        correctAnswer: ['2', '4'],
        explanation: 'Even numbers',
        points: 10,
      };

      expect(validateQuizAnswer(question, ['2', '4'])).toBe(true);
      expect(validateQuizAnswer(question, ['4', '2'])).toBe(true); // Order doesn't matter
      expect(validateQuizAnswer(question, ['2'])).toBe(false);
      expect(validateQuizAnswer(question, ['2', '3', '4'])).toBe(false);
    });
  });

  describe('generateSearchTerms', () => {
    it('should generate search terms from title and content', () => {
      const content = 'This is a guide on how to create amazing canvas applications using our platform';
      const title = 'Canvas Creation Guide';

      const terms = generateSearchTerms(content, title);

      expect(terms).toContain('canvas');
      expect(terms).toContain('creation');
      expect(terms).toContain('guide');
      expect(terms).toContain('create');
      expect(terms).toContain('applications');
      expect(terms.length).toBeLessThanOrEqual(50);
    });

    it('should filter out stop words', () => {
      const content = 'The quick brown fox jumps over the lazy dog';
      const title = 'Test Article';

      const terms = generateSearchTerms(content, title);

      expect(terms).not.toContain('the');
      expect(terms).not.toContain('and');
      expect(terms).not.toContain('or');
    });

    it('should filter out short words', () => {
      const content = 'A big app is fun to use';
      const title = 'App Guide';

      const terms = generateSearchTerms(content, title);

      expect(terms).not.toContain('a');
      expect(terms).not.toContain('is');
      expect(terms).not.toContain('to');
    });
  });

  describe('calculateRelevanceScore', () => {
    it('should score title matches highest', () => {
      const article: HelpArticle = {
        id: 'article1',
        title: 'Canvas Tutorial',
        content: 'Learn about drawing',
        category: 'cat1',
        tags: [],
        relatedArticles: [],
        author: 'test@example.com',
        createdAt: new Date(),
        updatedAt: new Date(),
        published: true,
        views: 0,
        helpful: 0,
        notHelpful: 0,
        searchTerms: ['canvas', 'tutorial'],
      };

      const score = calculateRelevanceScore(article, 'canvas');
      expect(score).toBeGreaterThan(0);
    });

    it('should boost recent articles', () => {
      const recentArticle: HelpArticle = {
        id: 'article1',
        title: 'Test',
        content: 'test content',
        category: 'cat1',
        tags: [],
        relatedArticles: [],
        author: 'test@example.com',
        createdAt: new Date(),
        updatedAt: new Date(),
        published: true,
        views: 0,
        helpful: 0,
        notHelpful: 0,
        searchTerms: ['test'],
      };

      const oldArticle: HelpArticle = {
        ...recentArticle,
        id: 'article2',
        updatedAt: new Date(Date.now() - 60 * 24 * 60 * 60 * 1000), // 60 days ago
      };

      const recentScore = calculateRelevanceScore(recentArticle, 'test');
      const oldScore = calculateRelevanceScore(oldArticle, 'test');

      expect(recentScore).toBeGreaterThan(oldScore);
    });

    it('should boost helpful articles', () => {
      const helpfulArticle: HelpArticle = {
        id: 'article1',
        title: 'Test',
        content: 'test content',
        category: 'cat1',
        tags: [],
        relatedArticles: [],
        author: 'test@example.com',
        createdAt: new Date(),
        updatedAt: new Date(),
        published: true,
        views: 0,
        helpful: 10,
        notHelpful: 1,
        searchTerms: ['test'],
      };

      const unhelpfulArticle: HelpArticle = {
        ...helpfulArticle,
        id: 'article2',
        helpful: 1,
        notHelpful: 10,
      };

      const helpfulScore = calculateRelevanceScore(helpfulArticle, 'test');
      const unhelpfulScore = calculateRelevanceScore(unhelpfulArticle, 'test');

      expect(helpfulScore).toBeGreaterThan(unhelpfulScore);
    });
  });
});
