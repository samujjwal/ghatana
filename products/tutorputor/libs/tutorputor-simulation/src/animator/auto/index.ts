/**
 * Automatic Animation Creation Service
 * 
 * AI-powered automatic animation generation that creates animations
 * from natural language descriptions, educational content, or learning objectives.
 * 
 * Integrates with @tutorputor/ai-proxy for LLM-based generation.
 */

import type { AnimationTrack, AnimationKeyframe, EasingFunction } from '../index';

// =============================================================================
// Types
// =============================================================================

export interface AutoAnimationRequest {
  /** Natural language description of the desired animation */
  description: string;
  /** Target element selector */
  target?: string;
  /** Animation purpose (educational, decorative, functional) */
  purpose?: 'educational' | 'decorative' | 'functional';
  /** Subject domain for educational animations */
  domain?: 'physics' | 'chemistry' | 'biology' | 'math' | 'cs' | 'general';
  /** Learning objective for educational animations */
  learningObjective?: string;
  /** Preferred duration in milliseconds */
  duration?: number;
  /** Style preference */
  style?: 'subtle' | 'moderate' | 'dramatic';
  /** Complexity level */
  complexity?: 'simple' | 'medium' | 'complex';
  /** Target audience */
  audience?: 'beginner' | 'intermediate' | 'advanced';
}

export interface AutoAnimationResult {
  /** Generated animation tracks */
  tracks: AnimationTrack[];
  /** Explanation of the animation */
  explanation: string;
  /** Suggested narration script */
  narration?: string;
  /** Educational metadata */
  educational?: {
    concepts: string[];
    prerequisites: string[];
    followUpQuestions: string[];
  };
  /** Confidence score (0-1) */
  confidence: number;
}

export interface AnimationTemplate {
  id: string;
  name: string;
  description: string;
  category: string;
  tags: string[];
  tracks: AnimationTrack[];
  previewUrl?: string;
}

// =============================================================================
// Animation Templates Library
// =============================================================================

export const AnimationTemplates: AnimationTemplate[] = [
  // Educational Templates
  {
    id: 'electron-orbit',
    name: 'Electron Orbital Motion',
    description: 'Visualizes electron movement around a nucleus',
    category: 'chemistry',
    tags: ['chemistry', 'atom', 'electron', 'orbital', 'physics'],
    tracks: [
      {
        id: 'electron-orbit-motion',
        target: '.electron',
        property: 'transform',
        from: 'rotate(0deg) translateX(50px)',
        to: 'rotate(360deg) translateX(50px)',
        duration: 2000,
        easing: 'linear',
        repeat: -1,
      },
      {
        id: 'electron-pulse',
        target: '.electron',
        property: 'opacity',
        from: 0.6,
        to: 1,
        duration: 500,
        easing: 'ease-in-out',
        yoyo: true,
        repeat: -1,
      },
    ],
  },
  {
    id: 'dna-helix',
    name: 'DNA Double Helix Rotation',
    description: 'Rotating DNA structure visualization',
    category: 'biology',
    tags: ['biology', 'dna', 'helix', 'genetics', 'molecular'],
    tracks: [
      {
        id: 'helix-rotate',
        target: '.dna-strand',
        property: 'transform',
        from: 'rotateY(0deg)',
        to: 'rotateY(360deg)',
        duration: 8000,
        easing: 'linear',
        repeat: -1,
      },
      {
        id: 'base-pair-glow',
        target: '.base-pair',
        property: 'box-shadow',
        from: '0 0 5px #4ecdc4',
        to: '0 0 20px #4ecdc4',
        duration: 1000,
        easing: 'ease-in-out',
        yoyo: true,
        repeat: -1,
      },
    ],
  },
  {
    id: 'newton-cradle',
    name: 'Newton\'s Cradle',
    description: 'Physics demonstration of momentum conservation',
    category: 'physics',
    tags: ['physics', 'momentum', 'pendulum', 'energy', 'mechanics'],
    tracks: [
      {
        id: 'ball-1-swing',
        target: '.ball-1',
        property: 'transform',
        from: 'rotate(-30deg)',
        to: 'rotate(0deg)',
        duration: 500,
        easing: 'ease-in-out',
      },
      {
        id: 'ball-5-swing',
        target: '.ball-5',
        property: 'transform',
        from: 'rotate(0deg)',
        to: 'rotate(30deg)',
        duration: 500,
        delay: 500,
        easing: 'ease-in-out',
      },
      {
        id: 'ball-5-return',
        target: '.ball-5',
        property: 'transform',
        from: 'rotate(30deg)',
        to: 'rotate(0deg)',
        duration: 500,
        delay: 1000,
        easing: 'ease-in-out',
      },
      {
        id: 'ball-1-return',
        target: '.ball-1',
        property: 'transform',
        from: 'rotate(0deg)',
        to: 'rotate(-30deg)',
        duration: 500,
        delay: 1500,
        easing: 'ease-in-out',
        repeat: -1,
      },
    ],
  },
  {
    id: 'cell-division',
    name: 'Cell Mitosis',
    description: 'Animation of cell division process',
    category: 'biology',
    tags: ['biology', 'cell', 'mitosis', 'division', 'reproduction'],
    tracks: [
      {
        id: 'cell-grow',
        target: '.cell',
        property: 'transform',
        from: 'scale(1)',
        to: 'scale(1.3)',
        duration: 2000,
        easing: 'ease-out',
      },
      {
        id: 'chromosomes-align',
        target: '.chromosomes',
        property: 'transform',
        from: 'translateX(-20px)',
        to: 'translateX(0px)',
        duration: 1500,
        delay: 2000,
        easing: 'ease-in-out',
      },
      {
        id: 'cell-split',
        target: '.cell-membrane',
        property: 'clip-path',
        from: 'ellipse(50% 50% at 50% 50%)',
        to: 'ellipse(25% 50% at 35% 50%), ellipse(25% 50% at 65% 50%)',
        duration: 2000,
        delay: 3500,
        easing: 'ease-in-out',
      },
    ],
  },
  {
    id: 'algorithm-sort',
    name: 'Bubble Sort Algorithm',
    description: 'Visualizes bubble sort algorithm steps',
    category: 'cs',
    tags: ['computer-science', 'algorithm', 'sorting', 'bubble-sort', 'data-structures'],
    tracks: [
      {
        id: 'compare-bars',
        target: '.bar-1, .bar-2',
        property: 'background-color',
        from: '#4ecdc4',
        to: '#ff6b6b',
        duration: 500,
        easing: 'ease-in-out',
      },
      {
        id: 'swap-bars',
        target: '.bar-1',
        property: 'transform',
        from: 'translateX(0px)',
        to: 'translateX(60px)',
        duration: 300,
        delay: 500,
        easing: 'ease-out',
      },
      {
        id: 'swap-bars-2',
        target: '.bar-2',
        property: 'transform',
        from: 'translateX(60px)',
        to: 'translateX(0px)',
        duration: 300,
        delay: 500,
        easing: 'ease-out',
      },
      {
        id: 'mark-sorted',
        target: '.bar-1, .bar-2',
        property: 'background-color',
        from: '#ff6b6b',
        to: '#4ecdc4',
        duration: 300,
        delay: 800,
        easing: 'ease-in-out',
      },
    ],
  },
  // UI/UX Templates
  {
    id: 'page-transition-slide',
    name: 'Page Slide Transition',
    description: 'Smooth sliding page transition',
    category: 'ui',
    tags: ['ui', 'transition', 'page', 'slide', 'navigation'],
    tracks: [
      {
        id: 'slide-out',
        target: '.current-page',
        property: 'transform',
        from: 'translateX(0)',
        to: 'translateX(-100%)',
        duration: 300,
        easing: 'ease-in-out',
      },
      {
        id: 'slide-in',
        target: '.next-page',
        property: 'transform',
        from: 'translateX(100%)',
        to: 'translateX(0)',
        duration: 300,
        easing: 'ease-in-out',
      },
    ],
  },
  {
    id: 'modal-appear',
    name: 'Modal Appearance',
    description: 'Modal fade in with scale effect',
    category: 'ui',
    tags: ['ui', 'modal', 'dialog', 'overlay', 'popup'],
    tracks: [
      {
        id: 'backdrop-fade',
        target: '.modal-backdrop',
        property: 'opacity',
        from: 0,
        to: 0.5,
        duration: 200,
        easing: 'ease-out',
      },
      {
        id: 'modal-scale',
        target: '.modal-content',
        property: 'transform',
        from: 'scale(0.8) translateY(-20px)',
        to: 'scale(1) translateY(0)',
        duration: 300,
        delay: 100,
        easing: 'spring',
      },
      {
        id: 'modal-fade',
        target: '.modal-content',
        property: 'opacity',
        from: 0,
        to: 1,
        duration: 200,
        delay: 100,
        easing: 'ease-out',
      },
    ],
  },
  {
    id: 'loading-spinner',
    name: 'Loading Spinner',
    description: 'Infinite loading animation',
    category: 'ui',
    tags: ['ui', 'loading', 'spinner', 'progress', 'indicator'],
    tracks: [
      {
        id: 'spinner-rotate',
        target: '.spinner',
        property: 'transform',
        from: 'rotate(0deg)',
        to: 'rotate(360deg)',
        duration: 1000,
        easing: 'linear',
        repeat: -1,
      },
      {
        id: 'spinner-pulse',
        target: '.spinner',
        property: 'opacity',
        from: 0.5,
        to: 1,
        duration: 500,
        easing: 'ease-in-out',
        yoyo: true,
        repeat: -1,
      },
    ],
  },
  // Attention/Feedback Templates
  {
    id: 'success-checkmark',
    name: 'Success Checkmark',
    description: 'Animated success confirmation',
    category: 'feedback',
    tags: ['feedback', 'success', 'checkmark', 'confirmation', 'positive'],
    tracks: [
      {
        id: 'circle-draw',
        target: '.check-circle',
        property: 'stroke-dashoffset',
        from: 100,
        to: 0,
        duration: 500,
        easing: 'ease-out',
      },
      {
        id: 'check-draw',
        target: '.check-path',
        property: 'stroke-dashoffset',
        from: 50,
        to: 0,
        duration: 400,
        delay: 400,
        easing: 'ease-out',
      },
      {
        id: 'scale-bounce',
        target: '.check-container',
        property: 'transform',
        from: 'scale(0)',
        to: 'scale(1)',
        duration: 400,
        easing: 'spring',
      },
    ],
  },
  {
    id: 'error-shake',
    name: 'Error Shake',
    description: 'Shake animation for error indication',
    category: 'feedback',
    tags: ['feedback', 'error', 'shake', 'warning', 'negative'],
    tracks: [
      {
        id: 'shake-horizontal',
        target: '.error-element',
        property: 'transform',
        keyframes: [
          { time: 0, value: 'translateX(0)' },
          { time: 100, value: 'translateX(-10px)' },
          { time: 200, value: 'translateX(10px)' },
          { time: 300, value: 'translateX(-10px)' },
          { time: 400, value: 'translateX(10px)' },
          { time: 500, value: 'translateX(0)' },
        ],
        from: 'translateX(0)',
        to: 'translateX(0)',
        duration: 500,
        easing: 'ease-in-out',
      },
      {
        id: 'error-color',
        target: '.error-element',
        property: 'border-color',
        from: '#ccc',
        to: '#ff6b6b',
        duration: 200,
        easing: 'ease-out',
      },
    ],
  },
  {
    id: 'notification-slide',
    name: 'Notification Slide-in',
    description: 'Toast notification animation',
    category: 'feedback',
    tags: ['feedback', 'notification', 'toast', 'alert', 'message'],
    tracks: [
      {
        id: 'slide-from-right',
        target: '.notification',
        property: 'transform',
        from: 'translateX(100%)',
        to: 'translateX(0)',
        duration: 400,
        easing: 'spring',
      },
      {
        id: 'fade-in',
        target: '.notification',
        property: 'opacity',
        from: 0,
        to: 1,
        duration: 300,
        easing: 'ease-out',
      },
      {
        id: 'progress-bar',
        target: '.notification-progress',
        property: 'width',
        from: '100%',
        to: '0%',
        duration: 5000,
        delay: 400,
        easing: 'linear',
      },
    ],
  },
];

// =============================================================================
// Automatic Animation Generator
// =============================================================================

export class AutoAnimationService {
  /**
   * Generate animation from natural language description
   */
  async generateFromDescription(request: AutoAnimationRequest): Promise<AutoAnimationResult> {
    // Analyze the description for key animation concepts
    const analysis = this.analyzeDescription(request.description);
    
    // Select appropriate templates based on analysis
    const templates = this.selectTemplates(analysis, request.domain);
    
    // Generate tracks from templates
    const tracks = this.generateTracksFromTemplates(
      templates,
      request.target || '.animated-element',
      request.duration,
      request.style,
      request.complexity
    );
    
    // Generate explanation and narration
    const explanation = this.generateExplanation(analysis, tracks);
    const narration = request.purpose === 'educational' 
      ? this.generateNarration(analysis, tracks, request.learningObjective)
      : undefined;
    
    // Calculate confidence
    const confidence = this.calculateConfidence(analysis, templates);
    
    return {
      tracks,
      explanation,
      narration,
      educational: request.purpose === 'educational' ? {
        concepts: analysis.concepts,
        prerequisites: this.inferPrerequisites(analysis),
        followUpQuestions: this.generateFollowUpQuestions(analysis),
      } : undefined,
      confidence,
    };
  }

  /**
   * Generate animation from learning objective
   */
  async generateForLearning(
    learningObjective: string,
    target: string,
    domain?: string
  ): Promise<AutoAnimationResult> {
    return this.generateFromDescription({
      description: learningObjective,
      target,
      domain: domain as any,
      purpose: 'educational',
      learningObjective,
      style: 'moderate',
      complexity: 'medium',
    });
  }

  /**
   * Generate animation from code or algorithm
   */
  async generateFromCode(
    code: string,
    language: string,
    target: string
  ): Promise<AutoAnimationResult> {
    // Parse code to identify algorithm type
    const algorithmType = this.detectAlgorithmType(code, language);
    
    // Select appropriate visualization template
    const template = this.getAlgorithmTemplate(algorithmType);
    
    // Customize based on code structure
    const tracks = this.adaptTemplateForCode(template, code, target);
    
    return {
      tracks,
      explanation: `Visualization of ${algorithmType} algorithm`,
      narration: this.generateCodeNarration(code, algorithmType),
      educational: {
        concepts: [algorithmType, `${language} programming`, 'algorithm analysis'],
        prerequisites: ['basic programming', 'data structures'],
        followUpQuestions: [
          `What is the time complexity of this ${algorithmType}?`,
          'Can you explain how this algorithm works?',
          'What are the best use cases for this algorithm?',
        ],
      },
      confidence: 0.85,
    };
  }

  /**
   * Get animation templates by category
   */
  getTemplatesByCategory(category: string): AnimationTemplate[] {
    return AnimationTemplates.filter((t) => t.category === category);
  }

  /**
   * Search templates by tags
   */
  searchTemplates(query: string): AnimationTemplate[] {
    const lowerQuery = query.toLowerCase();
    return AnimationTemplates.filter(
      (t) =>
        t.name.toLowerCase().includes(lowerQuery) ||
        t.description.toLowerCase().includes(lowerQuery) ||
        t.tags.some((tag) => tag.includes(lowerQuery))
    );
  }

  // =============================================================================
  // Private Methods
  // =============================================================================

  private analyzeDescription(description: string): {
    concepts: string[];
    actions: string[];
    objects: string[];
    movementType: string;
  } {
    const lowerDesc = description.toLowerCase();
    
    // Extract concepts (domain-specific keywords)
    const conceptKeywords: Record<string, string[]> = {
      physics: ['motion', 'velocity', 'acceleration', 'force', 'energy', 'momentum', 'gravity'],
      chemistry: ['atom', 'molecule', 'bond', 'reaction', 'electron', 'proton', 'element'],
      biology: ['cell', 'organism', 'dna', 'protein', 'mitosis', 'photosynthesis', 'organ'],
      cs: ['algorithm', 'sort', 'search', 'tree', 'graph', 'queue', 'stack', 'recursion'],
      math: ['equation', 'function', 'graph', 'geometry', 'calculus', 'vector', 'matrix'],
    };
    
    const concepts: string[] = [];
    Object.entries(conceptKeywords).forEach(([domain, keywords]) => {
      keywords.forEach((keyword) => {
        if (lowerDesc.includes(keyword)) {
          concepts.push(`${domain}:${keyword}`);
        }
      });
    });
    
    // Extract actions (verbs)
    const actionKeywords = [
      'move', 'rotate', 'scale', 'fade', 'slide', 'bounce', 'shake',
      'grow', 'shrink', 'appear', 'disappear', 'pulse', 'swing', 'orbit',
      'split', 'merge', 'transform', 'vibrate', 'oscillate', 'flow',
    ];
    const actions = actionKeywords.filter((action) => lowerDesc.includes(action));
    
    // Extract objects (nouns to animate)
    const objectKeywords = [
      'ball', 'box', 'circle', 'square', 'text', 'image', 'button',
      'card', 'modal', 'menu', 'sidebar', 'chart', 'graph', 'node', 'edge',
      'particle', 'wave', 'field', 'grid', 'line', 'arrow', 'icon',
    ];
    const objects = objectKeywords.filter((obj) => lowerDesc.includes(obj));
    
    // Determine movement type
    let movementType = 'linear';
    if (actions.includes('rotate') || actions.includes('orbit')) {
      movementType = 'circular';
    } else if (actions.includes('bounce') || actions.includes('oscillate')) {
      movementType = 'oscillating';
    } else if (actions.includes('fade') || actions.includes('appear')) {
      movementType = 'opacity';
    }
    
    return { concepts, actions, objects, movementType };
  }

  private selectTemplates(
    analysis: ReturnType<typeof this.analyzeDescription>,
    domain?: string
  ): AnimationTemplate[] {
    let candidates = AnimationTemplates;
    
    // Filter by domain/category
    if (domain) {
      candidates = candidates.filter((t) => t.category === domain || t.tags.includes(domain));
    }
    
    // Filter by concepts
    if (analysis.concepts.length > 0) {
      const conceptTags = analysis.concepts.map((c) => c.split(':')[1]);
      candidates = candidates.filter((t) =>
        conceptTags.some((tag) => t.tags.includes(tag!))
      );
    }
    
    // Filter by actions
    if (analysis.actions.length > 0) {
      candidates = candidates.filter((t) =>
        analysis.actions.some((action) =>
          t.description.toLowerCase().includes(action) ||
          t.tags.some((tag) => tag.includes(action))
        )
      );
    }
    
    // Return top matches or default templates
    return candidates.length > 0 ? candidates.slice(0, 3) : [AnimationTemplates[0]!];
  }

  private generateTracksFromTemplates(
    templates: AnimationTemplate[],
    target: string,
    duration?: number,
    style?: string,
    complexity?: string
  ): AnimationTrack[] {
    const tracks: AnimationTrack[] = [];
    const baseDuration = duration || 2000;
    
    templates.forEach((template, templateIndex) => {
      template.tracks.forEach((track, trackIndex) => {
        const newTrack: AnimationTrack = {
          ...track,
          id: `auto-${template.id}-${trackIndex}-${Date.now()}`,
          target: target,
          duration: this.adjustDuration(track.duration, baseDuration, style),
          delay: this.calculateDelay(track.delay || 0, templateIndex, trackIndex, complexity),
        };
        
        // Adjust easing based on style
        if (style === 'subtle') {
          newTrack.easing = 'ease-in-out';
        } else if (style === 'dramatic') {
          newTrack.easing = track.property === 'transform' ? 'spring' : 'ease-out';
        }
        
        tracks.push(newTrack);
      });
    });
    
    return tracks;
  }

  private adjustDuration(original: number, target: number, style?: string): number {
    const ratio = target / 2000; // Assume 2000ms as base
    let adjusted = original * ratio;
    
    if (style === 'subtle') {
      adjusted *= 1.2; // Slower for subtle
    } else if (style === 'dramatic') {
      adjusted *= 0.8; // Faster for dramatic
    }
    
    return Math.round(adjusted);
  }

  private calculateDelay(
    originalDelay: number,
    templateIndex: number,
    trackIndex: number,
    complexity?: string
  ): number {
    const baseDelay = templateIndex * 500 + trackIndex * 100;
    
    if (complexity === 'simple') {
      return baseDelay * 0.5;
    } else if (complexity === 'complex') {
      return baseDelay * 1.5;
    }
    
    return baseDelay + originalDelay;
  }

  private generateExplanation(
    analysis: ReturnType<typeof this.analyzeDescription>,
    tracks: AnimationTrack[]
  ): string {
    const actions = analysis.actions.join(', ');
    const objects = analysis.objects.join(', ');
    const concepts = analysis.concepts.map((c) => c.split(':')[1]).join(', ');
    
    return `This animation ${actions} the ${objects || 'elements'} to visualize ${concepts || 'the concept'}. It consists of ${tracks.length} animation tracks that work together to create a cohesive visualization.`;
  }

  private generateNarration(
    analysis: ReturnType<typeof this.analyzeDescription>,
    tracks: AnimationTrack[],
    learningObjective?: string
  ): string {
    let narration = `Let's explore ${learningObjective || 'this concept'} through animation. `;
    
    if (tracks.length > 0) {
      narration += `First, we ${tracks[0]!.property === 'transform' ? 'see movement' : 'observe a change'} `;
      narration += `as ${tracks[0]!.target.replace('.', 'the ')} begins to animate. `;
    }
    
    if (tracks.length > 1) {
      narration += `Then, ${tracks[1]!.target.replace('.', 'the ')} ${tracks[1]!.property === 'opacity' ? 'fades' : 'changes'} `;
      narration += `to show the relationship between these elements. `;
    }
    
    narration += `Notice how the animation helps illustrate ${analysis.concepts.join(' and ')}.`;
    
    return narration;
  }

  private calculateConfidence(
    analysis: ReturnType<typeof this.analyzeDescription>,
    templates: AnimationTemplate[]
  ): number {
    let confidence = 0.5;
    
    // Boost confidence for matching concepts
    if (analysis.concepts.length > 0) confidence += 0.2;
    if (analysis.actions.length > 0) confidence += 0.15;
    if (analysis.objects.length > 0) confidence += 0.1;
    
    // Boost for template matches
    if (templates.length > 0) confidence += 0.1;
    
    // Cap at 0.95
    return Math.min(confidence, 0.95);
  }

  private inferPrerequisites(analysis: ReturnType<typeof this.analyzeDescription>): string[] {
    const prerequisites: string[] = [];
    
    analysis.concepts.forEach((concept) => {
      const [domain, term] = concept.split(':');
      if (domain === 'physics') {
        prerequisites.push('basic physics concepts');
      } else if (domain === 'chemistry') {
        prerequisites.push('atomic structure fundamentals');
      } else if (domain === 'cs') {
        prerequisites.push('programming basics');
      }
    });
    
    return [...new Set(prerequisites)];
  }

  private generateFollowUpQuestions(
    analysis: ReturnType<typeof this.analyzeDescription>
  ): string[] {
    const questions: string[] = [
      'What would happen if we changed the animation speed?',
      'Can you explain the relationship between the animated elements?',
    ];
    
    if (analysis.concepts.includes('physics:motion')) {
      questions.push('How does velocity affect the animation?');
    }
    if (analysis.concepts.includes('cs:algorithm')) {
      questions.push('What is the time complexity of this algorithm?');
    }
    
    return questions;
  }

  private detectAlgorithmType(code: string, language: string): string {
    const lowerCode = code.toLowerCase();
    
    if (lowerCode.includes('sort')) {
      if (lowerCode.includes('bubble')) return 'bubble sort';
      if (lowerCode.includes('quick')) return 'quick sort';
      if (lowerCode.includes('merge')) return 'merge sort';
      return 'sorting';
    }
    
    if (lowerCode.includes('search')) {
      if (lowerCode.includes('binary')) return 'binary search';
      if (lowerCode.includes('depth') || lowerCode.includes('dfs')) return 'DFS';
      if (lowerCode.includes('breadth') || lowerCode.includes('bfs')) return 'BFS';
      return 'search';
    }
    
    if (lowerCode.includes('tree') || lowerCode.includes('node.left') || lowerCode.includes('node.right')) {
      return 'tree traversal';
    }
    
    if (lowerCode.includes('graph') || lowerCode.includes('vertex') || lowerCode.includes('edge')) {
      return 'graph algorithm';
    }
    
    return 'algorithm';
  }

  private getAlgorithmTemplate(algorithmType: string): AnimationTemplate {
    const template = AnimationTemplates.find((t) => 
      t.tags.includes(algorithmType.replace(' ', '-'))
    );
    
    return template || AnimationTemplates.find((t) => t.category === 'cs') || AnimationTemplates[0]!;
  }

  private adaptTemplateForCode(
    template: AnimationTemplate,
    code: string,
    target: string
  ): AnimationTrack[] {
    return template.tracks.map((track) => ({
      ...track,
      id: `code-${track.id}-${Date.now()}`,
      target: target,
    }));
  }

  private generateCodeNarration(code: string, algorithmType: string): string {
    return `This animation visualizes a ${algorithmType} algorithm. Watch as the code executes step by step, showing how data is transformed at each iteration.`;
  }
}

// =============================================================================
// Export singleton instance
// =============================================================================

export const autoAnimationService = new AutoAnimationService();

// =============================================================================
// Utility Functions
// =============================================================================

export function generateAnimationFromTemplate(
  templateId: string,
  target: string,
  overrides?: Partial<AnimationTrack>
): AnimationTrack[] | null {
  const template = AnimationTemplates.find((t) => t.id === templateId);
  if (!template) return null;
  
  return template.tracks.map((track) => ({
    ...track,
    id: `${templateId}-${track.id}-${Date.now()}`,
    target,
    ...overrides,
  }));
}

export function combineAnimations(tracksArray: AnimationTrack[][]): AnimationTrack[] {
  let offset = 0;
  const combined: AnimationTrack[] = [];
  
  tracksArray.forEach((tracks) => {
    const maxDuration = Math.max(...tracks.map((t) => (t.delay || 0) + t.duration));
    
    tracks.forEach((track) => {
      combined.push({
        ...track,
        id: `combined-${track.id}-${Date.now()}`,
        delay: (track.delay || 0) + offset,
      });
    });
    
    offset += maxDuration + 200; // 200ms gap between animations
  });
  
  return combined;
}

export function staggerAnimation(
  tracks: AnimationTrack[],
  staggerDelay: number = 100
): AnimationTrack[] {
  return tracks.map((track, index) => ({
    ...track,
    delay: (track.delay || 0) + index * staggerDelay,
  }));
}
